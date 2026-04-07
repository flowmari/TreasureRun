package plugin.legacy_unused;

import plugin.TreasureRunMultiChestPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class QuoteRereadService {

  public enum OutputMode {
    CHAT,
    TITLE,
    BOOK
  }

  private final TreasureRunMultiChestPlugin plugin;
  private final Random random = new Random();

  public QuoteRereadService(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  // =======================================================
  // ✅ Favoritesからランダム再読（無ければ recent へフォールバック）
  // =======================================================
  public boolean rereadRandom(Player player, OutputMode mode) {
    if (player == null) return false;

    UUID uuid = player.getUniqueId();
    Connection conn = null;

    try {
      conn = plugin.getMySQLConnection();
    } catch (Throwable ignored) {}

    List<String> pool = new ArrayList<>();

    // ✅ まず Favorites から拾う
    try {
      if (conn != null && plugin.getProverbLogRepository() != null) {
        List<String> fav = plugin.getProverbLogRepository().loadFavorites(conn, uuid, 200);
        if (fav != null && !fav.isEmpty()) {
          pool.addAll(fav);
        }
      }
    } catch (Throwable ignored) {}

    // ✅ 無いなら recent へ
    if (pool.isEmpty()) {
      try {
        if (conn != null && plugin.getProverbLogRepository() != null) {
          List<String> recent = plugin.getProverbLogRepository().loadRecentProverbs(conn, uuid, 80);
          if (recent != null && !recent.isEmpty()) {
            pool.addAll(recent);
          }
        }
      } catch (Throwable ignored) {}
    }

    if (pool.isEmpty()) {
      player.sendMessage(ChatColor.YELLOW + "★ No quotes yet. Play a game first!");
      return false;
    }

    // ✅ ランダム1件
    String chosen = pool.get(random.nextInt(pool.size()));
    if (chosen == null) chosen = "(empty)";

    // ✅ 表示
    if (mode == OutputMode.TITLE) {
      showAsTitle(player, chosen);
      return true;
    }

    if (mode == OutputMode.BOOK) {
      ItemStack book = buildRereadBook(player, chosen);
      if (book != null) {
        try {
          player.openBook(book);
          return true;
        } catch (Throwable ignored) {
          // openBook が無理ならチャットに落とす
        }
      }
      showAsChat(player, chosen);
      return true;
    }

    // CHAT (default)
    showAsChat(player, chosen);
    return true;
  }

  // =======================================================
  // ✅ CHAT表示
  // =======================================================
  private void showAsChat(Player player, String row) {
    player.sendMessage(ChatColor.AQUA + "📖 [TreasureRun] Re-read Random");
    player.sendMessage(ChatColor.GRAY + "------------------------------");
    for (String line : safeLines(row)) {
      player.sendMessage(ChatColor.WHITE + line);
    }
  }

  // =======================================================
  // ✅ TITLE表示（短く要約）
  // =======================================================
  private void showAsTitle(Player player, String row) {
    String plain = ChatColor.stripColor(row);
    if (plain == null) plain = "";
    plain = plain.replace("\r\n", "\n").trim();

    String title = "TreasureRun Re-read";
    String subtitle = plain;

    // 長すぎるとタイトルが崩れるので短縮
    if (subtitle.length() > 60) {
      subtitle = subtitle.substring(0, 60) + "...";
    }

    try {
      player.sendTitle(ChatColor.AQUA + title, ChatColor.WHITE + subtitle, 10, 60, 10);
    } catch (Throwable ignored) {
      // sendTitle が無理でも落とさない
      showAsChat(player, row);
    }
  }

  // =======================================================
  // ✅ BOOK表示（1冊だけ開く）
  // =======================================================
  private ItemStack buildRereadBook(Player player, String row) {
    if (player == null) return null;

    try {
      ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta meta = (BookMeta) book.getItemMeta();
      if (meta == null) return null;

      meta.setTitle("TreasureRun Re-read");
      meta.setAuthor(player.getName());
      meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Re-read Random");

      List<String> pages = new ArrayList<>();
      pages.add(ChatColor.DARK_AQUA + "TreasureRun Archive\n" +
          ChatColor.GRAY + "Random Re-read\n\n" +
          ChatColor.WHITE + fitToPage(row));

      meta.setPages(pages);
      book.setItemMeta(meta);
      return book;

    } catch (Throwable ignored) {
      return null;
    }
  }

  private List<String> safeLines(String row) {
    List<String> out = new ArrayList<>();
    if (row == null) return out;

    String t = row.replace("\r\n", "\n").trim();
    if (t.isEmpty()) return out;

    String[] lines = t.split("\n");
    for (String line : lines) {
      if (line == null) continue;
      String s = line.trim();
      if (!s.isEmpty()) out.add(s);
    }
    return out;
  }

  private String fitToPage(String s) {
    if (s == null) return "";
    String t = ChatColor.stripColor(s);
    if (t == null) t = "";
    t = t.trim();
    if (t.length() <= 230) return t;
    return t.substring(0, 227) + "...";
  }
}