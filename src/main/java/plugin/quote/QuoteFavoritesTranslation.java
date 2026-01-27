package plugin.quote;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import plugin.TreasureRunMultiChestPlugin;

import java.util.ArrayList;
import java.util.List;

public class QuoteFavoritesTranslation {

  private final TreasureRunMultiChestPlugin plugin;

  public QuoteFavoritesTranslation(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  /**
   * プレイヤー言語コードを取得
   * - 既存の LanguageStore が plugin 側にあるならそこから取るのが理想
   * - 無い場合は language.default へ fallback
   */
  public String resolvePlayerLang(Player player) {
    try {
      // ✅ あなたの既存に LanguageStore がある場合に備える（あれば利用）
      // plugin.getLanguageStore().getPlayerLang(player) みたいな実装があるならここで拾える
      // ※ 互換のため reflection にしない。無いなら無視して default。
    } catch (Throwable ignored) {}

    String def = plugin.getConfig().getString("language.default", "ja");
    return def == null ? "ja" : def;
  }

  /**
   * favorites専用の文字列取得（1行）
   */
  public String tr(Player player, String key, String fallbackRaw) {
    String lang = resolvePlayerLang(player);
    String value = getTranslationValue(lang, key);

    if (value == null) value = getTranslationValue("en", key);
    if (value == null) value = getTranslationValue("ja", key);

    String def = plugin.getConfig().getString("language.default", "ja");
    if (value == null && def != null) value = getTranslationValue(def, key);

    if (value == null) value = fallbackRaw;

    return colorize(value);
  }

  /**
   * favorites専用のリスト取得（複数行）
   */
  public List<String> trList(Player player, String key, List<String> fallback) {
    String lang = resolvePlayerLang(player);

    List<String> list = getTranslationList(lang, key);
    if (list == null) list = getTranslationList("en", key);
    if (list == null) list = getTranslationList("ja", key);

    String def = plugin.getConfig().getString("language.default", "ja");
    if (list == null && def != null) list = getTranslationList(def, key);

    if (list == null) list = fallback;

    List<String> out = new ArrayList<>();
    for (String s : list) out.add(colorize(s));
    return out;
  }

  private String getTranslationValue(String lang, String key) {
    ConfigurationSection sec = plugin.getConfig().getConfigurationSection("messages.translation." + lang);
    if (sec == null) return null;

    // favorites.title などを直接読む
    return sec.getString(key);
  }

  private List<String> getTranslationList(String lang, String key) {
    ConfigurationSection sec = plugin.getConfig().getConfigurationSection("messages.translation." + lang);
    if (sec == null) return null;
    if (!sec.isList(key)) return null;
    return sec.getStringList(key);
  }

  private String colorize(String s) {
    if (s == null) return "";
    return ChatColor.translateAlternateColorCodes('&', s);
  }
}