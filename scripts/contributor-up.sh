#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

COMPOSE_FILE="compose.contributor.yml"
PROJECT="treasurerun-contributor"
MC_PORT="${TREASURERUN_MC_PORT:-25575}"
PLAYER_NAME="${1:-}"
MODE="${2:-}"

if [[ -z "$PLAYER_NAME" ]]; then
  echo "ERROR: Provide your Minecraft Java username."
  echo "Examples:"
  echo "  ./scripts/contributor-up.sh YourMinecraftName"
  echo "  ./scripts/contributor-up.sh YourMinecraftName --with-db"
  exit 1
fi

if [[ -n "$MODE" && "$MODE" != "--with-db" ]]; then
  echo "ERROR: Unsupported option: $MODE"
  echo "Supported optional mode: --with-db"
  exit 1
fi

if [[ $# -gt 2 ]]; then
  echo "ERROR: Too many arguments."
  exit 1
fi

export TREASURERUN_OPS="$PLAYER_NAME"

WITH_DB=false
if [[ "$MODE" == "--with-db" ]]; then
  WITH_DB=true
  export TREASURERUN_DATABASE_ENABLED=true
  export TREASURERUN_DB_HOST="${TREASURERUN_DB_HOST:-minecraft_mysql}"
  export TREASURERUN_DB_PORT="${TREASURERUN_DB_PORT:-3306}"
  export TREASURERUN_DB_NAME="${TREASURERUN_DB_NAME:-treasureDB}"
  export TREASURERUN_DB_USER="${TREASURERUN_DB_USER:-user}"
  export TREASURERUN_DB_PASSWORD="${TREASURERUN_DB_PASSWORD:-password}"
else
  export TREASURERUN_DATABASE_ENABLED=false
fi

if [[ -x "./scripts/contributor-demo-world-sync.sh" ]]; then
  ./scripts/contributor-demo-world-sync.sh
fi

echo "========================================================================"
echo " TreasureRun local contributor runtime"
echo " - standard mode: Spigot 1.20.1 without MySQL"
echo " - optional mode: add --with-db for MySQL-backed feature verification"
echo " - operator permissions for local player: ${TREASURERUN_OPS}"
echo "========================================================================"

command -v docker >/dev/null 2>&1 || {
  echo "ERROR: Docker is required for the local game runtime."
  exit 1
}

docker info >/dev/null 2>&1 || {
  echo "ERROR: Docker Desktop is not running."
  exit 1
}

echo ""
echo "=== 1) Run the default build ==="
./gradlew clean build --no-daemon --console=plain

if [[ "$WITH_DB" == true ]]; then
  echo ""
  echo "=== 2) Run Docker-backed MySQL integration tests ==="
  ./gradlew integrationTest --no-daemon --console=plain
fi

JAR="$(find build/libs -maxdepth 1 -type f -name 'TreasureRun-*-all.jar' | LC_ALL=C sort | head -1)"
if [[ -z "$JAR" || ! -f "$JAR" ]]; then
  echo "ERROR: no built TreasureRun shadow JAR was found under build/libs/."
  exit 1
fi

echo "Plugin JAR: $JAR"

echo ""
if [[ "$WITH_DB" == true ]]; then
  echo "=== 3) Start MySQL and wait for health ==="
  docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d minecraft_mysql

  MYSQL_CID="$(docker compose -p "$PROJECT" -f "$COMPOSE_FILE" ps -q minecraft_mysql)"
  if [[ -z "$MYSQL_CID" ]]; then
    echo "ERROR: the contributor MySQL container could not be found."
    exit 1
  fi

  for _ in $(seq 1 90); do
    HEALTH="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$MYSQL_CID" 2>/dev/null || true)"
    if [[ "$HEALTH" == "healthy" ]]; then
      break
    fi
    sleep 2
  done

  HEALTH="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$MYSQL_CID" 2>/dev/null || true)"
  if [[ "$HEALTH" != "healthy" ]]; then
    echo "ERROR: contributor MySQL did not become healthy."
    docker compose -p "$PROJECT" -f "$COMPOSE_FILE" logs --tail=160 minecraft_mysql
    exit 1
  fi
else
  echo "=== 2) Ensure the standard runtime has no MySQL container ==="
  docker compose -p "$PROJECT" -f "$COMPOSE_FILE" stop minecraft_mysql >/dev/null 2>&1 || true
  docker compose -p "$PROJECT" -f "$COMPOSE_FILE" rm -f minecraft_mysql >/dev/null 2>&1 || true
fi

echo ""
echo "=== 4) Start the isolated Spigot container ==="
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d minecraft_spigot

SERVER_CID="$(docker compose -p "$PROJECT" -f "$COMPOSE_FILE" ps -q minecraft_spigot)"
if [[ -z "$SERVER_CID" ]]; then
  echo "ERROR: the local Spigot container could not be found."
  exit 1
fi

echo ""
echo "=== 5) Wait for the server plugin directory ==="
for _ in $(seq 1 60); do
  if docker exec "$SERVER_CID" test -d /data/plugins >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! docker exec "$SERVER_CID" test -d /data/plugins >/dev/null 2>&1; then
  echo "ERROR: /data/plugins was not created in the local server container."
  docker compose -p "$PROJECT" -f "$COMPOSE_FILE" logs --tail=160 minecraft_spigot
  exit 1
fi

echo ""
echo "=== 6) Install exactly one TreasureRun JAR and restart ==="
docker exec "$SERVER_CID" sh -lc '
  mkdir -p /data/plugins-disabled
  for f in /data/plugins/TreasureRun*.jar; do
    if [ -f "$f" ]; then
      mv "$f" "/data/plugins-disabled/$(basename "$f").before-contributor-up"
    fi
  done
'

docker cp "$JAR" "$SERVER_CID:/data/plugins/TreasureRun.jar"

ACTIVE_JAR_COUNT="$(docker exec "$SERVER_CID" sh -lc 'find /data/plugins -maxdepth 1 -type f -name "TreasureRun*.jar" | wc -l | tr -d " "')"
if [[ "$ACTIVE_JAR_COUNT" != "1" ]]; then
  echo "ERROR: expected exactly one active TreasureRun JAR, found $ACTIVE_JAR_COUNT."
  docker exec "$SERVER_CID" sh -lc 'find /data/plugins -maxdepth 1 -type f -name "TreasureRun*.jar" -print'
  exit 1
fi

RESTARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" restart minecraft_spigot

echo ""
echo "=== 7) Wait for Spigot Done and TreasureRun readiness ==="
EXPECTED_DATABASE_STATE="disabled"
if [[ "$WITH_DB" == true ]]; then
  EXPECTED_DATABASE_STATE="connected"
fi

READY=false
for _ in $(seq 1 180); do
  if ! docker inspect --format '{{.State.Running}}' "$SERVER_CID" 2>/dev/null | grep -qx true; then
    echo "ERROR: the Spigot container stopped during startup."
    docker compose -p "$PROJECT" -f "$COMPOSE_FILE" logs --tail=200 minecraft_spigot
    exit 1
  fi

  LOGS="$(docker logs --since "$RESTARTED_AT" "$SERVER_CID" 2>&1 || true)"
  if grep -Fq "[TreasureRun] Core runtime ready; database=${EXPECTED_DATABASE_STATE}" <<<"$LOGS" \
      && grep -Fq "Done (" <<<"$LOGS"; then
    READY=true
    break
  fi
  sleep 2
done

LOGS="$(docker logs --since "$RESTARTED_AT" "$SERVER_CID" 2>&1 || true)"
if [[ "$READY" != true ]]; then
  echo "ERROR: Spigot or TreasureRun did not reach the expected ready state."
  printf '%s\n' "$LOGS" | tail -n 220
  exit 1
fi

if [[ "$WITH_DB" == false ]]; then
  if grep -Ei 'Communications link failure|Connection refused|Failed to connect to MySQL|DB 初期化失敗|MySQL 再接続|SQLException|NullPointerException' <<<"$LOGS" >/dev/null; then
    echo "ERROR: the standard Spigot-only runtime emitted a database or null-safety failure."
    printf '%s\n' "$LOGS" | tail -n 220
    exit 1
  fi
fi

echo ""
echo "========================================================================"
echo "Local TreasureRun runtime is ready."
echo "Database mode: ${EXPECTED_DATABASE_STATE}"
echo "Connect from Minecraft Java Edition 1.20.1:"
echo "  localhost:${MC_PORT}"
echo ""
echo "First gameplay path:"
echo "  /gamestart normal"
echo ""
echo "Stop:"
echo "  ./scripts/contributor-down.sh"
echo "Reset all isolated data:"
echo "  ./scripts/contributor-down.sh --volumes"
echo ""
echo "Advanced i18n note:"
echo "  This playable setup does not automatically install ProtocolLib or the"
echo "  optional Fabric mod. Follow the advanced documentation when testing"
echo "  packet-boundary or runtime-language-sync behavior."
echo "========================================================================"
