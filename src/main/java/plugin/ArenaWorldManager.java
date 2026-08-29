package plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/**
 * Owns the isolated world used for TreasureRun stage construction.
 *
 * <p>No gameplay stage builder should mutate a normal server world. A fixed world name alone is
 * not sufficient proof of ownership, so TreasureRun also requires an on-disk marker bound to the
 * world's UUID before it configures or mutates an existing arena world.</p>
 */
final class ArenaWorldManager {

  static final String WORLD_NAME = "treasurerun_arena";
  static final int STAGE_X = 0;
  static final int STAGE_Z = 0;
  static final int PREFERRED_WATER_Y = 63;

  static final String OWNERSHIP_MARKER = ".treasurerun-owned-arena";
  static final String BASE_PREPARED_MARKER = ".treasurerun-base-prepared-v1";

  private static final String MARKER_FORMAT = "1";
  private static final String MARKER_OWNER = "TreasureRun";

  private final TreasureRunMultiChestPlugin plugin;

  ArenaWorldManager(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  Location getArenaBase() {
    World world = getArenaWorld();
    return new Location(world, STAGE_X, PREFERRED_WATER_Y, STAGE_Z);
  }

  World getArenaWorld() {
    return getOrCreateArenaWorld();
  }

  boolean isOwnedWorld(World world) {
    if (world == null || !WORLD_NAME.equals(world.getName())) {
      return false;
    }
    return markerMatchesWorld(world, ownershipMarker(world));
  }

  void requireOwnedWorld(World world) {
    if (!isOwnedWorld(world)) {
      String actual = world == null ? "null" : world.getName();
      throw new IllegalStateException(
          "TreasureRun refused arena mutation because ownership could not be authenticated: "
              + actual
              + ". Stop the server and resolve the treasurerun_arena ownership collision; "
              + "TreasureRun will not silently adopt an unmarked same-name world."
      );
    }
  }

  boolean isBasePrepared(World world, int waterRadius) {
    if (!isOwnedWorld(world)) {
      return false;
    }

    Map<String, String> marker = readMarker(basePreparedMarker(world));
    return MARKER_FORMAT.equals(marker.get("format"))
        && MARKER_OWNER.equals(marker.get("owner"))
        && world.getUID().toString().equals(marker.get("worldUuid"))
        && Integer.toString(waterRadius).equals(marker.get("waterRadius"));
  }

  void markBasePrepared(World world, int waterRadius) {
    requireOwnedWorld(world);
    writeMarkerAtomically(
        basePreparedMarker(world),
        List.of(
            "format=" + MARKER_FORMAT,
            "owner=" + MARKER_OWNER,
            "worldUuid=" + world.getUID(),
            "waterRadius=" + waterRadius
        )
    );
  }

  private World getOrCreateArenaWorld() {
    World existing = Bukkit.getWorld(WORLD_NAME);
    if (existing != null) {
      requireOwnedWorld(existing);
      configure(existing);
      return existing;
    }

    Path worldDirectory = arenaWorldDirectory();
    boolean directoryAlreadyExists = Files.exists(worldDirectory);
    if (directoryAlreadyExists
        && !Files.isRegularFile(worldDirectory.resolve(OWNERSHIP_MARKER))) {
      throw new IllegalStateException(
          "TreasureRun found an existing world directory named " + WORLD_NAME
              + " without its ownership marker. Refusing to claim or load it automatically: "
              + worldDirectory
      );
    }

    WorldCreator creator = new WorldCreator(WORLD_NAME)
        .environment(World.Environment.NORMAL)
        .type(WorldType.FLAT)
        .generateStructures(false);

    World created = creator.createWorld();
    if (created == null) {
      throw new IllegalStateException("Unable to create TreasureRun arena world: " + WORLD_NAME);
    }

    if (directoryAlreadyExists) {
      requireOwnedWorld(created);
    } else {
      writeOwnershipMarker(created);
    }

    configure(created);
    plugin.getLogger().info(
        "[Arena] Authenticated plugin-owned world: " + WORLD_NAME
            + " uuid=" + created.getUID()
    );
    return created;
  }

  private Path arenaWorldDirectory() {
    return plugin.getServer()
        .getWorldContainer()
        .toPath()
        .resolve(WORLD_NAME)
        .toAbsolutePath()
        .normalize();
  }

  private Path ownershipMarker(World world) {
    return world.getWorldFolder().toPath().resolve(OWNERSHIP_MARKER);
  }

  private Path basePreparedMarker(World world) {
    return world.getWorldFolder().toPath().resolve(BASE_PREPARED_MARKER);
  }

  private void writeOwnershipMarker(World world) {
    writeMarkerAtomically(
        ownershipMarker(world),
        List.of(
            "format=" + MARKER_FORMAT,
            "owner=" + MARKER_OWNER,
            "worldUuid=" + world.getUID()
        )
    );
  }

  private boolean markerMatchesWorld(World world, Path markerPath) {
    Map<String, String> marker = readMarker(markerPath);
    return MARKER_FORMAT.equals(marker.get("format"))
        && MARKER_OWNER.equals(marker.get("owner"))
        && world.getUID().toString().equals(marker.get("worldUuid"));
  }

  private Map<String, String> readMarker(Path path) {
    if (!Files.isRegularFile(path)) {
      return Map.of();
    }

    Map<String, String> values = new HashMap<>();
    try {
      for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
        int separator = line.indexOf('=');
        if (separator <= 0) {
          continue;
        }
        values.put(line.substring(0, separator), line.substring(separator + 1));
      }
      return values;
    } catch (IOException e) {
      plugin.getLogger().warning(
          "[Arena] Could not read ownership marker " + path + ": " + e.getMessage()
      );
      return Map.of();
    }
  }

  private void writeMarkerAtomically(Path target, List<String> lines) {
    try {
      Files.createDirectories(target.getParent());
      Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
      try {
        Files.write(temporary, lines, StandardCharsets.UTF_8);
        try {
          Files.move(
              temporary,
              target,
              StandardCopyOption.ATOMIC_MOVE,
              StandardCopyOption.REPLACE_EXISTING
          );
        } catch (AtomicMoveNotSupportedException ignored) {
          Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not persist TreasureRun arena marker " + target + ": " + e.getMessage(),
          e
      );
    }
  }

  private void configure(World world) {
    world.setAutoSave(true);
    world.setStorm(false);
    world.setThundering(false);
    world.setTime(6000L);
    world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
    world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
    world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
  }
}
