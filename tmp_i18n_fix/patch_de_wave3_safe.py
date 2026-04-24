from pathlib import Path
import shutil
import time

p = Path("src/main/resources/languages/de.yml")
src = p.read_text(encoding="utf-8")
bak = p.with_name(p.name + ".wave3_" + time.strftime("%Y%m%d_%H%M%S"))
shutil.copy2(p, bak)
print(f"BACKUP: {bak}")

pairs = {
    "guiCancelledGameStart: Die Sprachauswahl wurde abgebrochen. Zum erneuten Versuch":
    "guiCancelledGameStart: Die Sprachauswahl wurde abgebrochen. Versuche es erneut mit /lang.",

    "guiCancelledGameMenu: Die Sprachauswahl wurde abgebrochen. Zum erneuten Öffnen":
    "guiCancelledGameMenu: Die Sprachauswahl wurde abgebrochen. Öffne sie erneut mit /lang.",

    "removeUsage: '&cVerwendung: /quoteFavorite remove <id>'":
    "removeUsage: '&cVerwendung: /quoteFavorite remove <ID>'",

    "rereadNoQuotes: '&eEs gibt noch keine Zitate zum erneuten Lesen.'":
    "rereadNoQuotes: '&eEs gibt noch keine Zitate zum Wiederlesen.'",

    "title: '&bQuoteFavorite-Befehle'":
    "title: '&bQuoteFavorite-Befehle'",

    "badge: TreasureRun-Favoriten":
    "badge: TreasureRun-Favoriten",

    "hint: 'Tipp: Nutze /quoteFavorite latest oder /quoteFavorite list.'":
    "hint: 'Tipp: Nutze /quoteFavorite latest oder /quoteFavorite list.'",

    "saveHow1: 'Nutze /quoteFavorite latest.'":
    "saveHow1: 'Tipp: Nutze /quoteFavorite latest.'",

    "noQuotes: Du hast noch keine Zitate gespeichert.":
    "noQuotes: Du hast noch keine Zitate gespeichert.",

    "fallbackTitle: TreasureRun-Regeln":
    "fallbackTitle: TreasureRun-Regeln",

    "fallbackDisplayName: TreasureRun-Regelbuch":
    "fallbackDisplayName: TreasureRun-Regelbuch",

    "message: '&6📌 TreasureRun-Menü geöffnet. Sieh bitte ins Regelbuch.'":
    "message: '&6📌 TreasureRun-Menü geöffnet. Sieh bitte ins Regelbuch.'",

    "yourQuotes: Deine gespeicherten Zitate":
    "yourQuotes: Deine gespeicherten Zitate",

    "headerRecent: 'Letzte 20 | Sprache: {lang}'":
    "headerRecent: 'Letzte 20 | Sprache: {lang}'",

    "noFavorites: Noch keine Favoriten vorhanden.":
    "noFavorites: Noch keine Favoriten vorhanden.",

    "noLogs: Noch keine Zitatprotokolle vorhanden.":
    "noLogs: Noch keine Zitatprotokolle vorhanden.",

    "noQuotesInTab: In diesem Tab sind noch keine Zitate verfügbar.":
    "noQuotesInTab: In diesem Tab sind noch keine Zitate verfügbar.",

    "rightClickSave: 'Tipp: Rechtsklicke auf dieses Buch, um das letzte Zitat zu speichern.'":
    "rightClickSave: 'Tipp: Rechtsklicke auf dieses Buch, um das letzte Zitat zu speichern.'",

    "time_s_up_c5b3ef74: §c§lZEIT ABGELAUFEN!":
    "time_s_up_c5b3ef74: §c§lZEIT ABGELAUFEN!",

    "timeUp: ZEIT ABGELAUFEN":
    "timeUp: ZEIT ABGELAUFEN",

    "titleTimeUp: ZEIT ABGELAUFEN!":
    "titleTimeUp: ZEIT ABGELAUFEN!",

    "timeUpTitle: ZEIT ABGELAUFEN!":
    "timeUpTitle: ZEIT ABGELAUFEN!"
}

count = 0
for old, new in pairs.items():
    if old in src:
        src = src.replace(old, new)
        count += 1

p.write_text(src, encoding="utf-8")
print(f"PATCHED: {p}")
print(f"REPLACEMENTS: {count}")
