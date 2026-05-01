package plugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Localizes selected Minecraft/Spigot engine-generated death messages.
 *
 * This listener makes engine-side visible text follow the same YAML-based i18n
 * pipeline as TreasureRun's GUI, books, chat, BossBar, ActionBar, and ranking UI.
 *
 * Design:
 * - Resolve the dead player's selected language.
 * - Classify the vanilla death message / damage cause into a stable i18n key.
 * - Render the final message from languages/<lang>.yml.
 *
 * This keeps all supported languages parallel instead of treating English as the
 * only source and other languages as secondary overrides.
 */
public class LocalizedDeathMessageListener implements Listener {

  private final TreasureRunMultiChestPlugin plugin;

  public LocalizedDeathMessageListener(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    if (player == null) return;

    String lang = resolvePlayerLang(player);
    String key = resolveDeathKey(event);

    String message = plugin.getI18n().tr(
        lang,
        key,
        I18n.Placeholder.of("{player}", player.getName())
    );

    if (message == null || message.isBlank() || message.contains("Translation missing:")) {
      message = plugin.getI18n().tr(
          lang,
          "gameplay.death.generic",
          I18n.Placeholder.of("{player}", player.getName())
      );
    }

    if (message != null && !message.isBlank()) {
      event.setDeathMessage(message);
    }
  }

  private String resolvePlayerLang(Player player) {
    String defaultLang = "ja";
    try {
      if (plugin.getConfig() != null) {
        defaultLang = plugin.getConfig().getString("language.default", "ja");
      }
    } catch (Throwable ignored) {
      defaultLang = "ja";
    }

    try {
      if (plugin.getPlayerLanguageStore() != null) {
        return plugin.getPlayerLanguageStore().getLang(player, defaultLang);
      }
    } catch (Throwable ignored) {
      // fall through
    }

    return defaultLang;
  }

  private String resolveDeathKey(PlayerDeathEvent event) {
    String raw = event.getDeathMessage();
    String lower = raw == null ? "" : raw.toLowerCase(java.util.Locale.ROOT);

    // Firework deaths are often visible as vanilla/Spigot-generated text.
    if (lower.contains("firework") || lower.contains("fireworks") || lower.contains("花火")) {
      return "gameplay.death.firework";
    }

    try {
      EntityDamageEvent last = event.getEntity().getLastDamageCause();
      if (last != null) {
        EntityDamageEvent.DamageCause cause = last.getCause();
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
            || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
          return "gameplay.death.explosion";
        }
      }
    } catch (Throwable ignored) {
      // fall through
    }

    if (lower.contains("explosion")
        || lower.contains("blew up")
        || lower.contains("blown")
        || lower.contains("爆発")
        || lower.contains("爆ぜ")) {
      return "gameplay.death.explosion";
    }

    return "gameplay.death.generic";
  }
}
