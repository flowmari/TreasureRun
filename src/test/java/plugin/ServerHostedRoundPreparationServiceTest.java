package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerHostedRoundPreparationServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void durableBatchPrecedesEveryRuntimeSideEffectAndSuccessKeepsAllReturnsPending() {
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledgerWithWriter("success", events, false);
    Fixture fixture = fixture();

    ServerHostedRoundPreparationService<String> service =
        new ServerHostedRoundPreparationService<>(fixture.coordinator(), ledger);
    FakeRuntime runtime = new FakeRuntime(events);

    ServerHostedRoundPreparationService.Result result = service.prepare(
        fixture.context(),
        fixture.records(),
        runtime
    );

    assertEquals(ServerHostedRoundPreparationService.Code.PREPARED, result.code());
    assertTrue(result.prepared());
    assertTrue(result.rollbackComplete());
    assertEquals(fixture.context().participants(), result.durablePendingParticipants());
    assertEquals(
        List.of(
            "ledger",
            "prepare:" + fixture.first(),
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
  void coordinatorMustBeInAuthoritativeServerHostedStartingState() {
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledgerWithWriter("invalid-state", events, false);
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    UUID world = UUID.randomUUID();
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    assertEquals(ServerHostedRoundCoordinator.CreateCode.CREATED, coordinator.create());
    assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(first));
    assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(second));
    RoundRuntimeContext context = RoundRuntimeContext.serverHosted(List.of(first, second));
    List<PlayerReturnRecord> records = List.of(
        record(first, world, "world", 1.0),
        record(second, world, "world", 4.0)
    );

    ServerHostedRoundPreparationService.Result result =
        new ServerHostedRoundPreparationService<String>(coordinator, ledger).prepare(
            context, records, new FakeRuntime(events)
        );

    assertEquals(ServerHostedRoundPreparationService.Code.INVALID_STATE, result.code());
    assertEquals(List.of(), events);
    assertTrue(ledger.pendingRecords().isEmpty());
  }

  @Test
  void invalidContextIsRejectedBeforeLedgerOrRuntimeSideEffects() {
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledgerWithWriter("invalid-context", events, false);
    Fixture fixture = fixture();
    RoundRuntimeContext legacy = RoundRuntimeContext.legacy(fixture.first());

    ServerHostedRoundPreparationService.Result result =
        new ServerHostedRoundPreparationService<String>(fixture.coordinator(), ledger).prepare(
            legacy,
            List.of(fixture.records().get(0)),
            new FakeRuntime(events)
        );

    assertEquals(ServerHostedRoundPreparationService.Code.INVALID_CONTEXT, result.code());
    assertEquals(List.of(), events);
    assertTrue(ledger.pendingRecords().isEmpty());
  }

  @Test
  void existingPendingReturnBlocksASecondPreparationWithoutTouchingRuntime() {
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledgerWithWriter("existing-pending", events, false);
    Fixture fixture = fixture();
    assertTrue(ledger.putPending(fixture.records().get(0)).accepted());
    events.clear();

    ServerHostedRoundPreparationService.Result result =
        new ServerHostedRoundPreparationService<String>(fixture.coordinator(), ledger).prepare(
            fixture.context(),
            fixture.records(),
            new FakeRuntime(events)
        );

    assertEquals(ServerHostedRoundPreparationService.Code.PENDING_RETURN_EXISTS, result.code());
    assertEquals(List.of(), events);
    assertEquals(fixture.records().get(0), ledger.pendingRecord(fixture.first()).orElseThrow());
    assertTrue(ledger.pendingRecord(fixture.second()).isEmpty());
  }

  @Test
  void failedDurableBatchPreventsEveryRuntimeSideEffect() {
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledgerWithWriter("batch-failure", events, true);
    Fixture fixture = fixture();

    ServerHostedRoundPreparationService.Result result =
        new ServerHostedRoundPreparationService<String>(fixture.coordinator(), ledger).prepare(
            fixture.context(),
            fixture.records(),
            new FakeRuntime(events)
        );

    assertEquals(ServerHostedRoundPreparationService.Code.RETURN_BATCH_REJECTED, result.code());
    assertEquals(List.of("ledger"), events);
    assertTrue(ledger.pendingRecords().isEmpty());
  }

  @Test
  void missingArenaClearsNewObligationsWithoutTeleportingAnyone() {
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledgerWithWriter("missing-arena", events, false);
    Fixture fixture = fixture();
    FakeRuntime runtime = new FakeRuntime(events);
    runtime.arena = null;

    ServerHostedRoundPreparationService.Result result =
        new ServerHostedRoundPreparationService<String>(fixture.coordinator(), ledger).prepare(
            fixture.context(), fixture.records(), runtime
        );

    assertEquals(ServerHostedRoundPreparationService.Code.ARENA_PREPARATION_FAILED, result.code());
    assertTrue(result.rollbackComplete());
    assertEquals(List.of(), result.durablePendingParticipants());
    assertFalse(events.stream().anyMatch(value -> value.startsWith("teleport:")));
    assertTrue(ledger.pendingRecords().isEmpty());
  }

  @Test
  void chestFailureRollsBackArenaAndClearsUnmovedObligations() {
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledgerWithWriter("chest-failure", events, false);
    Fixture fixture = fixture();
    FakeRuntime runtime = new FakeRuntime(events);
    runtime.chestsPlaced = false;

    ServerHostedRoundPreparationService.Result result =
        new ServerHostedRoundPreparationService<String>(fixture.coordinator(), ledger).prepare(
            fixture.context(), fixture.records(), runtime
        );

    assertEquals(ServerHostedRoundPreparationService.Code.CHEST_PLACEMENT_FAILED, result.code());
    assertTrue(result.rollbackComplete());
    assertTrue(events.contains("rollback"));
    assertFalse(events.stream().anyMatch(value -> value.startsWith("teleport:")));
    assertTrue(ledger.pendingRecords().isEmpty());
  }

  @Test
  void secondTeleportFailureRestoresOnlyTheMovedParticipantAndClearsTheBatch() {
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledgerWithWriter("teleport-failure", events, false);
    Fixture fixture = fixture();
    FakeRuntime runtime = new FakeRuntime(events);
    runtime.failTeleportFor = fixture.second();

    ServerHostedRoundPreparationService.Result result =
        new ServerHostedRoundPreparationService<String>(fixture.coordinator(), ledger).prepare(
            fixture.context(), fixture.records(), runtime
        );

    assertEquals(ServerHostedRoundPreparationService.Code.TELEPORT_FAILED, result.code());
    assertTrue(result.rollbackComplete());
    assertTrue(events.contains("restore:" + fixture.first()));
    assertFalse(events.contains("restore:" + fixture.second()));
    assertTrue(ledger.pendingRecords().isEmpty());
  }

  @Test
  void failedRestoreLeavesOnlyThatMovedParticipantPendingForReplaySafeRecovery() {
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledgerWithWriter("restore-failure", events, false);
    Fixture fixture = fixture();
    FakeRuntime runtime = new FakeRuntime(events);
    runtime.failTeleportFor = fixture.second();
    runtime.failRestoreFor = fixture.first();

    ServerHostedRoundPreparationService.Result result =
        new ServerHostedRoundPreparationService<String>(fixture.coordinator(), ledger).prepare(
            fixture.context(), fixture.records(), runtime
        );

    assertEquals(ServerHostedRoundPreparationService.Code.TELEPORT_FAILED, result.code());
    assertFalse(result.rollbackComplete());
    assertEquals(List.of(fixture.first()), result.durablePendingParticipants());
    assertTrue(ledger.pendingRecord(fixture.first()).isPresent());
    assertTrue(ledger.pendingRecord(fixture.second()).isEmpty());
  }

  @Test
  void activationFailureRestoresEveryMovedParticipantAndClearsNewObligations() {
    List<String> events = new ArrayList<>();
    PlayerReturnLedger ledger = ledgerWithWriter("activation-failure", events, false);
    Fixture fixture = fixture();
    FakeRuntime runtime = new FakeRuntime(events);
    runtime.activationFails = true;

    ServerHostedRoundPreparationService.Result result =
        new ServerHostedRoundPreparationService<String>(fixture.coordinator(), ledger).prepare(
            fixture.context(), fixture.records(), runtime
        );

    assertEquals(ServerHostedRoundPreparationService.Code.ACTIVATION_FAILED, result.code());
    assertTrue(result.rollbackComplete());
    assertTrue(events.contains("restore:" + fixture.first()));
    assertTrue(events.contains("restore:" + fixture.second()));
    assertTrue(ledger.pendingRecords().isEmpty());
  }

  private PlayerReturnLedger ledgerWithWriter(
      String name,
      List<String> events,
      boolean failWrites
  ) {
    PlayerReturnLedger ledger = new PlayerReturnLedger(
        tempDir.resolve(name + ".ledger"),
        java.time.Clock.systemUTC(),
        (target, bytes) -> {
          events.add("ledger");
          if (failWrites) throw new IOException("simulated durable write failure");
          Files.write(target, bytes);
        }
    );
    assertTrue(ledger.open().available());
    return ledger;
  }

  private Fixture fixture() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    UUID world = UUID.randomUUID();
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    assertEquals(ServerHostedRoundCoordinator.CreateCode.CREATED, coordinator.create());
    assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(first));
    assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(second));
    ServerHostedRoundCoordinator.StartDecision start = coordinator.start();
    assertEquals(ServerHostedRoundCoordinator.StartCode.STARTING, start.code());
    return new Fixture(
        coordinator,
        RoundRuntimeContext.serverHosted(start.participants()),
        List.of(
            record(first, world, "world", 1.0),
            record(second, world, "world", 4.0)
        ),
        first,
        second
    );
  }

  private PlayerReturnRecord record(UUID player, UUID world, String worldName, double x) {
    return new PlayerReturnRecord(
        PlayerReturnRecord.CURRENT_SCHEMA_VERSION,
        UUID.randomUUID(),
        player,
        world,
        worldName,
        x,
        65.0,
        8.0,
        90.0f,
        0.0f,
        Instant.parse("2026-08-08T15:00:00Z")
    );
  }

  private record Fixture(
      ServerHostedRoundCoordinator coordinator,
      RoundRuntimeContext context,
      List<PlayerReturnRecord> records,
      UUID first,
      UUID second
  ) {}

  private static final class FakeRuntime
      implements ServerHostedRoundPreparationService.RuntimePort<String> {
    private final List<String> events;
    private String arena = "arena";
    private boolean chestsPlaced = true;
    private UUID failTeleportFor;
    private UUID failRestoreFor;
    private boolean activationFails;

    private FakeRuntime(List<String> events) {
      this.events = events;
    }

    @Override
    public String prepareArena(UUID effectsAudienceId) {
      events.add("prepare:" + effectsAudienceId);
      return arena;
    }

    @Override
    public boolean placeChests(String arenaHandle) {
      events.add("chests");
      return chestsPlaced;
    }

    @Override
    public boolean teleport(UUID playerId, String arenaHandle) {
      events.add("teleport:" + playerId);
      return !playerId.equals(failTeleportFor);
    }

    @Override
    public boolean restore(UUID playerId, PlayerReturnRecord returnRecord) {
      events.add("restore:" + playerId);
      return !playerId.equals(failRestoreFor);
    }

    @Override
    public void activate(String arenaHandle) throws Exception {
      events.add("activate");
      if (activationFails) throw new Exception("simulated activation failure");
    }

    @Override
    public void rollbackArena(String arenaHandle) {
      events.add("rollback");
    }
  }
}
