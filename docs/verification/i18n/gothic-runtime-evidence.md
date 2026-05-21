# Gothic Locale Runtime Evidence Checklist

## Summary

TreasureRun now includes an experimental Gothic locale as part of the same platform-boundary i18n pipeline used by the other supported locales.

## Locale Mapping

- TreasureRun language code: `got`
- Minecraft locale code: `got_de`

## Verified Static Layers

- Spigot plugin YAML: `src/main/resources/languages/got.yml`
- Language mapping: `src/main/resources/lang-map.yml`
- Fabric language mapping: `fabric-i18n-mod/src/main/resources/lang-map.yml`
- ResourcePack JSON: `resourcepacks/treasurerun-i18n-pack/assets/minecraft/lang/got_de.json`
- Fabric bundled JSON: `fabric-i18n-mod/src/main/resources/assets/minecraft/lang/got_de.json`
- Generated main ResourcePack ZIP: `resourcepacks/generated/treasurerun-i18n-pack.zip`
- SHA1 integrity: `.sha1` file and `config.yml` values synchronized
- Regression tests: ResourcePack artifact integrity and language-code mapping tests

## Runtime Evidence To Capture Next

After deploying the latest build and joining with the Fabric i18n client, capture evidence for:

- `lang sync received rawBytes=3 treasureRun='got' minecraft='got_de'`
- `Client language applied: got_de`
- `options.txt` contains `lang:got_de`
- ResourcePack loaded / accepted
- ESC menu ResourcePack-resolved text displays Gothic values for keys such as:
  - `menu.game`
  - `menu.options`
  - `menu.disconnect`

## Honest Boundary

This is experimental Gothic-style localization. The engineering value is not a claim of native-level Gothic translation quality.

The main engineering claim is that TreasureRun can add a new historical/experimental locale through a multi-layer, testable platform-boundary i18n pipeline:

1. Plugin-side YAML messages
2. Internal-to-Minecraft locale mapping
3. ResourcePack language assets
4. Fabric runtime language sync
5. ZIP/SHA1 integrity checks
6. Automated tests and GitHub Actions validation
7. Docker/runtime verification

## Technical-Review Wording

I added an experimental Gothic locale to TreasureRun through the same platform-boundary i18n pipeline: Spigot YAML, language mapping, ResourcePack assets, Fabric runtime sync, ZIP/SHA1 integrity checks, automated tests, and GitHub Actions validation.
