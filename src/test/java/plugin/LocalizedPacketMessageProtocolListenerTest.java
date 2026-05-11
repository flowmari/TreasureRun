package plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LocalizedPacketMessageProtocolListenerTest {

  @Test
  void localizeJsonDeep_replacesTranslateKeyWithConfiguredLanguageAndWithArgs() throws Exception {
    TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
    I18n i18n = mock(I18n.class);
    PlayerLanguageStore playerLanguageStore = mock(PlayerLanguageStore.class);
    Player player = mock(Player.class);

    YamlConfiguration config = new YamlConfiguration();
    config.set("language.default", "ja");

    when(plugin.getConfig()).thenReturn(config);
    when(plugin.getLogger()).thenReturn(Logger.getLogger("PacketI18nTest"));
    when(plugin.getI18n()).thenReturn(i18n);
    when(plugin.getPlayerLanguageStore()).thenReturn(playerLanguageStore);
    when(playerLanguageStore.getLang(player, "ja")).thenReturn("ja");

    when(i18n.tr(
        eq("ja"),
        eq("minecraft.packet.multiplayer.player.joined"),
        any(I18n.Placeholder[].class)
    )).thenReturn("{arg0} がゲームに参加しました");

    LocalizedPacketMessageProtocolListener listener =
        new LocalizedPacketMessageProtocolListener(plugin);

    Method method = LocalizedPacketMessageProtocolListener.class
        .getDeclaredMethod("localizeJsonDeep", Player.class, String.class);
    method.setAccessible(true);

    String json =
        "{\"translate\":\"multiplayer.player.joined\",\"with\":[{\"text\":\"flowmari\"}]}";

    String actual = (String) method.invoke(listener, player, json);

    assertEquals("{arg0} がゲームに参加しました", actual);

    ArgumentCaptor<I18n.Placeholder[]> captor =
        ArgumentCaptor.forClass(I18n.Placeholder[].class);

    verify(i18n).tr(
        eq("ja"),
        eq("minecraft.packet.multiplayer.player.joined"),
        captor.capture()
    );

    I18n.Placeholder[] placeholders = captor.getValue();

    assertEquals(1, placeholders.length);
    assertEquals("{arg0}", readPrivateString(placeholders[0], "key"));
    assertEquals("flowmari", readPrivateString(placeholders[0], "value"));
  }

  @Test
  void localizeJsonDeep_returnsNullWhenTranslationFallsBackToMissingMessage() throws Exception {
    TreasureRunMultiChestPlugin plugin = mock(TreasureRunMultiChestPlugin.class);
    I18n i18n = mock(I18n.class);
    Player player = mock(Player.class);

    YamlConfiguration config = new YamlConfiguration();
    config.set("language.default", "ja");

    when(plugin.getConfig()).thenReturn(config);
    when(plugin.getLogger()).thenReturn(Logger.getLogger("PacketI18nTest"));
    when(plugin.getI18n()).thenReturn(i18n);
    when(plugin.getPlayerLanguageStore()).thenReturn(null);

    when(i18n.tr(
        eq("ja"),
        eq("minecraft.packet.multiplayer.player.left"),
        any(I18n.Placeholder[].class)
    )).thenReturn("Translation missing: minecraft.packet.multiplayer.player.left");

    LocalizedPacketMessageProtocolListener listener =
        new LocalizedPacketMessageProtocolListener(plugin);

    Method method = LocalizedPacketMessageProtocolListener.class
        .getDeclaredMethod("localizeJsonDeep", Player.class, String.class);
    method.setAccessible(true);

    String json =
        "{\"translate\":\"multiplayer.player.left\",\"with\":[{\"text\":\"flowmari\"}]}";

    String actual = (String) method.invoke(listener, player, json);

    assertNull(actual);

    verify(i18n).tr(
        eq("ja"),
        eq("minecraft.packet.multiplayer.player.left"),
        any(I18n.Placeholder[].class)
    );
  }

  private static String readPrivateString(Object target, String fieldName) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return (String) field.get(target);
  }
}
