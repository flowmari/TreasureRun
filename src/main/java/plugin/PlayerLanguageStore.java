package plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * ✅ プレイヤーごとの言語を永続化
 * 保存先: plugins/TreasureRun/player_languages.yml
 *
 * 保存形式:
 * players:
 *   <uuid>:
 *     lang: "en"
 */
public class PlayerLanguageStore {

  private final JavaPlugin plugin;
  private final File file;
  private YamlConfiguration data;

  public PlayerLanguageStore(JavaPlugin plugin) {
    this.plugin = plugin;
    this.file = new File(plugin.getDataFolder(), "player_languages.yml");
    loadOrCreate(); // ✅ 忘れ防止（おすすめ）
  }

  /** ✅ 起動時/Reload時に必ず呼ぶ（なければ生成） */
  public void loadOrCreate() {
    try {
      if (!plugin.getDataFolder().exists()) {
        plugin.getDataFolder().mkdirs();
      }
      if (!file.exists()) {
        file.createNewFile();
      }
      this.data = YamlConfiguration.loadConfiguration(file);
    } catch (IOException e) {
      plugin.getLogger().severe("[PlayerLanguageStore] Failed to create/load player_languages.yml: " + e.getMessage());
      this.data = new YamlConfiguration(); // fallback
    }
  }

  private String norm(String lang) {
    if (lang == null) return "";
    return lang.trim().toLowerCase(Locale.ROOT);
  }

  /** ✅ プレイヤーの言語取得（無ければ defaultLang） */
  public String getLang(UUID uuid, String defaultLang) {
    if (data == null) loadOrCreate();
    String key = "players." + uuid + ".lang";
    String saved = norm(data.getString(key));
    if (saved.isBlank()) return norm(defaultLang);
    return saved;
  }

  public String getLang(Player player, String defaultLang) {
    return getLang(player.getUniqueId(), defaultLang);
  }

  /** ✅ プレイヤーの言語保存 */
  public void set(UUID uuid, String lang) {
    if (data == null) loadOrCreate();
    String key = "players." + uuid + ".lang";
    data.set(key, norm(lang));
    save();
  }

  public void set(Player player, String lang) {
    set(player.getUniqueId(), lang);
  }

  /** ✅ プレイヤーの言語を削除（未設定に戻す） */
  public void clear(UUID uuid) {
    if (data == null) loadOrCreate();
    String base = "players." + uuid; // players.<uuid>.lang を含むブロックごと削除
    data.set(base, null);
    save();
  }

  public void clear(Player player) {
    clear(player.getUniqueId());
  }

  /** ✅ 保存 */
  public void save() {
    try {
      if (data == null) return;
      data.save(file);
    } catch (IOException e) {
      plugin.getLogger().severe("[PlayerLanguageStore] Failed to save player_languages.yml: " + e.getMessage());
    }
  }
}