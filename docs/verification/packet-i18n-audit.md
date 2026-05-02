# Packet-Level i18n Audit Verification

This document records the runtime verification of TreasureRun's ProtocolLib-based packet-level i18n audit layer.

## Purpose

TreasureRun already localizes many player-visible messages through Bukkit events.

The packet-level i18n layer adds a lower-level audit foundation for server-to-client chat/system packets that may not be fully exposed through Bukkit events.

This layer is intentionally audit-first.

It allows the project to observe actual Minecraft JSON chat/system components in a running Spigot environment before expanding translation replacement logic.

## Architecture

TreasureRun uses a two-layer localization strategy.

1. Bukkit event layer
   - join / quit / death / advancement-style messages
   - handled through TreasureRun's existing YAML-backed i18n pipeline
   - respects each player's selected language

2. ProtocolLib packet layer
   - audits server-to-client packet JSON
   - currently targets conservative chat/system packets:
     - `SYSTEM_CHAT`
     - `CHAT`
     - `DISGUISED_CHAT`
   - avoids unsupported title / bossbar / tab-complete packet types to prevent ProtocolLib runtime warnings

## Runtime Environment

Verified in a Docker-based local environment:

- Minecraft / Spigot: 1.20.1
- Server container: `minecraft_spigot`
- ProtocolLib installed as a server plugin
- TreasureRun deployed as a Spigot plugin JAR

## Manual Test Flow

```text
1. Start the Docker Spigot server.
2. Join My Spigot Server as flowmari.
3. Run /lang ojp.
4. Send a normal chat message.
5. Disconnect.
6. Join the server again.
7. Check Docker server logs.
```

## Verification Command

```bash
docker logs --tail 1500 minecraft_spigot | grep -Ei "PacketI18n|translate=|yaml=minecraft.packet|json="
```

## Verified Runtime Evidence

The packet listener registered successfully:

```text
[TreasureRun] [PacketI18n] ProtocolLib packet listener registered: SYSTEM_CHAT / CHAT / DISGUISED_CHAT
```

The runtime audit captured actual `SYSTEM_CHAT` JSON for the player:

```text
[TreasureRun] [PacketI18n][AUDIT] player=flowmari packet=SYSTEM_CHAT json={"extra":[{"text":"flowmari、TreasureRun の世に入り給ひぬ。"}],"text":""}
```

The audit also captured the language-change confirmation message:

```text
[TreasureRun] [PacketI18n][AUDIT] player=flowmari packet=SYSTEM_CHAT json={"extra":[{"bold":false,"italic":false,"underlined":false,"strikethrough":false,"obfuscated":false,"color":"green","text":"✅ 言の葉を改めたり: {古文} ({ojp})"}],"text":""}
```

## Result

The packet-level audit layer was verified successfully.

Confirmed:

- ProtocolLib loads successfully.
- TreasureRun registers a packet listener.
- The listener receives server-to-client system chat packets.
- Runtime JSON can be captured per player.
- The implementation avoids unsupported packet types that previously caused ProtocolLib warnings.
- The system is suitable as an extensible audit foundation for future packet-level localization.

## Operational Policy

In normal operation, packet audit logging should remain disabled to avoid noisy logs.

Recommended default:

```yaml
packetMessages:
  enabled: true
  audit: false
  auditAllJson: false
  debug: false
  replaceTranslatedComponents: false
```

For verification or future expansion, temporarily enable:

```yaml
packetMessages:
  enabled: true
  audit: true
  auditAllJson: true
  debug: true
  replaceTranslatedComponents: false
```

## Portfolio Point

This work demonstrates:

- ProtocolLib integration
- packet-level runtime observation
- conservative packet-type selection
- evidence-based i18n expansion
- YAML-backed multilingual architecture
- Docker-based runtime verification
- operational safety by disabling verbose audit logs by default
