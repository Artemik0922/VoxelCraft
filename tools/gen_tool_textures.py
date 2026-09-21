#!/usr/bin/env python3
"""Minecraft-style 16x16 tool sprites for VoxelCraft.

Designs every tool on a 16x16 grid, upscales x4 to the 64x64 PNG slots
under src/main/resources/textures/blocks/ (same convention as the other
texture generator scripts). Writes a contact sheet to tools/tool_preview.png.

Shapes are diagonal like vanilla item icons: handle bottom-left, head
top-right, light from the upper-left. Each head uses 5 shades plus a
separate handle palette per material.
"""
from PIL import Image
import os

SRC = 'src/main/resources/textures/blocks'
S = 16
UP = 4
OUT = 'tools/tool_preview.png'

# head roles -> palette keys; handle roles -> palette keys
NONE = '.'
R = {  # role char -> (kind, key)
    '.': None,
    '1': ('h', '1'), '2': ('h', '2'), '3': ('h', '3'),
    '4': ('h', '4'), '5': ('h', '5'),
    'a': ('w', '0'), 'b': ('w', '1'), 'c': ('w', '2'), 'x': ('w', '3'),
}

MATERIALS = {
    'wooden': dict(
        h1=(0x4E, 0x2F, 0x17), h2=(0x6B, 0x44, 0x23), h3=(0x8A, 0x5C, 0x30),
        h4=(0xA8, 0x6E, 0x3E), h5=(0xC0, 0x8A, 0x56),
        w3=(0x35, 0x1F, 0x0C), w0=(0x4A, 0x2E, 0x12), w1=(0x6B, 0x44, 0x20), w2=(0x8A, 0x5C, 0x2C)),
    'stone': dict(
        h1=(0x3A, 0x3A, 0x3A), h2=(0x5A, 0x5A, 0x5A), h3=(0x7A, 0x7A, 0x7A),
        h4=(0x9E, 0x9E, 0x9E), h5=(0xB4, 0xB4, 0xB4),
        w3=(0x35, 0x1F, 0x0C), w0=(0x4A, 0x2E, 0x12), w1=(0x6B, 0x44, 0x20), w2=(0x8A, 0x5C, 0x2C)),
    'iron': dict(
        h1=(0x5A, 0x5A, 0x64), h2=(0x77, 0x77, 0x80), h3=(0xA0, 0xA0, 0xB0),
        h4=(0xC8, 0xC8, 0xD4), h5=(0xE0, 0xE0, 0xEA),
        w3=(0x35, 0x1F, 0x0C), w0=(0x4A, 0x2E, 0x12), w1=(0x6B, 0x44, 0x20), w2=(0x8A, 0x5C, 0x2C)),
    'diamond': dict(
        h1=(0x1A, 0x6E, 0x6A), h2=(0x2A, 0xA8, 0xA0), h3=(0x4C, 0xC8, 0xC0),
        h4=(0x7A, 0xE4, 0xE0), h5=(0xA0, 0xF4, 0xF0),
        w3=(0x35, 0x1F, 0x0C), w0=(0x4A, 0x2E, 0x12), w1=(0x6B, 0x44, 0x20), w2=(0x8A, 0x5C, 0x2C)),
    'golden': dict(
        h1=(0x8A, 0x66, 0x10), h2=(0xC8, 0x9A, 0x18), h3=(0xF0, 0xC0, 0x20),
        h4=(0xFC, 0xDC, 0x58), h5=(0xFF, 0xEE, 0x8C),
        w3=(0x35, 0x1F, 0x0C), w0=(0x4A, 0x2E, 0x12), w1=(0x6B, 0x44, 0x20), w2=(0x8A, 0x5C, 0x2C)),
    'meteorite': dict(
        h1=(0x1E, 0x21, 0x33), h2=(0x2A, 0x2E, 0x44), h3=(0x4A, 0x4F, 0x72),
        h4=(0x6E, 0x74, 0xA4), h5=(0x9A, 0xA0, 0xCC),
        w3=(0x35, 0x1F, 0x0C), w0=(0x4A, 0x2E, 0x12), w1=(0x6B, 0x44, 0x20), w2=(0x8A, 0x5C, 0x2C)),
}


class Grid:
    def __init__(self):
        self.cells = [[None] * S for _ in range(S)]

    def put(self, x, y, role):
        if 0 <= x < S and 0 <= y < S:
            self.cells[y][x] = role

    def rect(self, x0, y0, x1, y1, role):
        for y in range(max(0, y0), min(S, y1 + 1)):
            for x in range(max(0, x0), min(S, x1 + 1)):
                self.put(x, y, role)

    def render(self, mat):
        pal = MATERIALS[mat]
        out = [[None] * S for _ in range(S)]
        for y in range(S):
            for x in range(S):
                role = self.cells[y][x]
                if role is None:
                    continue
                kind, key = R[role]
                color = pal[kind + key]
                out[y][x] = color
        return out


def handle(g, x0=6, y0=9, x1=None, outline=True):
    """Vertical handle: dark col x0, mid col x0+1, optional left outline."""
    if x1 is None:
        x1 = x0 + 1
    for y in range(y0, S):
        if outline:
            g.put(x0 - 1, y, 'x')
        g.put(x0, y, 'a')
        g.put(x1, y, 'b')
    # grip highlight rows
    for y in range(y0 + 1, S - 1):
        g.put(x1 + 1, y, 'c')


def pickaxe(g):
    handle(g)
    # Continuous arch band (the classic pick head), widest at the bottom,
    # with tiny claw stubs on both lower corners and the handle at centre.
    arch = [
        (7, 8, '5'),    # r0
        (6, 9, '5'),    # r1
        (5, 10, '4'),   # r2
        (4, 11, '4'),   # r3
        (3, 12, '4'),   # r4
        (2, 12, '3'),   # r5
        (2, 12, '3'),   # r6
        (2, 11, '2'),   # r7
        (3, 11, '2'),   # r8
    ]
    for r, (lo, hi, s) in enumerate(arch):
        for x in range(lo, hi + 1):
            g.put(x, r, s)
    # left-claw + right-claw stubs
    for (x, y) in [(2, 8), (3, 8), (13, 7), (12, 7)]:
        g.put(x, y, '1')
    g.put(2, 9, '2'); g.put(12, 8, '2')
    # bottom shading / side ticks
    for (x, y) in [(1, 8), (13, 6), (1, 6)]:
        g.put(x, y, '1')
    # neck down to the handle
    for y in range(9, 11):
        for x in range(6, 8):
            if not g.cells[y][x]:
                g.put(x, y, '3')
    g.put(6, 8, '3'); g.put(7, 8, '3')


def axe(g):
    handle(g)
    # Wide flat blade: cutting edge up-right, near-vertical back on the
    # left, tapering into the neck.
    blade = [
        (11, 12, '5'),   # r1 thick edge
        (9, 12, '5'),    # r2
        (8, 12, '4'),    # r3
        (7, 12, '4'),    # r4
        (6, 11, '4'),    # r5
        (5, 10, '3'),    # r6
        (5, 9, '3'),     # r7
        (5, 8, '2'),     # r8
        (5, 7, '2'),     # r9
    ]
    for r, (lo, hi, s) in enumerate(blade, start=1):
        for x in range(lo, hi + 1):
            g.put(x, r, s)
    # lit rim along the cutting edge
    for i, (x, y) in enumerate([(12, 1), (11, 2), (10, 3), (9, 4), (8, 5)]):
        g.put(x, y, '5')
    # back edge + bottom outlines (follow each row's left boundary)
    for r, (lo, hi, s) in enumerate(blade, start=1):
        g.put(lo - 1, r, '1')
    g.put(5, 9, '1'); g.put(6, 9, '1'); g.put(7, 9, '1')
    # neck to the handle
    g.put(6, 9, '3'); g.put(7, 9, '3'); g.put(8, 9, '3')


def shovel(g):
    handle(g)
    # rounded scoop: rows 2..7, widest at row 4
    sco = [(6, 9), (5, 10), (4, 11), (4, 11), (5, 10), (6, 9)]
    shades = ['5', '4', '3', '2', '3', '2']
    for idx, (lo, hi) in enumerate(sco, start=2):
        row_shade = shades[idx - 2]
        for x in range(lo, hi + 1):
            g.put(x, idx, row_shade)
    # silver lip on the top edge
    for x in range(lo, hi + 1):
        g.put(x, 2, '5')
    # bottom dark outline
    for x in range(6, 9):
        g.put(x, 7, '1')
    # connect
    g.put(7, 8, '3'); g.put(8, 8, '3')


def hoe(g):
    handle(g)
    # Flat matt blade: a shallow horizontal bar top-right with a curved
    # underside, angled down into the neck.
    blade = [
        (8, 13, '5'),    # r2 top lit edge
        (7, 13, '5'),    # r3
        (7, 13, '4'),    # r4
        (6, 13, '4'),    # r5
        (6, 12, '3'),    # r6
        (6, 11, '2'),    # r7
        (6, 10, '2'),    # r8
    ]
    for r, (lo, hi, s) in enumerate(blade, start=2):
        for x in range(lo, hi + 1):
            g.put(x, r, s)
    # right vertical cutting edge (bright)
    for y in range(2, 6):
        g.put(14, y, '5')
    g.put(14, 2, '2'); g.put(14, 5, '2')
    # bottom outline + back
    g.put(5, 8, '1'); g.put(5, 9, '1')
    g.put(6, 9, '1'); g.put(7, 9, '1')
    # neck to the handle
    g.put(7, 9, '3'); g.put(8, 9, '3')


def sword(g):
    # blade diagonal (5,10)..(13,2), 2px: lit top edge + core
    for i in range(9):
        x = 5 + i
        y = 10 - i
        g.put(x, y, '4')          # blade core
        g.put(x, y - 1, '5')      # lit top edge
    g.put(14, 0, '5')             # tip
    g.put(13, 1, '4')
    # crossguard
    g.rect(4, 10, 11, 11, '3')
    g.put(4, 10, '5'); g.put(11, 10, '5')
    g.put(4, 11, '2'); g.put(11, 11, '2')
    # grip (wood handle)
    for y in range(12, 15):
        g.put(7, y, 'a')
        g.put(8, y, 'b')
    g.put(6, 12, 'x'); g.put(9, 12, 'x')   # guard shoulders
    g.put(7, 15, 'a'); g.put(8, 15, 'b')   # pommel


def build(mat, shape):
    g = Grid()
    shape(g)
    return g.render(mat)


def to_image(grid):
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    for y in range(S):
        for x in range(S):
            c = grid[y][x]
            if c is not None:
                img.putpixel((x, y), c + (255,))
    return img


def main():
    os.makedirs(SRC, exist_ok=True)
    shapes = {'pickaxe': pickaxe, 'axe': axe, 'shovel': shovel,
              'sword': sword, 'hoe': hoe}
    written = []
    for mat in MATERIALS:
        for name, fn in shapes.items():
            img = upscale(to_image(build(mat, fn)))
            img.save(os.path.join(SRC, f'{mat}_{name}.png'))
            written.append((f'{mat}_{name}', img))
    # held iron sword (inventory icon + held sprite share the iron palette)
    img = upscale(to_image(build('iron', sword)))
    img.save(os.path.join(SRC, 'iron_sword.png'))
    written.append(('iron_sword', img))
    # iron sword inventory icon is iron_sword_item (same art)
    img.save(os.path.join(SRC, 'iron_sword_item.png'))
    written.append(('iron_sword_item', img))

    cols = 7
    rows = (len(written) + cols - 1) // cols
    tile = S * UP
    sheet = Image.new('RGBA', (cols * tile, rows * tile), (20, 20, 28, 255))
    for i, (name, img_) in enumerate(written):
        x = i % cols
        y = i // cols
        sheet.paste(img_, (x * tile, y * tile), img_)
        from PIL import ImageDraw
        d = ImageDraw.Draw(sheet)
        d.text((x * tile + 2, y * tile + tile - 14), name, fill=(255, 255, 255, 255))
    sheet.save(OUT)
    print(f'{len(written)} textures written; sheet -> {OUT}')


def upscale(img):
    return img.resize((S * UP, S * UP), Image.NEAREST)


if __name__ == '__main__':
    main()