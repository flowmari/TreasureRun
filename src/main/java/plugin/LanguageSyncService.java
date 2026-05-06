package plugin;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends only the player's selected language code to the Fabric client mod.
 *
 * Performance design:
 * - Do NOT send 8039 keys x 20 languages over the network.
 * - The heavy language JSON assets are shipped ahead of time by Fabric Mod / ResourcePack.
 * - At runtime, the server sends only a tiny selected-language payload such as "ja" or "de".
 */
public final class LanguageSyncService {

  public static final String CHANNEL = "treasurerun:lang";
  private static final int MAX_LANG_CODE_BYTES = 64;

  private static final Map<UUID, String> LAST_SENT = new ConcurrentHashMap<>();

  private LanguageSyncService() {}

  public static void syncSelectedLanguage(Plugin plugin, Player player, String treasureRunLang, String reason) {
    if (plugin == null || player == null) return;

    String normalized = normalizeTreasureRunLang(treasureRunLang);
    if (normalized.isBlank()) {
      normalized = plugin.getConfig().getString("language.default", "ja");
      normalized = normalizeTreasureRunLang(normalized);
    }

    byte[] payload = normalized.getBytes(StandardCharsets.UTF_8);
    if (payload.length > MAX_LANG_CODE_BYTES) {
      plugin.getLogger().warning("[LanguageSync] skipped oversized lang code for "
          + player.getName() + ": " + normalized);
      return;
    }

    // Send immediately + delayed retries.
    // This makes runtime sync robust against resource-pack loading / client join timing.
    sendOnce(plugin, player, normalized, payload, reason, "now");

    long[] delays = new long[] {10L, 40L, 80L};
    for (long delay : delays) {
      final String lang = normalized;
      final byte[] copy = payload.clone();
      final String r = reason;
      plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
        if (player.isOnline()) {
          sendOnce(plugin, player, lang, copy, r, "delay_" + delay + "t");
        }
      }, delay);
    }
  }

  private static void sendOnce(Plugin plugin, Player player, String normalized, byte[] payload, String reason, String timing) {
    String last = LAST_SENT.get(player.getUniqueId());
    boolean sameAsLast = normalized.equals(last);

    try {
      player.sendPluginMessage(plugin, CHANNEL, payload);
      LAST_SENT.put(player.getUniqueId(), normalized);

      plugin.getLogger().info("[LanguageSync] sent selected lang only"
          + " player=" + player.getName()
          + " lang=" + normalized
          + " bytes=" + payload.length
          + " reason=" + safeReason(reason)
          + " timing=" + timing
          + " duplicate=" + sameAsLast
          + " channel=" + CHANNEL);
    } catch (Throwable t) {
      plugin.getLogger().warning("[LanguageSync] failed for "
          + player.getName() + " lang=" + normalized + " timing=" + timing + ": " + t.getMessage());
    }
  }

  public static void forget(Player player) {
    if (player != null) {
      LAST_SENT.remove(player.getUniqueId());
    }
  }

  private static String normalizeTreasureRunLang(String lang) {
    if (lang == null) return "";
    return lang.trim().toLowerCase(Locale.ROOT).replace('-', '_');
  }

  private static String safeReason(String reason) {
    if (reason == null || reason.isBlank()) return "unknown";
    return reason.replaceAll("[^a-zA-Z0-9_:/.-]", "_");
  }
}
