package plugin;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;

public class I18n {

  private final MessagesYamlStore store;

  public I18n(MessagesYamlStore store) {
    this.store = store;
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

  // ==============================
  // ✅ fallback つき raw 取得
  // ==============================
  private String rawString(String lang, String key) {
    FileConfiguration cfg = store.getConfig();
    if (cfg == null) return "(messages.yml not loaded)";

    // 1) lang
    String v = cfg.getString(path(lang, key));
    if (v != null) return v;

    // 2) en
    v = cfg.getString(path("en", key));
    if (v != null) return v;

    // 3) ja
    v = cfg.getString(path("ja", key));
    if (v != null) return v;

    // 4) default
    v = cfg.getString("messages.default.unknown");
    if (v != null) {
      return v.replace("{key}", key);
    }

    // 5) unknown
    return "(Translation missing: " + key + ")";
  }

  private List<String> rawStringList(String lang, String key) {
    FileConfiguration cfg = store.getConfig();
    if (cfg == null) return Collections.emptyList();

    // 1) lang
    List<String> v = cfg.getStringList(path(lang, key));
    if (v != null && !v.isEmpty()) return v;

    // 2) en
    v = cfg.getStringList(path("en", key));
    if (v != null && !v.isEmpty()) return v;

    // 3) ja
    v = cfg.getStringList(path("ja", key));
    if (v != null && !v.isEmpty()) return v;

    // 4) default unknown を list として返す
    String unknown = cfg.getString("messages.default.unknown");
    if (unknown != null) {
      return List.of(unknown.replace("{key}", key));
    }

    return List.of("(Translation missing: " + key + ")");
  }

  private String path(String lang, String key) {
    return "messages.translation." + lang + "." + key;
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

  // =======================================================
  // ✅ compatibility bridge (auto-added)
  // =======================================================

  /** Allow: new I18n(JavaPlugin) */
  public I18n(org.bukkit.plugin.java.JavaPlugin plugin) {
    this(new plugin.MessagesYamlStore(plugin));
  }

  /** Old call sites expect this method. */
  public void loadOrCreate() {
    if (this.store != null) this.store.loadOrCreate();
  }

  /** Allow: tr(lang, key, Map.of(...)) */
  public String tr(String lang, String key, java.util.Map<String, String> vars) {
    if (vars == null || vars.isEmpty()) return tr(lang, key);
    java.util.List<Placeholder> ps = new java.util.ArrayList<>();
    for (java.util.Map.Entry<String,String> e : vars.entrySet()) {
      ps.add(Placeholder.of(e.getKey(), e.getValue()));
    }
    return tr(lang, key, ps.toArray(new Placeholder[0]));
  }

}
