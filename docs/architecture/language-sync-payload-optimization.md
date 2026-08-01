> **V4 local-only ResourcePack contract:** TreasureRun does not store or publish Minecraft language payload JSON files or reconstructed full ResourcePack ZIP files. Client packs for the 17 reviewed official locale mappings are generated only from the operator's own installed Minecraft 1.20.1 assets. The six custom/missing mappings remain available to plugin-side i18n, while their client-pack generation remains held.

# Language Sync Payload Optimization

TreasureRun does not send all Minecraft language JSON data to the client at runtime.

## Problem

The Minecraft 1.20.1 standard-message i18n layer spans thousands of translation keys across many locale assets. TreasureRun does not bundle or transmit that full payload as part of its V4 distribution contract.

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
The heavy language data remains in the player's Minecraft installation and, where needed, in a locally generated client pack.

## Runtime behavior

### Fabric Mod client

If the player has the TreasureRun Fabric i18n mod installed:

1. The Spigot plugin sends only the selected language code.
2. The Fabric Mod receives the code on `treasurerun:lang`.
3. The Fabric Mod switches Minecraft's client locale when the selected locale is available from the player's installation or a locally generated client pack.
4. Client-side Minecraft standard text resolves through the player's installed locale assets or the locally generated client pack.

### No Fabric Mod client

If the player does not have the Fabric Mod installed:

1. The plugin message is ignored by the vanilla client.
2. Automatic public ResourcePack delivery remains disabled; an operator may generate an eligible local client pack separately.
3. ProtocolLib / Spigot-side rewriting still covers server-reachable messages.
4. Fully client-only UI remains limited by Minecraft client constraints.

## Engineering value

This design separates heavy static assets from lightweight runtime state:

- Heavy assets: resolved from the player's installation or a locally generated client pack
- Runtime packet: only selected language code
- Non-Fabric path: ProtocolLib where applicable, plus an optional locally generated client pack

This keeps the architecture scalable while respecting Minecraft's platform boundaries.
