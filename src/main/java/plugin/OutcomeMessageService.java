package plugin;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import plugin.i18n.OutcomeMessageKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class OutcomeMessageService {

  private final TreasureRunMultiChestPlugin plugin;
  private final Random random = new Random();

  public OutcomeMessageService(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  public String pickSubtitle(GameOutcome outcome, String difficulty, String lang) {
    String d = normalizeDifficulty(difficulty);

    List<String> pool = switch (outcome) {
      case SUCCESS -> localizedSuccessPool(d, lang);
      case TIME_UP -> localizedTimeUpPool(d, lang);
    };

    if (pool == null || pool.isEmpty()) {
      return switch (outcome) {
        case SUCCESS -> fallbackText(lang, OutcomeMessageKeys.OUTCOME_FALLBACK_RUN_COMPLETE, "Run complete.");
        case TIME_UP -> fallbackText(lang, OutcomeMessageKeys.OUTCOME_FALLBACK_TIME_UP, "Time's up.");
      };
    }

    return pool.get(random.nextInt(pool.size()));
  }

  public String pickTraderSubtitle(String difficulty) {
    String defaultLang = plugin.getConfig().getString("language.default", "en");
    return pickTraderSubtitle(difficulty, defaultLang);
  }

  public String pickTraderSubtitle(String difficulty, String lang) {
    String d = normalizeDifficulty(difficulty);
    List<String> pool = localizedTraderPool(d, lang);
    if (pool == null || pool.isEmpty()) {
      return fallbackText(lang, OutcomeMessageKeys.OUTCOME_FALLBACK_TRADER,
          "Perfect balance.\nThe scales held level.");
    }
    return pool.get(random.nextInt(pool.size()));
  }

  public String pickSuccessQuoteBilingual(String difficulty) {
    String defaultLang = plugin.getConfig().getString("language.default", "en");
    return pickSuccessQuoteBilingual(difficulty, defaultLang);
  }

  public String pickSuccessQuoteBilingual(String difficulty, String lang) {
    String d = normalizeDifficulty(difficulty);
    List<String> pool = localizedSuccessPool(d, lang);
    if (pool == null || pool.isEmpty()) {
      return fallbackText(lang, OutcomeMessageKeys.OUTCOME_FALLBACK_RUN_COMPLETE, "Run complete.");
    }
    return pool.get(random.nextInt(pool.size()));
  }

  public String pickTimeUpQuoteBilingual(String difficulty) {
    String defaultLang = plugin.getConfig().getString("language.default", "en");
    return pickTimeUpQuoteBilingual(difficulty, defaultLang);
  }

  public String pickTimeUpQuoteBilingual(String difficulty, String lang) {
    String d = normalizeDifficulty(difficulty);
    List<String> pool = localizedTimeUpPool(d, lang);
    if (pool == null || pool.isEmpty()) {
      return fallbackText(lang, OutcomeMessageKeys.OUTCOME_FALLBACK_TIME_UP, "Time's up.");
    }
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

  private String fallbackText(String lang, String key, String fallback) {
    String s = plugin.getI18n().tr(lang, key);
    if (s == null || s.isBlank() || s.equals(key) || s.startsWith("Translation missing:")) {
      return fallback;
    }
    return s;
  }

  private List<String> localizedSuccessPool(String d, String lang) {
    List<String> out = new ArrayList<>();
    out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_SUCCESS_COMMON_POOL));

    switch (d) {
      case "EASY" -> out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_SUCCESS_EASY_POOL));
      case "NORMAL" -> out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_SUCCESS_NORMAL_POOL));
      case "HARD" -> out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_SUCCESS_HARD_POOL));
    }
    return out;
  }

  private List<String> localizedTimeUpPool(String d, String lang) {
    List<String> out = new ArrayList<>();
    switch (d) {
      case "EASY" -> out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_TIMEUP_EASY_POOL));
      case "NORMAL" -> out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_TIMEUP_NORMAL_POOL));
      case "HARD" -> out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_TIMEUP_HARD_POOL));
    }
    return out;
  }

  private List<String> localizedTraderPool(String d, String lang) {
    List<String> out = new ArrayList<>();
    out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_TRADER_COMMON_POOL));

    switch (d) {
      case "EASY" -> out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_TRADER_EASY_POOL));
      case "NORMAL" -> out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_TRADER_NORMAL_POOL));
      case "HARD" -> out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_TRADER_HARD_POOL));
    }
    return out;
  }

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
      while (s.startsWith(">")) s = s.substring(1).trim();
      if (!s.isEmpty()) out.add(s);
    }
    return out;
  }
}
