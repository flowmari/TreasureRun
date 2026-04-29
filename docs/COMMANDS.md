# TreasureRun Command Reference

TreasureRun is a Minecraft Spigot 1.20.1 mini-game plugin.

This document externalizes command behavior from the README so the README can stay concise.

Commands are declared mainly in:

```text
src/main/resources/plugin.yml
```

---

## Player Commands

| Command | Usage | Permission | Default | Purpose |
|---|---|---|---|---|
| `/gamestart` | `/gamestart [easy\|normal\|hard]` | `treasure.start` | true | Starts TreasureRun. If the player has no saved language, the language GUI is shown first. |
| `/gameStart` | `/gameStart [easy\|normal\|hard]` | `treasure.start` | true | Alias of `/gamestart`. |
| `/gameMenu` | `/gameMenu` | `treasure.menu` | true | Opens the TreasureRun rule/menu book. |
| `/gameMenu gui` | `/gameMenu gui` | `treasure.menu` | true | Opens the language GUI before showing the game menu. |
| `/gameRank` | `/gameRank [weekly\|monthly\|all]` | `treasure.rank` | true | Shows ranking data. Defaults to weekly ranking. |
| `/lang` | `/lang` | `treasure.lang` | true | Opens the language selection GUI. |
| `/lang <code>` | `/lang ja`, `/lang en`, `/lang de` | `treasure.lang` | true | Sets the player's language. |
| `/lang list` | `/lang list` | `treasure.lang` | true | Lists allowed language codes. |
| `/lang current` | `/lang current` | `treasure.lang` | true | Shows the current player language. |
| `/lang gui` | `/lang gui` | `treasure.lang` | true | Opens the language selection GUI. |
| `/lang reset` | `/lang reset` | `treasure.lang` | true | Resets the player's saved language. |
| `/craftspecialemerald` | `/craftspecialemerald` | `treasure.craftspecialemerald` | true | Crafts a Treasure Emerald using 3 diamonds. |
| `/checktreasureemerald` | `/checktreasureemerald` | `treasure.checktreasureemerald` | true | Checks whether the item in the player's main hand is a Treasure Emerald. |
| `/quoteFavorite` | `/quoteFavorite <latest\|list\|remove\|reread\|book>` | `treasure.quoteFavorite` | true | Manages favorite quotes/proverbs. |
| `/qfav` | `/qfav <latest\|list\|remove\|reread\|book>` | `treasure.quoteFavorite` | true | Alias of `/quoteFavorite`. |
| `/quotefav` | `/quotefav <latest\|list\|remove\|reread\|book>` | `treasure.quoteFavorite` | true | Alias of `/quoteFavorite`. |
| `/favquote` | `/favquote <latest\|list\|remove\|reread\|book>` | `treasure.quoteFavorite` | true | Alias of `/quoteFavorite`. |

---

## Operator / Admin Commands

| Command | Usage | Permission | Default | Purpose |
|---|---|---|---|---|
| `/game` | `/game start`, `/game end` | `treasure.game` | op | Game control command declared in `plugin.yml`. |
| `/gameEnd` | `/gameEnd` | `treasure.game` | op | Ends the current TreasureRun game and performs cleanup. |
| `/gameReload` | `/gameReload` | `treasure.reload` | op | Reloads TreasureRun configuration. |
| `/treasureReload` | `/treasureReload` | `treasure.reload` | op | Reloads config, language files, GUI state, quote module, and runtime managers. |
| `/clearStageBlocks` | `/clearStageBlocks` | `treasure.clearstage` | op | Clears generated difficulty/stage blocks. |
| `/treasureExportLang` | `/treasureExportLang [overwrite]` | `treasure.reload` | op | Exports `messages.translation.*` from `config.yml` into `languages/*.yml`. |
| `/rank` | `/rank <1\|2\|3\|demo>` | `treasure.debug.rank` | op | Debug/demo command for rank reward effects. Requires `rankDebug.enabled=true`. |
| `/givespecialemerald` | `/givespecialemerald` | `treasure.givespecialemerald` | op | Gives a special emerald. |

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
ja, en, de, fr, it, sv, es, fi, nl, ru, ko, zh_tw, pt, hi, la, lzh, is, sa, asl_gloss
```

---

## Design Notes

- Player-visible text is externalized into `languages/*.yml`.
- Player language is persisted per player.
- Reload behavior is designed for server operation.
- Debug/demo commands are protected by operator permission and config flags.
- TreasureRun is a Spigot plugin, not a REST API service. Therefore Swagger/OpenAPI is intentionally not used.
