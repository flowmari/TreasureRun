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

## Additional Runtime Evidence

Recorded at: 2026-05-03 08:35:40

Evidence files:

- 
- 

Observed translate keys:

~~~text
No Minecraft translate keys were observed in this audit run.
~~~

Recent PacketI18n audit log excerpt:

~~~text
[22:45:53] [Server thread/INFO]: [TreasureRun] [PacketI18n] skipped unsupported packet type: TAB_COMPLETE
[22:45:53] [Server thread/WARN]: [TreasureRun] [PacketI18n] no supported chat packet types detected. Packet-level i18n skipped.
[22:48:56] [Server thread/INFO]: [TreasureRun] [PacketI18n] detected packet type: SYSTEM_CHAT
[22:48:56] [Server thread/INFO]: [TreasureRun] [PacketI18n] detected packet type: CHAT
[22:48:56] [Server thread/INFO]: [TreasureRun] [PacketI18n] detected packet type: DISGUISED_CHAT
[22:48:56] [Server thread/INFO]: [TreasureRun] [PacketI18n] ProtocolLib packet listener registered: SYSTEM_CHAT / CHAT / DISGUISED_CHAT
[22:52:22] [Server thread/INFO]: [TreasureRun] [PacketI18n][AUDIT] player=flowmari packet=SYSTEM_CHAT json={"extra":[{"text":"flowmari、TreasureRun の世に入り給ひぬ。"}],"text":""}
[22:52:38] [Server thread/INFO]: [TreasureRun] [PacketI18n][AUDIT] player=flowmari packet=SYSTEM_CHAT json={"extra":[{"bold":false,"italic":false,"underlined":false,"strikethrough":false,"obfuscated":false,"color":"green","text":"✅ 言の葉を改めたり: {古文} ({ojp})"}],"text":""}
[22:53:54] [Server thread/INFO]: [TreasureRun] [PacketI18n][DEBUG] player=flowmari packet=CHAT json=NONE
[22:59:07] [Server thread/INFO]: [TreasureRun] [PacketI18n][AUDIT] player=flowmari packet=SYSTEM_CHAT json={"extra":[{"text":"flowmari、TreasureRun の世に入り給ひぬ。"}],"text":""}
[23:13:03] [Server thread/INFO]: [TreasureRun] [PacketI18n][AUDIT] player=flowmari packet=SYSTEM_CHAT json={"extra":[{"text":"flowmariは命の緒、ここに絶えにけり。"}],"text":""}
[23:21:37] [Server thread/INFO]: [TreasureRun] [PacketI18n] detected packet type: SYSTEM_CHAT
[23:21:37] [Server thread/INFO]: [TreasureRun] [PacketI18n] detected packet type: CHAT
[23:21:37] [Server thread/INFO]: [TreasureRun] [PacketI18n] detected packet type: DISGUISED_CHAT
[23:21:37] [Server thread/INFO]: [TreasureRun] [PacketI18n] ProtocolLib packet listener registered: SYSTEM_CHAT / CHAT / DISGUISED_CHAT
[23:25:41] [Server thread/INFO]: [TreasureRun] [PacketI18n][AUDIT] player=flowmari packet=SYSTEM_CHAT json={"extra":[{"text":"flowmari、TreasureRun の世に入り給ひぬ。"}],"text":""}
[23:26:03] [Server thread/INFO]: [TreasureRun] [PacketI18n][AUDIT] player=flowmari packet=SYSTEM_CHAT json={"extra":[{"bold":false,"italic":false,"underlined":false,"strikethrough":false,"obfuscated":false,"color":"green","text":"✅ 言の葉を改めたり: {古文} ({ojp})"}],"text":""}
[23:26:14] [Server thread/INFO]: [TreasureRun] [PacketI18n][DEBUG] player=flowmari packet=CHAT json=NONE
[23:28:46] [Server thread/INFO]: [TreasureRun] [PacketI18n][AUDIT] player=flowmari packet=SYSTEM_CHAT json={"extra":[{"text":"flowmari、TreasureRun の世に入り給ひぬ。"}],"text":""}
[23:29:01] [Server thread/INFO]: [TreasureRun] [PacketI18n][AUDIT] player=flowmari packet=SYSTEM_CHAT json={"extra":[{"bold":false,"italic":false,"underlined":false,"strikethrough":false,"obfuscated":false,"color":"green","text":"✅ 言の葉を改めたり: {古文} ({ojp})"}],"text":""}
~~~

Interpretation:

- The ProtocolLib packet listener registered successfully.
-  are the current conservative packet targets.
- Runtime  JSON was captured for player .
- No Minecraft  keys were observed in this run, so the current evidence supports packet JSON auditing, not expanded vanilla translate-key replacement yet.
- Packet audit logging was turned off after verification to keep normal server logs quiet.
