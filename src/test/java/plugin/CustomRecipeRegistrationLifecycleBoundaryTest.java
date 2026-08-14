package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CustomRecipeRegistrationLifecycleBoundaryTest {

  @Test
  void everyOwnedRecipeUsesRemoveBeforeAddForSameProcessPluginReenable() throws Exception {
    String source = Files.readString(
        Path.of("src/main/java/plugin/CustomRecipeLoader.java"),
        StandardCharsets.UTF_8
    );

    assertEquals(3, occurrences(source, "replaceRecipe(key, recipe);"));

    String helper = methodBody(
        source,
        "private void replaceRecipe(NamespacedKey key, Recipe recipe)"
    );
    int remove = helper.indexOf("Bukkit.removeRecipe(key);");
    int add = helper.indexOf("if (!Bukkit.addRecipe(recipe))");

    assertTrue(remove >= 0);
    assertTrue(add > remove);
    assertTrue(helper.contains(
        "throw new IllegalStateException(\"Failed to register TreasureRun recipe: \" + key);"
    ));

    assertTrue(source.contains("new NamespacedKey(plugin, \"special_emerald_recipe\")"));
    assertTrue(source.contains("new NamespacedKey(plugin, \"golden_apple_custom_recipe\")"));
    assertTrue(source.contains("new NamespacedKey(plugin, \"special_iron_block_recipe\")"));
  }

  private static int occurrences(String source, String needle) {
    int count = 0;
    int from = 0;
    while (true) {
      int index = source.indexOf(needle, from);
      if (index < 0) return count;
      count++;
      from = index + needle.length();
    }
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
