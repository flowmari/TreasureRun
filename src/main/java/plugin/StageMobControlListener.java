package plugin;

import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/**
 * 宝箱ステージのモブ制御用リスナー。
 * ・ゲーム中は「行商人＋行商人ラマ」以外のスポーンを止める
 * ・さらに「カウントダウン中（/gameStart 実行〜 startGame 前）」も止める（暗闇スポーン対策）
 *
 * ※TreasureRunMultiChestPlugin 側の状態を変更せずに実現するため、
 *   カウントダウン中の判定はプラグイン内部フィールドを reflection で参照します。
 */
public class StageMobControlListener implements Listener {

  private final TreasureRunMultiChestPlugin plugin;

  // ---- reflection cache（1回取得して使い回す）----
  private Field fCurrentStageCenter;
  private Field fTotalChestsRemaining;
  private Field fPlayerScores;
  private boolean reflectionInitTried = false;
  private boolean reflectionAvailable = false;
  private boolean reflectionWarned = false;

  public StageMobControlListener(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void onCreatureSpawn(CreatureSpawnEvent event) {

    // ======== いつ制御するか ========
    // ・ゲーム中(plugin.isGameRunning==true)は当然止める
    // ・さらに、/gameStart 実行後〜 startGame() に入るまでの「カウントダウン中」も止める
    //   （この間は isGameRunning==false なので、ここを追加しないと暗闇スポーンが混ざる）
    boolean shouldControl = plugin.isGameRunning() || isCountdownOrStagePreparing();

    if (!shouldControl) {
      return;
    }

    EntityType type = event.getEntityType();

    // 行商人と、そのラマだけは許可（SpawnReasonは問わない）
    if (type == EntityType.WANDERING_TRADER ||
        type == EntityType.TRADER_LLAMA) {
      return;
    }

    // ✅ 追加：報酬演出の「虹色の狼」を許可（キャンセル済みでも復活させる）
    if (type == EntityType.WOLF) {
      // パターンA: SpawnReason が CUSTOM
      if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
        plugin.getLogger().info("[MobControl] WOLF allowed (CUSTOM). name=" + event.getEntity().getCustomName());
        event.setCancelled(false);
        return;
      }

      // パターンB: たとえ Reason が違っても「Rainbow Wolf」なら許可（色コード入りでも contains でOK）
      String name = event.getEntity().getCustomName();
      if (name != null && name.contains("Rainbow Wolf")) {
        plugin.getLogger().info("[MobControl] WOLF allowed (name match). reason=" + event.getSpawnReason()
            + " name=" + name);
        event.setCancelled(false);
        return;
      }

      // ✅ ログ：もしここまで来たら「虹狼ではないWOLF」なのでキャンセル対象
      plugin.getLogger().warning("[MobControl] WOLF cancelled! reason=" + event.getSpawnReason()
          + " name=" + name);
    }

    // それ以外は湧きをキャンセル（SpawnReasonも問わず確実に止める）
    // ※プレイヤーがスポーンエッグで出す動物/モブ等も含めて止まります（「確実に湧かない」優先）
    event.setCancelled(true);
  }

  /**
   * /gameStart 実行直後の「カウントダウン中」を検出する。
   * TreasureRunMultiChestPlugin は startGame() で isRunning=true にするため、
   * その前に暗い環境だと自然湧き（例: クリーパー）が混ざることがある。
   *
   * ここでは「ステージ中心がセットされている」「宝箱残数 > 0」「playerScores が空でない」
   * を満たす場合を “カウントダウン中 or 準備中” とみなす。
   */
  private boolean isCountdownOrStagePreparing() {
    tryInitReflection();

    if (!reflectionAvailable) {
      return false; // reflection が使えない環境では、ゲーム中のみ制御にフォールバック
    }

    try {
      Object stageCenter = fCurrentStageCenter.get(plugin);
      int remaining = (int) fTotalChestsRemaining.get(plugin);
      @SuppressWarnings("unchecked")
      Map<UUID, Integer> scores = (Map<UUID, Integer>) fPlayerScores.get(plugin);

      // カウントダウン〜ゲーム準備中の「それっぽい状態」
      return stageCenter != null && remaining > 0 && scores != null && !scores.isEmpty();

    } catch (Throwable t) {
      if (!reflectionWarned) {
        reflectionWarned = true;
        plugin.getLogger().warning("⚠ StageMobControlListener: カウントダウン判定(reflection)に失敗。ゲーム中のみ制御します。原因: " + t.getMessage());
      }
      reflectionAvailable = false;
      return false;
    }
  }

  private void tryInitReflection() {
    if (reflectionInitTried) return;
    reflectionInitTried = true;

    try {
      fCurrentStageCenter = TreasureRunMultiChestPlugin.class.getDeclaredField("currentStageCenter");
      fTotalChestsRemaining = TreasureRunMultiChestPlugin.class.getDeclaredField("totalChestsRemaining");
      fPlayerScores = TreasureRunMultiChestPlugin.class.getDeclaredField("playerScores");

      fCurrentStageCenter.setAccessible(true);
      fTotalChestsRemaining.setAccessible(true);
      fPlayerScores.setAccessible(true);

      reflectionAvailable = true;

    } catch (Throwable t) {
      reflectionAvailable = false;
      plugin.getLogger().warning("⚠ StageMobControlListener: reflection初期化に失敗。ゲーム中のみ制御します。原因: " + t.getMessage());
    }
  }
}