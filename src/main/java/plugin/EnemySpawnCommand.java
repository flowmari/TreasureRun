package plugin;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import plugin.BaseCommand;
import plugin.Main;

import java.util.List;
import java.util.SplittableRandom;

public class EnemySpawnCommand extends BaseCommand implements Listener {

  private final Main main;

  public EnemySpawnCommand(Main main) {
    this.main = main;
  }

  @Override
  public boolean onExecutePlayerCommand(Player player, Command command, String label, String[] args) {
    player.sendMessage(ChatColor.GRAY + "[DEBUG] EnemySpawnコマンドが呼ばれました"); // ← ここを追加

    EntityType enemy = getRandomEnemy();
    Location spawnLoc = getRandomSpawnLocation(player);

    Entity spawned = player.getWorld().spawnEntity(spawnLoc, enemy);
    if (spawned instanceof LivingEntity living) {
      living.setCustomName(player.getName() + "_enemy");
      living.setCustomNameVisible(false);
    }

    player.sendMessage("👾 敵を召喚しました: " + enemy.name());
    return true;
  }

  @Override
  public boolean onExecuteNPCCommand(CommandSender sender, Command command, String label, String[] args) {
    sender.sendMessage("このコマンドはプレイヤー専用です。");
    return false;
  }

  private EntityType getRandomEnemy() {
    List<EntityType> enemies = List.of(
        EntityType.ZOMBIE,
        EntityType.SKELETON,
        EntityType.SPIDER,
        EntityType.CREEPER,
        EntityType.WITCH
    );
    return enemies.get(new SplittableRandom().nextInt(enemies.size()));
  }

  private Location getRandomSpawnLocation(Player player) {
    Location loc = player.getLocation();
    int xOffset = new SplittableRandom().nextInt(-8, 9);
    int zOffset = new SplittableRandom().nextInt(-8, 9);
    return new Location(player.getWorld(), loc.getX() + xOffset, loc.getY(), loc.getZ() + zOffset);
  }
}