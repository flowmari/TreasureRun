package plugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * ✅ FavoritesTemplatesLoader
 *
 * favorites_templates.yml を読み込み、
 * config.yml (plugin.getConfig()) に「不足分だけ」合成する。
 *
 * ✅ 重要仕様
 * - config.yml は編集しない（saveConfigしない）
 * - favorites_templates.yml 側は “追加分だけ”
 * - 既存キーは上書きしない（壊さない）
 */
public class FavoritesTemplatesLoader {

  private final JavaPlugin plugin;

  private File templatesFile;
  private FileConfiguration templatesConfig;

  public FavoritesTemplatesLoader(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  /**
   * ✅ 初回起動時、plugins/PluginName/favorites_templates.yml を自動生成
   * （src/main/resources/favorites_templates.yml が必要）
   */
  public void ensureTemplatesFileExists() {
    templatesFile = new File(plugin.getDataFolder(), "favorites_templates.yml");

    if (!templatesFile.exists()) {
      plugin.getDataFolder().mkdirs();
      plugin.saveResource("favorites_templates.yml", false);
      plugin.getLogger().info("[TreasureRun] favorites_templates.yml created.");
    }
  }

  /**
   * ✅ favorites_templates.yml を読み込む
   */
  public void reloadTemplates() {
    if (templatesFile == null) {
      templatesFile = new File(plugin.getDataFolder(), "favorites_templates.yml");
    }
    templatesConfig = YamlConfiguration.loadConfiguration(templatesFile);
  }

  /**
   * ✅ config.yml 側へ“不足分だけ”合成する（上書きしない）
   *
   * 合成対象は
   * messages.translation.<lang>.favorites.chapter.successTpl1/2
   * messages.translation.<lang>.favorites.chapter.timeupTpl1/2
   */
  public void mergeInto(FileConfiguration baseConfig) {
    if (templatesConfig == null) return;

    ConfigurationSection fromRoot = templatesConfig.getConfigurationSection("messages.translation");
    if (fromRoot == null) return;

    ConfigurationSection toRoot = baseConfig.getConfigurationSection("messages.translation");
    if (toRoot == null) {
      // config.yml 側に translation がなければ丸ごと作る（でも通常あなたのconfigにはある）
      baseConfig.createSection("messages.translation");
      toRoot = baseConfig.getConfigurationSection("messages.translation");
    }

    // ✅ 不足分だけ再帰的に合成
    mergeSectionIfMissing(fromRoot, toRoot, "messages.translation");
  }

  /**
   * ✅ “存在しないキーだけ”を base 側へコピーする
   * - Section 同士なら再帰
   * - 値なら set する（上書きはしない）
   */
  private void mergeSectionIfMissing(ConfigurationSection from, ConfigurationSection to, String currentPath) {
    for (String key : from.getKeys(false)) {
      Object fromValue = from.get(key);
      String childPath = currentPath + "." + key;

      // ✅ base 側の参照
      Object toValue = to.get(key);

      if (fromValue instanceof ConfigurationSection fromChildSection) {

        ConfigurationSection toChildSection = (toValue instanceof ConfigurationSection)
            ? (ConfigurationSection) toValue
            : to.createSection(key);

        mergeSectionIfMissing(fromChildSection, toChildSection, childPath);
        continue;
      }

      // ✅ 値（String / List / Number / Boolean etc）
      if (toValue == null) {
        to.set(key, fromValue); // ←不足分だけ追加
      }
    }
  }
}