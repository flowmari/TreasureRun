#!/usr/bin/env python3
"""Compatibility entry point for the V4 local-only ResourcePack contract check."""
from __future__ import annotations
import subprocess
import sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "scripts/check_local_resourcepack_contract.py"
print("NOTICE: the old publishable fallback-pack contract is retired; checking V4 local-only boundaries.")
raise SystemExit(subprocess.call([sys.executable, str(TARGET), *sys.argv[1:]], cwd=ROOT))
