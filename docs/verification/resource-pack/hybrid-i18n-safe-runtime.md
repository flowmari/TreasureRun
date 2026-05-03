# Hybrid Minecraft i18n Safe Runtime Verification

Generated: 20260504_030140

This document records safe runtime verification after restoring a valid `config.yml`.

## Runtime evidence

- Evidence log: `docs/verification/runtime-evidence/hybrid-i18n-safe-runtime-20260504_030140.txt`
- Observed translate keys: `docs/verification/runtime-evidence/hybrid-i18n-safe-translate-keys-20260504_030140.txt`

## Verification checklist

| Check | Result |
|---|---:|
| Resource Pack sent | 1 |
| Resource Pack accepted | 1 |
| Resource Pack successfully loaded | 1 |
| PacketI18n translate audit | 1 |
| PacketI18n replacement evidence | 1 |

## Correct portfolio wording

TreasureRun implements a hybrid i18n architecture that maximizes practical localization coverage by combining plugin YAML translations, ProtocolLib packet-level audit/rewrite, and a server-delivered Minecraft 1.20.1 vanilla-key resource pack.

## Honest limitation

This should not be described as absolute complete control of every Minecraft engine/client string.

Some pre-login, authentication, settings, and purely client-local UI strings cannot be guaranteed from a server plugin.
