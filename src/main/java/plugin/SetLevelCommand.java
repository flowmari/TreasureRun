package plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetLevelCommand implements CommandExecutor {

  private final Main main;

  public SetLevelCommand(Main main) {
    this.main = main;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (sender instanceof Player player) {
      if (args.length == 1) {
        try {
          int level = Integer.parseInt(args[0]);
          player.setLevel(level);
          player.sendMessage("レベルが " + level + " に設定されました。");
        } catch (NumberFormatException e) {
          player.sendMessage("有効な数字を入力してください。");
        }
      } else {
        String message = main.getConfig().getString("Message");
        if (message != null) {
          player.sendMessage(message);
        } else {
          player.sendMessage("設定メッセージが見つかりません。");
        }
      }
    } else {
      sender.sendMessage("このコマンドはプレイヤーのみ使用可能です。");
    }
    return true;
  }
}