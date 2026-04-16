#!/usr/bin/env bash
set -euo pipefail

echo "===== compileJava ====="
./gradlew compileJava
echo

echo "===== yaml_syntax ====="
python3 scripts/check_i18n_yaml_syntax.py
echo

echo "===== required_keys ====="
python3 scripts/check_i18n_required_keys.py
echo

echo "===== referenced_keys ====="
python3 scripts/check_i18n_referenced_keys.py
echo

echo "===== duplicate_keys ====="
python3 scripts/check_i18n_duplicate_keys.py
echo

echo "===== suspect_keys (report only) ====="
python3 scripts/check_i18n_suspect_keys.py || true
