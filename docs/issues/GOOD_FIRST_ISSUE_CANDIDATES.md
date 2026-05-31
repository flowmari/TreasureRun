# Good First Issue Candidates

This document lists beginner-safe external contribution candidates.

These are candidates only. They should be converted into actual GitHub issues after the maintainer confirms scope and labels.

## 1. Translation wording cleanup

Suggested labels:

- `good first issue`
- `translation`
- `i18n`
- `help wanted`

Scope:

Review a small set of visible language strings and improve wording without changing Java code.

Safe files:

- `src/main/resources/languages/*.yml`

Rules:

- Keep YAML syntax valid.
- Do not rename keys.
- Do not change placeholders such as `{score}`, `{rank}`, `{time}`, `%s`, or `%d`.
- Keep command names such as `/gamestart`, `/lang`, `/rank`, and `/quoteFavorite` unchanged.

Suggested first target:

- mixed-language command help text;
- visible Japanese remnants in non-Japanese language files;
- English fallback wording in experimental languages.

## 2. Documentation clarity

Suggested labels:

- `good first issue`
- `documentation`
- `help wanted`

Scope:

Improve contributor-facing docs without changing runtime behavior.

Safe files:

- `README.md`
- `CONTRIBUTING.md`
- `docs/external/ALPHA_TESTER_SETUP_GUIDE.md`
- `docs/external/FRESH_CLONE_QUICKSTART_EVIDENCE.md`

Suggested tasks:

- clarify setup wording;
- add screenshots;
- improve troubleshooting notes;
- explain expected ResourcePack prompts.

## 3. Gameplay configuration review

Suggested labels:

- `good first issue`
- `gameplay`
- `configuration`
- `help wanted`

Scope:

Identify gameplay constants that are already configurable and document them clearly.

Safe files for first pass:

- `src/main/resources/config.yml`
- docs only

Do not change Java logic until a maintainer confirms the exact constant and expected behavior.

Candidate config areas:

- difficulty time limits;
- chest counts;
- spawn radius;
- score values;
- rank ticker display options;
- reward tuning.

## 4. Alpha tester feedback issue

Suggested labels:

- `alpha`
- `feedback`
- `help wanted`

Scope:

Ask external testers to run the documented local setup and report whether the first-run path works.

Requested evidence:

- OS;
- Java version;
- Docker version;
- Minecraft version;
- command transcript;
- screenshot or logs;
- whether `/lang` and `/gamestart normal` worked.


## Acceptance criteria template

Each real GitHub issue created from this document should include a short checklist like this:

- The change is small enough for a first-time contributor.
- The touched files are listed explicitly.
- The expected behavior is described in plain English.
- The command or check needed to verify the change is included.
- The issue clearly states what is out of scope.

## Suggested real issues to open first

### Issue 1: Review mixed-language translation helper strings

Acceptance criteria:

- Identify a small set of mixed-language or machine-generated helper strings.
- Limit the change to one or two language files.
- Keep YAML syntax valid.
- Run the relevant i18n validation command.
- Do not attempt a full translation-quality rewrite.

### Issue 2: Improve the alpha tester setup guide with screenshots

Acceptance criteria:

- Add or request screenshots for the ResourcePack prompt and first successful join.
- Keep the guide short and tester-focused.
- Do not change runtime behavior.
- Do not claim Paper compatibility.

### Issue 3: Review one gameplay configuration value

Acceptance criteria:

- Pick one configurable gameplay value, such as time limit, chest count, or score reward.
- Identify where it is configured and where it is read.
- Propose a small, safe adjustment or documentation clarification.
- Do not refactor the gameplay system.
