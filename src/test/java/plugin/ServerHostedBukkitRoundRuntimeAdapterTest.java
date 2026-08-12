package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerHostedBukkitRoundRuntimeAdapterTest {

  @TempDir Path tempDir;

  @Test
  void resolvesCompleteReturnDestinationWithoutWritingLedger() {
    UUID playerId = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    Player player = mock(Player.class);
    World world = mock(World.class);
    Location current = new Location(world, 12.5, 70.0, -4.25, 15.0f, 25.0f);
    when(player.isOnline()).thenReturn(true);
    when(player.getLocation()).thenReturn(current);
    when(world.getUID()).thenReturn(worldId);
    when(world.getName()).thenReturn("world");

    PlayerReturnLedger ledger = ledger("resolve");
    ServerHostedBukkitRoundRuntimeAdapter adapter = adapter(
        ledger,
        new PlayerReturnRecoveryService(ledger),
        ignored -> player,
        ignored -> world,
        ignored -> world
    );

    Optional<PlayerReturnRecord> resolved = adapter.resolveReturnDestination(playerId);

    assertTrue(resolved.isPresent());
    assertEquals(playerId, resolved.orElseThrow().playerId());
    assertEquals(worldId, resolved.orElseThrow().worldId());
    assertEquals("world", resolved.orElseThrow().worldName());
    assertEquals(12.5, resolved.orElseThrow().x());
    assertTrue(ledger.pendingRecords().isEmpty());
  }

  @Test
  void delegatesPreparationChestTeleportAndActivationToSeparatedApis() {
    UUID playerId = UUID.randomUUID();
    Player player = mock(Player.class);
    World world = mock(World.class);
    GameStageManager stage = mock(GameStageManager.class);
    TreasureChestManager chests = mock(TreasureChestManager.class);
    Location arena = new Location(world, 0.0, 64.0, 0.0);
    when(player.isOnline()).thenReturn(true);
    when(stage.prepareSeasideStage(player)).thenReturn(arena);
    when(chests.spawnChests(arena, "Hard", 12)).thenReturn(true);
    when(stage.teleportPlayerToPreparedStage(player, arena)).thenReturn(true);

    PlayerReturnLedger ledger = ledger("delegate");
    ServerHostedBukkitRoundRuntimeAdapter adapter = new ServerHostedBukkitRoundRuntimeAdapter(
        () -> stage,
        () -> chests,
        ledger,
        new PlayerReturnRecoveryService(ledger),
        () -> "Hard",
        () -> 12,
        ignored -> player,
        ignored -> world,
        ignored -> world,
        fixedClock()
    );

    assertEquals(arena, adapter.prepareArena(playerId));
    assertTrue(adapter.placeChests(arena));
    assertTrue(adapter.teleport(playerId, arena));
    adapter.activate(arena);

    verify(stage).prepareSeasideStage(player);
    verify(chests).spawnChests(arena, "Hard", 12);
    verify(stage).teleportPlayerToPreparedStage(player, arena);
    verify(stage).activatePreparedStage(arena);
  }

  @Test
  void cleanupReturnsOnlineParticipantAndClearsOnlyAfterSuccessfulTeleport() {
    UUID playerId = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    World world = mock(World.class);
    Player player = mock(Player.class);
    when(world.getUID()).thenReturn(worldId);
    when(world.getName()).thenReturn("world");
    when(player.isOnline()).thenReturn(true);
    when(player.teleport(any(Location.class))).thenReturn(true);

    PlayerReturnLedger ledger = ledger("online-cleanup");
    PlayerReturnRecord record = record(playerId, worldId);
    assertTrue(ledger.putPending(record).accepted());
    GameStageManager stage = mock(GameStageManager.class);
    TreasureChestManager chests = mock(TreasureChestManager.class);
    ServerHostedBukkitRoundRuntimeAdapter adapter = new ServerHostedBukkitRoundRuntimeAdapter(
        () -> stage,
        () -> chests,
        ledger,
        new PlayerReturnRecoveryService(ledger),
        () -> "Normal",
        () -> 10,
        ignored -> player,
        ignored -> world,
        ignored -> world,
        fixedClock()
    );

    boolean completed = adapter.cleanup(new ServerHostedRoundCoordinator.CleanupClaim(
        ServerHostedRoundCoordinator.ResetCause.STOP_REQUESTED,
        List.of(playerId)
    ));

    assertTrue(completed);
    assertTrue(ledger.pendingRecord(playerId).isEmpty());
    verify(player).teleport(any(Location.class));
    verify(chests).removeAllChests();
    verify(stage).clearDifficultyBlocks();
    verify(stage).clearShopEntities();
  }

  @Test
  void cleanupAllowsOfflineParticipantToRemainDurablyPending() {
    UUID playerId = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    PlayerReturnLedger ledger = ledger("offline-cleanup");
    PlayerReturnRecord record = record(playerId, worldId);
    assertTrue(ledger.putPending(record).accepted());
    GameStageManager stage = mock(GameStageManager.class);
    TreasureChestManager chests = mock(TreasureChestManager.class);

    ServerHostedBukkitRoundRuntimeAdapter adapter = new ServerHostedBukkitRoundRuntimeAdapter(
        () -> stage,
        () -> chests,
        ledger,
        new PlayerReturnRecoveryService(ledger),
        () -> "Normal",
        () -> 10,
        ignored -> null,
        ignored -> null,
        ignored -> null,
        fixedClock()
    );

    boolean completed = adapter.cleanup(new ServerHostedRoundCoordinator.CleanupClaim(
        ServerHostedRoundCoordinator.ResetCause.PARTICIPANT_DISCONNECTED,
        List.of(playerId)
    ));

    assertTrue(completed);
    assertTrue(ledger.pendingRecord(playerId).isPresent());
  }

  @Test
  void disconnectCleanupKeepsKnownUnavailableParticipantPendingEvenIfBukkitStillReportsOnline() {
    UUID playerId = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    PlayerReturnLedger ledger = ledger("quit-event-online-window");
    PlayerReturnRecord record = record(playerId, worldId);
    assertTrue(ledger.putPending(record).accepted());

    Player player = mock(Player.class);
    when(player.isOnline()).thenReturn(true);
    when(player.teleport(any(Location.class))).thenReturn(true);

    ServerHostedBukkitRoundRuntimeAdapter adapter = adapter(
        ledger,
        new PlayerReturnRecoveryService(ledger),
        ignored -> player,
        ignored -> null,
        ignored -> null
    );

    boolean completed = adapter.cleanup(
        new ServerHostedRoundCoordinator.CleanupClaim(
            ServerHostedRoundCoordinator.ResetCause.PARTICIPANT_DISCONNECTED,
            List.of(playerId)
        ),
        Set.of(playerId)
    );

    assertTrue(completed);
    assertTrue(ledger.pendingRecord(playerId).isPresent());
    verify(player, never()).teleport(any(Location.class));
  }

  @Test
  void cleanupFailsClosedWhenOnlineReturnTeleportFails() {
    UUID playerId = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    World world = mock(World.class);
    Player player = mock(Player.class);
    when(world.getUID()).thenReturn(worldId);
    when(world.getName()).thenReturn("world");
    when(player.isOnline()).thenReturn(true);
    when(player.teleport(any(Location.class))).thenReturn(false);

    PlayerReturnLedger ledger = ledger("failed-return");
    PlayerReturnRecord record = record(playerId, worldId);
    assertTrue(ledger.putPending(record).accepted());
    ServerHostedBukkitRoundRuntimeAdapter adapter = adapter(
        ledger,
        new PlayerReturnRecoveryService(ledger),
        ignored -> player,
        ignored -> world,
        ignored -> world
    );

    boolean completed = adapter.cleanup(new ServerHostedRoundCoordinator.CleanupClaim(
        ServerHostedRoundCoordinator.ResetCause.PLUGIN_DISABLED,
        List.of(playerId)
    ));

    assertFalse(completed);
    assertTrue(ledger.pendingRecord(playerId).isPresent());
  }

  @Test
  void rollbackArtifactCleanupIsSafeToRepeat() {
    PlayerReturnLedger ledger = ledger("idempotent-artifacts");
    GameStageManager stage = mock(GameStageManager.class);
    TreasureChestManager chests = mock(TreasureChestManager.class);
    ServerHostedBukkitRoundRuntimeAdapter adapter = new ServerHostedBukkitRoundRuntimeAdapter(
        () -> stage,
        () -> chests,
        ledger,
        new PlayerReturnRecoveryService(ledger),
        () -> "Normal",
        () -> 10,
        ignored -> null,
        ignored -> null,
        ignored -> null,
        fixedClock()
    );

    adapter.rollbackArena(null);
    adapter.rollbackArena(null);

    verify(chests, times(2)).removeAllChests();
    verify(stage, times(2)).clearDifficultyBlocks();
    verify(stage, times(2)).clearShopEntities();
  }

  private ServerHostedBukkitRoundRuntimeAdapter adapter(
      PlayerReturnLedger ledger,
      PlayerReturnRecoveryService recovery,
      java.util.function.Function<UUID, Player> playerLookup,
      java.util.function.Function<UUID, World> worldById,
      java.util.function.Function<String, World> worldByName
  ) {
    GameStageManager stage = mock(GameStageManager.class);
    TreasureChestManager chests = mock(TreasureChestManager.class);
    return new ServerHostedBukkitRoundRuntimeAdapter(
        () -> stage,
        () -> chests,
        ledger,
        recovery,
        () -> "Normal",
        () -> 10,
        playerLookup,
        worldById,
        worldByName,
        fixedClock()
    );
  }

  private PlayerReturnLedger ledger(String name) {
    PlayerReturnLedger ledger = new PlayerReturnLedger(tempDir.resolve(name + ".ledger"));
    assertTrue(ledger.open().available());
    return ledger;
  }

  private PlayerReturnRecord record(UUID playerId, UUID worldId) {
    return new PlayerReturnRecord(
        PlayerReturnRecord.CURRENT_SCHEMA_VERSION,
        UUID.randomUUID(),
        playerId,
        worldId,
        "world",
        10.0,
        70.0,
        20.0,
        30.0f,
        40.0f,
        Instant.parse("2026-08-09T00:00:00Z")
    );
  }

  private Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-08-09T00:00:00Z"), ZoneOffset.UTC);
  }
}
