package plugin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StageCleanupCommand implements CommandExecutor {

  private final TreasureRunMultiChestPlugin plugin;

  public StageCleanupCommand(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

    if (!(sender instanceof Player player)) {
      sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
      return true;
    }

    GameStageManager gsm = plugin.getGameStageManager();
    if (gsm == null) {
      player.sendMessage(ChatColor.RED + "[TreasureRun] ステージ管理が初期化されていません。");
      return true;
    }

    int cleaned = gsm.clearDifficultyBlocks();

    player.sendMessage(ChatColor.AQUA + "[TreasureRun] "
        + ChatColor.YELLOW + cleaned
        + ChatColor.AQUA + " 個の難易度ブロックを片付けました。");

    return true;
  }
}