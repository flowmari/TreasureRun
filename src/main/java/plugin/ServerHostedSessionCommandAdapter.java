package plugin;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * Connects the framework-independent server-hosted session command service to Bukkit and i18n.
 *
 * <p>This adapter maps Bukkit senders to typed actors, delegates create/join/leave/status to
 * {@link ServerHostedSessionCommandService}, obtains typed start/stop decisions from
 * {@link ServerHostedSessionControlService}, and forwards only accepted runtime work to the
 * already-owned {@link ServerHostedBukkitRoundController}. Player-facing results are resolved
 * through the existing i18n boundary.</p>
 */
public final class ServerHostedSessionCommandAdapter implements TabExecutor {

  static final String ADMIN_PERMISSION = "treasure.admin";

  @FunctionalInterface
  public interface MessageResolver {
    String resolve(String language, String key, Map<String, String> placeholders);
  }

  private static final List<String> PLAYER_SUBCOMMANDS =
      List.of("join", "leave", "status");

  private final ServerHostedSessionCommandService service;
  private final ServerHostedSessionControlService controlService;
  private final ServerHostedBukkitRoundController<?> roundController;
  private final PostRoundActionService postRoundActionService;
  private final BooleanSupplier playAgainEnabled;
  private final Function<CommandSender, String> languageResolver;
  private final MessageResolver messageResolver;

  public ServerHostedSessionCommandAdapter(
      ServerHostedSessionCommandService service,
      ServerHostedSessionControlService controlService,
      ServerHostedBukkitRoundController<?> roundController,
      PostRoundActionService postRoundActionService,
      BooleanSupplier playAgainEnabled,
      Function<CommandSender, String> languageResolver,
      MessageResolver messageResolver
  ) {
    this.service = Objects.requireNonNull(service, "service");
    this.controlService = Objects.requireNonNull(
        controlService,
        "controlService"
    );
    this.roundController = Objects.requireNonNull(
        roundController,
        "roundController"
    );
    this.postRoundActionService = Objects.requireNonNull(
        postRoundActionService,
        "postRoundActionService"
    );
    this.playAgainEnabled = Objects.requireNonNull(
        playAgainEnabled,
        "playAgainEnabled"
    );
    this.languageResolver = Objects.requireNonNull(
        languageResolver,
        "languageResolver"
    );
    this.messageResolver = Objects.requireNonNull(
        messageResolver,
        "messageResolver"
    );
  }

  @Override
  public boolean onCommand(
      CommandSender sender,
      Command command,
      String label,
      String[] arguments
  ) {
    Objects.requireNonNull(sender, "sender");

    List<String> copiedArguments = arguments == null
        ? List.of()
        : Arrays.asList(arguments);

    if (copiedArguments.size() == 1) {
      String subcommand = copiedArguments.get(0) == null
          ? ""
          : copiedArguments.get(0).toLowerCase(Locale.ROOT);

      if (subcommand.equals("playagain")) {
        if (!(sender instanceof Player player)) {
          sender.sendMessage(messageResolver.resolve(
              language(sender),
              "serverHostedSession.command.playerRequired",
              Map.of()
          ));
          return true;
        }

        if (!playAgainEnabled.getAsBoolean()) {
          sender.sendMessage(messageResolver.resolve(
              language(sender),
              "serverHostedSession.command.playAgainNotAvailable",
              Map.of()
          ));
          return true;
        }

        PostRoundActionService.Result replay = postRoundActionService.playAgain(
            player.getUniqueId()
        );
        sender.sendMessage(message(sender, replay));
        return true;
      }

      if (subcommand.equals("start")) {
        ServerHostedSessionControlService.StartDecision decision =
            controlService.requestStart(sender.hasPermission(ADMIN_PERMISSION));
        if (decision.code() == ServerHostedSessionControlService.StartCode.ROSTER_LOCKED) {
          roundController.start(decision);
        }
        sender.sendMessage(message(sender, decision));
        return true;
      }

      if (subcommand.equals("stop")) {
        ServerHostedSessionControlService.StopDecision decision =
            controlService.requestStop(sender.hasPermission(ADMIN_PERMISSION));
        if (decision.code() == ServerHostedSessionControlService.StopCode.CLEANUP_REQUIRED) {
          roundController.stop(decision);
        }
        sender.sendMessage(message(sender, decision));
        return true;
      }
    }

    ServerHostedSessionCommandService.Result result = service.execute(
        actor(sender),
        copiedArguments
    );

    sender.sendMessage(message(sender, result));
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender,
      Command command,
      String alias,
      String[] arguments
  ) {
    Objects.requireNonNull(sender, "sender");

    if (arguments == null || arguments.length != 1) {
      return List.of();
    }

    String prefix = arguments[0] == null
        ? ""
        : arguments[0].toLowerCase(Locale.ROOT);
    List<String> candidates = sender.hasPermission(ADMIN_PERMISSION)
        ? List.of("create", "join", "leave", "start", "stop", "status")
        : PLAYER_SUBCOMMANDS;

    return candidates.stream()
        .filter(value -> value.startsWith(prefix))
        .toList();
  }

  private ServerHostedSessionCommandService.Actor actor(
      CommandSender sender
  ) {
    boolean administrator = sender.hasPermission(ADMIN_PERMISSION);

    if (sender instanceof Player player) {
      return ServerHostedSessionCommandService.Actor.player(
          player.getUniqueId(),
          administrator
      );
    }

    return ServerHostedSessionCommandService.Actor.console(administrator);
  }

  private String message(
      CommandSender sender,
      ServerHostedSessionCommandService.Result result
  ) {
    String language = language(sender);

    ServerHostedSessionCommandService.Snapshot snapshot = result.snapshot();
    Map<String, String> placeholders = new LinkedHashMap<>();
    placeholders.put("{players}", String.valueOf(snapshot.playerCount()));
    placeholders.put("{min}", String.valueOf(snapshot.minimumPlayers()));
    placeholders.put("{max}", String.valueOf(snapshot.maximumPlayers()));

    if (result.code() == ServerHostedSessionCommandService.ResultCode.STATUS) {
      placeholders.put(
          "{state}",
          messageResolver.resolve(
              language,
              stateKey(snapshot.state()),
              Map.of()
          )
      );
      placeholders.put(
          "{ready}",
          messageResolver.resolve(
              language,
              snapshot.canStart()
                  ? "serverHostedSession.value.ready"
                  : "serverHostedSession.value.notReady",
              Map.of()
          )
      );
    }

    return messageResolver.resolve(
        language,
        resultKey(result.code()),
        Map.copyOf(placeholders)
    );
  }

  private String message(
      CommandSender sender,
      PostRoundActionService.Result result
  ) {
    return messageResolver.resolve(
        language(sender),
        switch (result.code()) {
          case JOINED -> "serverHostedSession.command.joined";
          case ALREADY_JOINED -> "serverHostedSession.command.alreadyJoined";
          case NOT_ELIGIBLE -> "serverHostedSession.command.playAgainNotAvailable";
          case SESSION_NOT_AVAILABLE -> "serverHostedSession.command.sessionNotWaiting";
          case SESSION_FULL -> "serverHostedSession.command.sessionFull";
        },
        Map.of(
            "{players}", String.valueOf(result.playerCount()),
            "{min}", String.valueOf(ServerHostedSession.MIN_PLAYERS),
            "{max}", String.valueOf(ServerHostedSession.MAX_PLAYERS)
        )
    );
  }

  private String language(CommandSender sender) {
    String language = languageResolver.apply(sender);
    return language == null || language.isBlank() ? "en" : language;
  }

  private String message(
      CommandSender sender,
      ServerHostedSessionControlService.StartDecision decision
  ) {
    return controlMessage(
        sender,
        startResultKey(decision.code())
    );
  }

  private String message(
      CommandSender sender,
      ServerHostedSessionControlService.StopDecision decision
  ) {
    return controlMessage(
        sender,
        stopResultKey(decision.code())
    );
  }

  private String controlMessage(CommandSender sender, String key) {
    String language = language(sender);

    ServerHostedSessionCommandService.Snapshot snapshot = service.snapshot();
    Map<String, String> placeholders = new LinkedHashMap<>();
    placeholders.put("{players}", String.valueOf(snapshot.playerCount()));
    placeholders.put("{min}", String.valueOf(snapshot.minimumPlayers()));
    placeholders.put("{max}", String.valueOf(snapshot.maximumPlayers()));

    return messageResolver.resolve(
        language,
        key,
        Map.copyOf(placeholders)
    );
  }

  private String startResultKey(
      ServerHostedSessionControlService.StartCode code
  ) {
    return switch (code) {
      case ROSTER_LOCKED ->
          "serverHostedSession.command.startRosterLocked";
      case ADMIN_REQUIRED ->
          "serverHostedSession.command.startAdminRequired";
      case TOO_FEW_PLAYERS ->
          "serverHostedSession.command.startTooFewPlayers";
      case SESSION_NOT_WAITING ->
          "serverHostedSession.command.startSessionNotWaiting";
    };
  }

  private String stopResultKey(
      ServerHostedSessionControlService.StopCode code
  ) {
    return switch (code) {
      case WAITING_RESET ->
          "serverHostedSession.command.stopWaitingReset";
      case CLEANUP_REQUIRED ->
          "serverHostedSession.command.stopCleanupRequired";
      case NO_ACTIVE_SESSION ->
          "serverHostedSession.command.stopNoActiveSession";
      case ADMIN_REQUIRED ->
          "serverHostedSession.command.stopAdminRequired";
    };
  }


  private String stateKey(ServerHostedSession.State state) {
    return switch (state) {
      case IDLE -> "serverHostedSession.value.state.idle";
      case WAITING -> "serverHostedSession.value.state.waiting";
      case LOCKED -> "serverHostedSession.value.state.locked";
    };
  }

  private String resultKey(
      ServerHostedSessionCommandService.ResultCode code
  ) {
    return switch (code) {
      case CREATED -> "serverHostedSession.command.created";
      case ADMIN_REQUIRED -> "serverHostedSession.command.adminRequired";
      case SESSION_ALREADY_EXISTS ->
          "serverHostedSession.command.sessionAlreadyExists";
      case JOINED -> "serverHostedSession.command.joined";
      case ALREADY_JOINED -> "serverHostedSession.command.alreadyJoined";
      case SESSION_NOT_WAITING ->
          "serverHostedSession.command.sessionNotWaiting";
      case SESSION_FULL -> "serverHostedSession.command.sessionFull";
      case LEFT -> "serverHostedSession.command.left";
      case LEFT_AND_SESSION_RESET ->
          "serverHostedSession.command.leftAndSessionReset";
      case NOT_JOINED -> "serverHostedSession.command.notJoined";
      case STATUS -> "serverHostedSession.command.status";
      case PLAYER_REQUIRED -> "serverHostedSession.command.playerRequired";
      case MISSING_SUBCOMMAND ->
          "serverHostedSession.command.missingSubcommand";
      case UNKNOWN_SUBCOMMAND ->
          "serverHostedSession.command.unknownSubcommand";
      case UNEXPECTED_ARGUMENTS ->
          "serverHostedSession.command.unexpectedArguments";
    };
  }
}
