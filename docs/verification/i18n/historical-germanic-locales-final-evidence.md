# Historical Germanic Locales Final Evidence

## Summary

TreasureRun added experimental historical Germanic locale support:

- `ang -> ang_gb`: Old English / Ænglisc
- `non -> non_is`: Old Norse / Dǫnsk tunga

This change is not positioned as a claim of native-quality historical-language translation.
It is evidence that TreasureRun's platform-boundary i18n architecture can safely extend non-standard locale codes across multiple layers.

## Verified architecture layers

- Plugin YAML language files: `src/main/resources/languages/ang.yml`, `src/main/resources/languages/non.yml`
- Shared language mapping: `src/main/resources/lang-map.yml`
- Fabric language mapping: `fabric-i18n-mod/src/main/resources/lang-map.yml`
- Server ResourcePack language JSON files
- Fabric direct language JSON files
- Fabric embedded ResourcePack language JSON files
- `pack.mcmeta` language metadata
- Runtime language registry in `config.yml`
- Shared ResourcePack ZIP artifact
- SHA1 integrity file
- GitHub Actions i18n gates
- JUnit ResourcePack integrity tests

## Final verified values

- Allowed TreasureRun languages: 22
- ResourcePack language JSON files: 23
- Minecraft standard translation keys per JSON: 8039
- Exact key-set consistency: verified against `en_us.json`
- ResourcePack SHA1: `4b12c7bed6d3f491601b623058079dda46a88cb5`

## Historical Germanic visible UI samples

### Old English / `ang_gb`

- `language.name`: Ænglisc
- `language.region`: Englaland
- `menu.singleplayer`: Āna plegian
- `menu.multiplayer`: Mid ōðrum plegian
- `menu.options`: Stillingas...
- `options.language`: Sprǣc...
- `selectWorld.title`: Woruld geceosan

### Old Norse / `non_is`

- `language.name`: Dǫnsk tunga
- `language.region`: Norðrlǫnd
- `menu.singleplayer`: Leika einn
- `menu.multiplayer`: Leika með ǫðrum
- `menu.options`: Stillingar...
- `options.language`: Tunga...
- `selectWorld.title`: Velja heim

## Local verification commands passed

- `python3 scripts/check_suspicious_i18n.py`
- `python3 scripts/check_minecraft_packet_coverage.py`
- `./gradlew test --tests 'plugin.i18n.*' --console=plain`
- `./gradlew clean build --console=plain`

## Technical positioning

This is best described as:

> A platform-boundary i18n expansion proving that a Minecraft Spigot plugin can coordinate server-side YAML translations, ResourcePack language assets, Fabric runtime language sync, SHA1 artifact integrity, and CI regression gates while honestly documenting the boundary of what a server plugin can and cannot control.

## Honest limitation

This does not mean a Spigot plugin can control every Minecraft UI text path.
TreasureRun intentionally documents the platform boundary: server-observable and ResourcePack-resolved text paths can be expanded, while fully client-local screens and pre-login/client-owned UI remain outside pure Spigot control.

