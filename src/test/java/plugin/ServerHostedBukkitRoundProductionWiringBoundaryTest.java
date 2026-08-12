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
  void productionOwnsExactlyOneControllerAndCommandAdapterDelegatesAcceptedRuntimeWork() throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String commandAdapter = read("src/main/java/plugin/ServerHostedSessionCommandAdapter.java");
    assertEquals(1, occurrences(plugin,
        "private ServerHostedBukkitRoundController<Location> serverHostedBukkitRoundController;"));
    assertEquals(1, occurrences(plugin, "new ServerHostedBukkitRoundController<>("));
    assertEquals(1, occurrences(plugin, "new ServerHostedRoundActivationService<>("));
    assertTrue(plugin.contains("private final ServerHostedRoundCoordinator serverHostedRoundCoordinator"));
    assertTrue(plugin.contains("new ServerHostedBukkitRoundRuntimeAdapter("));
    assertTrue(plugin.contains("new ServerHostedBukkitRoundOrchestrator<>("));
    assertTrue(plugin.contains(
        "serverHostedBukkitRoundRuntimeAdapter::resolveReturnDestination,\n"
            + "        serverHostedBukkitRoundRuntimeAdapter\n"
    ));
    assertFalse(plugin.contains("serverHostedBukkitRoundRuntimeAdapter::cleanup"));
    assertTrue(plugin.contains("java.time.Duration.ofSeconds(Math.max(1, normalTimeLimit))"));
    assertTrue(commandAdapter.contains("ServerHostedBukkitRoundController<?> roundController"));
    assertTrue(commandAdapter.contains("roundController.start(decision)"));
    assertTrue(commandAdapter.contains("roundController.stop(decision)"));
    assertFalse(commandAdapter.contains("startGame("));
    assertTrue(plugin.indexOf("new ServerHostedSessionCommandAdapter(")
        > plugin.indexOf("new ServerHostedBukkitRoundController<>("));
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
    assertTrue(plugin.contains("this::showServerHostedCountdown"));
    assertTrue(plugin.contains("this::beginServerHostedGameplay"));
    assertTrue(plugin.contains("this::stopServerHostedGameplayPresentation"));
  }

  @Test
  void productionGameplayConsumesTheControllerOwnedSharedRuntimeWithoutASecondStateOwner()
      throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String controller = read("src/main/java/plugin/ServerHostedBukkitRoundController.java");
    String orchestrator = read("src/main/java/plugin/ServerHostedBukkitRoundOrchestrator.java");
    String inventoryOpen = methodBody(plugin, "public void onInventoryOpen(InventoryOpenEvent event)");

    assertTrue(plugin.contains("activeServerHostedRuntime()"));
    assertTrue(plugin.contains("activeRoundContext = runtime.context();"));
    assertTrue(plugin.contains("runtime.remainingMillis()"));
    assertTrue(plugin.contains("runtime.timeExpired()"));
    assertTrue(plugin.contains("serverHostedBukkitRoundController.roundCompleted()"));
    assertTrue(plugin.contains("serverHostedBukkitRoundController.timeExpired()"));
    assertTrue(inventoryOpen.contains("isGameplayParticipant(player)"));
    assertTrue(inventoryOpen.contains("recordGameplayScore(player, add)"));
    assertTrue(inventoryOpen.contains("finishServerHostedRound("));
    assertFalse(inventoryOpen.contains("if (!roundLifecycle.isRunning()) return;"));
    assertTrue(controller.contains("public synchronized Result roundCompleted()"));
    assertTrue(controller.contains("public synchronized Result timeExpired()"));
    assertTrue(orchestrator.contains(
        "abort(ServerHostedRoundCoordinator.ResetCause.ROUND_COMPLETED)"
    ));
    assertTrue(orchestrator.contains(
        "abort(ServerHostedRoundCoordinator.ResetCause.TIME_EXPIRED)"
    ));
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
  void commandActivationUsesControllerWhileProtectedSubsystemsRemainOutOfScope() throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String commandAdapter = read("src/main/java/plugin/ServerHostedSessionCommandAdapter.java");
    String orchestrator = read("src/main/java/plugin/ServerHostedBukkitRoundOrchestrator.java");
    long languageFiles;
    try (var stream = Files.list(Path.of("src/main/resources/languages"))) {
      languageFiles = stream.filter(path -> path.getFileName().toString().endsWith(".yml")).count();
    }
    assertEquals(23, languageFiles);
    assertTrue(orchestrator.contains("coordinator.beginCountdown()"));
    assertTrue(commandAdapter.contains("roundController.start(decision)"));
    assertTrue(commandAdapter.contains("roundController.stop(decision)"));
    assertFalse(commandAdapter.contains("startGame("));
    assertFalse(plugin.contains("startGame(serverHosted"));
  }


  @Test
  void activatedCommandMessagesDescribePostControllerRequestsTruthfully() throws Exception {
    String adapter = read("src/main/java/plugin/ServerHostedSessionCommandAdapter.java");
    String english = read("src/main/resources/languages/en.yml");
    String japanese = read("src/main/resources/languages/ja.yml");

    assertTrue(adapter.contains("ServerHostedBukkitRoundController"));
    assertTrue(adapter.contains("roundController.start("));
    assertTrue(adapter.contains("roundController.stop("));

    assertTrue(english.contains(
        "The participant roster was locked and server-hosted round preparation was requested."
    ));
    assertTrue(english.contains(
        "Cleanup was requested for the locked server-hosted round."
    ));
    assertFalse(english.contains("Gameplay has not started yet."));
    assertFalse(english.contains(
        "Runtime cleanup is required. The locked roster was preserved."
    ));

    assertTrue(japanese.contains(
        "参加者名簿を確定し、サーバー運営型ラウンドの開始準備を要求しました。"
    ));
    assertTrue(japanese.contains(
        "確定済みのサーバー運営型ラウンドについて、後片付けを要求しました。"
    ));
    assertFalse(japanese.contains("ゲーム本体はまだ開始していません。"));
    assertFalse(japanese.contains(
        "ランタイムの後片付けが必要です。確定済みの参加者名簿は保持されています。"
    ));
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
