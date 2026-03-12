package plugin.rank;

import plugin.TreasureRunMultiChestPlugin;

import java.sql.*;
import java.util.UUID;

/**
 * season_scores（weekly）と alltime_scores を「同一トランザクション」で加算
 */
public class SeasonScoreRepository {

  private final TreasureRunMultiChestPlugin plugin;

  public SeasonScoreRepository(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  public void addWeeklyAndAllTime(
      long seasonId,
      UUID uuid,
      String name,
      int addScore,
      int addWins,
      Long bestTimeMsOrNull,
      String langCode
  ) throws SQLException {

    Connection con = plugin.getConnection();
    if (con == null) throw new SQLException("MySQL connection is null");

    boolean oldAutoCommit = con.getAutoCommit();
    con.setAutoCommit(false);

    final String SQL_WEEKLY =
        "INSERT INTO season_scores (season_id, uuid, name, score, wins, best_time_ms, lang_code) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "  name = VALUES(name), " +
            "  score = score + VALUES(score), " +
            "  wins = wins + VALUES(wins), " +
            "  best_time_ms = CASE " +
            "    WHEN VALUES(best_time_ms) IS NULL THEN best_time_ms " +
            "    WHEN best_time_ms IS NULL THEN VALUES(best_time_ms) " +
            "    WHEN VALUES(best_time_ms) < best_time_ms THEN VALUES(best_time_ms) " +
            "    ELSE best_time_ms " +
            "  END, " +
            "  lang_code = VALUES(lang_code)";

    final String SQL_ALLTIME =
        "INSERT INTO alltime_scores (uuid, name, score, wins, best_time_ms, lang_code) " +
            "VALUES (?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "  name = VALUES(name), " +
            "  score = score + VALUES(score), " +
            "  wins = wins + VALUES(wins), " +
            "  best_time_ms = CASE " +
            "    WHEN VALUES(best_time_ms) IS NULL THEN best_time_ms " +
            "    WHEN best_time_ms IS NULL THEN VALUES(best_time_ms) " +
            "    WHEN VALUES(best_time_ms) < best_time_ms THEN VALUES(best_time_ms) " +
            "    ELSE best_time_ms " +
            "  END, " +
            "  lang_code = VALUES(lang_code)";

    try (PreparedStatement ps1 = con.prepareStatement(SQL_WEEKLY);
         PreparedStatement ps2 = con.prepareStatement(SQL_ALLTIME)) {

      // weekly
      ps1.setLong(1, seasonId);
      ps1.setString(2, uuid.toString());
      ps1.setString(3, name);
      ps1.setInt(4, addScore);
      ps1.setInt(5, addWins);
      if (bestTimeMsOrNull == null) ps1.setNull(6, Types.BIGINT);
      else ps1.setLong(6, bestTimeMsOrNull);
      ps1.setString(7, (langCode == null || langCode.isBlank()) ? "ja" : langCode);
      ps1.executeUpdate();

      // alltime
      ps2.setString(1, uuid.toString());
      ps2.setString(2, name);
      ps2.setInt(3, addScore);
      ps2.setInt(4, addWins);
      if (bestTimeMsOrNull == null) ps2.setNull(5, Types.BIGINT);
      else ps2.setLong(5, bestTimeMsOrNull);
      ps2.setString(6, (langCode == null || langCode.isBlank()) ? "ja" : langCode);
      ps2.executeUpdate();

      con.commit();
    } catch (SQLException e) {
      try { con.rollback(); } catch (SQLException ignored) {}
      throw e;
    } finally {
      try { con.setAutoCommit(oldAutoCommit); } catch (SQLException ignored) {}
    }
  }
}
