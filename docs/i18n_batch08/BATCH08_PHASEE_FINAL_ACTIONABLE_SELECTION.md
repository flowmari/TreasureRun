# Batch08 Phase E — final actionable selection

## Goal
- Reduce GameMenu to the literals that truly require i18n replacement
- Exclude symbols / newline fragments / classification tokens / admin logs

## Result
- final actionable: 5
- dropped token/non-i18n: 7
- dropped admin/debug: 0

## Next
- Add keys only for final actionable literals
- Write ja/en naturally
- Seed the other 17 locales
- Replace Java literals
- Run gates / build / runtime
