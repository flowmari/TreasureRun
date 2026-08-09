package plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ServerHostedBukkitRoundProductionWiringBoundaryTest {

  @Test
  void productionConstructsExactlyOneRuntimeAdapterAndOrchestratorWithoutCommandActivation()
      throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String commandAdapter = read("src/main/java/plugin/ServerHostedSessionCommandAdapter.java");

    assertTrue(plugin.contains("private final ServerHostedRoundCoordinator serverHostedRoundCoordinator"));
    assertTrue(plugin.contains("new ServerHostedBukkitRoundRuntimeAdapter("));
    assertTrue(plugin.contains("new ServerHostedBukkitRoundOrchestrator<>("));
    assertTrue(plugin.contains("serverHostedBukkitRoundRuntimeAdapter::resolveReturnDestination"));
    assertTrue(plugin.contains("serverHostedBukkitRoundRuntimeAdapter::cleanup"));
    assertFalse(plugin.contains("prepareLockedRound("));
    assertFalse(commandAdapter.contains("ServerHostedBukkitRoundOrchestrator"));
    assertTrue(commandAdapter.contains("This adapter starts no gameplay"));
  }

  @Test
  void lockedQuitRoutesToRuntimeAbortBeforeWaitingLeaveSemantics() throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String quit = methodBody(plugin, "public void onPlayerQuit(PlayerQuitEvent event)");

    int locked = quit.indexOf("serverHostedState != ServerHostedRoundState.WAITING");
    int abort = quit.indexOf("serverHostedBukkitRoundOrchestrator.participantDisconnected(playerId)");
    int waitingLeave = quit.indexOf("serverHostedSessionLifecycle.handlePlayerQuit(playerId)");

    assertTrue(locked >= 0);
    assertTrue(abort > locked);
    assertTrue(waitingLeave > abort);
    assertTrue(quit.contains("serverHostedParticipants.contains(playerId)"));
  }

  @Test
  void pluginDisableClaimsServerHostedCleanupBeforeFacadeReset() throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String disable = methodBody(plugin, "public void onDisable()");

    int cleanup = disable.indexOf("serverHostedBukkitRoundOrchestrator.pluginDisabled()");
    int reset = disable.indexOf("serverHostedSessionLifecycle.reset()");

    assertTrue(cleanup >= 0);
    assertTrue(reset > cleanup);
    assertTrue(disable.contains("OwnershipMode.SERVER_HOSTED"));
    assertTrue(disable.contains("CLEANUP_PENDING"));
  }

  @Test
  void previewKeepsUserFacingLanguageAndSharedRuntimeActivationOutOfScope() throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String orchestrator = read("src/main/java/plugin/ServerHostedBukkitRoundOrchestrator.java");
    long languageFiles;
    try (var stream = Files.list(Path.of("src/main/resources/languages"))) {
      languageFiles = stream.filter(path -> path.getFileName().toString().endsWith(".yml")).count();
    }

    assertTrue(languageFiles == 23);
    assertTrue(orchestrator.contains("coordinator.beginCountdown()"));
    assertFalse(plugin.contains("serverHostedRoundCoordinator.beginRunning()"));
    assertFalse(plugin.contains("serverHostedBukkitRoundOrchestrator.prepareLockedRound("));
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
