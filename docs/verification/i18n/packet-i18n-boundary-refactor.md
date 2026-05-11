# PacketI18n Boundary Refactor Verification

This document records how TreasureRun verifies the PacketI18n boundary refactor.

## Purpose

Minecraft standard UI text cannot be fully controlled by a Spigot plugin alone.

TreasureRun addresses this platform boundary by separating the i18n system into multiple layers:

- Spigot / Bukkit plugin layer
- ProtocolLib packet boundary layer
- ResourcePack standard translation-key layer
- Fabric runtime language synchronization layer
- pure-Java packet JSON localizer

The goal is to isolate Minecraft-dependent runtime code at the outer boundary and move packet JSON localization into a small, independently testable Java component.

## Design Summary

### Boundary adapter

`LocalizedPacketMessageProtocolListener` is the Minecraft/ProtocolLib boundary adapter.

It is responsible for:

- receiving outgoing packet events
- reading `WrappedChatComponent` and packet string components
- resolving the target player's selected language
- adapting TreasureRun i18n placeholders
- writing localized JSON components back to the packet

This class is intentionally allowed to depend on Spigot, Bukkit, ProtocolLib, Player, and other plugin-runtime APIs.

### Pure-Java localizer

`PacketI18nJsonLocalizer` contains the packet-localization logic.

It is responsible for:

- parsing Minecraft JSON chat/title/actionbar components
- finding `translate` keys
- converting Minecraft translation keys into TreasureRun packet i18n keys
- extracting `with` arguments as placeholders
- returning a JSON text component when replacement is safe

This class has no dependency on Bukkit, Spigot, ProtocolLib, Player, or Minecraft server runtime objects.

## Verified Boundary

The pure-Java localizer imports only Gson and Java standard-library classes:

```java
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
```

The ProtocolLib listener delegates JSON localization to the pure-Java localizer:

```java
return PacketI18nJsonLocalizer.localizeJson(
    json,
    lang,
    (targetLang, yamlKey, args) -> {
        // plugin I18n adapter
    }
);
```

## Test Coverage

The refactor is covered by focused tests:

- `PacketI18nJsonLocalizerTest`
- `LocalizedPacketMessageProtocolListenerTest`

`PacketI18nJsonLocalizerTest` verifies packet JSON localization without requiring a Minecraft server runtime.

`LocalizedPacketMessageProtocolListenerTest` verifies that the ProtocolLib-facing class remains a boundary adapter and delegates localization to the pure-Java localizer.

## ResourcePack Verification

The generated ResourcePack is verified for:

- ZIP existence
- SHA1 consistency
- language file inventory
- selected language key counts
- representative standard Minecraft UI keys

Verified examples:

| Language file | Key count |
|---|---:|
| `en_us.json` | 8039 |
| `ja_jp.json` | 8039 |
| `ojp_jp.json` | 8039 |
| `asl_us.json` | 8039 |
| `sa_in.json` | 8039 |
| `la_la.json` | 8039 |
| `lzh_hant.json` | 8039 |

Representative OJP keys:

```text
menu.online = オンライン（遠隔の通ひ）...
menu.options = 仕立て（設え定む）...
addServer.resourcePack = サーバーのリソースパック
addServer.resourcePack.prompt = 尋ぬ
```

## Existing Related Documentation

This verification connects with the existing i18n architecture documents:

- `docs/architecture/hybrid-minecraft-standard-message-i18n.md`
- `docs/architecture/fabric-runtime-language-hotswap.md`
- `docs/architecture/language-sync-payload-optimization.md`
- `docs/I18N_RESOURCEPACK_ALIASING_FALLBACK.md`
- `docs/dev/packet-i18n-audit-commands.md`
- `docs/verification/i18n/non-mod-resourcepack-fallback.md`
- `docs/verification/i18n/custom-language-registration.md`

## Engineering Value

This refactor demonstrates that TreasureRun is not only replacing visible text.

It separates a Minecraft platform-boundary problem into:

- a ProtocolLib boundary adapter
- ResourcePack-backed standard translation assets
- Fabric runtime language synchronization
- a pure-Java packet JSON localizer
- focused unit tests

This makes the i18n layer easier to test, audit, maintain, and explain.

In short:

> TreasureRun separates Minecraft-dependent boundary code from pure packet-localization logic, turning a platform-constrained i18n problem into a testable multi-layer architecture.
