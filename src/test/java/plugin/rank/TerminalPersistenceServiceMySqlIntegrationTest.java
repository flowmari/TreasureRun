package plugin.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import plugin.DatabaseRuntimeSettings;

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
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Testcontainers
class TerminalPersistenceServiceMySqlIntegrationTest {

  private static final UUID SUCCESS_EVENT_ID =
      UUID.fromString("30000000-0000-0000-0000-000000000001");

  private static final UUID SUCCESS_PLAYER_ID =
      UUID.fromString("30000000-0000-0000-0000-000000000101");

  private static final UUID ROLLBACK_EVENT_ID =
      UUID.fromString("30000000-0000-0000-0000-000000000002");

  private static final UUID ROLLBACK_PLAYER_ID =
      UUID.fromString("30000000-0000-0000-0000-000000000999");

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.36")
          .withDatabaseName("treasurerun_terminal_tx")
          .withUsername("treasurerun")
          .withPassword("treasurerun");

  @BeforeAll
  static void createSchema() throws Exception {
    try (Connection connection = openConnection()) {
      executeMigration(connection, "db/migration/V1__create_ranking_tables.sql");
      executeMigration(connection, "db/migration/V2__support_monthly_seasons.sql");
      executeMigration(connection, "db/migration/V3__create_transactional_outbox.sql");

      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            "CREATE TABLE IF NOT EXISTS scores ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "uuid VARCHAR(36) NOT NULL,"
                + "player_name VARCHAR(255) NOT NULL,"
                + "score INT NOT NULL,"
                + "`time` BIGINT NOT NULL,"
                + "difficulty VARCHAR(64) NOT NULL,"
                + "lang_code VARCHAR(32) NOT NULL,"
                + "played_at TIMESTAMP NOT NULL"
                + ") ENGINE=InnoDB"
        );
      }
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
      statement.executeUpdate("DELETE FROM scores");
    }
  }

  @Test
  void terminalSuccessAndDuplicateRetryAreAtomicThroughRealMySql()
      throws Exception {

    TerminalPersistenceService service =
        new TerminalPersistenceService(settings(), quietLogger());

    try {
      TerminalPersistenceService.TerminalWrite write =
          write(SUCCESS_EVENT_ID, SUCCESS_PLAYER_ID);

      TerminalPersistenceService.PersistenceResult first =
          service.submit(write)
              .toCompletableFuture()
              .get(15, TimeUnit.SECONDS);

      assertEquals(
          TerminalPersistenceService.Status.PERSISTED,
          first.status()
      );

      assertTerminalRows(
          SUCCESS_EVENT_ID,
          SUCCESS_PLAYER_ID,
          1,
          1,
          1,
          1,
          100,
          100
      );

      TerminalPersistenceService.PersistenceResult duplicate =
          service.submit(write)
              .toCompletableFuture()
              .get(15, TimeUnit.SECONDS);

      assertEquals(
          TerminalPersistenceService.Status.ALREADY_PERSISTED,
          duplicate.status(),
          "an idempotent duplicate retry must be reported as already persisted"
      );

      /*
       * The second terminal callback has the same event id.
       * No raw history row, outbox row, weekly increment or all-time
       * increment may be added a second time.
       */
      assertTerminalRows(
          SUCCESS_EVENT_ID,
          SUCCESS_PLAYER_ID,
          1,
          1,
          1,
          1,
          100,
          100
      );
    } finally {
      service.close();
    }
  }

  @Test
  void allTimeFailureRollsBackRawOutboxAndWeeklyThroughRealMySql()
      throws Exception {

    try (Connection connection = openConnection();
         Statement statement = connection.createStatement()) {

      statement.executeUpdate(
          "ALTER TABLE alltime_scores "
              + "ADD CONSTRAINT reject_terminal_rollback_player "
              + "CHECK (uuid <> '" + ROLLBACK_PLAYER_ID + "')"
      );
    }

    TerminalPersistenceService service =
        new TerminalPersistenceService(settings(), quietLogger());

    try {
      TerminalPersistenceService.PersistenceResult result =
          service.submit(write(ROLLBACK_EVENT_ID, ROLLBACK_PLAYER_ID))
              .toCompletableFuture()
              .get(15, TimeUnit.SECONDS);

      assertEquals(
          TerminalPersistenceService.Status.FAILED,
          result.status(),
          "forced aggregate failure must surface as FAILED"
      );

      try (Connection connection = openConnection()) {
        assertEquals(
            0,
            intQuery(
                connection,
                "SELECT COUNT(*) FROM scores "
                    + "WHERE uuid = '" + ROLLBACK_PLAYER_ID + "'"
            ),
            "raw score escaped rollback"
        );

        assertEquals(
            0,
            intQuery(
                connection,
                "SELECT COUNT(*) FROM outbox_events "
                    + "WHERE event_id = '" + ROLLBACK_EVENT_ID + "'"
            ),
            "outbox claim escaped rollback"
        );

        assertEquals(
            0,
            intQuery(
                connection,
                "SELECT COUNT(*) FROM season_scores "
                    + "WHERE uuid = '" + ROLLBACK_PLAYER_ID + "'"
            ),
            "weekly aggregate escaped rollback"
        );

        assertEquals(
            0,
            intQuery(
                connection,
                "SELECT COUNT(*) FROM alltime_scores "
                    + "WHERE uuid = '" + ROLLBACK_PLAYER_ID + "'"
            ),
            "all-time aggregate unexpectedly persisted"
        );
      }
    } finally {
      service.close();

      try (Connection connection = openConnection();
           Statement statement = connection.createStatement()) {

        statement.executeUpdate(
            "ALTER TABLE alltime_scores "
                + "DROP CHECK reject_terminal_rollback_player"
        );
      }
    }
  }

  @Test
  void weeklySeasonCreationRecoversAfterConcurrentCommitPastRepeatableReadSnapshot()
      throws Exception {

    Instant occurredAt =
        Instant.parse("2026-09-23T00:00:00Z");

    try (Connection staleSnapshot = openConnection();
         Connection winner = openConnection()) {

      staleSnapshot.setTransactionIsolation(
          Connection.TRANSACTION_REPEATABLE_READ
      );
      staleSnapshot.setAutoCommit(false);
      winner.setAutoCommit(false);

      /*
       * Establish an empty consistent snapshot in connection A before
       * connection B creates the weekly season.
       */
      try (Statement statement = staleSnapshot.createStatement();
           ResultSet resultSet =
               statement.executeQuery("SELECT COUNT(*) FROM seasons")) {

        assertTrue(resultSet.next());
        assertEquals(
            0,
            resultSet.getInt(1),
            "precondition: seasons must be empty before the race proof"
        );
      }

      long winningSeasonId =
          SeasonRepository.getOrCreateCurrentWeeklySeasonId(
              winner,
              occurredAt,
              4
          );

      winner.commit();

      /*
       * The first plain SELECT inside this call still sees A's older
       * REPEATABLE READ snapshot. Its INSERT therefore reaches the real
       * duplicate-key race. The post-race current read must recover B's id.
       */
      long recoveredSeasonId =
          SeasonRepository.getOrCreateCurrentWeeklySeasonId(
              staleSnapshot,
              occurredAt,
              4
          );

      assertEquals(
          winningSeasonId,
          recoveredSeasonId,
          "losing transaction must resolve the concurrently committed season"
      );

      staleSnapshot.rollback();

      /*
       * Verify from a fresh transaction that the race produced exactly one
       * WEEKLY row and that the row carries the canonical V2 season identity.
       * 2026-09-23 in Asia/Tokyo is ISO week 39.
       */
      try (Connection verifier = openConnection();
           Statement statement = verifier.createStatement();
           ResultSet resultSet =
               statement.executeQuery(
                   "SELECT COUNT(*) AS row_count, "
                       + "MIN(season_key) AS season_key "
                       + "FROM seasons "
                       + "WHERE season_type='WEEKLY' "
                       + "AND year=2026 AND week=39"
               )) {

        assertTrue(resultSet.next());

        assertEquals(
            1,
            resultSet.getInt("row_count"),
            "the weekly creation race must leave exactly one committed season"
        );

        assertEquals(
            "2026-W39",
            resultSet.getString("season_key"),
            "new weekly seasons must use the canonical V2 season_key"
        );
      }
    }
  }

  private static void assertTerminalRows(
      UUID eventId,
      UUID playerId,
      int rawCount,
      int outboxCount,
      int weeklyCount,
      int allTimeCount,
      int weeklyScore,
      int allTimeScore
  ) throws Exception {

    try (Connection connection = openConnection()) {
      assertEquals(
          rawCount,
          intQuery(
              connection,
              "SELECT COUNT(*) FROM scores "
                  + "WHERE uuid = '" + playerId + "'"
          )
      );

      assertEquals(
          outboxCount,
          intQuery(
              connection,
              "SELECT COUNT(*) FROM outbox_events "
                  + "WHERE event_id = '" + eventId + "'"
          )
      );

      assertEquals(
          weeklyCount,
          intQuery(
              connection,
              "SELECT COUNT(*) FROM season_scores "
                  + "WHERE uuid = '" + playerId + "'"
          )
      );

      assertEquals(
          allTimeCount,
          intQuery(
              connection,
              "SELECT COUNT(*) FROM alltime_scores "
                  + "WHERE uuid = '" + playerId + "'"
          )
      );

      assertEquals(
          weeklyScore,
          intQuery(
              connection,
              "SELECT COALESCE(MAX(score), 0) FROM season_scores "
                  + "WHERE uuid = '" + playerId + "'"
          )
      );

      assertEquals(
          allTimeScore,
          intQuery(
              connection,
              "SELECT COALESCE(MAX(score), 0) FROM alltime_scores "
                  + "WHERE uuid = '" + playerId + "'"
          )
      );
    }
  }

  private static TerminalPersistenceService.TerminalWrite write(
      UUID eventId,
      UUID playerId
  ) {
    return new TerminalPersistenceService.TerminalWrite(
        eventId,
        Instant.parse("2026-09-23T00:00:00Z"),
        playerId,
        "RealMySqlPlayer",
        "en",
        100,
        42L,
        "Normal",
        "SUCCESS",
        true,
        true,
        42_000L,
        null
    );
  }

  private static DatabaseRuntimeSettings settings() {
    return new DatabaseRuntimeSettings(
        true,
        MYSQL.getHost(),
        MYSQL.getMappedPort(3306),
        MYSQL.getDatabaseName(),
        MYSQL.getUsername(),
        MYSQL.getPassword()
    );
  }

  private static Connection openConnection() throws SQLException {
    return DriverManager.getConnection(
        MYSQL.getJdbcUrl(),
        MYSQL.getUsername(),
        MYSQL.getPassword()
    );
  }

  private static int intQuery(
      Connection connection,
      String sql
  ) throws SQLException {

    try (Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(sql)) {

      assertTrue(
          resultSet.next(),
          "expected one result row for SQL: " + sql
      );

      return resultSet.getInt(1);
    }
  }

  private static void executeMigration(
      Connection connection,
      String resourcePath
  ) throws Exception {

    String sql;

    try (InputStream stream =
             TerminalPersistenceServiceMySqlIntegrationTest.class
                 .getClassLoader()
                 .getResourceAsStream(resourcePath)) {

      sql = new String(
          Objects.requireNonNull(
              stream,
              "missing migration resource: " + resourcePath
          ).readAllBytes(),
          StandardCharsets.UTF_8
      );
    }

    String executableSql =
        Arrays.stream(sql.split("\\R"))
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

  private static Logger quietLogger() {
    Logger logger =
        Logger.getLogger(
            TerminalPersistenceServiceMySqlIntegrationTest.class.getName()
                + "."
                + UUID.randomUUID()
        );

    logger.setUseParentHandlers(false);
    logger.setLevel(Level.OFF);
    return logger;
  }
}
