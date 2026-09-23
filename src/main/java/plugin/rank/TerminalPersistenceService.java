package plugin.rank;

import plugin.DatabaseRuntimeSettings;
import plugin.rank.event.GameResultRecorded;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * DB-H3A terminal-persistence boundary.
 *
 * <p>The Minecraft thread captures immutable values and submits them here.
 * This class deliberately has no Bukkit dependency and never uses the
 * plugin's shared mutable JDBC Connection.</p>
 */
public final class TerminalPersistenceService implements AutoCloseable {

  public static final int CONNECT_TIMEOUT_MILLIS = 3_000;
  public static final int SOCKET_TIMEOUT_MILLIS = 5_000;
  public static final int QUERY_TIMEOUT_SECONDS = 4;
  public static final int QUEUE_CAPACITY = 64;

  public enum Status {
    PERSISTED,
    ALREADY_PERSISTED,
    SKIPPED_DATABASE_DISABLED,
    REJECTED,
    FAILED
  }

  public record PersistenceResult(Status status, String detail) {
    public PersistenceResult {
      Objects.requireNonNull(status, "status");
      detail = detail == null ? "" : detail;
    }

    public boolean persisted() {
      return status == Status.PERSISTED || status == Status.ALREADY_PERSISTED;
    }
  }

  public record TerminalWrite(
      UUID eventId,
      Instant occurredAt,
      UUID playerUuid,
      String playerName,
      String langCode,
      int score,
      long elapsedSeconds,
      String difficulty,
      String outcome,
      boolean updateRankingAggregates,
      boolean win,
      Long bestTimeMs,
      String proverbText
  ) {
    public TerminalWrite {
      Objects.requireNonNull(eventId, "eventId");
      Objects.requireNonNull(occurredAt, "occurredAt");
      Objects.requireNonNull(playerUuid, "playerUuid");
      playerName = normalize(playerName, "unknown");
      langCode = normalize(langCode, "ja").toLowerCase(Locale.ROOT);
      difficulty = normalize(difficulty, "Normal");
      outcome = normalize(outcome, "UNKNOWN").toUpperCase(Locale.ROOT);
      elapsedSeconds = Math.max(0L, elapsedSeconds);
      proverbText = normalizeOptional(proverbText);
    }
  }

  public record ProverbWrite(
      UUID playerUuid,
      String playerName,
      String outcome,
      String difficulty,
      String langCode,
      String proverbText
  ) {
    public ProverbWrite {
      Objects.requireNonNull(playerUuid, "playerUuid");
      playerName = normalize(playerName, "unknown");
      outcome = normalize(outcome, "UNKNOWN").toUpperCase(Locale.ROOT);
      difficulty = normalize(difficulty, "Normal");
      langCode = normalize(langCode, "ja").toLowerCase(Locale.ROOT);
      proverbText = normalizeOptional(proverbText);
    }
  }

  private final DatabaseRuntimeSettings settings;
  private final Logger logger;
  private final AtomicBoolean accepting = new AtomicBoolean(true);
  private final ThreadPoolExecutor executor;

  private final class PendingTask implements Runnable {
    private final CompletableFuture<PersistenceResult> completion;
    private final Runnable delegate;
    private final String stoppedDetail;

    private PendingTask(
        CompletableFuture<PersistenceResult> completion,
        Runnable delegate,
        String stoppedDetail
    ) {
      this.completion = completion;
      this.delegate = delegate;
      this.stoppedDetail = stoppedDetail;
    }

    @Override
    public void run() {
      if (!accepting.get()) {
        rejectBeforeExecution(stoppedDetail);
        return;
      }
      delegate.run();
    }

    private void rejectBeforeExecution(String detail) {
      completion.complete(new PersistenceResult(Status.REJECTED, detail));
    }
  }

  public TerminalPersistenceService(
      DatabaseRuntimeSettings settings,
      Logger logger
  ) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.logger = Objects.requireNonNull(logger, "logger");

    this.executor = new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(QUEUE_CAPACITY),
        runnable -> {
          Thread thread = new Thread(runnable, "TreasureRun-terminal-persistence");
          thread.setDaemon(true);
          return thread;
        },
        new ThreadPoolExecutor.AbortPolicy()
    );
  }

  public CompletionStage<PersistenceResult> submit(TerminalWrite write) {
    Objects.requireNonNull(write, "write");

    if (!settings.enabled()) {
      return CompletableFuture.completedFuture(
          new PersistenceResult(
              Status.SKIPPED_DATABASE_DISABLED,
              "database disabled"
          )
      );
    }

    if (!accepting.get()) {
      return rejected("terminal persistence is shutting down");
    }

    CompletableFuture<PersistenceResult> completion = new CompletableFuture<>();

    PendingTask task =
        new PendingTask(
            completion,
            () -> {
              try {
                completion.complete(persistTerminal(write));
              } catch (Exception exception) {
                String detail = detail(exception);
                logger.warning(
                    "[Database][TerminalPersistence] persistence failed: "
                        + detail
                        + " eventId="
                        + write.eventId()
                        + " outcome="
                        + write.outcome()
                );
                completion.complete(
                    new PersistenceResult(Status.FAILED, detail)
                );
              }
            },
            "terminal persistence stopped before execution"
        );

    try {
      executor.execute(task);
    } catch (RejectedExecutionException rejected) {
      logger.warning(
          "[Database][TerminalPersistence] submission rejected: queue full or executor stopped"
      );
      task.rejectBeforeExecution("queue full or executor stopped");
    }

    return completion;
  }

  public CompletionStage<PersistenceResult> submitProverb(ProverbWrite write) {
    Objects.requireNonNull(write, "write");

    if (write.proverbText() == null) {
      return CompletableFuture.completedFuture(
          new PersistenceResult(Status.PERSISTED, "no proverb to persist")
      );
    }

    if (!settings.enabled()) {
      return CompletableFuture.completedFuture(
          new PersistenceResult(
              Status.SKIPPED_DATABASE_DISABLED,
              "database disabled"
          )
      );
    }

    if (!accepting.get()) {
      return rejected("terminal persistence is shutting down");
    }

    CompletableFuture<PersistenceResult> completion = new CompletableFuture<>();

    PendingTask task =
        new PendingTask(
            completion,
            () -> {
              try (Connection connection = openConnection()) {
                persistProverb(connection, write);
                completion.complete(
                    new PersistenceResult(Status.PERSISTED, "proverb persisted")
                );
              } catch (Exception exception) {
                String detail = detail(exception);
                logger.warning(
                    "[Database][TerminalPersistence] proverb persistence failed: "
                        + detail
                );
                completion.complete(
                    new PersistenceResult(Status.FAILED, detail)
                );
              }
            },
            "terminal persistence stopped before proverb execution"
        );

    try {
      executor.execute(task);
    } catch (RejectedExecutionException rejected) {
      logger.warning(
          "[Database][TerminalPersistence] proverb submission rejected: queue full or executor stopped"
      );
      task.rejectBeforeExecution("queue full or executor stopped");
    }

    return completion;
  }

  private CompletionStage<PersistenceResult> rejected(String detail) {
    logger.warning("[Database][TerminalPersistence] submission rejected: " + detail);
    return CompletableFuture.completedFuture(
        new PersistenceResult(Status.REJECTED, detail)
    );
  }

  private PersistenceResult persistTerminal(TerminalWrite write) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");

    try (Connection connection = openConnection()) {
      boolean oldAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);

      try {
        GameResultRecorded event = null;

        if (write.updateRankingAggregates()) {
          long seasonId =
              SeasonRepository.getOrCreateCurrentWeeklySeasonId(
                  connection,
                  write.occurredAt(),
                  QUERY_TIMEOUT_SECONDS
              );

          event = GameResultRecorded.create(
              write.eventId(),
              write.occurredAt(),
              seasonId,
              write.playerUuid(),
              write.playerName(),
              write.outcome(),
              write.score(),
              write.win() ? 1 : 0,
              write.bestTimeMs(),
              write.langCode()
          );
        }

        /*
         * Raw history is now inside the same caller-owned transaction as the
         * idempotency/outbox claim and ranking aggregates. If the event id is
         * already durable, rollback removes this attempted raw insert too.
         */
        persistRawScore(connection, write);

        if (event != null) {
          boolean newlyApplied =
              SeasonScoreRepository.addWeeklyAndAllTimeInTransaction(
                  connection,
                  event,
                  QUERY_TIMEOUT_SECONDS
              );

          if (!newlyApplied) {
            connection.rollback();
            return new PersistenceResult(
                Status.ALREADY_PERSISTED,
                "event already persisted"
            );
          }
        }

        if (write.proverbText() != null) {
          persistProverb(
              connection,
              new ProverbWrite(
                  write.playerUuid(),
                  write.playerName(),
                  write.outcome(),
                  write.difficulty(),
                  write.langCode(),
                  write.proverbText()
              )
          );
        }

        connection.commit();
        return new PersistenceResult(Status.PERSISTED, "persisted");
      } catch (Exception exception) {
        try {
          connection.rollback();
        } catch (SQLException rollbackFailure) {
          exception.addSuppressed(rollbackFailure);
        }
        throw exception;
      } finally {
        try {
          connection.setAutoCommit(oldAutoCommit);
        } catch (SQLException ignored) {
          // Connection is operation-owned and closes immediately afterwards.
        }
      }
    }
  }

  private void persistRawScore(
      Connection connection,
      TerminalWrite write
  ) throws SQLException {
    final String sql =
        "INSERT INTO scores "
            + "(uuid, player_name, score, time, difficulty, lang_code, played_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      statement.setString(1, write.playerUuid().toString());
      statement.setString(2, write.playerName());
      statement.setInt(3, write.score());
      statement.setLong(4, write.elapsedSeconds());
      statement.setString(5, write.difficulty());
      statement.setString(6, write.langCode());
      statement.setTimestamp(7, Timestamp.from(write.occurredAt()));
      statement.executeUpdate();
    }
  }

  private void persistProverb(
      Connection connection,
      ProverbWrite write
  ) throws SQLException {
    if (write.proverbText() == null) {
      return;
    }

    final String sql =
        "INSERT INTO proverb_logs "
            + "(player_uuid, player_name, outcome, difficulty, lang, quote_text) "
            + "VALUES (?, ?, ?, ?, ?, ?)";

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      statement.setString(1, write.playerUuid().toString());
      statement.setString(2, write.playerName());
      statement.setString(3, write.outcome());
      statement.setString(4, write.difficulty());
      statement.setString(5, write.langCode());
      statement.setString(6, write.proverbText());
      statement.executeUpdate();
    }
  }

  private Connection openConnection() throws SQLException {
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

  @Override
  public void close() {
    if (!accepting.compareAndSet(true, false)) {
      return;
    }

    /*
     * Never wait for DB completion on the Minecraft thread.
     * An already-running JDBC call remains bounded by connection/socket/query
     * timeouts and has no authority to publish Bukkit state.
     *
     * shutdownNow() returns queued tasks that never started. Their futures
     * must still reach a terminal REJECTED state.
     */
    List<Runnable> abandoned = executor.shutdownNow();
    for (Runnable runnable : abandoned) {
      if (runnable instanceof TerminalPersistenceService.PendingTask pending) {
        pending.rejectBeforeExecution(
            "terminal persistence stopped before queued task execution"
        );
      }
    }
  }

  int queuedTaskCount() {
    return executor.getQueue().size();
  }

  boolean isAccepting() {
    return accepting.get();
  }

  private static String normalize(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static String detail(Throwable throwable) {
    String message = throwable.getMessage();
    return message == null || message.isBlank()
        ? throwable.getClass().getSimpleName()
        : message;
  }
}
