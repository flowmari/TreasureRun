package plugin.i18n;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architectural fitness function for the pure i18n package.
 *
 * plugin.i18n must remain independent from Bukkit, ProtocolLib, Fabric, and Minecraft runtime APIs.
 * Adapter classes may depend on platform APIs, but pure localization logic must not.
 */
class PureI18nPackageBoundaryTest {

  private static final Path PURE_I18N_DIR = Path.of("src/main/java/plugin/i18n");

  private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
      "import org.bukkit.",
      "import com.comphenix.protocol.",
      "import net.fabricmc.",
      "import net.minecraft."
  );

  @Test
  void pureI18nPackageDoesNotImportPlatformApis() throws Exception {
    StringBuilder violations = new StringBuilder();

    try (Stream<Path> files = Files.walk(PURE_I18N_DIR)) {
      files
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(path -> checkFile(path, violations));
    }

    assertTrue(
        violations.isEmpty(),
        "plugin.i18n must remain platform-free. Move platform access to adapter/boundary classes.\n"
            + violations
    );
  }

  private static void checkFile(Path path, StringBuilder violations) {
    try {
      List<String> lines = Files.readAllLines(path);

      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i).trim();

        for (String forbidden : FORBIDDEN_IMPORT_PREFIXES) {
          if (line.startsWith(forbidden)) {
            violations
                .append(path)
                .append(":")
                .append(i + 1)
                .append(" -> ")
                .append(line)
                .append(System.lineSeparator());
          }
        }
      }
    } catch (Exception ex) {
      throw new RuntimeException("Failed to inspect " + path, ex);
    }
  }
}
