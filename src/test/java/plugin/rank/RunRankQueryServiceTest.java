package plugin.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RunRankQueryServiceTest {

  @Test
  void boundedRunRankPreservesLegacyOrderingAndFindsTheMatchingRow() throws Exception {
    AtomicReference<String> capturedSql = new AtomicReference<>();
    AtomicInteger capturedTimeout = new AtomicInteger();
    AtomicReference<String> capturedDifficulty = new AtomicReference<>();

    Connection connection = connectionReturning(
        capturedSql,
        capturedTimeout,
        capturedDifficulty,
        List.of(
            new Row("Fast", 100, 10L, "Hard"),
            new Row("Target", 90, 11L, "Hard"),
            new Row("Slow", 80, 12L, "Hard")
        )
    );

    int rank = RankingQueryService.findRunRank(
        connection,
        "Target",
        90,
        11L,
        "Hard",
        JdbcLeaderboardSnapshotLoader.QUERY_TIMEOUT_SECONDS
    );

    assertEquals(2, rank);
    assertEquals("Hard", capturedDifficulty.get());
    assertEquals(JdbcLeaderboardSnapshotLoader.QUERY_TIMEOUT_SECONDS, capturedTimeout.get());
    assertTrue(capturedSql.get().contains("WHERE UPPER(difficulty) = UPPER(?)"));
    assertTrue(capturedSql.get().contains("ORDER BY time ASC, score DESC"));
    assertFalse(capturedSql.get().contains(
        "ORDER BY time ASC, score DESC, id DESC"));
  }

  @Test
  void boundedRunRankReturnsMinusOneWhenTheSavedRunCannotBeLocated() throws Exception {
    AtomicReference<String> capturedSql = new AtomicReference<>();
    AtomicInteger capturedTimeout = new AtomicInteger();
    AtomicReference<String> capturedDifficulty = new AtomicReference<>();

    int rank = RankingQueryService.findRunRank(
        connectionReturning(
            capturedSql,
            capturedTimeout,
            capturedDifficulty,
            List.of(new Row("Other", 10, 99L, "Normal"))
        ),
        "Missing",
        1,
        100L,
        "Normal",
        2
    );

    assertEquals(-1, rank);
  }

  private static Connection connectionReturning(
      AtomicReference<String> capturedSql,
      AtomicInteger capturedTimeout,
      AtomicReference<String> capturedDifficulty,
      List<Row> rows
  ) {
    ClassLoader loader = RunRankQueryServiceTest.class.getClassLoader();

    return (Connection) Proxy.newProxyInstance(
        loader,
        new Class<?>[] {Connection.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "prepareStatement" -> {
            capturedSql.set((String) args[0]);
            yield preparedStatementReturning(
                loader,
                capturedTimeout,
                capturedDifficulty,
                rows
            );
          }
          case "close" -> null;
          case "isClosed" -> false;
          case "toString" -> "RunRankQueryServiceTestConnection";
          default -> throw new UnsupportedOperationException(
              "Unexpected Connection method: " + method.getName()
          );
        }
    );
  }

  private static PreparedStatement preparedStatementReturning(
      ClassLoader loader,
      AtomicInteger capturedTimeout,
      AtomicReference<String> capturedDifficulty,
      List<Row> rows
  ) {
    return (PreparedStatement) Proxy.newProxyInstance(
        loader,
        new Class<?>[] {PreparedStatement.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "setQueryTimeout" -> {
            capturedTimeout.set((Integer) args[0]);
            yield null;
          }
          case "setString" -> {
            if (((Integer) args[0]) == 1) {
              capturedDifficulty.set((String) args[1]);
            }
            yield null;
          }
          case "executeQuery" -> resultSetReturning(loader, rows);
          case "close" -> null;
          case "isClosed" -> false;
          case "toString" -> "RunRankQueryServiceTestPreparedStatement";
          default -> throw new UnsupportedOperationException(
              "Unexpected PreparedStatement method: " + method.getName()
          );
        }
    );
  }

  private static ResultSet resultSetReturning(ClassLoader loader, List<Row> rows) {
    AtomicInteger cursor = new AtomicInteger(-1);

    return (ResultSet) Proxy.newProxyInstance(
        loader,
        new Class<?>[] {ResultSet.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "next" -> cursor.incrementAndGet() < rows.size();
          case "getString" -> {
            Row row = current(rows, cursor.get());
            String column = (String) args[0];
            yield switch (column) {
              case "player_name" -> row.playerName();
              case "difficulty" -> row.difficulty();
              default -> throw new UnsupportedOperationException(
                  "Unexpected string column: " + column
              );
            };
          }
          case "getInt" -> current(rows, cursor.get()).score();
          case "getLong" -> current(rows, cursor.get()).time();
          case "close" -> null;
          case "isClosed" -> false;
          case "wasNull" -> false;
          case "toString" -> "RunRankQueryServiceTestResultSet";
          default -> throw new UnsupportedOperationException(
              "Unexpected ResultSet method: " + method.getName()
          );
        }
    );
  }

  private static Row current(List<Row> rows, int index) {
    if (index < 0 || index >= rows.size()) {
      throw new IllegalStateException("ResultSet cursor is not on a row.");
    }
    return rows.get(index);
  }

  private record Row(String playerName, int score, long time, String difficulty) {
  }
}
