# QuoteFavorite live path visible surface

## Scope
- QuoteFavoriteBookClickListener / QuoteFavoriteCommand / QuoteFavoriteShortcutListener の live path だけを見る
- player-facing の source-of-truth を確認する
- internal/debug/SQL はここでは触らない

## Goal
- 1 class / 1 visible surface 単位で、CI を壊さずに次の i18n batch を切る
