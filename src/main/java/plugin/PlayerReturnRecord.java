package plugin;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable destination required to return one player after arena teleport. */
public record PlayerReturnRecord(
    int schemaVersion,
    UUID recoveryId,
    UUID playerId,
    UUID worldId,
    String worldName,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    Instant createdAt
) {
  public static final int CURRENT_SCHEMA_VERSION = 1;

  public PlayerReturnRecord {
    if (schemaVersion != CURRENT_SCHEMA_VERSION) {
      throw new IllegalArgumentException("Unsupported player-return schema: " + schemaVersion);
    }
    recoveryId = Objects.requireNonNull(recoveryId, "recoveryId");
    playerId = Objects.requireNonNull(playerId, "playerId");
    worldId = Objects.requireNonNull(worldId, "worldId");
    worldName = Objects.requireNonNull(worldName, "worldName");
    if (worldName.isBlank()) throw new IllegalArgumentException("worldName must not be blank");
    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
      throw new IllegalArgumentException("Coordinates must be finite");
    }
    if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
      throw new IllegalArgumentException("Rotation must be finite");
    }
    createdAt = Instant.ofEpochMilli(Objects.requireNonNull(createdAt, "createdAt").toEpochMilli());
  }

  public static PlayerReturnRecord create(
      UUID playerId,
      UUID worldId,
      String worldName,
      double x,
      double y,
      double z,
      float yaw,
      float pitch,
      Instant createdAt
  ) {
    return new PlayerReturnRecord(
        CURRENT_SCHEMA_VERSION,
        UUID.randomUUID(),
        playerId,
        worldId,
        worldName,
        x,
        y,
        z,
        yaw,
        pitch,
        createdAt
    );
  }

  public boolean hasSameDestination(PlayerReturnRecord other) {
    Objects.requireNonNull(other, "other");
    return playerId.equals(other.playerId)
        && worldId.equals(other.worldId)
        && worldName.equals(other.worldName)
        && Double.compare(x, other.x) == 0
        && Double.compare(y, other.y) == 0
        && Double.compare(z, other.z) == 0
        && Float.compare(yaw, other.yaw) == 0
        && Float.compare(pitch, other.pitch) == 0;
  }
}
