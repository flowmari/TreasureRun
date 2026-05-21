# Gothic Locale Expansion

## Summary

TreasureRun adds an experimental Gothic locale as part of its platform-boundary i18n architecture.

- TreasureRun language code: `got`
- Minecraft locale code: `got_de`
- Server YAML: `src/main/resources/languages/got.yml`
- ResourcePack JSON: `resourcepacks/treasurerun-i18n-pack/assets/minecraft/lang/got_de.json`
- Fabric JSON: `fabric-i18n-mod/src/main/resources/assets/minecraft/lang/got_de.json`
- Main ResourcePack ZIP: `resourcepacks/generated/treasurerun-i18n-pack.zip`
- SHA1: `a39664f5fcdac5dacda372c7a8cb9e4069b6602c`

## Design note

This is intentionally added through the same multi-layer path as the existing languages:

1. `lang-map.yml` as the single source of truth
2. Bukkit/Spigot YAML messages
3. ResourcePack Minecraft lang JSON
4. Fabric runtime language sync JSON
5. ZIP regeneration and SHA1 integrity update
6. CI/local validation gates

## Honesty boundary

The first Gothic version is an experimental wiring proof.

It demonstrates that TreasureRun can scale a historical/custom language through the same i18n architecture without Java code changes. Full philological review and natural-language polish are separate translation-quality phases.
