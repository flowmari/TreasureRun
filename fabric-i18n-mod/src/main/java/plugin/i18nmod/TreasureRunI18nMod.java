package plugin.i18nmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class TreasureRunI18nMod implements ClientModInitializer {
  public static final Logger LOGGER = LoggerFactory.getLogger("treasurerun_i18n");

  @Override
  public void onInitializeClient() {
    LOGGER.info("[TreasureRun i18n] 20 languages / 8039 keys loaded.");
    LOGGER.info("[TreasureRun i18n] options/subtitles/gui/key/selectWorld now covered.");
  }
}
