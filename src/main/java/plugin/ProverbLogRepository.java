package plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.sql.ResultSet;
import java.util.UUID;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * ProverbLogRepository
 *
 * proverb_logs への保存/取得を担当するクラス
 *
 * ✅ ログ文言は “超ネイティブ英語” に統一
 * - Save success : "Proverb logged to MySQL: proverb_logs"
 * - Save fail    : "Failed to log proverb to MySQL: proverb_logs"
 * - Fetch success: "Loaded proverb logs from MySQL: proverb_logs"
 * - Fetch fail   : "Failed to load proverb logs from MySQL: proverb_logs"
 *
 * ✅ 追加：お気に入り（Favorites）
 * - ✅方式A：favorite_quotes テーブルに保存/取得（本命）
 * - ✅互換：proverb_favorites テーブルでも動く（既存環境を壊さない）
 *
 * ✅ 本命メソッド：
 * - getFavorites(conn, uuid, limit)
 */
public class ProverbLogRepository {

  private final TreasureRunMultiChestPlugin plugin;

  // =======================================================
  // ✅ Favorites Table Name（方式A：本命）
  // =======================================================
  private static final String FAVORITES_TABLE_PRIMARY = "favorite_quotes";

  // ✅ 互換用（あなたが既に使ってる名前）
  private static final String FAVORITES_TABLE_LEGACY = "proverb_favorites";

  public ProverbLogRepository(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  // =======================================================
  // ✅ CREATE TABLE（proverb_logs）
  // =======================================================
  public void createTableIfNotExists(Connection conn) {
    if (conn == null) {
      plugin.getLogger().warning("[ProverbLog] Table not created: MySQL connection is null.");
      return;
    }

    final String sql =
        "CREATE TABLE IF NOT EXISTS proverb_logs (" +
            "id INT NOT NULL AUTO_INCREMENT, " +
            "player_uuid VARCHAR(36) NOT NULL, " +
            "player_name VARCHAR(64) NOT NULL, " +
            "outcome VARCHAR(32) NOT NULL, " +
            "difficulty VARCHAR(16) NOT NULL, " +
            "lang VARCHAR(16) NOT NULL, " +
            "quote_text TEXT NOT NULL, " +
            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
            "PRIMARY KEY (id), " +
            "INDEX idx_player_uuid_created_at (player_uuid, created_at)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.executeUpdate();
      plugin.getLogger().info("[ProverbLog] CREATE TABLE IF NOT EXISTS success: proverb_logs");
    } catch (SQLException e) {
      plugin.getLogger().severe(
          "[ProverbLog] CREATE TABLE IF NOT EXISTS failed: proverb_logs\n" +
              e.getMessage()
      );
    }
  }

  // =======================================================
  // ✅ INSERT（proverb_logs 保存）
  // =======================================================
  public void insertProverbLog(Connection conn,
      UUID uuid,
      String playerName,
      String outcome,
      String difficulty,
      String lang,
      String quoteText) {

    if (conn == null) {
      plugin.getLogger().warning("[ProverbLog] Proverb not logged: MySQL connection is null.");
      return;
    }
    if (uuid == null) {
      plugin.getLogger().warning("[ProverbLog] Proverb not logged: UUID is null.");
      return;
    }
    if (quoteText == null || quoteText.isBlank()) {
      plugin.getLogger().warning("[ProverbLog] Proverb not logged: quoteText is empty.");
      return;
    }

    createTableIfNotExists(conn);

    final String sql =
        "INSERT INTO proverb_logs (player_uuid, player_name, outcome, difficulty, lang, quote_text) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    String safePlayerName = safe(playerName);
    if (safePlayerName.isBlank()) safePlayerName = "unknown";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, uuid.toString());
      ps.setString(2, safePlayerName);
      ps.setString(3, safe(outcome, "UNKNOWN"));
      ps.setString(4, safe(difficulty, "Normal"));
      ps.setString(5, safe(lang, "ja"));
      ps.setString(6, safeQuote(quoteText));

      ps.executeUpdate();

      plugin.getLogger().info(
          "Proverb logged to MySQL: proverb_logs" +
              " (uuid=" + uuid +
              ", player=" + safePlayerName +
              ", outcome=" + safe(outcome, "UNKNOWN") +
              ", difficulty=" + safe(difficulty, "Normal") +
              ", lang=" + safe(lang, "ja") +
              ")"
      );

    } catch (SQLException e) {
      plugin.getLogger().severe(
          "[ProverbLog] Failed to log proverb to MySQL: proverb_logs" +
              " (uuid=" + uuid +
              ", player=" + safePlayerName +
              ", outcome=" + safe(outcome, "UNKNOWN") +
              ", difficulty=" + safe(difficulty, "Normal") +
              ", lang=" + safe(lang, "ja") +
              ")\n" +
              e.getMessage()
      );
    }
  }

  // ✅ 互換用オーバーロード
  public void insertProverbLog(Connection conn,
      UUID uuid,
      String outcome,
      String difficulty,
      String lang,
      String quoteText) {
    insertProverbLog(conn, uuid, "unknown", outcome, difficulty, lang, quoteText);
  }

  // =======================================================
  // ✅ SELECT（proverb_logs 取得）
  // =======================================================
  public List<String> loadRecentProverbs(Connection conn, UUID uuid, int limit) {
    List<String> list = new ArrayList<>();

    if (conn == null) {
      plugin.getLogger().warning("[ProverbLog] Failed to load: MySQL connection is null.");
      return list;
    }
    if (uuid == null) {
      plugin.getLogger().warning("[ProverbLog] Failed to load: UUID is null.");
      return list;
    }

    createTableIfNotExists(conn);

    final String sql =
        "SELECT outcome, difficulty, lang, quote_text, created_at " +
            "FROM proverb_logs " +
            "WHERE player_uuid = ? " +
            "ORDER BY created_at DESC " +
            "LIMIT ?";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, uuid.toString());
      ps.setInt(2, Math.max(1, limit));

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          String outcome = rs.getString("outcome");
          String diff    = rs.getString("difficulty");
          String lang    = rs.getString("lang");
          String quote   = rs.getString("quote_text");

          String row = "【" + outcome + " / " + diff + " / " + lang + "】\n" + quote;
          list.add(row);
        }
      }

      plugin.getLogger().info(
          "Loaded proverb logs from MySQL: proverb_logs" +
              " (uuid=" + uuid + ", count=" + list.size() + ")"
      );

    } catch (SQLException e) {
      plugin.getLogger().severe(
          "[ProverbLog] Failed to load proverb logs from MySQL: proverb_logs" +
              " (uuid=" + uuid + ")\n" +
              e.getMessage()
      );
    }

    return list;
  }

  // =======================================================
  // ✅ 追加：Favorites 用テーブル作成（方式A：favorite_quotes）
  // =======================================================
  public void createFavoritesTableIfNotExists(Connection conn) {
    if (conn == null) {
      plugin.getLogger().warning("[ProverbFav] Table not created: MySQL connection is null.");
      return;
    }

    // ✅ quote_hash を UNIQUE にして「同じ格言を何回もお気に入り登録」できないようにする
    // ✅ 方式A：favorite_quotes（本命）
    final String sql =
        "CREATE TABLE IF NOT EXISTS " + FAVORITES_TABLE_PRIMARY + " (" +
            "id INT NOT NULL AUTO_INCREMENT, " +
            "player_uuid VARCHAR(36) NOT NULL, " +
            "quote_hash VARCHAR(64) NOT NULL, " +
            "outcome VARCHAR(32) NOT NULL, " +
            "difficulty VARCHAR(16) NOT NULL, " +
            "lang VARCHAR(16) NOT NULL, " +
            "quote_text TEXT NOT NULL, " +
            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
            "PRIMARY KEY (id), " +
            "UNIQUE KEY uk_player_quote (player_uuid, quote_hash), " +
            "INDEX idx_player_uuid_created_at (player_uuid, created_at)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.executeUpdate();
      plugin.getLogger().info("[ProverbFav] CREATE TABLE IF NOT EXISTS success: " + FAVORITES_TABLE_PRIMARY);
    } catch (SQLException e) {
      plugin.getLogger().severe(
          "[ProverbFav] CREATE TABLE IF NOT EXISTS failed: " + FAVORITES_TABLE_PRIMARY + "\n" +
              e.getMessage()
      );
    }

    // ✅ 互換：昔の proverb_favorites が残ってても邪魔しない
    // （環境によっては既に存在しているので、あってもOK）
    createLegacyFavoritesTableIfNotExists(conn);
  }

  // =======================================================
  // ✅ 互換：旧 favorites テーブル（proverb_favorites）も残したい場合
  // - 既存のデータが消えない
  // - 既存コードが壊れない
  // =======================================================
  private void createLegacyFavoritesTableIfNotExists(Connection conn) {
    if (conn == null) return;

    final String sql =
        "CREATE TABLE IF NOT EXISTS " + FAVORITES_TABLE_LEGACY + " (" +
            "id INT NOT NULL AUTO_INCREMENT, " +
            "player_uuid VARCHAR(36) NOT NULL, " +
            "quote_hash VARCHAR(64) NOT NULL, " +
            "outcome VARCHAR(32) NOT NULL, " +
            "difficulty VARCHAR(16) NOT NULL, " +
            "lang VARCHAR(16) NOT NULL, " +
            "quote_text TEXT NOT NULL, " +
            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
            "PRIMARY KEY (id), " +
            "UNIQUE KEY uk_player_quote (player_uuid, quote_hash), " +
            "INDEX idx_player_uuid_created_at (player_uuid, created_at)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.executeUpdate();
      plugin.getLogger().info("[ProverbFav] (legacy) Table ready: " + FAVORITES_TABLE_LEGACY);
    } catch (SQLException ignored) {
      // 互換側は失敗しても致命的ではないので黙る（壊れないため）
    }
  }

  // =======================================================
  // ✅ 追加：お気に入り登録（Favorites INSERT）
  // - 本命：favorite_quotes に入れる
  // - UNIQUE(player_uuid, quote_hash) なので重複しない
  // =======================================================
  public boolean insertFavorite(Connection conn,
      UUID uuid,
      String outcome,
      String difficulty,
      String lang,
      String quoteText) {

    if (conn == null) {
      plugin.getLogger().warning("[ProverbFav] Favorite not saved: MySQL connection is null.");
      return false;
    }
    if (uuid == null) {
      plugin.getLogger().warning("[ProverbFav] Favorite not saved: UUID is null.");
      return false;
    }
    if (quoteText == null || quoteText.isBlank()) {
      plugin.getLogger().warning("[ProverbFav] Favorite not saved: quoteText is empty.");
      return false;
    }

    createFavoritesTableIfNotExists(conn);

    String hash = sha256Hex(quoteText);
    if (hash.isBlank()) {
      plugin.getLogger().warning("[ProverbFav] Favorite not saved: quote_hash is empty.");
      return false;
    }

    // ✅ 本命：favorite_quotes
    final String sql =
        "INSERT INTO " + FAVORITES_TABLE_PRIMARY + " (player_uuid, quote_hash, outcome, difficulty, lang, quote_text) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, uuid.toString());
      ps.setString(2, hash);
      ps.setString(3, safe(outcome, "UNKNOWN"));
      ps.setString(4, safe(difficulty, "Normal"));
      ps.setString(5, safe(lang, "ja"));
      ps.setString(6, safeQuote(quoteText));

      ps.executeUpdate();

      plugin.getLogger().info(
          "Favorite saved to MySQL: " + FAVORITES_TABLE_PRIMARY +
              " (uuid=" + uuid +
              ", outcome=" + safe(outcome, "UNKNOWN") +
              ", difficulty=" + safe(difficulty, "Normal") +
              ", lang=" + safe(lang, "ja") +
              ")"
      );
      return true;

    } catch (SQLException e) {
      // ✅ だいたいここは「すでに登録済み」の時に起きる（UNIQUE制約）
      plugin.getLogger().warning(
          "[ProverbFav] Favorite not saved (maybe duplicate): " + FAVORITES_TABLE_PRIMARY +
              " (uuid=" + uuid + ")\n" +
              e.getMessage()
      );
      return false;
    }
  }

  // =======================================================
  // ✅ 追加：お気に入り削除（Favorites DELETE）
  // - 本命：favorite_quotes から削除
  // =======================================================
  public boolean deleteFavoriteById(Connection conn, UUID uuid, int favoriteId) {
    if (conn == null) {
      plugin.getLogger().warning("[ProverbFav] Favorite not removed: MySQL connection is null.");
      return false;
    }
    if (uuid == null) {
      plugin.getLogger().warning("[ProverbFav] Favorite not removed: UUID is null.");
      return false;
    }
    if (favoriteId <= 0) {
      plugin.getLogger().warning("[ProverbFav] Favorite not removed: favoriteId is invalid.");
      return false;
    }

    createFavoritesTableIfNotExists(conn);

    final String sql =
        "DELETE FROM " + FAVORITES_TABLE_PRIMARY + " " +
            "WHERE player_uuid = ? AND id = ?";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, uuid.toString());
      ps.setInt(2, favoriteId);

      int rows = ps.executeUpdate();

      plugin.getLogger().info(
          "Favorite removed from MySQL: " + FAVORITES_TABLE_PRIMARY +
              " (uuid=" + uuid + ", id=" + favoriteId + ", rows=" + rows + ")"
      );
      return rows > 0;

    } catch (SQLException e) {
      plugin.getLogger().severe(
          "[ProverbFav] Failed to remove favorite: " + FAVORITES_TABLE_PRIMARY +
              " (uuid=" + uuid + ", id=" + favoriteId + ")\n" +
              e.getMessage()
      );
      return false;
    }
  }

  // =======================================================
  // ✅ 追加：お気に入り一覧（Favorites SELECT）
  // - 本命：favorite_quotes から読む
  // =======================================================
  public List<String> loadFavorites(Connection conn, UUID uuid, int limit) {
    List<String> list = new ArrayList<>();

    if (conn == null) {
      plugin.getLogger().warning("[ProverbFav] Failed to load: MySQL connection is null.");
      return list;
    }
    if (uuid == null) {
      plugin.getLogger().warning("[ProverbFav] Failed to load: UUID is null.");
      return list;
    }

    createFavoritesTableIfNotExists(conn);

    final String sql =
        "SELECT id, outcome, difficulty, lang, quote_text, created_at " +
            "FROM " + FAVORITES_TABLE_PRIMARY + " " +
            "WHERE player_uuid = ? " +
            "ORDER BY created_at DESC " +
            "LIMIT ?";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, uuid.toString());
      ps.setInt(2, Math.max(1, limit));

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          int id         = rs.getInt("id");
          String outcome = rs.getString("outcome");
          String diff    = rs.getString("difficulty");
          String lang    = rs.getString("lang");
          String quote   = rs.getString("quote_text");

          // ✅ Book表示用：IDも出す（削除コマンドで使える）
          String row =
              "★#" + id + "\n" +
                  "【" + outcome + " / " + diff + " / " + lang + "】\n" +
                  quote;

          list.add(row);
        }
      }

      plugin.getLogger().info(
          "Loaded favorites from MySQL: " + FAVORITES_TABLE_PRIMARY +
              " (uuid=" + uuid + ", count=" + list.size() + ")"
      );

    } catch (SQLException e) {
      plugin.getLogger().severe(
          "[ProverbFav] Failed to load favorites: " + FAVORITES_TABLE_PRIMARY +
              " (uuid=" + uuid + ")\n" +
              e.getMessage()
      );

      // ✅ 万一 favorite_quotes が無い環境でも壊れない：旧テーブルから読む fallback
      List<String> fallback = loadFavoritesLegacy(conn, uuid, limit);
      if (!fallback.isEmpty()) return fallback;
    }

    return list;
  }

  // =======================================================
  // ✅ 互換：旧 favorites テーブルから読む（fallback）
  // - 既に proverb_favorites にデータがある人のため
  // =======================================================
  private List<String> loadFavoritesLegacy(Connection conn, UUID uuid, int limit) {
    List<String> list = new ArrayList<>();
    if (conn == null || uuid == null) return list;

    final String sql =
        "SELECT id, outcome, difficulty, lang, quote_text, created_at " +
            "FROM " + FAVORITES_TABLE_LEGACY + " " +
            "WHERE player_uuid = ? " +
            "ORDER BY created_at DESC " +
            "LIMIT ?";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, uuid.toString());
      ps.setInt(2, Math.max(1, limit));

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          int id         = rs.getInt("id");
          String outcome = rs.getString("outcome");
          String diff    = rs.getString("difficulty");
          String lang    = rs.getString("lang");
          String quote   = rs.getString("quote_text");

          String row =
              "★#" + id + "\n" +
                  "【" + outcome + " / " + diff + " / " + lang + "】\n" +
                  quote;
          list.add(row);
        }
      }

      plugin.getLogger().info(
          "Loaded favorites from MySQL (legacy): " + FAVORITES_TABLE_LEGACY +
              " (uuid=" + uuid + ", count=" + list.size() + ")"
      );

    } catch (SQLException ignored) {
      // 互換fallbackは失敗してもOK
    }

    return list;
  }

  // =======================================================
  // ✅ ✅ ✅ 本命メソッド：Favoritesを一覧取得する（getFavorites）
  // - あなたが欲しかった「本命」API
  // - 内部的には loadFavorites を呼ぶ（= favorite_quotes を読む）
  // =======================================================
  public List<String> getFavorites(Connection conn, UUID uuid, int limit) {
    return loadFavorites(conn, uuid, limit);
  }

  // =======================================================
  // ✅ 追加：直近1件（logsの最新）を取得 → お気に入り登録に使う
  // =======================================================
  public boolean favoriteLatestLog(Connection conn, UUID uuid) {
    if (conn == null) {
      plugin.getLogger().warning("[ProverbFav] Favorite latest failed: MySQL connection is null.");
      return false;
    }
    if (uuid == null) {
      plugin.getLogger().warning("[ProverbFav] Favorite latest failed: UUID is null.");
      return false;
    }

    createTableIfNotExists(conn);

    final String sql =
        "SELECT outcome, difficulty, lang, quote_text " +
            "FROM proverb_logs " +
            "WHERE player_uuid = ? " +
            "ORDER BY created_at DESC " +
            "LIMIT 1";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, uuid.toString());

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          plugin.getLogger().warning("[ProverbFav] Favorite latest failed: no logs found.");
          return false;
        }

        String outcome = rs.getString("outcome");
        String diff    = rs.getString("difficulty");
        String lang    = rs.getString("lang");
        String quote   = rs.getString("quote_text");

        return insertFavorite(conn, uuid, outcome, diff, lang, quote);
      }

    } catch (SQLException e) {
      plugin.getLogger().severe(
          "[ProverbFav] Favorite latest failed\n" + e.getMessage()
      );
      return false;
    }
  }

  // =======================================================
  // helpers（安全対策）
  // =======================================================
  private static String safe(String s) {
    return (s == null) ? "" : s.trim();
  }

  private static String safe(String s, String fallback) {
    String t = safe(s);
    return t.isBlank() ? fallback : t;
  }

  private static String safeQuote(String s) {
    if (s == null) return "";
    String t = s.trim();
    if (t.length() > 2000) {
      t = t.substring(0, 2000) + "…";
    }
    return t;
  }

  private static String sha256Hex(String text) {
    if (text == null) return "";
    String t = text.trim();
    if (t.isBlank()) return "";

    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] bytes = md.digest(t.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      return "";
    }
  }
}