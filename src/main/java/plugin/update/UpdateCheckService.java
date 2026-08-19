package plugin.update;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateCheckService implements AutoCloseable {

  private final boolean enabled;
  private final String currentVersion;
  private final Duration cacheInterval;
  private final RemoteReleaseLookup releaseLookup;
  private final UpdateVersionComparator versionComparator;
  private final Executor executor;
  private final Clock clock;
  private final Runnable closeAction;

  private final Object lock = new Object();
  private CacheEntry cached;
  private CompletableFuture<UpdateCheckResult> inFlight;

  public UpdateCheckService(
      boolean enabled,
      String currentVersion,
      Duration cacheInterval,
      RemoteReleaseLookup releaseLookup
  ) {
    this(
        enabled,
        currentVersion,
        cacheInterval,
        releaseLookup,
        new UpdateVersionComparator(),
        newDaemonExecutor(),
        Clock.systemUTC(),
        null
    );
  }

  UpdateCheckService(
      boolean enabled,
      String currentVersion,
      Duration cacheInterval,
      RemoteReleaseLookup releaseLookup,
      UpdateVersionComparator versionComparator,
      Executor executor,
      Clock clock,
      Runnable closeAction
  ) {
    this.enabled = enabled;
    this.currentVersion = Objects.requireNonNull(currentVersion, "currentVersion");
    this.cacheInterval = Objects.requireNonNull(cacheInterval, "cacheInterval");
    this.releaseLookup = Objects.requireNonNull(releaseLookup, "releaseLookup");
    this.versionComparator = Objects.requireNonNull(versionComparator, "versionComparator");
    this.executor = Objects.requireNonNull(executor, "executor");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.closeAction =
        closeAction != null ? closeAction : defaultCloseAction(executor);
  }

  public CompletableFuture<UpdateCheckResult> checkAsync() {
    if (!enabled) {
      return CompletableFuture.completedFuture(
          UpdateCheckResult.disabled(currentVersion)
      );
    }

    synchronized (lock) {
      Instant now = clock.instant();

      if (cached != null
          && now.isBefore(cached.checkedAt().plus(cacheInterval))) {
        return CompletableFuture.completedFuture(cached.result());
      }

      if (inFlight != null) {
        return inFlight;
      }

      CompletableFuture<UpdateCheckResult> future =
          CompletableFuture.supplyAsync(this::performLookupSafely, executor);
      inFlight = future;

      future.whenComplete((result, throwable) -> {
        UpdateCheckResult safeResult = result != null
            ? result
            : UpdateCheckResult.unavailable(
                currentVersion,
                "Update lookup failed."
            );

        synchronized (lock) {
          cached = new CacheEntry(clock.instant(), safeResult);
          if (inFlight == future) {
            inFlight = null;
          }
        }
      });

      return future;
    }
  }

  private UpdateCheckResult performLookupSafely() {
    try {
      List<String> tags = releaseLookup.fetchReleaseTags();

      String newest = tags.stream()
          .filter(versionComparator::isSupported)
          .max(versionComparator)
          .orElse(null);

      if (newest == null) {
        return UpdateCheckResult.unavailable(
            currentVersion,
            "No supported TreasureRun release version was returned."
        );
      }

      if (versionComparator.isNewer(newest, currentVersion)) {
        return UpdateCheckResult.updateAvailable(currentVersion, newest);
      }

      return UpdateCheckResult.current(currentVersion, newest);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return UpdateCheckResult.unavailable(
          currentVersion,
          "Update lookup was interrupted."
      );
    } catch (Exception exception) {
      return UpdateCheckResult.unavailable(
          currentVersion,
          "Update lookup unavailable: "
              + exception.getClass().getSimpleName()
      );
    }
  }

  @Override
  public void close() {
    closeAction.run();
  }

  private static ExecutorService newDaemonExecutor() {
    return Executors.newSingleThreadExecutor(runnable -> {
      Thread thread =
          new Thread(runnable, "TreasureRun-update-check");
      thread.setDaemon(true);
      return thread;
    });
  }

  private static Runnable defaultCloseAction(Executor executor) {
    if (executor instanceof ExecutorService service) {
      return service::shutdownNow;
    }
    return () -> {};
  }

  private record CacheEntry(
      Instant checkedAt,
      UpdateCheckResult result
  ) {}
}
