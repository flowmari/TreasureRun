package plugin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary-level tests for LocalizedPacketMessageProtocolListener.
 *
 * The real translation logic belongs to com.treasurerun.i18n.PacketI18nJsonLocalizer,
 * which is tested as pure Java.
 *
 * This test intentionally avoids mocking Bukkit/ProtocolLib internals.
 * Its purpose is to keep this listener thin:
 * - packet/player access stays here
 * - JSON localization is delegated to the pure localizer
 */
class LocalizedPacketMessageProtocolListenerTest {

  private static final Path LISTENER =
      Path.of("src/main/java/plugin/LocalizedPacketMessageProtocolListener.java");

  @Test
  void listenerDelegatesJsonLocalizationToPureJavaLocalizer() throws Exception {
    String src = Files.readString(LISTENER);

    assertTrue(
        src.contains("PacketI18nJsonLocalizer.localizeJson("),
        "ProtocolLib listener should delegate JSON localization to PacketI18nJsonLocalizer."
    );

    assertTrue(
        src.contains("WrappedChatComponent.fromJson(loc)"),
        "Localized packet replacement should write a JSON text component, not raw plain text."
    );
  }

  @Test
  void listenerRemainsProtocolLibBoundaryAndDoesNotOwnJsonParsingLogic() throws Exception {
    String src = Files.readString(LISTENER);

    assertTrue(src.contains("PacketAdapter"), "Listener should remain the ProtocolLib adapter boundary.");
    assertTrue(src.contains("PacketEvent"), "Listener should still receive ProtocolLib packet events.");

    assertFalse(
        src.contains("JsonParser.parseString"),
        "JSON parsing should not live in the ProtocolLib boundary listener."
    );

    assertFalse(
        src.contains("new Gson("),
        "JSON serialization should not live in the ProtocolLib boundary listener."
    );
  }
}
