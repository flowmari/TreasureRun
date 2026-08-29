package plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class ArenaWorldOwnershipAndPreparationTest {

  @TempDir
  Path tempDir;

  @Test
  void sameNameWithoutMarkerIsNotOwned() {
    TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
    ArenaWorldManager manager = new ArenaWorldManager(plugin);
    World world = arenaWorld(tempDir, UUID.randomUUID());

    assertFalse(manager.isOwnedWorld(world));
  }

  @Test
  void markerMustMatchWorldUuid() throws Exception {
    TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
    ArenaWorldManager manager = new ArenaWorldManager(plugin);
    UUID worldId = UUID.randomUUID();
    World world = arenaWorld(tempDir, worldId);

    writeOwnershipMarker(tempDir, UUID.randomUUID());
    assertFalse(manager.isOwnedWorld(world));

    writeOwnershipMarker(tempDir, worldId);
    assertTrue(manager.isOwnedWorld(world));
  }

  @Test
  void basePreparationMarkerIsBoundToOwnedWorldAndRadius() throws Exception {
    TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
    ArenaWorldManager manager = new ArenaWorldManager(plugin);
    UUID worldId = UUID.randomUUID();
    World world = arenaWorld(tempDir, worldId);
    writeOwnershipMarker(tempDir, worldId);

    assertFalse(manager.isBasePrepared(world, 64));
    manager.markBasePrepared(world, 64);
    assertTrue(manager.isBasePrepared(world, 64));
    assertFalse(manager.isBasePrepared(world, 32));
  }

  @Test
  void preExistingUnmarkedDirectoryFailsBeforeWorldCreatorCanAdoptIt() throws Exception {
    Path worldContainer = tempDir.resolve("server");
    Files.createDirectories(worldContainer.resolve(ArenaWorldManager.WORLD_NAME));

    TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
    Server server = mock(Server.class);
    when(plugin.getServer()).thenReturn(server);
    when(server.getWorldContainer()).thenReturn(worldContainer.toFile());

    ArenaWorldManager manager = new ArenaWorldManager(plugin);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getWorld(ArenaWorldManager.WORLD_NAME)).thenReturn(null);

      IllegalStateException failure = assertThrows(
          IllegalStateException.class,
          manager::getArenaWorld
      );
      assertTrue(failure.getMessage().contains("Refusing to claim or load it automatically"));
    }
  }

  private static World arenaWorld(Path folder, UUID worldId) {
    World world = mock(World.class);
    when(world.getName()).thenReturn(ArenaWorldManager.WORLD_NAME);
    when(world.getUID()).thenReturn(worldId);
    when(world.getWorldFolder()).thenReturn(folder.toFile());
    return world;
  }

  private static void writeOwnershipMarker(Path folder, UUID worldId) throws Exception {
    Files.writeString(
        folder.resolve(ArenaWorldManager.OWNERSHIP_MARKER),
        "format=1\nowner=TreasureRun\nworldUuid=" + worldId + "\n",
        StandardCharsets.UTF_8
    );
  }
}
