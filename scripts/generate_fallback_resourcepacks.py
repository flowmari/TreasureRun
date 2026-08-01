#!/usr/bin/env python3
"""Compatibility entry point for the V4 local-only ResourcePack generator."""
from __future__ import annotations
import subprocess
import sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "tools/client-resourcepack/build-local-official-language-pack.py"
print("NOTICE: public fallback-pack generation is retired; using the local-only V4 generator.")
raise SystemExit(subprocess.call([sys.executable, str(TARGET), *sys.argv[1:]], cwd=ROOT))
