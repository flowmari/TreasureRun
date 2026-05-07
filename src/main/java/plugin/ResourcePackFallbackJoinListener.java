package plugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ResourcePackFallbackJoinListener implements Listener {
  private final TreasureRunMultiChestPlugin plugin;
  public ResourcePackFallbackJoinListener(TreasureRunMultiChestPlugin plugin) { this.plugin = plugin; }
  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    ResourcePackFallbackService service = plugin.getResourcePackFallbackService();
    if (service == null) return;
    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;
      FabricModDetector det = plugin.getFabricModDetector();
      if (det != null && det.hasFabricMod(player)) return;
      String def = plugin.getConfig().getString("language.default", "ja");
      String lang = def;
      try {
        PlayerLanguageStore pls = plugin.getPlayerLanguageStore();
        if (pls != null) { String s = pls.getLang(player, def); if (s != null && !s.isBlank()) lang = s; }
      } catch (Throwable ignored) {}
      service.sendFallbackPack(player, lang);
      try { LanguageSyncService.syncSelectedLanguage(plugin, player, lang, "join-resync"); } catch (Throwable ignored) {}
    }, 20L);
  }
  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    try { ResourcePackFallbackService s = plugin.getResourcePackFallbackService(); if (s != null) s.forget(event.getPlayer()); }
    catch (Throwable ignored) {}
  }
}
