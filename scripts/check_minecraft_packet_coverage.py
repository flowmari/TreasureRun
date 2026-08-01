#!/usr/bin/env python3
"""Check packet-localisation coverage using TreasureRun-owned YAML, not tracked Minecraft payload JSON."""
import sys
from pathlib import Path
import yaml
ROOT = Path(__file__).resolve().parents[1]
YAML_DIR = ROOT / "src/main/resources/languages"

def flatten(value, prefix=""):
    keys = set()
    if isinstance(value, dict):
        for key, child in value.items():
            keys |= flatten(child, f"{prefix}.{key}" if prefix else str(key))
    else:
        keys.add(prefix)
    return keys
with (ROOT / "src/main/resources/lang-map.yml").open() as stream:
    mappings = yaml.safe_load(stream).get("mappings", {})
with (YAML_DIR / "en.yml").open() as stream:
    english = yaml.safe_load(stream) or {}
reference = flatten(english.get("minecraft", {}).get("packet", {}))
if not reference:
    raise SystemExit("FAIL: English minecraft.packet reference keyset is empty")
low = {"asl_gloss", "en", "la", "sa", "ang", "non", "got"}
failures = []
print(f"TreasureRun-owned English packet keys: {len(reference)}, Languages: {len(mappings)}")
for language in sorted(mappings):
    path = YAML_DIR / f"{language}.yml"
    if not path.is_file(): failures.append(f"{language}: YAML missing"); continue
    with path.open() as stream: data = yaml.safe_load(stream) or {}
    covered = flatten(data.get("minecraft", {}).get("packet", {}))
    coverage = len(covered & reference) / len(reference)
    threshold = 0.73 if language in low else 0.85
    print(f"  {'✓' if coverage >= threshold else '✗'} {language}: {coverage:.1%} (threshold: {threshold:.0%})")
    if coverage < threshold: failures.append(f"{language}: {coverage:.1%} < {threshold:.0%}")
if failures:
    print(f"FAIL: {failures}"); sys.exit(1)
print("PASS: packet-localisation YAML coverage has no regression.")
