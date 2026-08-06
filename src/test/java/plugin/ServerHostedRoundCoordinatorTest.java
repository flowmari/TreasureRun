package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServerHostedRoundCoordinatorTest {

  @Test
  void initialStateIsIdleAndContainsNoParticipants() {
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();

    assertEquals(ServerHostedSession.MIN_PLAYERS, ServerHostedRoundCoordinator.MIN_PLAYERS);
    assertEquals(ServerHostedSession.MAX_PLAYERS, ServerHostedRoundCoordinator.MAX_PLAYERS);
    assertEquals(ServerHostedRoundState.IDLE, coordinator.state());
    assertEquals(List.of(), coordinator.participants());
    assertEquals(0, coordinator.playerCount());
    assertFalse(coordinator.canStart());
    assertFalse(coordinator.cleanupClaimed());
    assertEquals(java.util.Optional.empty(), coordinator.resetCause());
  }

  @Test
  void createOpensWaitingAndRejectsSecondCreate() {
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();

    assertEquals(ServerHostedRoundCoordinator.CreateCode.CREATED, coordinator.create());
    assertEquals(ServerHostedRoundState.WAITING, coordinator.state());
    assertEquals(
        ServerHostedRoundCoordinator.CreateCode.SESSION_ALREADY_EXISTS,
        coordinator.create()
    );
    assertEquals(ServerHostedRoundState.WAITING, coordinator.state());
  }

  @Test
  void waitingRosterSupportsTwoToEightPlayersAndRejectsOverflow() {
    ServerHostedRoundCoordinator coordinator = waitingCoordinator();
    List<UUID> participants = new ArrayList<>();

    for (int index = 0; index < ServerHostedRoundCoordinator.MAX_PLAYERS; index++) {
      UUID playerId = UUID.randomUUID();
      participants.add(playerId);
      assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(playerId));
    }

    assertEquals(participants, coordinator.participants());
    assertEquals(ServerHostedRoundCoordinator.MAX_PLAYERS, coordinator.playerCount());
    assertTrue(coordinator.canStart());
    assertEquals(
        ServerHostedRoundCoordinator.JoinCode.ALREADY_JOINED,
        coordinator.join(participants.get(0))
    );
    assertEquals(
        ServerHostedRoundCoordinator.JoinCode.SESSION_FULL,
        coordinator.join(UUID.randomUUID())
    );
  }

  @Test
  void leavingLastWaitingParticipantReturnsToIdle() {
    ServerHostedRoundCoordinator coordinator = waitingCoordinator();
    UUID playerId = UUID.randomUUID();
    assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(playerId));

    assertEquals(
        ServerHostedRoundCoordinator.LeaveCode.LEFT_AND_SESSION_RESET,
        coordinator.leave(playerId)
    );
    assertEquals(ServerHostedRoundState.IDLE, coordinator.state());
    assertEquals(List.of(), coordinator.participants());
  }

  @Test
  void startRequiresAtLeastTwoPlayers() {
    ServerHostedRoundCoordinator coordinator = waitingCoordinator();
    UUID playerId = UUID.randomUUID();
    coordinator.join(playerId);

    ServerHostedRoundCoordinator.StartDecision decision = coordinator.start();

    assertEquals(ServerHostedRoundCoordinator.StartCode.TOO_FEW_PLAYERS, decision.code());
    assertEquals(ServerHostedRoundState.WAITING, decision.state());
    assertEquals(List.of(playerId), decision.participants());
    assertEquals(ServerHostedRoundState.WAITING, coordinator.state());
  }

  @Test
  void startAtomicallyFreezesImmutableParticipantSnapshot() {
    ServerHostedRoundCoordinator coordinator = waitingCoordinator();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    coordinator.join(first);
    coordinator.join(second);

    ServerHostedRoundCoordinator.StartDecision decision = coordinator.start();

    assertEquals(ServerHostedRoundCoordinator.StartCode.STARTING, decision.code());
    assertEquals(ServerHostedRoundState.STARTING, coordinator.state());
    assertEquals(List.of(first, second), decision.participants());
    assertEquals(List.of(first, second), coordinator.participants());
    assertThrows(UnsupportedOperationException.class, () -> decision.participants().add(UUID.randomUUID()));
    assertThrows(UnsupportedOperationException.class, () -> coordinator.participants().clear());
  }

  @Test
  void startingCountdownAndRunningFollowExactOrder() {
    ServerHostedRoundCoordinator coordinator = startedCoordinator();

    assertEquals(
        ServerHostedRoundCoordinator.TransitionCode.INVALID_STATE,
        coordinator.beginRunning()
    );
    assertEquals(
        ServerHostedRoundCoordinator.TransitionCode.TRANSITIONED,
        coordinator.beginCountdown()
    );
    assertEquals(ServerHostedRoundState.COUNTDOWN, coordinator.state());
    assertEquals(
        ServerHostedRoundCoordinator.TransitionCode.INVALID_STATE,
        coordinator.beginCountdown()
    );
    assertEquals(
        ServerHostedRoundCoordinator.TransitionCode.TRANSITIONED,
        coordinator.beginRunning()
    );
    assertEquals(ServerHostedRoundState.RUNNING, coordinator.state());
  }

  @Test
  void postStartRosterMutationsAndSecondStartAreRejected() {
    ServerHostedRoundCoordinator coordinator = startedCoordinator();
    List<UUID> snapshot = coordinator.participants();

    assertEquals(
        ServerHostedRoundCoordinator.JoinCode.SESSION_NOT_WAITING,
        coordinator.join(UUID.randomUUID())
    );
    assertEquals(
        ServerHostedRoundCoordinator.LeaveCode.SESSION_NOT_WAITING,
        coordinator.leave(snapshot.get(0))
    );
    assertEquals(
        ServerHostedRoundCoordinator.CreateCode.SESSION_ALREADY_EXISTS,
        coordinator.create()
    );
    assertEquals(
        ServerHostedRoundCoordinator.StartCode.SESSION_NOT_WAITING,
        coordinator.start().code()
    );
    assertEquals(snapshot, coordinator.participants());
  }

  @Test
  void resetFromWaitingCapturesCurrentRoster() {
    ServerHostedRoundCoordinator coordinator = waitingCoordinator();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    coordinator.join(first);
    coordinator.join(second);

    ServerHostedRoundCoordinator.ResetDecision decision = coordinator.requestReset(
        ServerHostedRoundCoordinator.ResetCause.STOP_REQUESTED
    );

    assertEquals(ServerHostedRoundCoordinator.ResetCode.RESETTING, decision.code());
    assertEquals(ServerHostedRoundState.RESETTING, decision.state());
    assertEquals(List.of(first, second), decision.participants());
    assertEquals(List.of(first, second), coordinator.participants());
  }

  @Test
  void everyResetCauseConvergesOnResetting() {
    for (ServerHostedRoundCoordinator.ResetCause cause
        : ServerHostedRoundCoordinator.ResetCause.values()) {
      ServerHostedRoundCoordinator coordinator = startedCoordinator();

      ServerHostedRoundCoordinator.ResetDecision decision = coordinator.requestReset(cause);

      assertEquals(ServerHostedRoundCoordinator.ResetCode.RESETTING, decision.code());
      assertEquals(ServerHostedRoundState.RESETTING, coordinator.state());
      assertEquals(java.util.Optional.of(cause), decision.cause());
      assertEquals(java.util.Optional.of(cause), coordinator.resetCause());
    }
  }

  @Test
  void repeatedResetPreservesFirstCauseAndParticipantSnapshot() {
    ServerHostedRoundCoordinator coordinator = startedCoordinator();
    List<UUID> snapshot = coordinator.participants();

    ServerHostedRoundCoordinator.ResetDecision first = coordinator.requestReset(
        ServerHostedRoundCoordinator.ResetCause.PARTICIPANT_DISCONNECTED
    );
    ServerHostedRoundCoordinator.ResetDecision second = coordinator.requestReset(
        ServerHostedRoundCoordinator.ResetCause.PLUGIN_DISABLED
    );

    assertEquals(ServerHostedRoundCoordinator.ResetCode.RESETTING, first.code());
    assertEquals(ServerHostedRoundCoordinator.ResetCode.ALREADY_RESETTING, second.code());
    assertEquals(
        java.util.Optional.of(ServerHostedRoundCoordinator.ResetCause.PARTICIPANT_DISCONNECTED),
        second.cause()
    );
    assertEquals(snapshot, second.participants());
    assertEquals(snapshot, coordinator.participants());
  }

  @Test
  void cleanupCanBeClaimedExactlyOnce() {
    ServerHostedRoundCoordinator coordinator = startedCoordinator();
    List<UUID> snapshot = coordinator.participants();
    coordinator.requestReset(ServerHostedRoundCoordinator.ResetCause.STOP_REQUESTED);

    java.util.Optional<ServerHostedRoundCoordinator.CleanupClaim> first = coordinator.claimCleanup();
    java.util.Optional<ServerHostedRoundCoordinator.CleanupClaim> second = coordinator.claimCleanup();

    assertTrue(first.isPresent());
    assertEquals(ServerHostedRoundCoordinator.ResetCause.STOP_REQUESTED, first.orElseThrow().cause());
    assertEquals(snapshot, first.orElseThrow().participants());
    assertTrue(coordinator.cleanupClaimed());
    assertTrue(second.isEmpty());
  }

  @Test
  void completeResetRequiresClaimAndClearsState() {
    ServerHostedRoundCoordinator coordinator = startedCoordinator();
    coordinator.requestReset(ServerHostedRoundCoordinator.ResetCause.ROUND_COMPLETED);

    assertThrows(IllegalStateException.class, coordinator::completeReset);
    coordinator.claimCleanup();
    coordinator.completeReset();

    assertEquals(ServerHostedRoundState.IDLE, coordinator.state());
    assertEquals(List.of(), coordinator.participants());
    assertEquals(0, coordinator.playerCount());
    assertFalse(coordinator.cleanupClaimed());
    assertEquals(java.util.Optional.empty(), coordinator.resetCause());
  }

  @Test
  void resetFromIdleIsRejectedWithoutCreatingCleanupWork() {
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();

    ServerHostedRoundCoordinator.ResetDecision decision = coordinator.requestReset(
        ServerHostedRoundCoordinator.ResetCause.PLUGIN_DISABLED
    );

    assertEquals(ServerHostedRoundCoordinator.ResetCode.NO_ACTIVE_ROUND, decision.code());
    assertEquals(ServerHostedRoundState.IDLE, coordinator.state());
    assertTrue(coordinator.claimCleanup().isEmpty());
  }

  @Test
  void coordinatorCanBeginFreshSessionAfterCompletedReset() {
    ServerHostedRoundCoordinator coordinator = startedCoordinator();
    coordinator.requestReset(ServerHostedRoundCoordinator.ResetCause.TIME_EXPIRED);
    coordinator.claimCleanup();
    coordinator.completeReset();

    assertEquals(ServerHostedRoundCoordinator.CreateCode.CREATED, coordinator.create());
    assertEquals(ServerHostedRoundState.WAITING, coordinator.state());
    assertEquals(List.of(), coordinator.participants());
  }

  private static ServerHostedRoundCoordinator waitingCoordinator() {
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    assertEquals(ServerHostedRoundCoordinator.CreateCode.CREATED, coordinator.create());
    return coordinator;
  }

  private static ServerHostedRoundCoordinator startedCoordinator() {
    ServerHostedRoundCoordinator coordinator = waitingCoordinator();
    coordinator.join(UUID.randomUUID());
    coordinator.join(UUID.randomUUID());
    assertEquals(ServerHostedRoundCoordinator.StartCode.STARTING, coordinator.start().code());
    return coordinator;
  }
}
