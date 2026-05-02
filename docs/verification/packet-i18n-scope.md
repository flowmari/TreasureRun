# Packet-Level i18n Scope

This document defines the realistic scope of TreasureRun's ProtocolLib-based packet-level i18n layer.

## Goal

TreasureRun aims to support multilingual gameplay messages through a layered i18n architecture.

The project already localizes many player-visible messages through Bukkit events and TreasureRun's YAML-backed i18n pipeline.

The ProtocolLib packet layer exists to observe and eventually localize lower-level server-to-client chat/system packets that are not fully exposed through Bukkit events.

## Important Scope Boundary

Not every Minecraft standard message can be localized by a Spigot plugin.

Minecraft messages can appear from different layers:

1. Bukkit event layer
   - join / quit / death / advancement-style messages
   - often exposed safely by Bukkit events
   - suitable for TreasureRun's normal i18n pipeline

2. Server-to-client packet layer
   - chat/system packet JSON sent from the server to the player
   - observable with ProtocolLib when the message is actually sent as a packet
   - suitable for audit-first expansion

3. Client-side layer
   - GUI labels, menus, buttons, client authentication messages, resource-pack UI, connection screens, and many vanilla client texts
   - rendered by the Minecraft client itself
   - generally outside the control of a Bukkit/Spigot plugin

Therefore, TreasureRun should describe this feature as:

> Packet-level audit and localization foundation for observable server-to-client Minecraft chat/system messages.

It should not claim full Minecraft client/protocol localization.

## Current Packet Targets

The implementation currently keeps the packet target list conservative for Spigot 1.20.1 + ProtocolLib:

- `SYSTEM_CHAT`
- `CHAT`
- `DISGUISED_CHAT`

Unsupported packet types such as title / bossbar / tab-complete were intentionally removed after runtime testing, because some ProtocolLib builds expose constants that are not registered for the active Minecraft server version.

This avoids runtime warnings like unknown packet registration errors.

## Expansion Strategy

TreasureRun should expand packet-level localization in this order:

1. Enable audit temporarily.
2. Trigger Minecraft events in a real server.
3. Capture actual JSON components from runtime logs.
4. Extract `translate` keys when present.
5. Add only observed keys to `languages/*.yml`.
6. Verify each new key in-game.
7. Keep audit disabled by default for normal operation.

This makes the localization scope evidence-based rather than guess-based.

## Why This Is Portfolio-Relevant

This demonstrates practical engineering judgment:

- understanding boundaries between Bukkit, ProtocolLib, server packets, and client-side rendering
- avoiding unsupported packet registrations
- using runtime evidence before expanding behavior
- keeping production logs quiet by default
- designing for safe extension rather than overclaiming
