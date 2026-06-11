#!/usr/bin/env bash
set -euo pipefail

echo "========================================================================"
echo " TreasureRun demo-world setup helper"
echo "========================================================================"

if [[ $# -ne 1 ]]; then
  echo "Usage:"
  echo "  ./scripts/demo-world-setup.sh /path/to/source/world"
  echo ""
  echo "Example:"
  echo '  ./scripts/demo-world-setup.sh "$HOME/Library/Application Support/minecraft/saves/TreasureRunDemo"'
  exit 1
fi

SOURCE_WORLD="$1"
TARGET_ROOT=".local/demo-world"
TARGET_WORLD="$TARGET_ROOT/world"

if [[ ! -d "$SOURCE_WORLD" ]]; then
  echo "ERROR: source world directory does not exist:"
  echo "  $SOURCE_WORLD"
  exit 1
fi

if [[ ! -f "$SOURCE_WORLD/level.dat" ]]; then
  echo "ERROR: source directory does not look like a Minecraft world."
  echo "Missing:"
  echo "  $SOURCE_WORLD/level.dat"
  exit 1
fi

mkdir -p "$TARGET_ROOT"

if [[ -d "$TARGET_WORLD" ]]; then
  BACKUP="$TARGET_ROOT/world.backup.$(date +%Y%m%d_%H%M%S)"
  echo "Existing demo world found."
  echo "Moving existing world to:"
  echo "  $BACKUP"
  mv "$TARGET_WORLD" "$BACKUP"
fi

echo ""
echo "Copying source world:"
echo "  $SOURCE_WORLD"
echo ""
echo "To local demo-world workspace:"
echo "  $TARGET_WORLD"

cp -R "$SOURCE_WORLD" "$TARGET_WORLD"

echo ""
echo "Result:"
du -sh "$TARGET_WORLD" || true

echo ""
echo "DONE."
echo ""
echo "The demo world was copied into:"
echo "  $TARGET_WORLD"
echo ""
echo "This path is intentionally ignored by Git."
echo "Do not commit Minecraft world data directly."
