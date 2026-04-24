package plugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

public enum GameOutcome {
  SUCCESS,
  TIME_UP;

  /**
   * ✅ 成功/タイムアップ共通：
   *  - finishDelayTicks の 2秒前(40tick前) に他UI停止
   *  - finishDelayTicks で名言(英語+日本語優先)を白文字でチャットへ全部出す
   *
   * 使い方例：
   *   outcome.scheduleFinalQuoteAndUiStop(
   *       plugin, player, outcomeMessageService, difficulty, finishDelay,
   *       this::endResultUiLock
   *   );
   */
  public void scheduleFinalQuoteAndUiStop(
      Plugin plugin,
      Player player,
      OutcomeMessageService outcomeMessageService,
      String difficulty,
      long finishDelayTicks,
      Consumer<Player> endResultUiLock
  ) {
    if (plugin == null || player == null || outcomeMessageService == null) return;

    // ✅ 2秒前に他UI停止（40tick = 2秒）
    long clearUiDelay = Math.max(0L, finishDelayTicks - 40L);
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;

      // ここで「Title更新タスク停止」「ActionBar粘着停止」など
      if (endResultUiLock != null) endResultUiLock.accept(player);

      // 必要なら残骸掃除（使うなら呼び出し側でやってもOK）
      // player.resetTitle();
      // player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
    }, clearUiDelay);

    // ✅ 最後に“名言だけ”チャット欄へ白で全部残す
    //    言語は「表示時点」で取り直す（ゲーム中 /lang 切替に追従）
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;

      String langNow = "en";
      if (plugin instanceof TreasureRunMultiChestPlugin trPlugin) {
        langNow = trPlugin.getConfig().getString("language.default", "en");
        try {
          if (trPlugin.getPlayerLanguageStore() != null) {
            String saved = trPlugin.getPlayerLanguageStore().getLang(player, langNow);
            if (saved != null && !saved.isBlank()) {
              langNow = saved;
            }
          }
        } catch (Throwable ignored) {}
      }

      final String quoteNow = outcomeMessageService.sanitizeVisibleText(
          this,
          langNow,
          (this == SUCCESS)
              ? outcomeMessageService.pickSuccessQuoteBilingual(difficulty, langNow)
              : outcomeMessageService.pickTimeUpQuoteBilingual(difficulty, langNow)
      );

      outcomeMessageService.sendFinalChatQuoteWhite(player, quoteNow);
    }, finishDelayTicks);
  }
}