from pathlib import Path

LANG_DIR = Path("src/main/resources/languages")

def split_top_level_sections(lines):
    sections = []
    current_name = None
    current_lines = []
    for line in lines:
        if line and not line.startswith(" ") and line.endswith(":"):
            if current_name is not None:
                sections.append((current_name, current_lines))
            current_name = line[:-1]
            current_lines = [line]
        else:
            if current_name is None:
                current_name = "__preamble__"
                current_lines = []
            current_lines.append(line)
    if current_name is not None:
        sections.append((current_name, current_lines))
    return sections

def split_command_children(section_lines):
    """
    command: 配下の 2スペース indent の子ブロック単位で分割
    """
    header = section_lines[0]
    body = section_lines[1:]

    children = []
    current_key = None
    current_block = []

    for line in body:
        if line.startswith("  ") and not line.startswith("    ") and line.rstrip().endswith(":"):
            if current_key is not None:
                children.append((current_key, current_block))
            current_key = line.strip()[:-1]
            current_block = [line]
        else:
            if current_key is None:
                # command: 直下にぶら下がる予期しない行
                current_key = "__loose__"
                current_block = []
            current_block.append(line)

    if current_key is not None:
        children.append((current_key, current_block))

    return header, children

def merge_command_sections(command_sections):
    """
    後ろの command: で追加された子ブロックを first command にマージ
    同名 child は後勝ち
    """
    merged_order = []
    merged_map = {}

    for sec in command_sections:
        _, children = split_command_children(sec)
        for key, block in children:
            if key not in merged_map:
                merged_order.append(key)
            merged_map[key] = block

    out = ["command:"]
    for key in merged_order:
        out.extend(merged_map[key])
    return out

for path in sorted(LANG_DIR.glob("*.yml")):
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()

    sections = split_top_level_sections(lines)

    command_sections = [sec_lines for sec_name, sec_lines in sections if sec_name == "command"]
    if len(command_sections) <= 1:
        print(f"[SKIP] {path.name}: command section count = {len(command_sections)}")
        continue

    new_sections = []
    command_merged_done = False

    for sec_name, sec_lines in sections:
        if sec_name != "command":
            new_sections.append((sec_name, sec_lines))
            continue

        if not command_merged_done:
            merged = merge_command_sections(command_sections)
            new_sections.append(("command", merged))
            command_merged_done = True
        else:
            # duplicate command section は捨てる
            pass

    out_lines = []
    for i, (_, sec_lines) in enumerate(new_sections):
        if i > 0 and (len(out_lines) == 0 or out_lines[-1] != ""):
            out_lines.append("")
        out_lines.extend(sec_lines)

    path.write_text("\n".join(out_lines) + "\n", encoding="utf-8")
    print(f"[FIXED] {path.name}: merged {len(command_sections)} command sections")
