package plugin;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies the create, join, leave, and status command contract to one server-hosted session.
 *
 * <p>This service contains no Bukkit calls and no player-visible text.
 * {@link ServerHostedSessionCommandAdapter} maps a Bukkit command sender to {@link Actor}, passes
 * command arguments into this service, and translates the returned result code through the
 * existing i18n boundary.</p>
 */
public final class ServerHostedSessionCommandService {

  public enum ResultCode {
    CREATED,
    ADMIN_REQUIRED,
    SESSION_ALREADY_EXISTS,
    JOINED,
    ALREADY_JOINED,
    SESSION_NOT_WAITING,
    SESSION_FULL,
    LEFT,
    LEFT_AND_SESSION_RESET,
    NOT_JOINED,
    STATUS,
    PLAYER_REQUIRED,
    MISSING_SUBCOMMAND,
    UNKNOWN_SUBCOMMAND,
    UNEXPECTED_ARGUMENTS
  }

  public record Actor(Optional<UUID> playerId, boolean administrator) {
    public Actor {
      playerId = Objects.requireNonNull(playerId, "playerId");
    }

    public static Actor player(UUID playerId, boolean administrator) {
      return new Actor(
          Optional.of(Objects.requireNonNull(playerId, "playerId")),
          administrator
      );
    }

    public static Actor console(boolean administrator) {
      return new Actor(Optional.empty(), administrator);
    }
  }

  public record Snapshot(
      ServerHostedSession.State state,
      int playerCount,
      int minimumPlayers,
      int maximumPlayers,
      boolean canStart,
      List<UUID> participants
  ) {
    public Snapshot {
      state = Objects.requireNonNull(state, "state");
      participants = List.copyOf(
          Objects.requireNonNull(participants, "participants")
      );
    }
  }

  public record Result(ResultCode code, Snapshot snapshot) {
    public Result {
      code = Objects.requireNonNull(code, "code");
      snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }
  }

  private final ServerHostedSession session;

  public ServerHostedSessionCommandService(
      ServerHostedSession session
  ) {
    this.session = Objects.requireNonNull(session, "session");
  }

  public synchronized Result execute(
      Actor actor,
      List<String> arguments
  ) {
    Objects.requireNonNull(actor, "actor");
    List<String> copiedArguments = List.copyOf(
        Objects.requireNonNull(arguments, "arguments")
    );

    if (copiedArguments.isEmpty()) {
      return result(ResultCode.MISSING_SUBCOMMAND);
    }

    if (copiedArguments.size() != 1) {
      return result(ResultCode.UNEXPECTED_ARGUMENTS);
    }

    String subcommand = copiedArguments.get(0)
        .toLowerCase(Locale.ROOT);

    return switch (subcommand) {
      case "create" -> create(actor);
      case "join" -> join(actor);
      case "leave" -> leave(actor);
      case "status" -> result(ResultCode.STATUS);
      default -> result(ResultCode.UNKNOWN_SUBCOMMAND);
    };
  }

  public synchronized Snapshot snapshot() {
    return new Snapshot(
        session.state(),
        session.playerCount(),
        ServerHostedSession.MIN_PLAYERS,
        ServerHostedSession.MAX_PLAYERS,
        session.canStart(),
        session.participants()
    );
  }

  private Result create(Actor actor) {
    if (!actor.administrator()) {
      return result(ResultCode.ADMIN_REQUIRED);
    }

    return result(
        session.create()
            ? ResultCode.CREATED
            : ResultCode.SESSION_ALREADY_EXISTS
    );
  }

  private Result join(Actor actor) {
    Optional<UUID> playerId = actor.playerId();

    if (playerId.isEmpty()) {
      return result(ResultCode.PLAYER_REQUIRED);
    }

    ResultCode code = switch (session.join(playerId.get())) {
      case JOINED -> ResultCode.JOINED;
      case ALREADY_JOINED -> ResultCode.ALREADY_JOINED;
      case SESSION_NOT_WAITING -> ResultCode.SESSION_NOT_WAITING;
      case SESSION_FULL -> ResultCode.SESSION_FULL;
    };

    return result(code);
  }

  private Result leave(Actor actor) {
    Optional<UUID> playerId = actor.playerId();

    if (playerId.isEmpty()) {
      return result(ResultCode.PLAYER_REQUIRED);
    }

    ResultCode code = switch (session.leave(playerId.get())) {
      case LEFT -> ResultCode.LEFT;
      case LEFT_AND_SESSION_RESET ->
          ResultCode.LEFT_AND_SESSION_RESET;
      case NOT_JOINED -> ResultCode.NOT_JOINED;
      case SESSION_NOT_WAITING -> ResultCode.SESSION_NOT_WAITING;
    };

    return result(code);
  }

  private Result result(ResultCode code) {
    return new Result(code, snapshot());
  }
}
