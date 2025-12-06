package plugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 宝箱を開けた瞬間に、
 * - キラキラ演出
 * - 宝箱の上に「取得アイテムがポンと出る（ItemDisplay）」演出
 *
 * ※このListenerは「演出だけ」で、アイテム付与やスコア加算などのゲーム処理には触れません。
 * ※ TreasureRunMultiChestPlugin 側の onInventoryOpen() が実際の付与を行います。
 */
public class TreasureChestPickupListener implements Listener {

  private final TreasureRunMultiChestPlugin plugin;

  /** 同じチェストで二重に演出が走らないようにガード（チェストは壊されるので基本増えません） */
  private final Set<BlockKey> played = new HashSet<>();

  public TreasureChestPickupListener(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onChestOpen(InventoryOpenEvent event) {
    if (!(event.getPlayer() instanceof Player player)) return;
    if (!plugin.isGameRunning()) return;

    Inventory inv = event.getInventory();
    Object holder = inv.getHolder();
    if (!(holder instanceof Chest) && !(holder instanceof DoubleChest)) return;

    Location chestLoc = inv.getLocation();
    if (chestLoc == null && holder instanceof DoubleChest dc) {
      chestLoc = dc.getLocation();
    }
    if (chestLoc == null) return;

    Block chestBlock = chestLoc.getBlock();
    if (chestBlock.getType() != Material.CHEST) return;

    TreasureChestManager mgr = plugin.getTreasureChestManager();
    if (mgr == null) return;
    if (!mgr.isOurChest(chestBlock)) return;

    BlockKey key = new BlockKey(chestLoc);
    if (played.contains(key)) return;
    played.add(key);

    // この時点の「宝箱の中身」をコピーしておく（この後メイン処理でclearされるため）
    ItemStack[] raw = inv.getContents();
    ItemStack[] snapshot = new ItemStack[raw.length];
    for (int i = 0; i < raw.length; i++) {
      ItemStack it = raw[i];
      snapshot[i] = (it == null) ? null : it.clone();
    }

    // ★修正：ラムダで使う変数を final（実質final）にする
    final Location chestLocFinal = chestLoc;
    final ItemStack[] snapshotFinal = snapshot;

    // メイン処理と同tickで走ってもOKですが、見た目を安定させるため 0tick ディレイでスケジュール
    Bukkit.getScheduler().runTask(plugin, () -> playPickupEffects(player, chestLocFinal, snapshotFinal));
  }

  private void playPickupEffects(Player player, Location chestLoc, ItemStack[] contents) {
    World w = chestLoc.getWorld();
    if (w == null) return;

    // 宝箱の天面ちょい上
    Location base = chestLoc.clone().add(0.5, 1.15, 0.5);

    // キラキラ（最初のパッ！）
    w.spawnParticle(Particle.FIREWORKS_SPARK, base, 35, 0.35, 0.25, 0.35, 0.02);
    w.spawnParticle(Particle.END_ROD,        base, 12, 0.20, 0.20, 0.20, 0.01);
    w.playSound(base, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.6f);
    w.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME,   0.6f, 1.8f);

    // 表示するアイテムを抽出（AIR/NULL除外）
    ItemStack[] toShow = filterItems(contents, 4); // 表示上限：最大4個（多いとゴチャつく）
    if (toShow.length == 0) return;

    // 複数ならちょい散らす
    double[][] offsets = switch (toShow.length) {
      case 1 -> new double[][]{{0.0, 0.0}};
      case 2 -> new double[][]{{-0.12, 0.0}, {0.12, 0.0}};
      case 3 -> new double[][]{{-0.16, -0.06}, {0.16, -0.06}, {0.0, 0.14}};
      default -> new double[][]{{-0.16, -0.08}, {0.16, -0.08}, {-0.16, 0.12}, {0.16, 0.12}};
    };

    for (int i = 0; i < toShow.length; i++) {
      ItemStack item = toShow[i];
      double ox = offsets[i][0];
      double oz = offsets[i][1];
      spawnPopItemDisplay(base.clone().add(ox, 0.0, oz), item);
    }
  }

  private void spawnPopItemDisplay(Location spawnLoc, ItemStack item) {
    World w = spawnLoc.getWorld();
    if (w == null) return;

    ItemDisplay display = (ItemDisplay) w.spawnEntity(spawnLoc, EntityType.ITEM_DISPLAY);
    display.setItemStack(item);

    // 見た目調整（軽く小さめ＆中心に見える）
    display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
    display.setBillboard(Display.Billboard.CENTER);

    // スケール（1.0だと大きめなので少し縮小）
    float s = 0.65f;
    display.setTransformation(new Transformation(
        new Vector3f(0f, 0f, 0f),
        new Quaternionf(),
        new Vector3f(s, s, s),
        new Quaternionf()
    ));

    // ふわっと上に「ポン」＋軽い回転＋キラキラ追従
    new BukkitRunnable() {
      int tick = 0;
      final int total = 26;     // 表示時間
      final double rise = 0.55; // 上昇量
      final double wobble = 0.03;

      final Location origin = spawnLoc.clone();

      @Override
      public void run() {
        if (!display.isValid()) {
          cancel();
          return;
        }

        tick++;
        double t = tick / (double) total;          // 0..1
        double ease = 1.0 - Math.pow(1.0 - t, 3);  // ease-out cubic

        double y = rise * ease;
        double x = Math.sin(tick * 0.35) * wobble;
        double z = Math.cos(tick * 0.35) * wobble;

        Location now = origin.clone().add(x, y, z);
        display.teleport(now);

        // 追従キラキラ
        w.spawnParticle(Particle.END_ROD, now.clone().add(0, 0.05, 0), 1, 0.02, 0.02, 0.02, 0.0);
        if (tick % 3 == 0) {
          w.spawnParticle(Particle.FIREWORKS_SPARK, now, 2, 0.05, 0.05, 0.05, 0.01);
        }

        if (tick >= total) {
          // 消える時も少しキラッとして終了
          w.spawnParticle(Particle.FIREWORKS_SPARK, now, 10, 0.12, 0.10, 0.12, 0.02);
          display.remove();
          cancel();
        }
      }
    }.runTaskTimer(plugin, 0L, 1L);
  }

  private ItemStack[] filterItems(ItemStack[] contents, int max) {
    if (contents == null || contents.length == 0) return new ItemStack[0];

    ItemStack[] temp = new ItemStack[Math.min(max, contents.length)];
    int n = 0;

    for (ItemStack it : contents) {
      if (it == null) continue;
      if (it.getType() == Material.AIR) continue;

      temp[n++] = it.clone();
      if (n >= max) break;
    }

    ItemStack[] out = new ItemStack[n];
    System.arraycopy(temp, 0, out, 0, n);
    return out;
  }

  /** ブロック座標キー（equals/hashCode 用） */
  private static class BlockKey {
    final String world;
    final int x, y, z;

    BlockKey(Location loc) {
      this.world = (loc.getWorld() == null) ? "null" : loc.getWorld().getName();
      this.x = loc.getBlockX();
      this.y = loc.getBlockY();
      this.z = loc.getBlockZ();
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof BlockKey)) return false;
      BlockKey k = (BlockKey) o;
      return x == k.x && y == k.y && z == k.z && Objects.equals(world, k.world);
    }

    @Override public int hashCode() {
      return Objects.hash(world, x, y, z);
    }
  }
}