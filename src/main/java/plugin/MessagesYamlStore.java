package plugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class MessagesYamlStore {

  private final JavaPlugin plugin;
  private final File file;
  private FileConfiguration config;

  public MessagesYamlStore(JavaPlugin plugin) {
    this.plugin = plugin;
    this.file = new File(plugin.getDataFolder(), "messages.yml"); // plugins/TreasureRun/messages.yml
  }

  public void load() {
    if (!plugin.getDataFolder().exists()) {
      plugin.getDataFolder().mkdirs();
    }

    // ✅ なければ jar内 resources/messages.yml をコピーして生成（最強）
    if (!file.exists()) {
      try {
        plugin.saveResource("messages.yml", false);
      } catch (IllegalArgumentException ignored) {
        // jar内に無い場合でも落ちない（超安全）
      }
    }

    this.config = YamlConfiguration.loadConfiguration(file);
  }

  public void reload() {
    load();
  }

  public FileConfiguration getConfig() {
    return config;
  }

  public File getFile() {
    return file;
  }
}
