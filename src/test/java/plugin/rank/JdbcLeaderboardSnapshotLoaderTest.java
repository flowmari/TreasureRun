package plugin.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcLeaderboardSnapshotLoaderTest {

    @Test
    void mappingPreservesExistingOrderingAndBoundsSnapshotToTopTen() {
        List<RankingQueryService.RankingEntry> entries = new ArrayList<>();
        for (int rank = 1; rank <= 12; rank++) {
            entries.add(
                    new RankingQueryService.RankingEntry(
                            "Player" + rank, 1_000 - rank, 40L + rank, "Normal", "en"));
        }

        LeaderboardSnapshot snapshot = JdbcLeaderboardSnapshotLoader.toSnapshot(entries);
        assertEquals(10, snapshot.entries().size());
        assertEquals("Player1", snapshot.entryAt(1).orElseThrow().player());
        assertEquals("999", snapshot.entryAt(1).orElseThrow().score());
        assertEquals("41", snapshot.entryAt(1).orElseThrow().time());
        assertEquals("Player10", snapshot.entryAt(10).orElseThrow().player());
    }

    @Test
    void mappingUsesExistingUnknownPlayerFallbackWithoutBukkitLookup() {
        List<RankingQueryService.RankingEntry> entries =
                List.of(new RankingQueryService.RankingEntry(null, 7, 88L, "Hard", "en"));
        LeaderboardSnapshot snapshot = JdbcLeaderboardSnapshotLoader.toSnapshot(entries);
        assertEquals("unknown", snapshot.entryAt(1).orElseThrow().player());
        assertEquals("7", snapshot.entryAt(1).orElseThrow().score());
        assertEquals("88", snapshot.entryAt(1).orElseThrow().time());
    }
}
