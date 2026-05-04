package plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * languages/*.yml をロードするストア
 * - plugins/TreasureRun/languages/<lang>.yml を正とする（運用で編集可能）
 * - 初回起動時: Jar同梱 resources/languages/<lang>.yml があれば dataFolder にコピー
 * - フォールバック: lang -> en -> ja -> default.unknown
 */
public class LanguagesYamlStore {

  private final JavaPlugin plugin;
  private final File dir;

  // 読み込みキャッシュ（lang -> yml）
  private final Map<String, YamlConfiguration> cache = new HashMap<>();

  public LanguagesYamlStore(JavaPlugin plugin) {
    this.plugin = plugin;
    this.dir = new File(plugin.getDataFolder(), "languages");
  }

  public synchronized void loadOrCreate() {
    try {
      if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
      if (!dir.exists()) dir.mkdirs();
    } catch (Throwable ignored) {}

    // ✅ config.yml の language.allowedLanguages を優先してファイルを用意
    List<String> langs = plugin.getConfig().getStringList("language.allowedLanguages");
    if (langs == null || langs.isEmpty()) {
      langs = Arrays.asList("ja", "en"); // 最低限
    }

    // en/ja は必ず確保（フォールバック用）
    ensureExists("en");
    ensureExists("ja");

    for (String lang : langs) {
      if (lang == null || lang.isBlank()) continue;
      ensureExists(lang.trim());
    }

    // ✅ 実務っぽく：allowedLanguagesに無い yml があれば警告（削除はしない）
    warnExtraLanguageFiles(langs);

    // reload時はキャッシュクリア
    cache.clear();
  }

  public synchronized YamlConfiguration get(String lang) {
    if (lang == null || lang.isBlank()) lang = "en";
    if (!dir.exists()) loadOrCreate();

    final String l = lang.trim();

    return cache.computeIfAbsent(l, k -> {
      File f = new File(dir, k + ".yml");
      if (!f.exists()) {
        // 無ければ en.yml を読む（ファイルが無い言語でも壊れない）
        f = new File(dir, "en.yml");
        if (!f.exists()) {
          // en.ymlすら無いのは異常なので作る
          ensureExists("en");
          f = new File(dir, "en.yml");
        }
      }
      return YamlConfiguration.loadConfiguration(f);
    });
  }

  private void ensureExists(String lang) {
    final String l = (lang == null ? "" : lang.trim());
    if (l.isEmpty()) return;

    try {
      File f = new File(dir, l + ".yml");
      if (f.exists()) return;

      // ✅ 1) Jar同梱 resources/languages/<lang>.yml があればコピー（上書きしない）
      // saveResource は resource が無いと例外になるので、getResourceで事前確認
      String resourcePath = "languages/" + l + ".yml";
      if (plugin.getResource(resourcePath) != null) {
        plugin.saveResource(resourcePath, false);
        plugin.getLogger().info("[Lang] copied from jar: " + resourcePath);
        return;
      }

      // ✅ 2) 無ければ保険：空の yml を生成（最低限 unknown を入れる）
      YamlConfiguration y = new YamlConfiguration();
      y.set("default.unknown", "Translation missing: {key}");
      y.save(f);

      plugin.getLogger().warning("[Lang] jar resource missing, created empty: languages/" + l + ".yml");
    } catch (Throwable t) {
      plugin.getLogger().warning("[Lang] failed to prepare languages/" + l + ".yml: " + t.getMessage());
    }
  }

  private void warnExtraLanguageFiles(List<String> allowedFromConfig) {
    try {
      if (!dir.exists()) return;

      // allowedLanguages + フォールバックの en/ja を許可集合に入れる
      Set<String> allowed = new HashSet<>();
      if (allowedFromConfig != null) {
        for (String s : allowedFromConfig) {
          if (s == null) continue;
          String v = s.trim();
          if (!v.isEmpty()) allowed.add(v);
        }
      }
      allowed.add("en");
      allowed.add("ja");

      File[] files = dir.listFiles((d, name) -> name != null && name.endsWith(".yml"));
      if (files == null) return;

      List<String> extras = new ArrayList<>();
      for (File file : files) {
        String name = file.getName();
        String lang = name.substring(0, name.length() - 4); // remove .yml
        if (!allowed.contains(lang)) extras.add(name);
      }

      if (!extras.isEmpty()) {
        Collections.sort(extras);
        plugin.getLogger().warning("[Lang] Extra language files exist (not in language.allowedLanguages): " + extras);
        plugin.getLogger().warning("[Lang] If you don't need them, you can remove them manually from: " + dir.getPath());
      }
    } catch (Throwable t) {
      plugin.getLogger().warning("[Lang] failed to scan extra language files: " + t.getMessage());
    }
  }

  // PACKET_I18N_SECTION_VALUE_FIX
  // Minecraft translation keys can be both a leaf and a parent:
  //   death.attack.anvil
  //   death.attack.anvil.player
  // Nested YAML cannot store a String and a Section at the same path.
  // We store the parent leaf text as "_value" and read it here.
  private String getStringOrSectionValue(YamlConfiguration yml, String path) {
    if (yml == null || path == null) return null;

    Object raw = yml.get(path);

    if (raw instanceof String) {
      return (String) raw;
    }

    if (raw instanceof ConfigurationSection) {
      String sectionValue = ((ConfigurationSection) raw).getString("_value");
      if (sectionValue != null) return sectionValue;
    }

    String direct = getStringOrSectionValue(yml, path);
    if (direct != null) return direct;

    return null;
  }


}