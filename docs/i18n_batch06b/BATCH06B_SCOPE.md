# Batch 06b — QuoteFavoriteBookClickListener

## Scope
- Finish the remaining key-only normalization in QuoteFavoriteBookClickListener
- Remove the last self-key fallback call sites:
  - favorites.empty.noFav
  - favorites.title
- Keep locale polish separate
- Keep source-of-truth cleanup separate

## Why this batch is strong
- finishes one class cleanly
- tiny diff
- easy to review
- safe to gate / build / runtime-check
