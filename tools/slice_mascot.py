"""Cut the kitty mascot spritesheets into app frames.

    python tools/slice_mascot.py art/kitty_walk.png art/kitty_sleep.png

Each sheet is one row of 8 frames. Pixels that are almost transparent (the sheets carry a faint
haze) or plain white are cleared, each frame is found by the empty columns around it (stray
sparkles join their nearest frame), and every frame of both sheets is placed on one canvas with
its feet on one baseline and its nose at one edge (the kitty faces right), so the walk doesn't
jitter. Writes app/src/main/res/drawable-nodpi/kitty_walk_0..7.png and kitty_sleep_0..7.png.
"""
import sys
from pathlib import Path
from PIL import Image

FRAMES = 8
TARGET_H = 120  # px; drawn at ~30dp, so crisp up to 4x screens
SOLID = 60      # alpha at or below this is haze, not kitty
OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi"


def clean(im):
    im = im.convert("RGBA")
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a <= SOLID or (r > 245 and g > 245 and b > 245):
                px[x, y] = (0, 0, 0, 0)
    return im


def frames(im):
    a = im.split()[3]
    w, h = im.size
    col = a.load()
    filled = [any(col[x, y] for y in range(0, h, 2)) for x in range(w)]
    runs, start = [], None
    for x, f in enumerate(filled + [False]):
        if f and start is None:
            start = x
        elif not f and start is not None:
            runs.append([start, x]); start = None
    # The 8 widest runs are the frames; anything smaller (a sparkle) joins the nearest one.
    main = sorted(sorted(runs, key=lambda r: r[1] - r[0], reverse=True)[:FRAMES])
    for r in runs:
        if r in main:
            continue
        near = min(main, key=lambda m: min(abs(r[0] - m[1]), abs(m[0] - r[1])))
        near[0], near[1] = min(near[0], r[0]), max(near[1], r[1])
    out = []
    for x0, x1 in main:
        crop = im.crop((x0, 0, x1, h))
        out.append(crop.crop(crop.getbbox()))
    return out


def main(walk_path, sleep_path):
    sheets = {"walk": frames(clean(Image.open(walk_path))), "sleep": frames(clean(Image.open(sleep_path)))}
    every = [f for fs in sheets.values() for f in fs]
    cw = max(f.width for f in every)
    ch = max(f.height for f in every)
    scale = TARGET_H / ch
    size = (round(cw * scale), TARGET_H)
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fs in sheets.items():
        for i, f in enumerate(fs):
            canvas = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
            canvas.paste(f, (cw - f.width, ch - f.height), f)  # nose right, feet down
            canvas.resize(size, Image.LANCZOS).save(OUT / f"kitty_{name}_{i}.png", optimize=True)
    print(f"wrote {sum(len(v) for v in sheets.values())} frames of {size[0]}x{size[1]} to {OUT}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
