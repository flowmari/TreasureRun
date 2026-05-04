from pathlib import Path
import sys

lang_dir = Path("src/main/resources/languages")

# 非英語ファイルで「表示残骸」として検出したいものだけに絞る
# "Favorites" は正規の翻訳未完・別用途文言・内部説明でも出やすく誤検知しやすいので外す
non_en_patterns = [
    "Quote Collection",
    "★Latest",
    "Language / 言語",
    "Easy（ゆったり）",
    "Normal（標準）",
    "Hard（高難度）",
    # "Contents" is intentionally NOT checked as a bare substring here.
    # Reason:
    #   Minecraft standard-message keys may contain safe internal names such as
    #   minecraft.packet.container.unknownContents.
    # A bare "Contents" substring check creates false positives after importing
    # Mojang official language assets into TreasureRun's 20-language i18n set.
    "How to play",
    "Score & route",
]

# en.yml は英語UIが正なので、日本語旧残骸だけを見る
en_patterns = [
    "格言集（",
    "お気に入り（",
    "Easy（ゆったり）",
    "Normal（標準）",
    "Hard（高難度）",
    "（この本を右クリックすると ★Latest を保存できます）",
]

bad = False

for p in sorted(lang_dir.glob("*.yml")):
    text = p.read_text(encoding="utf-8")

    patterns = en_patterns if p.name == "en.yml" else non_en_patterns
    hits = [s for s in patterns if s in text]

    print(f"== {p.name} ==")
    if hits:
        bad = True
        print("suspicious display remnants:")
        for h in hits:
            print("  -", h)
    else:
        print("OK")
    print()

if bad:
    sys.exit(1)

print("OK: no suspicious GameMenu display remnants found")
