#!/usr/bin/env bash
set -euo pipefail

echo "== Check 0: duplicate YAML keys =="
python3 scripts/check_yaml_duplicate_keys.py

echo "== Check 1: missing i18n keys =="
python3 scripts/check_missing_i18n_keys.py

echo "== Check 2: suspicious i18n values =="
python3 scripts/check_suspicious_i18n.py

echo "OK: i18n checks passed ✅"
