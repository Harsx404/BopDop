"""Cut a mascot's spritesheets into app frames.

    python tools/slice_mascot.py kitty art/kitty_walk.png art/kitty_sleep.png
    python tools/slice_mascot.py bunny art/bunny_b.png art/bunny_a.png --keep-height
    python tools/slice_mascot.py bike art/bike.png --rows 4 5
    python tools/slice_mascot.py wagon art/wagon.png --rows 1 2
    python tools/slice_mascot.py dino art/dino.png --rows 1 2 --counts 8 6
    python tools/slice_mascot.py villager art/villager.png --rows 1 4 --sleep-frames 8

With --rows, one sheet holds several rows of animations (frame counts may differ): the first
number is the move row, the second the sleep row (1-based, top to bottom).

Each sheet is one row of 8 frames: the first is the move cycle (walk, hop), the second the sleep.
Pixels that are almost transparent (the sheets carry a faint haze) or plain white are cleared,
each frame is found by the empty columns around it (stray sparkles join their nearest frame), and
every frame is placed on one canvas with its nose at one edge (the mascots face right). Feet sit on
one baseline, except with --keep-height, where the move frames keep their height from the sheet
(a hop leaves the ground). Writes app/src/main/res/drawable-nodpi/<name>_walk_0..7.png and
<name>_sleep_0..7.png.
"""
import sys
from pathlib import Path
from PIL import Image

FRAMES = 8
TARGET_H = 120  # px; drawn at ~30dp, so crisp up to 4x screens
SOLID = 60      # alpha at or below this is haze, not mascot
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


def bands(im):
    """The sheet's rows as (top, bottom). Thin bands (text labels like "Walking (8 frames)") are
    dropped; a band several rows tall (rows that touch) is split at its emptiest lines."""
    a = im.split()[3].load()
    w, h = im.size
    filled = [any(a[x, y] for x in range(0, w, 3)) for y in range(h)]
    out, start = [], None
    for y, f in enumerate(filled + [False]):
        if f and start is None:
            start = y
        elif not f and start is not None:
            out.append((start, y)); start = None
    out = [(t, b) for t, b in out if b - t >= 70]
    typical = min(b - t for t, b in out)
    ink = lambda y: sum(1 for x in range(w) if a[x, y])
    rows = []
    for t, b in out:
        k = max(1, round((b - t) / typical))
        cuts = [t]
        for j in range(1, k):
            guess = t + (b - t) * j // k
            cuts.append(min(range(guess - typical // 3, guess + typical // 3), key=ink))
        cuts.append(b)
        rows += list(zip(cuts, cuts[1:]))
    return rows


def trim_slivers(im):
    """A frame cut out of touching neighbours keeps a sliver of each (a tail tip at the edge):
    small pieces that touch the left or right edge are cleared."""
    from collections import deque
    px = im.load()
    w, h = im.size
    seen = [[False] * h for _ in range(w)]
    total = sum(1 for x in range(w) for y in range(h) if px[x, y][3])
    for sx in (0, w - 1):
        for sy in range(h):
            if seen[sx][sy] or not px[sx, sy][3]:
                continue
            blob, q = [], deque([(sx, sy)])
            seen[sx][sy] = True
            while q:
                x, y = q.popleft(); blob.append((x, y))
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= nx < w and 0 <= ny < h and not seen[nx][ny] and px[nx, ny][3]:
                        seen[nx][ny] = True; q.append((nx, ny))
            if len(blob) < 0.04 * total:
                for x, y in blob:
                    px[x, y] = (0, 0, 0, 0)
    return im


def frames_by_shape(im, want):
    """Frames that overlap in columns (a tail reaching under the next frame) but don't touch:
    each of the [want] biggest connected shapes is a frame; smaller shapes (sparkles, "z z")
    join the frame whose box is nearest. Each frame keeps only its own pixels."""
    from collections import deque
    px = im.load()
    w, h = im.size
    label = [[-1] * h for _ in range(w)]
    blobs = []
    for sx in range(w):
        for sy in range(h):
            if label[sx][sy] >= 0 or not px[sx, sy][3]:
                continue
            pts, q = [], deque([(sx, sy)])
            label[sx][sy] = len(blobs)
            while q:
                x, y = q.popleft(); pts.append((x, y))
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1), (x + 1, y + 1), (x - 1, y - 1), (x + 1, y - 1), (x - 1, y + 1)):
                    if 0 <= nx < w and 0 <= ny < h and label[nx][ny] < 0 and px[nx, ny][3]:
                        label[nx][ny] = len(blobs); q.append((nx, ny))
            blobs.append(pts)
    order = sorted(range(len(blobs)), key=lambda i: len(blobs[i]), reverse=True)
    big = sorted(order[:want], key=lambda i: min(x for x, _ in blobs[i]))
    box = lambda i: (min(x for x, _ in blobs[i]), min(y for _, y in blobs[i]), max(x for x, _ in blobs[i]), max(y for _, y in blobs[i]))
    groups = {i: list(blobs[i]) for i in big}
    for i in order[want:]:
        bx = box(i); cx = (bx[0] + bx[2]) / 2; cy = (bx[1] + bx[3]) / 2
        def dist(j):
            b = box(j)
            dx = max(b[0] - cx, 0, cx - b[2]); dy = max(b[1] - cy, 0, cy - b[3])
            return dx * dx + dy * dy
        groups[min(big, key=dist)] += blobs[i]
    out = []
    for i in big:
        pts = groups[i]
        x0 = min(x for x, _ in pts); y0 = min(y for _, y in pts); x1 = max(x for x, _ in pts); y1 = max(y for _, y in pts)
        f = Image.new("RGBA", (x1 - x0 + 1, y1 - y0 + 1), (0, 0, 0, 0))
        fp = f.load()
        for x, y in pts:
            fp[x - x0, y - y0] = px[x, y]
        out.append((f, y0))
    return out


def frames(im, count=FRAMES, want=None):
    """The frames as (image, top y in the sheet). [count] None: as many as the row holds."""
    if want:
        return frames_by_shape(im, want)
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
    # The widest runs are the frames; anything smaller (a sparkle, a puff of smoke) joins the nearest one.
    if count is None or want:
        typical = sorted(r[1] - r[0] for r in runs)[len(runs) // 2]
        main = [r for r in runs if r[1] - r[0] >= 0.5 * typical]
        # Frames that touch (a tail over the next frame) came out as one run: split the widest
        # run at its thinnest column until the row has the frames it should.
        ink = lambda x: sum(1 for y in range(0, h, 2) if col[x, y])
        while want and len(main) < want:
            r = max(main, key=lambda m: m[1] - m[0])
            n = round((r[1] - r[0]) / ((r[1] - r[0]) / max(2, want - len(main) + 1)))
            span = r[1] - r[0]
            guess = r[0] + span // n
            cut = min(range(guess - span // (3 * n), guess + span // (3 * n)), key=ink)
            i = main.index(r)
            main[i:i + 1] = [[r[0], cut], [cut, r[1]]]
            runs = [x for x in runs if x is not r]
    else:
        main = sorted(sorted(runs, key=lambda r: r[1] - r[0], reverse=True)[:count])
    for r in runs:
        if r in main or any(m[0] <= r[0] and r[1] <= m[1] for m in main):
            continue
        near = min(main, key=lambda m: min(abs(r[0] - m[1]), abs(m[0] - r[1])))
        near[0], near[1] = min(near[0], r[0]), max(near[1], r[1])
    out = []
    for x0, x1 in main:
        crop = trim_slivers(im.crop((x0, 0, x1, h))) if want else im.crop((x0, 0, x1, h))
        box = crop.getbbox()
        out.append((crop.crop(box), box[1]))
    return out


def main(name, move_path, sleep_path, keep_height=False, rows=None, sleep_take=None, counts=None):
    if rows:
        sheet = clean(Image.open(move_path))
        bs = bands(sheet)
        pick = lambda n, want: frames(sheet.crop((0, bs[n - 1][0], sheet.width, bs[n - 1][1])), count=None, want=want)
        sheets = {"walk": pick(rows[0], counts[0] if counts else None), "sleep": pick(rows[1], counts[1] if counts else None)[:sleep_take]}
    else:
        sheets = {"walk": frames(clean(Image.open(move_path))), "sleep": frames(clean(Image.open(sleep_path)))}
    every = [f for fs in sheets.values() for f, _ in fs]
    cw = max(f.width for f in every)
    # A hop keeps its height: the move frames share the sheet's own ground line.
    if keep_height:
        top = min(y for _, y in sheets["walk"])
        ground = max(y + f.height for f, y in sheets["walk"])
        move_h = ground - top
    else:
        move_h = 0
    ch = max([move_h] + [f.height for f in every])
    scale = TARGET_H / ch
    size = (round(cw * scale), TARGET_H)
    OUT.mkdir(parents=True, exist_ok=True)
    for kind, fs in sheets.items():
        for i, (f, y) in enumerate(fs):
            canvas = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
            dy = (ch - move_h) + (y - top) if keep_height and kind == "walk" else ch - f.height
            canvas.paste(f, (cw - f.width, dy), f)  # nose right
            canvas.resize(size, Image.LANCZOS).save(OUT / f"{name}_{kind}_{i}.png", optimize=True)
    print(f"wrote {sum(len(v) for v in sheets.values())} {name} frames of {size[0]}x{size[1]} to {OUT}")


if __name__ == "__main__":
    argv = sys.argv[1:]
    if "--rows" in argv:
        i = argv.index("--rows")
        take = int(argv[argv.index("--sleep-frames") + 1]) if "--sleep-frames" in argv else None
        counts = (int(argv[argv.index("--counts") + 1]), int(argv[argv.index("--counts") + 2])) if "--counts" in argv else None
        main(argv[0], argv[1], None, rows=(int(argv[i + 1]), int(argv[i + 2])), sleep_take=take, counts=counts)
    else:
        args = [a for a in argv if not a.startswith("--")]
        main(args[0], args[1], args[2], keep_height="--keep-height" in argv)
