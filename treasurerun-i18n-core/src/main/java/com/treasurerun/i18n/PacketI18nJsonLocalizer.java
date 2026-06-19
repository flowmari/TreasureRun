package com.treasurerun.i18n;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure Java localizer for Minecraft packet JSON messages.
 *
 * Boundary rule:
 * - no server API imports
 * - no packet-library imports
 * - no game-entity imports
 *
 * This class only knows:
 * - raw JSON string
 * - selected language code
 * - a translator callback
 *
 * The outer adapter remains responsible for packet access and runtime language lookup.
 */
public final class PacketI18nJsonLocalizer {

    private static final Gson GSON = new Gson();

    private PacketI18nJsonLocalizer() {
        // utility class
    }

    @FunctionalInterface
    public interface Translator {
        /**
         * @return localized text, or null when the key should not be replaced.
         */
        String translate(String lang, String i18nKey, Placeholder[] placeholders);
    }

    /**
     * Own placeholder type so this class remains independent from plugin.I18n internals.
     */
    public record Placeholder(String name, String value) {
    }

    /**
     * Localize a Minecraft JSON chat/title/actionbar component.
     *
     * @return rewritten JSON text component, or null when no safe replacement should happen.
     */
    public static String localizeJson(String rawJson, String lang, Translator translator) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        if (lang == null || lang.isBlank()) {
            return null;
        }
        Objects.requireNonNull(translator, "translator");

        final JsonElement root;
        try {
            root = JsonParser.parseString(rawJson);
        } catch (RuntimeException ex) {
            return null;
        }

        Replacement replacement = findFirstReplacement(root, lang, translator);
        if (replacement == null || replacement.localizedText == null || replacement.localizedText.isBlank()) {
            return null;
        }

        JsonObject out = new JsonObject();
        out.addProperty("text", replacement.localizedText);
        return GSON.toJson(out);
    }

    private static Replacement findFirstReplacement(JsonElement element, String lang, Translator translator) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            if (obj.has("translate") && obj.get("translate").isJsonPrimitive()) {
                String minecraftTranslateKey = obj.get("translate").getAsString();
                String pluginKey = toPluginPacketKey(minecraftTranslateKey);

                Placeholder[] placeholders = extractPlaceholders(obj);
                String translated = translator.translate(lang, pluginKey, placeholders);

                if (translated == null || translated.isBlank()) {
                    return null;
                }

                return new Replacement(translated);
            }

            for (String key : obj.keySet()) {
                Replacement nested = findFirstReplacement(obj.get(key), lang, translator);
                if (nested != null) {
                    return nested;
                }
            }

            return null;
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                Replacement nested = findFirstReplacement(child, lang, translator);
                if (nested != null) {
                    return nested;
                }
            }
        }

        return null;
    }

    static String toPluginPacketKey(String minecraftTranslateKey) {
        if (minecraftTranslateKey == null || minecraftTranslateKey.isBlank()) {
            return "";
        }
        return "minecraft.packet." + minecraftTranslateKey;
    }

    private static Placeholder[] extractPlaceholders(JsonObject obj) {
        if (!obj.has("with") || !obj.get("with").isJsonArray()) {
            return new Placeholder[0];
        }

        JsonArray with = obj.getAsJsonArray("with");
        List<Placeholder> placeholders = new ArrayList<>();

        for (int i = 0; i < with.size(); i++) {
            JsonElement item = with.get(i);
            String value = extractPlaceholderValue(item);
            placeholders.add(new Placeholder("arg" + i, value));
        }

        return placeholders.toArray(new Placeholder[0]);
    }

    private static String extractPlaceholderValue(JsonElement item) {
        if (item == null || item.isJsonNull()) {
            return "";
        }

        if (item.isJsonPrimitive()) {
            return item.getAsString();
        }

        if (item.isJsonObject()) {
            JsonObject obj = item.getAsJsonObject();

            if (obj.has("text") && obj.get("text").isJsonPrimitive()) {
                return obj.get("text").getAsString();
            }

            if (obj.has("translate") && obj.get("translate").isJsonPrimitive()) {
                return obj.get("translate").getAsString();
            }
        }

        return item.toString();
    }

    private record Replacement(String localizedText) {
    }
}
