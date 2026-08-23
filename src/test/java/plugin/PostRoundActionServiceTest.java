package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostRoundActionServiceTest {

  @Test
  void onlyOfferedParticipantsCanCreateAndJoinTheNextWaitingSession() {
    ServerHostedSession session = new ServerHostedSession();
    PostRoundActionService service = new PostRoundActionService(session);
    UUID offered = UUID.randomUUID();
    UUID outsider = UUID.randomUUID();

    service.offer(List.of(offered));

    assertEquals(
        PostRoundActionService.Code.NOT_ELIGIBLE,
        service.playAgain(outsider).code()
    );
    assertEquals(ServerHostedSession.State.IDLE, session.state());

    PostRoundActionService.Result joined = service.playAgain(offered);
    assertEquals(PostRoundActionService.Code.JOINED, joined.code());
    assertEquals(ServerHostedSession.State.WAITING, session.state());
    assertEquals(List.of(offered), session.participants());
    assertFalse(service.isEligible(offered));
    assertEquals(
        PostRoundActionService.Code.NOT_ELIGIBLE,
        service.playAgain(offered).code()
    );
  }

  @Test
  void revokedParticipantCannotUseStaleReplayEligibility() {
    ServerHostedSession session = new ServerHostedSession();
    PostRoundActionService service = new PostRoundActionService(session);
    UUID participant = UUID.randomUUID();

    service.offer(List.of(participant));
    assertTrue(service.isEligible(participant));

    service.revoke(participant);

    assertFalse(service.isEligible(participant));
    assertEquals(
        PostRoundActionService.Code.NOT_ELIGIBLE,
        service.playAgain(participant).code()
    );
    assertEquals(ServerHostedSession.State.IDLE, session.state());
  }

  @Test
  void replayCandidatesShareOneWaitingSessionRatherThanCompetingSessions() {
    ServerHostedSession session = new ServerHostedSession();
    PostRoundActionService service = new PostRoundActionService(session);
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    service.offer(List.of(first, second));

    assertEquals(PostRoundActionService.Code.JOINED, service.playAgain(first).code());
    assertEquals(PostRoundActionService.Code.JOINED, service.playAgain(second).code());

    assertEquals(ServerHostedSession.State.WAITING, session.state());
    assertEquals(List.of(first, second), session.participants());
    assertEquals(0, service.eligibleCount());
  }

  @Test
  void replayDoesNotBypassAnAlreadyLockedSession() {
    ServerHostedSession session = new ServerHostedSession();
    PostRoundActionService service = new PostRoundActionService(session);
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    assertTrue(session.create());
    assertEquals(ServerHostedSession.JoinResult.JOINED, session.join(first));
    assertEquals(ServerHostedSession.JoinResult.JOINED, session.join(second));
    assertEquals(ServerHostedSession.StartStatus.LOCKED, session.lockForStart().status());

    UUID replay = UUID.randomUUID();
    service.offer(List.of(replay));

    assertEquals(
        PostRoundActionService.Code.SESSION_NOT_AVAILABLE,
        service.playAgain(replay).code()
    );
    assertTrue(service.isEligible(replay));
    assertFalse(session.contains(replay));
  }

  @Test
  void newOfferReplacesStaleEligibility() {
    ServerHostedSession session = new ServerHostedSession();
    PostRoundActionService service = new PostRoundActionService(session);
    UUID oldPlayer = UUID.randomUUID();
    UUID newPlayer = UUID.randomUUID();

    service.offer(List.of(oldPlayer));
    service.offer(List.of(newPlayer));

    assertFalse(service.isEligible(oldPlayer));
    assertTrue(service.isEligible(newPlayer));
    assertEquals(1, service.eligibleCount());
  }
}
