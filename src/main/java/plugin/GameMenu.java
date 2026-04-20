package plugin;

import plugin.i18n.GameMenuKeys;
import plugin.i18n.GameMenuFallbackTexts;

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
      // 最低限フォールバック（GameMenu.java にプレイヤー向け直書きを残さない）
      player.sendMessage(ChatColor.GOLD + GameMenuFallbackTexts.BRAND_TITLE);
      player.sendMessage(ChatColor.RED + GameMenuFallbackTexts.pluginNotReady(player));
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
  /**
   * 本(WRITTEN_BOOK)のUIで、詳しいルール説明を表示する（旧版：互換用）
   *
   * ✅ 直書きゼロ方針：
   * - 旧メソッド名は残すが、中身は i18n 本命(openRuleBookFromConfig)へ委譲する
   * - これにより「誤って呼ばれても必ず多言語表示」になる
   */
  @Deprecated(since = "1.0", forRemoval = false)
  public static void openRuleBook(Player player, String difficulty) {

    TreasureRunMultiChestPlugin plugin =
        (TreasureRunMultiChestPlugin) org.bukkit.Bukkit.getPluginManager().getPlugin("TreasureRun");

    if (plugin == null || player == null) return;

    // lang は未指定（保存済み言語 or default を openRuleBookFromConfig が解決）
    openRuleBookFromConfig(player, difficulty, plugin, "");
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
        plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_BOOK_FALLBACK_TITLE));
    String author = cfg.getString("ruleBook.author", GameMenuFallbackTexts.BRAND_TITLE);
    String displayNameRaw = cfg.getString("ruleBook.displayName." + actualLang,
        plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_BOOK_FALLBACK_DISPLAY_NAME));

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
    String contentsPage = buildContentsPage(plugin, actualLang, difficulty, diffLabel);
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
      allNames.add(plugin.getI18n().tr("ja", GameMenuKeys.UI_MENU_BOOK_FALLBACK_DISPLAY_NAME));
      allNames.add(plugin.getI18n().tr("en", GameMenuKeys.UI_MENU_BOOK_FALLBACK_DISPLAY_NAME));
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

    // ✅ latestHint（右クリック保存のヒント）{latestLabel} を各言語のラベルで置換
    String latestLabel = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_LABEL_LATEST);
    player.sendMessage(colorize(plugin.getI18n().tr(
        actualLang,
        GameMenuKeys.UI_MENU_BOOK_LATEST_HINT,
        I18n.Placeholder.of("{latestLabel}", latestLabel)
    )));

  }

  // =========================================================
  // ✅ ✅ ✅ 本の Contents（導線ページ）
  // =========================================================
  private static String buildContentsPage(
      TreasureRunMultiChestPlugin plugin,
      String actualLang,
      String difficulty,
      String diffLabel
  ) {
    String reopenHint = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_PAGE_HINT_REOPEN_MENU);

    // ✅ i18n化：直書きをYAMLキーから取得（19言語対応）
    String contentsTitle = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_PAGE_CONTENTS_TITLE);
    String rulesItem     = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_PAGE_CONTENTS_RULES);
    String tipsItem      = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_PAGE_CONTENTS_TIPS);
    String quoteTitle    = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_TITLE);
    String favTitle      = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_FAVORITES_TITLE);

    // ✅ i18n化：サブ説明文もYAMLキーから取得（19言語対応）
    String howToPlay  = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_PAGE_CONTENTS_HOW_TO_PLAY);
    String scoreRoute = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_PAGE_CONTENTS_SCORE_ROUTE);
    String savedOn    = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_PAGE_CONTENTS_SAVED_ON);
    String langLine   = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_PAGE_CONTENTS_LANGUAGE,
        I18n.Placeholder.of("{lang}", actualLang));
    String yourQuotes = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_PAGE_CONTENTS_YOUR_QUOTES);
    String diffLine   = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_PAGE_CONTENTS_DIFFICULTY,
        I18n.Placeholder.of("{difficulty}", diffLabel));

    return ChatColor.AQUA + "" + ChatColor.BOLD + tint(ChatColor.AQUA, contentsTitle) + "\n\n" +
        ChatColor.DARK_BLUE + diffLine + "\n\n" +

        ChatColor.AQUA + rulesItem + "\n" +
        ChatColor.DARK_BLUE + howToPlay + "\n\n" +

        ChatColor.AQUA + tipsItem + "\n" +
        ChatColor.DARK_BLUE + scoreRoute + "\n\n" +

        ChatColor.AQUA + quoteTitle + "\n" +
        ChatColor.DARK_BLUE + savedOn + "\n" +
        ChatColor.DARK_BLUE + langLine + "\n\n" +

        ChatColor.AQUA + favTitle + "\n" +
        ChatColor.DARK_BLUE + yourQuotes + "\n\n" +

        tint(ChatColor.DARK_BLUE, reopenHint);
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

  private static List<String> buildQuoteTabsPages(Player player, TreasureRunMultiChestPlugin plugin, String actualLang) {
    List<String> pages = new ArrayList<>();

    pages.add(buildQuoteTabsIntroPage(plugin, actualLang));
    pages.addAll(buildProverbCollectionPages(player, plugin, actualLang, QuoteTab.ALL, true));
    pages.addAll(buildProverbCollectionPages(player, plugin, actualLang, QuoteTab.SUCCESS, true));
    pages.addAll(buildProverbCollectionPages(player, plugin, actualLang, QuoteTab.TIME_UP, true));
    pages.addAll(buildFavoritesPages(player, plugin, actualLang, true));

    return pages;
  }

  // =========================================================
  // ✅ ✅ ✅ 完全進化：Quote “タブ風UI + Page番号 + Latest固定 + Favoritesタブ”
  // =========================================================
  private static String buildQuoteTabsIntroPage(TreasureRunMultiChestPlugin plugin, String actualLang) {

    String title = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_TITLE);

    String tabsTitle = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_INTRO_TABS_TITLE);
    String legendTitle = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_INTRO_LEGEND_TITLE);
    String storedTitle = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_INTRO_STORED_IN_DB);

    String dbLogs = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_INTRO_DB_LOGS);
    String dbFav = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_INTRO_DB_FAVORITES);

    String legendSuccess = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_LEGEND_SUCCESS);
    String legendTimeUp = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_LEGEND_TIME_UP);
    String legendFavorites = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_LEGEND_FAVORITES);

    // ✅ legend見出しの「SUCCESS/TIME_UP/Favorites」もキー化（ui.labels.tab.* を再利用）
    String labSuccess = tabLabel(plugin, actualLang, QuoteTab.SUCCESS);
    String labTimeUp  = tabLabel(plugin, actualLang, QuoteTab.TIME_UP);
    String labFav     = tabLabel(plugin, actualLang, QuoteTab.FAVORITES);

    String noteBookFormat = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_MENU_PAGE_NOTE_BOOK_FORMAT);

    String latestLabel = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_LABEL_LATEST);
    String tipRightClickSave = plugin.getI18n().tr(
        actualLang,
        GameMenuKeys.UI_QUOTE_TIP_RIGHT_CLICK_SAVE,
        I18n.Placeholder.of("{latestLabel}", latestLabel)
    );

    String langLine = plugin.getI18n().tr(
        actualLang,
        GameMenuKeys.UI_QUOTE_INTRO_LANG_LINE,
        I18n.Placeholder.of("{lang}", actualLang)
    );

    return ChatColor.AQUA + "" + ChatColor.BOLD + bookPrefix(plugin, actualLang) + tint(ChatColor.AQUA, title) + "\n\n" +

        tint(ChatColor.DARK_BLUE, tabsTitle) + "\n\n" +
        tabHeader(plugin, actualLang, QuoteTab.ALL) + "\n" +
        tabHeader(plugin, actualLang, QuoteTab.SUCCESS) + "\n" +
        tabHeader(plugin, actualLang, QuoteTab.TIME_UP) + "\n" +
        tabHeader(plugin, actualLang, QuoteTab.FAVORITES) + "\n\n" +

        tint(ChatColor.DARK_BLUE, legendTitle) + "\n" +
        ChatColor.GREEN  + legendPrefix(plugin, actualLang) + labSuccess + ChatColor.DARK_BLUE + " " + legendSuccess + "\n" +
        ChatColor.RED    + legendPrefix(plugin, actualLang) + labTimeUp  + ChatColor.DARK_BLUE + " " + legendTimeUp + "\n" +
        ChatColor.YELLOW + legendPrefix(plugin, actualLang) + labFav     + ChatColor.DARK_BLUE + " " + legendFavorites + "\n\n" +

        tint(ChatColor.DARK_BLUE, storedTitle) + "\n" +
        ChatColor.GRAY + dbBullet(plugin, actualLang) + dbLogs + "\n" +
        ChatColor.GRAY + dbBullet(plugin, actualLang) + dbFav + "\n\n" +

        tint(ChatColor.DARK_BLUE, noteBookFormat) + "\n\n" +
        tint(ChatColor.DARK_BLUE, langLine) + "\n\n" +
        tint(ChatColor.DARK_BLUE, tipRightClickSave);
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
      pages.add(ChatColor.AQUA + "" + ChatColor.BOLD + bookPrefix(plugin, actualLang) +
          tint(ChatColor.AQUA, plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_TITLE)) + "\n\n" +
          ChatColor.DARK_BLUE + plugin.getI18n().tr(actualLang, GameMenuKeys.UI_ERROR_NO_DATA));
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

    // ✅ i18n化：直書きをYAMLキーから取得（19言語対応）
    String quoteTitle = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_TITLE);
    String recentHeader = plugin.getI18n().tr(
        actualLang,
        GameMenuKeys.UI_QUOTE_HEADER_RECENT,
        I18n.Placeholder.of("{lang}", actualLang)
    );
    String header =
        ChatColor.AQUA + "" + ChatColor.BOLD + bookPrefix(plugin, actualLang) + tint(ChatColor.AQUA, quoteTitle) + "\n\n" +
            tabBar(plugin, actualLang, tab) + "\n" +
            ChatColor.DARK_BLUE + recentHeader + "\n\n";

    // ✅ logs が空なら i18n メッセージを 1ページで返す
    if (logs == null || logs.isEmpty()) {
      String msg = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_NO_LOGS);
      pages.add(applyPageFooter(
          plugin,
          actualLang,
          header +
              tint(ChatColor.DARK_BLUE, msg) + "\n\n" +
              ChatColor.GRAY + plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_FOOTER_DB_LOGS),
          1,
          1,
          showPageNumber
      ));
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
      String msg = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_NO_QUOTES_IN_TAB);
      pages.add(applyPageFooter(
          plugin,
          actualLang,
          header + tint(ChatColor.DARK_BLUE, msg),
          1,
          1,
          showPageNumber
      ));
      return pages;
    }

    String latestRow = target.get(0);

    List<String> rawPages = new ArrayList<>();

    StringBuilder sb = new StringBuilder(header);
    int lineCount = countLines(sb.toString());

    String latestBlock = buildLatestBlock(plugin, actualLang, latestRow);
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

      String block = buildNormalBlock(plugin, actualLang, row, idx);
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
      pages.add(applyPageFooter(plugin, actualLang, rawPages.get(i), i + 1, total, showPageNumber));
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
      pages.add(ChatColor.YELLOW + "" + ChatColor.BOLD +
          tint(ChatColor.YELLOW, plugin.getI18n().tr(actualLang, GameMenuKeys.UI_FAVORITES_TITLE)) + "\n\n" +
          ChatColor.DARK_BLUE + plugin.getI18n().tr(actualLang, GameMenuKeys.UI_ERROR_NO_DATA));
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

    // ✅ i18n化：直書きをYAMLキーから取得（19言語対応）
    String favoritesTitle = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_FAVORITES_TITLE);
    String latestHeader = plugin.getI18n().tr(
        actualLang,
        GameMenuKeys.UI_FAVORITES_HEADER_LATEST,
        I18n.Placeholder.of("{lang}", actualLang)
    );
    String header =
        ChatColor.YELLOW + "" + ChatColor.BOLD + tint(ChatColor.YELLOW, favoritesTitle) + "\n\n" +
            tabBar(plugin, actualLang, QuoteTab.FAVORITES) + "\n" +
            ChatColor.DARK_BLUE + latestHeader + "\n\n";

    if (favorites == null || favorites.isEmpty()) {
      String noFav = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_QUOTE_NO_FAVORITES);

      String latestLabel = plugin.getI18n().tr(actualLang, GameMenuKeys.UI_LABEL_LATEST);
      String tip = plugin.getI18n().tr(
          actualLang,
          GameMenuKeys.UI_QUOTE_TIP_RIGHT_CLICK_SAVE,
          I18n.Placeholder.of("{latestLabel}", latestLabel)
      );

      pages.add(applyPageFooter(
          plugin,
          actualLang,
          header +
              tint(ChatColor.DARK_BLUE, noFav) + "\n\n" +
              tint(ChatColor.DARK_BLUE, tip) + "\n\n" +
              ChatColor.GRAY + plugin.getI18n().tr(actualLang, GameMenuKeys.UI_FAVORITES_FOOTER_DB_FAVORITES),
          1,
          1,
          showPageNumber
      ));
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

      String block = buildFavoriteBlock(plugin, actualLang, row, idx);
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
      pages.add(applyPageFooter(plugin, actualLang, rawPages.get(i), i + 1, total, showPageNumber));
    }

    return pages;
  }

  private static String buildLatestBlock(TreasureRunMultiChestPlugin plugin, String lang, String row) {
    String outcome = extractOutcome(row);
    String diff = extractDifficulty(row);
    String rowLang = extractLang(row);
    String quoteText = extractQuoteText(row);

    ChatColor outcomeColor = colorByOutcome(outcome);
    ChatColor diffColor = colorByDifficulty(diff);

    String latestLabel = plugin.getI18n().tr(lang, GameMenuKeys.UI_LABEL_LATEST);
    String outcomeLabel = outcomeLabel(plugin, lang, outcome);
    String diffLabel = difficultyLabel(plugin, lang, diff);

    return ChatColor.GOLD + "" + ChatColor.BOLD + latestLabel + "\n" +
        labelWrap(plugin, lang, outcomeLabel, outcomeColor) + " " +
        diffColor + diffLabel + " " +
        ChatColor.GRAY + metaWrap(plugin, lang, safe(rowLang)) + "\n" +
        ChatColor.DARK_BLUE + safeQuote(quoteText) + "\n\n";
  }

  private static String buildNormalBlock(TreasureRunMultiChestPlugin plugin, String lang, String row, int idx) {
    String outcome = extractOutcome(row);
    String diff = extractDifficulty(row);
    String rowLang = extractLang(row);
    String quoteText = extractQuoteText(row);

    ChatColor outcomeColor = colorByOutcome(outcome);
    ChatColor diffColor = colorByDifficulty(diff);

    String outcomeLabel = outcomeLabel(plugin, lang, outcome);
    String diffLabel = difficultyLabel(plugin, lang, diff);

    return ChatColor.AQUA + indexPrefix(plugin, lang, idx) + "\n" +
        labelWrap(plugin, lang, outcomeLabel, outcomeColor) + " " +
        diffColor + diffLabel + " " +
        ChatColor.GRAY + metaWrap(plugin, lang, safe(rowLang)) + "\n" +
        ChatColor.DARK_BLUE + safeQuote(quoteText) + "\n\n";
  }

  private static String buildFavoriteBlock(TreasureRunMultiChestPlugin plugin, String lang, String row, int idx) {
    String favoriteId = extractFavoriteId(row);

    String outcome = extractOutcome(row);
    String diff = extractDifficulty(row);
    String rowLang = extractLang(row);
    String quoteText = extractQuoteText(row);

    ChatColor outcomeColor = colorByOutcome(outcome);
    ChatColor diffColor = colorByDifficulty(diff);

    String idLabel = (favoriteId != null && !favoriteId.isBlank())
        ? (ChatColor.YELLOW + favoriteIdPrefix(plugin, lang, favoriteId))
        : (ChatColor.YELLOW + favoritePrefix(plugin, lang));

    String outcomeLabel = outcomeLabel(plugin, lang, outcome);
    String diffLabel = difficultyLabel(plugin, lang, diff);

    return idLabel + ChatColor.GRAY + "  " + metaWrap(plugin, lang, String.valueOf(idx)) + "\n" +
        labelWrap(plugin, lang, outcomeLabel, outcomeColor) + " " +
        diffColor + diffLabel + " " +
        ChatColor.GRAY + metaWrap(plugin, lang, safe(rowLang)) + "\n" +
        ChatColor.DARK_BLUE + safeQuote(quoteText) + "\n\n";
  }

  private static String tabHeader(TreasureRunMultiChestPlugin plugin, String lang, QuoteTab current) {
    String label = tabLabel(plugin, lang, current);

    if (current == QuoteTab.ALL) return ChatColor.AQUA + activeMarker(plugin, lang) + inactiveTabWrap(plugin, lang, label);
    if (current == QuoteTab.SUCCESS) return ChatColor.GREEN + activeMarker(plugin, lang) + inactiveTabWrap(plugin, lang, label);
    if (current == QuoteTab.TIME_UP) return ChatColor.RED + activeMarker(plugin, lang) + inactiveTabWrap(plugin, lang, label);
    return ChatColor.YELLOW + activeMarker(plugin, lang) + inactiveTabWrap(plugin, lang, label);
  }

  private static String tabBar(TreasureRunMultiChestPlugin plugin, String lang, QuoteTab current) {
    String all = tabLabel(plugin, lang, QuoteTab.ALL);
    String success = tabLabel(plugin, lang, QuoteTab.SUCCESS);
    String timeUp = tabLabel(plugin, lang, QuoteTab.TIME_UP);
    String fav = tabLabel(plugin, lang, QuoteTab.FAVORITES);

    String tabsLabel = plugin.getI18n().tr(lang, GameMenuKeys.UI_LABEL_TABS);
    tabsLabel = colorize(tabsLabel);

    return ChatColor.DARK_BLUE + "" + tabsLabel + " " +
        (current == QuoteTab.ALL ? activeTabWrap(plugin, lang, all, ChatColor.AQUA) : inactiveTabWrap(plugin, lang, all)) +
        ChatColor.DARK_BLUE + " " +
        (current == QuoteTab.SUCCESS ? activeTabWrap(plugin, lang, success, ChatColor.GREEN) : inactiveTabWrap(plugin, lang, success)) +
        ChatColor.DARK_BLUE + " " +
        (current == QuoteTab.TIME_UP ? activeTabWrap(plugin, lang, timeUp, ChatColor.RED) : inactiveTabWrap(plugin, lang, timeUp)) +
        ChatColor.DARK_BLUE + " " +
        (current == QuoteTab.FAVORITES ? activeTabWrap(plugin, lang, fav, ChatColor.YELLOW) : inactiveTabWrap(plugin, lang, fav));
  }

  private static String applyPageFooter(
      TreasureRunMultiChestPlugin plugin,
      String lang,
      String page,
      int pageIndex,
      int totalPages,
      boolean showPageNumber
  ) {
    if (!showPageNumber) return page;

    // ✅ i18n: "Page 1/3" も言語化（{page}/{total} を埋める）
    String fmt = plugin.getI18n().tr(
        lang,
        GameMenuKeys.UI_LABEL_PAGE, // ui.labels.page
        I18n.Placeholder.of("{page}", String.valueOf(pageIndex)),
        I18n.Placeholder.of("{total}", String.valueOf(totalPages))
    );

    return page + colorize(fmt);
  }

  // ✅ 追加：ページ分割のための行数カウント（これが無いと大量エラーになる）
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

  // =========================================================
  // ✅ Phase 2 helpers: label dictionary (ui.labels.*)
  // =========================================================




  private static String uiSym(TreasureRunMultiChestPlugin plugin, String lang, String key, String fallback) {
    if (plugin == null) return fallback;
    try {
      String v = plugin.getI18n().tr(lang, key);
      if (v == null || v.isBlank() || v.equals(key)) return fallback;
      return v;
    } catch (Throwable ignored) {
      return fallback;
    }
  }

  private static String activeMarker(TreasureRunMultiChestPlugin plugin, String lang) {
    return uiSym(plugin, lang, "ui.symbol.activeMarker", "▶ ");
  }

  private static String bookPrefix(TreasureRunMultiChestPlugin plugin, String lang) {
    return uiSym(plugin, lang, "ui.symbol.bookPrefix", "📖 ");
  }

  private static String legendPrefix(TreasureRunMultiChestPlugin plugin, String lang) {
    return uiSym(plugin, lang, "ui.symbol.legendPrefix", "■ ");
  }

  private static String dbBullet(TreasureRunMultiChestPlugin plugin, String lang) {
    return uiSym(plugin, lang, "ui.symbol.dbBullet", "- ");
  }

  private static String indexPrefix(TreasureRunMultiChestPlugin plugin, String lang, int idx) {
    return uiSym(plugin, lang, "ui.symbol.indexPrefix", "#") + idx;
  }

  private static String favoriteIdPrefix(TreasureRunMultiChestPlugin plugin, String lang, String id) {
    return uiSym(plugin, lang, "ui.symbol.favoriteIdPrefix", "★#") + id;
  }

  private static String favoritePrefix(TreasureRunMultiChestPlugin plugin, String lang) {
    return uiSym(plugin, lang, "ui.symbol.favoritePrefix", "★");
  }

  private static String metaWrap(TreasureRunMultiChestPlugin plugin, String lang, String body) {
    return uiSym(plugin, lang, "ui.symbol.metaOpen", "(") + body +
        uiSym(plugin, lang, "ui.symbol.metaClose", ")");
  }

  private static String labelWrap(TreasureRunMultiChestPlugin plugin, String lang, String body, ChatColor bodyColor) {
    return ChatColor.WHITE + uiSym(plugin, lang, "ui.symbol.labelOpen", "[") +
        bodyColor + body +
        ChatColor.WHITE + uiSym(plugin, lang, "ui.symbol.labelClose", "]");
  }

  private static String inactiveTabWrap(TreasureRunMultiChestPlugin plugin, String lang, String body) {
    return ChatColor.GRAY + uiSym(plugin, lang, "ui.symbol.tabInactiveOpen", "[") +
        body +
        ChatColor.GRAY + uiSym(plugin, lang, "ui.symbol.tabInactiveClose", "]");
  }

  private static String activeTabWrap(TreasureRunMultiChestPlugin plugin, String lang, String body, ChatColor bodyColor) {
    return ChatColor.WHITE + uiSym(plugin, lang, "ui.symbol.tabActiveOpen", "【") +
        bodyColor + body +
        ChatColor.WHITE + uiSym(plugin, lang, "ui.symbol.tabActiveClose", "】");
  }


  private static String normalizeDifficultyKey(String diff) {
    if (diff == null || diff.isBlank()) return "Normal";

    String d = diff.trim();

    // 内部値はそのまま維持
    if (d.equalsIgnoreCase("Easy")) return "Easy";
    if (d.equalsIgnoreCase("Normal")) return "Normal";
    if (d.equalsIgnoreCase("Hard")) return "Hard";

    // 表示ラベルや旧表現から内部キーへ寄せる
    String u = d.toUpperCase();

    if (u.contains("BEGINNER") || u.contains("EINSTEIGER") || u.contains("PRINCIPIANTE")
        || u.contains("DÉBUTANT") || u.contains("INICIANTE")
        || u.contains("初級") || u.contains("초급")
        || u.contains("ALOITTELIJA") || u.contains("BYRJANDI")) {
      return "Easy";
    }

    if (u.contains("INTERMEDIATE") || u.contains("MITTEL") || u.contains("INTERMEDIO")
        || u.contains("INTERMÉDIAIRE") || u.contains("INTERMEDIÁRIO")
        || u.contains("中級") || u.contains("중급")
        || u.contains("KESKITASO") || u.contains("GEMIDDELD")
        || u.contains("MELLAN") || u.contains("मध्यम")) {
      return "Normal";
    }

    if (u.contains("ADVANCED") || u.contains("FORTGESCHRITTEN") || u.contains("AVANZATO")
        || u.contains("AVANZADO") || u.contains("AVANCÉ")
        || u.contains("上級") || u.contains("高級") || u.contains("고급")
        || u.contains("EDISTYNYT") || u.contains("GEVORDERD")
        || u.contains("उन्नत")) {
      return "Hard";
    }

    return "Normal";
  }

  private static String difficultyLabel(TreasureRunMultiChestPlugin plugin, String lang, String diff) {
    if (plugin == null) return safe(diff);

    String key = normalizeDifficultyKey(diff);
    return plugin.getConfig().getString(
        "ruleBook.difficultyLabel." + lang + "." + key,
        key
    );
  }

  private static String outcomeLabel(TreasureRunMultiChestPlugin plugin, String lang, String outcome) {
    if (plugin == null) return safe(outcome);

    String o = (outcome == null) ? "" : outcome.trim().toUpperCase();
    if (o.contains("SUCCESS")) return plugin.getI18n().tr(lang, GameMenuKeys.UI_LABEL_OUTCOME_SUCCESS);
    if (o.contains("TIME_UP")) return plugin.getI18n().tr(lang, GameMenuKeys.UI_LABEL_OUTCOME_TIME_UP);
    return safe(outcome);
  }

  private static String tabLabel(TreasureRunMultiChestPlugin plugin, String lang, QuoteTab tab) {
    if (plugin == null) return tab.name();

    return switch (tab) {
      case ALL -> plugin.getI18n().tr(lang, GameMenuKeys.UI_LABEL_TAB_ALL);
      case SUCCESS -> plugin.getI18n().tr(lang, GameMenuKeys.UI_LABEL_TAB_SUCCESS);
      case TIME_UP -> plugin.getI18n().tr(lang, GameMenuKeys.UI_LABEL_TAB_TIME_UP);
      case FAVORITES -> plugin.getI18n().tr(lang, GameMenuKeys.UI_LABEL_TAB_FAVORITES);
    };
  }

  private static String tint(ChatColor color, String text) {
    if (text == null) return color.toString();
    // 行ごとに色を付け直して「途中で色が戻る」を防ぐ
    return color + text.replace("\n", "\n" + color);
  }

  private static String normalizeName(String s) {
    if (s == null) return "";
    return ChatColor.stripColor(colorize(s));
  }
}
