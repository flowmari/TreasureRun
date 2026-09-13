package plugin.rank;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Coordinates one-at-a-time leaderboard refresh and fail-closed publication. */
public final class LeaderboardSnapshotRefresher implements AutoCloseable {

    @FunctionalInterface
    public interface Loader {
        LeaderboardSnapshot load() throws Exception;
    }

    private final LeaderboardSnapshotCache cache;
    private final Loader loader;
    private final Consumer<Exception> failureListener;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final AtomicBoolean failureActive = new AtomicBoolean(false);
    private final AtomicLong generation = new AtomicLong(0L);

    public LeaderboardSnapshotRefresher(
            LeaderboardSnapshotCache cache,
            Loader loader,
            Consumer<Exception> failureListener) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.failureListener = Objects.requireNonNull(failureListener, "failureListener");
    }

    /**
     * Attempts one refresh. The caller chooses the execution thread.
     *
     * @return true only when this call acquired the single in-flight refresh slot.
     */
    public boolean refresh() {
        if (!active.get() || !refreshInFlight.compareAndSet(false, true)) {
            return false;
        }

        long refreshGeneration = generation.get();
        try {
            LeaderboardSnapshot next =
                    Objects.requireNonNull(loader.load(), "leaderboard loader returned null");
            if (active.get() && generation.get() == refreshGeneration) {
                cache.publish(next);
                failureActive.set(false);
            }
        } catch (Exception exception) {
            if (active.get() && failureActive.compareAndSet(false, true)) {
                failureListener.accept(exception);
            }
        } finally {
            refreshInFlight.set(false);
        }
        return true;
    }

    @Override
    public void close() {
        active.set(false);
        generation.incrementAndGet();
    }
}
