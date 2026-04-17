# Batch 01 scope

## Goal
Smallest safe player-facing i18n batch.

## Touched
- CraftSpecialEmeraldCommand.java
- command.craftSpecialEmerald.playersOnly
- command.stageCleanup.playersOnly

## Verify only
- CheckTreasureEmeraldCommand
- TreasureRunStartCommand

## Why this batch is strong
- One direct string removed from Java
- Two command surfaces normalized in locale files
- Low blast radius
- Easy to review
- Easy to runtime-check
