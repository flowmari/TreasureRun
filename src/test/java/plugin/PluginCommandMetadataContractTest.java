package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PluginCommandMetadataContractTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path PLUGIN_YML =
      ROOT.resolve("src/main/resources/plugin.yml");

  private static final Pattern COMMAND_KEY =
      Pattern.compile("^  ([A-Za-z0-9_-]+):\\s*(?:#.*)?$");

  private static final Pattern PERMISSION_KEY =
      Pattern.compile("^  ([A-Za-z0-9_.-]+):\\s*(?:#.*)?$");

  private static final Pattern ALIASES =
      Pattern.compile("^    aliases:\\s*\\[(.*)]\\s*(?:#.*)?$");

  private static final Pattern COMMAND_PERMISSION =
      Pattern.compile("^    permission:\\s*([^\\s#]+)");

  private static final Pattern PERMISSION_DEFAULT =
      Pattern.compile("^    default:\\s*([^\\s#]+)");

  private static final Pattern GET_COMMAND =
      Pattern.compile("getCommand\\(\\s*\"([^\"]+)\"\\s*\\)");

  @Test
  void commandMetadataMatchesTheSupportedPublicContract() throws IOException {
    String yaml = Files.readString(PLUGIN_YML);
    Map<String, CommandSpec> commands = parseCommands(yaml);

    Set<String> expectedCommands = Set.of(
        "trsecret",
        "treasuresecret",
        "packetI18nProbe",
        "treasureReload",
        "gameRank",
        "craftspecialemerald",
        "checktreasureemerald",
        "gameMenu",
        "gameEnd",
        "gamestart",
        "clearStageBlocks",
        "quoteFavorite",
        "lang",
        "treasureExportLang",
        "rank",
        "heartbeatTest"
    );

    assertEquals(expectedCommands, commands.keySet());
    assertEquals(
        List.of("gameReload"),
        commands.get("treasureReload").aliases
    );
    assertTrue(commands.get("gamestart").aliases.isEmpty());
    assertEquals("treasure.admin", commands.get("gamestart").permission);
    assertEquals("treasure.admin", commands.get("gameEnd").permission);
    assertEquals("op", parsePermissionDefaults(yaml).get("treasure.admin"));

    Set<String> declaredPermissions = parsePermissionKeys(yaml);

    for (Map.Entry<String, CommandSpec> entry : commands.entrySet()) {
      String permission = entry.getValue().permission;

      if (permission != null) {
        assertTrue(
            declaredPermissions.contains(permission),
            () -> "Command " + entry.getKey()
                + " references undeclared permission " + permission
        );
      }
    }

    Set<String> primaryNames = lowerCaseSet(commands.keySet());
    Set<String> aliases = new LinkedHashSet<>();

    for (Map.Entry<String, CommandSpec> entry : commands.entrySet()) {
      String primary = entry.getKey().toLowerCase(Locale.ROOT);

      for (String aliasValue : entry.getValue().aliases) {
        String alias = aliasValue.toLowerCase(Locale.ROOT);

        assertNotEquals(
            primary,
            alias,
            "Case-only aliases are redundant"
        );

        assertFalse(
            primaryNames.contains(alias),
            () -> "Alias collides with an independently declared command: "
                + aliasValue
        );

        assertTrue(
            aliases.add(alias),
            () -> "Alias is declared more than once: " + aliasValue
        );
      }
    }
  }

  @Test
  void JavaCommandReferencesAndPluginMetadataAreBidirectionallyComplete()
      throws IOException {

    String yaml = Files.readString(PLUGIN_YML);
    Map<String, CommandSpec> commands = parseCommands(yaml);

    Set<String> declared = lowerCaseSet(commands.keySet());
    Set<String> aliases = new LinkedHashSet<>();

    for (CommandSpec command : commands.values()) {
      for (String alias : command.aliases) {
        aliases.add(alias.toLowerCase(Locale.ROOT));
      }
    }

    Set<String> references = new LinkedHashSet<>();
    List<String> unresolved = new ArrayList<>();

    try (Stream<Path> paths = Files.walk(ROOT.resolve("src/main/java"))) {
      for (Path path : paths
          .filter(value -> value.toString().endsWith(".java"))
          .toList()) {

        String source = Files.readString(path);
        Matcher matcher = GET_COMMAND.matcher(source);

        while (matcher.find()) {
          String command = matcher.group(1);
          String normalized = command.toLowerCase(Locale.ROOT);

          references.add(normalized);

          if (!declared.contains(normalized)
              && !aliases.contains(normalized)) {
            unresolved.add(
                ROOT.relativize(path) + ": " + command
            );
          }
        }
      }
    }

    assertTrue(
        unresolved.isEmpty(),
        () -> "Undeclared getCommand references: " + unresolved
    );

    assertEquals(declared, references);
  }

  @Test
  void RemovedLegacyMetadataAndInactiveDbstatusRegistrationStayAbsent()
      throws IOException {

    Map<String, CommandSpec> commands =
        parseCommands(Files.readString(PLUGIN_YML));

    assertFalse(commands.containsKey("game"));
    assertFalse(commands.containsKey("gameReload"));
    assertFalse(commands.containsKey("givespecialemerald"));
    assertFalse(commands.containsKey("dbstatus"));

    String mysqlManager = Files.readString(
        ROOT.resolve("src/main/java/plugin/MySQLManager.java")
    );

    assertFalse(
        mysqlManager.contains("getCommand(\"dbstatus\")")
    );

    String commandReference =
        Files.readString(ROOT.resolve("docs/COMMANDS.md"));

    assertFalse(commandReference.contains("| `/game` |"));
    assertFalse(commandReference.contains("| `/gameStart` |"));
    assertFalse(
        commandReference.contains("| `/givespecialemerald` |")
    );

    assertTrue(
        commandReference.contains(
            "| `/gameReload` | `/gameReload` | "
                + "`treasure.reload` | op | "
                + "Alias of `/treasureReload`. |"
        )
    );
  }

  private static Map<String, CommandSpec> parseCommands(String yaml) {
    Map<String, CommandSpec> commands = new LinkedHashMap<>();
    boolean inCommands = false;
    CommandSpec current = null;

    for (String line : yaml.lines().toList()) {
      if (line.equals("commands:")) {
        inCommands = true;
        current = null;
        continue;
      }

      if (!inCommands) {
        continue;
      }

      if (!line.isBlank() && !line.startsWith(" ")) {
        break;
      }

      Matcher commandMatcher = COMMAND_KEY.matcher(line);

      if (commandMatcher.matches()) {
        current = new CommandSpec();
        commands.put(commandMatcher.group(1), current);
        continue;
      }

      if (current == null) {
        continue;
      }

      Matcher aliasesMatcher = ALIASES.matcher(line);

      if (aliasesMatcher.matches()) {
        for (String rawAlias : aliasesMatcher.group(1).split(",")) {
          String alias = rawAlias
              .trim()
              .replace("\"", "")
              .replace("'", "");

          if (!alias.isEmpty()) {
            current.aliases.add(alias);
          }
        }

        continue;
      }

      Matcher permissionMatcher =
          COMMAND_PERMISSION.matcher(line);

      if (permissionMatcher.find()) {
        current.permission = permissionMatcher.group(1);
      }
    }

    return commands;
  }

  private static Set<String> parsePermissionKeys(String yaml) {
    Set<String> permissions = new LinkedHashSet<>();
    boolean inPermissions = false;

    for (String line : yaml.lines().toList()) {
      if (line.equals("permissions:")) {
        inPermissions = true;
        continue;
      }

      if (!inPermissions) {
        continue;
      }

      if (!line.isBlank() && !line.startsWith(" ")) {
        break;
      }

      Matcher matcher = PERMISSION_KEY.matcher(line);

      if (matcher.matches()) {
        permissions.add(matcher.group(1));
      }
    }

    return permissions;
  }

  private static Map<String, String> parsePermissionDefaults(String yaml) {
    Map<String, String> defaults = new LinkedHashMap<>();
    boolean inPermissions = false;
    String current = null;

    for (String line : yaml.lines().toList()) {
      if (line.equals("permissions:")) {
        inPermissions = true;
        current = null;
        continue;
      }

      if (!inPermissions) {
        continue;
      }

      if (!line.isBlank() && !line.startsWith(" ")) {
        break;
      }

      Matcher permissionMatcher = PERMISSION_KEY.matcher(line);
      if (permissionMatcher.matches()) {
        current = permissionMatcher.group(1);
        continue;
      }

      if (current == null) {
        continue;
      }

      Matcher defaultMatcher = PERMISSION_DEFAULT.matcher(line);
      if (defaultMatcher.find()) {
        defaults.put(current, defaultMatcher.group(1));
      }
    }

    return defaults;
  }

  private static Set<String> lowerCaseSet(Set<String> values) {
    Set<String> result = new LinkedHashSet<>();

    for (String value : values) {
      result.add(value.toLowerCase(Locale.ROOT));
    }

    return result;
  }

  private static final class CommandSpec {
    private final List<String> aliases = new ArrayList<>();
    private String permission;
  }
}
