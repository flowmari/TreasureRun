package plugin;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackDeliveryBoundaryTest {

  private static final Path CONFIG = Path.of("src/main/resources/config.yml");
  private static final Path ZIP =
      Path.of("resourcepacks/generated/treasurerun-i18n-pack.zip");
  private static final Path SHA1 =
      Path.of("resourcepacks/generated/treasurerun-i18n-pack.zip.sha1");
  private static final Path SHA256 =
      Path.of("resourcepacks/generated/treasurerun-i18n-pack.zip.sha256");
  private static final Path WORKFLOW =
      Path.of(".github/workflows/resourcepack-sha1.yml");

  @Test
  void defaultConfigurationCannotActivateTwoDeliveryPaths() throws Exception {
    Map<String, String> standard = topLevelScalars("resourcePack");
    Map<String, String> fallback = topLevelScalars("resourcePackFallback");

    boolean standardEnabled = Boolean.parseBoolean(standard.get("enabled"));
    boolean fallbackEnabled = Boolean.parseBoolean(fallback.get("enabled"));

    assertFalse(standardEnabled && fallbackEnabled);
    assertEquals("false", standard.get("force"));
    assertFalse(fallbackEnabled);

    String url = standard.get("url");
    assertFalse(url.contains("/main/"));
    assertFalse(url.contains("raw.githubusercontent.com"));

    if (standardEnabled) {
      assertTrue(
          url.matches(
              "https://github\\.com/flowmari/TreasureRun/releases/download/"
                  + "v[^/]+/treasurerun-i18n-pack\\.zip"
          )
      );
    } else {
      assertTrue(url.isEmpty());
    }
  }

  @Test
  void trackedArtifactMatchesSha1Sha256AndConfig() throws Exception {
    assertTrue(Files.exists(ZIP));
    assertTrue(Files.exists(SHA1));
    assertTrue(Files.exists(SHA256));

    String actualSha1 = digest("SHA-1", ZIP);
    String actualSha256 = digest("SHA-256", ZIP);

    assertEquals(actualSha1, checksum(SHA1, ZIP.getFileName().toString()));
    assertEquals(actualSha256, checksum(SHA256, ZIP.getFileName().toString()));
    assertEquals(actualSha1, topLevelScalars("resourcePack").get("sha1"));
  }

  @Test
  void workflowVerifiesArtifactsWithoutCommittingToMain() throws Exception {
    String workflow = Files.readString(WORKFLOW, StandardCharsets.UTF_8);

    assertTrue(workflow.contains("contents: read"));
    assertTrue(workflow.contains("build_shared_resourcepack.py --check"));
    assertFalse(workflow.contains("contents: write"));
    assertFalse(workflow.contains("git commit"));
    assertFalse(workflow.contains("git push"));
  }

  private static Map<String, String> topLevelScalars(String section) throws Exception {
    List<String> lines = Files.readAllLines(CONFIG, StandardCharsets.UTF_8);
    Map<String, String> values = new HashMap<>();
    boolean inside = false;

    for (String line : lines) {
      if (line.equals(section + ":")) {
        inside = true;
        continue;
      }
      if (!inside) {
        continue;
      }
      if (!line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
        break;
      }
      if (!line.startsWith("  ") || line.startsWith("    ")) {
        continue;
      }

      int separator = line.indexOf(':');
      if (separator < 0) {
        continue;
      }
      String key = line.substring(2, separator).trim();
      String value = line.substring(separator + 1).trim();
      if (value.length() >= 2
          && ((value.startsWith("\"") && value.endsWith("\""))
          || (value.startsWith("'") && value.endsWith("'")))) {
        value = value.substring(1, value.length() - 1);
      }
      values.put(key, value);
    }

    assertTrue(inside, "Missing config section: " + section);
    return values;
  }

  private static String checksum(Path path, String expectedName) throws Exception {
    String[] parts = Files.readString(path, StandardCharsets.UTF_8).trim().split("\\s+");
    assertEquals(2, parts.length);
    assertEquals(expectedName, parts[1]);
    return parts[0].toLowerCase();
  }

  private static String digest(String algorithm, Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance(algorithm);
    return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
  }
}
