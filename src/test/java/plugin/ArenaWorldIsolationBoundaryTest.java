package plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ArenaWorldIsolationBoundaryTest {

  private static final Path STAGE_MANAGER =
      Path.of("src/main/java/plugin/GameStageManager.java");
  private static final Path ARENA_MANAGER =
      Path.of("src/main/java/plugin/ArenaWorldManager.java");
  private static final Path PLUGIN =
      Path.of("src/main/java/plugin/TreasureRunMultiChestPlugin.java");

  @Test
  void stageBuildUsesOnlyThePluginOwnedArenaWorld() throws Exception {
    String stage = read(STAGE_MANAGER);
    String arena = read(ARENA_MANAGER);
    String buildMethod = methodBody(stage, "public Location buildSeasideStageAndTeleport");

    assertTrue(arena.contains("WORLD_NAME = \"treasurerun_arena\""));
    assertTrue(buildMethod.contains("arenaWorldManager.getArenaBase()"));
    assertTrue(buildMethod.contains("arenaWorldManager.requireOwnedWorld(w)"));
    assertFalse(buildMethod.contains("forceFindOcean(player.getLocation())"));
    assertFalse(buildMethod.contains("player.getLocation().clone().add(320"));
    assertFalse(stage.contains("forceFindOcean("));
    assertFalse(stage.contains("findNearbySeaLocation("));
  }

  @Test
  void everyArenaWaterPreparationHasAnOwnedWorldGuard() throws Exception {
    String stage = read(STAGE_MANAGER);
    String patchMethod = methodBody(stage, "private void prepareOwnedArenaWater");

    assertTrue(patchMethod.contains("arenaWorldManager.requireOwnedWorld(w)"));
    assertTrue(stage.contains("prepareOwnedArenaWater(base, ARENA_WATER_RADIUS)"));
    assertFalse(stage.contains("prepareOwnedArenaWater(base, 128)"));
  }

  @Test
  void startupPurgeAndEnvironmentRestoreStayInsideTheGameplayWorld() throws Exception {
    String stage = read(STAGE_MANAGER);
    String plugin = read(PLUGIN);
    String purgeMethod = methodBody(stage, "public int purgeTreasureShopEntitiesOnStartup");
    String restoreMethod = methodBody(plugin, "private void restoreWorldAndPlayer");

    assertTrue(purgeMethod.contains("arenaWorldManager.getArenaWorld()"));
    assertFalse(purgeMethod.contains("Bukkit.getWorlds()"));

    int capture = restoreMethod.indexOf("World gameplayWorld = player.getWorld()");
    int teleport = restoreMethod.indexOf("player.teleport(original)");
    assertTrue(capture >= 0);
    assertTrue(teleport > capture);
    assertTrue(restoreMethod.contains("gameplayWorld.setTime(previousWorldTime)"));
    assertFalse(restoreMethod.contains("World w = player.getWorld()"));
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
