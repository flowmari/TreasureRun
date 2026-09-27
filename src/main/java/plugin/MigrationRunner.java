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

public final class MigrationRunner {
  private static final int QUERY_TIMEOUT_SECONDS = 10;
  private static final List<String> MIGRATIONS = List.of(
      "V1__create_ranking_tables.sql",
      "V2__support_monthly_seasons.sql",
      "V3__create_transactional_outbox.sql",
      "V4__adopt_legacy_runtime_schema.sql"
  );

  private final TreasureRunMultiChestPlugin plugin;

  public MigrationRunner(TreasureRunMultiChestPlugin plugin) { this.plugin = plugin; }

  public boolean runAll(Connection connection) {
    if (!plugin.isDatabaseEnabled()) {
      plugin.getLogger().info("[Migration] skipped: database disabled at startup.");
      return false;
    }
    if (connection == null) {
      plugin.getLogger().warning("[Migration] failed closed: bootstrap connection is null.");
      return false;
    }
    try {
      ensureSchemaMigrationsTable(connection);
      for (String fileName : MIGRATIONS) runOne(connection, fileName);
      plugin.getLogger().info("[Migration] completed.");
      return true;
    } catch (Exception exception) {
      plugin.getLogger().warning("[Migration] failed closed: " + detail(exception));
      return false;
    }
  }

  private void ensureSchemaMigrationsTable(Connection connection) throws Exception {
    String sql = "CREATE TABLE IF NOT EXISTS schema_migrations ("
        + "version VARCHAR(64) NOT NULL PRIMARY KEY,"
        + "description VARCHAR(255) NULL,"
        + "script VARCHAR(255) NOT NULL,"
        + "checksum CHAR(64) NOT NULL,"
        + "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    try (Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      statement.execute(sql);
    }
  }

  private void runOne(Connection connection, String fileName) throws Exception {
    String version = versionOf(fileName);
    String description = descriptionOf(fileName);
    String sql = readResource("db/migration/" + fileName);
    String checksum = sha256(sql);

    AppliedVersion applied = loadAppliedVersion(connection, version);
    if (applied != null) {
      if (!checksum.equalsIgnoreCase(applied.checksum())) {
        throw new IllegalStateException("Migration checksum mismatch for " + fileName
            + ". Applied script=" + applied.script());
      }
      plugin.getLogger().info("[Migration] already applied: " + fileName);
      return;
    }

    plugin.getLogger().info("[Migration] applying: " + fileName);
    // MySQL DDL is not treated as a rollbackable multi-statement transaction.
    // Migration SQL must therefore be rerunnable/recoverable by construction.
    for (String raw : splitSqlStatements(sql)) {
      String statementSql = raw.trim();
      if (statementSql.isEmpty()) continue;
      try (Statement statement = connection.createStatement()) {
        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        statement.execute(statementSql);
      }
    }
    recordApplied(connection, version, description, fileName, checksum);
    plugin.getLogger().info("[Migration] applied: " + fileName);
  }

  private AppliedVersion loadAppliedVersion(Connection connection, String version) throws Exception {
    String sql = "SELECT script, checksum FROM schema_migrations WHERE version=? LIMIT 1";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      statement.setString(1, version);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) return null;
        return new AppliedVersion(resultSet.getString("script"), resultSet.getString("checksum"));
      }
    }
  }

  private void recordApplied(Connection connection, String version, String description,
      String script, String checksum) throws Exception {
    String sql = "INSERT INTO schema_migrations "
        + "(version, description, script, checksum, applied_at) VALUES (?, ?, ?, ?, ?)";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      statement.setString(1, version);
      statement.setString(2, description);
      statement.setString(3, script);
      statement.setString(4, checksum);
      statement.setTimestamp(5, java.sql.Timestamp.from(Instant.now()));
      statement.executeUpdate();
    }
  }

  private String readResource(String resourcePath) throws Exception {
    try (InputStream input = plugin.getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (input == null) throw new IllegalStateException("Migration resource not found: " + resourcePath);
      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) output.append(line).append('\n');
      }
      return output.toString();
    }
  }

  private List<String> splitSqlStatements(String sql) {
    List<String> statements = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean single=false, dbl=false, lineComment=false;
    for (int i=0;i<sql.length();i++) {
      char c=sql.charAt(i), n=i+1<sql.length()?sql.charAt(i+1):'\0';
      if (lineComment) { current.append(c); if (c=='\n') lineComment=false; continue; }
      if (!single && !dbl && c=='-' && n=='-') { lineComment=true; current.append(c); continue; }
      if (c=='\'' && !dbl) { single=!single; current.append(c); continue; }
      if (c=='"' && !single) { dbl=!dbl; current.append(c); continue; }
      if (c==';' && !single && !dbl) { statements.add(current.toString()); current.setLength(0); continue; }
      current.append(c);
    }
    if (current.length()>0) statements.add(current.toString());
    return statements;
  }

  private String versionOf(String fileName) {
    int p=fileName.indexOf("__");
    return (p<=0?fileName.replace(".sql",""):fileName.substring(0,p)).toUpperCase(Locale.ROOT);
  }
  private String descriptionOf(String fileName) {
    int p=fileName.indexOf("__");
    String raw=p>=0?fileName.substring(p+2):fileName;
    return raw.replace(".sql","").replace('_',' ');
  }
  private String sha256(String text) throws Exception {
    byte[] hash=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    StringBuilder out=new StringBuilder();
    for (byte b:hash) out.append(String.format("%02x",b));
    return out.toString();
  }
  private static String detail(Exception e) {
    String m=e.getMessage(); return m==null||m.isBlank()?e.getClass().getSimpleName():m;
  }
  private record AppliedVersion(String script, String checksum) {}
}
