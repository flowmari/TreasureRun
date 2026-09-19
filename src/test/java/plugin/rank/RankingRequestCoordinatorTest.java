package plugin.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RankingRequestCoordinatorTest {

    @Test
    void samePlayerSpamJoinsOnlyOnceAndOneFlightCompletesOnce() {
        RankingRequestCoordinator coordinator = new RankingRequestCoordinator();
        UUID player = UUID.randomUUID();
        coordinator.activate();
        var first = coordinator.request(player);
        var duplicate = coordinator.request(player);
        assertTrue(first.lifecycleActive());
        assertTrue(first.waiterAdded());
        assertTrue(first.flightToStart() != null);
        assertTrue(duplicate.lifecycleActive());
        assertFalse(duplicate.waiterAdded());
        assertNull(duplicate.flightToStart());
        var completion = coordinator.complete(first.flightToStart());
        assertTrue(completion.authoritative());
        assertEquals(List.of(player), completion.waiters());
        var duplicateCompletion = coordinator.complete(first.flightToStart());
        assertFalse(duplicateCompletion.authoritative());
        assertTrue(duplicateCompletion.waiters().isEmpty());
    }

    @Test
    void twoPlayersShareOneFlightAndAreDeliveredTogether() {
        RankingRequestCoordinator coordinator = new RankingRequestCoordinator();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        coordinator.activate();
        var first = coordinator.request(firstPlayer);
        var second = coordinator.request(secondPlayer);
        assertTrue(first.flightToStart() != null);
        assertTrue(second.waiterAdded());
        assertNull(second.flightToStart());
        var completion = coordinator.complete(first.flightToStart());
        assertTrue(completion.authoritative());
        assertEquals(List.of(firstPlayer, secondPlayer), completion.waiters());
    }

    @Test
    void abortReleasesAuthorityAndAllowsCleanRetry() {
        RankingRequestCoordinator coordinator = new RankingRequestCoordinator();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        UUID retryPlayer = UUID.randomUUID();
        coordinator.activate();
        var first = coordinator.request(firstPlayer);
        coordinator.request(secondPlayer);
        var aborted = coordinator.abort(first.flightToStart());
        assertTrue(aborted.authoritative());
        assertEquals(List.of(firstPlayer, secondPlayer), aborted.waiters());
        var retry = coordinator.request(retryPlayer);
        assertTrue(retry.flightToStart() != null);
        assertFalse(first.flightToStart().equals(retry.flightToStart()));
        var staleOldCompletion = coordinator.complete(first.flightToStart());
        assertFalse(staleOldCompletion.authoritative());
        assertTrue(coordinator.isAuthoritative(retry.flightToStart()));
        var retryCompletion = coordinator.complete(retry.flightToStart());
        assertTrue(retryCompletion.authoritative());
        assertEquals(List.of(retryPlayer), retryCompletion.waiters());
    }

    @Test
    void disableThenReenableRejectsOldCompletionWithoutTouchingNewFlight() {
        RankingRequestCoordinator coordinator = new RankingRequestCoordinator();
        UUID oldPlayer = UUID.randomUUID();
        UUID newPlayer = UUID.randomUUID();
        coordinator.activate();
        var oldRequest = coordinator.request(oldPlayer);
        coordinator.deactivate();
        coordinator.activate();
        var newRequest = coordinator.request(newPlayer);
        assertTrue(newRequest.flightToStart() != null);
        assertFalse(oldRequest.flightToStart().equals(newRequest.flightToStart()));
        var oldCompletion = coordinator.complete(oldRequest.flightToStart());
        assertFalse(oldCompletion.authoritative());
        assertTrue(oldCompletion.waiters().isEmpty());
        assertTrue(coordinator.isAuthoritative(newRequest.flightToStart()));
        var newCompletion = coordinator.complete(newRequest.flightToStart());
        assertTrue(newCompletion.authoritative());
        assertEquals(List.of(newPlayer), newCompletion.waiters());
    }

    @Test
    void duplicateOldCompletionCannotDrainAYoungerFlight() {
        RankingRequestCoordinator coordinator = new RankingRequestCoordinator();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        coordinator.activate();
        var first = coordinator.request(firstPlayer);
        assertTrue(coordinator.complete(first.flightToStart()).authoritative());
        var second = coordinator.request(secondPlayer);
        assertTrue(second.flightToStart() != null);
        var duplicateOld = coordinator.complete(first.flightToStart());
        assertFalse(duplicateOld.authoritative());
        assertTrue(coordinator.isAuthoritative(second.flightToStart()));
        var secondCompletion = coordinator.complete(second.flightToStart());
        assertTrue(secondCompletion.authoritative());
        assertEquals(List.of(secondPlayer), secondCompletion.waiters());
    }

    @Test
    void inactiveLifecycleRejectsRequestsAndNeverCreatesAuthority() {
        RankingRequestCoordinator coordinator = new RankingRequestCoordinator();
        UUID player = UUID.randomUUID();
        var inactive = coordinator.request(player);
        assertFalse(inactive.lifecycleActive());
        assertFalse(inactive.waiterAdded());
        assertNull(inactive.flightToStart());
        coordinator.activate();
        coordinator.deactivate();
        var disabled = coordinator.request(player);
        assertFalse(disabled.lifecycleActive());
        assertFalse(disabled.waiterAdded());
        assertNull(disabled.flightToStart());
    }
}
