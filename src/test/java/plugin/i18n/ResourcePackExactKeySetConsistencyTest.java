package plugin.i18n;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResourcePackExactKeySetConsistencyTest {

  @Test
  void localOnlyPolicyHasOneExactUniqueAliasPathSet() throws Exception {
    JsonObject policy = JsonParser.parseString(
        Files.readString(Path.of("resourcepacks/local-generator-policy.json"))
    ).getAsJsonObject();
    JsonArray aliases = policy.getAsJsonArray("alias_locales");
    Set<String> unique = new HashSet<>();
    aliases.forEach(value -> unique.add(value.getAsString()));
    assertEquals(128, aliases.size());
    assertEquals(128, unique.size());
  }

  @Test
  void officialAndHeldLanguageSetsAreDisjoint() throws Exception {
    JsonObject policy = JsonParser.parseString(
        Files.readString(Path.of("resourcepacks/local-generator-policy.json"))
    ).getAsJsonObject();
    Set<String> official = policy.getAsJsonObject("official_mappings").keySet();
    Set<String> held = policy.getAsJsonObject("held_mappings").keySet();
    assertEquals(17, official.size());
    assertEquals(6, held.size());
    assertFalse(official.stream().anyMatch(held::contains));
  }
}
