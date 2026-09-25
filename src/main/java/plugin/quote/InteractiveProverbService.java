package plugin.quote;

import plugin.DatabaseRuntimeSettings;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * DB-H3B interactive proverb/favorites boundary.
 *
 * <p>Request/event handlers never perform JDBC directly. They capture immutable
 * request data, submit it here, and render the completed value back on the Bukkit
 * main thread. This service has no Bukkit dependency.</p>
 */
public final class InteractiveProverbService implements AutoCloseable {

  public static final int QUEUE_CAPACITY = 32;
  public static final long MAX_QUEUE_AGE_MILLIS = 8_000L;

  public enum Status {
    SUCCESS,
    DATABASE_DISABLED,
    REJECTED,
    FAILED
  }

  public record Result<T>(Status status, T value, String detail) {
    public Result {
      Objects.requireNonNull(status, "status");
      detail = detail == null ? "" : detail;
    }

    public boolean successful() {
      return status == Status.SUCCESS;
    }
  }

  public record BookData(List<String> recent, List<String> favorites) {
    public BookData {
      recent = List.copyOf(recent == null ? List.of() : recent);
      favorites = List.copyOf(favorites == null ? List.of() : favorites);
    }

    public static BookData empty() {
      return new BookData(List.of(), List.of());
    }
  }

  /** Blocking backend. Every method is executed only by this service's worker. */
  public interface Backend {
    BookData loadBookData(UUID playerId, int recentLimit, int favoritesLimit) throws Exception;
    List<String> loadFavorites(UUID playerId, int limit) throws Exception;
    boolean favoriteLatest(UUID playerId) throws Exception;
    boolean deleteFavorite(UUID playerId, int favoriteId) throws Exception;
  }

  private record ReadKey(String operation, UUID playerId, int first, int second) {
  }

  private final boolean databaseEnabled;
  private final Backend backend;
  private final Logger logger;
  private final AtomicBoolean accepting = new AtomicBoolean(true);
  private final ThreadPoolExecutor executor;
  private final ConcurrentHashMap<ReadKey, CompletableFuture<?>> readFlights =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, BookData> lastBookData =
      new ConcurrentHashMap<>();

  public InteractiveProverbService(
      DatabaseRuntimeSettings settings,
      Backend backend,
      Logger logger
  ) {
    Objects.requireNonNull(settings, "settings");
    this.databaseEnabled = settings.enabled();
    this.backend = Objects.requireNonNull(backend, "backend");
    this.logger = Objects.requireNonNull(logger, "logger");

    this.executor = new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(QUEUE_CAPACITY),
        runnable -> {
          Thread thread = new Thread(runnable, "TreasureRun-interactive-proverb-db");
          thread.setDaemon(true);
          return thread;
        },
        new ThreadPoolExecutor.AbortPolicy()
    );
  }

  public CompletionStage<Result<BookData>> loadBookData(
      UUID playerId,
      int recentLimit,
      int favoritesLimit
  ) {
    Objects.requireNonNull(playerId, "playerId");
    int safeRecent = Math.max(1, recentLimit);
    int safeFavorites = Math.max(1, favoritesLimit);
    ReadKey key = new ReadKey("book", playerId, safeRecent, safeFavorites);

    return submitRead(
        key,
        () -> backend.loadBookData(playerId, safeRecent, safeFavorites),
        BookData.empty(),
        data -> lastBookData.put(playerId, data)
    );
  }

  public CompletionStage<Result<List<String>>> loadFavorites(UUID playerId, int limit) {
    Objects.requireNonNull(playerId, "playerId");
    int safeLimit = Math.max(1, limit);
    ReadKey key = new ReadKey("favorites", playerId, safeLimit, 0);

    return submitRead(
        key,
        () -> List.copyOf(backend.loadFavorites(playerId, safeLimit)),
        List.of(),
        favorites -> lastBookData.compute(
            playerId,
            (ignored, previous) -> new BookData(
                previous == null ? List.of() : previous.recent(),
                favorites
            )
        )
    );
  }

  public CompletionStage<Result<Boolean>> favoriteLatest(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return submitMutation(
        "favoriteLatest",
        () -> backend.favoriteLatest(playerId),
        Boolean.FALSE
    );
  }

  public CompletionStage<Result<Boolean>> deleteFavorite(UUID playerId, int favoriteId) {
    Objects.requireNonNull(playerId, "playerId");
    if (favoriteId <= 0) {
      return CompletableFuture.completedFuture(
          new Result<>(Status.FAILED, Boolean.FALSE, "favorite id must be positive")
      );
    }
    return submitMutation(
        "deleteFavorite",
        () -> backend.deleteFavorite(playerId, favoriteId),
        Boolean.FALSE
    );
  }

  /** Memory-only compatibility snapshot. Never performs JDBC. */
  public List<String> recentSnapshot(UUID playerId, int limit) {
    if (playerId == null) return List.of();
    BookData data = lastBookData.get(playerId);
    if (data == null || data.recent().isEmpty()) return List.of();
    int end = Math.min(Math.max(1, limit), data.recent().size());
    return List.copyOf(data.recent().subList(0, end));
  }

  private <T> CompletionStage<Result<T>> submitRead(
      ReadKey key,
      Callable<T> work,
      T fallback,
      java.util.function.Consumer<T> successfulPublication
  ) {
    if (!databaseEnabled) {
      return CompletableFuture.completedFuture(
          new Result<>(Status.DATABASE_DISABLED, fallback, "database disabled")
      );
    }
    if (!accepting.get()) {
      return rejected(fallback, "interactive database service is shutting down");
    }

    for (;;) {
      CompletableFuture<?> existing = readFlights.get(key);
      if (existing != null) {
        @SuppressWarnings("unchecked")
        CompletableFuture<Result<T>> same = (CompletableFuture<Result<T>>) existing;
        return same;
      }

      CompletableFuture<Result<T>> created = new CompletableFuture<>();
      if (readFlights.putIfAbsent(key, created) != null) {
        continue;
      }

      PendingTask<T> task = new PendingTask<>(
          "read:" + key.operation(),
          created,
          work,
          fallback,
          successfulPublication,
          () -> readFlights.remove(key, created)
      );
      execute(task);
      return created;
    }
  }

  private <T> CompletionStage<Result<T>> submitMutation(
      String operation,
      Callable<T> work,
      T fallback
  ) {
    if (!databaseEnabled) {
      return CompletableFuture.completedFuture(
          new Result<>(Status.DATABASE_DISABLED, fallback, "database disabled")
      );
    }
    if (!accepting.get()) {
      return rejected(fallback, "interactive database service is shutting down");
    }

    CompletableFuture<Result<T>> completion = new CompletableFuture<>();
    PendingTask<T> task = new PendingTask<>(
        operation,
        completion,
        work,
        fallback,
        ignored -> { },
        () -> { }
    );
    execute(task);
    return completion;
  }

  private void execute(PendingTask<?> task) {
    try {
      executor.execute(task);
    } catch (RejectedExecutionException rejected) {
      task.reject("queue full or executor stopped");
    }
  }

  private final class PendingTask<T> implements Runnable {
    private final String operation;
    private final CompletableFuture<Result<T>> completion;
    private final Callable<T> work;
    private final T fallback;
    private final java.util.function.Consumer<T> successfulPublication;
    private final Runnable cleanup;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final long submittedAtNanos = System.nanoTime();

    private PendingTask(
        String operation,
        CompletableFuture<Result<T>> completion,
        Callable<T> work,
        T fallback,
        java.util.function.Consumer<T> successfulPublication,
        Runnable cleanup
    ) {
      this.operation = operation;
      this.completion = completion;
      this.work = work;
      this.fallback = fallback;
      this.successfulPublication = successfulPublication;
      this.cleanup = cleanup;
    }

    @Override
    public void run() {
      if (!accepting.get()) {
        reject("service stopped before execution");
        return;
      }

      long queuedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - submittedAtNanos);
      if (queuedMillis > MAX_QUEUE_AGE_MILLIS) {
        reject("request expired in bounded queue");
        return;
      }

      try {
        T value = work.call();
        if (!accepting.get()) {
          reject("service stopped before publication");
          return;
        }
        successfulPublication.accept(value);
        complete(new Result<>(Status.SUCCESS, value, ""));
      } catch (Exception exception) {
        if (!accepting.get()) {
          reject("service stopped while operation was in flight");
          return;
        }
        String message = detail(exception);
        logger.warning("[Database][InteractiveProverb] " + operation + " failed: " + message);
        complete(new Result<>(Status.FAILED, fallback, message));
      }
    }

    private void reject(String detail) {
      complete(new Result<>(Status.REJECTED, fallback, detail));
    }

    private void complete(Result<T> result) {
      if (!finished.compareAndSet(false, true)) {
        return;
      }
      try {
        completion.complete(result);
      } finally {
        cleanup.run();
      }
    }
  }

  private <T> CompletionStage<Result<T>> rejected(T fallback, String detail) {
    return CompletableFuture.completedFuture(
        new Result<>(Status.REJECTED, fallback, detail)
    );
  }

  @Override
  public void close() {
    if (!accepting.compareAndSet(true, false)) {
      return;
    }

    for (Runnable pending : executor.shutdownNow()) {
      if (pending instanceof InteractiveProverbService.PendingTask<?> task) {
        task.reject("interactive database service stopped before execution");
      }
    }
  }

  private static String detail(Exception exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank()
        ? exception.getClass().getSimpleName()
        : message;
  }
}
