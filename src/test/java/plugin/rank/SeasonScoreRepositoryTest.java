package plugin.rank;

import plugin.TreasureRunMultiChestPlugin;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies ranking persistence behavior without a real MySQL server.
 *
 * This protects:
 * - weekly score update
 * - all-time score update
 * - one transaction across both tables
 * - rollback when the second write fails
 */
class SeasonScoreRepositoryTest {

    @Test
    void addWeeklyAndAllTimeWritesBothTablesAndCommitsTransaction() throws Exception {
        TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
        Connection connection = mock(Connection.class);
        PreparedStatement weeklyStatement = mock(PreparedStatement.class);
        PreparedStatement allTimeStatement = mock(PreparedStatement.class);

        when(plugin.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(contains("season_scores"))).thenReturn(weeklyStatement);
        when(connection.prepareStatement(contains("alltime_scores"))).thenReturn(allTimeStatement);

        SeasonScoreRepository repository = new SeasonScoreRepository(plugin);

        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000123");

        repository.addWeeklyAndAllTime(
                42L,
                uuid,
                "flowmari",
                100,
                1,
                12345L,
                "en"
        );

        verify(connection).setAutoCommit(false);

        verify(weeklyStatement).setLong(1, 42L);
        verify(weeklyStatement).setString(2, uuid.toString());
        verify(weeklyStatement).setString(3, "flowmari");
        verify(weeklyStatement).setInt(4, 100);
        verify(weeklyStatement).setInt(5, 1);
        verify(weeklyStatement).setLong(6, 12345L);
        verify(weeklyStatement).setString(7, "en");

        verify(allTimeStatement).setString(1, uuid.toString());
        verify(allTimeStatement).setString(2, "flowmari");
        verify(allTimeStatement).setInt(3, 100);
        verify(allTimeStatement).setInt(4, 1);
        verify(allTimeStatement).setLong(5, 12345L);
        verify(allTimeStatement).setString(6, "en");

        InOrder order = inOrder(connection, weeklyStatement, allTimeStatement);
        order.verify(connection).setAutoCommit(false);
        order.verify(weeklyStatement).executeUpdate();
        order.verify(allTimeStatement).executeUpdate();
        order.verify(connection).commit();
        order.verify(connection).setAutoCommit(true);
    }

    @Test
    void addWeeklyAndAllTimeUsesJapaneseFallbackWhenLanguageCodeIsBlank() throws Exception {
        TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
        Connection connection = mock(Connection.class);
        PreparedStatement weeklyStatement = mock(PreparedStatement.class);
        PreparedStatement allTimeStatement = mock(PreparedStatement.class);

        when(plugin.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(contains("season_scores"))).thenReturn(weeklyStatement);
        when(connection.prepareStatement(contains("alltime_scores"))).thenReturn(allTimeStatement);

        SeasonScoreRepository repository = new SeasonScoreRepository(plugin);

        repository.addWeeklyAndAllTime(
                1L,
                UUID.fromString("00000000-0000-0000-0000-000000000456"),
                "flowmari",
                50,
                0,
                null,
                " "
        );

        verify(weeklyStatement).setNull(6, Types.BIGINT);
        verify(weeklyStatement).setString(7, "ja");

        verify(allTimeStatement).setNull(5, Types.BIGINT);
        verify(allTimeStatement).setString(6, "ja");

        verify(connection).commit();
        verify(connection).setAutoCommit(true);
    }

    @Test
    void addWeeklyAndAllTimeRollsBackWhenAllTimeWriteFails() throws Exception {
        TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
        Connection connection = mock(Connection.class);
        PreparedStatement weeklyStatement = mock(PreparedStatement.class);
        PreparedStatement allTimeStatement = mock(PreparedStatement.class);

        when(plugin.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(contains("season_scores"))).thenReturn(weeklyStatement);
        when(connection.prepareStatement(contains("alltime_scores"))).thenReturn(allTimeStatement);

        doThrow(new SQLException("alltime write failed"))
                .when(allTimeStatement)
                .executeUpdate();

        SeasonScoreRepository repository = new SeasonScoreRepository(plugin);

        assertThrows(SQLException.class, () -> repository.addWeeklyAndAllTime(
                9L,
                UUID.fromString("00000000-0000-0000-0000-000000000789"),
                "flowmari",
                10,
                1,
                999L,
                "en"
        ));

        verify(weeklyStatement).executeUpdate();
        verify(allTimeStatement).executeUpdate();
        verify(connection).rollback();
        verify(connection).setAutoCommit(true);
    }
}
