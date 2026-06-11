#!/usr/bin/env bash
set -euo pipefail

SOURCE_WORLD=".local/demo-world/world"
TARGET_WORLD="spigot-data/world"
BACKUP_ROOT=".local/demo-world/runtime-backups"

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

if [[ -d "$TARGET_WORLD" ]]; then
  if [[ -f "$TARGET_WORLD/level.dat" ]]; then
    BACKUP="$BACKUP_ROOT/world.before-demo-sync.$(date +%Y%m%d_%H%M%S)"
    echo "Existing runtime world found."
    echo "Moving existing runtime world to:"
    echo "  $BACKUP"
    mv "$TARGET_WORLD" "$BACKUP"
  else
    echo "Existing target path does not look like a Minecraft world:"
    echo "  $TARGET_WORLD"
    echo "Moving it aside for safety."
    BACKUP="$BACKUP_ROOT/world.nonworld-before-demo-sync.$(date +%Y%m%d_%H%M%S)"
    mv "$TARGET_WORLD" "$BACKUP"
  fi
fi

echo ""
echo "Copying local demo world into contributor runtime workspace:"
echo "  from: $SOURCE_WORLD"
echo "  to:   $TARGET_WORLD"

cp -R "$SOURCE_WORLD" "$TARGET_WORLD"

echo ""
echo "Result:"
du -sh "$TARGET_WORLD" || true

echo ""
echo "DONE: contributor runtime will use the local demo world."
echo ""
echo "Note:"
echo " - .local/ is ignored by Git"
echo " - spigot-data/world is runtime data and should not be committed"
echo " - set TREASURERUN_USE_DEMO_WORLD=0 to skip this sync"
