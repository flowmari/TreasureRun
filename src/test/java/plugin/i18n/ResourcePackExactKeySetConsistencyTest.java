package plugin.i18n;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that every generated ResourcePack language JSON has the exact same key set.
 *
 * This is stronger than checking only the key count.
 * It protects the claim that all ResourcePack languages share the same Minecraft 1.20.1
 * standard translation-key coverage.
 */
class ResourcePackExactKeySetConsistencyTest {

    private static final Path RESOURCE_PACK_ZIP =
            Path.of("resourcepacks/generated/treasurerun-i18n-pack.zip");

    private static final String BASE_LANGUAGE_ENTRY =
            "assets/minecraft/lang/en_us.json";

    @Test
    void everyLanguageJsonUsesTheExactSameKeySetAsEnglish() throws Exception {
        try (ZipFile zip = new ZipFile(RESOURCE_PACK_ZIP.toFile())) {
            JsonObject baseJson = readJsonObject(zip, BASE_LANGUAGE_ENTRY);
            Set<String> baseKeys = new TreeSet<>(baseJson.keySet());

            assertEquals(8039, baseKeys.size(),
                    "Base English ResourcePack JSON should contain 8039 keys.");

            var langEntries = Collections.list(zip.entries()).stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.startsWith("assets/minecraft/lang/"))
                    .filter(name -> name.endsWith(".json"))
                    .sorted()
                    .toList();

            assertFalse(langEntries.isEmpty(), "ResourcePack language JSON files should exist.");

            for (String entryName : langEntries) {
                JsonObject json = readJsonObject(zip, entryName);
                Set<String> keys = new TreeSet<>(json.keySet());

                assertEquals(baseKeys, keys,
                        entryName + " should have the exact same key set as " + BASE_LANGUAGE_ENTRY);
            }
        }
    }

    private static JsonObject readJsonObject(ZipFile zip, String entryName) throws Exception {
        ZipEntry entry = zip.getEntry(entryName);
        assertNotNull(entry, "ZIP entry should exist: " + entryName);

        try (InputStreamReader reader = new InputStreamReader(
                zip.getInputStream(entry),
                StandardCharsets.UTF_8
        )) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
