package plugin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns one server-hosted session and its command service across Bukkit lifecycle callbacks.
 *
 * <p>This boundary does not start gameplay. It keeps command handling, WAITING-player disconnect
 * cleanup, and plugin-disable reset attached to the same session instance.</p>
 */
final class ServerHostedSessionLifecycle {

  private final ServerHostedSession session;
  private final ServerHostedSessionCommandService commandService;

  ServerHostedSessionLifecycle() {
    this(new ServerHostedSession());
  }

  ServerHostedSessionLifecycle(ServerHostedSession session) {
    this.session = Objects.requireNonNull(session, "session");
    this.commandService = new ServerHostedSessionCommandService(session);
  }

  ServerHostedSessionCommandService commandService() {
    return commandService;
  }

  ServerHostedSessionCommandService.Result handlePlayerQuit(UUID playerId) {
    return commandService.execute(
        ServerHostedSessionCommandService.Actor.player(
            Objects.requireNonNull(playerId, "playerId"),
            false
        ),
        List.of("leave")
    );
  }

  void reset() {
    session.reset();
  }
}
