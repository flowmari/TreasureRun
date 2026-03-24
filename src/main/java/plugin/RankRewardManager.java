package plugin;

import org.bukkit.Bukkit;

import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.Particle.DustOptions;
import org.bukkit.DyeColor;

import java.util.Map;

public class RankRewardManager {

  private final TreasureRunMultiChestPlugin plugin;

  // 念のため長すぎる演出は上限（5分）
  private static final long MAX_KEEP_TICKS = 20L * 60L * 5L;

  public RankRewardManager(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  /** 互換：旧シグネチャ。内部で「だいたいの演出時間」だけ維持する */
  public void giveRankRewardWithEffect(Player player, int rank) {
    long fallback = switch (rank) {
      case 1 -> 180L;
      case 2 -> 140L;
      case 3 ->  50L;
      default -> 0L;
    };
    giveRankRewardWithEffect(player, rank, fallback);
  }

  /** ✅ 新：DJ総Tick(keepTicks)まで“ずっと”報酬+演出を維持する */
  public void giveRankRewardWithEffect(Player player, int rank, long keepTicks) {
    if (player == null || !player.isOnline()) return;

    // ✅ 非同期対策：必ずメインスレッドで実行
    if (!Bukkit.isPrimaryThread()) {
      Bukkit.getScheduler().runTask(plugin, () -> giveRankRewardWithEffect(player, rank, keepTicks));
      return;
    }

    long keep = Math.max(0L, Math.min(keepTicks, MAX_KEEP_TICKS));

    plugin.getLogger().info("[Reward] giveRankRewardWithEffect called player=" + player.getName()
        + " rank=" + rank + " keepTicks=" + keep);

    ItemStack reward;
    String sub;

    if (rank == 1) {
      reward = new ItemStack(Material.NETHERITE_INGOT, 1);
      sub = ChatColor.YELLOW + "ネザライトインゴット獲得！";
    } else if (rank == 2) {
      reward = new ItemStack(Material.DIAMOND, 1);
      sub = ChatColor.WHITE + "ダイヤ獲得！";
    } else if (rank == 3) {
      reward = new ItemStack(Material.GOLDEN_APPLE, 1);
      sub = ChatColor.YELLOW + "金リンゴ獲得！";
    } else {
      return;
    }

    // ① アイテム付与（インベントリ満杯なら足元に落とす）
    Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward);
    if (!leftover.isEmpty()) {
      for (ItemStack item : leftover.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), item);
      }
    }

    // ② チャット表示（Titleは MultiChestPlugin 側で維持するのでここでは送らない）
    String chatRank = ChatColor.GOLD + "🏆 No." + rank + " !!";
    player.sendMessage(chatRank + ChatColor.RESET + " " +
        ChatColor.GREEN + "[Reward] " + ChatColor.RESET + sub);

    // ③ 音（気づく用：2つ重ねる）
    playSafe(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    playSafe(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);

    // ④ パーティクル（視界の少し前に出す）
    World w = player.getWorld();
    Location loc = player.getEyeLocation().clone()
        .add(player.getLocation().getDirection().multiply(0.8));

    w.spawnParticle(Particle.TOTEM, loc, 30, 0.4, 0.4, 0.4, 0.01);
    w.spawnParticle(Particle.FIREWORKS_SPARK, loc, 50, 0.6, 0.6, 0.6, 0.02);
    w.spawnParticle(Particle.END_ROD, loc, 80, 0.7, 0.7, 0.7, 0.02);

    // ======================================================
    // ✅ 順位ごと演出（DJ終点 keepTicks まで“ずっと”）
    // ======================================================
    if (keep <= 0L) return;



    // オーロラ：runTaskTimer(..., 2L) なので durationTicks は keep/2
    int auroraDuration = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (keep + 1L) / 2L));

    // 虹：runTaskTimer(..., 1L) なので durationTicks は keep
    int rainbowDuration = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, keep));

    if (rank == 1) {
      plugin.getLogger().info("[Reward] rank==1 -> aurora/rainbow/wolf 유지 keepTicks=" + keep);

      spawnAuroraCurtain(player, auroraDuration, 7.0, 5.0, 3);
      spawnRainbowArc(player, rainbowDuration, 2.6, 1.5, 42);
      spawnRainbowWolfParadeGuaranteed(player, keep);
      // ✅ NEW：1位の追加演出（虹の上を空に左→右へドラゴン → 1秒後にペガサス）
      spawnSkyDragonOverRainbow(player, keep, 0L);    // すぐ
      spawnSkyPegasusOverRainbow(player, keep, 20L);  // 1秒後（20 ticks）

    } else if (rank == 2) {
      spawnAuroraCurtain(player, auroraDuration, 5.2, 3.6, 2);
      spawnRainbowArc(player, rainbowDuration, 2.0, 1.1, 36);

    } else if (rank == 3) {
      spawnRainbowArc(player, rainbowDuration, 2.3, 1.3, 36);
    }
  }

  // ======================================================
  // ★ オーロラ（カーテン）演出：ゆらゆら光の幕が揺れる
  // durationTicks は「内部t回数」(period=2tick) なので実時間は durationTicks*2tick
  // ======================================================
  private void spawnAuroraCurtain(Player player, int durationTicks, double width, double height, int curtains) {
    World w = player.getWorld();
    if (w == null) return;

    Vector forward = player.getLocation().getDirection().clone();
    forward.setY(0);
    if (forward.lengthSquared() < 0.0001) forward = new Vector(0, 0, 1);
    forward.normalize();

    Vector side = new Vector(-forward.getZ(), 0, forward.getX()).normalize();

    Location base = player.getEyeLocation().clone()
        .add(forward.clone().multiply(4.0))
        .add(0, 1.0, 0);

    final Vector fwd = forward.clone();
    final Vector sd = side.clone();
    final Location bs = base.clone();
    final World world = w;

    int xSteps = 24;
    int ySteps = 14;
    double curtainDepth = 1.2;

    new BukkitRunnable() {
      int t = 0;

      @Override
      public void run() {
        if (!player.isOnline()) { cancel(); return; }

        double time = t * 0.12;

        for (int c = 0; c < curtains; c++) {
          double depth = (c - (curtains - 1) / 2.0) * curtainDepth;
          float hueBase = (float) ((0.33 + c * 0.08) % 1.0);

          for (int xi = 0; xi <= xSteps; xi++) {
            double xN = (xi / (double) xSteps) * 2.0 - 1.0;
            double x = xN * (width / 2.0);

            for (int yi = 0; yi <= ySteps; yi++) {
              double yN = (yi / (double) ySteps);
              double y = yN * height;

              double wave1 = Math.sin(time + xN * 2.2 + yN * 1.4);
              double wave2 = Math.sin(time * 0.7 + xN * 4.0);
              double sway = (wave1 * 0.25 + wave2 * 0.15);

              Location p = bs.clone()
                  .add(sd.clone().multiply(x + sway))
                  .add(0, y, 0)
                  .add(fwd.clone().multiply(depth + sway * 0.3));

              float hue = (float) ((hueBase + xN * 0.10 + time * 0.02) % 1.0);
              float brightness = (float) clamp01(0.35 + yN * 0.55 + (wave1 * 0.10));

              Color col = hsvToBukkitColor(hue, 0.95f, brightness);
              float size = (c == 1 ? 1.30f : 1.10f);

              world.spawnParticle(
                  Particle.REDSTONE,
                  p,
                  1,
                  0, 0, 0, 0,
                  new DustOptions(col, size)
              );
            }
          }
        }

        if (t % 3 == 0) {
          world.spawnParticle(Particle.END_ROD, bs, 10, 1.2, 1.0, 1.2, 0.01);
        }

        t++;
        if (t >= durationTicks) cancel();
      }

    }.runTaskTimer(plugin, 0L, 2L);
  }

  private static double clamp01(double v) {
    return Math.max(0.0, Math.min(1.0, v));
  }

  // ======================================================
  // ★ 虹（レインボーアーチ）
  // durationTicks は「実tick」(period=1tick)
  // ======================================================
  private void spawnRainbowArc(Player player, int durationTicks, double radius, double height, int points) {
    World w = player.getWorld();
    if (w == null) return;

    Vector dir = player.getLocation().getDirection().clone();
    dir.setY(0);
    if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 1);
    dir.normalize();

    Vector side = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

    Location base = player.getLocation().clone().add(dir.clone().multiply(2.4));
    double baseY = player.getEyeLocation().getY() - 0.2;
    base.setY(baseY);

    new BukkitRunnable() {
      int t = 0;

      @Override
      public void run() {
        if (!player.isOnline()) {
          cancel();
          return;
        }

        for (int i = 0; i <= points; i++) {
          double p = (double) i / (double) points;
          double theta = Math.PI * (1.0 - p);
          double x = Math.cos(theta) * radius;
          double y = Math.sin(theta) * height;

          Location point = base.clone()
              .add(side.clone().multiply(x))
              .add(0, y, 0);

          float hue = (float) ((p + (t * 0.02)) % 1.0);
          Color c = hsvToBukkitColor(hue, 1.0f, 1.0f);

          DustOptions dust = new DustOptions(c, 1.25f);
          w.spawnParticle(Particle.REDSTONE, point, 1, 0, 0, 0, 0, dust);
        }

        Location sparkle = base.clone().add(0, height * 0.6, 0);
        w.spawnParticle(Particle.END_ROD, sparkle, 6, 0.2, 0.2, 0.2, 0.01);

        t++;
        if (t >= durationTicks) cancel();
      }

    }.runTaskTimer(plugin, 0L, 1L);
  }

  private Color hsvToBukkitColor(float h, float s, float v) {
    int rgb = java.awt.Color.HSBtoRGB(h, s, v);
    int r = (rgb >> 16) & 0xFF;
    int g = (rgb >> 8) & 0xFF;
    int b = rgb & 0xFF;
    return Color.fromRGB(r, g, b);
  }

  // ======================================================
  // ★ 1位：虹色の狼（DJ終点 keepTicks まで生存＆踊り続ける）
  // ======================================================
  private void spawnRainbowWolfParadeGuaranteed(Player player, long keepTicks) {
    World w = player.getWorld();
    if (w == null) return;

    Vector dir = player.getLocation().getDirection().clone();
    dir.setY(0);
    if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 1);
    dir.normalize();

    Vector side = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

    Location base = player.getLocation().clone().add(dir.clone().multiply(2.2));
    Location start = base.clone().add(side.clone().multiply(-4.0));
    Location end   = base.clone().add(side.clone().multiply( 4.0));

    start.setY(player.getLocation().getY());
    end.setY(player.getLocation().getY());

    plugin.getLogger().info("[Reward] spawnRainbowWolfParadeGuaranteed: start=" + start + " end=" + end + " world=" + w.getName());

    try {
      if (!start.getChunk().isLoaded()) start.getChunk().load(true);
      if (!end.getChunk().isLoaded()) end.getChunk().load(true);
    } catch (Throwable t) {
      plugin.getLogger().warning("[Reward] chunk load warning: " + t.getMessage());
    }

    final int maxAttempts = 6;
    attemptSpawnWolf(player, w, start, end, keepTicks, 1, maxAttempts);
  }

  private void attemptSpawnWolf(Player player, World w, Location start, Location end, long keepTicks, int attempt, int maxAttempts) {
    if (player == null || !player.isOnline()) return;
    if (w == null) return;

    plugin.getLogger().info("[Reward] attemptSpawnWolf attempt=" + attempt + "/" + maxAttempts + " player=" + player.getName());

    Wolf wolf;
    try {
      wolf = (Wolf) w.spawnEntity(start, EntityType.WOLF);
    } catch (Throwable t) {
      plugin.getLogger().severe("[Reward] attemptSpawnWolf: spawnEntity threw: " + t.getMessage());
      if (attempt < maxAttempts) {
        Bukkit.getScheduler().runTaskLater(plugin, () ->
            attemptSpawnWolf(player, w, start, end, keepTicks, attempt + 1, maxAttempts), 2L);
      }
      return;
    }

    if (wolf == null || !wolf.isValid()) {
      plugin.getLogger().warning("[Reward] attemptSpawnWolf: wolf is null/invalid (maybe cancelled).");
      if (attempt < maxAttempts) {
        Bukkit.getScheduler().runTaskLater(plugin, () ->
            attemptSpawnWolf(player, w, start, end, keepTicks, attempt + 1, maxAttempts), 2L);
      }
      return;
    }

    setupRainbowWolf(wolf, player);

    plugin.getLogger().info("[Reward] attemptSpawnWolf: spawned OK uuid=" + wolf.getUniqueId()
        + " name=" + wolf.getCustomName());

    runWolfParade(player, w, wolf, start, end, keepTicks);
  }

  private void setupRainbowWolf(Wolf wo, Player player) {
    try {
      wo.setAI(false);
      wo.setSilent(true);
      wo.setInvulnerable(true);
      wo.setPersistent(true);
      wo.setRemoveWhenFarAway(false);

      wo.setGlowing(false); // ★ここを false に（または行自体削除）

      wo.setCustomName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Rainbow Wolf");
      wo.setCustomNameVisible(true);

      wo.setTamed(true);
      wo.setOwner(player);
      wo.setCollarColor(DyeColor.RED);
    } catch (Throwable t) {
      plugin.getLogger().warning("[Reward] setupRainbowWolf warning: " + t.getMessage());
    }
  }

  private void runWolfParade(Player player, World w, Wolf finalWolf, Location start, Location end, long keepTicks) {
    if (player == null || !player.isOnline()) return;

    final DyeColor[] rainbow = new DyeColor[] {
        DyeColor.RED,
        DyeColor.ORANGE,
        DyeColor.YELLOW,
        DyeColor.LIME,
        DyeColor.LIGHT_BLUE,
        DyeColor.BLUE,
        DyeColor.PURPLE,
        DyeColor.PINK
    };

    playSafe(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.3f);
    playSafe(player, Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.0f);

    final int totalTicks = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, Math.min(keepTicks, MAX_KEEP_TICKS)));

    new BukkitRunnable() {
      int t = 0;

      @Override
      public void run() {
        if (finalWolf == null || finalWolf.isDead() || !finalWolf.isValid() || !player.isOnline()) {
          cleanup();
          return;
        }

        // 往復させるための ping-pong
        double phase = (t % 80) / 80.0; // 0..1
        double prog = (phase <= 0.5) ? (phase * 2.0) : (2.0 - phase * 2.0); // 0->1->0

        double x = start.getX() + (end.getX() - start.getX()) * prog;
        double z = start.getZ() + (end.getZ() - start.getZ()) * prog;
        double y = start.getY() + 0.2 + Math.sin(t * 0.6) * 0.25;

        Location next = new Location(w, x, y, z);

        Vector moveDir = end.toVector().subtract(start.toVector());
        float yaw = (float) Math.toDegrees(Math.atan2(-moveDir.getX(), moveDir.getZ()));
        next.setYaw(yaw);
        next.setPitch(0);

        finalWolf.teleport(next);
        finalWolf.setCollarColor(rainbow[t % rainbow.length]);

        w.spawnParticle(Particle.END_ROD, next.clone().add(0, 0.6, 0), 6, 0.2, 0.2, 0.2, 0.01);
        w.spawnParticle(Particle.NOTE, next.clone().add(0, 0.8, 0), 2, 0.2, 0.2, 0.2, 0.0);

        spawnRainbowDustAura(finalWolf, t);

        if (t % 10 == 0) {
          playSafe(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
        }

        t++;
        if (t >= totalTicks) cleanup();
      }

      private void cleanup() {
        try {
          if (finalWolf != null && !finalWolf.isDead()) finalWolf.remove();
        } catch (Exception ignored) {}
        cancel();
      }

    }.runTaskTimer(plugin, 0L, 1L);
  }

  // ======================================================
  // ✅ 狼の周りに虹色 Dust（REDSTONE）をまとわせる
  // ======================================================
  private void spawnRainbowDustAura(Wolf wolf, int t) {
    if (wolf == null || wolf.isDead()) return;

    World w = wolf.getWorld();
    if (w == null) return;

    Location base = wolf.getLocation().clone().add(0, 0.75, 0);
    double r = 0.45;

    for (int i = 0; i < 10; i++) {
      double ang = (t * 0.35) + (i * (Math.PI * 2.0 / 10.0));
      double x = Math.cos(ang) * r;
      double z = Math.sin(ang) * r;

      float hue = (float) (((i / 10.0) + (t * 0.03)) % 1.0);
      Color c = hsvToBukkitColor(hue, 1.0f, 1.0f);

      Location p = base.clone().add(x, (i % 2 == 0 ? 0.12 : -0.08), z);
      w.spawnParticle(Particle.REDSTONE, p, 1, 0, 0, 0, 0, new DustOptions(c, 1.05f));
    }

    w.spawnParticle(Particle.END_ROD, base, 2, 0.25, 0.18, 0.25, 0.01);
  }

  // ======================================================
  // ✅ NEW：1位追加演出（ドラゴン→1秒後ペガサス）
  // 虹の上の空を “左→右” に横切るように飛ばす
  // ※安全のため Entity（本物のエンダードラゴン等）は出さず、粒子でシルエットを描く
  // ======================================================

  private void spawnSkyDragonOverRainbow(Player player, long keepTicks, long delayTicks) {
    if (player == null || !player.isOnline()) return;
    long safeDelay = Math.max(0L, delayTicks);

    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;
      int duration = (int) Math.max(50L, Math.min(120L, keepTicks)); // 2.5s〜6sくらい
      spawnFlyingSpriteOverRainbow(player, duration, 7.0, 3.4, FlyingSpriteType.DRAGON);
    }, safeDelay);
  }

  private void spawnSkyPegasusOverRainbow(Player player, long keepTicks, long delayTicks) {
    if (player == null || !player.isOnline()) return;
    long safeDelay = Math.max(0L, delayTicks);

    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;
      int duration = (int) Math.max(50L, Math.min(120L, keepTicks)); // 2.5s〜6sくらい
      spawnFlyingSpriteOverRainbow(player, duration, 7.0, 3.4, FlyingSpriteType.PEGASUS);
    }, safeDelay);
  }

  private enum FlyingSpriteType { DRAGON, PEGASUS }

  /**
   * 虹の上の空を、左→右へ横切る粒子スプライト（ドラゴン/ペガサス）。
   * - プレイヤー視点に対して前方に固定
   * - 半円アーチの軌道で「虹の上っぽさ」を出す
   */
  private void spawnFlyingSpriteOverRainbow(Player player,
      int durationTicks,
      double radius,
      double arcHeight,
      FlyingSpriteType type) {
    World w = player.getWorld();
    if (w == null) return;

    // プレイヤー向き（水平）
    Vector dir = player.getLocation().getDirection().clone();
    dir.setY(0);
    if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 1);
    dir.normalize();

    // 左右方向
    Vector side = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

    // 虹の“上の空”の基準点：前方 + 高め
    Location base = player.getEyeLocation().clone()
        .add(dir.clone().multiply(4.2))
        .add(0, 2.8, 0);

    final Vector sd = side.clone();
    final Location bs = base.clone();
    final World world = w;

    new BukkitRunnable() {
      int t = 0;

      @Override
      public void run() {
        if (!player.isOnline()) { cancel(); return; }

        double p = t / (double) durationTicks; // 0..1
        double s = (p * 2.0) - 1.0;            // -1..+1（左→右）

        // 虹の上をなぞるアーチ（0..π）
        double y = Math.sin(p * Math.PI) * arcHeight;

        Location center = bs.clone()
            .add(sd.clone().multiply(s * radius))
            .add(0, y, 0);

        // 進行方向（右へ）
        Vector heading = sd.clone().normalize();

        if (type == FlyingSpriteType.DRAGON) {
          drawDragonParticles(world, center, heading, t);
        } else {
          drawPegasusParticles(world, center, heading, t);
        }

        t++;
        if (t >= durationTicks) cancel();
      }
    }.runTaskTimer(plugin, 0L, 1L);
  }

  // -------------------------
  // DRAGON（粒子シルエット）
  // -------------------------
  private void drawDragonParticles(World world, Location c, Vector heading, int tick) {
    Color body = Color.fromRGB(60, 10, 80);      // 濃い紫
    Color wing = Color.fromRGB(120, 30, 160);    // 紫

    Vector right = new Vector(-heading.getZ(), 0, heading.getX()).normalize();
    Vector up = new Vector(0, 1, 0);

    // 体幹（点列）
    for (int i = -3; i <= 3; i++) {
      Location p = c.clone()
          .add(heading.clone().multiply(i * 0.25))
          .add(0, Math.sin((tick + i) * 0.25) * 0.05, 0);
      world.spawnParticle(Particle.REDSTONE, p, 1, 0, 0, 0, 0,
          new DustOptions(body, 1.35f));
    }

    // 頭
    Location head = c.clone().add(heading.clone().multiply(1.0)).add(0, 0.10, 0);
    world.spawnParticle(Particle.REDSTONE, head, 2, 0.02, 0.02, 0.02, 0,
        new DustOptions(Color.fromRGB(30, 0, 40), 1.6f));

    // 目（緑）
    Location eye1 = head.clone().add(right.clone().multiply(0.15)).add(0, 0.02, 0);
    Location eye2 = head.clone().add(right.clone().multiply(-0.15)).add(0, 0.02, 0);
    world.spawnParticle(Particle.REDSTONE, eye1, 1, 0, 0, 0, 0,
        new DustOptions(Color.fromRGB(80, 255, 120), 1.0f));
    world.spawnParticle(Particle.REDSTONE, eye2, 1, 0, 0, 0, 0,
        new DustOptions(Color.fromRGB(80, 255, 120), 1.0f));

    // 翼（羽ばたき）
    double flap = 0.55 + 0.25 * Math.sin(tick * 0.35);
    for (int i = 0; i <= 5; i++) {
      double r = i * 0.35;

      Location lw = c.clone()
          .add(right.clone().multiply(-r))
          .add(up.clone().multiply(flap * (0.15 + i * 0.04)));
      Location rw = c.clone()
          .add(right.clone().multiply(r))
          .add(up.clone().multiply(flap * (0.15 + i * 0.04)));

      world.spawnParticle(Particle.REDSTONE, lw, 1, 0.02, 0.02, 0.02, 0,
          new DustOptions(wing, 1.2f));
      world.spawnParticle(Particle.REDSTONE, rw, 1, 0.02, 0.02, 0.02, 0,
          new DustOptions(wing, 1.2f));
    }

    // 尾（火花）
    Location tail = c.clone().add(heading.clone().multiply(-1.0)).add(0, -0.05, 0);
    world.spawnParticle(Particle.END_ROD, tail, 2, 0.08, 0.03, 0.08, 0.01);
    if (tick % 2 == 0) {
      world.spawnParticle(Particle.FLAME, tail, 2, 0.05, 0.02, 0.05, 0.005);
    }
  }

  // -------------------------
  // PEGASUS（粒子シルエット）
  // -------------------------
  private void drawPegasusParticles(World world, Location c, Vector heading, int tick) {
    Color body = Color.fromRGB(245, 245, 255);   // 白
    Color wing = Color.fromRGB(190, 230, 255);   // 淡い水色

    Vector right = new Vector(-heading.getZ(), 0, heading.getX()).normalize();
    Vector up = new Vector(0, 1, 0);

    // 体（小さめ）
    for (int i = -2; i <= 2; i++) {
      Location p = c.clone()
          .add(heading.clone().multiply(i * 0.22))
          .add(0, Math.sin((tick + i) * 0.25) * 0.04, 0);
      world.spawnParticle(Particle.REDSTONE, p, 1, 0, 0, 0, 0,
          new DustOptions(body, 1.25f));
    }

    // 頭
    Location head = c.clone().add(heading.clone().multiply(0.9)).add(0, 0.10, 0);
    world.spawnParticle(Particle.REDSTONE, head, 1, 0.02, 0.02, 0.02, 0,
        new DustOptions(body, 1.35f));

    // たてがみ（キラッ）
    if (tick % 2 == 0) {
      world.spawnParticle(Particle.END_ROD, head, 1, 0.05, 0.05, 0.05, 0.01);
    }

    // 翼（大きく）
    double flap = 0.65 + 0.35 * Math.sin(tick * 0.45);
    for (int i = 0; i <= 6; i++) {
      double r = i * 0.30;

      Location lw = c.clone()
          .add(right.clone().multiply(-r))
          .add(up.clone().multiply(flap * (0.18 + i * 0.05)));
      Location rw = c.clone()
          .add(right.clone().multiply(r))
          .add(up.clone().multiply(flap * (0.18 + i * 0.05)));

      world.spawnParticle(Particle.REDSTONE, lw, 1, 0.02, 0.02, 0.02, 0,
          new DustOptions(wing, 1.15f));
      world.spawnParticle(Particle.REDSTONE, rw, 1, 0.02, 0.02, 0.02, 0,
          new DustOptions(wing, 1.15f));
    }

    // 尾（雲）
    Location tail = c.clone().add(heading.clone().multiply(-0.9)).add(0, -0.05, 0);
    world.spawnParticle(Particle.CLOUD, tail, 2, 0.08, 0.03, 0.08, 0.01);
  }

  private void playSafe(Player player, Sound sound, float volume, float pitch) {
    try {
      player.playSound(player.getLocation(), sound, volume, pitch);
    } catch (Exception e) {
      plugin.getLogger().warning("Sound play failed: " + sound + " : " + e.getMessage());
    }
  }
}
