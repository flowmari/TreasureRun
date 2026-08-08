package plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MultiplayerRuntimeFoundationBoundaryTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();

  @Test
  void productionPluginUsesAnImmutableRoundContextInsteadOfActiveRoundPlayerId() throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String context = read("src/main/java/plugin/RoundRuntimeContext.java");

    assertFalse(plugin.contains("activeRoundPlayerId"));
    assertTrue(plugin.contains("private RoundRuntimeContext activeRoundContext;"));
    assertTrue(plugin.contains("RoundRuntimeContext.legacy(player.getUniqueId())"));
    assertTrue(context.contains("participants = List.copyOf"));
    assertTrue(context.contains("public static RoundRuntimeContext serverHosted"));
  }

  @Test
  void stagePreparationAndParticipantTeleportAreSeparateOperations() throws Exception {
    String stage = read("src/main/java/plugin/GameStageManager.java");
    String prepare = methodBody(stage, "public Location prepareSeasideStage");
    String teleport = methodBody(stage, "public boolean teleportPlayerToPreparedStage");
    String compatibility = methodBody(stage, "public Location buildSeasideStageAndTeleport");

    assertFalse(prepare.contains("player.teleport("));
    assertFalse(prepare.contains("effectsAudience.teleport("));
    assertTrue(teleport.contains("return player.teleport(target);"));
    assertTrue(compatibility.contains("prepareSeasideStage(player)"));
    assertTrue(compatibility.contains("teleportPlayerToPreparedStage(player, stageCenter)"));
  }

  @Test
  void chestPlacementCanUseTheRoundOwnedStageAnchorWithoutAPlayerLocation() throws Exception {
    String manager = read("src/main/java/plugin/TreasureChestManager.java");
    String anchorMethod = methodBody(
        manager,
        "public boolean spawnChests(\n      Location anchor"
    );

    assertTrue(anchorMethod.contains("World world = anchor.getWorld();"));
    assertTrue(anchorMethod.contains("Location loc = anchor.clone().add("));
    assertFalse(anchorMethod.contains("player.getLocation()"));
  }

  @Test
  void participantReturnLedgerExposesOneAtomicBatchOperation() throws Exception {
    String ledger = read("src/main/java/plugin/PlayerReturnLedger.java");
    String batch = methodBody(
        ledger,
        "public synchronized PutBatchResult putPendingBatch"
    );

    assertTrue(batch.contains("Map<UUID, PlayerReturnRecord> next"));
    assertTrue(batch.contains("persist(next);"));
    assertTrue(batch.contains("pending.clear();"));
    assertTrue(batch.indexOf("persist(next);") < batch.indexOf("pending.clear();"));
  }

  private static String read(String relative) throws Exception {
    return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
  }

  private static String methodBody(String source, String signature) {
    int start = source.indexOf(signature);
    assertTrue(start >= 0, "Missing method: " + signature);
    int brace = source.indexOf('{', start);
    assertTrue(brace >= 0, "Missing opening brace: " + signature);

    int depth = 0;
    for (int index = brace; index < source.length(); index++) {
      char value = source.charAt(index);
      if (value == '{') depth++;
      if (value == '}') {
        depth--;
        if (depth == 0) return source.substring(start, index + 1);
      }
    }
    throw new AssertionError("Missing closing brace: " + signature);
  }
}
