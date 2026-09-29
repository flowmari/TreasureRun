package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Testcontainers
class MigrationRunnerMySqlIntegrationTest {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.36")
          .withDatabaseName("treasurerun_h3c_migration")
          .withUsername("treasurerun")
          .withPassword("treasurerun");

  @BeforeEach
  void resetSchema() throws Exception {
    try (Connection connection = openConnection();
         Statement statement = connection.createStatement()) {
      statement.execute("SET FOREIGN_KEY_CHECKS=0");
      statement.execute("DROP TABLE IF EXISTS outbox_events");
      statement.execute("DROP TABLE IF EXISTS season_scores");
      statement.execute("DROP TABLE IF EXISTS seasons");
      statement.execute("DROP TABLE IF EXISTS alltime_scores");
      statement.execute("DROP TABLE IF EXISTS favorite_quotes");
      statement.execute("DROP TABLE IF EXISTS proverb_favorites");
      statement.execute("DROP TABLE IF EXISTS proverb_logs");
      statement.execute("DROP TABLE IF EXISTS scores");
      statement.execute("DROP TABLE IF EXISTS schema_migrations");
      statement.execute("SET FOREIGN_KEY_CHECKS=1");
    }
  }

  @Test
  void freshDatabaseAppliesCompleteSchema() throws Exception {
    try (Connection connection = openConnection()) {
      assertTrue(newRunner().runAll(connection));
      assertEquals(4, count(connection, "SELECT COUNT(*) FROM schema_migrations"));
      assertTrue(tableExists(connection, "scores"));
      assertTrue(tableExists(connection, "proverb_logs"));
      assertTrue(tableExists(connection, "favorite_quotes"));
      assertTrue(columnExists(connection, "scores", "uuid"));
      assertTrue(columnExists(connection, "scores", "lang_code"));
      assertTrue(columnExists(connection, "scores", "played_at"));
      assertTrue(indexExists(connection, "scores", "idx_scores_played_at"));
      assertTrue(indexExists(connection, "scores", "idx_scores_diff_time"));
      assertTrue(indexExists(connection, "scores", "idx_scores_uuid_played"));
    }
  }

  @Test
  void legacyRuntimeSchemaUpgradesAndPreservesFavoriteData() throws Exception {
    UUID playerId = UUID.fromString("70000000-0000-0000-0000-000000000001");

    try (Connection connection = openConnection();
         Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE TABLE scores ("
              + "id INT AUTO_INCREMENT PRIMARY KEY,"
              + "player_name VARCHAR(50) NOT NULL,"
              + "score INT NOT NULL,"
              + "time BIGINT NOT NULL,"
              + "difficulty VARCHAR(10) NOT NULL"
              + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
      );

      statement.execute(
          "CREATE TABLE proverb_logs ("
              + "id INT AUTO_INCREMENT PRIMARY KEY,"
              + "player_uuid VARCHAR(36) NOT NULL,"
              + "player_name VARCHAR(50) NOT NULL,"
              + "outcome VARCHAR(20) NOT NULL,"
              + "difficulty VARCHAR(10) NOT NULL,"
              + "lang VARCHAR(10) NOT NULL,"
              + "quote_text TEXT NOT NULL,"
              + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
              + "INDEX idx_player_uuid_created_at (player_uuid, created_at)"
              + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
      );

      statement.executeUpdate(
          "INSERT INTO proverb_logs "
              + "(player_uuid, player_name, outcome, difficulty, lang, quote_text) VALUES "
              + "('70000000-0000-0000-0000-000000000001',"
              + "'LegacyPlayer','TIME_UP','Normal','en',"
              + "'Legacy log must survive migration')"
      );

      statement.execute(
          "CREATE TABLE proverb_favorites ("
              + "id INT NOT NULL AUTO_INCREMENT,"
              + "player_uuid VARCHAR(36) NOT NULL,"
              + "quote_hash VARCHAR(64) NOT NULL,"
              + "outcome VARCHAR(32) NOT NULL,"
              + "difficulty VARCHAR(16) NOT NULL,"
              + "lang VARCHAR(16) NOT NULL,"
              + "quote_text TEXT NOT NULL,"
              + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
              + "PRIMARY KEY (id),"
              + "UNIQUE KEY uk_player_quote (player_uuid, quote_hash),"
              + "INDEX idx_player_uuid_created_at (player_uuid, created_at)"
              + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
      );

      try (PreparedStatement insert = connection.prepareStatement(
          "INSERT INTO proverb_favorites "
              + "(player_uuid, quote_hash, outcome, difficulty, lang, quote_text) "
              + "VALUES (?, ?, ?, ?, ?, ?)")) {
        insert.setString(1, playerId.toString());
        insert.setString(2, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        insert.setString(3, "SUCCESS");
        insert.setString(4, "Normal");
        insert.setString(5, "en");
        insert.setString(6, "Legacy quote must survive migration");
        insert.executeUpdate();
      }

      assertTrue(newRunner().runAll(connection));
      assertTrue(columnExists(connection, "scores", "uuid"));
      assertTrue(columnExists(connection, "scores", "lang_code"));
      assertTrue(columnExists(connection, "scores", "played_at"));
      assertTrue(indexExists(connection, "scores", "idx_scores_played_at"));
      assertTrue(indexExists(connection, "scores", "idx_scores_diff_time"));
      assertTrue(indexExists(connection, "scores", "idx_scores_uuid_played"));

      assertEquals(
          64,
          characterMaximumLength(connection, "proverb_logs", "player_name")
      );
      assertEquals(
          32,
          characterMaximumLength(connection, "proverb_logs", "outcome")
      );
      assertEquals(
          16,
          characterMaximumLength(connection, "proverb_logs", "difficulty")
      );
      assertEquals(
          16,
          characterMaximumLength(connection, "proverb_logs", "lang")
      );
      assertEquals(
          1,
          count(
              connection,
              "SELECT COUNT(*) FROM proverb_logs "
                  + "WHERE quote_text='Legacy log must survive migration'"
          )
      );

      assertTrue(tableExists(connection, "proverb_favorites"));
      assertEquals(
          1,
          count(connection,
              "SELECT COUNT(*) FROM favorite_quotes "
                  + "WHERE player_uuid='" + playerId + "' "
                  + "AND quote_text='Legacy quote must survive migration'")
      );
    }
  }

  @Test
  void v1ToV3AppliedAndPartialV4RecoversOnRerun() throws Exception {
    try (Connection connection = openConnection();
         Statement statement = connection.createStatement()) {
      assertTrue(newRunner().runAll(connection));

      statement.executeUpdate("DELETE FROM schema_migrations WHERE version='V4'");
      statement.execute("ALTER TABLE scores DROP INDEX idx_scores_uuid_played");
      statement.execute("DROP TABLE favorite_quotes");
      statement.execute(
          "CREATE TABLE IF NOT EXISTS proverb_favorites ("
              + "id INT NOT NULL AUTO_INCREMENT,"
              + "player_uuid VARCHAR(36) NOT NULL,"
              + "quote_hash VARCHAR(64) NOT NULL,"
              + "outcome VARCHAR(32) NOT NULL,"
              + "difficulty VARCHAR(16) NOT NULL,"
              + "lang VARCHAR(16) NOT NULL,"
              + "quote_text TEXT NOT NULL,"
              + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
              + "PRIMARY KEY (id),"
              + "UNIQUE KEY uk_player_quote (player_uuid, quote_hash),"
              + "INDEX idx_player_uuid_created_at (player_uuid, created_at)"
              + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
      );
      statement.executeUpdate(
          "INSERT IGNORE INTO proverb_favorites "
              + "(player_uuid, quote_hash, outcome, difficulty, lang, quote_text) VALUES "
              + "('70000000-0000-0000-0000-000000000002',"
              + "'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',"
              + "'SUCCESS','Normal','en','Partial V4 recovery quote')"
      );

      assertTrue(newRunner().runAll(connection));
      assertEquals(4, count(connection, "SELECT COUNT(*) FROM schema_migrations"));
      assertTrue(indexExists(connection, "scores", "idx_scores_uuid_played"));
      assertTrue(tableExists(connection, "favorite_quotes"));
      assertEquals(
          1,
          count(connection,
              "SELECT COUNT(*) FROM favorite_quotes "
                  + "WHERE quote_text='Partial V4 recovery quote'")
      );

      assertTrue(newRunner().runAll(connection));
      assertEquals(
          1,
          count(connection,
              "SELECT COUNT(*) FROM favorite_quotes "
                  + "WHERE quote_text='Partial V4 recovery quote'")
      );
    }
  }

  @Test
  void checksumMismatchFailsClosedWithoutRewritingHistory() throws Exception {
    try (Connection connection = openConnection();
         Statement statement = connection.createStatement()) {
      assertTrue(newRunner().runAll(connection));

      statement.executeUpdate(
          "UPDATE schema_migrations SET checksum=REPEAT('0', 64) WHERE version='V4'"
      );

      assertFalse(newRunner().runAll(connection));
      assertEquals(
          1,
          count(connection,
              "SELECT COUNT(*) FROM schema_migrations "
                  + "WHERE version='V4' AND checksum=REPEAT('0', 64)")
      );
      assertEquals(4, count(connection, "SELECT COUNT(*) FROM schema_migrations"));
    }
  }

  private static MigrationRunner newRunner() {
    TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
    when(plugin.isDatabaseEnabled()).thenReturn(true);
    when(plugin.getLogger()).thenReturn(quietLogger());
    return new MigrationRunner(plugin);
  }

  private static Connection openConnection() throws Exception {
    return DriverManager.getConnection(
        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()
    );
  }

  private static boolean tableExists(Connection connection, String table) throws Exception {
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
      ps.setString(1, table);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1) == 1;
      }
    }
  }

  private static boolean columnExists(Connection connection, String table, String column)
      throws Exception {
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?")) {
      ps.setString(1, table);
      ps.setString(2, column);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1) == 1;
      }
    }
  }

  private static int characterMaximumLength(
      Connection connection,
      String table,
      String column
  ) throws Exception {
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?")) {
      ps.setString(1, table);
      ps.setString(2, column);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          throw new IllegalStateException(
              "Missing column while reading CHARACTER_MAXIMUM_LENGTH: "
                  + table + "." + column
          );
        }
        return rs.getInt(1);
      }
    }
  }

  private static boolean indexExists(Connection connection, String table, String index)
      throws Exception {
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS "
            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?")) {
      ps.setString(1, table);
      ps.setString(2, index);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1) > 0;
      }
    }
  }

  private static int count(Connection connection, String sql) throws Exception {
    try (Statement statement = connection.createStatement();
         ResultSet rs = statement.executeQuery(sql)) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private static Logger quietLogger() {
    Logger logger = Logger.getLogger(
        MigrationRunnerMySqlIntegrationTest.class.getName() + "." + UUID.randomUUID()
    );
    logger.setUseParentHandlers(false);
    logger.setLevel(Level.OFF);
    return logger;
  }
}
