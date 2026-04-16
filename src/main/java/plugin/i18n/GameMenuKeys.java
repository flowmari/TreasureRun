package plugin.i18n;

/**
 * GameMenu i18n keys.
 * Keep all keys centralized to avoid scattering string literals across code.
 */
public final class GameMenuKeys {
  private GameMenuKeys() {}

  // Chat menu (TOC)
  public static final String UI_MENU_TOC_MESSAGE = "ui.menu.toc.message";
  public static final String UI_MENU_LEGACY_TOC_MESSAGE = "ui.menu.legacy.toc.message";

  // Book messages
  public static final String UI_MENU_BOOK_OPEN_FAILED = "ui.menu.book.openFailed";
  public static final String UI_MENU_BOOK_HOTBAR_GIVEN = "ui.menu.book.hotbarGiven";
  public static final String UI_MENU_BOOK_HOTBAR_HINT  = "ui.menu.book.hotbarHint";

  // ui.menu.*
  public static final String UI_MENU_PAGE_NOTE_BOOK_FORMAT = "ui.menu.page.note.bookFormat";
  public static final String UI_MENU_PAGE_HINT_REOPEN_MENU = "ui.menu.page.hint.reopenMenu";

  // ui.quote.* (existing)
  public static final String UI_QUOTE_LEGEND_SUCCESS = "ui.quote.legend.success";
  public static final String UI_QUOTE_LEGEND_TIME_UP = "ui.quote.legend.timeUp";
  public static final String UI_QUOTE_LEGEND_FAVORITES = "ui.quote.legend.favorites";

  public static final String UI_QUOTE_NO_LOGS = "ui.quote.noLogs";
  public static final String UI_QUOTE_NO_QUOTES_IN_TAB = "ui.quote.noQuotesInTab";
  public static final String UI_QUOTE_NO_FAVORITES = "ui.quote.noFavorites";

  public static final String UI_QUOTE_TIP_RIGHT_CLICK_SAVE = "ui.quote.tip.rightClickSave";

  // ✅ Introを100%ローカライズするために追加（C）
  public static final String UI_QUOTE_TITLE = "ui.quote.title";
  public static final String UI_QUOTE_INTRO_TABS_TITLE = "ui.quote.intro.tabsTitle";
  public static final String UI_QUOTE_INTRO_LEGEND_TITLE = "ui.quote.intro.legendTitle";
  public static final String UI_QUOTE_INTRO_STORED_IN_DB = "ui.quote.intro.storedInDb";
  public static final String UI_QUOTE_INTRO_DB_LOGS = "ui.quote.intro.db.logs";
  public static final String UI_QUOTE_INTRO_DB_FAVORITES = "ui.quote.intro.db.favorites";
  public static final String UI_QUOTE_INTRO_LANG_LINE = "ui.quote.intro.langLine";

  // ui.labels.* (Phase 2)
  public static final String UI_LABEL_LATEST = "ui.labels.latest";

  public static final String UI_LABEL_OUTCOME_SUCCESS = "ui.labels.outcome.success";
  public static final String UI_LABEL_OUTCOME_TIME_UP = "ui.labels.outcome.timeUp";

  public static final String UI_LABEL_TAB_ALL = "ui.labels.tab.all";
  public static final String UI_LABEL_TAB_SUCCESS = "ui.labels.tab.success";
  public static final String UI_LABEL_TAB_TIME_UP = "ui.labels.tab.timeUp";
  public static final String UI_LABEL_TAB_FAVORITES = "ui.labels.tab.favorites";

  // ✅ Tabs / Page を完全ローカライズするために追加（B）
  public static final String UI_LABEL_TABS = "ui.labels.tabs";
  public static final String UI_LABEL_PAGE = "ui.labels.page";

  // ✅ Contents page items
  public static final String UI_MENU_PAGE_CONTENTS_TITLE = "ui.menu.page.contents.title";
  public static final String UI_MENU_PAGE_CONTENTS_RULES = "ui.menu.page.contents.rulesItem";
  public static final String UI_MENU_PAGE_CONTENTS_TIPS  = "ui.menu.page.contents.tipsItem";

  // ✅ Quote / Favorites headers
  public static final String UI_QUOTE_HEADER_RECENT     = "ui.quote.headerRecent";
  public static final String UI_FAVORITES_HEADER_LATEST = "ui.favorites.headerLatest";
  public static final String UI_FAVORITES_TITLE         = "ui.favorites.title";

  // ✅ Contents page sub-items
  public static final String UI_MENU_PAGE_CONTENTS_HOW_TO_PLAY  = "ui.menu.page.contents.howToPlay";
  public static final String UI_MENU_PAGE_CONTENTS_SCORE_ROUTE  = "ui.menu.page.contents.scoreRoute";
  public static final String UI_MENU_PAGE_CONTENTS_SAVED_ON     = "ui.menu.page.contents.savedOn";
  public static final String UI_MENU_PAGE_CONTENTS_LANGUAGE     = "ui.menu.page.contents.language";
  public static final String UI_MENU_PAGE_CONTENTS_YOUR_QUOTES  = "ui.menu.page.contents.yourQuotes";
  public static final String UI_MENU_PAGE_CONTENTS_DIFFICULTY   = "ui.menu.page.contents.difficulty";

  // ✅ Error / Fallback messages
  public static final String UI_ERROR_NO_DATA = "ui.error.noData";

  // ✅ GameMenu first-wave fallback / footer keys
  public static final String UI_MENU_BOOK_FALLBACK_TITLE = "ui.menu.book.fallbackTitle";
  public static final String UI_MENU_BOOK_FALLBACK_DISPLAY_NAME = "ui.menu.book.fallbackDisplayName";
  public static final String UI_QUOTE_FOOTER_DB_LOGS = "ui.quote.footer.dbLogs";
  public static final String UI_FAVORITES_FOOTER_DB_FAVORITES = "ui.favorites.footer.dbFavorites";


  // Optional
  public static final String UI_MENU_BOOK_LATEST_HINT = "ui.menu.book.latestHint";
}