package plugin.rank;

import plugin.DatabaseRuntimeSettings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Refresh-only JDBC loader. Every load owns a new short-lived connection and never uses the
 * plugin's shared mutable Connection field.
 *
 * <p>The Connector/J connect and socket timeouts bound driver network operations. The explicit
 * JDBC statement timeout below bounds the leaderboard SELECT itself. The refresh cadence is
 * scheduling only and must not be described as a deadline.
 */
public final class JdbcLeaderboardSnapshotLoader implements LeaderboardSnapshotRefresher.Loader {

    static final int CONNECT_TIMEOUT_MILLIS = 3_000;
    static final int SOCKET_TIMEOUT_MILLIS = 5_000;
    static final int QUERY_TIMEOUT_SECONDS = 4;

    private final DatabaseRuntimeSettings settings;

    public JdbcLeaderboardSnapshotLoader(DatabaseRuntimeSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public LeaderboardSnapshot load() throws Exception {
        if (!settings.enabled()) {
            return LeaderboardSnapshot.empty();
        }

        Class.forName("com.mysql.cj.jdbc.Driver");
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

        try (Connection connection =
                DriverManager.getConnection(url, settings.user(), settings.password())) {
            return toSnapshot(
                    RankingQueryService.loadAllTime(connection, QUERY_TIMEOUT_SECONDS));
        }
    }

    static LeaderboardSnapshot toSnapshot(List<RankingQueryService.RankingEntry> rankingEntries) {
        Objects.requireNonNull(rankingEntries, "rankingEntries");
        int size = Math.min(LeaderboardSnapshot.MAX_RANK, rankingEntries.size());
        List<LeaderboardSnapshot.Entry> displayEntries = new ArrayList<>(size);

        for (int index = 0; index < size; index++) {
            RankingQueryService.RankingEntry entry =
                    Objects.requireNonNull(rankingEntries.get(index), "ranking entry");
            displayEntries.add(
                    new LeaderboardSnapshot.Entry(
                            entry.playerName() == null ? "unknown" : entry.playerName(),
                            Integer.toString(entry.score()),
                            Long.toString(entry.time())));
        }
        return new LeaderboardSnapshot(displayEntries);
    }
}
