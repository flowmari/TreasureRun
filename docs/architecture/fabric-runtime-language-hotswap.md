> **V4 local-only ResourcePack contract:** TreasureRun does not store or publish Minecraft language payload JSON files or reconstructed full ResourcePack ZIP files. Client packs for the 17 reviewed official locale mappings are generated only from the operator's own installed Minecraft 1.20.1 assets. The six custom/missing mappings remain available to plugin-side i18n, while their client-pack generation remains held.

# Fabric Runtime Language Hot-Swap

TreasureRun's Fabric i18n mod applies Minecraft standard-message language changes at runtime.

## Goal

The goal is to update Minecraft standard text after joining a server without requiring the player to restart Minecraft.

TreasureRun does not patch the Minecraft engine binary itself.  
Instead, it uses Fabric client-side integration and Minecraft's resource reload path.

## Runtime flow

1. The Spigot plugin stores the player's selected TreasureRun language.
2. The server sends only a tiny selected-language payload over `treasurerun:lang`.
3. The Fabric Mod maps the TreasureRun language to a Minecraft locale code.
4. The Fabric Mod updates:
   - `client.options.language`
   - Minecraft's `LanguageManager`
5. The Fabric Mod calls `client.reloadResources()`.
6. Minecraft reloads language resources available from the player's installation and any locally generated client pack.

## Why not send every supported language at runtime?

The Minecraft standard-message layer spans thousands of translation keys across many locale assets.

Sending all of that data over the network would be wasteful.

TreasureRun separates the design:

- Heavy data: resolved from the player's installation or a locally generated client pack
- Runtime payload: selected language code only
- Hot-swap: client-side resource reload without Minecraft restart

## Why not directly mutate TranslationStorage?

Directly replacing the internal `TranslationStorage` map is fragile because it depends on private Minecraft internals and mappings.

TreasureRun prefers the safer route:

- update the selected language
- use Minecraft's resource reload pipeline
- let Minecraft rebuild translation storage from installed locale resources and any locally generated client pack

This keeps the design closer to Minecraft's own resource lifecycle while still working around platform boundaries.
