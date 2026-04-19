# Live-path i18n milestone

## Strongest current claim
19-locale i18n migration with CI-backed rollout and safe fallback strategy.

## What is already strong
- fallback-first design
- staged rollout by class / visible surface
- local gates
- build verification
- runtime verification
- branch-based incremental history
- suspect classification separated from live-path replacement

## Current live-path status
The current live-path player-facing surfaces are largely organized around keys and source-of-truth locale data.

Covered or heavily advanced:
- OutcomeMessageService batch
- GameMenu batch (documentation + actionable literal narrowing)
- Lang UI visible surfaces
- QuoteFavorite live path
- Favorites visible surfaces
- TreasureItemFactory / GameStart / StageCleanup / CraftSpecial / CheckTreasure

## Remaining work is mainly
- legacy / dead suspect
- debug / internal literals
- SQL / technical literals
- intentionally non-localized tokens
- locale polish as a separate phase

## What should NOT be claimed yet
- “all 19 locales are fully native-polished”
- “all literals in the whole repository are gone”

## What SHOULD be claimed
- safe live-path migration
- fallback design
- staged rollout
- CI/local-gate discipline
- runtime verification
- explicit separation of locale polish from structural i18n migration

## Next engineering move after this status pass
Pick only one of these:
1. legacy/dead key cleanup
2. locale polish on a clearly scoped visible surface
3. next live-path class only if classification proves it is still active
