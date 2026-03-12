package plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * languages/*.yml をロードするストア
 * - plugins/TreasureRun/languages/<lang>.yml
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
      }
      return YamlConfiguration.loadConfiguration(f);
    });
  }

  private void ensureExists(String lang) {
    try {
      File f = new File(dir, lang + ".yml");
      if (f.exists()) return;

      YamlConfiguration y = new YamlConfiguration();
      // ✅ 最低限：unknown（無いと missing の時に困る）
      y.set("default.unknown", "Translation missing: {key}");
      y.save(f);

      plugin.getLogger().info("[Lang] created: languages/" + lang + ".yml");
    } catch (Throwable t) {
      plugin.getLogger().warning("[Lang] failed to create languages/" + lang + ".yml: " + t.getMessage());
    }
  }
}