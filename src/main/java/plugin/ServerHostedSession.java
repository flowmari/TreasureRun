package plugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the roster contract for one server-hosted TreasureRun session.
 *
 * <p>This class contains no Bukkit calls and does not start gameplay. It defines the
 * single-session, two-to-eight-player boundary that command and runtime adapters can connect to
 * in later changes.</p>
 */
public final class ServerHostedSession {

  public static final int MIN_PLAYERS = 2;
  public static final int MAX_PLAYERS = 8;

  public enum State {
    IDLE,
    WAITING,
    LOCKED
  }

  public enum JoinResult {
    JOINED,
    ALREADY_JOINED,
    SESSION_NOT_WAITING,
    SESSION_FULL
  }

  public enum LeaveResult {
    LEFT,
    LEFT_AND_SESSION_RESET,
    NOT_JOINED,
    SESSION_NOT_WAITING
  }

  public enum StartStatus {
    LOCKED,
    TOO_FEW_PLAYERS,
    SESSION_NOT_WAITING
  }

  public record StartResult(StartStatus status, List<UUID> participants) {
    public StartResult {
      Objects.requireNonNull(status, "status");
      participants =
          List.copyOf(Objects.requireNonNull(participants, "participants"));
    }
  }

  private State state = State.IDLE;
  private final Set<UUID> participants = new LinkedHashSet<>();

  public synchronized State state() {
    return state;
  }

  public synchronized boolean create() {
    if (state != State.IDLE) return false;

    participants.clear();
    state = State.WAITING;
    return true;
  }

  public synchronized JoinResult join(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");

    if (state != State.WAITING) {
      return JoinResult.SESSION_NOT_WAITING;
    }

    if (participants.contains(playerId)) {
      return JoinResult.ALREADY_JOINED;
    }

    if (participants.size() >= MAX_PLAYERS) {
      return JoinResult.SESSION_FULL;
    }

    participants.add(playerId);
    return JoinResult.JOINED;
  }

  public synchronized LeaveResult leave(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");

    if (state != State.WAITING) {
      return LeaveResult.SESSION_NOT_WAITING;
    }

    if (!participants.remove(playerId)) {
      return LeaveResult.NOT_JOINED;
    }

    if (participants.isEmpty()) {
      state = State.IDLE;
      return LeaveResult.LEFT_AND_SESSION_RESET;
    }

    return LeaveResult.LEFT;
  }

  public synchronized StartResult lockForStart() {
    if (state != State.WAITING) {
      return new StartResult(
          StartStatus.SESSION_NOT_WAITING,
          List.of()
      );
    }

    if (participants.size() < MIN_PLAYERS) {
      return new StartResult(
          StartStatus.TOO_FEW_PLAYERS,
          participants()
      );
    }

    state = State.LOCKED;

    return new StartResult(
        StartStatus.LOCKED,
        participants()
    );
  }

  public synchronized boolean canStart() {
    return state == State.WAITING
        && participants.size() >= MIN_PLAYERS;
  }

  public synchronized int playerCount() {
    return participants.size();
  }

  public synchronized boolean contains(UUID playerId) {
    return participants.contains(
        Objects.requireNonNull(playerId, "playerId")
    );
  }

  public synchronized List<UUID> participants() {
    return List.copyOf(participants);
  }

  public synchronized void reset() {
    participants.clear();
    state = State.IDLE;
  }
}
