# ANG/NON Outcome Proverb Localization Evidence

## Summary

This document records the plugin-side outcome proverb localization work for TreasureRun's historical Germanic locales.

Target files:

- `src/main/resources/languages/ang.yml`
- `src/main/resources/languages/non.yml`

Target sections:

- `outcome.success.easy.pool`
- `outcome.success.normal.pool`
- `outcome.success.hard.pool`
- `outcome.timeup.easy.pool`
- `outcome.timeup.normal.pool`
- `outcome.timeup.hard.pool`

## What was changed

The remaining English outcome proverb pools were replaced for:

- `ang.yml`: Old English-style poetic localization
- `non.yml`: Old Norse-style poetic localization

Each locale contains 158 outcome proverb entries across success and time-up outcomes.

## Honest boundary

These translations are not presented as native-speaker-certified historical language translations.

Old English and Old Norse are historical languages, so the safest OSS-facing description is:

> reviewable Old English / Old Norse-style poetic localization

This keeps the implementation honest while still showing that the project supports experimental historical-language localization as part of its i18n system.

## Why this matters

This work extends TreasureRun's i18n coverage beyond Minecraft standard UI keys.

Previous runtime evidence verified:

- ResourcePack-resolved Minecraft UI keys
- Fabric runtime language switching
- `ang -> ang_gb`
- `non -> non_is`
- 8039-key ResourcePack consistency
- SHA1 integrity
- runtime screenshots / client logs / CI

This step improves the plugin-side language files, especially visible result and proverb messages shown during gameplay.

## Verification

The following checks were run locally:

- no English-identical entries remain in the patched outcome proverb pools
- outcome proverb pool counts are preserved
- required i18n keys are present
- focused i18n tests pass
- full Gradle build passes

## Technical-review wording

Recommended wording:

> I extended TreasureRun's historical Germanic locale support from ResourcePack-resolved Minecraft UI keys into plugin-side outcome proverb messages. Because Old English and Old Norse are historical languages, I treated the work as reviewable poetic localization rather than claiming native-level accuracy. The implementation preserves YAML key compatibility, passes i18n checks, and is verified through CI.

