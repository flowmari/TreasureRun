package plugin.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LeaderboardSnapshotRefresherTest {

    @Test
    void successfulRefreshAtomicallyReplacesSnapshot() {
        LeaderboardSnapshotCache cache = new LeaderboardSnapshotCache();
        LeaderboardSnapshot expected =
                new LeaderboardSnapshot(
                        List.of(new LeaderboardSnapshot.Entry("Alice", "120", "42")));
        LeaderboardSnapshotRefresher refresher =
                new LeaderboardSnapshotRefresher(cache, () -> expected, ignored -> {});

        assertTrue(refresher.refresh());
        assertEquals(expected, cache.current());
    }

    @Test
    void failedRefreshRetainsLastSnapshotAndLogsOnlyFailureTransition() {
        LeaderboardSnapshot initial =
                new LeaderboardSnapshot(
                        List.of(new LeaderboardSnapshot.Entry("Old", "1", "99")));
        LeaderboardSnapshot recovered =
                new LeaderboardSnapshot(
                        List.of(new LeaderboardSnapshot.Entry("New", "2", "50")));
        LeaderboardSnapshotCache cache = new LeaderboardSnapshotCache();
        cache.publish(initial);
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger warnings = new AtomicInteger();

        LeaderboardSnapshotRefresher refresher =
                new LeaderboardSnapshotRefresher(
                        cache,
                        () -> {
                            int attempt = attempts.incrementAndGet();
                            if (attempt == 1 || attempt == 2 || attempt == 4) {
                                throw new IllegalStateException("database unavailable");
                            }
                            return recovered;
                        },
                        ignored -> warnings.incrementAndGet());

        assertTrue(refresher.refresh());
        assertEquals(initial, cache.current());
        assertEquals(1, warnings.get());
        assertTrue(refresher.refresh());
        assertEquals(initial, cache.current());
        assertEquals(1, warnings.get());
        assertTrue(refresher.refresh());
        assertEquals(recovered, cache.current());
        assertEquals(1, warnings.get());
        assertTrue(refresher.refresh());
        assertEquals(recovered, cache.current());
        assertEquals(2, warnings.get());
    }

    @Test
    void overlappingRefreshIsRejected() throws Exception {
        LeaderboardSnapshotCache cache = new LeaderboardSnapshotCache();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        LeaderboardSnapshotRefresher refresher =
                new LeaderboardSnapshotRefresher(
                        cache,
                        () -> {
                            loads.incrementAndGet();
                            entered.countDown();
                            if (!release.await(2, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("test loader timed out");
                            }
                            return LeaderboardSnapshot.empty();
                        },
                        ignored -> {});

        Thread worker = new Thread(refresher::refresh, "leaderboard-refresh-test");
        worker.start();
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        assertFalse(refresher.refresh());
        assertEquals(1, loads.get());
        release.countDown();
        worker.join(2_000L);
        assertFalse(worker.isAlive());
    }

    @Test
    void closeRejectsLatePublicationFromAlreadyRunningRefresh() throws Exception {
        LeaderboardSnapshot original =
                new LeaderboardSnapshot(
                        List.of(new LeaderboardSnapshot.Entry("Original", "10", "70")));
        LeaderboardSnapshot late =
                new LeaderboardSnapshot(
                        List.of(new LeaderboardSnapshot.Entry("Late", "999", "1")));
        LeaderboardSnapshotCache cache = new LeaderboardSnapshotCache();
        cache.publish(original);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        LeaderboardSnapshotRefresher refresher =
                new LeaderboardSnapshotRefresher(
                        cache,
                        () -> {
                            entered.countDown();
                            if (!release.await(2, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("test loader timed out");
                            }
                            return late;
                        },
                        ignored -> {});

        Thread worker = new Thread(refresher::refresh, "leaderboard-late-publish-test");
        worker.start();
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        refresher.close();
        release.countDown();
        worker.join(2_000L);
        assertFalse(worker.isAlive());
        assertEquals(original, cache.current());
        assertFalse(refresher.refresh());
    }
}
