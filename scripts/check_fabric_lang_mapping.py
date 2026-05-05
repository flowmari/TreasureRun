#!/usr/bin/env python3
import re, sys
from pathlib import Path
ROOT = Path(__file__).parent.parent
MOD_JAVA = ROOT / "fabric-i18n-mod/src/main/java/plugin/i18nmod/TreasureRunI18nMod.java"
YAML_DIR = ROOT / "src/main/resources/languages"
YAML_TO_JSON = {
    "en":"en_us","ja":"ja_jp","de":"de_de","fr":"fr_fr","es":"es_es",
    "it":"it_it","ko":"ko_kr","ru":"ru_ru","zh_tw":"zh_tw","pt":"pt_br",
    "nl":"nl_nl","fi":"fi_fi","sv":"sv_se","is":"is_is","la":"la_la",
    "hi":"hi_in","sa":"sa_in","lzh":"lzh_hant","ojp":"ojp_jp","asl_gloss":"asl_us",
}
yaml_langs = {p.stem for p in YAML_DIR.glob("*.yml")}
java_content = MOD_JAVA.read_text()
cases = set(re.findall(r'case\s+"([^"]+)"\s+->', java_content))
missing = yaml_langs - cases
failures = []
if missing:
    print(f"FAIL: YAML langs missing from Java switch: {sorted(missing)}"); failures.append("missing")
else:
    print(f"✓ All {len(yaml_langs)} langs have switch cases")
lang_dir = ROOT / "resourcepacks/treasurerun-i18n-pack/assets/minecraft/lang"
for yl, jl in YAML_TO_JSON.items():
    if not (lang_dir / f"{jl}.json").exists():
        print(f"FAIL: {jl}.json missing"); failures.append(jl)
    else:
        print(f"  ✓ {yl} -> {jl}.json")
if failures: sys.exit(1)
else: print("PASS: Fabric mapping complete.")
