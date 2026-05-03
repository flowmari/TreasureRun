# Official Mojang lang based resource pack rebuild

Date: 2026-05-04 04:31:08

TreasureRun rebuilt its server-side resource pack using Mojang's official Minecraft 1.20.1 language assets as the base layer.

## Layers

- ResourcePack layer: client lang key override
- PacketI18n layer: ProtocolLib packet audit / replace
- Plugin i18n layer: TreasureRun YAML messages

## Generated artifact

- resourcepacks/generated/treasurerun-i18n-pack.zip
- SHA1: 33b80018de13a137a5d78368880e25626bcd0292
- URL: https://raw.githubusercontent.com/flowmari/TreasureRun/main/resourcepacks/generated/treasurerun-i18n-pack.zip

## Scope

This broadens localization coverage for Minecraft standard messages after server join.
It does not claim absolute control over pre-login, authentication, settings screens,
or purely client-local UI that the server cannot control.
