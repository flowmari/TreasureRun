#!/usr/bin/env python3
"""Build and verify TreasureRun's shared ResourcePack deterministically."""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import tempfile
from pathlib import Path
from zipfile import ZIP_STORED, ZipFile, ZipInfo

ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "resourcepacks" / "treasurerun-i18n-pack"
OUTPUT_DIR = ROOT / "resourcepacks" / "generated"
ZIP_NAME = "treasurerun-i18n-pack.zip"
TRACKED_ZIP = OUTPUT_DIR / ZIP_NAME
SHA1_FILE = OUTPUT_DIR / f"{ZIP_NAME}.sha1"
SHA256_FILE = OUTPUT_DIR / f"{ZIP_NAME}.sha256"
CONFIG_FILE = ROOT / "src" / "main" / "resources" / "config.yml"
FIXED_TIMESTAMP = (1980, 1, 1, 0, 0, 0)
FILE_MODE = 0o100644


def source_files() -> list[Path]:
    files: list[Path] = []
    for path in SOURCE_DIR.rglob("*"):
        if path.is_symlink():
            raise SystemExit(f"Symlinks are not allowed in the shared ResourcePack: {path}")
        if path.is_file():
            files.append(path)
    if not files:
        raise SystemExit(f"No ResourcePack source files found in {SOURCE_DIR}")
    return sorted(files, key=lambda path: path.relative_to(SOURCE_DIR).as_posix())


def build_zip(target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(target, "w", compression=ZIP_STORED, strict_timestamps=True) as archive:
        for path in source_files():
            relative = path.relative_to(SOURCE_DIR).as_posix()
            info = ZipInfo(relative, FIXED_TIMESTAMP)
            info.create_system = 3
            info.external_attr = FILE_MODE << 16
            info.compress_type = ZIP_STORED
            archive.writestr(info, path.read_bytes())


def digest(path: Path, algorithm: str) -> str:
    value = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def checksum_value(path: Path, expected_name: str, length: int) -> str:
    if not path.is_file():
        raise SystemExit(f"Missing checksum file: {path}")
    parts = path.read_text(encoding="utf-8").strip().split()
    if len(parts) != 2 or parts[1] != expected_name:
        raise SystemExit(f"Invalid checksum file format: {path}")
    value = parts[0].lower()
    if not re.fullmatch(rf"[0-9a-f]{{{length}}}", value):
        raise SystemExit(f"Invalid checksum value: {path}")
    return value


def resourcepack_blocks(config: str) -> tuple[str, str]:
    standard = re.search(
        r"(?ms)^resourcePack:\n.*?(?=^resourcePackFallback:|\Z)", config
    )
    fallback = re.search(
        r"(?ms)^resourcePackFallback:\n.*?(?=^[^\s]|\Z)", config
    )
    if standard is None or fallback is None:
        raise SystemExit("ResourcePack configuration blocks are missing.")
    return standard.group(0), fallback.group(0)


def scalar(block: str, key: str) -> str:
    match = re.search(
        rf"(?m)^  {re.escape(key)}:\s*[\"']?([^\"'\s]*)[\"']?\s*$", block
    )
    if match is None:
        raise SystemExit(f"Missing ResourcePack configuration key: {key}")
    return match.group(1)


def update_config_sha1(sha1: str) -> None:
    config = CONFIG_FILE.read_text(encoding="utf-8")
    standard, _ = resourcepack_blocks(config)
    updated_standard, count = re.subn(
        r"(?m)^  sha1:\s*[\"']?[0-9a-fA-F]{40}[\"']?\s*$",
        f"  sha1: {sha1}",
        standard,
        count=1,
    )
    if count != 1:
        raise SystemExit("Expected exactly one top-level resourcePack.sha1 value.")
    updated = config.replace(standard, updated_standard, 1)
    CONFIG_FILE.write_text(updated, encoding="utf-8")


def verify_configuration(expected_sha1: str) -> None:
    config = CONFIG_FILE.read_text(encoding="utf-8")
    standard, fallback = resourcepack_blocks(config)

    standard_enabled = scalar(standard, "enabled").lower() == "true"
    fallback_enabled = scalar(fallback, "enabled").lower() == "true"
    force = scalar(standard, "force").lower()
    url = scalar(standard, "url")
    configured_sha1 = scalar(standard, "sha1").lower()

    if standard_enabled and fallback_enabled:
        raise SystemExit("Standard and fallback ResourcePack delivery cannot both be enabled.")
    if force != "false":
        raise SystemExit("Standard ResourcePack delivery must use force: false.")
    if fallback_enabled:
        raise SystemExit("Fallback ResourcePack delivery must be disabled by default.")
    if "raw.githubusercontent.com" in url or "/main/" in url:
        raise SystemExit("Mutable main-branch ResourcePack URLs are not allowed.")
    if standard_enabled:
        versioned = re.fullmatch(
            r"https://github\.com/flowmari/TreasureRun/releases/download/"
            r"v[^/]+/treasurerun-i18n-pack\.zip",
            url,
        )
        if versioned is None:
            raise SystemExit(
                "An enabled standard ResourcePack must use an immutable versioned Release asset URL."
            )
    elif url:
        raise SystemExit(
            "The pre-release default must keep resourcePack.url empty while delivery is disabled."
        )
    if configured_sha1 != expected_sha1:
        raise SystemExit("config.yml ResourcePack SHA-1 does not match the tracked artifact.")


def write_artifacts() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="treasurerun-resourcepack-") as directory:
        candidate = Path(directory) / ZIP_NAME
        build_zip(candidate)
        os.replace(candidate, TRACKED_ZIP)

    sha1 = digest(TRACKED_ZIP, "sha1")
    sha256 = digest(TRACKED_ZIP, "sha256")
    SHA1_FILE.write_text(f"{sha1}  {ZIP_NAME}\n", encoding="utf-8")
    SHA256_FILE.write_text(f"{sha256}  {ZIP_NAME}\n", encoding="utf-8")
    update_config_sha1(sha1)

    print(f"Updated: {TRACKED_ZIP.relative_to(ROOT)}")
    print(f"SHA-1:   {sha1}")
    print(f"SHA-256: {sha256}")


def verify_artifacts() -> None:
    if not TRACKED_ZIP.is_file():
        raise SystemExit(f"Missing tracked ResourcePack: {TRACKED_ZIP}")

    with tempfile.TemporaryDirectory(prefix="treasurerun-resourcepack-check-") as directory:
        first = Path(directory) / "first.zip"
        second = Path(directory) / "second.zip"
        build_zip(first)
        build_zip(second)
        if first.read_bytes() != second.read_bytes():
            raise SystemExit("Two builds from the same input produced different ZIP bytes.")
        if first.read_bytes() != TRACKED_ZIP.read_bytes():
            raise SystemExit(
                "Tracked ResourcePack differs from the deterministic build. "
                "Run: python3 scripts/build_shared_resourcepack.py --write"
            )

    sha1 = digest(TRACKED_ZIP, "sha1")
    sha256 = digest(TRACKED_ZIP, "sha256")
    if checksum_value(SHA1_FILE, ZIP_NAME, 40) != sha1:
        raise SystemExit("Tracked ResourcePack SHA-1 does not match the artifact.")
    if checksum_value(SHA256_FILE, ZIP_NAME, 64) != sha256:
        raise SystemExit("Tracked ResourcePack SHA-256 does not match the artifact.")

    verify_configuration(sha1)
    print("Shared ResourcePack artifact contract verified.")


def main() -> None:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true")
    mode.add_argument("--check", action="store_true")
    args = parser.parse_args()

    if args.write:
        write_artifacts()
        verify_artifacts()
    else:
        verify_artifacts()


if __name__ == "__main__":
    main()
