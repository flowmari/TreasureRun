package plugin.rank;

import plugin.TreasureRunMultiChestPlugin;
import plugin.rank.event.GameResultRecorded;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;

/**
 * Persists ranking aggregate changes and a durable outbox record atomically.
 *
 * <p>The outbox insert happens before ranking increments. A duplicate event_id
 * therefore stops a repeated terminal callback before weekly or all-time
 * ranking totals can be incremented again.</p>
 *
 * <p>The legacy public wrapper owns its transaction. DB-H3A may instead use
 * the caller-owned transaction method so raw score history, outbox idempotency,
 * and ranking aggregates can participate in one operation-owned transaction.</p>
 */
public class SeasonScoreRepository {

  private static final int MYSQL_DUPLICATE_KEY_ERROR = 1062;

  private final TreasureRunMultiChestPlugin plugin;

  public SeasonScoreRepository(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  /**
   * Existing shared-connection compatibility boundary.
   */
  public boolean addWeeklyAndAllTime(
      GameResultRecorded event
  ) throws SQLException {
    Connection connection = plugin.getConnection();
    if (connection == null) {
      throw new SQLException("MySQL connection is null");
    }

    return addWeeklyAndAllTime(connection, event, 0);
  }

  /**
   * Operation-owned connection boundary used by DB-H3A.
   *
   * @return true if the event and ranking deltas were newly committed;
   *         false if that event id had already been committed.
   */
  public static boolean addWeeklyAndAllTime(
      Connection connection,
      GameResultRecorded event,
      int queryTimeoutSeconds
  ) throws SQLException {
    if (connection == null) {
      throw new SQLException("MySQL connection is null");
    }

    boolean oldAutoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);

    try {
      boolean applied = addWeeklyAndAllTimeInTransaction(
          connection,
          event,
          queryTimeoutSeconds
      );

      if (!applied) {
        connection.rollback();
        return false;
      }

      connection.commit();
      return true;
    } catch (SQLException exception) {
      try {
        connection.rollback();
      } catch (SQLException ignored) {
        // Preserve the original persistence exception.
      }
      throw exception;
    } finally {
      try {
        connection.setAutoCommit(oldAutoCommit);
      } catch (SQLException ignored) {
        // Preserve the original persistence result.
      }
    }
  }

  /**
   * Applies the idempotency claim and ranking deltas inside a transaction owned
   * by the caller.
   *
   * <p>This method deliberately does not change auto-commit and does not call
   * commit or rollback. A duplicate event id returns false so the caller can
   * roll back every write in the terminal operation, including raw history.</p>
   */
  public static boolean addWeeklyAndAllTimeInTransaction(
      Connection connection,
      GameResultRecorded event,
      int queryTimeoutSeconds
  ) throws SQLException {
    if (connection == null) {
      throw new SQLException("MySQL connection is null");
    }

    final String SQL_OUTBOX =
        "INSERT INTO outbox_events "
            + "(event_id, aggregate_type, aggregate_id, event_type, outcome, payload_json, occurred_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    final String SQL_WEEKLY =
        "INSERT INTO season_scores "
            + "(season_id, uuid, name, score, wins, best_time_ms, lang_code) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE "
            + "name = VALUES(name), "
            + "score = score + VALUES(score), "
            + "wins = wins + VALUES(wins), "
            + "best_time_ms = CASE "
            + "WHEN VALUES(best_time_ms) IS NULL THEN best_time_ms "
            + "WHEN best_time_ms IS NULL THEN VALUES(best_time_ms) "
            + "WHEN VALUES(best_time_ms) < best_time_ms THEN VALUES(best_time_ms) "
            + "ELSE best_time_ms END, "
            + "lang_code = VALUES(lang_code)";

    final String SQL_ALLTIME =
        "INSERT INTO alltime_scores "
            + "(uuid, name, score, wins, best_time_ms, lang_code) "
            + "VALUES (?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE "
            + "name = VALUES(name), "
            + "score = score + VALUES(score), "
            + "wins = wins + VALUES(wins), "
            + "best_time_ms = CASE "
            + "WHEN VALUES(best_time_ms) IS NULL THEN best_time_ms "
            + "WHEN best_time_ms IS NULL THEN VALUES(best_time_ms) "
            + "WHEN VALUES(best_time_ms) < best_time_ms THEN VALUES(best_time_ms) "
            + "ELSE best_time_ms END, "
            + "lang_code = VALUES(lang_code)";

    try (PreparedStatement outbox = connection.prepareStatement(SQL_OUTBOX);
         PreparedStatement weekly = connection.prepareStatement(SQL_WEEKLY);
         PreparedStatement allTime = connection.prepareStatement(SQL_ALLTIME)) {

      applyTimeout(outbox, queryTimeoutSeconds);
      applyTimeout(weekly, queryTimeoutSeconds);
      applyTimeout(allTime, queryTimeoutSeconds);

      outbox.setString(1, event.eventId().toString());
      outbox.setString(2, event.aggregateType());
      outbox.setString(3, event.aggregateId());
      outbox.setString(4, event.eventType());
      outbox.setString(5, event.outcome());
      outbox.setString(6, event.payloadJson());
      outbox.setTimestamp(7, Timestamp.from(event.occurredAt()));

      try {
        outbox.executeUpdate();
      } catch (SQLException exception) {
        if (exception.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR) {
          return false;
        }
        throw exception;
      }

      weekly.setLong(1, event.seasonId());
      weekly.setString(2, event.playerUuid().toString());
      weekly.setString(3, event.playerName());
      weekly.setInt(4, event.scoreDelta());
      weekly.setInt(5, event.winDelta());
      if (event.bestTimeMs() == null) {
        weekly.setNull(6, Types.BIGINT);
      } else {
        weekly.setLong(6, event.bestTimeMs());
      }
      weekly.setString(7, event.langCode());
      weekly.executeUpdate();

      allTime.setString(1, event.playerUuid().toString());
      allTime.setString(2, event.playerName());
      allTime.setInt(3, event.scoreDelta());
      allTime.setInt(4, event.winDelta());
      if (event.bestTimeMs() == null) {
        allTime.setNull(5, Types.BIGINT);
      } else {
        allTime.setLong(5, event.bestTimeMs());
      }
      allTime.setString(6, event.langCode());
      allTime.executeUpdate();

      return true;
    }
  }

  private static void applyTimeout(
      PreparedStatement statement,
      int queryTimeoutSeconds
  ) throws SQLException {
    if (queryTimeoutSeconds > 0) {
      statement.setQueryTimeout(queryTimeoutSeconds);
    }
  }
}
