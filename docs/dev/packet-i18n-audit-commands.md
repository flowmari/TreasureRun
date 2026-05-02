# PacketI18n Audit Commands

Use this guide when expanding TreasureRun's packet-level i18n coverage.

## 1. Enable runtime audit temporarily

This changes only the server-side runtime config inside the Docker container.

```bash
docker exec minecraft_spigot bash -lc '
python3 - <<PY
from pathlib import Path

path = Path("/data/plugins/TreasureRun/config.yml")
text = path.read_text(encoding="utf-8")

start = text.find("packetMessages:")
if start == -1:
    raise SystemExit("packetMessages block not found")

lines = text[start:].splitlines(keepends=True)
for i, line in enumerate(lines[1:], start=1):
    if line and not line.startswith((" ", "\t", "#", "\n", "\r")) and ":" in line:
        break
else:
    i = len(lines)

new_block = """packetMessages:
  enabled: true
  audit: true
  auditAllJson: true
  debug: true
  replaceTranslatedComponents: false
"""

text = text[:start] + new_block + text[start + len("".join(lines[:i])):]
path.write_text(text, encoding="utf-8")
print("server packetMessages audit enabled")
PY
'

docker restart minecraft_spigot
```

## 2. Trigger runtime events

In Minecraft:

```text
/lang ojp
send normal chat
disconnect
join again
/gameStart easy
open chest
die once if needed
trigger advancement if needed
```

## 3. Check audit logs

```bash
docker logs --tail 2000 minecraft_spigot \
  | grep -Ei "PacketI18n|translate=|yaml=minecraft.packet|json="
```

## 4. Extract possible Minecraft translate keys

```bash
docker logs --tail 5000 minecraft_spigot \
  | grep -oE '"translate"[[:space:]]*:[[:space:]]*"[^"]+"' \
  | sed -E 's/.*"translate"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/' \
  | sort -u
```

## 5. Disable audit after verification

```bash
docker exec minecraft_spigot bash -lc '
python3 - <<PY
from pathlib import Path

path = Path("/data/plugins/TreasureRun/config.yml")
text = path.read_text(encoding="utf-8")

start = text.find("packetMessages:")
if start == -1:
    raise SystemExit("packetMessages block not found")

lines = text[start:].splitlines(keepends=True)
for i, line in enumerate(lines[1:], start=1):
    if line and not line.startswith((" ", "\t", "#", "\n", "\r")) and ":" in line:
        break
else:
    i = len(lines)

new_block = """packetMessages:
  enabled: true
  audit: false
  auditAllJson: false
  debug: false
  replaceTranslatedComponents: false
"""

text = text[:start] + new_block + text[start + len("".join(lines[:i])):]
path.write_text(text, encoding="utf-8")
print("server packetMessages audit disabled")
PY
'

docker restart minecraft_spigot
```

## Policy

Do not keep packet audit enabled during normal gameplay.

Audit logs are useful for verification but can become noisy.
