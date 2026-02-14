package plugin.quote;

import org.bukkit.plugin.Plugin;

import plugin.I18n;
import plugin.LanguageConfigStore;
import plugin.PlayerLanguageStore;
import plugin.TreasureRunMultiChestPlugin;

/**
 * QuoteModule
 *
 * ✅ 目的:
 * - TreasureRunMultiChestPlugin から参照されている QuoteModule を提供し、ビルドを止めない
 * - enable()/reload() の呼び出し点を保持し、将来ここにFavorites/再読込処理を集約できる
 *
 * ※ 現段階は「安全スタブ」：動作はログ出力のみ（ゲームを壊さない）
 */
public class QuoteModule {

  private final TreasureRunMultiChestPlugin plugin;
  @SuppressWarnings("unused")
  private final PlayerLanguageStore playerLanguageStore;
  @SuppressWarnings("unused")
  private final LanguageConfigStore languageConfigStore;
  @SuppressWarnings("unused")
  private final I18n i18n;

  public QuoteModule(
      TreasureRunMultiChestPlugin plugin,
      PlayerLanguageStore playerLanguageStore,
      LanguageConfigStore languageConfigStore,
      I18n i18n
  ) {
    this.plugin = plugin;
    this.playerLanguageStore = playerLanguageStore;
    this.languageConfigStore = languageConfigStore;
    this.i18n = i18n;
  }

  /** ✅ onEnable 相当：現時点は安全にログだけ */
  public void enable() {
    logInfo("QuoteModule enabled (stub).");
    // TODO: 将来ここでコマンド/Listener登録、Favorites図鑑の初期化などを行う
  }

  /** ✅ /treasureReload から呼ばれる：現時点は安全にログだけ */
  public void reload() {
    logInfo("QuoteModule reloaded (stub).");
    // TODO: 将来ここで翻訳再読み込み、Book再生成、ストア再読込など
  }

  private void logInfo(String msg) {
    try {
      if (plugin != null && plugin.getLogger() != null) {
        plugin.getLogger().info(msg);
        return;
      }
    } catch (Throwable ignored) {}
    // 最終保険
    try {
      Plugin p = plugin;
      if (p != null && p.getLogger() != null) p.getLogger().info(msg);
    } catch (Throwable ignored) {}
  }
}
