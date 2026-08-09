package plugin;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Narrow framework-independent bridge from a locked server-hosted roster to the shared runtime.
 *
 * <p>This service owns no lifecycle state, runtime state, Bukkit task, message, or release metadata.
 * The authoritative {@link ServerHostedRoundCoordinator} remains the only lifecycle owner. Durable
 * preparation is delegated to {@link ServerHostedBukkitRoundOrchestrator}; a later countdown
 * completion callback may then transition COUNTDOWN to RUNNING and receive one shared runtime for
 * the exact locked participant snapshot.</p>
 */
public final class ServerHostedRoundActivationService<A> {

  public enum Code {
    PREPARED_FOR_COUNTDOWN,
    RUNNING,
    INVALID_STATE,
    PREPARATION_FAILED,
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
  interface SharedRuntimeFactory {
    ServerHostedSharedRoundRuntime begin(
        ServerHostedRoundCoordinator coordinator,
        RoundRuntimeContext context,
        Duration duration
    );
  }

  private final ServerHostedRoundCoordinator coordinator;
  private final ServerHostedBukkitRoundOrchestrator<A> orchestrator;
  private final ServerHostedRoundPreparationService.RuntimePort<A> runtimePort;
  private final Duration roundDuration;
  private final SharedRuntimeFactory sharedRuntimeFactory;

  public ServerHostedRoundActivationService(
      ServerHostedRoundCoordinator coordinator,
      ServerHostedBukkitRoundOrchestrator<A> orchestrator,
      ServerHostedRoundPreparationService.RuntimePort<A> runtimePort,
      Duration roundDuration
  ) {
    this(
        coordinator,
        orchestrator,
        runtimePort,
        roundDuration,
        ServerHostedSharedRoundRuntime::begin
    );
  }

  ServerHostedRoundActivationService(
      ServerHostedRoundCoordinator coordinator,
      ServerHostedBukkitRoundOrchestrator<A> orchestrator,
      ServerHostedRoundPreparationService.RuntimePort<A> runtimePort,
      Duration roundDuration,
      SharedRuntimeFactory sharedRuntimeFactory
  ) {
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
    this.runtimePort = Objects.requireNonNull(runtimePort, "runtimePort");
    this.roundDuration = requirePositive(roundDuration);
    this.sharedRuntimeFactory = Objects.requireNonNull(sharedRuntimeFactory, "sharedRuntimeFactory");
  }

  /**
   * Prepares the already-locked roster and leaves the authoritative coordinator in COUNTDOWN.
   *
   * <p>No participant is allowed to move before the existing preparation transaction has durably
   * committed every return obligation.</p>
   */
  public synchronized Result prepareLockedRound() {
    ServerHostedBukkitRoundOrchestrator.Result preparation =
        orchestrator.prepareLockedRound(runtimePort);

    return switch (preparation.code()) {
      case PREPARED_FOR_COUNTDOWN -> result(
          Code.PREPARED_FOR_COUNTDOWN,
          preparation.participants(),
          Optional.empty(),
          preparation.detail()
      );
      case CLEANUP_PENDING -> result(
          Code.CLEANUP_PENDING,
          preparation.participants(),
          Optional.empty(),
          preparation.detail()
      );
      case INVALID_STATE, NO_ACTIVE_ROUND -> result(
          Code.INVALID_STATE,
          preparation.participants(),
          Optional.empty(),
          preparation.detail()
      );
      case RETURN_DESTINATION_UNAVAILABLE, PREPARATION_FAILED, CLEANUP_COMPLETED -> result(
          Code.PREPARATION_FAILED,
          preparation.participants(),
          Optional.empty(),
          preparation.detail()
      );
    };
  }

  /**
   * Completes the countdown boundary and returns a shared runtime for the exact locked roster.
   *
   * <p>The Bukkit countdown task is intentionally outside this service. A future production adapter
   * calls this method once when the countdown reaches zero and owns the returned runtime handle.</p>
   */
  public synchronized Result beginRunningAfterCountdown() {
    if (coordinator.ownershipMode()
            != ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED
        || coordinator.stateFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED)
            != ServerHostedRoundState.COUNTDOWN) {
      return result(
          Code.INVALID_STATE,
          coordinator.participantsFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED),
          Optional.empty(),
          "The authoritative server-hosted round is not ready to enter RUNNING."
      );
    }

    List<UUID> participants = coordinator.participantsFor(
        ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED
    );
    RoundRuntimeContext context = RoundRuntimeContext.serverHosted(participants);

    if (coordinator.beginRunning()
        != ServerHostedRoundCoordinator.TransitionCode.TRANSITIONED) {
      return result(
          Code.INVALID_STATE,
          participants,
          Optional.empty(),
          "The authoritative coordinator rejected the COUNTDOWN to RUNNING transition."
      );
    }

    try {
      ServerHostedSharedRoundRuntime runtime = Objects.requireNonNull(
          sharedRuntimeFactory.begin(coordinator, context, roundDuration),
          "sharedRuntimeFactory result"
      );
      return result(
          Code.RUNNING,
          participants,
          Optional.of(runtime),
          "The authoritative round is RUNNING with one shared runtime for the locked roster."
      );
    } catch (RuntimeException activationFailure) {
      ServerHostedBukkitRoundOrchestrator.Result cleanup =
          orchestrator.runtimeActivationFailed();
      Code code = cleanup.code() == ServerHostedBukkitRoundOrchestrator.Code.CLEANUP_PENDING
          ? Code.CLEANUP_PENDING
          : Code.PREPARATION_FAILED;
      return result(
          code,
          cleanup.participants(),
          Optional.empty(),
          "Shared runtime activation failed and cleanup was requested: "
              + messageOf(activationFailure)
      );
    }
  }

  private static Result result(
      Code code,
      List<UUID> participants,
      Optional<ServerHostedSharedRoundRuntime> runtime,
      String detail
  ) {
    return new Result(code, participants, runtime, detail);
  }

  private static Duration requirePositive(Duration duration) {
    Objects.requireNonNull(duration, "roundDuration");
    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException("roundDuration must be positive");
    }
    return duration;
  }

  private static String messageOf(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank()
        ? failure.getClass().getSimpleName()
        : message;
  }
}
