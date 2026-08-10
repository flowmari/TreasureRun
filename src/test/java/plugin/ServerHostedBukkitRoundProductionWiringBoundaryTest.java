package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ServerHostedBukkitRoundProductionWiringBoundaryTest {

  @Test
  void productionOwnsExactlyOneControllerWithoutCommandActivation() throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String commandAdapter = read("src/main/java/plugin/ServerHostedSessionCommandAdapter.java");
    assertEquals(1, occurrences(plugin,
        "private ServerHostedBukkitRoundController<Location> serverHostedBukkitRoundController;"));
    assertEquals(1, occurrences(plugin, "new ServerHostedBukkitRoundController<>("));
    assertEquals(1, occurrences(plugin, "new ServerHostedRoundActivationService<>("));
    assertTrue(plugin.contains("private final ServerHostedRoundCoordinator serverHostedRoundCoordinator"));
    assertTrue(plugin.contains("new ServerHostedBukkitRoundRuntimeAdapter("));
    assertTrue(plugin.contains("new ServerHostedBukkitRoundOrchestrator<>("));
    assertTrue(plugin.contains("java.time.Duration.ofSeconds(Math.max(1, normalTimeLimit))"));
    assertFalse(commandAdapter.contains("ServerHostedBukkitRoundController"));
    assertTrue(commandAdapter.contains("This adapter starts no gameplay"));
    assertFalse(plugin.contains("serverHostedBukkitRoundController.start("));
  }

  @Test
  void controllerSchedulerUsesCancellableBukkitTaskBridge() throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String controller = read("src/main/java/plugin/ServerHostedBukkitRoundController.java");
    assertTrue(controller.contains(
        "ScheduledTask scheduleRepeating(long initialDelayTicks, long periodTicks, Runnable task)"
    ));
    assertTrue(controller.contains(
        "scheduler.scheduleRepeating(\n              TICKS_PER_SECOND,\n"
            + "              TICKS_PER_SECOND,\n              this::countdownTick"
    ));
    assertTrue(controller.contains("20L"));
    assertTrue(plugin.contains(
        "(initialDelayTicks, periodTicks, task) -> {"
    ));
    assertTrue(plugin.contains("Bukkit.getScheduler().runTaskTimer("));
    assertTrue(plugin.contains("initialDelayTicks"));
    assertTrue(plugin.contains("periodTicks"));
    assertTrue(plugin.contains("return scheduled::cancel;"));
    assertTrue(plugin.contains("ignoredRemaining -> { }"));
  }

  @Test
  void lockedQuitRoutesThroughControllerBeforeWaitingLeaveSemantics() throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String quit = methodBody(plugin, "public void onPlayerQuit(PlayerQuitEvent event)");
    int locked = quit.indexOf("serverHostedState != ServerHostedRoundState.WAITING");
    int controllerAbort = quit.indexOf(
        "serverHostedBukkitRoundController.participantDisconnected(playerId)");
    int waitingLeave = quit.indexOf("serverHostedSessionLifecycle.handlePlayerQuit(playerId)");
    assertTrue(locked >= 0);
    assertTrue(controllerAbort > locked);
    assertTrue(waitingLeave > controllerAbort);
    assertTrue(quit.contains("serverHostedParticipants.contains(playerId)"));
    assertFalse(quit.contains("serverHostedBukkitRoundOrchestrator.participantDisconnected(playerId)"));
  }

  @Test
  void pluginDisableRoutesThroughControllerBeforeFacadeReset() throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String disable = methodBody(plugin, "public void onDisable()");
    int cleanup = disable.indexOf("serverHostedBukkitRoundController.pluginDisabled()");
    int reset = disable.indexOf("serverHostedSessionLifecycle.reset()");
    assertTrue(cleanup >= 0);
    assertTrue(reset > cleanup);
    assertTrue(disable.contains("OwnershipMode.SERVER_HOSTED"));
    assertTrue(disable.contains("CLEANUP_PENDING"));
    assertFalse(disable.contains("serverHostedBukkitRoundOrchestrator.pluginDisabled()"));
  }

  @Test
  void commandActivationAndProtectedSubsystemsRemainOutOfScope() throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String commandAdapter = read("src/main/java/plugin/ServerHostedSessionCommandAdapter.java");
    String orchestrator = read("src/main/java/plugin/ServerHostedBukkitRoundOrchestrator.java");
    long languageFiles;
    try (var stream = Files.list(Path.of("src/main/resources/languages"))) {
      languageFiles = stream.filter(path -> path.getFileName().toString().endsWith(".yml")).count();
    }
    assertEquals(23, languageFiles);
    assertTrue(orchestrator.contains("coordinator.beginCountdown()"));
    assertFalse(commandAdapter.contains("ServerHostedBukkitRoundController"));
    assertFalse(plugin.contains("serverHostedBukkitRoundController.start("));
    assertFalse(plugin.contains("startGame(serverHosted"));
  }

  private static int occurrences(String source, String needle) {
    int count = 0;
    int from = 0;
    while (true) {
      int index = source.indexOf(needle, from);
      if (index < 0) return count;
      count++;
      from = index + needle.length();
    }
  }

  private static String read(String relativePath) throws Exception {
    return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
  }

  private static String methodBody(String source, String signature) {
    int start = source.indexOf(signature);
    assertTrue(start >= 0, "Missing method: " + signature);
    int brace = source.indexOf('{', start);
    assertTrue(brace >= 0, "Missing opening brace: " + signature);
    int depth = 0;
    for (int index = brace; index < source.length(); index++) {
      char value = source.charAt(index);
      if (value == '{') depth++;
      if (value == '}') {
        depth--;
        if (depth == 0) return source.substring(start, index + 1);
      }
    }
    throw new AssertionError("Missing closing brace: " + signature);
  }
}
