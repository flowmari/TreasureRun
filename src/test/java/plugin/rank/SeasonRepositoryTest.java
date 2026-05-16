package plugin.rank;

import plugin.TreasureRunMultiChestPlugin;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies weekly season lookup / creation behavior without a real MySQL server.
 */
class SeasonRepositoryTest {

    @Test
    void returnsExistingWeeklySeasonIdWhenFound() throws Exception {
        TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
        Connection connection = mock(Connection.class);
        PreparedStatement selectStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(plugin.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(selectStatement);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("id")).thenReturn(123L);

        SeasonRepository repository = new SeasonRepository(plugin);

        long seasonId = repository.getOrCreateCurrentWeeklySeasonId();

        assertEquals(123L, seasonId);

        verify(selectStatement).setString(1, "WEEKLY");
        verify(selectStatement).setInt(2, java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Tokyo"))
                .get(java.time.temporal.WeekFields.ISO.weekBasedYear()));
        verify(selectStatement).setInt(3, java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Tokyo"))
                .get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()));
    }

    @Test
    void insertsWeeklySeasonAndReturnsGeneratedIdWhenNotFound() throws Exception {
        TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
        Connection connection = mock(Connection.class);

        PreparedStatement firstSelect = mock(PreparedStatement.class);
        ResultSet firstResult = mock(ResultSet.class);

        PreparedStatement insertStatement = mock(PreparedStatement.class);
        ResultSet generatedKeys = mock(ResultSet.class);

        when(plugin.getConnection()).thenReturn(connection);

        when(connection.prepareStatement(anyString()))
                .thenReturn(firstSelect);

        when(connection.prepareStatement(anyString(), anyInt()))
                .thenReturn(insertStatement);

        when(firstSelect.executeQuery()).thenReturn(firstResult);
        when(firstResult.next()).thenReturn(false);

        when(insertStatement.getGeneratedKeys()).thenReturn(generatedKeys);
        when(generatedKeys.next()).thenReturn(true);
        when(generatedKeys.getLong(1)).thenReturn(999L);

        SeasonRepository repository = new SeasonRepository(plugin);

        long seasonId = repository.getOrCreateCurrentWeeklySeasonId();

        assertEquals(999L, seasonId);

        verify(insertStatement).setString(1, "WEEKLY");
        verify(insertStatement).executeUpdate();
        verify(connection).prepareStatement(anyString(), org.mockito.ArgumentMatchers.eq(Statement.RETURN_GENERATED_KEYS));
    }
}
