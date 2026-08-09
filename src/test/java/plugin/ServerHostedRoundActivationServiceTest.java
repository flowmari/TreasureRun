package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerHostedRoundActivationServiceTest {

  @TempDir Path tempDir;

  @Test
  void successfulPreparationStopsAtCountdownWithoutStartingRuntime() {
    Fixture fixture = fixture();

    ServerHostedRoundActivationService.Result result =
        activation(fixture).prepareLockedRound();

    assertEquals(ServerHostedRoundActivationService.Code.PREPARED_FOR_COUNTDOWN, result.code());
    assertEquals(ServerHostedRoundState.COUNTDOWN, fixture.coordinator().state());
    assertEquals(List.of(fixture.first(), fixture.second()), result.participants());
    assertTrue(result.runtime().isEmpty());
    assertEquals(
        List.of(
            "resolve:" + fixture.first(),
            "resolve:" + fixture.second(),
            "prepare",
            "chests",
            "teleport:" + fixture.first(),
            "teleport:" + fixture.second(),
            "activate"
        ),
        fixture.events()
    );
  }

  @Test
  void countdownCompletionEntersRunningAndReturnsSharedRuntimeForLockedRoster() {
    Fixture fixture = fixture();
    ServerHostedRoundActivationService<String> activation = activation(fixture);
    assertEquals(
        ServerHostedRoundActivationService.Code.PREPARED_FOR_COUNTDOWN,
        activation.prepareLockedRound().code()
    );

    ServerHostedRoundActivationService.Result result = activation.beginRunningAfterCountdown();

    assertEquals(ServerHostedRoundActivationService.Code.RUNNING, result.code());
    assertEquals(ServerHostedRoundState.RUNNING, fixture.coordinator().state());
    assertEquals(List.of(fixture.first(), fixture.second()), result.participants());
    ServerHostedSharedRoundRuntime runtime = result.runtime().orElseThrow();
    assertEquals(result.participants(), runtime.participants());
    assertEquals(0, runtime.score(fixture.first()));
    assertEquals(0, runtime.score(fixture.second()));
  }

  @Test
  void runningActivationCannotBeRepeated() {
    Fixture fixture = fixture();
    ServerHostedRoundActivationService<String> activation = activation(fixture);
    activation.prepareLockedRound();
    assertEquals(
        ServerHostedRoundActivationService.Code.RUNNING,
        activation.beginRunningAfterCountdown().code()
    );

    ServerHostedRoundActivationService.Result repeated = activation.beginRunningAfterCountdown();

    assertEquals(ServerHostedRoundActivationService.Code.INVALID_STATE, repeated.code());
    assertEquals(ServerHostedRoundState.RUNNING, fixture.coordinator().state());
    assertTrue(repeated.runtime().isEmpty());
  }

  @Test
  void preparationBeforeRosterLockIsRejectedWithoutRuntimeSideEffects() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    assertEquals(ServerHostedRoundCoordinator.CreateCode.CREATED, coordinator.create());
    assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(first));
    assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(second));
    List<String> events = new ArrayList<>();
    Fixture fixture = new Fixture(coordinator, first, second, UUID.randomUUID(), events);

    ServerHostedRoundActivationService.Result result = activation(fixture).prepareLockedRound();

    assertEquals(ServerHostedRoundActivationService.Code.INVALID_STATE, result.code());
    assertEquals(ServerHostedRoundState.WAITING, coordinator.state());
    assertTrue(result.runtime().isEmpty());
    assertTrue(events.isEmpty());
  }

  @Test
  void preparationFailureUsesExistingCleanupAndReturnsCoordinatorToIdle() {
    Fixture fixture = fixture();
    FakeRuntime runtime = new FakeRuntime(fixture.events());
    runtime.chestsPlaced = false;

    ServerHostedRoundActivationService.Result result =
        activation(fixture, runtime).prepareLockedRound();

    assertEquals(ServerHostedRoundActivationService.Code.PREPARATION_FAILED, result.code());
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator().state());
    assertTrue(fixture.events().contains("cleanup:PREPARATION_FAILED"));
    assertTrue(result.runtime().isEmpty());
  }

  @Test
  void sharedRuntimeFactoryFailureTriggersFailClosedCleanup() {
    Fixture fixture = fixture();
    ServerHostedRoundActivationService<String> activation = activation(
        fixture,
        new FakeRuntime(fixture.events()),
        (coordinator, context, duration) -> {
          throw new IllegalStateException("simulated shared runtime factory failure");
        }
    );
    assertEquals(
        ServerHostedRoundActivationService.Code.PREPARED_FOR_COUNTDOWN,
        activation.prepareLockedRound().code()
    );

    ServerHostedRoundActivationService.Result result = activation.beginRunningAfterCountdown();

    assertEquals(ServerHostedRoundActivationService.Code.PREPARATION_FAILED, result.code());
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator().state());
    assertTrue(fixture.events().contains("cleanup:PREPARATION_FAILED"));
    assertTrue(result.runtime().isEmpty());
  }

  @Test
  void nonPositiveRoundDurationIsRejectedBeforeAnyStateChange() {
    Fixture fixture = fixture();

    assertThrows(
        IllegalArgumentException.class,
        () -> activation(fixture, new FakeRuntime(fixture.events()), Duration.ZERO)
    );
    assertEquals(ServerHostedRoundState.STARTING, fixture.coordinator().state());
    assertTrue(fixture.events().isEmpty());
  }

  private ServerHostedRoundActivationService<String> activation(Fixture fixture) {
    return activation(fixture, new FakeRuntime(fixture.events()));
  }

  private ServerHostedRoundActivationService<String> activation(
      Fixture fixture,
      FakeRuntime runtime
  ) {
    return activation(
        fixture,
        runtime,
        (coordinator, context, duration) ->
            ServerHostedSharedRoundRuntime.begin(coordinator, context, duration)
    );
  }

  private ServerHostedRoundActivationService<String> activation(
      Fixture fixture,
      FakeRuntime runtime,
      ServerHostedRoundActivationService.SharedRuntimeFactory factory
  ) {
    return new ServerHostedRoundActivationService<>(
        fixture.coordinator(),
        orchestrator(fixture),
        runtime,
        Duration.ofMinutes(5),
        factory
    );
  }

  private ServerHostedRoundActivationService<String> activation(
      Fixture fixture,
      FakeRuntime runtime,
      Duration duration
  ) {
    return new ServerHostedRoundActivationService<>(
        fixture.coordinator(),
        orchestrator(fixture),
        runtime,
        duration
    );
  }

  private ServerHostedBukkitRoundOrchestrator<String> orchestrator(Fixture fixture) {
    PlayerReturnLedger ledger = new PlayerReturnLedger(
        tempDir.resolve(UUID.randomUUID() + ".ledger")
    );
    assertTrue(ledger.open().available());

    return new ServerHostedBukkitRoundOrchestrator<>(
        fixture.coordinator(),
        new ServerHostedRoundPreparationService<>(fixture.coordinator(), ledger),
        playerId -> {
          fixture.events().add("resolve:" + playerId);
          return Optional.of(fixture.record(playerId));
        },
        claim -> {
          fixture.events().add("cleanup:" + claim.cause());
          return true;
        }
    );
  }

  private static Fixture fixture() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    UUID world = UUID.randomUUID();
    List<String> events = new ArrayList<>();
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    assertEquals(ServerHostedRoundCoordinator.CreateCode.CREATED, coordinator.create());
    assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(first));
    assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(second));
    assertEquals(ServerHostedRoundCoordinator.StartCode.STARTING, coordinator.start().code());
    return new Fixture(coordinator, first, second, world, events);
  }

  private record Fixture(
      ServerHostedRoundCoordinator coordinator,
      UUID first,
      UUID second,
      UUID world,
      List<String> events
  ) {
    PlayerReturnRecord record(UUID playerId) {
      double x = playerId.equals(first) ? 1.0 : 2.0;
      return new PlayerReturnRecord(
          PlayerReturnRecord.CURRENT_SCHEMA_VERSION,
          UUID.randomUUID(),
          playerId,
          world,
          "world",
          x,
          70.0,
          x,
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
      return true;
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
