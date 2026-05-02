package plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ProtocolLib packet-level i18n layer.
 *
 * Purpose:
 * - Intercept server -> client chat/system packets.
 * - Detect vanilla JSON components with "translate": "...".
 * - Convert them to TreasureRun languages/*.yml keys:
 *
 *   minecraft.packet.<vanilla translate key>
 *
 * Example:
 *   JSON translate: multiplayer.player.joined
 *   YAML key:       minecraft.packet.multiplayer.player.joined
 *
 * Notes:
 * - This is intentionally conservative.
 * - Bukkit event-layer messages should stay in LocalizedSystemMessageListener / LocalizedDeathMessageListener.
 * - This class covers the lower packet layer for messages that escape Bukkit events.
 */
public final class LocalizedPacketMessageProtocolListener {

  private static final Pattern TRANSLATE_PATTERN =
      Pattern.compile("\\\"translate\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

  private static final Pattern TEXT_ARG_PATTERN =
      Pattern.compile("\\\"text\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

  private final TreasureRunMultiChestPlugin plugin;
  private PacketAdapter adapter;

  public LocalizedPacketMessageProtocolListener(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  public void enable() {
    if (!enabled()) {
      plugin.getLogger().info("[PacketI18n] disabled by config: packetMessages.enabled=false");
      return;
    }

    Plugin protocolLib = Bukkit.getPluginManager().getPlugin("ProtocolLib");
    if (protocolLib == null || !protocolLib.isEnabled()) {
      plugin.getLogger().warning("[PacketI18n] ProtocolLib is not installed/enabled. Packet-level i18n skipped.");
      return;
    }

    adapter = new PacketAdapter(
        plugin,
        ListenerPriority.NORMAL,
        PacketType.Play.Server.SYSTEM_CHAT,
        PacketType.Play.Server.CHAT
    ) {
      @Override
      public void onPacketSending(PacketEvent event) {
        handlePacket(event);
      }
    };

    ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
    plugin.getLogger().info("[PacketI18n] ProtocolLib packet listener registered: SYSTEM_CHAT / CHAT");
  }

  public void disable() {
    try {
      if (adapter != null) {
        ProtocolLibrary.getProtocolManager().removePacketListener(adapter);
        adapter = null;
      }
    } catch (Throwable ignored) {
      // no-op
    }
  }

  private void handlePacket(PacketEvent event) {
    if (event == null || event.isCancelled()) return;
    if (!enabled()) return;

    Player player = event.getPlayer();
    if (player == null) return;

    PacketContainer packet = event.getPacket();
    if (packet == null) return;

    try {
      StructureModifier<WrappedChatComponent> components = packet.getChatComponents();
      if (components == null || components.size() <= 0) return;

      WrappedChatComponent component = components.read(0);
      if (component == null) return;

      String json = component.getJson();
      if (json == null || json.isBlank()) return;

      audit(player, json);

      if (!replaceTranslatedComponents()) return;

      String localized = localizeJsonComponent(player, json);
      if (localized == null || localized.isBlank()) return;

      components.write(0, WrappedChatComponent.fromText(localized));
    } catch (Throwable t) {
      if (auditEnabled()) {
        plugin.getLogger().warning("[PacketI18n] packet handling failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
      }
    }
  }

  private String localizeJsonComponent(Player player, String json) {
    String translateKey = extractTranslateKey(json);
    if (translateKey == null || translateKey.isBlank()) return null;

    String yamlKey = "minecraft.packet." + translateKey;
    String lang = resolvePlayerLang(player);

    List<String> args = extractTextArgs(json);
    I18n.Placeholder[] placeholders = new I18n.Placeholder[Math.min(args.size(), 10)];
    for (int i = 0; i < placeholders.length; i++) {
      placeholders[i] = I18n.Placeholder.of("{arg" + i + "}", strip(args.get(i)));
    }

    String translated = plugin.getI18n().tr(lang, yamlKey, placeholders);

    if (!valid(translated)) {
      if (auditEnabled()) {
        plugin.getLogger().info("[PacketI18n] missing yaml key: " + yamlKey + " lang=" + lang);
      }
      return null;
    }

    return translated;
  }

  private String extractTranslateKey(String json) {
    Matcher m = TRANSLATE_PATTERN.matcher(json);
    if (!m.find()) return null;
    return unescapeJson(m.group(1));
  }

  private List<String> extractTextArgs(String json) {
    List<String> args = new ArrayList<>();
    Matcher m = TEXT_ARG_PATTERN.matcher(json);
    while (m.find() && args.size() < 10) {
      String value = unescapeJson(m.group(1));
      if (value != null && !value.isBlank()) {
        args.add(value);
      }
    }
    return args;
  }

  private String resolvePlayerLang(Player player) {
    String lang = "ja";

    try {
      lang = plugin.getConfig().getString("language.default", "ja");
    } catch (Throwable ignored) {
      lang = "ja";
    }

    try {
      if (plugin.getPlayerLanguageStore() != null) {
        lang = plugin.getPlayerLanguageStore().getLang(player, lang);
      }
    } catch (Throwable ignored) {
      // fall through
    }

    if (lang == null || lang.isBlank()) return "ja";
    return lang.toLowerCase(Locale.ROOT);
  }

  private boolean enabled() {
    try {
      return plugin.getConfig().getBoolean("packetMessages.enabled", true);
    } catch (Throwable ignored) {
      return true;
    }
  }

  private boolean auditEnabled() {
    try {
      return plugin.getConfig().getBoolean("packetMessages.audit", true);
    } catch (Throwable ignored) {
      return true;
    }
  }

  private boolean replaceTranslatedComponents() {
    try {
      return plugin.getConfig().getBoolean("packetMessages.replaceTranslatedComponents", true);
    } catch (Throwable ignored) {
      return true;
    }
  }

  private void audit(Player player, String json) {
    if (!auditEnabled()) return;
    if (json == null || json.isBlank()) return;

    String translateKey = extractTranslateKey(json);
    if (translateKey != null && !translateKey.isBlank()) {
      plugin.getLogger().info("[PacketI18n][AUDIT] player=" + player.getName()
          + " translate=" + translateKey
          + " yaml=minecraft.packet." + translateKey);
      return;
    }

    // ログが巨大化しないよう短く切る
    String compact = json.replace('\n', ' ').replace('\r', ' ');
    if (compact.length() > 220) compact = compact.substring(0, 220) + "...";
    plugin.getLogger().info("[PacketI18n][AUDIT] player=" + player.getName() + " json=" + compact);
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

  private String unescapeJson(String s) {
    if (s == null) return "";
    return s
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t");
  }
}
