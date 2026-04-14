#!/usr/bin/env python3
from pathlib import Path
import sys

LANG_DIR = Path("src/main/resources/languages")

REQUIRED_KEYS = [
    ("command", "craftSpecialEmerald", "needDiamonds"),
    ("command", "craftSpecialEmerald", "success"),
    ("items", "specialEmerald", "displayName"),
("items", "specialEmerald", "loreCrafted"),
("items", "specialEmerald", "loreSpecial"),
]

def has_nested_key(lines, path):
    indent = 0
    start = 0
    for part in path:
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

def main():
    if not LANG_DIR.exists():
        print(f"NG: language dir not found: {LANG_DIR}")
        sys.exit(1)

    failed = False
    for f in sorted(LANG_DIR.glob("*.yml")):
        text = f.read_text(encoding="utf-8")
        lines = text.splitlines()
        missing = []
        for key_path in REQUIRED_KEYS:
            if not has_nested_key(lines, key_path):
                missing.append(".".join(key_path))

        if missing:
            failed = True
            print(f"NG: {f.name}")
            for m in missing:
                print(f"  missing: {m}")
        else:
            print(f"OK: {f.name}")

    if failed:
        sys.exit(1)

if __name__ == "__main__":
    main()
