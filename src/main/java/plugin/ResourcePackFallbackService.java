package plugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourcePackFallbackService {
  private final JavaPlugin plugin;
  private final Map<UUID, String> lastSentLang = new ConcurrentHashMap<>();
  public ResourcePackFallbackService(JavaPlugin plugin) { this.plugin = plugin; }
  public void sendFallbackPack(Player player, String lang) {
    if (!isEnabled()) return;
    if (player == null || lang == null || lang.isBlank()) return;
    String n = normalize(lang);
    String[] us = resolveUrlAndSha1(n);
    if (us == null) { plugin.getLogger().warning("[ResourcePackFallback] No pack for lang=" + n); return; }
    if (n.equals(lastSentLang.get(player.getUniqueId()))) return;
    String prompt = plugin.getConfig().getString("resourcePackFallback.prompt", "TreasureRun i18n: Accept the language pack.");
    boolean force = plugin.getConfig().getBoolean("resourcePackFallback.force", false);
    try {
      deliverPack(player, us[0], us[1], prompt, force);
      lastSentLang.put(player.getUniqueId(), n);
      plugin.getLogger().info("[ResourcePackFallback] sent lang=" + n + " -> " + player.getName());
    } catch (Throwable t) { plugin.getLogger().warning("[ResourcePackFallback] failed: " + t.getMessage()); }
  }
  public void forget(Player player) { if (player != null) lastSentLang.remove(player.getUniqueId()); }
  public void clear() { lastSentLang.clear(); }
  private boolean isEnabled() { return plugin.getConfig().getBoolean("resourcePackFallback.enabled", false); }
  private String[] resolveUrlAndSha1(String n) {
    FileConfiguration cfg = plugin.getConfig();
    String url = cfg.getString("resourcePackFallback.packs." + n + ".url", "").trim();
    String sha1 = cfg.getString("resourcePackFallback.packs." + n + ".sha1", "").trim();
    if (!url.isEmpty() && !sha1.isEmpty()) return new String[]{url, sha1};
    if (!"en".equals(n)) {
      url = cfg.getString("resourcePackFallback.packs.en.url", "").trim();
      sha1 = cfg.getString("resourcePackFallback.packs.en.sha1", "").trim();
      if (!url.isEmpty() && !sha1.isEmpty()) return new String[]{url, sha1};
    }
    return null;
  }
  private void deliverPack(Player player, String url, String sha1hex, String prompt, boolean force) throws Exception {
    byte[] hash = hexToBytes(sha1hex);
    try {
      Method m = player.getClass().getMethod("setResourcePack", String.class, byte[].class, String.class, boolean.class);
      m.invoke(player, url, hash, prompt, force); return;
    } catch (NoSuchMethodException ignored) {}
    player.setResourcePack(url, hash);
  }
  private static byte[] hexToBytes(String hex) {
    String n = hex.trim().toLowerCase(Locale.ROOT);
    if (!n.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("SHA1 must be 40 hex chars: " + hex);
    byte[] r = new byte[20];
    for (int i = 0; i < 20; i++) r[i] = (byte) Integer.parseInt(n.substring(i*2, i*2+2), 16);
    return r;
  }
  private static String normalize(String lang) { return lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT); }
}
