#!/usr/bin/env python3
from pathlib import Path
import re
import sys

LANG_DIR = Path("src/main/resources/languages")

# 例: playerscore_b91d4cd3: みたいな、ハッシュ付き暫定キーを検出
SUSPECT_RE = re.compile(r'^\s{2,}[a-z0-9_]+_[0-9a-f]{8}:\s*', re.MULTILINE)

def main():
    if not LANG_DIR.exists():
        print(f"NG: language dir not found: {LANG_DIR}")
        sys.exit(1)

    failed = False
    for f in sorted(LANG_DIR.glob("*.yml")):
        text = f.read_text(encoding="utf-8")
        matches = []
        for i, line in enumerate(text.splitlines(), start=1):
            if SUSPECT_RE.match(line):
                matches.append((i, line))

        if matches:
            failed = True
            print(f"NG: {f.name}")
            for line_no, line in matches[:100]:
                print(f"  L{line_no}: {line}")
        else:
            print(f"OK: {f.name}")

    if failed:
        sys.exit(1)

if __name__ == "__main__":
    main()
