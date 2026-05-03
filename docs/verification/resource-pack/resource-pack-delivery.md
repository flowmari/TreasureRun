# Server-Side Resource Pack Delivery Verification

TreasureRun sends its generated Minecraft i18n resource pack to joining players.

## Current resource pack

- ZIP: `resourcepacks/generated/treasurerun-i18n-pack.zip`
- SHA1: `17500006569f628a0dc5e18d2ca8135917c82a2d`
- URL: `https://raw.githubusercontent.com/flowmari/TreasureRun/03ca968042c3b35dad03ec810525b8f18fa96f53/resourcepacks/generated/treasurerun-i18n-pack.zip`

## Architecture

TreasureRun uses a hybrid Minecraft i18n architecture:

1. plugin YAML translations for TreasureRun gameplay text,
2. ProtocolLib PacketI18n for observable server-sent translatable packet components,
3. server-side resource pack language JSON files for client-resolved Minecraft language keys.

## Runtime behavior

`ResourcePackDeliveryListener` sends the pack on `PlayerJoinEvent`.

The source and server-side config use:

```yaml
resourcePack:
  enabled: true
  force: true
  url: "https://raw.githubusercontent.com/flowmari/TreasureRun/03ca968042c3b35dad03ec810525b8f18fa96f53/resourcepacks/generated/treasurerun-i18n-pack.zip"
  sha1: "17500006569f628a0dc5e18d2ca8135917c82a2d"
```

## Honest limitation

This does not guarantee absolute control over every Minecraft string.

Pre-login, authentication, some disconnect flows, settings screens, and client-only UI may remain outside server-side plugin control.

Recommended wording:

> TreasureRun implements a hybrid i18n architecture combining plugin YAML translations, ProtocolLib packet-level translation, and a server-side resource-pack language layer generated from Minecraft 1.20.1 vanilla language keys. This maximizes practical localization coverage for server-observable packets and client language-key based messages while documenting client-only limitations honestly.
