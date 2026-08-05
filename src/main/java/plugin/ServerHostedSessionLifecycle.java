package plugin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns one server-hosted session and its command services across Bukkit lifecycle callbacks.
 *
 * <p>This boundary does not start gameplay. It keeps command handling, start/stop decisions,
 * WAITING-player disconnect cleanup, and plugin-disable reset attached to the same session
 * instance.</p>
 */
final class ServerHostedSessionLifecycle {
  private final ServerHostedSession session;
  private final ServerHostedSessionCommandService commandService;
  private final ServerHostedSessionControlService controlService;

  ServerHostedSessionLifecycle() {
    this(new ServerHostedSession());
  }

  ServerHostedSessionLifecycle(ServerHostedSession session) {
    this.session = Objects.requireNonNull(session, "session");
    this.commandService = new ServerHostedSessionCommandService(session);
    this.controlService = new ServerHostedSessionControlService(session);
  }

  ServerHostedSessionCommandService commandService() {
    return commandService;
  }

  ServerHostedSessionControlService controlService() {
    return controlService;
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
