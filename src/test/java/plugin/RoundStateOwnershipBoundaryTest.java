package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoundStateOwnershipBoundaryTest {

  @Test
  void serverHostedCommandsAndLegacyFacadeShareOneAuthoritativeCoordinator() {
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    ServerHostedSessionLifecycle lifecycle = new ServerHostedSessionLifecycle(coordinator);
    RoundLifecycle legacy = new RoundLifecycle(coordinator);
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    lifecycle.commandService().execute(
        ServerHostedSessionCommandService.Actor.console(true), List.of("create"));
    lifecycle.commandService().execute(
        ServerHostedSessionCommandService.Actor.player(first, false), List.of("join"));
    lifecycle.commandService().execute(
        ServerHostedSessionCommandService.Actor.player(second, false), List.of("join"));

    assertEquals(ServerHostedRoundState.WAITING, coordinator.state());
    assertEquals(ServerHostedSession.State.WAITING, lifecycle.commandService().snapshot().state());
    assertFalse(legacy.isActive());
    assertFalse(legacy.tryBeginPreparation());

    assertEquals(
        ServerHostedSessionControlService.StartCode.ROSTER_LOCKED,
        lifecycle.controlService().requestStart(true).code());
    assertEquals(ServerHostedRoundState.STARTING, coordinator.state());
    assertEquals(ServerHostedSession.State.LOCKED, lifecycle.commandService().snapshot().state());
    assertFalse(legacy.isActive());
  }

  @Test
  void legacyGamestartCompatibilityUsesTheSameCoordinatorWithoutWeakeningTwoPlayerCreateJoinStart() {
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    RoundLifecycle legacy = new RoundLifecycle(coordinator);
    ServerHostedSession session = new ServerHostedSession(coordinator);

    assertTrue(legacy.tryBeginPreparation());
    assertEquals(
        ServerHostedRoundCoordinator.OwnershipMode.LEGACY_GAMESTART_COMPATIBILITY,
        coordinator.ownershipMode());
    assertEquals(ServerHostedRoundState.STARTING, coordinator.state());
    assertEquals(RoundState.PREPARING, legacy.state());
    assertEquals(ServerHostedSession.State.IDLE, session.state());
    assertEquals(List.of(), session.participants());
    assertEquals(0, session.playerCount());
    assertFalse(session.canStart());
    assertFalse(session.create());

    assertTrue(legacy.beginCountdown());
    assertTrue(legacy.beginRunning());
    assertTrue(legacy.beginReset());
    assertTrue(legacy.claimCleanup());
    assertFalse(legacy.claimCleanup());
    legacy.completeReset();
    assertEquals(ServerHostedRoundState.IDLE, coordinator.state());
    assertEquals(
        ServerHostedRoundCoordinator.OwnershipMode.NONE,
        coordinator.ownershipMode());
  }

  @Test
  void serverHostedDisableResetDoesNotEraseAnActiveLegacyCompatibilityRound() {
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    RoundLifecycle legacy = new RoundLifecycle(coordinator);
    ServerHostedSession session = new ServerHostedSession(coordinator);

    assertTrue(legacy.tryBeginPreparation());
    session.reset();

    assertEquals(ServerHostedRoundState.STARTING, coordinator.state());
    assertTrue(legacy.is(RoundState.PREPARING));
    assertFalse(coordinator.cleanupClaimed());
  }

  @Test
  void serverHostedStopDecisionCannotMutateAnActiveLegacyCompatibilityRound() {
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    RoundLifecycle legacy = new RoundLifecycle(coordinator);
    ServerHostedSession session = new ServerHostedSession(coordinator);
    ServerHostedSessionControlService control = new ServerHostedSessionControlService(session);

    assertTrue(legacy.tryBeginPreparation());
    ServerHostedSessionControlService.StopDecision decision = control.requestStop(true);

    assertEquals(ServerHostedSessionControlService.StopCode.NO_ACTIVE_SESSION, decision.code());
    assertEquals(ServerHostedSession.State.IDLE, decision.state());
    assertEquals(List.of(), decision.participants());
    assertEquals(ServerHostedRoundState.STARTING, coordinator.state());
    assertEquals(RoundState.PREPARING, legacy.state());
    assertFalse(coordinator.cleanupClaimed());
  }

  @Test
  void legacyPreparationCannotStartWhileServerHostedSessionOwnsTheCoordinator() {
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    RoundLifecycle legacy = new RoundLifecycle(coordinator);
    ServerHostedSession session = new ServerHostedSession(coordinator);

    assertTrue(session.create());
    assertFalse(legacy.tryBeginPreparation());
    assertEquals(
        ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED,
        coordinator.ownershipMode());
    assertEquals(ServerHostedRoundState.WAITING, coordinator.state());
    assertEquals(RoundState.IDLE, legacy.state());
  }
}
