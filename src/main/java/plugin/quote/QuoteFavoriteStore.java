package plugin.quote;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class QuoteFavoriteStore {

  // ✅ あなたのテーブル構造に合わせたRow
  public static class FavoriteRow {
    public final int id;
    public final UUID playerUuid;
    public final String quoteHash;
    public final String outcome;     // SUCCESS / TIME_UP など
    public final String difficulty;  // Easy / Normal / Hard など
    public final String lang;        // ja/en/...
    public final String quoteText;
    public final Instant createdAt;

    public FavoriteRow(int id, UUID playerUuid, String quoteHash, String outcome,
        String difficulty, String lang, String quoteText, Instant createdAt) {
      this.id = id;
      this.playerUuid = playerUuid;
      this.quoteHash = quoteHash;
      this.outcome = outcome;
      this.difficulty = difficulty;
      this.lang = lang;
      this.quoteText = quoteText;
      this.createdAt = createdAt;
    }
  }

  private final JavaPlugin plugin;

  // DB接続情報
  private String host;
  private int port;
  private String database;
  private String user;
  private String password;

  // ✅ Favorites保存先テーブル（どちらでもOK）
  private String favoritesTable = "proverb_favorites"; // ←基本はこれ推奨

  public QuoteFavoriteStore(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  // ✅ /treasureReload で呼ぶ用
  public void reload(FileConfiguration config) {
    // あなたの config.yml に合わせてキーを揃えてね
    this.host = config.getString("mysql.host", "127.0.0.1");
    this.port = config.getInt("mysql.port", 3307);
    this.database = config.getString("mysql.database", "treasureDB");
    this.user = config.getString("mysql.user", "user");
    this.password = config.getString("mysql.password", "password");

    // テーブル名を config で変えられるようにしてもOK
    this.favoritesTable = config.getString("favorites.table", "proverb_favorites");

    plugin.getLogger().info("[QuoteFavoriteStore] Reloaded DB: host=" + host + ":" + port
        + " db=" + database + " table=" + favoritesTable);
  }

  private Connection getConn() throws SQLException {
    String url = "jdbc:mysql://" + host + ":" + port + "/" + database
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    return DriverManager.getConnection(url, user, password);
  }

  // ✅ quote_hash を生成（64文字）
  public String computeQuoteHash(String quoteText, String outcome, String difficulty, String lang) {
    try {
      String base = (quoteText == null ? "" : quoteText.trim())
          + "|" + (outcome == null ? "" : outcome.trim())
          + "|" + (difficulty == null ? "" : difficulty.trim())
          + "|" + (lang == null ? "" : lang.trim());

      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(base.getBytes(StandardCharsets.UTF_8));

      StringBuilder sb = new StringBuilder();
      for (byte b : digest) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) {
      // 万一SHAが使えない場合の保険
      return UUID.randomUUID().toString().replace("-", "");
    }
  }

  // ✅ すでに同じお気に入りが存在するか（player_uuid + quote_hash）
  public boolean exists(UUID playerUuid, String quoteHash) {
    String sql = "SELECT 1 FROM " + favoritesTable + " WHERE player_uuid=? AND quote_hash=? LIMIT 1";
    try (Connection conn = getConn();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, playerUuid.toString());
      ps.setString(2, quoteHash);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    } catch (Exception e) {
      plugin.getLogger().warning("[QuoteFavoriteStore] exists failed: " + e.getMessage());
      return false;
    }
  }

  // ✅ INSERT（Favorites保存）
  public boolean addFavorite(UUID playerUuid, String outcome, String difficulty, String lang, String quoteText) {
    String quoteHash = computeQuoteHash(quoteText, outcome, difficulty, lang);

    // 二重登録防止
    if (exists(playerUuid, quoteHash)) return false;

    String sql = "INSERT INTO " + favoritesTable
        + " (player_uuid, quote_hash, outcome, difficulty, lang, quote_text) "
        + "VALUES (?, ?, ?, ?, ?, ?)";

    try (Connection conn = getConn();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, playerUuid.toString());
      ps.setString(2, quoteHash);
      ps.setString(3, safe(outcome, "OTHER"));
      ps.setString(4, safe(difficulty, "Normal"));
      ps.setString(5, safe(lang, "ja"));
      ps.setString(6, safe(quoteText, ""));

      int rows = ps.executeUpdate();
      return rows > 0;

    } catch (Exception e) {
      plugin.getLogger().warning("[QuoteFavoriteStore] addFavorite failed: " + e.getMessage());
      return false;
    }
  }

  // ✅ DELETE（player_uuid + quote_hash で削除）
  public boolean removeFavorite(UUID playerUuid, String quoteHash) {
    String sql = "DELETE FROM " + favoritesTable + " WHERE player_uuid=? AND quote_hash=?";
    try (Connection conn = getConn();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, playerUuid.toString());
      ps.setString(2, quoteHash);

      int rows = ps.executeUpdate();
      return rows > 0;

    } catch (Exception e) {
      plugin.getLogger().warning("[QuoteFavoriteStore] removeFavorite failed: " + e.getMessage());
      return false;
    }
  }

  // ✅ DELETE（idで削除）※図鑑UIにidを埋め込む場合に便利
  public boolean removeFavoriteById(UUID playerUuid, int id) {
    String sql = "DELETE FROM " + favoritesTable + " WHERE player_uuid=? AND id=?";
    try (Connection conn = getConn();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, playerUuid.toString());
      ps.setInt(2, id);

      int rows = ps.executeUpdate();
      return rows > 0;

    } catch (Exception e) {
      plugin.getLogger().warning("[QuoteFavoriteStore] removeFavoriteById failed: " + e.getMessage());
      return false;
    }
  }

  // ✅ SELECT（一覧）
  public List<FavoriteRow> listFavorites(UUID playerUuid, int limit) {
    List<FavoriteRow> out = new ArrayList<>();

    String sql = "SELECT id, player_uuid, quote_hash, outcome, difficulty, lang, quote_text, created_at "
        + "FROM " + favoritesTable + " WHERE player_uuid=? "
        + "ORDER BY created_at DESC LIMIT ?";

    try (Connection conn = getConn();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, playerUuid.toString());
      ps.setInt(2, Math.max(1, limit));

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          int id = rs.getInt("id");
          String uuidStr = rs.getString("player_uuid");
          String quoteHash = rs.getString("quote_hash");
          String outcome = rs.getString("outcome");
          String difficulty = rs.getString("difficulty");
          String lang = rs.getString("lang");
          String quoteText = rs.getString("quote_text");

          Timestamp ts = rs.getTimestamp("created_at");
          Instant createdAt = (ts == null) ? Instant.now() : ts.toInstant();

          UUID uuid = UUID.fromString(uuidStr);

          out.add(new FavoriteRow(
              id, uuid, quoteHash,
              safe(outcome, "OTHER"),
              safe(difficulty, "Normal"),
              safe(lang, "ja"),
              safe(quoteText, ""),
              createdAt
          ));
        }
      }

      return out;

    } catch (Exception e) {
      plugin.getLogger().warning("[QuoteFavoriteStore] listFavorites failed: " + e.getMessage());
      return out;
    }
  }

  // ✅ SELECT（件数）
  public int countFavorites(UUID playerUuid) {
    String sql = "SELECT COUNT(*) FROM " + favoritesTable + " WHERE player_uuid=?";
    try (Connection conn = getConn();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, playerUuid.toString());

      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getInt(1);
      }
      return 0;

    } catch (Exception e) {
      plugin.getLogger().warning("[QuoteFavoriteStore] countFavorites failed: " + e.getMessage());
      return 0;
    }
  }

  private String safe(String v, String def) {
    if (v == null) return def;
    String s = v.trim();
    return s.isBlank() ? def : s;
  }
}