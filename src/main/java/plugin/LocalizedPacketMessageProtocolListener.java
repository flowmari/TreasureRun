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

public final class LocalizedPacketMessageProtocolListener {

  private static final Pattern TRANSLATE_PATTERN =
      Pattern.compile("\"translate\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern WITH_BLOCK_PATTERN =
      Pattern.compile("\"with\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
  private static final Pattern TEXT_PATTERN =
      Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"");

  private final TreasureRunMultiChestPlugin plugin;
  private PacketAdapter adapter;

  public LocalizedPacketMessageProtocolListener(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  public void enable() {
    if (!plugin.getConfig().getBoolean("packetMessages.enabled", true)) {
      plugin.getLogger().info("[PacketI18n] disabled by config"); return;
    }
    Plugin protocolLib = Bukkit.getPluginManager().getPlugin("ProtocolLib");
    if (protocolLib == null || !protocolLib.isEnabled()) {
      plugin.getLogger().warning("[PacketI18n] ProtocolLib not enabled. Skipped."); return;
    }
    List<PacketType> packetTypes = detectPacketTypes();
    if (packetTypes.isEmpty()) {
      plugin.getLogger().warning("[PacketI18n] no packet types. Skipped."); return;
    }
    adapter = new PacketAdapter(plugin, ListenerPriority.NORMAL, packetTypes.toArray(new PacketType[0])) {
      @Override
      public void onPacketSending(PacketEvent event) {
        try { handlePacket(event); }
        catch (Throwable t) { plugin.getLogger().warning("[PacketI18n] error: " + t.getMessage()); }
      }
    };
    ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
    plugin.getLogger().info("[PacketI18n] registered: " + packetTypes.size() + " packet type(s)");
  }

  public void disable() {
    try { if (adapter != null) { ProtocolLibrary.getProtocolManager().removePacketListener(adapter); adapter = null; } }
    catch (Throwable ignored) {}
  }

  private List<PacketType> detectPacketTypes() {
    List<PacketType> out = new ArrayList<>();
    for (String name : new String[]{
        "SYSTEM_CHAT","CHAT","PLAYER_CHAT","DISGUISED_CHAT",
        "SET_TITLE_TEXT","SET_SUBTITLE_TEXT","SET_ACTION_BAR_TEXT","TITLE",
        "BOSS","KICK_DISCONNECT","DISCONNECT","OPEN_WINDOW",
        "TAB_HEADER_AND_FOOTER","SCOREBOARD_OBJECTIVE","SCOREBOARD_TEAM"
    }) addIfExists(out, name);
    return out;
  }

  private void addIfExists(List<PacketType> out, String name) {
    try {
      Field f = PacketType.Play.Server.class.getField(name);
      Object v = f.get(null);
      if (v instanceof PacketType type) { out.add(type); plugin.getLogger().info("[PacketI18n] registered: " + name); }
    } catch (Throwable ignored) {}
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
        WrappedChatComponent c = comps.readSafely(i);
        if (c == null) continue;
        String json = c.getJson();
        if (json == null || json.isBlank()) continue;
        jsons.add(json);
        if (replace) {
          String loc = localizeJsonDeep(player, json);
          if (isUsable(loc)) { comps.write(i, WrappedChatComponent.fromText(loc)); touched = true;
            if (audit) plugin.getLogger().info("[PacketI18n][REPLACE] " + player.getName() + " packet=" + packetName + " text=" + compact(loc)); }
        }
      }
    } catch (Throwable t) { if (debug) plugin.getLogger().warning("[PacketI18n] comp scan: " + t.getMessage()); }
    try {
      StructureModifier<String> strings = packet.getStrings();
      for (int i = 0; i < strings.size(); i++) {
        String s = strings.readSafely(i);
        if (s != null && looksLikeJson(s)) {
          jsons.add(s);
          if (replace) {
            String loc = localizeJsonDeep(player, s);
            if (isUsable(loc)) {
              try { strings.write(i, WrappedChatComponent.fromText(loc).getJson()); touched = true;
                if (audit) plugin.getLogger().info("[PacketI18n][REPLACE] " + player.getName() + " packet=" + packetName + " src=STRING text=" + compact(loc)); }
              catch (Throwable w) { if (debug) plugin.getLogger().warning("[PacketI18n] write: " + w.getMessage()); }
            }
          }
        }
      }
    } catch (Throwable ignored) {}
    if (jsons.isEmpty()) return;
    if (audit || debug) for (String json : jsons) auditJson(player, packetName, json, auditAllJson);
  }

  private String localizeJsonDeep(Player player, String json) {
    String topKey = extractTranslateKey(json);
    if (topKey == null || topKey.isBlank()) return null;
    String lang = resolvePlayerLang(player);
    String yamlKey = toYamlKey(topKey);
    List<String> resolvedArgs = resolveWithArgs(player, json, lang);
    List<I18n.Placeholder> placeholders = new ArrayList<>();
    for (int i = 0; i < resolvedArgs.size() && i < 10; i++)
      placeholders.add(I18n.Placeholder.of("{arg" + i + "}", resolvedArgs.get(i)));
    String loc = plugin.getI18n().tr(lang, yamlKey, placeholders.toArray(new I18n.Placeholder[0]));
    if (!isUsable(loc) || isFallback(loc, yamlKey, topKey)) return null;
    return loc;
  }

  private List<String> resolveWithArgs(Player player, String json, String lang) {
    Matcher wm = WITH_BLOCK_PATTERN.matcher(json);
    if (!wm.find()) return extractTextFallback(json);
    List<String> args = new ArrayList<>();
    for (String elem : splitJsonArray(wm.group(1))) {
      String r = resolveElem(player, elem.trim(), lang);
      if (r != null) args.add(r);
    }
    return args;
  }

  private String resolveElem(Player player, String elem, String lang) {
    if (elem.startsWith("{")) {
      Matcher tm = TRANSLATE_PATTERN.matcher(elem);
      if (tm.find()) {
        String nk = unescape(tm.group(1));
        String nyk = toYamlKey(nk);
        String res = plugin.getI18n().tr(lang, nyk);
        return (isUsable(res) && !isFallback(res, nyk, nk)) ? res : nk;
      }
      Matcher txm = TEXT_PATTERN.matcher(elem);
      if (txm.find()) return unescape(txm.group(1));
    } else if (elem.startsWith("\"")) {
      return unescape(elem.replaceAll("^\"|\"$", ""));
    }
    return null;
  }

  private List<String> splitJsonArray(String block) {
    List<String> result = new ArrayList<>();
    int depth = 0; StringBuilder cur = new StringBuilder();
    for (char c : block.toCharArray()) {
      if (c == '{') depth++; if (c == '}') depth--;
      if (c == ',' && depth == 0) { result.add(cur.toString().trim()); cur.setLength(0); }
      else cur.append(c);
    }
    if (!cur.toString().isBlank()) result.add(cur.toString().trim());
    return result;
  }

  private List<String> extractTextFallback(String json) {
    List<String> out = new ArrayList<>();
    Matcher m = TEXT_PATTERN.matcher(json);
    while (m.find() && out.size() < 10) { String v = unescape(m.group(1)); if (v != null && !v.isBlank()) out.add(v); }
    return out;
  }

  private String resolvePlayerLang(Player player) {
    String lang = plugin.getConfig().getString("language.default", "ja");
    try { if (plugin.getPlayerLanguageStore() != null) lang = plugin.getPlayerLanguageStore().getLang(player, lang); }
    catch (Throwable ignored) {}
    return (lang == null || lang.isBlank()) ? "ja" : lang;
  }

  private String extractTranslateKey(String json) {
    Matcher m = TRANSLATE_PATTERN.matcher(json);
    return m.find() ? unescape(m.group(1)) : null;
  }

  private boolean isUsable(String s) { return s != null && !s.isBlank(); }

  private boolean isFallback(String loc, String yamlKey, String translateKey) {
    if (loc == null) return true;
    String plain = loc.replace("§c","").replace("§r","").trim();
    return plain.equals(yamlKey) || plain.equals(translateKey)
        || plain.contains("Translation missing:") || plain.contains("(Translation missing:");
  }

  private boolean looksLikeJson(String s) {
    String t = s.trim();
    return t.startsWith("{") || t.startsWith("[") || t.contains("\"translate\"")
        || t.contains("multiplayer.player.") || t.contains("death.") || t.contains("chat.type.")
        || t.contains("command.") || t.contains("commands.") || t.contains("advancements.")
        || t.contains("entity.") || t.contains("effect.") || t.contains("enchantment.")
        || t.contains("gamerule.") || t.contains("biome.") || t.contains("stat.")
        || t.contains("attribute.") || t.contains("item.minecraft.") || t.contains("block.minecraft.");
  }

  private void auditJson(Player player, String packetName, String json, boolean auditAllJson) {
    Matcher m = TRANSLATE_PATTERN.matcher(json); boolean found = false;
    while (m.find()) { found = true;
      plugin.getLogger().info("[PacketI18n][AUDIT] player=" + player.getName() + " packet=" + packetName + " translate=" + unescape(m.group(1))); }
    if (!found && auditAllJson)
      plugin.getLogger().info("[PacketI18n][AUDIT] player=" + player.getName() + " packet=" + packetName + " json=" + compact(json));
  }

  private String toYamlKey(String k) {
    return (k == null || k.isBlank()) ? "minecraft.packet.unknown" : "minecraft.packet." + k;
  }

  private String unescape(String s) {
    if (s == null) return "";
    return s.replace("\\\\","\\").replace("\\\"","\"").replace("\\n","\n").replace("\\r","\r").replace("\\t","\t");
  }

  private String compact(String s) {
    String x = s.replace('\n',' ').replace('\r',' ').trim();
    return x.length() > 500 ? x.substring(0,500) + "..." : x;
  }
}
