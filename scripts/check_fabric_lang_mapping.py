#!/usr/bin/env python3
import re, sys, yaml
from pathlib import Path
ROOT = Path(__file__).parent.parent
LANG_MAP_FILE = ROOT / "src/main/resources/lang-map.yml"
YAML_DIR = ROOT / "src/main/resources/languages"
RP_LANG_DIR = ROOT / "resourcepacks/treasurerun-i18n-pack/assets/minecraft/lang"
MOD_LANG_DIR = ROOT / "fabric-i18n-mod/src/main/resources/assets/minecraft/lang"
MOD_JAVA = ROOT / "fabric-i18n-mod/src/main/java/plugin/i18nmod/TreasureRunI18nMod.java"
with open(LANG_MAP_FILE) as f:
    mappings = yaml.safe_load(f).get("mappings", {})
print(f"lang-map.yml: {len(mappings)} languages")
failures = []
for tr_lang, mc_lang in sorted(mappings.items()):
    issues = []
    if not (YAML_DIR / f"{tr_lang}.yml").exists(): issues.append(f"languages/{tr_lang}.yml MISSING")
    if not (RP_LANG_DIR / f"{mc_lang}.json").exists(): issues.append(f"resourcepack/{mc_lang}.json MISSING")
    if not (MOD_LANG_DIR / f"{mc_lang}.json").exists(): issues.append(f"fabric-mod/{mc_lang}.json MISSING")
    status = "✓" if not issues else "✗"
    print(f"  {status} {tr_lang} -> {mc_lang}")
    for issue in issues: print(f"      FAIL: {issue}")
    failures.extend(issues)
if MOD_JAVA.exists():
    jc = MOD_JAVA.read_text()
    if "lang-map.yml" in jc or "langMap" in jc or "LANG_MAP" in jc:
        print(f"\n  ✓ Fabric mod uses dynamic lang-map.yml lookup (zero-code scaling)")
    elif "switch" in jc:
        cases = set(re.findall(r'case\s+"([^"]+)"\s+->', jc))
        missing = set(mappings.keys()) - cases
        if missing: print(f"\n  WARN: Java switch missing: {sorted(missing)}")
if failures: print(f"\nFAIL: {failures}"); sys.exit(1)
else: print(f"\nPASS: All {len(mappings)} languages provisioned.")
