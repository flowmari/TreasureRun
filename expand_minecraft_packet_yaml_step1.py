#!/usr/bin/env python3
import json, re, sys
from pathlib import Path
CWD = Path.cwd()
LANG_JSON_DIR = CWD / "resourcepacks/treasurerun-i18n-pack/assets/minecraft/lang"
YAML_DIR = CWD / "src/main/resources/languages"
if not LANG_JSON_DIR.exists():
    print(f"ERROR: {LANG_JSON_DIR} not found.\nRun from TreasureRun-main/"); sys.exit(1)
YAML_TO_JSON = {
    "en":"en_us","ja":"ja_jp","de":"de_de","fr":"fr_fr","es":"es_es",
    "it":"it_it","ko":"ko_kr","ru":"ru_ru","zh_tw":"zh_tw","pt":"pt_br",
    "nl":"nl_nl","fi":"fi_fi","sv":"sv_se","is":"is_is","la":"la_la",
    "hi":"hi_in","sa":"sa_in","lzh":"lzh_hant","ojp":"ojp_jp","asl_gloss":"asl_us",
}
SERVER_SENDABLE_CATS = {
    "entity","effect","enchantment","gamerule","biome","stat","color","attribute",
    "potion","soundCategory","stat_type","merchant","team","menu","title",
    "translation","arguments","parsing","filled_map","chat_screen","recipe",
    "resourcepack","advancement","build","item_modifier","lectern","mount",
    "particle","predicate","slot","stats",
}
ALREADY_IN = {"chat","command","commands","death","multiplayer","advancements","argument","connect","disconnect","resourcePack"}
def load_json(lang):
    jl = YAML_TO_JSON.get(lang)
    if not jl: return {}
    p = LANG_JSON_DIR / f"{jl}.json"
    return json.loads(p.read_text(encoding="utf-8")) if p.exists() else {}
def yaml_escape(s):
    s = str(s) if s else ""
    if not s: return "''"
    if any(c in s for c in ':#{[]}|>&*!%@`\n\r') or s.startswith(("'",'"','-')):
        return '"' + s.replace('\\','\\\\').replace('"','\\"').replace('\n','\\n').replace('\r','') + '"'
    return s
def build_nested(kv):
    root = {}
    for k in sorted(kv):
        v = kv[k]; parts = k.split("."); node = root
        for part in parts[:-1]:
            ex = node.get(part)
            if ex is None: node[part] = {}; node = node[part]
            elif isinstance(ex, dict): node = ex
            else: node[part] = {"_value": ex}; node = node[part]
        leaf = parts[-1]; ex2 = node.get(leaf)
        if ex2 is None: node[leaf] = v
        elif isinstance(ex2, dict): ex2.setdefault("_value", v)
    return root
def to_lines(d, indent=0):
    out = []; p = "  " * indent
    for k, v in sorted(d.items()):
        if isinstance(v, dict):
            lv = v.get("_value"); sub = {sk:sv for sk,sv in v.items() if sk != "_value"}
            if lv is not None and not sub: out.append(f"{p}{k}: {yaml_escape(lv)}")
            elif lv is not None:
                out.append(f"{p}{k}:"); out.append(f"  {p}_value: {yaml_escape(lv)}")
                out.extend(to_lines(sub, indent+1))
            else: out.append(f"{p}{k}:"); out.extend(to_lines(v, indent+1))
        else: out.append(f"{p}{k}: {yaml_escape(v)}")
    return out
def existing_cats(content):
    cats = set(); in_mc = in_pkt = False
    for line in content.split("\n"):
        if re.match(r'^minecraft:', line): in_mc = True; in_pkt = False
        elif in_mc and re.match(r'^  packet:', line): in_pkt = True
        elif in_pkt:
            m = re.match(r'^    (\w+):', line)
            if m: cats.add(m.group(1))
        elif re.match(r'^\S', line) and line.strip() and not re.match(r'^minecraft:', line): in_mc = in_pkt = False
    return cats
def pkt_end(lines):
    in_mc = in_pkt = False
    for i, ln in enumerate(lines):
        if re.match(r'^minecraft:', ln): in_mc = True; in_pkt = False
        elif in_mc and re.match(r'^  packet:', ln): in_pkt = True
        elif in_pkt:
            if re.match(r'^\S', ln) and ln.strip(): return i
            if re.match(r'^  \S', ln) and not ln.startswith("    "): return i
        elif in_mc and not in_pkt and re.match(r'^\S', ln) and ln.strip(): in_mc = False
    return len(lines)
def process(path, lang):
    jd = load_json(lang) or load_json("en")
    missing = {k: str(v) for k,v in jd.items()
               if k.split(".")[0] in SERVER_SENDABLE_CATS and k.split(".")[0] not in ALREADY_IN}
    content = path.read_text(encoding="utf-8")
    ec = existing_cats(content)
    new_kv = {k:v for k,v in missing.items() if k.split(".")[0] not in ec}
    if not new_kv: print(f"  [{lang}] already up-to-date"); return 0
    nested = build_nested(new_kv)
    blk = []
    for cat in sorted(nested): blk.append(f"    {cat}:"); blk.extend(to_lines(nested[cat], 3))
    lines = content.split("\n"); pe = pkt_end(lines)
    out = "\n".join(lines[:pe] + blk + lines[pe:])
    out = re.sub(r'^(\s+\w+:\s+)-\s*$', r'\1"-"', out, flags=re.MULTILINE)
    out = out.replace('\x7f', '')
    path.write_text(out, encoding="utf-8")
    print(f"  [{lang}] +{len(new_kv)} keys")
    return len(new_kv)
total = 0
for p in sorted(YAML_DIR.glob("*.yml")):
    total += process(p, p.stem)
print(f"\nStep 1 complete. Total keys added: {total}")
