package plugin;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Bukkit side-effect adapter for the server-hosted preparation and cleanup boundaries.
 *
 * <p>This adapter owns no lifecycle state. The authoritative state and immutable participant
 * snapshot remain in {@link ServerHostedRoundCoordinator}. It only translates UUIDs and durable
 * return records into the already-separated Bukkit stage, chest, teleport, and recovery APIs.</p>
 *
 * <p>Runtime cleanup is replay-safe: arena artifacts may be cleared repeatedly, online players are
 * returned through the durable recovery service, and offline players keep their ledger records for
 * PlayerJoin/WorldLoad recovery.</p>
 */
public final class ServerHostedBukkitRoundRuntimeAdapter
    implements ServerHostedRoundPreparationService.RuntimePort<Location> {

  private final Supplier<GameStageManager> stageManagerSupplier;
  private final Supplier<TreasureChestManager> chestManagerSupplier;
  private final PlayerReturnLedger ledger;
  private final PlayerReturnRecoveryService recoveryService;
  private final Supplier<String> difficultySupplier;
  private final IntSupplier chestCountSupplier;
  private final Function<UUID, Player> playerLookup;
  private final Function<UUID, World> worldById;
  private final Function<String, World> worldByName;
  private final Clock clock;

  public ServerHostedBukkitRoundRuntimeAdapter(
      Supplier<GameStageManager> stageManagerSupplier,
      Supplier<TreasureChestManager> chestManagerSupplier,
      PlayerReturnLedger ledger,
      PlayerReturnRecoveryService recoveryService,
      Supplier<String> difficultySupplier,
      IntSupplier chestCountSupplier
  ) {
    this(
        stageManagerSupplier,
        chestManagerSupplier,
        ledger,
        recoveryService,
        difficultySupplier,
        chestCountSupplier,
        Bukkit::getPlayer,
        Bukkit::getWorld,
        Bukkit::getWorld,
        Clock.systemUTC()
    );
  }

  ServerHostedBukkitRoundRuntimeAdapter(
      Supplier<GameStageManager> stageManagerSupplier,
      Supplier<TreasureChestManager> chestManagerSupplier,
      PlayerReturnLedger ledger,
      PlayerReturnRecoveryService recoveryService,
      Supplier<String> difficultySupplier,
      IntSupplier chestCountSupplier,
      Function<UUID, Player> playerLookup,
      Function<UUID, World> worldById,
      Function<String, World> worldByName,
      Clock clock
  ) {
    this.stageManagerSupplier = Objects.requireNonNull(stageManagerSupplier, "stageManagerSupplier");
    this.chestManagerSupplier = Objects.requireNonNull(chestManagerSupplier, "chestManagerSupplier");
    this.ledger = Objects.requireNonNull(ledger, "ledger");
    this.recoveryService = Objects.requireNonNull(recoveryService, "recoveryService");
    this.difficultySupplier = Objects.requireNonNull(difficultySupplier, "difficultySupplier");
    this.chestCountSupplier = Objects.requireNonNull(chestCountSupplier, "chestCountSupplier");
    this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
    this.worldById = Objects.requireNonNull(worldById, "worldById");
    this.worldByName = Objects.requireNonNull(worldByName, "worldByName");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Resolves one complete current return destination without writing to the ledger. */
  public Optional<PlayerReturnRecord> resolveReturnDestination(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    Player player = onlinePlayer(playerId);
    if (player == null) return Optional.empty();

    Location location = player.getLocation();
    World world = location == null ? null : location.getWorld();
    if (world == null) return Optional.empty();

    return Optional.of(PlayerReturnRecord.create(
        playerId,
        world.getUID(),
        world.getName(),
        location.getX(),
        location.getY(),
        location.getZ(),
        location.getYaw(),
        location.getPitch(),
        Instant.now(clock)
    ));
  }

  @Override
  public Location prepareArena(UUID effectsAudienceId) {
    Player effectsAudience = onlinePlayer(effectsAudienceId);
    if (effectsAudience == null) {
      throw new IllegalStateException("The effects audience is not online during arena preparation.");
    }
    return stageManager().prepareSeasideStage(effectsAudience);
  }

  @Override
  public boolean placeChests(Location arena) {
    String difficulty = difficultySupplier.get();
    if (difficulty == null || difficulty.isBlank()) difficulty = "Normal";
    int chestCount = chestCountSupplier.getAsInt();
    if (chestCount < 0) return false;
    return chestManager().spawnChests(arena, difficulty, chestCount);
  }

  @Override
  public boolean teleport(UUID playerId, Location arena) {
    Player player = onlinePlayer(playerId);
    return player != null && stageManager().teleportPlayerToPreparedStage(player, arena);
  }

  @Override
  public boolean restore(UUID playerId, PlayerReturnRecord returnRecord) {
    Objects.requireNonNull(returnRecord, "returnRecord");
    Player player = onlinePlayer(playerId);
    if (player == null || !playerId.equals(returnRecord.playerId())) return false;
    Location destination = strictDestination(returnRecord);
    return destination != null && player.teleport(destination);
  }

  @Override
  public void activate(Location arena) {
    stageManager().activatePreparedStage(arena);
  }

  @Override
  public void rollbackArena(Location arena) {
    clearRoundArtifacts();
  }

  /**
   * Idempotent cleanup port used by the retained orchestration cleanup claim.
   *
   * <p>Offline participants intentionally keep their durable return records and do not block round
   * cleanup. An online participant whose return cannot be completed keeps the claim pending so the
   * same cleanup can be retried.</p>
   */
  public boolean cleanup(ServerHostedRoundCoordinator.CleanupClaim claim) {
    Objects.requireNonNull(claim, "claim");
    clearRoundArtifacts();

    boolean onlineReturnsComplete = true;
    for (UUID participant : claim.participants()) {
      if (ledger.pendingRecord(participant).isEmpty()) continue;

      Player player = onlinePlayer(participant);
      if (player == null) {
        continue;
      }

      PlayerReturnRecoveryService.RecoveryResult recovered = recoveryService.recover(
          participant,
          record -> attemptStrictReturn(player, record)
      );

      switch (recovered.code()) {
        case NO_PENDING, RETURNED_AND_CLEARED, PLAYER_UNAVAILABLE -> { }
        case RETURNED_RECORD_RETAINED, DESTINATION_UNAVAILABLE, DESTINATION_MISMATCH,
            TELEPORT_FAILED, ATTEMPT_ERROR, ALREADY_IN_PROGRESS -> onlineReturnsComplete = false;
      }
    }
    return onlineReturnsComplete;
  }

  private PlayerReturnRecoveryService.AttemptCode attemptStrictReturn(
      Player player,
      PlayerReturnRecord record
  ) {
    if (player == null || !player.isOnline()) {
      return PlayerReturnRecoveryService.AttemptCode.PLAYER_UNAVAILABLE;
    }
    Location destination = strictDestination(record);
    if (destination == null) {
      World byId = worldById.apply(record.worldId());
      World byName = worldByName.apply(record.worldName());
      if (byId == null && byName == null) {
        return PlayerReturnRecoveryService.AttemptCode.DESTINATION_UNAVAILABLE;
      }
      return PlayerReturnRecoveryService.AttemptCode.DESTINATION_MISMATCH;
    }
    return player.teleport(destination)
        ? PlayerReturnRecoveryService.AttemptCode.RETURNED
        : PlayerReturnRecoveryService.AttemptCode.TELEPORT_FAILED;
  }

  private Location strictDestination(PlayerReturnRecord record) {
    World byId = worldById.apply(record.worldId());
    World byName = worldByName.apply(record.worldName());
    if (byId == null || byName == null) return null;
    if (!byId.getUID().equals(record.worldId())
        || !byName.getUID().equals(record.worldId())
        || !byId.getName().equals(record.worldName())
        || !byName.getName().equals(record.worldName())) {
      return null;
    }
    return new Location(
        byId,
        record.x(),
        record.y(),
        record.z(),
        record.yaw(),
        record.pitch()
    );
  }

  private Player onlinePlayer(UUID playerId) {
    Player player = playerLookup.apply(playerId);
    return player != null && player.isOnline() ? player : null;
  }

  private GameStageManager stageManager() {
    return Objects.requireNonNull(stageManagerSupplier.get(), "GameStageManager is unavailable.");
  }

  private TreasureChestManager chestManager() {
    return Objects.requireNonNull(chestManagerSupplier.get(), "TreasureChestManager is unavailable.");
  }

  private void clearRoundArtifacts() {
    chestManager().removeAllChests();
    stageManager().clearDifficultyBlocks();
    stageManager().clearShopEntities();
  }
}
