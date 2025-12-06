package plugin;

import org.bukkit.ChatColor;
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
    // 1. コマンドを実行したのがプレイヤーか確認
    if (!(sender instanceof Player)) {
      sender.sendMessage("このコマンドはプレイヤーのみが実行できます。");
      return true;
    }

    Player player = (Player) sender;
    int requiredDiamonds = 3;

    // 2. プレイヤーのインベントリにダイヤモンドが3個あるか確認
    if (!player.getInventory().contains(Material.DIAMOND, requiredDiamonds)) {
      player.sendMessage(ChatColor.RED + "スペシャルエメラルドの作成には、ダイヤモンドが3個必要です。");
      return true;
    }

    // 3. インベントリからダイヤモンドを3個消費する
    player.getInventory().removeItem(new ItemStack(Material.DIAMOND, requiredDiamonds));

    // 4. スペシャルエメラルドのアイテムを作成
    ItemStack specialEmerald = TreasureRunMultiChestPlugin.getPlugin(TreasureRunMultiChestPlugin.class)
        .getItemFactory()
        .createTreasureEmerald(1);

    ItemMeta meta = specialEmerald.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6特製エメラルド"));
      specialEmerald.setItemMeta(meta);
    }

    // 5. プレイヤーにアイテムを渡す
    player.getInventory().addItem(specialEmerald);
    player.sendMessage(ChatColor.AQUA + "ダイヤモンド3個でスペシャルエメラルドを作成しました！");

    return true;
  }
}