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

  public String pickSubtitle(GameOutcome outcome, String difficulty, String lang) {
    String d = normalizeDifficulty(difficulty);
    List<String> pool = switch (outcome) {
      case SUCCESS -> localizedSuccessPool(d, lang);
      case TIME_UP -> localizedTimeUpPool(d, lang);
    };

    if (pool.isEmpty()) return fallbackText(outcome, lang);
    return pool.get(random.nextInt(pool.size()));
  }

  public String pickTraderSubtitle(String difficulty) {
    return pickTraderSubtitle(difficulty, "en");
  }

  public String pickTraderSubtitle(String difficulty, String lang) {
    String d = normalizeDifficulty(difficulty);
    List<String> pool = localizedTraderPool(d, lang);
    if (pool.isEmpty()) return fallbackKey(lang, OutcomeMessageKeys.OUTCOME_FALLBACK_TRADER);
    return pool.get(random.nextInt(pool.size()));
  }

  public String pickSuccessQuoteBilingual(String difficulty) {
    return pickSuccessQuoteBilingual(difficulty, "en");
  }

  public String pickTimeUpQuoteBilingual(String difficulty) {
    return pickTimeUpQuoteBilingual(difficulty, "en");
  }

  public String pickSuccessQuoteBilingual(String difficulty, String lang) {
    String d = normalizeDifficulty(difficulty);
    List<String> pool = localizedSuccessPool(d, lang);
    if (pool.isEmpty()) return fallbackKey(lang, OutcomeMessageKeys.OUTCOME_FALLBACK_RUN_COMPLETE);
    return pickFromPool(pool);
  }

  public String pickTimeUpQuoteBilingual(String difficulty, String lang) {
    String d = normalizeDifficulty(difficulty);
    List<String> pool = localizedTimeUpPool(d, lang);
    if (pool.isEmpty()) return fallbackKey(lang, OutcomeMessageKeys.OUTCOME_FALLBACK_TIME_UP);
    return pickFromPool(pool);
  }

  public String sanitizeVisibleText(GameOutcome outcome, String lang, String text) {
    if (text == null || text.isBlank()) return fallbackText(outcome, lang);

    String out = text;
    if (lang == null || !lang.toLowerCase(Locale.ROOT).startsWith("ja")) {
      out = stripJapaneseParentheticalLines(out);
    }
    return out;
  }

  public void sendFinalChatQuoteWhite(Player player, String quote) {
    if (player == null || quote == null) return;

    for (String line : toChatLines(quote)) {
      if (!line.isBlank()) player.sendMessage(ChatColor.WHITE + line);
    }
  }

  private String normalizeDifficulty(String difficulty) {
    if (difficulty == null) return "NORMAL";
    String s = difficulty.trim().toUpperCase(Locale.ROOT);
    if (s.contains("EASY")) return "EASY";
    if (s.contains("HARD")) return "HARD";
    return "NORMAL";
  }

  private List<String> localizedSuccessPool(String d, String lang) {
    List<String> out = new ArrayList<>();
    out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_SUCCESS_COMMON_POOL));
    out.addAll(switch (d) {
      case "EASY" -> configuredPool(lang, OutcomeMessageKeys.OUTCOME_SUCCESS_EASY_POOL);
      case "NORMAL" -> configuredPool(lang, OutcomeMessageKeys.OUTCOME_SUCCESS_NORMAL_POOL);
      case "HARD" -> configuredPool(lang, OutcomeMessageKeys.OUTCOME_SUCCESS_HARD_POOL);
      default -> List.of();
    });
    return out;
  }

  private List<String> localizedTimeUpPool(String d, String lang) {
    return switch (d) {
      case "EASY" -> configuredPool(lang, OutcomeMessageKeys.OUTCOME_TIMEUP_EASY_POOL);
      case "NORMAL" -> configuredPool(lang, OutcomeMessageKeys.OUTCOME_TIMEUP_NORMAL_POOL);
      case "HARD" -> configuredPool(lang, OutcomeMessageKeys.OUTCOME_TIMEUP_HARD_POOL);
      default -> List.of();
    };
  }

  private List<String> localizedTraderPool(String d, String lang) {
    List<String> out = new ArrayList<>();
    out.addAll(configuredPool(lang, OutcomeMessageKeys.OUTCOME_TRADER_COMMON_POOL));
    out.addAll(switch (d) {
      case "EASY" -> configuredPool(lang, OutcomeMessageKeys.OUTCOME_TRADER_EASY_POOL);
      case "NORMAL" -> configuredPool(lang, OutcomeMessageKeys.OUTCOME_TRADER_NORMAL_POOL);
      case "HARD" -> configuredPool(lang, OutcomeMessageKeys.OUTCOME_TRADER_HARD_POOL);
      default -> List.of();
    });
    return out;
  }

  private List<String> configuredPool(String lang, String key) {
    if (plugin == null || plugin.getI18n() == null) return List.of();

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

  private String fallbackText(GameOutcome outcome, String lang) {
    return outcome == GameOutcome.SUCCESS
        ? fallbackKey(lang, OutcomeMessageKeys.OUTCOME_FALLBACK_RUN_COMPLETE)
        : fallbackKey(lang, OutcomeMessageKeys.OUTCOME_FALLBACK_TIME_UP);
  }

  private String fallbackKey(String lang, String key) {
    if (plugin == null || plugin.getI18n() == null) return "";
    String v = plugin.getI18n().tr(lang, key);
    if (v == null || v.isBlank()) return "";
    if (v.equals(key) || v.startsWith("Translation missing:")) return "";
    return v;
  }

  private String pickFromPool(List<String> pool) {
    if (pool == null || pool.isEmpty()) return "";
    return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
  }

  private String stripJapaneseParentheticalLines(String text) {
    if (text == null || text.isBlank()) return text;

    List<String> kept = new ArrayList<>();
    for (String raw : text.split("\\R")) {
      String t = raw.trim();
      if (t.startsWith("（") && t.endsWith("）")) continue;
      kept.add(raw);
    }

    String out = String.join("\n", kept).trim();
    return out.isEmpty() ? text : out;
  }

  private List<String> toChatLines(String text) {
    List<String> out = new ArrayList<>();
    if (text == null) return out;

    for (String raw : text.split("\\R")) {
      String s = raw.trim();
      while (s.startsWith(">")) s = s.substring(1).trim();
      if (!s.isEmpty()) out.add(s);
    }
    return out;
  }
}
