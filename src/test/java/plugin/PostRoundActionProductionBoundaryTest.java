package plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PostRoundActionProductionBoundaryTest {

  @Test
  void postRoundActionsAreOfferedOnlyAfterAuthoritativeCleanupCompleted() throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String finish = methodBody(
        plugin,
        "private void finishServerHostedRound("
    );

    int pending = finish.indexOf(
        "ServerHostedBukkitRoundController.Code.CLEANUP_PENDING"
    );
    int completed = finish.indexOf(
        "ServerHostedBukkitRoundController.Code.CLEANUP_COMPLETED"
    );
    int offer = finish.indexOf("postRoundActionService.offer(");
    int presentation = finish.indexOf(
        "sendServerHostedResultSnapshot(snapshot, completed);"
    );

    assertTrue(pending >= 0);
    assertTrue(completed > pending);
    assertTrue(offer > completed);
    assertTrue(presentation > offer);
  }

  @Test
  void playAgainIsHiddenFromNormalTabCompletionAndCannotBypassEligibility()
      throws Exception {
    String adapter = read("src/main/java/plugin/ServerHostedSessionCommandAdapter.java");
    String service = read("src/main/java/plugin/PostRoundActionService.java");

    assertTrue(adapter.contains("subcommand.equals(\"playagain\")"));
    assertFalse(adapter.contains(
        "List.of(\"join\", \"leave\", \"playagain\", \"status\")"
    ));
    assertTrue(service.contains("replayEligiblePlayers.contains(playerId)"));
    assertTrue(service.contains("if (state == ServerHostedSession.State.IDLE)"));
    assertTrue(service.contains("state != ServerHostedSession.State.WAITING"));
    assertTrue(service.contains("session.create()"));
    assertTrue(service.contains("session.join(playerId)"));
  }

  @Test
  void playAgainConfigurationGatesEligibilityPresentationAndTheHiddenEndpoint()
      throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String adapter = read("src/main/java/plugin/ServerHostedSessionCommandAdapter.java");

    assertTrue(plugin.contains("private boolean isPostRoundPlayAgainEnabled()"));
    assertTrue(plugin.contains("getConfig().getBoolean(\"postRound.enabled\", true)"));
    assertTrue(plugin.contains(
        "getConfig().getBoolean(\"postRound.playAgain.enabled\", true)"
    ));
    assertTrue(plugin.contains("this::isPostRoundPlayAgainEnabled"));
    assertTrue(plugin.contains("isPostRoundPlayAgainEnabled()\n              ? snapshot.results()"));
    assertTrue(plugin.contains(": List.of()"));
    assertTrue(adapter.contains("BooleanSupplier playAgainEnabled"));
    assertTrue(adapter.contains("!playAgainEnabled.getAsBoolean()"));
  }

  @Test
  void hubIsDisabledByDefaultAndRunsTheConfiguredCommandAsThePlayer() throws Exception {
    String plugin = read("src/main/java/plugin/TreasureRunMultiChestPlugin.java");
    String config = read("src/main/resources/config.yml");

    assertTrue(config.contains(
        "postRound:\n"
            + "  enabled: true\n"
            + "  playAgain:\n"
            + "    enabled: true\n"
            + "  hub:\n"
            + "    enabled: false\n"
            + "    command: \"\""
    ));
    assertTrue(plugin.contains(
        "getConfig().getString(\n"
            + "        \"postRound.hub.command\","
    ));
    assertTrue(plugin.contains(
        "getConfig().getBoolean(\n"
            + "        \"postRound.hub.enabled\","
    ));
    assertTrue(plugin.contains("ClickEvent.Action.RUN_COMMAND"));
    assertFalse(plugin.contains(
        "Bukkit.dispatchCommand(Bukkit.getConsoleSender()"
    ));
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
