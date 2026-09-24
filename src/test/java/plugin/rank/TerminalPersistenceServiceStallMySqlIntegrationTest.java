package plugin.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import plugin.DatabaseRuntimeSettings;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
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

/**
 * DB-H3A executable failure-boundary proof.
 *
 * <p>This test deliberately exercises three different failure classes:
 * a TCP connection whose MySQL handshake never arrives, a real MySQL
 * statement blocked behind a table lock, and plugin/service shutdown while
 * that JDBC statement is demonstrably in flight.</p>
 */
@Tag("integration")
@Testcontainers
class TerminalPersistenceServiceStallMySqlIntegrationTest {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.36")
          .withDatabaseName("treasurerun_stall_integration")
          .withUsername("treasurerun")
          .withPassword("treasurerun");

  @BeforeAll
  static void applyMigration() throws Exception {
    try (Connection connection = openRealMySql()) {
      executeMigration(connection, "db/migration/V1__create_ranking_tables.sql");

      /*
       * V1 owns ranking aggregate tables; the legacy raw scores table is
       * created by TreasureRun's database setup path. This integration test
       * exercises TerminalPersistenceService directly, so reproduce that
       * required schema explicitly instead of relying on another test or on
       * test execution order.
       */
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            "CREATE TABLE IF NOT EXISTS scores ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "uuid VARCHAR(36) NULL,"
                + "player_name VARCHAR(50) NOT NULL,"
                + "score INT NOT NULL,"
                + "time BIGINT NOT NULL,"
                + "difficulty VARCHAR(10) NOT NULL,"
                + "lang_code VARCHAR(10) NOT NULL DEFAULT 'ja',"
                + "played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "INDEX idx_scores_played_at (played_at),"
                + "INDEX idx_scores_diff_time (difficulty, time),"
                + "INDEX idx_scores_uuid_played (uuid, played_at)"
                + ") ENGINE=InnoDB"
        );
      }
    }
  }

  @BeforeEach
  void clearScores() throws Exception {
    try (Connection connection = openRealMySql();
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM scores");
    }
  }

  @Test
  void tcpHandshakeBlackholeIsBoundedBySocketTimeout() throws Exception {
    CountDownLatch accepted = new CountDownLatch(1);
    CountDownLatch releaseServer = new CountDownLatch(1);

    try (ServerSocket server =
             new ServerSocket(
                 0,
                 1,
                 InetAddress.getLoopbackAddress()
             )) {

      Thread fakeMysql =
          new Thread(
              () -> {
                try (Socket socket = server.accept()) {
                  accepted.countDown();

                  /*
                   * TCP is established, but this endpoint never sends a
                   * MySQL handshake. Connector/J must therefore leave through
                   * its configured socket read timeout rather than hanging.
                   */
                  releaseServer.await(12, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                  // Test assertions are made by the submitting thread.
                }
              },
              "TreasureRun-fake-mysql-handshake-blackhole"
          );

      fakeMysql.setDaemon(true);
      fakeMysql.start();

      TerminalPersistenceService service =
          new TerminalPersistenceService(
              new DatabaseRuntimeSettings(
                  true,
                  "127.0.0.1",
                  server.getLocalPort(),
                  "stall",
                  "stall",
                  "stall"
              ),
              quietLogger()
          );

      long started = System.nanoTime();

      try {
        CompletionStage<TerminalPersistenceService.PersistenceResult> stage =
            service.submit(write(90_001));

        assertTrue(
            accepted.await(2, TimeUnit.SECONDS),
            "Connector/J never established the TCP connection to the fake endpoint"
        );

        TerminalPersistenceService.PersistenceResult result =
            stage.toCompletableFuture().get(8, TimeUnit.SECONDS);

        long elapsedMillis =
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertEquals(
            TerminalPersistenceService.Status.FAILED,
            result.status(),
            "a stalled MySQL handshake must fail rather than hang forever"
        );

        assertTrue(
            elapsedMillis < 7_500L,
            "socket-stalled JDBC exceeded the bounded failure window: "
                + elapsedMillis
                + " ms"
        );
      } finally {
        service.close();
        releaseServer.countDown();
      }
    }
  }

  @Test
  void realMySqlStatementLockIsBoundedByQueryTimeout() throws Exception {
    TerminalPersistenceService service =
        new TerminalPersistenceService(realSettings(), quietLogger());

    try (Connection blocker = openRealMySql();
         Connection observer = openRealMySql();
         Statement lock = blocker.createStatement()) {

      lock.execute("LOCK TABLES scores WRITE");

      long started = System.nanoTime();

      CompletionStage<TerminalPersistenceService.PersistenceResult> stage =
          service.submit(write(90_002));

      try {
        assertTrue(
            waitForScoreInsertToBecomeInflight(observer, 3_000L),
            "the terminal worker never became visibly blocked inside the real scores INSERT"
        );

        TerminalPersistenceService.PersistenceResult result =
            stage.toCompletableFuture().get(8, TimeUnit.SECONDS);

        long elapsedMillis =
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertEquals(
            TerminalPersistenceService.Status.FAILED,
            result.status(),
            "a statement held behind a real MySQL table lock must leave through the query timeout"
        );

        assertTrue(
            elapsedMillis < 7_500L,
            "real MySQL statement stall exceeded the bounded failure window: "
                + elapsedMillis
                + " ms"
        );
      } finally {
        try {
          lock.execute("UNLOCK TABLES");
        } catch (SQLException ignored) {
          // Closing blocker also releases the lock.
        }
      }
    } finally {
      service.close();
    }
  }

  @Test
  void closeReturnsWhileRealJdbcIsActuallyInflight() throws Exception {
    TerminalPersistenceService service =
        new TerminalPersistenceService(realSettings(), quietLogger());

    CompletionStage<TerminalPersistenceService.PersistenceResult> inflight;

    try (Connection blocker = openRealMySql();
         Connection observer = openRealMySql();
         Statement lock = blocker.createStatement()) {

      lock.execute("LOCK TABLES scores WRITE");

      inflight = service.submit(write(90_003));

      assertTrue(
          waitForScoreInsertToBecomeInflight(observer, 3_000L),
          "cannot prove disable behavior because the JDBC INSERT was not actually in flight"
      );

      assertFalse(
          inflight.toCompletableFuture().isDone(),
          "the in-flight operation unexpectedly completed before close()"
      );

      long closeStarted = System.nanoTime();
      service.close();
      long closeMillis =
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closeStarted);

      assertTrue(
          closeMillis < 500L,
          "close() waited for JDBC instead of returning promptly: "
              + closeMillis
              + " ms"
      );

      assertFalse(service.isAccepting());

      TerminalPersistenceService.PersistenceResult afterClose =
          service.submit(write(90_004))
              .toCompletableFuture()
              .get(1, TimeUnit.SECONDS);

      assertEquals(
          TerminalPersistenceService.Status.REJECTED,
          afterClose.status(),
          "new work must be rejected after the submission boundary is closed"
      );

      try {
        lock.execute("UNLOCK TABLES");
      } catch (SQLException ignored) {
        // Closing blocker also releases the lock.
      }
    } finally {
      /*
       * Idempotent close is intentional. The first close occurred while the
       * JDBC call was known to be in flight.
       */
      service.close();
    }

    TerminalPersistenceService.PersistenceResult late =
        inflight.toCompletableFuture().get(8, TimeUnit.SECONDS);

    /*
     * shutdownNow() does not promise that an already-running JDBC operation
     * is transactionally cancelled. It may fail due interruption/cancellation,
     * or finish after the DB lock is released. What is required here is:
     *
     *   - close() never waits for it;
     *   - new work is rejected;
     *   - the future does not remain orphaned forever.
     */
    assertNotNull(late);
    assertTrue(
        late.status() == TerminalPersistenceService.Status.PERSISTED
            || late.status() == TerminalPersistenceService.Status.FAILED,
        "unexpected late in-flight result after close: " + late
    );
  }

  @Test
  void lateCompletionHasNoDirectBukkitAuthority() throws Exception {
    String service =
        Files.readString(
            Path.of("src/main/java/plugin/rank/TerminalPersistenceService.java")
        );

    assertFalse(service.contains("org.bukkit"));
    assertFalse(service.contains("Bukkit."));
    assertFalse(service.contains("JavaPlugin"));
    assertFalse(service.contains("BukkitRunnable"));

    String plugin =
        Files.readString(
            Path.of("src/main/java/plugin/TreasureRunMultiChestPlugin.java")
        );

    int start =
        plugin.indexOf(
            "private void continueRunRankAfterTerminalPersistence"
        );

    int end =
        plugin.indexOf(
            "private void submitTerminalProverb",
            start
        );

    assertTrue(start >= 0, "terminal continuation method not found");
    assertTrue(end > start, "terminal continuation boundary not found");

    String continuation = plugin.substring(start, end);

    /*
     * Completion is polled by a Bukkit-owned main-thread task.
     * The DB worker itself has no Bukkit dependency or continuation callback.
     */
    assertTrue(continuation.contains("new BukkitRunnable()"));
    assertTrue(continuation.contains(".runTaskTimer(this"));
    assertFalse(continuation.contains(".thenAccept("));
    assertFalse(continuation.contains(".whenComplete("));
    assertFalse(continuation.contains(".thenRun("));
    assertFalse(continuation.contains(".handle("));
  }

  private static boolean waitForScoreInsertToBecomeInflight(
      Connection observer,
      long timeoutMillis
  ) throws Exception {
    long deadline =
        System.nanoTime()
            + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

    while (System.nanoTime() < deadline) {
      try (Statement statement = observer.createStatement();
           ResultSet resultSet =
               statement.executeQuery("SHOW FULL PROCESSLIST")) {

        while (resultSet.next()) {
          String info = resultSet.getString(8);

          if (info != null
              && info.toUpperCase().contains("INSERT INTO SCORES")) {
            return true;
          }
        }
      }

      Thread.sleep(50L);
    }

    return false;
  }

  private static DatabaseRuntimeSettings realSettings() {
    return new DatabaseRuntimeSettings(
        true,
        MYSQL.getHost(),
        MYSQL.getMappedPort(3306),
        MYSQL.getDatabaseName(),
        MYSQL.getUsername(),
        MYSQL.getPassword()
    );
  }

  private static Connection openRealMySql() throws SQLException {
    return DriverManager.getConnection(
        MYSQL.getJdbcUrl(),
        MYSQL.getUsername(),
        MYSQL.getPassword()
    );
  }

  private static TerminalPersistenceService.TerminalWrite write(int ordinal) {
    UUID eventId =
        UUID.nameUUIDFromBytes(
            ("db-h3a-stall-event-" + ordinal)
                .getBytes(StandardCharsets.UTF_8)
        );

    UUID playerId =
        UUID.nameUUIDFromBytes(
            ("db-h3a-stall-player-" + ordinal)
                .getBytes(StandardCharsets.UTF_8)
        );

    return new TerminalPersistenceService.TerminalWrite(
        eventId,
        Instant.parse("2026-09-23T00:00:00Z")
            .plusSeconds(ordinal),
        playerId,
        "StallProofPlayer" + ordinal,
        "en",
        100 + ordinal,
        42L,
        "Normal",
        "SUCCESS",
        false,
        false,
        null,
        null
    );
  }

  private static Logger quietLogger() {
    Logger logger =
        Logger.getLogger(
            TerminalPersistenceServiceStallMySqlIntegrationTest.class.getName()
                + "."
                + UUID.randomUUID()
        );

    logger.setUseParentHandlers(false);
    logger.setLevel(Level.OFF);
    return logger;
  }

  private static void executeMigration(
      Connection connection,
      String resourcePath
  ) throws Exception {
    String sql;

    try (InputStream stream =
             TerminalPersistenceServiceStallMySqlIntegrationTest.class
                 .getClassLoader()
                 .getResourceAsStream(resourcePath)) {

      sql =
          new String(
              Objects.requireNonNull(
                      stream,
                      "Missing migration resource: " + resourcePath
                  )
                  .readAllBytes(),
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
}
