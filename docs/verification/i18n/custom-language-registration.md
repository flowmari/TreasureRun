# Custom Minecraft Language Registration Verification

This document records the TreasureRun verification for registering custom Minecraft client languages through a client-side ResourcePack.

## Goal

Minecraft standard UI text is not fully controllable from a Spigot plugin alone.

TreasureRun therefore uses a hybrid i18n design:

- Spigot / Bukkit plugin-side language selection
- ProtocolLib packet-level observable message handling
- Server-side ResourcePack for standard translation keys
- Fabric client runtime sync for selected language code
- Client ResourcePack language metadata for custom language registration

This verification focuses on the last part:

> Register custom languages in the Minecraft language list and apply them to standard Minecraft UI screens.

## Verified custom languages

The client language ResourcePack registers:

| Code | Display name | Purpose |
|---|---|---|
| `ojp_jp` | 上代日本語 | Old/Classical Japanese style UI |
| `asl_us` | ASL Gloss | ASL gloss-style UI |
| `sa_in` | संस्कृतम् | Sanskrit UI |
| `la_la` | Latina | Latin UI |
| `lzh_hant` | 文言 | Classical Chinese UI |

## Verified files

Source location:

```text
resourcepacks/client-custom-languages/
  pack.mcmeta
  assets/minecraft/lang/ojp_jp.json
  assets/minecraft/lang/asl_us.json
  assets/minecraft/lang/sa_in.json
  assets/minecraft/lang/la_la.json
  assets/minecraft/lang/lzh_hant.json
```

Build helper:

```text
tools/client-resourcepack/build-custom-language-pack.sh
```

## Verification result

The following Minecraft client state was confirmed locally:

```text
resourcePacks:["fabric","file/treasurerun_custom_languages.zip"]
lang:ojp_jp
```

The following behavior was confirmed:

- `上代日本語` appears in the Minecraft language list.
- `ASL Gloss` appears in the Minecraft language list.
- `संस्कृतम्` appears in the Minecraft language list.
- Title screen text changes according to the selected custom language.
- Multiplayer screen text changes according to the selected custom language.
- Game loading screen text changes according to the selected custom language.

## Representative key samples

For `ojp_jp`:

```text
menu.game = 遊びの目録
menu.returnToGame = 遊びにかへる
menu.options = しつらへ...
menu.disconnect = 離れ去る
options.title = しつらへ
options.language = 言の葉...
gui.done = 終はり
multiplayer.title = もろ人と遊ぶ
```

For `asl_us`:

```text
menu.multiplayer = PLAY TOGETHER
menu.options = SETTINGS...
options.language = LANGUAGE...
```

For `sa_in`:

```text
menu.multiplayer = Bahu-krīḍā
menu.options = Vikalpāḥ...
options.language = Bhāṣā...
```

## Portfolio significance

This demonstrates that TreasureRun does not only translate plugin-owned text.

It also handles a platform-boundary i18n problem:

> Minecraft standard UI language registration and runtime language behavior require a client resource layer, not only a Spigot server plugin.

The implementation keeps local user files out of Git while committing reproducible source assets, scripts, and verification evidence.
