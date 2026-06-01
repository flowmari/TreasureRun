# PR #28 Cleanup: README milestone wording and remaining scratch directory

## Purpose

This PR cleans up one remaining scratch directory and updates README project-status wording after the Fresh Clone QuickStart evidence was completed.

## Changes

- Removed `tmp_i18n_fix/` if present.
- Updated README milestone wording so it no longer says Fresh Clone QuickStart measurement is still a future task.
- Recorded the presence of `attic/` or `_disabled/` for future review, without deleting them blindly.

## Boundaries

This PR intentionally does not:

- change gameplay behavior;
- repair DB migrations;
- restart Docker;
- change ResourcePack publication;
- mutate GitHub Releases;
- claim Paper compatibility;
- publish to SpigotMC or Paper communities.

## Notes

`attic/` and `_disabled/`, if present, should be reviewed separately before deletion because they may contain intentionally retained historical or disabled material.

## Root directory review

```
ABSENT: tmp_i18n_fix
PRESENT: attic
PRESENT: _disabled
```
