package plugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class LanguageConfigStore {

  private String defaultLang = "ja";
  private final List<String> allowedLanguages = new ArrayList<>();

  private final Map<String, String> displayName = new HashMap<>();
  private final Map<String, String> shortLabel = new HashMap<>();
  private final Map<String, Material> iconMaterial = new HashMap<>();

  private String loreDefault = "Click to select";
  private final Map<String, String> lore = new HashMap<>();

  private String norm(String lang) {
    return (lang == null) ? "" : lang.trim().toLowerCase(Locale.ROOT);
  }

  public void reloadFromConfig(FileConfiguration config) {
    ConfigurationSection langSec = config.getConfigurationSection("language");
    if (langSec == null) {
      Bukkit.getLogger().warning("[TreasureRun][Lang] config.yml に 'language:' セクションが見つかりません。");
      return;
    }

    displayName.clear();
    shortLabel.clear();
    iconMaterial.clear();
    lore.clear();
    allowedLanguages.clear();

    defaultLang = norm(langSec.getString("default", "ja"));

    List<String> list = langSec.getStringList("allowedLanguages");
    if (list != null) {
      for (String s : list) {
        String k = norm(s);
        if (!k.isBlank()) allowedLanguages.add(k);
      }
    }
    if (allowedLanguages.isEmpty()) allowedLanguages.add(defaultLang);

    ConfigurationSection dn = langSec.getConfigurationSection("displayName");
    if (dn != null) {
      for (String key : dn.getKeys(false)) {
        String k = norm(key);
        displayName.put(k, dn.getString(key, key));
      }
    }

    ConfigurationSection sl = langSec.getConfigurationSection("shortLabel");
    if (sl != null) {
      for (String key : sl.getKeys(false)) {
        String k = norm(key);
        shortLabel.put(k, sl.getString(key, key.toUpperCase(Locale.ROOT)));
      }
    }

    ConfigurationSection im = langSec.getConfigurationSection("iconMaterial");
    if (im != null) {
      for (String key : im.getKeys(false)) {
        String k = norm(key);
        String matName = langSec.getString("iconMaterial." + key, "");
        Material mat = Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
        if (mat != null) iconMaterial.put(k, mat);
      }
    }

    loreDefault = langSec.getString("loreDefault", "Click to select");
    ConfigurationSection lo = langSec.getConfigurationSection("lore");
    if (lo != null) {
      for (String key : lo.getKeys(false)) {
        String k = norm(key);
        lore.put(k, lo.getString(key, loreDefault));
      }
    }

    Bukkit.getLogger().info("[TreasureRun][Lang] default=" + defaultLang
        + " allowed=" + allowedLanguages.size()
        + " displayName=" + displayName.size()
        + " shortLabel=" + shortLabel.size()
        + " iconMaterial=" + iconMaterial.size()
        + " lore=" + lore.size());
  }

  public String getDefaultLang() { return defaultLang; }
  public List<String> getAllowedLanguages() { return Collections.unmodifiableList(allowedLanguages); }

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