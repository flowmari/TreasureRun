# ResourcePack Alias Fallback for Minecraft Client Language Limitations

## Portfolio Summary

TreasureRun implements a hybrid Minecraft i18n architecture that handles both Fabric and non-Fabric clients.

The core problem is a Minecraft platform boundary: a Spigot server cannot directly change the client-side `options.language` setting, rewrite `options.txt`, call `LanguageManager#setLanguage`, or trigger `client.reloadResources()` on a vanilla client.

TreasureRun handles this limitation with a two-path design:

- **Fabric client available**: synchronize `/lang` through Plugin Messages and apply the selected Minecraft locale on the client side.
- **Fabric client unavailable**: send a per-language ResourcePack alias fallback so vanilla translation keys resolve to the selected language as far as the client/server boundary allows.

This is not only a translation feature. It is a platform-boundary workaround that combines Spigot, ProtocolLib, ResourcePacks, Fabric integration, runtime capability detection, SHA-1 verified pack delivery, and documented fallback behavior.

## Problem

Minecraft standard UI text is resolved on the client side.

A Spigot plugin can control many server-sent messages, but it cannot fully control every client-local UI string. In particular, a vanilla client does not expose a server-side API for changing the active Minecraft language.

Without a client mod, the server cannot directly perform these client-side operations:

- change `client.options.language`
- rewrite `options.txt`
- call `LanguageManager#setLanguage`
- call `client.reloadResources()`
- fully register custom locale metadata in the Minecraft language menu

Therefore, TreasureRun treats vanilla-client support as a fallback architecture, not as a false claim of total client control.

## Design

### Fabric Client Mod Path

When the Fabric Client Mod is available, TreasureRun uses Plugin Message based synchronization.

Runtime flow:

```text
/lang de
↓
Spigot stores the player's TreasureRun language code
↓
Spigot sends a lightweight Plugin Message payload
↓
Fabric maps TreasureRun language code via lang-map.yml
↓
Fabric applies the Minecraft locale code
↓
Minecraft reloads bundled ResourcePack / lang JSON assets
```

This path is the strongest option because the client mod can operate inside the Minecraft client process.

### Non-Fabric ResourcePack Alias Fallback Path

When the Fabric Client Mod is not available, TreasureRun sends a per-language fallback ResourcePack.

Example for German:

```text
/lang de
↓
Spigot sends treasurerun-i18n-pack-de.zip
↓
The pack contains many locale filenames
↓
Each locale JSON contains German translation content
↓
The client's currently selected locale resolves keys from German content
```

A German fallback pack can include files such as:

```text
assets/minecraft/lang/en_us.json
assets/minecraft/lang/ja_jp.json
assets/minecraft/lang/de_de.json
assets/minecraft/lang/fr_fr.json
assets/minecraft/lang/ko_kr.json
assets/minecraft/lang/sa_in.json
assets/minecraft/lang/ojp_jp.json
```

Each of these files contains the selected target-language content. This is the aliasing strategy.

The result is not a full client language-setting change. It is a practical fallback that makes Minecraft translation-key based text resolve to the selected language wherever ResourcePack language assets are used.

## Implemented Components

- `FabricModDetector`
  - Detects whether the player has the Fabric client-side language sync mod installed.
  - Uses a Plugin Message handshake to distinguish Fabric-capable clients from vanilla clients.

- `ResourcePackFallbackService`
  - Sends per-language fallback ResourcePacks.
  - Uses SHA-1 verification for generated packs.
  - Avoids repeatedly sending the same fallback pack to the same player.

- `ResourcePackFallbackJoinListener`
  - Restores the saved fallback language pack when a player reconnects.
  - Keeps non-Fabric clients covered after join.

- `LangCommand`
  - Routes `/lang` behavior based on client capability.
  - Fabric-capable clients use Plugin Message based language sync.
  - Non-Fabric clients receive the selected language's ResourcePack alias fallback.

- `TreasureRunMultiChestPlugin`
  - Registers the Fabric detector and ResourcePack fallback listener during startup.

- `scripts/generate_fallback_resourcepacks.py`
  - Generates per-language ResourcePack ZIP files.
  - Writes selected-language JSON content under multiple Minecraft locale filenames.

- `resourcepacks/generated/treasurerun-i18n-pack-*.zip`
  - Committed generated fallback packs for the supported TreasureRun languages.

## Runtime Coverage

This architecture covers multiple i18n layers:

| Layer | Strategy |
| --- | --- |
| TreasureRun gameplay text | YAML-backed plugin i18n |
| Server-sent chat/title/actionbar/bossbar paths | Bukkit / ProtocolLib-side replacement |
| Minecraft translation-key based text | ResourcePack language JSON assets |
| Fabric clients | Plugin Message language sync + client reload path |
| Non-Fabric clients | ResourcePack alias fallback |
| Rejoin behavior | Saved language fallback pack re-send |

## Verified Fallback Behavior

The ResourcePack alias fallback is designed to reach the practical maximum for vanilla clients:

```text
/lang de
↓
Fabric Mod available?
├─ yes → Plugin Message sync to Fabric client
└─ no  → send de fallback ResourcePack
        ↓
        multiple locale JSON files contain German content
        ↓
        vanilla translation keys resolve to German where ResourcePack language assets apply
```

The generated fallback ZIPs are SHA-1 verified and tracked in Git so the server can send stable URLs from `config.yml`.

## Limitations

This design is intentionally honest about platform limits.

Without a Fabric Client Mod, TreasureRun cannot guarantee control over:

- pre-login screens
- authentication screens
- client settings screens
- every ESC/options menu string
- Minecraft's language selection menu metadata
- every purely client-local text path

For custom language codes such as `sa`, `ojp`, `la`, and `asl_gloss`, the ResourcePack alias fallback can still provide practical translation behavior through existing locale filenames, but the languages may not appear as fully native Minecraft language-menu entries without client-side registration support.

## Engineering Value

This implementation demonstrates:

- platform constraint analysis
- client/server boundary awareness
- fallback architecture under hard technical limits
- runtime client capability detection
- ResourcePack generation and SHA-1 verified delivery
- separation between server-side i18n and client-side language resolution
- maintainable multilingual asset generation
- realistic documentation of what is and is not technically controllable

The key engineering point is not simply translating strings. It is designing a layered system that works with Minecraft's constraints while maximizing the multilingual player experience across both modded and vanilla clients.
