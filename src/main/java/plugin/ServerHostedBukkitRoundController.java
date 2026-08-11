package plugin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Owns the server-hosted Bukkit countdown/runtime handle without owning lifecycle state.
 *
 * <p>The authoritative {@link ServerHostedRoundCoordinator} remains the only lifecycle owner.
 * This controller consumes already-typed start/stop decisions, delegates durable preparation and
 * RUNNING activation to {@link ServerHostedRoundActivationService}, and owns only the cancellable
 * scheduler handle plus the active shared-runtime reference required by the Bukkit integration.
 * It does not own player messages, i18n, ResourcePack, database/ranking state, or release metadata.</p>
 */
public final class ServerHostedBukkitRoundController<A> {

  public static final int COUNTDOWN_SECONDS = 10;
  static final long TICKS_PER_SECOND = 20L;

  public enum Code {
    IGNORED,
    COUNTDOWN_STARTED,
    RUNNING,
    INVALID_STATE,
    PREPARATION_FAILED,
    CLEANUP_COMPLETED,
    CLEANUP_PENDING
  }

  public record Result(
      Code code,
      List<UUID> participants,
      Optional<ServerHostedSharedRoundRuntime> runtime,
      String detail
  ) {
    public Result {
      code = Objects.requireNonNull(code, "code");
      participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
      runtime = Objects.requireNonNull(runtime, "runtime");
      detail = Objects.requireNonNull(detail, "detail");
    }
  }

  @FunctionalInterface
  public interface SchedulerPort {
    ScheduledTask scheduleRepeating(long initialDelayTicks, long periodTicks, Runnable task);
  }

  @FunctionalInterface
  public interface ScheduledTask {
    void cancel();
  }

  private final ServerHostedRoundCoordinator coordinator;
  private final ServerHostedRoundActivationService<A> activationService;
  private final ServerHostedBukkitRoundOrchestrator<A> orchestrator;
  private final SchedulerPort scheduler;
  private final IntConsumer countdownObserver;
  private final Consumer<ServerHostedSharedRoundRuntime> runningObserver;
  private final Runnable runtimeStoppedObserver;

  private ScheduledTask countdownTask;
  private int countdownRemaining;
  private ServerHostedSharedRoundRuntime activeRuntime;

  public ServerHostedBukkitRoundController(
      ServerHostedRoundCoordinator coordinator,
      ServerHostedRoundActivationService<A> activationService,
      ServerHostedBukkitRoundOrchestrator<A> orchestrator,
      SchedulerPort scheduler,
      IntConsumer countdownObserver
  ) {
    this(
        coordinator,
        activationService,
        orchestrator,
        scheduler,
        countdownObserver,
        ignoredRuntime -> { },
        () -> { }
    );
  }

  public ServerHostedBukkitRoundController(
      ServerHostedRoundCoordinator coordinator,
      ServerHostedRoundActivationService<A> activationService,
      ServerHostedBukkitRoundOrchestrator<A> orchestrator,
      SchedulerPort scheduler,
      IntConsumer countdownObserver,
      Consumer<ServerHostedSharedRoundRuntime> runningObserver,
      Runnable runtimeStoppedObserver
  ) {
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    this.activationService = Objects.requireNonNull(activationService, "activationService");
    this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.countdownObserver = Objects.requireNonNull(countdownObserver, "countdownObserver");
    this.runningObserver = Objects.requireNonNull(runningObserver, "runningObserver");
    this.runtimeStoppedObserver = Objects.requireNonNull(
        runtimeStoppedObserver,
        "runtimeStoppedObserver"
    );
  }

  /**
   * Consumes a successful control-service roster lock and starts only the server-hosted preparation
   * and ten-second countdown path. Non-successful start decisions are left untouched.
   */
  public synchronized Result start(ServerHostedSessionControlService.StartDecision decision) {
    Objects.requireNonNull(decision, "decision");

    if (decision.code() != ServerHostedSessionControlService.StartCode.ROSTER_LOCKED) {
      return result(
          Code.IGNORED,
          decision.participants(),
          Optional.ofNullable(activeRuntime),
          "The start decision did not lock a roster; no runtime action was taken."
      );
    }

    if (countdownTask != null || activeRuntime != null) {
      return result(
          Code.INVALID_STATE,
          decision.participants(),
          Optional.ofNullable(activeRuntime),
          "A server-hosted countdown or shared runtime is already owned by this controller."
      );
    }

    ServerHostedRoundActivationService.Result preparation =
        activationService.prepareLockedRound();

    if (preparation.code()
        != ServerHostedRoundActivationService.Code.PREPARED_FOR_COUNTDOWN) {
      return mapActivation(preparation);
    }

    countdownRemaining = COUNTDOWN_SECONDS;

    try {
      countdownObserver.accept(COUNTDOWN_SECONDS);
      countdownTask = Objects.requireNonNull(
          scheduler.scheduleRepeating(
              TICKS_PER_SECOND,
              TICKS_PER_SECOND,
              this::countdownTick
          ),
          "scheduler result"
      );
    } catch (RuntimeException schedulingFailure) {
      countdownRemaining = 0;
      ServerHostedBukkitRoundOrchestrator.Result cleanup =
          orchestrator.runtimeActivationFailed();
      return mapCleanup(
          cleanup,
          "The server-hosted countdown could not be scheduled: "
              + messageOf(schedulingFailure)
      );
    }

    return result(
        Code.COUNTDOWN_STARTED,
        preparation.participants(),
        Optional.empty(),
        "The locked roster is prepared and the ten-second server-hosted countdown is scheduled."
    );
  }

  /**
   * Consumes a typed stop decision. WAITING reset and rejected stop decisions need no runtime work;
   * a locked stop cancels the controller-owned countdown before claiming the existing cleanup path.
   */
  public synchronized Result stop(ServerHostedSessionControlService.StopDecision decision) {
    Objects.requireNonNull(decision, "decision");

    if (decision.code() != ServerHostedSessionControlService.StopCode.CLEANUP_REQUIRED) {
      return result(
          Code.IGNORED,
          decision.participants(),
          Optional.ofNullable(activeRuntime),
          "The stop decision requires no server-hosted runtime cleanup."
      );
    }

    cancelOwnedRuntimeState();
    return mapCleanup(orchestrator.stopRequested(), "Server-hosted stop requested.");
  }

  /**
   * Cancels controller-owned runtime state before the existing disconnect cleanup path is claimed.
   * Unrelated players do not cancel the active round.
   */
  public synchronized Result participantDisconnected(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");

    List<UUID> participants = coordinator.participantsFor(
        ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED
    );
    ServerHostedRoundState state = coordinator.stateFor(
        ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED
    );

    if (participants.contains(playerId)
        && state != ServerHostedRoundState.IDLE
        && state != ServerHostedRoundState.WAITING) {
      cancelOwnedRuntimeState();
    }

    return mapCleanup(
        orchestrator.participantDisconnected(playerId),
        "Server-hosted participant disconnect processed."
    );
  }

  /** Cancels controller-owned task/runtime state before the existing plugin-disable cleanup path. */
  public synchronized Result pluginDisabled() {
    if (coordinator.ownershipMode()
        == ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED
        && coordinator.stateFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED)
            != ServerHostedRoundState.IDLE) {
      cancelOwnedRuntimeState();
    }

    return mapCleanup(orchestrator.pluginDisabled(), "Server-hosted plugin disable processed.");
  }

  /** Completes a RUNNING server-hosted round through the existing single cleanup owner. */
  public synchronized Result roundCompleted() {
    if (!hasActiveRunningRuntime()) {
      return result(
          Code.INVALID_STATE,
          coordinator.participantsFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED),
          Optional.ofNullable(activeRuntime),
          "No RUNNING server-hosted runtime can be completed."
      );
    }
    cancelOwnedRuntimeState();
    return mapCleanup(orchestrator.roundCompleted(), "Server-hosted round completion processed.");
  }

  /** Ends a RUNNING server-hosted round after the shared monotonic runtime expires. */
  public synchronized Result timeExpired() {
    if (!hasActiveRunningRuntime()) {
      return result(
          Code.INVALID_STATE,
          coordinator.participantsFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED),
          Optional.ofNullable(activeRuntime),
          "No RUNNING server-hosted runtime can expire."
      );
    }
    cancelOwnedRuntimeState();
    return mapCleanup(orchestrator.timeExpired(), "Server-hosted time expiry processed.");
  }

  /** Retries only the orchestrator's already-retained cleanup claim; no new task is created. */
  public synchronized Result retryCleanup() {
    return mapCleanup(orchestrator.retryCleanup(), "Retried retained server-hosted cleanup.");
  }

  public synchronized boolean countdownScheduled() {
    return countdownTask != null;
  }

  public synchronized int countdownRemaining() {
    return countdownRemaining;
  }

  public synchronized Optional<ServerHostedSharedRoundRuntime> activeRuntime() {
    return Optional.ofNullable(activeRuntime);
  }

  private synchronized void countdownTick() {
    if (countdownTask == null) return;

    if (coordinator.ownershipMode()
        != ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED
        || coordinator.stateFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED)
            != ServerHostedRoundState.COUNTDOWN) {
      cancelOwnedRuntimeState();
      return;
    }

    countdownRemaining--;
    if (countdownRemaining > 0) {
      try {
        countdownObserver.accept(countdownRemaining);
      } catch (RuntimeException observerFailure) {
        cancelCountdownOnly();
        activeRuntime = null;
        orchestrator.runtimeActivationFailed();
      }
      return;
    }

    cancelCountdownOnly();
    ServerHostedRoundActivationService.Result activation =
        activationService.beginRunningAfterCountdown();

    if (activation.code() == ServerHostedRoundActivationService.Code.RUNNING) {
      activeRuntime = activation.runtime().orElseThrow();
      try {
        runningObserver.accept(activeRuntime);
      } catch (RuntimeException observerFailure) {
        cancelOwnedRuntimeState();
        orchestrator.runtimeActivationFailed();
      }
      return;
    }

    activeRuntime = null;
  }

  private boolean hasActiveRunningRuntime() {
    return activeRuntime != null
        && coordinator.ownershipMode()
            == ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED
        && coordinator.stateFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED)
            == ServerHostedRoundState.RUNNING;
  }

  private void cancelOwnedRuntimeState() {
    cancelCountdownOnly();
    boolean hadRuntime = activeRuntime != null;
    activeRuntime = null;
    if (hadRuntime) {
      try {
        runtimeStoppedObserver.run();
      } catch (RuntimeException ignored) {
        // Cleanup must not be blocked by presentation shutdown.
      }
    }
  }

  private void cancelCountdownOnly() {
    ScheduledTask task = countdownTask;
    countdownTask = null;
    countdownRemaining = 0;
    if (task != null) {
      task.cancel();
    }
  }

  private Result mapActivation(ServerHostedRoundActivationService.Result activation) {
    Code code = switch (activation.code()) {
      case PREPARED_FOR_COUNTDOWN -> Code.COUNTDOWN_STARTED;
      case RUNNING -> Code.RUNNING;
      case INVALID_STATE -> Code.INVALID_STATE;
      case PREPARATION_FAILED -> Code.PREPARATION_FAILED;
      case CLEANUP_PENDING -> Code.CLEANUP_PENDING;
    };
    return result(code, activation.participants(), activation.runtime(), activation.detail());
  }

  private Result mapCleanup(
      ServerHostedBukkitRoundOrchestrator.Result cleanup,
      String prefix
  ) {
    Code code = switch (cleanup.code()) {
      case CLEANUP_COMPLETED -> Code.CLEANUP_COMPLETED;
      case CLEANUP_PENDING -> Code.CLEANUP_PENDING;
      case INVALID_STATE, NO_ACTIVE_ROUND -> Code.INVALID_STATE;
      case RETURN_DESTINATION_UNAVAILABLE, PREPARATION_FAILED -> Code.PREPARATION_FAILED;
      case PREPARED_FOR_COUNTDOWN -> Code.INVALID_STATE;
    };

    return result(
        code,
        cleanup.participants(),
        Optional.ofNullable(activeRuntime),
        prefix + " " + cleanup.detail()
    );
  }

  private static Result result(
      Code code,
      List<UUID> participants,
      Optional<ServerHostedSharedRoundRuntime> runtime,
      String detail
  ) {
    return new Result(code, participants, runtime, detail);
  }

  private static String messageOf(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank()
        ? failure.getClass().getSimpleName()
        : message;
  }
}
