package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServerHostedSessionLifecycleTest {

  @Test
  void commandAndQuitPathsShareOneStableServiceAndSession() {
    ServerHostedSessionLifecycle lifecycle = new ServerHostedSessionLifecycle();
    ServerHostedSessionCommandService service = lifecycle.commandService();
    UUID playerId = UUID.randomUUID();

    assertSame(service, lifecycle.commandService());
    execute(service, administrator(), "create");
    execute(service, player(playerId), "join");

    ServerHostedSessionCommandService.Result result =
        lifecycle.handlePlayerQuit(playerId);

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.LEFT_AND_SESSION_RESET,
        result.code()
    );
    assertEquals(ServerHostedSession.State.IDLE, result.snapshot().state());
    assertEquals(0, result.snapshot().playerCount());
  }

  @Test
  void waitingQuitRemovesOnlyTheDepartingParticipant() {
    ServerHostedSessionLifecycle lifecycle = new ServerHostedSessionLifecycle();
    ServerHostedSessionCommandService service = lifecycle.commandService();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    execute(service, administrator(), "create");
    execute(service, player(first), "join");
    execute(service, player(second), "join");

    ServerHostedSessionCommandService.Result result =
        lifecycle.handlePlayerQuit(first);

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.LEFT,
        result.code()
    );
    assertEquals(ServerHostedSession.State.WAITING, result.snapshot().state());
    assertEquals(List.of(second), result.snapshot().participants());
  }

  @Test
  void unrelatedQuitLeavesTheWaitingRosterUnchanged() {
    ServerHostedSessionLifecycle lifecycle = new ServerHostedSessionLifecycle();
    ServerHostedSessionCommandService service = lifecycle.commandService();
    UUID joined = UUID.randomUUID();

    execute(service, administrator(), "create");
    execute(service, player(joined), "join");

    ServerHostedSessionCommandService.Result result =
        lifecycle.handlePlayerQuit(UUID.randomUUID());

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.NOT_JOINED,
        result.code()
    );
    assertEquals(ServerHostedSession.State.WAITING, result.snapshot().state());
    assertEquals(List.of(joined), result.snapshot().participants());
  }

  @Test
  void lockedRosterIsNotMutatedByTheWaitingQuitBoundary() {
    ServerHostedSession session = new ServerHostedSession();
    ServerHostedSessionLifecycle lifecycle =
        new ServerHostedSessionLifecycle(session);
    ServerHostedSessionCommandService service = lifecycle.commandService();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    execute(service, administrator(), "create");
    execute(service, player(first), "join");
    execute(service, player(second), "join");
    assertEquals(ServerHostedSession.StartStatus.LOCKED,
        session.lockForStart().status());

    ServerHostedSessionCommandService.Result result =
        lifecycle.handlePlayerQuit(first);

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.SESSION_NOT_WAITING,
        result.code()
    );
    assertEquals(ServerHostedSession.State.LOCKED, result.snapshot().state());
    assertEquals(List.of(first, second), result.snapshot().participants());
  }

  @Test
  void disableResetClearsLockedRosterAndReturnsToIdle() {
    ServerHostedSession session = new ServerHostedSession();
    ServerHostedSessionLifecycle lifecycle =
        new ServerHostedSessionLifecycle(session);
    ServerHostedSessionCommandService service = lifecycle.commandService();

    execute(service, administrator(), "create");
    execute(service, player(UUID.randomUUID()), "join");
    execute(service, player(UUID.randomUUID()), "join");
    assertEquals(ServerHostedSession.StartStatus.LOCKED,
        session.lockForStart().status());

    lifecycle.reset();

    ServerHostedSessionCommandService.Snapshot snapshot = service.snapshot();
    assertEquals(ServerHostedSession.State.IDLE, snapshot.state());
    assertEquals(0, snapshot.playerCount());
    assertEquals(List.of(), snapshot.participants());
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
