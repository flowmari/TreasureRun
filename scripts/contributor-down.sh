#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

COMPOSE_FILE="compose.contributor.yml"
PROJECT="treasurerun-contributor"

if [[ "${1:-}" == "--volumes" ]]; then
  docker compose -p "$PROJECT" -f "$COMPOSE_FILE" down --volumes
  echo "Local TreasureRun runtime stopped and its isolated data volumes were removed."
else
  docker compose -p "$PROJECT" -f "$COMPOSE_FILE" down
  echo "Local TreasureRun runtime stopped. Its isolated data volumes were kept."
fi
