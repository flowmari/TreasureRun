package plugin.rank;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure in-memory resolver for leaderboard placeholders.
 *
 * <p>Each resolution reads exactly one cached snapshot. Separate placeholder callbacks may
 * observe different generations if a refresh is published between those callbacks.
 *
 * <p>Accepted identifiers:
 * top_1_player
 * top_1_score
 * top_1_time
 *
 * <p>Ranks 1 through 10 are valid.
 * A valid but currently missing row resolves to the empty string.
 * Malformed identifiers resolve to null.
 */
public final class LeaderboardPlaceholderResolver {

    private final LeaderboardSnapshotCache cache;

    public LeaderboardPlaceholderResolver(LeaderboardSnapshotCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    public String resolve(String identifier) {
        ParsedIdentifier parsed = parse(identifier);

        if (parsed == null) {
            return null;
        }

        Optional<LeaderboardSnapshot.Entry> entry =
                cache.current().entryAt(parsed.rank());

        if (entry.isEmpty()) {
            return "";
        }

        return switch (parsed.field()) {
            case PLAYER -> entry.get().player();
            case SCORE -> entry.get().score();
            case TIME -> entry.get().time();
        };
    }

    private ParsedIdentifier parse(String identifier) {
        if (identifier == null) {
            return null;
        }

        String[] parts = identifier.split("_", -1);

        if (parts.length != 3 || !"top".equals(parts[0])) {
            return null;
        }

        int rank;

        try {
            rank = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {
            return null;
        }

        if (rank < 1 || rank > LeaderboardSnapshot.MAX_RANK) {
            return null;
        }

        Field field = switch (parts[2]) {
            case "player" -> Field.PLAYER;
            case "score" -> Field.SCORE;
            case "time" -> Field.TIME;
            default -> null;
        };

        if (field == null) {
            return null;
        }

        return new ParsedIdentifier(rank, field);
    }

    private record ParsedIdentifier(int rank, Field field) {}

    private enum Field {
        PLAYER,
        SCORE,
        TIME
    }
}
