package plugin.rank;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shared read boundary for the existing in-plugin leaderboard views.
 *
 * <p>Ranking SQL ordering is owned here so command, placeholder, and ticker
 * paths cannot silently drift apart.</p>
 */
public final class RankingQueryService {

  public enum Window {
    WEEKLY,
    ALL_TIME,
    MONTHLY
  }

  private static final String WEEKLY_SQL =
      "SELECT player_name, score, time, difficulty, lang_code, played_at " +
          "FROM scores " +
          "WHERE played_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
          "ORDER BY score DESC, time ASC, id DESC " +
          "LIMIT 10";

  private static final String ALL_TIME_SQL =
      "SELECT player_name, score, time, difficulty, lang_code, played_at " +
          "FROM scores " +
          "ORDER BY score DESC, time ASC, id DESC " +
          "LIMIT 10";

  private static final String MONTHLY_SQL =
      "SELECT player_name, score, time, difficulty, lang_code, played_at " +
          "FROM scores " +
          "WHERE YEAR(played_at) = YEAR(NOW()) " +
          "  AND MONTH(played_at) = MONTH(NOW()) " +
          "ORDER BY score DESC, time ASC, id DESC " +
          "LIMIT 10";

  private static final String RUN_RANK_SQL =
      "SELECT player_name, score, time, difficulty " +
          "FROM scores " +
          "WHERE UPPER(difficulty) = UPPER(?) " +
          "ORDER BY time ASC, score DESC";

  private RankingQueryService() {
  }

  public static List<RankingEntry> loadWeekly(Connection connection) throws SQLException {
    return query(connection, WEEKLY_SQL);
  }

  static List<RankingEntry> loadWeekly(
      Connection connection,
      int queryTimeoutSeconds
  ) throws SQLException {
    requirePositiveTimeout(queryTimeoutSeconds);
    return query(connection, WEEKLY_SQL, queryTimeoutSeconds);
  }

  public static List<RankingEntry> loadAllTime(Connection connection) throws SQLException {
    return query(connection, ALL_TIME_SQL);
  }

  static List<RankingEntry> loadAllTime(
      Connection connection,
      int queryTimeoutSeconds
  ) throws SQLException {
    requirePositiveTimeout(queryTimeoutSeconds);
    return query(connection, ALL_TIME_SQL, queryTimeoutSeconds);
  }

  public static List<RankingEntry> loadMonthly(Connection connection) throws SQLException {
    return query(connection, MONTHLY_SQL);
  }

  static List<RankingEntry> loadMonthly(
      Connection connection,
      int queryTimeoutSeconds
  ) throws SQLException {
    requirePositiveTimeout(queryTimeoutSeconds);
    return query(connection, MONTHLY_SQL, queryTimeoutSeconds);
  }

  static List<RankingEntry> load(
      Connection connection,
      Window window,
      int queryTimeoutSeconds
  ) throws SQLException {
    Objects.requireNonNull(window, "window");

    return switch (window) {
      case WEEKLY -> loadWeekly(connection, queryTimeoutSeconds);
      case ALL_TIME -> loadAllTime(connection, queryTimeoutSeconds);
      case MONTHLY -> loadMonthly(connection, queryTimeoutSeconds);
    };
  }

  static int findRunRank(
      Connection connection,
      String playerName,
      int score,
      long timeSec,
      String difficulty,
      int queryTimeoutSeconds
  ) throws SQLException {
    Objects.requireNonNull(connection, "connection");
    Objects.requireNonNull(playerName, "playerName");
    Objects.requireNonNull(difficulty, "difficulty");
    requirePositiveTimeout(queryTimeoutSeconds);

    try (PreparedStatement statement = connection.prepareStatement(RUN_RANK_SQL)) {
      statement.setQueryTimeout(queryTimeoutSeconds);
      statement.setString(1, difficulty);

      try (ResultSet resultSet = statement.executeQuery()) {
        int rank = 0;
        while (resultSet.next()) {
          rank++;

          String name = resultSet.getString("player_name");
          int rowScore = resultSet.getInt("score");
          long rowTime = resultSet.getLong("time");
          String rowDifficulty = resultSet.getString("difficulty");

          if (rowScore == score
              && rowTime == timeSec
              && rowDifficulty != null
              && rowDifficulty.equalsIgnoreCase(difficulty)
              && name != null
              && name.equalsIgnoreCase(playerName)) {
            return rank;
          }
        }
      }
    }

    return -1;
  }

  private static void requirePositiveTimeout(int queryTimeoutSeconds) {
    if (queryTimeoutSeconds <= 0) {
      throw new IllegalArgumentException(
          "queryTimeoutSeconds must be greater than zero"
      );
    }
  }

  private static List<RankingEntry> query(Connection connection, String sql)
      throws SQLException {
    return query(connection, sql, 0);
  }

  private static List<RankingEntry> query(
      Connection connection,
      String sql,
      int queryTimeoutSeconds
  ) throws SQLException {
    Objects.requireNonNull(connection, "connection");

    List<RankingEntry> entries = new ArrayList<>();

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      if (queryTimeoutSeconds > 0) {
        statement.setQueryTimeout(queryTimeoutSeconds);
      }

      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          entries.add(
              new RankingEntry(
                  resultSet.getString("player_name"),
                  resultSet.getInt("score"),
                  resultSet.getLong("time"),
                  resultSet.getString("difficulty"),
                  resultSet.getString("lang_code")
              )
          );
        }
      }
    }

    return List.copyOf(entries);
  }

  public record RankingEntry(
      String playerName,
      int score,
      long time,
      String difficulty,
      String languageCode
  ) {
  }
}
