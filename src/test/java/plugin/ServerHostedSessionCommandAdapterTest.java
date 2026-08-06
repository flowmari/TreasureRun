package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ServerHostedSessionCommandAdapterTest {

  @Test
  void mapsAdministrativeConsoleAndPlayerActorsWithoutStartingGameplay() {
    Fixture fixture = new Fixture();
    CommandSender console = fixture.console(true);
    Player player = fixture.player(false);

    assertTrue(fixture.execute(console, "create"));
    assertTrue(fixture.execute(player, "join"));

    assertEquals(ServerHostedSession.State.WAITING, fixture.session.state());
    assertEquals(List.of(player.getUniqueId()), fixture.session.participants());
    fixture.assertLastKey("serverHostedSession.command.joined");
  }

  @Test
  void nonAdministrativeCreateIsRejectedAndDoesNotMutateTheSession() {
    Fixture fixture = new Fixture();
    Player player = fixture.player(false);

    fixture.execute(player, "create");

    assertEquals(ServerHostedSession.State.IDLE, fixture.session.state());
    assertEquals(0, fixture.session.playerCount());
    fixture.assertLastKey("serverHostedSession.command.adminRequired");
  }

  @Test
  void consoleJoinAndLeaveRequireAPlayerButStatusRemainsAvailable() {
    Fixture fixture = new Fixture();
    CommandSender console = fixture.console(true);

    fixture.execute(console, "create");
    fixture.execute(console, "join");
    fixture.assertLastKey("serverHostedSession.command.playerRequired");

    fixture.execute(console, "leave");
    fixture.assertLastKey("serverHostedSession.command.playerRequired");

    fixture.execute(console, "status");
    fixture.assertLastKey("serverHostedSession.command.status");
  }

  @Test
  void everyServiceResultMapsToTheExpectedI18nKey() {
    Fixture fixture = new Fixture();
    CommandSender admin = fixture.console(true);
    Player first = fixture.player(false);
    Player absent = fixture.player(false);

    fixture.execute(first, "join");
    fixture.assertLastKey("serverHostedSession.command.sessionNotWaiting");

    fixture.execute(admin, "create");
    fixture.execute(admin, "create");
    fixture.assertLastKey("serverHostedSession.command.sessionAlreadyExists");

    fixture.execute(first, "join");
    fixture.execute(first, "join");
    fixture.assertLastKey("serverHostedSession.command.alreadyJoined");

    fixture.execute(absent, "leave");
    fixture.assertLastKey("serverHostedSession.command.notJoined");

    List<Player> joined = new ArrayList<>();
    joined.add(first);
    for (int index = 1; index < ServerHostedSession.MAX_PLAYERS; index++) {
      Player player = fixture.player(false);
      joined.add(player);
      fixture.execute(player, "join");
    }

    Player overflow = fixture.player(false);
    fixture.execute(overflow, "join");
    fixture.assertLastKey("serverHostedSession.command.sessionFull");

    fixture.execute(joined.get(0), "leave");
    fixture.assertLastKey("serverHostedSession.command.left");

    Fixture resetFixture = new Fixture();
    CommandSender resetAdmin = resetFixture.console(true);
    Player onlyPlayer = resetFixture.player(false);
    resetFixture.execute(resetAdmin, "create");
    resetFixture.execute(onlyPlayer, "join");
    resetFixture.execute(onlyPlayer, "leave");
    resetFixture.assertLastKey(
        "serverHostedSession.command.leftAndSessionReset"
    );
  }

  @Test
  void statusCarriesLocalizedStateAndTypedSnapshotValues() {
    Fixture fixture = new Fixture();
    CommandSender console = fixture.console(true);
    Player first = fixture.player(false);
    Player second = fixture.player(false);

    fixture.execute(console, "create");
    fixture.execute(first, "join");
    fixture.execute(second, "join");
    fixture.execute(first, "status");

    Invocation status = fixture.lastInvocation();
    assertEquals("ja", status.language());
    assertEquals("serverHostedSession.command.status", status.key());
    assertEquals(
        "serverHostedSession.value.state.waiting",
        status.placeholders().get("{state}")
    );
    assertEquals("2", status.placeholders().get("{players}"));
    assertEquals("2", status.placeholders().get("{min}"));
    assertEquals("8", status.placeholders().get("{max}"));
    assertEquals(
        "serverHostedSession.value.ready",
        status.placeholders().get("{ready}")
    );
  }

  @Test
  void missingUnknownAndUnexpectedArgumentsRemainFailClosed() {
    Fixture fixture = new Fixture();
    CommandSender console = fixture.console(true);

    fixture.executeNullArguments(console);
    fixture.assertLastKey("serverHostedSession.command.missingSubcommand");

    fixture.execute(console, "launch");
    fixture.assertLastKey("serverHostedSession.command.unknownSubcommand");

    fixture.execute(console, "create", "extra");
    fixture.assertLastKey("serverHostedSession.command.unexpectedArguments");

    fixture.execute(console, "start", "extra");
    fixture.assertLastKey("serverHostedSession.command.unexpectedArguments");

    fixture.execute(console, "stop", "extra");
    fixture.assertLastKey("serverHostedSession.command.unexpectedArguments");

    assertEquals(ServerHostedSession.State.IDLE, fixture.session.state());
  }

  @Test
  void tabCompletionExposesAdministrativeCommandsOnlyToAdministratorsAndFiltersPrefixes() {
    Fixture fixture = new Fixture();
    CommandSender player = fixture.console(false);
    CommandSender administrator = fixture.console(true);

    assertEquals(
        List.of("join", "leave", "status"),
        fixture.complete(player, "")
    );
    assertEquals(List.of("join"), fixture.complete(player, "j"));
    assertEquals(
        List.of("create", "join", "leave", "start", "stop", "status"),
        fixture.complete(administrator, "")
    );
    assertEquals(List.of("start", "stop", "status"), fixture.complete(administrator, "st"));
    assertEquals(List.of("status"), fixture.complete(player, "st"));
    assertEquals(List.of(), fixture.complete(player, "sto"));
    assertEquals(List.of(), fixture.complete(administrator, "x"));
    assertEquals(
        List.of(),
        fixture.adapter.onTabComplete(
            administrator,
            fixture.command,
            "treasurerun",
            new String[]{"status", "extra"}
        )
    );
  }


  @Test
  void administratorStartLocksTwoPlayerRosterWithoutStartingGameplay() {
    Fixture fixture = new Fixture();
    CommandSender administrator = fixture.console(true);
    Player first = fixture.player(false);
    Player second = fixture.player(false);

    fixture.execute(administrator, "create");
    fixture.execute(first, "join");
    fixture.execute(second, "join");
    fixture.execute(administrator, "start");

    assertEquals(ServerHostedSession.State.LOCKED, fixture.session.state());
    assertEquals(
        List.of(first.getUniqueId(), second.getUniqueId()),
        fixture.session.participants()
    );
    fixture.assertLastKey("serverHostedSession.command.startRosterLocked");
    assertEquals("2", fixture.lastInvocation().placeholders().get("{players}"));
    assertEquals("8", fixture.lastInvocation().placeholders().get("{max}"));
  }

  @Test
  void nonAdministratorStartIsRejectedWithoutMutation() {
    Fixture fixture = new Fixture();
    CommandSender administrator = fixture.console(true);
    Player participant = fixture.player(false);

    fixture.execute(administrator, "create");
    fixture.execute(participant, "join");
    fixture.execute(participant, "start");

    assertEquals(ServerHostedSession.State.WAITING, fixture.session.state());
    assertEquals(List.of(participant.getUniqueId()), fixture.session.participants());
    fixture.assertLastKey("serverHostedSession.command.startAdminRequired");
  }

  @Test
  void onePlayerStartReportsTooFewPlayersAndRemainsWaiting() {
    Fixture fixture = new Fixture();
    CommandSender administrator = fixture.console(true);
    Player participant = fixture.player(false);

    fixture.execute(administrator, "create");
    fixture.execute(participant, "join");
    fixture.execute(administrator, "start");

    assertEquals(ServerHostedSession.State.WAITING, fixture.session.state());
    assertEquals(List.of(participant.getUniqueId()), fixture.session.participants());
    fixture.assertLastKey("serverHostedSession.command.startTooFewPlayers");
    assertEquals("2", fixture.lastInvocation().placeholders().get("{min}"));
  }

  @Test
  void repeatedStartReportsSessionNotWaitingAndPreservesLockedRoster() {
    Fixture fixture = new Fixture();
    CommandSender administrator = fixture.console(true);
    Player first = fixture.player(false);
    Player second = fixture.player(false);

    fixture.execute(administrator, "create");
    fixture.execute(first, "join");
    fixture.execute(second, "join");
    fixture.execute(administrator, "start");
    List<UUID> locked = fixture.session.participants();
    fixture.execute(administrator, "start");

    assertEquals(ServerHostedSession.State.LOCKED, fixture.session.state());
    assertEquals(locked, fixture.session.participants());
    fixture.assertLastKey("serverHostedSession.command.startSessionNotWaiting");
  }

  @Test
  void administratorWaitingStopResetsDirectlyToIdle() {
    Fixture fixture = new Fixture();
    CommandSender administrator = fixture.console(true);
    Player participant = fixture.player(false);

    fixture.execute(administrator, "create");
    fixture.execute(participant, "join");
    fixture.execute(administrator, "stop");

    assertEquals(ServerHostedSession.State.IDLE, fixture.session.state());
    assertEquals(List.of(), fixture.session.participants());
    fixture.assertLastKey("serverHostedSession.command.stopWaitingReset");
  }

  @Test
  void administratorLockedStopRequiresCleanupAndPreservesRoster() {
    Fixture fixture = new Fixture();
    CommandSender administrator = fixture.console(true);
    Player first = fixture.player(false);
    Player second = fixture.player(false);

    fixture.execute(administrator, "create");
    fixture.execute(first, "join");
    fixture.execute(second, "join");
    fixture.execute(administrator, "start");
    List<UUID> locked = fixture.session.participants();
    fixture.execute(administrator, "stop");

    assertEquals(ServerHostedSession.State.LOCKED, fixture.session.state());
    assertEquals(locked, fixture.session.participants());
    fixture.assertLastKey("serverHostedSession.command.stopCleanupRequired");
  }

  @Test
  void nonAdministratorStopIsRejectedWithoutMutation() {
    Fixture fixture = new Fixture();
    CommandSender administrator = fixture.console(true);
    Player participant = fixture.player(false);

    fixture.execute(administrator, "create");
    fixture.execute(participant, "join");
    fixture.execute(participant, "stop");

    assertEquals(ServerHostedSession.State.WAITING, fixture.session.state());
    assertEquals(List.of(participant.getUniqueId()), fixture.session.participants());
    fixture.assertLastKey("serverHostedSession.command.stopAdminRequired");
  }

  @Test
  void idleStopReportsNoActiveSession() {
    Fixture fixture = new Fixture();
    CommandSender administrator = fixture.console(true);

    fixture.execute(administrator, "stop");

    assertEquals(ServerHostedSession.State.IDLE, fixture.session.state());
    assertEquals(List.of(), fixture.session.participants());
    fixture.assertLastKey("serverHostedSession.command.stopNoActiveSession");
  }

  @Test
  void blankLanguageFallsBackToEnglishAndEachCommandSendsOneMessage() {
    Fixture fixture = new Fixture(sender -> " ");
    CommandSender console = fixture.console(true);

    fixture.execute(console, "status");

    assertEquals("en", fixture.lastInvocation().language());
    verify(console).sendMessage(anyString());
  }

  private record Invocation(
      String language,
      String key,
      Map<String, String> placeholders
  ) {}

  private static final class Fixture {
    private final ServerHostedSession session = new ServerHostedSession();
    private final List<Invocation> invocations = new ArrayList<>();
    private final Command command = mock(Command.class);
    private final ServerHostedSessionCommandAdapter adapter;

    private Fixture() {
      this(sender -> sender instanceof Player ? "ja" : "en");
    }

    private Fixture(
        java.util.function.Function<CommandSender, String> languageResolver
    ) {
      adapter = new ServerHostedSessionCommandAdapter(
          new ServerHostedSessionCommandService(session),
          new ServerHostedSessionControlService(session),
          languageResolver,
          (language, key, placeholders) -> {
            invocations.add(new Invocation(language, key, placeholders));
            return key;
          }
      );
    }

    private Player player(boolean administrator) {
      Player player = mock(Player.class);
      when(player.getUniqueId()).thenReturn(UUID.randomUUID());
      when(player.hasPermission(
          ServerHostedSessionCommandAdapter.ADMIN_PERMISSION
      )).thenReturn(administrator);
      return player;
    }

    private CommandSender console(boolean administrator) {
      CommandSender sender = mock(CommandSender.class);
      when(sender.hasPermission(
          ServerHostedSessionCommandAdapter.ADMIN_PERMISSION
      )).thenReturn(administrator);
      return sender;
    }

    private boolean execute(CommandSender sender, String... arguments) {
      return adapter.onCommand(
          sender,
          command,
          "treasurerun",
          arguments
      );
    }

    private boolean executeNullArguments(CommandSender sender) {
      return adapter.onCommand(
          sender,
          command,
          "treasurerun",
          null
      );
    }

    private List<String> complete(CommandSender sender, String prefix) {
      return adapter.onTabComplete(
          sender,
          command,
          "treasurerun",
          new String[]{prefix}
      );
    }

    private Invocation lastInvocation() {
      return invocations.get(invocations.size() - 1);
    }

    private void assertLastKey(String expected) {
      assertEquals(expected, lastInvocation().key());
    }
  }
}
