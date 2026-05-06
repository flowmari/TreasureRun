# Runtime Language Hot-Swap Demo Guide

This document describes how to verify TreasureRun's runtime Minecraft language hot-swap behavior.

## What the demo should show

The demo should show this flow:

1. Start Minecraft with the TreasureRun Fabric client mod installed.
2. Join the local Spigot server.
3. Run a language change command such as `/lang ja`, `/lang en`, or `/lang de`.
4. Confirm that the server sends only the selected language code.
5. Confirm that the Fabric client applies the language through Minecraft's normal runtime resource lifecycle.
6. Confirm that Minecraft text changes without restarting the game.

## Architecture being demonstrated

TreasureRun does not directly mutate Minecraft's internal `TranslationStorage` map.

Instead, the Fabric client mod uses Minecraft's own resource reload path:

- update selected language
- call Minecraft's `LanguageManager`
- call `client.reloadResources()`
- reload bundled Minecraft standard-message language assets

This demonstrates platform-boundary engineering:

- Spigot cannot fully control Minecraft client-side standard UI text alone.
- ResourcePack covers server-delivered asset behavior.
- Fabric Mod covers client-side standard-message areas unreachable from Spigot alone.
- ProtocolLib / packet audit covers server-observable translation-key behavior.
- GitHub Actions validates i18n coverage, mapping completeness, and build health.

## Recommended GIF length

Keep the GIF short:

- 15 to 30 seconds
- show the command
- show the UI change
- show that Minecraft was not restarted

## Suggested filename

Save the final GIF as:

`~/Downloads/treasurerun-lang-hotswap-demo.gif`

Then run the evidence script again to copy it into:

`docs/verification/runtime-demo/treasurerun-lang-hotswap-demo.gif`
