package plugin;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CraftSpecialEmeraldCommand implements CommandExecutor {

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    TreasureRunMultiChestPlugin plugin =
        TreasureRunMultiChestPlugin.getPlugin(TreasureRunMultiChestPlugin.class);
    I18nHelper i18n = new I18nHelper(plugin);

    if (!(sender instanceof Player player)) {
      sender.sendMessage(i18n.trDefault(
          "command.onlyPlayer",
          "&cThis command can only be used by a player."
      ));
      return true;
    }

    final int requiredDiamonds = 3;

    if (!player.getInventory().contains(Material.DIAMOND, requiredDiamonds)) {
      player.sendMessage(i18n.tr(
          player,
          "command.craftSpecialEmerald.needDiamonds",
          "&cYou need 3 diamonds to craft a Special Emerald."
      ));
      return true;
    }

    player.getInventory().removeItem(new ItemStack(Material.DIAMOND, requiredDiamonds));

    ItemStack specialEmerald = plugin.getItemFactory().createTreasureEmerald(1);

    ItemMeta meta = specialEmerald.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(i18n.tr(
          player,
          "items.specialEmerald.displayName",
          "&6Special Emerald"
      ));
      specialEmerald.setItemMeta(meta);
    }

    player.getInventory().addItem(specialEmerald);
    player.sendMessage(i18n.tr(
        player,
        "command.craftSpecialEmerald.success",
        "&bYou crafted a Special Emerald using 3 diamonds!"
    ));

    return true;
  }
}
