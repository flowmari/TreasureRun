package plugin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerReturnLedgerTest {

  @TempDir Path tempDir;

  @Test
  void persistsAndReloadsOneImmutablePendingReturn() {
    Path path = tempDir.resolve("pending-player-returns.ledger");
    PlayerReturnLedger ledger = new PlayerReturnLedger(path);
    assertTrue(ledger.open().available());

    PlayerReturnRecord record = record(UUID.randomUUID(), UUID.randomUUID(), "world", 10.25, 64, -3.5);
    assertEquals(PlayerReturnLedger.PutCode.SAVED, ledger.putPending(record).code());

    PlayerReturnLedger reopened = new PlayerReturnLedger(path);
    assertEquals(PlayerReturnLedger.OpenCode.AVAILABLE_LOADED, reopened.open().code());
    assertEquals(record, reopened.pendingRecord(record.playerId()).orElseThrow());
  }

  @Test
  void identicalDestinationIsIdempotentButConflictingDestinationIsRejected() {
    Path path = tempDir.resolve("pending-player-returns.ledger");
    PlayerReturnLedger ledger = new PlayerReturnLedger(path);
    ledger.open();

    UUID playerId = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    PlayerReturnRecord first = record(playerId, worldId, "world", 1, 2, 3);
    PlayerReturnRecord sameDestination = record(playerId, worldId, "world", 1, 2, 3);
    PlayerReturnRecord conflict = record(playerId, worldId, "world", 4, 5, 6);

    assertEquals(PlayerReturnLedger.PutCode.SAVED, ledger.putPending(first).code());
    PlayerReturnLedger.PutResult idempotent = ledger.putPending(sameDestination);
    assertEquals(PlayerReturnLedger.PutCode.ALREADY_PENDING, idempotent.code());
    assertEquals(first.recoveryId(), idempotent.record().orElseThrow().recoveryId());

    assertEquals(PlayerReturnLedger.PutCode.CONFLICT, ledger.putPending(conflict).code());
    assertEquals(first, ledger.pendingRecord(playerId).orElseThrow());
  }

  @Test
  void completionRequiresTheCurrentRecoveryIdAndPersistsRemoval() {
    Path path = tempDir.resolve("pending-player-returns.ledger");
    PlayerReturnLedger ledger = new PlayerReturnLedger(path);
    ledger.open();
    PlayerReturnRecord record = record(UUID.randomUUID(), UUID.randomUUID(), "world", 1, 2, 3);
    ledger.putPending(record);

    assertEquals(
        PlayerReturnLedger.CompleteCode.RECOVERY_ID_MISMATCH,
        ledger.complete(record.playerId(), UUID.randomUUID()).code()
    );
    assertTrue(ledger.pendingRecord(record.playerId()).isPresent());

    assertEquals(
        PlayerReturnLedger.CompleteCode.CLEARED,
        ledger.complete(record.playerId(), record.recoveryId()).code()
    );
    assertTrue(ledger.pendingRecord(record.playerId()).isEmpty());

    PlayerReturnLedger reopened = new PlayerReturnLedger(path);
    reopened.open();
    assertTrue(reopened.pendingRecord(record.playerId()).isEmpty());
  }

  @Test
  void corruptBytesAreQuarantinedAndNewTeleportsMustFailClosed() throws Exception {
    Path path = tempDir.resolve("pending-player-returns.ledger");
    byte[] corrupt = "not-a-valid-ledger\nkeep-this-evidence\n".getBytes(StandardCharsets.UTF_8);
    Files.write(path, corrupt);

    Clock fixed = Clock.fixed(Instant.parse("2026-08-07T11:17:47Z"), ZoneOffset.UTC);
    PlayerReturnLedger ledger = new PlayerReturnLedger(path, fixed, (target, bytes) -> Files.write(target, bytes));
    PlayerReturnLedger.OpenResult result = ledger.open();

    assertEquals(PlayerReturnLedger.OpenCode.UNAVAILABLE_CORRUPT, result.code());
    assertFalse(ledger.isAvailable());
    Path quarantine = result.quarantinePath().orElseThrow();
    assertTrue(Files.exists(quarantine));
    assertArrayEquals(corrupt, Files.readAllBytes(quarantine));
    assertTrue(Files.exists(path));
    assertArrayEquals(corrupt, Files.readAllBytes(path));

    PlayerReturnLedger reopened = new PlayerReturnLedger(path, fixed, (target, bytes) -> Files.write(target, bytes));
    assertEquals(PlayerReturnLedger.OpenCode.UNAVAILABLE_CORRUPT, reopened.open().code());

    PlayerReturnRecord record = record(UUID.randomUUID(), UUID.randomUUID(), "world", 1, 2, 3);
    assertEquals(PlayerReturnLedger.PutCode.STORAGE_UNAVAILABLE, ledger.putPending(record).code());
  }

  @Test
  void failedAtomicSnapshotDoesNotPublishTheCandidateAndDisablesNewWrites() {
    Path path = tempDir.resolve("pending-player-returns.ledger");
    AtomicBoolean failWrites = new AtomicBoolean(false);
    PlayerReturnLedger.SnapshotWriter writer = (target, bytes) -> {
      if (failWrites.get()) throw new IOException("simulated durable write failure");
      Files.write(target, bytes);
    };

    PlayerReturnLedger ledger = new PlayerReturnLedger(path, Clock.systemUTC(), writer);
    ledger.open();
    PlayerReturnRecord first = record(UUID.randomUUID(), UUID.randomUUID(), "world", 1, 2, 3);
    assertEquals(PlayerReturnLedger.PutCode.SAVED, ledger.putPending(first).code());

    failWrites.set(true);
    PlayerReturnRecord second = record(UUID.randomUUID(), UUID.randomUUID(), "world", 4, 5, 6);
    assertEquals(PlayerReturnLedger.PutCode.STORAGE_UNAVAILABLE, ledger.putPending(second).code());
    assertFalse(ledger.isAvailable());
    assertTrue(ledger.pendingRecord(second.playerId()).isEmpty());
    assertEquals(first, ledger.pendingRecord(first.playerId()).orElseThrow());
  }

  @Test
  void serializedSnapshotIsStableForTheSameRecordsRegardlessOfInsertionOrder() {
    UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    PlayerReturnRecord first = record(firstId, UUID.randomUUID(), "world one", 1, 2, 3);
    PlayerReturnRecord second = record(secondId, UUID.randomUUID(), "world two", 4, 5, 6);

    java.util.Map<UUID, PlayerReturnRecord> a = new java.util.LinkedHashMap<>();
    a.put(secondId, second);
    a.put(firstId, first);
    java.util.Map<UUID, PlayerReturnRecord> b = new java.util.LinkedHashMap<>();
    b.put(firstId, first);
    b.put(secondId, second);

    assertArrayEquals(PlayerReturnLedger.serialize(a), PlayerReturnLedger.serialize(b));
    assertNotEquals(0, PlayerReturnLedger.serialize(a).length);
  }

  @Test
  void batchPersistsTwoParticipantsWithOneSnapshotWriteAndReloadsBoth() {
    Path path = tempDir.resolve("pending-player-returns.ledger");
    AtomicInteger writes = new AtomicInteger();
    PlayerReturnLedger ledger = new PlayerReturnLedger(
        path,
        Clock.systemUTC(),
        (target, bytes) -> {
          writes.incrementAndGet();
          Files.write(target, bytes);
        }
    );
    ledger.open();

    PlayerReturnRecord first =
        record(UUID.randomUUID(), UUID.randomUUID(), "world", 1, 2, 3);
    PlayerReturnRecord second =
        record(UUID.randomUUID(), UUID.randomUUID(), "world", 4, 5, 6);

    PlayerReturnLedger.PutBatchResult result =
        ledger.putPendingBatch(List.of(first, second));

    assertEquals(PlayerReturnLedger.PutBatchCode.SAVED, result.code());
    assertEquals(1, writes.get());
    assertEquals(2, result.records().size());

    PlayerReturnLedger reopened = new PlayerReturnLedger(path);
    assertTrue(reopened.open().available());
    assertEquals(first, reopened.pendingRecord(first.playerId()).orElseThrow());
    assertEquals(second, reopened.pendingRecord(second.playerId()).orElseThrow());
  }

  @Test
  void conflictingBatchPublishesNoNewParticipant() {
    Path path = tempDir.resolve("pending-player-returns.ledger");
    PlayerReturnLedger ledger = new PlayerReturnLedger(path);
    ledger.open();

    UUID existingPlayer = UUID.randomUUID();
    UUID world = UUID.randomUUID();
    PlayerReturnRecord existing = record(existingPlayer, world, "world", 1, 2, 3);
    ledger.putPending(existing);

    PlayerReturnRecord newParticipant =
        record(UUID.randomUUID(), world, "world", 4, 5, 6);
    PlayerReturnRecord conflict =
        record(existingPlayer, world, "world", 7, 8, 9);

    PlayerReturnLedger.PutBatchResult result =
        ledger.putPendingBatch(List.of(newParticipant, conflict));

    assertEquals(PlayerReturnLedger.PutBatchCode.CONFLICT, result.code());
    assertTrue(ledger.pendingRecord(newParticipant.playerId()).isEmpty());
    assertEquals(existing, ledger.pendingRecord(existingPlayer).orElseThrow());
  }

  @Test
  void failedBatchSnapshotPublishesNoParticipantAndFailsClosed() {
    Path path = tempDir.resolve("pending-player-returns.ledger");
    PlayerReturnLedger ledger = new PlayerReturnLedger(
        path,
        Clock.systemUTC(),
        (target, bytes) -> {
          throw new IOException("simulated batch write failure");
        }
    );
    ledger.open();

    PlayerReturnRecord first =
        record(UUID.randomUUID(), UUID.randomUUID(), "world", 1, 2, 3);
    PlayerReturnRecord second =
        record(UUID.randomUUID(), UUID.randomUUID(), "world", 4, 5, 6);

    PlayerReturnLedger.PutBatchResult result =
        ledger.putPendingBatch(List.of(first, second));

    assertEquals(PlayerReturnLedger.PutBatchCode.STORAGE_UNAVAILABLE, result.code());
    assertFalse(ledger.isAvailable());
    assertTrue(ledger.pendingRecord(first.playerId()).isEmpty());
    assertTrue(ledger.pendingRecord(second.playerId()).isEmpty());
  }

  @Test
  void batchRejectsDuplicatePlayersBeforeAnyWrite() {
    Path path = tempDir.resolve("pending-player-returns.ledger");
    AtomicInteger writes = new AtomicInteger();
    PlayerReturnLedger ledger = new PlayerReturnLedger(
        path,
        Clock.systemUTC(),
        (target, bytes) -> {
          writes.incrementAndGet();
          Files.write(target, bytes);
        }
    );
    ledger.open();

    UUID player = UUID.randomUUID();
    UUID world = UUID.randomUUID();
    PlayerReturnRecord first = record(player, world, "world", 1, 2, 3);
    PlayerReturnRecord second = record(player, world, "world", 1, 2, 3);

    PlayerReturnLedger.PutBatchResult result =
        ledger.putPendingBatch(List.of(first, second));

    assertEquals(PlayerReturnLedger.PutBatchCode.DUPLICATE_PLAYER, result.code());
    assertEquals(0, writes.get());
    assertTrue(ledger.pendingRecords().isEmpty());
  }

  private static PlayerReturnRecord record(
      UUID playerId,
      UUID worldId,
      String worldName,
      double x,
      double y,
      double z
  ) {
    return new PlayerReturnRecord(
        PlayerReturnRecord.CURRENT_SCHEMA_VERSION,
        UUID.randomUUID(),
        playerId,
        worldId,
        worldName,
        x,
        y,
        z,
        90.0f,
        12.5f,
        Instant.parse("2026-08-07T11:00:00Z")
    );
  }
}
