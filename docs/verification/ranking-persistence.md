# Ranking Persistence Verification

This document records the runtime verification of TreasureRun's MySQL-backed ranking persistence.

## Feature

TreasureRun stores gameplay ranking data in MySQL-backed weekly and all-time ranking tables.

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

Weekly ranking data is stored in `season_scores`.

Each weekly score row is linked to a season row through:

```text
season_scores.season_id -> seasons.id
```

All-time ranking data is stored separately in `alltime_scores`.

This separation allows TreasureRun to support both weekly and all-time ranking views while keeping season-based data structurally consistent.

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
SELECT id, season_id, uuid, name, score, wins, best_time_ms, lang_code, created_at, updated_at
FROM season_scores
ORDER BY id DESC
LIMIT 5;

SELECT id, uuid, name, score, wins, best_time_ms, lang_code, created_at, updated_at
FROM alltime_scores
ORDER BY id DESC
LIMIT 5;
```

Verified result:

```text
season_scores:
flowmari / score=2600 / wins=1 / best_time_ms=72376 / lang_code=ojp

alltime_scores:
flowmari / score=2600 / wins=1 / best_time_ms=72376 / lang_code=ojp
```

## Portfolio Point

This feature demonstrates:

- Java repository-layer persistence
- MySQL schema design
- weekly and all-time ranking separation
- foreign key integrity
- unique-key based upsert design
- selected-language tracking for multilingual gameplay data
- Docker-based runtime verification
