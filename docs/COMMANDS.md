# TreasureRun Command Reference

TreasureRun is a Minecraft Spigot 1.20.1 mini-game plugin.

This document externalizes command behavior from the README so the README can stay concise.

Commands are declared mainly in:

```text
src/main/resources/plugin.yml
```

---

## Server-hosted Round Commands

`/treasurerun` manages one server-hosted session with a minimum of 2 and a maximum of 8
participants. Players can join or leave only while the session is waiting; the participant roster
is fixed when an administrator starts the round.

| Command | Who can run it | Permission | Purpose |
|---|---|---|---|
| `/treasurerun create` | Administrator / operator | `treasure.admin` | Creates the one server-hosted session and opens it for joins. |
| `/treasurerun join` | Player | none | Joins the waiting session, up to 8 participants. |
| `/treasurerun leave` | Player | none | Leaves the waiting session; an empty waiting session resets to idle. |
| `/treasurerun start` | Administrator / operator | `treasure.admin` | Requires at least 2 participants, locks the roster, prepares the shared round, and starts the 10-second countdown. |
| `/treasurerun stop` | Administrator / operator | `treasure.admin` | Resets a waiting session or requests the shared cleanup path for an active server-hosted round. |
| `/treasurerun status` | Player or console | none | Reports the session state, participant count, minimum, maximum, and whether the waiting roster can start. |

The existing `/gamestart` and `/gameEnd` commands remain available as the established
single-run compatibility route. For the 2–8 player flow, use
[`docs/playtest/SERVER_HOSTED_QUICKSTART.md`](playtest/SERVER_HOSTED_QUICKSTART.md).

### Server-hosted follow-up behavior

- No external or DeathRun map is required for the current one-arena flow. TreasureRun uses its
  dedicated `treasurerun_arena` world.
- Post-round Play Again is enabled by default and becomes available only after authoritative cleanup
  completes. It enters a new WAITING session rather than reopening the completed round.
- The optional Hub action is disabled by default. When configured, TreasureRun presents the
  operator-supplied command as a player-side clickable action; TreasureRun does not own the Hub
  plugin's success/failure lifecycle.
- The administrator update notifier is disabled by default. When enabled, it performs asynchronous,
  cached checks against TreasureRun's GitHub Releases feed; the displayed message and destination
  link remain operator-configurable. It does not scrape SpigotMC.
- `/treasurerunadmin forcestart` is a countdown bypass, not a safety bypass.

---

## Player Commands

| Command | Usage | Permission | Default | Purpose |
|---|---|---|---|---|
| `/gameMenu` | `/gameMenu` | `treasure.menu` | true | Opens the TreasureRun rule/menu book. |
| `/gameMenu gui` | `/gameMenu gui` | `treasure.menu` | true | Opens the language GUI before showing the game menu. |
| `/gameRank` | `/gameRank [weekly\|monthly\|all]` | `treasure.rank` | true | Shows ranking data. Defaults to weekly ranking. |
| `/lang` | `/lang` | `treasure.lang` | true | Opens the language selection GUI. |
| `/lang <code>` | `/lang ja`, `/lang en`, `/lang de` | `treasure.lang` | true | Sets the player's language. |
| `/lang list` | `/lang list` | `treasure.lang` | true | Lists allowed language codes. |
| `/lang current` | `/lang current` | `treasure.lang` | true | Shows the current player language. |
| `/lang gui` | `/lang gui` | `treasure.lang` | true | Opens the language selection GUI. |
| `/lang reset` | `/lang reset` | `treasure.lang` | true | Resets the player's saved language. |
| `/craftspecialemerald` | `/craftspecialemerald` | `treasure.craftspecialemerald` | true | Crafts a Special Emerald using 3 diamonds. |
| `/checktreasureemerald` | `/checktreasureemerald` | `treasure.checktreasureemerald` | true | Checks whether the item in the player's main hand is a TreasureRun Special Emerald. |
| `/quoteFavorite` | `/quoteFavorite <latest\|list\|remove\|reread\|book>` | `treasure.quoteFavorite` | true | Manages favorite quotes/proverbs. |
| `/qfav` | `/qfav <latest\|list\|remove\|reread\|book>` | `treasure.quoteFavorite` | true | Alias of `/quoteFavorite`. |
| `/quotefav` | `/quotefav <latest\|list\|remove\|reread\|book>` | `treasure.quoteFavorite` | true | Alias of `/quoteFavorite`. |
| `/favquote` | `/favquote <latest\|list\|remove\|reread\|book>` | `treasure.quoteFavorite` | true | Alias of `/quoteFavorite`. |

---

## Operator / Admin Commands

| Command | Usage | Permission | Default | Purpose |
|---|---|---|---|---|
| `/gamestart` | `/gamestart [easy\|normal\|hard]` | `treasure.admin` | op | Starts TreasureRun. If the player has no saved language, the language GUI is shown first. |
| `/treasurerunadmin forcestart` | `/treasurerunadmin forcestart` | `treasure.admin` | op | Uses the normal server-hosted start contract but skips only the scheduled 10-second wait before RUNNING. Minimum-player, roster, durable-return, preparation, and cleanup requirements are preserved. |
| `/gameEnd` | `/gameEnd` | `treasure.admin` | op | Ends the current TreasureRun game and performs cleanup. |
| `/gameReload` | `/gameReload` | `treasure.reload` | op | Alias of `/treasureReload`. |
| `/treasureReload` | `/treasureReload` | `treasure.reload` | op | Reloads config, language files, GUI state, quote module, and runtime managers. |
| `/clearStageBlocks` | `/clearStageBlocks` | `treasure.clearstage` | op | Clears generated difficulty/stage blocks. |
| `/treasureExportLang` | `/treasureExportLang [overwrite]` | `treasure.reload` | op | Exports `messages.translation.*` from `config.yml` into `languages/*.yml`. |
| `/rank` | `/rank <1\|2\|3\|demo>` | `treasure.debug.rank` | op | Debug/demo command for rank reward effects. Requires `rankDebug.enabled=true`. |

---

## Permission Compatibility

- `/gamestart` and `/gameEnd` use the operator-only `treasure.admin` permission.
- `treasure.start` and `treasure.game` remain declared as legacy operator-only nodes; neither is assigned to `/gamestart` or `/gameEnd` in `plugin.yml`.

---

## Quote Favorite Subcommands

| Command | Purpose |
|---|---|
| `/quoteFavorite help` | Shows help. |
| `/quoteFavorite latest` | Saves the latest quote/proverb log as a favorite. |
| `/quoteFavorite list` | Lists saved favorites. |
| `/quoteFavorite remove <id>` | Removes a favorite by ID. |
| `/quoteFavorite reread [chat\|title\|book]` | Replays a saved favorite. |
| `/quoteFavorite book [toc\|success\|timeup\|other\|full]` | Opens the favorite quote book view. |

---

## Language Codes

The language GUI and `/lang` command are driven by `config.yml` and `src/main/resources/languages/*.yml`.

```text
ja, en, de, it, sv, es, la, is, fi, nl, fr, ru, ko, zh_tw, sa, pt, hi, lzh, ojp, asl_gloss, ang, non, got
```

---

## Design Notes

- Player-visible text is externalized into `languages/*.yml`.
- Player language is persisted per player.
- Reload behavior is designed for server operation.
- Debug/demo commands are protected by operator permission and config flags.
- TreasureRun's core gameplay is exposed through Spigot commands. The optional ranking-api module is documented separately and includes OpenAPI contract verification for its read-only HTTP boundary.
