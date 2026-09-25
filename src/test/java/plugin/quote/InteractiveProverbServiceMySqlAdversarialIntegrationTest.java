package plugin.quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import plugin.DatabaseRuntimeSettings;
import plugin.ProverbLogRepository;
import plugin.TreasureRunMultiChestPlugin;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * DB-H3B executable adversarial proof.
 *
 * <p>This suite distinguishes normal business-false outcomes from SQL
 * infrastructure failures, proves last-good snapshot preservation and
 * recovery against a real MySQL table stall, verifies lifecycle revocation
 * while real JDBC is in flight, bounds a TCP/MySQL-handshake blackhole, and
 * proves queue saturation rejects callers without blocking them.</p>
 */
@Tag("integration")
@Testcontainers
class InteractiveProverbServiceMySqlAdversarialIntegrationTest {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.36")
          .withDatabaseName("treasurerun_h3b_integration")
          .withUsername("treasurerun")
          .withPassword("treasurerun");

  @BeforeAll
  static void createSchema() throws Exception {
    try (Connection connection = openRealMySql();
         Statement statement = connection.createStatement()) {

      statement.executeUpdate(
          "CREATE TABLE IF NOT EXISTS proverb_logs ("
              + "id INT NOT NULL AUTO_INCREMENT,"
              + "player_uuid VARCHAR(36) NOT NULL,"
              + "player_name VARCHAR(64) NOT NULL,"
              + "outcome VARCHAR(32) NOT NULL,"
              + "difficulty VARCHAR(16) NOT NULL,"
              + "lang VARCHAR(16) NOT NULL,"
              + "quote_text TEXT NOT NULL,"
              + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
              + "PRIMARY KEY (id),"
              + "INDEX idx_player_uuid_created_at (player_uuid, created_at)"
              + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
      );

      createFavoritesTable(statement, "favorite_quotes");
      createFavoritesTable(statement, "proverb_favorites");
    }
  }

  @BeforeEach
  void clearTables() throws Exception {
    try (Connection connection = openRealMySql();
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM favorite_quotes");
      statement.executeUpdate("DELETE FROM proverb_favorites");
      statement.executeUpdate("DELETE FROM proverb_logs");
    }
  }

  @Test
  void businessFalseRemainsSuccessWhileRealSqlStallBecomesFailureAndRecoveryWorks()
      throws Exception {

    UUID playerId = UUID.randomUUID();

    try (InteractiveProverbService service = newService(realSettings())) {
      InteractiveProverbService.Result<Boolean> noLog =
          await(service.favoriteLatest(playerId), 8);
      assertEquals(InteractiveProverbService.Status.SUCCESS, noLog.status());
      assertEquals(Boolean.FALSE, noLog.value());

      insertProverb(
          playerId,
          "baseline",
          "2026-01-01 00:00:00"
      );

      InteractiveProverbService.Result<Boolean> firstFavorite =
          await(service.favoriteLatest(playerId), 8);
      assertEquals(InteractiveProverbService.Status.SUCCESS, firstFavorite.status());
      assertEquals(Boolean.TRUE, firstFavorite.value());

      InteractiveProverbService.Result<Boolean> duplicateFavorite =
          await(service.favoriteLatest(playerId), 8);
      assertEquals(InteractiveProverbService.Status.SUCCESS, duplicateFavorite.status());
      assertEquals(
          Boolean.FALSE,
          duplicateFavorite.value(),
          "MySQL duplicate-key business outcome must remain SUCCESS + false"
      );

      InteractiveProverbService.Result<Boolean> missingDelete =
          await(service.deleteFavorite(playerId, Integer.MAX_VALUE), 8);
      assertEquals(InteractiveProverbService.Status.SUCCESS, missingDelete.status());
      assertEquals(Boolean.FALSE, missingDelete.value());

      InteractiveProverbService.Result<InteractiveProverbService.BookData> baseline =
          await(service.loadBookData(playerId, 20, 20), 8);
      assertEquals(InteractiveProverbService.Status.SUCCESS, baseline.status());
      assertFalse(baseline.value().recent().isEmpty());
      assertFalse(baseline.value().favorites().isEmpty());

      List<String> lastGood = service.recentSnapshot(playerId, 20);
      assertFalse(lastGood.isEmpty());

      try (Connection locker = openRealMySql();
           Statement lockStatement = locker.createStatement()) {
        lockStatement.execute("LOCK TABLES proverb_logs WRITE");
        try {
          long started = System.nanoTime();
          CompletionStage<InteractiveProverbService.Result<InteractiveProverbService.BookData>>
              stalledStage = service.loadBookData(playerId, 20, 20);

          waitForBlockedProverbQuery();

          InteractiveProverbService.Result<InteractiveProverbService.BookData> stalled =
              await(stalledStage, 10);
          long elapsedMillis =
              TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

          assertEquals(
              InteractiveProverbService.Status.FAILED,
              stalled.status(),
              "real SQL infrastructure failure must not masquerade as SUCCESS + empty"
          );
          assertTrue(
              elapsedMillis < 9_000L,
              "real stalled JDBC read must remain bounded; elapsed=" + elapsedMillis
          );
          assertEquals(
              lastGood,
              service.recentSnapshot(playerId, 20),
              "failed refresh must preserve the last successful snapshot"
          );
        } finally {
          lockStatement.execute("UNLOCK TABLES");
        }
      }

      insertProverb(
          playerId,
          "recovered",
          "2026-01-01 00:00:01"
      );

      InteractiveProverbService.Result<InteractiveProverbService.BookData> recovered =
          await(service.loadBookData(playerId, 20, 20), 8);
      assertEquals(InteractiveProverbService.Status.SUCCESS, recovered.status());
      assertTrue(recovered.value().recent().size() >= 2);
      assertNotEquals(lastGood, service.recentSnapshot(playerId, 20));
    }
  }

  @Test
  void closeReturnsWithoutWaitingForRealMysqlAndLateInFlightResultCannotPublish()
      throws Exception {

    UUID playerId = UUID.randomUUID();
    insertProverb(playerId, "before-close", "2026-01-01 00:00:00");

    InteractiveProverbService service = newService(realSettings());
    try {
      InteractiveProverbService.Result<InteractiveProverbService.BookData> baseline =
          await(service.loadBookData(playerId, 20, 20), 8);
      assertEquals(InteractiveProverbService.Status.SUCCESS, baseline.status());
      List<String> lastGood = service.recentSnapshot(playerId, 20);
      assertFalse(lastGood.isEmpty());

      CompletionStage<InteractiveProverbService.Result<InteractiveProverbService.BookData>>
          stalledStage;

      try (Connection locker = openRealMySql();
           Statement lockStatement = locker.createStatement()) {
        lockStatement.execute("LOCK TABLES proverb_logs WRITE");
        try {
          stalledStage = service.loadBookData(playerId, 20, 20);
          waitForBlockedProverbQuery();

          long closeStarted = System.nanoTime();
          service.close();
          long closeMillis =
              TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closeStarted);

          assertTrue(
              closeMillis < 1_500L,
              "service close must not wait for in-flight JDBC; elapsed=" + closeMillis
          );
        } finally {
          lockStatement.execute("UNLOCK TABLES");
        }
      }

      InteractiveProverbService.Result<InteractiveProverbService.BookData> late =
          await(stalledStage, 8);
      assertEquals(
          InteractiveProverbService.Status.REJECTED,
          late.status(),
          "already-running JDBC work must lose publication authority after close"
      );
      assertEquals(lastGood, service.recentSnapshot(playerId, 20));

      InteractiveProverbService.Result<List<String>> afterClose =
          await(service.loadFavorites(playerId, 20), 1);
      assertEquals(InteractiveProverbService.Status.REJECTED, afterClose.status());
    } finally {
      service.close();
    }
  }

  @Test
  void tcpHandshakeBlackholeIsBoundedAndReportedAsInfrastructureFailure()
      throws Exception {

    try (BlackholeServer blackhole = new BlackholeServer()) {
      DatabaseRuntimeSettings settings =
          new DatabaseRuntimeSettings(
              true,
              "127.0.0.1",
              blackhole.port(),
              "treasurerun_h3b_blackhole",
              "treasurerun",
              "treasurerun"
          );

      try (InteractiveProverbService service = newService(settings)) {
        long started = System.nanoTime();
        CompletionStage<InteractiveProverbService.Result<List<String>>> stage =
            service.loadFavorites(UUID.randomUUID(), 20);

        assertTrue(
            blackhole.awaitAccepted(3, TimeUnit.SECONDS),
            "precondition: JDBC TCP connection must reach the blackhole"
        );

        InteractiveProverbService.Result<List<String>> result = await(stage, 10);
        long elapsedMillis =
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertEquals(InteractiveProverbService.Status.FAILED, result.status());
        assertTrue(result.value().isEmpty());
        assertTrue(
            elapsedMillis < 9_000L,
            "handshake/socket failure must remain bounded; elapsed=" + elapsedMillis
        );
      }
    }
  }

  @Test
  void queueSaturationRejectsWithoutBlockingSubmittingThread() throws Exception {
    try (BlackholeServer blackhole = new BlackholeServer()) {
      DatabaseRuntimeSettings settings =
          new DatabaseRuntimeSettings(
              true,
              "127.0.0.1",
              blackhole.port(),
              "treasurerun_h3b_queue",
              "treasurerun",
              "treasurerun"
          );

      InteractiveProverbService service = newService(settings);
      try {
        CompletionStage<InteractiveProverbService.Result<Boolean>> first =
            service.favoriteLatest(UUID.randomUUID());

        assertTrue(
            blackhole.awaitAccepted(3, TimeUnit.SECONDS),
            "precondition: first JDBC operation must occupy the single worker"
        );

        List<CompletableFuture<InteractiveProverbService.Result<Boolean>>> submitted =
            new ArrayList<>();

        long started = System.nanoTime();
        for (int i = 0; i < 64; i++) {
          submitted.add(
              service.favoriteLatest(UUID.randomUUID()).toCompletableFuture()
          );
        }
        long submitMillis =
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        long immediateRejected =
            submitted.stream()
                .filter(CompletableFuture::isDone)
                .map(CompletableFuture::join)
                .filter(result -> result.status() == InteractiveProverbService.Status.REJECTED)
                .count();

        assertTrue(
            immediateRejected > 0,
            "bounded queue must reject overflow instead of blocking the caller"
        );
        assertTrue(
            submitMillis < 2_000L,
            "queue saturation must not block the submitting thread; elapsed=" + submitMillis
        );

        service.close();
        blackhole.release();

        InteractiveProverbService.Result<Boolean> inFlight = await(first, 5);
        assertEquals(InteractiveProverbService.Status.REJECTED, inFlight.status());

        for (CompletableFuture<InteractiveProverbService.Result<Boolean>> future : submitted) {
          InteractiveProverbService.Result<Boolean> result = future.get(5, TimeUnit.SECONDS);
          assertTrue(
              result.status() == InteractiveProverbService.Status.REJECTED
                  || result.status() == InteractiveProverbService.Status.FAILED,
              "shutdown/saturation result must be terminal and non-success: " + result.status()
          );
        }
      } finally {
        service.close();
        blackhole.release();
      }
    }
  }

  private static InteractiveProverbService newService(DatabaseRuntimeSettings settings) {
    Logger logger = quietLogger();
    TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
    when(plugin.getLogger()).thenReturn(logger);

    ProverbLogRepository repository = new ProverbLogRepository(plugin);
    JdbcInteractiveProverbBackend backend =
        new JdbcInteractiveProverbBackend(settings, repository);

    return new InteractiveProverbService(settings, backend, logger);
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

  private static Connection openRealMySql() throws Exception {
    return DriverManager.getConnection(
        MYSQL.getJdbcUrl(),
        MYSQL.getUsername(),
        MYSQL.getPassword()
    );
  }

  private static void createFavoritesTable(Statement statement, String table) throws Exception {
    statement.executeUpdate(
        "CREATE TABLE IF NOT EXISTS " + table + " ("
            + "id INT NOT NULL AUTO_INCREMENT,"
            + "player_uuid VARCHAR(36) NOT NULL,"
            + "quote_hash VARCHAR(64) NOT NULL,"
            + "outcome VARCHAR(32) NOT NULL,"
            + "difficulty VARCHAR(16) NOT NULL,"
            + "lang VARCHAR(16) NOT NULL,"
            + "quote_text TEXT NOT NULL,"
            + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "PRIMARY KEY (id),"
            + "UNIQUE KEY uk_player_quote (player_uuid, quote_hash),"
            + "INDEX idx_player_uuid_created_at (player_uuid, created_at)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
    );
  }

  private static void insertProverb(UUID playerId, String quote, String createdAt)
      throws Exception {
    try (Connection connection = openRealMySql();
         var statement = connection.prepareStatement(
             "INSERT INTO proverb_logs "
                 + "(player_uuid, player_name, outcome, difficulty, lang, quote_text, created_at) "
                 + "VALUES (?, ?, ?, ?, ?, ?, ?)"
         )) {
      statement.setString(1, playerId.toString());
      statement.setString(2, "integration-player");
      statement.setString(3, "SUCCESS");
      statement.setString(4, "Normal");
      statement.setString(5, "en");
      statement.setString(6, quote);
      statement.setString(7, createdAt);
      statement.executeUpdate();
    }
  }

  private static void waitForBlockedProverbQuery() throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);

    while (System.nanoTime() < deadline) {
      try (Connection inspector = openRealMySql();
           Statement statement = inspector.createStatement();
           ResultSet resultSet = statement.executeQuery("SHOW FULL PROCESSLIST")) {

        while (resultSet.next()) {
          String info = resultSet.getString("Info");
          String state = resultSet.getString("State");
          if (info == null || state == null) {
            continue;
          }

          String normalizedInfo = info.toLowerCase(Locale.ROOT);
          String normalizedState = state.toLowerCase(Locale.ROOT);

          if (normalizedInfo.contains("proverb_logs")
              && normalizedState.contains("wait")) {
            return;
          }
        }
      }

      Thread.sleep(50L);
    }

    fail("real MySQL did not expose an in-flight blocked proverb_logs statement");
  }

  private static <T> InteractiveProverbService.Result<T> await(
      CompletionStage<InteractiveProverbService.Result<T>> stage,
      long timeoutSeconds
  ) throws Exception {
    return stage.toCompletableFuture().get(timeoutSeconds, TimeUnit.SECONDS);
  }

  private static Logger quietLogger() {
    Logger logger = Logger.getLogger(
        InteractiveProverbServiceMySqlAdversarialIntegrationTest.class.getName()
            + "."
            + UUID.randomUUID()
    );
    logger.setUseParentHandlers(false);
    logger.setLevel(Level.OFF);
    return logger;
  }

  private static final class BlackholeServer implements AutoCloseable {
    private final ServerSocket server;
    private final CountDownLatch accepted = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final Thread thread;
    private volatile Socket acceptedSocket;

    private BlackholeServer() throws Exception {
      server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
      thread = new Thread(
          () -> {
            try (Socket socket = server.accept()) {
              acceptedSocket = socket;
              accepted.countDown();
              release.await(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {
              accepted.countDown();
            }
          },
          "TreasureRun-H3B-blackhole"
      );
      thread.setDaemon(true);
      thread.start();
    }

    private int port() {
      return server.getLocalPort();
    }

    private boolean awaitAccepted(long timeout, TimeUnit unit) throws InterruptedException {
      return accepted.await(timeout, unit);
    }

    private void release() {
      release.countDown();
      Socket socket = acceptedSocket;
      if (socket != null) {
        try {
          socket.close();
        } catch (Exception ignored) {
        }
      }
    }

    @Override
    public void close() {
      release();
      try {
        server.close();
      } catch (Exception ignored) {
      }
      try {
        thread.join(1_000L);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
