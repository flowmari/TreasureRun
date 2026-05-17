# Core i18n and Ranking Persistence Tests

## Purpose

TreasureRun is not only a Minecraft mini-game plugin. It also contains several core systems that need to remain stable as the project grows:

- YAML-based i18n loading and fallback
- Minecraft packet translation key resolution
- weekly season lookup / creation
- ranking score persistence
- transaction behavior across weekly and all-time ranking tables

These tests make those core behaviors reviewable and repeatable.

The goal is to avoid relying only on manual runtime checks. Instead, important behavior is verified by JUnit / Mockito tests that can run in CI.

```text
Core gameplay support logic
→ i18n fallback and key resolution
→ ranking season lookup / creation
→ DB persistence transaction behavior
→ JUnit / Mockito tests
→ CI quality gate
```

## What these tests verify

### `I18nLanguagesYamlStoreFallbackTest`

This test verifies TreasureRun's YAML-based i18n loading and fallback behavior.

It checks:

- language files can be loaded from `languages/*.yml`
- missing requested-language keys can fall back to English
- missing English keys can fall back to Japanese
- missing keys use `default.unknown`
- Minecraft translation keys that are both a parent section and a leaf value can resolve through `_value`

This matters because Minecraft translation keys can have nested shapes such as:

```text
minecraft.packet.death.attack.anvil
minecraft.packet.death.attack.anvil.player
```

In YAML, that means one key path may need to behave both as a section and as a resolved value.  
The `_value` convention keeps that structure testable.

### `SeasonRepositoryTest`

This test verifies weekly season lookup / creation behavior without requiring a real MySQL server.

It checks:

- an existing weekly season ID is returned when found
- a new weekly season is inserted when no current season exists
- generated IDs from the database insert path are handled

This protects the season boundary used by weekly rankings.

### `SeasonScoreRepositoryTest`

This test verifies ranking persistence behavior with mocked JDBC objects.

It checks:

- weekly score rows are updated
- all-time score rows are updated
- both writes are handled in one transaction
- `commit()` is called when both writes succeed
- `rollback()` is called when the second write fails
- blank language codes fall back safely

This matters because ranking persistence is stateful.  
A partial write could corrupt the relationship between weekly and all-time score data, so transaction behavior is part of the correctness of the system.

## Why Mockito is used here

The repository tests do not need a real MySQL server to verify control flow.

Mockito is used to check how the repository code interacts with JDBC:

- which SQL path is used
- which parameters are bound
- whether `commit()` is called
- whether `rollback()` is called on failure
- whether `autoCommit` is restored

This keeps the tests fast and focused while still covering important persistence behavior.

## Claim-to-test traceability

| Core claim | Verification |
| --- | --- |
| YAML language files can be loaded and resolved | `I18nLanguagesYamlStoreFallbackTest` |
| missing keys follow the intended fallback chain | `I18nLanguagesYamlStoreFallbackTest` |
| nested Minecraft translation keys can use `_value` | `I18nLanguagesYamlStoreFallbackTest` |
| weekly seasons can be looked up | `SeasonRepositoryTest` |
| missing weekly seasons can be inserted | `SeasonRepositoryTest` |
| weekly and all-time ranking writes happen together | `SeasonScoreRepositoryTest` |
| ranking persistence rolls back on partial failure | `SeasonScoreRepositoryTest` |

## Engineering value

These tests move TreasureRun closer to a maintainable OSS project because they verify behavior at the level where bugs would be costly:

```text
If i18n fallback breaks, tests fail.
If nested Minecraft translation keys stop resolving, tests fail.
If weekly season lookup breaks, tests fail.
If ranking persistence stops committing correctly, tests fail.
If a failed second DB write does not roll back, tests fail.
```

That means the project is not only demonstrating a Minecraft i18n workaround.  
It is also building a testable foundation around the systems that support gameplay, localization, and ranking persistence.

## Related files

- [`I18nLanguagesYamlStoreFallbackTest`](../../../src/test/java/plugin/I18nLanguagesYamlStoreFallbackTest.java)
- [`SeasonRepositoryTest`](../../../src/test/java/plugin/rank/SeasonRepositoryTest.java)
- [`SeasonScoreRepositoryTest`](../../../src/test/java/plugin/rank/SeasonScoreRepositoryTest.java)
- [`I18n`](../../../src/main/java/plugin/I18n.java)
- [`LanguagesYamlStore`](../../../src/main/java/plugin/LanguagesYamlStore.java)
- [`SeasonRepository`](../../../src/main/java/plugin/rank/SeasonRepository.java)
- [`SeasonScoreRepository`](../../../src/main/java/plugin/rank/SeasonScoreRepository.java)
