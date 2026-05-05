#!/usr/bin/env python3
import json, sys, yaml
from pathlib import Path
ROOT = Path(__file__).parent.parent
LANG_JSON = ROOT / "resourcepacks/treasurerun-i18n-pack/assets/minecraft/lang/en_us.json"
YAML_DIR = ROOT / "src/main/resources/languages"
THRESHOLDS = {"asl_gloss": 0.73, "en": 0.73, "la": 0.73, "sa": 0.73, "DEFAULT": 0.85}
CLIENT_ONLY = {
    'subtitles','mco','options','gui','key','selectWorld','createWorld',
    'telemetry','debug','narrator','advMode','demo','narration','tutorial',
    'selectServer','book','generator','flat_world_preset','optimizeWorld',
    'addServer','lanServer','spectatorMenu','deathScreen','controls',
    'telemetry_info','credits_and_attribution','quickplay','compliance',
    'language','multiplayerWarning','sleep','accessibility','outOfMemory',
    'permissions','realms','screenshot','symlink_warning','itemGroup',
}
en_keys = set(json.loads(LANG_JSON.read_text()).keys())
server_keys = {k for k in en_keys if k.split('.')[0] not in CLIENT_ONLY}
print(f"Server-sendable keys: {len(server_keys)}")
failures = []
for yaml_path in sorted(YAML_DIR.glob("*.yml")):
    lang = yaml_path.stem
    with open(yaml_path) as f: data = yaml.safe_load(f)
    mc = data.get('minecraft', {}).get('packet', {})
    def flatten(d, prefix=''):
        keys = set()
        if isinstance(d, dict):
            for k, v in d.items(): keys |= flatten(v, f"{prefix}.{k}" if prefix else str(k))
        else: keys.add(prefix)
        return keys
    covered = flatten(mc)
    coverage = len(covered & server_keys) / len(server_keys)
    threshold = THRESHOLDS.get(lang, THRESHOLDS["DEFAULT"])
    status = "✓" if coverage >= threshold else "✗"
    print(f"  {status} {lang}: {coverage:.1%} (threshold: {threshold:.0%})")
    if coverage < threshold: failures.append(f"{lang}: {coverage:.1%} < {threshold:.0%}")
if failures:
    print(f"\nFAIL: Regression in {failures}"); sys.exit(1)
else:
    print(f"\nPASS: No regression.")
