package plugin.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdministratorUpdateNotificationListenerTest {

  @Test
  void nonAdministratorDoesNotStartUpdateLookup() {
    AtomicInteger calls = new AtomicInteger();

    try (UpdateCheckService service = service(calls)) {
      JavaPlugin plugin = mock(JavaPlugin.class);
      Player player = mock(Player.class);
      when(player.hasPermission("treasure.admin")).thenReturn(false);

      AdministratorUpdateNotificationListener listener =
          new AdministratorUpdateNotificationListener(
              plugin,
              service,
              "treasure.admin",
              "%current_version% -> %new_version% %link%",
              "https://example.invalid/releases"
          );

      listener.notifyIfAdministrator(player);

      assertEquals(0, calls.get());
    }
  }

  @Test
  void updateAvailableDefersBukkitStateChecksToScheduledTask() {
    try (UpdateCheckService service = serviceWithUpdate()) {
      JavaPlugin plugin = mock(JavaPlugin.class);
      Server server = mock(Server.class);
      BukkitScheduler scheduler = mock(BukkitScheduler.class);
      Player player = mock(Player.class);

      when(player.hasPermission("treasure.admin")).thenReturn(true);
      when(plugin.getServer()).thenReturn(server);
      when(server.getScheduler()).thenReturn(scheduler);

      AdministratorUpdateNotificationListener listener =
          new AdministratorUpdateNotificationListener(
              plugin,
              service,
              "treasure.admin",
              "%current_version% -> %new_version% %link%",
              "https://example.invalid/releases"
          );

      listener.notifyIfAdministrator(player);

      ArgumentCaptor<Runnable> task =
          ArgumentCaptor.forClass(Runnable.class);
      verify(scheduler).runTask(eq(plugin), task.capture());

      verify(plugin, never()).isEnabled();
      verify(player, never()).isOnline();
      verify(player, never()).sendMessage(any(String.class));

      when(plugin.isEnabled()).thenReturn(true);
      when(player.isOnline()).thenReturn(true);

      task.getValue().run();

      verify(plugin).isEnabled();
      verify(player).isOnline();
      verify(player).sendMessage(
          "0.2.0-alpha -> v0.2.1-alpha https://example.invalid/releases"
      );
    }
  }

  @Test
  void administratorUsesCachedLookupWhenNoUpdateExists() {
    AtomicInteger calls = new AtomicInteger();

    try (UpdateCheckService service = service(calls)) {
      JavaPlugin plugin = mock(JavaPlugin.class);
      Player player = mock(Player.class);
      when(player.hasPermission("treasure.admin")).thenReturn(true);

      AdministratorUpdateNotificationListener listener =
          new AdministratorUpdateNotificationListener(
              plugin,
              service,
              "treasure.admin",
              "%current_version% -> %new_version% %link%",
              "https://example.invalid/releases"
          );

      listener.notifyIfAdministrator(player);
      listener.notifyIfAdministrator(player);

      assertEquals(1, calls.get());
    }
  }

  private UpdateCheckService serviceWithUpdate() {
    return new UpdateCheckService(
        true,
        "0.2.0-alpha",
        Duration.ofHours(6),
        () -> List.of("v0.2.1-alpha"),
        new UpdateVersionComparator(),
        Runnable::run,
        Clock.systemUTC(),
        () -> {}
    );
  }

  private UpdateCheckService service(AtomicInteger calls) {
    return new UpdateCheckService(
        true,
        "0.2.0-alpha",
        Duration.ofHours(6),
        () -> {
          calls.incrementAndGet();
          return List.of("v0.2.0-alpha");
        },
        new UpdateVersionComparator(),
        Runnable::run,
        Clock.systemUTC(),
        () -> {}
    );
  }
}
