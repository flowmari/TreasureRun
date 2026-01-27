package plugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class LanguageStore {

  private String defaultLang = "ja";
  private final List<String> allowedLanguages = new ArrayList<>();

  private final Map<String, String> displayName = new HashMap<>();
  private final Map<String, String> shortLabel = new HashMap<>();
  private final Map<String, Material> iconMaterial = new HashMap<>();

  private String loreDefault = "Click to select";
  private final Map<String, String> lore = new HashMap<>();

  // プレイヤーごとの選択言語（メモリ保持）
  private final Map<UUID, String> selectedByPlayer = new HashMap<>();

  private String norm(String lang) {
    return (lang == null) ? "" : lang.trim().toLowerCase(Locale.ROOT);
  }

  // ✅ config.yml から読み直し（キー完全一致）
  public void reloadFromConfig(FileConfiguration config) {
    ConfigurationSection langSec = config.getConfigurationSection("language");
    if (langSec == null) {
      Bukkit.getLogger().warning("[TreasureRun][Lang] config.yml に 'language:' セクションが見つかりません。");
      return;
    }

    // 初期化（再読み込みで古い値が残らないように）
    displayName.clear();
    shortLabel.clear();
    iconMaterial.clear();
    lore.clear();
    allowedLanguages.clear();

    defaultLang = norm(langSec.getString("default", "ja"));

    // allowedLanguages
    List<String> list = langSec.getStringList("allowedLanguages");
    if (list != null) {
      for (String s : list) {
        String k = norm(s);
        if (!k.isBlank()) allowedLanguages.add(k);
      }
    }
    if (allowedLanguages.isEmpty()) allowedLanguages.add(defaultLang);

    // displayName
    ConfigurationSection dn = langSec.getConfigurationSection("displayName");
    if (dn != null) {
      for (String key : dn.getKeys(false)) {
        String k = norm(key);
        displayName.put(k, dn.getString(key, key));
      }
    }

    // shortLabel
    ConfigurationSection sl = langSec.getConfigurationSection("shortLabel");
    if (sl != null) {
      for (String key : sl.getKeys(false)) {
        String k = norm(key);
        shortLabel.put(k, sl.getString(key, key.toUpperCase(Locale.ROOT)));
      }
    }

    // iconMaterial（ここが読めないと全部 PAPER になります）
    ConfigurationSection im = langSec.getConfigurationSection("iconMaterial");
    if (im != null) {
      for (String key : im.getKeys(false)) {
        String k = norm(key);
        String matName = im.getString(key, "");
        if (matName == null) matName = "";
        matName = matName.trim();

        Material mat = null;
        if (!matName.isBlank()) {
          mat = Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
          if (mat == null) mat = Material.matchMaterial(matName);
        }

        if (mat != null) {
          iconMaterial.put(k, mat);
        } else {
          Bukkit.getLogger().warning("[TreasureRun][Lang] 無効なMaterial: lang=" + k + " value='" + matName + "'");
        }
      }
    } else {
      Bukkit.getLogger().warning("[TreasureRun][Lang] 'language.iconMaterial:' セクションが見つかりません（全部PAPERになります）");
    }

    // loreDefault / lore
    loreDefault = langSec.getString("loreDefault", "Click to select");

    ConfigurationSection lo = langSec.getConfigurationSection("lore");
    if (lo != null) {
      for (String key : lo.getKeys(false)) {
        String k = norm(key);
        lore.put(k, lo.getString(key, loreDefault));
      }
    }

    // ✅ 読み取り結果ログ（ここが“確実に原因特定”のカギ）
    Bukkit.getLogger().info("[TreasureRun][Lang] default=" + defaultLang
        + " allowed=" + allowedLanguages.size()
        + " displayName=" + displayName.size()
        + " shortLabel=" + shortLabel.size()
        + " iconMaterial=" + iconMaterial.size()
        + " lore=" + lore.size());
  }

  public String getDefaultLang() {
    return defaultLang;
  }

  public List<String> getAllowedLanguages() {
    return Collections.unmodifiableList(allowedLanguages);
  }

  public void set(UUID playerId, String lang) {
    selectedByPlayer.put(playerId, norm(lang));
  }

  public String get(UUID playerId) {
    return selectedByPlayer.getOrDefault(playerId, defaultLang);
  }

  public String getDisplayName(String lang) {
    String k = norm(lang);
    return displayName.getOrDefault(k, k);
  }

  public String getShortLabel(String lang) {
    String k = norm(lang);
    return shortLabel.getOrDefault(k, k.toUpperCase(Locale.ROOT));
  }

  public String getLore(String lang) {
    String k = norm(lang);
    return lore.getOrDefault(k, loreDefault);
  }

  public Material getIconMaterial(String lang) {
    String k = norm(lang);
    return iconMaterial.getOrDefault(k, Material.PAPER);
  }
}