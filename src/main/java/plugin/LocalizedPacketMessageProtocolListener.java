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
 * ProtocolLib packet-level i18n audit layer.
 *
 * Purpose:
 * - Bukkit event layer handles safe high-level messages.
 * - This packet layer audits lower-level vanilla/system JSON messages.
 * - It detects Minecraft translate keys such as:
 *   multiplayer.player.joined
 *   multiplayer.player.left
 *
 * This class intentionally starts as an audit-first foundation.
 * Replacement can be expanded safely after real runtime packet keys are observed.
 */
public final class LocalizedPacketMessageProtocolListener {

  private static final Pattern TRANSLATE_PATTERN =
      Pattern.compile("\"translate\"\\s*:\\s*\"([^\"]+)\"");

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

    // Keep this layer conservative for Spigot 1.20.1 + ProtocolLib.
    // TITLE is exposed by some ProtocolLib builds but can be unregistered at runtime,
    // so we intentionally avoid title/boss/tab packets here.
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

      if (v instanceof PacketType) {
        PacketType type = (PacketType) v;
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
    Player player = event.getPlayer();
    if (player == null) return;

    PacketContainer packet = event.getPacket();
    String packetName = event.getPacketType().name();

    boolean audit = plugin.getConfig().getBoolean("packetMessages.audit", true);
    boolean debug = plugin.getConfig().getBoolean("packetMessages.debug", false);
    boolean auditAllJson = plugin.getConfig().getBoolean("packetMessages.auditAllJson", true);

    if (!audit && !debug) return;

    Set<String> jsons = new LinkedHashSet<>();

    // 1) WrappedChatComponent fields
    try {
      StructureModifier<WrappedChatComponent> comps = packet.getChatComponents();
      for (int i = 0; i < comps.size(); i++) {
        WrappedChatComponent c = comps.readSafely(i);
        if (c != null && c.getJson() != null) {
          jsons.add(c.getJson());
        }
      }
    } catch (Throwable ignored) {
    }

    // 2) Raw String fields that may contain JSON
    try {
      StructureModifier<String> strings = packet.getStrings();
      for (int i = 0; i < strings.size(); i++) {
        String s = strings.readSafely(i);
        if (s != null && looksLikeJsonOrTranslate(s)) {
          jsons.add(s);
        }
      }
    } catch (Throwable ignored) {
    }

    // 3) Debug fallback: packet summary when no JSON was found
    if (jsons.isEmpty()) {
      if (debug) {
        plugin.getLogger().info("[PacketI18n][DEBUG] player=" + player.getName()
            + " packet=" + packetName
            + " json=NONE");
      }
      return;
    }

    for (String json : jsons) {
      auditJson(player, packetName, json, auditAllJson);
    }
  }

  private boolean looksLikeJsonOrTranslate(String s) {
    String t = s.trim();
    return t.startsWith("{")
        || t.startsWith("[")
        || t.contains("\"translate\"")
        || t.contains("multiplayer.player.")
        || t.contains("death.")
        || t.contains("chat.type.")
        || t.contains("advancements.");
  }

  private void auditJson(Player player, String packetName, String json, boolean auditAllJson) {
    if (json == null || json.isBlank()) return;

    Matcher m = TRANSLATE_PATTERN.matcher(json);
    boolean foundTranslate = false;

    while (m.find()) {
      foundTranslate = true;
      String translateKey = m.group(1);
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

  private String compact(String s) {
    String x = s.replace('\n', ' ').replace('\r', ' ').trim();
    if (x.length() > 500) {
      return x.substring(0, 500) + "...";
    }
    return x;
  }
}
