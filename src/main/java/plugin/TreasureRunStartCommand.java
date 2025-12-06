package plugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class TreasureRunStartCommand implements CommandExecutor {

  private final TreasureRunPlugin plugin;
  private final Random random = new Random();

  public TreasureRunStartCommand(TreasureRunPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("このコマンドはプレイヤーからのみ実行できます。");
      return true;
    }

    String difficulty = "Easy"; // 仮でEasy固定、後で args[0] で切り替え可能
    int timeLimit = plugin.getConfig().getInt("difficultySettings." + difficulty + ".timeLimit", 300);

    // チェスト生成情報
    World world = Bukkit.getWorld(plugin.getConfig().getString("startLocation.world"));
    double startX = plugin.getConfig().getDouble("startLocation.x");
    double startY = plugin.getConfig().getDouble("startLocation.y");
    double startZ = plugin.getConfig().getDouble("startLocation.z");
    int treasureChestCount = plugin.getConfig().getInt("chests.treasureChestCount", 1);
    int otherChestCount = plugin.getConfig().getInt("chests.otherChestCount." + difficulty, 3);
    int chestSpawnRadius = plugin.getConfig().getInt("chests.chestSpawnRadius", 20);
    String treasureName = plugin.getConfig().getString("treasureItem", "DIAMOND");
    Material treasureMaterial = Material.getMaterial(treasureName.toUpperCase());
    if (treasureMaterial == null) treasureMaterial = Material.DIAMOND;

    List<Location> chestLocations = new ArrayList<>();

    // 宝物チェストの座標をランダム生成
    for (int i = 0; i < treasureChestCount + otherChestCount; i++) {
      double offsetX = random.nextInt(chestSpawnRadius * 2 + 1) - chestSpawnRadius;
      double offsetZ = random.nextInt(chestSpawnRadius * 2 + 1) - chestSpawnRadius;
      Location loc = new Location(world, startX + offsetX, startY, startZ + offsetZ);
      chestLocations.add(loc);
    }

    // 宝物を置くチェストをランダムで1つ選ぶ
    Collections.shuffle(chestLocations);
    Location treasureLocation = chestLocations.get(0);

    for (Location loc : chestLocations) {
      Block block = world.getBlockAt(loc);
      block.setType(Material.CHEST);
      if (block.getState() instanceof Chest chest) {
        // 宝物はランダムに1つだけ
        if (loc.equals(treasureLocation)) {
          chest.getInventory().addItem(new ItemStack(treasureMaterial, 1));
        }
      }
    }

    player.sendMessage("§a宝探しゲーム開始！ 難易度: " + difficulty + " 制限時間: " + timeLimit + " 秒");
    plugin.getLogger().info("TreasureRun: " + chestLocations.size() + " 個のチェストを生成しました。宝物は " + treasureLocation);

    return true;
  }
}