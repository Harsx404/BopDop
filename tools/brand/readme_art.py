"""README art: banner.jpg and gallery.jpg from the brand icon, the wordmark and full-size screenshots.

    python tools/brand/readme_art.py <project folder> <folder with home_full.png, player_full.png, ...>

The screenshots are 1344 x 2992 captures from the emulator (adb exec-out screencap -p).
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

REPO = sys.argv[1]
S = sys.argv[2]
OUT = os.path.join(REPO, 'docs', 'readme')
os.makedirs(OUT, exist_ok=True)
FONT = os.path.join(REPO, 'app', 'src', 'main', 'res', 'font', 'jakarta.ttf')

# The brand orange, from deep ember to warm peach.
STOPS = [(0.0, (0x8A, 0x1E, 0x10)), (0.55, (0xFF, 0x4D, 0x2D)), (1.0, (0xFF, 0x94, 0x66))]
BRAND = os.path.join(REPO, 'docs', 'brand')


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def grad_at(t):
    for (t0, c0), (t1, c1) in zip(STOPS, STOPS[1:]):
        if t <= t1:
            return lerp(c0, c1, (t - t0) / (t1 - t0))
    return STOPS[-1][1]


def diagonal(w, h, stops=None):
    """The icon's purple → pink → orange, corner to corner."""
    small = Image.new('RGB', (256, 256))
    px = small.load()
    for y in range(256):
        for x in range(256):
            px[x, y] = grad_at((x + y) / 510)
    return small.resize((w, h), Image.BICUBIC)


def font(size, weight=800):
    f = ImageFont.truetype(FONT, size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def logo(size):
    """The glass app icon (docs/brand, made by build-brand.cjs)."""
    return Image.open(os.path.join(BRAND, 'app-icon-glass.png')).convert('RGBA').resize((size, size), Image.LANCZOS)


def wordmark(width):
    w = Image.open(os.path.join(BRAND, 'wordmark-dark.png')).convert('RGBA')
    return w.resize((width, round(w.height * width / w.width)), Image.LANCZOS)


def phone(shot, height, radius_frac=0.115):
    """A screenshot in a thin dark phone frame with rounded corners."""
    im = Image.open(shot).convert('RGB')
    w = round(height * im.width / im.height)
    im = im.resize((w, height), Image.LANCZOS)
    bez = max(6, height // 70)
    W, H = w + 2 * bez, height + 2 * bez
    frame = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    fd = ImageDraw.Draw(frame)
    fd.rounded_rectangle([0, 0, W - 1, H - 1], radius=int(W * radius_frac), fill=(14, 14, 18, 255))
    fd.rounded_rectangle([1, 1, W - 2, H - 2], radius=int(W * radius_frac) - 1, outline=(70, 70, 80, 255), width=2)
    m = Image.new('L', (w, height), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w - 1, height - 1], radius=int(W * radius_frac) - bez, fill=255)
    frame.paste(im, (bez, bez), m)
    return frame


def shadowed(base, layer, xy, blur=28, alpha=150, dy=18):
    sh = Image.new('RGBA', layer.size, (0, 0, 0, 0))
    sh.putalpha(layer.split()[3].point(lambda a: alpha if a else 0))
    pad = blur * 3
    big = Image.new('RGBA', (layer.width + 2 * pad, layer.height + 2 * pad), (0, 0, 0, 0))
    big.paste(sh, (pad, pad))
    big = big.filter(ImageFilter.GaussianBlur(blur))
    base.alpha_composite(big, (xy[0] - pad, xy[1] - pad + dy))
    base.alpha_composite(layer, xy)


def backdrop(w, h):
    """Dark base washed with the icon gradient, like the app's liquid-metal backdrop."""
    bg = Image.new('RGBA', (w, h), (16, 14, 14, 255))
    glow = diagonal(w, h).convert('RGBA')
    m = Image.new('L', (w, h), 0)
    md = ImageDraw.Draw(m)
    md.ellipse([int(w * 0.42), int(-h * 0.35), int(w * 1.25), int(h * 1.2)], fill=200)
    md.ellipse([int(-w * 0.2), int(h * 0.55), int(w * 0.45), int(h * 1.6)], fill=110)
    m = m.filter(ImageFilter.GaussianBlur(min(w, h) // 5))
    bg.paste(glow, (0, 0), m)
    # Fine grain so the gradient doesn't band in JPEG.
    noise = Image.effect_noise((w, h), 18).convert('L').point(lambda v: int(v * 0.06))
    bg = Image.composite(Image.new('RGBA', (w, h), (255, 255, 255, 255)), bg, noise)
    return bg


def shot(name):
    return os.path.join(S, name + '_full.png')



# --- banner -----------------------------------------------------------------------------
W, H = 1920, 960
b = backdrop(W, H)
d = ImageDraw.Draw(b, 'RGBA')
lg = logo(150)
shadowed(b, lg, (130, 230), blur=20, alpha=120, dy=10)
shadowed(b, wordmark(560), (132, 430), blur=14, alpha=70, dy=6)
d.text((136, 610), 'Music, wrapped in glass.', font=font(46, 600), fill=(255, 255, 255, 235))
d.text((136, 680), 'YouTube Music + JioSaavn 320 kbps', font=font(32, 500), fill=(255, 255, 255, 170))
d.text((136, 724), 'Synced lyrics · offline · made-for-you mixes', font=font(32, 500), fill=(255, 255, 255, 170))

ph = 820
back_l = phone(shot('lyrics'), int(ph * 0.86))
back_r = phone(shot('library'), int(ph * 0.86))
front = phone(shot('player'), ph)
cx = 1390
shadowed(b, back_l.rotate(6, expand=True, resample=Image.BICUBIC), (cx - 470, 120))
shadowed(b, back_r.rotate(-6, expand=True, resample=Image.BICUBIC), (cx + 130, 120))
shadowed(b, front, (cx - front.width // 2, (H - front.height) // 2 + 10), blur=34, alpha=180)
b.convert('RGB').save(os.path.join(OUT, 'banner.jpg'), quality=88, optimize=True, progressive=True)

# --- gallery: six framed phones in a row ------------------------------------------------
names = ['home', 'search', 'queue', 'explore', 'settings_new']
ph = 760
frames = [phone(shot(n), ph) for n in names]
gap = 46
W = sum(f.width for f in frames) + gap * (len(frames) + 1)
H = ph + 200
g = backdrop(W, H)
x = gap
for i, f in enumerate(frames):
    shadowed(g, f, (x, 70 + (30 if i % 2 else 0)), blur=22, alpha=140, dy=14)
    x += f.width + gap
g = g.convert('RGB')
g = g.resize((2000, round(g.height * 2000 / g.width)), Image.LANCZOS)
g.save(os.path.join(OUT, 'gallery.jpg'), quality=86, optimize=True, progressive=True)
print('ok', os.listdir(OUT))
