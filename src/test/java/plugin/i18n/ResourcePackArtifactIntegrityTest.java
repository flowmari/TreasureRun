package plugin.i18n;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the generated Minecraft ResourcePack artifact matches the
 * architecture claims documented in README / docs.
 *
 * This protects:
 * - ZIP / .sha1 / config.yml SHA consistency
 * - 23 generated language JSON files
 * - 8039-key coverage per language JSON
 * - important Minecraft standard UI keys
 */
class ResourcePackArtifactIntegrityTest {

    private static final Path RESOURCE_PACK_ZIP =
            Path.of("resourcepacks/generated/treasurerun-i18n-pack.zip");

    private static final Path RESOURCE_PACK_SHA1 =
            Path.of("resourcepacks/generated/treasurerun-i18n-pack.zip.sha1");

    private static final Path CONFIG_YML =
            Path.of("src/main/resources/config.yml");

    private static final int EXPECTED_LANGUAGE_JSON_COUNT = 23;
    private static final int EXPECTED_KEY_COUNT_PER_LANGUAGE = 8039;

    private static final List<String> IMPORTANT_MINECRAFT_UI_KEYS = List.of(
            "menu.singleplayer",
            "menu.multiplayer",
            "menu.options",
            "menu.quit",
            "gui.cancel",
            "multiplayer.title",
            "connect.connecting",
            "connect.encrypting"
    );

    @Test
    void resourcePackZipShaFileAndConfigShaValuesStayInSync() throws Exception {
        assertTrue(Files.exists(RESOURCE_PACK_ZIP), "ResourcePack ZIP should exist.");
        assertTrue(Files.exists(RESOURCE_PACK_SHA1), ".sha1 file should exist.");
        assertTrue(Files.exists(CONFIG_YML), "config.yml should exist.");

        String actualZipSha = sha1(RESOURCE_PACK_ZIP);
        String shaFileValue = Files.readString(RESOURCE_PACK_SHA1, StandardCharsets.UTF_8)
                .trim()
                .split("\\s+")[0];

        assertEquals(actualZipSha, shaFileValue,
                "ResourcePack ZIP SHA should match the generated .sha1 file.");

        String configText = Files.readString(CONFIG_YML, StandardCharsets.UTF_8);
        Pattern shaPattern = Pattern.compile("\\bsha1:\\s*[\"']?([0-9a-fA-F]{40})[\"']?");
        Matcher matcher = shaPattern.matcher(configText);

        List<String> configShaValues = new ArrayList<>();
        while (matcher.find()) {
            configShaValues.add(matcher.group(1));
        }

        assertEquals(EXPECTED_LANGUAGE_JSON_COUNT, configShaValues.size(),
                "config.yml should contain one ResourcePack SHA value per language/fallback entry.");

        for (String configSha : configShaValues) {
            assertEquals(actualZipSha, configSha,
                    "Every config.yml ResourcePack sha1 value should match the generated ZIP SHA.");
        }
    }

    @Test
    void resourcePackContainsExpectedLanguageJsonCoverage() throws Exception {
        assertTrue(Files.exists(RESOURCE_PACK_ZIP), "ResourcePack ZIP should exist.");

        try (ZipFile zip = new ZipFile(RESOURCE_PACK_ZIP.toFile())) {
            assertNotNull(zip.getEntry("pack.mcmeta"), "pack.mcmeta should exist.");

            List<String> langJsonEntries = Collections.list(zip.entries()).stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.startsWith("assets/minecraft/lang/"))
                    .filter(name -> name.endsWith(".json"))
                    .sorted()
                    .toList();

            assertEquals(EXPECTED_LANGUAGE_JSON_COUNT, langJsonEntries.size(),
                    "ResourcePack should contain exactly 23 language JSON files.");

            for (String entryName : langJsonEntries) {
                JsonObject json = readJsonObject(zip, entryName);

                assertEquals(EXPECTED_KEY_COUNT_PER_LANGUAGE, json.size(),
                        entryName + " should contain 8039 Minecraft language keys.");

                for (String key : IMPORTANT_MINECRAFT_UI_KEYS) {
                    assertTrue(json.has(key),
                            entryName + " should contain important Minecraft UI key: " + key);
                }
            }
        }
    }

    @Test
    void allLanguageJsonFilesUseTheSameKeyCount() throws Exception {
        try (ZipFile zip = new ZipFile(RESOURCE_PACK_ZIP.toFile())) {
            List<String> langJsonEntries = Collections.list(zip.entries()).stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.startsWith("assets/minecraft/lang/"))
                    .filter(name -> name.endsWith(".json"))
                    .sorted()
                    .toList();

            assertFalse(langJsonEntries.isEmpty(), "Language JSON files should exist.");

            List<Integer> keyCounts = new ArrayList<>();
            for (String entryName : langJsonEntries) {
                keyCounts.add(readJsonObject(zip, entryName).size());
            }

            long uniqueCountSize = keyCounts.stream().distinct().count();

            assertEquals(1, uniqueCountSize,
                    "All language JSON files should have the same key count.");
            assertEquals(EXPECTED_KEY_COUNT_PER_LANGUAGE, keyCounts.get(0),
                    "The shared key count should be 8039.");
        }
    }

    private static JsonObject readJsonObject(ZipFile zip, String entryName) throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        assertNotNull(entry, "ZIP entry should exist: " + entryName);

        try (InputStreamReader reader = new InputStreamReader(
                zip.getInputStream(entry),
                StandardCharsets.UTF_8
        )) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static String sha1(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] bytes = Files.readAllBytes(path);
        return HexFormat.of().formatHex(digest.digest(bytes));
    }
}
