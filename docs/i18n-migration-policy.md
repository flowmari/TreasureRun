# i18n Migration Policy

## Goal
Migrate TreasureRun from hardcoded strings to a maintainable YAML-based 19-language i18n system.

## Migration Strategy
We use a staged migration approach:

1. Player-facing runtime messages first
   - command responses
   - GUI labels
   - gameplay messages
   - titles / lore / prompts

2. Internal/debug strings later
   - debug logs
   - SQL fragments
   - internal diagnostics
   - legacy generated keys

## Validation
The following checks are enforced:
- YAML syntax check
- required key check
- duplicate key check

Suspect key detection is currently treated as a reporting check, not a blocking check, because legacy migration is still in progress.

## Why this approach
This keeps runtime behavior safe while allowing fast incremental migration across many classes.
