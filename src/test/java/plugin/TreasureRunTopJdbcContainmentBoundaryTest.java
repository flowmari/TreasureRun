package plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TreasureRunTopJdbcContainmentBoundaryTest {

  private static final Path PLUGIN_SOURCE =
      Path.of("src/main/java/plugin/TreasureRunMultiChestPlugin.java");

  private static final Path JDBC_LOADER_SOURCE =
      Path.of("src/main/java/plugin/rank/JdbcLeaderboardSnapshotLoader.java");

  private static final Path RANKING_QUERY_SERVICE_SOURCE =
      Path.of("src/main/java/plugin/rank/RankingQueryService.java");

  @Test
  void allThreeRankingCommandsUseTheAsyncBoundedBoundary() throws Exception {
    String source = read(PLUGIN_SOURCE);
    String body = methodBody(source, "private void showRankingWindow(");

    assertTrue(body.contains("RankingRequestCoordinator"));
    assertTrue(body.contains("runTaskAsynchronously"));
    assertTrue(body.contains("loadRankingWindow(settings, window)"));
    assertFalse(body.contains("getConnection("));
    assertFalse(body.contains("prepareStatement("));
    assertFalse(body.contains("executeQuery("));
    assertFalse(body.contains("DriverManager"));

    assertTrue(methodBody(source, "private void showWeeklyRanking(Player player)")
        .contains("RankingQueryService.Window.WEEKLY"));
    assertTrue(methodBody(source, "private void showAllTimeRanking(Player player)")
        .contains("RankingQueryService.Window.ALL_TIME"));
    assertTrue(methodBody(source, "private void showMonthlyRanking(Player player)")
        .contains("RankingQueryService.Window.MONTHLY"));
  }

  @Test
  void blockingLoaderOwnsShortLivedConnectionsAndTimeouts() throws Exception {
    String loaderSource = read(JDBC_LOADER_SOURCE);
    String rankingQuerySource = read(RANKING_QUERY_SERVICE_SOURCE);
    String allTimeLoad = methodBody(
        loaderSource,
        "public List<RankingQueryService.RankingEntry> loadEntries()");
    String windowedLoad = methodBody(
        loaderSource,
        "RankingQueryService.Window window");
    String runRankLoad = methodBody(loaderSource, "public int loadRunRank(");
    String openConnection = methodBody(
        loaderSource,
        "private Connection openConnection() throws Exception");

    assertTrue(allTimeLoad.contains("try (Connection connection = openConnection())"));
    assertTrue(allTimeLoad.contains("RankingQueryService.loadAllTime("));
    assertTrue(allTimeLoad.contains("QUERY_TIMEOUT_SECONDS"));

    assertTrue(windowedLoad.contains("try (Connection connection = openConnection())"));
    assertTrue(windowedLoad.contains("RankingQueryService.load("));
    assertTrue(windowedLoad.contains("QUERY_TIMEOUT_SECONDS"));

    assertTrue(runRankLoad.contains("try (Connection connection = openConnection())"));
    assertTrue(runRankLoad.contains("RankingQueryService.findRunRank("));
    assertTrue(runRankLoad.contains("QUERY_TIMEOUT_SECONDS"));

    assertTrue(openConnection.contains("DriverManager.getConnection"));
    assertTrue(openConnection.contains("&connectTimeout="));
    assertTrue(openConnection.contains("CONNECT_TIMEOUT_MILLIS"));
    assertTrue(openConnection.contains("&socketTimeout="));
    assertTrue(openConnection.contains("SOCKET_TIMEOUT_MILLIS"));

    assertTrue(rankingQuerySource.contains(
        "statement.setQueryTimeout(queryTimeoutSeconds)"));
  }

  @Test
  void runRankReadIsOffThreadAndResumesOnlyOnTheMainThread() throws Exception {
    String source = read(PLUGIN_SOURCE);
    String body = methodBody(source, "private void resolveRunRankAsync(");

    assertTrue(body.contains("runTaskAsynchronously"));
    assertTrue(body.contains("loadRunRank("));
    assertTrue(body.contains("runTask("));
    assertTrue(body.contains("finishSuccessfulRunAfterRank("));
    assertTrue(body.contains("isRankingReadGenerationCurrent(generation)"));
    assertFalse(body.contains("getConnection("));
    assertFalse(body.contains("prepareStatement("));
    assertFalse(body.contains("executeQuery("));
  }

  @Test
  void rankingPublicationAuthorityIsRevokedBeforeDisableTeardown() throws Exception {
    String source = read(PLUGIN_SOURCE);
    String enable = methodBody(source, "public void onEnable()");
    String disable = methodBody(source, "public void onDisable()");

    assertTrue(enable.contains("weeklyRankingRequests.activate()"));
    assertTrue(enable.contains("allTimeRankingRequests.activate()"));
    assertTrue(enable.contains("monthlyRankingRequests.activate()"));
    assertTrue(enable.contains("rankingReadCallbacksActive.set(true)"));

    assertTrue(disable.contains("rankingReadCallbacksActive.set(false)"));
    assertTrue(disable.contains("rankingReadGeneration.incrementAndGet()"));
    assertTrue(disable.contains("weeklyRankingRequests.deactivate()"));
    assertTrue(disable.contains("allTimeRankingRequests.deactivate()"));
    assertTrue(disable.contains("monthlyRankingRequests.deactivate()"));
  }

  @Test
  void tickerStartupDoesNotProbeTheSharedConnectionOnTheMainThread() throws Exception {
    String source = read(PLUGIN_SOURCE);
    String enable = methodBody(source, "public void onEnable()");

    assertTrue(enable.contains("rankTicker = new RealtimeRankTicker"));
    assertFalse(enable.contains("isDatabaseEnabled() && getConnection() != null"));
  }

  private static String read(Path path) throws IOException {
    return Files.readString(path, StandardCharsets.UTF_8);
  }

  private static String methodBody(String source, String signature) {
    int signatureIndex = source.indexOf(signature);
    if (signatureIndex < 0) throw new AssertionError("Signature not found: " + signature);
    int openingBrace = source.indexOf('{', signatureIndex);
    if (openingBrace < 0) throw new AssertionError("Opening brace not found: " + signature);
    int depth = 0;
    for (int index = openingBrace; index < source.length(); index++) {
      char current = source.charAt(index);
      if (current == '{') depth++;
      else if (current == '}') {
        depth--;
        if (depth == 0) return source.substring(openingBrace + 1, index);
      }
    }
    throw new AssertionError("Closing brace not found: " + signature);
  }
}
