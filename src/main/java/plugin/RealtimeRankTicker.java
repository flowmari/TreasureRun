package plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import plugin.rank.JdbcLeaderboardSnapshotLoader;
import plugin.rank.RankingQueryService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Realtime leaderboard sidebar.
 *
 * <p>Thread contract:
 *
 * <ul>
 *   <li>Minecraft main thread: Bukkit/player/scoreboard state only.</li>
 *   <li>Async worker: bounded JDBC read only.</li>
 *   <li>No plugin shared JDBC Connection is used by this ticker.</li>
 *   <li>Late results from a stopped/old lifecycle are rejected.</li>
 * </ul>
 */
public class RealtimeRankTicker {

  private final TreasureRunMultiChestPlugin plugin;
  private final int intervalSec;
  private final DatabaseRuntimeSettings databaseSettings;

  private final AtomicBoolean loadInFlight = new AtomicBoolean(false);
  private final AtomicBoolean active = new AtomicBoolean(false);
  private final AtomicLong lifecycleGeneration = new AtomicLong(0L);

  private final Map<Mode, Integer> lastDigestByMode =
      new EnumMap<>(Mode.class);

  private BukkitTask task;
  // Main-thread-only failure transition flag: one warning per outage, one recovery log.
  private boolean loadFailureActive = false;

  private Mode mode = Mode.WEEKLY;
  private int toggleCounter = 0;

  private enum Mode {
    WEEKLY,
    ALLTIME,
    MONTHLY
  }

  private record LoadResult(
      Mode mode,
      List<Row> rows,
      Throwable failure
  ) {
    private LoadResult {
      rows = List.copyOf(rows);
    }
  }

  public RealtimeRankTicker(
      TreasureRunMultiChestPlugin plugin,
      int intervalSec,
      int topN,
      int tickerWidth
  ) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.intervalSec = Math.max(2, intervalSec);

    // Database settings are startup-scoped, matching the existing
    // optional leaderboard integration contract.
    this.databaseSettings =
        DatabaseRuntimeSettings.load(plugin.getConfig());
  }

  public void start() {
    stop();

    long generation = lifecycleGeneration.incrementAndGet();
    active.set(true);
    loadFailureActive = false;

    plugin.getLogger().info(
        "[RankTicker] started intervalSec="
            + intervalSec
            + " (DB reads off main thread)"
    );

    task =
        Bukkit.getScheduler()
            .runTaskTimer(
                plugin,
                () -> tick(generation),
                20L,
                intervalSec * 20L
            );
  }

  public void stop() {
    active.set(false);
    lifecycleGeneration.incrementAndGet();

    if (task != null) {
      task.cancel();
      task = null;
    }
  }

  /**
   * Main-thread heartbeat only. No JDBC is allowed here.
   */
  private void tick(long generation) {
    if (!isCurrentLifecycle(generation)) {
      return;
    }

    if (Bukkit.getOnlinePlayers().isEmpty()) {
      return;
    }

    Mode requestedMode = mode;

    scheduleLoad(generation, requestedMode);
    advanceModeForNextTick();
  }

  /**
   * Schedules one coalesced off-thread DB read.
   */
  private void scheduleLoad(long generation, Mode requestedMode) {
    if (!loadInFlight.compareAndSet(false, true)) {
      return;
    }

    try {
      Bukkit.getScheduler()
          .runTaskAsynchronously(
              plugin,
              () -> {
                LoadResult result;

                try {
                  List<RankingQueryService.RankingEntry> entries =
                      new JdbcLeaderboardSnapshotLoader(databaseSettings)
                          .loadEntries(toWindow(requestedMode));

                  result =
                      new LoadResult(
                          requestedMode,
                          toRows(entries, 3),
                          null
                      );
                } catch (Throwable failure) {
                  result =
                      new LoadResult(
                          requestedMode,
                          List.of(),
                          failure
                      );
                } finally {
                  loadInFlight.set(false);
                }

                if (!isCurrentLifecycle(generation)) {
                  return;
                }

                LoadResult completed = result;

                try {
                  Bukkit.getScheduler()
                      .runTask(
                          plugin,
                          () ->
                              finishLoad(
                                  generation,
                                  completed
                              )
                      );
                } catch (RuntimeException schedulingFailure) {
                  if (isCurrentLifecycle(generation)) {
                    plugin
                        .getLogger()
                        .warning(
                            "[RankTicker] could not schedule main-thread "
                                + "ranking delivery: "
                                + schedulingFailure.getMessage()
                        );
                  }
                }
              }
          );
    } catch (RuntimeException schedulingFailure) {
      loadInFlight.set(false);

      if (isCurrentLifecycle(generation)) {
        plugin
            .getLogger()
            .warning(
                "[RankTicker] could not schedule asynchronous ranking load: "
                    + schedulingFailure.getMessage()
            );
      }
    }
  }

  /**
   * Main-thread publication/render boundary.
   */
  private void finishLoad(long generation, LoadResult result) {
    if (!isCurrentLifecycle(generation)) {
      return;
    }

    if (result.failure() != null) {
      if (!loadFailureActive) {
        String detail =
            result.failure().getMessage() == null
                ? result.failure().getClass().getSimpleName()
                : result.failure().getMessage();

        plugin
            .getLogger()
            .warning(
                "[RankTicker] ranking DB unavailable; retaining the last rendered state: "
                    + detail
            );
        loadFailureActive = true;
      }

      return;
    }

    if (loadFailureActive) {
      plugin.getLogger().info("[RankTicker] ranking DB recovered.");
      loadFailureActive = false;
    }

    int digest = digest(result.rows(), result.mode());
    Integer previousDigest =
        lastDigestByMode.get(result.mode());

    boolean changed =
        previousDigest != null
            && digest != previousDigest;

    renderSidebar(result.rows(), result.mode());

    lastDigestByMode.put(
        result.mode(),
        digest
    );

    if (changed) {
      notifyLeaderboardUpdatedChat(result.mode());
    }
  }

  private boolean isCurrentLifecycle(long generation) {
    // This method is called from both main and async threads, so keep it
    // strictly Java-only. onDisable() revokes authority before teardown.
    return active.get()
        && lifecycleGeneration.get() == generation;
  }

  private void advanceModeForNextTick() {
    if (plugin.isGameRunning()) {
      toggleCounter += intervalSec;

      if (toggleCounter >= 10) {
        toggleCounter = 0;

        if (mode == Mode.WEEKLY) {
          mode = Mode.ALLTIME;
        } else if (mode == Mode.ALLTIME) {
          mode = Mode.MONTHLY;
        } else {
          mode = Mode.WEEKLY;
        }
      }
    } else {
      toggleCounter = 0;
      mode = Mode.WEEKLY;
    }
  }

  private RankingQueryService.Window toWindow(Mode requestedMode) {
    return switch (requestedMode) {
      case WEEKLY -> RankingQueryService.Window.WEEKLY;
      case ALLTIME -> RankingQueryService.Window.ALL_TIME;
      case MONTHLY -> RankingQueryService.Window.MONTHLY;
    };
  }

  private List<Row> toRows(
      List<RankingQueryService.RankingEntry> entries,
      int limit
  ) {
    int size = Math.min(
        Math.max(0, limit),
        entries.size()
    );

    List<Row> rows = new ArrayList<>(size);

    for (int i = 0; i < size; i++) {
      RankingQueryService.RankingEntry entry =
          entries.get(i);

      rows.add(
          new Row(
              entry.playerName(),
              entry.score(),
              entry.time(),
              entry.languageCode()
          )
      );
    }

    return List.copyOf(rows);
  }

  private void renderSidebar(
      List<Row> top,
      Mode mode
  ) {
    ScoreboardManager mgr =
        Bukkit.getScoreboardManager();

    if (mgr == null) {
      return;
    }

    for (Player p : Bukkit.getOnlinePlayers()) {
      String lang = "ja";

      try {
        if (plugin.getPlayerLanguageStore() != null) {
          lang =
              plugin
                  .getPlayerLanguageStore()
                  .getLang(
                      p,
                      plugin
                          .getConfig()
                          .getString(
                              "language.default",
                              "ja"
                          )
                  );
        } else {
          lang =
              plugin
                  .getConfig()
                  .getString(
                      "language.default",
                      "ja"
                  );
        }
      } catch (Throwable ignored) {
      }

      String title =
          (mode == Mode.WEEKLY)
              ? ChatColor.GOLD
                  + plugin
                      .getI18n()
                      .tr(
                          lang,
                          "rankTicker.weeklyTitle"
                      )
              : (mode == Mode.ALLTIME)
                  ? ChatColor.GOLD
                      + plugin
                          .getI18n()
                          .tr(
                              lang,
                              "rankTicker.allTimeTitle"
                          )
                  : ChatColor.GOLD
                      + plugin
                          .getI18n()
                          .tr(
                              lang,
                              "rankTicker.monthlyTitle"
                          );

      Scoreboard sb =
          mgr.getNewScoreboard();

      Objective obj =
          sb.registerNewObjective(
              "tr_rank",
              "dummy",
              title
          );

      obj.setDisplaySlot(
          DisplaySlot.SIDEBAR
      );

      int scoreLine = 15;

      obj.getScore(
              ChatColor.DARK_GRAY + " "
          )
          .setScore(scoreLine--);

      int rank = 1;

      for (Row r : top) {
        String rowLang =
            r.langCode() == null
                    || r.langCode().isBlank()
                ? "JA"
                : r.langCode()
                    .toUpperCase(Locale.ROOT);

        String line =
            ChatColor.AQUA
                + "#"
                + rank
                + " "
                + ChatColor.WHITE
                + trim(r.name(), 12)
                + " "
                + ChatColor.DARK_GRAY
                + "- "
                + ChatColor.GOLD
                + r.score()
                + " "
                + ChatColor.GRAY
                + "("
                + rowLang
                + ")";

        line = makeUnique(line, rank);

        obj.getScore(line)
            .setScore(scoreLine--);

        rank++;

        if (rank > 3 || scoreLine <= 1) {
          break;
        }
      }

      obj.getScore(
              ChatColor.DARK_GRAY + "  "
          )
          .setScore(scoreLine--);

      String footer =
          plugin
              .getI18n()
              .tr(
                  lang,
                  "rankTicker.footerHint"
              );

      obj.getScore(
              ChatColor.GRAY + footer
          )
          .setScore(1);

      p.setScoreboard(sb);
    }
  }

  private void notifyLeaderboardUpdatedChat(
      Mode mode
  ) {
    for (Player p : Bukkit.getOnlinePlayers()) {
      String lang =
          plugin
              .getConfig()
              .getString(
                  "language.default",
                  "ja"
              );

      try {
        if (plugin.getPlayerLanguageStore() != null) {
          String saved =
              plugin
                  .getPlayerLanguageStore()
                  .getLang(p, lang);

          if (saved != null
              && !saved.isBlank()) {
            lang = saved;
          }
        }
      } catch (Throwable ignored) {
      }

      String which =
          (mode == Mode.WEEKLY)
              ? plugin
                  .getI18n()
                  .tr(
                      lang,
                      "ui.rankTicker.mode.weekly"
                  )
              : (mode == Mode.ALLTIME)
                  ? plugin
                      .getI18n()
                      .tr(
                          lang,
                          "ui.rankTicker.mode.allTime"
                      )
                  : plugin
                      .getI18n()
                      .tr(
                          lang,
                          "ui.rankTicker.mode.monthly"
                      );

      p.sendMessage(
          ChatColor.AQUA
              + plugin
                  .getI18n()
                  .tr(
                      lang,
                      "ui.rankTicker.updated"
                  )
                  .replace(
                      "{which}",
                      which
                  )
      );

      p.playSound(
          p.getLocation(),
          Sound.UI_TOAST_IN,
          SoundCategory.PLAYERS,
          0.25f,
          1.3f
      );
    }
  }

  private int digest(
      List<Row> rows,
      Mode mode
  ) {
    int h = Objects.hash(mode);

    for (Row r : rows) {
      h =
          31 * h
              + Objects.hash(
                  r.name(),
                  r.score(),
                  r.time(),
                  safe(r.langCode())
              );
    }

    return h;
  }

  private String makeUnique(
      String value,
      int rank
  ) {
    ChatColor[] uniqueSuffixes =
        new ChatColor[] {
          ChatColor.BLACK,
          ChatColor.DARK_BLUE,
          ChatColor.DARK_GREEN,
          ChatColor.DARK_AQUA
        };

    return value
        + uniqueSuffixes[
            Math.min(
                rank,
                uniqueSuffixes.length - 1
            )
        ];
  }

  private String trim(
      String value,
      int max
  ) {
    if (value == null) {
      return "unknown";
    }

    if (value.length() <= max) {
      return value;
    }

    return value.substring(0, max);
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private record Row(
      String name,
      int score,
      long time,
      String langCode
  ) {
  }
}
