package plugin.i18nmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class TreasureRunI18nMod implements ClientModInitializer {

  public static final Logger LOGGER = LoggerFactory.getLogger("treasurerun_i18n");
  private static final Identifier LANG_CHANNEL = new Identifier("treasurerun", "lang");

  @Override
  public void onInitializeClient() {
    LOGGER.info("[TreasureRun i18n] 20 languages / 8039 keys loaded.");

    ClientPlayNetworking.registerGlobalReceiver(LANG_CHANNEL, (client, handler, buf, responseSender) -> {
      String trLang = buf.readString(64);
      String mcLang = toMinecraftLang(trLang);
      LOGGER.info("[TreasureRun i18n] lang sync: {} -> {}", trLang, mcLang);
      client.execute(() -> applyLanguage(client, mcLang));
    });

    LOGGER.info("[TreasureRun i18n] Auto-sync active on channel: {}", LANG_CHANNEL);
  }

  private static void applyLanguage(MinecraftClient client, String langCode) {
    try {
      if (langCode.equals(client.options.language)) return;
      LOGGER.info("[TreasureRun i18n] Switching: {} -> {}", client.options.language, langCode);
      client.options.language = langCode;
      client.options.write();
      client.getLanguageManager().setLanguage(langCode);
      client.reloadResources();
    } catch (Throwable t) {
      LOGGER.warn("[TreasureRun i18n] Switch failed: {}", t.getMessage());
    }
  }

  private static String toMinecraftLang(String trLang) {
    if (trLang == null || trLang.isBlank()) return "en_us";
    return switch (trLang.toLowerCase().trim()) {
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
      default          -> trLang;
    };
  }
}
