package plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RealtimeRankTickerJdbcContainmentBoundaryTest {

  private static final Path TICKER =
      Path.of("src/main/java/plugin/RealtimeRankTicker.java");

  private static final Path PLUGIN =
      Path.of("src/main/java/plugin/TreasureRunMultiChestPlugin.java");

  @Test
  void tickerDoesNotUseThePluginSharedConnectionOrRawJdbc() throws Exception {
    String source = Files.readString(TICKER);

    assertFalse(source.contains("plugin.getConnection()"));
    assertFalse(source.contains("prepareStatement("));
    assertFalse(source.contains("executeQuery("));

    assertTrue(source.contains("runTaskAsynchronously"));
    assertTrue(source.contains("JdbcLeaderboardSnapshotLoader"));
    assertTrue(source.contains("loadInFlight.compareAndSet(false, true)"));
    assertFalse(source.contains("plugin.isEnabled()"));
  }

  @Test
  void bukkitRenderingRemainsOnTheMainThreadCompletionBoundary() throws Exception {
    String source = Files.readString(TICKER);

    assertTrue(source.contains("runTask("));
    assertTrue(source.contains("finishLoad("));
    assertTrue(source.contains("renderSidebar("));
    assertTrue(source.contains("Bukkit.getOnlinePlayers()"));
  }

  @Test
  void tickerRejectsLateResultsAcrossLifecycleStop() throws Exception {
    String source = Files.readString(TICKER);
    String plugin = Files.readString(PLUGIN);

    assertTrue(source.contains("lifecycleGeneration"));
    assertTrue(source.contains("isCurrentLifecycle(generation)"));
    assertTrue(source.contains("active.set(false)"));
    assertTrue(source.contains("loadFailureActive"));

    assertTrue(plugin.contains("rankTicker.stop()"));
    assertTrue(plugin.contains("rankTicker = null"));
  }

  @Test
  void allThreeTickerWindowsUseTheBoundedSharedRankingBoundary() throws Exception {
    String source = Files.readString(TICKER);

    assertTrue(source.contains("RankingQueryService.Window.WEEKLY"));
    assertTrue(source.contains("RankingQueryService.Window.ALL_TIME"));
    assertTrue(source.contains("RankingQueryService.Window.MONTHLY"));
  }
}
