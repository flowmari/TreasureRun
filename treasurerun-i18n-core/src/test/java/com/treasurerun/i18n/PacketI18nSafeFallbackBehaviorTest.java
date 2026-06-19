package com.treasurerun.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies safe no-replacement behavior for packet JSON localization.
 *
 * The pure localizer should return null when replacement is unsafe or unavailable.
 * The ProtocolLib boundary can then keep the original packet content instead of
 * producing broken JSON or an empty message.
 */
class PacketI18nSafeFallbackBehaviorTest {

    @Test
    void returnsNullWhenJsonHasNoTranslateKey() {
        String actual = PacketI18nJsonLocalizer.localizeJson(
                "{\"text\":\"plain message\"}",
                "en",
                (lang, key, placeholders) -> "unused"
        );

        assertNull(actual);
    }

    @Test
    void returnsNullWhenTranslatorReturnsBlankText() {
        String actual = PacketI18nJsonLocalizer.localizeJson(
                "{\"translate\":\"multiplayer.player.joined\",\"with\":[{\"text\":\"flowmari\"}]}",
                "en",
                (lang, key, placeholders) -> "   "
        );

        assertNull(actual);
    }

    @Test
    void returnsNullWhenRawJsonIsNullOrBlank() {
        assertNull(PacketI18nJsonLocalizer.localizeJson(
                null,
                "en",
                (lang, key, placeholders) -> "unused"
        ));

        assertNull(PacketI18nJsonLocalizer.localizeJson(
                "   ",
                "en",
                (lang, key, placeholders) -> "unused"
        ));
    }

    @Test
    void returnsNullWhenLanguageIsNullOrBlank() {
        String json = "{\"translate\":\"multiplayer.player.left\"}";

        assertNull(PacketI18nJsonLocalizer.localizeJson(
                json,
                null,
                (lang, key, placeholders) -> "unused"
        ));

        assertNull(PacketI18nJsonLocalizer.localizeJson(
                json,
                "   ",
                (lang, key, placeholders) -> "unused"
        ));
    }
}
