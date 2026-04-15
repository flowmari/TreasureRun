#!/usr/bin/env python3
from pathlib import Path
import re
import sys

JAVA_DIR = Path("src/main/java")
LANG_DIR = Path("src/main/resources/languages")

# Java 側で拾いたいパターン
PATTERNS = [
    re.compile(r'i18n\.tr\s*\(\s*player\s*,\s*"([^"]+)"'),
    re.compile(r'i18n\.tr\s*\(\s*"([^"]+)"'),
    re.compile(r'i18n\.trDefault\s*\(\s*"([^"]+)"'),
    re.compile(r'\btr\s*\(\s*player\s*,\s*"([^"]+)"'),
    re.compile(r'\btr\s*\(\s*"([^"]+)"'),
]

def has_nested_key(lines, path_parts):
    indent = 0
    start = 0
    for part in path_parts:
        found = False
        target = "  " * indent + f"{part}:"
        for i in range(start, len(lines)):
            if lines[i].startswith(target):
                found = True
                start = i + 1
                indent += 1
                break
        if not found:
            return False
    return True

def collect_java_keys():
    keys = {}
    for f in sorted(JAVA_DIR.rglob("*.java")):
        text = f.read_text(encoding="utf-8")
        found = []
        for pat in PATTERNS:
            found.extend(pat.findall(text))
        if found:
            keys[f] = sorted(set(found))
    return keys

def main():
    if not JAVA_DIR.exists():
        print(f"NG: Java dir not found: {JAVA_DIR}")
        sys.exit(1)
    if not LANG_DIR.exists():
        print(f"NG: language dir not found: {LANG_DIR}")
        sys.exit(1)

    java_keys = collect_java_keys()
    all_keys = sorted({k for ks in java_keys.values() for k in ks})

    if not all_keys:
        print("INFO: no i18n keys referenced in Java.")
        return

    failed = False

    for lang_file in sorted(LANG_DIR.glob("*.yml")):
        lines = lang_file.read_text(encoding="utf-8").splitlines()
        missing = []

        for key in all_keys:
            parts = tuple(key.split("."))
            if not has_nested_key(lines, parts):
                missing.append(key)

        if missing:
            failed = True
            print(f"NG: {lang_file.name}")
            for m in missing:
                print(f"  missing: {m}")
        else:
            print(f"OK: {lang_file.name}")

    print()
    print("===== referenced key inventory =====")
    for src, ks in java_keys.items():
        print(f"[{src}]")
        for k in ks:
            print(f"  {k}")

    if failed:
        sys.exit(1)

if __name__ == "__main__":
    main()
