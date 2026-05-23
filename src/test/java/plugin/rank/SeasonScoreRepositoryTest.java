package plugin.rank;

import plugin.TreasureRunMultiChestPlugin;
import plugin.rank.event.GameResultRecorded;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeasonScoreRepositoryTest {

  private static final UUID EVENT_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID PLAYER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000123");

  private GameResultRecorded successEvent() {
    return GameResultRecorded.create(
        EVENT_ID,
        Instant.parse("2026-05-23T00:00:00Z"),
        42L,
        PLAYER_ID,
        "flowmari",
        "SUCCESS",
        100,
        1,
        12345L,
        "en"
    );
  }

  @Test
  void insertsOutboxBeforeRankingAggregatesAndCommits() throws Exception {
    TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
    Connection connection = mock(Connection.class);
    PreparedStatement outbox = mock(PreparedStatement.class);
    PreparedStatement weekly = mock(PreparedStatement.class);
    PreparedStatement allTime = mock(PreparedStatement.class);

    when(plugin.getConnection()).thenReturn(connection);
    when(connection.getAutoCommit()).thenReturn(true);
    when(connection.prepareStatement(contains("outbox_events"))).thenReturn(outbox);
    when(connection.prepareStatement(contains("season_scores"))).thenReturn(weekly);
    when(connection.prepareStatement(contains("alltime_scores"))).thenReturn(allTime);

    SeasonScoreRepository repository = new SeasonScoreRepository(plugin);

    assertTrue(repository.addWeeklyAndAllTime(successEvent()));

    verify(outbox).setString(1, EVENT_ID.toString());
    verify(outbox).setString(2, "PLAYER_RANKING");
    verify(outbox).setString(3, PLAYER_ID.toString());
    verify(outbox).setString(4, "GameResultRecorded");
    verify(outbox).setString(5, "SUCCESS");
    verify(outbox).setString(eq(6), matches(".*\"eventId\":\"" + EVENT_ID + "\".*"));
    verify(outbox).setTimestamp(eq(7), any(Timestamp.class));

    verify(weekly).setLong(1, 42L);
    verify(weekly).setString(2, PLAYER_ID.toString());
    verify(weekly).setInt(4, 100);
    verify(weekly).setInt(5, 1);
    verify(weekly).setLong(6, 12345L);
    verify(weekly).setString(7, "en");

    verify(allTime).setString(1, PLAYER_ID.toString());
    verify(allTime).setInt(3, 100);
    verify(allTime).setInt(4, 1);
    verify(allTime).setLong(5, 12345L);
    verify(allTime).setString(6, "en");

    InOrder order = inOrder(connection, outbox, weekly, allTime);
    order.verify(connection).setAutoCommit(false);
    order.verify(outbox).executeUpdate();
    order.verify(weekly).executeUpdate();
    order.verify(allTime).executeUpdate();
    order.verify(connection).commit();
    order.verify(connection).setAutoCommit(true);
  }

  @Test
  void duplicateTerminalCallbackDoesNotIncrementRankingAgain() throws Exception {
    TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
    Connection connection = mock(Connection.class);
    PreparedStatement outbox = mock(PreparedStatement.class);
    PreparedStatement weekly = mock(PreparedStatement.class);
    PreparedStatement allTime = mock(PreparedStatement.class);

    when(plugin.getConnection()).thenReturn(connection);
    when(connection.getAutoCommit()).thenReturn(true);
    when(connection.prepareStatement(contains("outbox_events"))).thenReturn(outbox);
    when(connection.prepareStatement(contains("season_scores"))).thenReturn(weekly);
    when(connection.prepareStatement(contains("alltime_scores"))).thenReturn(allTime);

    doThrow(new SQLIntegrityConstraintViolationException(
        "Duplicate event id", "23000", 1062
    )).when(outbox).executeUpdate();

    SeasonScoreRepository repository = new SeasonScoreRepository(plugin);

    assertFalse(repository.addWeeklyAndAllTime(successEvent()));

    verify(outbox).executeUpdate();
    verify(weekly, never()).executeUpdate();
    verify(allTime, never()).executeUpdate();
    verify(connection).rollback();
    verify(connection, never()).commit();
    verify(connection).setAutoCommit(true);
  }

  @Test
  void rollsBackOutboxAndWeeklyWriteWhenAllTimeWriteFails() throws Exception {
    TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
    Connection connection = mock(Connection.class);
    PreparedStatement outbox = mock(PreparedStatement.class);
    PreparedStatement weekly = mock(PreparedStatement.class);
    PreparedStatement allTime = mock(PreparedStatement.class);

    when(plugin.getConnection()).thenReturn(connection);
    when(connection.getAutoCommit()).thenReturn(true);
    when(connection.prepareStatement(contains("outbox_events"))).thenReturn(outbox);
    when(connection.prepareStatement(contains("season_scores"))).thenReturn(weekly);
    when(connection.prepareStatement(contains("alltime_scores"))).thenReturn(allTime);

    doThrow(new SQLException("alltime write failed")).when(allTime).executeUpdate();

    SeasonScoreRepository repository = new SeasonScoreRepository(plugin);

    assertThrows(SQLException.class, () -> repository.addWeeklyAndAllTime(successEvent()));

    verify(outbox).executeUpdate();
    verify(weekly).executeUpdate();
    verify(allTime).executeUpdate();
    verify(connection).rollback();
    verify(connection, never()).commit();
    verify(connection).setAutoCommit(true);
  }

  @Test
  void writesNormalizedNullableEventDataToRankingAggregates() throws Exception {
    TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
    Connection connection = mock(Connection.class);
    PreparedStatement outbox = mock(PreparedStatement.class);
    PreparedStatement weekly = mock(PreparedStatement.class);
    PreparedStatement allTime = mock(PreparedStatement.class);

    when(plugin.getConnection()).thenReturn(connection);
    when(connection.getAutoCommit()).thenReturn(true);
    when(connection.prepareStatement(contains("outbox_events"))).thenReturn(outbox);
    when(connection.prepareStatement(contains("season_scores"))).thenReturn(weekly);
    when(connection.prepareStatement(contains("alltime_scores"))).thenReturn(allTime);

    GameResultRecorded event = GameResultRecorded.create(
        EVENT_ID,
        Instant.parse("2026-05-23T00:00:00Z"),
        1L,
        PLAYER_ID,
        "flowmari",
        "TIME_UP",
        50,
        0,
        null,
        " "
    );

    SeasonScoreRepository repository = new SeasonScoreRepository(plugin);

    assertTrue(repository.addWeeklyAndAllTime(event));

    verify(weekly).setNull(6, Types.BIGINT);
    verify(weekly).setString(7, "ja");
    verify(allTime).setNull(5, Types.BIGINT);
    verify(allTime).setString(6, "ja");
    verify(connection).commit();
  }
}
