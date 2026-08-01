package plugin;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackDeliveryBoundaryTest {
  private static final Path CONFIG = Path.of("src/main/resources/config.yml");
  private static final Path WORKFLOW = Path.of(".github/workflows/resourcepack-sha1.yml");

  @Test
  void defaultConfigurationDisablesBothPublicDeliveryPaths() throws Exception {
    String config = Files.readString(CONFIG, StandardCharsets.UTF_8);
    String standard = section(config, "resourcePack", "resourcePackFallback");
    String fallback = section(config, "resourcePackFallback", null);

    assertTrue(standard.contains("  enabled: false"));
    assertTrue(standard.contains("  url: \"\""));
    assertTrue(standard.contains("  sha1: \"\""));
    assertFalse(standard.contains("github.com/flowmari/TreasureRun/releases/download"));

    assertTrue(fallback.contains("  enabled: false"));
    assertTrue(fallback.contains("  packs: {}"));
    assertFalse(fallback.contains("github.com/flowmari/TreasureRun/releases/download"));
  }

  @Test
  void workflowIsReadOnlyAndChecksTheLocalOnlyContract() throws Exception {
    String workflow = Files.readString(WORKFLOW, StandardCharsets.UTF_8);
    assertTrue(workflow.contains("contents: read"));
    assertTrue(workflow.contains("check_local_resourcepack_contract.py"));
    assertFalse(workflow.contains("contents: write"));
    assertFalse(workflow.contains("git push"));
    assertFalse(workflow.contains("gh release"));
  }

  private static String section(String text, String start, String next) {
    String end = next == null ? "\\z" : "(?=^" + Pattern.quote(next) + ":)";
    Pattern pattern = Pattern.compile("(?ms)^" + Pattern.quote(start) + ":\\n.*?" + end);
    Matcher matcher = pattern.matcher(text);
    assertTrue(matcher.find(), "Missing config section: " + start);
    String value = matcher.group();
    assertNotNull(value);
    return value;
  }
}
