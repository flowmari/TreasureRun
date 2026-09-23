package plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TerminalPersistenceContainmentBoundaryTest {

  private static final Path PLUGIN =
      Path.of("src/main/java/plugin/TreasureRunMultiChestPlugin.java");

  private static final Path SERVICE =
      Path.of("src/main/java/plugin/rank/TerminalPersistenceService.java");

  private static final Path SEASON =
      Path.of("src/main/java/plugin/rank/SeasonRepository.java");

  private static final Path SEASON_SCORE =
      Path.of("src/main/java/plugin/rank/SeasonScoreRepository.java");

  @Test
  void terminalPersistenceWorkerHasNoBukkitDependencyAndOwnsBoundedJdbc() throws Exception {
    String source = Files.readString(SERVICE);

    assertFalse(source.contains("org.bukkit"));
    assertFalse(source.contains("TreasureRunMultiChestPlugin"));

    assertTrue(source.contains("new ArrayBlockingQueue<>(QUEUE_CAPACITY)"));
    assertTrue(source.contains("ThreadPoolExecutor.AbortPolicy"));
    assertTrue(source.contains("DriverManager.getConnection"));
    assertTrue(source.contains("&connectTimeout="));
    assertTrue(source.contains("&socketTimeout="));
    assertTrue(source.contains("setQueryTimeout(QUERY_TIMEOUT_SECONDS)"));
    assertTrue(source.contains("executor.shutdownNow()"));
    assertTrue(source.contains("List<Runnable> abandoned"));
    assertTrue(source.contains("rejectBeforeExecution"));
  }

  @Test
  void terminalGameplayCallSitesNoLongerInvokeSynchronousWriteHelpers() throws Exception {
    String source = Files.readString(PLUGIN);

    assertFalse(source.contains(
        "saveScore(player, finalScore, elapsedSec, runDifficulty);"
    ));
    assertFalse(source.contains(
        "addSeasonScore(player, finalScore, true, elapsedMs, \"SUCCESS\");"
    ));
    assertFalse(source.contains(
        "addSeasonScore(player, finalScore, false, null, \"TIME_UP\");"
    ));
    assertFalse(source.contains(
        "addSeasonScore(player, score, false, null, \"QUIT\");"
    ));

    assertTrue(source.contains(
        "\"MANUAL_STOP\","
    ));
    assertTrue(source.contains(
        "terminal persistence submitted on quit"
    ));
  }

  @Test
  void quitCleanupIsNotSequencedAfterJdbcCompletion() throws Exception {
    String source = Files.readString(PLUGIN);

    int submit = source.indexOf(
        "\"QUIT\",",
        source.indexOf("public void onPlayerQuit")
    );

    if (submit < 0) {
      submit = source.indexOf("\"QUIT\",");
    }

    int cleanup = source.indexOf(
        "finishRoundCleanup(player, CleanupReason.QUIT, true)"
    );

    assertTrue(submit >= 0);
    assertTrue(cleanup > submit);

    String between = source.substring(submit, cleanup);
    assertFalse(between.contains(".join()"));
    assertFalse(between.contains(".get()"));
    assertFalse(between.contains("getConnection()"));
  }

  @Test
  void explicitRepositoryConnectionOverloadsPreserveSeasonTransactionBoundary()
      throws Exception {
    String season = Files.readString(SEASON);
    String score = Files.readString(SEASON_SCORE);

    assertTrue(season.contains(
        "getOrCreateCurrentWeeklySeasonId("
    ));
    assertTrue(season.contains(
        "Connection connection,"
    ));
    assertTrue(season.contains(
        "statement.setQueryTimeout(queryTimeoutSeconds)"
    ));

    assertTrue(score.contains(
        "addWeeklyAndAllTime("
    ));
    assertTrue(score.contains(
        "Connection connection,"
    ));
    assertTrue(score.contains("connection.setAutoCommit(false)"));
    assertTrue(score.contains("connection.commit()"));
    assertTrue(score.contains("connection.rollback()"));
    assertTrue(score.contains(
        "statement.setQueryTimeout(queryTimeoutSeconds)"
    ));
  }

  @Test
  void disableClosesSubmissionBoundaryWithoutWaitingForDatabase() throws Exception {
    String source = Files.readString(PLUGIN);

    int disable = source.indexOf("public void onDisable()");
    int close = source.indexOf("terminalPersistenceService.close()", disable);
    int databaseClose = source.indexOf("closeDatabaseConnection()", disable);

    assertTrue(disable >= 0);
    assertTrue(close > disable);
    assertTrue(databaseClose > close);

    String disableBody = source.substring(disable, databaseClose);
    assertFalse(disableBody.contains("awaitTermination"));
    assertFalse(disableBody.contains(".join()"));
    assertFalse(disableBody.contains(".get()"));
  }

  @Test
  void terminalOperationOwnsRawAndSeasonTransactionAtomically() throws Exception {
    String service = Files.readString(SERVICE);
    String score = Files.readString(SEASON_SCORE);

    int transactionStart = service.indexOf("connection.setAutoCommit(false)");
    int rawWrite = service.indexOf("persistRawScore(connection, write)");
    int aggregateWrite = service.indexOf(
        "SeasonScoreRepository.addWeeklyAndAllTimeInTransaction"
    );
    int commit = service.indexOf("connection.commit()", aggregateWrite);

    assertTrue(transactionStart >= 0);
    assertTrue(rawWrite > transactionStart);
    assertTrue(aggregateWrite > rawWrite);
    assertTrue(commit > aggregateWrite);

    assertTrue(service.contains("Status.ALREADY_PERSISTED"));
    assertTrue(service.contains("connection.rollback()"));

    int callerOwned = score.indexOf(
        "public static boolean addWeeklyAndAllTimeInTransaction"
    );
    int applyTimeout = score.indexOf(
        "private static void applyTimeout",
        callerOwned
    );

    assertTrue(callerOwned >= 0);
    assertTrue(applyTimeout > callerOwned);

    String callerOwnedBody = score.substring(callerOwned, applyTimeout);
    assertFalse(callerOwnedBody.contains("connection.commit()"));
    assertFalse(callerOwnedBody.contains("connection.rollback()"));
    assertFalse(callerOwnedBody.contains("connection.setAutoCommit("));
  }

  @Test
  void failedOrRejectedSuccessPersistenceDoesNotQueryRunRank() throws Exception {
    String source = Files.readString(PLUGIN);

    assertFalse(source.contains("run-rank will continue in degraded mode"));
    assertTrue(source.contains(
        "SUCCESS rank unavailable because "
    ));
    assertTrue(source.contains(
        "the terminal write was not durably persisted: "
    ));

    int guard = source.indexOf("if (result == null || !result.persisted())");
    int unavailableFinish = source.indexOf(
        "finishSuccessfulRunAfterRank(",
        guard
    );
    int returnStatement = source.indexOf("return;", unavailableFinish);
    int rankLookup = source.indexOf("resolveRunRankAsync(", returnStatement);

    assertTrue(guard >= 0);
    assertTrue(unavailableFinish > guard);
    assertTrue(returnStatement > unavailableFinish);
    assertTrue(rankLookup > returnStatement);
  }

  @Test
  void shutdownCompletesQueuedPersistenceTasksAsRejected() throws Exception {
    String source = Files.readString(SERVICE);

    int shutdown = source.indexOf("List<Runnable> abandoned = executor.shutdownNow()");
    int drain = source.indexOf("for (Runnable runnable : abandoned)", shutdown);
    int reject = source.indexOf("pending.rejectBeforeExecution", drain);

    assertTrue(shutdown >= 0);
    assertTrue(drain > shutdown);
    assertTrue(reject > drain);
  }


  @Test
  void dockerBackedDbH3ATestsStayBehindIntegrationTaskBoundary()
      throws Exception {
    String build = Files.readString(Path.of("build.gradle"));
    String transaction =
        Files.readString(
            Path.of(
                "src/test/java/plugin/rank/"
                    + "TerminalPersistenceServiceMySqlIntegrationTest.java"
            )
        );
    String stall =
        Files.readString(
            Path.of(
                "src/test/java/plugin/rank/"
                    + "TerminalPersistenceServiceStallMySqlIntegrationTest.java"
            )
        );

    assertTrue(build.contains("excludeTags 'integration'"));
    assertTrue(build.contains("includeTags 'integration'"));

    assertTrue(transaction.contains("@Testcontainers"));
    assertTrue(transaction.contains("@Tag(\"integration\")"));

    assertTrue(stall.contains("@Testcontainers"));
    assertTrue(stall.contains("@Tag(\"integration\")"));
  }

}
