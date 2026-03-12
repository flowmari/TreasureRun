package plugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

public class ChestProximitySoundService {

  private final JavaPlugin plugin;
  private final TreasureChestManager chestManager;
  private final Map<UUID, BukkitTask> tasks = new HashMap<>();
  private final java.util.function.BooleanSupplier isRunning;

  // ✅ 追加：デバッグ表示スイッチ（config.yml から読む）
  private final boolean debugChat;
  private final boolean debugActionBar;

  // ✅ 追加：ActionBarロック（結果表示中だけ止める）
  private final Set<UUID> actionBarLocked = Collections.newSetFromMap(new ConcurrentHashMap<>());

  // =========================================================
  // ✅ True Faith寄せの“体感テンポ”
  // =========================================================
  private static final double TARGET_BPM = 118.0;
  private static final int STEPS_PER_BAR = 16;

  // 16分音符の長さ（tick）
  private static final double STEP_TICKS_FLOAT = 300.0 / TARGET_BPM;

  // =========================================================
  // 音量ノブ（MASTERで鳴らすなら控えめ推奨）
  // =========================================================
  private final float bassBase  = 0.10f;
  private final float stabBase  = 0.08f;
  private final float harpBase  = 0.06f;
  private final float airBase   = 0.06f;
  private final float chimeBase = 0.10f;
  private final float drumBase  = 0.16f;

  public ChestProximitySoundService(JavaPlugin plugin,
      TreasureChestManager chestManager,
      java.util.function.BooleanSupplier isRunning) {
    this.plugin = plugin;
    this.chestManager = chestManager;
    this.isRunning = isRunning;

    // ✅ 追加：config.yml のスイッチ（なければfalse）
    this.debugChat = plugin.getConfig().getBoolean("chestSound.debug", false);
    this.debugActionBar = plugin.getConfig().getBoolean("chestSound.actionBarDebug", false);
  }

  // ✅ 追加：外部（結果表示側）からロック/解除できる
  public void setActionBarLocked(Player player, boolean locked) {
    if (player == null) return;
    UUID uuid = player.getUniqueId();
    if (locked) actionBarLocked.add(uuid);
    else actionBarLocked.remove(uuid);

    // ロックした瞬間に残骸が残らないようにクリア
    if (locked && player.isOnline()) {
      try {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
      } catch (Throwable ignored) {}
    }
  }

  private boolean isActionBarLocked(Player player) {
    if (player == null) return false;
    return actionBarLocked.contains(player.getUniqueId());
  }

  public void start(Player player) {
    stop(player);

    if (debugChat) {
      player.sendMessage("§b[ChestSound] start() called (16th-grid mode)");
    }

    final long[] tickNow = {0L};
    final double[] nextStepAt = {STEP_TICKS_FLOAT};
    final int[] step = {0};
    final int[] bar = {0};
    final int[] lastDebugSec = {-1};



    BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
      if (!player.isOnline() || !isRunning.getAsBoolean()) {
        stop(player);
        return;
      }
      if (chestManager == null) return;

      tickNow[0]++;

      while (tickNow[0] >= nextStepAt[0]) {
        nextStepAt[0] += STEP_TICKS_FLOAT;

        processStep(player, step, bar, tickNow, lastDebugSec);

        step[0]++;
        if (step[0] >= STEPS_PER_BAR) {
          step[0] = 0;
          bar[0]++;
        }

        if (tickNow[0] - (long) Math.floor(nextStepAt[0]) > 40) break;
      }

    }, 0L, 1L);

    tasks.put(player.getUniqueId(), task);
  }

  private void processStep(Player player,
      int[] step, int[] bar,
      long[] tickNow, int[] lastDebugSec) {

    Collection<Location> chestLocations = chestManager.getChestLocations();
    if (chestLocations == null || chestLocations.isEmpty()) {
      // ✅ ActionBarは debugActionBar AND ロックされてないときだけ
      if (debugActionBar && !isActionBarLocked(player)) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
            new TextComponent("§7[ChestSound] chests=0"));
      }
      return;
    }

    Location p = player.getLocation();
    World pw = p.getWorld();
    if (pw == null) return;

    double bestD2 = Double.MAX_VALUE;
    Location bestLoc = null;

    for (Location c : chestLocations) {
      if (c == null || c.getWorld() == null) continue;
      if (!c.getWorld().getUID().equals(pw.getUID())) continue;

      double d2 = p.distanceSquared(c);
      if (d2 < bestD2) {
        bestD2 = d2;
        bestLoc = c;
      }
    }
    if (bestLoc == null) return;

    double dist = Math.sqrt(bestD2);

    long elapsedTicks = tickNow[0];
    int secondsNow = (int) (elapsedTicks / 20L);

    final double maxRange = 16.0;
    double t = clamp01(1.0 - (dist / maxRange));
    String inRange = (dist <= maxRange) ? "§aIN" : "§cOUT";

    // ✅ ActionBarは debugActionBar AND ロックされてないときだけ
    if (debugActionBar && !isActionBarLocked(player)) {
      String msg = String.format(
          "§b[ChestSound] §fD=§e%.1fm §7(%s§7) §ft=§a%.2f §7step=%d bar=%d",
          dist, inRange, t, step[0], bar[0]
      );
      player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    if (debugChat) {
      if (secondsNow != lastDebugSec[0] && (secondsNow % 2 == 0)) {
        lastDebugSec[0] = secondsNow;
        player.sendMessage(String.format("§b[ChestSound] nearest=%.1fm %s  t=%.2f", dist, inRange, t));
      }
    }

    if (dist > maxRange) return;

    final double soundRange = 10.0;
    if (dist > soundRange) return;

    double a = clamp01(1.0 - (dist / soundRange));

    boolean stage1 = (a >= 0.25);
    boolean stage2 = (a >= 0.55);
    boolean stage3 = (a >= 0.80);

    int[] chord = chordByBar(bar[0]);
    int root = chord[0];

    boolean quarter = (step[0] == 0 || step[0] == 4 || step[0] == 8 || step[0] == 12);
    boolean eighth  = (step[0] % 2 == 0);

    boolean playBase = stage2 ? true : (stage1 ? eighth : quarter);

    if (playBase) {
      float baseVol   = (float) (0.08 + 0.26 * a);
      float basePitch = (float) (0.95 + 0.30 * a);
      player.playSound(player.getLocation(),
          Sound.BLOCK_AMETHYST_BLOCK_CHIME,
          SoundCategory.MASTER,
          baseVol,
          basePitch
      );
    }

    if (stage1) {
      boolean off = (step[0] == 2 || step[0] == 6 || step[0] == 10 || step[0] == 14);
      boolean extra = stage2 && (step[0] == 1 || step[0] == 3 || step[0] == 5 || step[0] == 7
          || step[0] == 9 || step[0] == 11 || step[0] == 13 || step[0] == 15);

      if (off || extra) {
        float v = (float) (airBase * (0.30 + 0.95 * a));
        player.playSound(player.getLocation(),
            Sound.BLOCK_GLASS_HIT,
            SoundCategory.MASTER,
            v,
            (float) (1.00 + 0.18 * a)
        );
        player.playSound(player.getLocation(),
            Sound.BLOCK_AMETHYST_CLUSTER_HIT,
            SoundCategory.MASTER,
            (float) (v * 0.85f),
            (float) (1.00 + 0.25 * a)
        );
      }
    }

    if (stage1) {
      float arpVol = (float) (chimeBase * (0.40 + 1.10 * a));

      boolean playArp = stage2 ? true : (step[0] % 2 == 0);
      if (playArp) {
        int[] arpIdx = {0,2,1,2, 0,2,1,2, 0,2,1,2, 0,2,1,2};
        int tone = chord[arpIdx[step[0] & 15]];

        int octave = stage3 ? 24 : (stage2 ? 12 : 0);
        int semi = tone + octave;

        player.playSound(player.getLocation(),
            Sound.BLOCK_NOTE_BLOCK_CHIME,
            SoundCategory.MASTER,
            arpVol,
            pitchSemi(semi)
        );

        if (stage2 && (step[0] % 4 == 2)) {
          player.playSound(player.getLocation(),
              Sound.BLOCK_NOTE_BLOCK_CHIME,
              SoundCategory.MASTER,
              (float) (arpVol * 0.65f),
              pitchSemi(semi + 7)
          );
        }
      }
    }

    if (stage2) {
      boolean stabMain = (step[0] == 4 || step[0] == 12);
      boolean stabPre  = (step[0] == 3 || step[0] == 11);
      boolean stabFinalExtra = stage3 && (step[0] == 7 || step[0] == 15);

      if (stabMain || stabPre || stabFinalExtra) {

        float stabVol = (float) (stabBase * (0.70 + 1.50 * a));

        if (stabPre) stabVol *= 0.70f;
        if (stabFinalExtra) stabVol *= 0.80f;

        int top = stage3 ? 24 : 12;

        player.playSound(player.getLocation(),
            Sound.BLOCK_NOTE_BLOCK_BELL,
            SoundCategory.MASTER,
            stabVol,
            pitchSemi(chord[0] + top));
        player.playSound(player.getLocation(),
            Sound.BLOCK_NOTE_BLOCK_BELL,
            SoundCategory.MASTER,
            stabVol,
            pitchSemi(chord[1] + top));
        player.playSound(player.getLocation(),
            Sound.BLOCK_NOTE_BLOCK_BELL,
            SoundCategory.MASTER,
            stabVol,
            pitchSemi(chord[2] + top));
      }
    }

    {
      float bassVol = (float) (bassBase * (0.65 + 1.60 * a));

      Integer note = null;

      if (!stage1) {
        if (quarter) note = root - 12;
      } else if (!stage2) {
        if (eighth) note = (step[0] % 4 == 2) ? (root - 12 + 7) : (root - 12);
      } else {
        switch (step[0]) {
          case 0, 8  -> note = root - 12;
          case 2, 10 -> note = root;
          case 4, 12 -> note = (root - 12) + 7;
          case 6     -> note = root;
          case 14    -> note = stage3 ? (root + 12) : root;
        }
      }

      if (note != null) {
        player.playSound(player.getLocation(),
            Sound.BLOCK_NOTE_BLOCK_BASS,
            SoundCategory.MASTER,
            bassVol,
            pitchSemi(note));
      }
    }

    if (stage1) {
      float dv = (float) (drumBase * (0.55 + 1.20 * a));

      if (step[0] == 0 || step[0] == 8) {
        player.playSound(player.getLocation(),
            Sound.BLOCK_NOTE_BLOCK_BASEDRUM,
            SoundCategory.MASTER,
            dv,
            1.00f);
      }

      if (stage2) {
        if (step[0] == 4 || step[0] == 12) {
          player.playSound(player.getLocation(),
              Sound.BLOCK_NOTE_BLOCK_SNARE,
              SoundCategory.MASTER,
              (float) (dv * 0.80f),
              1.00f);
          if (stage3) {
            player.playSound(player.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_SNARE,
                SoundCategory.MASTER,
                (float) (dv * 0.45f),
                1.18f);
          }
        }

        boolean hat = true;

        if (hat) {
          float hatVol = (float) (dv * 0.32f);

          boolean accent = (step[0] == 0 || step[0] == 4 || step[0] == 8 || step[0] == 12);
          boolean preAcc = (step[0] == 3 || step[0] == 11);

          if (accent) hatVol *= 1.25f;
          if (preAcc) hatVol *= 1.10f;

          if (!stage3) hatVol *= 0.92f;

          player.playSound(player.getLocation(),
              Sound.BLOCK_NOTE_BLOCK_HAT,
              SoundCategory.MASTER,
              hatVol,
              1.55f);
        }
      }
    }

    if (stage3 && (step[0] % 4 == 0)) {
      float leadVol = (float) (0.06 + 0.18 * a);
      player.playSound(player.getLocation(),
          Sound.BLOCK_NOTE_BLOCK_CHIME,
          SoundCategory.MASTER,
          leadVol,
          pitchSemi(chord[2] + 24));
      player.playSound(player.getLocation(),
          Sound.BLOCK_NOTE_BLOCK_CHIME,
          SoundCategory.MASTER,
          (float) (leadVol * 0.85f),
          pitchSemi(chord[1] + 24));
    }

    if (step[0] == 0) {
      float v = (float) (0.01 + 0.05 * a);
      float pch = (float) (0.92 + 0.28 * a);
      player.playSound(player.getLocation(),
          Sound.BLOCK_BEACON_POWER_SELECT,
          SoundCategory.MASTER,
          v,
          pch
      );
    }
  }

  public void stop(Player player) {
    BukkitTask task = tasks.remove(player.getUniqueId());
    if (task != null) task.cancel();

    // ✅ ActionBarデバッグを使ってる人だけ、表示が残らないように空送信（ロック中でも消す）
    if (player != null && player.isOnline()) {
      try {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
      } catch (Throwable ignored) {}
    }
  }

  public void stopAll() {
    for (BukkitTask t : tasks.values()) t.cancel();
    tasks.clear();
  }

  private static double clamp01(double v) {
    if (v < 0.0) return 0.0;
    if (v > 1.0) return 1.0;
    return v;
  }

  private int[] chordByBar(int bar) {
    int idx = Math.floorMod(bar, 4);
    return switch (idx) {
      case 0 -> new int[]{0, 3, 7};
      case 1 -> new int[]{-4, -1, 3};
      case 2 -> new int[]{-9, -6, -2};
      default -> new int[]{-2, 1, 5};
    };
  }

  private float pitchSemi(int semitoneOffset) {
    int s = semitoneOffset;
    while (Math.pow(2.0, s / 12.0) < 0.5)  s += 12;
    while (Math.pow(2.0, s / 12.0) > 2.0)  s -= 12;
    return (float) Math.pow(2.0, s / 12.0);
  }
}