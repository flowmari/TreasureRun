package plugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class CraftSpecialEmeraldCommand implements CommandExecutor {

  private final TreasureRunMultiChestPlugin plugin;
  private final I18nHelper i18n;
  private final Configuration config;

  public CraftSpecialEmeraldCommand(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
    this.i18n = new I18nHelper(plugin);
    this.config = plugin.getConfig();
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(ChatColor.RED + i18n.trDefault(
          "command.craftSpecialEmerald.playersOnly",
          "Only players can use this command."
      ));
      return true;
    }

    int requiredDiamonds = config.getInt("craftSpecialEmerald.diamondsRequired", 3); // 3 diamonds as fallback.

    int diamonds = countMaterial(player, Material.DIAMOND);

    if (diamonds < requiredDiamonds) {
      player.sendMessage(i18n.trp(
          player,
          "command.craftSpecialEmerald.needDiamonds",
          Map.of("requiredDiamonds", String.valueOf(requiredDiamonds)),
          "&cYou need {requiredDiamonds} diamonds to craft a Special Emerald."
      ));
      return true;
    }

    player.getInventory().removeItem(new ItemStack(Material.DIAMOND, requiredDiamonds));

    ItemStack specialEmerald = plugin.getItemFactory().createTreasureEmerald(1, player);
    player.getInventory().addItem(specialEmerald);

    player.sendMessage(i18n.tr(
        player,
        "command.craftSpecialEmerald.success",
        "&bYou crafted a Special Emerald using {requiredDiamonds} diamonds!"
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
