package plugin;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class LangCommand implements CommandExecutor, TabCompleter {

  private final TreasureRunMultiChestPlugin plugin;

  public LangCommand(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

    if (!(sender instanceof Player player)) {
      sender.sendMessage(ChatColor.RED + trDefault("command.lang.playersOnly", "Only players can run this command."));
      return true;
    }

    List<String> allowed = getAllowedLanguagesFromConfig();
    String defaultLang = plugin.getConfig().getString("language.default", "ja");

    if (args.length == 0) {
      openLanguageGuiOrFallbackMessage(player, allowed, defaultLang);
      return true;
    }

    String sub = args[0].trim();

    if (sub.equalsIgnoreCase("reset")) {
      try {
        if (plugin.getPlayerLanguageStore() != null) {
          plugin.getPlayerLanguageStore().clear(player.getUniqueId());
        }
      } catch (Throwable ignored) {}

      try {
        LanguageStore ls = plugin.getLanguageStore();
        if (ls != null) {
          ls.set(player.getUniqueId(), defaultLang);
        }
      } catch (Throwable ignored) {}

      player.sendMessage(ChatColor.GREEN + tr(player, "command.lang.resetDone", "command.lang.resetDone"));
      return true;
    }

    if (sub.equalsIgnoreCase("list")) {
      sendLanguageList(player, allowed, defaultLang);
      return true;
    }

    if (sub.equalsIgnoreCase("current")) {
      String current = resolveCurrentLang(player.getUniqueId(), defaultLang);
      String displayName = plugin.getConfig().getString("language.displayName." + current, current);
      player.sendMessage(ChatColor.AQUA + trp(
          player,
          "command.lang.current",
          java.util.Map.of("lang", displayName + " (" + current + ")"),
          "command.lang.current"
      ));
      return true;
    }

    if (sub.equalsIgnoreCase("gui")) {
      openLanguageGuiOrFallbackMessage(player, allowed, defaultLang);
      return true;
    }

    String lang = normalizeLangCode(sub);
    if (lang.isBlank()) {
      player.sendMessage(ChatColor.RED + tr(player, "command.lang.emptyCode", "command.lang.emptyCode"));
      return true;
    }

    if (!allowed.contains(lang)) {
      player.sendMessage(ChatColor.RED + trp(player, "command.lang.notAllowed", java.util.Map.of("lang", lang), "command.lang.notAllowed"));
      player.sendMessage(ChatColor.GRAY + trp(player, "command.lang.allowedList", java.util.Map.of("allowed", String.join(", ", allowed)), "command.lang.allowedList"));
      return true;
    }

    boolean saved = setPlayerLang(player.getUniqueId(), lang);
    trySetLangToLanguageStore(player.getUniqueId(), lang);
    sendLangToClient(player, lang);

    String displayName = plugin.getConfig().getString("language.displayName." + lang, lang);

    if (saved) {
      player.sendMessage(ChatColor.GREEN + trByLang(
          lang,
          "command.lang.changed",
          "command.lang.changed",
          java.util.Map.of("displayName", displayName, "lang", lang)
      ));
    } else {
      player.sendMessage(ChatColor.YELLOW + trByLang(
          lang,
          "command.lang.changedButNotSaved",
          "command.lang.changedButNotSaved",
          java.util.Map.of("lang", lang)
      ));
    }

    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (!(sender instanceof Player)) return Collections.emptyList();

    List<String> allowed = getAllowedLanguagesFromConfig();

    if (args.length == 1) {
      String prefix = args[0].toLowerCase(Locale.ROOT);

      List<String> base = new ArrayList<>();
      base.add("list");
      base.add("current");
      base.add("gui");
      base.add("reset");
      base.addAll(allowed);

      return base.stream()
          .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
          .distinct()
          .sorted()
          .collect(Collectors.toList());
    }

    return Collections.emptyList();
  }

  private List<String> getAllowedLanguagesFromConfig() {
    List<String> list = plugin.getConfig().getStringList("language.allowedLanguages");
    if (list == null || list.isEmpty()) {
      return new ArrayList<>(Arrays.asList("ja", "en"));
    }
    LinkedHashSet<String> set = new LinkedHashSet<>();
    for (String s : list) {
      String n = normalizeLangCode(s);
      if (!n.isBlank()) set.add(n);
    }
    return new ArrayList<>(set);
  }

  private String normalizeLangCode(String raw) {
    if (raw == null) return "";
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  private void sendLanguageList(Player player, List<String> allowed, String defaultLang) {
    player.sendMessage(ChatColor.AQUA + tr(player, "command.lang.list.title", "command.lang.list.title"));
    player.sendMessage(ChatColor.GRAY + trp(player, "command.lang.list.default", java.util.Map.of("default", defaultLang), "command.lang.list.default"));
    player.sendMessage(ChatColor.GRAY + trp(player, "command.lang.list.allowed", java.util.Map.of("allowed", String.join(", ", allowed)), "command.lang.list.allowed"));
    player.sendMessage(ChatColor.GRAY + tr(player, "command.lang.list.usage", "command.lang.list.usage"));
    player.sendMessage(ChatColor.GRAY + tr(player, "command.lang.list.gui", "command.lang.list.gui"));
  }

  private void openLanguageGuiOrFallbackMessage(Player player, List<String> allowed, String defaultLang) {
    try {
      LanguageSelectGui gui = plugin.getLanguageSelectGui();
      if (gui != null) {
        gui.open(player, "Normal");
        return;
      }
    } catch (Throwable ignored) {}

    player.sendMessage(ChatColor.RED + tr(player, "command.lang.guiNotReady", "command.lang.guiNotReady"));
    sendLanguageList(player, allowed, defaultLang);
  }

  private String resolveCurrentLang(UUID uuid, String defaultLang) {
    String fromPLS = tryGetLangFromPlayerLanguageStore(uuid);
    if (fromPLS != null && !fromPLS.isBlank()) return fromPLS;

    String fromLS = tryGetLangFromLanguageStore(uuid);
    if (fromLS != null && !fromLS.isBlank()) return fromLS;

    return defaultLang;
  }

  private boolean setPlayerLang(UUID uuid, String lang) {
    if (uuid == null) return false;
    try {
      PlayerLanguageStore pls = plugin.getPlayerLanguageStore();
      if (pls != null) {
        pls.set(uuid, lang);
        return true;
      }
    } catch (Throwable t) {
      plugin.getLogger().warning("[Lang] Failed to save language: " + t.getMessage());
    }
    return false;
  }

  private String tryGetLangFromPlayerLanguageStore(UUID uuid) {
    if (uuid == null) return null;
    try {
      PlayerLanguageStore pls = plugin.getPlayerLanguageStore();
      if (pls == null) return null;
      String s = pls.getLang(uuid, "");
      return (s == null || s.isBlank()) ? null : s;
    } catch (Throwable ignored) {}
    return null;
  }

  private void trySetLangToLanguageStore(UUID uuid, String lang) {
    if (uuid == null) return;
    try {
      LanguageStore store = plugin.getLanguageStore();
      if (store != null) {
        store.set(uuid, lang);
      }
    } catch (Throwable ignored) {}
  }

  private String tryGetLangFromLanguageStore(UUID uuid) {
    if (uuid == null) return null;
    try {
      LanguageStore store = plugin.getLanguageStore();
      if (store == null) return null;
      String s = store.get(uuid);
      return (s == null || s.isBlank()) ? null : s;
    } catch (Throwable ignored) {}
    return null;
  }

  private Object resolveI18nObject() {
    try {
      java.lang.reflect.Field f = plugin.getClass().getDeclaredField("i18n");
      f.setAccessible(true);
      return f.get(plugin);
    } catch (Throwable ignored) {}
    return null;
  }

  private String trDefault(String key, String fallback) {
    String lang = plugin.getConfig().getString("language.default", "ja");
    return trByLang(lang, key, fallback, null);
  }

  private String tr(Player player, String key, String fallback) {
    String lang = plugin.getConfig().getString("language.default", "ja");
    try {
      if (player != null) {
        lang = resolveCurrentLang(player.getUniqueId(), lang);
      }
    } catch (Throwable ignored) {}
    return trByLang(lang, key, fallback, null);
  }

  private String trp(Player player, String key, java.util.Map<String, String> vars, String fallback) {
    String lang = plugin.getConfig().getString("language.default", "ja");
    try {
      if (player != null) {
        lang = resolveCurrentLang(player.getUniqueId(), lang);
      }
    } catch (Throwable ignored) {}
    return trByLang(lang, key, fallback, vars);
  }

  private String trByLang(String lang, String key, String fallback, java.util.Map<String, String> vars) {
    Object i18n = resolveI18nObject();
    String out = null;

    if (i18n != null) {
      try {
        if (vars != null && !vars.isEmpty()) {
          Method m = i18n.getClass().getMethod("tr", String.class, String.class, java.util.Map.class);
          Object ret = m.invoke(i18n, lang, key, vars);
          if (ret instanceof String s && !s.isBlank() && !s.contains("Translation missing:")) {
            out = s;
          }
        }
      } catch (Throwable ignored) {}

      if (out == null) {
        try {
          Method m = i18n.getClass().getMethod("tr", String.class, String.class);
          Object ret = m.invoke(i18n, lang, key);
          if (ret instanceof String s && !s.isBlank() && !s.contains("Translation missing:")) {
            out = s;
          }
        } catch (Throwable ignored) {}
      }
    }

    if (out == null || out.isBlank()) out = fallback;

    if (vars != null && !vars.isEmpty()) {
      for (Map.Entry<String, String> e : vars.entrySet()) {
        out = out.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
      }
    }

    return ChatColor.translateAlternateColorCodes('&', out);
  }

  private void sendLangToClient(Player player, String lang) {
    LanguageSyncService.syncSelectedLanguage(plugin, player, lang, "command:/lang");
    try {
      FabricModDetector det = plugin.getFabricModDetector();
      if (det != null && det.hasFabricMod(player)) return;
      ResourcePackFallbackService fb = plugin.getResourcePackFallbackService();
      if (fb != null) fb.sendFallbackPack(player, lang);
    } catch (Throwable t) {
      plugin.getLogger().warning("[LangCommand] fallback error: " + t.getMessage());
    }
  }
}