package plugin.quote;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import plugin.I18n;
import plugin.TreasureRunMultiChestPlugin;

import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class QuoteRereadService {

  public enum OutputMode {
    CHAT, TITLE, BOOK
  }

  private final TreasureRunMultiChestPlugin plugin;
  private final QuoteFavoritesBookBuilder bookBuilder;
  private final I18n i18n;
  private final Random random = new Random();

  public QuoteRereadService(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;

    // ✅ pluginが持っている i18n を “同じ参照” で拾う（/treasureReload 反映用）
    this.i18n = resolveI18n(plugin);

    // ✅ Favorites図鑑Builderは I18n渡しで統一（messages.yml 参照）
    this.bookBuilder = new QuoteFavoritesBookBuilder(this.i18n);
  }

  public boolean rereadRandom(Player player, OutputMode mode) {
    UUID uuid = player.getUniqueId();

    // ✅ プレイヤー言語（PlayerLanguageStore優先 → default）
    String lang = resolvePlayerLang(player);

    Connection conn = null;
    try {
      conn = plugin.getMySQLConnection();
    } catch (Throwable ignored) {}

    if (conn == null || plugin.getProverbLogRepository() == null) {
      player.sendMessage(ChatColor.RED + trOrFallback(lang, "command.quoteReread.repositoryNotReady", "The favorites repository is not ready yet."));
      return false;
    }

    // ✅ favorites からランダム（なければ false）
    List<String> favs = plugin.getProverbLogRepository().loadFavorites(conn, uuid, 200);
    if (favs == null || favs.isEmpty()) return false;

    String pick = favs.get(random.nextInt(favs.size()));

    // ✅ あなたの既存Parserをそのまま活かす
    QuoteFavoriteRow row = QuoteFavoriteRowParser.parse(pick);
    String quoteText = (row == null || row.quote == null) ? "" : row.quote.trim();
    if (quoteText.isBlank()) return false;

    // ✅ messages.yml（I18n）で表示文を取得（無ければ英語にフォールバック）
    String head = trOrFallback(lang, "favorites.reread.head", "favorites.reread.head");
    String noQuotes = trOrFallback(lang, "favorites.reread.noQuotes", "favorites.reread.noQuotes"); // reserved for future empty-state UI parity

    if (mode == OutputMode.CHAT) {
      player.sendMessage(ChatColor.AQUA + head);
      player.sendMessage(ChatColor.WHITE + quoteText);
      return true;
    }

    if (mode == OutputMode.TITLE) {
      try {
        player.sendTitle(
            ChatColor.AQUA + head,
            ChatColor.WHITE + trimForTitle(quoteText),
            10, 60, 10
        );
      } catch (Throwable ignored) {}
      return true;
    }

    // ✅ BOOK（OneShot）
    try {
      // Builderが reflection で読める Row に変換して渡す（壊れない）
      Object objRow = new SimpleRow(extractKindFromRaw(pick), quoteText);

      ItemStack book = bookBuilder.buildRereadOneShotBook(player, objRow);
      if (book == null) {
        player.sendMessage(ChatColor.RED + trOrFallback(lang, "command.quoteReread.bookOpenFailed", "Could not open the favorites book."));
        return false;
      }

      player.openBook(book);
      return true;

    } catch (Throwable t) {
      player.sendMessage(ChatColor.RED + trOrFallback(lang, "command.quoteReread.openBookFailed", "Could not open the book."));
      // 念のため CHAT で出す（完全に無言にならない）
      player.sendMessage(ChatColor.AQUA + head);
      player.sendMessage(ChatColor.WHITE + quoteText);
      return true;
    }
  }

  // =======================================================
  // ✅ messages.yml を読む（無ければ英語フォールバック）
  // =======================================================
  private String trOrFallback(String lang, String key, String fallback) {
    try {
      if (i18n == null) return fallback;
      String s = i18n.tr(lang, key);
      if (s == null || s.isBlank() || s.equalsIgnoreCase(key)) return fallback;
      return s;
    } catch (Throwable ignored) {
      return fallback;
    }
  }

  // =======================================================
  // ✅ pluginが持つ I18n を “同じ参照” で拾う（/treasureReload 即反映）
  // =======================================================
  private static I18n resolveI18n(TreasureRunMultiChestPlugin plugin) {
    if (plugin == null) return null;

    // 1) getI18n() がある場合
    try {
      java.lang.reflect.Method m = plugin.getClass().getMethod("getI18n");
      Object v = m.invoke(plugin);
      if (v instanceof I18n i) return i;
    } catch (Throwable ignored) {}

    // 2) private field "i18n" を反射で拾う
    try {
      java.lang.reflect.Field f = plugin.getClass().getDeclaredField("i18n");
      f.setAccessible(true);
      Object v = f.get(plugin);
      if (v instanceof I18n i) return i;
    } catch (Throwable ignored) {}

    // 3) fallback
    try {
      I18n i = new I18n(plugin);
      i.loadOrCreate();
      return i;
    } catch (Throwable ignored) {}

    return null;
  }

  // =======================================================
  // ✅ PlayerLanguageStore があればそれを優先して言語取得
  // =======================================================
  private String resolvePlayerLang(Player player) {
    if (player == null) return plugin.getConfig().getString("language.default", "ja");

    UUID uuid = player.getUniqueId();

    // playerLanguageStore.get(uuid) を反射で拾う（クラス構造が変わっても壊れない）
    try {
      java.lang.reflect.Field f = plugin.getClass().getDeclaredField("playerLanguageStore");
      f.setAccessible(true);
      Object store = f.get(plugin);

      if (store != null) {
        java.lang.reflect.Method m = store.getClass().getMethod("get", UUID.class);
        Object ret = m.invoke(store, uuid);
        if (ret instanceof String s && !s.isBlank()) {
          return s;
        }
      }
    } catch (Throwable ignored) {}

    return plugin.getConfig().getString("language.default", "ja");
  }

  // =======================================================
  // ✅ repositoryの1行から kind を推定（成功/時間切れ/その他）
  // =======================================================
  private String extractKindFromRaw(String raw) {
    if (raw == null) return "OTHER";
    String u = raw.toUpperCase(Locale.ROOT);
    if (u.contains("SUCCESS")) return "SUCCESS";
    if (u.contains("TIME_UP") || u.contains("TIME UP") || u.contains("TIMEUP")) return "TIME_UP";
    return "OTHER";
  }

  // =======================================================
  // ✅ Builder 側が reflection で kind/text を読めるRow
  // =======================================================
  public static class SimpleRow {
    private final String kind;
    private final String text;

    public SimpleRow(String kind, String text) {
      this.kind = (kind == null) ? "OTHER" : kind;
      this.text = (text == null) ? "" : text;
    }

    public String getKind() { return kind; }
    public String getText() { return text; }
  }

  private String trimForTitle(String s) {
    if (s == null) return "";
    s = ChatColor.stripColor(s);
    if (s.length() > 40) return s.substring(0, 40) + "...";
    return s;
  }
}