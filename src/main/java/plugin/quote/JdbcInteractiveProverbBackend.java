package plugin.quote;

import plugin.DatabaseRuntimeSettings;
import plugin.ProverbLogRepository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Short-lived, bounded JDBC backend for interactive proverb/favorites operations. */
public final class JdbcInteractiveProverbBackend implements InteractiveProverbService.Backend {

  public static final int CONNECT_TIMEOUT_MILLIS = 3_000;
  public static final int SOCKET_TIMEOUT_MILLIS = 5_000;

  private final DatabaseRuntimeSettings settings;
  private final ProverbLogRepository repository;

  public JdbcInteractiveProverbBackend(
      DatabaseRuntimeSettings settings,
      ProverbLogRepository repository
  ) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  @Override
  public InteractiveProverbService.BookData loadBookData(
      UUID playerId,
      int recentLimit,
      int favoritesLimit
  ) throws Exception {
    try (Connection connection = openConnection()) {
      List<String> recent = repository.loadRecentProverbs(connection, playerId, recentLimit);
      List<String> favorites = repository.loadFavorites(connection, playerId, favoritesLimit);
      return new InteractiveProverbService.BookData(recent, favorites);
    }
  }

  @Override
  public List<String> loadFavorites(UUID playerId, int limit) throws Exception {
    try (Connection connection = openConnection()) {
      return repository.loadFavorites(connection, playerId, limit);
    }
  }

  @Override
  public boolean favoriteLatest(UUID playerId) throws Exception {
    try (Connection connection = openConnection()) {
      return repository.favoriteLatestLog(connection, playerId);
    }
  }

  @Override
  public boolean deleteFavorite(UUID playerId, int favoriteId) throws Exception {
    try (Connection connection = openConnection()) {
      return repository.deleteFavoriteById(connection, playerId, favoriteId);
    }
  }

  private Connection openConnection() throws Exception {
    if (!settings.enabled()) {
      throw new IllegalStateException("database disabled");
    }

    Class.forName("com.mysql.cj.jdbc.Driver");
    String url =
        "jdbc:mysql://" + settings.host() + ":" + settings.port() + "/" + settings.database()
            + "?useSSL=false"
            + "&allowPublicKeyRetrieval=true"
            + "&serverTimezone=UTC"
            + "&connectTimeout=" + CONNECT_TIMEOUT_MILLIS
            + "&socketTimeout=" + SOCKET_TIMEOUT_MILLIS;

    return DriverManager.getConnection(url, settings.user(), settings.password());
  }
}
