package plugin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Keeps command handling and lifecycle callbacks attached to one coordinator-backed session facade.
 *
 * <p>This boundary owns no lifecycle state and starts no gameplay.</p>
 */
final class ServerHostedSessionLifecycle {
  private final ServerHostedSession session;
  private final ServerHostedSessionCommandService commandService;
  private final ServerHostedSessionControlService controlService;
  private final PostRoundActionService postRoundActionService;

  ServerHostedSessionLifecycle() { this(new ServerHostedRoundCoordinator()); }

  ServerHostedSessionLifecycle(ServerHostedRoundCoordinator coordinator) {
    this(new ServerHostedSession(Objects.requireNonNull(coordinator, "coordinator")));
  }

  ServerHostedSessionLifecycle(ServerHostedSession session) {
    this.session = Objects.requireNonNull(session, "session");
    this.commandService = new ServerHostedSessionCommandService(session);
    this.controlService = new ServerHostedSessionControlService(session);
    this.postRoundActionService = new PostRoundActionService(session);
  }

  ServerHostedSessionCommandService commandService() { return commandService; }
  ServerHostedSessionControlService controlService() { return controlService; }
  PostRoundActionService postRoundActionService() { return postRoundActionService; }

  ServerHostedSessionCommandService.Result handlePlayerQuit(UUID playerId) {
    return commandService.execute(
        ServerHostedSessionCommandService.Actor.player(
            Objects.requireNonNull(playerId, "playerId"), false),
        List.of("leave")
    );
  }

  void reset() { session.reset(); }
}
