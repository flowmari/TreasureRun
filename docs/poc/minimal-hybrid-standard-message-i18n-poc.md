# Minimal Hybrid Minecraft Standard-Message i18n PoC

## Purpose

This PoC demonstrates a practical approach for localizing Minecraft standard messages that are difficult to control from a normal Spigot plugin alone.

The PoC combines:

1. ProtocolLib packet audit / replace
2. Server-side resource pack delivery
3. Mojang official Minecraft 1.20.1 language assets
4. Custom language-key overrides
5. Runtime evidence collection

## Why ProtocolLib alone is not enough

ProtocolLib can observe and rewrite many server-to-client packets after the player joins the server.

However, some text is rendered by the Minecraft client itself or appears before the server can safely intervene.

Therefore, ProtocolLib alone cannot guarantee full coverage of every Minecraft client / engine string.

## Why the resource pack layer is added

A server-side resource pack can override Minecraft client language keys.

This makes it possible to cover client-rendered text that packet rewriting alone cannot naturally handle, as long as the relevant language key is resolved through the client language system after the resource pack is loaded.

## Practical maximum strategy

TreasureRun uses a hybrid design:

- PacketI18n handles server-to-client translatable messages.
- The resource pack handles client language-key overrides.
- TreasureRun custom YAML handles player-selected plugin language behavior.
- Runtime logs verify whether the system actually worked.

## Verified result

Runtime verification confirmed:

- ResourcePack sent
- ResourcePack accepted
- ResourcePack loaded
- PacketI18n translate audit
- PacketI18n replace
- Translation missing: 0
- I18n Missing key warning: 0

## Scope statement

This PoC targets Minecraft standard messages visible after server join.

It does not claim absolute control over:

- pre-login messages
- authentication messages
- client settings screens
- purely client-local UI
- text rendered before server-side packet/resource-pack control is available

## Portfolio wording

Japanese:

TreasureRun では、ProtocolLib による packet audit / replace と、server-side resource pack による client lang key override を組み合わせ、通常の Spigot plugin だけでは書き換えにくい Minecraft 標準メッセージを、サーバー参加後の実用上最大範囲で多言語化する PoC を実装した。

English:

Implemented a minimal hybrid i18n PoC for Minecraft standard messages by combining ProtocolLib packet audit/rewrite with server-side resource-pack language-key overrides. The system targets the practical maximum coverage of Minecraft standard messages visible after server join and includes runtime verification for ResourcePack delivery and packet replacement behavior.
