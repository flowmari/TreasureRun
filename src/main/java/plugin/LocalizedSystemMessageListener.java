package plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Localizes selected Minecraft/Spigot engine/system messages through the same
 * YAML-based i18n pipeline used by TreasureRun's UI, books, chat, BossBar,
 * ActionBar, rankings, and death messages.
 *
 * Scope:
 * - Bukkit event-layer visible system messages are controllable here.
 * - Client-side/network/authentication messages are outside the plugin layer.
 *
 * Covered:
 * - join / quit / kick messages
 * - advancement announcements, broadcast per receiver language
 * - server list MOTD
 * - unknown command
 * - no permission
 */
public class LocalizedSystemMessageListener implements Listener {

  private final TreasureRunMultiChestPlugin plugin;

  public LocalizedSystemMessageListener(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
    applyAdvancementGameruleIfConfigured();
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onJoin(PlayerJoinEvent event) {
    if (!enabled()) return;

    Player player = event.getPlayer();
    String message = trFor(
        player,
        "gameplay.system.join",
        I18n.Placeholder.of("{player}", player.getName())
    );

    if (valid(message)) {
      event.setJoinMessage(message);
    }
    final String playerLang = langOf(player);
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      LanguageSyncService.syncSelectedLanguage(plugin, player, playerLang, "join:auto-sync");
    }, 40L);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onQuit(PlayerQuitEvent event) {
    if (!enabled()) return;

    Player player = event.getPlayer();
    String message = trFor(
        player,
        "gameplay.system.quit",
        I18n.Placeholder.of("{player}", player.getName())
    );

    if (valid(message)) {
      event.setQuitMessage(message);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onKick(PlayerKickEvent event) {
    if (!enabled()) return;

    Player player = event.getPlayer();
    String reason = event.getReason() == null ? "" : event.getReason();

    String message = trFor(
        player,
        "gameplay.system.kick",
        I18n.Placeholder.of("{player}", player.getName()),
        I18n.Placeholder.of("{reason}", strip(reason))
    );

    if (valid(message)) {
      event.setLeaveMessage(message);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onAdvancement(PlayerAdvancementDoneEvent event) {
    if (!enabled()) return;

    Player player = event.getPlayer();
    Advancement advancement = event.getAdvancement();

    String key = advancement == null || advancement.getKey() == null
        ? ""
        : advancement.getKey().toString();

    // Recipe advancements are noisy and not meaningful as player-facing achievements.
    if (key.contains("recipes/")) return;

    String advancementName = friendlyAdvancementName(key);

    // Broadcast per receiver language.
    // This demonstrates a deeper localization pipeline than a single global server language.
    for (Player receiver : Bukkit.getOnlinePlayers()) {
      String message = trForLang(
          langOf(receiver),
          "gameplay.system.advancement",
          I18n.Placeholder.of("{player}", player.getName()),
          I18n.Placeholder.of("{advancement}", advancementName)
      );

      if (valid(message)) {
        receiver.sendMessage(message);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onServerListPing(ServerListPingEvent event) {
    if (!enabled()) return;

    String lang = defaultLang();

    String message = trForLang(
        lang,
        "gameplay.system.serverMotd",
        I18n.Placeholder.of("{server}", "TreasureRun")
    );

    if (valid(message)) {
      event.setMotd(message);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
    if (!enabled()) return;
    if (event.isCancelled()) return;

    Player player = event.getPlayer();
    String raw = event.getMessage();
    if (raw == null || raw.isBlank() || !raw.startsWith("/")) return;

    String body = raw.substring(1).trim();
    if (body.isBlank()) return;

    String label = body.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
    if (label.isBlank()) return;

    Command command = findCommand(label);

    if (command == null) {
      event.setCancelled(true);
      player.sendMessage(trFor(
          player,
          "gameplay.system.unknownCommand",
          I18n.Placeholder.of("{command}", "/" + label)
      ));
      return;
    }

    if (!command.testPermissionSilent(player)) {
      event.setCancelled(true);
      player.sendMessage(trFor(
          player,
          "gameplay.system.noPermission",
          I18n.Placeholder.of("{command}", "/" + label)
      ));
    }
  }

  private boolean enabled() {
    try {
      return plugin.getConfig().getBoolean("systemMessages.enabled", true);
    } catch (Throwable ignored) {
      return true;
    }
  }

  private void applyAdvancementGameruleIfConfigured() {
    boolean suppressVanilla = true;
    try {
      suppressVanilla = plugin.getConfig().getBoolean("systemMessages.suppressVanillaAdvancements", true);
    } catch (Throwable ignored) {
      suppressVanilla = true;
    }

    if (!suppressVanilla) return;

    try {
      for (World world : Bukkit.getWorlds()) {
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
      }
      plugin.getLogger().info("[I18n] Vanilla advancement announcements suppressed; localized advancement messages enabled.");
    } catch (Throwable t) {
      plugin.getLogger().warning("[I18n] Could not suppress vanilla advancement announcements: " + t.getMessage());
    }
  }

  private Command findCommand(String label) {
    try {
      Command direct = Bukkit.getPluginCommand(label);
      if (direct != null) return direct;
    } catch (Throwable ignored) {
      // fall through
    }

    try {
      CommandMap map = commandMap();
      if (map != null) {
        return map.getCommand(label);
      }
    } catch (Throwable ignored) {
      // fall through
    }

    return null;
  }

  private CommandMap commandMap() {
    try {
      Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
      field.setAccessible(true);
      Object value = field.get(Bukkit.getServer());
      if (value instanceof CommandMap map) return map;
    } catch (Throwable ignored) {
      // fall through
    }
    return null;
  }

  private String friendlyAdvancementName(String rawKey) {
    if (rawKey == null || rawKey.isBlank()) return "-";

    String s = rawKey;
    int slash = s.lastIndexOf('/');
    if (slash >= 0 && slash + 1 < s.length()) {
      s = s.substring(slash + 1);
    }

    int colon = s.lastIndexOf(':');
    if (colon >= 0 && colon + 1 < s.length()) {
      s = s.substring(colon + 1);
    }

    return s.replace('_', ' ');
  }

  private String trFor(Player player, String key, I18n.Placeholder... placeholders) {
    return trForLang(langOf(player), key, placeholders);
  }

  private String trForLang(String lang, String key, I18n.Placeholder... placeholders) {
    try {
      if (plugin.getI18n() == null) return "";
      String message = plugin.getI18n().tr(lang, key, placeholders);
      return message == null ? "" : message;
    } catch (Throwable ignored) {
      return "";
    }
  }

  private String langOf(Player player) {
    String lang = defaultLang();

    try {
      if (player != null && plugin.getPlayerLanguageStore() != null) {
        lang = plugin.getPlayerLanguageStore().getLang(player, lang);
      }
    } catch (Throwable ignored) {
      // fall through
    }

    if (lang == null || lang.isBlank()) return defaultLang();
    return lang;
  }

  private String defaultLang() {
    try {
      return plugin.getConfig().getString("language.default", "ja");
    } catch (Throwable ignored) {
      return "ja";
    }
  }

  private boolean valid(String message) {
    return message != null
        && !message.isBlank()
        && !message.contains("Translation missing:")
        && !message.contains("default.unknown");
  }

  private String strip(String s) {
    if (s == null) return "";
    String stripped = ChatColor.stripColor(s);
    return stripped == null ? "" : stripped;
  }
}
