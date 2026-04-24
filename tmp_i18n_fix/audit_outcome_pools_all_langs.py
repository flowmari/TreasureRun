from pathlib import Path
import re

langs_dir = Path("src/main/resources/languages")
out = Path("docs/i18n_native_review/outcome_pool_audit.tsv")

expected = {
    "success.easy": 17,
    "success.normal": 35,
    "success.hard": 32,
    "timeup.easy": 16,
    "timeup.normal": 20,
    "timeup.hard": 38,
}

def count_pool(lines, start_idx, base_indent):
    n = 0
    i = start_idx + 1
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        indent = len(line) - len(line.lstrip(" "))
        if indent <= base_indent:
            break
        s = line.strip()
        if s.startswith("- "):
            n += 1
        i += 1
    return n

def find_count(text, path_parts):
    lines = text.splitlines()
    idx = 0
    current_indent = -1
    for part in path_parts:
        found = None
        pattern = re.compile(rf'^(\s*){re.escape(part)}:\s*(.*)$')
        for i in range(idx, len(lines)):
            m = pattern.match(lines[i])
            if not m:
                continue
            indent = len(m.group(1))
            if indent > current_indent:
                found = (i, indent)
                break
        if found is None:
            return -1
        idx, current_indent = found

    return count_pool(lines, idx, current_indent)

rows = []
for f in sorted(langs_dir.glob("*.yml")):
    text = f.read_text(encoding="utf-8")
    lang = f.stem
    for key, exp in expected.items():
        parts = ["outcome"] + key.split(".") + ["pool"]
        count = find_count(text, parts)
        status = "OK" if count == exp else ("MISSING" if count < 0 else "NG")
        rows.append((lang, key, exp, count, status))

with out.open("w", encoding="utf-8") as w:
    w.write("lang\tkey\texpected\tactual\tstatus\n")
    for r in rows:
        w.write("\t".join(map(str, r)) + "\n")

print(f"WROTE: {out}")
