package plugin;

import org.bukkit.Bukkit;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TreasureRun BGM（Synthwave版 / Javaのみ / バニラ音源）
 *
 * ✅ プレイヤーごとに1タスク
 * ✅ フェーズ切り替え（INTRO→EXPLORE→END）
 * ✅ 宝箱開けた瞬間：sparkle単発（今鳴ってるBGMに重ねる）
 * ✅ 「宝物を全部取れなかった」時：失敗ジングル（単発）+ ENDへ
 *
 * 使い方（例）：
 *  - startGameBgm(player)         : ゲーム開始時（INTRO→EXPLORE→END）
 *  - switchToRankingBgm(player)   : ゲーム終了後のランキングBGM用（ENDだけ再生）
 *  - playTreasureSparkle(player)  : 宝箱Openの瞬間
 *  - end(player, allCollected)    : 途中でENDフェーズに切り替えたい場合（内部用）
 *  - stop(player)                 : 強制停止（退出など）
 */
public class StartThemePlayer {

  private final JavaPlugin plugin;

  // プレイヤーごとに1セッション
  private final Map<UUID, Session> running = new HashMap<>();

  // =========================
  // 音量ノブ（SoundCategory.RECORDS）
  // =========================
  private final float drumVol = 0.70f;   // ドラム全体
  private final float bassVol = 0.55f;   // ベース
  private final float padVol  = 0.42f;   // 和音パッド
  private final float arpVol  = 0.55f;   // アルペジオ
  private final float fxVol   = 0.90f;   // キラッ/ジングル

  // =========================
  // テンポ設定（Synthwave向け）
  // =========================
  // 100 BPMだと 16分音符=約0.15秒 → 3tick で綺麗に刻める
  private static final int BPM = 100;
  private static final int STEP_TICKS = 3; // 16分音符1ステップあたり（3tick）
  private static final int STEPS_PER_BAR = 16;

  // INTROは約30秒：100BPMだと 1小節=2.4秒、12小節=28.8秒
  private static final int INTRO_BARS = 12;

  // ENDは短めに（演出用）
  private static final int END_BARS = 8;

  // =========================
  // フェーズ
  // =========================
  public enum Phase { INTRO, EXPLORE, END }

  public StartThemePlayer(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  // =========================
  // 公開API
  // =========================

  /** ✅ ゲーム開始BGM（INTRO → EXPLORE → END まで流れる通常版） */
  public void startGameBgm(Player player) {
    start(player);
  }

  /** ✅ ランキング表示用BGM（END フェーズだけ） */
  public void switchToRankingBgm(Player player) {
    stop(player);

    Session s = new Session(player.getUniqueId());
    s.phase = Phase.END;
    s.bar = 0;
    s.step = 0;
    s.failStingerPlayed = false;



    s.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(player), 0L, STEP_TICKS);
    running.put(player.getUniqueId(), s);
  }

  /** ✅ 追加：カウントダウンの「ピッ」（3,2,1用） */
  public void playCountdownTick(Player player, int count) {
    if (player == null || !player.isOnline()) return;

    // countに応じて少しピッチを変える（3→低め、1→高め）
    float pitch;
    switch (count) {
      case 3 -> pitch = 1.00f;
      case 2 -> pitch = 1.12f;
      case 1 -> pitch = 1.26f;
      default -> pitch = 1.12f;
    }

    // BGMと同じカテゴリで自然に混ざる
    play(player, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.RECORDS, fxVol * 0.35f, pitch);
  }

  /** ✅ 追加：GO! の「起動音」 */
  public void playGoActivate(Player player) {
    if (player == null || !player.isOnline()) return;

    // GO! は「始まる」感を出す（レベルアップ）
    play(player, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.RECORDS, fxVol * 0.55f, 1.35f);

    // 少しだけ追いキラで気持ちよくする（任意）
    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;
      play(player, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.RECORDS, fxVol * 0.40f, 1.60f);
    }, 3L);
  }

  /** ゲーム開始：INTRO→EXPLORE へ自動遷移（内部用） */
  public void start(Player player) {
    stop(player);

    Session s = new Session(player.getUniqueId());
    s.phase = Phase.INTRO;
    s.bar = 0;
    s.step = 0;
    s.failStingerPlayed = false;



    s.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(player), 0L, STEP_TICKS);
    running.put(player.getUniqueId(), s);
  }

  /** 強制停止（退出・中断など） */
  public void stop(Player player) {
    Session old = running.remove(player.getUniqueId());
    if (old != null && old.task != null) old.task.cancel();
  }

  /** 全員停止 */
  public void stopAll() {
    for (Session s : running.values()) {
      if (s.task != null) s.task.cancel();
    }
    running.clear();
  }

  /** 宝箱Openの瞬間に重ねる「キラッ」 */
  public void playTreasureSparkle(Player player) {
    // ここはBGMと独立した単発FX（重ねてもOK）
    play(player, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.RECORDS, fxVol, pitchSemi(12));
    play(player, Sound.BLOCK_NOTE_BLOCK_BELL,  SoundCategory.RECORDS, fxVol, pitchSemi(19));
    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
      if (player.isOnline()) {
        play(player, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.RECORDS, fxVol * 0.85f, pitchSemi(24));
      }
    }, 4L);
  }

  /**
   * ゲーム終了
   * @param allCollected 宝物を全部取れたならtrue、取れなかったならfalse
   */
  public void end(Player player, boolean allCollected) {
    Session s = running.get(player.getUniqueId());
    if (s == null) return;

    // 取れなかった時のジングルを先に鳴らす（1回だけ）
    if (!allCollected && !s.failStingerPlayed) {
      s.failStingerPlayed = true;
      playFailStinger(player);
    }

    s.phase = Phase.END;
    s.bar = 0;
    s.step = 0;
  }

  // =========================
  // 内部：メインtick
  // =========================
  private void tick(Player player) {
    if (player == null || !player.isOnline()) return;

    Session s = running.get(player.getUniqueId());
    if (s == null) return;

    // ✅ tick偶奇/秒偶奇を作る（ランダム減らしの土台）
    s.callCount++;
    s.secondsNow = (int) ((s.callCount * (long) STEP_TICKS) / 20L);
    s.evenSecond = ((s.secondsNow & 1) == 0);

    // 小節頭の処理
    if (s.step == 0) {
      // フェーズ遷移
      if (s.phase == Phase.INTRO && s.bar >= INTRO_BARS) {
        s.phase = Phase.EXPLORE;
        s.bar = 0;
      }
      if (s.phase == Phase.END && s.bar >= END_BARS) {
        // ENDを流し切ったら停止
        stop(player);
        return;
      }

      // 小節頭：パッド（和音）
      playPadChord(player, s);
    }

    // 16分ごとの処理：ドラム・ベース・アルペジオ
    playDrums(player, s);
    playBass(player, s);
    playArp(player, s);

    // =========================================================
    // ✅ 追加①/②：BELLオフビート固定 + EXP_ORB粒（ランダム減）
    // =========================================================
    playPopBellStabAndSparkle(player, s);

    // ステップ進行
    s.step++;
    if (s.step >= STEPS_PER_BAR) {
      s.step = 0;
      s.bar++;
    }
  }

  // =========================
  // パターン（Synthwave）
  // =========================

  /** コード進行（Cm - Ab - Eb - Bb を相対半音で表現） */
  private int[] chordRootByBar(int bar) {
    int idx = Math.floorMod(bar, 4);
    return switch (idx) {
      case 0 -> new int[]{0, 3, 7};    // minor triad
      case 1 -> new int[]{-4, -1, 3};  // Ab-ish
      case 2 -> new int[]{-9, -6, -2}; // Eb-ish
      default -> new int[]{-2, 1, 5};  // Bb-ish
    };
  }

  private void playPadChord(Player p, Session s) {
    float vol = padVol;
    if (s.phase == Phase.INTRO) vol *= 0.85f;
    if (s.phase == Phase.END)   vol *= 1.10f;

    int[] chord = chordRootByBar(s.bar);

    play(p, Sound.BLOCK_NOTE_BLOCK_HARP,  SoundCategory.RECORDS, vol,             pitchSemi(chord[0]));
    play(p, Sound.BLOCK_NOTE_BLOCK_FLUTE, SoundCategory.RECORDS, vol * 0.95f,    pitchSemi(chord[1]));
    play(p, Sound.BLOCK_NOTE_BLOCK_BIT,   SoundCategory.RECORDS, vol * 0.90f,    pitchSemi(chord[2]));

    if (s.phase == Phase.END) {
      play(p, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, vol * 0.8f,    pitchSemi(chord[2] + 12));
    }
  }

  private void playDrums(Player p, Session s) {
    int step = s.step;

    // キック：4つ打ち
    if (step == 0 || step == 4 || step == 8 || step == 12) {
      play(p, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.RECORDS, drumVol, 1.0f);
    }

    // スネア：裏拍
    if (step == 8 || step == 12) {
      float sv = drumVol * (s.phase == Phase.END ? 1.05f : 0.95f);
      play(p, Sound.BLOCK_NOTE_BLOCK_SNARE, SoundCategory.RECORDS, sv, 1.0f);

      // =========================================================
      // 追加③：ゲートっぽいスネア 2発（80s推進）
      //  - ランダム減：tick偶数の秒だけ 2発目を足す
      // =========================================================
      if (s.evenSecond) {
        float a = phaseStrength01(s);
        float gv = (float) (0.03 + 0.10 * a);
        playLater(p, 10L, Sound.BLOCK_NOTE_BLOCK_SNARE, SoundCategory.RECORDS, gv, 1.08f);
        playLater(p, 12L, Sound.BLOCK_NOTE_BLOCK_SNARE, SoundCategory.RECORDS, (float)(gv * 0.72f), 1.18f);
      }
    }

    // ハイハット
    boolean hatOn = (step % 2 == 0);
    if (s.phase == Phase.INTRO) hatOn = (step % 4 == 0);
    if (s.phase == Phase.END)   hatOn = true;

    if (hatOn) {
      float hv = drumVol * 0.40f;
      if (s.phase == Phase.END) hv *= 1.15f;
      play(p, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.RECORDS, hv, 1.35f);
    }
  }

  private void playBass(Player p, Session s) {
    int step = s.step;
    int[] chord = chordRootByBar(s.bar);
    int root = chord[0];

    float vol = bassVol;
    if (s.phase == Phase.INTRO) vol *= 0.80f;
    if (s.phase == Phase.END)   vol *= 1.10f;

    // =========================================================
    // 4) 重圧（パルスベース）：8分で一定（ランダム無し）
    //  - 「前進」を安定させて New Order 寄りへ
    // =========================================================
    if ((step & 1) == 0) { // 16分ステップの偶数 = 8分
      play(p, Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.RECORDS, vol, pitchSemi(root - 12));
    }
  }

  private void playArp(Player p, Session s) {
    int step = s.step;
    int[] chord = chordRootByBar(s.bar);

    float vol = arpVol;
    if (s.phase == Phase.INTRO) vol *= 0.60f;
    if (s.phase == Phase.END)   vol *= 1.10f;

    if (step % 2 != 0) return;

    int tone = chord[(step / 2) % chord.length];
    int semi = tone + 12;

    Sound arpSound = (s.phase == Phase.END)
        ? Sound.BLOCK_NOTE_BLOCK_PLING
        : Sound.BLOCK_NOTE_BLOCK_BIT;

    play(p, arpSound, SoundCategory.RECORDS, vol, pitchSemi(semi));

    if (s.phase == Phase.END && step == 8) {
      play(p, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.RECORDS, vol * 0.85f, pitchSemi(semi + 7));
    }
  }

  // =========================================================
  // 追加①：BELLコード・スタブ（オフビート固定） + 追加②：EXP_ORB粒
  // =========================================================
  private void playPopBellStabAndSparkle(Player player, Session s) {
    float a = phaseStrength01(s);
    int step = s.step;

    // 4つ打ちの拍で「次のオフビート(10tick後)」に刺す
    if (step == 0 || step == 4 || step == 8 || step == 12) {

      // =========================================================
      // 追加：POPなコード・スタブ（BELL）= “Bizarre Love Triangle感”
      //  - ランダム無し：オフビート(10tick)で固定
      //  - 2種類をtick偶奇で交互（ランダム無しの“進行感”）
      // =========================================================
      float stabVol = (float) (0.03 + 0.12 * a);

      // tick偶奇でコードを交互に（固定パターン）
      if ((s.secondsNow & 1) == 0) {
        // 明るめ
        playLater(player, 10L, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, stabVol, 1.00f);
        playLater(player, 10L, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, stabVol, 1.26f);
        playLater(player, 10L, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, stabVol, 1.50f);
      } else {
        // 少し落ち着いた別ボイシング
        playLater(player, 10L, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, stabVol, 0.94f);
        playLater(player, 10L, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, stabVol, 1.19f);
        playLater(player, 10L, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, stabVol, 1.41f);
      }
    }

    // =========================================================
    // 追加：スパークル粒（EXP_ORB）= キラキラの“フック”
    //  - ランダム減：tick偶数の秒だけ鳴らす
    // =========================================================
    if (s.lastSparkleSecond != s.secondsNow) {
      s.lastSparkleSecond = s.secondsNow;

      if (s.evenSecond) {
        float sv = (float) (0.01 + 0.05 * a); // 小さめ推奨
        playLater(player, 2L, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.RECORDS, sv, 1.55f);
        playLater(player, 7L, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.RECORDS, (float)(sv * 0.90f), 1.85f);
      }
    }
  }

  // =========================
  // 失敗ジングル（宝物を全部取れなかった）
  // =========================
  private void playFailStinger(Player p) {
    play(p, Sound.BLOCK_NOTE_BLOCK_BASEDRUM,   SoundCategory.RECORDS, fxVol * 0.70f, 0.8f);
    play(p, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, SoundCategory.RECORDS, fxVol * 0.65f, pitchSemi(-12));

    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
      if (!p.isOnline()) return;
      play(p, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, SoundCategory.RECORDS, fxVol * 0.55f, pitchSemi(-15));
      play(p, Sound.BLOCK_NOTE_BLOCK_BASS,       SoundCategory.RECORDS, fxVol * 0.45f, pitchSemi(-19));
    }, 6L);

    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
      if (!p.isOnline()) return;
      play(p, Sound.BLOCK_NOTE_BLOCK_FLUTE, SoundCategory.RECORDS, fxVol * 0.35f, pitchSemi(-7));
    }, 12L);
  }

  // =========================
  // ユーティリティ
  // =========================
  private void play(Player p, Sound sound, SoundCategory cat, float vol, float pitch) {
    p.playSound(p.getLocation(), sound, cat, vol, pitch);
  }

  // ✅ 追加：オフビート固定など用（10tick後など）
  private void playLater(Player player, long delayTicks, Sound sound, SoundCategory cat, float vol, float pitch) {
    if (player == null) return;
    if (vol <= 0.0f) return;

    final float safeVol = vol;
    final float safePitch = (pitch <= 0.0f) ? 0.01f : pitch;

    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;
      play(player, sound, cat, safeVol, safePitch);
    }, delayTicks);
  }

  private float pitchSemi(int semitoneOffset) {
    int s = semitoneOffset;
    while (Math.pow(2.0, s / 12.0) < 0.5)  s += 12;
    while (Math.pow(2.0, s / 12.0) > 2.0)  s -= 12;
    return (float) Math.pow(2.0, s / 12.0);
  }

  // ✅ “a”の代替：フェーズ強度（0..1）
  private float phaseStrength01(Session s) {
    if (s.phase == Phase.INTRO) return 0.70f;
    if (s.phase == Phase.EXPLORE) return 0.85f;
    return 1.00f;
  }

  // =========================
  // セッション
  // =========================
  private static class Session {
    final UUID uuid;
    Phase phase = Phase.INTRO;
    int bar = 0;
    int step = 0;
    boolean failStingerPlayed = false;
    BukkitTask task;

    // ✅ 追加：tick偶奇 / 秒偶奇のため
    long callCount = 0;
    int secondsNow = 0;
    boolean evenSecond = false;

    // ✅ 追加：EXP_ORBを「1秒に1回」に抑える
    int lastSparkleSecond = -1;

    Session(UUID uuid) {
      this.uuid = uuid;
    }
  }
}