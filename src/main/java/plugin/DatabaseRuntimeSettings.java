package plugin;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves the optional database runtime boundary from config and environment overrides.
 *
 * <p>The default installation is database-free. Docker and production deployments may
 * opt in with {@code TREASURERUN_DATABASE_ENABLED=true} and the accompanying
 * {@code TREASURERUN_DB_*} values.</p>
 */
public record DatabaseRuntimeSettings(
    boolean enabled,
    String host,
    int port,
    String database,
    String user,
    String password
) {

  public static DatabaseRuntimeSettings load(FileConfiguration config) {
    return load(config, System.getenv());
  }

  static DatabaseRuntimeSettings load(FileConfiguration config, Map<String, String> environment) {
    boolean configuredEnabled = config.getBoolean("database.enabled", false);
    boolean enabled = parseBoolean(
        environment.get("TREASURERUN_DATABASE_ENABLED"),
        configuredEnabled
    );

    String host = firstNonBlank(
        environment.get("TREASURERUN_DB_HOST"),
        config.getString("database.host", "localhost"),
        "localhost"
    );

    int configuredPort = config.getInt("database.port", 3306);
    int port = parsePort(environment.get("TREASURERUN_DB_PORT"), configuredPort);

    String database = firstNonBlank(
        environment.get("TREASURERUN_DB_NAME"),
        config.getString("database.database", "treasureDB"),
        "treasureDB"
    );

    String user = firstNonBlank(
        environment.get("TREASURERUN_DB_USER"),
        config.getString("database.user", ""),
        ""
    );

    String password = firstNonBlank(
        environment.get("TREASURERUN_DB_PASSWORD"),
        config.getString("database.password", ""),
        ""
    );

    return new DatabaseRuntimeSettings(enabled, host, port, database, user, password);
  }

  private static boolean parseBoolean(String value, boolean fallback) {
    if (value == null || value.isBlank()) return fallback;

    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "1", "true", "yes", "on" -> true;
      case "0", "false", "no", "off" -> false;
      default -> fallback;
    };
  }

  private static int parsePort(String value, int fallback) {
    if (value == null || value.isBlank()) return fallback;

    try {
      int parsed = Integer.parseInt(value.trim());
      return parsed > 0 && parsed <= 65535 ? parsed : fallback;
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static String firstNonBlank(String first, String second, String fallback) {
    if (first != null && !first.isBlank()) return first.trim();
    if (second != null && !second.isBlank()) return second.trim();
    return fallback;
  }
}
