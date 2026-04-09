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
import java.util.Map;
import java.util.Random;

public class TreasureRunStartCommand implements CommandExecutor {

  private final TreasureRunMultiChestPlugin plugin;
  private final I18nHelper i18n;
  private final Random random = new Random();

  public TreasureRunStartCommand(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
    this.i18n = new I18nHelper(plugin);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(i18n.trDefault(
          "command.gameStart.playersOnly",
          "This command can only be used by players."
      ));
      return true;
    }

    String difficulty = "Easy";
    int timeLimit = plugin.getConfig().getInt("difficultySettings." + difficulty + ".timeLimit", 300);

    String worldName = plugin.getConfig().getString("startLocation.world");
    World world = Bukkit.getWorld(worldName);
    if (world == null) {
      player.sendMessage(i18n.tr(
          player,
          "command.gameStart.worldNotFound",
          "[TreasureRun] The start world is not configured or could not be found."
      ));
      return true;
    }

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

    for (int i = 0; i < treasureChestCount + otherChestCount; i++) {
      double offsetX = random.nextInt(chestSpawnRadius * 2 + 1) - chestSpawnRadius;
      double offsetZ = random.nextInt(chestSpawnRadius * 2 + 1) - chestSpawnRadius;
      Location loc = new Location(world, startX + offsetX, startY, startZ + offsetZ);
      chestLocations.add(loc);
    }

    Collections.shuffle(chestLocations);
    Location treasureLocation = chestLocations.get(0);

    for (Location loc : chestLocations) {
      Block block = world.getBlockAt(loc);
      block.setType(Material.CHEST);
      if (block.getState() instanceof Chest chest) {
        if (loc.equals(treasureLocation)) {
          chest.getInventory().addItem(new ItemStack(treasureMaterial, 1));
        }
      }
    }

    plugin.startVanillaMusicSuppress(player);

    player.sendMessage(i18n.trp(
        player,
        "command.gameStart.started",
        Map.of(
            "difficulty", difficulty,
            "timeLimit", String.valueOf(timeLimit)
        ),
        "Treasure hunt started! Difficulty: {difficulty} | Time limit: {timeLimit} seconds"
    ));

    plugin.getLanguageSelectGui().open(player, difficulty);
    plugin.getLogger().info("TreasureRun: " + chestLocations.size() + " chests generated. Treasure is at " + treasureLocation);

    return true;
  }
}
