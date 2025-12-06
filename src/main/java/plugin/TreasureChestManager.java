package plugin;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;

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

  /** 配置したチェストの位置を追跡 */
  private final Set<BlockKey> placedChests = new HashSet<>();

  public TreasureChestManager(TreasureRunMultiChestPlugin plugin,
      Map<String, Integer> treasureChestCounts,
      List<Material> treasurePool,
      int chestSpawnRadius) {
    this.plugin = plugin;
    this.treasureChestCounts = treasureChestCounts;
    this.treasurePool = treasurePool;
    this.chestSpawnRadius = chestSpawnRadius;
  }

  /** 現在配置したチェストを全部消す（ブロックを AIR に） */
  public void removeAllChests() {
    for (BlockKey key : placedChests) {
      World w = Bukkit.getWorld(key.world);
      if (w == null) continue;
      Block b = w.getBlockAt(key.x, key.y, key.z);
      if (b.getType() == Material.CHEST) {
        b.setType(Material.AIR);
      }
    }
    placedChests.clear();
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
  public void spawnChests(org.bukkit.entity.Player player, String difficulty, int count) {
    World world = player.getWorld();
    Random random = new Random();

    // ★ 難易度ごとの床ブロック（Easy:紫, Normal:緑, Hard:青）
    Material floorMaterial;
    switch (difficulty) {
      case "Easy":
        floorMaterial = Material.PURPLE_CONCRETE;   // 紫
        break;
      case "Normal":
        floorMaterial = Material.LIME_CONCRETE;     // 緑
        break;
      case "Hard":
        floorMaterial = Material.BLUE_CONCRETE;     // 青
        break;
      default:
        floorMaterial = Material.WHITE_CONCRETE;    // 念のため
        break;
    }

    for (int i = 0; i < count; i++) {
      int dx = random.nextInt(chestSpawnRadius * 2) - chestSpawnRadius;
      int dz = random.nextInt(chestSpawnRadius * 2) - chestSpawnRadius;
      Location loc = player.getLocation().clone().add(dx, 0, dz);
      loc.setY(world.getHighestBlockYAt(loc) + 1);

      Block block = world.getBlockAt(loc);
      block.setType(Material.CHEST);
      placedChests.add(new BlockKey(block.getLocation()));

      // ★ チェストの真下に難易度カラーのブロックを設置
      Block underBlock = block.getRelative(0, -1, 0);
      underBlock.setType(floorMaterial);

      // 💎 宝箱にランダムな宝物を入れる処理（ここが新しく追加）
      if (block.getState() instanceof Chest chest) {
        for (int j = 0; j < 1 + random.nextInt(3); j++) { // 1〜3種類
          Material itemMat = treasurePool.get(random.nextInt(treasurePool.size()));
          int amount = 1 + random.nextInt(3); // 1〜3個
          chest.getBlockInventory().addItem(new ItemStack(itemMat, amount));
        }
      }
    }

    plugin.getLogger().info("✅ " + count + " 個の宝箱をスポーンしました（中身付き）");
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