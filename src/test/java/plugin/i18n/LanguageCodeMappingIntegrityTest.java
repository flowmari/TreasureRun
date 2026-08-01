package plugin.i18n;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LanguageCodeMappingIntegrityTest {

  @Test
  void pluginMappingsArePreservedAndPartitionedByClientPackPolicy() throws Exception {
    Map<String, String> mappings = readSimpleMappings();
    JsonObject policy = JsonParser.parseString(
        Files.readString(Path.of("resourcepacks/local-generator-policy.json"))
    ).getAsJsonObject();
    JsonObject official = policy.getAsJsonObject("official_mappings");
    JsonObject held = policy.getAsJsonObject("held_mappings");

    assertEquals(23, mappings.size());
    assertEquals(17, official.size());
    assertEquals(6, held.size());
    for (Map.Entry<String, String> entry : mappings.entrySet()) {
      JsonObject bucket = official.has(entry.getKey()) ? official : held;
      assertEquals(entry.getValue(), bucket.get(entry.getKey()).getAsString());
    }
    assertEquals("lzh_hant", held.get("lzh").getAsString());
    assertFalse(official.has("lzh"));
  }

  private static Map<String, String> readSimpleMappings() throws Exception {
    Map<String, String> mappings = new LinkedHashMap<>();
    boolean inMappings = false;
    for (String rawLine : Files.readAllLines(Path.of("src/main/resources/lang-map.yml"), StandardCharsets.UTF_8)) {
      String line = rawLine.split("#", 2)[0];
      if (line.trim().equals("mappings:")) { inMappings = true; continue; }
      if (!inMappings || line.isBlank()) { continue; }
      if (!line.startsWith("  ")) { break; }
      String[] parts = line.trim().split(":", 2);
      if (parts.length == 2) { mappings.put(parts[0].trim(), parts[1].trim()); }
    }
    return mappings;
  }
}
