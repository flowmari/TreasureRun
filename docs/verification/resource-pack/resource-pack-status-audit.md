# Resource Pack Status Audit

This document records the runtime verification layer for TreasureRun's server-side resource pack delivery.

## Purpose

TreasureRun uses a hybrid Minecraft i18n architecture:

1. plugin YAML translations for TreasureRun gameplay text,
2. ProtocolLib PacketI18n for observable server-sent translatable packets,
3. server-side resource-pack language JSON files for client-resolved Minecraft language keys.

This status audit records whether the client accepted, declined, failed to download, or successfully loaded the server-delivered resource pack.

## Runtime evidence

- `docs/verification/runtime-evidence/resource-pack-delivery-status-20260503_205315.txt`
- `docs/verification/runtime-evidence/hybrid-i18n-final-keys-20260503_205315.txt`

## Verification target

Expected successful runtime evidence includes:

- `[ResourcePack][STATUS] status=ACCEPTED`
- `[ResourcePack][STATUS] status=SUCCESSFULLY_LOADED`
- PacketI18n audit lines such as `translate=...`
- PacketI18n replacement evidence when `packetMessages.replaceTranslatedComponents=true` is temporarily enabled for verification

## Honest limitation

This architecture maximizes practical coverage for server-observable packets and client language-key based messages.

It should not be described as absolute full control over every Minecraft string. Some text remains outside guaranteed server-side control, including pre-login, authentication, settings, disconnect flows, and some client-only UI.
