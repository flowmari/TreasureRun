package plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.World;

public class ZombieCommand implements CommandExecutor {

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player)) {
      sender.sendMessage("このコマンドはプレイヤーのみ使用できます。");
      return true;
    }

    Player player = (Player) sender;
    World world = player.getWorld();
    Location location = player.getLocation();

    world.spawn(location, Zombie.class);
    player.sendMessage("ゾンビを召喚しました！");
    return true;
  }
}
