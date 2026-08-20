package plugin.update;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class AdministratorUpdateNotificationListener implements Listener {

  private final JavaPlugin plugin;
  private final UpdateCheckService updateCheckService;
  private final String permission;
  private final String messageTemplate;
  private final String link;

  public AdministratorUpdateNotificationListener(
      JavaPlugin plugin,
      UpdateCheckService updateCheckService,
      String permission,
      String messageTemplate,
      String link
  ) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.updateCheckService =
        Objects.requireNonNull(updateCheckService, "updateCheckService");
    this.permission = Objects.requireNonNull(permission, "permission");
    this.messageTemplate =
        Objects.requireNonNull(messageTemplate, "messageTemplate");
    this.link = Objects.requireNonNull(link, "link");
  }

  @EventHandler
  public void onAdministratorJoin(PlayerJoinEvent event) {
    notifyIfAdministrator(event.getPlayer());
  }

  void notifyIfAdministrator(Player player) {
    if (!player.hasPermission(permission)) {
      return;
    }

    updateCheckService.checkAsync().thenAccept(result -> {
      if (!result.hasUpdate()) {
        return;
      }

      plugin.getServer().getScheduler().runTask(plugin, () -> {
        if (!plugin.isEnabled()
            || !player.isOnline()
            || !player.hasPermission(permission)) {
          return;
        }

        String rendered = result.render(messageTemplate, link);
        player.sendMessage(
            ChatColor.translateAlternateColorCodes('&', rendered)
        );
      });
    });
  }
}
