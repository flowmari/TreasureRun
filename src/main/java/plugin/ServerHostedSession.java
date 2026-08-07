package plugin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Compatibility facade for the older session API.
 *
 * <p>All mutable lifecycle and roster state is owned by {@link ServerHostedRoundCoordinator}.</p>
 */
public final class ServerHostedSession {

  public static final int MIN_PLAYERS = ServerHostedRoundCoordinator.MIN_PLAYERS;
  public static final int MAX_PLAYERS = ServerHostedRoundCoordinator.MAX_PLAYERS;

  public enum State { IDLE, WAITING, LOCKED }
  public enum JoinResult { JOINED, ALREADY_JOINED, SESSION_NOT_WAITING, SESSION_FULL }
  public enum LeaveResult { LEFT, LEFT_AND_SESSION_RESET, NOT_JOINED, SESSION_NOT_WAITING }
  public enum StartStatus { LOCKED, TOO_FEW_PLAYERS, SESSION_NOT_WAITING }

  public record StartResult(StartStatus status, List<UUID> participants) {
    public StartResult {
      Objects.requireNonNull(status, "status");
      participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
    }
  }

  private final ServerHostedRoundCoordinator coordinator;

  public ServerHostedSession() { this(new ServerHostedRoundCoordinator()); }

  ServerHostedSession(ServerHostedRoundCoordinator coordinator) {
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
  }

  ServerHostedRoundCoordinator coordinator() { return coordinator; }

  public synchronized State state() {
    return switch (coordinator.stateFor(
        ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED)) {
      case IDLE -> State.IDLE;
      case WAITING -> State.WAITING;
      case STARTING, COUNTDOWN, RUNNING, RESETTING -> State.LOCKED;
    };
  }

  public synchronized boolean create() {
    return coordinator.create() == ServerHostedRoundCoordinator.CreateCode.CREATED;
  }

  public synchronized JoinResult join(UUID playerId) {
    return switch (coordinator.join(Objects.requireNonNull(playerId, "playerId"))) {
      case JOINED -> JoinResult.JOINED;
      case ALREADY_JOINED -> JoinResult.ALREADY_JOINED;
      case SESSION_NOT_WAITING -> JoinResult.SESSION_NOT_WAITING;
      case SESSION_FULL -> JoinResult.SESSION_FULL;
    };
  }

  public synchronized LeaveResult leave(UUID playerId) {
    return switch (coordinator.leave(Objects.requireNonNull(playerId, "playerId"))) {
      case LEFT -> LeaveResult.LEFT;
      case LEFT_AND_SESSION_RESET -> LeaveResult.LEFT_AND_SESSION_RESET;
      case NOT_JOINED -> LeaveResult.NOT_JOINED;
      case SESSION_NOT_WAITING -> LeaveResult.SESSION_NOT_WAITING;
    };
  }

  public synchronized StartResult lockForStart() {
    ServerHostedRoundCoordinator.StartDecision decision = coordinator.start();
    StartStatus status = switch (decision.code()) {
      case STARTING -> StartStatus.LOCKED;
      case TOO_FEW_PLAYERS -> StartStatus.TOO_FEW_PLAYERS;
      case SESSION_NOT_WAITING -> StartStatus.SESSION_NOT_WAITING;
    };
    return new StartResult(status, decision.participants());
  }

  public synchronized boolean canStart() { return coordinator.canStart(); }
  public synchronized int playerCount() { return participants().size(); }
  public synchronized boolean contains(UUID playerId) {
    return participants().contains(Objects.requireNonNull(playerId, "playerId"));
  }
  public synchronized List<UUID> participants() {
    return coordinator.participantsFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED);
  }

  /** Reset only the server-hosted ownership path; legacy /gamestart cleanup remains owned by RoundLifecycle. */
  public synchronized void reset() {
    if (coordinator.ownershipMode()
        == ServerHostedRoundCoordinator.OwnershipMode.LEGACY_GAMESTART_COMPATIBILITY) {
      return;
    }
    if (coordinator.state() == ServerHostedRoundState.IDLE) return;
    coordinator.requestReset(ServerHostedRoundCoordinator.ResetCause.PLUGIN_DISABLED);
    if (coordinator.claimCleanup().isPresent()) coordinator.completeReset();
  }
}
