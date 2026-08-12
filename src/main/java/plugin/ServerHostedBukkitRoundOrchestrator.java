package plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Candidate orchestration boundary between the server-hosted command lifecycle and Bukkit runtime.
 *
 * <p>This class deliberately contains no Bukkit types. Production wiring can adapt Bukkit players,
 * locations, arena state, and cleanup into the narrow ports below while keeping the lifecycle
 * contract testable. It owns no second state machine: {@link ServerHostedRoundCoordinator} remains
 * authoritative.</p>
 *
 * <p>Preparation is fail-closed. The complete locked participant snapshot is read first, every
 * return destination is resolved before the preparation transaction is invoked, and
 * {@link ServerHostedRoundPreparationService} durably commits the whole batch before any runtime
 * teleport is permitted.</p>
 */
public final class ServerHostedBukkitRoundOrchestrator<A> {

  public enum Code {
    PREPARED_FOR_COUNTDOWN,
    INVALID_STATE,
    RETURN_DESTINATION_UNAVAILABLE,
    PREPARATION_FAILED,
    CLEANUP_COMPLETED,
    CLEANUP_PENDING,
    NO_ACTIVE_ROUND
  }

  public record Result(
      Code code,
      List<UUID> participants,
      Optional<ServerHostedRoundPreparationService.Code> preparationCode,
      String detail
  ) {
    public Result {
      code = Objects.requireNonNull(code, "code");
      participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
      preparationCode = Objects.requireNonNull(preparationCode, "preparationCode");
      detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean readyForCountdown() {
      return code == Code.PREPARED_FOR_COUNTDOWN;
    }
  }

  @FunctionalInterface
  public interface ReturnDestinationResolver {
    Optional<PlayerReturnRecord> resolve(UUID playerId) throws Exception;
  }

  /**
   * Idempotent runtime cleanup boundary.
   *
   * <p>Returning {@code false} leaves the coordinator in RESETTING. The same cleanup claim is kept
   * by this orchestrator and can be retried without claiming a second cleanup operation.</p>
   */
  @FunctionalInterface
  public interface CleanupPort {
    boolean cleanup(ServerHostedRoundCoordinator.CleanupClaim claim) throws Exception;

    /**
     * Cleanup with participants that are known to be unavailable for synchronous return.
     *
     * <p>The default preserves compatibility for cleanup ports that do not need this distinction.
     * Bukkit production cleanup overrides it so PlayerQuitEvent identity remains authoritative
     * even if the quitting Player is still observable during event dispatch.</p>
     */
    default boolean cleanup(
        ServerHostedRoundCoordinator.CleanupClaim claim,
        Set<UUID> unavailableParticipants
    ) throws Exception {
      Objects.requireNonNull(unavailableParticipants, "unavailableParticipants");
      return cleanup(claim);
    }
  }

  private final ServerHostedRoundCoordinator coordinator;
  private final ServerHostedRoundPreparationService<A> preparationService;
  private final ReturnDestinationResolver returnDestinationResolver;
  private final CleanupPort cleanupPort;

  private ServerHostedRoundCoordinator.CleanupClaim retainedCleanupClaim;
  private Set<UUID> retainedUnavailableParticipants = Set.of();

  public ServerHostedBukkitRoundOrchestrator(
      ServerHostedRoundCoordinator coordinator,
      ServerHostedRoundPreparationService<A> preparationService,
      ReturnDestinationResolver returnDestinationResolver,
      CleanupPort cleanupPort
  ) {
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    this.preparationService = Objects.requireNonNull(preparationService, "preparationService");
    this.returnDestinationResolver = Objects.requireNonNull(
        returnDestinationResolver, "returnDestinationResolver");
    this.cleanupPort = Objects.requireNonNull(cleanupPort, "cleanupPort");
  }

  /**
   * Resolves every participant return destination and then invokes the existing durable preparation
   * transaction. No runtime side effect is allowed before all destinations have been resolved.
   */
  public synchronized Result prepareLockedRound(
      ServerHostedRoundPreparationService.RuntimePort<A> runtime
  ) {
    Objects.requireNonNull(runtime, "runtime");

    if (coordinator.ownershipMode()
        != ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED
        || coordinator.stateFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED)
            != ServerHostedRoundState.STARTING) {
      return result(
          Code.INVALID_STATE,
          coordinator.participantsFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED),
          Optional.empty(),
          "The authoritative server-hosted coordinator is not in STARTING."
      );
    }

    List<UUID> participants = coordinator.participantsFor(
        ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED);
    List<PlayerReturnRecord> records = new ArrayList<>(participants.size());

    for (UUID participant : participants) {
      Optional<PlayerReturnRecord> resolved;
      try {
        resolved = Objects.requireNonNull(
            returnDestinationResolver.resolve(participant),
            "returnDestinationResolver result"
        );
      } catch (Exception failure) {
        return abortAfterPreparationProblem(
            Code.RETURN_DESTINATION_UNAVAILABLE,
            Optional.empty(),
            "Return destination resolution failed for " + participant + ": " + messageOf(failure)
        );
      }

      if (resolved.isEmpty() || !participant.equals(resolved.orElseThrow().playerId())) {
        return abortAfterPreparationProblem(
            Code.RETURN_DESTINATION_UNAVAILABLE,
            Optional.empty(),
            "A complete return destination was not resolved for " + participant + "."
        );
      }
      records.add(resolved.orElseThrow());
    }

    RoundRuntimeContext context = RoundRuntimeContext.serverHosted(participants);
    ServerHostedRoundPreparationService.Result preparation = preparationService.prepare(
        context,
        records,
        runtime
    );

    if (!preparation.prepared()) {
      return abortAfterPreparationProblem(
          Code.PREPARATION_FAILED,
          Optional.of(preparation.code()),
          preparation.detail()
      );
    }

    if (coordinator.beginCountdown()
        != ServerHostedRoundCoordinator.TransitionCode.TRANSITIONED) {
      return abortAfterPreparationProblem(
          Code.PREPARATION_FAILED,
          Optional.of(preparation.code()),
          "Preparation succeeded but the authoritative coordinator could not enter COUNTDOWN."
      );
    }

    return result(
        Code.PREPARED_FOR_COUNTDOWN,
        participants,
        Optional.of(preparation.code()),
        "All return obligations are durable and the shared round is ready for COUNTDOWN."
    );
  }

  /**
   * Fail-closed cleanup entry used when shared runtime construction fails after COUNTDOWN.
   *
   * <p>The lifecycle remains owned by the coordinator; this method only reuses the retained
   * idempotent cleanup path with the semantically correct failure cause.</p>
   */
  public synchronized Result runtimeActivationFailed() {
    return abort(ServerHostedRoundCoordinator.ResetCause.PREPARATION_FAILED);
  }

  public synchronized Result stopRequested() {
    return abort(ServerHostedRoundCoordinator.ResetCause.STOP_REQUESTED);
  }

  public synchronized Result roundCompleted() {
    return abort(ServerHostedRoundCoordinator.ResetCause.ROUND_COMPLETED);
  }

  public synchronized Result timeExpired() {
    return abort(ServerHostedRoundCoordinator.ResetCause.TIME_EXPIRED);
  }

  public synchronized Result participantDisconnected(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    List<UUID> participants = coordinator.participantsFor(
        ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED);
    if (!participants.contains(playerId)) {
      return result(
          Code.NO_ACTIVE_ROUND,
          participants,
          Optional.empty(),
          "The disconnected player is not part of the active server-hosted round."
      );
    }
    if (coordinator.stateFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED)
        == ServerHostedRoundState.WAITING) {
      return result(
          Code.INVALID_STATE,
          participants,
          Optional.empty(),
          "WAITING disconnects remain a roster-leave concern and do not require round cleanup."
      );
    }
    return abort(
        ServerHostedRoundCoordinator.ResetCause.PARTICIPANT_DISCONNECTED,
        Set.of(playerId)
    );
  }

  public synchronized Result pluginDisabled() {
    return abort(ServerHostedRoundCoordinator.ResetCause.PLUGIN_DISABLED);
  }

  /** Retry an already-claimed cleanup after an idempotent runtime cleanup returned false/threw. */
  public synchronized Result retryCleanup() {
    if (retainedCleanupClaim == null) {
      return result(
          Code.NO_ACTIVE_ROUND,
          coordinator.participantsFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED),
          Optional.empty(),
          "No retained cleanup claim is available for retry."
      );
    }
    return runRetainedCleanup(Optional.empty(), "Retrying retained cleanup claim.");
  }

  private Result abortAfterPreparationProblem(
      Code failureCode,
      Optional<ServerHostedRoundPreparationService.Code> preparationCode,
      String detail
  ) {
    Result cleanup = abort(ServerHostedRoundCoordinator.ResetCause.PREPARATION_FAILED);
    if (cleanup.code() == Code.CLEANUP_PENDING) {
      return result(
          Code.CLEANUP_PENDING,
          cleanup.participants(),
          preparationCode,
          detail + " Cleanup is still pending: " + cleanup.detail()
      );
    }
    if (cleanup.code() == Code.CLEANUP_COMPLETED) {
      return result(failureCode, cleanup.participants(), preparationCode, detail);
    }
    return result(failureCode, cleanup.participants(), preparationCode, detail);
  }

  private Result abort(ServerHostedRoundCoordinator.ResetCause cause) {
    return abort(cause, Set.of());
  }

  private Result abort(
      ServerHostedRoundCoordinator.ResetCause cause,
      Set<UUID> unavailableParticipants
  ) {
    Objects.requireNonNull(cause, "cause");
    Objects.requireNonNull(unavailableParticipants, "unavailableParticipants");

    if (coordinator.state() == ServerHostedRoundState.IDLE) {
      return result(
          Code.NO_ACTIVE_ROUND,
          List.of(),
          Optional.empty(),
          "No active round requires cleanup."
      );
    }

    ServerHostedRoundCoordinator.ResetDecision reset = coordinator.requestReset(cause);
    List<UUID> participants = reset.participants();

    if (!unavailableParticipants.isEmpty()) {
      LinkedHashSet<UUID> combined = new LinkedHashSet<>(retainedUnavailableParticipants);
      for (UUID unavailableParticipant : unavailableParticipants) {
        if (participants.contains(unavailableParticipant)) {
          combined.add(unavailableParticipant);
        }
      }
      retainedUnavailableParticipants = Set.copyOf(combined);
    }

    if (retainedCleanupClaim == null) {
      retainedCleanupClaim = coordinator.claimCleanup().orElse(null);
    }

    if (retainedCleanupClaim == null) {
      return result(
          Code.CLEANUP_PENDING,
          participants,
          Optional.empty(),
          "The round is RESETTING but this orchestrator does not own the cleanup claim."
      );
    }

    return runRetainedCleanup(
        Optional.empty(),
        "Cleanup requested for " + retainedCleanupClaim.cause() + "."
    );
  }

  private Result runRetainedCleanup(
      Optional<ServerHostedRoundPreparationService.Code> preparationCode,
      String detail
  ) {
    ServerHostedRoundCoordinator.CleanupClaim claim = retainedCleanupClaim;
    boolean completed;
    try {
      completed = cleanupPort.cleanup(claim, retainedUnavailableParticipants);
    } catch (Exception failure) {
      completed = false;
      detail = detail + " Runtime cleanup failed: " + messageOf(failure);
    }

    if (!completed) {
      return result(
          Code.CLEANUP_PENDING,
          claim.participants(),
          preparationCode,
          detail
      );
    }

    coordinator.completeReset();
    retainedCleanupClaim = null;
    retainedUnavailableParticipants = Set.of();
    return result(
        Code.CLEANUP_COMPLETED,
        claim.participants(),
        preparationCode,
        detail
    );
  }

  private static Result result(
      Code code,
      List<UUID> participants,
      Optional<ServerHostedRoundPreparationService.Code> preparationCode,
      String detail
  ) {
    return new Result(code, participants, preparationCode, detail);
  }

  private static String messageOf(Exception failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank()
        ? failure.getClass().getSimpleName()
        : message;
  }
}
