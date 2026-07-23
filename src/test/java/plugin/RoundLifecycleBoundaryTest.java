package plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoundLifecycleBoundaryTest {

  private static final Path PLUGIN =
      Path.of("src/main/java/plugin/TreasureRunMultiChestPlugin.java");

  @Test
  void preparationIsClaimedBeforeArenaMutationAndCountdown() throws Exception {
    String source = read(PLUGIN);
    String method = methodBody(source, "public void beginGameStartAfterLanguageSelected");

    int preparation = method.indexOf("roundLifecycle.tryBeginPreparation()");
    int stageBuild = method.indexOf("gameStageManager.buildSeasideStageAndTeleport(player)");
    int countdown = method.indexOf("roundLifecycle.beginCountdown()");

    assertTrue(preparation >= 0);
    assertTrue(stageBuild > preparation);
    assertTrue(countdown > stageBuild);
    assertTrue(method.contains("countdownDelayTask ="));
    assertTrue(method.contains("countdownTask ="));
  }

  @Test
  void terminalAndAbnormalPathsUseTheSharedCleanupBoundary() throws Exception {
    String source = read(PLUGIN);

    assertTrue(source.contains("beginTerminalReset(player, CleanupReason.SUCCESS)"));
    assertTrue(source.contains("beginTerminalReset(player, CleanupReason.TIME_UP)"));
    assertTrue(source.contains("finishRoundCleanup(player, CleanupReason.MANUAL_STOP, true)"));
    assertTrue(source.contains("finishRoundCleanup(player, CleanupReason.QUIT, true)"));
    assertTrue(source.contains("finishRoundCleanup(activePlayer, CleanupReason.PLUGIN_DISABLE, true)"));
    assertTrue(source.contains("finishRoundCleanup(player, CleanupReason.PREPARATION_FAILED, true)"));
  }

  @Test
  void cleanupIsIdempotentAndOwnsRoundArtifacts() throws Exception {
    String source = read(PLUGIN);
    String cleanup = methodBody(source, "private void finishRoundCleanup");
    String stop = methodBody(source, "private void stopRoundActivity");
    String artifacts = methodBody(source, "private void clearRoundArtifacts");

    assertTrue(cleanup.contains("roundLifecycle.claimCleanup()"));
    assertTrue(cleanup.contains("roundLifecycle.completeReset()"));
    assertTrue(cleanup.contains("restoreWorldAndPlayer(player)"));
    assertTrue(stop.contains("cancelCountdownTasks()"));
    assertTrue(stop.contains("bossBar.removeAll()"));
    assertTrue(artifacts.contains("treasureChestManager.removeAllChests()"));
    assertTrue(artifacts.contains("gameStageManager.clearDifficultyBlocks()"));
    assertTrue(artifacts.contains("gameStageManager.clearShopEntities()"));
  }

  @Test
  void legacyBooleanRunningFlagIsRemoved() throws Exception {
    String source = read(PLUGIN);

    assertFalse(source.contains("private boolean isRunning"));
    assertTrue(source.contains("private final RoundLifecycle roundLifecycle"));
    assertTrue(source.contains("return roundLifecycle.isRunning()"));
  }

  private static String read(Path path) throws Exception {
    return Files.readString(path, StandardCharsets.UTF_8);
  }

  private static String methodBody(String source, String signature) {
    int start = source.indexOf(signature);
    assertTrue(start >= 0, "Missing method: " + signature);

    int brace = source.indexOf('{', start);
    assertTrue(brace >= 0, "Missing opening brace: " + signature);

    int depth = 0;
    for (int i = brace; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') depth++;
      if (c == '}') {
        depth--;
        if (depth == 0) return source.substring(start, i + 1);
      }
    }

    throw new AssertionError("Missing closing brace: " + signature);
  }
}
