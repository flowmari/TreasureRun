package plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class I18nTest {

  @TempDir
  Path tempDir;

  private JavaPlugin mockPluginWithConfig(List<String> allowedLanguages) {
    JavaPlugin plugin = mock(JavaPlugin.class);

    YamlConfiguration config = new YamlConfiguration();
    config.set("language.allowedLanguages", allowedLanguages);
    config.set("language.default", "ja");

    when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
    when(plugin.getLogger()).thenReturn(Logger.getLogger("I18nTest"));
    when(plugin.getConfig()).thenReturn(config);

    return plugin;
  }

  private void writeLangFile(String lang, String content) throws Exception {
    File dir = tempDir.resolve("languages").toFile();
    if (!dir.exists()) {
      assertTrue(dir.mkdirs() || dir.exists());
    }
    File file = new File(dir, lang + ".yml");
    java.nio.file.Files.writeString(file.toPath(), content);
  }

  @Test
  void requestedLanguageValue_isReturnedWhenKeyExists() throws Exception {
    writeLangFile("en", """
default:
  unknown: "Translation missing: {key}"
menu:
  title: "&aEnglish Title"
""");

    writeLangFile("ja", """
default:
  unknown: "翻訳がありません: {key}"
menu:
  title: "&b日本語タイトル"
""");

    JavaPlugin plugin = mockPluginWithConfig(List.of("en", "ja"));
    I18n i18n = new I18n(plugin);
    i18n.loadOrCreate();

    assertEquals("§aEnglish Title", i18n.tr("en", "menu.title"));
    assertEquals("§b日本語タイトル", i18n.tr("ja", "menu.title"));
  }

  @Test
  void missingInRequestedLanguage_fallsBackToEnglish() throws Exception {
    writeLangFile("de", """
default:
  unknown: "Fehlt: {key}"
""");

    writeLangFile("en", """
default:
  unknown: "Translation missing: {key}"
menu:
  title: "English Fallback"
""");

    writeLangFile("ja", """
default:
  unknown: "翻訳がありません: {key}"
menu:
  title: "日本語フォールバック"
""");

    JavaPlugin plugin = mockPluginWithConfig(List.of("de", "en", "ja"));
    I18n i18n = new I18n(plugin);
    i18n.loadOrCreate();

    assertEquals("English Fallback", i18n.tr("de", "menu.title"));
  }

  @Test
  void missingInRequestedAndEnglish_fallsBackToJapanese() throws Exception {
    writeLangFile("fr", """
default:
  unknown: "Manquant: {key}"
""");

    writeLangFile("en", """
default:
  unknown: "Translation missing: {key}"
""");

    writeLangFile("ja", """
default:
  unknown: "翻訳がありません: {key}"
menu:
  title: "日本語のみ"
""");

    JavaPlugin plugin = mockPluginWithConfig(List.of("fr", "en", "ja"));
    I18n i18n = new I18n(plugin);
    i18n.loadOrCreate();

    assertEquals("日本語のみ", i18n.tr("fr", "menu.title"));
  }

  @Test
  void missingEverywhere_returnsDefaultUnknownMessage() throws Exception {
    writeLangFile("en", """
default:
  unknown: "Translation missing: {key}"
""");

    writeLangFile("ja", """
default:
  unknown: "翻訳がありません: {key}"
""");

    JavaPlugin plugin = mockPluginWithConfig(List.of("en", "ja"));
    I18n i18n = new I18n(plugin);
    i18n.loadOrCreate();

    assertEquals("Translation missing: menu.title", i18n.tr("it", "menu.title"));
  }

  @Test
  void placeholderReplacement_works() throws Exception {
    writeLangFile("en", """
default:
  unknown: "Translation missing: {key}"
message:
  current: "Current language: {lang}"
""");

    writeLangFile("ja", """
default:
  unknown: "翻訳がありません: {key}"
""");

    JavaPlugin plugin = mockPluginWithConfig(List.of("en", "ja"));
    I18n i18n = new I18n(plugin);
    i18n.loadOrCreate();

    String actual = i18n.tr("en", "message.current",
        I18n.Placeholder.of("{lang}", "de"));

    assertEquals("Current language: de", actual);
  }

  @Test
  void trList_returnsColoredLines() throws Exception {
    writeLangFile("en", """
default:
  unknown: "Translation missing: {key}"
help:
  lines:
    - "&aLine1"
    - "&bLine2"
""");

    writeLangFile("ja", """
default:
  unknown: "翻訳がありません: {key}"
""");

    JavaPlugin plugin = mockPluginWithConfig(List.of("en", "ja"));
    I18n i18n = new I18n(plugin);
    i18n.loadOrCreate();

    List<String> lines = i18n.trList("en", "help.lines");

    assertEquals(2, lines.size());
    assertEquals("§aLine1", lines.get(0));
    assertEquals("§bLine2", lines.get(1));
  }
}
