package plugin;

import plugin.i18n.GameMenuKeys;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ゲーム開始時に表示する「目次（ルール説明）」を担当するクラス
 * ・showGameMenu(...)  : チャットに短い要約を1ブロックで表示
 * ・openRuleBook(...) : 本(WRITTEN_BOOK)のUIで詳しい説明を表示
 *
 * ✅ 追加要素（作品版）：
 * - 格言集 Quote Collection を DB(MySQL proverb_logs) から読み、複数ページで本に埋め込む
 * - SUCCESS/TIME_UP の色分け
 * - 現在言語(lang)でフィルター（無ければ全件表示）
 * - 目次（チャット + 本のContentsページ）に「格言集」を導線として追加
 *
 * ✅ さらに完全進化（体験設計）：
 * - Quote Collection を ALL / SUCCESS / TIME_UP “タブ風”に切り替え
 * - ページ番号 “Page 1/3” 表示（Quote部分のみ）
 * - “最後に出た格言を最上段固定（★Latest）”
 *
 * ✅ ✅ ✅ 追加（Favorites機能）：
 * - Favorites を DB(MySQL proverb_favorites) から読み、タブとして収録
 * - 本を右クリックした瞬間に ★Latest を保存できる設計（Listener側で favoriteLatestLog を呼ぶ）
 */
public class GameMenu {

  /**
   * (Legacy) チャットに「短い要約メニュー（1〜7）」を1ブロックで表示する（直書き版）
   *
   * <p>✅ i18n移行済み：呼び出し側は以下へ統一してください
   * <pre>
   *   showGameMenu(player, difficulty, plugin, lang)
   * </pre>
   *
   * <p>採用向け意図：
   * - 既存呼び出しとの互換を残しつつ
   * - 表示文言は languages/*.yml（19言語）へ集約する
   */
  @Deprecated(since = "1.0", forRemoval = false)
  public static void showGameMenu(Player player, String difficulty) {

    // Legacy: plugin/lang を引数に持たないので Bukkit から取得して i18n を使う
    TreasureRunMultiChestPlugin plugin =
        (TreasureRunMultiChestPlugin) org.bukkit.Bukkit.getPluginManager().getPlugin("TreasureRun");

    if (plugin == null) {
      // 最低限フォールバック（NPEで落ちない）
      player.sendMessage(ChatColor.GOLD + "TreasureRun");
      player.sendMessage(ChatColor.RED + "Plugin not ready.");
      return;
    }

    var cfg = plugin.getConfig();

    // ✅ default
    String defaultLang = cfg.getString("language.default", "ja");

    // ✅ “保存済み言語” を最優先（/lang がここに入る）
    String actualLang = defaultLang;
    if (plugin.getPlayerLanguageStore() != null) {
      actualLang = plugin.getPlayerLanguageStore().getLang(player, defaultLang);
    }
    if (actualLang == null || actualLang.isBlank()) actualLang = defaultLang;

    // {difficulty} は表示用ラベル（無ければそのまま）
    String diffLabel = cfg.getString(
        "ruleBook.difficultyLabel." + actualLang + "." + difficulty,
        difficulty
    );

    String raw = plugin.getI18n().tr(
        actualLang,
        GameMenuKeys.UI_MENU_LEGACY_TOC_MESSAGE,
        I18n.Placeholder.of("{difficulty}", diffLabel)
    );

    // & → § 変換して送る（YAMLが複数行でもOK）
    player.sendMessage(colorize(raw));
  }

  // =========================================================
  // ✅ i18n版：チャット目次を languages/*.yml の ui.menu.toc.message から表示
  // =========================================================
  public static void showGameMenu(Player player, String difficulty,
      TreasureRunMultiChestPlugin plugin, String lang) {

    if (player == null || plugin == null) {
      // 旧互換（事故らないように）
      showGameMenu(player, difficulty);
      return;
    }

    var cfg = plugin.getConfig();

    // ✅ default
    String defaultLang = cfg.getString("language.default", "ja");

    // ✅ “本当に保存されている言語” を最優先（/lang がここに入る）
    // ※ getLang() は未保存でも locale を返すので使わない
    String actualLang = null;
    if (plugin.getPlayerLanguageStore() != null) {
      // ✅ 未保存なら "" を返す（=保存有無判定に使える）
      actualLang = plugin.getPlayerLanguageStore().getLang(player.getUniqueId(), "");
    }

    // ✅ 保存が無い場合だけ、引数 lang（GUI選択）を採用
    if ((actualLang == null || actualLang.isBlank()) && lang != null && !lang.isBlank()) {
      actualLang = lang;
    }

    if (actualLang == null || actualLang.isBlank()) actualLang = defaultLang;

    // {difficulty} は「表示用ラベル」を入れる（無ければ difficulty をそのまま）
    String diffLabel = cfg.getString(
        "ruleBook.difficultyLabel." + actualLang + "." + difficulty,
        difficulty
    );

    String raw = plugin.getI18n().tr(
        actualLang,
        GameMenuKeys.UI_MENU_TOC_MESSAGE,
        I18n.Placeholder.of("{difficulty}", diffLabel)
    );

   // & 色コード → § に変換して送る（messageは複数行）
   player.sendMessage(colorize(raw));
  }

  /**
   * 本(WRITTEN_BOOK)のUIで、詳しいルール説明を表示する（旧版：互換用）
   * ※本命は openRuleBookFromConfig(...) なので、ここは直書きのまま残すのが安全
   */
  public static void openRuleBook(Player player, String difficulty) {

    ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
    BookMeta meta = (BookMeta) book.getItemMeta();

    // ✅ ここで plugin/actualLang は使えない（互換用メソッドなので直書き）
    if (meta == null) {
      player.sendMessage(ChatColor.RED + "ルールブックを開けませんでした。");
      return;
    }

    meta.setTitle("TreasureRun ルール");
    meta.setAuthor("TreasureRun");

    String diffJP = switch (difficulty) {
      case "Easy" -> "Easy（ゆったり）";
      case "Hard" -> "Hard（高難度）";
      default -> "Normal（標準）";
    };

    List<String> pages = new ArrayList<>();

    pages.add(
        ChatColor.AQUA + "" + ChatColor.BOLD + "TreasureRun ルール\n\n" +
            ChatColor.DARK_BLUE +
            "難易度: " + diffJP + "\n\n" +
            "制限時間内にできるだけ多くの\n" +
            "宝箱を開けよう！\n" +
            "レアな宝物ほど高得点です。"
    );

    pages.add(
        ChatColor.AQUA + "★ 基本の流れ\n\n" +
            ChatColor.DARK_BLUE +
            "1. /gameStart <難易度>\n" +
            "2. 緑のマークの宝箱を探す\n" +
            "3. 開けるとスコア + アイテム\n" +
            "4. 全て開けるとクリア！"
    );

    pages.add(
        ChatColor.AQUA + "★ ヒント\n\n" +
            ChatColor.DARK_BLUE +
            "・ネザライト/ブロック系は\n" +
            "  ジャックポット高得点！\n\n" +
            "・途中で /gameMenu を打つと\n" +
            "  この本を再取得できます。\n\n" +
            "・タイムアップに注意！"
    );

    meta.setPages(pages);
    book.setItemMeta(meta);

    ItemMeta displayMeta = book.getItemMeta();
    if (displayMeta != null) {
      displayMeta.setDisplayName(ChatColor.AQUA + "TreasureRun ルールブック");
      book.setItemMeta(displayMeta);
    }

    PlayerInventory inv = player.getInventory();

    for (int i = 0; i < inv.getSize(); i++) {
      ItemStack item = inv.getItem(i);
      if (item == null) continue;
      if (item.getType() != Material.WRITTEN_BOOK) continue;
      if (!item.hasItemMeta()) continue;

      ItemMeta im = item.getItemMeta();
      if (im == null || !im.hasDisplayName()) continue;

      String name = ChatColor.stripColor(im.getDisplayName());
      if ("TreasureRun ルールブック".equals(name)) {
        inv.clear(i);
      }
    }

    inv.setItem(0, book);
    player.updateInventory();

    player.getInventory().setHeldItemSlot(0);
    player.openBook(book);

    // ✅ 互換用は直書き
    player.sendMessage(ChatColor.GOLD + "📖 ルールブックをホットバーに配布しました。");
    player.sendMessage(ChatColor.YELLOW + "手に持って右クリックすると、いつでも読み直せます。");
  }

  // =========================================================
  // ✅ config.yml の ruleBook から本を生成して開く（メイン版）
  // - ここは i18n（ui.menu.book.*）に寄せる
  // =========================================================
  public static void openRuleBookFromConfig(Player player, String difficulty,
      TreasureRunMultiChestPlugin plugin, String lang) {

    var cfg = plugin.getConfig();

    // ✅ default
    String defaultLang = cfg.getString("language.default", "ja");

    // ✅ “本当に保存されている言語” を最優先（/lang がここに入る）
    // ※ getLang() は未保存でも locale を返すので使わない
    String actualLang = null;
    if (plugin.getPlayerLanguageStore() != null) {
      // ✅ 未保存なら "" を返す（=保存有無判定に使える）
      actualLang = plugin.getPlayerLanguageStore().getLang(player.getUniqueId(), "");
    }

    // ✅ 保存が無い場合だけ、引数 lang（GUI選択）を採用
    if ((actualLang == null || actualLang.isBlank()) && lang != null && !lang.isBlank()) {
      actualLang = lang;
    }

    if (actualLang == null || actualLang.isBlank()) actualLang = defaultLang;

    // フォールバック（その言語が無いときはja）
    String title = cfg.getString("ruleBook.title." + actualLang,
        cfg.getString("ruleBook.title.ja", "TreasureRun ルール"));
    String author = cfg.getString("ruleBook.author", "TreasureRun");
    String displayNameRaw = cfg.getString("ruleBook.displayName." + actualLang,
        cfg.getString("ruleBook.displayName.ja", "TreasureRun ルールブック"));

    String displayName = colorize(displayNameRaw);

    // %diffLabel% 差し込み
    String diffLabel = cfg.getString(
        "ruleBook.difficultyLabel." + actualLang + "." + difficulty,
        difficulty
    );

    List<String> pages = cfg.getStringList("ruleBook.pages." + actualLang);
    if (pages == null || pages.isEmpty()) {
      pages = cfg.getStringList("ruleBook.pages.ja");
    }

    List<String> replaced = new ArrayList<>();
    if (pages != null) {
      for (String p : pages) {
        if (p == null) continue;
        replaced.add(p.replace("%diffLabel%", diffLabel));
      }
    }

    // =========================================================
    // ✅ ✅ ✅ 作品導線：本の“最初の方”に Contents（目次ページ）を追加
    // =========================================================
    String contentsPage = buildContentsPage(actualLang, difficulty, diffLabel);
    if (replaced.size() >= 1) {
      replaced.add(1, contentsPage);
    } else {
      replaced.add(contentsPage);
    }

    // =========================================================
    // ✅ ✅ ✅ Quote Collection（完全進化：タブ風 + Page 1/3 + Latest固定）
    // =========================================================
    replaced.addAll(buildQuoteTabsPages(player, plugin, actualLang));

    // 本作成
    ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
    BookMeta meta = (BookMeta) book.getItemMeta();
    if (meta == null) {
      player.sendMessage(colorize(plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_BOOK_OPEN_FAILED)));
      return;
    }

    meta.setTitle(title);
    meta.setAuthor(author);
    meta.setPages(replaced);
    book.setItemMeta(meta);

    // 表示名（ホットバー用）
    ItemMeta displayMeta = book.getItemMeta();
    if (displayMeta != null) {
      displayMeta.setDisplayName(displayName);
      book.setItemMeta(displayMeta);
    }

    PlayerInventory inv = player.getInventory();

    // 既存の「TreasureRunルール本」系を消す（重複防止）
    List<String> allNames = new ArrayList<>();

    ConfigurationSection dnSec = cfg.getConfigurationSection("ruleBook.displayName");
    if (dnSec != null) {
      for (String code : dnSec.getKeys(false)) {
        String n = dnSec.getString(code);
        if (n != null && !n.isBlank()) allNames.add(n);
      }
    }

    if (allNames.isEmpty()) {
      allNames.add("TreasureRun ルールブック");
      allNames.add("TreasureRun Rule Book");
    }

    for (int i = 0; i < inv.getSize(); i++) {
      ItemStack item = inv.getItem(i);
      if (item == null) continue;
      if (item.getType() != Material.WRITTEN_BOOK) continue;
      if (!item.hasItemMeta()) continue;

      ItemMeta im = item.getItemMeta();
      if (im == null || !im.hasDisplayName()) continue;

      String name = normalizeName(im.getDisplayName());
      for (String candidate : allNames) {
        if (candidate == null) continue;
        if (name.equals(normalizeName(candidate))) {
          inv.clear(i);
          break;
        }
      }
    }

    inv.setItem(0, book);
    player.updateInventory();
    player.getInventory().setHeldItemSlot(0);
    player.openBook(book);

    // ✅ メッセージ i18n 化（languages/*.yml の ui.menu.book.*）
    player.sendMessage(colorize(plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_BOOK_HOTBAR_GIVEN)));
    player.sendMessage(colorize(plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_BOOK_HOTBAR_HINT)));
  }

  // =========================================================
  // ✅ ✅ ✅ 本の Contents（導線ページ）
  // =========================================================
  private static String buildContentsPage(String actualLang, String difficulty, String diffLabel) {
    // ※言語ごとに分岐したいならここを config にしてもOK
    return ChatColor.AQUA + "" + ChatColor.BOLD + "📌 Contents\n\n" +
        ChatColor.DARK_BLUE + "Difficulty: " + diffLabel + "\n\n" +

        ChatColor.AQUA + "1) Rules / ルール\n" +
        ChatColor.DARK_BLUE + "  ・How to play\n\n" +

        ChatColor.AQUA + "2) Tips / ヒント\n" +
        ChatColor.DARK_BLUE + "  ・Score & route\n\n" +

        ChatColor.AQUA + "3) Quote Collection\n" +
        ChatColor.DARK_BLUE + "  ・Saved on SUCCESS / TIME_UP\n" +
        ChatColor.DARK_BLUE + "  ・Language: " + ChatColor.GRAY + actualLang + "\n\n" +

        ChatColor.AQUA + "4) Favorites\n" +
        ChatColor.DARK_BLUE + "  ・Your ★ saved quotes\n\n" +

        ChatColor.DARK_BLUE + "Hint: /gameMenu で再度開けます";
  }

  // =========================================================
  // ✅ ✅ ✅ Quote Collection（複数ページ）
  // =========================================================
  private static List<String> buildProverbCollectionPages(
      Player player,
      TreasureRunMultiChestPlugin plugin,
      String actualLang
  ) {
    return buildProverbCollectionPages(player, plugin, actualLang, QuoteTab.ALL, true);
  }

  // =========================================================
  // ✅ ✅ ✅ 完全進化：Quote “タブ風UI + Page番号 + Latest固定 + Favoritesタブ”
  // =========================================================
  private static List<String> buildQuoteTabsPages(Player player, TreasureRunMultiChestPlugin plugin, String actualLang) {
    List<String> pages = new ArrayList<>();

    pages.add(buildQuoteTabsIntroPage(actualLang));
    pages.addAll(buildProverbCollectionPages(player, plugin, actualLang, QuoteTab.ALL, true));
    pages.addAll(buildProverbCollectionPages(player, plugin, actualLang, QuoteTab.SUCCESS, true));
    pages.addAll(buildProverbCollectionPages(player, plugin, actualLang, QuoteTab.TIME_UP, true));
    pages.addAll(buildFavoritesPages(player, plugin, actualLang, true));

    return pages;
  }

  private static String buildQuoteTabsIntroPage(String actualLang) {
    return ChatColor.AQUA + "" + ChatColor.BOLD + "📖 Quote Collection\n\n" +
        ChatColor.DARK_BLUE + "Tabs:\n\n" +
        tabHeader(QuoteTab.ALL) + "\n" +
        tabHeader(QuoteTab.SUCCESS) + "\n" +
        tabHeader(QuoteTab.TIME_UP) + "\n" +
        tabHeader(QuoteTab.FAVORITES) + "\n\n" +

        ChatColor.DARK_BLUE + "Legend:\n" +
        ChatColor.GREEN + "■ SUCCESS" + ChatColor.DARK_BLUE + " = 完走 / 成功\n" +
        ChatColor.RED + "■ TIME_UP" + ChatColor.DARK_BLUE + " = 時間切れ\n" +
        ChatColor.YELLOW + "■ Favorites" + ChatColor.DARK_BLUE + " = ★保存した格言\n\n" +

        ChatColor.GRAY + "Stored in MySQL DB:\n" +
        ChatColor.GRAY + "- proverb_logs (history)\n" +
        ChatColor.GRAY + "- proverb_favorites (★)\n\n" +

        ChatColor.DARK_BLUE + "※ Minecraftの本は\n" +
        ChatColor.DARK_BLUE + "クリックで切替できないので\n" +
        ChatColor.DARK_BLUE + "「セクション」形式で収録します。\n\n" +
        ChatColor.DARK_BLUE + "lang: " + ChatColor.GRAY + actualLang + "\n\n" +
        ChatColor.DARK_BLUE + "Tip: この本を右クリックで\n" +
        ChatColor.DARK_BLUE + "★Latest を保存できます。";
  }

  private static List<String> buildProverbCollectionPages(
      Player player,
      TreasureRunMultiChestPlugin plugin,
      String actualLang,
      QuoteTab tab,
      boolean showPageNumber
  ) {
    List<String> pages = new ArrayList<>();

    if (player == null || plugin == null) {
      pages.add(ChatColor.AQUA + "" + ChatColor.BOLD + "📖 Quote Collection\n\n" +
          ChatColor.DARK_BLUE + "No data.");
      return pages;
    }

    UUID uuid = player.getUniqueId();

    List<String> logs = new ArrayList<>();
    try {
      logs = plugin.getRecentProverbs(uuid, 20);
    } catch (Exception e) {
      plugin.getLogger().severe("[GameMenu] Failed to load Quote Collection from DB: " + e.getMessage());
      logs = new ArrayList<>();
    }

    String header =
        ChatColor.AQUA + "" + ChatColor.BOLD + "📖 Quote Collection\n\n" +
            tabBar(tab) + "\n" +
            ChatColor.DARK_BLUE + "Recent 20  |  lang: " + ChatColor.GRAY + actualLang + "\n\n";

    if (logs == null || logs.isEmpty()) {
      pages.add(applyPageFooter(header +
          ChatColor.DARK_BLUE + "まだ格言ログがありません。\n" +
          ChatColor.DARK_BLUE + "SUCCESS / TIME_UP で\n" +
          ChatColor.DARK_BLUE + "自動で保存されます。\n\n" +
          ChatColor.GRAY + "（DB: proverb_logs）", 1, 1, showPageNumber));
      return pages;
    }

    List<String> langFiltered = new ArrayList<>();
    for (String row : logs) {
      if (row == null || row.isBlank()) continue;
      String rowLang = extractLang(row);
      if (rowLang != null && rowLang.equalsIgnoreCase(actualLang)) {
        langFiltered.add(row);
      }
    }
    List<String> baseTarget = (!langFiltered.isEmpty()) ? langFiltered : logs;

    List<String> target = new ArrayList<>();
    for (String row : baseTarget) {
      if (row == null || row.isBlank()) continue;

      String outcome = extractOutcome(row);
      if (tab == QuoteTab.ALL) {
        target.add(row);
      } else if (tab == QuoteTab.SUCCESS) {
        if (outcome != null && outcome.toUpperCase().contains("SUCCESS")) target.add(row);
      } else if (tab == QuoteTab.TIME_UP) {
        if (outcome != null && outcome.toUpperCase().contains("TIME_UP")) target.add(row);
      }
    }

    if (target.isEmpty()) {
      pages.add(applyPageFooter(header +
          ChatColor.DARK_BLUE + "このタブには表示できる格言がありません。\n" +
          ChatColor.DARK_BLUE + "（まだ保存されていない可能性があります）\n\n" +
          ChatColor.DARK_BLUE + "別タブも見てみてください。", 1, 1, showPageNumber));
      return pages;
    }

    String latestRow = target.get(0);

    List<String> rawPages = new ArrayList<>();

    StringBuilder sb = new StringBuilder(header);
    int lineCount = countLines(sb.toString());

    String latestBlock = buildLatestBlock(latestRow);
    int latestLines = countLines(latestBlock);

    if (lineCount + latestLines > 14) {
      rawPages.add(sb.toString());
      sb = new StringBuilder(header);
      lineCount = countLines(sb.toString());
    }
    sb.append(latestBlock);
    lineCount += latestLines;

    int idx = 1;
    for (String row : target) {
      if (row == null || row.isBlank()) continue;
      if (row.equals(latestRow)) continue;

      String block = buildNormalBlock(row, idx);
      int blockLines = countLines(block);

      if (lineCount + blockLines > 14) {
        rawPages.add(sb.toString());
        sb = new StringBuilder(header);
        lineCount = countLines(sb.toString());
      }

      sb.append(block);
      lineCount += blockLines;
      idx++;
    }

    if (sb.length() > 0) rawPages.add(sb.toString());

    int total = rawPages.size();
    for (int i = 0; i < rawPages.size(); i++) {
      pages.add(applyPageFooter(rawPages.get(i), i + 1, total, showPageNumber));
    }

    return pages;
  }

  private static List<String> buildFavoritesPages(
      Player player,
      TreasureRunMultiChestPlugin plugin,
      String actualLang,
      boolean showPageNumber
  ) {
    List<String> pages = new ArrayList<>();

    if (player == null || plugin == null) {
      pages.add(ChatColor.AQUA + "" + ChatColor.BOLD + "★ Favorites\n\n" +
          ChatColor.DARK_BLUE + "No data.");
      return pages;
    }

    UUID uuid = player.getUniqueId();

    List<String> favorites = new ArrayList<>();
    try {
      Connection conn = plugin.getMySQLConnection();
      if (conn != null && plugin.getProverbLogRepository() != null) {
        favorites = plugin.getProverbLogRepository().loadFavorites(conn, uuid, 20);
      }
    } catch (Exception e) {
      plugin.getLogger().severe("[GameMenu] Failed to load Favorites from DB: " + e.getMessage());
      favorites = new ArrayList<>();
    }

    String header =
        ChatColor.YELLOW + "" + ChatColor.BOLD + "★ Favorites\n\n" +
            tabBar(QuoteTab.FAVORITES) + "\n" +
            ChatColor.DARK_BLUE + "Latest 20  |  lang: " + ChatColor.GRAY + actualLang + "\n\n";

    if (favorites == null || favorites.isEmpty()) {
      pages.add(applyPageFooter(header +
          ChatColor.DARK_BLUE + "まだ★お気に入りがありません。\n\n" +
          ChatColor.DARK_BLUE + "本を右クリックすると\n" +
          ChatColor.DARK_BLUE + "★Latest を保存できます。\n\n" +
          ChatColor.GRAY + "（DB: proverb_favorites）", 1, 1, showPageNumber));
      return pages;
    }

    List<String> langFiltered = new ArrayList<>();
    for (String row : favorites) {
      if (row == null || row.isBlank()) continue;
      String rowLang = extractLang(row);
      if (rowLang != null && rowLang.equalsIgnoreCase(actualLang)) {
        langFiltered.add(row);
      }
    }
    List<String> target = (!langFiltered.isEmpty()) ? langFiltered : favorites;

    List<String> rawPages = new ArrayList<>();

    StringBuilder sb = new StringBuilder(header);
    int lineCount = countLines(sb.toString());

    int idx = 1;
    for (String row : target) {
      if (row == null || row.isBlank()) continue;

      String block = buildFavoriteBlock(row, idx);
      int blockLines = countLines(block);

      if (lineCount + blockLines > 14) {
        rawPages.add(sb.toString());
        sb = new StringBuilder(header);
        lineCount = countLines(sb.toString());
      }

      sb.append(block);
      lineCount += blockLines;
      idx++;
    }

    if (sb.length() > 0) rawPages.add(sb.toString());

    int total = rawPages.size();
    for (int i = 0; i < rawPages.size(); i++) {
      pages.add(applyPageFooter(rawPages.get(i), i + 1, total, showPageNumber));
    }

    return pages;
  }

  private static String buildLatestBlock(String row) {
    String outcome = extractOutcome(row);
    String diff = extractDifficulty(row);
    String rowLang = extractLang(row);
    String quoteText = extractQuoteText(row);

    ChatColor outcomeColor = colorByOutcome(outcome);
    ChatColor diffColor = colorByDifficulty(diff);

    return ChatColor.GOLD + "" + ChatColor.BOLD + "★ Latest\n" +
        outcomeColor + "[" + safe(outcome) + "] " +
        diffColor + safe(diff) + " " +
        ChatColor.GRAY + "(" + safe(rowLang) + ")\n" +
        ChatColor.DARK_BLUE + safeQuote(quoteText) + "\n\n";
  }

  private static String buildNormalBlock(String row, int idx) {
    String outcome = extractOutcome(row);
    String diff = extractDifficulty(row);
    String rowLang = extractLang(row);
    String quoteText = extractQuoteText(row);

    ChatColor outcomeColor = colorByOutcome(outcome);
    ChatColor diffColor = colorByDifficulty(diff);

    return ChatColor.AQUA + "#" + idx + "\n" +
        outcomeColor + "[" + safe(outcome) + "] " +
        diffColor + safe(diff) + " " +
        ChatColor.GRAY + "(" + safe(rowLang) + ")\n" +
        ChatColor.DARK_BLUE + safeQuote(quoteText) + "\n\n";
  }

  private static String buildFavoriteBlock(String row, int idx) {
    String favoriteId = extractFavoriteId(row);

    String outcome = extractOutcome(row);
    String diff = extractDifficulty(row);
    String rowLang = extractLang(row);
    String quoteText = extractQuoteText(row);

    ChatColor outcomeColor = colorByOutcome(outcome);
    ChatColor diffColor = colorByDifficulty(diff);

    String idLabel = (favoriteId != null && !favoriteId.isBlank())
        ? (ChatColor.YELLOW + "★#" + favoriteId)
        : (ChatColor.YELLOW + "★");

    return idLabel + ChatColor.GRAY + "  (" + idx + ")\n" +
        outcomeColor + "[" + safe(outcome) + "] " +
        diffColor + safe(diff) + " " +
        ChatColor.GRAY + "(" + safe(rowLang) + ")\n" +
        ChatColor.DARK_BLUE + safeQuote(quoteText) + "\n\n";
  }

  private static String tabHeader(QuoteTab current) {
    if (current == QuoteTab.ALL) return ChatColor.AQUA + "▶ [ALL]";
    if (current == QuoteTab.SUCCESS) return ChatColor.GREEN + "▶ [SUCCESS]";
    if (current == QuoteTab.TIME_UP) return ChatColor.RED + "▶ [TIME_UP]";
    return ChatColor.YELLOW + "▶ [FAVORITES]";
  }

  private static String tabBar(QuoteTab current) {
    return ChatColor.DARK_BLUE + "Tabs: " +
        (current == QuoteTab.ALL ? ChatColor.WHITE + "【" + ChatColor.AQUA + "ALL" + ChatColor.WHITE + "】" : ChatColor.GRAY + "[ALL]") +
        ChatColor.DARK_BLUE + " " +
        (current == QuoteTab.SUCCESS ? ChatColor.WHITE + "【" + ChatColor.GREEN + "SUCCESS" + ChatColor.WHITE + "】" : ChatColor.GRAY + "[SUCCESS]") +
        ChatColor.DARK_BLUE + " " +
        (current == QuoteTab.TIME_UP ? ChatColor.WHITE + "【" + ChatColor.RED + "TIME_UP" + ChatColor.WHITE + "】" : ChatColor.GRAY + "[TIME_UP]") +
        ChatColor.DARK_BLUE + " " +
        (current == QuoteTab.FAVORITES ? ChatColor.WHITE + "【" + ChatColor.YELLOW + "FAVORITES" + ChatColor.WHITE + "】" : ChatColor.GRAY + "[FAVORITES]");
  }

  private static String applyPageFooter(String page, int pageIndex, int totalPages, boolean showPageNumber) {
    if (!showPageNumber) return page;
    return page + ChatColor.DARK_GRAY + "Page " + pageIndex + "/" + totalPages;
  }

  private static int countLines(String s) {
    if (s == null || s.isEmpty()) return 0;
    return s.split("\\R", -1).length;
  }

  private static String safeQuote(String quoteText) {
    if (quoteText == null) return "";
    String t = quoteText.trim();
    if (t.length() > 320) t = t.substring(0, 320) + "…";
    return t;
  }

  private static String safe(String s) {
    if (s == null || s.isBlank()) return "-";
    return s.trim();
  }

  private static String extractOutcome(String row) {
    String inside = extractBracketInside(row);
    if (inside == null) return "UNKNOWN";
    String[] parts = inside.split("/");
    if (parts.length < 1) return "UNKNOWN";
    return parts[0].trim();
  }

  private static String extractDifficulty(String row) {
    String inside = extractBracketInside(row);
    if (inside == null) return "Normal";
    String[] parts = inside.split("/");
    if (parts.length < 2) return "Normal";
    return parts[1].trim();
  }

  private static String extractLang(String row) {
    String inside = extractBracketInside(row);
    if (inside == null) return "-";
    String[] parts = inside.split("/");
    if (parts.length < 3) return "-";
    return parts[2].trim();
  }

  private static String extractQuoteText(String row) {
    if (row == null) return "";

    int b = row.indexOf("】");
    if (b >= 0) {
      int after = row.indexOf("\n", b);
      if (after >= 0 && after + 1 < row.length()) {
        return row.substring(after + 1).trim();
      }
    }

    int nl = row.indexOf("\n");
    if (nl < 0) return row.trim();
    return row.substring(nl + 1).trim();
  }

  private static String extractBracketInside(String row) {
    if (row == null) return null;
    int a = row.indexOf("【");
    int b = row.indexOf("】");
    if (a < 0 || b < 0 || b <= a) return null;
    return row.substring(a + 1, b).trim();
  }

  private static String extractFavoriteId(String row) {
    if (row == null) return "";
    int star = row.indexOf("★#");
    if (star < 0) return "";
    int end = row.indexOf("\n", star);
    String line = (end >= 0) ? row.substring(star, end) : row.substring(star);
    return line.replace("★#", "").trim();
  }

  private static ChatColor colorByOutcome(String outcome) {
    if (outcome == null) return ChatColor.GRAY;
    String o = outcome.trim().toUpperCase();
    if (o.contains("SUCCESS")) return ChatColor.GREEN;
    if (o.contains("TIME_UP")) return ChatColor.RED;
    return ChatColor.GRAY;
  }

  private static ChatColor colorByDifficulty(String diff) {
    if (diff == null) return ChatColor.WHITE;
    String d = diff.trim().toUpperCase();
    if (d.contains("EASY")) return ChatColor.LIGHT_PURPLE;
    if (d.contains("NORMAL")) return ChatColor.YELLOW;
    if (d.contains("HARD")) return ChatColor.AQUA;
    return ChatColor.WHITE;
  }

  private enum QuoteTab {
    ALL,
    SUCCESS,
    TIME_UP,
    FAVORITES
  }

  private static String colorize(String s) {
    if (s == null) return null;
    return ChatColor.translateAlternateColorCodes('&', s);
  }

  private static String normalizeName(String s) {
    if (s == null) return "";
    return ChatColor.stripColor(colorize(s));
  }
}
