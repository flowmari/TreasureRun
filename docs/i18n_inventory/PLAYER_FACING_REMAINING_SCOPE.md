# Player-facing remaining inventory

## Purpose
- identify live-path player-facing classes that are not yet fully finished
- separate player-facing / admin-facing / debug-internal / SQL-technical
- avoid wasting a batch on classes that are already key-driven

## Technical evidence narrative
- safe fallback strategy
- phased rollout
- local gates + runtime verification
- locale polish separated from structural i18n
