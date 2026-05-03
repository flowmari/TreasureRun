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
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ProtocolLib packet-level i18n layer.
 *
 * Purpose:
 * - Audit observable server-to-client chat/system packets.
 * - Detect Minecraft JSON components containing "translate".
 * - Optionally replace observed translate keys with TreasureRun YAML-backed text:
 *
 *   minecraft.packet.<vanilla translate key>
 *
 * Scope:
 * - Conservative packet targets for Spigot 1.20.1 + ProtocolLib.
 * - This does not claim full Minecraft client UI localization.
 * - Client-side screens still require a resource pack.
 */
public final class LocalizedPacketMessageProtocolListener {

  private static final Pattern TRANSLATE_PATTERN =
      Pattern.compile("\"translate\"\\s*:\\s*\"([^\"]+)\"");

  private static final Pattern TEXT_PATTERN =
      Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"");

  private final TreasureRunMultiChestPlugin plugin;
  private PacketAdapter adapter;

  public LocalizedPacketMessageProtocolListener(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  public void enable() {
    if (!plugin.getConfig().getBoolean("packetMessages.enabled", true)) {
      plugin.getLogger().info("[PacketI18n] disabled by config: packetMessages.enabled=false");
      return;
    }

    Plugin protocolLib = Bukkit.getPluginManager().getPlugin("ProtocolLib");
    if (protocolLib == null || !protocolLib.isEnabled()) {
      plugin.getLogger().warning("[PacketI18n] ProtocolLib is not installed/enabled. Packet-level i18n skipped.");
      return;
    }

    List<PacketType> packetTypes = detectPacketTypes();

    if (packetTypes.isEmpty()) {
      plugin.getLogger().warning("[PacketI18n] no supported chat packet types detected. Packet-level i18n skipped.");
      return;
    }

    adapter = new PacketAdapter(
        plugin,
        ListenerPriority.NORMAL,
        packetTypes.toArray(new PacketType[0])
    ) {
      @Override
      public void onPacketSending(PacketEvent event) {
        try {
          handlePacket(event);
        } catch (Throwable t) {
          plugin.getLogger().warning("[PacketI18n] packet handling failed: "
              + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
      }
    };

    ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
    plugin.getLogger().info("[PacketI18n] ProtocolLib packet listener registered: " + packetTypeNames(packetTypes));
  }

  public void disable() {
    try {
      if (adapter != null) {
        ProtocolLibrary.getProtocolManager().removePacketListener(adapter);
        adapter = null;
      }
    } catch (Throwable ignored) {
    }
  }

  private List<PacketType> detectPacketTypes() {
    List<PacketType> out = new ArrayList<>();

    // Keep this layer conservative.
    // Some ProtocolLib builds expose title/boss/tab constants that are not registered at runtime.
    addPacketTypeIfExists(out, "SYSTEM_CHAT");
    addPacketTypeIfExists(out, "CHAT");
    addPacketTypeIfExists(out, "PLAYER_CHAT");
    addPacketTypeIfExists(out, "DISGUISED_CHAT");

    return out;
  }

  private void addPacketTypeIfExists(List<PacketType> out, String name) {
    try {
      Field f = PacketType.Play.Server.class.getField(name);
      Object v = f.get(null);

      if (v instanceof PacketType type) {
        out.add(type);
        plugin.getLogger().info("[PacketI18n] detected packet type: " + name);
      }
    } catch (Throwable ignored) {
      // ProtocolLib version does not expose this packet name.
    }
  }

  private String packetTypeNames(List<PacketType> packetTypes) {
    List<String> names = new ArrayList<>();
    for (PacketType t : packetTypes) {
      names.add(t.name());
    }
    return String.join(" / ", names);
  }

  private void handlePacket(PacketEvent event) {
    if (event == null || event.isCancelled()) return;

    Player player = event.getPlayer();
    if (player == null) return;

    PacketContainer packet = event.getPacket();
    if (packet == null) return;

    String packetName = event.getPacketType().name();

    boolean audit = plugin.getConfig().getBoolean("packetMessages.audit", false);
    boolean debug = plugin.getConfig().getBoolean("packetMessages.debug", false);
    boolean auditAllJson = plugin.getConfig().getBoolean("packetMessages.auditAllJson", false);
    boolean replace = plugin.getConfig().getBoolean("packetMessages.replaceTranslatedComponents", false);

    if (!audit && !debug && !replace) return;

    boolean touched = false;
    Set<String> jsons = new LinkedHashSet<>();

    try {
      StructureModifier<WrappedChatComponent> comps = packet.getChatComponents();

      for (int i = 0; i < comps.size(); i++) {
        WrappedChatComponent component = comps.readSafely(i);
        if (component == null) continue;

        String json = component.getJson();
        if (json == null || json.isBlank()) continue;

        jsons.add(json);

        if (replace) {
          String localized = localizeJson(player, json);
          if (isUsableLocalizedText(localized)) {
            comps.write(i, WrappedChatComponent.fromText(localized));
            touched = true;

            if (audit) {
              plugin.getLogger().info("[PacketI18n][REPLACE] player=" + player.getName()
                  + " packet=" + packetName
                  + " text=" + compact(localized));
            }
          }
        }
      }
    } catch (Throwable t) {
      if (debug || audit) {
        plugin.getLogger().warning("[PacketI18n] chat component scan failed: "
            + t.getClass().getSimpleName() + ": " + t.getMessage());
      }
    }

    try {
      StructureModifier<String> strings = packet.getStrings();
      for (int i = 0; i < strings.size(); i++) {
        String s = strings.readSafely(i);
        if (s != null && looksLikeJsonOrTranslate(s)) {
          jsons.add(s);

          if (replace) {
            String localized = localizeJson(player, s);
            if (isUsableLocalizedText(localized)) {
              try {
                String replacementJson = WrappedChatComponent.fromText(localized).getJson();
                strings.write(i, replacementJson);
                touched = true;

                if (audit) {
                  plugin.getLogger().info("[PacketI18n][REPLACE] player=" + player.getName()
                      + " packet=" + packetName
                      + " source=STRING"
                      + " text=" + compact(localized));
                }
              } catch (Throwable writeFailure) {
                if (debug || audit) {
                  plugin.getLogger().warning("[PacketI18n] string replacement failed: "
                      + writeFailure.getClass().getSimpleName() + ": " + writeFailure.getMessage());
                }
              }
            }
          }
        }
      }
    } catch (Throwable ignored) {
    }

    if (jsons.isEmpty()) {
      if (debug) {
        plugin.getLogger().info("[PacketI18n][DEBUG] player=" + player.getName()
            + " packet=" + packetName
            + " json=NONE");
      }
      return;
    }

    if (audit || debug) {
      for (String json : jsons) {
        auditJson(player, packetName, json, auditAllJson);
      }
    }

    if (touched && debug) {
      plugin.getLogger().info("[PacketI18n][DEBUG] packet component replaced for " + player.getName());
    }
  }

  private String localizeJson(Player player, String json) {
    String translateKey = extractTranslateKey(json);
    if (translateKey == null || translateKey.isBlank()) return null;

    String yamlKey = toYamlKey(translateKey);
    String lang = resolvePlayerLang(player);

    List<String> args = extractTextArgs(json);
    List<I18n.Placeholder> placeholders = new ArrayList<>();

    for (int i = 0; i < args.size() && i < 10; i++) {
      placeholders.add(I18n.Placeholder.of("{arg" + i + "}", args.get(i)));
    }

    String localized = plugin.getI18n().tr(
        lang,
        yamlKey,
        placeholders.toArray(new I18n.Placeholder[0])
    );

    if (!isUsableLocalizedText(localized)) return null;
    if (localized.equals(yamlKey) || localized.equals(translateKey)) return null;
    if (isMissingTranslationFallback(localized, yamlKey, translateKey)) return null;

    return localized;
  }

  private boolean isMissingTranslationFallback(String localized, String yamlKey, String translateKey) {
    if (localized == null) return true;

    String plain = localized
        .replace("§c", "")
        .replace("§r", "")
        .trim();

    return plain.equals(yamlKey)
        || plain.equals(translateKey)
        || plain.contains("Translation missing:")
        || plain.contains("(Translation missing:")
        || plain.contains(yamlKey + ")")
        || plain.contains(translateKey + ")");
  }

  private String resolvePlayerLang(Player player) {
    String lang = plugin.getConfig().getString("language.default", "ja");

    try {
      if (plugin.getPlayerLanguageStore() != null) {
        lang = plugin.getPlayerLanguageStore().getLang(player, lang);
      }
    } catch (Throwable ignored) {
    }

    if (lang == null || lang.isBlank()) lang = "ja";
    return lang;
  }

  private String extractTranslateKey(String json) {
    Matcher m = TRANSLATE_PATTERN.matcher(json);
    if (!m.find()) return null;
    return unescapeJson(m.group(1));
  }

  private List<String> extractTextArgs(String json) {
    List<String> out = new ArrayList<>();

    Matcher m = TEXT_PATTERN.matcher(json);
    while (m.find() && out.size() < 10) {
      String value = unescapeJson(m.group(1));
      if (value == null) continue;

      // Root JSON often has "text":"" beside the real translatable component.
      if (value.isBlank()) continue;

      out.add(value);
    }

    return out;
  }

  private boolean isUsableLocalizedText(String s) {
    return s != null && !s.isBlank();
  }

  private boolean looksLikeJsonOrTranslate(String s) {
    String t = s.trim();
    return t.startsWith("{")
        || t.startsWith("[")
        || t.contains("\"translate\"")
        || t.contains("multiplayer.player.")
        || t.contains("death.")
        || t.contains("chat.type.")
        || t.contains("command.")
        || t.contains("commands.")
        || t.contains("advancements.");
  }

  private void auditJson(Player player, String packetName, String json, boolean auditAllJson) {
    if (json == null || json.isBlank()) return;

    Matcher m = TRANSLATE_PATTERN.matcher(json);
    boolean foundTranslate = false;

    while (m.find()) {
      foundTranslate = true;
      String translateKey = unescapeJson(m.group(1));
      String yamlKey = toYamlKey(translateKey);

      plugin.getLogger().info("[PacketI18n][AUDIT] player=" + player.getName()
          + " packet=" + packetName
          + " translate=" + translateKey
          + " yaml=" + yamlKey);
    }

    if (!foundTranslate && auditAllJson) {
      plugin.getLogger().info("[PacketI18n][AUDIT] player=" + player.getName()
          + " packet=" + packetName
          + " json=" + compact(json));
    }
  }

  private String toYamlKey(String translateKey) {
    if (translateKey == null || translateKey.isBlank()) {
      return "minecraft.packet.unknown";
    }
    return "minecraft.packet." + translateKey;
  }

  private String unescapeJson(String s) {
    if (s == null) return "";
    return s
        .replace("\\\\", "\\")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t");
  }

  private String compact(String s) {
    String x = s.replace('\n', ' ').replace('\r', ' ').trim();
    if (x.length() > 500) {
      return x.substring(0, 500) + "...";
    }
    return x;
  }
}
