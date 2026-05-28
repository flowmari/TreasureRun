#!/usr/bin/env python3
"""
Build reproducible per-language fallback resource packs for Minecraft Java Edition 1.20.1.

Each generated resource pack aliases Minecraft locale JSON paths to one selected
TreasureRun language payload. The ZIP writer normalizes entry metadata so that
identical checked-in inputs produce byte-identical artifacts within the pinned
verification toolchain.

By default, artifacts are written under build/ rather than to tracked binary
directories. This script does not publish release assets or change runtime routing.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path
from typing import Mapping

try:
    import yaml
except Exception:
    print(
        "ERROR: PyYAML is required. Install it with: python3 -m pip install pyyaml",
        file=sys.stderr,
    )
    raise

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT_DIR = ROOT / "build/generated/fallback-resourcepacks"
DEFAULT_LANG_MAP = ROOT / "src/main/resources/lang-map.yml"
DEFAULT_SOURCE_LANG_DIR = ROOT / "resourcepacks/treasurerun-i18n-pack/assets/minecraft/lang"

LOCALES = [
    "af_za", "ar_sa", "az_az", "ba_ru", "bar", "be_by", "bg_bg", "br_fr", "brb", "bs_ba",
    "ca_es", "cs_cz", "cy_gb", "da_dk", "de_at", "de_ch", "de_de", "el_gr", "en_au", "en_ca",
    "en_gb", "en_nz", "en_pt", "en_ud", "en_us", "enp", "enws", "eo_uy", "es_ar", "es_cl",
    "es_ec", "es_es", "es_mx", "es_uy", "es_ve", "esan", "et_ee", "eu_es", "fa_ir", "fi_fi",
    "fil_ph", "fo_fo", "fr_ca", "fr_fr", "fra_de", "fur_it", "fy_nl", "ga_ie", "gd_gb",
    "gl_es", "got_de", "gr_el", "gsw_ch", "gu_in", "gv_im", "ha_ng", "he_il", "hi_in",
    "hr_hr", "hu_hu", "hy_am", "id_id", "ig_ng", "io_en", "is_is", "it_it", "ja_jp", "jbo_en",
    "ka_ge", "kk_kz", "kn_in", "ko_kr", "ksh_de", "kw_gb", "la_la", "lb_lu", "li_li",
    "lmo_it", "lo_la", "lt_lt", "lv_lv", "lzh_hant", "mk_mk", "mn_mn", "ms_my", "mt_mt",
    "my_mm", "nl_nl", "no_no", "oc_fr", "ojp_jp", "pl_pl", "pt_br", "pt_pt", "qya_aa",
    "ro_ro", "rpr", "ru_ru", "se_no", "sk_sk", "sl_si", "so_so", "sq_al", "sr_cs", "sr_sp",
    "sv_se", "sxu_de", "szl_pl", "ta_in", "th_th", "tl_ph", "tlh_aa", "tok", "tr_tr",
    "tt_ru", "uk_ua", "val_es", "vec_it", "vi_vn", "yi_de", "yo_ng", "zh_cn", "zh_tw",
    "zlm_arab", "asl_us", "sa_in", "ang_gb", "non_is",
]

PACK_MCMETA = json.dumps(
    {
        "pack": {
            "pack_format": 15,
            "description": "TreasureRun i18n fallback for Minecraft 1.20.1",
        }
    },
    ensure_ascii=False,
    indent=2,
).encode("utf-8")

FIXED_ZIP_TIMESTAMP = (1980, 1, 1, 0, 0, 0)
FIXED_FILE_MODE = 0o100644 << 16


def sha1_of(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def deterministic_zip_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(filename=name, date_time=FIXED_ZIP_TIMESTAMP)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.create_system = 3
    info.external_attr = FIXED_FILE_MODE
    return info


def write_entry(zf: zipfile.ZipFile, name: str, content: bytes) -> None:
    zf.writestr(
        deterministic_zip_info(name),
        content,
        compress_type=zipfile.ZIP_DEFLATED,
        compresslevel=9,
    )


def read_mappings(lang_map_file: Path) -> Mapping[str, str]:
    parsed = yaml.safe_load(lang_map_file.read_text(encoding="utf-8")) or {}
    mappings = parsed.get("mappings", {})
    if not isinstance(mappings, dict) or not mappings:
        raise ValueError(f"No language mappings were found in {lang_map_file}")
    return {str(language): str(locale) for language, locale in mappings.items()}


def build_pack(
    output_dir: Path,
    source_lang_dir: Path,
    treasure_run_language: str,
    minecraft_locale: str,
) -> Path:
    source = source_lang_dir / f"{minecraft_locale}.json"
    if not source.exists():
        raise FileNotFoundError(f"Missing source language JSON: {source}")

    payload = source.read_bytes()
    json.loads(payload.decode("utf-8"))

    output = output_dir / f"treasurerun-i18n-pack-{treasure_run_language}.zip"
    with zipfile.ZipFile(
        output,
        "w",
        compression=zipfile.ZIP_DEFLATED,
        compresslevel=9,
    ) as zf:
        write_entry(zf, "pack.mcmeta", PACK_MCMETA)
        for locale in LOCALES:
            write_entry(zf, f"assets/minecraft/lang/{locale}.json", payload)

    return output


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help=(
            "Directory for generated ZIP files. "
            "Defaults to build/generated/fallback-resourcepacks."
        ),
    )
    parser.add_argument(
        "--lang-map",
        type=Path,
        default=DEFAULT_LANG_MAP,
        help="TreasureRun-to-Minecraft locale mapping YAML.",
    )
    parser.add_argument(
        "--source-lang-dir",
        type=Path,
        default=DEFAULT_SOURCE_LANG_DIR,
        help="Directory containing the source resource-pack language JSON files.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    mappings = read_mappings(args.lang_map)
    args.output_dir.mkdir(parents=True, exist_ok=True)

    results: list[tuple[str, str, Path]] = []
    for language, locale in sorted(mappings.items()):
        output = build_pack(args.output_dir, args.source_lang_dir, language, locale)
        digest = sha1_of(output)
        results.append((language, digest, output))
        print(f"  {language}: {output.stat().st_size // 1024}KB sha1={digest}")

    print("\n=== reproducible fallback resource pack SHA-1 values ===")
    for language, digest, _ in results:
        print(f"    {language}: {digest}")

    print(f"\nPASS: built {len(results)} reproducible per-language fallback resource packs.")
    print(f"Output directory: {args.output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
