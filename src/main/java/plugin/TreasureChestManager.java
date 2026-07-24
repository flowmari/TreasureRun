package plugin;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * TreasureRunMultiChestPlugin 専用のチェスト管理クラス。
 * - チェストのスポーン（座標確定・ブロック置換）
 * - 中身の詰め込み（treasurePool からランダム）
 * - 配置したチェストの追跡（isOurChest 判定用）
 * - 全削除（removeAllChests）
 */
public class TreasureChestManager {

  private final TreasureRunMultiChestPlugin plugin;
  private final Map<String, Integer> treasureChestCounts;
  private final List<Material> treasurePool;
  private final int chestSpawnRadius;

  /** 配置したチェストの位置を追跡（判定用：ブロック座標） */
  private final Set<BlockKey> placedChests = new HashSet<>();

  /**
   * ✅ 追加：設置した宝箱の Location を追跡（近接サウンド用）
   * ※ toBlockLocation() を入れて「ブロック座標だけ」で一致するようにする
   */
  private final Set<Location> chestLocations = new HashSet<>();

  /** Original block data for every block changed during chest placement. */
  private final Map<BlockKey, org.bukkit.block.data.BlockData> originalBlockData =
      new LinkedHashMap<>();

  // ✅ 追加：再起動/リロード後に追跡を復元するためのタグ
  // chestのTileState(PDC)にこれを刻む
  private final NamespacedKey TREASURE_CHEST_TAG;

  public TreasureChestManager(TreasureRunMultiChestPlugin plugin,
      Map<String, Integer> treasureChestCounts,
      List<Material> treasurePool,
      int chestSpawnRadius) {
    this.plugin = plugin;
    this.treasureChestCounts = treasureChestCounts;
    this.treasurePool = treasurePool;
    this.chestSpawnRadius = chestSpawnRadius;

    this.TREASURE_CHEST_TAG = new NamespacedKey(plugin, "treasure_run_chest");
  }

  /**
   * ✅ 追加：近接サウンド側が参照する「宝箱Location一覧」
   */
  public Collection<Location> getChestLocations() {
    return new ArrayList<>(chestLocations);
  }

  // =========================================================
  // ✅ 追加：MovingSafetyZoneTask の TreasureProvider 対応
  // =========================================================

  /**
   * ✅ TreasureProvider が求める名前：getTreasureLocations()
   * - 中身は既存の getChestLocations() をそのまま流用
   */
  public Collection<Location> getTreasureLocations() {
    return getChestLocations();
  }

  /**
   * ✅ あなたの例に合わせた別名（任意だけど便利）
   */
  public Collection<Location> getActiveTreasureLocations() {
    return getChestLocations();
  }

  // ✅ 追加：Spigot互換の「ブロック座標へ丸めたLocation」を作るヘルパー
  private static Location toBlockLocation(Location loc) {
    if (loc == null || loc.getWorld() == null) return loc;
    return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
  }

  // =========================================================
  // ✅ 追加：登録処理を「1箇所」に集約（ここが “確実に登録される” 決定打）
  // =========================================================
  private void registerPlacedChest(Block chestBlock) {
    if (chestBlock == null || chestBlock.getWorld() == null) return;
    if (chestBlock.getType() != Material.CHEST) return;

    // ✅ 判定用（BlockKey）
    placedChests.add(new BlockKey(chestBlock.getLocation()));

    // ✅ 近接演出用（Location）
    chestLocations.add(toBlockLocation(chestBlock.getLocation()));

    // ✅ 再起動後に復元できるよう PDC タグを刻む
    try {
      if (chestBlock.getState() instanceof Chest chest) {
        PersistentDataContainer pdc = chest.getPersistentDataContainer();
        pdc.set(TREASURE_CHEST_TAG, PersistentDataType.BYTE, (byte) 1);
        // ❌ ここでは update しない（中身を入れる前に確定させない）
      }
    } catch (Throwable ignored) {}


    // ✅ デバッグ：ここが必ず増えるポイント
    plugin.getLogger().info("[TreasureChestManager] registerPlacedChest ok"
        + " size=" + chestLocations.size()
        + " at=" + chestBlock.getWorld().getName() + ":" + chestBlock.getX() + "," + chestBlock.getY() + "," + chestBlock.getZ());
  }

  // =========================================================
  // ✅ 追加：再起動/リロード/インスタンス差し替えでも復元できるようにする
  //  - ゲーム開始直後に1回呼べば、MSZのcount=0が消える
  //  - 「タグ付きのチェストだけ」を拾うので誤爆しにくい
  // =========================================================
  public int rescanTreasureChestsAround(Location center, int radius, int yRange) {
    if (center == null || center.getWorld() == null) return 0;

    World w = center.getWorld();
    int cx = center.getBlockX();
    int cy = center.getBlockY();
    int cz = center.getBlockZ();

    int found = 0;

    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        for (int dy = -yRange; dy <= yRange; dy++) {
          Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
          if (b.getType() != Material.CHEST) continue;

          // ✅ タグ付きのみ復元
          try {
            if (b.getState() instanceof Chest chest) {
              PersistentDataContainer pdc = chest.getPersistentDataContainer();
              Byte tag = pdc.get(TREASURE_CHEST_TAG, PersistentDataType.BYTE);
              if (tag == null || tag != (byte) 1) continue;

              // ✅ 登録（重複はSetが潰す）
              placedChests.add(new BlockKey(b.getLocation()));
              chestLocations.add(toBlockLocation(b.getLocation()));
              found++;
            }
          } catch (Throwable ignored) {}
        }
      }
    }

    if (found > 0) {
      plugin.getLogger().info("[TreasureChestManager] rescanTreasureChestsAround found=" + found
          + " totalTracked=" + chestLocations.size()
          + " center=" + w.getName() + ":" + cx + "," + cy + "," + cz
          + " radius=" + radius + " yRange=" + yRange);
    } else {
      plugin.getLogger().info("[TreasureChestManager] rescanTreasureChestsAround found=0"
          + " totalTracked=" + chestLocations.size());
    }

    return found;
  }

  /** Restore every block changed during the current chest-placement attempt. */
  public void removeAllChests() {
    Set<BlockKey> restoredKeys = new HashSet<>(originalBlockData.keySet());
    restoreChangedBlocks();

    // A tagged chest recovered without an in-memory snapshot can only occur
    // after an unclean process stop. Remove the plugin-owned chest itself
    // rather than leaving a stale gameplay block behind.
    for (BlockKey key : placedChests) {
      if (restoredKeys.contains(key)) continue;
      World w = Bukkit.getWorld(key.world);
      if (w == null) continue;
      Block b = w.getBlockAt(key.x, key.y, key.z);
      if (b.getType() == Material.CHEST) {
        b.setType(Material.AIR, false);
      }
    }

    placedChests.clear();
    chestLocations.clear();
  }

  private void rememberOriginalBlock(Block block) {
    if (block == null || block.getWorld() == null) return;
    BlockKey key = new BlockKey(block.getLocation());
    originalBlockData.putIfAbsent(key, block.getBlockData().clone());
  }

  private void restoreChangedBlocks() {
    List<Map.Entry<BlockKey, org.bukkit.block.data.BlockData>> entries =
        new ArrayList<>(originalBlockData.entrySet());
    Collections.reverse(entries);

    for (Map.Entry<BlockKey, org.bukkit.block.data.BlockData> entry : entries) {
      BlockKey key = entry.getKey();
      World world = Bukkit.getWorld(key.world);
      if (world == null) continue;
      world.getBlockAt(key.x, key.y, key.z)
          .setBlockData(entry.getValue().clone(), false);
    }

    originalBlockData.clear();
  }

  /** このブロックが自分がスポーンしたチェストかどうか */
  public boolean isOurChest(Block b) {
    if (b == null) return false;
    Location loc = b.getLocation();
    return placedChests.contains(new BlockKey(
        loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
    ));
  }

  // =========================================================
  // ✅ 宝箱をスポーンして中身を詰める（新バージョン）
  // =========================================================
  static record ChestOffset(int dx, int dz) {}

  static List<ChestOffset> planUniqueChestOffsets(
      int radius,
      int count,
      Random random
  ) {
    if (radius < 0 || count < 0 || random == null) return List.of();

    List<ChestOffset> candidates = new ArrayList<>();
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        candidates.add(new ChestOffset(dx, dz));
      }
    }

    if (count > candidates.size()) return List.of();

    Collections.shuffle(candidates, random);
    return new ArrayList<>(candidates.subList(0, count));
  }

  public boolean spawnChests(
      org.bukkit.entity.Player player,
      String difficulty,
      int count
  ) {
    if (player == null || player.getWorld() == null || count < 0) {
      return false;
    }

    World world = player.getWorld();
    if (!ArenaWorldManager.WORLD_NAME.equals(world.getName())) {
      plugin.getLogger().severe(
          "[TreasureChestManager] Chest placement refused outside "
              + ArenaWorldManager.WORLD_NAME + ": " + world.getName()
      );
      return false;
    }

    Random random = new Random();
    List<ChestOffset> offsets =
        planUniqueChestOffsets(chestSpawnRadius, count, random);

    if (offsets.size() != count) {
      plugin.getLogger().severe(
          "[TreasureChestManager] Unable to reserve " + count
              + " unique chest columns within radius " + chestSpawnRadius
      );
      return false;
    }

    Material floorMaterial;
    switch (difficulty) {
      case "Easy":
        floorMaterial = Material.PURPLE_CONCRETE;
        break;
      case "Normal":
        floorMaterial = Material.LIME_CONCRETE;
        break;
      case "Hard":
        floorMaterial = Material.BLUE_CONCRETE;
        break;
      default:
        floorMaterial = Material.WHITE_CONCRETE;
        break;
    }

    int before = chestLocations.size();

    try {
      for (ChestOffset offset : offsets) {
        Location loc = player.getLocation().clone().add(offset.dx(), 0, offset.dz());
        loc.setY(world.getHighestBlockYAt(loc) + 1);

        Block block = world.getBlockAt(loc);
        Block underBlock = block.getRelative(0, -1, 0);

        rememberOriginalBlock(underBlock);
        rememberOriginalBlock(block);

        underBlock.setType(floorMaterial, false);
        block.setType(Material.CHEST, false);

        if (!(block.getState() instanceof Chest chest)) {
          throw new IllegalStateException(
              "Placed block did not expose a chest state at "
                  + block.getX() + "," + block.getY() + "," + block.getZ()
          );
        }

        org.bukkit.inventory.Inventory inv = chest.getBlockInventory();

        int poolSize = treasurePool == null ? 0 : treasurePool.size();
        plugin.getLogger().info("[CHEST][POOL] size=" + poolSize);

        if (poolSize > 0) {
          int kinds = 1 + random.nextInt(3);
          for (int j = 0; j < kinds; j++) {
            Material itemMat = treasurePool.get(random.nextInt(poolSize));
            int amount = 1 + random.nextInt(3);
            inv.addItem(new ItemStack(itemMat, amount));
          }
        }

        if (block.getState() instanceof Chest fresh) {
          fresh.getPersistentDataContainer()
              .set(
                  TREASURE_CHEST_TAG,
                  PersistentDataType.BYTE,
                  (byte) 1
              );
          fresh.update(true, false);
        }

        registerPlacedChest(block);

        long itemCount = 0;
        for (ItemStack item : inv.getContents()) {
          if (item == null || item.getType() == Material.AIR) continue;
          itemCount++;
        }
        plugin.getLogger().info("[CHEST][FILL] items=" + itemCount);
      }

      int added = chestLocations.size() - before;
      if (added != count) {
        throw new IllegalStateException(
            "Expected " + count + " tracked chests but added " + added
        );
      }

      plugin.getLogger().info(
          "✅ " + count + " 個の宝箱をスポーンしました（中身付き）"
      );
      plugin.getLogger().info(
          "[TreasureChestManager] chestLocations size before=" + before
              + " after=" + chestLocations.size()
              + " added=" + added
      );
      return true;
    } catch (Throwable failure) {
      plugin.getLogger().warning(
          "[TreasureChestManager] Chest placement rolled back: "
              + failure.getMessage()
      );
      restoreChangedBlocks();
      placedChests.clear();
      chestLocations.clear();
      return false;
    }
  }

  // =========================================================
  // ✅ 海ステージ完全対応の高度算出メソッド
  // =========================================================
  private int findSurfaceY(World w, int x, int z) {
    int y = w.getHighestBlockYAt(x, z);
    Material mat = w.getBlockAt(x, y, z).getType();

    // ✅ 海なら水面+1
    if (isWaterLike(mat)) {
      y = y + 1;
    }

    // 周囲を安全確保
    Block here = w.getBlockAt(x, y, z);
    Block above = w.getBlockAt(x, y + 1, z);

    if (!here.isEmpty()) here.setType(Material.AIR);
    if (!above.isEmpty()) above.setType(Material.AIR);

    return y;
  }

  // ✅ “水扱い”判定
  private boolean isWaterLike(Material m) {
    if (m == Material.WATER) return true;
    String name = m.name();
    return name.equals("KELP")
        || name.equals("KELP_PLANT")
        || name.equals("SEAGRASS")
        || name.equals("TALL_SEAGRASS")
        || name.equals("SEA_PICKLE")
        || name.equals("BUBBLE_COLUMN")
        || name.contains("CORAL");
  }

  /** ブロック座標キー（equals/hashCode 用） */
  private static class BlockKey {
    final String world;
    final int x, y, z;
    BlockKey(String world, int x, int y, int z) {
      this.world = world; this.x = x; this.y = y; this.z = z;
    }
    BlockKey(Location loc) {
      this(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof BlockKey)) return false;
      BlockKey k = (BlockKey) o;
      return x == k.x && y == k.y && z == k.z && Objects.equals(world, k.world);
    }
    @Override public int hashCode() { return Objects.hash(world, x, y, z); }
  }
}