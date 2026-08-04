package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServerHostedSessionCommandServiceTest {

  @Test
  void rejectsInvalidArgumentShapesWithoutMutatingTheSession() {
    ServerHostedSession session = new ServerHostedSession();
    ServerHostedSessionCommandService service =
        new ServerHostedSessionCommandService(session);
    ServerHostedSessionCommandService.Actor actor =
        ServerHostedSessionCommandService.Actor.console(true);

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.MISSING_SUBCOMMAND,
        service.execute(actor, List.of()).code()
    );
    assertEquals(
        ServerHostedSessionCommandService.ResultCode.UNKNOWN_SUBCOMMAND,
        service.execute(actor, List.of("start")).code()
    );
    assertEquals(
        ServerHostedSessionCommandService.ResultCode.UNEXPECTED_ARGUMENTS,
        service.execute(actor, List.of("status", "extra")).code()
    );
    assertEquals(ServerHostedSession.State.IDLE, session.state());
    assertEquals(0, session.playerCount());
  }

  @Test
  void createRequiresAdministrationAndAllowsTheConsole() {
    ServerHostedSession session = new ServerHostedSession();
    ServerHostedSessionCommandService service =
        new ServerHostedSessionCommandService(session);

    ServerHostedSessionCommandService.Result forbidden =
        service.execute(
            ServerHostedSessionCommandService.Actor.console(false),
            List.of("create")
        );

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.ADMIN_REQUIRED,
        forbidden.code()
    );
    assertEquals(ServerHostedSession.State.IDLE, forbidden.snapshot().state());

    ServerHostedSessionCommandService.Result created =
        service.execute(
            ServerHostedSessionCommandService.Actor.console(true),
            List.of("CREATE")
        );

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.CREATED,
        created.code()
    );
    assertEquals(
        ServerHostedSession.State.WAITING,
        created.snapshot().state()
    );

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.SESSION_ALREADY_EXISTS,
        service.execute(
            ServerHostedSessionCommandService.Actor.console(true),
            List.of("create")
        ).code()
    );
  }

  @Test
  void creatingASessionDoesNotAutomaticallyJoinTheCreator() {
    ServerHostedSession session = new ServerHostedSession();
    ServerHostedSessionCommandService service =
        new ServerHostedSessionCommandService(session);
    UUID creator = UUID.randomUUID();

    ServerHostedSessionCommandService.Result result =
        service.execute(
            ServerHostedSessionCommandService.Actor.player(creator, true),
            List.of("create")
        );

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.CREATED,
        result.code()
    );
    assertEquals(0, result.snapshot().playerCount());
    assertTrue(result.snapshot().participants().isEmpty());
    assertFalse(session.contains(creator));
  }

  @Test
  void joinAndLeaveRequireAPlayerActor() {
    ServerHostedSession session = new ServerHostedSession();
    ServerHostedSessionCommandService service =
        new ServerHostedSessionCommandService(session);
    ServerHostedSessionCommandService.Actor console =
        ServerHostedSessionCommandService.Actor.console(true);

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.CREATED,
        service.execute(console, List.of("create")).code()
    );
    assertEquals(
        ServerHostedSessionCommandService.ResultCode.PLAYER_REQUIRED,
        service.execute(console, List.of("join")).code()
    );
    assertEquals(
        ServerHostedSessionCommandService.ResultCode.PLAYER_REQUIRED,
        service.execute(console, List.of("leave")).code()
    );
    assertEquals(0, session.playerCount());
  }

  @Test
  void mapsJoinResultsAndPreservesParticipantOrder() {
    ServerHostedSession session = new ServerHostedSession();
    ServerHostedSessionCommandService service =
        new ServerHostedSessionCommandService(session);
    ServerHostedSessionCommandService.Actor admin =
        ServerHostedSessionCommandService.Actor.console(true);
    List<UUID> players = new ArrayList<>();

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.SESSION_NOT_WAITING,
        service.execute(
            ServerHostedSessionCommandService.Actor.player(
                UUID.randomUUID(),
                false
            ),
            List.of("join")
        ).code()
    );

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.CREATED,
        service.execute(admin, List.of("create")).code()
    );

    for (int index = 0;
         index < ServerHostedSession.MAX_PLAYERS;
         index++) {
      UUID playerId = UUID.randomUUID();
      players.add(playerId);

      assertEquals(
          ServerHostedSessionCommandService.ResultCode.JOINED,
          service.execute(
              ServerHostedSessionCommandService.Actor.player(
                  playerId,
                  false
              ),
              List.of("join")
          ).code()
      );
    }

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.ALREADY_JOINED,
        service.execute(
            ServerHostedSessionCommandService.Actor.player(
                players.get(0),
                false
            ),
            List.of("join")
        ).code()
    );
    assertEquals(
        ServerHostedSessionCommandService.ResultCode.SESSION_FULL,
        service.execute(
            ServerHostedSessionCommandService.Actor.player(
                UUID.randomUUID(),
                false
            ),
            List.of("join")
        ).code()
    );

    ServerHostedSessionCommandService.Result status =
        service.execute(admin, List.of("status"));

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.STATUS,
        status.code()
    );
    assertEquals(players, status.snapshot().participants());
    assertEquals(ServerHostedSession.MAX_PLAYERS, status.snapshot().playerCount());
    assertTrue(status.snapshot().canStart());
  }

  @Test
  void mapsLeaveResultsAndResetsWhenTheLastPlayerLeaves() {
    ServerHostedSession session = new ServerHostedSession();
    ServerHostedSessionCommandService service =
        new ServerHostedSessionCommandService(session);
    ServerHostedSessionCommandService.Actor admin =
        ServerHostedSessionCommandService.Actor.console(true);
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    UUID absent = UUID.randomUUID();

    service.execute(admin, List.of("create"));
    service.execute(
        ServerHostedSessionCommandService.Actor.player(first, false),
        List.of("join")
    );
    service.execute(
        ServerHostedSessionCommandService.Actor.player(second, false),
        List.of("join")
    );

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.NOT_JOINED,
        service.execute(
            ServerHostedSessionCommandService.Actor.player(absent, false),
            List.of("leave")
        ).code()
    );
    assertEquals(
        ServerHostedSessionCommandService.ResultCode.LEFT,
        service.execute(
            ServerHostedSessionCommandService.Actor.player(first, false),
            List.of("leave")
        ).code()
    );

    ServerHostedSessionCommandService.Result finalLeave =
        service.execute(
            ServerHostedSessionCommandService.Actor.player(second, false),
            List.of("leave")
        );

    assertEquals(
        ServerHostedSessionCommandService.ResultCode.LEFT_AND_SESSION_RESET,
        finalLeave.code()
    );
    assertEquals(ServerHostedSession.State.IDLE, finalLeave.snapshot().state());
    assertTrue(finalLeave.snapshot().participants().isEmpty());
  }

  @Test
  void reportsLockedStateAndRejectsMembershipChanges() {
    ServerHostedSession session = new ServerHostedSession();
    ServerHostedSessionCommandService service =
        new ServerHostedSessionCommandService(session);
    ServerHostedSessionCommandService.Actor admin =
        ServerHostedSessionCommandService.Actor.console(true);
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    service.execute(admin, List.of("create"));
    service.execute(
        ServerHostedSessionCommandService.Actor.player(first, false),
        List.of("join")
    );
    service.execute(
        ServerHostedSessionCommandService.Actor.player(second, false),
        List.of("join")
    );

    assertEquals(
        ServerHostedSession.StartStatus.LOCKED,
        session.lockForStart().status()
    );
    assertEquals(
        ServerHostedSessionCommandService.ResultCode.SESSION_NOT_WAITING,
        service.execute(
            ServerHostedSessionCommandService.Actor.player(
                UUID.randomUUID(),
                false
            ),
            List.of("join")
        ).code()
    );
    assertEquals(
        ServerHostedSessionCommandService.ResultCode.SESSION_NOT_WAITING,
        service.execute(
            ServerHostedSessionCommandService.Actor.player(first, false),
            List.of("leave")
        ).code()
    );

    ServerHostedSessionCommandService.Result status =
        service.execute(admin, List.of("status"));

    assertEquals(ServerHostedSession.State.LOCKED, status.snapshot().state());
    assertEquals(List.of(first, second), status.snapshot().participants());
  }

  @Test
  void snapshotsAreImmutableAndInvalidInputsFailClosed() {
    ServerHostedSession session = new ServerHostedSession();
    ServerHostedSessionCommandService service =
        new ServerHostedSessionCommandService(session);
    UUID playerId = UUID.randomUUID();
    ServerHostedSessionCommandService.Actor actor =
        ServerHostedSessionCommandService.Actor.player(playerId, false);

    assertThrows(
        NullPointerException.class,
        () -> new ServerHostedSessionCommandService(null)
    );
    assertThrows(
        NullPointerException.class,
        () -> ServerHostedSessionCommandService.Actor.player(null, false)
    );
    assertThrows(
        NullPointerException.class,
        () -> new ServerHostedSessionCommandService.Actor(null, false)
    );
    assertThrows(
        NullPointerException.class,
        () -> service.execute(null, List.of("status"))
    );
    assertThrows(
        NullPointerException.class,
        () -> service.execute(actor, null)
    );
    assertThrows(
        NullPointerException.class,
        () -> service.execute(
            actor,
            java.util.Arrays.asList("status", null)
        )
    );

    ServerHostedSessionCommandService.Snapshot snapshot =
        service.snapshot();

    assertEquals(Optional.of(playerId), actor.playerId());
    assertEquals(ServerHostedSession.MIN_PLAYERS, snapshot.minimumPlayers());
    assertEquals(ServerHostedSession.MAX_PLAYERS, snapshot.maximumPlayers());
    assertThrows(
        UnsupportedOperationException.class,
        () -> snapshot.participants().add(UUID.randomUUID())
    );
  }
}
