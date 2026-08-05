package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServerHostedSessionControlServiceTest {

  @Test
  void nonAdministratorStartIsRejectedWithoutMutation() {
    ServerHostedSession session = waitingSession();
    UUID player = UUID.randomUUID();
    session.join(player);
    ServerHostedSessionControlService service =
        new ServerHostedSessionControlService(session);

    ServerHostedSessionControlService.StartDecision result =
        service.requestStart(false);

    assertEquals(
        ServerHostedSessionControlService.StartCode.ADMIN_REQUIRED,
        result.code()
    );
    assertEquals(ServerHostedSession.State.WAITING, result.state());
    assertEquals(List.of(player), result.participants());
    assertEquals(ServerHostedSession.State.WAITING, session.state());
  }

  @Test
  void tooFewPlayerStartRemainsWaiting() {
    ServerHostedSession session = waitingSession();
    UUID player = UUID.randomUUID();
    session.join(player);
    ServerHostedSessionControlService service =
        new ServerHostedSessionControlService(session);

    ServerHostedSessionControlService.StartDecision result =
        service.requestStart(true);

    assertEquals(
        ServerHostedSessionControlService.StartCode.TOO_FEW_PLAYERS,
        result.code()
    );
    assertEquals(ServerHostedSession.State.WAITING, result.state());
    assertEquals(List.of(player), result.participants());
    assertEquals(ServerHostedSession.State.WAITING, session.state());
  }

  @Test
  void twoPlayerStartLocksOneOrderedImmutableSnapshotThroughSharedLifecycle() {
    ServerHostedSessionLifecycle lifecycle = new ServerHostedSessionLifecycle();
    ServerHostedSessionCommandService commands = lifecycle.commandService();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    execute(commands, administrator(), "create");
    execute(commands, player(first), "join");
    execute(commands, player(second), "join");

    ServerHostedSessionControlService.StartDecision result =
        lifecycle.controlService().requestStart(true);

    assertEquals(
        ServerHostedSessionControlService.StartCode.ROSTER_LOCKED,
        result.code()
    );
    assertEquals(ServerHostedSession.State.LOCKED, result.state());
    assertEquals(List.of(first, second), result.participants());
    assertThrows(
        UnsupportedOperationException.class,
        () -> result.participants().add(UUID.randomUUID())
    );
    assertEquals(
        ServerHostedSession.State.LOCKED,
        commands.snapshot().state()
    );
  }

  @Test
  void repeatedStartIsRejectedAfterLockedWithoutRosterMutation() {
    ServerHostedSession session = twoPlayerWaitingSession();
    ServerHostedSessionControlService service =
        new ServerHostedSessionControlService(session);
    ServerHostedSessionControlService.StartDecision first =
        service.requestStart(true);

    ServerHostedSessionControlService.StartDecision second =
        service.requestStart(true);

    assertEquals(
        ServerHostedSessionControlService.StartCode.ROSTER_LOCKED,
        first.code()
    );
    assertEquals(
        ServerHostedSessionControlService.StartCode.SESSION_NOT_WAITING,
        second.code()
    );
    assertEquals(ServerHostedSession.State.LOCKED, second.state());
    assertEquals(first.participants(), second.participants());
  }

  @Test
  void nonAdministratorStopIsRejectedWithoutMutation() {
    ServerHostedSession session = twoPlayerWaitingSession();
    ServerHostedSessionControlService service =
        new ServerHostedSessionControlService(session);
    List<UUID> before = session.participants();

    ServerHostedSessionControlService.StopDecision result =
        service.requestStop(false);

    assertEquals(
        ServerHostedSessionControlService.StopCode.ADMIN_REQUIRED,
        result.code()
    );
    assertEquals(ServerHostedSession.State.WAITING, result.state());
    assertEquals(before, result.participants());
    assertEquals(before, session.participants());
  }

  @Test
  void idleStopReturnsNoActiveSession() {
    ServerHostedSession session = new ServerHostedSession();
    ServerHostedSessionControlService service =
        new ServerHostedSessionControlService(session);

    ServerHostedSessionControlService.StopDecision result =
        service.requestStop(true);

    assertEquals(
        ServerHostedSessionControlService.StopCode.NO_ACTIVE_SESSION,
        result.code()
    );
    assertEquals(ServerHostedSession.State.IDLE, result.state());
    assertEquals(List.of(), result.participants());
  }

  @Test
  void waitingStopResetsDirectlyToIdle() {
    ServerHostedSession session = twoPlayerWaitingSession();
    ServerHostedSessionControlService service =
        new ServerHostedSessionControlService(session);
    List<UUID> before = session.participants();

    ServerHostedSessionControlService.StopDecision result =
        service.requestStop(true);

    assertEquals(
        ServerHostedSessionControlService.StopCode.WAITING_RESET,
        result.code()
    );
    assertEquals(ServerHostedSession.State.IDLE, result.state());
    assertEquals(before, result.participants());
    assertEquals(ServerHostedSession.State.IDLE, session.state());
    assertEquals(List.of(), session.participants());
  }

  @Test
  void lockedStopPreservesRosterAndRequestsCleanup() {
    ServerHostedSession session = twoPlayerWaitingSession();
    ServerHostedSessionControlService service =
        new ServerHostedSessionControlService(session);
    ServerHostedSessionControlService.StartDecision start =
        service.requestStart(true);

    ServerHostedSessionControlService.StopDecision stop =
        service.requestStop(true);

    assertEquals(
        ServerHostedSessionControlService.StopCode.CLEANUP_REQUIRED,
        stop.code()
    );
    assertEquals(ServerHostedSession.State.LOCKED, stop.state());
    assertEquals(start.participants(), stop.participants());
    assertEquals(ServerHostedSession.State.LOCKED, session.state());
    assertEquals(start.participants(), session.participants());
  }

  private static ServerHostedSession waitingSession() {
    ServerHostedSession session = new ServerHostedSession();
    session.create();
    return session;
  }

  private static ServerHostedSession twoPlayerWaitingSession() {
    ServerHostedSession session = waitingSession();
    session.join(UUID.randomUUID());
    session.join(UUID.randomUUID());
    return session;
  }

  private static ServerHostedSessionCommandService.Result execute(
      ServerHostedSessionCommandService service,
      ServerHostedSessionCommandService.Actor actor,
      String command
  ) {
    return service.execute(actor, List.of(command));
  }

  private static ServerHostedSessionCommandService.Actor administrator() {
    return ServerHostedSessionCommandService.Actor.console(true);
  }

  private static ServerHostedSessionCommandService.Actor player(UUID playerId) {
    return ServerHostedSessionCommandService.Actor.player(playerId, false);
  }
}
