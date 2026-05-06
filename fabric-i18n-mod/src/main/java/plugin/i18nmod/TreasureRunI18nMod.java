package plugin.i18nmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

@Environment(EnvType.CLIENT)
public class TreasureRunI18nMod implements ClientModInitializer {

  public static final Logger LOGGER = LoggerFactory.getLogger("treasurerun_i18n");
  private static final Identifier LANG_CHANNEL = new Identifier("treasurerun", "lang");
  private static final Map<String, String> LANG_MAP = new HashMap<>();

  @Override
  public void onInitializeClient() {
    loadLangMap();
    LOGGER.info("[TreasureRun i18n] {} languages loaded from lang-map.yml", LANG_MAP.size());
    ClientPlayNetworking.registerGlobalReceiver(LANG_CHANNEL, (client, handler, buf, responseSender) -> {
      byte[] payload = new byte[buf.readableBytes()];
      buf.readBytes(payload);

      String trLang = new String(payload, StandardCharsets.UTF_8)
          .toLowerCase()
          .trim();

      String mcLang = toMinecraftLang(trLang);
      LOGGER.info("[TreasureRun i18n] lang sync raw payload='{}' -> minecraft='{}'", trLang, mcLang);
      client.execute(() -> applyLanguage(client, mcLang));
    });
    LOGGER.info("[TreasureRun i18n] Auto-sync active. Add languages via lang-map.yml only.");
  }

  private static void loadLangMap() {
    try (InputStream is = TreasureRunI18nMod.class.getClassLoader()
        .getResourceAsStream("lang-map.yml")) {
      if (is == null) { LOGGER.warn("[TreasureRun i18n] lang-map.yml not found."); return; }
      boolean inMappings = false;
      Scanner scanner = new Scanner(is, StandardCharsets.UTF_8);
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        String trimmed = line.trim();
        if (trimmed.equals("mappings:")) { inMappings = true; continue; }
        if (!inMappings || trimmed.startsWith("#") || trimmed.isEmpty()) continue;
        if (!line.startsWith(" ") && !line.startsWith("\t")) { inMappings = false; continue; }
        if (trimmed.contains(":")) {
          String[] parts = trimmed.split(":\\s*", 2);
          if (parts.length == 2 && !parts[0].trim().isEmpty() && !parts[1].trim().isEmpty())
            LANG_MAP.put(parts[0].trim(), parts[1].trim());
        }
      }
      LOGGER.info("[TreasureRun i18n] lang-map.yml: {} entries", LANG_MAP.size());
    } catch (Throwable t) { LOGGER.warn("[TreasureRun i18n] lang-map load failed: {}", t.getMessage()); }
  }

  private static String toMinecraftLang(String trLang) {
    if (trLang == null || trLang.isBlank()) return "en_us";
    String mc = LANG_MAP.get(trLang);
    if (mc != null) return mc;
    LOGGER.warn("[TreasureRun i18n] '{}' not in lang-map.yml — add it.", trLang);
    return trLang.replace('-', '_');
  }

  private static void applyLanguage(MinecraftClient client, String langCode) {
    try {
      if (langCode.equals(client.options.language)) return;
      LOGGER.info("[TreasureRun i18n] Switching: {} -> {}", client.options.language, langCode);
      client.options.language = langCode;
      client.options.write();
      client.getLanguageManager().setLanguage(langCode);
      client.reloadResources();
    } catch (Throwable t) { LOGGER.warn("[TreasureRun i18n] Switch failed: {}", t.getMessage()); }
  }
}
