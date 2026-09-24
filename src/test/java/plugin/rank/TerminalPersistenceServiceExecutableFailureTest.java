package plugin.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import plugin.DatabaseRuntimeSettings;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

class TerminalPersistenceServiceExecutableFailureTest {

  @Test
  void databaseDisabledCompletesImmediatelyWithoutQueueing() throws Exception {
    TerminalPersistenceService service =
        new TerminalPersistenceService(disabledSettings(), quietLogger());

    try {
      CompletionStage<TerminalPersistenceService.PersistenceResult> stage =
          service.submit(write(1));

      assertTrue(stage.toCompletableFuture().isDone());
      assertEquals(
          TerminalPersistenceService.Status.SKIPPED_DATABASE_DISABLED,
          stage.toCompletableFuture().get(1, TimeUnit.SECONDS).status()
      );
      assertEquals(0, executorOf(service).getQueue().size());
    } finally {
      service.close();
    }
  }

  @Test
  void saturatedQueueRejectsImmediatelyWithoutBlockingCaller() throws Exception {
    TerminalPersistenceService service =
        new TerminalPersistenceService(enabledButUnusedSettings(), quietLogger());

    ThreadPoolExecutor executor = executorOf(service);
    CountDownLatch workerEntered = new CountDownLatch(1);
    CountDownLatch releaseWorker = new CountDownLatch(1);

    executor.execute(() -> {
      workerEntered.countDown();
      try {
        releaseWorker.await();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    });

    assertTrue(workerEntered.await(1, TimeUnit.SECONDS));

    try {
      List<CompletionStage<TerminalPersistenceService.PersistenceResult>> queued =
          new ArrayList<>();

      for (int i = 0; i < TerminalPersistenceService.QUEUE_CAPACITY; i++) {
        CompletionStage<TerminalPersistenceService.PersistenceResult> stage =
            service.submit(write(100 + i));

        assertFalse(
            stage.toCompletableFuture().isDone(),
            "queued request unexpectedly executed while worker was blocked"
        );

        queued.add(stage);
      }

      assertEquals(
          TerminalPersistenceService.QUEUE_CAPACITY,
          executor.getQueue().size()
      );

      CompletionStage<TerminalPersistenceService.PersistenceResult> overflow =
          service.submit(write(10_000));

      assertTrue(
          overflow.toCompletableFuture().isDone(),
          "queue-full submission must be rejected synchronously"
      );

      assertEquals(
          TerminalPersistenceService.Status.REJECTED,
          overflow.toCompletableFuture().get(1, TimeUnit.SECONDS).status()
      );
    } finally {
      service.close();
      releaseWorker.countDown();
    }
  }

  @Test
  void shutdownCompletesEveryQueuedFutureAsRejected() throws Exception {
    TerminalPersistenceService service =
        new TerminalPersistenceService(enabledButUnusedSettings(), quietLogger());

    ThreadPoolExecutor executor = executorOf(service);
    CountDownLatch workerEntered = new CountDownLatch(1);
    CountDownLatch releaseWorker = new CountDownLatch(1);

    executor.execute(() -> {
      workerEntered.countDown();
      try {
        releaseWorker.await();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    });

    assertTrue(workerEntered.await(1, TimeUnit.SECONDS));

    List<CompletionStage<TerminalPersistenceService.PersistenceResult>> queued =
        new ArrayList<>();

    for (int i = 0; i < 12; i++) {
      CompletionStage<TerminalPersistenceService.PersistenceResult> stage =
          service.submit(write(20_000 + i));

      assertFalse(stage.toCompletableFuture().isDone());
      queued.add(stage);
    }

    assertEquals(12, executor.getQueue().size());

    service.close();
    releaseWorker.countDown();

    for (CompletionStage<TerminalPersistenceService.PersistenceResult> stage : queued) {
      assertTrue(
          stage.toCompletableFuture().isDone(),
          "shutdown left a queued persistence future incomplete"
      );

      assertEquals(
          TerminalPersistenceService.Status.REJECTED,
          stage.toCompletableFuture().get(1, TimeUnit.SECONDS).status()
      );
    }

    assertFalse(service.isAccepting());
  }

  @Test
  void submissionAfterShutdownIsRejectedImmediately() throws Exception {
    TerminalPersistenceService service =
        new TerminalPersistenceService(enabledButUnusedSettings(), quietLogger());

    service.close();

    CompletionStage<TerminalPersistenceService.PersistenceResult> stage =
        service.submit(write(30_000));

    assertTrue(stage.toCompletableFuture().isDone());

    assertEquals(
        TerminalPersistenceService.Status.REJECTED,
        stage.toCompletableFuture().get(1, TimeUnit.SECONDS).status()
    );
  }

  private static DatabaseRuntimeSettings disabledSettings() {
    return new DatabaseRuntimeSettings(
        false,
        "127.0.0.1",
        1,
        "unused",
        "unused",
        "unused"
    );
  }

  /*
   * These settings are deliberately unusable.
   *
   * The queue/shutdown tests prevent the worker from reaching JDBC at all.
   * If they unexpectedly execute a DB task, the test must fail rather than
   * relying on a real database.
   */
  private static DatabaseRuntimeSettings enabledButUnusedSettings() {
    return new DatabaseRuntimeSettings(
        true,
        "127.0.0.1",
        1,
        "unused",
        "unused",
        "unused"
    );
  }

  private static TerminalPersistenceService.TerminalWrite write(int ordinal) {
    UUID eventId =
        UUID.nameUUIDFromBytes(
            ("db-h3a-event-" + ordinal).getBytes(StandardCharsets.UTF_8)
        );

    UUID playerId =
        UUID.nameUUIDFromBytes(
            ("db-h3a-player-" + ordinal).getBytes(StandardCharsets.UTF_8)
        );

    return new TerminalPersistenceService.TerminalWrite(
        eventId,
        Instant.parse("2026-09-23T00:00:00Z").plusSeconds(ordinal),
        playerId,
        "FailureProofPlayer" + ordinal,
        "en",
        100 + ordinal,
        42L,
        "Normal",
        "SUCCESS",
        true,
        true,
        42_000L,
        null
    );
  }

  private static ThreadPoolExecutor executorOf(
      TerminalPersistenceService service
  ) throws Exception {
    Field field =
        TerminalPersistenceService.class.getDeclaredField("executor");

    field.setAccessible(true);

    return (ThreadPoolExecutor) field.get(service);
  }

  private static Logger quietLogger() {
    Logger logger =
        Logger.getLogger(
            TerminalPersistenceServiceExecutableFailureTest.class.getName()
                + "."
                + UUID.randomUUID()
        );

    logger.setUseParentHandlers(false);
    logger.setLevel(Level.OFF);
    return logger;
  }
}
