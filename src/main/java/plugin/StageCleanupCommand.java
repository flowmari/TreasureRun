package plugin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class StageCleanupCommand implements CommandExecutor {

  private final TreasureRunMultiChestPlugin plugin;
  private final I18nHelper i18n;

  public StageCleanupCommand(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
    this.i18n = new I18nHelper(plugin);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

    if (!(sender instanceof Player player)) {
      sender.sendMessage(ChatColor.RED + i18n.trDefault(
          "command.stageCleanup.playersOnly",
          "This command can only be used by players."
      ));
      return true;
    }

    GameStageManager gsm = plugin.getGameStageManager();
    if (gsm == null) {
      player.sendMessage(ChatColor.RED + i18n.tr(
          player,
          "command.stageCleanup.managerNotReady",
          "[TreasureRun] The stage manager is not initialized."
      ));
      return true;
    }

    int cleaned = gsm.clearDifficultyBlocks();

    player.sendMessage(ChatColor.AQUA + i18n.trp(
        player,
        "command.stageCleanup.cleaned",
        Map.of("count", String.valueOf(cleaned)),
        "[TreasureRun] Cleaned up {count} difficulty blocks."
    ));

    return true;
  }
}
