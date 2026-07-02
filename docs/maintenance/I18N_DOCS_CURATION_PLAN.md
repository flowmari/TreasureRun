# i18n docs curation plan

This note records a small documentation cleanup plan before asking for broader alpha feedback.

The goal is not to hide project history or remove useful evidence. The goal is to make the repository easier to read for a first-time reader or tester by separating current project documentation from research-log style working notes.

## Current audit summary

The latest read-only audit found:

- `docs/i18n_native_review/`: 207 tracked files
- `docs/i18n_batch*`: 48 tracked files
- `docs/archive/`: already exists
- inactive legacy-code folders are no longer tracked after PR #98
- the architecture tradeoff summary is now tracked under `docs/architecture/` after PR #99

The public-facing reference to these research-log areas is currently limited to maintenance documentation.

## Proposed documentation boundary

Keep the main reader path focused on:

- gameplay overview
- first-time setup
- player quickstart
- architecture notes
- verification and release notes
- current contribution paths

Treat the following areas as research-log or historical working material:

- `docs/i18n_native_review/`
- `docs/i18n_batch01/`
- `docs/i18n_batch02/`
- `docs/i18n_batch03/`
- `docs/i18n_batch04/`
- `docs/i18n_batch05/`
- `docs/i18n_batch06/`
- `docs/i18n_batch06b/`
- `docs/i18n_batch07/`
- `docs/i18n_batch08/`
- `docs/i18n_batch09/`

## Proposed next PR

Create one separate docs-only PR that moves the research-log style folders under an archive path, for example:

```text
docs/archive/i18n-research-logs/
```

That future PR should preserve the files, avoid rewriting their contents, and update only the minimum maintenance references needed after the move.

## Non-goals

- No Java implementation changes.
- No i18n behavior changes.
- No language file changes.
- No ResourcePack changes.
- No Fabric changes.
- No ranking API changes.
- No test coverage changes.
- No unrelated issue-specific changes.
- No external tester outreach in this PR.
