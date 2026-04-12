package plugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class LanguageStoreTest {

    private YamlConfiguration createConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("language.default", "ja");
        config.set("language.allowedLanguages", List.of("ja", "en", "de"));

        config.set("language.displayName.ja", "日本語");
        config.set("language.displayName.en", "English");

        config.set("language.shortLabel.ja", "JA");
        config.set("language.shortLabel.en", "EN");

        config.set("language.iconMaterial.ja", "PAPER");
        config.set("language.iconMaterial.en", "BOOK");

        config.set("language.loreDefault", "Click to select");
        config.set("language.lore.ja", "クリックして選択");

        return config;
    }

    @Test
    void reloadFromConfig_loadsAllowedLanguagesAndDefaultLang() {
        LanguageStore store = new LanguageStore();
        YamlConfiguration config = createConfig();

        try (MockedStatic<Bukkit> mocked = mockStatic(Bukkit.class)) {
            mocked.when(Bukkit::getLogger).thenReturn(Logger.getLogger("LanguageStoreTest"));
            store.reloadFromConfig(config);
        }

                assertEquals("ja", store.getDefaultLang());
            assertEquals(List.of("ja", "en", "de"), store.getAllowedLanguages());
        }

    @Test
    void reloadFromConfig_loadsDisplayNameShortLabelLoreAndIconMaterial() {
        LanguageStore store = new LanguageStore();
        YamlConfiguration config = createConfig();

        try (MockedStatic<Bukkit> mocked = mockStatic(Bukkit.class)) {
            mocked.when(Bukkit::getLogger).thenReturn(Logger.getLogger("LanguageStoreTest"));
            store.reloadFromConfig(config);
        }

                assertEquals("日本語", store.getDisplayName("ja"));
            assertEquals("English", store.getDisplayName("en"));

            assertEquals("JA", store.getShortLabel("ja"));
            assertEquals("EN", store.getShortLabel("en"));

            assertEquals("クリックして選択", store.getLore("ja"));
            assertEquals("Click to select", store.getLore("en"));

            assertEquals(Material.PAPER, store.getIconMaterial("ja"));
            assertEquals(Material.BOOK, store.getIconMaterial("en"));
        }

    @Test
    void invalidIconMaterial_fallsBackToPaper() {
        LanguageStore store = new LanguageStore();
        YamlConfiguration config = createConfig();
        config.set("language.iconMaterial.de", "NOT_A_REAL_MATERIAL");

        try (MockedStatic<Bukkit> mocked = mockStatic(Bukkit.class)) {
            mocked.when(Bukkit::getLogger).thenReturn(Logger.getLogger("LanguageStoreTest"));
            store.reloadFromConfig(config);
        }

                assertEquals(Material.PAPER, store.getIconMaterial("de"));
        }

    @Test
    void set_thenGet_returnsNormalizedPlayerLanguage() {
        LanguageStore store = new LanguageStore();
        UUID uuid = UUID.randomUUID();

        store.set(uuid, "EN");

        assertEquals("en", store.get(uuid));
    }
}