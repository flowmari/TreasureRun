package plugin.rank;

import plugin.TreasureRunMultiChestPlugin;

import java.sql.*;
import java.time.*;
import java.time.temporal.WeekFields;

/**
 * seasons テーブルから「今週のWEEKLY season_id」を取得/なければ作成
 */
public class SeasonRepository {

  private final TreasureRunMultiChestPlugin plugin;

  public SeasonRepository(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  /**
   * Existing shared-connection compatibility boundary.
   * New asynchronous terminal persistence must use the explicit Connection overload.
   */
  public long getOrCreateCurrentWeeklySeasonId() throws SQLException {
    Connection connection = plugin.getConnection();
    if (connection == null) {
      throw new SQLException("MySQL connection is null");
    }

    return getOrCreateCurrentWeeklySeasonId(
        connection,
        Instant.now(),
        0
    );
  }

  /**
   * Operation-owned JDBC boundary used by DB-H3A.
   */
  public static long getOrCreateCurrentWeeklySeasonId(
      Connection connection,
      Instant occurredAt,
      int queryTimeoutSeconds
  ) throws SQLException {
    if (connection == null) {
      throw new SQLException("MySQL connection is null");
    }

    ZonedDateTime now =
        ZonedDateTime.ofInstant(
            occurredAt == null ? Instant.now() : occurredAt,
            ZoneId.of("Asia/Tokyo")
        );

    WeekFields fields = WeekFields.ISO;
    int year = now.get(fields.weekBasedYear());
    int week = now.get(fields.weekOfWeekBasedYear());

    LocalDate monday = now.toLocalDate().with(fields.dayOfWeek(), 1);
    LocalDate nextMonday = monday.plusDays(7);

    Timestamp startsAt = Timestamp.valueOf(monday.atStartOfDay());
    Timestamp endsAt = Timestamp.valueOf(nextMonday.atStartOfDay());

    final String selectSql =
        "SELECT id FROM seasons "
            + "WHERE season_type=? AND year=? AND week=? "
            + "LIMIT 1";

    try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
      applyTimeout(statement, queryTimeoutSeconds);
      statement.setString(1, "WEEKLY");
      statement.setInt(2, year);
      statement.setInt(3, week);

      try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {
          return resultSet.getLong("id");
        }
      }
    }

    final String insertSql =
        "INSERT INTO seasons "
            + "(season_type, year, week, starts_at, ends_at) "
            + "VALUES (?, ?, ?, ?, ?)";

    try (PreparedStatement statement =
             connection.prepareStatement(
                 insertSql,
                 Statement.RETURN_GENERATED_KEYS
             )) {
      applyTimeout(statement, queryTimeoutSeconds);
      statement.setString(1, "WEEKLY");
      statement.setInt(2, year);
      statement.setInt(3, week);
      statement.setTimestamp(4, startsAt);
      statement.setTimestamp(5, endsAt);

      try {
        statement.executeUpdate();
      } catch (SQLException ignored) {
        // Another server/thread may have created the same weekly season.
      }

      try (ResultSet keys = statement.getGeneratedKeys()) {
        if (keys.next()) {
          return keys.getLong(1);
        }
      }
    }

    try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
      applyTimeout(statement, queryTimeoutSeconds);
      statement.setString(1, "WEEKLY");
      statement.setInt(2, year);
      statement.setInt(3, week);

      try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {
          return resultSet.getLong("id");
        }
      }
    }

    throw new SQLException(
        "Failed to getOrCreate season_id for WEEKLY "
            + year
            + "-W"
            + week
    );
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
