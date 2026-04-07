package plugin.legacy_unused;

import plugin.TreasureRunMultiChestPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

public class QuoteFavoriteCommand implements CommandExecutor {

  private final TreasureRunMultiChestPlugin plugin;

  public QuoteFavoriteCommand(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

    if (!(sender instanceof Player)) {
      sender.sendMessage("Players only.");
      return true;
    }

    Player player = (Player) sender;
    UUID uuid = player.getUniqueId();

    // ✅ 接続取得（あなたの plugin 側にある想定）
    // もしメソッド名が違ったら教えて！一瞬で合わせる
    Connection conn = plugin.getMySQLConnection();

    if (args.length == 0) {
      player.sendMessage(ChatColor.AQUA + "[TreasureRun] Quote Favorite Commands:");
      player.sendMessage(ChatColor.GRAY + "/quoteFavorite latest  - save latest quote as favorite");
      player.sendMessage(ChatColor.GRAY + "/quoteFavorite list    - show favorites");
      player.sendMessage(ChatColor.GRAY + "/quoteFavorite remove <id> - remove favorite");
      return true;
    }

    String sub = args[0].toLowerCase();

    // ---------------------------------------------------
    // latest
    // ---------------------------------------------------
    if (sub.equals("latest")) {
      boolean ok = plugin.getProverbLogRepository().favoriteLatestLog(conn, uuid);

      if (ok) {
        player.sendMessage(ChatColor.GREEN + "★ Favorite saved!");
      } else {
        player.sendMessage(ChatColor.RED + "Favorite not saved. (maybe no logs yet / or duplicate)");
      }
      return true;
    }

    // ---------------------------------------------------
    // list
    // ---------------------------------------------------
    if (sub.equals("list")) {
      List<String> favs = plugin.getProverbLogRepository().loadFavorites(conn, uuid, 20);

      player.sendMessage(ChatColor.AQUA + "★ Favorites (latest 20): " + ChatColor.GRAY + "(count=" + favs.size() + ")");
      for (String row : favs) {
        player.sendMessage(ChatColor.GRAY + "------------------------------");
        // 1行ずつ送る
        for (String line : row.split("\n")) {
          player.sendMessage(ChatColor.WHITE + line);
        }
      }
      return true;
    }

    // ---------------------------------------------------
    // remove <id>
    // ---------------------------------------------------
    if (sub.equals("remove")) {
      if (args.length < 2) {
        player.sendMessage(ChatColor.RED + "Usage: /quoteFavorite remove <id>");
        return true;
      }

      int id;
      try {
        id = Integer.parseInt(args[1]);
      } catch (NumberFormatException e) {
        player.sendMessage(ChatColor.RED + "ID must be a number.");
        return true;
      }

      boolean ok = plugin.getProverbLogRepository().deleteFavoriteById(conn, uuid, id);

      if (ok) {
        player.sendMessage(ChatColor.GREEN + "★ Favorite removed: #" + id);
      } else {
        player.sendMessage(ChatColor.RED + "Favorite not removed. (not found?)");
      }
      return true;
    }

    player.sendMessage(ChatColor.RED + "Unknown subcommand. Use: /quoteFavorite");
    return true;
  }
}