package plugin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CheckTreasureEmeraldCommand implements CommandExecutor {

  private final TreasureRunMultiChestPlugin plugin;
  private final I18nHelper i18n;

  public CheckTreasureEmeraldCommand(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
    this.i18n = new I18nHelper(plugin);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(ChatColor.RED + i18n.trDefault(
          "command.checkTreasureEmerald.playersOnly"
      ));
      return true;
    }

    ItemStack item = player.getInventory().getItemInMainHand();
    boolean result = plugin.getItemFactory().isTreasureEmerald(item);

    player.sendMessage(ChatColor.AQUA + i18n.tr(
        player,
        result
            ? "command.checkTreasureEmerald.resultTrue"
            : "command.checkTreasureEmerald.resultFalse"
    ));

    return true;
  }
}
