package plugin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Defines framework-independent start and stop decisions for one server-hosted session.
 *
 * <p>This service contains no Bukkit calls and starts no gameplay. A successful start only locks
 * and returns one immutable participant snapshot. A LOCKED stop request preserves that roster so
 * a later runtime adapter can capture it before cleanup and reset.</p>
 */
public final class ServerHostedSessionControlService {

  public enum StartCode {
    ROSTER_LOCKED,
    ADMIN_REQUIRED,
    TOO_FEW_PLAYERS,
    SESSION_NOT_WAITING
  }

  public enum StopCode {
    WAITING_RESET,
    CLEANUP_REQUIRED,
    NO_ACTIVE_SESSION,
    ADMIN_REQUIRED
  }

  public record StartDecision(
      StartCode code,
      ServerHostedSession.State state,
      List<UUID> participants
  ) {
    public StartDecision {
      code = Objects.requireNonNull(code, "code");
      state = Objects.requireNonNull(state, "state");
      participants = List.copyOf(
          Objects.requireNonNull(participants, "participants")
      );
    }
  }

  public record StopDecision(
      StopCode code,
      ServerHostedSession.State state,
      List<UUID> participants
  ) {
    public StopDecision {
      code = Objects.requireNonNull(code, "code");
      state = Objects.requireNonNull(state, "state");
      participants = List.copyOf(
          Objects.requireNonNull(participants, "participants")
      );
    }
  }

  private final ServerHostedSession session;

  public ServerHostedSessionControlService(ServerHostedSession session) {
    this.session = Objects.requireNonNull(session, "session");
  }

  public StartDecision requestStart(boolean administrator) {
    synchronized (session) {
      if (!administrator) {
        return new StartDecision(
            StartCode.ADMIN_REQUIRED,
            session.state(),
            session.participants()
        );
      }

      ServerHostedSession.StartResult result = session.lockForStart();
      StartCode code = switch (result.status()) {
        case LOCKED -> StartCode.ROSTER_LOCKED;
        case TOO_FEW_PLAYERS -> StartCode.TOO_FEW_PLAYERS;
        case SESSION_NOT_WAITING -> StartCode.SESSION_NOT_WAITING;
      };

      return new StartDecision(
          code,
          session.state(),
          session.participants()
      );
    }
  }

  public StopDecision requestStop(boolean administrator) {
    synchronized (session) {
      if (!administrator) {
        return new StopDecision(
            StopCode.ADMIN_REQUIRED,
            session.state(),
            session.participants()
        );
      }

      ServerHostedSession.State state = session.state();
      List<UUID> participants = session.participants();

      return switch (state) {
        case IDLE -> new StopDecision(
            StopCode.NO_ACTIVE_SESSION,
            ServerHostedSession.State.IDLE,
            participants
        );
        case WAITING -> {
          session.reset();
          yield new StopDecision(
              StopCode.WAITING_RESET,
              ServerHostedSession.State.IDLE,
              participants
          );
        }
        case LOCKED -> new StopDecision(
            StopCode.CLEANUP_REQUIRED,
            ServerHostedSession.State.LOCKED,
            participants
        );
      };
    }
  }
}
