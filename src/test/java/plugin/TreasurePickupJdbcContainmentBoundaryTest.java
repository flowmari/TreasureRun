package plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TreasurePickupJdbcContainmentBoundaryTest {

  private static final Path EFFECTS_SOURCE =
      Path.of("src/main/java/plugin/TreasureRunGameEffectsPlugin.java");

  private static final Path PLUGIN_SOURCE =
      Path.of("src/main/java/plugin/TreasureRunMultiChestPlugin.java");

  @Test
  void treasurePickupEffectsHaveNoJdbcOrLegacyPerPlayerCountState() throws Exception {
    String source = read(EFFECTS_SOURCE);
    String pickup = methodBody(source, "public void onTreasureFound(");

    assertTrue(pickup.contains("playMiniDJEffect(player)"));

    assertFalse(pickup.contains("getConnection("));
    assertFalse(pickup.contains("prepareStatement("));
    assertFalse(pickup.contains("executeUpdate("));
    assertFalse(pickup.contains("playerTreasureCount"));

    assertFalse(source.contains("player_treasure_count"));
    assertFalse(source.contains("playerTreasureCount"));
    assertFalse(source.contains("saveTreasureCountToDB"));
    assertFalse(source.contains("initializeDatabaseStorage"));
    assertFalse(source.contains("resetPlayerTreasureCount"));
    assertFalse(source.contains("java.sql"));
  }

  @Test
  void mainPluginNoLongerInitializesOrResetsLegacyPickupPersistence() throws Exception {
    String source = read(PLUGIN_SOURCE);

    assertFalse(source.contains(
        "treasureRunGameEffectsPlugin.initializeDatabaseStorage()"
    ));
    assertFalse(source.contains(
        "treasureRunGameEffectsPlugin.resetPlayerTreasureCount("
    ));
    assertFalse(source.contains("player_treasure_count"));
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

      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return source.substring(start, i + 1);
        }
      }
    }

    throw new AssertionError("Missing closing brace: " + signature);
  }
}
