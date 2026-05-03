# TreasureRun i18n Resource Pack

This resource pack is part of TreasureRun's hybrid Minecraft i18n architecture.

It contains `assets/minecraft/lang/*.json` files generated from the official Minecraft 1.20.1 vanilla `en_us.json`.

## Strategy

- Include all vanilla Minecraft language keys as a complete fallback base.
- Use English fallback for keys that have not yet been manually translated.
- Overlay TreasureRun's observed `minecraft.packet.*` translations from `src/main/resources/languages/*.yml`.
- Combine this with ProtocolLib PacketI18n replacement for server-observable translatable packet components.

## Honest limitation

This pack can override client language keys only when the client accepts and applies the resource pack.

It should not be described as absolute control over pre-login, authentication, disconnect, settings, or every client-only UI string.
