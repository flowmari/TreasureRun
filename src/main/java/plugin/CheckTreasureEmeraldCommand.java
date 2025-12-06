package plugin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CheckTreasureEmeraldCommand implements CommandExecutor {

  private final TreasureRunMultiChestPlugin plugin;

  public CheckTreasureEmeraldCommand(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("このコマンドはプレイヤーのみが実行できます。");
      return true;
    }

    ItemStack item = player.getInventory().getItemInMainHand();
    boolean result = plugin.getItemFactory().isTreasureEmerald(item);

    player.sendMessage(ChatColor.AQUA + "isTreasureEmerald = " + (result ? ChatColor.GREEN + "true" : ChatColor.RED + "false"));
    return true;
  }
}
