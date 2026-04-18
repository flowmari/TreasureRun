# Batch 06 — QuoteFavoriteBookClickListener

## Scope
- Normalize QuoteFavoriteBookClickListener to key-only helper calls
- Remove repeated self-key fallback literals at call sites
- Keep locale polish separate
- Keep debug/internal strings out of scope

## Why this batch is strong
- high-visibility player-facing surface
- class-unit reviewable diff
- low blast radius
- safe to gate / build / runtime-check
