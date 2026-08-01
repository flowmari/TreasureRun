#!/usr/bin/env python3
"""Verify the 23-language server map and the 17/6 V4 client-pack policy partition."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def read_map(path):
    result = {}; inside = False
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].rstrip()
        if line.strip() == "mappings:": inside = True; continue
        if not inside or not line.strip(): continue
        if not line.startswith("  "): break
        key, sep, value = line.strip().partition(":")
        if sep and value.strip(): result[key.strip()] = value.strip()
    return result
src = read_map(ROOT / "src/main/resources/lang-map.yml")
mod = read_map(ROOT / "fabric-i18n-mod/src/main/resources/lang-map.yml")
policy = json.loads((ROOT / "resourcepacks/local-generator-policy.json").read_text(encoding="utf-8"))
official = policy["official_mappings"]; held = policy["held_mappings"]
assert src == mod, "server and Fabric lang-map.yml files differ"
assert len(src) == 23 and len(official) == 17 and len(held) == 6
assert {**official, **held} == src
assert held.get("lzh") == "lzh_hant" and "lzh" not in official
print("PASS: 23 plugin/Fabric mappings preserved; 17 official local-pack mappings and six held mappings are explicit.")
