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
6. Minecraft reloads bundled language assets from the Fabric Mod / ResourcePack layer.

## Why not send all 20 languages at runtime?

The Minecraft standard-message layer contains:

- 8039 translation keys
- 20+ locale JSON assets

Sending all of that data over the network would be wasteful.

TreasureRun separates the design:

- Heavy data: shipped statically in Fabric Mod / ResourcePack assets
- Runtime payload: selected language code only
- Hot-swap: client-side resource reload without Minecraft restart

## Why not directly mutate TranslationStorage?

Directly replacing the internal `TranslationStorage` map is fragile because it depends on private Minecraft internals and mappings.

TreasureRun prefers the safer route:

- update the selected language
- use Minecraft's resource reload pipeline
- let Minecraft rebuild translation storage from bundled assets

This keeps the design closer to Minecraft's own resource lifecycle while still working around platform boundaries.
