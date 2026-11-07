#!/usr/bin/env python3
"""
Regenerate the bundled serif font subset so the launcher can render Chinese app
names in a serif face.

Android's built-in `serif` family only carries Latin glyphs; Chinese characters
fall back to the sans CJK font, which is why the launcher ships its own subset.

    tools/make-font-subset.py \
        --names "夸克网盘,云视听小电视,乐播投屏,应用圈,华数鲜时光,芒果TV,网易云音乐,HDMI2,Spotify" \
        --out launcher/app/src/main/assets/app_name_serif.ttf

Requires: pip install fonttools
The source font can be any CJK serif. Defaults to macOS Songti SC; set
SOURCE_FONT (or --source) to e.g. /usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc
on Linux.
"""
import argparse
import os
import sys

try:
    from fontTools.ttLib import TTFont, TTCollection
    from fontTools import subset
except ImportError:
    sys.exit("fontTools is required:  pip install fonttools")

DEFAULT_SOURCE = "/System/Library/Fonts/Supplemental/Songti.ttc"
DEFAULT_FACE_INDEX = 1          # Songti SC Bold inside the collection


def load_face(path, index):
    if path.lower().endswith(".ttc"):
        collection = TTCollection(path)
        if index >= len(collection.fonts):
            sys.exit(f"{path} has {len(collection.fonts)} faces, index {index} is out of range")
        return collection.fonts[index]
    return TTFont(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--names", required=True,
                    help="comma separated list of the app names shown on screen")
    ap.add_argument("--out", required=True, help="output .ttf path")
    ap.add_argument("--source", default=os.environ.get("SOURCE_FONT", DEFAULT_SOURCE))
    ap.add_argument("--face-index", type=int, default=DEFAULT_FACE_INDEX)
    ap.add_argument("--extra", default="",
                    help="extra characters to include (e.g. punctuation)")
    args = ap.parse_args()

    if not os.path.exists(args.source):
        sys.exit(f"source font not found: {args.source}\n"
                 f"pass --source /path/to/a/CJK-serif.ttf")

    labels = [n.strip() for n in args.names.split(",") if n.strip()]
    chars = set("".join(labels))
    chars.update(chr(c) for c in range(0x20, 0x7F))     # ASCII for Latin names
    chars.update("·•—–")
    chars.update(args.extra)

    cjk = "".join(sorted(c for c in chars if ord(c) > 0x2E80))
    print(f"labels : {', '.join(labels)}")
    print(f"glyphs : {len(chars)} total, {len(cjk)} CJK -> {cjk}")

    font = load_face(args.source, args.face_index)
    opts = subset.Options()
    opts.name_IDs = ["*"]
    opts.name_legacy = True
    opts.notdef_outline = True
    opts.drop_tables += ["DSIG"]

    sub = subset.Subsetter(options=opts)
    sub.populate(text="".join(sorted(chars)))
    sub.subset(font)

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    font.save(args.out)
    size = os.path.getsize(args.out) / 1024
    print(f"saved  : {args.out}  ({size:.0f} KB)")

    # Verify coverage so a missing glyph can't slip through unnoticed.
    cmap = TTFont(args.out).getBestCmap()
    missing = [c for c in cjk if ord(c) not in cmap]
    if missing:
        sys.exit(f"NOT COVERED: {''.join(missing)}")
    print("verify : all CJK glyphs covered")


if __name__ == "__main__":
    main()
