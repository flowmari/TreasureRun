package plugin.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects the mapping between TreasureRun internal language codes and
 * Minecraft locale file names.
 *
 * Example:
 * - ojp       -> ojp_jp
 * - sa        -> sa_in
 * - asl_gloss -> asl_us
 * - ang       -> ang_gb
 * - non       -> non_is
 */
class LanguageCodeMappingIntegrityTest {

    private static final Path LANG_MAP =
            Path.of("src/main/resources/lang-map.yml");

    private static final Path RESOURCE_PACK_SOURCE_LANG_DIR =
            Path.of("resourcepacks/treasurerun-i18n-pack/assets/minecraft/lang");

    private static final Path RESOURCE_PACK_ZIP =
            Path.of("resourcepacks/generated/treasurerun-i18n-pack.zip");

    @Test
    void importantInternalLanguageCodesMapToExpectedMinecraftLocales() throws Exception {
        Map<String, String> mappings = readSimpleMappings();

        assertEquals("ojp_jp", mappings.get("ojp"));
        assertEquals("sa_in", mappings.get("sa"));
        assertEquals("asl_us", mappings.get("asl_gloss"));
        assertEquals("ang_gb", mappings.get("ang"));
        assertEquals("non_is", mappings.get("non"));
        assertEquals("zh_tw", mappings.get("zh_tw"));
        assertEquals("en_us", mappings.get("en"));
        assertEquals("ja_jp", mappings.get("ja"));
    }

    @Test
    void everyMappedMinecraftLocaleExistsInSourceResourcePackAndGeneratedZip() throws Exception {
        Map<String, String> mappings = readSimpleMappings();

        assertTrue(mappings.size() >= 20,
                "lang-map.yml should define the expected TreasureRun language mappings.");

        try (ZipFile zip = new ZipFile(RESOURCE_PACK_ZIP.toFile())) {
            for (Map.Entry<String, String> mapping : mappings.entrySet()) {
                String treasureRunLang = mapping.getKey();
                String minecraftLocale = mapping.getValue();

                Path sourceJson = RESOURCE_PACK_SOURCE_LANG_DIR.resolve(minecraftLocale + ".json");
                assertTrue(Files.exists(sourceJson),
                        treasureRunLang + " should have source ResourcePack JSON: " + sourceJson);

                String zipEntry = "assets/minecraft/lang/" + minecraftLocale + ".json";
                assertNotNull(zip.getEntry(zipEntry),
                        treasureRunLang + " should have generated ZIP ResourcePack JSON: " + zipEntry);
            }
        }
    }

    private static Map<String, String> readSimpleMappings() throws Exception {
        Map<String, String> mappings = new LinkedHashMap<>();
        boolean inMappings = false;

        for (String rawLine : Files.readAllLines(LANG_MAP, StandardCharsets.UTF_8)) {
            String line = rawLine;
            int commentIndex = line.indexOf("#");
            if (commentIndex >= 0) {
                line = line.substring(0, commentIndex);
            }

            if (line.trim().equals("mappings:")) {
                inMappings = true;
                continue;
            }

            if (!inMappings || line.isBlank()) {
                continue;
            }

            if (!line.startsWith("  ")) {
                break;
            }

            String trimmed = line.trim();
            String[] parts = trimmed.split(":", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    mappings.put(key, value);
                }
            }
        }

        return mappings;
    }
}
