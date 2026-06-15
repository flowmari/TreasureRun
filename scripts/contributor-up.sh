#!/usr/bin/env bash
set -euo pipefail
# First-time tester convenience:
# Allow the Minecraft player name to be passed as the first argument:
#   ./scripts/contributor-up.sh YourMinecraftName
# The TREASURERUN_OPS environment variable still works and takes precedence.
if [ "${1:-}" != "" ] && [ -z "${TREASURERUN_OPS:-}" ]; then
  export TREASURERUN_OPS="$1"
fi


ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# If a local demo world has been prepared, sync it into the contributor runtime workspace.
# The world data itself stays outside Git under .local/demo-world/.
if [[ -x "./scripts/contributor-demo-world-sync.sh" ]]; then
  ./scripts/contributor-demo-world-sync.sh
fi

COMPOSE_FILE="compose.contributor.yml"
PROJECT="treasurerun-contributor"
MC_PORT="${TREASURERUN_MC_PORT:-25575}"

if [[ -z "${TREASURERUN_OPS:-}" ]]; then
  echo "ERROR: Set your Minecraft Java username before starting the local game runtime."
  echo "Example:"
  echo "  TREASURERUN_OPS=YourMinecraftName ./scripts/contributor-up.sh"
  exit 1
fi

echo "========================================================================"
echo " TreasureRun local contributor runtime"
echo " - default Gradle build without Docker-backed integration tests"
echo " - isolated MySQL and Spigot runtime managed by Docker Compose"
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

JAR="$(find build/libs -maxdepth 1 -type f -name 'TreasureRun-*-all.jar' | LC_ALL=C sort | head -1)"

if [[ -z "$JAR" || ! -f "$JAR" ]]; then
  echo "ERROR: no built TreasureRun shadow JAR was found under build/libs/."
  exit 1
fi

echo "Plugin JAR: $JAR"

echo ""
echo "=== 2) Start the isolated MySQL and Spigot containers ==="
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d minecraft_mysql minecraft_spigot

SERVER_CID="$(docker compose -p "$PROJECT" -f "$COMPOSE_FILE" ps -q minecraft_spigot)"

if [[ -z "$SERVER_CID" ]]; then
  echo "ERROR: the local Spigot container could not be found."
  exit 1
fi

echo ""
echo "=== 3) Wait for the server plugin directory ==="
for _ in $(seq 1 60); do
  if docker exec "$SERVER_CID" test -d /data/plugins >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! docker exec "$SERVER_CID" test -d /data/plugins >/dev/null 2>&1; then
  echo "ERROR: /data/plugins was not created in the local server container."
  docker compose -p "$PROJECT" -f "$COMPOSE_FILE" logs --tail=120 minecraft_spigot
  exit 1
fi

echo ""
echo "=== 4) Install TreasureRun and restart the local server ==="
echo "Removing stale TreasureRun plugin JARs from the active plugins directory..."
docker exec "$SERVER_CID" sh -lc 'mkdir -p /data/plugins-disabled && for f in /data/plugins/TreasureRun*.jar; do if [ -f "$f" ]; then mv "$f" "/data/plugins-disabled/$(basename "$f").before-contributor-up"; fi; done'

echo "Installing latest built plugin JAR as /data/plugins/TreasureRun.jar"
docker cp "$JAR" "$SERVER_CID:/data/plugins/TreasureRun.jar"

echo "Active TreasureRun plugin JARs:"
docker exec "$SERVER_CID" sh -lc 'ls -lh /data/plugins | grep -E "TreasureRun.*\.jar" || true'

docker compose -p "$PROJECT" -f "$COMPOSE_FILE" restart minecraft_spigot

echo ""
echo "========================================================================"
echo "Local TreasureRun runtime started."
echo ""
echo "Connect from Minecraft Java Edition 1.20.1:"
echo "  localhost:${MC_PORT}"
echo ""
echo "Player granted operator permissions:"
echo "  ${TREASURERUN_OPS}"
echo ""
echo "Useful commands:"
echo "  docker compose -p $PROJECT -f $COMPOSE_FILE logs -f minecraft_spigot"
echo "  ./scripts/contributor-down.sh"
echo ""
echo "Advanced i18n note:"
echo "  This playable setup does not automatically install ProtocolLib or the"
echo "  optional Fabric mod. Follow the advanced documentation when testing"
echo "  packet-boundary or runtime-language-sync behavior."
echo "========================================================================"
