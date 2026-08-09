package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ServerHostedSharedRoundRuntimeTest {

  @Test
  void beginRequiresRunningServerHostedOwnership() {
    Fixture fixture = fixtureAtCountdown();

    assertThrows(
        IllegalStateException.class,
        () -> ServerHostedSharedRoundRuntime.begin(
            fixture.coordinator(),
            fixture.context(),
            Duration.ofSeconds(60),
            fixture.clock()::get
        )
    );
  }

  @Test
  void beginRejectsLegacyContext() {
    Fixture fixture = runningFixture();

    assertThrows(
        IllegalArgumentException.class,
        () -> ServerHostedSharedRoundRuntime.begin(
            fixture.coordinator(),
            RoundRuntimeContext.legacy(fixture.first()),
            Duration.ofSeconds(60),
            fixture.clock()::get
        )
    );
  }

  @Test
  void beginRejectsParticipantSnapshotMismatch() {
    Fixture fixture = runningFixture();
    RoundRuntimeContext mismatched = RoundRuntimeContext.serverHosted(
        List.of(fixture.first(), UUID.randomUUID())
    );

    assertThrows(
        IllegalArgumentException.class,
        () -> ServerHostedSharedRoundRuntime.begin(
            fixture.coordinator(),
            mismatched,
            Duration.ofSeconds(60),
            fixture.clock()::get
        )
    );
  }

  @Test
  void beginRequiresPositiveDuration() {
    Fixture fixture = runningFixture();

    assertThrows(
        IllegalArgumentException.class,
        () -> ServerHostedSharedRoundRuntime.begin(
            fixture.coordinator(),
            fixture.context(),
            Duration.ZERO,
            fixture.clock()::get
        )
    );
  }

  @Test
  void participantSnapshotIsImmutableAndOrdered() {
    Fixture fixture = runningFixture();
    ServerHostedSharedRoundRuntime runtime = runtime(fixture);

    assertEquals(List.of(fixture.first(), fixture.second()), runtime.participants());
    assertThrows(UnsupportedOperationException.class, () -> runtime.participants().clear());
  }

  @Test
  void allParticipantsStartWithIndependentZeroScores() {
    Fixture fixture = runningFixture();
    ServerHostedSharedRoundRuntime runtime = runtime(fixture);

    assertEquals(0, runtime.score(fixture.first()));
    assertEquals(0, runtime.score(fixture.second()));
  }

  @Test
  void signedScoreUpdatesAreParticipantScoped() {
    Fixture fixture = runningFixture();
    ServerHostedSharedRoundRuntime runtime = runtime(fixture);

    assertEquals(
        ServerHostedSharedRoundRuntime.ScoreCode.RECORDED,
        runtime.addScore(fixture.first(), 7)
    );
    assertEquals(
        ServerHostedSharedRoundRuntime.ScoreCode.RECORDED,
        runtime.addScore(fixture.second(), -2)
    );

    assertEquals(7, runtime.score(fixture.first()));
    assertEquals(-2, runtime.score(fixture.second()));
  }

  @Test
  void nonParticipantScoreMutationIsRejected() {
    Fixture fixture = runningFixture();
    ServerHostedSharedRoundRuntime runtime = runtime(fixture);

    assertEquals(
        ServerHostedSharedRoundRuntime.ScoreCode.NOT_PARTICIPANT,
        runtime.addScore(UUID.randomUUID(), 1)
    );
  }

  @Test
  void scoreOverflowIsRejectedWithoutChangingStoredValue() {
    Fixture fixture = runningFixture();
    ServerHostedSharedRoundRuntime runtime = runtime(fixture);

    assertEquals(
        ServerHostedSharedRoundRuntime.ScoreCode.RECORDED,
        runtime.addScore(fixture.first(), Integer.MAX_VALUE)
    );
    assertEquals(
        ServerHostedSharedRoundRuntime.ScoreCode.SCORE_OVERFLOW,
        runtime.addScore(fixture.first(), 1)
    );
    assertEquals(Integer.MAX_VALUE, runtime.score(fixture.first()));
  }

  @Test
  void scoreMutationStopsWhenCoordinatorLeavesRunning() {
    Fixture fixture = runningFixture();
    ServerHostedSharedRoundRuntime runtime = runtime(fixture);
    fixture.coordinator().requestReset(ServerHostedRoundCoordinator.ResetCause.STOP_REQUESTED);

    assertEquals(
        ServerHostedSharedRoundRuntime.ScoreCode.ROUND_NOT_RUNNING,
        runtime.addScore(fixture.first(), 1)
    );
    assertEquals(0, runtime.score(fixture.first()));
  }

  @Test
  void oneSharedMonotonicClockDrivesElapsedAndRemainingTime() {
    Fixture fixture = runningFixture();
    ServerHostedSharedRoundRuntime runtime = runtime(fixture, Duration.ofSeconds(10));

    fixture.clock().set(Duration.ofSeconds(3).toNanos());

    assertEquals(3000L, runtime.elapsedMillis());
    assertEquals(7000L, runtime.remainingMillis());
    assertFalse(runtime.timeExpired());
  }

  @Test
  void backwardsClockObservationClampsElapsedToZero() {
    Fixture fixture = runningFixture();
    fixture.clock().set(100L);
    ServerHostedSharedRoundRuntime runtime = runtime(fixture, Duration.ofSeconds(10));
    fixture.clock().set(50L);

    assertEquals(0L, runtime.elapsedMillis());
    assertEquals(10000L, runtime.remainingMillis());
    assertFalse(runtime.timeExpired());
  }

  @Test
  void timeExpiresOnceSharedDurationIsReached() {
    Fixture fixture = runningFixture();
    ServerHostedSharedRoundRuntime runtime = runtime(fixture, Duration.ofSeconds(10));

    fixture.clock().set(Duration.ofSeconds(10).toNanos());

    assertEquals(10000L, runtime.elapsedMillis());
    assertEquals(0L, runtime.remainingMillis());
    assertTrue(runtime.timeExpired());
  }

  @Test
  void resultSnapshotPreservesParticipantOrderAndIsImmutable() {
    Fixture fixture = runningFixture();
    ServerHostedSharedRoundRuntime runtime = runtime(fixture);
    runtime.addScore(fixture.first(), 4);
    runtime.addScore(fixture.second(), 9);

    ServerHostedSharedRoundRuntime.Snapshot snapshot = runtime.snapshot();

    assertEquals(fixture.context().roundId(), snapshot.roundId());
    assertEquals(List.of(fixture.first(), fixture.second()), snapshot.participants());
    assertEquals(
        List.of(
            new ServerHostedSharedRoundRuntime.ParticipantResult(fixture.first(), 4),
            new ServerHostedSharedRoundRuntime.ParticipantResult(fixture.second(), 9)
        ),
        snapshot.results()
    );
    assertThrows(UnsupportedOperationException.class, () -> snapshot.results().clear());
  }

  @Test
  void resultSnapshotRemainsReadableDuringResettingWithoutOwningLifecycle() {
    Fixture fixture = runningFixture();
    ServerHostedSharedRoundRuntime runtime = runtime(fixture);
    runtime.addScore(fixture.first(), 3);
    fixture.coordinator().requestReset(ServerHostedRoundCoordinator.ResetCause.ROUND_COMPLETED);

    ServerHostedSharedRoundRuntime.Snapshot snapshot = runtime.snapshot();

    assertEquals(ServerHostedRoundState.RESETTING, fixture.coordinator().state());
    assertEquals(3, snapshot.results().get(0).score());
    assertEquals(0, snapshot.results().get(1).score());
  }

  private static ServerHostedSharedRoundRuntime runtime(Fixture fixture) {
    return runtime(fixture, Duration.ofSeconds(60));
  }

  private static ServerHostedSharedRoundRuntime runtime(Fixture fixture, Duration duration) {
    return ServerHostedSharedRoundRuntime.begin(
        fixture.coordinator(),
        fixture.context(),
        duration,
        fixture.clock()::get
    );
  }

  private static Fixture runningFixture() {
    Fixture fixture = fixtureAtCountdown();
    assertEquals(
        ServerHostedRoundCoordinator.TransitionCode.TRANSITIONED,
        fixture.coordinator().beginRunning()
    );
    return fixture;
  }

  private static Fixture fixtureAtCountdown() {
    ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    assertEquals(ServerHostedRoundCoordinator.CreateCode.CREATED, coordinator.create());
    assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(first));
    assertEquals(ServerHostedRoundCoordinator.JoinCode.JOINED, coordinator.join(second));

    ServerHostedRoundCoordinator.StartDecision start = coordinator.start();
    assertEquals(ServerHostedRoundCoordinator.StartCode.STARTING, start.code());

    RoundRuntimeContext context = RoundRuntimeContext.serverHosted(start.participants());
    assertEquals(
        ServerHostedRoundCoordinator.TransitionCode.TRANSITIONED,
        coordinator.beginCountdown()
    );

    return new Fixture(coordinator, context, first, second, new AtomicLong());
  }

  private record Fixture(
      ServerHostedRoundCoordinator coordinator,
      RoundRuntimeContext context,
      UUID first,
      UUID second,
      AtomicLong clock
  ) { }
}
