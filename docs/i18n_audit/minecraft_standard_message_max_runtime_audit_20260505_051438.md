# Minecraft Standard-Message i18n Maximum Runtime Audit

Date: 20260505_051438

## Purpose

This audit expands TreasureRun's Minecraft standard-message i18n verification beyond a small fixed probe set.

It tests the practical maximum server-side coverage available to a Spigot plugin using:

- Mojang official Minecraft 1.20.1 language assets
- server-delivered ResourcePack
- ProtocolLib PacketI18n audit / replacement
- Bukkit / server-side command surfaces
- runtime missing-key detection

## Technical Boundary

This audit targets Minecraft standard messages visible after joining the server.

It does not claim full control over client-only areas such as:

- Minecraft Launcher UI
- Microsoft authentication UI
- pre-join client screens
- client-internal UI strings that are never sent by the server
- messages not exposed through server packets or server events

## Runtime Result

| Metric | Count |
|---|---:|
| ResourcePack sent | 0 |
| ResourcePack accepted | 0 |
| ResourcePack successfully loaded | 0 |
| ResourcePack failed | 0 |
| ResourcePack failed download | 0 |
| PacketI18n translate audit | 1021 |
| PacketI18n replace | 1021 |
| Translation missing | 0 |
| I18n Missing key warning | 0 |
| Selected Minecraft translate keys probed | 1021 |
| Unique audited minecraft.packet keys | 1021 |
| Unique missing minecraft.packet keys | 0 |

## Probe Scope

The audit extracted candidate translation keys from:

`resourcepacks/treasurerun-i18n-pack/assets/minecraft/lang/en_us.json`

Primary categories:

- multiplayer.*
- death.*
- chat.*
- commands.*
- argument.*
- disconnect.*
- advancements.*
- resourcePack.*
- connect.*

It also executed safe real command surfaces through the server runtime, including help, list, seed, time query, weather query, difficulty, gamemode, effect, xp, teleport, give, and clear.

## Evidence Files

- Runtime log: `tmp_i18n_fix/max_standard_message_audit_20260505_051438/runtime_max_audit_20260505_051438.log`
- Selected translate keys: `tmp_i18n_fix/max_standard_message_audit_20260505_051438/minecraft_translate_keys_selected_20260505_051438.txt`
- Audited yaml keys: `tmp_i18n_fix/max_standard_message_audit_20260505_051438/audited_keys_20260505_051438.txt`
- Missing keys: `tmp_i18n_fix/max_standard_message_audit_20260505_051438/missing_keys_20260505_051438.txt`

## Conclusion

TreasureRun uses a hybrid i18n architecture targeting the practical maximum server-visible Minecraft standard-message coverage available from a Spigot plugin:

```text
Mojang official lang assets
+ server-side ResourcePack delivery
+ ProtocolLib packet audit/replacement
+ Bukkit/server command surface verification
+ runtime missing-key guard
```

This is intentionally described as practical maximum server-visible coverage, not complete control of every Minecraft client or launcher string.
