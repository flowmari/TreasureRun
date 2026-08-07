package plugin;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Coordinates replay-safe recovery attempts without owning gameplay state. */
public final class PlayerReturnRecoveryService {
  public enum AttemptCode {
    RETURNED,
    PLAYER_UNAVAILABLE,
    DESTINATION_UNAVAILABLE,
    DESTINATION_MISMATCH,
    TELEPORT_FAILED
  }

  public enum RecoveryCode {
    NO_PENDING,
    RETURNED_AND_CLEARED,
    RETURNED_RECORD_RETAINED,
    PLAYER_UNAVAILABLE,
    DESTINATION_UNAVAILABLE,
    DESTINATION_MISMATCH,
    TELEPORT_FAILED,
    ATTEMPT_ERROR,
    ALREADY_IN_PROGRESS
  }

  public record RecoveryResult(
      RecoveryCode code,
      Optional<PlayerReturnRecord> record,
      String detail
  ) {
    public RecoveryResult {
      code = Objects.requireNonNull(code, "code");
      record = Objects.requireNonNull(record, "record");
      detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean playerWasReturned() {
      return code == RecoveryCode.RETURNED_AND_CLEARED
          || code == RecoveryCode.RETURNED_RECORD_RETAINED;
    }
  }

  @FunctionalInterface
  public interface ReturnAttempt {
    AttemptCode attempt(PlayerReturnRecord record) throws Exception;
  }

  private final PlayerReturnLedger ledger;
  private final Set<UUID> inFlight = new HashSet<>();

  public PlayerReturnRecoveryService(PlayerReturnLedger ledger) {
    this.ledger = Objects.requireNonNull(ledger, "ledger");
  }

  public RecoveryResult recover(UUID playerId, ReturnAttempt returnAttempt) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(returnAttempt, "returnAttempt");

    synchronized (inFlight) {
      if (!inFlight.add(playerId)) {
        return new RecoveryResult(
            RecoveryCode.ALREADY_IN_PROGRESS,
            ledger.pendingRecord(playerId),
            "A recovery attempt is already in progress for this player."
        );
      }
    }

    try {
      Optional<PlayerReturnRecord> optionalRecord = ledger.pendingRecord(playerId);
      if (optionalRecord.isEmpty()) {
        return new RecoveryResult(
            RecoveryCode.NO_PENDING,
            Optional.empty(),
            "No durable player-return obligation is pending."
        );
      }

      PlayerReturnRecord record = optionalRecord.get();
      final AttemptCode attemptCode;
      try {
        attemptCode = Objects.requireNonNull(returnAttempt.attempt(record), "attemptCode");
      } catch (Exception exception) {
        return new RecoveryResult(
            RecoveryCode.ATTEMPT_ERROR,
            Optional.of(record),
            "Return attempt threw: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
        );
      }

      if (attemptCode != AttemptCode.RETURNED) {
        return new RecoveryResult(map(attemptCode), Optional.of(record), "Return remains pending.");
      }

      PlayerReturnLedger.CompleteResult completed = ledger.complete(playerId, record.recoveryId());
      if (completed.cleared()) {
        return new RecoveryResult(
            RecoveryCode.RETURNED_AND_CLEARED,
            Optional.of(record),
            completed.detail()
        );
      }
      return new RecoveryResult(
          RecoveryCode.RETURNED_RECORD_RETAINED,
          Optional.of(record),
          completed.detail()
      );
    } finally {
      synchronized (inFlight) {
        inFlight.remove(playerId);
      }
    }
  }

  private static RecoveryCode map(AttemptCode attemptCode) {
    return switch (attemptCode) {
      case PLAYER_UNAVAILABLE -> RecoveryCode.PLAYER_UNAVAILABLE;
      case DESTINATION_UNAVAILABLE -> RecoveryCode.DESTINATION_UNAVAILABLE;
      case DESTINATION_MISMATCH -> RecoveryCode.DESTINATION_MISMATCH;
      case TELEPORT_FAILED -> RecoveryCode.TELEPORT_FAILED;
      case RETURNED -> throw new IllegalArgumentException("RETURNED is handled before mapping.");
    };
  }
}
