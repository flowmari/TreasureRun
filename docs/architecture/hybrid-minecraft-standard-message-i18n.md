# Hybrid Minecraft Standard-Message i18n Architecture

## Overview

TreasureRun implements a hybrid i18n architecture that combines Bukkit/Adventure, ProtocolLib, and a server-side resource pack.

The goal is to target the practical maximum range of Minecraft standard messages visible after a player joins the server.

## 日本語概要

TreasureRun では、Bukkit/Adventure・ProtocolLib・server-side resource pack を統合したハイブリッド i18n 基盤を実装した。

PacketI18n で server-to-client の標準メッセージを監査・置換しつつ、resource pack 側では client lang key override を担わせることで、参加後に表示される Minecraft 標準文を多言語化する現実的な最大到達点を狙う設計に整理した。

ResourcePack の送信、accept、load も実行ログで検証し、PacketI18n についても translate audit / replace / missing warning を数値で追跡できる状態にした。

## What was built

The system integrates:

1. Mojang official Minecraft 1.20.1 language assets
2. Official Minecraft 1.20.1 client-jar `en_us.json` base for English-derived pack files
3. Server-side resource pack delivery
4. TreasureRun custom standard-message overrides
5. ProtocolLib-based PacketI18n audit / replace processing
6. Runtime verification for resource-pack delivery and packet rewrite behavior

## Runtime verification

The final runtime verification confirmed:

- ResourcePack sent: PASS
- ResourcePack accepted: PASS
- ResourcePack loaded: PASS
- PacketI18n translate audit: PASS
- PacketI18n replace: PASS
- Translation missing: 0
- I18n Missing key warning: 0

## Technical scope

This architecture targets Minecraft standard messages visible after server join.

It intentionally does not claim absolute control over every Minecraft engine or client string.

The following areas are not guaranteed to be controllable from a Spigot plugin:

- pre-login messages
- authentication messages
- client settings screens
- purely client-local UI
- text rendered before the server can send packets or a resource pack
- messages controlled entirely by the Minecraft client

## Recruiter-safe English summary

Built a hybrid i18n architecture for TreasureRun using Bukkit/Adventure, ProtocolLib, Mojang official Minecraft 1.20.1 language assets, and server-side resource pack delivery.

The system audits and rewrites server-to-client Minecraft standard messages through PacketI18n while using the resource pack layer for client language-key overrides.

It targets the practical maximum coverage of Minecraft standard messages visible after server join, with runtime verification for ResourcePack sent / accepted / loaded, PacketI18n audit / replace, and zero Translation missing / Missing key warnings.
