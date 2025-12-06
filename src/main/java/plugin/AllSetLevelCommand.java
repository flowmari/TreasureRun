package plugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AllSetLevelCommand implements CommandExecutor {


  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (sender instanceof Player) {
      sender.sendMessage("このコマンドはコンソールからのみ実行できます。");
      return true;
    }

    if (args.length != 1) {
      sender.sendMessage("使用方法: /allsetlevel <レベル>");
      return false;
    }

    int level;
    try {
      level = Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      sender.sendMessage("有効な数字を入力してください。");
      return false;
    }

    for (Player player : Bukkit.getOnlinePlayers()) {
      player.setLevel(level);
      System.out.println("プレイヤー " + player.getName() + " のレベルが " + level + " に設定されました。");
    }

    return true;
  }
}