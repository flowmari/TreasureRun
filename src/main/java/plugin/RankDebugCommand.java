package plugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class RankDebugCommand implements CommandExecutor {

  private final TreasureRunMultiChestPlugin plugin;

  public RankDebugCommand(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }


  private String lang(Player player) {
    try {
      String lang = "en";
      if (plugin.getPlayerLanguageStore() != null) {
        lang = plugin.getPlayerLanguageStore().getLang(player, lang);
      }
      return lang;
    } catch (Throwable ignored) {
      return "en";
    }
  }

  private String tr(Player player, String key) {
    try {
      return plugin.getI18n().tr(lang(player), key);
    } catch (Throwable ignored) {
      return key;
    }
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

    // プレイヤーのみ
    if (!(sender instanceof Player player)) {
      sender.sendMessage(plugin.getI18n().tr("en", "finalAudit.command.playerOnly"));
      return true;
    }

    // Operator-only command for safety
    if (!player.isOp()) {
      player.sendMessage(tr(player, "finalAudit.command.opOnly"));
      return true;
    }

    // rankDebug.enabled=true のときだけ有効（安全）
    if (!plugin.getConfig().getBoolean("rankDebug.enabled", false)) {
      player.sendMessage(tr(player, "finalAudit.command.rankDebugOff"));
      return true;
    }

    // /rank demo  または /rank 1|2|3
    if (args.length != 1) {
      player.sendMessage(tr(player, "finalAudit.command.rankUsage"));
      return true;
    }

    // =========================
    // ✅ /rank demo（README動画用：宝物→1位 まで自動）
    // =========================
    if (args[0].equalsIgnoreCase("demo")) {
      GameStageManager stageManager = plugin.getGameStageManager();
      if (stageManager == null) return true;

      stageManager.clearDifficultyBlocks();
      stageManager.clearShopEntities();
      stageManager.prepareSeasideStageAsync(
          player,
          center -> {
            if (!player.isOnline()) return;
            try {
              if (!stageManager.teleportPlayerToPreparedStage(player, center)) return;
              stageManager.activatePreparedStage(center);
              stageManager.startPreparedStageArrival(player, center);
              stageManager.startLoopEffects(center);
              runDemoEffects(player, center);
            } catch (Throwable failure) {
              plugin.getLogger().warning(
                  "[RankDebug] demo stage continuation failed: " + failure.getMessage()
              );
            }
          },
          failure -> plugin.getLogger().warning(
              "[RankDebug] demo arena preparation failed: " + failure.getMessage()
          )
      );
      return true;
    }

    // =========================
    // ✅ /rank 1|2|3（演出だけ）
    // =========================
    int rank;
    try {
      rank = Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      player.sendMessage(tr(player, "finalAudit.command.rankNumberOnly"));
      return true;
    }

    if (rank < 1 || rank > 3) {
      player.sendMessage(tr(player, "finalAudit.command.rankOnlyOneToThree"));
      return true;
    }

    plugin.getRankRewardManager().giveRankRewardWithEffect(player, rank);

    // ✅ READMEにデバッグ文字を出さない
    // player.sendMessage("DEBUG: rank " + rank + " の演出だけ発動しました（ランキング/DBは変更なし）");

    return true;
  }
  private void runDemoEffects(Player player, Location center) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;
      Location demoSpot = center.clone().add(4.0, 1.2, 4.0);
      demoSpot.setDirection(center.toVector().subtract(demoSpot.toVector()));
      player.teleport(demoSpot);
    }, 1L);

    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;

      Location base = player.getLocation().clone()
          .add(player.getLocation().getDirection().normalize().multiply(1.3))
          .add(0, 1.0, 0);
      ItemStack treasure = new ItemStack(Material.DIAMOND, 1);
      base.getWorld().playSound(base, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
      Item drop = base.getWorld().dropItem(base, treasure);
      drop.setPickupDelay(Integer.MAX_VALUE);
      drop.setVelocity(new Vector(0, 0.35, 0));

      if (plugin.getTreasureRunGameEffectsPlugin() != null) {
        plugin.getTreasureRunGameEffectsPlugin().playMiniDJEffect(player);
      }

      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (!player.isOnline()) return;
        plugin.getRankRewardManager().giveRankRewardWithEffect(player, 1);
      }, 25L);
      Bukkit.getScheduler().runTaskLater(plugin, drop::remove, 60L);
    }, 10L);
  }

}