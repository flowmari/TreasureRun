package plugin;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * I18n (languages/*.yml 方式)
 *
 * - plugins/TreasureRun/languages/<lang>.yml を読む
 * - fallback: lang -> en -> ja -> default.unknown
 *
 * 互換:
 * - new I18n(JavaPlugin) / loadOrCreate() / tr(...) / trList(...)
 */
public class I18n {

  // ✅ languages/*.yml store
  private final LanguagesYamlStore store;

  // ✅ warn once 用（ログスパム防止）
  private final org.bukkit.plugin.java.JavaPlugin plugin;
  private final Set<String> warned = ConcurrentHashMap.newKeySet();

  // ==============================
  // ✅ 基本コンストラクタ
  // ==============================
  public I18n(org.bukkit.plugin.java.JavaPlugin plugin) {
    this.plugin = plugin;
    this.store = new LanguagesYamlStore(plugin);
  }

  private void warnOnce(String requestedLang, String key, String fallbackLang) {
    if (plugin == null) return;
    if (requestedLang == null || requestedLang.isBlank()) return;
    if ("en".equalsIgnoreCase(requestedLang)) return; // en 自体は基準言語なので騒がない

    String id = requestedLang + ":" + key + "->" + fallbackLang;
    if (warned.add(id)) {
      plugin.getLogger().warning("[I18n] Missing key '" + key + "' for lang=" + requestedLang
          + ", falling back to " + fallbackLang);
    }
  }

  // ==============================
  // ✅ 読み込み（必要ならファイル作成）
  // ==============================
  public void loadOrCreate() {
    store.loadOrCreate();
  }

  // ==============================
  // ✅ String 取得（壊れない）
  // ==============================
  public String tr(String lang, String key) {
    String raw = rawString(lang, key);
    return color(raw);
  }

  // ==============================
  // ✅ List<String> 取得（壊れない）
  // ==============================
  public List<String> trList(String lang, String key) {
    List<String> raw = rawStringList(lang, key);
    if (raw == null) return Collections.emptyList();
    return raw.stream().map(this::color).toList();
  }

  // ==============================
  // ✅ プレースホルダー置換（任意）
  // ==============================
  public String tr(String lang, String key, Placeholder... placeholders) {
    String s = tr(lang, key);
    for (Placeholder p : placeholders) {
      s = s.replace(p.key, p.value);
    }
    return s;
  }

  public List<String> trList(String lang, String key, Placeholder... placeholders) {
    List<String> list = trList(lang, key);
    return list.stream().map(line -> {
      String s = line;
      for (Placeholder p : placeholders) {
        s = s.replace(p.key, p.value);
      }
      return s;
    }).toList();
  }

  // ✅ Map 置換互換
  public String tr(String lang, String key, Map<String, String> vars) {
    if (vars == null || vars.isEmpty()) return tr(lang, key);
    java.util.List<Placeholder> ps = new java.util.ArrayList<>();
    for (Map.Entry<String, String> e : vars.entrySet()) {
      ps.add(Placeholder.of(e.getKey(), e.getValue()));
    }
    return tr(lang, key, ps.toArray(new Placeholder[0]));
  }

  // ==============================
  // ✅ fallback つき raw 取得
  // ==============================
  private String rawString(String lang, String key) {
    if (key == null || key.isBlank()) return "";

    // 1) requested lang
    String v = getStringSafe(store.get(lang), key);
    if (v != null) return v;

    // 2) en
    v = getStringSafe(store.get("en"), key);
    if (v != null) {
      warnOnce(lang, key, "en");
      return v;
    }

    // 3) ja
    v = getStringSafe(store.get("ja"), key);
    if (v != null) {
      warnOnce(lang, key, "ja");
      return v;
    }

    // 4) default unknown
    String unknown = getStringSafe(store.get(lang), "default.unknown");
    if (unknown == null) unknown = getStringSafe(store.get("en"), "default.unknown");
    if (unknown == null) unknown = getStringSafe(store.get("ja"), "default.unknown");
    if (unknown == null) unknown = "Translation missing: {key}";

    warnOnce(lang, key, "default.unknown");
    return unknown.replace("{key}", key);
  }

  private List<String> rawStringList(String lang, String key) {
    if (key == null || key.isBlank()) return Collections.emptyList();

    // 1) requested lang
    List<String> v = getStringListSafe(store.get(lang), key);
    if (v != null && !v.isEmpty()) return v;

    // 2) en
    v = getStringListSafe(store.get("en"), key);
    if (v != null && !v.isEmpty()) {
      warnOnce(lang, key, "en");
      return v;
    }

    // 3) ja
    v = getStringListSafe(store.get("ja"), key);
    if (v != null && !v.isEmpty()) {
      warnOnce(lang, key, "ja");
      return v;
    }

    // 4) default unknown を list として返す
    String unknown = getStringSafe(store.get("en"), "default.unknown");
    if (unknown == null) unknown = "Translation missing: {key}";

    warnOnce(lang, key, "default.unknown");
    return List.of(unknown.replace("{key}", key));
  }

  // PACKET_I18N_SECTION_VALUE_FIX
  // Minecraft translation keys can be both a leaf and a parent:
  //   death.attack.anvil
  //   death.attack.anvil.player
  // Nested YAML cannot store both a String and a Section at the same path.
  // We store the parent leaf text as "_value" and read it here.
  private String getStringSafe(FileConfiguration cfg, String path) {
    try {
      if (cfg == null || path == null || path.isBlank()) return null;

      Object raw = cfg.get(path);

      if (raw instanceof String) {
        return (String) raw;
      }

      if (raw instanceof ConfigurationSection) {
        String sectionValue = ((ConfigurationSection) raw).getString("_value");
        if (sectionValue != null) return sectionValue;
      }

      return cfg.getString(path);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private List<String> getStringListSafe(FileConfiguration cfg, String path) {
    try {
      if (cfg == null) return null;
      if (!cfg.isList(path)) return null;
      List<String> list = cfg.getStringList(path);
      return (list == null || list.isEmpty()) ? null : list;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private String color(String s) {
    if (s == null) return "";
    return ChatColor.translateAlternateColorCodes('&', s);
  }

  // ✅ placeholder helper
  public static class Placeholder {
    private final String key;
    private final String value;

    private Placeholder(String key, String value) {
      this.key = key;
      this.value = value;
    }

    public static Placeholder of(String key, String value) {
      return new Placeholder(key, value);
    }
  }
}