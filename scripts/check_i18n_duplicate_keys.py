#!/usr/bin/env python3
from pathlib import Path
import sys
import yaml

LANG_DIR = Path("src/main/resources/languages")

class UniqueKeyLoader(yaml.SafeLoader):
    pass

def construct_mapping(loader, node, deep=False):
    mapping = {}
    duplicates = []
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in mapping:
            duplicates.append(key)
        value = loader.construct_object(value_node, deep=deep)
        mapping[key] = value
    if duplicates:
        raise yaml.YAMLError(f"duplicate keys detected: {duplicates}")
    return mapping

UniqueKeyLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG,
    construct_mapping
)

def main():
    if not LANG_DIR.exists():
        print(f"NG: language dir not found: {LANG_DIR}")
        sys.exit(1)

    failed = False
    for f in sorted(LANG_DIR.glob("*.yml")):
        try:
            with f.open("r", encoding="utf-8") as fp:
                yaml.load(fp, Loader=UniqueKeyLoader)
            print(f"OK: {f.name}")
        except Exception as e:
            failed = True
            print(f"NG: {f.name}")
            print(f"  {e}")

    if failed:
        sys.exit(1)

if __name__ == "__main__":
    main()
