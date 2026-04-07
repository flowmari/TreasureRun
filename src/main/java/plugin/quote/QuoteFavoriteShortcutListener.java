package plugin.quote;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import plugin.I18n;
import plugin.TreasureRunMultiChestPlugin;

import java.sql.Connection;
import java.util.UUID;

public class QuoteFavoriteShortcutListener implements Listener {

  private final TreasureRunMultiChestPlugin plugin;
  private final I18n i18n;

  public QuoteFavoriteShortcutListener(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
    this.i18n = resolveI18n(plugin);
  }

  /**
   * ✅ Hybrid演出：
   * - ルールブックを持って Sneak + 右クリック → 最新格言をお気に入り保存（/quoteFavorite latest 相当）
   * - 既存の /gameMenu を壊さないため Sneak限定
   */
  @EventHandler
  public void onInteract(PlayerInteractEvent e) {
    Player player = e.getPlayer();
    if (!player.isSneaking()) return;

    ItemStack hand = player.getInventory().getItemInMainHand();
    if (hand == null) return;

    // ルールブック（Written Book）を持っている時だけ反応
    if (hand.getType() != Material.WRITTEN_BOOK && hand.getType() != Material.WRITABLE_BOOK) return;

    Connection conn = null;
    try {
      conn = plugin.getMySQLConnection();
    } catch (Throwable ignored) {}

    if (conn == null || plugin.getProverbLogRepository() == null) {
      player.sendMessage(ChatColor.RED + tr(player, "command.quoteFavorite.repositoryNotReady"));
      return;
    }

    UUID uuid = player.getUniqueId();
    boolean ok = plugin.getProverbLogRepository().favoriteLatestLog(conn, uuid);

    if (ok) {
      player.sendMessage(ChatColor.GREEN + tr(player, "command.quoteFavorite.shortcutSaved"));
    } else {
      player.sendMessage(ChatColor.RED + tr(player, "command.quoteFavorite.shortcutNotSaved"));
    }
  }

  private static I18n resolveI18n(TreasureRunMultiChestPlugin plugin) {
    if (plugin == null) return null;

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

  private String resolvePlayerLang(Player player) {
    if (player == null) return plugin.getConfig().getString("language.default", "ja");

    java.util.UUID uuid = player.getUniqueId();

    try {
      java.lang.reflect.Field f = plugin.getClass().getDeclaredField("playerLanguageStore");
      f.setAccessible(true);
      Object store = f.get(plugin);

      if (store != null) {
        try {
          java.lang.reflect.Method m = store.getClass().getMethod("get", java.util.UUID.class);
          Object ret = m.invoke(store, uuid);
          if (ret instanceof String s && !s.isBlank()) return s;
        } catch (Throwable ignored) {}

        try {
          java.lang.reflect.Method m = store.getClass().getMethod("getLang", Player.class, String.class);
          Object ret = m.invoke(store, player, plugin.getConfig().getString("language.default", "ja"));
          if (ret instanceof String s && !s.isBlank()) return s;
        } catch (Throwable ignored) {}
      }
    } catch (Throwable ignored) {}

    return plugin.getConfig().getString("language.default", "ja");
  }

  private String tr(Player player, String key) {
    String lang = resolvePlayerLang(player);
    try {
      if (i18n != null) {
        String s = i18n.tr(lang, key);
        if (s != null && !s.isBlank() && !s.equals(key)) return s;
      }
    } catch (Throwable ignored) {}
    return key;
  }

}
