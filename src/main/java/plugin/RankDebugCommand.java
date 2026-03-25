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

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

    // プレイヤーのみ
    if (!(sender instanceof Player player)) {
      sender.sendMessage("プレイヤーのみ実行できます");
      return true;
    }

    // OP限定（安全）
    if (!player.isOp()) {
      player.sendMessage("権限がありません（OP限定）");
      return true;
    }

    // rankDebug.enabled=true のときだけ有効（安全）
    if (!plugin.getConfig().getBoolean("rankDebug.enabled", false)) {
      player.sendMessage("Rank debug is OFF. config.yml の rankDebug.enabled: true にしてください");
      return true;
    }

    // /rank demo  または /rank 1|2|3
    if (args.length != 1) {
      player.sendMessage("使い方: /rank <1|2|3|demo>");
      return true;
    }

    // =========================
    // ✅ /rank demo（README動画用：宝物→1位 まで自動）
    // =========================
    if (args[0].equalsIgnoreCase("demo")) {

      // 1) 海上ステージへワープ（ゲーム開始はしない）
      Location center = null;
      if (plugin.getGameStageManager() != null) {
        plugin.getGameStageManager().clearDifficultyBlocks();
        plugin.getGameStageManager().clearShopEntities();
        center = plugin.getGameStageManager().buildSeasideStageAndTeleport(player);
        if (center != null) plugin.getGameStageManager().startLoopEffects(center);
      }

      // ✅ ラムダ用に final 変数へコピー（これがポイント）
      final Location centerFinal = center;

      // ✅ 行商人に被らない「撮影スポット」へ移動して、中心を見る
      if (centerFinal != null) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
          if (!player.isOnline()) return;

          // 中心から6ブロック離れた位置（被り防止）
          Location demoSpot = centerFinal.clone().add(4.0, 1.2, 4.0);

          // その場所から中心方向を向かせる（宝物が“前に出る”ようになる）
          demoSpot.setDirection(centerFinal.toVector().subtract(demoSpot.toVector()));

          player.teleport(demoSpot);
        }, 1L);
      }

      // 2) すぐ「宝物が出る演出」（見た目：アイテムがポンと出る）
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (!player.isOnline()) return;

        Location base = player.getLocation().clone()
            .add(player.getLocation().getDirection().normalize().multiply(1.3))
            .add(0, 1.0, 0);

        // READMEに映したい宝物（見た目用。必要なら変更OK）
        ItemStack treasure = new ItemStack(Material.DIAMOND, 1);

        // チェスト開く音（それっぽく）
        base.getWorld().playSound(base, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);

        // アイテムが出る
        Item drop = base.getWorld().dropItem(base, treasure);
        drop.setPickupDelay(Integer.MAX_VALUE); // 取れないようにする（動画用）
        drop.setVelocity(new Vector(0, 0.35, 0)); // 上にふわっと

        // 3) 宝物取得時の「音楽（ミニDJ）」を鳴らす（DBは触らない）
        if (plugin.getTreasureRunGameEffectsPlugin() != null) {
          plugin.getTreasureRunGameEffectsPlugin().playMiniDJEffect(player);
        }

        // 4) 少し待ってから「1位演出」
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
          if (!player.isOnline()) return;
          plugin.getRankRewardManager().giveRankRewardWithEffect(player, 1);
        }, 25L);

        // 動画用アイテムはあとで消す
        Bukkit.getScheduler().runTaskLater(plugin, drop::remove, 60L);

      }, 10L);

      // ✅ READMEにデバッグ文字を一切出さない（何もsendMessageしない）
      return true;
    }

    // =========================
    // ✅ /rank 1|2|3（演出だけ）
    // =========================
    int rank;
    try {
      rank = Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      player.sendMessage("数字で入力してください: /rank <1|2|3|demo>");
      return true;
    }

    if (rank < 1 || rank > 3) {
      player.sendMessage("1〜3だけ使えます: /rank <1|2|3>");
      return true;
    }

    plugin.getRankRewardManager().giveRankRewardWithEffect(player, rank);

    // ✅ READMEにデバッグ文字を出さない
    // player.sendMessage("DEBUG: rank " + rank + " の演出だけ発動しました（ランキング/DBは変更なし）");

    return true;
  }
}