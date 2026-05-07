# ResourcePack Aliasing Fallback for Minecraft Client Language Limits

## Overview

TreasureRun uses a hybrid Minecraft i18n architecture across Spigot, ProtocolLib, ResourcePack, and Fabric Client Mod layers.

The goal is to connect the server-side `/lang` selection not only to TreasureRun's own plugin messages, but also to Minecraft's standard translate-key based UI wherever the platform allows it.

## Platform Constraint

A Spigot server cannot directly modify the Minecraft client's `options.language` setting.

As a result, server-side code alone cannot fully switch built-in client UI text such as pause menu labels, multiplayer screen labels, options text, and other vanilla UI elements resolved by Minecraft's translation system.

When the Fabric Client Mod is available, TreasureRun synchronizes the selected server language to the client through Plugin Message based communication.

When the Fabric Client Mod is not available, TreasureRun falls back to ResourcePack aliasing.

## ResourcePack Aliasing Fallback

The fallback generates per-language ResourcePacks that place the selected target-language content under multiple Minecraft locale filenames.

For example, when the selected language is German, the generated fallback pack can provide German translation content under many locale file names. This allows Minecraft's normal translation-key resolution to resolve to German strings even when the server cannot directly change the client's active language setting.

This approach works within Minecraft's existing ResourcePack and translation-loading behavior, without modifying the Minecraft client itself.

## Added Components

- `FabricModDetector`
  - Detects whether the player has the Fabric client-side language sync mod installed.
  - Uses a plugin-message handshake to distinguish Fabric-capable clients from vanilla clients.

- `ResourcePackFallbackService`
  - Sends per-language fallback ResourcePacks.
  - Uses SHA-1 verification for generated packs.

- `ResourcePackFallbackJoinListener`
  - Restores the selected fallback language pack when a player reconnects.

- `LangCommand`
  - Routes `/lang` behavior based on client capability.
  - Fabric-capable clients use Plugin Message based language sync.
  - Non-Fabric clients use ResourcePack aliasing fallback.

- `TreasureRunMultiChestPlugin`
  - Registers the Fabric detector and ResourcePack fallback listener during plugin startup.

- `scripts/generate_fallback_resourcepacks.py`
  - Generates per-language ResourcePack ZIP files for fallback delivery.

## Coverage

This architecture covers multiple i18n layers:

- TreasureRun plugin messages
- Server-sent chat, title, bossbar, and actionbar paths
- ProtocolLib packet-side translation coverage
- ResourcePack-based vanilla translate-key resolution
- Fabric Client Mod based live language sync where available
- ResourcePack fallback behavior where Fabric is not available

## Engineering Value

This design demonstrates cross-boundary problem solving across:

- Spigot plugin architecture
- Minecraft client/server separation
- ProtocolLib packet handling
- ResourcePack translation behavior
- Fabric Client Mod integration
- client capability detection
- fallback design under platform constraints

The key engineering point is not simply translating strings, but designing a layered system that works around Minecraft's client/server boundaries while still providing a practical multilingual player experience.
