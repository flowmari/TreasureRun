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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies two ResourcePack integrity contracts:
 *
 * <ol>
 *     <li>The retained shared ResourcePack artifact remains internally consistent
 *         with its tracked SHA-1 file and top-level {@code resourcePack.sha1} entry.</li>
 *     <li>The vanilla fallback routing configuration matches the published,
 *         versioned GitHub prerelease asset manifest reviewed for delivery.</li>
 * </ol>
 *
 * <p>This test verifies configured routing and artifact integrity. Representative
 * in-game behavior on vanilla clients has not yet been verified; it will be
 * covered in dedicated runtime testing.</p>
 */
class ResourcePackArtifactIntegrityTest {

    private static final Path RETAINED_SHARED_RESOURCE_PACK_ZIP =
            Path.of("resourcepacks/generated/treasurerun-i18n-pack.zip");

    private static final Path RETAINED_SHARED_RESOURCE_PACK_SHA1 =
            Path.of("resourcepacks/generated/treasurerun-i18n-pack.zip.sha1");

    private static final Path CONFIG_YML =
            Path.of("src/main/resources/config.yml");

    private static final Path PUBLISHED_ROUTE_MANIFEST =
            Path.of("src/test/resources/i18n/release-assets/v0.1.2-alpha-resourcepack-fallback.sha1");

    private static final String RELEASE_TAG = "v0.1.2-alpha-resourcepack-fallback";

    private static final String RELEASE_DOWNLOAD_BASE =
            "https://github.com/flowmari/TreasureRun/releases/download/" + RELEASE_TAG + "/";

    private static final int EXPECTED_SHARED_LANGUAGE_JSON_COUNT = 24;
    private static final int EXPECTED_ROUTED_LANGUAGE_COUNT = 23;
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
    void retainedSharedZipShaFileAndTopLevelConfigShaStayInSync() throws Exception {
        assertTrue(Files.exists(RETAINED_SHARED_RESOURCE_PACK_ZIP),
                "Retained shared ResourcePack ZIP should exist.");
        assertTrue(Files.exists(RETAINED_SHARED_RESOURCE_PACK_SHA1),
                "Retained shared ResourcePack .sha1 file should exist.");
        assertTrue(Files.exists(CONFIG_YML), "config.yml should exist.");

        String actualZipSha = sha1(RETAINED_SHARED_RESOURCE_PACK_ZIP);
        String shaFileValue = Files.readString(RETAINED_SHARED_RESOURCE_PACK_SHA1, StandardCharsets.UTF_8)
                .trim()
                .split("\\s+")[0];
        String topLevelConfiguredSha = readTopLevelSharedResourcePackSha();

        assertEquals(actualZipSha, shaFileValue,
                "Retained shared ResourcePack ZIP SHA should match its tracked .sha1 file.");
        assertEquals(actualZipSha, topLevelConfiguredSha,
                "Top-level resourcePack.sha1 should continue to match the retained shared ZIP.");
    }

    @Test
    void configuredFallbackRoutesMatchPublishedReleaseManifestContract() throws Exception {
        assertTrue(Files.exists(PUBLISHED_ROUTE_MANIFEST),
                "Published routed-asset SHA-1 manifest fixture should exist.");
        assertTrue(Files.exists(CONFIG_YML), "config.yml should exist.");

        Map<String, RoutedAsset> expectedRoutes = readPublishedReleaseManifestContract();
        Map<String, RoutedAsset> configuredRoutes = readConfiguredFallbackRoutes();

        assertEquals(EXPECTED_ROUTED_LANGUAGE_COUNT, expectedRoutes.size(),
                "Published Release manifest should define exactly 23 fallback routes.");
        assertEquals(expectedRoutes, configuredRoutes,
                "resourcePackFallback.packs should match the published versioned GitHub prerelease asset manifest.");
    }

    @Test
    void retainedSharedResourcePackContainsExpectedLanguageJsonCoverage() throws Exception {
        assertTrue(Files.exists(RETAINED_SHARED_RESOURCE_PACK_ZIP),
                "Retained shared ResourcePack ZIP should exist.");

        try (ZipFile zip = new ZipFile(RETAINED_SHARED_RESOURCE_PACK_ZIP.toFile())) {
            assertNotNull(zip.getEntry("pack.mcmeta"), "pack.mcmeta should exist.");

            List<String> langJsonEntries = Collections.list(zip.entries()).stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.startsWith("assets/minecraft/lang/"))
                    .filter(name -> name.endsWith(".json"))
                    .sorted()
                    .toList();

            assertEquals(EXPECTED_SHARED_LANGUAGE_JSON_COUNT, langJsonEntries.size(),
                    "Retained shared ResourcePack should contain exactly 24 language JSON files.");

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
    void retainedSharedLanguageJsonFilesUseTheSameKeyCount() throws Exception {
        try (ZipFile zip = new ZipFile(RETAINED_SHARED_RESOURCE_PACK_ZIP.toFile())) {
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

            assertEquals(1, keyCounts.stream().distinct().count(),
                    "All retained shared ResourcePack language JSON files should have the same key count.");
            assertEquals(EXPECTED_KEY_COUNT_PER_LANGUAGE, keyCounts.get(0),
                    "The shared key count should be 8039.");
        }
    }

    private static String readTopLevelSharedResourcePackSha() throws IOException {
        Pattern sectionPattern = Pattern.compile("^resourcePack:\\s*$");
        Pattern shaPattern = Pattern.compile("^\\s{2}sha1:\\s*[\"']?([0-9a-fA-F]{40})[\"']?\\s*$");

        boolean inResourcePackSection = false;
        for (String line : Files.readAllLines(CONFIG_YML, StandardCharsets.UTF_8)) {
            if (sectionPattern.matcher(line).matches()) {
                inResourcePackSection = true;
                continue;
            }

            if (inResourcePackSection && !line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
                break;
            }

            if (inResourcePackSection) {
                Matcher matcher = shaPattern.matcher(line);
                if (matcher.matches()) {
                    return matcher.group(1);
                }
            }
        }

        throw new IllegalStateException(
                "Could not locate the top-level resourcePack.sha1 value in config.yml."
        );
    }

    private static Map<String, RoutedAsset> readConfiguredFallbackRoutes() throws IOException {
        Pattern rootPattern = Pattern.compile("^resourcePackFallback:\\s*$");
        Pattern packsPattern = Pattern.compile("^\\s{2}packs:\\s*$");
        Pattern languagePattern = Pattern.compile("^\\s{4}([a-z0-9_]+):\\s*$");
        Pattern urlPattern = Pattern.compile("^\\s{6}url:\\s*[\"']?([^\"'\\s]+)[\"']?\\s*$");
        Pattern shaPattern = Pattern.compile("^\\s{6}sha1:\\s*[\"']?([0-9a-fA-F]{40})[\"']?\\s*$");

        Map<String, RoutedAsset> routes = new LinkedHashMap<>();
        boolean inFallback = false;
        boolean inPacks = false;
        String currentLanguage = null;
        String currentUrl = null;
        String currentSha1 = null;

        for (String line : Files.readAllLines(CONFIG_YML, StandardCharsets.UTF_8)) {
            if (rootPattern.matcher(line).matches()) {
                inFallback = true;
                continue;
            }

            if (inFallback && !line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
                break;
            }

            if (!inFallback) {
                continue;
            }

            if (packsPattern.matcher(line).matches()) {
                inPacks = true;
                continue;
            }

            if (!inPacks) {
                continue;
            }

            Matcher languageMatcher = languagePattern.matcher(line);
            if (languageMatcher.matches()) {
                if (currentLanguage != null) {
                    routes.put(currentLanguage, requireCompleteRoute(currentLanguage, currentUrl, currentSha1));
                }
                currentLanguage = languageMatcher.group(1);
                currentUrl = null;
                currentSha1 = null;
                continue;
            }

            Matcher urlMatcher = urlPattern.matcher(line);
            if (urlMatcher.matches()) {
                currentUrl = urlMatcher.group(1);
                continue;
            }

            Matcher shaMatcher = shaPattern.matcher(line);
            if (shaMatcher.matches()) {
                currentSha1 = shaMatcher.group(1);
            }
        }

        if (currentLanguage != null) {
            routes.put(currentLanguage, requireCompleteRoute(currentLanguage, currentUrl, currentSha1));
        }

        return routes;
    }

    private static Map<String, RoutedAsset> readPublishedReleaseManifestContract() throws IOException {
        Pattern linePattern = Pattern.compile(
                "^([0-9a-fA-F]{40})\\s{2}treasurerun-i18n-pack-([a-z0-9_]+)\\.zip$"
        );

        Map<String, RoutedAsset> routes = new LinkedHashMap<>();

        for (String line : Files.readAllLines(PUBLISHED_ROUTE_MANIFEST, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }

            Matcher matcher = linePattern.matcher(line);
            assertTrue(matcher.matches(),
                    "Manifest line should contain a SHA-1 and routed ZIP filename: " + line);

            String sha1 = matcher.group(1);
            String language = matcher.group(2);
            String filename = "treasurerun-i18n-pack-" + language + ".zip";

            routes.put(language, new RoutedAsset(RELEASE_DOWNLOAD_BASE + filename, sha1));
        }

        return routes;
    }

    private static RoutedAsset requireCompleteRoute(
            String language,
            String url,
            String sha1
    ) {
        if (url == null || sha1 == null) {
            throw new IllegalStateException(
                    "Incomplete resourcePackFallback route for language: " + language
            );
        }
        return new RoutedAsset(url, sha1);
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

    private record RoutedAsset(String url, String sha1) {
    }
}
