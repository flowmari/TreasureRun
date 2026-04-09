package plugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerLanguageStoreTest {

  @TempDir
  Path tempDir;

  private JavaPlugin mockPlugin() {
    JavaPlugin plugin = mock(JavaPlugin.class);
    when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
    when(plugin.getLogger()).thenReturn(Logger.getLogger("PlayerLanguageStoreTest"));
    return plugin;
  }

  @Test
  void set_thenGetLang_returnsSavedLanguage() {
    JavaPlugin plugin = mockPlugin();
    PlayerLanguageStore store = new PlayerLanguageStore(plugin);

    UUID uuid = UUID.randomUUID();
    store.set(uuid, "en");

    assertEquals("en", store.getLang(uuid, "ja"));
  }

  @Test
  void clear_afterSet_returnsDefaultLang() {
    JavaPlugin plugin = mockPlugin();
    PlayerLanguageStore store = new PlayerLanguageStore(plugin);

    UUID uuid = UUID.randomUUID();
    store.set(uuid, "de");
    store.clear(uuid);

    assertEquals("ja", store.getLang(uuid, "ja"));
  }

  @Test
  void uppercaseLanguage_isNormalizedToLowercase() {
    JavaPlugin plugin = mockPlugin();
    PlayerLanguageStore store = new PlayerLanguageStore(plugin);

    UUID uuid = UUID.randomUUID();
    store.set(uuid, "EN");

    assertEquals("en", store.getLang(uuid, "ja"));
  }

  @Test
  void unsavedUser_returnsDefaultLang() {
    JavaPlugin plugin = mockPlugin();
    PlayerLanguageStore store = new PlayerLanguageStore(plugin);

    UUID uuid = UUID.randomUUID();

    assertEquals("ja", store.getLang(uuid, "ja"));
    assertEquals("de", store.getLang(uuid, "de"));
  }

  @Test
  void nullOrBlankInput_doesNotBreak_andFallsBackToDefault() {
    JavaPlugin plugin = mockPlugin();
    PlayerLanguageStore store = new PlayerLanguageStore(plugin);

    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();

    store.set(uuid1, null);
    store.set(uuid2, "   ");

    assertEquals("ja", store.getLang(uuid1, "ja"));
    assertEquals("ja", store.getLang(uuid2, "ja"));
  }
}
