package plugin.quote;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import plugin.TreasureRunMultiChestPlugin;

import java.sql.Connection;
import java.util.UUID;

public class QuoteFavoriteShortcutListener implements Listener {

  private final TreasureRunMultiChestPlugin plugin;

  public QuoteFavoriteShortcutListener(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
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
      player.sendMessage(ChatColor.RED + "MySQL / Repository not ready.");
      return;
    }

    UUID uuid = player.getUniqueId();
    boolean ok = plugin.getProverbLogRepository().favoriteLatestLog(conn, uuid);

    if (ok) {
      player.sendMessage(ChatColor.GREEN + "★ Favorite saved! (Sneak+RightClick)");
    } else {
      player.sendMessage(ChatColor.RED + "Favorite not saved. (maybe no logs yet / or duplicate)");
    }
  }
}