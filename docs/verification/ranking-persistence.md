# Ranking Persistence Verification

This document records the runtime verification of TreasureRun's MySQL-backed ranking persistence.

## Feature

TreasureRun stores gameplay ranking data in MySQL-backed weekly, monthly, and all-time ranking tables.

The ranking persistence stores:

- player UUID
- player name
- score
- win count
- best clear time
- selected language code
- created / updated timestamps

## Tables

- `seasons`
- `season_scores`
- `alltime_scores`

## Design

Weekly and monthly ranking data are stored in `season_scores`.

Each season-based score row is linked to a season row through:

```text
season_scores.season_id -> seasons.id
```

All-time ranking data is stored separately in `alltime_scores`.

This separation allows TreasureRun to support weekly, monthly, and all-time ranking views while keeping season-based data structurally consistent.

## Runtime Verification

The feature was verified in a Docker-based Spigot + MySQL environment.

Manual test flow:

```text
/lang ojp
/gameStart easy
Open a treasure chest or progress until score persistence is triggered.
```

Verification query:

```sql
SELECT
  s.season_type,
  s.season_key,
  ss.uuid,
  ss.name,
  ss.score,
  ss.wins,
  ss.best_time_ms,
  ss.lang_code,
  ss.created_at
FROM season_scores ss
JOIN seasons s ON s.id = ss.season_id
WHERE s.season_type IN ('WEEKLY','MONTHLY')

UNION ALL

SELECT
  'ALLTIME' AS season_type,
  'ALLTIME' AS season_key,
  a.uuid,
  a.name,
  a.score,
  a.wins,
  a.best_time_ms,
  a.lang_code,
  a.created_at
FROM alltime_scores a

ORDER BY created_at DESC
LIMIT 50;
```

Verified result:

```text
MONTHLY:
flowmari / score=2600 / wins=1 / best_time_ms=72376 / lang_code=ojp

WEEKLY:
flowmari / score=2600 / wins=1 / best_time_ms=72376 / lang_code=ojp

ALLTIME:
flowmari / score=2600 / wins=1 / best_time_ms=72376 / lang_code=ojp
```

## Portfolio Point

This feature demonstrates:

- Java repository-layer persistence
- MySQL schema design
- weekly / monthly / all-time ranking separation
- foreign key integrity
- unique-key based upsert design
- selected-language tracking for multilingual gameplay data
- Docker-based runtime verification
