#!/usr/bin/env python3
"""
Verify the reproducible build contract for per-language fallback resource packs.

This check creates two clean temporary builds and verifies that:
- all configured TreasureRun languages produce a resource pack ZIP;
- ResourcePack and Fabric source JSON files remain semantically identical;
- every source payload contains exactly 8039 Minecraft 1.20.1 translation keys;
- every generated ZIP declares Minecraft 1.20.1 compatibility with pack_format 15;
- every generated ZIP contains the expected locale aliases;
- every alias payload exactly matches its ResourcePack source payload;
- two clean builds in the pinned verification toolchain are byte-identical and
  produce identical SHA-1 values.

No generated ZIP is written into the repository.
"""

from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

try:
    import yaml
except Exception:
    print(
        "ERROR: PyYAML is required. Install it with: python3 -m pip install pyyaml",
        file=sys.stderr,
    )
    raise

ROOT = Path(__file__).resolve().parent.parent
GENERATOR = ROOT / "scripts/generate_fallback_resourcepacks.py"
LANG_MAP = ROOT / "src/main/resources/lang-map.yml"
RESOURCE_PACK_LANG_DIR = ROOT / "resourcepacks/treasurerun-i18n-pack/assets/minecraft/lang"
FABRIC_LANG_DIR = ROOT / "fabric-i18n-mod/src/main/resources/assets/minecraft/lang"

EXPECTED_LANGUAGE_COUNT = 23
EXPECTED_ALIAS_COUNT = 128
EXPECTED_KEY_COUNT = 8039
EXPECTED_PACK_FORMAT = 15
EXPECTED_DESCRIPTION = "TreasureRun i18n fallback for Minecraft 1.20.1"


def sha1_of(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_mappings() -> dict[str, str]:
    parsed = yaml.safe_load(LANG_MAP.read_text(encoding="utf-8")) or {}
    mappings = parsed.get("mappings", {})
    if not isinstance(mappings, dict) or not mappings:
        raise AssertionError("lang-map.yml must contain non-empty language mappings.")
    return {str(language): str(locale) for language, locale in mappings.items()}


def load_json(path: Path) -> dict[str, object]:
    if not path.exists():
        raise AssertionError(f"Required language JSON does not exist: {path}")
    parsed = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(parsed, dict):
        raise AssertionError(f"Expected a JSON object in {path}")
    return parsed


def verify_cross_layer_source_contract(language: str, locale: str) -> bytes:
    resource_pack_path = RESOURCE_PACK_LANG_DIR / f"{locale}.json"
    fabric_path = FABRIC_LANG_DIR / f"{locale}.json"

    resource_pack_json = load_json(resource_pack_path)
    fabric_json = load_json(fabric_path)

    if len(resource_pack_json) != EXPECTED_KEY_COUNT:
        raise AssertionError(
            f"{language}: ResourcePack source {locale}.json has "
            f"{len(resource_pack_json)} keys, expected {EXPECTED_KEY_COUNT}."
        )

    if len(fabric_json) != EXPECTED_KEY_COUNT:
        raise AssertionError(
            f"{language}: Fabric source {locale}.json has "
            f"{len(fabric_json)} keys, expected {EXPECTED_KEY_COUNT}."
        )

    if resource_pack_json != fabric_json:
        raise AssertionError(
            f"{language}: ResourcePack and Fabric source content differ for {locale}.json."
        )

    return resource_pack_path.read_bytes()


def build_into(output_dir: Path) -> None:
    result = subprocess.run(
        [
            sys.executable,
            str(GENERATOR),
            "--output-dir",
            str(output_dir),
            "--lang-map",
            str(LANG_MAP),
            "--source-lang-dir",
            str(RESOURCE_PACK_LANG_DIR),
        ],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )

    if "PASS: built" not in result.stdout:
        raise AssertionError("Generator did not report a successful reproducible build.")


def inspect_generated_pack(path: Path, expected_payload: bytes) -> tuple[list[str], str]:
    if not path.exists():
        raise AssertionError(f"Generated resource pack ZIP does not exist: {path}")

    with zipfile.ZipFile(path) as zf:
        names = zf.namelist()

        if "pack.mcmeta" not in names:
            raise AssertionError(f"{path.name}: pack.mcmeta is missing.")

        metadata = json.loads(zf.read("pack.mcmeta").decode("utf-8"))
        expected_metadata = {
            "pack": {
                "pack_format": EXPECTED_PACK_FORMAT,
                "description": EXPECTED_DESCRIPTION,
            }
        }
        if metadata != expected_metadata:
            raise AssertionError(
                f"{path.name}: unexpected pack.mcmeta content: {metadata!r}"
            )

        alias_entries = sorted(
            name for name in names
            if name.startswith("assets/minecraft/lang/") and name.endswith(".json")
        )
        if len(alias_entries) != EXPECTED_ALIAS_COUNT:
            raise AssertionError(
                f"{path.name}: expected {EXPECTED_ALIAS_COUNT} alias JSON files, "
                f"found {len(alias_entries)}."
            )

        payload_hashes: set[str] = set()
        for entry in alias_entries:
            payload = zf.read(entry)
            if payload != expected_payload:
                raise AssertionError(
                    f"{path.name}: alias payload differs from its ResourcePack source: {entry}"
                )

            parsed = json.loads(payload.decode("utf-8"))
            if len(parsed) != EXPECTED_KEY_COUNT:
                raise AssertionError(
                    f"{path.name}: {entry} has {len(parsed)} keys, "
                    f"expected {EXPECTED_KEY_COUNT}."
                )

            payload_hashes.add(hashlib.sha1(payload).hexdigest())

        if len(payload_hashes) != 1:
            raise AssertionError(f"{path.name}: alias entries do not share one uniform payload.")

        return alias_entries, sha1_of(path)


def main() -> int:
    mappings = load_mappings()
    if len(mappings) != EXPECTED_LANGUAGE_COUNT:
        raise AssertionError(
            f"Expected {EXPECTED_LANGUAGE_COUNT} mapped languages, found {len(mappings)}."
        )

    print(f"Mapped languages: {len(mappings)}")
    print("Verifying ResourcePack/Fabric source-layer alignment...")
    source_payloads: dict[str, bytes] = {}

    for language, locale in sorted(mappings.items()):
        source_payloads[language] = verify_cross_layer_source_contract(language, locale)

    print("PASS: ResourcePack and Fabric source JSON content is aligned for every mapped language.")
    print(f"PASS: all mapped source payloads contain exactly {EXPECTED_KEY_COUNT} keys.")
    print("Building all per-language fallback resource packs twice in temporary directories...")

    with tempfile.TemporaryDirectory(prefix="treasurerun_fallback_build_a_") as first_tmp, \
         tempfile.TemporaryDirectory(prefix="treasurerun_fallback_build_b_") as second_tmp:
        first_dir = Path(first_tmp)
        second_dir = Path(second_tmp)

        build_into(first_dir)
        build_into(second_dir)

        results: list[tuple[str, str, int]] = []

        for language, locale in sorted(mappings.items()):
            first_zip = first_dir / f"treasurerun-i18n-pack-{language}.zip"
            second_zip = second_dir / f"treasurerun-i18n-pack-{language}.zip"

            first_entries, first_sha1 = inspect_generated_pack(
                first_zip,
                source_payloads[language],
            )
            second_entries, second_sha1 = inspect_generated_pack(
                second_zip,
                source_payloads[language],
            )

            if first_entries != second_entries:
                raise AssertionError(
                    f"{language}: alias file names differ between clean builds."
                )

            if first_zip.read_bytes() != second_zip.read_bytes():
                raise AssertionError(
                    f"{language}: ZIP bytes differ between clean builds."
                )

            if first_sha1 != second_sha1:
                raise AssertionError(
                    f"{language}: SHA-1 values differ between clean builds: "
                    f"{first_sha1} != {second_sha1}"
                )

            results.append((language, first_sha1, first_zip.stat().st_size))

    print("")
    print("=== reproducible per-language fallback resource pack verification ===")
    for language, digest, size in results:
        print(f"PASS {language:10s} bytes={size:9d} sha1={digest}")

    print("")
    print(f"PASS: verified {len(results)} reproducible per-language fallback resource pack builds.")
    print(f"PASS: every generated pack declares Minecraft 1.20.1 compatibility with pack_format {EXPECTED_PACK_FORMAT}.")
    print(f"PASS: every generated pack contains {EXPECTED_ALIAS_COUNT} uniform locale aliases.")
    print("PASS: every generated alias payload matches the ResourcePack source payload.")
    print("PASS: ResourcePack and Fabric source payloads remain semantically aligned.")
    print("PASS: two clean builds are byte-identical and SHA-1 reproducible in the pinned verification toolchain.")
    print("NOTE: this artifact-build check does not modify configured routing or establish vanilla-client in-game display evidence.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
