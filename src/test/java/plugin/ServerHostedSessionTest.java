package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServerHostedSessionTest {

  @Test
  void createsExactlyOneWaitingSession() {
    ServerHostedSession session = new ServerHostedSession();

    assertEquals(ServerHostedSession.State.IDLE, session.state());
    assertTrue(session.create());
    assertEquals(ServerHostedSession.State.WAITING, session.state());
    assertFalse(session.create());
  }

  @Test
  void requiresTwoPlayersBeforeTheRosterCanLock() {
    ServerHostedSession session = new ServerHostedSession();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    assertTrue(session.create());
    assertFalse(session.canStart());

    assertEquals(
        ServerHostedSession.JoinResult.JOINED,
        session.join(first)
    );
    assertFalse(session.canStart());

    ServerHostedSession.StartResult tooFew =
        session.lockForStart();

    assertEquals(
        ServerHostedSession.StartStatus.TOO_FEW_PLAYERS,
        tooFew.status()
    );
    assertEquals(List.of(first), tooFew.participants());
    assertEquals(
        ServerHostedSession.State.WAITING,
        session.state()
    );

    assertEquals(
        ServerHostedSession.JoinResult.JOINED,
        session.join(second)
    );
    assertTrue(session.canStart());

    ServerHostedSession.StartResult locked =
        session.lockForStart();

    assertEquals(
        ServerHostedSession.StartStatus.LOCKED,
        locked.status()
    );
    assertEquals(List.of(first, second), locked.participants());
    assertEquals(
        ServerHostedSession.State.LOCKED,
        session.state()
    );
  }

  @Test
  void lockedRosterRejectsLateMembershipChanges() {
    ServerHostedSession session = new ServerHostedSession();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    UUID latePlayer = UUID.randomUUID();

    assertTrue(session.create());
    assertEquals(
        ServerHostedSession.JoinResult.JOINED,
        session.join(first)
    );
    assertEquals(
        ServerHostedSession.JoinResult.JOINED,
        session.join(second)
    );

    ServerHostedSession.StartResult locked =
        session.lockForStart();

    assertEquals(
        ServerHostedSession.StartStatus.LOCKED,
        locked.status()
    );
    assertEquals(
        ServerHostedSession.JoinResult.SESSION_NOT_WAITING,
        session.join(latePlayer)
    );
    assertEquals(
        ServerHostedSession.LeaveResult.SESSION_NOT_WAITING,
        session.leave(first)
    );
    assertEquals(List.of(first, second), session.participants());
  }

  @Test
  void rejectsDuplicateAndNinthPlayer() {
    ServerHostedSession session = new ServerHostedSession();
    assertTrue(session.create());

    List<UUID> players = new ArrayList<>();

    for (int i = 0;
         i < ServerHostedSession.MAX_PLAYERS;
         i++) {

      UUID player = UUID.randomUUID();
      players.add(player);

      assertEquals(
          ServerHostedSession.JoinResult.JOINED,
          session.join(player)
      );
    }

    assertEquals(
        ServerHostedSession.JoinResult.ALREADY_JOINED,
        session.join(players.get(0))
    );

    assertEquals(
        ServerHostedSession.JoinResult.SESSION_FULL,
        session.join(UUID.randomUUID())
    );

    assertEquals(
        ServerHostedSession.MAX_PLAYERS,
        session.playerCount()
    );
  }

  @Test
  void lastPlayerLeavingResetsTheWaitingSession() {
    ServerHostedSession session = new ServerHostedSession();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    assertTrue(session.create());
    assertEquals(
        ServerHostedSession.JoinResult.JOINED,
        session.join(first)
    );
    assertEquals(
        ServerHostedSession.JoinResult.JOINED,
        session.join(second)
    );

    assertEquals(
        ServerHostedSession.LeaveResult.LEFT,
        session.leave(first)
    );
    assertEquals(
        ServerHostedSession.State.WAITING,
        session.state()
    );

    assertEquals(
        ServerHostedSession.LeaveResult.LEFT_AND_SESSION_RESET,
        session.leave(second)
    );
    assertEquals(
        ServerHostedSession.State.IDLE,
        session.state()
    );
    assertTrue(session.participants().isEmpty());
  }

  @Test
  void resetAndInvalidInputsFailClosed() {
    ServerHostedSession session = new ServerHostedSession();
    UUID player = UUID.randomUUID();

    assertEquals(
        ServerHostedSession.JoinResult.SESSION_NOT_WAITING,
        session.join(player)
    );
    assertEquals(
        ServerHostedSession.LeaveResult.SESSION_NOT_WAITING,
        session.leave(player)
    );

    assertThrows(
        NullPointerException.class,
        () -> session.join(null)
    );
    assertThrows(
        NullPointerException.class,
        () -> session.leave(null)
    );
    assertThrows(
        NullPointerException.class,
        () -> session.contains(null)
    );

    assertTrue(session.create());
    assertEquals(
        ServerHostedSession.JoinResult.JOINED,
        session.join(player)
    );

    session.reset();

    assertEquals(
        ServerHostedSession.State.IDLE,
        session.state()
    );
    assertEquals(0, session.playerCount());
    assertFalse(session.canStart());
  }
}
