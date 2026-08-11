package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class ServerHostedBukkitRoundControllerTest {

  @TempDir Path tempDir;

  @Test
  void successfulStartOwnsOneTenSecondCountdownThenOneSharedRuntime() {
    Fixture fixture = fixture();

    ServerHostedBukkitRoundController.Result start =
        fixture.controller.start(fixture.lockedStart());

    assertEquals(ServerHostedBukkitRoundController.Code.COUNTDOWN_STARTED, start.code());
    assertEquals(ServerHostedRoundState.COUNTDOWN, fixture.coordinator.state());
    assertEquals(List.of(10), fixture.countdownSeconds);
    assertTrue(fixture.controller.countdownScheduled());
    assertEquals(10, fixture.controller.countdownRemaining());
    assertEquals(1, fixture.scheduler.tasks.size());

    fixture.scheduler.tick(9);

    assertEquals(ServerHostedRoundState.COUNTDOWN, fixture.coordinator.state());
    assertEquals(List.of(10, 9, 8, 7, 6, 5, 4, 3, 2, 1), fixture.countdownSeconds);
    assertTrue(fixture.controller.countdownScheduled());
    assertTrue(fixture.controller.activeRuntime().isEmpty());

    fixture.scheduler.tick(1);

    assertEquals(ServerHostedRoundState.RUNNING, fixture.coordinator.state());
    assertFalse(fixture.controller.countdownScheduled());
    assertEquals(0, fixture.controller.countdownRemaining());
    assertTrue(fixture.scheduler.tasks.get(0).cancelled);
    assertEquals(
        List.of(fixture.first, fixture.second),
        fixture.controller.activeRuntime().orElseThrow().participants()
    );
  }

  @Test
  void rejectedStartDecisionDoesNotPrepareOrSchedule() {
    Fixture fixture = waitingFixture();
    ServerHostedSessionControlService.StartDecision decision =
        fixture.control.requestStart(false);

    ServerHostedBukkitRoundController.Result result =
        fixture.controller.start(decision);

    assertEquals(ServerHostedBukkitRoundController.Code.IGNORED, result.code());
    assertEquals(ServerHostedRoundState.WAITING, fixture.coordinator.state());
    assertTrue(fixture.events.isEmpty());
    assertTrue(fixture.scheduler.tasks.isEmpty());
  }

  @Test
  void repeatedControllerStartDuringCountdownCreatesNoSecondTask() {
    Fixture fixture = fixture();
    ServerHostedSessionControlService.StartDecision decision = fixture.lockedStart();

    assertEquals(
        ServerHostedBukkitRoundController.Code.COUNTDOWN_STARTED,
        fixture.controller.start(decision).code()
    );

    ServerHostedBukkitRoundController.Result repeated =
        fixture.controller.start(decision);

    assertEquals(ServerHostedBukkitRoundController.Code.INVALID_STATE, repeated.code());
    assertEquals(1, fixture.scheduler.tasks.size());
    assertEquals(ServerHostedRoundState.COUNTDOWN, fixture.coordinator.state());
  }

  @Test
  void countdownSchedulingFailureFailsClosedThroughExistingCleanup() {
    Fixture fixture = fixture();
    fixture.scheduler.failure = new IllegalStateException("scheduler unavailable");

    ServerHostedBukkitRoundController.Result result =
        fixture.controller.start(fixture.lockedStart());

    assertEquals(ServerHostedBukkitRoundController.Code.CLEANUP_COMPLETED, result.code());
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator.state());
    assertFalse(fixture.controller.countdownScheduled());
    assertTrue(fixture.controller.activeRuntime().isEmpty());
    assertTrue(fixture.events.contains("cleanup:PREPARATION_FAILED"));
  }

  @Test
  void lockedStopDuringCountdownCancelsTaskBeforeCleanup() {
    Fixture fixture = fixture();
    fixture.controller.start(fixture.lockedStart());
    FakeScheduledTask task = fixture.scheduler.tasks.get(0);

    ServerHostedSessionControlService.StopDecision stop = fixture.control.requestStop(true);
    ServerHostedBukkitRoundController.Result result = fixture.controller.stop(stop);

    assertEquals(ServerHostedSessionControlService.StopCode.CLEANUP_REQUIRED, stop.code());
    assertEquals(ServerHostedBukkitRoundController.Code.CLEANUP_COMPLETED, result.code());
    assertTrue(task.cancelled);
    assertFalse(fixture.controller.countdownScheduled());
    assertTrue(fixture.controller.activeRuntime().isEmpty());
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator.state());
    assertTrue(fixture.events.contains("cleanup:STOP_REQUESTED"));
  }

  @Test
  void participantDisconnectDuringCountdownCancelsTaskAndAbortsRound() {
    Fixture fixture = fixture();
    fixture.controller.start(fixture.lockedStart());
    FakeScheduledTask task = fixture.scheduler.tasks.get(0);

    ServerHostedBukkitRoundController.Result result =
        fixture.controller.participantDisconnected(fixture.first);

    assertEquals(ServerHostedBukkitRoundController.Code.CLEANUP_COMPLETED, result.code());
    assertTrue(task.cancelled);
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator.state());
    assertTrue(fixture.events.contains("cleanup:PARTICIPANT_DISCONNECTED"));
  }

  @Test
  void unrelatedDisconnectDoesNotCancelCountdown() {
    Fixture fixture = fixture();
    fixture.controller.start(fixture.lockedStart());
    FakeScheduledTask task = fixture.scheduler.tasks.get(0);

    ServerHostedBukkitRoundController.Result result =
        fixture.controller.participantDisconnected(UUID.randomUUID());

    assertEquals(ServerHostedBukkitRoundController.Code.INVALID_STATE, result.code());
    assertFalse(task.cancelled);
    assertTrue(fixture.controller.countdownScheduled());
    assertEquals(ServerHostedRoundState.COUNTDOWN, fixture.coordinator.state());
  }

  @Test
  void pluginDisableDuringCountdownCancelsTaskAndUsesExistingCleanup() {
    Fixture fixture = fixture();
    fixture.controller.start(fixture.lockedStart());
    FakeScheduledTask task = fixture.scheduler.tasks.get(0);

    ServerHostedBukkitRoundController.Result result = fixture.controller.pluginDisabled();

    assertEquals(ServerHostedBukkitRoundController.Code.CLEANUP_COMPLETED, result.code());
    assertTrue(task.cancelled);
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator.state());
    assertTrue(fixture.events.contains("cleanup:PLUGIN_DISABLED"));
  }

  @Test
  void stopAfterRunningClearsActiveRuntimeAndCleansRound() {
    Fixture fixture = fixture();
    fixture.controller.start(fixture.lockedStart());
    fixture.scheduler.tick(10);
    assertTrue(fixture.controller.activeRuntime().isPresent());

    ServerHostedBukkitRoundController.Result result =
        fixture.controller.stop(fixture.control.requestStop(true));

    assertEquals(ServerHostedBukkitRoundController.Code.CLEANUP_COMPLETED, result.code());
    assertTrue(fixture.controller.activeRuntime().isEmpty());
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator.state());
  }

  @Test
  void cleanupPendingKeepsNoControllerTaskAndRetryUsesRetainedClaim() {
    Fixture fixture = fixture();
    fixture.cleanupSucceeds = false;
    fixture.controller.start(fixture.lockedStart());
    FakeScheduledTask task = fixture.scheduler.tasks.get(0);

    ServerHostedBukkitRoundController.Result pending =
        fixture.controller.stop(fixture.control.requestStop(true));

    assertEquals(ServerHostedBukkitRoundController.Code.CLEANUP_PENDING, pending.code());
    assertTrue(task.cancelled);
    assertFalse(fixture.controller.countdownScheduled());
    assertTrue(fixture.controller.activeRuntime().isEmpty());
    assertEquals(ServerHostedRoundState.RESETTING, fixture.coordinator.state());

    fixture.cleanupSucceeds = true;
    ServerHostedBukkitRoundController.Result completed = fixture.controller.retryCleanup();

    assertEquals(ServerHostedBukkitRoundController.Code.CLEANUP_COMPLETED, completed.code());
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator.state());
    assertEquals(2, fixture.events.stream().filter(value -> value.startsWith("cleanup:")).count());
  }


  @Test
  void countdownObserverFailureFailsClosedAndLeavesNoTask() {
    Fixture fixture = fixture();
    fixture.controller = fixture.controllerWithObserver(value -> {
      if (value == 9) throw new IllegalStateException("observer failure");
      fixture.countdownSeconds.add(value);
    });
    fixture.controller.start(fixture.lockedStart());
    FakeScheduledTask task = fixture.scheduler.tasks.get(0);

    fixture.scheduler.tick(1);

    assertTrue(task.cancelled);
    assertFalse(fixture.controller.countdownScheduled());
    assertTrue(fixture.controller.activeRuntime().isEmpty());
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator.state());
    assertTrue(fixture.events.contains("cleanup:PREPARATION_FAILED"));
  }

  @Test
  void sharedRuntimeFactoryFailureLeavesNoControllerRuntimeOrTask() {
    Fixture fixture = fixture(true);
    fixture.controller.start(fixture.lockedStart());

    fixture.scheduler.tick(10);

    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator.state());
    assertFalse(fixture.controller.countdownScheduled());
    assertTrue(fixture.controller.activeRuntime().isEmpty());
    assertTrue(fixture.events.contains("cleanup:PREPARATION_FAILED"));
  }

  @Test
  void runningObserverReceivesTheExactSharedRuntimeAndStopObserverRunsOnCompletion() {
    Fixture fixture = fixture();
    List<ServerHostedSharedRoundRuntime> running = new ArrayList<>();
    int[] stopped = {0};
    fixture.controller = fixture.controllerWithRuntimeObservers(
        fixture.countdownSeconds::add,
        running::add,
        () -> stopped[0]++
    );

    fixture.controller.start(fixture.lockedStart());
    fixture.scheduler.tick(10);

    assertEquals(1, running.size());
    assertTrue(fixture.controller.activeRuntime().isPresent());
    assertTrue(running.get(0) == fixture.controller.activeRuntime().orElseThrow());

    ServerHostedBukkitRoundController.Result completed = fixture.controller.roundCompleted();

    assertEquals(ServerHostedBukkitRoundController.Code.CLEANUP_COMPLETED, completed.code());
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator.state());
    assertTrue(fixture.controller.activeRuntime().isEmpty());
    assertEquals(1, stopped[0]);
    assertTrue(fixture.events.contains("cleanup:ROUND_COMPLETED"));
  }

  @Test
  void timeExpiryUsesTheSameCleanupOwnerAndClearsTheRuntime() {
    Fixture fixture = fixture();
    fixture.controller.start(fixture.lockedStart());
    fixture.scheduler.tick(10);
    assertTrue(fixture.controller.activeRuntime().isPresent());

    ServerHostedBukkitRoundController.Result expired = fixture.controller.timeExpired();

    assertEquals(ServerHostedBukkitRoundController.Code.CLEANUP_COMPLETED, expired.code());
    assertEquals(ServerHostedRoundState.IDLE, fixture.coordinator.state());
    assertTrue(fixture.controller.activeRuntime().isEmpty());
    assertTrue(fixture.events.contains("cleanup:TIME_EXPIRED"));
  }

  private Fixture fixture() {
    return fixture(false);
  }

  private Fixture waitingFixture() {
    Fixture fixture = fixture(false);
    fixture.prepareWaitingOnly();
    return fixture;
  }

  private Fixture fixture(boolean failRuntimeFactory) {
    return new Fixture(failRuntimeFactory);
  }

  private final class Fixture {
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID world = UUID.randomUUID();
    private final ServerHostedRoundCoordinator coordinator = new ServerHostedRoundCoordinator();
    private final ServerHostedSession session = new ServerHostedSession(coordinator);
    private final ServerHostedSessionControlService control =
        new ServerHostedSessionControlService(session);
    private final List<String> events = new ArrayList<>();
    private final List<Integer> countdownSeconds = new ArrayList<>();
    private final FakeScheduler scheduler = new FakeScheduler();
    private boolean cleanupSucceeds = true;
    private final ServerHostedBukkitRoundOrchestrator<String> orchestrator;
    private final ServerHostedRoundActivationService<String> activation;
    private ServerHostedBukkitRoundController<String> controller;
    private boolean waitingPrepared;

    private Fixture(boolean failRuntimeFactory) {
      PlayerReturnLedger ledger = new PlayerReturnLedger(
          tempDir.resolve(UUID.randomUUID() + ".ledger")
      );
      assertTrue(ledger.open().available());

      ServerHostedRoundPreparationService<String> preparation =
          new ServerHostedRoundPreparationService<>(coordinator, ledger);
      FakeRuntime runtime = new FakeRuntime(events);

      orchestrator = new ServerHostedBukkitRoundOrchestrator<>(
          coordinator,
          preparation,
          playerId -> Optional.of(record(playerId)),
          claim -> {
            events.add("cleanup:" + claim.cause());
            return cleanupSucceeds;
          }
      );

      activation =
          failRuntimeFactory
              ? new ServerHostedRoundActivationService<>(
                  coordinator,
                  orchestrator,
                  runtime,
                  Duration.ofMinutes(5),
                  (ignoredCoordinator, ignoredContext, ignoredDuration) -> {
                    throw new IllegalStateException("simulated runtime factory failure");
                  }
              )
              : new ServerHostedRoundActivationService<>(
                  coordinator,
                  orchestrator,
                  runtime,
                  Duration.ofMinutes(5)
              );

      controller = controllerWithObserver(countdownSeconds::add);
    }

    private ServerHostedBukkitRoundController<String> controllerWithObserver(
        java.util.function.IntConsumer observer
    ) {
      return new ServerHostedBukkitRoundController<>(
          coordinator,
          activation,
          orchestrator,
          scheduler,
          observer
      );
    }

    private ServerHostedBukkitRoundController<String> controllerWithRuntimeObservers(
        java.util.function.IntConsumer countdownObserver,
        java.util.function.Consumer<ServerHostedSharedRoundRuntime> runningObserver,
        Runnable stoppedObserver
    ) {
      return new ServerHostedBukkitRoundController<>(
          coordinator,
          activation,
          orchestrator,
          scheduler,
          countdownObserver,
          runningObserver,
          stoppedObserver
      );
    }

    private void prepareWaitingOnly() {
      if (waitingPrepared) return;
      assertTrue(session.create());
      assertEquals(ServerHostedSession.JoinResult.JOINED, session.join(first));
      waitingPrepared = true;
    }

    private ServerHostedSessionControlService.StartDecision lockedStart() {
      if (!waitingPrepared) {
        assertTrue(session.create());
        assertEquals(ServerHostedSession.JoinResult.JOINED, session.join(first));
        assertEquals(ServerHostedSession.JoinResult.JOINED, session.join(second));
        waitingPrepared = true;
      }
      return control.requestStart(true);
    }

    private PlayerReturnRecord record(UUID playerId) {
      return new PlayerReturnRecord(
          PlayerReturnRecord.CURRENT_SCHEMA_VERSION,
          UUID.randomUUID(),
          playerId,
          world,
          "world",
          playerId.equals(first) ? 1.0 : 2.0,
          70.0,
          3.0,
          10.0f,
          20.0f,
          Instant.parse("2026-08-09T00:00:00Z")
      );
    }
  }

  private static final class FakeScheduler
      implements ServerHostedBukkitRoundController.SchedulerPort {
    private final List<FakeScheduledTask> tasks = new ArrayList<>();
    private RuntimeException failure;

    @Override
    public ServerHostedBukkitRoundController.ScheduledTask scheduleRepeating(
        long initialDelayTicks,
        long periodTicks,
        Runnable task
    ) {
      assertEquals(20L, initialDelayTicks);
      assertEquals(20L, periodTicks);
      if (failure != null) throw failure;
      FakeScheduledTask scheduled = new FakeScheduledTask(task);
      tasks.add(scheduled);
      return scheduled;
    }

    void tick(int count) {
      for (int index = 0; index < count; index++) {
        for (FakeScheduledTask task : List.copyOf(tasks)) {
          if (!task.cancelled) task.runnable.run();
        }
      }
    }
  }

  private static final class FakeScheduledTask
      implements ServerHostedBukkitRoundController.ScheduledTask {
    private final Runnable runnable;
    private boolean cancelled;

    FakeScheduledTask(Runnable runnable) {
      this.runnable = runnable;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }

  private static final class FakeRuntime
      implements ServerHostedRoundPreparationService.RuntimePort<String> {
    private final List<String> events;

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
      return true;
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
