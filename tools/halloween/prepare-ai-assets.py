"""Format AI artwork for Android; never draws or changes the companion characters.

Raw image generations and prompts live in art/halloween-ai. This importer removes
near-transparent generation residue, registers atlas cells and samples a real
native pixel grid. Artwork itself comes from the saved AI originals.
"""
from pathlib import Path
import hashlib
import json
from PIL import Image
import numpy as np
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'art/halloween-ai'
DEST = ROOT / 'app/src/main/res/drawable-nodpi'
PREVIEW = ROOT / 'output/halloween-verification/ai-assets'
NEAREST = Image.Resampling.NEAREST


def clean(im):
    im = im.convert('RGBA')
    # Almost-transparent white residue is present in some generated PNGs.
    im.putalpha(im.getchannel('A').point(lambda a: 255 if a >= 160 else 0))
    return im


def atlas_cells(im):
    # Generators may overrun the requested cell margin. Isolate whole components
    # before assigning them to cells, so e.g. the moon cannot bleed into a bat.
    labels, count = ndimage.label(np.array(im.getchannel('A')) > 0)
    areas = np.bincount(labels.ravel())
    groups = [[] for _ in range(16)]
    minimum = max(8, areas[1:].max() * .003)
    for label, box in enumerate(ndimage.find_objects(labels), 1):
        if areas[label] < minimum:
            continue
        cy, cx = ndimage.center_of_mass(labels == label)
        slot = min(3, int(cy * 4 / im.height)) * 4 + min(3, int(cx * 4 / im.width))
        groups[slot].append(label)
    result = []
    for group in groups:
        if not group:
            raise ValueError('Empty generated atlas cell')
        largest = max(areas[label] for label in group)
        keep = [label for label in group if areas[label] >= largest * .025]
        cell = im.copy()
        cell.putalpha(Image.fromarray((np.isin(labels, keep) * 255).astype('uint8')))
        result.append(cell.crop(cell.getbbox()))
    return result


def registered(im, size, margin):
    bbox = im.getbbox()
    if not bbox:
        raise ValueError('Empty generated atlas cell')
    im = im.crop(bbox)
    ratio = (size - margin * 2) / max(im.size)
    im = im.resize((max(1, round(im.width * ratio)), max(1, round(im.height * ratio))), NEAREST)
    out = Image.new('RGBA', (size, size))
    out.paste(im, ((size - im.width) // 2, (size - im.height) // 2))
    return out


def palette(im):
    alpha = im.getchannel('A')
    colors = ['21162f', '31223e', '49345f', '654c7e', '876a9e', 'b39bd1', 'd1b9df',
              '602943', '913619', 'b75019', 'e57918', 'f7a34f', 'ffc45b', 'ffde9c', 'f4e4c1']
    pal = Image.new('P', (1, 1))
    rgb = [int(color[i:i+2], 16) for color in colors for i in (0, 2, 4)]
    pal.putpalette(rgb + rgb[:3] * (256 - len(colors)))
    out = im.convert('RGB').quantize(palette=pal, dither=Image.Dither.NONE).convert('RGBA')
    out.putalpha(alpha)
    # Transparent RGB is irrelevant; canonicalize it for deterministic validation.
    out.paste((0, 0, 0, 0), mask=alpha.point(lambda a: 255 - a))
    return out


def save(im, name):
    im.save(DEST / (name + '.png'), optimize=True)
    bg = Image.new('RGBA', im.size, '#21162f')
    bg.alpha_composite(im)
    bg.resize((im.width * 4, im.height * 4), NEAREST).save(PREVIEW / (name + '.png'))


def main():
    DEST.mkdir(parents=True, exist_ok=True)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    before = {name: hashlib.sha256((DEST / f'halloween_{name}.png').read_bytes()).hexdigest()
              for name in ('wisp', 'ember', 'echo')}
    for label in ('a', 'b', 'c'):
        source = clean(Image.open(ART / 'raw' / f'icons-{label}.png'))
        cells = atlas_cells(source)
        atlas = Image.new('RGBA', (96, 96))
        for i in range(16):
            cell = registered(cells[i], 24, 2)
            ink = Image.new('RGBA', cell.size, 'white')
            ink.putalpha(cell.getchannel('A'))
            atlas.paste(ink, (i % 4 * 24, i // 4 * 24))
        save(atlas, f'halloween_icons_{label}')
    source = clean(Image.open(ART / 'raw/props.png'))
    props = Image.new('RGBA', (256, 256))
    isolated = atlas_cells(source)
    # One common source scale per row preserves animation proportions.
    for row in range(4):
        cells = isolated[row * 4:row * 4 + 4]
        boxes = [cell.getbbox() for cell in cells]
        if any(box is None for box in boxes):
            raise ValueError('Missing environment sprite')
        factor = 52 / max(max(b[2] - b[0], b[3] - b[1]) for b in boxes)
        for col, (cell, box) in enumerate(zip(cells, boxes)):
            cell = cell.crop(box)
            cell = cell.resize((max(1, round(cell.width * factor)), max(1, round(cell.height * factor))), NEAREST)
            props.paste(cell, (col * 64 + (64 - cell.width) // 2, row * 64 + (64 - cell.height) // 2))
    props = palette(props)
    save(props, 'halloween_props')
    roofs = clean(Image.open(ART / 'raw/rooftops.png')).resize((288, 96), NEAREST)
    save(palette(roofs), 'halloween_rooftops')
    # A generated lantern makes the app icon; adaptive-icon safe zone is centered.
    lantern = props.crop((0, 128, 64, 192))
    launcher = Image.new('RGBA', (108, 108))
    launcher.paste(lantern, (22, 22))
    save(launcher, 'halloween_launcher')
    report = {'source': 'Built-in image_gen', 'character_sha256': before, 'assets': {}}
    for path in sorted(DEST.glob('halloween_*.png')):
        im = Image.open(path).convert('RGBA')
        report['assets'][path.name] = {'size': im.size, 'colors': len(im.getcolors(im.width * im.height)),
            'alpha': sorted(set(im.getchannel('A').getdata()))}
    for name, digest in before.items():
        assert hashlib.sha256((DEST / f'halloween_{name}.png').read_bytes()).hexdigest() == digest
    (ART / 'validation.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
