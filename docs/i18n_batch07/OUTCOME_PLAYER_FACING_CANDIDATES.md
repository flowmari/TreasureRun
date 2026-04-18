# OutcomeMessageService player-facing candidates

## L21

Easy

## L21

Normal

## L21

Hard

## L51

NORMAL

## L53

EASY

## L53

EASY

## L54

HARD

## L54

HARD

## L55

NORMAL

## L76

EASY

## L77

NORMAL

## L78

HARD

## L94

EASY

## L95

NORMAL

## L96

HARD

## L151


  );

  private static final List<String> BOOST_SUCCESS_MID = List.of(
      

## L158


  );

  private static final List<String> BOOST_TIMEUP_FAIL = List.of(
      

## L166


  );

  private static final List<String> BOOST_TRADER = List.of(
      

## L172


  );

  private static final List<String> BOOST_PHILOSOPHY = List.of(
      

## L177


  );

  private static final List<String> BOOST_SUCCESS_NORMAL_BASE17 = List.of(
      

## L199


  );

  // =========================================================
  // SUCCESS（RUN COMPLETE）
  // =========================================================
  private List<String> successPool(String d) {
    return switch (d) {

      // -------------------------
      // EASY（✅ SUCCESS 高ランク/中ランク/哲学 を “他より3倍”）
      // -------------------------
      case 

## L211

 -> {
        List<String> base = List.of(
            

## L243

,

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
      case 

## L274

 -> {
        List<String> base = List.of(
            

## L289

,

            // ✅ 成功・中ランク（指定）
            

## L295

,

            // ✅ 成功・高ランク（指定）— NORMALにも1回入れる（= 3倍対象にするため）
            BOOST_SUCCESS_HIGH.get(0),
            BOOST_SUCCESS_HIGH.get(1),
            BOOST_SUCCESS_HIGH.get(2),
            BOOST_SUCCESS_HIGH.get(3),
            BOOST_SUCCESS_HIGH.get(4),

            // RUN COMPLETE 短文セット
            

## L308

,

            // 哲学パック（S1–S5）
            

## L316

,

            // 成功時（ゲームに勝ったとき）【5】
            

## L323

,

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
      case 

## L344

 -> {
        List<String> base = List.of(
            

## L369

,

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
            

## L403


        );

        List<String> boosted = new ArrayList<>();
        boosted.addAll(BOOST_SUCCESS_HIGH);
        boosted.addAll(BOOST_SUCCESS_MID);
        boosted.addAll(BOOST_PHILOSOPHY);

        yield boost(base, boosted, 3);
      }

      default -> List.of(

## L414

);
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
      case 

## L427

 -> {
        List<String> base = List.of(
            

## L438

,

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
      case 

## L462

 -> {
        List<String> base = List.of(
            

## L476

,

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
            

## L504


        );

        List<String> boosted = new ArrayList<>();
        boosted.addAll(BOOST_TIMEUP_FAIL);
        boosted.addAll(BOOST_PHILOSOPHY);

        yield boost(base, boosted, 3);
      }

      // -------------------------
      // HARD（失敗・時間切れ5文＋哲学2文を“他の全てより3倍”）
      // -------------------------
      case 

## L517

 -> {
        List<String> base = List.of(
            

## L545

,

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
            

## L563

,

            // TIME’S UP（時間切れ）【5】
            

## L570


        );

        List<String> boosted = new ArrayList<>();
        boosted.addAll(BOOST_TIMEUP_FAIL);
        boosted.addAll(BOOST_PHILOSOPHY);

        yield boost(base, boosted, 3);
      }

      default -> List.of(

## L580

);
    };
  }

  // =========================================================
  // TRADER（行商人との交換）
  // =========================================================
  private List<String> traderPool(String d) {
    return switch (d) {
      case 

## L589

 -> {
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
          

## L610


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
    if (pool == null || pool.isEmpty()) return 

## L633

;

    List<String> bilingual = new ArrayList<>();
    for (String s : pool) {
      if (s == null) continue;
      if (s.contains(

## L638

) && s.contains(

## L638

)) bilingual.add(s);
    }

    List<String> src = bilingual.isEmpty() ? pool : bilingual;
    if (src.isEmpty()) return 

## L642

;

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
    for (String raw : text.split(

## L660

)) {
      String s = raw.trim();

      // 先頭の 

## L663

 を全部削る（blockquote対策）
      while (s.startsWith(

