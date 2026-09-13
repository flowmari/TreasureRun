package plugin.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeaderboardPlaceholderResolverTest {

    @Test
    void emptyCacheReturnsEmptyStringForValidMissingRows() {
        LeaderboardSnapshotCache cache = new LeaderboardSnapshotCache();
        LeaderboardPlaceholderResolver resolver = new LeaderboardPlaceholderResolver(cache);

        assertEquals("", resolver.resolve("top_1_player"));
        assertEquals("", resolver.resolve("top_10_score"));
        assertEquals("", resolver.resolve("top_7_time"));
    }

    @Test
    void resolvesPlayerScoreAndTimeFromOnePublishedSnapshot() {
        LeaderboardSnapshotCache cache = new LeaderboardSnapshotCache();
        LeaderboardPlaceholderResolver resolver = new LeaderboardPlaceholderResolver(cache);

        cache.publish(
                new LeaderboardSnapshot(
                        List.of(
                                new LeaderboardSnapshot.Entry("Alice", "120", "42s"),
                                new LeaderboardSnapshot.Entry("Bob", "95", "58s"))));

        assertEquals("Alice", resolver.resolve("top_1_player"));
        assertEquals("120", resolver.resolve("top_1_score"));
        assertEquals("42s", resolver.resolve("top_1_time"));

        assertEquals("Bob", resolver.resolve("top_2_player"));
        assertEquals("95", resolver.resolve("top_2_score"));
        assertEquals("58s", resolver.resolve("top_2_time"));

        assertEquals("", resolver.resolve("top_3_player"));
    }

    @Test
    void malformedAndOutOfRangeIdentifiersReturnNull() {
        LeaderboardSnapshotCache cache = new LeaderboardSnapshotCache();
        LeaderboardPlaceholderResolver resolver = new LeaderboardPlaceholderResolver(cache);

        assertNull(resolver.resolve(null));
        assertNull(resolver.resolve(""));
        assertNull(resolver.resolve("top"));
        assertNull(resolver.resolve("top_player"));
        assertNull(resolver.resolve("top_x_player"));
        assertNull(resolver.resolve("top_0_player"));
        assertNull(resolver.resolve("top_11_player"));
        assertNull(resolver.resolve("top_1_points"));
        assertNull(resolver.resolve("weekly_1_player"));
        assertNull(resolver.resolve("top_1_player_extra"));
    }

    @Test
    void snapshotDefensivelyCopiesInputList() {
        List<LeaderboardSnapshot.Entry> mutable = new ArrayList<>();
        mutable.add(new LeaderboardSnapshot.Entry("Alice", "120", "42s"));

        LeaderboardSnapshot snapshot = new LeaderboardSnapshot(mutable);

        mutable.add(new LeaderboardSnapshot.Entry("Bob", "95", "58s"));

        assertEquals(1, snapshot.entries().size());
        assertEquals("Alice", snapshot.entryAt(1).orElseThrow().player());
    }

    @Test
    void publicationReplacesTheWholeSnapshot() {
        LeaderboardSnapshotCache cache = new LeaderboardSnapshotCache();
        LeaderboardPlaceholderResolver resolver = new LeaderboardPlaceholderResolver(cache);

        cache.publish(
                new LeaderboardSnapshot(
                        List.of(new LeaderboardSnapshot.Entry("Old", "1", "99s"))));

        assertEquals("Old", resolver.resolve("top_1_player"));
        assertEquals("1", resolver.resolve("top_1_score"));
        assertEquals("99s", resolver.resolve("top_1_time"));

        cache.publish(
                new LeaderboardSnapshot(
                        List.of(new LeaderboardSnapshot.Entry("New", "999", "10s"))));

        assertEquals("New", resolver.resolve("top_1_player"));
        assertEquals("999", resolver.resolve("top_1_score"));
        assertEquals("10s", resolver.resolve("top_1_time"));
    }
}
