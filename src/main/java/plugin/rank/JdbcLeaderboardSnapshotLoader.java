package plugin.rank;

import plugin.DatabaseRuntimeSettings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded JDBC leaderboard loader.
 *
 * <p>Every load owns a new short-lived connection and never reuses the
 * plugin's shared mutable Connection field. Callers must keep this blocking
 * boundary off the Minecraft main thread.</p>
 */
public final class JdbcLeaderboardSnapshotLoader
    implements LeaderboardSnapshotRefresher.Loader {

    static final int CONNECT_TIMEOUT_MILLIS = 3_000;
    static final int SOCKET_TIMEOUT_MILLIS = 5_000;
    static final int QUERY_TIMEOUT_SECONDS = 4;

    private final DatabaseRuntimeSettings settings;

    public JdbcLeaderboardSnapshotLoader(DatabaseRuntimeSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public LeaderboardSnapshot load() throws Exception {
        return toSnapshot(loadEntries());
    }

    /**
     * Existing all-time boundary used by PlaceholderAPI and /treasurerun top.
     */
    public List<RankingQueryService.RankingEntry> loadEntries() throws Exception {
        if (!settings.enabled()) {
            return List.of();
        }

        Class.forName("com.mysql.cj.jdbc.Driver");

        try (Connection connection = openConnection()) {
            return RankingQueryService.loadAllTime(
                connection,
                QUERY_TIMEOUT_SECONDS
            );
        }
    }

    /**
     * Bounded ranking read for RealtimeRankTicker.
     */
    public List<RankingQueryService.RankingEntry> loadEntries(
        RankingQueryService.Window window
    ) throws Exception {
        Objects.requireNonNull(window, "window");

        if (!settings.enabled()) {
            return List.of();
        }

        Class.forName("com.mysql.cj.jdbc.Driver");

        try (Connection connection = openConnection()) {
            return RankingQueryService.load(
                connection,
                window,
                QUERY_TIMEOUT_SECONDS
            );
        }
    }

    /**
     * Bounded run-rank lookup. Callers must keep this method off the
     * Minecraft main thread.
     */
    public int loadRunRank(
        String playerName,
        int score,
        long timeSec,
        String difficulty
    ) throws Exception {
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(difficulty, "difficulty");

        if (!settings.enabled()) {
            return -1;
        }

        Class.forName("com.mysql.cj.jdbc.Driver");

        try (Connection connection = openConnection()) {
            return RankingQueryService.findRunRank(
                connection,
                playerName,
                score,
                timeSec,
                difficulty,
                QUERY_TIMEOUT_SECONDS
            );
        }
    }

    private Connection openConnection() throws Exception {
        String url =
            "jdbc:mysql://"
                + settings.host()
                + ":"
                + settings.port()
                + "/"
                + settings.database()
                + "?useSSL=false"
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC"
                + "&connectTimeout="
                + CONNECT_TIMEOUT_MILLIS
                + "&socketTimeout="
                + SOCKET_TIMEOUT_MILLIS;

        return DriverManager.getConnection(
            url,
            settings.user(),
            settings.password()
        );
    }

    static LeaderboardSnapshot toSnapshot(
        List<RankingQueryService.RankingEntry> rankingEntries
    ) {
        Objects.requireNonNull(rankingEntries, "rankingEntries");

        int size = Math.min(
            LeaderboardSnapshot.MAX_RANK,
            rankingEntries.size()
        );

        List<LeaderboardSnapshot.Entry> displayEntries =
            new ArrayList<>(size);

        for (int index = 0; index < size; index++) {
            RankingQueryService.RankingEntry entry =
                Objects.requireNonNull(
                    rankingEntries.get(index),
                    "ranking entry"
                );

            displayEntries.add(
                new LeaderboardSnapshot.Entry(
                    entry.playerName() == null
                        ? "unknown"
                        : entry.playerName(),
                    Integer.toString(entry.score()),
                    Long.toString(entry.time())
                )
            );
        }

        return new LeaderboardSnapshot(displayEntries);
    }
}
