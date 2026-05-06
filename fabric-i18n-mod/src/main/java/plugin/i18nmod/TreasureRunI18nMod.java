package plugin.i18nmod;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

@Environment(EnvType.CLIENT)
public class TreasureRunI18nMod implements ClientModInitializer {

  public static final Logger LOGGER = LoggerFactory.getLogger("treasurerun_i18n");
  private static final Identifier LANG_CHANNEL = new Identifier("treasurerun", "lang");
  private static final Map<String, String> LANG_MAP = new HashMap<>();

  @Override
  public void onInitializeClient() {
    loadLangMap();

    LOGGER.info("[TreasureRun i18n] initialized");
    LOGGER.info("[TreasureRun i18n] {} languages loaded from lang-map.yml", LANG_MAP.size());
    LOGGER.info("[TreasureRun i18n] listening channel={}", LANG_CHANNEL);

    ClientPlayNetworking.registerGlobalReceiver(LANG_CHANNEL, (client, handler, buf, responseSender) -> {
      byte[] payload = new byte[buf.readableBytes()];
      buf.readBytes(payload);

      String trLang = decodePayload(payload);
      String mcLang = toMinecraftLang(trLang);

      LOGGER.info("[TreasureRun i18n] lang sync received rawBytes={} treasureRun='{}' minecraft='{}'",
          payload.length, trLang, mcLang);

      client.execute(() -> {
        if (client.player != null) {
          client.player.sendMessage(Text.literal("§b[TreasureRun i18n] payload received: " + trLang + " -> " + mcLang), false);
        }
        applyLanguage(client, mcLang, "server-payload:/lang");
      });
    });

    ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
      dispatcher.register(literal("trlang")
          .then(argument("code", StringArgumentType.word())
              .executes(ctx -> {
                String trLang = StringArgumentType.getString(ctx, "code");
                String mcLang = toMinecraftLang(trLang);
                MinecraftClient client = MinecraftClient.getInstance();

                LOGGER.info("[TreasureRun i18n] /trlang command treasureRun='{}' minecraft='{}'", trLang, mcLang);
                client.execute(() -> applyLanguage(client, mcLang, "client-command:/trlang"));

                return 1;
              })));

      dispatcher.register(literal("trlanguage")
          .then(argument("code", StringArgumentType.word())
              .executes(ctx -> {
                String trLang = StringArgumentType.getString(ctx, "code");
                String mcLang = toMinecraftLang(trLang);
                MinecraftClient client = MinecraftClient.getInstance();

                LOGGER.info("[TreasureRun i18n] /trlanguage command treasureRun='{}' minecraft='{}'", trLang, mcLang);
                client.execute(() -> applyLanguage(client, mcLang, "client-command:/trlanguage"));

                return 1;
              })));
    });

    LOGGER.info("[TreasureRun i18n] Auto-sync active. Use /trlang ja for client-side debug.");
  }

  private static String decodePayload(byte[] payload) {
    if (payload == null || payload.length == 0) return "";

    String raw = new String(payload, StandardCharsets.UTF_8)
        .toLowerCase(Locale.ROOT)
        .trim();

    // Defensive cleanup:
    // If a server accidentally sends Java/DataOutput UTF with length bytes,
    // keep only normal language-code characters.
    String cleaned = raw.replaceAll("[^a-z0-9_\\-]", "");

    if (!raw.equals(cleaned)) {
      LOGGER.info("[TreasureRun i18n] cleaned payload '{}' -> '{}'", raw, cleaned);
    }

    return cleaned;
  }

  private static void loadLangMap() {
    try (InputStream is = TreasureRunI18nMod.class.getClassLoader().getResourceAsStream("lang-map.yml")) {
      if (is == null) {
        LOGGER.warn("[TreasureRun i18n] lang-map.yml not found.");
        return;
      }

      boolean inMappings = false;
      Scanner scanner = new Scanner(is, StandardCharsets.UTF_8);
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        String trimmed = line.trim();

        if (trimmed.equals("mappings:")) {
          inMappings = true;
          continue;
        }

        if (!inMappings || trimmed.startsWith("#") || trimmed.isEmpty()) continue;
        if (!line.startsWith(" ") && !line.startsWith("\t")) {
          inMappings = false;
          continue;
        }

        if (trimmed.contains(":")) {
          String[] parts = trimmed.split(":\\s*", 2);
          if (parts.length == 2 && !parts[0].trim().isEmpty() && !parts[1].trim().isEmpty()) {
            LANG_MAP.put(parts[0].trim().toLowerCase(Locale.ROOT), parts[1].trim().toLowerCase(Locale.ROOT));
          }
        }
      }

      LOGGER.info("[TreasureRun i18n] lang-map.yml: {} entries", LANG_MAP.size());
    } catch (Throwable t) {
      LOGGER.warn("[TreasureRun i18n] lang-map load failed: {}", t.getMessage());
    }
  }

  private static String toMinecraftLang(String trLang) {
    if (trLang == null || trLang.isBlank()) return "en_us";

    String key = trLang.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    String mc = LANG_MAP.get(key);

    if (mc != null && !mc.isBlank()) return mc;

    LOGGER.warn("[TreasureRun i18n] '{}' not in lang-map.yml — fallback replace '-' -> '_'", trLang);
    return key;
  }

  private static void applyLanguage(MinecraftClient client, String langCode, String reason) {
    try {
      if (client == null || langCode == null || langCode.isBlank()) return;

      String before = client.options.language;
      LOGGER.info("[TreasureRun i18n] Switching language reason={} before={} after={}", reason, before, langCode);

      client.options.language = langCode;
      client.options.write();
      client.getLanguageManager().setLanguage(langCode);

      // Important:
      // Always reload even when before == after.
      // The ResourcePack / Fabric language assets may have changed or loaded after join.
      client.reloadResources().thenRun(() -> client.execute(() -> {
        LOGGER.info("[TreasureRun i18n] reloadResources complete language={}", client.options.language);
        if (client.player != null) {
          client.player.sendMessage(Text.literal("§a[TreasureRun i18n] Client language applied: " + langCode), false);
        }
      }));
    } catch (Throwable t) {
      LOGGER.warn("[TreasureRun i18n] Switch failed: {}", t.getMessage(), t);
      try {
        if (client != null && client.player != null) {
          client.player.sendMessage(Text.literal("§c[TreasureRun i18n] Switch failed: " + t.getMessage()), false);
        }
      } catch (Throwable ignored) {}
    }
  }
}
