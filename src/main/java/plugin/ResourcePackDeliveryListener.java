package plugin;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Sends TreasureRun's server-side resource pack to players on join.
 *
 * This is part of the hybrid Minecraft i18n architecture:
 * - plugin YAML handles TreasureRun gameplay text
 * - ProtocolLib PacketI18n handles observable server packets
 * - resource pack language JSON handles client-resolved Minecraft language keys
 *
 * Honest limitation:
 * This does not guarantee control over pre-login, authentication,
 * disconnect, settings, or client-only UI text.
 */
public final class ResourcePackDeliveryListener implements Listener {

  private final JavaPlugin plugin;

  public ResourcePackDeliveryListener(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    FileConfiguration config = plugin.getConfig();

    if (!config.getBoolean("resourcePack.enabled", false)) {
      return;
    }

    String url = config.getString("resourcePack.url", "").trim();
    String sha1 = config.getString("resourcePack.sha1", "").trim();
    String prompt = config.getString(
        "resourcePack.prompt",
        "TreasureRun uses a multilingual resource pack. Please accept it for the full i18n experience."
    );
    boolean force = config.getBoolean("resourcePack.force", true);

    if (url.isEmpty() || sha1.isEmpty()) {
      plugin.getLogger().warning("[ResourcePack] skipped: resourcePack.url or resourcePack.sha1 is empty");
      return;
    }

    Player player = event.getPlayer();

    try {
      byte[] hash = hexToBytes(sha1);

      // Prefer modern Spigot/Paper signature when available:
      // Player#setResourcePack(String url, byte[] hash, String prompt, boolean force)
      try {
        Method modern = player.getClass().getMethod(
            "setResourcePack",
            String.class,
            byte[].class,
            String.class,
            boolean.class
        );
        modern.invoke(player, url, hash, prompt, force);
        plugin.getLogger().info("[ResourcePack] sent multilingual pack to " + player.getName()
            + " force=" + force + " sha1=" + sha1);
        return;
      } catch (NoSuchMethodException ignored) {
        // Fall through to older Spigot signature.
      }

      // Older Spigot-compatible signature:
      // Player#setResourcePack(String url, byte[] hash)
      player.setResourcePack(url, hash);
      plugin.getLogger().info("[ResourcePack] sent multilingual pack to " + player.getName()
          + " sha1=" + sha1 + " using legacy Spigot API");

      if (force) {
        player.sendMessage(ChatColor.YELLOW
            + "[TreasureRun] Please accept the multilingual resource pack for the full i18n experience.");
      }
    } catch (Throwable t) {
      plugin.getLogger().warning("[ResourcePack] failed to send pack to "
          + player.getName() + ": " + t.getMessage());
    }
  }

  private static byte[] hexToBytes(String hex) {
    String normalized = hex.trim().toLowerCase(Locale.ROOT);

    if (!normalized.matches("[0-9a-f]{40}")) {
      throw new IllegalArgumentException("SHA1 must be 40 hex characters: " + hex);
    }

    byte[] result = new byte[20];
    for (int i = 0; i < result.length; i++) {
      int idx = i * 2;
      result[i] = (byte) Integer.parseInt(normalized.substring(idx, idx + 2), 16);
    }
    return result;
  }
}
