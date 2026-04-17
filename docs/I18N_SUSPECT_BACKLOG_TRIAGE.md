# I18N Suspect Backlog Triage

## Goal
Make the i18n migration look recruiter-strong by keeping:
- player-facing first
- P1 / P2 / P3 separated
- branch-by-branch merge
- CI-safe rollout

## Evidence
- docs/i18n_triage/i18n_local_gates.log
- docs/i18n_triage/referenced_key_inventory.txt
- docs/i18n_triage/player_facing_candidates.txt
- docs/i18n_triage/player_facing_suspect_excerpt.txt
- docs/i18n_triage/admin_or_debug_suspect_excerpt.txt
- docs/i18n_triage/hotspots.txt

## P1: player-facing first
Target only user-visible gameplay / command / GUI / item text first.

Primary classes from current referenced-key inventory:
- CheckTreasureEmeraldCommand
- CraftSpecialEmeraldCommand
- LangCommand
- LanguageSelectGui
- QuoteFavoriteBookClickListener
- StageCleanupCommand
- TreasureItemFactory
- TreasureRunStartCommand
- quote/QuoteFavoriteCommand
- quote/QuoteFavoriteShortcutListener

## P2: admin-facing / operational
Keep separate from player-facing work.
Examples:
- config / repository / operational notices
- setup / reload / startup / migration notices

## P3: debug / SQL / internal / probable dead code
Do not mix these into player-facing i18n branches.
Examples:
- RETURN / RETURN(LATER)
- InventoryClickEvent fired
- SQL literals
- MySQL connection / repository failure details
- reflection / wolf-control debug lines

## Merge policy
1. Only merge a branch when local gates pass
2. Runtime-impacting player-facing changes go first
3. Admin/debug cleanup never mixed into player-facing batch
4. Keep each branch single-purpose and reviewable

## Fixed execution order
A. Remove runtime-impacting player-facing direct strings  
B. Classify suspect_keys into player-facing / admin / debug / SQL / intentional non-localized  
C. Fix only player-facing i18n targets  
D. Major locale polish  
E. Remaining locale polish  

## Next action
- Create the first focused player-facing batch branch from this triage
- Keep GameMenu / Quote / command surfaces separate from debug cleanup
- Treat suspect_keys as report-only, not as a release blocker by itself
