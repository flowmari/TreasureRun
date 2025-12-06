package plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RealtimeRankTicker {

  private final TreasureRunMultiChestPlugin plugin;
  private final int intervalSec;
  private final int topN;
  private final int tickerWidth;

  private int taskId = -1;
  private int tickerTaskId = -1;

  private List<Row> lastTop = Collections.emptyList();
  private final AtomicBoolean tickerActive = new AtomicBoolean(false);
  private String marqueeBase = "";
  private int marqueeOffset = 0;

  // ✅ 追加：画面上部に出す BossBar
  private BossBar tickerBar = null;

  public RealtimeRankTicker(TreasureRunMultiChestPlugin plugin, int intervalSec, int topN, int tickerWidth) {
    this.plugin = plugin;
    this.intervalSec = Math.max(3, intervalSec);
    this.topN = Math.max(3, topN);
    this.tickerWidth = Math.max(16, tickerWidth);
  }

  public void start() {
    if (taskId != -1) return;

    // ✅ ランキング更新ポーリング
    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
      if (!plugin.rankDirty) return;
      plugin.rankDirty = false;

      Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        List<Row> top = fetchTop();
        if (top == null) return;

        boolean changed = !equalsRank(lastTop, top);
        lastTop = top;

        if (changed) {
          Bukkit.getScheduler().runTask(plugin, () -> {
            updateSidebar(top);
            startOrUpdateTicker(buildTickerLine(top));
          });
        }
      });
    }, 0L, intervalSec * 20L);

    // ✅ ティッカー（BossBarスクロール表示）
    tickerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
      if (!tickerActive.get()) return;
      if (marqueeBase.isEmpty()) return;

      String frame = marqueeFrame(marqueeBase, tickerWidth, marqueeOffset++);
      updateBossBar(frame);

    }, 0L, 10L); // ← 10tick (0.5秒)ごとにスクロール
  }

  public void stop() {
    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
    if (tickerTaskId != -1) {
      Bukkit.getScheduler().cancelTask(tickerTaskId);
      tickerTaskId = -1;
    }

    if (tickerBar != null) {
      tickerBar.removeAll();
      tickerBar = null;
    }
    tickerActive.set(false);
  }

  private List<Row> fetchTop() {
    String sql =
        "SELECT player_name, MAX(score) AS best, MIN(time) AS best_time " +
            "FROM scores GROUP BY player_name " +
            "ORDER BY best DESC, best_time ASC LIMIT ?";
    try (PreparedStatement ps = plugin.getConnection().prepareStatement(sql)) {
      ps.setInt(1, topN);
      try (ResultSet rs = ps.executeQuery()) {
        List<Row> list = new ArrayList<>();
        while (rs.next()) {
          list.add(new Row(
              rs.getString("player_name"),
              rs.getInt("best"),
              rs.getLong("best_time")
          ));
        }
        return list;
      }
    } catch (SQLException e) {
      plugin.getLogger().warning("[TreasureRun] fetchTop failed: " + e.getMessage());
      return null;
    }
  }

  private void updateSidebar(List<Row> top) {
    Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
    Objective obj = board.getObjective("TRank");

    if (obj == null) {
      obj = board.registerNewObjective("TRank", "dummy", ChatColor.AQUA + "TreasureRun TOP");
    }
    obj.setDisplaySlot(DisplaySlot.SIDEBAR);

    // 既存行クリア
    for (String entry : new HashSet<>(board.getEntries())) {
      board.resetScores(entry);
    }

    int place = 1;
    for (Row r : top) {
      String line =
          ChatColor.YELLOW + "#" + place + " " +
              ChatColor.WHITE + safeName(r.name) + " " +
              ChatColor.GOLD + r.score;

      if (line.length() > 40) line = line.substring(0, 40);

      Score s = obj.getScore(line);
      s.setScore(topN - place);
      place++;
    }

    Bukkit.getOnlinePlayers().forEach(p -> p.setScoreboard(board));
  }

  // =======================
  // ✅ BossBar スクロール更新
  // =======================
  private void updateBossBar(String frame) {
    if (tickerBar == null) {
      tickerBar = Bukkit.createBossBar(ChatColor.GOLD + frame, BarColor.YELLOW, BarStyle.SOLID);
      Bukkit.getOnlinePlayers().forEach(tickerBar::addPlayer);
    } else {
      tickerBar.setTitle(ChatColor.GOLD + frame);
    }
  }

  private void startOrUpdateTicker(String text) {
    marqueeBase = "   " + text + "   ";
    marqueeOffset = 0;
    tickerActive.set(true);
  }

  private String buildTickerLine(List<Row> top) {
    StringBuilder sb = new StringBuilder();
    sb.append("TOP").append(top.size()).append(" ");
    int i = 1;
    for (Row r : top) {
      if (i > 1) sb.append(" | ");
      sb.append("#").append(i).append(" ").append(safeName(r.name))
          .append("(").append(r.score).append(")");
      i++;
    }
    return sb.toString();
  }

  private String marqueeFrame(String base, int width, int offset) {
    if (base.length() < width)
      base += " ".repeat(width - base.length());

    int n = base.length();
    int pos = Math.floorMod(offset, n);

    String doubled = base + base;
    return doubled.substring(pos, pos + width);
  }

  private boolean equalsRank(List<Row> a, List<Row> b) {
    if (a.size() != b.size()) return false;
    for (int i = 0; i < a.size(); i++) {
      Row x = a.get(i);
      Row y = b.get(i);
      if (!Objects.equals(x.name, y.name)) return false;
      if (x.score != y.score) return false;
      if (x.time != y.time) return false;
    }
    return true;
  }

  private String safeName(String s) {
    if (s == null) return "?";
    return s.replaceAll("[§\\n\\r\\t]", "");
  }

  static class Row {
    final String name;
    final int score;
    final long time;

    Row(String n, int s, long t) {
      name = n;
      score = s;
      time = t;
    }
  }
}