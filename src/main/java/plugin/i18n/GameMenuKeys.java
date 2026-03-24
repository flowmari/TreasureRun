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

  // Optional (Phase 2 candidates)
  public static final String UI_MENU_BOOK_LATEST_HINT = "ui.menu.book.latestHint";
}