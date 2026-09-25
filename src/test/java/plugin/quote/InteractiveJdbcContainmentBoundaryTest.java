package plugin.quote;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

class InteractiveJdbcContainmentBoundaryTest {

  @Test
  void bukkitFacingQuotePathsDoNotAcquireJdbcDirectly() throws Exception {
    String[] paths = {
        "src/main/java/plugin/quote/QuoteFavoriteCommand.java",
        "src/main/java/plugin/quote/QuoteRereadService.java",
        "src/main/java/plugin/QuoteFavoriteBookClickListener.java",
        "src/main/java/plugin/GameMenu.java"
    };

    for (String path : paths) {
      String source = Files.readString(Path.of(path));
      assertFalse(source.contains("getMySQLConnection("), path);
      assertFalse(source.contains("DriverManager.getConnection("), path);
      assertFalse(source.contains("PreparedStatement"), path);
      assertFalse(source.contains("executeQuery("), path);
      assertFalse(source.contains("executeUpdate("), path);
    }
  }

  @Test
  void compatibilityHelpersCannotReenterSharedConnectionPath() throws Exception {
    String source = Files.readString(
        Path.of("src/main/java/plugin/TreasureRunMultiChestPlugin.java"));

    String proverbGetter = between(
        source,
        "public List<String> getRecentProverbs(UUID playerUuid, int limit)",
        "private String resolveCurrentLang(Player player)"
    );
    assertFalse(proverbGetter.contains("getConnection("));
    assertFalse(proverbGetter.contains("getMySQLConnection("));
    assertTrue(proverbGetter.contains("recentSnapshot("));

    String proverbWriter = between(
        source,
        "public void saveProverbLog(UUID playerUuid, String playerName",
        "public List<String> getRecentProverbs(UUID playerUuid, int limit)"
    );
    assertFalse(proverbWriter.contains("getConnection("));
    assertFalse(proverbWriter.contains("executeUpdate("));
    assertTrue(proverbWriter.contains("submitTerminalProverb("));

    assertFalse(source.contains("new plugin.quote.QuoteFavoriteShortcutListener"));
  }

  @Test
  void backendOwnsBoundedShortLivedJdbc() throws Exception {
    String source = Files.readString(
        Path.of("src/main/java/plugin/quote/JdbcInteractiveProverbBackend.java"));

    assertTrue(source.contains("connectTimeout="));
    assertTrue(source.contains("socketTimeout="));
    assertTrue(source.contains("try (Connection connection = openConnection())"));
  }

  @Test
  void repositoryStatementsAreBoundedAndLegacyDirectStoresAreGone() throws Exception {
    String repository = Files.readString(Path.of("src/main/java/plugin/ProverbLogRepository.java"));
    assertTrue(repository.contains("QUERY_TIMEOUT_SECONDS = 4"));
    assertTrue(repository.contains("setQueryTimeout(QUERY_TIMEOUT_SECONDS)"));

    assertFalse(Files.exists(Path.of("src/main/java/plugin/quote/QuoteFavoriteStore.java")));
    assertFalse(Files.exists(Path.of("src/main/java/plugin/quote/QuoteFavoriteShortcutListener.java")));
  }

  @Test
  void interactiveRepositoryPropagatesInfrastructureFailuresToServiceBoundary() throws Exception {
    String repository = Files.readString(Path.of("src/main/java/plugin/ProverbLogRepository.java"));

    String recent = between(
        repository,
        "public List<String> loadRecentProverbs(Connection conn, UUID uuid, int limit)",
        "// =======================================================\n  // ✅ 追加：Favorites 用テーブル作成"
    );
    assertTrue(recent.contains("throws SQLException"));
    assertFalse(recent.contains("catch (SQLException"));

    String remove = between(
        repository,
        "public boolean deleteFavoriteById(Connection conn, UUID uuid, int favoriteId)",
        "// =======================================================\n  // ✅ 追加：お気に入り一覧"
    );
    assertTrue(remove.contains("throws SQLException"));
    assertFalse(remove.contains("catch (SQLException"));

    String favorites = between(
        repository,
        "public List<String> loadFavorites(Connection conn, UUID uuid, int limit)",
        "// =======================================================\n  // ✅ 互換：旧 favorites テーブルから読む"
    );
    assertTrue(favorites.contains("throws SQLException"));
    assertFalse(favorites.contains("catch (SQLException"));

    String latest = between(
        repository,
        "public boolean favoriteLatestLog(Connection conn, UUID uuid)",
        "// =======================================================\n  // helpers（安全対策）"
    );
    assertTrue(latest.contains("throws SQLException"));
    assertFalse(latest.contains("catch (SQLException"));

    String insert = between(
        repository,
        "public boolean insertFavorite(Connection conn,",
        "// =======================================================\n  // ✅ 追加：お気に入り削除"
    );
    assertTrue(insert.contains("throws SQLException"));
    assertTrue(insert.contains("e.getErrorCode() == 1062"));
    assertTrue(insert.contains("throw e;"));
  }

  private static String between(String source, String startNeedle, String endNeedle) {
    int start = source.indexOf(startNeedle);
    int end = source.indexOf(endNeedle, start + startNeedle.length());
    assertTrue(start >= 0, "missing start: " + startNeedle);
    assertTrue(end > start, "missing end: " + endNeedle);
    return source.substring(start, end);
  }
}
