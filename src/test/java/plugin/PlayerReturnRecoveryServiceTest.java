package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerReturnRecoveryServiceTest {

  @TempDir Path tempDir;

  @Test
  void successfulReturnClearsTheDurableRecordOnlyAfterTheAttemptSucceeds() {
    PlayerReturnLedger ledger = ledgerWithOneRecord();
    PlayerReturnRecord record = ledger.pendingRecords().get(0);
    PlayerReturnRecoveryService service = new PlayerReturnRecoveryService(ledger);
    AtomicInteger attempts = new AtomicInteger();

    PlayerReturnRecoveryService.RecoveryResult result = service.recover(record.playerId(), pending -> {
      attempts.incrementAndGet();
      assertEquals(record, pending);
      assertTrue(ledger.pendingRecord(record.playerId()).isPresent());
      return PlayerReturnRecoveryService.AttemptCode.RETURNED;
    });

    assertEquals(1, attempts.get());
    assertEquals(PlayerReturnRecoveryService.RecoveryCode.RETURNED_AND_CLEARED, result.code());
    assertTrue(ledger.pendingRecord(record.playerId()).isEmpty());
  }

  @Test
  void failedOrDeferredReturnKeepsTheRecordForReplay() {
    PlayerReturnLedger ledger = ledgerWithOneRecord();
    PlayerReturnRecord record = ledger.pendingRecords().get(0);
    PlayerReturnRecoveryService service = new PlayerReturnRecoveryService(ledger);

    assertEquals(
        PlayerReturnRecoveryService.RecoveryCode.DESTINATION_UNAVAILABLE,
        service.recover(
            record.playerId(),
            pending -> PlayerReturnRecoveryService.AttemptCode.DESTINATION_UNAVAILABLE
        ).code()
    );
    assertEquals(record, ledger.pendingRecord(record.playerId()).orElseThrow());

    assertEquals(
        PlayerReturnRecoveryService.RecoveryCode.TELEPORT_FAILED,
        service.recover(
            record.playerId(),
            pending -> PlayerReturnRecoveryService.AttemptCode.TELEPORT_FAILED
        ).code()
    );
    assertEquals(record, ledger.pendingRecord(record.playerId()).orElseThrow());
  }

  @Test
  void thrownReturnAttemptKeepsTheRecord() {
    PlayerReturnLedger ledger = ledgerWithOneRecord();
    PlayerReturnRecord record = ledger.pendingRecords().get(0);
    PlayerReturnRecoveryService service = new PlayerReturnRecoveryService(ledger);

    PlayerReturnRecoveryService.RecoveryResult result = service.recover(
        record.playerId(),
        pending -> { throw new IOException("simulated teleport exception"); }
    );

    assertEquals(PlayerReturnRecoveryService.RecoveryCode.ATTEMPT_ERROR, result.code());
    assertEquals(record, ledger.pendingRecord(record.playerId()).orElseThrow());
  }

  @Test
  void returnSuccessWithDurableClearFailureRemainsReplaySafe() throws Exception {
    Path path = tempDir.resolve("pending-player-returns.ledger");
    AtomicBoolean failWrites = new AtomicBoolean(false);
    PlayerReturnLedger.SnapshotWriter writer = (target, bytes) -> {
      if (failWrites.get()) throw new IOException("simulated clear failure");
      Files.write(target, bytes);
    };
    PlayerReturnLedger ledger = new PlayerReturnLedger(path, Clock.systemUTC(), writer);
    ledger.open();
    PlayerReturnRecord record = record();
    ledger.putPending(record);
    failWrites.set(true);

    PlayerReturnRecoveryService service = new PlayerReturnRecoveryService(ledger);
    PlayerReturnRecoveryService.RecoveryResult result = service.recover(
        record.playerId(),
        pending -> PlayerReturnRecoveryService.AttemptCode.RETURNED
    );

    assertEquals(PlayerReturnRecoveryService.RecoveryCode.RETURNED_RECORD_RETAINED, result.code());
    assertTrue(result.playerWasReturned());
    assertFalse(ledger.isAvailable());
    assertEquals(record, ledger.pendingRecord(record.playerId()).orElseThrow());
  }

  @Test
  void noPendingRecordDoesNotInvokeTheReturnCallback() {
    Path path = tempDir.resolve("pending-player-returns.ledger");
    PlayerReturnLedger ledger = new PlayerReturnLedger(path);
    ledger.open();
    PlayerReturnRecoveryService service = new PlayerReturnRecoveryService(ledger);
    AtomicInteger attempts = new AtomicInteger();

    PlayerReturnRecoveryService.RecoveryResult result = service.recover(UUID.randomUUID(), pending -> {
      attempts.incrementAndGet();
      return PlayerReturnRecoveryService.AttemptCode.RETURNED;
    });

    assertEquals(PlayerReturnRecoveryService.RecoveryCode.NO_PENDING, result.code());
    assertEquals(0, attempts.get());
  }

  @Test
  void pluginIntegrationPersistsBeforeStageTeleportAndKeepsProtectedSurfacesOutOfScope() throws Exception {
    String plugin = Files.readString(
        Path.of("src/main/java/plugin/TreasureRunMultiChestPlugin.java"),
        StandardCharsets.UTF_8
    );

    int durableWrite = plugin.indexOf("persistPlayerReturnBeforeArenaTeleport(player, originalReturnLocation)");
    int stageTeleport = plugin.indexOf("gameStageManager.buildSeasideStageAndTeleport(player)");
    assertTrue(durableWrite >= 0);
    assertTrue(stageTeleport > durableWrite);
    assertTrue(plugin.contains("pending-player-returns.ledger"));
    assertTrue(plugin.contains("playerReturnRecoveryService.recover"));
    assertTrue(plugin.contains("Bukkit.getWorld(record.worldId())"));
    assertTrue(plugin.contains("Bukkit.getWorld(record.worldName())"));
    assertTrue(plugin.contains("if (!playerReturnLedger.isAvailable())"));
    assertTrue(plugin.contains("public void onWorldLoad(WorldLoadEvent event)"));

    assertFalse(plugin.contains("PlayerReturnRecord.CURRENT_SCHEMA_VERSION + 1"));
  }

  private PlayerReturnLedger ledgerWithOneRecord() {
    Path path = tempDir.resolve("pending-player-returns-" + UUID.randomUUID() + ".ledger");
    PlayerReturnLedger ledger = new PlayerReturnLedger(path);
    ledger.open();
    PlayerReturnRecord record = record();
    assertEquals(PlayerReturnLedger.PutCode.SAVED, ledger.putPending(record).code());
    return ledger;
  }

  private static PlayerReturnRecord record() {
    return new PlayerReturnRecord(
        PlayerReturnRecord.CURRENT_SCHEMA_VERSION,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "world",
        12.25,
        70.0,
        -9.5,
        180.0f,
        -5.0f,
        Instant.parse("2026-08-07T11:00:00Z")
    );
  }
}
