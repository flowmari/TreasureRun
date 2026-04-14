package plugin;

import org.bukkit.Bukkit;

import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * UfoCaravanController (Complete Integrated Edition)
 *
 * ✅ BlockDisplay版の豪華UFO（円盤/ドーム/ライト）＋フェードイン（スケール0→1）
 * ✅ Arrival：UFO真上→青緑ビーム→中間で商人＋ラマ2頭が“同時に出現”（bind済みなら移動 / 無ければspawn保険）→降下→着地→UFO消える
 * ✅ Departure：UFO真上→青緑ビーム→商人＋ラマが光の中でゆっくり上昇→中間(vanishY)で“スッと消える転送アウト”→UFO消える
 *
 * ✅ ArmorStand版（不可視）＋粒子（円盤/ビーム）演出も「削除せず」同梱
 * ✅ bindGroup/bindPair/unbind API 同梱
 * ✅ Controller単体でも成立する Treasure Shop レシピ作成も同梱
 * ✅ 多重起動防止：busy（主）＋ runningArrival/runningDeparture（互換）
 *
 * ✅ 追加（100%同期用：GameStageManager直呼び確定API）
 * - Arrival/Departure の「真の完了フラグ」を提供
 *   -> isArrivalComplete(Player), isDepartureComplete()
 * - trader/llama にタグ/PDCを必ず付与（spawn保険／bind済み個体／補充個体すべて）
 *   -> scoreboard tag: TR_UFO_CARAVAN
 *   -> PDC: ufo_owner(UUID文字列), ufo_role(trader/llama)
 */
public class UfoCaravanController {

  // ============================================================
  // プラグイン参照
  // ============================================================

  /** BlockDisplay版は JavaPlugin でも足りるが、レシピ作成で TreasureRunMultiChestPlugin にも対応 */
  private final JavaPlugin plugin;
  private final TreasureRunMultiChestPlugin treasurePluginOrNull;

  // ============================================================
  // ✅ 追加：100% 同期用（タグ/PDC + “真の完了”フラグ）
  // ============================================================

  private static final String TAG_UFO = "TR_UFO_CARAVAN";
  private static final String ROLE_TRADER = "trader";
  private static final String ROLE_LLAMA  = "llama";

  // ✅ NPE回避：NamespacedKey はコンストラクタで plugin 確定後に初期化する
  private final NamespacedKey KEY_UFO_OWNER;
  private final NamespacedKey KEY_UFO_ROLE;

  // “真のArrival/Departure完了” フラグ
  private UUID lastOwnerId;            // startArrival(owner, ...) で記録（owner無しなら null のまま）
  private UUID arrivalOwnerId;         // 進行中Arrivalのowner（ポーリングは Player/UUID で確認可能）
  private boolean arrivalEffectDone = false;   // UFO消失まで含めて完了
  private boolean departureEffectDone = false; // UFO消失まで含めて完了

  public UfoCaravanController(JavaPlugin plugin) {
    this.plugin = plugin;
    this.treasurePluginOrNull = (plugin instanceof TreasureRunMultiChestPlugin)
        ? (TreasureRunMultiChestPlugin) plugin
        : null;

    // ✅ plugin 確定後に初期化
    this.KEY_UFO_OWNER = new NamespacedKey(this.plugin, "ufo_owner");
    this.KEY_UFO_ROLE  = new NamespacedKey(this.plugin, "ufo_role");
  }

  public UfoCaravanController(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
    this.treasurePluginOrNull = plugin;

    // ✅ plugin 確定後に初期化
    this.KEY_UFO_OWNER = new NamespacedKey(this.plugin, "ufo_owner");
    this.KEY_UFO_ROLE  = new NamespacedKey(this.plugin, "ufo_role");
  }

  /** ✅ 到着演出完了（UFO消えた）フラグ：推奨API */
  public boolean isArrivalComplete(Player player) {
    // owner指定がある場合は owner一致を推奨するが、ここでは “完了状態” を返す（GameStageManager側でowner管理するなら十分）
    return arrivalEffectDone;
  }

  // ✅ ADD: GameStageManager は (UUID) 版も探すので、必ず用意する（呼び出し失敗ゼロへ）
  public boolean isArrivalComplete(UUID ownerId) { return arrivalEffectDone; }
  public boolean hasArrivalCompleted(UUID ownerId) { return isArrivalComplete(ownerId); }
  public boolean isArrived(UUID ownerId) { return isArrivalComplete(ownerId); }
  public boolean hasArrived(UUID ownerId) { return isArrivalComplete(ownerId); }
  public boolean isArrivalDone(UUID ownerId) { return isArrivalComplete(ownerId); }
  public boolean arrivalCompleted(UUID ownerId) { return isArrivalComplete(ownerId); }

  /** ✅ 互換エイリアス（GameStageManagerが候補で探しやすい） */
  public boolean hasArrived(Player player) { return isArrivalComplete(player); }
  public boolean isArrived(Player player) { return isArrivalComplete(player); }
  public boolean isArrivalDone(Player player) { return isArrivalComplete(player); }
  public boolean hasArrivalCompleted(Player player) { return isArrivalComplete(player); }
  public boolean arrivalCompleted(Player player) { return isArrivalComplete(player); }

  /** ✅ 出発演出完了（UFO消えた）フラグ：推奨API */
  public boolean isDepartureComplete() {
    return departureEffectDone;
  }

  /** ✅ 互換エイリアス */
  public boolean hasDeparted() { return isDepartureComplete(); }
  public boolean isDeparted() { return isDepartureComplete(); }
  public boolean isDepartureDone() { return isDepartureComplete(); }
  public boolean hasDepartureCompleted() { return isDepartureComplete(); }
  public boolean departureCompleted() { return isDepartureComplete(); }

  // ✅ ADD: GameStageManager は (Player) 版の Departure完了も探し得るので用意（失敗ゼロへ）
  public boolean isDepartureComplete(Player player) { return isDepartureComplete(); }
  public boolean hasDeparted(Player player) { return isDepartureComplete(); }
  public boolean isDeparted(Player player) { return isDepartureComplete(); }
  public boolean isDepartureDone(Player player) { return isDepartureComplete(); }
  public boolean hasDepartureCompleted(Player player) { return isDepartureComplete(); }
  public boolean departureCompleted(Player player) { return isDepartureComplete(); }

  /** ✅ タグ/PDC 付与（spawn保険／bind済み個体／補充個体に必ず適用） */
  private void markAsUfoEntity(UUID ownerId, Entity entity, String role) {
    if (entity == null) return;

    // scoreboard tag
    try { entity.addScoreboardTag(TAG_UFO); } catch (Exception ignored) {}

    // PDC
    try {
      PersistentDataContainer pdc = entity.getPersistentDataContainer();
      if (ownerId != null) {
        pdc.set(KEY_UFO_OWNER, PersistentDataType.STRING, ownerId.toString());
      }
      if (role != null) {
        pdc.set(KEY_UFO_ROLE, PersistentDataType.STRING, role);
      }
    } catch (Exception ignored) {}

    // ✅ MSZ用：carrier印（補充spawnのときも対象になる）
    try { entity.setMetadata(MovingSafetyZoneTask.CARRIER_META, new FixedMetadataValue(plugin, true)); } catch (Exception ignored) {}
  }

  /** ✅ bind済み個体にもタグ/PDCを必ず付与して追跡可能にする */
  private void ensureMarkedGroup(UUID ownerId) {
    if (trader != null && trader.isValid()) {
      markAsUfoEntity(ownerId, trader, ROLE_TRADER);
    }
    for (TraderLlama l : boundLlamas) {
      if (l == null || !l.isValid()) continue;
      markAsUfoEntity(ownerId, l, ROLE_LLAMA);
    }
  }

  // ============================================================
  // ✅ Group保持（商人1 + ラマ複数）  ※ArmorStand版の bound～ も維持
  // ============================================================

  // ---- BlockDisplay版（元コード）互換名 ----
  private WanderingTrader trader;

  /** boundLlamas と llamas は「同じ List オブジェクト」を参照（削除しない・二重管理しない） */
  private final List<TraderLlama> boundLlamas = new ArrayList<>();
  private final List<TraderLlama> llamas = boundLlamas;

  // ---- ArmorStand版（元コード）互換名 ----
  private WanderingTrader boundTrader;

  // ============================================================
  // UFO本体（BlockDisplay群）
  // ============================================================

  private final List<UfoPart> ufoParts = new ArrayList<>();

  // ============================================================
  // 状態管理：busy（主）＋ runningArrival/runningDeparture（互換）
  // ============================================================

  private boolean busy = false;

  // 互換（削除しない）
  private boolean runningArrival = false;
  private boolean runningDeparture = false;

  // landing/pickup の最後に使った中心（互換）
  private Location lastCenter;

  // ============================================================
  // ===== 見た目：蛍光青緑（BlockDisplay版粒子）
  // ============================================================

  private final Particle.DustOptions neonTeal = new Particle.DustOptions(Color.fromRGB(80, 255, 220), 1.6f);
  private final Particle.DustOptions beamTeal = new Particle.DustOptions(Color.fromRGB(120, 255, 210), 1.85f);
  private final Particle.DustOptions ringWhite = new Particle.DustOptions(Color.fromRGB(240, 240, 240), 1.1f);

  // ============================================================
  // ===== パラメータ（BlockDisplay版）
  // ============================================================

  private final double ufoHeight = 24.0;     // UFO高度（地上から）
  private final double midHeight = 12.0;     // 中間スポーン/中間消滅（地上から）

  private final double descentPerTick = 0.22;
  private final double risePerTick = 0.22;

  // UFO外周リング（粒子）
  private final double ufoRingRadius = 2.8;
  private final int ufoRingPoints = 24;

  // ビーム白リング段数
  private final int beamRingLevels = 5;

  // UFOフェードイン設定（スケールを0→1へ）
  private final int ufoFadeInTicks = 18; // 0.9秒（20tps基準）
  private final float ufoFullScale = 1.0f;

  // ============================================================
  // ===== パラメータ（ArmorStand版）※削除しない（互換/追加演出）
  // ============================================================

  private static final double UFO_HEIGHT = 16.0;              // ArmorStand版のUFO高さ
  private static final double BEAM_RADIUS = 0.55;             // ビームの太さ感
  private static final double DISC_RADIUS = 1.6;              // UFOディスク半径
  private static final double ARRIVAL_SPAWN_RATIO = 0.55;     // 中間(割合) ※互換
  private static final double DESCEND_SPEED = 0.18;           // 降下速度 ※互換
  private static final double ASCEND_SPEED = 0.14;            // 上昇速度 ※互換
  private static final int BEAM_TICK = 2;                     // ビーム更新間隔（tick）※互換
  private static final int ARRIVAL_SPAWN_DELAY = 10;          // 出現遅延 ※互換
  private static final int LANDING_GRACE_TICKS = 10;          // 着地判定余裕 ※互換
  private static final int UFO_VANISH_DELAY = 16;             // 演出後UFO消えるまで ※互換

  private static final Color BEAM_A = Color.fromRGB(40, 255, 220);
  private static final Color BEAM_B = Color.fromRGB(60, 180, 255);

  // ArmorStand版UFOの「目印」（不可視）
  private ArmorStand ufoStand;

  // ============================================================
  // 外側API（GameStageManager用）
  // ============================================================

  /** BlockDisplay版の midHeight を外側へ */
  public double getMidHeightOffset() {
    return midHeight;
  }

  public boolean isBusy() {
    return busy;
  }

  // ---- 互換（ArmorStand版の getter 名を残す） ----
  public WanderingTrader getBoundTrader() {
    return boundTrader;
  }

  public List<TraderLlama> getBoundLlamasSnapshot() {
    return Collections.unmodifiableList(new ArrayList<>(boundLlamas));
  }

  // ============================================================
  // ✅ ADD: GameStageManager 側の reflection “候補名” を100%吸う互換メソッド群
  // ============================================================
  // GameStageManager.invokeArrivalStartOnUfoController が探す：
  //   (Player, Location) : startArrival / tryStartArrival / beginArrival / startUfoArrival / tryStartUfoArrival / spawnArrival / startCaravanArrival
  //   (Player)           : startArrival / tryStartArrival / beginArrival / startUfoArrival / tryStartUfoArrival
  //
  // GameStageManager.invokeDepartureStartOnUfoController が探す：
  //   ()      : tryStartUfoDeparture / startUfoDeparture / tryStartDeparture / startDeparture / beginDeparture / startCaravanDeparture / despawnCaravan
  //   (Player): tryStartUfoDeparture / startUfoDeparture / tryStartDeparture / startDeparture / beginDeparture / startCaravanDeparture

  // ---- Arrival alias (Player, Location) ----
  public void beginArrival(Player owner, Location landing) { tryStartArrival(owner, landing); }
  public void startUfoArrival(Player owner, Location landing) { tryStartArrival(owner, landing); }
  public void tryStartUfoArrival(Player owner, Location landing) { tryStartArrival(owner, landing); }
  public void spawnArrival(Player owner, Location landing) { tryStartArrival(owner, landing); }
  public void startCaravanArrival(Player owner, Location landing) { tryStartArrival(owner, landing); }

  // ---- Arrival alias (Player only) ----
  // ※ Location無し版は「最後の中心(lastCenter)」があればそこで開始。無ければ何もしない（例外で落とさない）
  public void beginArrival(Player owner) { startUfoArrival(owner); }
  public void startUfoArrival(Player owner) {
    Location c = (lastCenter != null ? lastCenter.clone() : null);
    if (c == null) return;
    tryStartArrival(owner, c);
  }
  public void tryStartUfoArrival(Player owner) { startUfoArrival(owner); }

  // ---- Departure alias (no-arg) ----
  public void tryStartUfoDeparture() { tryStartDeparture(); }
  public void startUfoDeparture() { tryStartDeparture(); }

  // ❌ ここが boolean tryStartDeparture() と衝突して「あいまい」になる
  // public void tryStartDeparture() { tryStartDeparture((Player) null); }

  public void beginDeparture() { tryStartDeparture((Player) null); }
  public void startCaravanDeparture() { tryStartDeparture((Player) null); }
  public void despawnCaravan() { tryStartDeparture((Player) null); }

  // ---- Departure alias (Player) ----
  public void tryStartUfoDeparture(Player owner) { tryStartDeparture(owner); }
  public void startUfoDeparture(Player owner) { tryStartDeparture(owner); }

  // ❌ boolean tryStartDeparture(Player) と衝突 + 自己再帰になるのでコメントアウト
  // public void tryStartDeparture(Player owner) { tryStartDeparture(owner); }

  public void startDeparture(Player owner) { tryStartDeparture(owner); }    // 既存 startDeparture() はあるが、(Player) 版も用意
  public void beginDeparture(Player owner) { tryStartDeparture(owner); }
  public void startCaravanDeparture(Player owner) { tryStartDeparture(owner); }

  // ============================================================
  // bind API（外側から「回収対象」を渡す）
  // ============================================================

  public void bindGroup(WanderingTrader trader, List<TraderLlama> llamas) {
    this.trader = trader;
    this.boundTrader = trader;

    this.boundLlamas.clear();
    if (llamas != null) {
      for (TraderLlama l : llamas) {
        if (l != null) this.boundLlamas.add(l);
      }
    }
  }

  public void bindPair(WanderingTrader trader, TraderLlama llama) {
    bindGroup(trader, (llama == null) ? Collections.emptyList() : java.util.List.of(llama));
  }

  public void unbind() {
    this.trader = null;
    this.boundTrader = null;
    this.boundLlamas.clear();
  }

  // ✅ ADD: GameStageManager.clearShopEntitiesInternal が reflection で "clearBind" も呼ぶ可能性があるため保険
  public void clearBind() { unbind(); }

  // ============================================================
  // ✅ 直呼び用：外側（GameStageManager等）から
  // ============================================================

  /**
   * ✅ 追加：owner付き版（推奨）
   * - “この到着は誰のUFOか” を Controller が把握できるので
   *   PDC ufo_owner を確実に埋められる
   */
  public boolean tryStartArrival(Player owner, Location landing) {
    if (landing == null || landing.getWorld() == null) return false;
    if (busy) return false;

    // 完了フラグをリセット
    arrivalEffectDone = false;
    departureEffectDone = false;

    // owner記録
    this.arrivalOwnerId = (owner == null ? null : owner.getUniqueId());
    this.lastOwnerId = this.arrivalOwnerId;

    // 互換フラグも同期
    busy = true;
    runningArrival = true;
    runningDeparture = false;

    startArrival(owner, landing);
    return true;
  }

  /** すでにbusyなら開始しない（=false）/ 開始できたら true */
  public boolean tryStartArrival(Location landing) {
    // 互換（owner無し）
    return tryStartArrival((Player) null, landing);
  }

  /**
   * ✅ 追加：Departureの直呼び確定API（推奨）
   * - ownerが無い場合でも lastOwnerId があれば PDC に入る
   */
  public boolean tryStartDeparture(Player owner) {
    if (busy) return false;

    // ✅「商人がいる」ことだけを最低条件にする（ラマ不足は startDeparture 内で補充）
    if (trader == null || !trader.isValid()) return false;

    // 完了フラグをリセット
    departureEffectDone = false;

    // owner記録（任意）
    UUID oid = (owner == null ? null : owner.getUniqueId());
    if (oid != null) this.lastOwnerId = oid;

    busy = true;
    runningDeparture = true;
    runningArrival = false;

    startDeparture(oid);
    return true;
  }

  /** ペア/グループがいなければ開始できない（=false）/ 開始できたら true */
  public boolean tryStartDeparture() {
    return tryStartDeparture((Player) null);
  }

  // ============================================================
  // ✅ Arrival：豪華UFOフェードイン → 青緑ビーム → 中間で同時出現(移動/保険spawn) → 降下 → 着地 → UFO消える
  // ============================================================

  /** ✅ 追加：owner付き版（推奨） */
  public void startArrival(Player owner, Location landing) {
    UUID oid = (owner == null ? this.arrivalOwnerId : owner.getUniqueId());
    // owner無しでも lastOwnerId があれば使う
    if (oid == null) oid = this.lastOwnerId;

    // 互換の startArrival(Location) と同等動作を維持しつつ、タグ/PDCと完了フラグを確定させる
    startArrivalInternal(landing, oid);
  }

  public void startArrival(Location landing) {
    // 互換（owner無し）
    UUID oid = this.arrivalOwnerId;
    if (oid == null) oid = this.lastOwnerId;
    startArrivalInternal(landing, oid);
  }

  private void startArrivalInternal(Location landing, UUID ownerId) {
    if (landing == null || landing.getWorld() == null) {
      busy = false;
      runningArrival = false;
      return;
    }

    this.lastCenter = landing.clone();
    World w = landing.getWorld();

    final double ufoY = landing.getY() + ufoHeight;
    final double midY = landing.getY() + midHeight;

    final Location ufoCenter = new Location(w, landing.getX(), ufoY, landing.getZ());

    // (A) BlockDisplay UFO：ゼロスケール生成→フェードイン
    ensureUfoDisc(w, ufoCenter);
    setUfoScale(0.02f);

    // (B) ArmorStand版：不可視スタンドも出しておく（削除しない／追加演出の基準にも使える）
    //     ※高さは ArmorStand版定数(UFO_HEIGHT)を使う
    Location ufoLocArmor = landing.clone().add(0.5, UFO_HEIGHT, 0.5);
    spawnUfoStand(ufoLocArmor);

    // サウンド
    w.playSound(landing, Sound.BLOCK_BEACON_AMBIENT, 0.35f, 1.8f);
    w.playSound(landing, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 1.2f);

    new BukkitRunnable() {
      int t = 0;
      boolean spawnedOrBoundMoved = false;
      double y = midY;

      @Override
      public void run() {
        if (!plugin.isEnabled()) {
          cleanupAll();
          busy = false;
          runningArrival = false;
          cancel();
          return;
        }

        // UFO固定（真上）
        moveUfoDisc(ufoCenter);

        // フェードイン
        float s = (float) (0.02 + (ufoFullScale - 0.02) * (t / (double) ufoFadeInTicks));
        if (s > ufoFullScale) s = ufoFullScale;
        setUfoScale(s);

        // 粒子（豪華版）
        spawnUfoRing(w, ufoCenter, ufoRingRadius, ufoRingPoints);
        spawnBeamCone(w, ufoCenter, landing);
        spawnBeamRings(w, landing, ufoCenter.getY(), beamRingLevels);

        // 追加演出（ArmorStand版の粒子UFO/ビームも残す）
        drawUfoAndBeam(landing, ufoLocArmor);

        // フェードイン完了の瞬間に「中間ポップイン」→ 降下開始
        if (!spawnedOrBoundMoved && t >= ufoFadeInTicks) {
          spawnedOrBoundMoved = true;

          Location midLoc = landing.clone();
          midLoc.setY(midY);
          midLoc.setX(landing.getBlockX() + 0.5);
          midLoc.setZ(landing.getBlockZ() + 0.5);

          spawnTeleportInFX(w, midLoc);

          // ✅ bind済みなら：その商人＋ラマ群を “同時に中間へ置く”
          // ✅ ただし「2頭未満/多すぎ」もここで必ず矯正する
          if (trader != null && trader.isValid()) {
            // ✅ bind済み個体にもタグ/PDCを必ず付与
            markAsUfoEntity(ownerId, trader, ROLE_TRADER);

            prepForMove(trader);
            trader.teleport(midLoc);                  // 先に商人を中間へ

            // ★必ず2頭に揃える（補充/間引き） + 補充個体へタグ/PDC付与
            ensureTwoLlamas(w, midLoc, trader, ownerId);

            // 2頭になった後でラマもprep（補充した個体にも効く）
            for (TraderLlama l : boundLlamas) {
              if (l == null) continue;
              markAsUfoEntity(ownerId, l, ROLE_LLAMA);
              prepForMove(l);
            }

            // 全ラマを trader 周辺へ
            teleportLlamasAround(w, midLoc, y, boundLlamas);

            // leash復帰（例外握り）
            for (TraderLlama l : boundLlamas) {
              try { l.setLeashHolder(trader); } catch (Exception ignored) {}
            }

            // ✅ 最後にもう一度「確実にタグ/PDC」
            ensureMarkedGroup(ownerId);

          } else {
            // ✅ 無ければspawn保険（2頭デフォルト）＝「2頭同時出現」を必ず満たす
            trader = (WanderingTrader) w.spawnEntity(midLoc, EntityType.WANDERING_TRADER);
            boundTrader = trader;

            // タグ/PDC
            markAsUfoEntity(ownerId, trader, ROLE_TRADER);

            // 見栄え系（任意）
            try { trader.setPersistent(true); } catch (Exception ignored) {}
            trader.setGlowing(true);
            trader.setCustomName(ChatColor.GOLD + "" + ChatColor.BOLD + "Treasure Shop");
            trader.setCustomNameVisible(true);

            // レシピ（TreasureRunMultiChestPlugin のときだけ作れる）
            setupTreasureShopRecipes(trader);

            boundLlamas.clear();
            TraderLlama l1 = (TraderLlama) w.spawnEntity(midLoc.clone().add(1.2, 0, 0), EntityType.TRADER_LLAMA);
            TraderLlama l2 = (TraderLlama) w.spawnEntity(midLoc.clone().add(-1.2, 0, 0), EntityType.TRADER_LLAMA);

            // タグ/PDC
            markAsUfoEntity(ownerId, l1, ROLE_LLAMA);
            markAsUfoEntity(ownerId, l2, ROLE_LLAMA);

            boundLlamas.add(l1);
            boundLlamas.add(l2);

            for (TraderLlama l : boundLlamas) {
              if (l == null) continue;
              try { l.setPersistent(true); } catch (Exception ignored) {}
              l.setAdult();
              l.setGlowing(true);
            }

            prepForMove(trader);
            for (TraderLlama l : boundLlamas) prepForMove(l);

            for (TraderLlama l : boundLlamas) {
              try { l.setLeashHolder(trader); } catch (Exception ignored) {}
            }

            ensureMarkedGroup(ownerId);
          }
        }

        // 降下フェーズ
        if (spawnedOrBoundMoved) {

          // ✅ 途中で欠けても「常に2頭」を守る（不足なら補充）
          if (trader != null && trader.isValid()) {
            Location base = new Location(
                w,
                landing.getBlockX() + 0.5,
                y,
                landing.getBlockZ() + 0.5
            );
            ensureTwoLlamas(w, base, trader, ownerId);
            ensureMarkedGroup(ownerId);
          }

          if (!isGroupValid()) {
            cleanupAll();
            busy = false;
            runningArrival = false;
            cancel();
            return;
          }

          y -= descentPerTick;

          // trader
          trader.teleport(new Location(w, landing.getBlockX() + 0.5, y, landing.getBlockZ() + 0.5));
          // llamas（全頭）
          teleportLlamasAround(w, landing.clone().add(0.5, 0, 0.5), y, boundLlamas);

          // 着地
          if (y <= landing.getY() + 0.10) {
            double groundY = landing.getY() + 0.10;
            trader.teleport(new Location(w, landing.getBlockX() + 0.5, groundY, landing.getBlockZ() + 0.5));
            teleportLlamasAround(w, landing.clone().add(0.5, 0, 0.5), groundY, boundLlamas);

            restoreAfterMove(trader);
            for (TraderLlama l : boundLlamas) restoreAfterMove(l);

            // ✅ 到着（地上着地）時点でのタグ/PDC最終保証
            ensureMarkedGroup(ownerId);

            // Glow余韻解除（任意）
            new BukkitRunnable() {
              int sec = 0;
              @Override public void run() {
                sec++;
                if (sec >= 3) {
                  if (boundTrader != null && boundTrader.isValid()) boundTrader.setGlowing(false);
                  for (TraderLlama l : boundLlamas) {
                    if (l != null && l.isValid()) l.setGlowing(false);
                  }
                  cancel();
                }
              }

            }.runTaskTimer(plugin, 0L, 20L);

            w.playSound(landing, Sound.BLOCK_BEACON_POWER_SELECT, 0.65f, 1.95f);
            w.spawnParticle(Particle.END_ROD, landing.clone().add(0.5, 1.0, 0.5), 16, 0.35, 0.25, 0.35, 0.02);

            // UFO/ビーム終了（消える）
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
              cleanupUfoOnly();
              busy = false;
              runningArrival = false;

              // ✅ “真のArrival” 完了（UFO消失まで含めて完了）
              arrivalEffectDone = true;
              // このArrivalのownerを固定化（必要なら外側が参照可能）
              arrivalOwnerId = ownerId;
              if (ownerId != null) lastOwnerId = ownerId;

            }, UFO_VANISH_DELAY);

            cancel();
            return;
          }
        }

        t++;
        if (t > 400) {
          cleanupUfoOnly();
          busy = false;
          runningArrival = false;

          // タイムアウトでも “完了扱い” にしてゲーム側を止めない
          arrivalEffectDone = true;
          arrivalOwnerId = ownerId;
          if (ownerId != null) lastOwnerId = ownerId;

          cancel();
        }
      }

    }.runTaskTimer(plugin, 0L, 1L);
  }

  // ============================================================
  // ✅ Departure：豪華UFOフェードイン → 青緑ビーム → 上昇 → 中間(vanishY)で転送アウト消滅 → UFO消える
  // ============================================================

  /** ✅ 追加：ownerId を渡せる（PDC ufo_owner を確定させる） */
  public void startDeparture(UUID ownerId) {
    // ✅ 最低条件：商人だけは必要（ラマ不足は補充で解決する）
    if (trader == null || !trader.isValid()) {
      busy = false;
      runningDeparture = false;
      return;
    }

    Location ground = trader.getLocation().clone();
    World w = ground.getWorld();
    if (w == null) {
      busy = false;
      runningDeparture = false;
      return;
    }

    // ✅ ownerId を再代入しない（内部クラス/ラムダで参照するため）
    final UUID ownerFinal = (ownerId != null) ? ownerId : this.lastOwnerId;

    // ✅ 開始直後に「確実にタグ/PDC」
    markAsUfoEntity(ownerFinal, trader, ROLE_TRADER);
    for (TraderLlama l : boundLlamas) markAsUfoEntity(ownerFinal, l, ROLE_LLAMA);

    // ✅ ★ここで必ず2頭に揃える（開始直後・上昇前）
    ensureTwoLlamas(w, ground.clone().add(0.5, 0, 0.5), trader, ownerFinal);

    // ここで2頭揃っていなければ異常（保険）
    if (!isGroupValid()) {
      busy = false;
      runningDeparture = false;
      return;
    }

    this.lastCenter = ground.clone();

    final double ufoY = ground.getY() + ufoHeight;
    final double vanishY = ground.getY() + midHeight;

    final Location ufoCenter = new Location(w, ground.getX(), ufoY, ground.getZ());

    // (A) BlockDisplay UFO：ゼロスケール生成→フェードイン
    ensureUfoDisc(w, ufoCenter);
    setUfoScale(0.02f);

    // (B) ArmorStand版：不可視スタンドも出しておく（削除しない）
    Location ufoLocArmor = ground.clone();
    ufoLocArmor.setX(ground.getBlockX() + 0.5);
    ufoLocArmor.setZ(ground.getBlockZ() + 0.5);
    ufoLocArmor.add(0, UFO_HEIGHT, 0);
    spawnUfoStand(ufoLocArmor);

    w.playSound(ground, Sound.BLOCK_BEACON_AMBIENT, 0.35f, 1.8f);
    w.playSound(ground, Sound.ENTITY_ENDERMAN_TELEPORT, 0.25f, 1.4f);

    // 上昇中は停止/無敵/無重力（見栄え優先）
    prepForMove(trader);
    trader.setGlowing(true);
    for (TraderLlama l : boundLlamas) {
      prepForMove(l);
      if (l != null) l.setGlowing(true);
    }

    new BukkitRunnable() {
      int t = 0;
      double y = ground.getY();
      boolean fadeDone = false;

      @Override
      public void run() {
        if (!plugin.isEnabled()) {
          cleanupAll();
          busy = false;
          runningDeparture = false;
          cancel();
          return;
        }

        // UFO固定（真上）
        moveUfoDisc(ufoCenter);

        // フェードイン
        float s = (float) (0.02 + (ufoFullScale - 0.02) * (t / (double) ufoFadeInTicks));
        if (s > ufoFullScale) s = ufoFullScale;
        setUfoScale(s);

        // 粒子（豪華版）
        spawnUfoRing(w, ufoCenter, ufoRingRadius, ufoRingPoints);
        spawnBeamCone(w, ufoCenter, ground);
        spawnBeamRings(w, ground, ufoCenter.getY(), beamRingLevels);

        // 追加演出（ArmorStand版粒子UFO/ビームも残す）
        drawUfoAndBeam(ground, ufoLocArmor);

        if (t >= ufoFadeInTicks) fadeDone = true;

        if (fadeDone) {
          // ✅ 上昇中に欠けても「常に2頭」を守る（不足なら補充）
          if (trader != null && trader.isValid()) {
            Location base = new Location(w, ground.getBlockX() + 0.5, y, ground.getBlockZ() + 0.5);
            ensureTwoLlamas(w, base, trader, ownerFinal);
            for (TraderLlama l : boundLlamas) prepForMove(l); // 補充個体にも適用
            ensureMarkedGroup(ownerFinal);
          }

          if (!isGroupValid()) {
            cleanupAll();
            busy = false;
            runningDeparture = false;
            cancel();
            return;
          }

          y += risePerTick;

          Location centerXZ = ground.clone();
          centerXZ.setX(ground.getBlockX() + 0.5);
          centerXZ.setZ(ground.getBlockZ() + 0.5);

          trader.teleport(new Location(w, centerXZ.getX(), y, centerXZ.getZ()));
          teleportLlamasAround(w, centerXZ, y, boundLlamas);

          // 中間到達で“転送アウト”
          if (y >= vanishY) {
            Location vanish = new Location(w, centerXZ.getX(), vanishY, centerXZ.getZ());
            spawnTeleportOutFX(w, vanish);

            // remove all（商人＋ラマ）
            for (TraderLlama l : new ArrayList<>(boundLlamas)) {
              if (l != null && l.isValid()) {
                try { l.setLeashHolder(null); } catch (Exception ignored) {}
                l.remove();
              }
            }
            boundLlamas.clear();

            if (trader != null && trader.isValid()) trader.remove();
            trader = null;
            boundTrader = null;

            // UFOも消える
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
              cleanupUfoOnly();
              busy = false;
              runningDeparture = false;

              // ✅ “真のDeparture” 完了（UFO消失まで含めて完了）
              departureEffectDone = true;
              if (ownerFinal != null) lastOwnerId = ownerFinal;

            }, UFO_VANISH_DELAY);

            cancel();
            return;
          }
        }

        t++;
        if (t > 400) {
          cleanupUfoOnly();
          busy = false;
          runningDeparture = false;

          // タイムアウトでも “完了扱い”
          departureEffectDone = true;
          if (ownerFinal != null) lastOwnerId = ownerFinal;

          cancel();
        }
      }

    }.runTaskTimer(plugin, 0L, 1L);
  }

  // 互換（既存public APIは残す）
  public void startDeparture() {
    startDeparture(this.lastOwnerId);
  }

  // ============================================================
  // ✅ ラマを trader 周辺に配置（高度yへ）
  // ============================================================

  private void teleportLlamasAround(World w, Location centerXZ, double y, List<TraderLlama> ls) {
    if (w == null || centerXZ == null || ls == null || ls.isEmpty()) return;

    double radius = 1.2;
    for (int i = 0; i < ls.size(); i++) {
      TraderLlama l = ls.get(i);
      if (l == null || !l.isValid()) continue;

      double ang = (2 * Math.PI) * i / Math.max(1, ls.size());
      double ox = Math.cos(ang) * radius;
      double oz = Math.sin(ang) * radius;

      l.teleport(new Location(w, centerXZ.getX() + ox, y, centerXZ.getZ() + oz));
    }
  }

  // ============================================================
  // FX：転送 IN/OUT（未来テクノロジー感）
  // ============================================================

  private void spawnTeleportInFX(World w, Location loc) {
    w.spawnParticle(Particle.FLASH, loc.clone().add(0, 0.8, 0), 1, 0, 0, 0, 0);
    w.spawnParticle(Particle.GLOW, loc.clone().add(0, 1.0, 0), 14, 0.25, 0.35, 0.25, 0.01);
    w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1.0, 0), 18, 0.35, 0.35, 0.35, 0.02);
    w.playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 0.75f, 2.0f);
    w.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.35f, 1.7f);
  }

  private void spawnTeleportOutFX(World w, Location loc) {
    w.spawnParticle(Particle.FLASH, loc.clone().add(0, 0.8, 0), 1, 0, 0, 0, 0);
    w.spawnParticle(Particle.GLOW, loc.clone().add(0, 1.0, 0), 12, 0.25, 0.35, 0.25, 0.01);
    w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1.0, 0), 16, 0.35, 0.35, 0.35, 0.02);
    w.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.75f, 1.55f);
    w.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.35f, 1.2f);
  }

  // ============================================================
  // 粒子：UFOリング（蛍光青緑）
  // ============================================================

  private void spawnUfoRing(World w, Location center, double radius, int points) {
    for (int i = 0; i < points; i++) {
      double ang = (2 * Math.PI) * i / points;
      double x = center.getX() + Math.cos(ang) * radius;
      double z = center.getZ() + Math.sin(ang) * radius;
      Location p = new Location(w, x, center.getY(), z);

      w.spawnParticle(Particle.REDSTONE, p, 1, 0, 0, 0, 0, neonTeal);
      w.spawnParticle(Particle.ELECTRIC_SPARK, p, 1, 0.02, 0.02, 0.02, 0.0);
    }
    w.spawnParticle(Particle.END_ROD, center, 2, 0.2, 0.05, 0.2, 0.0);
  }

  // ============================================================
  // 粒子：緑青 “コーン” ビーム（UFO直下→地上）
  // ============================================================

  private void spawnBeamCone(World w, Location ufoCenter, Location target) {
    double yTop = ufoCenter.getY() - 0.6;
    double yBottom = target.getY() + 0.2;

    double step = 0.6;
    for (double y = yBottom; y <= yTop; y += step) {
      double t = (y - yBottom) / (yTop - yBottom); // 0..1
      double radius = 0.25 + (1.9 * (1.0 - t));    // 下ほど太い

      Location c = new Location(w, target.getX(), y, target.getZ());

      w.spawnParticle(Particle.REDSTONE, c, 18, radius, 0.06, radius, 0.0, beamTeal);
      w.spawnParticle(Particle.GLOW, c, 2, radius * 0.35, 0.02, radius * 0.35, 0.0);
    }
  }

  // ============================================================
  // 粒子：白リング（ビーム内の段々）
  // ============================================================

  private void spawnBeamRings(World w, Location target, double yTop, int levels) {
    double yBottom = target.getY() + 0.6;
    double top = yTop - 1.0;

    for (int i = 1; i <= levels; i++) {
      double t = (double) i / (levels + 1);
      double y = yBottom + (top - yBottom) * t;

      double radius = 0.7 + (1.3 * (1.0 - t));
      int points = 28;

      for (int p = 0; p < points; p++) {
        double ang = (2 * Math.PI) * p / points;
        double x = target.getX() + Math.cos(ang) * radius;
        double z = target.getZ() + Math.sin(ang) * radius;
        w.spawnParticle(Particle.REDSTONE, new Location(w, x, y, z), 1, 0, 0, 0, 0, ringWhite);
      }
    }
  }

  // ============================================================
  // UFO本体：BlockDisplay円盤＋ドーム＋ライト（スケール調整可能）
  // ============================================================

  private static class UfoPart {
    final BlockDisplay display;
    final Vector3f offset;
    final BlockData blockData;
    final Vector3f baseScale;

    UfoPart(BlockDisplay display, Vector3f offset, BlockData blockData, Vector3f baseScale) {
      this.display = display;
      this.offset = offset;
      this.blockData = blockData;
      this.baseScale = baseScale;
    }
  }

  private void ensureUfoDisc(World w, Location center) {
    if (!ufoParts.isEmpty() && ufoParts.stream().anyMatch(p -> p.display != null && p.display.isValid())) {
      moveUfoDisc(center);
      return;
    }

    ufoParts.clear();

    // 素材（好みで変えてOK）
    BlockData disc = Bukkit.createBlockData(Material.RED_CONCRETE);
    BlockData dome = Bukkit.createBlockData(Material.CYAN_STAINED_GLASS);
    BlockData light = Bukkit.createBlockData(Material.SEA_LANTERN);

    // 円盤ベース（薄く広く）
    ufoParts.add(createPart(w, center, disc,
        new Vector3f(0f, 0f, 0f),
        new Vector3f(3.8f, 0.12f, 3.8f)));

    // ドーム
    ufoParts.add(createPart(w, center, dome,
        new Vector3f(0f, 0.35f, 0f),
        new Vector3f(1.3f, 0.7f, 1.3f)));

    // 外周ライト
    int n = 10;
    float r = 2.9f;
    for (int i = 0; i < n; i++) {
      double ang = (2 * Math.PI) * i / n;
      float ox = (float) (Math.cos(ang) * r);
      float oz = (float) (Math.sin(ang) * r);

      ufoParts.add(createPart(w, center, light,
          new Vector3f(ox, 0.10f, oz),
          new Vector3f(0.35f, 0.18f, 0.35f)));
    }

    moveUfoDisc(center);
  }

  private UfoPart createPart(World w, Location center, BlockData data, Vector3f offset, Vector3f baseScale) {
    BlockDisplay bd = (BlockDisplay) w.spawnEntity(center, EntityType.BLOCK_DISPLAY);
    bd.setBlock(data);

    bd.setBrightness(new Display.Brightness(15, 15));
    bd.setViewRange(64f);

    bd.setInterpolationDuration(1);
    bd.setInterpolationDelay(0);

    bd.setTransformation(new Transformation(
        new Vector3f(0f, 0f, 0f),
        new Quaternionf(),
        baseScale,
        new Quaternionf()
    ));

    return new UfoPart(bd, offset, data, baseScale);
  }

  private void setUfoScale(float factor) {
    if (factor < 0.01f) factor = 0.01f;

    for (UfoPart p : ufoParts) {
      if (p.display == null || !p.display.isValid()) continue;

      Vector3f s = new Vector3f(
          p.baseScale.x * factor,
          p.baseScale.y * factor,
          p.baseScale.z * factor
      );

      p.display.setTransformation(new Transformation(
          new Vector3f(0f, 0f, 0f),
          new Quaternionf(),
          s,
          new Quaternionf()
      ));
    }
  }

  private void moveUfoDisc(Location center) {
    if (ufoParts.isEmpty()) return;
    for (UfoPart p : ufoParts) {
      if (p.display == null || !p.display.isValid()) continue;
      p.display.teleport(center.clone().add(p.offset.x, p.offset.y, p.offset.z));
    }
  }

  private void removeUfoDisc() {
    for (UfoPart p : ufoParts) {
      if (p.display != null && p.display.isValid()) p.display.remove();
    }
    ufoParts.clear();
  }

  // ============================================================
  // エンティティ制御（BlockDisplay版）
  // ============================================================

  private void prepForMove(LivingEntity e) {
    if (e == null) return;
    try { e.setAI(false); } catch (Exception ignored) {}
    try { e.setGravity(false); } catch (Exception ignored) {}
    try { e.setInvulnerable(true); } catch (Exception ignored) {}
    try { e.setSilent(true); } catch (Exception ignored) {}
    try { e.setCollidable(false); } catch (Exception ignored) {}
  }

  private void restoreAfterMove(LivingEntity e) {
    if (e == null) return;
    try { e.setGravity(true); } catch (Exception ignored) {}
    try { e.setAI(true); } catch (Exception ignored) {}
    try { e.setInvulnerable(false); } catch (Exception ignored) {}
    try { e.setSilent(false); } catch (Exception ignored) {}
    try { e.setCollidable(true); } catch (Exception ignored) {}
  }

  // ============================================================
  // ✅ 2頭強制：不足ならスポーンで補充（bind済みでも必ず2頭に揃える）
  // ============================================================

  // 互換（既存呼び出しを壊さない）
  private void ensureTwoLlamas(World w, Location base, WanderingTrader t) {
    ensureTwoLlamas(w, base, t, this.lastOwnerId);
  }

  // ✅ 追加：ownerId を渡せる版（補充個体にもタグ/PDCを確定）
  private void ensureTwoLlamas(World w, Location base, WanderingTrader t, UUID ownerId) {
    if (w == null || base == null || t == null || !t.isValid()) return;

    // 無効個体を除去
    boundLlamas.removeIf(l -> l == null || !l.isValid());

    // 今回は「必ず2頭」なので 2頭に間引く（余分はremove）
    while (boundLlamas.size() > 2) {
      TraderLlama extra = boundLlamas.remove(boundLlamas.size() - 1);
      if (extra != null && extra.isValid()) {
        try { extra.setLeashHolder(null); } catch (Exception ignored) {}
        extra.remove();
      }
    }

    // 不足分を補充（0 or 1）
    while (boundLlamas.size() < 2) {
      double offsetX = (boundLlamas.size() == 0) ? 1.2 : -1.2;
      Location spawnLoc = base.clone().add(offsetX, 0, 0);

      TraderLlama nl = (TraderLlama) w.spawnEntity(spawnLoc, EntityType.TRADER_LLAMA);
      try { nl.setPersistent(true); } catch (Exception ignored) {}
      nl.setAdult();
      nl.setGlowing(true);

      // ✅ タグ/PDC
      markAsUfoEntity(ownerId, nl, ROLE_LLAMA);

      prepForMove(nl);

      try { nl.setLeashHolder(t); } catch (Exception ignored) {}

      boundLlamas.add(nl);
    }

    // trader もタグ/PDCを確実に
    markAsUfoEntity(ownerId, t, ROLE_TRADER);

    // 最後に必ず leash を揃える（例外握り）
    for (TraderLlama l : boundLlamas) {
      if (l == null || !l.isValid()) continue;
      // ✅ 念のためタグ/PDC
      markAsUfoEntity(ownerId, l, ROLE_LLAMA);
      try { l.setLeashHolder(t); } catch (Exception ignored) {}
    }

    // ✅ PATCH: 「開始直後に必ず商人の横へ寄せる」(遠い時だけスナップ)
    // ensureTwoLlamas は開始直後/補充直後/進行中に何度も呼ばれるので、
    // 毎回テレポするとガタつく → “離れている時だけ” 1回寄せる。
    {
      double snapDistSq = 4.0; // 2.0 blocks^2 くらい。必要なら調整OK
      boolean far = false;

      for (TraderLlama l : boundLlamas) {
        if (l == null || !l.isValid()) continue;
        if (!l.getWorld().equals(base.getWorld())) { far = true; break; }
        if (l.getLocation().distanceSquared(base) > snapDistSq) { far = true; break; }
      }

      if (far) {
        teleportLlamasAround(w, base, base.getY(), boundLlamas);
      }
    }
  }

  // ============================================================
  // ✅ A'：2頭必須チェック（必ず2頭 + 全員valid）
  // ============================================================

  private boolean isGroupValid() {
    if (trader == null || !trader.isValid()) return false;
    if (boundLlamas.size() != 2) return false; // ★2頭必須
    for (TraderLlama l : boundLlamas) {
      if (l == null || !l.isValid()) return false;
    }
    return true;
  }

  private void cleanupAll() {
    for (TraderLlama l : new ArrayList<>(boundLlamas)) {
      if (l != null && l.isValid()) l.remove();
    }
    boundLlamas.clear();

    if (trader != null && trader.isValid()) trader.remove();
    trader = null;
    boundTrader = null;

    cleanupUfoOnly();
    busy = false;
    runningArrival = false;
    runningDeparture = false;

    // “停止した”なら完了扱いにしない（外側が誤判定しないように）
    // ※ただしゲーム側を止めない設計ならここを true にしてもOK
  }

  // ============================================================
  // ============================================================
  // ここから下は「ArmorStand版」部品（削除しない）
  // ============================================================
  // ============================================================

  private void spawnUfoStand(Location ufoLoc) {
    cleanupArmorStandOnly();
    if (ufoLoc == null || ufoLoc.getWorld() == null) return;

    World w = ufoLoc.getWorld();
    ArmorStand as = (ArmorStand) w.spawnEntity(ufoLoc, EntityType.ARMOR_STAND);
    as.setInvisible(true);
    as.setMarker(true);
    as.setGravity(false);
    as.setInvulnerable(true);
    as.setSilent(true);
    as.setCustomNameVisible(false);
    this.ufoStand = as;
  }

  private void cleanupArmorStandOnly() {
    if (ufoStand != null) {
      try { ufoStand.remove(); } catch (Exception ignored) {}
      ufoStand = null;
    }
  }

  /** BlockDisplay UFO と ArmorStand をまとめて消す（削除しない） */
  private void cleanupUfoOnly() {
    removeUfoDisc();
    cleanupArmorStandOnly();
  }

  private void drawUfoAndBeam(Location groundCenter, Location ufoLoc) {
    if (groundCenter == null || ufoLoc == null || groundCenter.getWorld() == null) return;
    World w = groundCenter.getWorld();

    // UFOディスク粒子（ArmorStand版）
    spawnDisc(w, ufoLoc);

    // 縦ビーム粒子（ArmorStand版）
    spawnBeam(w, groundCenter, ufoLoc);
  }

  private void spawnDisc(World w, Location ufoLoc) {
    int points = 18;
    for (int i = 0; i < points; i++) {
      double rad = Math.toRadians((360.0 / points) * i);
      double x = ufoLoc.getX() + Math.cos(rad) * DISC_RADIUS;
      double z = ufoLoc.getZ() + Math.sin(rad) * DISC_RADIUS;
      Location p = new Location(w, x, ufoLoc.getY(), z);
      w.spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0);
    }
    w.spawnParticle(Particle.END_ROD, ufoLoc, 6, 0.15, 0.05, 0.15, 0.01);
  }

  private void spawnBeam(World w, Location groundCenter, Location ufoLoc) {
    double y0 = groundCenter.getY();
    double y1 = ufoLoc.getY();

    Particle.DustOptions optA = new Particle.DustOptions(BEAM_A, 1.2f);
    Particle.DustOptions optB = new Particle.DustOptions(BEAM_B, 1.2f);

    for (double y = y0; y <= y1; y += 0.4) {
      Location p = new Location(w, groundCenter.getX() + 0.5, y, groundCenter.getZ() + 0.5);
      w.spawnParticle(Particle.REDSTONE, p, 4, BEAM_RADIUS, 0.02, BEAM_RADIUS, 0.0, optA);
      w.spawnParticle(Particle.REDSTONE, p, 2, BEAM_RADIUS * 0.6, 0.02, BEAM_RADIUS * 0.6, 0.0, optB);
    }

    w.spawnParticle(Particle.ENCHANTMENT_TABLE,
        groundCenter.clone().add(0.5, 1.2, 0.5),
        20,
        0.6, 0.9, 0.6,
        0.0
    );
  }

  // =======================================================
  // Controller内でTreasure Shopレシピも成立させる（削除しない）
  // =======================================================

  private void setupTreasureShopRecipes(WanderingTrader trader) {
    if (trader == null) return;

    // TreasureRunMultiChestPlugin でないなら安全にスキップ（落とさない）
    if (treasurePluginOrNull == null || treasurePluginOrNull.getItemFactory() == null) return;

    List<MerchantRecipe> recipes = new ArrayList<>();

    // 取引①：特製エメラルド 5 → 金リンゴ 1
    ItemStack specialEmerald5 = treasurePluginOrNull.getItemFactory().createTreasureEmerald(5);

    ItemStack result1 = new ItemStack(Material.GOLDEN_APPLE, 1);
    MerchantRecipe r1 = new MerchantRecipe(result1, 64);
    r1.addIngredient(specialEmerald5);
    recipes.add(r1);

    // エメラルドブロック 1 → エンチャ金リンゴ 1
    ItemStack result2 = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1);
    MerchantRecipe r2 = new MerchantRecipe(result2, 16);
    r2.addIngredient(new ItemStack(Material.EMERALD_BLOCK, 1));
    recipes.add(r2);

    // 鉄インゴット 16 → エメラルド 1
    ItemStack result3 = new ItemStack(Material.EMERALD, 1);
    MerchantRecipe r3 = new MerchantRecipe(result3, 64);
    r3.addIngredient(new ItemStack(Material.IRON_INGOT, 16));
    recipes.add(r3);

    trader.setRecipes(recipes);
  }
}