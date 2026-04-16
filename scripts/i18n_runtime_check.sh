#!/usr/bin/env bash
set -euo pipefail

JAR_GLOB="${1:-build/libs/*-all.jar}"
CONTAINER="${2:-minecraft_spigot}"
DEST="${3:-/data/plugins/TreasureRun-1.0-SNAPSHOT-all.jar}"

TMP_JAR=/tmp/TreasureRun-latest.jar

cp $JAR_GLOB "$TMP_JAR"
docker cp "$TMP_JAR" "$CONTAINER:$DEST"
docker restart "$CONTAINER" >/dev/null
sleep 20

echo "===== runtime filtered ====="
docker logs --tail 400 "$CONTAINER" 2>&1 | \
grep -nEi 'treasurerun|enabling|enabled|正常に起動しました|missing key|default\.unknown|error|exception' || true
