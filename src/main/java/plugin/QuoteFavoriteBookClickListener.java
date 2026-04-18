package plugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BookMeta;
import plugin.I18n;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class QuoteFavoriteBookClickListener implements Listener {

  private final TreasureRunMultiChestPlugin plugin;

  // ✅ 連打防止（右クリックが多重発火することがある）
  private final ConcurrentHashMap<UUID, Long> lastClickMs = new ConcurrentHashMap<>();
  private static final long COOLDOWN_MS = 800;

  // ✅ 近未来図鑑UI（理想形）
  private final QuoteFavoritesBookBuilder bookBuilder;

  public QuoteFavoriteBookClickListener(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
    this.bookBuilder = new QuoteFavoritesBookBuilder(plugin);
  }

  @EventHandler
  public void onRightClick(PlayerInteractEvent e) {
    if (e.getItem() == null) return;

    // ✅ 右クリック以外は無視（壊れないように安全に）
    Action act = e.getAction();
    if (!(act == Action.RIGHT_CLICK_AIR || act == Action.RIGHT_CLICK_BLOCK)) return;

    ItemStack item = e.getItem();
    if (item.getType() != Material.WRITTEN_BOOK) return;

    ItemMeta meta = item.getItemMeta();
    if (meta == null || !meta.hasDisplayName()) return;

    Player player = e.getPlayer();

    // ✅ 右クリック連打対策
    long now = System.currentTimeMillis();
    long last = lastClickMs.getOrDefault(player.getUniqueId(), 0L);
    if (now - last < COOLDOWN_MS) return;
    lastClickMs.put(player.getUniqueId(), now);

    // ✅ この本が TreasureRun のルールブックか判定
    if (!isTreasureRunRuleBook(meta.getDisplayName())) return;

    // ✅ TreasureRunルールブックに反応した場合は、原作の本表示を止める（二重挙動防止）
    e.setCancelled(true);

    // =======================================================
    // ✅ 操作仕様（壊れない + 理想形）
    //  - 通常右クリック：最新格言を Favorite 保存
    //  - Shift + 右クリック：Favorites 図鑑を表示（本で開く）
    // =======================================================
    if (player.isSneaking()) {
      boolean shown = showFavoritesBookHybrid(player);
      if (!shown) {
        // ✅ ここまで来て false の場合も、最低限メッセージ
        player.sendMessage(ChatColor.YELLOW + tr(player, "favorites.empty.noFav"));
      }
      return;
    }

    boolean ok = favoriteLatest(player);

    if (ok) {
      player.sendMessage(ChatColor.GREEN + tr(player, "command.quoteFavorite.latestSaved"));
    } else {
      player.sendMessage(ChatColor.YELLOW + tr(player, "command.quoteFavorite.latestNotSaved"));
    }
  }

  private boolean favoriteLatest(Player player) {
    if (player == null) return false;

    UUID uuid = player.getUniqueId();

    try {
      Connection conn = plugin.getMySQLConnection();
      if (conn == null) return false;
      if (plugin.getProverbLogRepository() == null) return false;

      return plugin.getProverbLogRepository().favoriteLatestLog(conn, uuid);

    } catch (Exception ex) {
      plugin.getLogger().severe("[QuoteFavoriteBook] Favorite failed: " + ex.getMessage());
      return false;
    }
  }

  // =======================================================
  // ✅ Favorites一覧を「本」で表示する（最強ハイブリッド）
  // - ① 反射でFavorites取得を試す（壊れない）
  // - ② 取れなければ recent へフォールバック（壊れない）
  // - ③ 0件なら空状態の本を表示（B路線完成）
  // - ④ Builderで図鑑生成（理想形）
  // - ⑤ Builderが失敗したら buildBookPages で最低限表示（壊れない）
  // =======================================================
  private boolean showFavoritesBookHybrid(Player player) {
    if (player == null) return false;

    UUID uuid = player.getUniqueId();
    List<String> rows = new ArrayList<>();

    Connection conn = null;
    try {
      conn = plugin.getMySQLConnection();
    } catch (Throwable ignored) {}

    // ✅ ① Favorites取得（反射で壊れない）
    try {
      if (conn != null && plugin.getProverbLogRepository() != null) {
        List<String> fav = tryGetFavoritesFromRepository(conn, uuid, 200);
        if (fav != null && !fav.isEmpty()) {
          rows.addAll(fav);
        }
      }
    } catch (Throwable ignored) {}

    // ✅ ② フォールバック：recent（Favoritesが無い/取れない時）
    if (rows.isEmpty()) {
      List<String> recent = tryGetRecentProverbsFromPlugin(uuid, 30);
      if (recent != null && !recent.isEmpty()) {
        rows.addAll(recent);
      }
    }

    // ✅ ③ 0件なら空状態メッセージ本を表示（ここがB路線の完成）
    if (rows.isEmpty()) {
      ItemStack empty = null;
      try {
        empty = bookBuilder.buildEmptyFavoritesBook(player);
      } catch (Throwable ignored) {}

      if (empty != null) {
        try {
          player.openBook(empty);
          return true;
        } catch (Throwable ignored) {
          player.sendMessage(ChatColor.YELLOW + tr(player, "favorites.empty.noFav"));
          return true;
        }
      }
      return false;
    }

    // ✅ ④ 近未来UI（Builderで図鑑を作る）
    ItemStack archive = null;
    try {
      archive = bookBuilder.buildFavoritesBook(player, rows);
    } catch (Throwable ignored) {}

    // ✅ ⑤ Builderが失敗した場合の最後の保険：最低限本表示
    if (archive == null) {
      archive = buildSimpleFallbackBook(player, rows);
    }

    if (archive == null) return false;

    // ✅ その場で本を開く（インベントリに入れずに開ける）
    try {
      player.openBook(archive);
      return true;
    } catch (Throwable t) {
      // openBookが環境差で失敗する可能性があるので、最悪チャットに出す
      player.sendMessage(ChatColor.YELLOW + tr(player, "favorites.title") + ":");
      for (String r : rows) {
        if (r == null) continue;
        String trimmed = r.trim();
        if (trimmed.isEmpty()) continue;
        player.sendMessage(ChatColor.WHITE + trimmed);
      }
      return true;
    }
  }

  // =======================================================
  // ✅ RepositoryからFavoritesを取る（反射で壊れない）
  // ありがちな候補メソッド名：
  // - getFavoriteQuotes(Connection, UUID, int)
  // - getFavorites(Connection, UUID, int)
  // - getFavoriteLogs(Connection, UUID, int)
  // - loadFavorites(Connection, UUID, int)
  // =======================================================
  @SuppressWarnings("unchecked")
  private List<String> tryGetFavoritesFromRepository(Connection conn, UUID uuid, int limit) {
    if (conn == null) return null;
    if (uuid == null) return null;
    if (plugin.getProverbLogRepository() == null) return null;

    Object repo = plugin.getProverbLogRepository();

    String[] candidates = new String[]{
        "getFavoriteQuotes",
        "getFavorites",
        "getFavoriteLogs",
        "getFavoriteProverbs",
        "loadFavorites"
    };

    for (String name : candidates) {
      try {
        java.lang.reflect.Method m =
            repo.getClass().getMethod(name, Connection.class, UUID.class, int.class);
        Object ret = m.invoke(repo, conn, uuid, Math.max(1, limit));
        if (ret instanceof List) {
          return (List<String>) ret;
        }
      } catch (NoSuchMethodException ignore) {
        // 次の候補へ
      } catch (Throwable ignored) {
        // 何が起きても落とさない
      }
    }

    return null;
  }

  // =======================================================
  // ✅ recent取得：plugin.getRecentProverbs(uuid, limit) があるなら反射で使う
  // なくてもコンパイルが壊れない
  // =======================================================
  @SuppressWarnings("unchecked")
  private List<String> tryGetRecentProverbsFromPlugin(UUID uuid, int limit) {
    if (uuid == null) return null;

    try {
      java.lang.reflect.Method m =
          plugin.getClass().getMethod("getRecentProverbs", UUID.class, int.class);
      Object ret = m.invoke(plugin, uuid, Math.max(1, limit));
      if (ret instanceof List) {
        return (List<String>) ret;
      }
    } catch (NoSuchMethodException ignore) {
      // plugin側に無いなら無視
    } catch (Throwable ignored) {
      // 何が起きても落とさない
    }

    return null;
  }

  // =======================================================
  // ✅ 最後の保険：最低限のFavorites本を作る（ページ分割あり）
  // =======================================================
  private ItemStack buildSimpleFallbackBook(Player player, List<String> rows) {
    if (player == null) return null;
    if (rows == null || rows.isEmpty()) return null;

    try {
      ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta bm = (BookMeta) book.getItemMeta();
      if (bm == null) return null;

      bm.setTitle(tr(player, "favorites.title"));
      bm.setAuthor(player.getName());
      bm.setDisplayName(ChatColor.AQUA + tr(player, "favorites.title"));

      List<String> pages = buildBookPages(player, rows);
      if (pages.isEmpty()) return null;

      bm.setPages(pages);
      book.setItemMeta(bm);
      return book;

    } catch (Throwable ignored) {
      return null;
    }
  }

  // =======================================================
  // ✅ ページ分割（壊れない）
  // =======================================================
  private List<String> buildBookPages(Player player, List<String> rows) {
    List<String> pages = new ArrayList<>();
    if (rows == null || rows.isEmpty()) return pages;

    final int PAGE_CHAR_LIMIT = 230;

    StringBuilder current = new StringBuilder();
    current.append(ChatColor.DARK_AQUA).append("★ Favorites").append("\n")
        .append(ChatColor.GRAY).append(tr(player, "favorites.toc.howtoShift")).append("\n\n");

    for (String row : rows) {
      if (row == null) continue;

      String text = ChatColor.stripColor(row);
      if (text == null) continue;

      String trimmed = text.trim();
      if (trimmed.isEmpty()) continue;

      String entry = "• " + trimmed + "\n\n";

      if (current.length() + entry.length() > PAGE_CHAR_LIMIT) {
        pages.add(current.toString());
        current = new StringBuilder();
      }

      if (entry.length() > PAGE_CHAR_LIMIT) {
        String cut = entry.substring(0, Math.min(entry.length(), PAGE_CHAR_LIMIT - 5)) + "...";
        current.append(cut).append("\n\n");
        continue;
      }

      current.append(entry);
    }

    if (current.length() > 0) {
      pages.add(current.toString());
    }

    return pages;
  }

  // =======================================================
  // ✅ ルールブック判定（あなたの config.yml の displayName 全対応）
  // =======================================================
  private boolean isTreasureRunRuleBook(String displayName) {
    if (displayName == null || displayName.isBlank()) return false;

    String normalized = ChatColor.stripColor(displayName);

    List<String> allNames = new ArrayList<>();

    try {
      var cfg = plugin.getConfig();
      ConfigurationSection sec = cfg.getConfigurationSection("ruleBook.displayName");
      if (sec != null) {
        for (String code : sec.getKeys(false)) {
          String n = sec.getString(code);
          if (n != null && !n.isBlank()) {
            allNames.add(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', n)));
          }
        }
      }
    } catch (Exception ignored) {}

    if (allNames.isEmpty()) {
      allNames.add("TreasureRun ルールブック");
      allNames.add("TreasureRun Rule Book");
    }

    for (String candidate : allNames) {
      if (candidate == null) continue;
      if (normalized.equals(candidate)) return true;
    }

    return false;
  }


  private String tr(Player player, String key) {
    return tr(player, key, key);
  }

  private String tr(Player player, String key, String fallback) {
    try {
      I18n i18n = resolveI18n();
      if (i18n == null) return fallback;
      String lang = resolvePlayerLang(player);
      String s = i18n.tr(lang, key);
      if (s == null || s.isBlank() || s.equals(key)) return fallback;
      return s;
    } catch (Throwable ignored) {
      return fallback;
    }
  }

  private String resolvePlayerLang(Player player) {
    if (player == null) return plugin.getConfig().getString("language.default", "ja");

    UUID uuid = player.getUniqueId();

    try {
      java.lang.reflect.Field f = plugin.getClass().getDeclaredField("playerLanguageStore");
      f.setAccessible(true);
      Object store = f.get(plugin);

      if (store != null) {
        try {
          java.lang.reflect.Method m = store.getClass().getMethod("get", UUID.class);
          Object ret = m.invoke(store, uuid);
          if (ret instanceof String s && !s.isBlank()) return s;
        } catch (Throwable ignored) {}

        try {
          java.lang.reflect.Method m = store.getClass().getMethod("getPlayerLang", UUID.class);
          Object ret = m.invoke(store, uuid);
          if (ret instanceof String s && !s.isBlank()) return s;
        } catch (Throwable ignored) {}
      }
    } catch (Throwable ignored) {}

    return plugin.getConfig().getString("language.default", "ja");
  }

  private I18n resolveI18n() {
    try {
      java.lang.reflect.Method m = plugin.getClass().getMethod("getI18n");
      Object v = m.invoke(plugin);
      if (v instanceof I18n i) return i;
    } catch (Throwable ignored) {}

    try {
      java.lang.reflect.Field f = plugin.getClass().getDeclaredField("i18n");
      f.setAccessible(true);
      Object v = f.get(plugin);
      if (v instanceof I18n i) return i;
    } catch (Throwable ignored) {}

    try {
      I18n i = new I18n(plugin);
      i.loadOrCreate();
      return i;
    } catch (Throwable ignored) {}

    return null;
  }

}
