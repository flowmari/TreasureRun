package plugin.i18n;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class GameMenuFallbackTexts {
  private GameMenuFallbackTexts() {}

  public static final String BRAND_TITLE = "TreasureRun";

  private static final Map<String, String> PLUGIN_NOT_READY = new HashMap<>();
  static {
    PLUGIN_NOT_READY.put("ja", "プラグインの準備がまだできていません。");
    PLUGIN_NOT_READY.put("en", "Plugin not ready.");
    PLUGIN_NOT_READY.put("de", "Das Plugin ist noch nicht bereit.");
    PLUGIN_NOT_READY.put("es", "El plugin todavía no está listo.");
    PLUGIN_NOT_READY.put("fi", "Lisäosa ei ole vielä valmis.");
    PLUGIN_NOT_READY.put("fr", "Le plugin n’est pas encore prêt.");
    PLUGIN_NOT_READY.put("hi", "प्लगइन अभी तैयार नहीं है।");
    PLUGIN_NOT_READY.put("is", "Viðbótin er ekki tilbúin enn.");
    PLUGIN_NOT_READY.put("it", "Il plugin non è ancora pronto.");
    PLUGIN_NOT_READY.put("ko", "플러그인이 아직 준비되지 않았습니다.");
    PLUGIN_NOT_READY.put("la", "Additamentum nondum paratum est.");
    PLUGIN_NOT_READY.put("lzh", "外掛未備。");
    PLUGIN_NOT_READY.put("nl", "De plugin is nog niet klaar.");
    PLUGIN_NOT_READY.put("pt", "O plugin ainda não está pronto.");
    PLUGIN_NOT_READY.put("ru", "Плагин ещё не готов.");
    PLUGIN_NOT_READY.put("sa", "प्लगिन् अद्यापि सिद्धं नास्ति।");
    PLUGIN_NOT_READY.put("sv", "Pluginen är inte redo än.");
    PLUGIN_NOT_READY.put("zh_tw", "外掛尚未就緒。");
    PLUGIN_NOT_READY.put("asl_gloss", "PLUGIN NOT READY YET.");
  }

  public static String pluginNotReady(Player player) {
    String lang = detect(player);
    return PLUGIN_NOT_READY.getOrDefault(lang, PLUGIN_NOT_READY.get("en"));
  }

  private static String detect(Player player) {
    if (player == null) return "en";
    try {
      String locale = player.getLocale();
      if (locale == null || locale.isBlank()) return "en";
      String l = locale.toLowerCase(Locale.ROOT);

      if (l.startsWith("ja")) return "ja";
      if (l.startsWith("en")) return "en";
      if (l.startsWith("de")) return "de";
      if (l.startsWith("es")) return "es";
      if (l.startsWith("fi")) return "fi";
      if (l.startsWith("fr")) return "fr";
      if (l.startsWith("hi")) return "hi";
      if (l.startsWith("is")) return "is";
      if (l.startsWith("it")) return "it";
      if (l.startsWith("ko")) return "ko";
      if (l.startsWith("la")) return "la";
      if (l.startsWith("zh_tw") || l.startsWith("zh_hant") || l.startsWith("zh-tw")) return "zh_tw";
      if (l.startsWith("nl")) return "nl";
      if (l.startsWith("pt")) return "pt";
      if (l.startsWith("ru")) return "ru";
      if (l.startsWith("sa")) return "sa";
      if (l.startsWith("sv")) return "sv";
      return "en";
    } catch (Throwable ignored) {
      return "en";
    }
  }
}
