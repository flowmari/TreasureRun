package plugin;

import org.bukkit.Bukkit;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public class HeartbeatSoundService {

  private final JavaPlugin plugin;
  private final Map<UUID, BukkitTask> tasks = new HashMap<>();

  // =========================================================
  // ✅ StartThemePlayer に合わせる
  //   - StartThemePlayer: STEP_TICKS=3（16分）
  // =========================================================
  private static final int GRID_TICKS = 3;                 // 16分グリッド
  // ★「ここから強くなる」閾値（以前は“ここから開始”だったが、今は開始から鳴らしつつ 15秒で加速）
  private static final int START_AT_SECONDS = 15;          // 残り15秒から“強化/加速”へ
  private static final SoundCategory CAT = SoundCategory.RECORDS; // BGMと同じカテゴリで混ぜる

  // 心拍の「ドクン」の2発目（lub-dub）の遅れ
  private static final long DUB_DELAY_TICKS = 2L; // 0.1秒（20tick=1秒）

  public HeartbeatSoundService(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  /**
   * ✅ StartThemePlayer と“コード進行”を同期させたい版
   * @param remainingSeconds  残り秒
   * @param isRunning         ゲーム中かどうか
   * @param bgmBarSupplier    StartThemePlayerの現在bar（Session.bar）を渡す
   */
  public void start(Player player,
      IntSupplier remainingSeconds,
      BooleanSupplier isRunning,
      IntSupplier bgmBarSupplier) {

    stop(player);
    UUID uuid = player.getUniqueId();

    // ✅ 追加：開始時点の「総制限時間」をここで確定（Easy/Normal/Hard の timeLimit を自然に拾える）
    // remainingSeconds は startGame() 直後は timeLimit を返す設計のため、ここでスナップショットすればOK
    final int totalSecondsAtStart = Math.max(1, safeGet(remainingSeconds, 180));

    BukkitTask task = new BukkitRunnable() {
      int tick = 0;
      int nextBeatTick = 0;

      @Override
      public void run() {
        if (!player.isOnline() || !isRunning.getAsBoolean()) {
          stop(player);
          cancel();
          return;
        }

        int r = remainingSeconds.getAsInt(); // 残り秒
        if (r < 0) r = 0;

        // =========================================================
        // ✅ “開始から最後まで自然に盛り上がるカーブ”
        //   - 前半：超薄く（ほぼ気配）
        //   - 中盤：ほんの少し存在感
        //   - 終盤：急に強く（ただし最後の15秒は別ロジックで一気に加速）
        // =========================================================
        float volume;
        int targetInterval;

        // ★最終15秒より前：全体進行度(0..1)で“ゆっくり増える”ベース曲線
        if (r > START_AT_SECONDS) {

          // 進行度：開始=0 / 終了=1
          double progress = 1.0 - (r / (double) totalSecondsAtStart);
          progress = clamp01(progress);

          // “序盤はほぼ動かない → 終盤だけ急に上がる”カーブ
          double eased = easeInPow(progress, 2.6);

          // 超薄い→薄い（最終15秒直前で 0.18 くらいまで）
          volume = (float) lerp(0.06, 0.18, eased);

          // 間隔：ゆっくり→少しだけ速い（最終15秒の 12→3 に繋ぐため、ここは 15→12）
          targetInterval = (int) Math.round(lerp(15, 12, eased));

        } else {
          // =========================================================
          // ✅ 最終15秒：従来の 15 / 10 / 5 段階式（スムージング付き）
          //   ※ここは「一気に緊張を上げる」担当
          // =========================================================

          // ★開始直後から鳴らすため：計算用のrを 15秒に“上限クランプ”する
          // ＝残り15秒より前は「ずっと弱い設定」で鳴り続ける（ただし今は上の分岐で吸収される）
          int rr = Math.min(r, START_AT_SECONDS);

          if (rr > 10) {
            // 15..10
            double t = (START_AT_SECONDS - rr) / 5.0; // 15で0, 10で1
            t = clamp01(t);
            volume = (float) lerp(0.18, 0.38, t);
            targetInterval = (int) Math.round(lerp(12, 9, t)); // 12→9
          } else if (rr > 5) {
            // 10..5
            double t = (10 - rr) / 5.0; // 10で0, 5で1
            t = clamp01(t);
            volume = (float) lerp(0.38, 0.68, t);
            targetInterval = (int) Math.round(lerp(9, 6, t));  // 9→6
          } else {
            // 5..0
            double t = (5 - rr) / 5.0; // 5で0, 0で1
            t = clamp01(t);
            volume = (float) lerp(0.68, 1.00, t);
            targetInterval = (int) Math.round(lerp(6, 3, t));  // 6→3
          }
        }

        // ✅ グリッド(3tick)に量子化（BGMと位相がズレないように）
        int interval = quantizeToGrid(targetInterval, GRID_TICKS);
        interval = Math.max(GRID_TICKS, interval);

        // ✅ 次の拍制御（発音タイミングも3tick境界に揃える）
        if (tick >= nextBeatTick) {
          int mod = tick % GRID_TICKS;
          if (mod != 0) {
            nextBeatTick = tick + (GRID_TICKS - mod);
          } else {
            int bar = safeGet(bgmBarSupplier, 0);

            // ※ playBeautifulHeartbeat(..., r, ...) は「残り秒そのもの」を渡したいならそのまま r のままでOK
            // （和音の厚みを「本当の残り時間」によって変えたいので）
            playBeautifulHeartbeat(player, uuid, isRunning, r, bar, volume);

            nextBeatTick = tick + interval;
          }
        }

        tick++;
      }

    }.runTaskTimer(plugin, 0L, 1L);

    tasks.put(uuid, task);
  }

  // 互換用：barが取れない場合は0で回す（Cm固定っぽくなる）
  public void start(Player player, IntSupplier remainingSeconds, BooleanSupplier isRunning) {
    start(player, remainingSeconds, isRunning, () -> 0);
  }

  public void stop(Player player) {
    BukkitTask t = tasks.remove(player.getUniqueId());
    if (t != null) t.cancel();
  }

  public void stopAll() {
    for (BukkitTask t : tasks.values()) t.cancel();
    tasks.clear();
  }

  // =========================================================
  // ✅ 美しさを入れた心拍（lub-dub + 薄い和音）
  // =========================================================
  private void playBeautifulHeartbeat(Player p, UUID uuid, BooleanSupplier isRunning,
      int remainingSec, int bar, float volume) {
    float v = clamp01f(volume);

    // 1) LUB（低いドクン）
    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, CAT, v * 0.90f, 0.80f);

    // 2) DUB（高い成分を少し足して心拍っぽさ）
    //    ※2tick遅れで「ドクン、ドッ」
    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
      if (!p.isOnline()) return;
      if (!tasks.containsKey(uuid)) return;
      if (!isRunning.getAsBoolean()) return;

      // 高域のアタックを少し（うるさくしない）
      p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT,   CAT, v * 0.22f, 1.60f);
      p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, CAT, v * 0.18f, 1.15f);
    }, DUB_DELAY_TICKS);

    // 3) 薄い和音（StartThemePlayerの進行と同じ）
    //    残り時間が少ないほど、和音を少し厚くして“美しさ＋緊張”を上げる
    int[] chord = chordRootByBar(bar); // {root, third, fifth}
    int root  = chord[0];
    int third = chord[1];
    int fifth = chord[2];

    float chordVolBase = v * 0.14f; // あくまで“薄く”
    if (remainingSec <= 5) chordVolBase *= 1.25f;

    // 15..10：rootだけ（綺麗に薄く）
    playChordTone(p, root + 12, chordVolBase * 0.85f);

    // 10..5：root + fifth
    if (remainingSec <= 10) {
      playChordTone(p, fifth + 12, chordVolBase * 0.70f);
    }

    // 5..0：triad（root + third + fifth）
    if (remainingSec <= 5) {
      playChordTone(p, third + 12, chordVolBase * 0.62f);
    }
  }

  private void playChordTone(Player p, int semitoneOffset, float vol) {
    if (vol <= 0.0f) return;

    // “美しさ”担当はBELL/CHIMEあたりが混ざりやすい
    p.playSound(
        p.getLocation(),
        Sound.BLOCK_NOTE_BLOCK_BELL,
        CAT,
        Math.min(1.0f, vol),
        pitchSemi(semitoneOffset)
    );
  }

  // StartThemePlayer と同じ進行（Cm - Ab - Eb - Bb）
  private int[] chordRootByBar(int bar) {
    int idx = Math.floorMod(bar, 4);
    return switch (idx) {
      case 0 -> new int[]{0, 3, 7};    // Cm
      case 1 -> new int[]{-4, -1, 3};  // Ab
      case 2 -> new int[]{-9, -6, -2}; // Eb
      default -> new int[]{-2, 1, 5};  // Bb
    };
  }

  // =========================================================
  // utils
  // =========================================================
  private static int quantizeToGrid(int valueTicks, int gridTicks) {
    return (int) Math.round(valueTicks / (double) gridTicks) * gridTicks;
  }

  private static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }

  private static double clamp01(double x) {
    return Math.max(0.0, Math.min(1.0, x));
  }

  private static float clamp01f(float x) {
    return Math.max(0.0f, Math.min(1.0f, x));
  }

  private static int safeGet(IntSupplier s, int fallback) {
    try { return s.getAsInt(); } catch (Throwable t) { return fallback; }
  }

  // ✅ 追加：序盤を“超薄く”して終盤だけ急に上げるためのイージング
  private static double easeInPow(double x, double pow) {
    x = clamp01(x);
    return Math.pow(x, pow);
  }

  private float pitchSemi(int semitoneOffset) {
    int s = semitoneOffset;
    while (Math.pow(2.0, s / 12.0) < 0.5) s += 12;
    while (Math.pow(2.0, s / 12.0) > 2.0) s -= 12;
    return (float) Math.pow(2.0, s / 12.0);
  }
}