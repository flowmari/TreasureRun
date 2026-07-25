package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TreasureChestPlacementSafetyTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();

  @Test
  void placementPlanReturnsTheExactRequestedNumberOfUniqueColumns() {
    List<TreasureChestManager.ChestOffset> offsets =
        TreasureChestManager.planUniqueChestOffsets(2, 20, new Random(42L));

    assertEquals(20, offsets.size());
    assertEquals(20, new HashSet<>(offsets).size());
    assertTrue(offsets.stream().allMatch(
        offset -> Math.abs(offset.dx()) <= 2 && Math.abs(offset.dz()) <= 2
    ));
  }

  @Test
  void placementPlanRejectsCountsThatCannotFitInsideTheRadius() {
    List<TreasureChestManager.ChestOffset> offsets =
        TreasureChestManager.planUniqueChestOffsets(1, 10, new Random(42L));

    assertTrue(offsets.isEmpty());
  }

  @Test
  void placementFailureUsesTheSharedCleanupAndPermissionsCannotBeBypassed()
      throws IOException {

    String manager = Files.readString(
        ROOT.resolve("src/main/java/plugin/TreasureChestManager.java")
    );
    String plugin = Files.readString(
        ROOT.resolve("src/main/java/plugin/TreasureRunMultiChestPlugin.java")
    );

    assertTrue(manager.contains("rememberOriginalBlock(underBlock);"));
    assertTrue(manager.contains("rememberOriginalBlock(block);"));
    assertTrue(manager.contains("restoreChangedBlocks();"));
    assertTrue(manager.contains("public boolean spawnChests("));
    assertTrue(manager.contains("if (added != count)"));

    assertTrue(plugin.contains(
        "if (!treasureChestManager.spawnChests("
    ));
    assertTrue(plugin.contains(
        "finishRoundCleanup(player, CleanupReason.PREPARATION_FAILED, true);"
    ));

    int preprocessHandler = plugin.indexOf(
        "public void onGamestartStyleCommand(PlayerCommandPreprocessEvent event)"
    );
    int preprocessCancel = plugin.indexOf(
        "event.setCancelled(true);",
        preprocessHandler
    );
    int permissionGuard = plugin.indexOf(
        "if (!player.hasPermission(ROUND_ADMIN_PERMISSION))",
        preprocessHandler
    );
    int permissionMessage = plugin.indexOf(
        "\"finalAudit.command.noPermission\"",
        permissionGuard
    );
    assertTrue(preprocessHandler >= 0);
    assertTrue(preprocessCancel > preprocessHandler);
    assertTrue(permissionGuard > preprocessCancel);
    assertTrue(permissionMessage > permissionGuard);

    int selfRegistration = plugin.indexOf(
        "Bukkit.getPluginManager().registerEvents(this, this);"
    );
    int localizedRegistration = plugin.indexOf(
        "new LocalizedSystemMessageListener(this)"
    );

    assertTrue(plugin.contains(
        "@EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)\n"
            + "  public void onGamestartStyleCommand"
    ));
    assertTrue(selfRegistration >= 0);
    assertTrue(localizedRegistration > selfRegistration);
  }
}
