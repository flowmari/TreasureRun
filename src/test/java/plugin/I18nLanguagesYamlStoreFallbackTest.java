package plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies TreasureRun's YAML-based i18n loading and fallback behavior.
 *
 * This protects:
 * - languages/*.yml loading from the plugin data folder
 * - requested language -> en -> ja fallback
 * - Minecraft translation keys that are both a section and a leaf via "_value"
 */
class I18nLanguagesYamlStoreFallbackTest {

    @TempDir
    Path tempDir;

    private JavaPlugin mockPluginWithAllowedLanguages(List<String> allowedLanguages) {
        JavaPlugin plugin = mock(JavaPlugin.class);

        YamlConfiguration config = new YamlConfiguration();
        config.set("language.allowedLanguages", allowedLanguages);
        config.set("language.default", "en");

        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("I18nLanguagesYamlStoreFallbackTest"));
        when(plugin.getConfig()).thenReturn(config);

        // In these tests, language files are created directly in the temp data folder.
        // Returning null makes LanguagesYamlStore avoid copying bundled resources.
        when(plugin.getResource(anyString())).thenReturn(null);

        return plugin;
    }

    private void writeLangFile(String lang, String content) throws Exception {
        File dir = tempDir.resolve("languages").toFile();
        assertTrue(dir.mkdirs() || dir.exists());
        Files.writeString(new File(dir, lang + ".yml").toPath(), content);
    }

    @Test
    void requestedLanguageFallsBackToEnglishThenJapanese() throws Exception {
        writeLangFile("en", """
default:
  unknown: "Translation missing: {key}"
messages:
  hello: "Hello from English"
""");

        writeLangFile("ja", """
default:
  unknown: "翻訳がありません: {key}"
messages:
  jaOnly: "日本語だけの文言"
""");

        JavaPlugin plugin = mockPluginWithAllowedLanguages(List.of("fr", "en", "ja"));

        I18n i18n = new I18n(plugin);
        i18n.loadOrCreate();

        assertEquals(
                "Hello from English",
                i18n.tr("fr", "messages.hello"),
                "Missing French key should fall back to English."
        );

        assertEquals(
                "日本語だけの文言",
                i18n.tr("fr", "messages.jaOnly"),
                "Missing French and English key should fall back to Japanese."
        );

        assertEquals(
                "Translation missing: messages.missing",
                i18n.tr("fr", "messages.missing"),
                "Missing key should fall back to default.unknown."
        );
    }

    @Test
    void minecraftTranslationKeyCanBeBothSectionAndLeafValue() throws Exception {
        writeLangFile("en", """
default:
  unknown: "Translation missing: {key}"
minecraft:
  packet:
    death:
      attack:
        anvil:
          _value: "{player} was squashed by a falling anvil"
          player: "{player} was squashed by a falling anvil while fighting {killer}"
""");

        writeLangFile("ja", """
default:
  unknown: "翻訳がありません: {key}"
""");

        JavaPlugin plugin = mockPluginWithAllowedLanguages(List.of("en", "ja"));

        I18n i18n = new I18n(plugin);
        i18n.loadOrCreate();

        assertEquals(
                "{player} was squashed by a falling anvil",
                i18n.tr("en", "minecraft.packet.death.attack.anvil"),
                "Parent leaf value should be resolved from _value."
        );

        assertEquals(
                "{player} was squashed by a falling anvil while fighting {killer}",
                i18n.tr("en", "minecraft.packet.death.attack.anvil.player"),
                "Child translation key should still resolve normally."
        );
    }
}
