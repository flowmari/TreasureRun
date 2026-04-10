from pathlib import Path
import sys
import yaml

LANG_DIR = Path("src/main/resources/languages")

class UniqueKeyLoader(yaml.SafeLoader):
    pass

def construct_mapping(loader, node, deep=False):
    mapping = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in mapping:
            raise yaml.constructor.ConstructorError(
                "while constructing a mapping",
                node.start_mark,
                f"found duplicate key: {key}",
                key_node.start_mark,
            )
        mapping[key] = loader.construct_object(value_node, deep=deep)
    return mapping

UniqueKeyLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG,
    construct_mapping
)

failed = False

for path in sorted(LANG_DIR.glob("*.yml")):
    print(f"== {path.name} ==")
    try:
        yaml.load(path.read_text(encoding="utf-8"), Loader=UniqueKeyLoader)
        print("OK\n")
    except Exception as e:
        failed = True
        print(f"NG: {e}\n")

if failed:
    print("ERROR: duplicate YAML keys detected")
    sys.exit(1)

print("OK: no duplicate YAML keys found")
