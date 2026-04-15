package plugin;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CraftSpecialEmeraldCommand implements CommandExecutor {

  private final TreasureRunMultiChestPlugin plugin;
  private final I18nHelper i18n;

  public CraftSpecialEmeraldCommand(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
    this.i18n = new I18nHelper(plugin);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Players only.");
      return true;
    }

    int requiredDiamonds = 3;
    int diamonds = countMaterial(player, Material.DIAMOND);

    if (diamonds < requiredDiamonds) {
      player.sendMessage(i18n.tr(
          player,
          "command.craftSpecialEmerald.needDiamonds",
          "&cYou need 3 diamonds to craft a Special Emerald."
      ));
      return true;
    }

    player.getInventory().removeItem(new ItemStack(Material.DIAMOND, requiredDiamonds));

    ItemStack specialEmerald = plugin.getItemFactory().createTreasureEmerald(1, player);
    player.getInventory().addItem(specialEmerald);

    player.sendMessage(i18n.tr(
        player,
        "command.craftSpecialEmerald.success",
        "&bYou crafted a Special Emerald using 3 diamonds!"
    ));

    return true;
  }

  private int countMaterial(Player player, Material material) {
    int total = 0;
    for (ItemStack item : player.getInventory().getContents()) {
      if (item == null) continue;
      if (item.getType() == material) {
        total += item.getAmount();
      }
    }
    return total;
  }
}
