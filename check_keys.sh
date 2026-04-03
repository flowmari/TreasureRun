#!/usr/bin/env bash
set -euo pipefail

ROOT="src/main/resources/languages"
OVERRIDE="plugins/TreasureRun/languages"

# ripgrepが無いと動かない
command -v rg >/dev/null || { echo "NG: ripgrep (rg) not found"; exit 2; }

echo "== Check: src languages =="

missing=0
for f in "$ROOT"/*.yml; do
  [ -f "$f" ] || continue
  miss=0

  # ---- ui.labels.* ----
  rg -n "^\s*ui:\s*$" "$f" >/dev/null || miss=1
  rg -n "^\s{2}labels:\s*$" "$f" >/dev/null || miss=1
  rg -n "^\s{4}latest:\s*['\"].+" "$f" >/dev/null || miss=1
  rg -n "^\s{4}tabs:\s*['\"].+" "$f" >/dev/null || miss=1
  rg -n "^\s{4}page:\s*['\"].*\{page\}.*\{total\}.*" "$f" >/dev/null || miss=1
  rg -n "^\s{4}tab:\s*$" "$f" >/dev/null || miss=1
  rg -n "^\s{6}all:\s*['\"].+" "$f" >/dev/null || miss=1
  rg -n "^\s{6}success:\s*['\"].+" "$f" >/dev/null || miss=1
  rg -n "^\s{6}timeUp:\s*['\"].+" "$f" >/dev/null || miss=1
  rg -n "^\s{6}favorites:\s*['\"].+" "$f" >/dev/null || miss=1

  # ---- ui.quote.intro.* ----
  rg -n "^\s{2}quote:\s*$" "$f" >/dev/null || miss=1
  rg -n "^\s{4}intro:\s*$" "$f" >/dev/null || miss=1
  rg -n "^\s{6}tabsTitle:\s*['\"].+" "$f" >/dev/null || miss=1
  rg -n "^\s{6}legendTitle:\s*['\"].+" "$f" >/dev/null || miss=1
  rg -n "^\s{6}storedInDb:\s*['\"].+" "$f" >/dev/null || miss=1
  rg -n "^\s{6}langLine:\s*['\"].*\{lang\}.*" "$f" >/dev/null || miss=1
  rg -n "^\s{6}db:\s*$" "$f" >/dev/null || miss=1
  rg -n "^\s{8}logs:\s*['\"].+" "$f" >/dev/null || miss=1
  rg -n "^\s{8}favorites:\s*['\"].+" "$f" >/dev/null || miss=1

  # ---- ui.menu.book.latestHint (placeholder) ----
  # ここは構造が「ui -> menu -> book -> latestHint」なので、インデントはファイルにより揺れてもOKにする
  rg -n "latestHint:.*\{latestLabel\}" "$f" >/dev/null || miss=1

  if [ "$miss" -eq 1 ]; then
    echo "MISSING: $(basename "$f")"
    missing=1
  fi
done

if [ "$missing" -eq 0 ]; then
  echo "OK: src languages look good ✅"
else
  echo "NG: missing keys in src languages ❌"
  exit 1
fi

# ---- override (plugins/TreasureRun/languages) ----
# overrideは存在するファイルだけ警告（fatalにはしない）
if [ -d "$OVERRIDE" ]; then
  echo "== Check: override languages (plugins/TreasureRun/languages) =="
  for f in "$OVERRIDE"/*.yml; do
    [ -f "$f" ] || continue
    if ! rg -n "latestHint:.*\{latestLabel\}" "$f" >/dev/null; then
      echo "WARN: override missing {latestLabel}: $(basename "$f")"
    fi
  done
  echo "Done (override warnings are not fatal)."
fi
