package plugin.rank;

import plugin.TreasureRunMultiChestPlugin;
import plugin.rank.event.GameResultRecorded;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Executes the real ranking repository against disposable MySQL 8.
 *
 * <p>The plugin object is mocked only as the existing connection-provider
 * boundary. JDBC statements, transactions, constraints, migrations and
 * persisted rows are exercised against a real MySQL database.</p>
 */
@Testcontainers
class SeasonScoreRepositoryMySqlIntegrationTest {

  private static final UUID EVENT_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000001");

  private static final UUID SECOND_EVENT_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000002");

  private static final UUID PLAYER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000123");

  private static final UUID ROLLBACK_PLAYER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000999");

  private static final long SEASON_ID = 42L;

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.36")
          .withDatabaseName("treasurerun_integration")
          .withUsername("treasurerun")
          .withPassword("treasurerun");

  @BeforeAll
  static void applyMigrations() throws Exception {
    try (Connection connection = openConnection()) {
      executeMigration(connection, "db/migration/V1__create_ranking_tables.sql");
      executeMigration(connection, "db/migration/V2__support_monthly_seasons.sql");
      executeMigration(connection, "db/migration/V3__create_transactional_outbox.sql");
    }
  }

  @BeforeEach
  void resetTables() throws Exception {
    try (Connection connection = openConnection();
         Statement statement = connection.createStatement()) {

      statement.executeUpdate("DELETE FROM outbox_events");
      statement.executeUpdate("DELETE FROM season_scores");
      statement.executeUpdate("DELETE FROM alltime_scores");
      statement.executeUpdate("DELETE FROM seasons");

      statement.executeUpdate(
          "INSERT INTO seasons " +
              "(id, season_type, year, week, season_key, starts_at, ends_at) VALUES " +
              "(" + SEASON_ID + ", 'WEEKLY', 2026, 21, '2026-W21', " +
              "'2026-05-18 00:00:00', '2026-05-25 00:00:00')"
      );
    }
  }

  @Test
  void commitsOutboxAndRankingAggregatesThroughRealJdbc() throws Exception {
    try (Connection connection = openConnection()) {
      SeasonScoreRepository repository = repositoryUsing(connection);

      assertTrue(repository.addWeeklyAndAllTime(successEvent(EVENT_ID, PLAYER_ID)));

      assertEquals(1, intQuery(connection,
          "SELECT COUNT(*) FROM outbox_events WHERE event_id = '" + EVENT_ID + "'"));
      assertEquals(100, intQuery(connection,
          "SELECT score FROM season_scores WHERE season_id = " + SEASON_ID +
              " AND uuid = '" + PLAYER_ID + "'"));
      assertEquals(1, intQuery(connection,
          "SELECT wins FROM season_scores WHERE season_id = " + SEASON_ID +
              " AND uuid = '" + PLAYER_ID + "'"));
      assertEquals(100, intQuery(connection,
          "SELECT score FROM alltime_scores WHERE uuid = '" + PLAYER_ID + "'"));
      assertEquals(1, intQuery(connection,
          "SELECT JSON_VALID(payload_json) FROM outbox_events WHERE event_id = '" + EVENT_ID + "'"));
    }
  }

  @Test
  void rejectsDuplicateEventIdWithoutDoubleCountingThroughRealJdbc() throws Exception {
    try (Connection connection = openConnection()) {
      SeasonScoreRepository repository = repositoryUsing(connection);
      GameResultRecorded event = successEvent(EVENT_ID, PLAYER_ID);

      assertTrue(repository.addWeeklyAndAllTime(event));
      assertFalse(repository.addWeeklyAndAllTime(event));

      assertEquals(1, intQuery(connection,
          "SELECT COUNT(*) FROM outbox_events WHERE event_id = '" + EVENT_ID + "'"));
      assertEquals(100, intQuery(connection,
          "SELECT score FROM season_scores WHERE season_id = " + SEASON_ID +
              " AND uuid = '" + PLAYER_ID + "'"));
      assertEquals(100, intQuery(connection,
          "SELECT score FROM alltime_scores WHERE uuid = '" + PLAYER_ID + "'"));
    }
  }

  @Test
  void rollsBackOutboxAndWeeklyRowsWhenRealMySqlRejectsAllTimeWrite() throws Exception {
    try (Connection connection = openConnection();
         Statement statement = connection.createStatement()) {

      statement.executeUpdate(
          "ALTER TABLE alltime_scores ADD CONSTRAINT reject_rollback_player " +
              "CHECK (uuid <> '" + ROLLBACK_PLAYER_ID + "')"
      );

      try {
        SeasonScoreRepository repository = repositoryUsing(connection);

        assertThrows(SQLException.class, () ->
            repository.addWeeklyAndAllTime(successEvent(SECOND_EVENT_ID, ROLLBACK_PLAYER_ID)));

        assertEquals(0, intQuery(connection,
            "SELECT COUNT(*) FROM outbox_events WHERE event_id = '" + SECOND_EVENT_ID + "'"));
        assertEquals(0, intQuery(connection,
            "SELECT COUNT(*) FROM season_scores WHERE season_id = " + SEASON_ID +
                " AND uuid = '" + ROLLBACK_PLAYER_ID + "'"));
        assertEquals(0, intQuery(connection,
            "SELECT COUNT(*) FROM alltime_scores WHERE uuid = '" + ROLLBACK_PLAYER_ID + "'"));
      } finally {
        statement.executeUpdate(
            "ALTER TABLE alltime_scores DROP CHECK reject_rollback_player"
        );
      }
    }
  }

  private static Connection openConnection() throws SQLException {
    return DriverManager.getConnection(
        MYSQL.getJdbcUrl(),
        MYSQL.getUsername(),
        MYSQL.getPassword()
    );
  }

  private static SeasonScoreRepository repositoryUsing(Connection connection) throws Exception {
    TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
    when(plugin.getConnection()).thenReturn(connection);
    return new SeasonScoreRepository(plugin);
  }

  private static GameResultRecorded successEvent(UUID eventId, UUID playerId) {
    return GameResultRecorded.create(
        eventId,
        Instant.parse("2026-05-23T00:00:00Z"),
        SEASON_ID,
        playerId,
        "flowmari",
        "SUCCESS",
        100,
        1,
        12345L,
        "en"
    );
  }

  private static int intQuery(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(sql)) {

      assertTrue(resultSet.next(), "Expected one result row for SQL: " + sql);
      return resultSet.getInt(1);
    }
  }

  private static void executeMigration(Connection connection, String resourcePath) throws Exception {
    String sql;

    try (InputStream stream = SeasonScoreRepositoryMySqlIntegrationTest.class
        .getClassLoader()
        .getResourceAsStream(resourcePath)) {

      sql = new String(
          Objects.requireNonNull(stream, "Missing migration resource: " + resourcePath)
              .readAllBytes(),
          StandardCharsets.UTF_8
      );
    }

    String executableSql = Arrays.stream(sql.split("\\R"))
        .filter(line -> !line.trim().startsWith("--"))
        .collect(Collectors.joining("\n"));

    for (String statementSql : executableSql.split(";")) {
      if (!statementSql.isBlank()) {
        try (Statement statement = connection.createStatement()) {
          statement.execute(statementSql.trim());
        }
      }
    }
  }
}
