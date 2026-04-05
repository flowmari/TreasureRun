from pathlib import Path
import sys
import yaml

lang_dir = Path("src/main/resources/languages")

REQUIRED_KEYS = [
    "gui.language.title",
    "gui.language.subtitle",
    "gui.language.selected",
    "gui.language.saved",

    "game.difficulty.easy",
    "game.difficulty.normal",
    "game.difficulty.hard",

    "favorites.title",
    "favorites.cover.head",
    "favorites.cover.hint",
    "favorites.empty.noFav",
    "favorites.empty.saveLatest",
    "favorites.toc.head",
    "favorites.toc.howto",
    "favorites.toc.manage",
    "favorites.toc.reread",

    "ui.menu.book.openFailed",
    "ui.menu.book.hotbarGiven",
    "ui.menu.book.hotbarHint",
    "ui.menu.book.latestHint",

    "ui.menu.page.contents.title",
    "ui.menu.page.contents.howToPlay",
    "ui.menu.page.contents.scoreRoute",
    "ui.menu.page.contents.savedOn",
    "ui.menu.page.contents.language",
    "ui.menu.page.contents.yourQuotes",
    "ui.menu.page.contents.difficulty",

    "ui.menu.page.note.bookFormat",
    "ui.menu.page.hint.reopenMenu",

    "ui.quote.title",
    "ui.quote.intro.tabsTitle",
    "ui.quote.intro.legendTitle",
    "ui.quote.intro.storedInDb",
    "ui.quote.intro.langLine",
    "ui.quote.headerRecent",
    "ui.quote.legend.success",
    "ui.quote.legend.timeUp",
    "ui.quote.legend.favorites",
    "ui.quote.noLogs",
    "ui.quote.noQuotesInTab",
    "ui.quote.noFavorites",
    "ui.quote.tip.rightClickSave",

    "ui.favorites.title",
    "ui.favorites.headerLatest",

    "quotes.outcome.success",
    "quotes.outcome.timeup",
    "quotes.outcome.other",
]

def flatten(d, prefix=""):
    out = {}
    if isinstance(d, dict):
        for k, v in d.items():
            key = f"{prefix}.{k}" if prefix else str(k)
            out[key] = v
            out.update(flatten(v, key))
    return out

bad = False

for p in sorted(lang_dir.glob("*.yml")):
    try:
        data = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
    except Exception as e:
        bad = True
        print(f"== {p.name} ==")
        print(f"YAML parse error: {e}")
        print()
        continue

    flat = flatten(data)
    missing = [k for k in REQUIRED_KEYS if k not in flat]

    print(f"== {p.name} ==")
    if missing:
        bad = True
        print("missing required keys:")
        for k in missing:
            print("  -", k)
    else:
        print("OK")
    print()

if bad:
    sys.exit(1)

print("OK: no missing required i18n keys found")
