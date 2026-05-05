# Language Sync Payload Optimization

TreasureRun does not send all Minecraft language JSON data to the client at runtime.

## Problem

The Minecraft 1.20.1 standard-message i18n layer contains:

- 8039 standard translation keys
- 20+ locale JSON assets
- Fabric Mod language assets
- ResourcePack language assets

Sending all language data to every player at runtime would be wasteful and would make the network path unnecessarily heavy.

## Optimization

TreasureRun sends only the player's selected language code over the plugin messaging channel:

```text
treasurerun:lang
```

Example payloads:

```text
ja
en
de
zh_tw
ojp
```

The payload is intentionally tiny.  
The heavy language data is already shipped through the Fabric Mod and ResourcePack layers.

## Runtime behavior

### Fabric Mod client

If the player has the TreasureRun Fabric i18n mod installed:

1. The Spigot plugin sends only the selected language code.
2. The Fabric Mod receives the code on `treasurerun:lang`.
3. The Fabric Mod switches Minecraft's client locale to the matching bundled locale.
4. Client-side Minecraft standard text can resolve through the local 8039-key language assets.

### No Fabric Mod client

If the player does not have the Fabric Mod installed:

1. The plugin message is ignored by the vanilla client.
2. TreasureRun still delivers the multilingual ResourcePack.
3. ProtocolLib / Spigot-side rewriting still covers server-reachable messages.
4. Fully client-only UI remains limited by Minecraft client constraints.

## Engineering value

This design separates heavy static assets from lightweight runtime state:

- Heavy assets: shipped once by Fabric Mod / ResourcePack
- Runtime packet: only selected language code
- Fallback: ResourcePack + ProtocolLib path for non-Fabric clients

This keeps the architecture scalable while respecting Minecraft's platform boundaries.
