package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerHostedBukkitRoundOrchestratorTest {

  @TempDir Path tempDir;

  @Test
  void resolvesEveryReturnDestinationBeforeDurableBatchAndAnyRuntimeSideEffect() {
    Fixture fixture = fixture();
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledger("ordering", events);
    FakeRuntime runtime = new FakeRuntime(events);

    ServerHostedBukkitRoundOrchestrator<String> orchestrator = orchestrator(
        fixture,
        ledger,
        playerId -> {
          events.add("resolve:" + playerId);
          return Optional.of(fixture.recordFor(playerId));
        },
        claim -> {
          events.add("cleanup:" + claim.cause());
          return true;
        }
    );

    ServerHostedBukkitRoundOrchestrator.Result result = orchestrator.prepareLockedRound(runtime);

    assertEquals(ServerHostedBukkitRoundOrchestrator.Code.PREPARED_FOR_COUNTDOWN, result.code());
    assertEquals(ServerHostedRoundState.COUNTDOWN, fixture.coordinator().state());
    assertEquals(
        List.of(
            "resolve:" + fixture.first(),
            "resolve:" + fixture.second(),
            "ledger",
            "prepare",
            "chests",
            "teleport:" + fixture.first(),
            "teleport:" + fixture.second(),
            "activate"
        ),
        events
    );
    assertTrue(ledger.pendingRecord(fixture.first()).isPresent());
    assertTrue(ledger.pendingRecord(fixture.second()).isPresent());
  }

  @Test
  void unresolvedParticipantAbortsBeforeLedgerOrRuntimeAndPreservesLockedSnapshotForCleanup() {
    Fixture fixture = fixture();
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledger("resolve-failure", events);
    List<UUID> cleanupParticipants = new ArrayList<>();

    ServerHostedBukkitRoundOrchestrator<String> orchestrator = orchestrator(
        fixture,
        ledger,
        playerId -> {
          events.add("resolve:" + playerId);
          return playerId.equals(fixture.first())
              ? Optional.of(fixture.recordFor(playerId))
              : Optional.empty();
        },
        claim -> {
          events.add("cleanup:" + claim.cause());
          cleanupParticipants.addAll(claim.participants());
          return true;
        }
    );

    ServerHostedBukkitRoundOrchestrator.Result result = orchestrator.prepareLockedRound(
        new FakeRuntime(events));

    assertEquals(
        ServerHostedBukkitRoundOrchestrator.Code.RETURN_DESTINATION_UNAVAILABLE,
        result.code()
    );
    assertEquals(List.of(fixture.first(), fixture.second()), cleanupParticipants);
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator().state());
    assertFalse(events.contains("ledger"));
    assertFalse(events.contains("prepare"));
    assertTrue(ledger.pendingRecords().isEmpty());
  }

  @Test
  void preparationFailureConvergesThroughPreparationFailedCleanup() {
    Fixture fixture = fixture();
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledger("prepare-failure", events);
    FakeRuntime runtime = new FakeRuntime(events);
    runtime.chestsPlaced = false;

    ServerHostedBukkitRoundOrchestrator<String> orchestrator = orchestrator(
        fixture,
        ledger,
        playerId -> Optional.of(fixture.recordFor(playerId)),
        claim -> {
          events.add("cleanup:" + claim.cause());
          return true;
        }
    );

    ServerHostedBukkitRoundOrchestrator.Result result = orchestrator.prepareLockedRound(runtime);

    assertEquals(ServerHostedBukkitRoundOrchestrator.Code.PREPARATION_FAILED, result.code());
    assertEquals(
        Optional.of(ServerHostedRoundPreparationService.Code.CHEST_PLACEMENT_FAILED),
        result.preparationCode()
    );
    assertTrue(events.contains("rollback"));
    assertTrue(events.contains("cleanup:PREPARATION_FAILED"));
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator().state());
    assertTrue(ledger.pendingRecords().isEmpty());
  }

  @Test
  void participantDisconnectAfterRosterLockAbortsWholeRoundWithImmutableSnapshot() {
    Fixture fixture = fixture();
    PlayerReturnLedger ledger = ledger("disconnect", new ArrayList<>());
    List<ServerHostedRoundCoordinator.CleanupClaim> claims = new ArrayList<>();

    ServerHostedBukkitRoundOrchestrator<String> orchestrator = orchestrator(
        fixture,
        ledger,
        playerId -> Optional.of(fixture.recordFor(playerId)),
        claim -> {
          claims.add(claim);
          return true;
        }
    );

    ServerHostedBukkitRoundOrchestrator.Result result = orchestrator.participantDisconnected(
        fixture.first());

    assertEquals(ServerHostedBukkitRoundOrchestrator.Code.CLEANUP_COMPLETED, result.code());
    assertEquals(1, claims.size());
    assertEquals(
        ServerHostedRoundCoordinator.ResetCause.PARTICIPANT_DISCONNECTED,
        claims.get(0).cause()
    );
    assertEquals(List.of(fixture.first(), fixture.second()), claims.get(0).participants());
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator().state());
  }

  @Test
  void waitingDisconnectRemainsRosterLeaveConcernAndDoesNotClaimRuntimeCleanup() {
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    assertEquals(ServerHostedRoundCoordinator.CreateCode.CREATED, coordinator.create());
    UUID player = UUID.randomUUID();
    assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(player));
    PlayerReturnLedger ledger = ledger("waiting-disconnect", new ArrayList<>());
    AtomicInteger cleanups = new AtomicInteger();

    ServerHostedBukkitRoundOrchestrator<String> orchestrator = new ServerHostedBukkitRoundOrchestrator<>(
        coordinator,
        new ServerHostedRoundPreparationService<>(coordinator, ledger),
        ignored -> Optional.empty(),
        claim -> {
          cleanups.incrementAndGet();
          return true;
        }
    );

    ServerHostedBukkitRoundOrchestrator.Result result = orchestrator.participantDisconnected(player);

    assertEquals(ServerHostedBukkitRoundOrchestrator.Code.INVALID_STATE, result.code());
    assertEquals(0, cleanups.get());
    assertEquals(ServerHostedRoundState.WAITING, coordinator.state());
    assertEquals(List.of(player), coordinator.participants());
  }

  @Test
  void competingAbortRequestsKeepFirstCauseAndReuseOneRetainedCleanupClaim() {
    Fixture fixture = fixture();
    PlayerReturnLedger ledger = ledger("competing-aborts", new ArrayList<>());
    List<ServerHostedRoundCoordinator.ResetCause> causes = new ArrayList<>();
    AtomicInteger attempts = new AtomicInteger();

    ServerHostedBukkitRoundOrchestrator<String> orchestrator = orchestrator(
        fixture,
        ledger,
        playerId -> Optional.of(fixture.recordFor(playerId)),
        claim -> {
          causes.add(claim.cause());
          return attempts.incrementAndGet() >= 2;
        }
    );

    ServerHostedBukkitRoundOrchestrator.Result first = orchestrator.stopRequested();
    ServerHostedBukkitRoundOrchestrator.Result second = orchestrator.participantDisconnected(
        fixture.second());

    assertEquals(ServerHostedBukkitRoundOrchestrator.Code.CLEANUP_PENDING, first.code());
    assertEquals(ServerHostedBukkitRoundOrchestrator.Code.CLEANUP_COMPLETED, second.code());
    assertEquals(
        List.of(
            ServerHostedRoundCoordinator.ResetCause.STOP_REQUESTED,
            ServerHostedRoundCoordinator.ResetCause.STOP_REQUESTED
        ),
        causes
    );
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator().state());
  }

  @Test
  void failedCleanupStaysResettingAndCanBeRetriedWithoutASecondClaim() {
    Fixture fixture = fixture();
    PlayerReturnLedger ledger = ledger("cleanup-retry", new ArrayList<>());
    AtomicBoolean allowCleanup = new AtomicBoolean(false);
    AtomicInteger attempts = new AtomicInteger();

    ServerHostedBukkitRoundOrchestrator<String> orchestrator = orchestrator(
        fixture,
        ledger,
        playerId -> Optional.of(fixture.recordFor(playerId)),
        claim -> {
          attempts.incrementAndGet();
          return allowCleanup.get();
        }
    );

    ServerHostedBukkitRoundOrchestrator.Result first = orchestrator.pluginDisabled();
    assertEquals(ServerHostedBukkitRoundOrchestrator.Code.CLEANUP_PENDING, first.code());
    assertEquals(ServerHostedRoundState.RESETTING, fixture.coordinator().state());
    assertTrue(fixture.coordinator().cleanupClaimed());

    allowCleanup.set(true);
    ServerHostedBukkitRoundOrchestrator.Result retry = orchestrator.retryCleanup();

    assertEquals(ServerHostedBukkitRoundOrchestrator.Code.CLEANUP_COMPLETED, retry.code());
    assertEquals(2, attempts.get());
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator().state());
  }

  @Test
  void partialTeleportFailureIsRolledBackBeforeLifecycleResetCompletes() {
    Fixture fixture = fixture();
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledger("partial-teleport", events);
    FakeRuntime runtime = new FakeRuntime(events);
    runtime.failTeleportFor = fixture.second();

    ServerHostedBukkitRoundOrchestrator<String> orchestrator = orchestrator(
        fixture,
        ledger,
        playerId -> Optional.of(fixture.recordFor(playerId)),
        claim -> {
          events.add("cleanup:" + claim.cause());
          return true;
        }
    );

    ServerHostedBukkitRoundOrchestrator.Result result = orchestrator.prepareLockedRound(runtime);

    assertEquals(ServerHostedBukkitRoundOrchestrator.Code.PREPARATION_FAILED, result.code());
    assertEquals(
        Optional.of(ServerHostedRoundPreparationService.Code.TELEPORT_FAILED),
        result.preparationCode()
    );
    assertTrue(events.contains("restore:" + fixture.first()));
    assertTrue(events.contains("rollback"));
    assertTrue(events.contains("cleanup:PREPARATION_FAILED"));
    assertTrue(ledger.pendingRecords().isEmpty());
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator().state());
  }

  @Test
  void prepareIsRejectedOutsideStartingWithoutResolvingOrTouchingRuntime() {
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    assertEquals(ServerHostedRoundCoordinator.CreateCode.CREATED, coordinator.create());
    UUID player = UUID.randomUUID();
    coordinator.join(player);
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledger("invalid-state", events);

    ServerHostedBukkitRoundOrchestrator<String> orchestrator = new ServerHostedBukkitRoundOrchestrator<>(
        coordinator,
        new ServerHostedRoundPreparationService<>(coordinator, ledger),
        ignored -> {
          events.add("resolve");
          return Optional.empty();
        },
        claim -> {
          events.add("cleanup");
          return true;
        }
    );

    ServerHostedBukkitRoundOrchestrator.Result result = orchestrator.prepareLockedRound(
        new FakeRuntime(events));

    assertEquals(ServerHostedBukkitRoundOrchestrator.Code.INVALID_STATE, result.code());
    assertTrue(events.isEmpty());
    assertEquals(ServerHostedRoundState.WAITING, coordinator.state());
  }

  private ServerHostedBukkitRoundOrchestrator<String> orchestrator(
      Fixture fixture,
      PlayerReturnLedger ledger,
      ServerHostedBukkitRoundOrchestrator.ReturnDestinationResolver resolver,
      ServerHostedBukkitRoundOrchestrator.CleanupPort cleanupPort
  ) {
    return new ServerHostedBukkitRoundOrchestrator<>(
        fixture.coordinator(),
        new ServerHostedRoundPreparationService<>(fixture.coordinator(), ledger),
        resolver,
        cleanupPort
    );
  }

  private PlayerReturnLedger ledger(String name, List<String> events) {
    Path path = tempDir.resolve(name + ".ledger");
    PlayerReturnLedger ledger = new PlayerReturnLedger(
        path,
        Clock.fixed(Instant.parse("2026-08-09T00:00:00Z"), ZoneOffset.UTC),
        (target, bytes) -> {
          events.add("ledger");
          Files.write(target, bytes);
        }
    );
    assertTrue(ledger.open().available());
    return ledger;
  }

  private static Fixture fixture() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    UUID world = UUID.randomUUID();
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    assertEquals(ServerHostedRoundCoordinator.CreateCode.CREATED, coordinator.create());
    assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(first));
    assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(second));
    assertEquals(ServerHostedRoundCoordinator.StartCode.STARTING, coordinator.start().code());
    return new Fixture(coordinator, first, second, world);
  }

  private record Fixture(
      ServerHostedRoundCoordinator coordinator,
      UUID first,
      UUID second,
      UUID world
  ) {
    PlayerReturnRecord recordFor(UUID playerId) {
      double offset = playerId.equals(first) ? 1.0 : 2.0;
      return new PlayerReturnRecord(
          PlayerReturnRecord.CURRENT_SCHEMA_VERSION,
          UUID.randomUUID(),
          playerId,
          world,
          "world",
          offset,
          70.0,
          offset,
          10.0f,
          20.0f,
          Instant.parse("2026-08-09T00:00:00Z")
      );
    }
  }

  private static final class FakeRuntime
      implements ServerHostedRoundPreparationService.RuntimePort<String> {
    private final List<String> events;
    private boolean chestsPlaced = true;
    private UUID failTeleportFor;

    FakeRuntime(List<String> events) {
      this.events = events;
    }

    @Override
    public String prepareArena(UUID effectsAudienceId) {
      events.add("prepare");
      return "arena";
    }

    @Override
    public boolean placeChests(String arena) {
      events.add("chests");
      return chestsPlaced;
    }

    @Override
    public boolean teleport(UUID playerId, String arena) {
      events.add("teleport:" + playerId);
      return !playerId.equals(failTeleportFor);
    }

    @Override
    public boolean restore(UUID playerId, PlayerReturnRecord returnRecord) {
      events.add("restore:" + playerId);
      return true;
    }

    @Override
    public void activate(String arena) {
      events.add("activate");
    }

    @Override
    public void rollbackArena(String arena) {
      events.add("rollback");
    }
  }
}
