package plugin;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * Connects the framework-independent server-hosted session command service to Bukkit and i18n.
 *
 * <p>This adapter starts no gameplay. It maps Bukkit senders to typed actors, delegates
 * create/join/leave/status to {@link ServerHostedSessionCommandService}, and translates the
 * returned result through the existing language boundary.</p>
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
  private final Function<CommandSender, String> languageResolver;
  private final MessageResolver messageResolver;

  public ServerHostedSessionCommandAdapter(
      ServerHostedSessionCommandService service,
      Function<CommandSender, String> languageResolver,
      MessageResolver messageResolver
  ) {
    this.service = Objects.requireNonNull(service, "service");
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

    ServerHostedSessionCommandService.Result result = service.execute(
        actor(sender),
        arguments == null ? List.of() : Arrays.asList(arguments)
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
        ? List.of("create", "join", "leave", "status")
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
    String language = languageResolver.apply(sender);
    if (language == null || language.isBlank()) {
      language = "en";
    }

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
