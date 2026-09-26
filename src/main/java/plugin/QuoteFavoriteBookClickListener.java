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

    Action act = e.getAction();
    if (!(act == Action.RIGHT_CLICK_AIR || act == Action.RIGHT_CLICK_BLOCK)) return;

    ItemStack item = e.getItem();
    if (item.getType() != Material.WRITTEN_BOOK) return;

    ItemMeta meta = item.getItemMeta();
    if (meta == null || !meta.hasDisplayName()) return;

    Player player = e.getPlayer();
    UUID playerId = player.getUniqueId();

    long now = System.currentTimeMillis();
    long last = lastClickMs.getOrDefault(playerId, 0L);
    if (now - last < COOLDOWN_MS) return;
    lastClickMs.put(playerId, now);

    if (!isTreasureRunRuleBook(meta.getDisplayName())) return;
    e.setCancelled(true);

    plugin.quote.InteractiveProverbService service = plugin.getInteractiveProverbService();
    if (service == null) {
      player.sendMessage(ChatColor.YELLOW + tr(player, "command.quoteFavorite.repositoryNotReady"));
      return;
    }

    // One canonical interaction contract:
    // normal right-click = save latest; sneak + right-click = open Favorites archive.
    if (player.isSneaking()) {
      service.loadBookData(playerId, 30, 200).whenComplete((result, failure) ->
          deliver(playerId, current -> {
            if (failure != null || result == null || !result.successful()) {
              current.sendMessage(ChatColor.YELLOW + tr(current, "favorites.empty.noFav"));
              return;
            }

            List<String> rows = new ArrayList<>(result.value().favorites());
            if (rows.isEmpty()) {
              rows.addAll(result.value().recent());
            }

            boolean shown = showFavoritesBookHybrid(current, rows);
            if (!shown) {
              current.sendMessage(ChatColor.YELLOW + tr(current, "favorites.empty.noFav"));
            }
          })
      );
      return;
    }

    service.favoriteLatest(playerId).whenComplete((result, failure) ->
        deliver(playerId, current -> {
          if (failure != null || result == null || !result.successful()) {
            current.sendMessage(ChatColor.YELLOW + tr(current, "command.quoteFavorite.repositoryNotReady"));
          } else if (Boolean.TRUE.equals(result.value())) {
            current.sendMessage(ChatColor.GREEN + tr(current, "command.quoteFavorite.latestSaved"));
          } else {
            current.sendMessage(ChatColor.YELLOW + tr(current, "command.quoteFavorite.latestNotSaved"));
          }
        })
    );
  }

  private boolean showFavoritesBookHybrid(Player player, List<String> rows) {
    if (player == null) return false;
    List<String> safeRows = rows == null ? List.of() : List.copyOf(rows);

    if (safeRows.isEmpty()) {
      ItemStack empty = null;
      try {
        empty = bookBuilder.buildEmptyFavoritesBook(player);
      } catch (Throwable ignored) { }

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

    ItemStack archive = null;
    try {
      archive = bookBuilder.buildFavoritesBook(player, safeRows);
    } catch (Throwable ignored) { }

    if (archive == null) {
      archive = buildSimpleFallbackBook(player, safeRows);
    }
    if (archive == null) return false;

    try {
      player.openBook(archive);
      return true;
    } catch (Throwable t) {
      player.sendMessage(ChatColor.YELLOW + tr(player, "favorites.title") + ":");
      for (String row : safeRows) {
        if (row == null || row.isBlank()) continue;
        player.sendMessage(ChatColor.WHITE + row.trim());
      }
      return true;
    }
  }

  private void deliver(UUID playerId, java.util.function.Consumer<Player> action) {
    try {
      plugin.getServer().getScheduler().runTask(plugin, () -> {
        if (!plugin.isEnabled()) return;
        Player current = plugin.getServer().getPlayer(playerId);
        if (current == null || !current.isOnline()) return;
        action.accept(current);
      });
    } catch (RuntimeException ignored) {
      // Plugin lifecycle is shutting down. Late DB results are intentionally dropped.
    }
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

      bm.setPages(safePages(pages));
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
    current.append(ChatColor.DARK_AQUA)
        .append(tr(player, "favorites.title", "Favorites"))
        .append("\n")
        .append(ChatColor.GRAY)
        .append(tr(player, "favorites.toc.howtoShift", "Sneak + right-click to open the Favorites archive"))
        .append("\n\n");

    for (String row : rows) {
      if (row == null) continue;

      String text = ChatColor.stripColor(row);
      if (text == null) continue;

      String trimmed = safeBookText(text.trim());
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
      allNames.add(ChatColor.stripColor(tr(null, "ruleBook.displayName.ja", "TreasureRun ルールブック")));
      allNames.add(ChatColor.stripColor(tr(null, "ruleBook.displayName.en", "TreasureRun Rule Book")));
    }

    for (String candidate : allNames) {
      if (candidate == null) continue;
      if (normalized.equals(candidate)) return true;
    }

    return false;
  }


  private List<String> safePages(List<String> pages) {
    if (pages == null || pages.isEmpty()) return java.util.List.of("");

    List<String> out = new ArrayList<>();
    for (String page : pages) {
      String cleaned = safeBookText(page);
      if (cleaned == null || cleaned.isBlank()) cleaned = " ";
      out.add(cleaned);
    }
    return out;
  }

  private String safeBookText(String input) {
    if (input == null || input.isBlank()) return "";

    StringBuilder sb = new StringBuilder(input.length());

    for (int i = 0; i < input.length(); ) {
      int cp = input.codePointAt(i);
      i += Character.charCount(cp);

      if (cp == '\r') continue;
      if (cp == '\t') {
        sb.append(' ');
        continue;
      }
      if (cp == '\n') {
        sb.append('\n');
        continue;
      }

      if (cp == 0x00A7) {
        sb.appendCodePoint(cp);
        continue;
      }

      if (cp >= 0xD800 && cp <= 0xDFFF) continue;
      if (cp >= 0xE000 && cp <= 0xF8FF) continue;
      if (cp >= 0xF0000 && cp <= 0xFFFFD) continue;
      if (cp >= 0x100000 && cp <= 0x10FFFD) continue;
      if (cp >= 0xFE00 && cp <= 0xFE0F) continue;
      if (cp >= 0xE0100 && cp <= 0xE01EF) continue;
      if (cp >= 0x1B000 && cp <= 0x1B16F) continue;
      if (cp >= 0x1AFF0 && cp <= 0x1AFFF) continue;
      if (cp >= 0x1F000 && cp <= 0x1FFFF) continue;
      if (cp > 0xFFFF) continue;
      if (Character.isISOControl(cp)) continue;

      sb.appendCodePoint(cp);
    }

    return sb.toString()
        .replace("光が等しく降り注ぎ、全ての影が消えた。", "雲間より光さし、道の末はほのかに明らみけり。")
        .replace("すべての影が消えた。", "影といふ影、跡なく消えにけり。")
        .replace("全ての影が消えた。", "影といふ影、跡なく消えにけり。")
        .replace("光が等しく降り注ぎ", "雲間より光さし")
        .replace("  ", " ")
        .replace(" ,", ",")
        .trim();
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
      if (s == null || s.isBlank() || s.equals(key) || s.startsWith("Translation missing:")) return fallback;
      return s;
    } catch (Throwable ignored) {
      return fallback;
    }
  }

  private String resolvePlayerLang(Player player) {
    if (player == null) return plugin.getConfig().getString("language.default", "ja");

    try {
      if (plugin.getPlayerLanguageStore() != null) {
        String saved = plugin.getPlayerLanguageStore().getLang(player.getUniqueId(), "");
        if (saved != null && !saved.isBlank()) return saved;
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
