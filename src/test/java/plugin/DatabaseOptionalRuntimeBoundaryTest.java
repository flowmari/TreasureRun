package plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DatabaseOptionalRuntimeBoundaryTest {

  @Test
  void defaultConfigAndStartupKeepMySqlOptional() throws Exception {
    String config = read("src/main/resources/config.yml");
    String main = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String effects = read("src/main/java/plugin/TreasureRunGameEffectsPlugin.java");

    assertTrue(config.contains("database:\n  enabled: false"));
    assertTrue(
        main.contains(
            "startupDatabaseSettings = DatabaseRuntimeSettings.load(getConfig());"
        )
    );
    assertTrue(main.contains("if (isDatabaseEnabled()) {"));
    assertTrue(
        main.contains(
            "if (!isDatabaseEnabled() || !databaseSchemaReady) return null;"
        )
    );
    assertTrue(main.contains("[TreasureRun] Core runtime ready; database="));
    assertFalse(effects.contains("DriverManager.getConnection"));
  }

  @Test
  void contributorDefaultIsSpigotOnlyAndHasAReadinessGate() throws Exception {
    String compose = read("compose.contributor.yml");
    String startup = read("scripts/contributor-up.sh");

    assertFalse(compose.contains("depends_on:"));
    assertTrue(
        compose.contains(
            "TREASURERUN_DATABASE_ENABLED: \"${TREASURERUN_DATABASE_ENABLED:-false}\""
        )
    );
    assertTrue(startup.contains("YourMinecraftName --with-db"));
    assertTrue(
        startup.contains(
            "docker compose -p \"$PROJECT\" -f \"$COMPOSE_FILE\" up -d minecraft_spigot"
        )
    );
    assertTrue(
        startup.contains(
            "[TreasureRun] Core runtime ready; database=${EXPECTED_DATABASE_STATE}"
        )
    );
    assertTrue(startup.contains("Done ("));
    assertTrue(startup.contains("--volumes"));
  }

  @Test
  void DatabaseFailureKeepsCoreRuntimeAndSchemaDependentServicesFailClosed()
      throws Exception {
    String main =
        read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");

    assertTrue(
        main.contains(
            "startupDatabaseSettings = DatabaseRuntimeSettings.load(getConfig());"
        )
    );
    assertTrue(main.contains("new MigrationRunner(this).runAll(connection)"));
    assertTrue(main.contains("databaseSchemaReady = true;"));
    assertTrue(
        main.contains(
            "core gameplay will continue without MySQL-backed features"
        )
    );
    assertTrue(main.contains("databaseSchemaReady ? databaseSettings()"));
    assertTrue(main.contains("disabledDatabaseSettings(databaseSettings())"));
    assertFalse(main.contains("new MigrationRunner(this).runAll();"));
  }

  private static String read(String relativePath) throws Exception {
    return Files.readString(
        Path.of(relativePath),
        StandardCharsets.UTF_8
    );
  }
}
