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

  private static final int QUERY_TIMEOUT_SECONDS = 4;

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

    final String sql =
        "INSERT INTO proverb_logs (player_uuid, player_name, outcome, difficulty, lang, quote_text) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    String safePlayerName = safe(playerName);
    if (safePlayerName.isBlank()) safePlayerName = "unknown";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

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
  public List<String> loadRecentProverbs(Connection conn, UUID uuid, int limit) throws SQLException {
    List<String> list = new ArrayList<>();

    if (conn == null) throw new SQLException("MySQL connection is null");
    if (uuid == null) throw new SQLException("UUID is null");

    final String sql =
        "SELECT outcome, difficulty, lang, quote_text, created_at " +
            "FROM proverb_logs " +
            "WHERE player_uuid = ? " +
            "ORDER BY created_at DESC " +
            "LIMIT ?";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
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
    }

    return list;
  }

  // =======================================================
  // ✅ 追加：Favorites 用テーブル作成（方式A：favorite_quotes）
  // DB-H3C: schema creation moved to versioned migrations.
  // =======================================================

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
      String quoteText) throws SQLException {

    if (conn == null) throw new SQLException("MySQL connection is null");
    if (uuid == null) throw new SQLException("UUID is null");
    if (quoteText == null || quoteText.isBlank()) return false;

    String hash = sha256Hex(quoteText);
    if (hash.isBlank()) return false;

    final String sql =
        "INSERT INTO " + FAVORITES_TABLE_PRIMARY + " (player_uuid, quote_hash, outcome, difficulty, lang, quote_text) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      ps.setString(1, uuid.toString());
      ps.setString(2, hash);
      ps.setString(3, safe(outcome, "UNKNOWN"));
      ps.setString(4, safe(difficulty, "Normal"));
      ps.setString(5, safe(lang, "ja"));
      ps.setString(6, safeQuote(quoteText));
      ps.executeUpdate();
      return true;
    } catch (SQLException e) {
      if (e.getErrorCode() == 1062) {
        return false;
      }
      throw e;
    }
  }

  // =======================================================
  // ✅ 追加：お気に入り削除（Favorites DELETE）
  // - 本命：favorite_quotes から削除
  // =======================================================
  public boolean deleteFavoriteById(Connection conn, UUID uuid, int favoriteId) throws SQLException {
    if (conn == null) throw new SQLException("MySQL connection is null");
    if (uuid == null) throw new SQLException("UUID is null");
    if (favoriteId <= 0) return false;

    final String sql =
        "DELETE FROM " + FAVORITES_TABLE_PRIMARY + " " +
            "WHERE player_uuid = ? AND id = ?";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      ps.setString(1, uuid.toString());
      ps.setInt(2, favoriteId);
      return ps.executeUpdate() > 0;
    }
  }

  // =======================================================
  // ✅ 追加：お気に入り一覧（Favorites SELECT）
  // - 本命：favorite_quotes から読む
  // =======================================================
  public List<String> loadFavorites(Connection conn, UUID uuid, int limit) throws SQLException {
    List<String> list = new ArrayList<>();

    if (conn == null) throw new SQLException("MySQL connection is null");
    if (uuid == null) throw new SQLException("UUID is null");

    final String sql =
        "SELECT id, outcome, difficulty, lang, quote_text, created_at " +
            "FROM " + FAVORITES_TABLE_PRIMARY + " " +
            "WHERE player_uuid = ? " +
            "ORDER BY created_at DESC " +
            "LIMIT ?";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
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
      ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
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
  public List<String> getFavorites(Connection conn, UUID uuid, int limit) throws SQLException {
    return loadFavorites(conn, uuid, limit);
  }

  // =======================================================
  // ✅ 追加：直近1件（logsの最新）を取得 → お気に入り登録に使う
  // =======================================================
  public boolean favoriteLatestLog(Connection conn, UUID uuid) throws SQLException {
    if (conn == null) throw new SQLException("MySQL connection is null");
    if (uuid == null) throw new SQLException("UUID is null");

    final String sql =
        "SELECT outcome, difficulty, lang, quote_text " +
            "FROM proverb_logs " +
            "WHERE player_uuid = ? " +
            "ORDER BY created_at DESC " +
            "LIMIT 1";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      ps.setString(1, uuid.toString());

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return false;

        String outcome = rs.getString("outcome");
        String diff    = rs.getString("difficulty");
        String lang    = rs.getString("lang");
        String quote   = rs.getString("quote_text");

        return insertFavorite(conn, uuid, outcome, diff, lang, quote);
      }
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