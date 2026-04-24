package plugin;

import plugin.i18n.OutcomeMessageKeys;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class OutcomeMessageService {

  private final TreasureRunMultiChestPlugin plugin;
  private final Random random = new Random();

  public OutcomeMessageService(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  /**
   * 結果(outcome) と 難易度(difficulty) から、SUBTITLE用の短文をランダムで1つ返す。
   * difficulty は "Easy" / "Normal" / "Hard" など、既存の文字列に合わせる（containsで判定）。
   *
   * NOTE:
   *  - ここは「最大2行」前提でしたが、ユーザー指定により 3行以上の詩ブロックも含めています。
   *  - Minecraft側の表示制限（字幕の幅/行数）により見切れる可能性があります。
   */
  public String pickSubtitle(GameOutcome outcome, String difficulty, String lang) {
    String d = normalizeDifficulty(difficulty);

    List<String> pool = switch (outcome) {
      case SUCCESS -> localizedSuccessPool(d, lang);
      case TIME_UP -> localizedTimeUpPool(d, lang);
    };

    if (pool == null || pool.isEmpty()) return "";
    return pool.get(random.nextInt(pool.size()));
  }

  /**
   * ✅ 行商人（Merchant / WanderingTrader）交換時の短文
   * ※ GameOutcome を増やさずに使えるよう、別メソッドにしています。
   */
  public String pickTraderSubtitle(String difficulty) {
    String d = normalizeDifficulty(difficulty);
    List<String> pool = traderPool(d);
    if (pool == null || pool.isEmpty()) return "";
    return pool.get(random.nextInt(pool.size()));
  }

  private String normalizeDifficulty(String difficulty) {
    if (difficulty == null) return "NORMAL";
    String s = difficulty.trim().toUpperCase(Locale.ROOT);
    if (s.contains("EASY")) return "EASY";
    if (s.contains("HARD")) return "HARD";
    return "NORMAL";
  }

  private List<String> configuredPool(String lang, String key) {
    List<String> list = plugin.getI18n().trList(lang, key);
    if (list == null || list.isEmpty()) return List.of();

    List<String> out = new ArrayList<>();
    for (String s : list) {
      if (s == null) continue;
      String t = s.trim();
      if (!t.isEmpty() && !t.equals(key) && !t.startsWith("Translation missing:")) {
        out.add(s);
      }
    }
    return out;
  }

  private int expectedSuccessCount(String d) {
    return switch (d) {
      case "EASY" -> 17;
      case "NORMAL" -> 35;
      case "HARD" -> 32;
      default -> 0;
    };
  }

  private int expectedTimeUpCount(String d) {
    return switch (d) {
      case "EASY" -> 16;
      case "NORMAL" -> 20;
      case "HARD" -> 38;
      default -> 0;
    };
  }


  private List<String> localizedSuccessPool(String d, String lang) {
    List<String> common = configuredPool(lang, OutcomeMessageKeys.OUTCOME_SUCCESS_COMMON_POOL);
    List<String> specific = switch (d) {
      case "EASY" -> configuredPool(lang, OutcomeMessageKeys.OUTCOME_SUCCESS_EASY_POOL);
      case "NORMAL" -> configuredPool(lang, OutcomeMessageKeys.OUTCOME_SUCCESS_NORMAL_POOL);
      case "HARD" -> configuredPool(lang, OutcomeMessageKeys.OUTCOME_SUCCESS_HARD_POOL);
      default -> List.of();
    };

    List<String> out = new ArrayList<>(common.size() + specific.size());
    out.addAll(common);
    out.addAll(specific);

    int expected = expectedSuccessCount(d);
    if (out.size() < expected) {
      List<String> legacy = successPool(d);
      if (legacy != null && !legacy.isEmpty()) {
        out.addAll(legacy);
      }
      if (plugin != null) {
        plugin.getLogger().warning("[Outcome] SUCCESS pool too small: lang=" + lang
            + " difficulty=" + d
            + " expected>=" + expected
            + " actual=" + out.size()
            + " (after legacy merge)");
      }
    }

    if (!out.isEmpty()) return out;
    return successPool(d); // legacy fallback
  }

  private List<String> localizedTimeUpPool(String d, String lang) {
    List<String> out = new ArrayList<>();

    switch (d) {
      case "EASY" -> out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_TIMEUP_EASY_POOL));
      case "NORMAL" -> out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_TIMEUP_NORMAL_POOL));
      case "HARD" -> out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_TIMEUP_HARD_POOL));
    }

    int expected = expectedTimeUpCount(d);
    if (out.size() < expected) {
      List<String> legacy = timeUpPool(d);
      if (legacy != null && !legacy.isEmpty()) {
        out.addAll(legacy);
      }
      if (plugin != null) {
        plugin.getLogger().warning("[Outcome] TIMEUP pool too small: lang=" + lang
            + " difficulty=" + d
            + " expected>=" + expected
            + " actual=" + out.size()
            + " (after legacy merge)");
      }
    }

    if (!out.isEmpty()) return out;
    return timeUpPool(d); // legacy fallback
  }

  // =========================================================
  // ✅ 追加：特定フレーズ群を「他より3倍」出やすくする
  // - base に1回入っている前提で、(factor-1) 回ぶん追加して合計 factor 回にする
  // =========================================================
  private List<String> boost(List<String> base, List<String> boosted, int factor) {
    if (base == null) return List.of();
    if (boosted == null || boosted.isEmpty() || factor <= 1) return base;

    int extra = factor - 1;
    List<String> out = new ArrayList<>(base.size() + boosted.size() * extra);
    out.addAll(base);

    for (String s : boosted) {
      for (int i = 0; i < extra; i++) out.add(s);
    }
    return out;
  }

  // =========================================================
  // ✅ 追加：3倍を“絶対に”保証する（baseに無くても最終的に factor 回に揃える）
  // - base 内の出現回数を数え、boosted の各文が factor 回未満なら不足分だけ足す
  // =========================================================
  private List<String> boostToExactFactor(List<String> base, List<String> boosted, int factor) {
    if (base == null) return List.of();
    List<String> out = new ArrayList<>(base);
    if (boosted == null || boosted.isEmpty() || factor <= 1) return out;

    Map<String, Integer> cnt = new HashMap<>();
    for (String s : out) cnt.merge(s, 1, Integer::sum);

    for (String s : boosted) {
      int have = cnt.getOrDefault(s, 0);
      for (int i = have; i < factor; i++) out.add(s); // 足りない分だけ足す
      cnt.put(s, Math.max(have, factor));
    }
    return out;
  }

  // =========================================================
  // ✅ 指定フレーズ（3倍にしたい“対象群”）をまとめて定義
  // =========================================================
  private static final List<String> BOOST_SUCCESS_HIGH = List.of(
      "Light fell evenly, ,\nand every shadow vanished.\n（光が等しく降り注ぎ、すべての影が消えた。）",
      "The wind fell silent.\nand everything returned to where it belonged.\n（風が止み、あるべき場所に、あるべきものが戻った。）",
      "A moment of silence.\nThe world steadied its breath.\n（一瞬の静寂。世界がその呼吸を整えた。）",
      "The record is etched deep in the sand—\nbeyond the reach of the wind.\n（記録は砂に深く刻まれ、風もそれを動かせない。）",
      "Harmony was restored.\nEverything had been for this moment.\n（調和が訪れた。すべては、この瞬間のためにあった。）"
  );

  private static final List<String> BOOST_SUCCESS_MID = List.of(
      "Time slipped by in silence,\ntucked away in the corners of memory.\n（時間は静かに流れ、記憶の隅に収まった。）",
      "The sky shifted in color,\nand one story came to its end.\n（空の色が変わり、一つの物語が完結を告げた。）",
      "The waves grew calm,\nand the sea hid its depths once more.\n（波は穏やかになり、海は再びその深淵を隠した。）",
      "The footsteps stopped,\nand only the place remained.\n（歩みは止まり、場所だけがそこに残された。）"
  );

  private static final List<String> BOOST_TIMEUP_FAIL = List.of(
      "The sun sank,\nand shadow covered the world once again.\n（日は落ち、影は再び世界を覆った。）",
      "The last grains of sand fell,\nand the story quietly came to a close.\n（砂はすべて落ちきり、物語は静かに幕を閉じた。）",
      "The wind swept through,\nand the footprints vanished without a trace.\n（風が吹き抜け、足跡は跡形もなく消え去った。）",
      "The tide came in,\nWhat once had been there returned to the sea.\n（潮が満ちた。かつてそこにあったものは、海へと還った。）",
      "Silence.\nOnly time passed through.\n（沈黙。ただ、時間だけがそこを通り過ぎた。）"
  );

  private static final List<String> BOOST_TRADER = List.of(
      "Perfect balance.\nThe scales held level.\n（等価の均衡。天秤は水平を保った。）",
      "Things changed,\ntaking on new forms as they carried on.\n（物事は移ろい、形を変えて引き継がれた。）",
      "There was no dialogue\n—only an exchange of facts.\n（対話はなく、ただ事実としての交換が行われた。）"
  );

  private static final List<String> BOOST_PHILOSOPHY = List.of(
      "Victory and defeat \nare nothing more than grains of sand.\n（勝利も敗北も、同じ砂の一粒に過ぎない。）",
      "The road goes on—or it ends.\nEither way, it lies on the same earth.\n（道は続き、あるいは途切れ、どちらも同じ大地の上にある。）"
  );

  private static final List<String> BOOST_SUCCESS_NORMAL_BASE17 = List.of(
      "Distance became meaning.\nTime answered your steps.\n（距離が意味になった。時間があなたの歩みに答えた。）",
      "Time ran out.\nNothing else did.\n（時間は終わった。ほかは終わっていない。）",
      "The center was found.\nThe world came to rest.\n（軸(中心)は見出された。世界はその運動を止めた。）",
      "The glass didn’t break.\nNeither did you.\n（ガラスは割れなかった。あなたも折れなかった。）",
      "Beneath a quiet sky,\nyour will held firm.\n（静かな空の下で、意志は持ちこたえた。）",
      "Very little shines forever\n—but this moment was different.\n（永遠に輝くものなんてほとんどないけれど、この瞬間は別格だった。）",
      "You made it across the fragile ground,\nholding fast to the horizon the whole way.\n（脆い地面を渡り切った。地平線景色をしっかりと握りしめたまま。）",
      "The moment passed.\n What remained was the record—the achievement.\n（瞬間は過ぎ去った。遺されたもの記録・功績は残る。）",
      "Luck fades quickly.\nOrder endures.\n（運は速やかに消え、整い秩序は永続する。）",
      "The sky knows no rest in its endless cycle.\nNeither do you.\n（空はその終わりなき循環に休止を知らぬ。汝もまた、止まらず。）",
      "The center was reached.\nThe world stood still.\n（中心は到達された。世界は立ち尽くし静止した。）",

      "At last,you’ve made it here.\nThe truth stands—unbowed.\n（ついに、ここへ辿り着いた。真実は屈することなく、そこに在る。）",
      "Complete. This path was yours\n—and yours alone.\n（完遂。この道を選んだのは他でもないあなただ。）",

      "Time drifted into silence,\nand found its place in the deepest corners of the mind.\n（時は静寂へと漂い、精神の奥底の隅に自らの居場所を見つけた。）",
      "As the sky shifted,\none story found its final resolution.\n（空が姿を変えるとともに、一つの物語はその最終的な解決完結を見出した。）",
      "The waves fell quiet,\nand the sky hid its depths once more.\n（波は静まり、空は再びその深淵を覆い隠した。）",
      "The footsteps ceased,\nand only the place remained.\n（歩みは途絶え、ただ場所だけがそこに在り続けた。）"
  );

  // =========================================================
  // SUCCESS（RUN COMPLETE）
  // =========================================================
  private List<String> successPool(String d) {
    return switch (d) {

      // -------------------------
      // EASY（✅ SUCCESS 高ランク/中ランク/哲学 を “他より3倍”）
      // -------------------------
      case "EASY" -> {
        List<String> base = List.of(
            "You’ve made it.\nThe truth remains.\n（たどり着いた。正さはそのままに。）",
            "Even if it was only a small step,\nyou held your ground.\n（小さな一歩でも、あなたは守り抜いた。）",
            "You listened.\n The path opened.\n（耳を澄ませた。道がひらいた。）",

            "> You made it here\n" +
                "> \n" +
                "> The rules were simple —\n" +
                "> \n" +
                "> the choice was yours.\n" +
                "\n" +
                "（あなたは辿り着いた。\n" +
                "\n" +
                "ルールは単純だった。\n" +
                "\n" +
                "選んだのは、あなた自身。）",

            "> Distance became meaning.\n" +
                "> \n" +
                "> and time answered your footsteps.\n" +
                "\n" +
                "（距離が意味になり、\n" +
                "\n" +
                "時間があなたの足取りに応えた。）",

            "> Not everything shines forever.\n" +
                "> \n" +
                "> But this moment did.\n" +
                "\n" +
                "（すべてが永遠に輝くわけじゃない。\n" +
                "\n" +
                "でも、この瞬間はそうだった。）",

            // ✅ SUCCESS “高ランク” 5文（指定）
            BOOST_SUCCESS_HIGH.get(0),
            BOOST_SUCCESS_HIGH.get(1),
            BOOST_SUCCESS_HIGH.get(2),
            BOOST_SUCCESS_HIGH.get(3),
            BOOST_SUCCESS_HIGH.get(4),

            // ✅ SUCCESS “中ランク” 4文（指定）
            BOOST_SUCCESS_MID.get(0),
            BOOST_SUCCESS_MID.get(1),
            BOOST_SUCCESS_MID.get(2),
            BOOST_SUCCESS_MID.get(3),

            // ✅ 汎用的な哲学（指定）
            BOOST_PHILOSOPHY.get(0),
            BOOST_PHILOSOPHY.get(1)
        );

        List<String> boosted = new ArrayList<>();
        boosted.addAll(BOOST_SUCCESS_HIGH);
        boosted.addAll(BOOST_SUCCESS_MID);
        boosted.addAll(BOOST_PHILOSOPHY);

        yield boost(base, boosted, 3);
      }

      // -------------------------
      // NORMAL（✅ SUCCESS 高ランク/中ランク/哲学 を “他より3倍”）
      // -------------------------
      case "NORMAL" -> {
        List<String> base = List.of(
            "The distance has found its meaning.\nTime has rewarded your journey.\n（距離が意味になった。時間があなたの歩みに答えた。）",
            "The time is up.\nbut the rest is far from over.\n（時間は終わった。ほかは終わっていない。）",
            "The axis is found.\nthe world has cased its motion.\n（軸(中心)は見出された。世界はその運動を止めた。）",
            "The glass held its breath,\nand you held your ground.\n（ガラスは割れなかった。あなたも折れなかった。）",
            "Under the stillness of the sky,\nyour spirit did not waver.\n（静かな空の下で、意志は持ちこたえた。）",
            "Few things shine forever.\nbut this moment was radiant.\n（永遠に輝くものなんてほとんどないけれど、この瞬間は別格だった。）",
            "You traversed the fragile earth,\nwithout losing the view.\n（脆い地面を渡り切った。地平線景色をしっかりと握りしめたまま。）",
            "The moment has passed.\nThe legacy remains.\n（瞬間は過ぎ去った。遺されたもの記録・功績は残る。）",
            "Luck fades fast.\norder endures.\n（運は速やかに消え、整い秩序は永続する。）",
            "The sky knows no pause in endless circle,\nnor shall you find yours.\n（空はその終わりなき循環に休止を知らぬ。汝もまた、止まらず。）",
            "The center has been reached.\nThe world stood still.\n（中心は到達された。世界は立ち尽くし静止した。）",

            "Finally here.\nThe truth remains unyielding.\n（ついに、ここへ辿り着いた。真実は屈することなく、そこに在る。）",
            "You have seen it through.\nAnd mark this: it was you, and no one else, who chose this path.\n（完遂。この道を選んだのは他でもないあなただ。）",

            // ✅ 成功・中ランク（指定）
            "Time drifted into silence,\nfinding its place in the recesses of the mind.\n（時は静寂へと漂い、精神の奥底の隅に自らの居場所を見つけた。）",
            "As the sky transformed,\nthe tale found its final resolved.\n（空が姿を変えるとともに、一つの物語はその最終的な解決完結を見出した。）",
            "The waters fell calm,\nand the sky once more veiled its abyss.\n（波は静まり、空は再びその深淵を覆い隠した。）",
            "The footsteps ceased,\nonly the place remained.\n（歩みは途絶え、ただ場所だけがそこに在り続けた。）",

            // ✅ 成功・高ランク（指定）— NORMALにも1回入れる（= 3倍対象にするため）
            BOOST_SUCCESS_HIGH.get(0),
            BOOST_SUCCESS_HIGH.get(1),
            BOOST_SUCCESS_HIGH.get(2),
            BOOST_SUCCESS_HIGH.get(3),
            BOOST_SUCCESS_HIGH.get(4),

            // RUN COMPLETE 短文セット
            "RUN COMPLETE.\nClear. Found it.\nThat's enough.\n（クリア。見つけた。それで十分。）",
            "RUN COMPLETE.\nThe path held.\nSo did you.\n（道は持ちこたえた。あなたも同じ。）",
            "The sky has kept moving.\nSo can you.\n（空はずっと歩み続けていた。あなたも進み続けられる。）",
            "RUN COMPLETE.\nA quiet result.\nThe weight of real work.\n（静かな結果。本物の積み重ね。）",

            // 哲学パック（S1–S5）
            "You only walked the path\nthe world had laid out for you.\n（世界が用意していた道を、あなたは歩いただけ。）",
            "The ground didn’t give way.\nIt trusted your steps.\n（大地は壊れなかった。あなたの足取りを、信じていた。）",
            "This moment aligned\nwith the stars’ ancient rhythm.\n（この瞬間は、星々の長いリズムと重なった。）",
            "You didn’t take anything.\n You understood something.\n（何かを奪ったわけじゃない。何かを、理解した。）",
            "You didn’t gain a thing.\nYou only saw the truth for what it was.\n（手に入れたのは何かではない。真実を、見抜いただけだ。）",
            "You didn’t take this place.\nYou belonged here.\n（征服したのではない。ここに、属していた。）",

            // 成功時（ゲームに勝ったとき）【5】
            "You walked a road\nolder than memory itself.\n（記憶より古い道を、あなたは辿った。）",
            "The earth did not resist.\nIt recognized you.\n（大地は抗わず、あなたを認識した。）",
            "This moment aligned\n with the stillness of the stars.\n（この瞬間は、星々の静けさと重なった。）",
            "No conquest.\nUnderstanding was enough.\n（征服はない。理解で、十分だった。）",
            "You didn’t claim it.\nYou were already home.\n（ここを奪ったのではない。ここに、属していた。）",

            // ✅ 汎用的な哲学（指定）
            BOOST_PHILOSOPHY.get(0),
            BOOST_PHILOSOPHY.get(1)
        );

        List<String> boosted = new ArrayList<>();
        boosted.addAll(BOOST_SUCCESS_HIGH);
        boosted.addAll(BOOST_SUCCESS_MID);
        boosted.addAll(BOOST_PHILOSOPHY);

        // ★追加：この17文も「最終的に必ず3回」にする
        boosted.addAll(BOOST_SUCCESS_NORMAL_BASE17);

        yield boostToExactFactor(base, boosted, 3);
      }

      // -------------------------
      // HARD（✅ SUCCESS 高ランク/中ランク/哲学 を “他より3倍”）
      // -------------------------
      case "HARD" -> {
        List<String> base = List.of(
            "In the north wind,\nclarity outlasts speed.\n（北風の中で、速さより明晰さが生き残る。）",
            "The center was found.\n The world came to rest.\n（軸(中心)は見出された。世界はその運動を止めた。）",
            "The stars stay silent.\nYour record does the speaking.\n（星は黙っている。語るのはあなたの記録だ。）",
            "The result  remained.\nThe position changed.\n（結果は残った。位置は変わった。）",
            "Between ice and glass,\nyou chose control.\n（氷とガラスのあいだで、あなたは制御を選んだ。）",
            "The waves fell quiet,\nand the sea reclaimed its hidden depths.\n（波は静まり、海はその隠された深淵を再び自らのものとした。）",
            "Some treasures still await.\nNext time, a different path.\n（待つ宝もある。次は、別の道を。）",
            "The sky didn’t change.\nYou did.\n（空は変わらなかった。変わったのはあなた。）",
            "The moment passed.\nWhat remained was order.\n（瞬間は過ぎた。整いは残った。）",
            "Winter is honest.\nSo was your run.\n（冬は正直だ。あなたの走りもそうだった。）",
            "The stars aligned for a moment.\nYou noticed.\n（星は束の間そろった。あなたはそれに気づいた。）",

            "The distance closed.\nTime did not.\n（距離は縮んでいた。時間は縮まらなかった。）",
            "The world’s rules stood firm.\n And the signal of possibility was real.\n（世界のルールは、厳然としてそこにあった。可能性（信号）は本物だった。）",
            "The path closed quietly.\nYou were already through.\n（道は静かに閉じた。あなたはもう抜けていた。）",

            "Nothing changed.\nOnly your position did.\n（何も変わらない。ただ、あなたの位置だけが変わった。）",
            "Not luck.\nAlignment.\n（運じゃない。整いだ。）",
            "The moment passed.\nThe record remains.\n（瞬間は過ぎた。記録は残る。）",

            "The outcome didn’t change.\nStill, you moved forward.\n（結果に違いはなかった。それでも、あなたは前に進んだ。）",
            "The output didn’t change.\nBut you reached a new level.\n（出力は変わらないままだが、あなたは新しいレベルに到達した。）",
            "The ending was the same.\nBut the one who reached it had changed.\n（結末は同じだが、そこに辿り着いた人間は変わった。）",
            "The destination is the same\n—but you’re no longer the traveler you once were.\n（目的地は同じだが、あなたはもう以前と同じ旅人ではない。）",

            // ✅ 成功・高ランク（指定）
            BOOST_SUCCESS_HIGH.get(0),
            BOOST_SUCCESS_HIGH.get(1),
            BOOST_SUCCESS_HIGH.get(2),
            BOOST_SUCCESS_HIGH.get(3),
            BOOST_SUCCESS_HIGH.get(4),

            // ✅ 成功・中ランク（指定）— HARDにも1回入れる（= 3倍対象にするため）
            BOOST_SUCCESS_MID.get(0),
            BOOST_SUCCESS_MID.get(1),
            BOOST_SUCCESS_MID.get(2),
            BOOST_SUCCESS_MID.get(3),

            // ✅ 汎用的な哲学（指定）
            BOOST_PHILOSOPHY.get(0),
            BOOST_PHILOSOPHY.get(1),

            // ブロック引用（あなた指定）
            "> Distance became meaning,\n" +
                "> \n" +
                "> and time answered your footsteps.\n" +
                "\n" +
                "（距離が意味になり、\n" +
                "\n" +
                "時間があなたの足取りに応えた。）",

            "> Not everything shines forever.\n" +
                "> \n" +
                "> But this moment did.\n" +
                "\n" +
                "（すべてが永遠に輝くわけじゃない。\n" +
                "\n" +
                "でも、この瞬間はそうだった。）"
        );

        List<String> boosted = new ArrayList<>();
        boosted.addAll(BOOST_SUCCESS_HIGH);
        boosted.addAll(BOOST_SUCCESS_MID);
        boosted.addAll(BOOST_PHILOSOPHY);

        yield boost(base, boosted, 3);
      }

      default -> List.of("Run complete.");
    };
  }

  // =========================================================
  // TIME_UP（TIME’S UP）
  // =========================================================
  private List<String> timeUpPool(String d) {
    return switch (d) {

      // -------------------------
      // EASY（✅ TIME_UP 失敗5文＋哲学2文 を “他より3倍”）
      // -------------------------
      case "EASY" -> {
        List<String> base = List.of(
            "Time ran out.\nNothing else did.\n（時間は終わった。ほかは終わっていない。）",
            "The treasure can wait.\nSo can you.\n（宝は待っている。あなたも待てる。）",
            "Breathe in.\nThen again.\n（息をして。もう一度。）",
            "That’s all for now.\nOn to the next step.\n（今回はここまで。次の一歩へ。）",
            "You're still here.\nThat’s what matters.\n（まだここにいる。それが大事。）",
            "The run ended.\nYou didn’t.\n（走りは止まった。あなたは止まっていない。）",

            "TIME'S UP.\nThe run ended.\nYou didn't.\n（時間は止まった。でも、あなたは止まっていない。）",
            "TIME'S UP.\nThe rules don’t change.\nAgain.\n（ルールは変わらない。もう一度。）",
            "TIME'S UP.\n“So close” is real.\n（惜しい、は本物だ。）",

            // ✅ 失敗・時間切れ（指定）— EASYにも1回入れる（= 3倍対象にするため）
            BOOST_TIMEUP_FAIL.get(0),
            BOOST_TIMEUP_FAIL.get(1),
            BOOST_TIMEUP_FAIL.get(2),
            BOOST_TIMEUP_FAIL.get(3),
            BOOST_TIMEUP_FAIL.get(4),

            // ✅ 汎用的な哲学（指定）
            BOOST_PHILOSOPHY.get(0),
            BOOST_PHILOSOPHY.get(1)
        );

        List<String> boosted = new ArrayList<>();
        boosted.addAll(BOOST_TIMEUP_FAIL);
        boosted.addAll(BOOST_PHILOSOPHY);

        yield boost(base, boosted, 3);
      }

      // -------------------------
      // NORMAL（失敗・時間切れ5文＋哲学2文を“他の全てより3倍”）
      // -------------------------
      case "NORMAL" -> {
        List<String> base = List.of(
            "The distance closed.\nTime did not.\n（距離は縮んだ。時間は縮まらなかった。）",
            "The world kept its rules.\nYou touched the edge.\n（世界はルールを守った。あなたは境界に触れた。）",
            "You didn’t make it.\nBut the signal was real.\n（届かなかった。でも合図は本物だった。）",
            "Some treasures still await.\nLet’s try a different path.\n（待つ宝もある。別の道を試そう。）",
            "The result remained.\nThe position changed.\n（結果は残った。位置は変わった。）",
            "Time ran out.\nMeaning didn't.\n（時間は終わった。意味は終わっていない。）",
            " So close.\nThe sound said so.\n（惜しかった。音がそう言っていた。）",
            "The map is still open.\nCome back to it.\n（地図は開いたまま。戻っておいで。）",

            "The world’s rules stood firm.\nThe signal of possibility was real.\n（世界のルールは厳然としてそこにあった。可能性（信号）は本物だった。）",

            "The world kept its rules.\n（世界はルールを守った。）",
            "The moment passed.\nThe record remains.\n（瞬間は過ぎた。記録は残る。）",

            // ✅ 失敗・時間切れ（指定）
            BOOST_TIMEUP_FAIL.get(0),
            BOOST_TIMEUP_FAIL.get(1),
            BOOST_TIMEUP_FAIL.get(2),
            BOOST_TIMEUP_FAIL.get(3),
            BOOST_TIMEUP_FAIL.get(4),

            // ✅ 汎用的な哲学（指定）
            BOOST_PHILOSOPHY.get(0),
            BOOST_PHILOSOPHY.get(1),

            // ブロック引用（あなた指定）
            "> Time ran out.\n" +
                "> \n" +
                "> Nothing else did.\n" +
                "\n" +
                "（時間は終わった。\n" +
                "\n" +
                "それ以外は、終わっていない。）",

            "> The outcome didn’t change.\n" +
                "> \n" +
                "> But your position did.\n" +
                "\n" +
                "（結果は変わらなかった。\n" +
                "\n" +
                "でも、あなたの位置は変わった。）"
        );

        List<String> boosted = new ArrayList<>();
        boosted.addAll(BOOST_TIMEUP_FAIL);
        boosted.addAll(BOOST_PHILOSOPHY);

        yield boost(base, boosted, 3);
      }

      // -------------------------
      // HARD（失敗・時間切れ5文＋哲学2文を“他の全てより3倍”）
      // -------------------------
      case "HARD" -> {
        List<String> base = List.of(
            "Even the aurora fades.\nCome back with a new stride.\n（オーロラでさえ消える。新しい歩みで戻れ。）",
            "In a fragile world,\nhaste shatters clarity before it can be found.\n（脆い世界では、焦りが明晰さの発見を壊してしまう。）",
            "The moment slipped away.\nThe meaning did not.\n（逃したのは瞬間。意味ではない。）",
            "Winter is patient.\nBe the same.\n（冬は忍耐強い。あなたもそうであれ。）",
            "The sky kept moving.\nSo will you.\n（空は進み続けた。あなたも進む。）",
            "The story moves on.\nThe end is not yet.\n（叙事は頁をめくる。これは最後じゃない。）",
            "The stars were there, watching.\nAgain.\n（星は見守っていた。もう一度。）",
            "Ice takes its time.\nNeither do you.\n（氷は急がない。あなたも急ぐ必要はない。）",
            "The sky kept moving.\nSo can you.\n（空は動き続けた。あなたも動き続けられる。）",
            "The north has learned to wait.\nSo should you.\n（北は『待つこと』を覚えている。そこから学べ。）",
            "This is an interlude\n—It’s not over.\n（これは休止だ。終わりじゃない。）",

            "Like drifting clouds,\ndon’t lose your flow—like a river.\n（雲が動き続けるように。川のように、流れを失わないで。）",

            "The sound was near.\nThe distance was real.\nBut that moment can’t be saved.\n（音は近かった。距離は本物だった。でも、その瞬間は保存できない。）",
            "You were closing in.\nOnly time wasn’t.\n（近づいていた。時間だけが近づかなかった。）",
            "The world held to its rules.\nYou touched the edge of them.\n（世界はルールを守った。あなたはその縁に触れた。）",
            "You didn’t reach the treasure. \nBut the signal was real.\n（宝には届かなかった。でも合図は本物だった。）",

            "Nothing changed.\nOnly your position did.\n（何も変わらない。ただ、あなたの位置だけが変わった。）",
            "It wasn’t luck.\nAlignment.\n（運じゃない。整いだ。）",
            "The moment slipped away,\nbut the record stayed.\n（瞬間は過ぎた。記録は残る。）",

            "You crossed fragile ground\nand left the horizon unbroken.\n（脆い地面を渡った。視界（景色）を壊さずに。）",

            "Nothing changed.\n Only your position did.\nNot luck.\nAlignment.\nThe moment passed.\nThe record remains.\n" +
                "（何も変わらない。ただ、あなたの位置だけが変わった。\n運じゃない。整いだ。\n瞬間は過ぎた。記録は残る。）",

            // ✅ 失敗・時間切れ（指定）
            BOOST_TIMEUP_FAIL.get(0),
            BOOST_TIMEUP_FAIL.get(1),
            BOOST_TIMEUP_FAIL.get(2),
            BOOST_TIMEUP_FAIL.get(3),
            BOOST_TIMEUP_FAIL.get(4),

            // ✅ 汎用的な哲学（指定）
            BOOST_PHILOSOPHY.get(0),
            BOOST_PHILOSOPHY.get(1),

            // 北欧哲学パック（F1–F5）
            "The season closed.\nThe land remained.\n（季節は閉じた。大地は、残っている。）",
            "The path The path remains.\nOnly your steps stopped.\n（道は、まだそこにある。あなたが歩みを止めただけ。）",
            "Your time ran out.\nThe sky didn’t.\n（あなたの時間は終わった。空は、終わっていない。）",
            "It wasn’t rejection.\nOnly silence.\n（拒絶ではない。ただ、沈黙だった。）",
            "The world is still intact.\nYou can return—changed.\n（世界は形を保っている。変わったあなたで、戻れる。）",

            // TIME’S UP（時間切れ）【5】
            "The season ended.\nThe forest remains.\n（季節は終わった。森は、残っている。）",
            "The distance was closing.\nTime kept its rhythm.\n（距離は縮んでいた。時間は、リズムを保った。）",
            "Your time came to a halt.\nThe sky kept moving.\n（あなたの時間は止まった。空は、止まらなかった。）",
            "Not rejection\nJust silence.\n（拒絶ではない。ただの、沈黙。）",
            "The path is still here.\nYou can come back—changed.\n（道は、まだここにある。違うあなたで、戻れる。）"
        );

        List<String> boosted = new ArrayList<>();
        boosted.addAll(BOOST_TIMEUP_FAIL);
        boosted.addAll(BOOST_PHILOSOPHY);

        yield boost(base, boosted, 3);
      }

      default -> List.of("Time's up.");
    };
  }

  // =========================================================
  // TRADER（行商人との交換）
  // =========================================================
  private List<String> traderPool(String d) {
    return switch (d) {
      case "EASY", "NORMAL", "HARD" -> {
        List<String> base = List.of(
            // ✅ 行商人との交換時（指定）
            BOOST_TRADER.get(0),
            BOOST_TRADER.get(1),
            BOOST_TRADER.get(2),

            // ✅ 汎用的な哲学（指定）
            BOOST_PHILOSOPHY.get(0),
            BOOST_PHILOSOPHY.get(1)
        );

        List<String> boosted = new ArrayList<>();
        boosted.addAll(BOOST_TRADER);
        boosted.addAll(BOOST_PHILOSOPHY);

        // 指定：これら“全て”を他より3倍
        yield boost(base, boosted, 3);
      }

      default -> List.of(
          "Perfect equilibrium.\nThe scales held level.\n（等価の均衡。天秤は水平を保った。）"
      );
    };
  }

  // =========================================================
  // ✅ 追加：英語+日本語（（…））入り名言を優先して選ぶ + 白文字で全部チャット出力
  // =========================================================

  public String pickSuccessQuoteBilingual(String difficulty) {
    String d = normalizeDifficulty(difficulty);
    List<String> pool = successPool(d);
    return pickBilingualOrFallback(pool);
  }

  public String pickTimeUpQuoteBilingual(String difficulty) {
    String d = normalizeDifficulty(difficulty);
    List<String> pool = timeUpPool(d);
    return pickBilingualOrFallback(pool);
  }

  // ✅ （日本語括弧）を含むものを優先して選ぶ。無ければ通常プールから選ぶ
  private String pickBilingualOrFallback(List<String> pool) {
    if (pool == null || pool.isEmpty()) return "Run complete.";

    List<String> bilingual = new ArrayList<>();
    for (String s : pool) {
      if (s == null) continue;
      if (s.contains("（") && s.contains("）")) bilingual.add(s);
    }

    List<String> src = bilingual.isEmpty() ? pool : bilingual;
    if (src.isEmpty()) return "Run complete.";

    return src.get(ThreadLocalRandom.current().nextInt(src.size()));
  }

  // ✅ 名言テキストを「チャット欄で切れずに全部」白で出す（>引用記号も除去）
  public void sendFinalChatQuoteWhite(Player player, String quote) {
    if (player == null || quote == null) return;

    for (String line : toChatLines(quote)) {
      if (!line.isBlank()) {
        player.sendMessage(ChatColor.WHITE + line);
      }
    }
  }

  private List<String> toChatLines(String text) {
    List<String> out = new ArrayList<>();
    for (String raw : text.split("\\R")) {
      String s = raw.trim();

      // 先頭の ">" を全部削る（blockquote対策）
      while (s.startsWith(">")) s = s.substring(1).trim();

      if (s.isEmpty()) continue;
      out.add(s);
    }
    return out;
  }
  // =========================================================
  // compatibility wrappers for current i18n-based callers
  // =========================================================


  public String pickTraderSubtitle(String difficulty, String lang) {
    return pickTraderSubtitle(difficulty);
  }

  public String pickSuccessQuoteBilingual(String difficulty, String lang) {
    String d = normalizeDifficulty(difficulty);
    return pickBilingualOrFallback(localizedSuccessPool(d, lang));
  }

  public String pickTimeUpQuoteBilingual(String difficulty, String lang) {
    String d = normalizeDifficulty(difficulty);
    return pickBilingualOrFallback(localizedTimeUpPool(d, lang));
  }

  public String sanitizeVisibleText(GameOutcome outcome, String lang, String text) {
    if (text == null || text.isBlank()) {
      return outcome == GameOutcome.SUCCESS ? "Run complete." : "Time's up.";
    }
    return text;
  }

}