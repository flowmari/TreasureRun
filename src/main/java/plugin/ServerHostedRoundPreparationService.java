package plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fail-closed preparation transaction for one locked server-hosted round.
 *
 * <p>This service owns no lifecycle state. It requires an immutable SERVER_HOSTED round context
 * that has already been locked by {@link ServerHostedRoundCoordinator}. Every participant return
 * obligation is durably committed in one ledger snapshot before the runtime port is allowed to
 * prepare an arena or teleport any participant.</p>
 *
 * <p>On a failure after the durable batch is accepted, the service rolls back arena work, restores
 * only participants that were actually teleported, clears obligations for participants who never
 * moved, and leaves any unsuccessfully restored participant pending for the existing replay-safe
 * recovery path.</p>
 */
public final class ServerHostedRoundPreparationService<A> {

  public enum Code {
    PREPARED,
    INVALID_STATE,
    INVALID_CONTEXT,
    PENDING_RETURN_EXISTS,
    RETURN_BATCH_REJECTED,
    ARENA_PREPARATION_FAILED,
    CHEST_PLACEMENT_FAILED,
    TELEPORT_FAILED,
    ACTIVATION_FAILED
  }

  public record Result(
      Code code,
      boolean rollbackComplete,
      List<UUID> durablePendingParticipants,
      String detail
  ) {
    public Result {
      code = Objects.requireNonNull(code, "code");
      durablePendingParticipants = List.copyOf(
          Objects.requireNonNull(durablePendingParticipants, "durablePendingParticipants")
      );
      detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean prepared() {
      return code == Code.PREPARED;
    }
  }

  /** Side-effect boundary implemented by the Bukkit adapter. */
  public interface RuntimePort<A> {
    A prepareArena(UUID effectsAudienceId) throws Exception;

    /**
     * Asynchronous preparation hook used by production when cold arena mutation must be spread
     * across server ticks. The default keeps existing test/runtime ports source-compatible.
     */
    default void prepareArenaAsync(
        UUID effectsAudienceId,
        Consumer<A> success,
        Consumer<Throwable> failure
    ) {
      Objects.requireNonNull(success, "success");
      Objects.requireNonNull(failure, "failure");
      try {
        success.accept(prepareArena(effectsAudienceId));
      } catch (Throwable preparationFailure) {
        failure.accept(preparationFailure);
      }
    }

    /** Cancels only an in-progress arena-preparation job, if the runtime owns one. */
    default void cancelArenaPreparation() { }

    boolean placeChests(A arena) throws Exception;

    boolean teleport(UUID playerId, A arena) throws Exception;

    boolean restore(UUID playerId, PlayerReturnRecord returnRecord) throws Exception;

    void activate(A arena) throws Exception;

    void rollbackArena(A arena) throws Exception;
  }

  private final ServerHostedRoundCoordinator coordinator;
  private final PlayerReturnLedger ledger;

  public ServerHostedRoundPreparationService(
      ServerHostedRoundCoordinator coordinator,
      PlayerReturnLedger ledger
  ) {
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    this.ledger = Objects.requireNonNull(ledger, "ledger");
  }

  public Result prepare(
      RoundRuntimeContext context,
      List<PlayerReturnRecord> returnRecords,
      RuntimePort<A> runtime
  ) {
    Objects.requireNonNull(runtime, "runtime");
    BatchAcceptance accepted = acceptDurableBatch(context, returnRecords);
    if (accepted.failure() != null) return accepted.failure();

    List<PlayerReturnRecord> durableRecords = accepted.records();
    A arena;
    try {
      arena = runtime.prepareArena(context.primaryParticipant());
    } catch (Exception preparationFailure) {
      return rollback(
          Code.ARENA_PREPARATION_FAILED,
          "Arena preparation failed: " + messageOf(preparationFailure),
          durableRecords,
          Set.of(),
          null,
          runtime
      );
    }

    return continueAfterArenaPrepared(arena, durableRecords, runtime);
  }

  /**
   * Asynchronous form of {@link #prepare} used by production cold preparation.
   *
   * <p>The durable return batch is committed synchronously first. Only then may the runtime begin
   * tick-batched world mutation. Completion continues chest placement, teleports, activation, and
   * rollback through exactly the same transaction semantics as the synchronous path.</p>
   */
  public void prepareAsync(
      RoundRuntimeContext context,
      List<PlayerReturnRecord> returnRecords,
      RuntimePort<A> runtime,
      Consumer<Result> completion
  ) {
    Objects.requireNonNull(runtime, "runtime");
    Objects.requireNonNull(completion, "completion");

    BatchAcceptance accepted = acceptDurableBatch(context, returnRecords);
    if (accepted.failure() != null) {
      completion.accept(accepted.failure());
      return;
    }

    List<PlayerReturnRecord> durableRecords = accepted.records();
    try {
      runtime.prepareArenaAsync(
          context.primaryParticipant(),
          arena -> completion.accept(continueAfterArenaPrepared(arena, durableRecords, runtime)),
          preparationFailure -> completion.accept(rollback(
              Code.ARENA_PREPARATION_FAILED,
              "Arena preparation failed: " + messageOf(preparationFailure),
              durableRecords,
              Set.of(),
              null,
              runtime
          ))
      );
    } catch (Throwable preparationFailure) {
      completion.accept(rollback(
          Code.ARENA_PREPARATION_FAILED,
          "Arena preparation failed: " + messageOf(preparationFailure),
          durableRecords,
          Set.of(),
          null,
          runtime
      ));
    }
  }

  private BatchAcceptance acceptDurableBatch(
      RoundRuntimeContext context,
      List<PlayerReturnRecord> returnRecords
  ) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(returnRecords, "returnRecords");

    List<PlayerReturnRecord> requestedRecords = List.copyOf(returnRecords);
    if (coordinator.stateFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED)
        != ServerHostedRoundState.STARTING) {
      return BatchAcceptance.failed(new Result(
          Code.INVALID_STATE,
          true,
          List.of(),
          "The authoritative server-hosted coordinator is not in STARTING."
      ));
    }

    if (!coordinator.participantsFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED)
        .equals(context.participants())
        || !matchesLockedServerHostedContext(context, requestedRecords)) {
      return BatchAcceptance.failed(new Result(
          Code.INVALID_CONTEXT,
          true,
          List.of(),
          "Return records must exactly match the locked SERVER_HOSTED participant snapshot."
      ));
    }

    for (UUID participant : context.participants()) {
      if (ledger.pendingRecord(participant).isPresent()) {
        return BatchAcceptance.failed(new Result(
            Code.PENDING_RETURN_EXISTS,
            true,
            List.of(participant),
            "A participant already has a pending durable return obligation."
        ));
      }
    }

    PlayerReturnLedger.PutBatchResult stored = ledger.putPendingBatch(requestedRecords);
    if (stored.code() != PlayerReturnLedger.PutBatchCode.SAVED) {
      return BatchAcceptance.failed(new Result(
          Code.RETURN_BATCH_REJECTED,
          true,
          List.of(),
          "A fresh durable participant-return batch was not established: " + stored.detail()
      ));
    }

    return BatchAcceptance.accepted(stored.records());
  }

  private Result continueAfterArenaPrepared(
      A arena,
      List<PlayerReturnRecord> durableRecords,
      RuntimePort<A> runtime
  ) {
    if (arena == null) {
      return rollback(
          Code.ARENA_PREPARATION_FAILED,
          "Arena preparation returned no arena handle.",
          durableRecords,
          Set.of(),
          null,
          runtime
      );
    }

    if (coordinator.stateFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED)
        != ServerHostedRoundState.STARTING) {
      return rollback(
          Code.ARENA_PREPARATION_FAILED,
          "The authoritative round left STARTING before arena preparation completed.",
          durableRecords,
          Set.of(),
          arena,
          runtime
      );
    }

    try {
      if (!runtime.placeChests(arena)) {
        return rollback(
            Code.CHEST_PLACEMENT_FAILED,
            "The complete round-owned chest plan could not be placed.",
            durableRecords,
            Set.of(),
            arena,
            runtime
        );
      }
    } catch (Exception chestFailure) {
      return rollback(
          Code.CHEST_PLACEMENT_FAILED,
          "Chest placement failed: " + messageOf(chestFailure),
          durableRecords,
          Set.of(),
          arena,
          runtime
      );
    }

    Set<UUID> teleported = new LinkedHashSet<>();
    for (PlayerReturnRecord record : durableRecords) {
      boolean moved;
      try {
        moved = runtime.teleport(record.playerId(), arena);
      } catch (Exception teleportFailure) {
        return rollback(
            Code.TELEPORT_FAILED,
            "Participant teleport failed: " + messageOf(teleportFailure),
            durableRecords,
            teleported,
            arena,
            runtime
        );
      }
      if (!moved) {
        return rollback(
            Code.TELEPORT_FAILED,
            "A participant teleport was rejected: " + record.playerId(),
            durableRecords,
            teleported,
            arena,
            runtime
        );
      }
      teleported.add(record.playerId());
    }

    try {
      runtime.activate(arena);
    } catch (Exception activationFailure) {
      return rollback(
          Code.ACTIVATION_FAILED,
          "Arena activation failed: " + messageOf(activationFailure),
          durableRecords,
          teleported,
          arena,
          runtime
      );
    }

    return new Result(
        Code.PREPARED,
        true,
        durableRecords.stream().map(PlayerReturnRecord::playerId).toList(),
        "All participants are durably recoverable and the shared arena is prepared."
    );
  }

  private boolean matchesLockedServerHostedContext(
      RoundRuntimeContext context,
      List<PlayerReturnRecord> records
  ) {
    if (context.mode() != RoundRuntimeContext.Mode.SERVER_HOSTED) return false;
    if (context.participants().size() != records.size()) return false;

    for (int i = 0; i < records.size(); i++) {
      PlayerReturnRecord record = records.get(i);
      if (record == null || !context.participants().get(i).equals(record.playerId())) {
        return false;
      }
    }
    return true;
  }

  private Result rollback(
      Code code,
      String detail,
      List<PlayerReturnRecord> durableRecords,
      Set<UUID> teleported,
      A arena,
      RuntimePort<A> runtime
  ) {
    boolean arenaRollbackSucceeded = true;
    if (arena != null) {
      try {
        runtime.rollbackArena(arena);
      } catch (Exception rollbackFailure) {
        arenaRollbackSucceeded = false;
      }
    }

    for (PlayerReturnRecord record : durableRecords) {
      boolean mayClear = !teleported.contains(record.playerId());
      if (!mayClear) {
        try {
          mayClear = runtime.restore(record.playerId(), record);
        } catch (Exception restoreFailure) {
          mayClear = false;
        }
      }

      if (mayClear) {
        ledger.complete(record.playerId(), record.recoveryId());
      }
    }

    List<UUID> stillPending = new ArrayList<>();
    for (PlayerReturnRecord record : durableRecords) {
      if (ledger.pendingRecord(record.playerId()).isPresent()) {
        stillPending.add(record.playerId());
      }
    }

    return new Result(
        code,
        arenaRollbackSucceeded && stillPending.isEmpty(),
        stillPending,
        detail
    );
  }

  private record BatchAcceptance(List<PlayerReturnRecord> records, Result failure) {
    private BatchAcceptance {
      records = List.copyOf(Objects.requireNonNull(records, "records"));
    }

    static BatchAcceptance accepted(List<PlayerReturnRecord> records) {
      return new BatchAcceptance(records, null);
    }

    static BatchAcceptance failed(Result failure) {
      return new BatchAcceptance(List.of(), Objects.requireNonNull(failure, "failure"));
    }
  }

  private static String messageOf(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank()
        ? failure.getClass().getSimpleName()
        : message;
  }
}
