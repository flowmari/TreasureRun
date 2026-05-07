package plugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FabricModDetector implements PluginMessageListener, Listener {
  public static final String HELLO_CHANNEL = "treasurerun:hello";
  private final JavaPlugin plugin;
  private final Set<UUID> fabricModPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
  public FabricModDetector(JavaPlugin plugin) { this.plugin = plugin; }
  public void register() {
    plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, HELLO_CHANNEL, this);
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
    plugin.getLogger().info("[FabricModDetector] registered: " + HELLO_CHANNEL);
  }
  @Override
  public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    if (!HELLO_CHANNEL.equals(channel) || player == null) return;
    fabricModPlayers.add(player.getUniqueId());
    plugin.getLogger().info("[FabricModDetector] Fabric mod confirmed: " + player.getName());
  }
  @EventHandler
  public void onQuit(PlayerQuitEvent event) { fabricModPlayers.remove(event.getPlayer().getUniqueId()); }
  public boolean hasFabricMod(Player player) { return player != null && fabricModPlayers.contains(player.getUniqueId()); }
  public void forget(Player player) { if (player != null) fabricModPlayers.remove(player.getUniqueId()); }
  public void clear() { fabricModPlayers.clear(); }
}
