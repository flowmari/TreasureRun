package plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseRuntimeSettingsTest {

  @Test
  void defaultsToDatabaseDisabled() {
    YamlConfiguration config = new YamlConfiguration();

    DatabaseRuntimeSettings settings = DatabaseRuntimeSettings.load(config, Map.of());

    assertFalse(settings.enabled());
    assertEquals("localhost", settings.host());
    assertEquals(3306, settings.port());
    assertEquals("treasureDB", settings.database());
    assertEquals("", settings.user());
    assertEquals("", settings.password());
  }

  @Test
  void environmentCanEnableAndOverrideDockerConnectionValues() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("database.enabled", false);

    DatabaseRuntimeSettings settings = DatabaseRuntimeSettings.load(
        config,
        Map.of(
            "TREASURERUN_DATABASE_ENABLED", "true",
            "TREASURERUN_DB_HOST", "minecraft_mysql",
            "TREASURERUN_DB_PORT", "3307",
            "TREASURERUN_DB_NAME", "runtime_db",
            "TREASURERUN_DB_USER", "runtime_user",
            "TREASURERUN_DB_PASSWORD", "runtime_password"
        )
    );

    assertTrue(settings.enabled());
    assertEquals("minecraft_mysql", settings.host());
    assertEquals(3307, settings.port());
    assertEquals("runtime_db", settings.database());
    assertEquals("runtime_user", settings.user());
    assertEquals("runtime_password", settings.password());
  }

  @Test
  void invalidEnvironmentValuesFallBackToConfig() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("database.enabled", true);
    config.set("database.port", 3310);

    DatabaseRuntimeSettings settings = DatabaseRuntimeSettings.load(
        config,
        Map.of(
            "TREASURERUN_DATABASE_ENABLED", "not-a-boolean",
            "TREASURERUN_DB_PORT", "70000"
        )
    );

    assertTrue(settings.enabled());
    assertEquals(3310, settings.port());
  }
}
