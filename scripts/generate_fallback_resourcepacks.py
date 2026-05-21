#!/usr/bin/env python3
"""
Generate per-language fallback ResourcePack ZIPs from src/main/resources/lang-map.yml.

This script is intentionally data-driven:
- Add a language to lang-map.yml
- Add the corresponding JSON file
- Run this script
No hard-coded language list is required.
"""
import hashlib
import json
import sys
import zipfile
from pathlib import Path

try:
    import yaml
except Exception as e:
    print("ERROR: PyYAML is required. Install with: python3 -m pip install pyyaml", file=sys.stderr)
    raise

LOCALES = [
    "af_za","ar_sa","az_az","ba_ru","bar","be_by","bg_bg","br_fr","brb","bs_ba",
    "ca_es","cs_cz","cy_gb","da_dk","de_at","de_ch","de_de","el_gr","en_au","en_ca",
    "en_gb","en_nz","en_pt","en_ud","en_us","enp","enws","eo_uy","es_ar","es_cl",
    "es_ec","es_es","es_mx","es_uy","es_ve","esan","et_ee","eu_es","fa_ir","fi_fi",
    "fil_ph","fo_fo","fr_ca","fr_fr","fra_de","fur_it","fy_nl","ga_ie","gd_gb",
    "gl_es","got_de","gr_el","gsw_ch","gu_in","gv_im","ha_ng","he_il","hi_in",
    "hr_hr","hu_hu","hy_am","id_id","ig_ng","io_en","is_is","it_it","ja_jp","jbo_en",
    "ka_ge","kk_kz","kn_in","ko_kr","ksh_de","kw_gb","la_la","lb_lu","li_li",
    "lmo_it","lo_la","lt_lt","lv_lv","lzh_hant","mk_mk","mn_mn","ms_my","mt_mt",
    "my_mm","nl_nl","no_no","oc_fr","ojp_jp","pl_pl","pt_br","pt_pt","qya_aa",
    "ro_ro","rpr","ru_ru","se_no","sk_sk","sl_si","so_so","sq_al","sr_cs","sr_sp",
    "sv_se","sxu_de","szl_pl","ta_in","th_th","tl_ph","tlh_aa","tok","tr_tr",
    "tt_ru","uk_ua","val_es","vec_it","vi_vn","yi_de","yo_ng","zh_cn","zh_tw",
    "zlm_arab","asl_us","sa_in","ang_gb","non_is"
]
MCMETA = json.dumps(
    {
        "pack": {
            "pack_format": 34,
            "supported_formats": [18, 34],
            "description": "TreasureRun i18n fallback"
        }
    },
    ensure_ascii=False,
    indent=2,
)

def sha1_of(path: Path) -> str:
    h = hashlib.sha1()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()

root = Path(__file__).resolve().parent.parent
lang_map_file = root / "src/main/resources/lang-map.yml"
lang_dir = root / "fabric-i18n-mod/src/main/resources/assets/minecraft/lang"
out_dir = root / "resourcepacks/generated"

if not lang_map_file.exists():
    print(f"ERROR: missing {lang_map_file}", file=sys.stderr)
    sys.exit(1)
if not lang_dir.is_dir():
    print(f"ERROR: missing {lang_dir}", file=sys.stderr)
    sys.exit(1)

mappings = yaml.safe_load(lang_map_file.read_text(encoding="utf-8")).get("mappings", {})
out_dir.mkdir(parents=True, exist_ok=True)

results = []
for tr_lang, mc_locale in sorted(mappings.items()):
    src = lang_dir / f"{mc_locale}.json"
    if not src.exists():
        print(f"FAIL {tr_lang}: missing {src}", file=sys.stderr)
        sys.exit(1)

    data = src.read_bytes()
    json.loads(data.decode("utf-8"))

    out = out_dir / f"treasurerun-i18n-pack-{tr_lang}.zip"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("pack.mcmeta", MCMETA)
        for loc in LOCALES:
            zf.writestr(f"assets/minecraft/lang/{loc}.json", data)

    sha1 = sha1_of(out)
    results.append((tr_lang, sha1))
    print(f"  {tr_lang}: {out.stat().st_size // 1024}KB sha1={sha1}")

print("\n=== generated fallback ResourcePack SHA1 ===")
for lang, sha1 in results:
    print(f"    {lang}: {sha1}")

print(f"\nPASS: generated {len(results)} fallback ZIPs")
