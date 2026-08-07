package plugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the framework-independent lifecycle contract for one TreasureRun round.
 *
 * <p>The server-hosted create/join/start path and the published legacy /gamestart path share this
 * one authoritative state owner. Bukkit gameplay wiring, persistent player-return recovery,
 * multiplayer runtime behavior, and player-visible messages remain outside this class.</p>
 */
public final class ServerHostedRoundCoordinator {

  public static final int MIN_PLAYERS = 2;
  public static final int MAX_PLAYERS = 8;

  public enum OwnershipMode {
    NONE,
    SERVER_HOSTED,
    LEGACY_GAMESTART_COMPATIBILITY
  }

  public enum CreateCode { CREATED, SESSION_ALREADY_EXISTS }
  public enum JoinCode { JOINED, ALREADY_JOINED, SESSION_NOT_WAITING, SESSION_FULL }
  public enum LeaveCode { LEFT, LEFT_AND_SESSION_RESET, NOT_JOINED, SESSION_NOT_WAITING }
  public enum StartCode { STARTING, TOO_FEW_PLAYERS, SESSION_NOT_WAITING }
  public enum TransitionCode { TRANSITIONED, INVALID_STATE }
  public enum ResetCode { RESETTING, ALREADY_RESETTING, NO_ACTIVE_ROUND }

  public enum ResetCause {
    STOP_REQUESTED,
    PARTICIPANT_DISCONNECTED,
    PLUGIN_DISABLED,
    PREPARATION_FAILED,
    ROUND_COMPLETED,
    TIME_EXPIRED,
    LEGACY_RUNTIME_RESET
  }

  public record StartDecision(StartCode code, ServerHostedRoundState state, List<UUID> participants) {
    public StartDecision {
      code = Objects.requireNonNull(code, "code");
      state = Objects.requireNonNull(state, "state");
      participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
    }
  }

  public record ResetDecision(
      ResetCode code,
      ServerHostedRoundState state,
      Optional<ResetCause> cause,
      List<UUID> participants
  ) {
    public ResetDecision {
      code = Objects.requireNonNull(code, "code");
      state = Objects.requireNonNull(state, "state");
      cause = Objects.requireNonNull(cause, "cause");
      participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
    }
  }

  public record CleanupClaim(ResetCause cause, List<UUID> participants) {
    public CleanupClaim {
      cause = Objects.requireNonNull(cause, "cause");
      participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
    }
  }

  private ServerHostedRoundState state = ServerHostedRoundState.IDLE;
  private OwnershipMode ownershipMode = OwnershipMode.NONE;
  private final Set<UUID> waitingRoster = new LinkedHashSet<>();
  private List<UUID> participantSnapshot = List.of();
  private ResetCause resetCause;
  private boolean cleanupClaimed;

  public synchronized ServerHostedRoundState state() { return state; }
  public synchronized OwnershipMode ownershipMode() { return ownershipMode; }

  /** Returns this owner's state only when the requested ownership mode currently owns the round. */
  public synchronized ServerHostedRoundState stateFor(OwnershipMode mode) {
    Objects.requireNonNull(mode, "mode");
    return ownershipMode == mode ? state : ServerHostedRoundState.IDLE;
  }

  /** Returns this owner's participant view only for the requested active ownership mode. */
  public synchronized List<UUID> participantsFor(OwnershipMode mode) {
    Objects.requireNonNull(mode, "mode");
    return ownershipMode == mode ? participantView() : List.of();
  }

  public synchronized CreateCode create() {
    if (state != ServerHostedRoundState.IDLE) return CreateCode.SESSION_ALREADY_EXISTS;
    clearTransientState();
    ownershipMode = OwnershipMode.SERVER_HOSTED;
    state = ServerHostedRoundState.WAITING;
    return CreateCode.CREATED;
  }

  /** Begins the already-published single-player /gamestart path without weakening the 2-8 server-hosted contract. */
  public synchronized boolean beginLegacyPreparation() {
    if (state != ServerHostedRoundState.IDLE) return false;
    clearTransientState();
    ownershipMode = OwnershipMode.LEGACY_GAMESTART_COMPATIBILITY;
    state = ServerHostedRoundState.STARTING;
    return true;
  }

  public synchronized JoinCode join(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    if (ownershipMode != OwnershipMode.SERVER_HOSTED || state != ServerHostedRoundState.WAITING) {
      return JoinCode.SESSION_NOT_WAITING;
    }
    if (waitingRoster.contains(playerId)) return JoinCode.ALREADY_JOINED;
    if (waitingRoster.size() >= MAX_PLAYERS) return JoinCode.SESSION_FULL;
    waitingRoster.add(playerId);
    return JoinCode.JOINED;
  }

  public synchronized LeaveCode leave(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    if (ownershipMode != OwnershipMode.SERVER_HOSTED || state != ServerHostedRoundState.WAITING) {
      return LeaveCode.SESSION_NOT_WAITING;
    }
    if (!waitingRoster.remove(playerId)) return LeaveCode.NOT_JOINED;
    if (waitingRoster.isEmpty()) {
      clearTransientState();
      ownershipMode = OwnershipMode.NONE;
      state = ServerHostedRoundState.IDLE;
      return LeaveCode.LEFT_AND_SESSION_RESET;
    }
    return LeaveCode.LEFT;
  }

  public synchronized StartDecision start() {
    if (ownershipMode != OwnershipMode.SERVER_HOSTED || state != ServerHostedRoundState.WAITING) {
      return new StartDecision(StartCode.SESSION_NOT_WAITING, state, participantView());
    }
    if (waitingRoster.size() < MIN_PLAYERS) {
      return new StartDecision(StartCode.TOO_FEW_PLAYERS, state, List.copyOf(waitingRoster));
    }
    participantSnapshot = List.copyOf(waitingRoster);
    state = ServerHostedRoundState.STARTING;
    return new StartDecision(StartCode.STARTING, state, participantSnapshot);
  }

  public synchronized TransitionCode beginCountdown() {
    if (state != ServerHostedRoundState.STARTING) return TransitionCode.INVALID_STATE;
    state = ServerHostedRoundState.COUNTDOWN;
    return TransitionCode.TRANSITIONED;
  }

  public synchronized TransitionCode beginRunning() {
    if (state != ServerHostedRoundState.COUNTDOWN) return TransitionCode.INVALID_STATE;
    state = ServerHostedRoundState.RUNNING;
    return TransitionCode.TRANSITIONED;
  }

  public synchronized ResetDecision requestReset(ResetCause cause) {
    Objects.requireNonNull(cause, "cause");
    if (state == ServerHostedRoundState.IDLE) return resetDecision(ResetCode.NO_ACTIVE_ROUND);
    if (state == ServerHostedRoundState.RESETTING) return resetDecision(ResetCode.ALREADY_RESETTING);
    if (state == ServerHostedRoundState.WAITING) participantSnapshot = List.copyOf(waitingRoster);
    resetCause = cause;
    cleanupClaimed = false;
    state = ServerHostedRoundState.RESETTING;
    return resetDecision(ResetCode.RESETTING);
  }

  public synchronized Optional<CleanupClaim> claimCleanup() {
    if (state != ServerHostedRoundState.RESETTING || cleanupClaimed) return Optional.empty();
    cleanupClaimed = true;
    return Optional.of(new CleanupClaim(resetCause, participantSnapshot));
  }

  public synchronized void completeReset() {
    if (state != ServerHostedRoundState.RESETTING || !cleanupClaimed) {
      throw new IllegalStateException("Cleanup must be claimed before reset completes.");
    }
    clearTransientState();
    ownershipMode = OwnershipMode.NONE;
    state = ServerHostedRoundState.IDLE;
  }

  public synchronized List<UUID> participants() { return participantView(); }
  public synchronized int playerCount() { return participantView().size(); }
  public synchronized boolean canStart() {
    return ownershipMode == OwnershipMode.SERVER_HOSTED
        && state == ServerHostedRoundState.WAITING
        && waitingRoster.size() >= MIN_PLAYERS;
  }
  public synchronized boolean cleanupClaimed() { return cleanupClaimed; }
  public synchronized Optional<ResetCause> resetCause() { return Optional.ofNullable(resetCause); }

  private List<UUID> participantView() {
    if (state == ServerHostedRoundState.IDLE) return List.of();
    if (state == ServerHostedRoundState.WAITING) return List.copyOf(waitingRoster);
    return participantSnapshot;
  }

  private ResetDecision resetDecision(ResetCode code) {
    return new ResetDecision(code, state, Optional.ofNullable(resetCause), participantView());
  }

  private void clearTransientState() {
    waitingRoster.clear();
    participantSnapshot = List.of();
    resetCause = null;
    cleanupClaimed = false;
  }
}
