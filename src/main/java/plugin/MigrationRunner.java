package plugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight MySQL migration runner for TreasureRun.
 *
 * Purpose:
 * - Apply bundled SQL migrations automatically at plugin startup
 * - Record applied migrations in schema_migrations
 * - Keep migration SQL under src/main/resources/db/migration/
 *
 * This is intentionally small and dependency-free.
 * It is not a full Flyway replacement, but follows the same basic idea:
 * versioned SQL files + applied-migration tracking.
 */
public class MigrationRunner {

  private final TreasureRunMultiChestPlugin plugin;

  private static final List<String> MIGRATIONS = List.of(
      "V1__create_ranking_tables.sql",
      "V2__support_monthly_seasons.sql"
  );

  public MigrationRunner(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  public void runAll() {
    boolean databaseEnabled = plugin.getConfig().getBoolean("database.enabled", true);
    if (!databaseEnabled) {
      plugin.getLogger().info("[Migration] skipped: database.enabled=false");
      return;
    }

    Connection con = plugin.getConnection();
    if (con == null) {
      plugin.getLogger().warning("[Migration] skipped: MySQL connection is null.");
      return;
    }

    try {
      ensureSchemaMigrationsTable(con);

      for (String fileName : MIGRATIONS) {
        runOne(con, fileName);
      }

      plugin.getLogger().info("[Migration] completed.");
    } catch (Exception e) {
      plugin.getLogger().warning("[Migration] failed: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void ensureSchemaMigrationsTable(Connection con) throws Exception {
    String sql = ""
        + "CREATE TABLE IF NOT EXISTS schema_migrations ("
        + "  version VARCHAR(64) NOT NULL PRIMARY KEY,"
        + "  description VARCHAR(255) NULL,"
        + "  script VARCHAR(255) NOT NULL,"
        + "  checksum CHAR(64) NOT NULL,"
        + "  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

    try (Statement st = con.createStatement()) {
      st.execute(sql);
    }
  }

  private void runOne(Connection con, String fileName) throws Exception {
    String version = versionOf(fileName);
    String description = descriptionOf(fileName);
    String resourcePath = "db/migration/" + fileName;

    String sql = readResource(resourcePath);
    String checksum = sha256(sql);

    if (isAlreadyApplied(con, version, checksum)) {
      plugin.getLogger().info("[Migration] already applied: " + fileName);
      return;
    }

    if (isVersionAppliedWithDifferentChecksum(con, version, checksum)) {
      throw new IllegalStateException(
          "Migration checksum mismatch for " + fileName
              + ". The same version was already applied with different SQL."
      );
    }

    plugin.getLogger().info("[Migration] applying: " + fileName);

    boolean originalAutoCommit = con.getAutoCommit();
    con.setAutoCommit(false);

    try {
      for (String statement : splitSqlStatements(sql)) {
        String trimmed = statement.trim();
        if (trimmed.isEmpty()) continue;
        try (Statement st = con.createStatement()) {
          st.execute(trimmed);
        }
      }

      recordApplied(con, version, description, fileName, checksum);
      con.commit();

      plugin.getLogger().info("[Migration] applied: " + fileName);
    } catch (Exception e) {
      con.rollback();
      throw e;
    } finally {
      con.setAutoCommit(originalAutoCommit);
    }
  }

  private boolean isAlreadyApplied(Connection con, String version, String checksum) throws Exception {
    String sql = "SELECT checksum FROM schema_migrations WHERE version=? LIMIT 1";
    try (PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, version);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() && checksum.equalsIgnoreCase(rs.getString("checksum"));
      }
    }
  }

  private boolean isVersionAppliedWithDifferentChecksum(Connection con, String version, String checksum) throws Exception {
    String sql = "SELECT checksum FROM schema_migrations WHERE version=? LIMIT 1";
    try (PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, version);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() && !checksum.equalsIgnoreCase(rs.getString("checksum"));
      }
    }
  }

  private void recordApplied(Connection con, String version, String description, String script, String checksum) throws Exception {
    String sql = ""
        + "INSERT INTO schema_migrations (version, description, script, checksum, applied_at) "
        + "VALUES (?, ?, ?, ?, ?)";

    try (PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, version);
      ps.setString(2, description);
      ps.setString(3, script);
      ps.setString(4, checksum);
      ps.setTimestamp(5, java.sql.Timestamp.from(Instant.now()));
      ps.executeUpdate();
    }
  }

  private String readResource(String resourcePath) throws Exception {
    ClassLoader cl = plugin.getClass().getClassLoader();

    try (InputStream in = cl.getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IllegalStateException("Migration resource not found: " + resourcePath);
      }

      StringBuilder sb = new StringBuilder();
      try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) {
          sb.append(line).append('\n');
        }
      }
      return sb.toString();
    }
  }

  private List<String> splitSqlStatements(String sql) {
    List<String> statements = new ArrayList<>();
    StringBuilder current = new StringBuilder();

    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    boolean inLineComment = false;

    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

      if (inLineComment) {
        current.append(c);
        if (c == '\n') inLineComment = false;
        continue;
      }

      if (!inSingleQuote && !inDoubleQuote && c == '-' && next == '-') {
        inLineComment = true;
        current.append(c);
        continue;
      }

      if (c == '\'' && !inDoubleQuote) {
        inSingleQuote = !inSingleQuote;
        current.append(c);
        continue;
      }

      if (c == '"' && !inSingleQuote) {
        inDoubleQuote = !inDoubleQuote;
        current.append(c);
        continue;
      }

      if (c == ';' && !inSingleQuote && !inDoubleQuote) {
        statements.add(current.toString());
        current.setLength(0);
        continue;
      }

      current.append(c);
    }

    if (current.length() > 0) {
      statements.add(current.toString());
    }

    return statements;
  }

  private String versionOf(String fileName) {
    int idx = fileName.indexOf("__");
    if (idx <= 0) return fileName.replace(".sql", "");
    return fileName.substring(0, idx).toUpperCase(Locale.ROOT);
  }

  private String descriptionOf(String fileName) {
    int start = fileName.indexOf("__");
    String raw = start >= 0 ? fileName.substring(start + 2) : fileName;
    raw = raw.replace(".sql", "");
    return raw.replace('_', ' ');
  }

  private String sha256(String text) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));

    StringBuilder sb = new StringBuilder();
    for (byte b : hash) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
