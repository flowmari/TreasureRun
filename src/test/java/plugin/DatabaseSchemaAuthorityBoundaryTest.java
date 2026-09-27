package plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DatabaseSchemaAuthorityBoundaryTest {
  private static final Path ROOT = Path.of(".");

  @Test void RuntimeCodeDoesNotOwnSchemaDdl() throws Exception {
    String p=Files.readString(ROOT.resolve("src/main/java/plugin/TreasureRunMultiChestPlugin.java"));
    String r=Files.readString(ROOT.resolve("src/main/java/plugin/ProverbLogRepository.java"));
    assertFalse(p.contains("CREATE TABLE IF NOT EXISTS scores"));
    assertFalse(p.contains("CREATE TABLE IF NOT EXISTS proverb_logs"));
    assertFalse(p.contains("ALTER TABLE scores"));
    assertFalse(p.contains("migrateScoresTable("));
    assertFalse(r.contains("CREATE TABLE"));
    assertFalse(r.contains("ALTER TABLE"));
  }

  @Test void MigrationFailureIsObservableAndV4OwnsLegacySchema() throws Exception {
    String m=Files.readString(ROOT.resolve("src/main/java/plugin/MigrationRunner.java"));
    String p=Files.readString(ROOT.resolve("src/main/java/plugin/TreasureRunMultiChestPlugin.java"));
    String v=Files.readString(ROOT.resolve("src/main/resources/db/migration/V4__adopt_legacy_runtime_schema.sql"));
    assertTrue(m.contains("V4__adopt_legacy_runtime_schema.sql"));
    assertTrue(m.contains("public boolean runAll(Connection connection)"));
    assertTrue(m.contains("[Migration] failed closed:"));
    assertFalse(m.contains("setAutoCommit(false)"));
    assertFalse(m.contains(".rollback()"));
    assertTrue(p.contains("new MigrationRunner(this).runAll(connection)"));
    assertTrue(p.contains("databaseSchemaReady = true;"));
    assertTrue(v.contains("INFORMATION_SCHEMA.COLUMNS"));
    assertTrue(v.contains("INFORMATION_SCHEMA.STATISTICS"));
    assertTrue(v.contains("INSERT IGNORE INTO favorite_quotes"));
    assertTrue(v.contains("PREPARE tr_h3c_stmt"));
    assertTrue(v.contains("MODIFY COLUMN player_name VARCHAR(64) NOT NULL"));
    assertTrue(v.contains("MODIFY COLUMN outcome VARCHAR(32) NOT NULL"));
    assertTrue(v.contains("MODIFY COLUMN difficulty VARCHAR(16) NOT NULL"));
    assertTrue(v.contains("MODIFY COLUMN lang VARCHAR(16) NOT NULL"));
    assertFalse(v.contains("DROP TABLE"));
  }

  @Test void SharedConnectionIsBoundedAndDbReloadIsRestartRequired() throws Exception {
    String p=Files.readString(ROOT.resolve("src/main/java/plugin/TreasureRunMultiChestPlugin.java"));
    assertTrue(p.contains("SHARED_DB_CONNECT_TIMEOUT_MILLIS = 3_000"));
    assertTrue(p.contains("SHARED_DB_SOCKET_TIMEOUT_MILLIS = 5_000"));
    assertTrue(p.contains("&connectTimeout="));
    assertTrue(p.contains("&socketTimeout="));
    assertTrue(p.contains("database topology is restart-required"));
  }

  @Test void DeadLegacyAuthorityStaysRemoved() {
    assertTrue(Files.notExists(ROOT.resolve("src/main/java/plugin/MySQLManager.java")));
  }
}
