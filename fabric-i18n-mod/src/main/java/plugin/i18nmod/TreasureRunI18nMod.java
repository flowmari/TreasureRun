package plugin.i18nmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
public class TreasureRunI18nMod implements ClientModInitializer {

  public static final Logger LOGGER = LoggerFactory.getLogger("treasurerun_i18n");
  private static final Identifier LANG_CHANNEL = new Identifier("treasurerun", "lang");

  private static final AtomicBoolean RELOAD_IN_FLIGHT = new AtomicBoolean(false);
  private static String lastAppliedLanguage = "";

  @Override
  public void onInitializeClient() {
    LOGGER.info("[TreasureRun i18n] Fabric runtime language hot-swap enabled.");
    LOGGER.info("[TreasureRun i18n] Runtime payload strategy: selected language code only, not full JSON data.");

    ClientPlayNetworking.registerGlobalReceiver(LANG_CHANNEL, (client, handler, buf, responseSender) -> {
      String treasureRunLang = buf.readString(64);
      String minecraftLang = toMinecraftLang(treasureRunLang);

      LOGGER.info("[TreasureRun i18n] lang payload received: treasureRunLang={} minecraftLang={}",
          treasureRunLang, minecraftLang);

      client.execute(() -> applyLanguageHotSwap(client, minecraftLang, "server-payload:" + sanitize(treasureRunLang)));
    });

    LOGGER.info("[TreasureRun i18n] Listening on channel: {}", LANG_CHANNEL);
  }

  private static void applyLanguageHotSwap(MinecraftClient client, String langCode, String reason) {
    try {
      if (client == null || langCode == null || langCode.isBlank()) return;

      String before = client.options.language == null ? "" : client.options.language;

      if (langCode.equals(before) && langCode.equals(lastAppliedLanguage) && !RELOAD_IN_FLIGHT.get()) {
        LOGGER.info("[TreasureRun i18n] language already active; skip reload lang={} reason={}", langCode, reason);
        return;
      }

      LOGGER.info("[TreasureRun i18n] runtime language hot-swap start: {} -> {} reason={}",
          before, langCode, reason);

      client.options.language = langCode;
      client.options.write();
      client.getLanguageManager().setLanguage(langCode);
      lastAppliedLanguage = langCode;

      if (!RELOAD_IN_FLIGHT.compareAndSet(false, true)) {
        LOGGER.info("[TreasureRun i18n] resource reload already in flight; language state saved lang={}", langCode);
        return;
      }

      CompletableFuture<Void> reloadFuture = client.reloadResources();
      reloadFuture.whenComplete((ignored, throwable) -> client.execute(() -> {
        RELOAD_IN_FLIGHT.set(false);

        if (throwable != null) {
          LOGGER.warn("[TreasureRun i18n] runtime language hot-swap reload failed lang={} error={}",
              langCode, throwable.getMessage());
          return;
        }

        LOGGER.info("[TreasureRun i18n] runtime language hot-swap complete lang={} via resource reload", langCode);
      }));
    } catch (Throwable t) {
      RELOAD_IN_FLIGHT.set(false);
      LOGGER.warn("[TreasureRun i18n] runtime language hot-swap failed lang={} error={}",
          langCode, t.getMessage());
    }
  }

  private static String toMinecraftLang(String treasureRunLang) {
    if (treasureRunLang == null || treasureRunLang.isBlank()) return "en_us";

    return switch (treasureRunLang.toLowerCase().trim()) {
      case "ja"        -> "ja_jp";
      case "en"        -> "en_us";
      case "de"        -> "de_de";
      case "fr"        -> "fr_fr";
      case "es"        -> "es_es";
      case "it"        -> "it_it";
      case "ko"        -> "ko_kr";
      case "ru"        -> "ru_ru";
      case "zh_tw"     -> "zh_tw";
      case "pt"        -> "pt_br";
      case "nl"        -> "nl_nl";
      case "fi"        -> "fi_fi";
      case "sv"        -> "sv_se";
      case "is"        -> "is_is";
      case "la"        -> "la_la";
      case "hi"        -> "hi_in";
      case "sa"        -> "sa_in";
      case "lzh"       -> "lzh_hant";
      case "ojp"       -> "ojp_jp";
      case "asl_gloss" -> "asl_us";
      default          -> treasureRunLang.toLowerCase().trim().replace('-', '_');
    };
  }

  private static String sanitize(String value) {
    if (value == null || value.isBlank()) return "unknown";
    return value.replaceAll("[^a-zA-Z0-9_:/.-]", "_");
  }
}
