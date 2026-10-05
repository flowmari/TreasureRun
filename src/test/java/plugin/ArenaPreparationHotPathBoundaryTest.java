package plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ArenaPreparationHotPathBoundaryTest {

  private static final Path STAGE_MANAGER =
      Path.of("src/main/java/plugin/GameStageManager.java");

  @Test
  void expensiveBaseWaterPreparationIsPersistentlyGuarded() throws Exception {
    String source = read(STAGE_MANAGER);
    String prepare = methodBody(source, "public void prepareSeasideStageAsync");

    assertTrue(prepare.contains("isBasePrepared(world, ARENA_WATER_RADIUS)"));
    assertTrue(prepare.contains("prepareOwnedArenaWaterColumn(base, dx, dz)"));
    assertTrue(prepare.contains("commitBasePreparation(world, ARENA_WATER_RADIUS)"));
    assertTrue(prepare.contains("ARENA_PREPARATION_BUDGET_NANOS"));
    assertTrue(prepare.contains("ARENA_PREPARATION_COLUMNS_PER_TICK"));
    assertTrue(
        prepare.indexOf("prepareOwnedArenaWaterColumn(base, dx, dz)")
            < prepare.indexOf("commitBasePreparation(world, ARENA_WATER_RADIUS)")
    );
  }

  @Test
  void normalRoundPathHasNoLargeFallbackAreaSweeps() throws Exception {
    String source = read(STAGE_MANAGER);
    String prepare = methodBody(source, "public void prepareSeasideStageAsync");
    String cleanup = methodBody(source, "public int clearDifficultyBlocks");

    assertFalse(prepare.contains("sweepAllLemonGlass"));
    assertFalse(cleanup.contains("sweepDifficultyBlocksAround"));
    assertFalse(source.contains("sweepLemonGlassAround"));
    assertFalse(source.contains("DIFF_SWEEP_RADIUS"));
    assertFalse(source.contains("recentStageCenters"));

    assertTrue(cleanup.contains("difficultyKeys.clear()"));
    assertTrue(cleanup.contains("difficultyBlocks.clear()"));
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
