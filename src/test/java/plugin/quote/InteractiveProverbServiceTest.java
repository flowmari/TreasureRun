package plugin.quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import plugin.DatabaseRuntimeSettings;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

class InteractiveProverbServiceTest {

  @Test
  void coalescesSamePlayerBookReadsIntoOneBlockingFlight() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();

    InteractiveProverbService.Backend backend = new StubBackend() {
      @Override
      public InteractiveProverbService.BookData loadBookData(
          UUID playerId, int recentLimit, int favoritesLimit) throws Exception {
        calls.incrementAndGet();
        entered.countDown();
        assertTrue(release.await(2, TimeUnit.SECONDS));
        return new InteractiveProverbService.BookData(List.of("recent"), List.of("favorite"));
      }
    };

    try (InteractiveProverbService service = service(true, backend)) {
      UUID playerId = UUID.randomUUID();
      var first = service.loadBookData(playerId, 20, 20).toCompletableFuture();
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      var second = service.loadBookData(playerId, 20, 20).toCompletableFuture();

      assertFalse(first.isDone());
      assertFalse(second.isDone());
      release.countDown();

      assertEquals(InteractiveProverbService.Status.SUCCESS, first.get(2, TimeUnit.SECONDS).status());
      assertEquals(InteractiveProverbService.Status.SUCCESS, second.get(2, TimeUnit.SECONDS).status());
      assertEquals(1, calls.get());
    }
  }

  @Test
  void closeRejectsLateInFlightPublication() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    UUID playerId = UUID.randomUUID();

    InteractiveProverbService.Backend backend = new StubBackend() {
      @Override
      public InteractiveProverbService.BookData loadBookData(
          UUID ignored, int recentLimit, int favoritesLimit) throws Exception {
        entered.countDown();
        release.await(2, TimeUnit.SECONDS);
        return new InteractiveProverbService.BookData(List.of("late"), List.of());
      }
    };

    InteractiveProverbService service = service(true, backend);
    var future = service.loadBookData(playerId, 20, 20).toCompletableFuture();
    assertTrue(entered.await(2, TimeUnit.SECONDS));

    service.close();
    release.countDown();

    assertEquals(
        InteractiveProverbService.Status.REJECTED,
        future.get(2, TimeUnit.SECONDS).status()
    );
    assertTrue(service.recentSnapshot(playerId, 20).isEmpty());
  }

  @Test
  void disabledDatabaseNeverCallsBackend() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    InteractiveProverbService.Backend backend = new StubBackend() {
      @Override
      public List<String> loadFavorites(UUID playerId, int limit) {
        calls.incrementAndGet();
        return List.of("unexpected");
      }
    };

    try (InteractiveProverbService service = service(false, backend)) {
      var result = service.loadFavorites(UUID.randomUUID(), 20)
          .toCompletableFuture().get(2, TimeUnit.SECONDS);
      assertEquals(InteractiveProverbService.Status.DATABASE_DISABLED, result.status());
      assertEquals(0, calls.get());
    }
  }

  @Test
  void mutationBackendIsSerializedBySingleBoundedWorker() throws Exception {
    AtomicInteger concurrent = new AtomicInteger();
    AtomicInteger maxConcurrent = new AtomicInteger();
    CountDownLatch release = new CountDownLatch(1);

    InteractiveProverbService.Backend backend = new StubBackend() {
      @Override
      public boolean favoriteLatest(UUID playerId) throws Exception {
        int active = concurrent.incrementAndGet();
        maxConcurrent.accumulateAndGet(active, Math::max);
        try {
          release.await(2, TimeUnit.SECONDS);
          return true;
        } finally {
          concurrent.decrementAndGet();
        }
      }
    };

    try (InteractiveProverbService service = service(true, backend)) {
      var first = service.favoriteLatest(UUID.randomUUID()).toCompletableFuture();
      var second = service.favoriteLatest(UUID.randomUUID()).toCompletableFuture();
      Thread.sleep(50L);
      release.countDown();

      assertEquals(InteractiveProverbService.Status.SUCCESS, first.get(2, TimeUnit.SECONDS).status());
      assertEquals(InteractiveProverbService.Status.SUCCESS, second.get(2, TimeUnit.SECONDS).status());
      assertEquals(1, maxConcurrent.get());
    }
  }

  @Test
  void backendFailureIsFailedAndDoesNotPublishFallbackSnapshot() throws Exception {
    UUID playerId = UUID.randomUUID();
    InteractiveProverbService.Backend backend = new StubBackend() {
      @Override
      public InteractiveProverbService.BookData loadBookData(
          UUID ignored, int recentLimit, int favoritesLimit) throws Exception {
        throw new java.sql.SQLException("synthetic database outage");
      }
    };

    try (InteractiveProverbService service = service(true, backend)) {
      var result = service.loadBookData(playerId, 20, 20)
          .toCompletableFuture().get(2, TimeUnit.SECONDS);
      assertEquals(InteractiveProverbService.Status.FAILED, result.status());
      assertTrue(service.recentSnapshot(playerId, 20).isEmpty());
    }
  }

  @Test
  void queueSaturationRejectsInsteadOfBlockingCaller() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    InteractiveProverbService.Backend backend = new StubBackend() {
      @Override
      public boolean favoriteLatest(UUID playerId) throws Exception {
        entered.countDown();
        release.await(2, TimeUnit.SECONDS);
        return true;
      }
    };

    try (InteractiveProverbService service = service(true, backend)) {
      var running = service.favoriteLatest(UUID.randomUUID()).toCompletableFuture();
      assertTrue(entered.await(2, TimeUnit.SECONDS));

      for (int i = 0; i < InteractiveProverbService.QUEUE_CAPACITY; i++) {
        service.favoriteLatest(UUID.randomUUID());
      }

      var rejected = service.favoriteLatest(UUID.randomUUID()).toCompletableFuture();
      assertEquals(
          InteractiveProverbService.Status.REJECTED,
          rejected.get(2, TimeUnit.SECONDS).status()
      );

      release.countDown();
      assertEquals(
          InteractiveProverbService.Status.SUCCESS,
          running.get(2, TimeUnit.SECONDS).status()
      );
    }
  }

  @Test
  void businessFalseRemainsSuccessfulWhileInfrastructureFailureIsFailed() throws Exception {
    InteractiveProverbService.Backend backend = new StubBackend() {
      @Override
      public boolean favoriteLatest(UUID playerId) {
        return false;
      }
    };

    try (InteractiveProverbService service = service(true, backend)) {
      var result = service.favoriteLatest(UUID.randomUUID())
          .toCompletableFuture().get(2, TimeUnit.SECONDS);
      assertEquals(InteractiveProverbService.Status.SUCCESS, result.status());
      assertEquals(Boolean.FALSE, result.value());
    }
  }

  private static InteractiveProverbService service(
      boolean enabled,
      InteractiveProverbService.Backend backend
  ) {
    return new InteractiveProverbService(
        new DatabaseRuntimeSettings(enabled, "localhost", 3306, "test", "user", "password"),
        backend,
        Logger.getLogger("InteractiveProverbServiceTest")
    );
  }

  private abstract static class StubBackend implements InteractiveProverbService.Backend {
    @Override
    public InteractiveProverbService.BookData loadBookData(
        UUID playerId, int recentLimit, int favoritesLimit) throws Exception {
      return InteractiveProverbService.BookData.empty();
    }

    @Override
    public List<String> loadFavorites(UUID playerId, int limit) throws Exception {
      return List.of();
    }

    @Override
    public boolean favoriteLatest(UUID playerId) throws Exception {
      return false;
    }

    @Override
    public boolean deleteFavorite(UUID playerId, int favoriteId) throws Exception {
      return false;
    }
  }
}
