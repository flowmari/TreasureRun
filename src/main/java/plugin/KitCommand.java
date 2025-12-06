package plugin;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class KitCommand implements CommandExecutor {

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    // プレイヤーかどうか確認
    if (!(sender instanceof Player player)) {
      sender.sendMessage("§cこのコマンドはプレイヤー専用です。");
      return true;
    }

    // パーミッションチェック
    if (!player.hasPermission("enemydown.kit")) {
      player.sendMessage("§cこのコマンドを使う権限がありません。");
      return true;
    }

    // プレイヤーの状態をリセット
    player.setHealth(player.getMaxHealth());
    player.setFoodLevel(20);
    player.setLevel(0);

    // インベントリ初期化
    PlayerInventory inventory = player.getInventory();
    inventory.clear();
    inventory.setArmorContents(null);

    // 防具装備
    inventory.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
    inventory.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
    inventory.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
    inventory.setBoots(new ItemStack(Material.DIAMOND_BOOTS));

    // メインハンドにダイヤモンドの剣
    inventory.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

    // 必要があればここに追加アイテム
    // inventory.addItem(new ItemStack(Material.BOW));
    // inventory.addItem(new ItemStack(Material.ARROW, 64));
    // inventory.addItem(new ItemStack(Material.COOKED_BEEF, 10));

    // メッセージ送信
    player.sendMessage("§a🛡️ キットを支給しました！戦いの準備は万端です。");

    return true;
  }
}