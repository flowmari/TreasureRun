#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SOURCE_WORLD=".local/demo-world/world"
TARGET_WORLD="spigot-data/world"
BACKUP_ROOT=".local/demo-world/runtime-backups"
SYNC_MARKER=".treasurerun-demo-world-sync"

echo "========================================================================"
echo " TreasureRun contributor demo-world sync"
echo "========================================================================"

if [[ "${TREASURERUN_USE_DEMO_WORLD:-1}" == "0" ]]; then
  echo "TREASURERUN_USE_DEMO_WORLD=0; skipping demo-world sync."
  exit 0
fi

if [[ ! -d "$SOURCE_WORLD" ]]; then
  echo "No local demo world found at:"
  echo "  $SOURCE_WORLD"
  echo "Skipping demo-world sync."
  exit 0
fi

if [[ ! -f "$SOURCE_WORLD/level.dat" ]]; then
  echo "ERROR: local demo world does not look like a Minecraft world."
  echo "Missing:"
  echo "  $SOURCE_WORLD/level.dat"
  exit 1
fi

mkdir -p "$(dirname "$TARGET_WORLD")"
mkdir -p "$BACKUP_ROOT"

if [[ -d "$TARGET_WORLD" && ! -f "$TARGET_WORLD/$SYNC_MARKER" ]]; then
  BACKUP="$BACKUP_ROOT/world.before-demo-sync.$(date +%Y%m%d_%H%M%S)"
  echo "Existing runtime world found."
  echo "Because it was not marked as a TreasureRun demo-world sync target,"
  echo "moving it to a safety backup first:"
  echo "  $BACKUP"
  mv "$TARGET_WORLD" "$BACKUP"
fi

if [[ -d "$TARGET_WORLD" && -f "$TARGET_WORLD/$SYNC_MARKER" ]]; then
  echo "Existing synced demo world found."
  echo "Refreshing it from:"
  echo "  $SOURCE_WORLD"
else
  echo "Creating runtime demo world from:"
  echo "  $SOURCE_WORLD"
fi

rm -rf "$TARGET_WORLD"
mkdir -p "$(dirname "$TARGET_WORLD")"
cp -R "$SOURCE_WORLD" "$TARGET_WORLD"
touch "$TARGET_WORLD/$SYNC_MARKER"

echo ""
echo "Result:"
du -sh "$TARGET_WORLD" || true

echo ""
echo "DONE: contributor runtime will use the local demo world."
echo ""
echo "Notes:"
echo " - .local/ is ignored by Git"
echo " - spigot-data/world is runtime data and should not be committed"
echo " - set TREASURERUN_USE_DEMO_WORLD=0 to skip this sync"
