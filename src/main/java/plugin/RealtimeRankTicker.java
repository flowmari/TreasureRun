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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * RealtimeRankTicker (scoresテーブル版)
 * - Sidebar scoreboard に Weekly / All-time / Monthly TOP3 を表示
 * - 10秒ごとに Weekly / All-time / Monthly 切替（ゲーム中だけ）
 * - ランキング更新時はチャットで通知（ActionBar上書き回避）
 */
public class RealtimeRankTicker {

  private final TreasureRunMultiChestPlugin plugin;
  private final int intervalSec;

  private BukkitTask task;

  private int lastDigest = 0;

  // 表示モード（デフォルト weekly）
  private Mode mode = Mode.WEEKLY;
  private enum Mode { WEEKLY, ALLTIME, MONTHLY }

  // 10秒ごと切替用
  private int toggleCounter = 0;

  public RealtimeRankTicker(TreasureRunMultiChestPlugin plugin, int intervalSec, int topN, int tickerWidth) {
    this.plugin = plugin;
    this.intervalSec = Math.max(2, intervalSec);
  }

  public void start() {
    plugin.getLogger().info("[RankTicker] started intervalSec=" + intervalSec);
    stop();
    task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, intervalSec * 20L);
  }

  public void stop() {
    if (task != null) {
      task.cancel();
      task = null;
    }
  }

  private void tick() {
    if (!plugin.isEnabled()) return;
    if (Bukkit.getOnlinePlayers().isEmpty()) return;

    Connection conn = plugin.getConnection();
    if (conn == null) return;

    List<Row> top;
    try {
      if (mode == Mode.WEEKLY) {
        top = fetchWeeklyTopFromScores(conn, 3);
      } else if (mode == Mode.ALLTIME) {
        top = fetchAllTimeTopFromScores(conn, 3);
      } else {
        top = fetchMonthlyTopFromScores(conn, 3);
      }
    } catch (Throwable t) {
      plugin.getLogger().warning("[RankTicker] fetch failed: " + t.getMessage());
      return;
    }

    int digest = digest(top, mode);
    boolean changed = (digest != lastDigest);

    renderSidebar(top, mode);

    if (changed) {
      lastDigest = digest;
      notifyLeaderboardUpdatedChat(mode);
    }

    // ✅ 10秒ごとにモード切替（ゲーム中だけ）
    if (plugin.isGameRunning()) {
      toggleCounter += intervalSec;
      if (toggleCounter >= 10) {
        toggleCounter = 0;

        // ✅ 2種類版の「三項演算子」風を崩さず、3種類に拡張
        if (mode == Mode.WEEKLY) mode = Mode.ALLTIME;
        else if (mode == Mode.ALLTIME) mode = Mode.MONTHLY;
        else mode = Mode.WEEKLY;

      }
    } else {
      toggleCounter = 0;
      mode = Mode.WEEKLY; // ゲーム外は固定
    }
  }


  // =========================================================
  // DB fetch (scoresテーブル)
  // =========================================================

  private List<Row> fetchWeeklyTopFromScores(Connection conn, int limit) throws SQLException {
    String sql =
        "SELECT player_name, score, time, lang_code, played_at " +
            "FROM scores " +
            "WHERE played_at >= NOW() - INTERVAL 7 DAY " +   // ✅ 直近7日
            "ORDER BY score DESC, time ASC, id DESC " +
            "LIMIT ?";

    List<Row> list = new ArrayList<>();
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, limit);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) list.add(Row.fromScores(rs));
      }
    }
    return list;
  }

  private List<Row> fetchAllTimeTopFromScores(Connection conn, int limit) throws SQLException {
    String sql =
        "SELECT player_name, score, time, lang_code, played_at " +
            "FROM scores " +
            "ORDER BY score DESC, time ASC, id DESC " +
            "LIMIT ?";

    List<Row> list = new ArrayList<>();
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, limit);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) list.add(Row.fromScores(rs));
      }
    }
    return list;
  }

  // ✅ 追加：今月TOP
  private List<Row> fetchMonthlyTopFromScores(Connection conn, int limit) throws SQLException {
    String sql =
        "SELECT player_name, score, time, lang_code, played_at " +
            "FROM scores " +
            "WHERE YEAR(played_at) = YEAR(NOW()) " +
            "  AND MONTH(played_at) = MONTH(NOW()) " +
            "ORDER BY score DESC, time ASC, id DESC " +
            "LIMIT ?";

    List<Row> list = new ArrayList<>();
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, limit);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) list.add(Row.fromScores(rs));
      }
    }
    return list;
  }

  // =========================================================
  // Sidebar render
  // =========================================================

  private void renderSidebar(List<Row> top, Mode mode) {
    ScoreboardManager mgr = Bukkit.getScoreboardManager();
    if (mgr == null) return;

    // ✅ 2種類版の書き方（? :）を保ったまま3種類に拡張（switch不使用）
    String title =
        (mode == Mode.WEEKLY)
            ? (ChatColor.GOLD + "Weekly TreasureRun")
            : (mode == Mode.ALLTIME)
                ? (ChatColor.GOLD + "All-time TreasureRun")
                : (ChatColor.GOLD + "Monthly TreasureRun");

    for (Player p : Bukkit.getOnlinePlayers()) {
      Scoreboard sb = mgr.getNewScoreboard();
      Objective obj = sb.registerNewObjective("tr_rank", "dummy", title);
      obj.setDisplaySlot(DisplaySlot.SIDEBAR);

      int scoreLine = 15;

      // 空行
      obj.getScore(ChatColor.DARK_GRAY + " ").setScore(scoreLine--);

      // 1〜3位
      int rank = 1;
      for (Row r : top) {
        String lang = (r.langCode == null || r.langCode.isBlank())
            ? "JA"
            : r.langCode.toUpperCase(Locale.ROOT);

        String line =
            ChatColor.AQUA + "#" + rank + " " +
                ChatColor.WHITE + trim(r.name, 12) + " " +
                ChatColor.DARK_GRAY + "- " +
                ChatColor.GOLD + r.score + " " +
                ChatColor.GRAY + "(" + lang + ")";

        // 同一文字列があるとスコアボードが壊れるのでユニーク化
        line = makeUnique(line, rank);
        obj.getScore(line).setScore(scoreLine--);

        rank++;
        if (rank > 3) break;
        if (scoreLine <= 1) break;
      }

      // 空行
      obj.getScore(ChatColor.DARK_GRAY + "  ").setScore(scoreLine--);

// フッター（残す）
      obj.getScore(ChatColor.GRAY + "/gameRank weekly|all|monthly").setScore(1);

      p.setScoreboard(sb);
    }
  }

  private String makeUnique(String s, int rank) {
    ChatColor[] u = new ChatColor[]{ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA};
    return s + u[Math.min(rank, u.length - 1)];
  }

  private String trim(String s, int max) {
    if (s == null) return "unknown";
    if (s.length() <= max) return s;
    return s.substring(0, max);
  }

  // =========================================================
  // Notify (ActionBarではなくチャット)
  // =========================================================

  private void notifyLeaderboardUpdatedChat(Mode mode) {
    // ✅ 2種類版の style を崩さず3種類化
    String which =
        (mode == Mode.WEEKLY) ? "Weekly"
            : (mode == Mode.ALLTIME) ? "All-time"
                : "Monthly";

    for (Player p : Bukkit.getOnlinePlayers()) {
      p.sendMessage(ChatColor.AQUA + "🏁 Leaderboard Updated! " + ChatColor.GRAY + "(" + which + ")");
      p.playSound(p.getLocation(), Sound.UI_TOAST_IN, SoundCategory.PLAYERS, 0.25f, 1.3f);
    }
  }

  private int digest(List<Row> rows, Mode mode) {
    int h = Objects.hash(mode);
    for (Row r : rows) {
      h = 31 * h + Objects.hash(r.name, r.score, r.time, safe(r.langCode));
    }
    return h;
  }

  private String safe(String s) { return (s == null) ? "" : s; }

  private static class Row {
    String name;
    int score;
    long time;
    String langCode;

    static Row fromScores(ResultSet rs) throws SQLException {
      Row r = new Row();
      r.name = rs.getString("player_name");
      r.score = rs.getInt("score");
      r.time = rs.getLong("time");
      r.langCode = rs.getString("lang_code");
      return r;
    }
  }
}