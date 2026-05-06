#!/usr/bin/env bash
set -euo pipefail

echo "============================================================"
echo " FIX no-Loom runtime compile error"
echo " - fix lambda final/effectively-final client variable"
echo " - receive treasurerun:lang"
echo " - update options.txt"
echo " - try live language reload"
echo "============================================================"

ROOT="$PWD"
MC_DIR="$HOME/Library/Application Support/minecraft"
MODS_DIR="$MC_DIR/mods"
TMP="$ROOT/tmp_i18n_fix/no_loom_lang_live_reload_fixed"
SRC="$TMP/src/plugin/i18nmod"
CLS="$TMP/classes"
JAR="$TMP/treasurerun-i18n-no-loom-runtime.jar"
RES="$ROOT/fabric-i18n-mod/src/main/resources"
LOADER_JAR="$MC_DIR/libraries/net/fabricmc/fabric-loader/0.14.22/fabric-loader-0.14.22.jar"

mkdir -p "$SRC" "$CLS" "$MODS_DIR"

echo ""
echo "=== 1) Kill Minecraft / Launcher ==="
osascript -e 'tell application "Minecraft Launcher" to quit' 2>/dev/null || true
osascript -e 'tell application "Minecraft" to quit' 2>/dev/null || true
sleep 2
pkill -9 -f "/Applications/Minecraft.app/Contents/MacOS/launcher" 2>/dev/null || true
pkill -9 -f "launcher-Helper" 2>/dev/null || true
pkill -9 -f "net.minecraft" 2>/dev/null || true
pkill -9 -f "com.mojang" 2>/dev/null || true
pkill -9 -f "fabric-loader" 2>/dev/null || true
sleep 2

ps aux | egrep -i "Minecraft|Minecraft Launcher|net.minecraft|com.mojang|launcher-Helper|fabric-loader" | grep -v egrep || echo "OK: no Minecraft / Launcher process remains"

echo ""
echo "=== 2) Remove old TreasureRun i18n jars ==="
find "$MODS_DIR" -maxdepth 1 -type f \( \
  -iname "treasurerun-i18n-no-loom-runtime.jar" -o \
  -iname "treasurerun-i18n-resource-only.jar" -o \
  -iname "treasurerun-i18n-real.jar" -o \
  -iname "treasurerun-i18n-runtime.jar" -o \
  -iname "*runtime-hotswap*.jar" \
\) -print -delete || true

echo ""
echo "=== 3) Verify dependencies/resources ==="
test -f "$LOADER_JAR" || { echo "ERROR: loader jar missing: $LOADER_JAR"; exit 1; }
test -d "$RES/assets/minecraft/lang" || { echo "ERROR: lang resources missing"; exit 1; }

ls -lh "$LOADER_JAR"
find "$MODS_DIR" -maxdepth 1 -type f -iname "fabric-api*.jar" -exec ls -lh {} \; || {
  echo "ERROR: Fabric API jar missing in mods folder"
  exit 1
}

echo "lang_json_count=$(find "$RES/assets/minecraft/lang" -maxdepth 1 -name '*.json' | wc -l | tr -d ' ')"

echo ""
echo "=== 4) Write fixed no-Loom Java mod ==="
cat > "$SRC/TreasureRunI18nMod.java" <<'JAVA'
package plugin.i18nmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class TreasureRunI18nMod implements ClientModInitializer {
    private static final String CHANNEL_NS = "treasurerun";
    private static final String CHANNEL_PATH = "lang";

    @Override
    public void onInitializeClient() {
        log("boot: no-Loom live-reload runtime loaded");
        try {
            registerReceiver();
            log("registered channel: " + CHANNEL_NS + ":" + CHANNEL_PATH);
        } catch (Throwable t) {
            log("ERROR: receiver registration failed: " + t);
            t.printStackTrace();
        }
    }

    private static void registerReceiver() throws Exception {
        MappingResolver mr = FabricLoader.getInstance().getMappingResolver();

        Class<?> identifierClass = Class.forName(mr.mapClassName("intermediary", "net.minecraft.class_2960"));
        Object id = identifierClass.getConstructor(String.class, String.class).newInstance(CHANNEL_NS, CHANNEL_PATH);

        Class<?> networkingClass = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");

        Class<?> handlerInterface = null;
        for (Class<?> c : networkingClass.getDeclaredClasses()) {
            if (c.getName().contains("PlayChannelHandler")) {
                handlerInterface = c;
                break;
            }
        }
        if (handlerInterface == null) {
            throw new IllegalStateException("ClientPlayNetworking.PlayChannelHandler not found");
        }

        Object proxy = Proxy.newProxyInstance(
                TreasureRunI18nMod.class.getClassLoader(),
                new Class<?>[]{handlerInterface},
                (Object p, Method method, Object[] args) -> {
                    if (args == null) return null;

                    Object detectedClient = null;
                    Object buf = null;

                    for (Object a : args) {
                        if (a == null) continue;
                        String cn = a.getClass().getName();
                        if (cn.contains("MinecraftClient") || cn.contains("class_310")) {
                            detectedClient = a;
                        }
                        if (hasMethod(a.getClass(), "readableBytes")) {
                            buf = a;
                        }
                    }

                    final Object clientFinal = detectedClient;
                    final String raw = readPayload(buf);
                    final String mcLang = mapLang(raw);

                    log("payload received: '" + raw + "' -> '" + mcLang + "'");

                    Runnable task = () -> {
                        try {
                            updateOptionsTxt(mcLang);
                            applyLanguageLive(clientFinal, mcLang);
                        } catch (Throwable t) {
                            log("ERROR: apply language failed: " + t);
                            t.printStackTrace();
                        }
                    };

                    if (clientFinal != null && tryClientExecute(clientFinal, task)) {
                        return null;
                    }

                    task.run();
                    return null;
                }
        );

        Method register = null;
        for (Method m : networkingClass.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) continue;
            if (!m.getName().equals("registerGlobalReceiver")) continue;
            Class<?>[] pts = m.getParameterTypes();
            if (pts.length == 2) {
                register = m;
                break;
            }
        }

        if (register == null) {
            throw new IllegalStateException("registerGlobalReceiver method not found");
        }

        register.invoke(null, id, proxy);
    }

    private static boolean hasMethod(Class<?> c, String name) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name)) return true;
        }
        return false;
    }

    private static String readPayload(Object buf) {
        if (buf == null) return "";
        try {
            Method readableBytes = buf.getClass().getMethod("readableBytes");
            int n = ((Number) readableBytes.invoke(buf)).intValue();
            if (n <= 0) return "";

            byte[] b = new byte[n];
            Method readBytes = buf.getClass().getMethod("readBytes", byte[].class);
            readBytes.invoke(buf, (Object) b);

            return new String(b, StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            log("ERROR: payload read failed: " + t);
            return "";
        }
    }

    private static String mapLang(String raw) {
        if (raw == null) return "ja_jp";
        String s = raw.trim().toLowerCase(Locale.ROOT);

        switch (s) {
            case "ja": return "ja_jp";
            case "en": return "en_us";
            case "de": return "de_de";
            case "fr": return "fr_fr";
            case "es": return "es_es";
            case "it": return "it_it";
            case "sv": return "sv_se";
            case "nl": return "nl_nl";
            case "pt": return "pt_br";
            case "ru": return "ru_ru";
            case "ko": return "ko_kr";
            case "zh_tw": return "zh_tw";
            case "hi": return "hi_in";
            case "fi": return "fi_fi";
            case "is": return "is_is";
            case "la": return "la_la";
            case "sa": return "sa_in";
            case "lzh": return "lzh_hant";
            case "wmy":
            case "ojp": return "ojp_jp";
            case "asl_gloss": return "asl_us";
            default:
                if (s.contains("_")) return s;
                return "ja_jp";
        }
    }

    private static boolean tryClientExecute(Object client, Runnable task) {
        try {
            for (Method m : client.getClass().getMethods()) {
                if (!m.getName().equals("execute")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 1 && Runnable.class.isAssignableFrom(pts[0])) {
                    m.invoke(client, task);
                    return true;
                }
            }
        } catch (Throwable t) {
            log("WARN: client.execute failed, run inline: " + t);
        }
        return false;
    }

    private static void updateOptionsTxt(String mcLang) throws IOException {
        File gameDir = FabricLoader.getInstance().getGameDir().toFile();
        File options = new File(gameDir, "options.txt");

        List<String> lines = new ArrayList<>();
        boolean found = false;

        if (options.isFile()) {
            lines.addAll(Files.readAllLines(options.toPath(), StandardCharsets.UTF_8));
        }

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("lang:")) {
                lines.set(i, "lang:" + mcLang);
                found = true;
            }
        }

        if (!found) {
            lines.add("lang:" + mcLang);
        }

        Files.write(options.toPath(), lines, StandardCharsets.UTF_8);
        log("options.txt updated: lang:" + mcLang);
    }

    private static void applyLanguageLive(Object clientMaybe, String mcLang) throws Exception {
        MappingResolver mr = FabricLoader.getInstance().getMappingResolver();

        Class<?> mcClass = Class.forName(mr.mapClassName("intermediary", "net.minecraft.class_310"));
        Object client = clientMaybe;

        if (client == null || !mcClass.isInstance(client)) {
            client = findMinecraftClientInstance(mcClass);
        }

        if (client == null) {
            log("WARN: MinecraftClient instance not found");
            return;
        }

        Class<?> langManagerClass = Class.forName(mr.mapClassName("intermediary", "net.minecraft.class_1076"));
        Object langManager = findReturnObject(client, langManagerClass);

        if (langManager == null) {
            log("WARN: LanguageManager not found");
        } else {
            boolean setOk = false;

            for (Method m : langManagerClass.getDeclaredMethods()) {
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 1 && pts[0] == String.class && m.getReturnType() == Void.TYPE) {
                    try {
                        m.setAccessible(true);
                        m.invoke(langManager, mcLang);
                        log("LanguageManager string setter invoked: " + m.getName() + " -> " + mcLang);
                        setOk = true;
                        break;
                    } catch (Throwable ignored) {
                    }
                }
            }

            if (!setOk) {
                log("WARN: LanguageManager string setter not found");
            }
        }

        boolean reloadOk = false;

        for (Method m : mcClass.getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && CompletableFuture.class.isAssignableFrom(m.getReturnType())) {
                try {
                    m.setAccessible(true);
                    Object r = m.invoke(client);
                    log("MinecraftClient reload-like method invoked: " + m.getName() + " result=" + r);
                    reloadOk = true;
                    break;
                } catch (Throwable ignored) {
                }
            }
        }

        if (!reloadOk) {
            log("WARN: reloadResources-like CompletableFuture method not found");
        }
    }

    private static Object findMinecraftClientInstance(Class<?> mcClass) {
        for (Method m : mcClass.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers())
                    && m.getParameterCount() == 0
                    && mcClass.isAssignableFrom(m.getReturnType())) {
                try {
                    m.setAccessible(true);
                    Object v = m.invoke(null);
                    if (v != null) return v;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Object findReturnObject(Object owner, Class<?> returnClass) {
        for (Method m : owner.getClass().getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && returnClass.isAssignableFrom(m.getReturnType())) {
                try {
                    m.setAccessible(true);
                    Object v = m.invoke(owner);
                    if (v != null) return v;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static void log(String msg) {
        System.out.println("[TreasureRun i18n no-loom] " + msg);
    }
}
JAVA

echo ""
echo "=== 5) Compile Java without Loom ==="
rm -rf "$CLS"
mkdir -p "$CLS"

javac -encoding UTF-8 -source 17 -target 17 \
  -cp "$LOADER_JAR" \
  -d "$CLS" \
  "$SRC/TreasureRunI18nMod.java"

echo "OK: javac compile success"

echo ""
echo "=== 6) Build jar manually ==="
rm -f "$JAR"
WORK="$TMP/jarwork"
rm -rf "$WORK"
mkdir -p "$WORK"

cp -R "$CLS"/* "$WORK/"
cp -R "$RES/assets" "$WORK/"
cp "$RES/lang-map.yml" "$WORK/lang-map.yml" 2>/dev/null || true

cat > "$WORK/fabric.mod.json" <<'JSON'
{
  "schemaVersion": 1,
  "id": "treasurerun_i18n",
  "version": "1.0.0",
  "name": "TreasureRun i18n no-Loom live reload runtime",
  "description": "No-Loom Fabric client runtime for TreasureRun language sync and live reload.",
  "authors": ["TreasureRun"],
  "license": "MIT",
  "environment": "client",
  "entrypoints": {
    "client": ["plugin.i18nmod.TreasureRunI18nMod"]
  },
  "depends": {
    "fabricloader": ">=0.14.22",
    "minecraft": "1.20.1",
    "java": ">=17",
    "fabric-api": "*"
  }
}
JSON

(
  cd "$WORK"
  jar cf "$JAR" .
)

ls -lh "$JAR"

echo ""
echo "=== 7) Verify jar contents ==="
jar tf "$JAR" | grep -E 'fabric.mod.json|plugin/i18nmod/TreasureRunI18nMod.class|assets/minecraft/lang/(ja_jp|en_us|de_de|fr_fr|zh_tw|ojp_jp)\.json|lang-map.yml' || true

SIZE="$(wc -c < "$JAR" | tr -d ' ')"
echo "jar_size=$SIZE"

if [ "$SIZE" -lt 2000000 ]; then
  echo "ERROR: jar too small"
  exit 1
fi

if ! jar tf "$JAR" | grep -q 'plugin/i18nmod/TreasureRunI18nMod.class'; then
  echo "ERROR: class missing"
  exit 1
fi

if ! jar tf "$JAR" | grep -q 'assets/minecraft/lang/en_us.json'; then
  echo "ERROR: en_us.json missing"
  exit 1
fi

echo "OK: live-reload no-Loom runtime jar is valid."

echo ""
echo "=== 8) Install fixed no-Loom runtime jar ==="
cp "$JAR" "$MODS_DIR/treasurerun-i18n-no-loom-runtime.jar"
ls -lh "$MODS_DIR/treasurerun-i18n-no-loom-runtime.jar"

echo ""
echo "=== 9) Clear resource pack cache ==="
rm -rf "$MC_DIR/server-resource-packs"
mkdir -p "$MC_DIR/server-resource-packs"
ls -la "$MC_DIR/server-resource-packs"

echo ""
echo "=== 10) Final mods folder ==="
find "$MODS_DIR" -maxdepth 1 -type f \( -iname "fabric-api*.jar" -o -iname "*treasurerun*i18n*.jar" \) -exec ls -lh {} \;

echo ""
echo "============================================================"
echo "DONE"
echo ""
echo "Now launch Minecraft:"
echo "1. Minecraft Launcher"
echo "2. fabric-loader-0.14.22-1.20.1"
echo "3. JAVAをプレイ"
echo "4. Join localhost:25565"
echo "5. Run:"
echo "   /lang en"
echo "   wait 10 sec, press Esc"
echo "   /lang de"
echo "   wait 10 sec, press Esc"
echo "   /lang ja"
echo "   wait 10 sec, press Esc"
echo ""
echo "If menu still does not switch, paste this:"
echo "grep -n \"TreasureRun i18n no-loom\" \"$MC_DIR/logs/latest.log\" | tail -120"
echo "============================================================"
