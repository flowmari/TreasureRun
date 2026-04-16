#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "usage: scripts/i18n_class_scan.sh <java-file>"
  exit 1
fi

FILE="$1"

echo "===== literal scan: $FILE ====="
rg -n --pcre2 '"([^"\\]|\\.)+"' "$FILE" || true
echo

echo "===== current i18n usage: $FILE ====="
rg -n 'I18n|tr\(|trList\(|Keys|sendMessage|ChatColor|BossBar|Title' "$FILE" || true
