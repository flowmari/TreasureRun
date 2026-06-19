package com.treasurerun.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PacketI18nJsonLocalizerTest {

    @Test
    void localizeJson_replacesSimpleTranslateComponent() {
        String json = "{\"translate\":\"multiplayer.player.left\",\"with\":[{\"text\":\"flowmari\"}]}";

        String actual = PacketI18nJsonLocalizer.localizeJson(
                json,
                "ojp",
                (lang, key, placeholders) -> {
                    assertEquals("ojp", lang);
                    assertEquals("minecraft.packet.multiplayer.player.left", key);
                    assertEquals(1, placeholders.length);
                    assertEquals("arg0", placeholders[0].name());
                    assertEquals("flowmari", placeholders[0].value());
                    return placeholders[0].value() + "、TreasureRun の世を離れ給ひぬ。";
                }
        );

        assertEquals("{\"text\":\"flowmari、TreasureRun の世を離れ給ひぬ。\"}", actual);
    }

    @Test
    void localizeJson_returnsNullWhenTranslatorReturnsNull() {
        String json = "{\"translate\":\"multiplayer.player.left\",\"with\":[{\"text\":\"flowmari\"}]}";

        String actual = PacketI18nJsonLocalizer.localizeJson(
                json,
                "ojp",
                (lang, key, placeholders) -> null
        );

        assertNull(actual);
    }

    @Test
    void localizeJson_returnsNullForInvalidJson() {
        String actual = PacketI18nJsonLocalizer.localizeJson(
                "{not json",
                "ojp",
                (lang, key, placeholders) -> "unused"
        );

        assertNull(actual);
    }

    @Test
    void localizeJson_findsNestedTranslateComponent() {
        String json = """
                {
                  "extra": [
                    {"text": "prefix"},
                    {"translate": "multiplayer.player.joined", "with": [{"text": "flowmari"}]}
                  ]
                }
                """;

        String actual = PacketI18nJsonLocalizer.localizeJson(
                json,
                "sa",
                (lang, key, placeholders) -> {
                    assertEquals("sa", lang);
                    assertEquals("minecraft.packet.multiplayer.player.joined", key);
                    assertEquals("flowmari", placeholders[0].value());
                    return "flowmari TreasureRun मध्ये प्रविष्टः।";
                }
        );

        assertEquals("{\"text\":\"flowmari TreasureRun मध्ये प्रविष्टः।\"}", actual);
    }

    @Test
    void toPluginPacketKey_prefixesMinecraftPacketNamespace() {
        assertEquals(
                "minecraft.packet.multiplayer.player.left",
                PacketI18nJsonLocalizer.toPluginPacketKey("multiplayer.player.left")
        );
    }
}
