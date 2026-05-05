# Minecraft Standard Message i18n Architecture

This document summarizes the Minecraft standard-message i18n work in TreasureRun.

## What this proves

TreasureRun implements a hybrid i18n architecture for Minecraft 1.20.1 standard translation keys by combining:

- Spigot plugin logic
- ProtocolLib packet audit / rewrite
- Server-side ResourcePack language overrides
- Fabric client mod assets
- Real-time language synchronization from server to client
- Audit reports for key coverage and translation-source tracking

The goal is not to modify the Minecraft engine itself.  
The goal is to work around platform boundaries by combining multiple layers that each cover a different part of Minecraft's message pipeline.

## Final key coverage

Both of the following asset sets are aligned to the same Minecraft standard translation key set:

- `fabric-i18n-mod/src/main/resources/assets/minecraft/lang/*.json`
- `resourcepacks/treasurerun-i18n-pack/assets/minecraft/lang/*.json`

Each locale JSON contains:

```text
8039 keys
```

This means the Fabric Mod and ResourcePack layers share the same standard-message key coverage.

## Official translation strategy

For locales supported by Mojang's official Minecraft 1.20.1 language assets, TreasureRun imports and applies the official translations.

For custom or unofficial locales, TreasureRun does not pretend that fallback text is native-quality translation.  
Instead, missing/non-official coverage is kept functional for runtime safety and separated into a review report:

- `docs/i18n_audit/native_translation_todo_unofficial_languages.tsv`

This keeps the system robust while making translation quality debt visible.

## Runtime architecture

TreasureRun uses a layered approach:

1. **Spigot plugin**
   - Stores the player's selected language.
   - Sends language state to the client.
   - Handles server-side i18n for plugin-controlled messages.

2. **ProtocolLib**
   - Audits and rewrites server-interceptable Minecraft translation packets.

3. **ResourcePack**
   - Provides Minecraft standard translation-key overrides for server-delivered assets.

4. **Fabric Mod**
   - Ships client-side Minecraft language assets for areas that cannot be fully reached by server-side techniques alone.
   - Receives the selected language from the server and adapts the Minecraft locale automatically.

## Engineering value

This work demonstrates:

- Understanding of platform boundaries
- Multi-layer architecture design
- Runtime verification
- Key-coverage normalization
- Resource integrity management through zip / SHA1 verification
- Separation of technical completeness from translation-quality review

In practical terms, this is a platform-constraint workaround: rather than assuming Spigot alone can control every Minecraft client message, the project combines server, packet, resource-pack, and client-mod layers to maximize i18n coverage.
