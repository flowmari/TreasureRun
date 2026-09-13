package plugin.rank;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Atomically publishes a complete immutable leaderboard snapshot.
 *
 * <p>Each {@link #current()} call returns one complete immutable snapshot generation.
 * Separate calls may observe different generations if a refresh is published between them.
 */
public final class LeaderboardSnapshotCache {

    private final AtomicReference<LeaderboardSnapshot> current =
            new AtomicReference<>(LeaderboardSnapshot.empty());

    public LeaderboardSnapshot current() {
        return current.get();
    }

    public void publish(LeaderboardSnapshot snapshot) {
        current.set(Objects.requireNonNull(snapshot, "snapshot"));
    }
}
