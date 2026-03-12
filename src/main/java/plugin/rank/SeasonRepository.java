package plugin.rank;

import plugin.TreasureRunMultiChestPlugin;

import java.sql.*;
import java.time.*;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * seasons テーブルから「今週のWEEKLY season_id」を取得/なければ作成
 */
public class SeasonRepository {

  private final TreasureRunMultiChestPlugin plugin;

  public SeasonRepository(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  /** 今週の weekly season_id を返す（なければ INSERT して返す） */
  public long getOrCreateCurrentWeeklySeasonId() throws SQLException {
    // 週の定義：ISO週（月曜開始）
    ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"));
    WeekFields wf = WeekFields.ISO;
    int year = now.get(wf.weekBasedYear());
    int week = now.get(wf.weekOfWeekBasedYear());

    LocalDate monday = now.toLocalDate().with(wf.dayOfWeek(), 1); // Monday
    LocalDate nextMonday = monday.plusDays(7);

    Timestamp startsAt = Timestamp.valueOf(monday.atStartOfDay());
    Timestamp endsAt = Timestamp.valueOf(nextMonday.atStartOfDay());

    Connection con = plugin.getConnection();
    if (con == null) throw new SQLException("MySQL connection is null");

    // 1) 既存取得
    String selectSql =
        "SELECT id FROM seasons " +
        "WHERE season_type=? AND year=? AND week=? " +
        "LIMIT 1";

    try (PreparedStatement ps = con.prepareStatement(selectSql)) {
      ps.setString(1, "WEEKLY");
      ps.setInt(2, year);
      ps.setInt(3, week);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getLong("id");
      }
    }

    // 2) なければ作成（並行実行でも安全にする）
    String insertSql =
        "INSERT INTO seasons (season_type, year, week, starts_at, ends_at) " +
        "VALUES (?, ?, ?, ?, ?)";

    try (PreparedStatement ps = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
      ps.setString(1, "WEEKLY");
      ps.setInt(2, year);
      ps.setInt(3, week);
      ps.setTimestamp(4, startsAt);
      ps.setTimestamp(5, endsAt);

      try {
        ps.executeUpdate();
      } catch (SQLException e) {
        // すでに別スレッド/別鯖が作った可能性 → 取り直す
      }

      try (ResultSet keys = ps.getGeneratedKeys()) {
        if (keys.next()) return keys.getLong(1);
      }
    }

    // 3) 最終的に取り直し
    try (PreparedStatement ps = con.prepareStatement(selectSql)) {
      ps.setString(1, "WEEKLY");
      ps.setInt(2, year);
      ps.setInt(3, week);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getLong("id");
      }
    }

    throw new SQLException("Failed to getOrCreate season_id for WEEKLY " + year + "-W" + week);
  }
}
