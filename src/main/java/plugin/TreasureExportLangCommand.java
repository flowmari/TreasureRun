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


  private String tr(String key) {
    try {
      String lang = plugin.getConfig().getString("language.default", "en");
      if (lang == null || lang.isBlank()) lang = "en";
      return plugin.getI18n().tr(lang, key);
    } catch (Throwable ignored) {
      return key;
    }
  }

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

    boolean overwrite = false;
    if (args != null && args.length >= 1) {
      overwrite = args[0].equalsIgnoreCase("overwrite") || args[0].equalsIgnoreCase("--overwrite");
    }

    // 1) Source data: messages.translation in config.yml
    ConfigurationSection root = plugin.getConfig().getConfigurationSection("messages.translation");
    if (root == null) {
      sender.sendMessage(ChatColor.RED + tr("finalAudit.command.exportMissingRoot"));
      sender.sendMessage(ChatColor.GRAY + tr("finalAudit.command.exportExample"));
      return true;
    }

    // 2) Output folder: plugins/TreasureRun/languages/
    File dir = new File(plugin.getDataFolder(), "languages");
    if (!dir.exists() && !dir.mkdirs()) {
      sender.sendMessage(ChatColor.RED + tr("finalAudit.command.exportCreateFolderFailed").replace("{path}", dir.getAbsolutePath()));
      return true;
    }

    // 3) Languages to export
    Set<String> langs = new LinkedHashSet<>(root.getKeys(false));

    // config.yml の language.allowedLanguages も一応加える（translationが無くてもファイルだけ作りたい場合）
    List<String> allowed = plugin.getConfig().getStringList("language.allowedLanguages");
    if (allowed != null) {
      for (String a : allowed) {
        if (a != null && !a.isBlank()) langs.add(a.trim());
      }
    }

    if (langs.isEmpty()) {
      sender.sendMessage(ChatColor.RED + tr("finalAudit.command.exportNoLanguages"));
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
        sender.sendMessage(ChatColor.RED + tr("finalAudit.command.exportFailed").replace("{lang}", lang).replace("{error}", String.valueOf(t.getMessage())));
      }
    }

    sender.sendMessage(ChatColor.GREEN + tr("finalAudit.command.exportComplete").replace("{count}", String.valueOf(written)));
    sender.sendMessage(ChatColor.GRAY + tr("finalAudit.command.exportOutputFolder").replace("{path}", dir.getAbsolutePath()));

    if (!skipped.isEmpty()) {
      sender.sendMessage(ChatColor.YELLOW + tr("finalAudit.command.exportSkippedExisting").replace("{langs}", String.join(", ", skipped)));
      sender.sendMessage(ChatColor.GRAY + tr("finalAudit.command.exportOverwriteHint"));
    }

    sender.sendMessage(ChatColor.AQUA + tr("finalAudit.command.exportNextStep"));
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
