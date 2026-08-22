package plugin;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One-shot replay eligibility and safe entry into the next WAITING session.
 *
 * <p>This service owns no round state. It only remembers which players were offered a
 * post-round replay action after authoritative cleanup completed, then delegates all session
 * creation and join semantics to the existing coordinator-backed {@link ServerHostedSession}.</p>
 */
public final class PostRoundActionService {

  public enum Code {
    JOINED,
    ALREADY_JOINED,
    NOT_ELIGIBLE,
    SESSION_NOT_AVAILABLE,
    SESSION_FULL
  }

  public record Result(Code code, ServerHostedSession.State state, int playerCount) {
    public Result {
      code = Objects.requireNonNull(code, "code");
      state = Objects.requireNonNull(state, "state");
    }
  }

  private final ServerHostedSession session;
  private final Set<UUID> replayEligiblePlayers = new LinkedHashSet<>();

  public PostRoundActionService(ServerHostedSession session) {
    this.session = Objects.requireNonNull(session, "session");
  }

  /**
   * Replaces the previous replay offer with the participants from the just-finished round.
   *
   * <p>Call this only after authoritative cleanup completed.</p>
   */
  public synchronized void offer(Collection<UUID> participants) {
    replayEligiblePlayers.clear();
    for (UUID participant : Objects.requireNonNull(participants, "participants")) {
      replayEligiblePlayers.add(Objects.requireNonNull(participant, "participant"));
    }
  }

  public synchronized Result playAgain(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");

    if (!replayEligiblePlayers.contains(playerId)) {
      return result(Code.NOT_ELIGIBLE);
    }

    ServerHostedSession.State state = session.state();
    if (state == ServerHostedSession.State.IDLE) {
      if (!session.create() && session.state() != ServerHostedSession.State.WAITING) {
        return result(Code.SESSION_NOT_AVAILABLE);
      }
      state = session.state();
    }

    if (state != ServerHostedSession.State.WAITING) {
      return result(Code.SESSION_NOT_AVAILABLE);
    }

    Code code = switch (session.join(playerId)) {
      case JOINED -> Code.JOINED;
      case ALREADY_JOINED -> Code.ALREADY_JOINED;
      case SESSION_NOT_WAITING -> Code.SESSION_NOT_AVAILABLE;
      case SESSION_FULL -> Code.SESSION_FULL;
    };

    if (code == Code.JOINED || code == Code.ALREADY_JOINED) {
      replayEligiblePlayers.remove(playerId);
    }

    return result(code);
  }

  public synchronized boolean isEligible(UUID playerId) {
    return replayEligiblePlayers.contains(Objects.requireNonNull(playerId, "playerId"));
  }

  synchronized int eligibleCount() {
    return replayEligiblePlayers.size();
  }

  private Result result(Code code) {
    return new Result(code, session.state(), session.playerCount());
  }
}
