package plugin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class TreasureExportLangCommand implements CommandExecutor {

  private final TreasureRunMultiChestPlugin plugin;

  public TreasureExportLangCommand(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

    boolean overwrite = false;
    if (args != null && args.length >= 1) {
      overwrite = args[0].equalsIgnoreCase("overwrite") || args[0].equalsIgnoreCase("--overwrite");
    }

    // 1) 元データ：config.yml の messages.translation
    ConfigurationSection root = plugin.getConfig().getConfigurationSection("messages.translation");
    if (root == null) {
      sender.sendMessage(ChatColor.RED + "❌ messages.translation was not found in config.yml.");
      sender.sendMessage(ChatColor.GRAY + "Example: messages.translation.ja.favorites.title: ...");
      return true;
    }

    // 2) 出力先：plugins/TreasureRun/languages/
    File dir = new File(plugin.getDataFolder(), "languages");
    if (!dir.exists() && !dir.mkdirs()) {
      sender.sendMessage(ChatColor.RED + "❌ Could not create the languages/ folder: " + dir.getAbsolutePath());
      return true;
    }

    // 3) 書き出し対象の言語一覧
    Set<String> langs = new LinkedHashSet<>(root.getKeys(false));

    // config.yml の language.allowedLanguages も一応加える（translationが無くてもファイルだけ作りたい場合）
    List<String> allowed = plugin.getConfig().getStringList("language.allowedLanguages");
    if (allowed != null) {
      for (String a : allowed) {
        if (a != null && !a.isBlank()) langs.add(a.trim());
      }
    }

    if (langs.isEmpty()) {
      sender.sendMessage(ChatColor.RED + "❌ No languages are configured for export.");
      return true;
    }

    int written = 0;
    List<String> skipped = new ArrayList<>();

    // 4) default.unknown も拾えるなら拾う（あれば）
    String defaultUnknown = plugin.getConfig().getString("messages.default.unknown", "Translation missing: {key}");

    for (String lang : langs) {
      if (lang == null || lang.isBlank()) continue;
      lang = lang.trim();

      File out = new File(dir, lang + ".yml");
      if (out.exists() && !overwrite) {
        skipped.add(lang);
        continue;
      }

      YamlConfiguration y = new YamlConfiguration();

      // default.unknown は入れておく（I18n fallback 用）
      y.set("default.unknown", defaultUnknown);

      // messages.translation.<lang> の中身を languages/<lang>.yml のルートへ写す
      ConfigurationSection langSec = root.getConfigurationSection(lang);
      if (langSec != null) {
        copySection(langSec, y, "");
      } else {
        // translation が無い言語でも空ファイルとして作る（フォールバックが効く）
      }

      try {
        y.save(out);
        written++;
      } catch (Throwable t) {
        sender.sendMessage(ChatColor.RED + "❌ Export failed: " + lang + " (" + t.getMessage() + ")");
      }
    }

    sender.sendMessage(ChatColor.GREEN + "✅ Export complete: wrote " + written + " file(s).");
    sender.sendMessage(ChatColor.GRAY + "Output folder: " + dir.getAbsolutePath());

    if (!skipped.isEmpty()) {
      sender.sendMessage(ChatColor.YELLOW + "⚠ Skipped existing file(s): " + String.join(", ", skipped));
      sender.sendMessage(ChatColor.GRAY + "To overwrite existing files, run: /treasureExportLang overwrite");
    }

    sender.sendMessage(ChatColor.AQUA + "Next: make I18n read languages/*.yml to apply these translations.");
    return true;
  }

  /** langSec の内容を yml に再帰コピーする（キー構造維持） */
  private void copySection(ConfigurationSection from, YamlConfiguration to, String prefix) {
    for (String key : from.getKeys(false)) {
      Object v = from.get(key);

      String path = prefix.isEmpty() ? key : (prefix + "." + key);

      if (v instanceof ConfigurationSection nested) {
        copySection(nested, to, path);
      } else {
        to.set(path, v);
      }
    }
  }
}
