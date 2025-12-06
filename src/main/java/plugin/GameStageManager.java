package plugin;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;   // ★ 行商人
import org.bukkit.entity.TraderLlama;      // ★ トレーダーラマ
import org.bukkit.event.EventHandler;      // ★ 追加
import org.bukkit.event.Listener;          // ★ 追加
import org.bukkit.event.inventory.InventoryClickEvent; // ★ 追加
import org.bukkit.event.inventory.InventoryType;       // ★ 追加
import org.bukkit.inventory.ItemStack;                 // ★ 追加
import org.bukkit.inventory.Merchant;                  // ★ 追加
import org.bukkit.inventory.MerchantInventory;         // ★ 追加
import org.bukkit.inventory.MerchantRecipe;            // ★ 追加
import org.bukkit.scheduler.BukkitRunnable;

public class GameStageManager implements Listener {

  private final TreasureRunMultiChestPlugin plugin;

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

  // =======================================================
  // ★ ShopDebug 出力（②：クラス内に1個追加 / メンバーとして）
  // =======================================================
  private void shopDebug(String msg) {
    plugin.getLogger().info("[ShopDebug] " + msg);
  }

  public GameStageManager(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
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
    // まず従来の海探索
    Location base = findNearbySeaLocation(player.getLocation(), 48);

    // バックアップ海探索（より広く探す）
    if (base == null) {
      base = forceFindOcean(player.getLocation());
      if (base != null) {
        plugin.getLogger().info("🌊 Backup 海探索で海を検出しました");
      }
    }

    // それでも見つからない場合は元の場所
    if (base == null) base = player.getLocation();

    Location stageCenter = base.clone();
    World w = base.getWorld();

    // 海なら水面+1 に調整（base 自体は海探索で見つけた地点）
    int seaY = w.getHighestBlockYAt(base);
    if (w.getBlockAt(base.getBlockX(), seaY, base.getBlockZ()).getType() == Material.WATER) {
      seaY += 1;
    }
    stageCenter.setY(seaY);

    // ✅ 追加：このステージ中心を記憶（後で難易度ブロックをスイープ掃除できる）
    rememberStageCenter(stageCenter);

    // ✨ ネオン床
    buildNeonFloor(stageCenter);
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
  // 海探索（元のロジック＋バックアップ版）
  // =======================================================
  private Location findNearbySeaLocation(Location origin, int radius) {
    World w = origin.getWorld();

    for (int dx = -radius; dx <= radius; dx += 8) {
      for (int dz = -radius; dz <= radius; dz += 8) {
        Location p = origin.clone().add(dx, 0, dz);
        int py = w.getHighestBlockYAt(p);

        for (int yy = py; yy >= py - 6 && yy >= 50; yy--) {
          Material m = w.getBlockAt(p.getBlockX(), yy, p.getBlockZ()).getType();
          if (m == Material.WATER) {
            return new Location(w, p.getBlockX(), yy, p.getBlockZ()).add(-4, 0, -4);
          }
        }
      }
    }
    return null;
  }

  /** 海を絶対に見つけるための広域スキャン（元のまま保持） */
  private Location forceFindOcean(Location origin) {
    World w = origin.getWorld();

    // 半径を徐々に拡大して海を探索（最大256）
    for (int r = 48; r <= 256; r += 16) {
      for (int dx = -r; dx <= r; dx += 8) {
        for (int dz = -r; dz <= r; dz += 8) {

          Location p = origin.clone().add(dx, 0, dz);
          int py = w.getHighestBlockYAt(p);

          // 水面〜その少し下までを探索
          for (int yy = py; yy >= py - 10 && yy >= 40; yy--) {
            Material m = w.getBlockAt(p.getBlockX(), yy, p.getBlockZ()).getType();
            if (m == Material.WATER) {
              return new Location(w, p.getBlockX(), yy, p.getBlockZ()).add(-4, 0, -4);
            }
          }
        }
      }
    }

    return null;
  }

  // =======================================================
  // ネオン床づくり（光る床＋色ガラス）※元のまま
  // =======================================================
  private void buildNeonFloor(Location center) {
    World w = center.getWorld();
    int cx = center.getBlockX();
    int cz = center.getBlockZ();
    int y = center.getBlockY();

    for (int dx = -2; dx <= 2; dx++) {
      for (int dz = -2; dz <= 2; dz++) {
        Block top = w.getBlockAt(cx + dx, y, cz + dz);
        Block under = w.getBlockAt(cx + dx, y - 1, cz + dz);

        // ✨ 真ん中の十字だけシーランタン
        if (dx == 0 || dz == 0) {
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

    for (int dx = -2; dx <= 2; dx++) {
      for (int dz = -2; dz <= 2; dz++) {
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
    Block easyBlock   = w.getBlockAt(cx - 1, y, cz + 3);
    Block normalBlock = w.getBlockAt(cx,     y, cz + 3);
    Block hardBlock   = w.getBlockAt(cx + 1, y, cz + 3);

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

    // 行商人
    if (stageTrader != null) {
      try {
        if (!stageTrader.isDead()) {
          stageTrader.remove();
          removed++;
        }
      } catch (Exception ignored) {}
      stageTrader = null;
    }

    // ラマ
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
  // =======================================================
  public void spawnTraderAndLlamas(Location center) {
    if (center == null) return;
    World w = center.getWorld();
    if (w == null) return;

    Location traderLoc = center.clone().add(0.5, 1.1, 0.5);

    WanderingTrader trader = w.spawn(traderLoc, WanderingTrader.class, t -> {
      t.setAI(true);
      t.setPersistent(true);
      t.setGlowing(true);
      t.setCustomName(ChatColor.GOLD + "" + ChatColor.BOLD + "Treasure Shop");
      t.setCustomNameVisible(true);
    });

    this.stageTrader = trader;
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

      stageLlamas.add(llama);
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
          if (stageTrader.getLocation().distanceSquared(centerLoc) > 4.0) {
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

    // 取引①：特製エメラルド 5 → 金リンゴ 1
    // CraftSpecialEmeraldCommand と完全一致させるため、表示名も「§6特製エメラルド」に揃える
    ItemStack specialEmerald5 = plugin.getItemFactory().createTreasureEmerald(5);
    org.bukkit.inventory.meta.ItemMeta m = specialEmerald5.getItemMeta();
    if (m != null) {
      m.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6特製エメラルド"));
      specialEmerald5.setItemMeta(m);
    }

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

    if (!plainTitle.toLowerCase().contains("treasure shop")) {
      shopDebug("RETURN: not Treasure Shop title. plainTitle=" + plainTitle);
      return;
    }

    // 結果アイテムが金リンゴか
    ItemStack current = event.getCurrentItem();
    if (current == null) {
      shopDebug("RETURN: current item is null");
      return;
    }
    if (current.getType() == Material.AIR) {
      shopDebug("RETURN: current item is AIR");
      return;
    }
    if (current.getType() != Material.GOLDEN_APPLE) {
      shopDebug("RETURN: current item is not GOLDEN_APPLE. type=" + current.getType());
      return;
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
      return;
    }

    shopDebug("OK: passed all checks -> scheduling effect with runTaskLater");

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
      player.sendTitle(
          ChatColor.GOLD + "Trade complete!",
          ChatColor.AQUA + "A hidden power awakens…",
          5,   // fadeIn (ticks)
          40,  // stay   (ticks)
          10   // fadeOut(ticks)
      );
      player.sendMessage(ChatColor.AQUA + "??? " + ChatColor.GOLD + "Treasure Shop の秘められた力を感じた…");

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
}