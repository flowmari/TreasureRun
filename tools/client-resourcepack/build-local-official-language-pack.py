#!/usr/bin/env python3
"""Build local-only TreasureRun client packs from the operator's Minecraft 1.20.1 assets.

The generated ZIP files are local test artifacts. They must not be committed,
uploaded as Release assets, or published to distribution platforms.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
POLICY = ROOT / "resourcepacks/local-generator-policy.json"
FIXED_TIME = (1980, 1, 1, 0, 0, 0)
FIXED_MODE = 0o100644 << 16


def fail(message: str, code: int = 1) -> "NoReturn":
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(code)


def inside(child: Path, parent: Path) -> bool:
    try:
        child.resolve().relative_to(parent.resolve())
        return True
    except ValueError:
        return False


def zip_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, FIXED_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.create_system = 3
    info.external_attr = FIXED_MODE
    return info


def write_entry(zf: zipfile.ZipFile, name: str, data: bytes) -> None:
    zf.writestr(zip_info(name), data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=1)


def load_policy() -> dict:
    data = json.loads(POLICY.read_text(encoding="utf-8"))
    if data.get("distribution_status") != "LOCAL_ONLY_DO_NOT_PUBLISH":
        fail("local generator policy does not enforce LOCAL_ONLY_DO_NOT_PUBLISH")
    return data


def load_asset_context(mc_dir: Path, version: str) -> tuple[dict, Path, Path]:
    version_json = mc_dir / "versions" / version / f"{version}.json"
    client_jar = mc_dir / "versions" / version / f"{version}.jar"
    if not version_json.is_file() or not client_jar.is_file():
        fail(f"Minecraft {version} version JSON/client JAR is missing under {mc_dir}")
    version_data = json.loads(version_json.read_text(encoding="utf-8"))
    index_id = str(version_data.get("assetIndex", {}).get("id", "")).strip()
    if not index_id:
        fail(f"assetIndex.id is missing from {version_json}")
    index_path = mc_dir / "assets" / "indexes" / f"{index_id}.json"
    if not index_path.is_file():
        fail(f"Minecraft asset index is missing: {index_path}")
    index = json.loads(index_path.read_text(encoding="utf-8"))
    return index, client_jar, mc_dir / "assets" / "objects"


def official_locale_bytes(locale: str, index: dict, client_jar: Path, objects_dir: Path) -> bytes:
    objects = index.get("objects", {})
    keys = [
        f"minecraft/lang/{locale}.json",
        f"assets/minecraft/lang/{locale}.json",
    ]
    key = next((candidate for candidate in keys if candidate in objects), None)
    if key is None:
        suffix = f"/lang/{locale}.json"
        matches = sorted(name for name in objects if name.endswith(suffix))
        if len(matches) == 1:
            key = matches[0]
    raw: bytes | None = None
    if key is not None:
        digest = str(objects[key].get("hash", ""))
        object_path = objects_dir / digest[:2] / digest
        if object_path.is_file():
            raw = object_path.read_bytes()
    if raw is None:
        entry = f"assets/minecraft/lang/{locale}.json"
        with zipfile.ZipFile(client_jar) as jar:
            try:
                raw = jar.read(entry)
            except KeyError:
                fail(f"official Minecraft locale asset was not found locally: {locale}")
    parsed = json.loads(raw.decode("utf-8"))
    if not isinstance(parsed, dict) or not parsed:
        fail(f"official locale is not a non-empty JSON object: {locale}")
    return (json.dumps(parsed, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def build_one(language: str, locale: str, aliases: list[str], payload: bytes, output_dir: Path, version: str, pack_format: int) -> Path:
    output = output_dir / f"treasurerun-local-{language}-{version}.zip"
    metadata = {
        "pack": {
            "pack_format": pack_format,
            "description": f"TreasureRun local-only {language} pack for Minecraft {version}",
        }
    }
    marker = {
        "distribution_status": "LOCAL_ONLY_DO_NOT_PUBLISH",
        "language": language,
        "source_locale": locale,
        "minecraft_version": version,
        "payload_sha256": hashlib.sha256(payload).hexdigest(),
    }
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=1) as zf:
        write_entry(zf, "pack.mcmeta", (json.dumps(metadata, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8"))
        write_entry(zf, "treasurerun-local-only.json", (json.dumps(marker, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8"))
        for alias in aliases:
            write_entry(zf, f"assets/minecraft/lang/{alias}.json", payload)
    return output


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--language")
    group.add_argument("--all", action="store_true")
    parser.add_argument("--minecraft-dir", type=Path, default=Path.home() / "Library/Application Support/minecraft")
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    policy = load_policy()
    official = {str(k): str(v) for k, v in policy["official_mappings"].items()}
    held = {str(k): str(v) for k, v in policy["held_mappings"].items()}
    aliases = [str(value) for value in policy["alias_locales"]]
    version = str(policy["minecraft_version"])
    pack_format = int(policy["pack_format"])

    if inside(args.output_dir, ROOT):
        fail(f"output directory must be outside the repository: {args.output_dir}")
    args.output_dir.mkdir(parents=True, exist_ok=True)

    requested = sorted(official) if args.all else [str(args.language)]
    for language in requested:
        if language in held:
            print(
                f"HOLD: client pack generation is disabled for {language} -> {held[language]}; "
                "origin/distribution permission is unresolved and English fallback is forbidden.",
                file=sys.stderr,
            )
            return 2
        if language not in official:
            fail(f"unknown TreasureRun language: {language}")

    index, client_jar, objects_dir = load_asset_context(args.minecraft_dir, version)
    rows: list[list[str]] = []
    for language in requested:
        locale = official[language]
        payload = official_locale_bytes(locale, index, client_jar, objects_dir)
        output = build_one(language, locale, aliases, payload, args.output_dir, version, pack_format)
        rows.append([
            language,
            locale,
            str(len(json.loads(payload.decode("utf-8")))),
            str(len(aliases)),
            str(output.stat().st_size),
            hashlib.sha1(output.read_bytes()).hexdigest(),
            hashlib.sha256(output.read_bytes()).hexdigest(),
            "LOCAL_ONLY_DO_NOT_PUBLISH",
            output.name,
        ])
        print(f"PASS: built local-only pack {language} -> {locale}: {output}")

    manifest = args.output_dir / "local-pack-manifest.tsv"
    with manifest.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(["language", "source_locale", "payload_keys", "alias_count", "bytes", "sha1", "sha256", "distribution_status", "artifact"])
        writer.writerows(rows)
    print(f"PASS: generated {len(rows)} local-only pack(s).")
    print(f"Manifest: {manifest}")
    print("HOLD: generated packs are local-only and must not be published.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
