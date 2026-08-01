package plugin.i18n;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackArtifactIntegrityTest {
  private static final List<Path> PAYLOAD_DIRS = List.of(
      Path.of("resourcepacks/treasurerun-i18n-pack/assets/minecraft/lang"),
      Path.of("resourcepacks/client-custom-languages/assets/minecraft/lang"),
      Path.of("fabric-i18n-mod/src/main/resources/assets/minecraft/lang"),
      Path.of("fabric-i18n-mod/src/main/resources/resourcepacks/treasurerun_langs/assets/minecraft/lang")
  );

  @Test
  void repositoryContainsNoMinecraftLanguagePayloadJson() throws Exception {
    for (Path directory : PAYLOAD_DIRS) {
      if (!Files.isDirectory(directory)) {
        continue;
      }
      try (var files = Files.list(directory)) {
        assertEquals(0, files.filter(path -> path.getFileName().toString().endsWith(".json")).count(),
            "Minecraft language payload JSON must not be tracked: " + directory);
      }
    }
  }

  @Test
  void sharedGeneratedArtifactsAreNotTracked() {
    assertFalse(Files.exists(Path.of("resourcepacks/generated/treasurerun-i18n-pack.zip")));
    assertFalse(Files.exists(Path.of("resourcepacks/generated/treasurerun-i18n-pack.zip.sha1")));
    assertFalse(Files.exists(Path.of("resourcepacks/generated/treasurerun-i18n-pack.zip.sha256")));
  }

  @Test
  void localGeneratorPolicyIsExplicitlyNonPublishable() throws Exception {
    Path policyPath = Path.of("resourcepacks/local-generator-policy.json");
    assertTrue(Files.exists(policyPath));
    JsonObject policy = JsonParser.parseString(Files.readString(policyPath)).getAsJsonObject();
    assertEquals("LOCAL_ONLY_DO_NOT_PUBLISH", policy.get("distribution_status").getAsString());
    assertEquals(17, policy.getAsJsonObject("official_mappings").size());
    assertEquals(6, policy.getAsJsonObject("held_mappings").size());
    assertTrue(Files.exists(Path.of("tools/client-resourcepack/build-local-official-language-pack.py")));
  }
}
