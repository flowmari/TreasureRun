package plugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Runtime evidence layer for server-side resource pack delivery.
 *
 * This does not claim absolute control over all Minecraft client text.
 * It records whether the client accepted, declined, failed, or loaded
 * the server-delivered resource pack.
 */
public final class ResourcePackStatusAuditListener implements Listener {

  private final JavaPlugin plugin;

  public ResourcePackStatusAuditListener(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
    Player player = event.getPlayer();

    plugin.getLogger().info(
        "[ResourcePack][STATUS] player="
            + player.getName()
            + " uuid="
            + player.getUniqueId()
            + " status="
            + event.getStatus()
    );

    switch (event.getStatus()) {
      case SUCCESSFULLY_LOADED:
        player.sendMessage("§a[TreasureRun] Resource pack loaded. Hybrid i18n layer is active.");
        break;

      case DECLINED:
        player.sendMessage("§e[TreasureRun] Resource pack was declined. Some Minecraft vanilla/client language-key text may remain client-default.");
        break;

      case FAILED_DOWNLOAD:
        player.sendMessage("§c[TreasureRun] Resource pack download failed. Check URL/SHA1/network access.");
        break;

      case ACCEPTED:
        player.sendMessage("§7[TreasureRun] Resource pack accepted. Loading...");
        break;

      default:
        break;
    }
  }
}
