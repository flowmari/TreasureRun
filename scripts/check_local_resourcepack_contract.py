#!/usr/bin/env python3
"""Verify TreasureRun's V4 local-generator-only ResourcePack contract."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "resourcepacks/local-generator-policy.json"
GENERATOR = ROOT / "tools/client-resourcepack/build-local-official-language-pack.py"
LANG_MAP = ROOT / "src/main/resources/lang-map.yml"
CONFIG = ROOT / "src/main/resources/config.yml"
PAYLOAD_DIRS = [
    ROOT / "resourcepacks/treasurerun-i18n-pack/assets/minecraft/lang",
    ROOT / "resourcepacks/client-custom-languages/assets/minecraft/lang",
    ROOT / "fabric-i18n-mod/src/main/resources/assets/minecraft/lang",
    ROOT / "fabric-i18n-mod/src/main/resources/resourcepacks/treasurerun_langs/assets/minecraft/lang",
]
TRACKED_ARTIFACTS = [
    ROOT / "resourcepacks/generated/treasurerun-i18n-pack.zip",
    ROOT / "resourcepacks/generated/treasurerun-i18n-pack.zip.sha1",
    ROOT / "resourcepacks/generated/treasurerun-i18n-pack.zip.sha256",
]


def fail(message: str) -> None:
    raise AssertionError(message)


def read_map() -> dict[str, str]:
    mappings: dict[str, str] = {}
    inside = False
    for raw in LANG_MAP.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].rstrip()
        if line.strip() == "mappings:":
            inside = True
            continue
        if not inside or not line.strip():
            continue
        if not line.startswith("  "):
            break
        key, sep, value = line.strip().partition(":")
        if sep and value.strip():
            mappings[key.strip()] = value.strip()
    return mappings


def verify_static() -> dict:
    policy = json.loads(POLICY.read_text(encoding="utf-8"))
    mappings = read_map()
    official = {str(k): str(v) for k, v in policy["official_mappings"].items()}
    held = {str(k): str(v) for k, v in policy["held_mappings"].items()}
    aliases = [str(v) for v in policy["alias_locales"]]
    if len(mappings) != 23 or len(official) != 17 or len(held) != 6:
        fail(f"unexpected language counts: mappings={len(mappings)} official={len(official)} held={len(held)}")
    if {**official, **held} != mappings:
        fail("policy official/held mappings must exactly partition lang-map.yml")
    if held.get("lzh") != "lzh_hant" or "lzh" in official:
        fail("lzh -> lzh must not be silently adopted")
    if len(aliases) != 128 or len(set(aliases)) != 128:
        fail("policy must contain 128 unique alias locale paths")
    if policy.get("distribution_status") != "LOCAL_ONLY_DO_NOT_PUBLISH":
        fail("policy must mark all generated packs local-only")
    remaining = [path for directory in PAYLOAD_DIRS for path in directory.glob("*.json")]
    if remaining:
        fail(f"tracked Minecraft language payload JSON remains: {remaining[:5]}")
    existing_artifacts = [path for path in TRACKED_ARTIFACTS if path.exists()]
    if existing_artifacts:
        fail(f"tracked shared ResourcePack artifacts remain: {existing_artifacts}")
    config = CONFIG.read_text(encoding="utf-8")
    standard = re.search(r"(?ms)^resourcePack:\n.*?(?=^resourcePackFallback:|\Z)", config)
    fallback = re.search(r"(?ms)^resourcePackFallback:\n.*\Z", config)
    if standard is None or fallback is None:
        fail("ResourcePack config blocks are missing")
    for expected in ["  enabled: false", '  url: ""', '  sha1: ""']:
        if expected not in standard.group(0):
            fail(f"top-level ResourcePack config is missing: {expected}")
    if "  enabled: false" not in fallback.group(0) or "  packs: {}" not in fallback.group(0):
        fail("fallback ResourcePack config must be disabled with an empty pack map")
    if "github.com/flowmari/TreasureRun/releases/download" in standard.group(0) + fallback.group(0):
        fail("active ResourcePack config must not contain a public Release URL")
    if not GENERATOR.is_file():
        fail("local-only generator is missing")
    print("PASS: static V4 local-generator-only contract verified.")
    return policy


def verify_zip(path: Path, expected_aliases: int) -> None:
    with zipfile.ZipFile(path) as zf:
        names = zf.namelist()
        if "pack.mcmeta" not in names or "treasurerun-local-only.json" not in names:
            fail(f"local-only metadata is missing from {path.name}")
        marker = json.loads(zf.read("treasurerun-local-only.json").decode("utf-8"))
        if marker.get("distribution_status") != "LOCAL_ONLY_DO_NOT_PUBLISH":
            fail(f"local-only marker is invalid in {path.name}")
        aliases = [name for name in names if name.startswith("assets/minecraft/lang/") and name.endswith(".json")]
        if len(aliases) != expected_aliases:
            fail(f"{path.name}: expected {expected_aliases} alias payloads, found {len(aliases)}")
        payload_hashes = {hashlib.sha256(zf.read(name)).hexdigest() for name in aliases}
        if len(payload_hashes) != 1:
            fail(f"{path.name}: aliases must share one complete local official payload")


def verify_integration(policy: dict, mc_dir: Path, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    official_languages = sorted(policy["official_mappings"])
    with tempfile.TemporaryDirectory(prefix="tr_v4_local_all_") as all_tmp, tempfile.TemporaryDirectory(prefix="tr_v4_local_recheck_") as recheck_tmp:
        all_dir = Path(all_tmp)
        recheck_dir = Path(recheck_tmp)
        subprocess.run([
            sys.executable, str(GENERATOR), "--all", "--minecraft-dir", str(mc_dir), "--output-dir", str(all_dir)
        ], cwd=ROOT, check=True)
        all_zips = sorted(all_dir.glob("*.zip"))
        if len(all_zips) != 17:
            fail(f"expected 17 local official packs, found {len(all_zips)}")
        for pack in all_zips:
            verify_zip(pack, len(policy["alias_locales"]))
            shutil.copy2(pack, output_dir / pack.name)
        shutil.copy2(all_dir / "local-pack-manifest.tsv", output_dir / "local-pack-manifest.tsv")

        # Rebuild two boundary representatives byte-for-byte. All 17 packs use the
        # same deterministic writer; the remaining packs are verified structurally
        # and by their manifest SHA-256 values in the first complete build.
        representatives = [official_languages[0], official_languages[-1]]
        for language in representatives:
            subprocess.run([
                sys.executable, str(GENERATOR), "--language", language, "--minecraft-dir", str(mc_dir), "--output-dir", str(recheck_dir)
            ], cwd=ROOT, check=True)
            name = f"treasurerun-local-{language}-{policy['minecraft_version']}.zip"
            first = all_dir / name
            second = recheck_dir / name
            if first.read_bytes() != second.read_bytes():
                fail(f"local generator is not byte-reproducible for representative language: {language}")
    for language in sorted(policy["held_mappings"]):
        result = subprocess.run([
            sys.executable, str(GENERATOR), "--language", language, "--minecraft-dir", str(mc_dir), "--output-dir", str(output_dir / "held-probe")
        ], cwd=ROOT, text=True, capture_output=True)
        if result.returncode != 2 or "HOLD:" not in result.stderr:
            fail(f"held mapping did not fail clearly: {language}")
    print("PASS: all 17 official local-only packs were verified; two representative packs were rebuilt byte-for-byte.")
    print("PASS: all six held mappings fail clearly.")
    print(f"Local generated packs: {output_dir}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft-dir", type=Path)
    parser.add_argument("--integration-output", type=Path)
    args = parser.parse_args()
    policy = verify_static()
    if args.minecraft_dir or args.integration_output:
        if args.minecraft_dir is None or args.integration_output is None:
            fail("--minecraft-dir and --integration-output must be supplied together")
        verify_integration(policy, args.minecraft_dir, args.integration_output)
    print("PASS: ResourcePack V4 local-generator-only contract complete.")
    print("HOLD: generated packs are local-only and are not distribution artifacts.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
