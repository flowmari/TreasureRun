package plugin;

import org.bukkit.Bukkit;

import org.bukkit.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.entity.Entity;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Merchant;
import org.bukkit.entity.WanderingTrader;   // ★ 行商人
import org.bukkit.entity.TraderLlama;      // ★ トレーダーラマ
import org.bukkit.event.EventHandler;      // ★ 追加
import org.bukkit.event.Listener;          // ★ 追加
import org.bukkit.event.inventory.InventoryClickEvent; // ★ 追加
import org.bukkit.event.inventory.InventoryType;       // ★ 追加
import org.bukkit.inventory.ItemStack;                 // ★ 追加
import org.bukkit.inventory.MerchantInventory;         // ★ 追加
import org.bukkit.inventory.MerchantRecipe;// ★ 追加
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.lang.reflect.*;

public class GameStageManager implements Listener {

  // ✅ 多重起動防止：MovingSafetyZoneTask の実行ハンドル
  private org.bukkit.scheduler.BukkitTask movingSafetyZoneHandle;

  // ✅ Runnable本体（cancel()で床復元するため保持）
  private MovingSafetyZoneTask movingSafetyZoneRunnable;

  private final TreasureRunMultiChestPlugin plugin;

  private final ArenaWorldManager arenaWorldManager;

  private static final int ARENA_WATER_RADIUS = 64;

  // ✅ UFO（型が違っても壊れないように Object で保持）
  private final Object ufo;


  // ★ 難易度ブロックだけを覚えておくリスト
  private final java.util.List<Block> difficultyBlocks = new java.util.ArrayList<>();

  // ✅ 追加：難易度ブロックを「座標キー」でも保持（Block参照が壊れても掃除できる）
  private final java.util.Set<String> difficultyKeys = new java.util.HashSet<>();

  // ✅ 追加：最近作ったステージ中心（複数回ゲームしても掃除できる）
  private final java.util.List<Location> recentStageCenters = new java.util.ArrayList<>();

  // ✅ 追加：安全スイープ設定（“難易度素材だけ”を回収する）
  private static final int DIFF_SWEEP_RADIUS = 96;   // 必要なら 64/96/128 で調整OK
  private static final int DIFF_SWEEP_Y_RANGE = 8;   // 高さブレ対策（±）

  // ★ ステージ中央の行商人＆ラマを覚えておく（Glow 制御＆中央テレポート用）
  private WanderingTrader stageTrader;
  private final java.util.List<TraderLlama> stageLlamas = new java.util.ArrayList<>();

  // Treasure Shop secret-trade scanner:
  // Keep this gameplay fix isolated from language files, ResourcePack assets, and i18n-core.
  private BukkitTask treasureShopSecretTradeScannerHandle;
  private final java.util.Map<java.util.UUID, Long> treasureShopAutoTradeCooldownUntilMs = new java.util.HashMap<>();


  // =======================================================
  // ★ ShopDebug 出力（②：クラス内に1個追加 / メンバーとして）
  // =======================================================
  private void shopDebug(String msg) {
    plugin.getLogger().info("[ShopDebug] " + msg);
  }

  public GameStageManager(TreasureRunMultiChestPlugin plugin) {
    this(plugin, null, new ArenaWorldManager(plugin));
    // ❌ 起動時にMSZを常時起動しない
    // MSZは buildSeasideStageAndTeleport() のステージ生成後に startMovingSafetyZoneTask() する
  }

  // ✅ 海上ステージ用：周囲を強制的に「水面＋上空クリア」にする
  // これで海岸・砂浜・ジャングル横に寄っても、ステージ周囲だけは必ず海になる
  private void prepareOwnedArenaWater(Location origin, int radius) {
    if (origin == null || origin.getWorld() == null) return;
    World w = origin.getWorld();
    arenaWorldManager.requireOwnedWorld(w);

    int cx = origin.getBlockX();
    int cz = origin.getBlockZ();

    // The arena uses a fixed water level. It must not inherit terrain height from another world.
    int waterY = origin.getBlockY();

    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        int x = cx + dx;
        int z = cz + dz;

        // 水面そのもの
        w.getBlockAt(x, waterY, z).setType(Material.WATER, false);

        // 水面より上を空気にして、砂丘・木・葉・土・草を消す
        for (int y = waterY + 1; y <= waterY + 10; y++) {
          Block b = w.getBlockAt(x, y, z);
          if (!b.getType().isAir()) {
            b.setType(Material.AIR, false);
          }
        }

        // 水面直下が空洞だと変なので、1段下も水にする
        w.getBlockAt(x, waterY - 1, z).setType(Material.WATER, false);
      }
    }

    // base は「水ブロックのY」を持つ
    origin.setY(waterY);
  }

  // ✅ UFO を渡せる版（TreasureRunMultiChestPlugin 側で new GameStageManager(this, ufo) にできる）
  public GameStageManager(TreasureRunMultiChestPlugin plugin, Object ufo) {
    this(plugin, ufo, new ArenaWorldManager(plugin));
  }

  GameStageManager(
      TreasureRunMultiChestPlugin plugin,
      Object ufo,
      ArenaWorldManager arenaWorldManager
  ) {
    this.plugin = plugin;
    this.ufo = ufo;
    this.arenaWorldManager = arenaWorldManager;
    startTreasureShopOpenViewSecretTradeScanner();
  }

  // ✅ 追加：difficultyKeys 用のキー生成
  private String toBlockKey(Block b) {
    if (b == null || b.getWorld() == null) return null;
    return b.getWorld().getName() + ":" + b.getX() + "," + b.getY() + "," + b.getZ();
  }

  // ✅ 追加：キー → Block
  private Block fromBlockKey(String key) {
    if (key == null || key.isEmpty()) return null;
    try {
      String[] parts = key.split(":");
      if (parts.length != 2) return null;

      World w = Bukkit.getWorld(parts[0]);
      if (w == null) return null;

      String[] xyz = parts[1].split(",");
      if (xyz.length != 3) return null;

      int x = Integer.parseInt(xyz[0]);
      int y = Integer.parseInt(xyz[1]);
      int z = Integer.parseInt(xyz[2]);

      return w.getBlockAt(x, y, z);
    } catch (Exception e) {
      return null;
    }
  }

  // ✅ 追加：難易度素材かチェック（ここだけを掃除対象にする）
  private boolean isDifficultyMaterial(Material m) {
    return m == Material.PURPLE_CONCRETE ||
        m == Material.LIME_CONCRETE ||
        m == Material.BLUE_CONCRETE;
  }

  // ✅ 追加：ステージ中心を履歴に残す（同じ座標は重複登録しない）
  private void rememberStageCenter(Location center) {
    if (center == null || center.getWorld() == null) return;

    Location c = center.clone();
    c.setX(c.getBlockX());
    c.setY(c.getBlockY());
    c.setZ(c.getBlockZ());

    for (Location old : recentStageCenters) {
      if (old == null || old.getWorld() == null) continue;
      if (old.getWorld().getName().equals(c.getWorld().getName())
          && old.getBlockX() == c.getBlockX()
          && old.getBlockY() == c.getBlockY()
          && old.getBlockZ() == c.getBlockZ()) {
        return;
      }
    }

    recentStageCenters.add(c);

    // 増えすぎ防止（最近10件だけ保持）
    while (recentStageCenters.size() > 10) {
      recentStageCenters.remove(0);
    }
  }

  /** 海辺ステージを作ってプレイヤーをテレポートする（ネオン床＋一発ドーン演出） */
  public Location buildSeasideStageAndTeleport(Player player) {

    plugin.getLogger().info("[STAGE][DEBUG] buildSeasideStageAndTeleport entered"
        + " player=" + (player != null ? player.getName() : "null")
        + " gsm=" + System.identityHashCode(this)
    );

    // Server-safety boundary: stage construction is confined to the plugin-owned arena world.
    // The initiating player's current world is never used as a mutable gameplay canvas.
    Location base = arenaWorldManager.getArenaBase();
    World w = base.getWorld();
    arenaWorldManager.requireOwnedWorld(w);

    plugin.getLogger().info("[Arena] Preparing isolated stage in " + w.getName()
        + " at x=" + base.getBlockX() + " y=" + base.getBlockY()
        + " z=" + base.getBlockZ());

    prepareOwnedArenaWater(base, ARENA_WATER_RADIUS);

    Location stageCenter = base.clone();

    // base は水ブロックのY。ステージ床はその1つ上。
    stageCenter.setY(base.getBlockY() + 1);

    // ✅ 追加：このステージ中心を記憶（後で難易度ブロックをスイープ掃除できる）
    rememberStageCenter(stageCenter);

    // ✅ 追加：過去残骸の黄色床を先に掃除（今回のバグの本丸）
    sweepAllLemonGlass();

    // ✨ ネオン床
    buildNeonFloor(stageCenter);
    // ===============================
    // [SeasideCheck] 海上100%判定ログ
    // ===============================
    try {
      World ww = stageCenter.getWorld();
      int y = stageCenter.getBlockY();
      int cx = stageCenter.getBlockX();
      int cz = stageCenter.getBlockZ();

      int waterCount = 0;
      int total = 0;
      StringBuilder bad = new StringBuilder();

      for (int dx = -2; dx <= 2; dx++) {
        for (int dz = -2; dz <= 2; dz++) {
          total++;
          Material under = ww.getBlockAt(cx + dx, y - 1, cz + dz).getType();
          if (under == Material.WATER) {
            waterCount++;
          } else {
            if (bad.length() < 280) {
              bad.append(" (").append(dx).append(",").append(dz).append(")=").append(under);
            }
          }
        }
      }

      plugin.getLogger().info(
          "[SeasideCheck] center=" + ww.getName() + " "
              + cx + "," + y + "," + cz
              + " | underWater=" + waterCount + "/" + total
              + (bad.length() > 0 ? " | notWater:" + bad : "")
      );

      Material feet = ww.getBlockAt(cx, y, cz).getType();
      Material below = ww.getBlockAt(cx, y - 1, cz).getType();
      plugin.getLogger().info("[SeasideCheck] feetBlock(y)=" + feet + " | below(y-1)=" + below);

    } catch (Exception e) {
      plugin.getLogger().warning("[SeasideCheck] ERROR " + e.getMessage());
    }
    // 頭上の空間確保
    clearAbove(stageCenter, 3);
    // 難易度ブロック（Easy/Normal/Hard）
    buildDifficultyBlocks(stageCenter);
    // 環境音 & パーティクルふわふわ
    playAmbient(stageCenter, player);

    // 🔥 一発ドーンの演出（円形＆柱＆星の爆発）
    spawnCircleParticles(stageCenter, Particle.END_ROD, 2.5, 40); // 外輪
    spawnCircleParticles(stageCenter, Particle.END_ROD, 1.5, 40); // 内輪
    spawnRisingPillars(stageCenter, Particle.END_ROD);            // 柱
    plugin.burstStars(stageCenter);                               // 星の爆発（メインクラスのメソッド）

    // プレイヤーをステージ中央へテレポート
    Location tp = stageCenter.clone().add(0.5, 1.1, 0.5);
    player.teleport(tp);

    // ★ ネオン床ステージの上に行商人＋ラマ2頭をスポーン
    spawnTraderAndLlamas(stageCenter);

    startMovingSafetyZoneTask();

    // ✅ UFO Arrival演出（商人はbind済みなのでUFOが持ち上げて降ろす）
    startUfoIfAvailable(player, stageCenter);

    return stageCenter.clone();
  }

  /** ゲーム開始後、ゲーム中ずっとキラキラ演出を出し続ける（旧演出＋新演出を両方入れたバージョン） */
  public void startLoopEffects(Location center) {
    new BukkitRunnable() {
      double angle = 0; // 外周を回るリング用

      @Override
      public void run() {
        // ゲームが終わったら自動停止
        if (!plugin.isGameRunning()) {
          cancel();
          return;
        }

        World w = center.getWorld();
        if (w == null) {
          cancel();
          return;
        }

        // ① もともとの「中心キラキラ」演出（従来そのまま残す）
        w.spawnParticle(
            Particle.END_ROD,
            center.clone().add(0.5, 1.2, 0.5),
            12,
            0.6, 0.4, 0.6,
            0.01
        );

        // ② ネオン床の上でキラキラ（シアン＆マゼンタ床の交互マスを中心に）
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
          for (int dz = -2; dz <= 2; dz++) {
            if ((dx + dz) % 2 == 0) {
              Location p = new Location(w, cx + dx + 0.5, cy + 0.2, cz + dz + 0.5);
              w.spawnParticle(
                  Particle.ENCHANTMENT_TABLE,
                  p,
                  2,
                  0.15, 0.1, 0.15,
                  0.0
              );
            }
          }
        }

        // ③ 外周をくるくる回る END_ROD のリング
        double r = 3.0;
        double rad = Math.toRadians(angle);
        double x = center.getX() + Math.cos(rad) * r;
        double z = center.getZ() + Math.sin(rad) * r;
        Location ring = new Location(w, x, center.getY() + 0.4, z);
        w.spawnParticle(
            Particle.END_ROD,
            ring,
            4,
            0.1, 0.1, 0.1,
            0.01
        );

        angle += 12;
        if (angle >= 360) {
          angle -= 360;
        }
      }

    }.runTaskTimer(plugin, 0L, 4L);
  }

  // =======================================================
  // ネオン床づくり（光る床＋色ガラス）※元のまま
  // =======================================================
  private void buildNeonFloor(Location center) {
    World w = center.getWorld();
    int cx = center.getBlockX();
    int cz = center.getBlockZ();
    int y = center.getBlockY();

    // ✅ Stage visual size:
    // radius=4 means 9x9 blocks.
    // This is the compact playable neon glass floor size.
    final int floorRadius = 4;

    for (int dx = -floorRadius; dx <= floorRadius; dx++) {
      for (int dz = -floorRadius; dz <= floorRadius; dz++) {
        Block top = w.getBlockAt(cx + dx, y, cz + dz);
        Block under = w.getBlockAt(cx + dx, y - 1, cz + dz);

        // ✨ 中央十字＋外周ラインをシーランタンにして、広い床でも輪郭が見えるようにする
        boolean centerCross = (dx == 0 || dz == 0);
        boolean border = (Math.abs(dx) == floorRadius || Math.abs(dz) == floorRadius);

        if (centerCross || border) {
          under.setType(Material.SEA_LANTERN);
        } else {
          under.setType(Material.PRISMARINE);
        }

        // ✨ ガラスはネオンっぽく 2色に切り替え
        if ((dx + dz) % 2 == 0) {
          top.setType(Material.CYAN_STAINED_GLASS);
        } else {
          top.setType(Material.MAGENTA_STAINED_GLASS);
        }
      }
    }
  }

  /** 上方向の空間を確保して窒息しないようにする */
  private void clearAbove(Location center, int height) {
    World w = center.getWorld();
    int cx = center.getBlockX();
    int cz = center.getBlockZ();
    int y = center.getBlockY();

    // ✅ ネオン床が9x9になったので、上空クリア範囲も同じ半径に合わせる
    final int floorRadius = 4;

    for (int dx = -floorRadius; dx <= floorRadius; dx++) {
      for (int dz = -floorRadius; dz <= floorRadius; dz++) {
        for (int dy = 1; dy <= height; dy++) {
          Block b = w.getBlockAt(cx + dx, y + dy, cz + dz);
          if (!b.getType().isAir()) b.setType(Material.AIR);
        }
      }
    }
  }

  /** 難易度ブロック（ステージの外周に3つ置く）※色だけ紫・緑・青に変更＋登録処理 */
  private void buildDifficultyBlocks(Location center) {
    World w = center.getWorld();
    int y = center.getBlockY();
    int cx = center.getBlockX();
    int cz = center.getBlockZ();

    // ステージの一辺に 3 つ並べる（左＝Easy, 真ん中＝Normal, 右＝Hard）
    // ✅ 床を半径4に調整したので、難易度ブロックは外周すぐ外に置く
    Block easyBlock   = w.getBlockAt(cx - 1, y, cz + 5);
    Block normalBlock = w.getBlockAt(cx,     y, cz + 5);
    Block hardBlock   = w.getBlockAt(cx + 1, y, cz + 5);

    // ★ 難易度カラー
    // Easy  : 紫
    // Normal: 緑（明るめの黄緑）
    // Hard  : 青
    easyBlock.setType(Material.PURPLE_CONCRETE);
    normalBlock.setType(Material.LIME_CONCRETE);
    hardBlock.setType(Material.BLUE_CONCRETE);

    // ★ 難易度ブロックとして登録（ゲーム終了時にここだけ消す）
    registerDifficultyBlock(easyBlock);
    registerDifficultyBlock(normalBlock);
    registerDifficultyBlock(hardBlock);
  }

  // ★ 難易度ブロックを登録する（あとで消すため）
  private void registerDifficultyBlock(Block block) {
    if (block == null) return;
    // 念のため、難易度用の色だけリストに入れる
    Material type = block.getType();
    if (type == Material.PURPLE_CONCRETE ||
        type == Material.LIME_CONCRETE ||
        type == Material.BLUE_CONCRETE) {
      difficultyBlocks.add(block);

      // ✅ 追加：座標キーでも必ず登録（これが “誰がやっても増えない” の決定打）
      String key = toBlockKey(block);
      if (key != null) difficultyKeys.add(key);
    }
  }

  // ✅ 追加：履歴中心の周辺をスキャンして「難易度素材だけ」回収する（登録漏れ・クラッシュ残骸対策）
  private int sweepDifficultyBlocksAround(Location center, int radius, int yRange) {
    if (center == null || center.getWorld() == null) return 0;

    World w = center.getWorld();
    int cx = center.getBlockX();
    int cy = center.getBlockY();
    int cz = center.getBlockZ();

    int cleaned = 0;

    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        for (int dy = -yRange; dy <= yRange; dy++) {
          int x = cx + dx;
          int y = cy + dy;
          int z = cz + dz;

          Block b = w.getBlockAt(x, y, z);
          Material t = b.getType();

          if (!isDifficultyMaterial(t)) continue;

          // “難易度ブロックらしい状況” だけ掃除（海上ステージ想定の安全弁）
          Block below = w.getBlockAt(x, y - 1, z);
          Material belowType = below.getType();
          boolean looksLikeOurStage =
              belowType == Material.WATER ||
                  belowType == Material.PRISMARINE ||
                  belowType == Material.SEA_LANTERN ||
                  belowType == Material.CYAN_STAINED_GLASS ||
                  belowType == Material.MAGENTA_STAINED_GLASS;

          if (!looksLikeOurStage) continue;

          // 下が水なら WATER に戻す／それ以外なら AIR にする（元ロジック踏襲）
          if (belowType == Material.WATER) {
            b.setType(Material.WATER);
          } else {
            b.setType(Material.AIR);
          }

          cleaned++;
        }
      }
    }

    return cleaned;
  }

  // ✅ 追加：残骸の黄色ガラス（YELLOW_STAINED_GLASS）だけを回収する
// - y==centerY（床面）→ CYAN/MAGENTA に戻す
// - それ以外（縦に残った残骸）→ AIR にする（ステージ上空の残骸を確実除去）
  private int sweepLemonGlassAround(Location center, int radius, int yRange) {
    if (center == null || center.getWorld() == null) return 0;

    World w = center.getWorld();
    int cx = center.getBlockX();
    int cy = center.getBlockY();
    int cz = center.getBlockZ();

    int cleaned = 0;

    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        for (int dy = -yRange; dy <= yRange; dy++) {
          int x = cx + dx;
          int y = cy + dy;
          int z = cz + dz;

          Block b = w.getBlockAt(x, y, z);
          if (b.getType() != Material.YELLOW_STAINED_GLASS) continue;

          // 誤爆防止：近傍が“ステージっぽい素材”のときだけ処理
          Material below = w.getBlockAt(x, y - 1, z).getType();
          Material hereBelow2 = w.getBlockAt(x, y - 2, z).getType(); // 念のためもう1段

          boolean looksLikeOurStage =
              below == Material.WATER ||
                  below == Material.PRISMARINE ||
                  below == Material.SEA_LANTERN ||
                  below == Material.CYAN_STAINED_GLASS ||
                  below == Material.MAGENTA_STAINED_GLASS ||
                  hereBelow2 == Material.WATER; // 縦残骸が浮いてても海上なら拾える

          if (!looksLikeOurStage) continue;

          if (y == cy) {
            // ネオン床（上面）は CYAN/MAGENTA 市松へ戻す
            Material restore = (((x - cx) + (z - cz)) % 2 == 0)
                ? Material.CYAN_STAINED_GLASS
                : Material.MAGENTA_STAINED_GLASS;
            b.setType(restore, false);
          } else {
            // 縦に残った残骸は消す
            b.setType(Material.AIR, false);
          }

          cleaned++;
        }
      }
    }

    return cleaned;
  }

  // ✅ 追加：最近のステージ周辺をまとめて掃除
  private int sweepAllLemonGlass() {
    int total = 0;
    for (Location c : recentStageCenters) {
      total += sweepLemonGlassAround(c, 128, 16); // ← 高さを16に上げて取りこぼし防止
    }
    if (total > 0) plugin.getLogger().info("[MSZ][CLEAN] lemon remnants cleaned=" + total);
    return total;
  }

  // ★ 難易度ブロックだけを全部消す（何個消したかを返す）
  public int clearDifficultyBlocks() {
    int cleaned = 0;

    // ✅ まず「登録済み座標キー + 旧difficultyBlocks」を全部まとめて掃除対象にする
    java.util.Set<String> keysToClean = new java.util.HashSet<>(difficultyKeys);
    for (Block b : difficultyBlocks) {
      String k = toBlockKey(b);
      if (k != null) keysToClean.add(k);
    }

    for (String key : keysToClean) {
      Block b = fromBlockKey(key);
      if (b == null) continue;

      Material type = b.getType();
      // 既に他のブロックに変わっていたら触らない
      if (!(type == Material.PURPLE_CONCRETE ||
          type == Material.LIME_CONCRETE ||
          type == Material.BLUE_CONCRETE)) {
        continue;
      }

      // 下が水なら WATER に戻す／それ以外なら AIR にする
      Block below = b.getWorld().getBlockAt(b.getX(), b.getY() - 1, b.getZ());
      if (below.getType() == Material.WATER) {
        b.setType(Material.WATER);
      } else {
        b.setType(Material.AIR);
      }

      cleaned++;
    }

    // ✅ 登録情報は消す（次のゲームで再登録される）
    difficultyBlocks.clear();
    difficultyKeys.clear();

    // ✅ 追加：それでも取り残しがある（登録漏れ/落ちた/再起動等）対策で “中心周辺スイープ”
    int sweptTotal = 0;
    for (Location c : recentStageCenters) {
      sweptTotal += sweepDifficultyBlocksAround(c, DIFF_SWEEP_RADIUS, DIFF_SWEEP_Y_RANGE);
    }

    return cleaned + sweptTotal;
  }

  // =======================================================
  // ★ 行商人＋ラマ2匹（Treasure Shop）を全削除する（新規実装）
  // =======================================================
  public int clearShopEntities() {
    int removed = 0;

    // ✅ 先に MovingSafetyZone を止める（存在すれば）
    stopMovingSafetyZoneTask();

    // ✅ UFO Departure演出を試みる
    boolean ufoDeparting = tryStartUfoDeparture();

    if (ufoDeparting) {
      stageTrader = null;
      stageLlamas.clear();
      plugin.getLogger().info("[UFO] Departure started - entities removed by UFO");
    } else {
      if (stageTrader != null) {
        try {
          if (!stageTrader.isDead()) {
            stageTrader.remove();
            removed++;
          }
        } catch (Exception ignored) {}
        stageTrader = null;
      }

      for (TraderLlama l : stageLlamas) {
        if (l == null) continue;
        try {
          if (!l.isDead()) {
            try { l.setLeashHolder(null); } catch (Exception ignored2) {}
            l.remove();
            removed++;
          }
        } catch (Exception ignored) {}
      }
      stageLlamas.clear();
    }

    return removed;
  }

  // =======================================================
  // ✅ サーバー起動直後に、前回残っていた Treasure Shop 残骸を掃除する
  // - stageTrader / stageLlamas の参照が無くても、ワールドを走査して削除する
  // - 「Treasure Shop」名の行商人と、その近くの TraderLlama を対象にする
  // =======================================================
  public int purgeTreasureShopEntitiesOnStartup() {
    int removed = 0;

    // 念のため、MSZ / UFO も先に止める
    stopMovingSafetyZoneIfRunning();
    stopUfoIfAvailable();

    java.util.List<Location> removedTraderLocs = new java.util.ArrayList<>();

    World arenaWorld = arenaWorldManager.getArenaWorld();
    for (org.bukkit.entity.Entity e : arenaWorld.getEntities()) {
      if (!(e instanceof WanderingTrader trader)) continue;

        String rawName = trader.getCustomName();
        if (rawName == null) continue;

        String plain = ChatColor.stripColor(rawName);
        if (!"Treasure Shop".equalsIgnoreCase(plain)) continue;

        removedTraderLocs.add(trader.getLocation().clone());

        try {
          if (!trader.isDead()) {
            trader.remove();
            removed++;
          }
        } catch (Exception ignored) {}
    }

    // 行商人の近くにいる TraderLlama も掃除
    for (org.bukkit.entity.Entity e : arenaWorld.getEntities()) {
      if (!(e instanceof TraderLlama llama)) continue;

        Location loc = llama.getLocation();
        boolean nearRemovedTrader = false;

        for (Location tloc : removedTraderLocs) {
          if (tloc.getWorld() != null
              && loc.getWorld() != null
              && tloc.getWorld().getUID().equals(loc.getWorld().getUID())
              && tloc.distanceSquared(loc) <= 64.0) { // 半径8ブロック
            nearRemovedTrader = true;
            break;
          }
        }

        if (!nearRemovedTrader) continue;

        try {
          if (!llama.isDead()) {
            try { llama.setLeashHolder(null); } catch (Exception ignored2) {}
            llama.remove();
            removed++;
          }
        } catch (Exception ignored) {}
    }

    stageTrader = null;
    stageLlamas.clear();

    plugin.getLogger().info("🧹 startup Treasure Shop purge removed=" + removed);
    return removed;
  }

  /** 初期のふわっとした演出と環境音 */
  private void playAmbient(Location center, Player player) {
    World w = center.getWorld();
    w.spawnParticle(Particle.END_ROD, center.clone().add(0.5, 1.2, 0.5),
        60, 2.0, 1.0, 2.0, 0.01);
    player.playSound(center, Sound.AMBIENT_UNDERWATER_LOOP, 0.8f, 1.0f);
  }

  // ========= 演出ユーティリティ =========

  private void spawnCircleParticles(Location center, Particle particle, double radius, int count) {
    World w = center.getWorld();

    new BukkitRunnable() {
      double angle = 0;

      @Override
      public void run() {
        for (int i = 0; i < count; i++) {
          double rad = Math.toRadians(angle + (360.0 / count) * i);
          double x = center.getX() + Math.cos(rad) * radius;
          double z = center.getZ() + Math.sin(rad) * radius;
          w.spawnParticle(
              particle,
              new Location(w, x, center.getY() + 0.3, z),
              1, 0, 0, 0, 0
          );
        }

        angle += 8;
        if (angle >= 360) {
          cancel();
        }
      }

    }.runTaskTimer(plugin, 0L, 2L);
  }

  private void spawnRisingPillars(Location center, Particle particle) {
    World w = center.getWorld();

    new BukkitRunnable() {
      double yOff = 0;

      @Override
      public void run() {
        for (int i = -1; i <= 1; i++) {
          for (int j = -1; j <= 1; j++) {
            Location loc = center.clone().add(i * 0.5, yOff, j * 0.5);
            w.spawnParticle(particle, loc, 3, 0.05, 0.1, 0.05, 0.01);
          }
        }
        yOff += 0.25;
        if (yOff > 3.5) cancel();
      }

    }.runTaskTimer(plugin, 0L, 2L);
  }

  // =======================================================
  // ★ 行商人＋ラマ2匹をネオン床ステージの上にスポーンさせる

  private String stageLang() {
    try {
      for (Player p : Bukkit.getOnlinePlayers()) {
        if (p != null && p.isOnline()) {
          String lang = "en";
          if (plugin.getPlayerLanguageStore() != null) {
            lang = plugin.getPlayerLanguageStore().getLang(p, lang);
          }
          return lang;
        }
      }
    } catch (Throwable ignored) {}
    return "en";
  }

  private String trStage(String key) {
    try {
      if (plugin != null && plugin.getI18n() != null) {
        return plugin.getI18n().tr(stageLang(), key);
      }
    } catch (Throwable ignored) {}
    return key;
  }

  private boolean isTreasureShopTitle(String title) {
    String plain = ChatColor.stripColor(title);
    if (plain == null) plain = "";
    String normalized = plain.toLowerCase(java.util.Locale.ROOT);

    if (normalized.contains("treasure shop")) {
      return true;
    }

    try {
      String localized = ChatColor.stripColor(trStage("finalAudit.shop.treasureShop"));
      if (localized != null && !localized.isBlank()
          && normalized.contains(localized.toLowerCase(java.util.Locale.ROOT))) {
        return true;
      }
    } catch (Throwable ignored) {}

    return false;
  }


  private void completeTreasureShopSecretTradeNow(Player player, MerchantInventory merchantInv, String source) {
    if (merchantInv != null) {
      merchantInv.setItem(0, null);
      merchantInv.setItem(1, null);
      merchantInv.setItem(2, null);
    }

    java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
    for (ItemStack leftover : overflow.values()) {
      player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }

    player.updateInventory();

    shopDebug("OK: completed immediate plugin-owned secret trade. source=" + source);

    String shopLang = plugin.getConfig().getString("language.default", "ja");
    try {
      if (plugin.getPlayerLanguageStore() != null) {
        String saved = plugin.getPlayerLanguageStore().getLang(player, shopLang);
        if (saved != null && !saved.isBlank()) shopLang = saved;
      }
    } catch (Throwable ignored) {}

    player.sendTitle(
        ChatColor.GOLD + plugin.getI18n().tr(shopLang, "gameplay.shop.tradeCompleteTitle"),
        ChatColor.AQUA + plugin.getI18n().tr(shopLang, "gameplay.shop.hiddenPowerAwakens"),
        5,
        40,
        10
    );

    player.sendMessage(
        ChatColor.AQUA + "??? " + ChatColor.GOLD + plugin.getI18n().tr(shopLang, "gameplay.shop.hiddenPowerChat")
    );

    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.8f);

    World w = player.getWorld();
    Location loc = player.getEyeLocation().clone()
        .add(player.getLocation().getDirection().multiply(0.8));
    w.spawnParticle(Particle.TOTEM, loc, 40, 0.4, 0.4, 0.4, 0.01);
    w.spawnParticle(Particle.END_ROD, loc, 120, 0.7, 0.7, 0.7, 0.02);
    w.spawnParticle(Particle.ENCHANTMENT_TABLE, loc, 80, 0.7, 0.7, 0.7, 0.0);
  }


  private void startTreasureShopOpenViewSecretTradeScanner() {
    if (treasureShopSecretTradeScannerHandle != null) return;

    treasureShopSecretTradeScannerHandle = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
      try {
        for (Player player : Bukkit.getOnlinePlayers()) {
          if (player == null || !player.isOnline()) continue;

          org.bukkit.inventory.InventoryView view = player.getOpenInventory();
          if (view == null || view.getTopInventory() == null) continue;

          // Local emergency fallback:
          // If the Treasure Shop view is open and the player has marker-backed
          // Special Emeralds, complete the trade from the player's inventory.
          // This avoids merchant-input ghost slots and client/server view mismatch.
          if (tryCompleteTreasureShopOpenViewPlayerInventoryFallback(player, view, "open-view-player-inventory-fallback")) {
            continue;
          }

          if (view.getTopInventory().getType() != InventoryType.MERCHANT) continue;
          if (!(view.getTopInventory() instanceof MerchantInventory merchantInv)) continue;

          // Marker-first fallback:
          // Do not gate this scanner on the GUI title. The TreasureRun item marker
          // is the source of truth for this gameplay transaction.
          tryCompleteTreasureShopOpenViewSecretTrade(player, merchantInv, view, "open-view-scanner");
        }
      } catch (Throwable t) {
        plugin.getLogger().warning("[ShopDebug] Treasure Shop open-view scanner failed: " + t.getMessage());
      }
    }, 20L, 5L);
  }

  @EventHandler(ignoreCancelled = true)
  public void onTreasureSecretCommandFallback(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
    if (event == null || event.getPlayer() == null) return;

    String message = event.getMessage();
    if (message == null) return;

    String normalized = message.trim().toLowerCase(java.util.Locale.ROOT);
    if (!normalized.equals("/trsecret")
        && !normalized.equals("/treasuresecret")
        && !normalized.equals("/treasureshopsecret")) {
      return;
    }

    event.setCancelled(true);

    Player player = event.getPlayer();
    shopDebug("command fallback invoked"
        + " player=" + player.getName()
        + " command=" + message
        + " available=" + countTreasureShopTradeCandidates(player));

    if (!tryCompleteTreasureShopServerSideFallback(player, "command-fallback:" + normalized)) {
      player.sendMessage(ChatColor.YELLOW + "[TreasureRun] You need five Special Emeralds for the secret trade.");
    }
  }

  private boolean tryCompleteTreasureShopServerSideFallback(Player player, String source) {
    if (player == null) return false;

    long now = System.currentTimeMillis();
    Long cooldownUntil = treasureShopAutoTradeCooldownUntilMs.get(player.getUniqueId());
    if (cooldownUntil != null && now < cooldownUntil) {
      shopDebug("command fallback skipped by cooldown. source=" + source);
      return false;
    }

    int available = countTreasureShopTradeCandidates(player);
    if (available < 5) {
      shopDebug("command fallback found too few Special Emeralds. available=" + available + " source=" + source);
      return false;
    }

    if (!consumeTreasureShopTradeCandidates(player, 5)) {
      shopDebug("WARN: command fallback found enough candidates but failed to consume five. source=" + source);
      player.updateInventory();
      return false;
    }

    treasureShopAutoTradeCooldownUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + 1000L);

    completeTreasureShopSecretTradeNow(player, null, source);
    player.updateInventory();

    shopDebug("OK: command fallback completed Treasure Shop secret trade. source=" + source);
    return true;
  }

  @EventHandler(ignoreCancelled = true)
  public void onTreasureShopTraderSecretTrade(org.bukkit.event.player.PlayerInteractEntityEvent event) {
    if (event == null) return;
    Player player = event.getPlayer();
    if (player == null) return;
    org.bukkit.entity.Entity clicked = event.getRightClicked();
    if (!isTreasureShopInteractionTarget(clicked)) return;

    long now = System.currentTimeMillis();
    Long cooldownUntil = treasureShopAutoTradeCooldownUntilMs.get(player.getUniqueId());
    if (cooldownUntil != null && now < cooldownUntil) {
      return;
    }

    int available = countTreasureShopTradeCandidates(player);
    shopDebug("trader-entity fallback checked"
        + " player=" + player.getName()
        + " clicked=" + (clicked == null ? "null" : clicked.getType())
        + " available=" + available);

    if (available < 5) {
      player.sendMessage(ChatColor.YELLOW + "[TreasureRun] The Treasure Shop needs five Special Emeralds.");
      return;
    }

    if (!consumeTreasureShopTradeCandidates(player, 5)) {
      shopDebug("WARN: trader-entity fallback found enough candidates but failed to consume five.");
      player.updateInventory();
      return;
    }

    treasureShopAutoTradeCooldownUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + 1000L);

    event.setCancelled(true);
    completeTreasureShopSecretTradeNow(player, null, "trader-entity-right-click-fallback");
    player.updateInventory();

    shopDebug("OK: trader-entity fallback completed Treasure Shop secret trade.");
  }

  private boolean isTreasureShopInteractionTarget(org.bukkit.entity.Entity entity) {
    if (!(entity instanceof WanderingTrader)) return false;

    try {
      if (stageTrader != null
          && stageTrader.isValid()
          && stageTrader.getUniqueId().equals(entity.getUniqueId())) {
        return true;
      }
    } catch (Throwable ignored) {}

    String plainName = "";
    try {
      plainName = ChatColor.stripColor(entity.getCustomName());
      if (plainName == null || plainName.isBlank()) {
        plainName = ChatColor.stripColor(entity.getName());
      }
    } catch (Throwable ignored) {}

    if (plainName == null) plainName = "";
    String normalized = plainName.toLowerCase(java.util.Locale.ROOT);

    if (normalized.contains("treasure shop")) return true;

    try {
      String localized = ChatColor.stripColor(trStage("finalAudit.shop.treasureShop"));
      if (localized != null
          && !localized.isBlank()
          && normalized.contains(localized.toLowerCase(java.util.Locale.ROOT))) {
        return true;
      }
    } catch (Throwable ignored) {}

    return false;
  }

  private boolean isTreasureShopTradeCandidate(ItemStack item) {
    if (item == null || item.getType() == Material.AIR) return false;

    try {
      if (plugin.getItemFactory().isTreasureEmerald(item)) return true;
    } catch (Throwable ignored) {}

    if (item.getType() != Material.EMERALD) return false;
    if (!item.hasItemMeta()) return false;

    try {
      org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
      if (meta == null) return false;

      if (meta.hasDisplayName()) {
        String name = ChatColor.stripColor(meta.getDisplayName());
        if (name != null && name.toLowerCase(java.util.Locale.ROOT).contains("special emerald")) {
          return true;
        }
      }

      if (meta.hasLore() && meta.getLore() != null) {
        for (String line : meta.getLore()) {
          String plain = ChatColor.stripColor(line);
          if (plain == null) continue;
          String normalized = plain.toLowerCase(java.util.Locale.ROOT);
          if (normalized.contains("crafted in treasurerun")
              || normalized.contains("special emerald")) {
            return true;
          }
        }
      }
    } catch (Throwable ignored) {}

    return false;
  }

  private int countTreasureShopTradeCandidates(Player player) {
    if (player == null) return 0;

    int total = 0;

    try {
      ItemStack cursor = player.getItemOnCursor();
      if (isTreasureShopTradeCandidate(cursor)) {
        total += cursor.getAmount();
      }
    } catch (Throwable ignored) {}

    for (ItemStack stack : player.getInventory().getContents()) {
      if (isTreasureShopTradeCandidate(stack)) {
        total += stack.getAmount();
      }
    }

    return total;
  }

  private boolean consumeTreasureShopTradeCandidates(Player player, int amount) {
    if (player == null || amount <= 0) return false;

    int remaining = amount;

    try {
      ItemStack cursor = player.getItemOnCursor();
      if (isTreasureShopTradeCandidate(cursor)) {
        int take = Math.min(remaining, cursor.getAmount());
        int left = cursor.getAmount() - take;

        if (left > 0) {
          ItemStack updated = cursor.clone();
          updated.setAmount(left);
          player.setItemOnCursor(updated);
        } else {
          player.setItemOnCursor(null);
        }

        remaining -= take;
      }
    } catch (Throwable t) {
      shopDebug("WARN: failed to consume candidate from cursor: " + t.getMessage());
    }

    for (int slot = 0; slot < player.getInventory().getSize() && remaining > 0; slot++) {
      ItemStack stack = player.getInventory().getItem(slot);
      if (!isTreasureShopTradeCandidate(stack)) continue;

      int take = Math.min(remaining, stack.getAmount());
      int left = stack.getAmount() - take;

      if (left > 0) {
        ItemStack updated = stack.clone();
        updated.setAmount(left);
        player.getInventory().setItem(slot, updated);
      } else {
        player.getInventory().setItem(slot, null);
      }

      remaining -= take;
    }

    return remaining == 0;
  }

  private boolean tryCompleteTreasureShopOpenViewPlayerInventoryFallback(
      Player player,
      org.bukkit.inventory.InventoryView view,
      String source
  ) {
    if (player == null || view == null) return false;

    long now = System.currentTimeMillis();
    Long cooldownUntil = treasureShopAutoTradeCooldownUntilMs.get(player.getUniqueId());
    if (cooldownUntil != null && now < cooldownUntil) {
      return false;
    }

    boolean titleMatches = false;
    String plainTitle = "";
    try {
      plainTitle = ChatColor.stripColor(view.getTitle());
      titleMatches = isTreasureShopTitle(view.getTitle());
    } catch (Throwable ignored) {}

    boolean nearStageTrader = false;
    try {
      nearStageTrader = stageTrader != null
          && stageTrader.isValid()
          && stageTrader.getWorld().equals(player.getWorld())
          && stageTrader.getLocation().distanceSquared(player.getLocation()) <= 100.0;
    } catch (Throwable ignored) {}

    if (!titleMatches && !nearStageTrader) {
      return false;
    }

    int available = 0;

    ItemStack cursor = null;
    try {
      cursor = player.getItemOnCursor();
      if (plugin.getItemFactory().isTreasureEmerald(cursor)) {
        available += cursor.getAmount();
      }
    } catch (Throwable ignored) {}

    for (ItemStack stack : player.getInventory().getContents()) {
      if (plugin.getItemFactory().isTreasureEmerald(stack)) {
        available += stack.getAmount();
      }
    }

    if (available < 5) {
      return false;
    }

    shopDebug("open-view player-inventory fallback detected marker-backed Treasure Emerald"
        + " title=" + plainTitle
        + " titleMatches=" + titleMatches
        + " nearStageTrader=" + nearStageTrader
        + " available=" + available
        + " cursor=" + describeItem(cursor));

    shopDebug("SKIP: open-view player-inventory fallback is disabled; use trader right-click or /trsecret instead.");

    if (!java.lang.Boolean.getBoolean("treasurerun.enableOpenViewSecretTrade")) {
      return false;
    }


    int remainingToConsume = 5;

    try {
      if (cursor != null && plugin.getItemFactory().isTreasureEmerald(cursor)) {
        int take = Math.min(remainingToConsume, cursor.getAmount());
        int left = cursor.getAmount() - take;
        if (left > 0) {
          ItemStack newCursor = cursor.clone();
          newCursor.setAmount(left);
          player.setItemOnCursor(newCursor);
        } else {
          player.setItemOnCursor(null);
        }
        remainingToConsume -= take;
      }
    } catch (Throwable t) {
      shopDebug("WARN: failed to consume Special Emeralds from cursor: " + t.getMessage());
    }

    for (int slot = 0; slot < player.getInventory().getSize() && remainingToConsume > 0; slot++) {
      ItemStack stack = player.getInventory().getItem(slot);
      if (!plugin.getItemFactory().isTreasureEmerald(stack)) continue;

      int take = Math.min(remainingToConsume, stack.getAmount());
      int left = stack.getAmount() - take;

      if (left > 0) {
        ItemStack updated = stack.clone();
        updated.setAmount(left);
        player.getInventory().setItem(slot, updated);
      } else {
        player.getInventory().setItem(slot, null);
      }

      remainingToConsume -= take;
    }

    if (remainingToConsume > 0) {
      shopDebug("WARN: fallback found enough Special Emeralds but failed to consume all five. remainingToConsume="
          + remainingToConsume);
      player.updateInventory();
      return false;
    }

    try {
      view.setItem(0, null);
      view.setItem(1, null);
      view.setItem(2, null);
    } catch (Throwable ignored) {}

    treasureShopAutoTradeCooldownUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + 1000L);

    completeTreasureShopSecretTradeNow(player, null, source);

    player.updateInventory();

    shopDebug("OK: open-view player-inventory fallback completed Treasure Shop secret trade. source=" + source);
    return true;
  }

  private ItemStack firstNonAir(ItemStack preferred, ItemStack fallback) {
    if (preferred != null && preferred.getType() != Material.AIR) return preferred;
    if (fallback != null && fallback.getType() != Material.AIR) return fallback;
    return null;
  }

  private String describeItem(ItemStack item) {
    if (item == null) return "null";
    return item.getType() + " x" + item.getAmount()
        + " special=" + plugin.getItemFactory().isTreasureEmerald(item);
  }

  private boolean tryCompleteTreasureShopOpenViewSecretTrade(Player player, MerchantInventory merchantInv, org.bukkit.inventory.InventoryView view, String source) {
    if (player == null || merchantInv == null) return false;

    long now = System.currentTimeMillis();
    Long cooldownUntil = treasureShopAutoTradeCooldownUntilMs.get(player.getUniqueId());
    if (cooldownUntil != null && now < cooldownUntil) {
      return false;
    }

    ItemStack merchantInput0 = merchantInv.getItem(0);
    ItemStack merchantInput1 = merchantInv.getItem(1);
    ItemStack viewInput0 = null;
    ItemStack viewInput1 = null;

    try {
      if (view != null) {
        viewInput0 = view.getItem(0);
        viewInput1 = view.getItem(1);
      }
    } catch (Throwable t) {
      shopDebug("WARN: failed to read merchant raw slots from InventoryView: " + t.getMessage());
    }

    ItemStack input0 = firstNonAir(viewInput0, merchantInput0);
    ItemStack input1 = firstNonAir(viewInput1, merchantInput1);

    boolean slot0Special = plugin.getItemFactory().isTreasureEmerald(input0)
        && input0.getAmount() >= 5;
    boolean slot1Special = plugin.getItemFactory().isTreasureEmerald(input1)
        && input1.getAmount() >= 5;

    boolean slot0Empty = input0 == null || input0.getType() == Material.AIR;
    boolean slot1Empty = input1 == null || input1.getType() == Material.AIR;

    if (input0 != null || input1 != null || merchantInput0 != null || merchantInput1 != null) {
      shopDebug("open-view scanner raw-slot snapshot"
          + " view0=" + describeItem(viewInput0)
          + " view1=" + describeItem(viewInput1)
          + " merchant0=" + describeItem(merchantInput0)
          + " merchant1=" + describeItem(merchantInput1)
          + " resolved0=" + describeItem(input0)
          + " resolved1=" + describeItem(input1)
          + " slot0Special=" + slot0Special
          + " slot1Special=" + slot1Special
          + " slot0Empty=" + slot0Empty
          + " slot1Empty=" + slot1Empty);
    }

    if (slot0Special || slot1Special) {
      String title = "";
      try {
        title = ChatColor.stripColor(player.getOpenInventory().getTitle());
      } catch (Throwable ignored) {}

      shopDebug("open-view scanner detected marker-backed Treasure Emerald input"
          + " title=" + title
          + " input0=" + (input0 == null ? "null" : input0.getType() + " x" + input0.getAmount())
          + " input1=" + (input1 == null ? "null" : input1.getType() + " x" + input1.getAmount())
          + " slot0Special=" + slot0Special
          + " slot1Special=" + slot1Special
          + " slot0Empty=" + slot0Empty
          + " slot1Empty=" + slot1Empty);
    }

    if (slot0Special && slot1Empty) {
      return completeTreasureShopSecretTradeFromMerchantSlot(player, merchantInv, view, input0, 0, source + ":slot0");
    }

    if (slot1Special && slot0Empty) {
      return completeTreasureShopSecretTradeFromMerchantSlot(player, merchantInv, view, input1, 1, source + ":slot1");
    }

    return false;
  }

  private boolean completeTreasureShopSecretTradeFromMerchantSlot(
      Player player,
      MerchantInventory merchantInv,
      org.bukkit.inventory.InventoryView view,
      ItemStack observedSourceStack,
      int sourceSlot,
      String source
  ) {
    ItemStack sourceStack = observedSourceStack != null && observedSourceStack.getType() != Material.AIR
        ? observedSourceStack.clone()
        : merchantInv.getItem(sourceSlot);
    if (!plugin.getItemFactory().isTreasureEmerald(sourceStack) || sourceStack.getAmount() < 5) {
      return false;
    }

    ItemStack fiveSpecialEmeralds = sourceStack.clone();
    fiveSpecialEmeralds.setAmount(5);

    ItemStack remainder = null;
    int remainingAmount = sourceStack.getAmount() - 5;
    if (remainingAmount > 0) {
      remainder = sourceStack.clone();
      remainder.setAmount(remainingAmount);
    }

    merchantInv.setItem(sourceSlot, fiveSpecialEmeralds);
    merchantInv.setItem(sourceSlot == 0 ? 1 : 0, null);

    try {
      if (view != null) {
        view.setItem(sourceSlot, fiveSpecialEmeralds);
        view.setItem(sourceSlot == 0 ? 1 : 0, null);
        view.setItem(2, null);
      }
    } catch (Throwable t) {
      shopDebug("WARN: failed to sync merchant raw slots before completion: " + t.getMessage());
    }

    treasureShopAutoTradeCooldownUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + 1000L);

    completeTreasureShopSecretTradeNow(player, merchantInv, source);

    try {
      if (view != null) {
        view.setItem(0, null);
        view.setItem(1, null);
        view.setItem(2, null);
      }
    } catch (Throwable t) {
      shopDebug("WARN: failed to clear merchant raw slots after completion: " + t.getMessage());
    }

    if (remainder != null) {
      java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(remainder);
      for (ItemStack leftover : overflow.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
      }
    }

    player.updateInventory();
    shopDebug("OK: open-view scanner completed Treasure Shop secret trade. source=" + source);
    return true;
  }


  // =======================================================
  // ✅ Guaranteed server-side fallback for Treasure Shop secret trade
  //   - right-clicking the Treasure Shop trader with 5 Special Emeralds works
  //   - /trsecret also works without plugin.yml command registration
  //   - the transaction is based only on the TreasureRun PDC marker
  // =======================================================
  @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
  public void onTreasureSecretCommandGuaranteedFallback(PlayerCommandPreprocessEvent event) {
    if (event == null || event.getPlayer() == null || event.getMessage() == null) return;
    if (event.isCancelled()) return;

    String normalized = event.getMessage().trim().toLowerCase(java.util.Locale.ROOT);
    if (!(normalized.equals("/trsecret")
        || normalized.startsWith("/trsecret ")
        || normalized.equals("/treasuresecret")
        || normalized.startsWith("/treasuresecret "))) {
      return;
    }

    event.setCancelled(true);
    Player player = event.getPlayer();
    shopDebug("guaranteed command fallback invoked command=" + normalized
        + " player=" + player.getName()
        + " available=" + countGuaranteedSecretTradeEmeralds(player));

    if (!tryCompleteGuaranteedSecretTrade(player, "guaranteed-command-fallback:" + normalized)) {
      player.sendMessage(ChatColor.YELLOW + "[TreasureRun] You need five Special Emeralds for the secret trade.");
    }
  }

  @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
  public void onTreasureShopTraderRightClickGuaranteedFallback(PlayerInteractEntityEvent event) {
    if (event == null || event.getPlayer() == null) return;

    try {
      if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
    } catch (Throwable ignored) {}

    Player player = event.getPlayer();
    Entity clicked = event.getRightClicked();
    if (!(clicked instanceof WanderingTrader)) return;
    if (!isGuaranteedTreasureShopTrader(clicked)) return;

    int available = countGuaranteedSecretTradeEmeralds(player);
    String clickedName = clicked.getCustomName();

    shopDebug("guaranteed trader right-click fallback checked"
        + " player=" + player.getName()
        + " entity=" + clicked.getType()
        + " name=" + (clickedName == null ? "null" : ChatColor.stripColor(clickedName))
        + " available=" + available);

    // If the player has fewer than five Special Emeralds, do not block the normal shop GUI.
    if (available < 5) return;

    event.setCancelled(true);
    if (!tryCompleteGuaranteedSecretTrade(player, "guaranteed-trader-right-click-fallback")) {
      player.sendMessage(ChatColor.YELLOW + "[TreasureRun] The Treasure Shop needs five Special Emeralds.");
    }
  }

  private boolean isGuaranteedTreasureShopTrader(Entity entity) {
    if (entity == null) return false;

    try {
      if (stageTrader != null && entity.getUniqueId().equals(stageTrader.getUniqueId())) {
        return true;
      }
    } catch (Throwable ignored) {}

    String name = entity.getCustomName();
    if (name != null && isTreasureShopTitle(name)) return true;

    String plainName = ChatColor.stripColor(name);
    return plainName != null && plainName.toLowerCase(java.util.Locale.ROOT).contains("treasure shop");
  }

  private int countGuaranteedSecretTradeEmeralds(Player player) {
    if (player == null || plugin == null || plugin.getItemFactory() == null) return 0;

    int total = 0;

    ItemStack cursor = player.getItemOnCursor();
    if (plugin.getItemFactory().isTreasureEmerald(cursor)) {
      total += cursor.getAmount();
    }

    for (ItemStack item : player.getInventory().getContents()) {
      if (plugin.getItemFactory().isTreasureEmerald(item)) {
        total += item.getAmount();
      }
    }

    return total;
  }

  private boolean consumeGuaranteedSecretTradeEmeralds(Player player, int amount) {
    if (player == null || amount <= 0 || plugin == null || plugin.getItemFactory() == null) return false;
    if (countGuaranteedSecretTradeEmeralds(player) < amount) return false;

    int remaining = amount;

    ItemStack cursor = player.getItemOnCursor();
    if (plugin.getItemFactory().isTreasureEmerald(cursor)) {
      int take = Math.min(remaining, cursor.getAmount());
      int left = cursor.getAmount() - take;
      if (left <= 0) {
        player.setItemOnCursor(null);
      } else {
        cursor.setAmount(left);
        player.setItemOnCursor(cursor);
      }
      remaining -= take;
    }

    org.bukkit.inventory.PlayerInventory inv = player.getInventory();
    for (int slot = 0; slot < inv.getSize() && remaining > 0; slot++) {
      ItemStack item = inv.getItem(slot);
      if (!plugin.getItemFactory().isTreasureEmerald(item)) continue;

      int take = Math.min(remaining, item.getAmount());
      int left = item.getAmount() - take;
      if (left <= 0) {
        inv.setItem(slot, null);
      } else {
        item.setAmount(left);
        inv.setItem(slot, item);
      }
      remaining -= take;
    }

    player.updateInventory();
    return remaining == 0;
  }


  public boolean runRegisteredSecretTradeFallback(Player player, String source) {
    String safeSource = (source == null || source.isBlank()) ? "registered-command" : source;
    return tryCompleteGuaranteedSecretTrade(player, safeSource);
  }

  private boolean tryCompleteGuaranteedSecretTrade(Player player, String source) {
    if (player == null) return false;

    long now = System.currentTimeMillis();
    Long cooldownUntil = treasureShopAutoTradeCooldownUntilMs.get(player.getUniqueId());
    if (cooldownUntil != null && now < cooldownUntil) {
      shopDebug("guaranteed fallback skipped by cooldown. source=" + source);
      return true;
    }

    int available = countGuaranteedSecretTradeEmeralds(player);
    if (available < 5) {
      shopDebug("guaranteed fallback found too few Special Emeralds. available=" + available + " source=" + source);
      return false;
    }

    if (!consumeGuaranteedSecretTradeEmeralds(player, 5)) {
      shopDebug("WARN: guaranteed fallback found enough candidates but failed to consume five. source=" + source);
      return false;
    }

    treasureShopAutoTradeCooldownUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + 1000L);
    completeTreasureShopSecretTradeNow(player, null, source);
    shopDebug("OK: guaranteed fallback completed Treasure Shop secret trade. source=" + source);
    return true;
  }

  // =======================================================
  public void spawnTraderAndLlamas(Location center) {
    if (center == null) return;
    World w = center.getWorld();
    if (w == null) return;

    // ✅ cleanup previous Treasure Shop caravan to prevent duplicates

    if (stageTrader != null && stageTrader.isValid()) { try { stageTrader.remove(); } catch (Exception ignored) {} }

    stageTrader = null;

    for (org.bukkit.entity.TraderLlama l : new java.util.ArrayList<>(stageLlamas)) {

      if (l != null && l.isValid()) { try { l.remove(); } catch (Exception ignored) {} }

    }

    stageLlamas.clear();

    // ✅ also remove stray Treasure Shop traders/llamas left in area (radius 48)

    try {

      for (org.bukkit.entity.Entity e : w.getNearbyEntities(center, 48, 24, 48)) {

        if (e instanceof org.bukkit.entity.WanderingTrader || e instanceof org.bukkit.entity.TraderLlama) {

          String n = e.getCustomName();

          if (n != null && (n.contains("Treasure Shop") || ChatColor.stripColor(n).contains(trStage("finalAudit.shop.treasureShop")))) { try { e.remove(); } catch (Exception ignored) {} }

        }

      }

    } catch (Exception ignored) {}

    Location traderLoc = center.clone().add(0.5, 1.1, 0.5);

    WanderingTrader trader = w.spawn(traderLoc, WanderingTrader.class, t -> {
      t.setAI(true);
      t.setPersistent(true);
      t.setGlowing(true);
      t.setCustomName(ChatColor.GOLD + "" + ChatColor.BOLD + trStage("finalAudit.shop.treasureShop"));
      t.setCustomNameVisible(true);
    });

    this.stageTrader = trader;
    // ✅ MSZが「この隊商だけ」を対象にできるように印を付ける
    trader.setMetadata(MovingSafetyZoneTask.CARRIER_META, new FixedMetadataValue(plugin, true));
    this.stageLlamas.clear();

    setupTreasureShopRecipes(trader);

    double[][] offsets = {
        { 1.5, 0.0 },
        { -1.5, 0.0 }
    };

    for (double[] off : offsets) {
      Location llamaLoc = traderLoc.clone().add(off[0], 0, off[1]);
      TraderLlama llama = w.spawn(llamaLoc, TraderLlama.class, l -> {
        l.setAI(true);
        l.setAdult();
        l.setPersistent(true);
        l.setGlowing(true);
      });
      llama.setLeashHolder(trader);
      // ✅ MSZ用：carrier印
      llama.setMetadata(MovingSafetyZoneTask.CARRIER_META, new FixedMetadataValue(plugin, true));

      stageLlamas.add(llama);
    }
    // ===============================
    // ✅ UFO に「この商人＋ラマ2頭」を bind する（増殖防止の決定打）
    //    これにより、UFO側は "trader != null && valid" ルートに入り、
    //    追加スポーン（保険）しなくなる
    // ===============================
    try {
      Object target = this.ufo;
      if (target == null) {
        target = tryCallGetter(plugin, "getUfo");
        if (target == null) target = tryCallGetter(plugin, "getUfoController");
      }

      if (target != null) {
        // bindGroup(WanderingTrader, List<TraderLlama>) を反射で呼ぶ（型違いでも壊れない）
        Method bm = findBestMethod(
            target.getClass(),
            "bindGroup",
            new Class[]{ org.bukkit.entity.WanderingTrader.class, java.util.List.class }
        );

        if (bm != null) {
          bm.setAccessible(true);
          bm.invoke(target, trader, new java.util.ArrayList<>(stageLlamas));
          plugin.getLogger().info("✅ UFO bindGroup OK: trader=" + trader.getUniqueId()
              + " llamas=" + stageLlamas.size());
        } else {
          plugin.getLogger().warning("⚠ UFO bindGroup method not found on " + target.getClass().getName());
        }
      } else {
        plugin.getLogger().warning("⚠ UFO controller is null; skip bindGroup");
      }
    } catch (Throwable t) {
      plugin.getLogger().warning("⚠ UFO bindGroup failed: " + t);
    }

    final Location centerLoc = traderLoc.clone();

    new BukkitRunnable() {
      int seconds = 0;

      @Override
      public void run() {
        if (!plugin.isGameRunning()) {
          if (stageTrader != null && !stageTrader.isDead()) {
            stageTrader.setGlowing(false);
          }
          for (TraderLlama l : stageLlamas) {
            if (l != null && !l.isDead()) {
              l.setGlowing(false);
            }
          }
          cancel();
          return;
        }

        if (stageTrader != null && !stageTrader.isDead()) {
          if (stageTrader.getLocation().distanceSquared(centerLoc) > 256.0) { // 16 blocks
            stageTrader.teleport(centerLoc);
          }
        }

        for (TraderLlama l : stageLlamas) {
          if (l == null || l.isDead()) continue;
          if (l.getLocation().distanceSquared(centerLoc) > 9.0) {
            Location newLoc = centerLoc.clone().add(
                (Math.random() - 0.5) * 2.0,
                0.0,
                (Math.random() - 0.5) * 2.0
            );
            l.teleport(newLoc);
          }
        }

        seconds++;
        if (seconds >= 60) {
          if (stageTrader != null && !stageTrader.isDead()) {
            stageTrader.setGlowing(false);
          }
          for (TraderLlama l : stageLlamas) {
            if (l != null && !l.isDead()) {
              l.setGlowing(false);
            }
          }
        }
      }

    }.runTaskTimer(plugin, 0L, 20L);
  }

  // =======================================================
  // ★ Treasure Shop のレシピ（ここで「特製エメラルド要求」に差し替え）
  // =======================================================
  private void setupTreasureShopRecipes(WanderingTrader trader) {
    java.util.List<MerchantRecipe> recipes = new java.util.ArrayList<>();

    // Trade 1: 5 emerald-compatible items -> 1 golden apple.
    // Keep the merchant recipe material-compatible so Spigot can populate the result slot.
    // The InventoryClickEvent handler still validates the TreasureRun PDC marker before
    // allowing the secret-trade feedback.
    ItemStack emeraldInput5 = new ItemStack(Material.EMERALD, 5);

    ItemStack result1 = new ItemStack(Material.GOLDEN_APPLE, 1);
    MerchantRecipe r1 = new MerchantRecipe(result1, 64);
    r1.addIngredient(emeraldInput5);
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

  // =======================================================
  // ★ 取引結果スロットをクリックしたときのフック（FIX版）
  //   - CraftMerchant問題のため WanderingTrader判定/UUID判定を使わない
  //   - 「画面タイトルが Treasure Shop」かで判定する
  //   - 原材料はクリック瞬間にスナップショットして PDC 判定を確定
  // =======================================================
  @EventHandler(ignoreCancelled = true)
  public void onTraderResultClick(InventoryClickEvent event) {

    shopDebug("InventoryClickEvent fired"
        + " player=" + (event.getWhoClicked() == null ? "null" : event.getWhoClicked().getName())
        + " rawSlot=" + event.getRawSlot()
        + " slotType=" + event.getSlotType()
        + " click=" + event.getClick()
        + " action=" + event.getAction()
        + " shift=" + event.isShiftClick()
        + " cancelled=" + event.isCancelled()
        + " topType=" + (event.getView() == null || event.getView().getTopInventory() == null ? "null" : event.getView().getTopInventory().getType())
    );

    ItemStack dbgCurrent = event.getCurrentItem();
    ItemStack dbgCursor = event.getCursor();
    shopDebug("items current=" + (dbgCurrent == null ? "null" : dbgCurrent.getType() + " x" + dbgCurrent.getAmount())
        + " / cursor=" + (dbgCursor == null ? "null" : dbgCursor.getType() + " x" + dbgCursor.getAmount()));

    if (!(event.getWhoClicked() instanceof Player player)) {
      shopDebug("RETURN: whoClicked is not Player");
      return;
    }

    // Merchant GUI 以外は無視
    if (event.getView() == null || event.getView().getTopInventory() == null
        || event.getView().getTopInventory().getType() != InventoryType.MERCHANT) {
      shopDebug("RETURN: topInventory is not MERCHANT");
      return;
    }

    // 結果スロット(rawSlot=2)以外は無視
    if (event.getRawSlot() != 2) {
      shopDebug("RETURN: not result slot. expected rawSlot=2 but was " + event.getRawSlot()
          + " (slotType=" + event.getSlotType() + ", shift=" + event.isShiftClick() + ")");
      return;
    }

    if (!(event.getView().getTopInventory() instanceof MerchantInventory merchantInv)) {
      shopDebug("RETURN: topInventory is MERCHANT but not MerchantInventory instance");
      return;
    }

    // ★ CraftMerchantでもOKにするため「画面タイトル」で Treasure Shop 判定
    String title = event.getView().getTitle();
    shopDebug("merchant view title=" + title);

    // 色コードが入る可能性があるので strip
    String plainTitle = ChatColor.stripColor(title);
    if (plainTitle == null) plainTitle = "";

    if (!isTreasureShopTitle(title)) {
      shopDebug("RETURN: not Treasure Shop title. plainTitle=" + plainTitle);
      return;
    }

    // The result slot may be empty when the vanilla merchant engine refuses to match
    // custom PDC-backed emeralds. Allow an empty result-slot click so the plugin can
    // complete the validated secret trade itself.
    ItemStack current = event.getCurrentItem();
    if (current != null && current.getType() != Material.AIR && current.getType() != Material.GOLDEN_APPLE) {
      shopDebug("RETURN: current item is not GOLDEN_APPLE. type=" + current.getType());
      return;
    }
    if (current == null || current.getType() == Material.AIR) {
      shopDebug("result slot is empty; attempting plugin-owned secret trade from validated input");
    }

    // ゲーム中のみ
    boolean runningNow = plugin.isGameRunning();
    shopDebug("gameRunning=" + runningNow);
    if (!runningNow) {
      shopDebug("RETURN: game is not running");
      return;
    }

    // ★ クリック瞬間の材料をスナップショット
    ItemStack in0Snap = merchantInv.getItem(0);
    ItemStack in1Snap = merchantInv.getItem(1);

    boolean isSpecial = plugin.getItemFactory().isTreasureEmerald(in0Snap);
    int amount = (in0Snap == null) ? 0 : in0Snap.getAmount();
    boolean slot1Empty = (in1Snap == null || in1Snap.getType() == Material.AIR);

    shopDebug("ingredients snapshot"
        + " in0=" + (in0Snap == null ? "null" : in0Snap.getType() + " x" + in0Snap.getAmount())
        + " in1=" + (in1Snap == null ? "null" : in1Snap.getType() + " x" + in1Snap.getAmount())
        + " isTreasureEmerald=" + isSpecial
        + " amount=" + amount
        + " slot1Empty=" + slot1Empty);

    if (!(isSpecial && amount >= 5 && slot1Empty)) {
      shopDebug("RETURN: ingredient check failed (need TreasureEmerald>=5 and slot1 empty)");
      event.setCancelled(true);
      return;
    }

    // Plugin-owned manual transaction:
    // do not rely on display text, lore, or vanilla merchant recipe matching
    // for this custom PDC-backed item. TreasureRun validates its own item marker
    // and completes the exchange directly.
    event.setCancelled(true);

    ItemStack remainingSpecialEmeralds = in0Snap.clone();
    int remainingAmount = amount - 5;
    if (remainingAmount > 0) {
      remainingSpecialEmeralds.setAmount(remainingAmount);
      merchantInv.setItem(0, remainingSpecialEmeralds);
    } else {
      merchantInv.setItem(0, null);
    }
    merchantInv.setItem(1, null);
    merchantInv.setItem(2, null);

    java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
    for (ItemStack leftover : overflow.values()) {
      player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }
    player.updateInventory();

    shopDebug("OK: completed plugin-owned secret trade -> scheduling effect with runTaskLater");

    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      shopDebug("RUN: runTaskLater executed");

      if (!plugin.isGameRunning()) {
        shopDebug("RETURN(LATER): game is not running");
        return;
      }
      if (!player.isOnline()) {
        shopDebug("RETURN(LATER): player is offline");
        return;
      }

      // まだMerchant画面を開いているか（可能なら同じタイトルかも見る）
      if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() == null) {
        shopDebug("RETURN(LATER): openInventory/topInventory is null");
        return;
      }
      if (player.getOpenInventory().getTopInventory().getType() != InventoryType.MERCHANT) {
        shopDebug("RETURN(LATER): topInventory is not MERCHANT. type=" + player.getOpenInventory().getTopInventory().getType());
        return;
      }

      String titleLater = player.getOpenInventory().getTitle();
      String plainLater = ChatColor.stripColor(titleLater);
      if (plainLater == null) plainLater = "";
      if (!plainLater.toLowerCase().contains("treasure shop")) {
        shopDebug("RETURN(LATER): not Treasure Shop title. plainTitle=" + plainLater);
        return;
      }

      shopDebug("OK(LATER): playing effects now");

      // 演出（100%気づく版）
      String shopLang = plugin.getConfig().getString("language.default", "ja");
      try {
        if (plugin.getPlayerLanguageStore() != null) {
          String saved = plugin.getPlayerLanguageStore().getLang(player, shopLang);
          if (saved != null && !saved.isBlank()) shopLang = saved;
        }
      } catch (Throwable ignored) {}

      player.sendTitle(
          ChatColor.GOLD + plugin.getI18n().tr(shopLang, "gameplay.shop.tradeCompleteTitle"),
          ChatColor.AQUA + plugin.getI18n().tr(shopLang, "gameplay.shop.hiddenPowerAwakens"),
          5,   // fadeIn (ticks)
          40,  // stay   (ticks)
          10   // fadeOut(ticks)
      );
      player.sendMessage(
          ChatColor.AQUA + "??? " + ChatColor.GOLD + plugin.getI18n().tr(shopLang, "gameplay.shop.hiddenPowerChat")
      );

      // 音：確実に聞こえるやつ
      player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
      player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.8f);

      // パーティクル：視界に入る量に増やす
      World w = player.getWorld();
      Location loc = player.getEyeLocation().clone()
          .add(player.getLocation().getDirection().multiply(0.8)); // 視界の少し前
      w.spawnParticle(Particle.TOTEM, loc, 40, 0.4, 0.4, 0.4, 0.01);
      w.spawnParticle(Particle.END_ROD, loc, 120, 0.7, 0.7, 0.7, 0.02);
      w.spawnParticle(Particle.ENCHANTMENT_TABLE, loc, 80, 0.7, 0.7, 0.7, 0.0);
    }, 1L);
  }

  // =======================================================
  // ✅ Treasure Shop manual result refresh
  //   - Do not change language resources or i18n-core.
  //   - Do not validate by display name or lore.
  //   - Show a visible result only after the TreasureRun item marker is present.
  //   - onTraderResultClick() still validates and completes the actual exchange.
  // =======================================================
  @EventHandler(ignoreCancelled = true)
  public void onTreasureShopInputClickRefresh(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) return;
    if (event.getView() == null || event.getView().getTopInventory() == null) return;
    if (event.getView().getTopInventory().getType() != InventoryType.MERCHANT) return;
    if (!(event.getView().getTopInventory() instanceof MerchantInventory)) return;

    // Result-slot clicks are handled by onTraderResultClick().
    if (event.getRawSlot() == 2) return;

    if (!isTreasureShopTitle(event.getView().getTitle())) return;

    ItemStack cursorSnap = event.getCursor() == null ? null : event.getCursor().clone();
    ItemStack currentSnap = event.getCurrentItem() == null ? null : event.getCurrentItem().clone();

    boolean placingSpecialEmeraldIntoMerchantInput =
        (event.getRawSlot() == 0 || event.getRawSlot() == 1)
            && plugin.getItemFactory().isTreasureEmerald(cursorSnap)
            && cursorSnap.getAmount() >= 5;

    boolean shiftClickingSpecialEmeraldFromPlayerInventory =
        event.isShiftClick()
            && event.getRawSlot() >= event.getView().getTopInventory().getSize()
            && plugin.getItemFactory().isTreasureEmerald(currentSnap)
            && currentSnap.getAmount() >= 5;

    if (placingSpecialEmeraldIntoMerchantInput || shiftClickingSpecialEmeraldFromPlayerInventory) {
      event.setCancelled(true);

      if (placingSpecialEmeraldIntoMerchantInput) {
        ItemStack remaining = cursorSnap.clone();
        int remainingAmount = remaining.getAmount() - 5;
        if (remainingAmount > 0) {
          remaining.setAmount(remainingAmount);
          player.setItemOnCursor(remaining);
        } else {
          player.setItemOnCursor(null);
        }
        completeTreasureShopSecretTradeNow(player, (MerchantInventory) event.getView().getTopInventory(), "cursor-to-merchant-input");
        return;
      }

      if (shiftClickingSpecialEmeraldFromPlayerInventory) {
        ItemStack remaining = currentSnap.clone();
        int remainingAmount = remaining.getAmount() - 5;
        if (remainingAmount > 0) {
          remaining.setAmount(remainingAmount);
          event.setCurrentItem(remaining);
        } else {
          event.setCurrentItem(null);
        }
        completeTreasureShopSecretTradeNow(player, (MerchantInventory) event.getView().getTopInventory(), "shift-click-from-player-inventory");
        return;
      }
    }

    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;
      if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() == null) return;
      if (player.getOpenInventory().getTopInventory().getType() != InventoryType.MERCHANT) return;
      if (!(player.getOpenInventory().getTopInventory() instanceof MerchantInventory openMerchantInv)) return;
      if (!isTreasureShopTitle(player.getOpenInventory().getTitle())) return;

      ItemStack input0 = openMerchantInv.getItem(0);
      ItemStack input1 = openMerchantInv.getItem(1);
      boolean hasSpecialInput = plugin.getItemFactory().isTreasureEmerald(input0)
          && input0.getAmount() >= 5
          && (input1 == null || input1.getType() == Material.AIR);

      if (hasSpecialInput) {
        openMerchantInv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 1));
        shopDebug("manual result refreshed for Treasure Shop secret trade");
      } else {
        ItemStack currentResult = openMerchantInv.getItem(2);
        if (currentResult != null && currentResult.getType() == Material.GOLDEN_APPLE) {
          openMerchantInv.setItem(2, null);
          shopDebug("manual result cleared for Treasure Shop secret trade");
        }
      }

      player.updateInventory();
    }, 1L);
  }

  // =======================================================
  // ✅ MovingSafetyZone 起動/停止（reflectionで “実装そのまま” を温存）
  // =======================================================
  private void startMovingSafetyZoneIfAvailable(Location stageCenter) {
    // 既に動いてたら二重起動しない
    stopMovingSafetyZoneIfRunning();

    if (stageTrader == null) return;

    // 実装クラス名候補（あなたの環境に合わせて増やせる）
    String[] candidates = {
        "plugin.MovingSafetyZone",
        "plugin.MovingSafetyZoneTask",
        "plugin.MovingSafetyZoneRunnable"
    };

    for (String cn : candidates) {
      try {
        Class<?> cls = Class.forName(cn);

        // 1) static start(...) があるならそれを優先
        for (Method m : cls.getDeclaredMethods()) {
          if (!Modifier.isStatic(m.getModifiers())) continue;
          if (!m.getName().toLowerCase().contains("start")) continue;
          m.setAccessible(true);

          Object ret = tryInvokeFlexibleStatic(m,
              new Object[]{ plugin, stageTrader, stageLlamas, stageCenter },
              new Object[]{ plugin, stageTrader, stageCenter },
              new Object[]{ plugin, stageTrader, stageLlamas },
              new Object[]{ plugin, stageTrader }
          );

          if (ret instanceof BukkitTask bt) {
            movingSafetyZoneHandle = bt;
          }

          plugin.getLogger().info("✅ MovingSafetyZone started via " + cn + "." + m.getName());
          return;
        }

        // 2) BukkitRunnable系（newしてrunTaskTimerするタイプ）
        Constructor<?>[] ctors = cls.getDeclaredConstructors();
        for (Constructor<?> c : ctors) {
          c.setAccessible(true);
          Object inst = tryNewFlexible(c,
              new Object[]{ plugin, stageTrader, stageLlamas, stageCenter },
              new Object[]{ plugin, stageTrader, stageCenter },
              new Object[]{ plugin, stageTrader, stageLlamas },
              new Object[]{ plugin, stageTrader }
          );
          if (inst == null) continue;

          Method runTaskTimer = findMethodByName(inst.getClass(), "runTaskTimer", 3);
          if (runTaskTimer != null) {
            runTaskTimer.setAccessible(true);
            Object ret = runTaskTimer.invoke(inst, plugin, 0L, 1L);
            if (ret instanceof BukkitTask bt) movingSafetyZoneHandle = bt;
            plugin.getLogger().info("✅ MovingSafetyZone started via new " + cn + "(...)");
            return;
          }
        }
      } catch (Throwable ignored) {}
    }

    plugin.getLogger().warning("⚠ MovingSafetyZone class not found or could not start (kept as-is).");
  }
  private void stopMovingSafetyZoneIfRunning() {
    // ✅ 修正: stopMovingSafetyZoneTask() と同じ処理に統一
    stopMovingSafetyZoneTask();
  }


  // =======================================================
  // ✅ UFO 開始/終了（reflectionで “実装そのまま” を温存）
  // =======================================================
  private void startUfoIfAvailable(Player player, Location stageCenter) {
    Object target = this.ufo;
    if (target == null) {
      target = tryCallGetter(plugin, "getUfo");
      if (target == null) target = tryCallGetter(plugin, "getUfoController");
    }
    if (target == null) {
      plugin.getLogger().warning("⚠ UFO controller not found. (kept as-is)");
      return;
    }

    String[] names = {
        "startUfoArrivalWithTruePolling",
        "startArrival",
        "arrival",
        "start",
        "begin"
    };

    for (String n : names) {
      try {
        Method m = findBestMethod(target.getClass(), n,
            new Class[]{ Player.class, Location.class },
            new Class[]{ Location.class },
            new Class[]{ Player.class }
        );
        if (m == null) continue;
        m.setAccessible(true);
        invokeWithBestArgs(m, target, player, stageCenter);
        plugin.getLogger().info("✅ UFO started via " + target.getClass().getSimpleName() + "." + m.getName());
        return;
      } catch (Throwable ignored) {}
    }

    plugin.getLogger().warning("⚠ UFO controller found but start method was not callable (kept as-is).");
  }

  private void stopUfoIfAvailable() {
    Object target = this.ufo;
    if (target == null) {
      target = tryCallGetter(plugin, "getUfo");
      if (target == null) target = tryCallGetter(plugin, "getUfoController");
    }
    if (target == null) return;

    String[] names = {
        "stop",
        "end",
        "departure",
        "startDeparture",
        "unbind",
        "clearBind",
        "cleanup"
    };
    for (String n : names) {
      try {
        Method m = findBestMethod(target.getClass(), n,
            new Class[]{},
            new Class[]{ Location.class }
        );
        if (m == null) continue;
        m.setAccessible(true);
        if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Location.class) {
          m.invoke(target, (Object) null);
        } else {
          m.invoke(target);
        }
        plugin.getLogger().info("✅ UFO stopped via " + target.getClass().getSimpleName() + "." + m.getName());
        return;
      } catch (Throwable ignored) {}
    }
  }

  // ✅ UFO Departure を試みる（成功=true / UFO無しor busy=false）
  private boolean tryStartUfoDeparture() {
    Object target = this.ufo;
    if (target == null) {
      target = tryCallGetter(plugin, "getUfo");
      if (target == null) target = tryCallGetter(plugin, "getUfoController");
    }
    if (target == null) return false;

    try {
      Method m = findBestMethod(target.getClass(), "tryStartDeparture",
          new Class[]{});
      if (m != null) {
        m.setAccessible(true);
        Object ret = m.invoke(target);
        if (ret instanceof Boolean b && b) {
          plugin.getLogger().info("✅ UFO Departure started");
          return true;
        }
      }
    } catch (Throwable ignored) {}

    return false;
  }

  // =======================================================
  // ✅ reflection ユーティリティ
  // =======================================================
  private static Method findMethodByName(Class<?> cls, String name, int paramCount) {
    for (Method m : cls.getMethods()) {
      if (!m.getName().equals(name)) continue;
      if (m.getParameterCount() != paramCount) continue;
      return m;
    }
    for (Method m : cls.getDeclaredMethods()) {
      if (!m.getName().equals(name)) continue;
      if (m.getParameterCount() != paramCount) continue;
      return m;
    }
    return null;
  }

  private static Method findBestMethod(Class<?> cls, String name, Class<?>[]... candidates) {
    for (Class<?>[] sig : candidates) {
      try { return cls.getMethod(name, sig); } catch (Throwable ignored) {}
      try { return cls.getDeclaredMethod(name, sig); } catch (Throwable ignored) {}
    }
    return null;
  }

  private static Object invokeWithBestArgs(Method m, Object target, Player p, Location c) throws Exception {
    int n = m.getParameterCount();
    if (n == 2) return m.invoke(target, p, c);
    if (n == 1) {
      Class<?> t = m.getParameterTypes()[0];
      if (t == Player.class) return m.invoke(target, p);
      if (t == Location.class) return m.invoke(target, c);
    }
    return m.invoke(target);
  }

  private static Object tryCallGetter(Object obj, String name) {
    try {
      Method m = obj.getClass().getMethod(name);
      m.setAccessible(true);
      return m.invoke(obj);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Object tryInvokeFlexibleStatic(Method m, Object[]... argsList) throws Exception {
    for (Object[] args : argsList) {
      try {
        if (m.getParameterCount() != args.length) continue;
        return m.invoke(null, args);
      } catch (Throwable ignored) {}
    }
    throw new IllegalArgumentException("no matching args");
  }

  private static Object tryNewFlexible(Constructor<?> c, Object[]... argsList) {
    for (Object[] args : argsList) {
      try {
        if (c.getParameterCount() != args.length) continue;
        return c.newInstance(args);
      } catch (Throwable ignored) {}
    }
    return null;
  }

  // =======================================================
// ✅ 追加：stageTrader が null/invalid の時に「近くの Treasure Shop 商人」を拾って復旧する保険
//  - 既存ロジックは一切変えず、startMovingSafetyZoneTask() の直前に呼ぶだけ
// =======================================================
  private void ensureStageTraderIfMissing() {
    try {
      if (stageTrader != null && stageTrader.isValid()) return;

      // どのワールドから探す？：オンラインプレイヤーのワールドを優先
      for (Player p : Bukkit.getOnlinePlayers()) {
        if (p == null || !p.isOnline()) continue;
        World w = p.getWorld();
        if (w == null) continue;

        // プレイヤー周辺を軽く探す（半径64）
        for (org.bukkit.entity.Entity e : w.getNearbyEntities(p.getLocation(), 64, 32, 64)) {
          if (!(e instanceof WanderingTrader wt)) continue;
          if (!wt.isValid()) continue;

          // Treasure Shop の商人っぽい条件（どれか一致でOK）
          String nm = wt.getCustomName();
          boolean nameOk = (nm != null && ChatColor.stripColor(nm).toLowerCase().contains("treasure shop"));
          boolean metaOk = wt.hasMetadata(MovingSafetyZoneTask.CARRIER_META);

          if (nameOk || metaOk) {
            stageTrader = wt;
            plugin.getLogger().info("[MSZ][GUARD] stageTrader recovered: "
                + wt.getWorld().getName() + " "
                + wt.getLocation().getBlockX() + "," + wt.getLocation().getBlockY() + "," + wt.getLocation().getBlockZ());
            return;
          }
        }
      }
    } catch (Throwable t) {
      plugin.getLogger().warning("[MSZ][GUARD] ensureStageTraderIfMissing failed: " + t);
    }
  }

  // =======================================================
  // ✅ MovingSafetyZoneTask 起動（置換1）
  // =======================================================
  private void startMovingSafetyZoneTask() {
    // まず完全停止（残骸も戻す）
    stopMovingSafetyZoneTask();

    // ✅ 追加：商人が null/invalid なら拾ってくる保険
    ensureStageTraderIfMissing();

    // ✅ 必須：この時点で商人がいなければ、MSZは起動しない（床Yも取れない）
    if (stageTrader == null || !stageTrader.isValid()) {
      plugin.getLogger().warning("[MSZ] stageTrader is null/invalid -> skip start");
      return;
    }

    // ✅ ステージ床Yは「商人の足元 -1」で固定（あなたの仕様通り）
    final int floorY = stageTrader.getLocation().getBlockY() - 1;

    plugin.getLogger().info("[MSZ][Y] traderBlockY=" + stageTrader.getLocation().getBlockY()
        + " fixedFloorY=" + floorY
        + " world=" + stageTrader.getWorld().getName()
    );

    try {
      // ✅ TreasureProvider（宝箱2m演出の“生存確認ログ”付き）
      MovingSafetyZoneTask.TreasureProvider treasureProvider = () -> {
        try {
          TreasureChestManager m = plugin.getTreasureChestManager();
          if (m == null) {
            plugin.getLogger().warning("[MSZ][TREASURE] TreasureChestManager is NULL");
            return java.util.Collections.emptyList();
          }

          // ★ getTreasureLocations() は Collection を返す想定なので Collection で受ける
          java.util.Collection<org.bukkit.Location> col = m.getTreasureLocations();

          // ★ MovingSafetyZoneTask が欲しいのは List なので List に変換して返す
          java.util.List<org.bukkit.Location> list =
              (col == null) ? java.util.Collections.emptyList() : new java.util.ArrayList<>(col);

          plugin.getLogger().fine("[MSZ][TREASURE] count=" + list.size()
              + " instance=" + System.identityHashCode(m));

          return list;
        } catch (Throwable t) {
          plugin.getLogger().warning("[MSZ][TREASURE] provider error: " + t);
          return java.util.Collections.emptyList();
        }
      };

      // ✅ task は1回だけ生成
      MovingSafetyZoneTask task = new MovingSafetyZoneTask(plugin, treasureProvider, floorY);
      this.movingSafetyZoneRunnable = task;

      // ✅ スケジュールは必ずこの1箇所で実行（period=2固定）
      scheduleMovingSafetyZone(task, 2L);

      // ✅ 追加：1tick後に「本当に動いてるか」確認（最短で原因切り分けできる）
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        try {
          boolean handleOk = (movingSafetyZoneHandle != null);
          boolean runnableOk = (movingSafetyZoneRunnable != null);
          plugin.getLogger().info("[MSZ][CHECK] handle=" + (handleOk ? movingSafetyZoneHandle.getTaskId() : "null")
              + " runnable=" + (runnableOk ? "ok" : "null")
              + " trader=" + (stageTrader != null && stageTrader.isValid())
              + " online=" + Bukkit.getOnlinePlayers().size());
        } catch (Throwable ignored) {}
      }, 1L);

      plugin.getLogger().info("[MSZ] started period=2 taskId="
          + (movingSafetyZoneHandle != null ? movingSafetyZoneHandle.getTaskId() : -1));

    } catch (Throwable t) {
      plugin.getLogger().severe("[MSZ] start failed: " + t);
      t.printStackTrace();
    }
  }

  // =======================================================
  // ✅ MSZスケジュール（置換2）
  // =======================================================
  private void scheduleMovingSafetyZone(org.bukkit.scheduler.BukkitRunnable r, long periodTicks) {
    if (r == null) return;

    // ✅ ここは「既存が残ってたら止めて作り直す」が正解（取り残し対策）
    if (movingSafetyZoneHandle != null) {
      try { movingSafetyZoneHandle.cancel(); } catch (Throwable ignored) {}
      movingSafetyZoneHandle = null;
    }

    movingSafetyZoneHandle = r.runTaskTimer(plugin, 0L, periodTicks);
    plugin.getLogger().info("[MSZ] scheduled period=" + periodTicks
        + " taskId=" + movingSafetyZoneHandle.getTaskId());
  }

  // =======================================================
  // ✅ MSZ停止（置換3）
  // =======================================================
  private void stopMovingSafetyZoneTask() {
    try {
      // ✅ 先に「Runnable.cancel()」を呼ぶ（restoreAllFloors を確実に走らせる）
      if (movingSafetyZoneRunnable != null) {
        try { movingSafetyZoneRunnable.cancel(); } catch (Throwable ignored) {}
      }

      // ✅ その後にスケジュール停止（残っててもOK）
      if (movingSafetyZoneHandle != null) {
        try { movingSafetyZoneHandle.cancel(); } catch (Throwable ignored) {}
      }

    } finally {
      movingSafetyZoneHandle = null;
      movingSafetyZoneRunnable = null;
      plugin.getLogger().info("[MSZ] stopped");
    }
  }

  // ✅ MovingSafetyZoneTask を“直起動”する（MovingSafetyZone.class 不要）
  // ❌ この経路は period=1 や床Y未固定など事故要因になるので完全に無効化する
  private void startMovingSafetyZoneTaskDirect(org.bukkit.Location stageCenter) {
    plugin.getLogger().info("[MSZ] startMovingSafetyZoneTaskDirect is disabled. Use startMovingSafetyZoneTask() only.");
    // 何もしない（封印）
  }

}
