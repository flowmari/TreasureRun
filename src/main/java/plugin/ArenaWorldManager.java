package plugin;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/**
 * Owns the isolated world used for TreasureRun stage construction.
 *
 * <p>No gameplay stage builder should mutate a normal server world. This manager is the single
 * boundary for resolving the plugin-owned arena world and its fixed stage origin.</p>
 */
final class ArenaWorldManager {

  static final String WORLD_NAME = "treasurerun_arena";
  static final int STAGE_X = 0;
  static final int STAGE_Z = 0;
  static final int PREFERRED_WATER_Y = 63;

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
    return world != null && WORLD_NAME.equals(world.getName());
  }

  void requireOwnedWorld(World world) {
    if (!isOwnedWorld(world)) {
      String actual = world == null ? "null" : world.getName();
      throw new IllegalStateException(
          "TreasureRun stage mutation was refused outside its owned arena world: " + actual
      );
    }
  }

  private World getOrCreateArenaWorld() {
    World existing = Bukkit.getWorld(WORLD_NAME);
    if (existing != null) {
      configure(existing);
      return existing;
    }

    WorldCreator creator = new WorldCreator(WORLD_NAME)
        .environment(World.Environment.NORMAL)
        .type(WorldType.FLAT)
        .generateStructures(false);

    World created = creator.createWorld();
    if (created == null) {
      throw new IllegalStateException("Unable to create TreasureRun arena world: " + WORLD_NAME);
    }

    configure(created);
    plugin.getLogger().info("[Arena] Created plugin-owned world: " + WORLD_NAME);
    return created;
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
