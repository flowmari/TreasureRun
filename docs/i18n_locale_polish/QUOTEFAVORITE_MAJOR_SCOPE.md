# Locale Polish — QuoteFavorite (major locales)

## Scope
- Target only player-facing QuoteFavorite/Favorites locale text
- Major locales first: de / fr / es / it / pt
- Keep debug/internal/SQL strings out of scope
- Keep structural i18n and locale polish separated

## Why this is technically significant
- Clear phase separation
- Safe rollout by surface
- Easy diff review
- Easy CI/runtime validation
