package plugin;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TraderLlama;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MovingSafetyZoneTask
 *
 * ✅ 商人＋ラマ2頭の「直下3マス」だけレモンイエロー（YELLOW_STAINED_GLASS）
 * ✅ 移動したら即座に元に戻す（塗り残り無し）
 * ✅ ステージ床だけ対象（fixedFloorYで固定）
 *
 * ✅ 残光：updateTrailAt / renderAndCleanupTrails
 * ✅ 霧：spawnColdFog
 * ✅ 冷音：playColdSoundWithCooldown
 * ✅ クラック：spawnCracksAroundPlayer（外）/ spawnSoftCracksInSafeZone（内）
 * ✅ 宝箱2m：spawnGlassShardDispersion / applyRefractionWobble / spawnGeometricCracksNearTreasure(60/45)
 */
public class MovingSafetyZoneTask extends BukkitRunnable {

  public static final String CARRIER_META = "treasurerun_msz_carrier";

  public interface TreasureProvider {
    List<Location> getTreasureLocations();
  }

  private final JavaPlugin plugin;
  private final TreasureProvider treasureProvider;

  // ✅ ステージ床Y（これ以外のYは一切触らない）
  private final int fixedFloorY;

  // ✅ タスクtick
  private long tick = 0;

  // ✅ キャリア（商人＋ラマ2頭）を追跡
  private final Map<UUID, Location> lastCarrierFloor = new HashMap<>();

  // ✅ 変更した床の「元ブロック」保持（確実復元）
  private final Map<String, Material> originalFloor = new HashMap<>();

  // ✅ 残光（床）: key -> expireTick
  private final Map<String, Long> trailExpireTick = new HashMap<>();

  // ✅ 冷音クールダウン
  private final Map<UUID, Long> coldSoundNextAllowedTick = new HashMap<>();

  // ==========================
  // チューニング（まず固定値でOK）
  // ==========================
  private static final long LOG_EVERY_TICKS = 40;     // 2秒に1回
  private static final long TRAIL_LIFETIME_TICKS = 40; // 2秒残光
  private static final long COLD_SOUND_COOLDOWN = 60;  // 3秒（period=2前提なら実時間は少し伸びる）
  private static final double SAFEZONE_RADIUS = 3.0;   // 近い＝安全
  private static final double COLD_START_DISTANCE = 12.0; // 離れると冷える
  private static final double TREASURE_NEAR_METERS = 2.0; // 宝箱2m以内

  // ==========================
  // ✅ 見た目：リング/軌跡 粒子（追加）
  // ==========================
  private static final int RING_POINTS = 18;     // リングの点数（軽め）
  private static final double RING_RADIUS = 3.0; // SAFEZONE_RADIUS と揃えると良い
  private static final Particle.DustOptions LEMON_DUST =
      new Particle.DustOptions(Color.fromRGB(255, 238, 90), 1.35f); // レモン

  public MovingSafetyZoneTask(JavaPlugin plugin, TreasureProvider treasureProvider, int fixedFloorY) {
    this.plugin = plugin;
    this.treasureProvider = treasureProvider;
    this.fixedFloorY = fixedFloorY;
  }

  // ==========================
  // run
  // ==========================
  @Override
  public void run() {
    tick++;

    try {

      // ✅ 追加：run() が回っているかのハートビート（2秒に1回）
      if (tick % LOG_EVERY_TICKS == 0) plugin.getLogger().info("[MSZ] heartbeat tick=" + tick + " fixedY=" + fixedFloorY);

      // ✅ プレイヤーがいなければ何もしない（ただし復元はcancel時）
      Collection<? extends Player> players = Bukkit.getOnlinePlayers();
      if (players == null || players.isEmpty()) {
        return;
      }

      // ✅ キャリアを集める（metadataで限定）
      List<Entity> carriers = findCarriers(players);
      if (carriers.isEmpty()) {
        // キャリアがいない＝何もできない。念のため床だけ復元し続ける
        if (tick % LOG_EVERY_TICKS == 0) {
          plugin.getLogger().warning("[MSZ] carriers=0 (metadata=" + CARRIER_META + ") fixedY=" + fixedFloorY);
        }
        renderAndCleanupTrails(); // 残光掃除だけは回す
        return;
      }

      // ✅ デバッグログ（スパム防止）
      if (tick % LOG_EVERY_TICKS == 0) {
        Location any = carriers.get(0).getLocation();
        plugin.getLogger().info("[MSZ] tick=" + tick
            + " players=" + players.size()
            + " carriers=" + carriers.size()
            + " world=" + any.getWorld().getName()
            + " fixedY=" + fixedFloorY
            + " treasures=" + safeTreasureList(any.getWorld()).size()
        );
      }

      renderLemonRing(carriers); // ✅ 追加：商人/ラマの周囲にリング粒子

      // ✅ 1) キャリア床 3マスだけ黄色にする（移動したら即戻す）
      updateCarrierFloors(carriers);

      // ✅ 2) 残光の描画・掃除
      renderAndCleanupTrails();

      // ✅ 3) プレイヤー演出（霧/音/クラック/宝2m）
      for (Player p : players) {
        if (p == null || !p.isOnline()) continue;

        World pw = p.getWorld();
        Location pl = p.getLocation();

        // ワールド一致（固定world禁止）
        List<Entity> carriersInWorld = filterByWorld(carriers, pw);
        if (carriersInWorld.isEmpty()) continue;

        double minDist = minDistance(pl, carriersInWorld);
        boolean inSafe = (minDist <= SAFEZONE_RADIUS);
        boolean cold = (!inSafe && minDist >= COLD_START_DISTANCE);

        if (cold) {
          spawnColdFog(p);
          playColdSoundWithCooldown(p);
        }

        // クラック：内は弱く、外は強く
        if (inSafe) {
          spawnSoftCracksInSafeZone(p);
        } else {
          spawnCracksAroundPlayer(p);
        }

        // ✅ DEBUG: 最寄り宝箱距離（1秒に1回だけ）
        if (tick % LOG_EVERY_TICKS == 0) {
          World w = pl.getWorld();
          double nearest = 9999;
          Location nearestLoc = null;

          for (Location tl : safeTreasureList(w)) {
            double d = tl.distance(pl);
            if (d < nearest) { nearest = d; nearestLoc = tl; }
          }

          plugin.getLogger().info("[MSZ][DIST] p=" + p.getName()
              + " pLoc=" + pl.getBlockX() + "," + pl.getBlockY() + "," + pl.getBlockZ()
              + " nearest=" + String.format("%.2f", nearest)
              + " chest=" + (nearestLoc == null ? "null" : nearestLoc.getBlockX() + "," + nearestLoc.getBlockY() + "," + nearestLoc.getBlockZ())
              + " world=" + w.getName());
        }

        // 宝箱2m
        Location nearTreasure = findNearestTreasureWithin(pl, TREASURE_NEAR_METERS);
        if (nearTreasure != null) {
          spawnGlassShardDispersion(p, nearTreasure);
          applyRefractionWobble(p);

          // ✅ DEBUG：宝箱演出が発火したことをログ
          if (tick % LOG_EVERY_TICKS == 0) {
            double d = nearTreasure.distance(pl);
            plugin.getLogger().info("[MSZ][FX] fire shards+sound+geo tick=" + tick
                + " p=" + p.getName()
                + " d=" + String.format("%.2f", d)
                + " chest=" + nearTreasure.getBlockX() + "," + nearTreasure.getBlockY() + "," + nearTreasure.getBlockZ());
          }
          spawnGeometricCracksNearTreasure(p, nearTreasure, 60);
          spawnGeometricCracksNearTreasure(p, nearTreasure, 45);
        }
      }

    } catch (Throwable t) {
      // ✅ 例外握り潰し禁止：落とさずにログ
      plugin.getLogger().severe("[MSZ] run() ERROR: " + t);
      t.printStackTrace();
    }
  }

  // ==========================
  // cancel -> 必ず床復元
  // ==========================
  @Override
  public synchronized void cancel() throws IllegalStateException {
    super.cancel();
    restoreAllFloors();
  }

  // ==========================
  // キャリア検出
  // ==========================
  private List<Entity> findCarriers(Collection<? extends Player> players) {
    // プレイヤーがいるワールドの周辺から探す（確実に見つかる方式）
    List<Entity> result = new ArrayList<>();

    // ワールド毎に1回だけ検索したいので set
    Set<World> worlds = new HashSet<>();
    for (Player p : players) if (p != null && p.getWorld() != null) worlds.add(p.getWorld());

    for (World w : worlds) {
      // 半径は広め（ステージ内で十分）
      for (Entity e : w.getEntities()) {
        if (e == null) continue;
        if (!(e instanceof WanderingTrader) && !(e instanceof TraderLlama)) continue;
        if (!hasCarrierMeta(e)) continue;
        result.add(e);
      }
    }
    return result;
  }

  private boolean hasCarrierMeta(Entity e) {
    try {
      if (!e.hasMetadata(CARRIER_META)) return false;
      for (MetadataValue v : e.getMetadata(CARRIER_META)) {
        if (v != null && v.asBoolean()) return true;
      }
      return true; // metadataが付いているだけでもOK
    } catch (Throwable ignored) {
      return false;
    }
  }

  private List<Entity> filterByWorld(List<Entity> carriers, World w) {
    List<Entity> r = new ArrayList<>();
    for (Entity e : carriers) {
      if (e != null && e.getWorld() == w) r.add(e);
    }
    return r;
  }

  private double minDistance(Location pl, List<Entity> carriers) {
    double best = Double.MAX_VALUE;
    for (Entity e : carriers) {
      Location el = e.getLocation();
      double d = el.distance(pl);
      if (d < best) best = d;
    }
    return best;
  }

  // ==========================
  // 足元3マスの黄色床（即復元）
  // ==========================
  private void updateCarrierFloors(List<Entity> carriers) {
    // ✅ 今回必要な床キー
    Set<String> needed = new HashSet<>();

    for (Entity e : carriers) {
      if (e == null || !e.isValid()) continue;

      Location floor = floorLocOfCarrier(e);
      if (floor == null) continue;

      String key = blockKey(floor);
      needed.add(key);

      // 移動検知：前回と違うなら残光に追加
      Location last = lastCarrierFloor.get(e.getUniqueId());
      if (last != null && !sameBlock(last, floor)) {
        updateTrailAt(last);
      }
      lastCarrierFloor.put(e.getUniqueId(), floor);

      // 今必要な床を黄色に
      setLemonFloor(floor);
    }

    // ✅ “必要じゃなくなった床”は即復元（塗り残りゼロの本体）
    // originalFloor に入ってるもののうち、needed にない床で、かつ trail として保持してないものを戻す
    List<String> keys = new ArrayList<>(originalFloor.keySet());
    for (String k : keys) {
      if (needed.contains(k)) continue;
      if (trailExpireTick.containsKey(k)) continue; // 残光として保持中
      restoreFloorByKey(k);
    }
  }

  private Location floorLocOfCarrier(Entity e) {
    Location loc = e.getLocation();
    if (loc == null || loc.getWorld() == null) return null;

    int x = loc.getBlockX();
    int z = loc.getBlockZ();

    // ✅ fixedFloorY を絶対に使う（ステージ床だけ）
    int y = fixedFloorY;

    return new Location(loc.getWorld(), x, y, z);
  }

  private void setLemonFloor(Location floor) {
    Block b = floor.getWorld().getBlockAt(floor.getBlockX(), floor.getBlockY(), floor.getBlockZ());

    // fixedFloorY以外は触らない（保険）
    if (b.getY() != fixedFloorY) return;

    String k = blockKey(floor);

    // 初回だけ元を記録
    originalFloor.putIfAbsent(k, b.getType());

    // すでに黄色なら触らない
    if (b.getType() == Material.YELLOW_STAINED_GLASS) return;

    b.setType(Material.YELLOW_STAINED_GLASS, false);
  }

  private void restoreFloorByKey(String key) {
    try {
      String[] p = key.split(":");
      if (p.length != 2) return;
      World w = Bukkit.getWorld(p[0]);
      if (w == null) return;

      String[] xyz = p[1].split(",");
      if (xyz.length != 3) return;

      int x = Integer.parseInt(xyz[0]);
      int y = Integer.parseInt(xyz[1]);
      int z = Integer.parseInt(xyz[2]);

      // ✅ yがfixed以外なら触らない
      if (y != fixedFloorY) {
        originalFloor.remove(key);
        return;
      }

      Material restore = originalFloor.get(key);
      if (restore == null) {
        originalFloor.remove(key);
        return;
      }

      Block b = w.getBlockAt(x, y, z);
      // 黄色以外に変わってたら尊重して触らない（事故防止）
      if (b.getType() == Material.YELLOW_STAINED_GLASS) {
        b.setType(restore, false);
      }

      originalFloor.remove(key);
    } catch (Throwable ignored) {
      originalFloor.remove(key);
    }
  }

  private void restoreAllFloors() {
    // trailを含め全部戻す
    for (String key : new ArrayList<>(originalFloor.keySet())) {
      restoreFloorByKey(key);
    }
    trailExpireTick.clear();
    lastCarrierFloor.clear();
    coldSoundNextAllowedTick.clear();
  }

  private boolean sameBlock(Location a, Location b) {
    return a.getWorld() == b.getWorld()
        && a.getBlockX() == b.getBlockX()
        && a.getBlockY() == b.getBlockY()
        && a.getBlockZ() == b.getBlockZ();
  }

  private String blockKey(Location l) {
    return l.getWorld().getName() + ":" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
  }

  // ==========================
  // 残光（トレイル）
  // ==========================
  private void updateTrailAt(Location floorLoc) {
    if (floorLoc == null || floorLoc.getWorld() == null) return;
    if (floorLoc.getBlockY() != fixedFloorY) return;

    String k = blockKey(floorLoc);

    // 元ブロック記録
    Block b = floorLoc.getWorld().getBlockAt(floorLoc.getBlockX(), floorLoc.getBlockY(), floorLoc.getBlockZ());
    originalFloor.putIfAbsent(k, b.getType());

    // 残光として期限を設定
    trailExpireTick.put(k, tick + TRAIL_LIFETIME_TICKS);

    // 視覚：黄色（残光）
    if (b.getType() != Material.YELLOW_STAINED_GLASS) {
      b.setType(Material.YELLOW_STAINED_GLASS, false);
    }

    spawnTrailParticlesAt(floorLoc); // ✅ 追加：残光地点に粒子
  }

  private void renderAndCleanupTrails() {
    if (trailExpireTick.isEmpty()) return;

    List<String> keys = new ArrayList<>(trailExpireTick.keySet());
    for (String k : keys) {
      spawnTrailParticlesAt(locFromKey(k)); // ✅ 追加：残光中は毎tick少しだけ粒子
      Long exp = trailExpireTick.get(k);
      if (exp == null || tick >= exp) {
        trailExpireTick.remove(k);
        // 期限切れは復元（ただし現在carrier直下の必要床は updateCarrierFloors が保護する）
        restoreFloorByKey(k);
      }
    }
  }

  // ==========================
  // 冷える世界（霧/音）
  // ==========================
  private void spawnColdFog(Player p) {
    World w = p.getWorld();
    Location l = p.getLocation().clone().add(0, 1.0, 0);

    // 霧っぽい：CLOUD + WHITE_ASH 少量
    w.spawnParticle(Particle.CLOUD, l, 12, 0.6, 0.4, 0.6, 0.01);
    w.spawnParticle(Particle.WHITE_ASH, l, 10, 0.8, 0.6, 0.8, 0.0);
  }

  private void playColdSoundWithCooldown(Player p) {
    UUID id = p.getUniqueId();
    long next = coldSoundNextAllowedTick.getOrDefault(id, 0L);
    if (tick < next) return;

    coldSoundNextAllowedTick.put(id, tick + COLD_SOUND_COOLDOWN);

    // 冷音：低めの音
    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.5f);
    p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.2f, 0.3f);
  }

  // ==========================
  // クラック（内/外）
  // ==========================
  private void spawnCracksAroundPlayer(Player p) {
    World w = p.getWorld();
    Location base = p.getLocation().clone().add(0, 0.05, 0);

    // 強いクラック：BLOCK_CRACK（ガラス系）
    BlockData bd = Material.GLASS.createBlockData();
    w.spawnParticle(Particle.BLOCK_CRACK, base, 30, 0.8, 0.1, 0.8, 0.02, bd);

    // 不安：音を少しだけ
    w.playSound(base, Sound.BLOCK_GLASS_STEP, 0.25f, 0.7f);
  }

  private void spawnSoftCracksInSafeZone(Player p) {
    World w = p.getWorld();
    Location base = p.getLocation().clone().add(0, 0.05, 0);

    BlockData bd = Material.GLASS.createBlockData();
    w.spawnParticle(Particle.BLOCK_CRACK, base, 8, 0.5, 0.05, 0.5, 0.01, bd);
  }

  // ==========================
  // 宝箱2m：破片・揺れ・角度固定クラック
  // ==========================
  private void spawnGlassShardDispersion(Player p, Location treasure) {
    World w = p.getWorld();
    Location l = treasure.clone().add(0.5, 1.0, 0.5);

    // ITEM_CRACK（ガラス片）
    ItemStack shard = new ItemStack(Material.GLASS_PANE);
    w.spawnParticle(Particle.ITEM_CRACK, l, 30, 0.6, 0.6, 0.6, 0.15, shard);
    w.playSound(l, Sound.BLOCK_GLASS_BREAK, 0.4f, 1.6f);
  }

  private void applyRefractionWobble(Player p) {
    // 0.5秒だけ軽い揺れ（NAUSEA）
    try {
      p.addPotionEffect(new PotionEffect(PotionEffectType. CONFUSION, 10, 0, true, false, false));
    } catch (Throwable ignored) {}
  }

  private void spawnGeometricCracksNearTreasure(Player p, Location treasure, int degreeStep) {
    World w = p.getWorld();
    Location c = treasure.clone().add(0.5, 0.2, 0.5);

    double rInner = 0.9;
    double rOuter = 1.6;

    BlockData bd = Material.GLASS.createBlockData();

    // 角度固定の放射
    for (int deg = 0; deg < 360; deg += degreeStep) {
      double rad = Math.toRadians(deg);
      double dx = Math.cos(rad);
      double dz = Math.sin(rad);

      // 内側
      Location a = c.clone().add(dx * rInner, 0, dz * rInner);
      w.spawnParticle(Particle.BLOCK_CRACK, a, 6, 0.05, 0.02, 0.05, 0.0, bd);

      // 外側
      Location b = c.clone().add(dx * rOuter, 0, dz * rOuter);
      w.spawnParticle(Particle.BLOCK_CRACK, b, 10, 0.08, 0.02, 0.08, 0.0, bd);
    }
  }

  // ==========================
  // 宝座標系
  // ==========================
  private List<Location> safeTreasureList(World w) {
    try {
      List<Location> list = (treasureProvider != null) ? treasureProvider.getTreasureLocations() : Collections.emptyList();
      if (list == null) return Collections.emptyList();

      List<Location> r = new ArrayList<>();
      for (Location l : list) {
        if (l == null || l.getWorld() == null) continue;
        if (l.getWorld() != w) continue; // ✅ ワールド一致必須
        r.add(l);
      }
      return r;
    } catch (Throwable t) {
      plugin.getLogger().warning("[MSZ] treasureProvider error: " + t);
      return Collections.emptyList();
    }
  }

  private Location findNearestTreasureWithin(Location pl, double meters) {
    World w = pl.getWorld();
    double maxD2 = meters * meters;

    Location best = null;
    double bestD2 = Double.MAX_VALUE;

    for (Location t : safeTreasureList(w)) {
      double d2 = t.distanceSquared(pl);
      if (d2 <= maxD2 && d2 < bestD2) {
        bestD2 = d2;
        best = t;
      }
    }
    return best;
  }

  // ==========================
  // ✅ 粒子リング（追加）
  // ==========================
  private void renderLemonRing(List<Entity> carriers) {
    for (Entity e : carriers) {
      if (e == null || !e.isValid()) continue;
      World w = e.getWorld();
      if (w == null) continue;

      Location base = e.getLocation();
      // リングの高さ（地面から少し上、見えやすい）
      double y = base.getY() + 0.25;

      for (int i = 0; i < RING_POINTS; i++) {
        double ang = (2.0 * Math.PI) * i / RING_POINTS;
        double x = base.getX() + Math.cos(ang) * RING_RADIUS;
        double z = base.getZ() + Math.sin(ang) * RING_RADIUS;

        Location p = new Location(w, x, y, z);
        w.spawnParticle(Particle.REDSTONE, p, 1, 0, 0, 0, 0, LEMON_DUST);
        // ちょいキラ
        if (i % 6 == 0) {
          w.spawnParticle(Particle.END_ROD, p, 1, 0.02, 0.02, 0.02, 0.0);
        }
      }
    }
  }

  // ==========================
  // ✅ 残光（トレイル）粒子（追加）
  // ==========================
  private void spawnTrailParticlesAt(Location floorLoc) {
    if (floorLoc == null || floorLoc.getWorld() == null) return;
    if (floorLoc.getBlockY() != fixedFloorY) return;

    World w = floorLoc.getWorld();

    // 足元より少し上（床の上に見える）
    Location p = floorLoc.clone().add(0.5, 0.20, 0.5);

    w.spawnParticle(Particle.REDSTONE, p, 2, 0.18, 0.02, 0.18, 0, LEMON_DUST);
    if ((tick % 4) == 0) {
      w.spawnParticle(Particle.END_ROD, p, 1, 0.08, 0.03, 0.08, 0.0);
    }
  }

  // key("world:x,y,z") -> Location（追加）
  private Location locFromKey(String key) {
    try {
      String[] p = key.split(":");
      if (p.length != 2) return null;
      World w = Bukkit.getWorld(p[0]);
      if (w == null) return null;

      String[] xyz = p[1].split(",");
      if (xyz.length != 3) return null;

      int x = Integer.parseInt(xyz[0]);
      int y = Integer.parseInt(xyz[1]);
      int z = Integer.parseInt(xyz[2]);

      return new Location(w, x, y, z);
    } catch (Exception e) {
      return null;
    }
  }
}
