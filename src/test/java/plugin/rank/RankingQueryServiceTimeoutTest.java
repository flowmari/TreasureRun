package plugin.rank;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RankingQueryServiceTimeoutTest {

    @Test
    void boundedAllTimeQuerySetsStatementDeadlineBeforeExecution() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        RankingQueryService.loadAllTime(
                connection,
                JdbcLeaderboardSnapshotLoader.QUERY_TIMEOUT_SECONDS);

        InOrder order = inOrder(statement);
        order.verify(statement)
                .setQueryTimeout(JdbcLeaderboardSnapshotLoader.QUERY_TIMEOUT_SECONDS);
        order.verify(statement).executeQuery();
    }

    @Test
    void existingAllTimeQueryPreservesUnboundedLegacyCallContract() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        RankingQueryService.loadAllTime(connection);

        verify(statement, never()).setQueryTimeout(anyInt());
        verify(statement).executeQuery();
    }

    @Test
    void nonPositiveStatementDeadlineFailsBeforeTouchingConnection() {
        Connection connection = mock(Connection.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> RankingQueryService.loadAllTime(connection, 0));

        verifyNoInteractions(connection);
    }
}
