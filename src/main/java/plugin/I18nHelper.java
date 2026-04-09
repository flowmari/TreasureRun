package plugin;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;

public class I18nHelper {

  private final TreasureRunMultiChestPlugin plugin;

  public I18nHelper(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  public String resolvePlayerLang(Player player) {
    String defaultLang = plugin.getConfig().getString("language.default", "ja");
    try {
      if (player != null && plugin.getPlayerLanguageStore() != null) {
        String saved = plugin.getPlayerLanguageStore().getLang(player.getUniqueId(), "");
        if (saved != null && !saved.isBlank()) return saved;
      }
    } catch (Throwable ignored) {}
    return defaultLang;
  }

  public String tr(Player player, String key, String fallback) {
    String lang = resolvePlayerLang(player);
    String s = plugin.getI18n().tr(lang, key);
    if (s == null || s.isBlank() || s.contains("Translation missing:")) {
      return fallback;
    }
    return ChatColor.translateAlternateColorCodes('&', s);
  }

  public String trp(Player player, String key, Map<String, String> vars, String fallback) {
    String s = tr(player, key, fallback);
    for (Map.Entry<String, String> e : vars.entrySet()) {
      s = s.replace("{" + e.getKey() + "}", e.getValue());
    }
    return s;
  }

  public String trDefault(String key, String fallback) {
    String lang = plugin.getConfig().getString("language.default", "ja");
    String s = plugin.getI18n().tr(lang, key);
    if (s == null || s.isBlank() || s.contains("Translation missing:")) {
      return fallback;
    }
    return ChatColor.translateAlternateColorCodes('&', s);
  }
}
