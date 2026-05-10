#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MC_DIR="${MC_DIR:-$HOME/Library/Application Support/minecraft}"
PACK_SRC="$ROOT/resourcepacks/client-custom-languages"
OUT="$MC_DIR/resourcepacks/treasurerun_custom_languages.zip"

echo "============================================================"
echo " Build TreasureRun client custom language ResourcePack"
echo " - registers custom Minecraft languages"
echo " - installs to local Minecraft resourcepacks folder"
echo "============================================================"

if [ ! -d "$PACK_SRC" ]; then
  echo "ERROR: missing $PACK_SRC"
  exit 1
fi

mkdir -p "$MC_DIR/resourcepacks"

python3 -m json.tool "$PACK_SRC/pack.mcmeta" >/dev/null

for f in ojp_jp asl_us sa_in la_la lzh_hant; do
  test -f "$PACK_SRC/assets/minecraft/lang/${f}.json"
done

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

cp -R "$PACK_SRC"/. "$tmp"/

(
  cd "$tmp"
  zip -qr "$OUT" pack.mcmeta assets
)

echo "Created:"
ls -lh "$OUT"

echo ""
echo "To enable manually in Minecraft:"
echo "  Options -> Resource Packs -> enable treasurerun_custom_languages.zip"
echo ""
echo "Optional options.txt target state:"
echo '  resourcePacks:["fabric","file/treasurerun_custom_languages.zip"]'
echo "  lang:ojp_jp"
