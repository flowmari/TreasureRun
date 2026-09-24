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

  private static final int MYSQL_DUPLICATE_KEY_ERROR = 1062;

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

    /*
     * V2 defines weekly/monthly season identity with season_key included in
     * the unique tuple. New WEEKLY rows must therefore always carry a
     * canonical non-null key. Keep the lookup by type/year/week for backwards
     * compatibility with any legacy WEEKLY row created before this invariant
     * was enforced.
     */
    final String seasonKey =
        String.format(Locale.ROOT, "%04d-W%02d", year, week);

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
            + "(season_type, year, week, season_key, starts_at, ends_at) "
            + "VALUES (?, ?, ?, ?, ?, ?)";

    try (PreparedStatement statement =
             connection.prepareStatement(
                 insertSql,
                 Statement.RETURN_GENERATED_KEYS
             )) {
      applyTimeout(statement, queryTimeoutSeconds);
      statement.setString(1, "WEEKLY");
      statement.setInt(2, year);
      statement.setInt(3, week);
      statement.setString(4, seasonKey);
      statement.setTimestamp(5, startsAt);
      statement.setTimestamp(6, endsAt);

      boolean inserted = false;

      try {
        int updatedRows = statement.executeUpdate();

        if (updatedRows != 1) {
          throw new SQLException(
              "Expected one weekly season row to be inserted, got "
                  + updatedRows
          );
        }

        inserted = true;
      } catch (SQLException exception) {
        /*
         * Only duplicate-key means another actor won the weekly-season
         * creation race. Never reinterpret arbitrary SQL failures as
         * successful concurrent creation.
         *
         * A failed INSERT must never contribute generated keys. MySQL may
         * consume an AUTO_INCREMENT value while rejecting a duplicate row,
         * so generated keys are meaningful only after a successful INSERT.
         */
        if (exception.getErrorCode() != MYSQL_DUPLICATE_KEY_ERROR) {
          throw exception;
        }
      }

      if (inserted) {
        try (ResultSet keys = statement.getGeneratedKeys()) {
          if (keys.next()) {
            return keys.getLong(1);
          }
        }
      }
    }

    /*
     * The transaction may already own a REPEATABLE READ snapshot from the
     * first plain SELECT. If another transaction committed the same weekly
     * season before our INSERT, the duplicate-key check sees that row while
     * another plain SELECT can still read the older snapshot.
     *
     * FOR UPDATE is intentionally used only for this post-race resolution:
     * it is a current/locking read and therefore resolves the committed
     * winner inside this caller-owned transaction.
     */
    try (PreparedStatement statement =
             connection.prepareStatement(selectSql + " FOR UPDATE")) {
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
