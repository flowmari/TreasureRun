package plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class MessagesYamlStore {

  private final JavaPlugin plugin;
  private final File file;
  private YamlConfiguration yml;

  public MessagesYamlStore(JavaPlugin plugin) {
    this.plugin = plugin;
    this.file = new File(plugin.getDataFolder(), "messages.yml");
  }

  public synchronized void loadOrCreate() {
    try {
      if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
      if (!file.exists()) file.createNewFile();
    } catch (Throwable ignored) {}
    try {
      this.yml = YamlConfiguration.loadConfiguration(file);
    } catch (Throwable t) {
      this.yml = new YamlConfiguration();
    }
  }

  public synchronized YamlConfiguration yaml() {
    if (yml == null) loadOrCreate();
    return yml;
  }

  public synchronized boolean contains(String path) {
    return yaml().contains(path);
  }

  public synchronized String getString(String path) {
    return yaml().getString(path);
  }

  public synchronized java.util.List<String> getStringList(String path) {
    return yaml().getStringList(path);
  }

  // ✅ I18n が読むための互換API
  public synchronized org.bukkit.configuration.file.FileConfiguration getConfig() {
    return yaml();
  }


  public synchronized void saveQuietly() {
    try { yaml().save(file); } catch (Throwable ignored) {}
  }
}
