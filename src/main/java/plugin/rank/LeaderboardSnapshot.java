package plugin.rank;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, display-ready leaderboard state.
 *
 * <p>This type deliberately has no Bukkit, PlaceholderAPI, JDBC, HTTP, or file-I/O dependency.
 * Ranking position is represented by list order: index 0 is rank 1.
 */
public record LeaderboardSnapshot(List<Entry> entries) {

    public static final int MAX_RANK = 10;

    public LeaderboardSnapshot {
        Objects.requireNonNull(entries, "entries");

        if (entries.size() > MAX_RANK) {
            throw new IllegalArgumentException(
                    "Leaderboard snapshot cannot contain more than " + MAX_RANK + " entries");
        }

        entries = List.copyOf(entries);
    }

    public static LeaderboardSnapshot empty() {
        return new LeaderboardSnapshot(List.of());
    }

    public Optional<Entry> entryAt(int rank) {
        if (rank < 1 || rank > MAX_RANK) {
            return Optional.empty();
        }

        int index = rank - 1;

        if (index >= entries.size()) {
            return Optional.empty();
        }

        return Optional.of(entries.get(index));
    }

    /**
     * Display-ready values published as one immutable unit.
     *
     * <p>The future DB adapter is responsible for converting database/domain values into these
     * strings before publication. Placeholder callbacks must not perform name lookup or formatting
     * through Bukkit.
     */
    public record Entry(String player, String score, String time) {

        public Entry {
            player = Objects.requireNonNull(player, "player");
            score = Objects.requireNonNull(score, "score");
            time = Objects.requireNonNull(time, "time");
        }
    }
}
