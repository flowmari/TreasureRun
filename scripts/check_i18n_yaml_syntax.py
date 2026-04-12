#!/usr/bin/env python3
from pathlib import Path
import sys
import yaml

LANG_DIR = Path("src/main/resources/languages")

def main():
    if not LANG_DIR.exists():
        print(f"NG: language dir not found: {LANG_DIR}")
        sys.exit(1)

    failed = False
    for f in sorted(LANG_DIR.glob("*.yml")):
        try:
            with f.open("r", encoding="utf-8") as fp:
                yaml.safe_load(fp)
            print(f"OK: {f.name}")
        except Exception as e:
            failed = True
            print(f"NG: {f.name}")
            print(f"  {e}")

    if failed:
        sys.exit(1)

if __name__ == "__main__":
    main()
