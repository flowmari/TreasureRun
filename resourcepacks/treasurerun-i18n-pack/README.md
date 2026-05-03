# TreasureRun i18n Resource Pack Layer

This resource pack is part of TreasureRun's hybrid i18n architecture.

It complements:

1. plugin-level YAML i18n,
2. ProtocolLib packet-level translation for observable server packets,
3. client-side Minecraft language-key overrides through `assets/minecraft/lang/*.json`.

## Important Scope Note

This layer improves coverage for Minecraft language keys that the client resolves from language JSON files.

It does **not** guarantee total control over every Minecraft client-side string. Some UI, pre-login, authentication, disconnect, client-only, and modded/client-controlled text can remain outside server-side control.

Recommended wording:

> TreasureRun uses a hybrid i18n architecture combining plugin YAML translations, ProtocolLib packet-level translation, and a server-side resource-pack language layer. This maximizes localization coverage for server-observable and client language-key based messages while documenting client-only limitations honestly.

