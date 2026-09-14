#!/usr/bin/env python3
"""Deterministic vanilla-style texture generator for VoxelCraft.

Designs every texture on a 16x16 grid and upscales x4 into the existing
64x64 PNG slots under src/main/resources/textures/blocks/. Vanilla
principles: tight 4-6 shade palettes, ordered dithering, hand-placed
features. Writes a contact sheet to tools/texture_sheet.png for review.
"""
from PIL import Image
import os
import random

SRC = 'src/main/resources/textures/blocks'
S = 16          # design size
UP = 4          # upscale to 64x64

# 4x4 Bayer matrix for ordered dithering
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def rnd(seed):
    return random.Random(seed)


def clamp(v):
    return max(0, min(255, int(v)))


def value_noise(seed, scale):
    """Smooth-ish value noise on the 16x16 grid, 0..1."""
    r = rnd(seed)
    coarse = [[r.random() for _ in range(scale + 1)] for _ in range(scale + 1)]
    out = [[0.0] * S for _ in range(S)]
    for y in range(S):
        for x in range(S):
            fx, fy = x * scale / S, y * scale / S
            x0, y0 = int(fx), int(fy)
            tx, ty = fx - x0, fy - y0
            tx = tx * tx * (3 - 2 * tx)
            ty = ty * ty * (3 - 2 * ty)
            a = coarse[y0][x0]
            b = coarse[y0][x0 + 1]
            c = coarse[y0 + 1][x0]
            d = coarse[y0 + 1][x0 + 1]
            out[y][x] = (a * (1 - tx) + b * tx) * (1 - ty) + \
                        (c * (1 - tx) + d * tx) * ty
    return out


def palette_pick(pixel_view, palette):
    """pixel_view(x, y) -> 0..1; ordered-dither into the palette."""
    out = [[None] * S for _ in range(S)]
    for y in range(S):
        for x in range(S):
            n = pixel_view(x, y)
            n += (BAYER[y % 4][x % 4] / 16.0 - 0.5) * (1.0 / max(1, len(palette) - 1))
            idx = max(0, min(len(palette) - 1, int(n * len(palette))))
            out[y][x] = palette[idx]
    return out


def save(name, grid, alpha=None):
    """grid[y][x] = (r,g,b) or None for hole; alpha[y][x] overrides alpha."""
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    for y in range(S):
        for x in range(S):
            c = grid[y][x]
            if c is None:
                continue
            a = 255 if alpha is None else alpha[y][x]
            im.putpixel((x, y), (c[0], c[1], c[2], a))
    im.resize((S * UP, S * UP), Image.NEAREST).save(os.path.join(SRC, name))
    return im


# ---------------------------------------------------------------- dirt

def dirt():
    pal = [(134, 96, 67), (121, 85, 58), (102, 70, 47), (87, 60, 42), (150, 108, 77)]
    n1 = value_noise(101, 5)
    n2 = value_noise(202, 11)
    grid = palette_pick(lambda x, y: n1[y][x] * 0.65 + n2[y][x] * 0.35, pal)
    r = rnd(303)
    for _ in range(5):  # small pebbles
        x, y = r.randrange(S), r.randrange(S)
        grid[y][x] = (160, 148, 138)
        if r.random() < 0.5:
            grid[(y + 1) % S][x] = (76, 55, 40)
    return save('dirt.png', grid)


# ---------------------------------------------------------------- stone

def stone(variant):
    pal = [(136, 136, 136), (125, 125, 125), (112, 112, 112), (100, 100, 100), (148, 148, 148)]
    n1 = value_noise(400 + variant * 17, 4)
    n2 = value_noise(500 + variant * 17, 9)
    grid = palette_pick(lambda x, y: n1[y][x] * 0.7 + n2[y][x] * 0.3, pal)
    # hairline cracks: short random walks in a darker shade
    r = rnd(600 + variant)
    for _ in range(2):
        x, y = r.randrange(S), r.randrange(S)
        for _ in range(4 + r.randrange(5)):
            grid[y % S][x % S] = (88, 88, 88)
            x += r.choice((-1, 0, 1))
            y += r.choice((0, 1))
    name = 'stone.png' if variant == 0 else f'stone#{variant}.png'
    return save(name, grid)


# ---------------------------------------------------------------- cobble

def cobble():
    mortar = (90, 90, 90)
    grid = [[mortar] * S for _ in range(S)]
    stones = [(1, 1, 4, 3), (7, 0, 5, 4), (0, 5, 4, 4), (5, 5, 6, 5),
              (12, 5, 4, 6), (1, 10, 5, 5), (7, 11, 4, 4), (12, 12, 4, 4)]
    r = rnd(707)
    for (sx, sy, w, h) in stones:
        base = 120 + r.randrange(-14, 15)
        for y in range(sy, min(S, sy + h)):
            for x in range(sx, min(S, sx + w)):
                # rounded corners
                cx = x - sx - w / 2 + 0.5
                cy = y - sy - h / 2 + 0.5
                if (cx < -w / 2 + 1 and cy < -h / 2 + 1) or \
                   (cx > w / 2 - 1 and cy < -h / 2 + 1) or \
                   (cx < -w / 2 + 1 and cy > h / 2 - 1) or \
                   (cx > w / 2 - 1 and cy > h / 2 - 1):
                    continue
                # light from top-left, dark rim bottom-right
                v = base + (3 if (y == sy or x == sx) else 0) \
                       - (14 if (y == sy + h - 1 or x == sx + w - 1) else 0)
                v += r.randrange(-6, 7)
                grid[y][x] = (clamp(v), clamp(v), clamp(v))
    return save('cobblestone.png', grid)


# ---------------------------------------------------------------- sand

def sand(variant):
    pal = [(231, 222, 178), (222, 212, 165), (213, 202, 152), (240, 233, 194)]
    n = value_noise(800 + variant * 13, 8)
    grid = palette_pick(lambda x, y: 0.5 + (n[y][x] - 0.5) * 0.55, pal)
    # faint horizontal ripples
    for y in range(2, S, 5):
        for x in range(S):
            if (x * 7 + y * 3) % 5 < 2:
                c = grid[y][x]
                grid[y][x] = (max(0, c[0] - 9), max(0, c[1] - 9), max(0, c[2] - 9))
    name = 'sand.png' if variant == 0 else f'sand#{variant}.png'
    return save(name, grid)


# ---------------------------------------------------------------- gravel

def gravel():
    base = [(120, 112, 104), (104, 96, 90), (136, 128, 118)]
    grid = [[base[(x * 3 + y * 5) % 3] for x in range(S)] for y in range(S)]
    r = rnd(909)
    for _ in range(22):  # pebbles with a light top and dark bottom
        x, y = r.randrange(S), r.randrange(S)
        w = r.choice((1, 2))
        tone = r.randrange(-28, 30)
        for dy in range(2):
            for dx in range(w):
                xx, yy = (x + dx) % S, (y + dy) % S
                v = 118 + tone + (10 if dy == 0 else -12)
                grid[yy][xx] = (clamp(v), clamp(v - 4), clamp(v - 10))
    return save('gravel.png', grid)


# ---------------------------------------------------------------- snow

def snow():
    n = value_noise(1111, 10)
    grid = [[None] * S for _ in range(S)]
    for y in range(S):
        for x in range(S):
            v = 246 + int((n[y][x] - 0.5) * 14)
            grid[y][x] = (clamp(v - 3), clamp(v - 1), clamp(v + 4))
    return save('snow.png', grid)


# ---------------------------------------------------------------- logs

def bark_stripes(pal, groove, seed, knot=True):
    """Vertical ridge bands + one wandering groove + optional knot."""
    grid = [[None] * S for _ in range(S)]
    r = rnd(seed)
    band = [0] * S
    prev = -1.0
    idx = 1
    for x in range(S):
        n = r.random()
        if n < prev - 0.15 or n > prev + 0.15:
            idx = r.randrange(len(pal))
        band[x] = idx
        prev = n
    gx = r.randrange(2, S - 2)
    kx, ky = r.randrange(3, S - 3), r.randrange(3, S - 3)
    for y in range(S):
        gxr = gx + r.choice((-1, 0, 0, 1))
        for x in range(S):
            c = pal[min(len(pal) - 1, band[x] + r.randrange(-1, 2))]
            d = abs(x - gxr)
            if d == 0:
                c = groove
            elif d == 1 and r.random() < 0.5:
                c = pal[0]
            if knot:
                kdx, kdy = x - kx, y - ky
                kd = kdx * kdx * 3 + kdy * kdy
                if kd <= 2:
                    c = groove
                elif kd == 3:
                    c = pal[-1]
            grid[y][x] = c
    return grid


def rings(pal_wood, pal_bark, seed, border=1.5):
    grid = [[None] * S for _ in range(S)]
    import math
    r = rnd(seed)
    cx = cy = 7.5
    for y in range(S):
        for x in range(S):
            dx, dy = x - cx, y - cy
            dist = math.sqrt(dx * dx + dy * dy)
            if dist > 7.4 - border * 0.4:
                grid[y][x] = pal_bark[r.randrange(len(pal_bark))]
                continue
            wob = (r.random() - 0.5) * 0.9
            ring = int((dist + wob) / 1.7)
            c = pal_wood[ring % len(pal_wood)]
            if ring % 3 == 2:
                c = pal_wood[0]
            ang = math.atan2(dy, dx)
            if r.random() < 0.04:
                c = pal_wood[1]
            grid[y][x] = c
    return grid


def oak_log():
    pal = [(105, 77, 42), (94, 68, 36), (123, 92, 50), (82, 60, 32), (134, 101, 56)]
    return save('oak_log.png', bark_stripes(pal, (66, 47, 24), 1212))


def oak_log_top():
    return save('oak_log_top.png', rings(
        [(156, 127, 78), (174, 143, 90), (140, 112, 68), (182, 152, 98)],
        [(96, 70, 40), (110, 82, 48)], 1313))


def birch_log_side():
    # cream bark + soft grey streaks + dark horizontal dashes (vanilla birch)
    base = [(216, 211, 202), (225, 221, 213), (205, 199, 189), (232, 228, 221)]
    n = value_noise(1414, 7)
    grid = palette_pick(lambda x, y: n[y][x], base)
    r = rnd(1515)
    for _ in range(7):  # dark dashes
        x, y = r.randrange(0, S - 3), r.randrange(1, S - 1)
        w = r.choice((2, 3, 4))
        for i in range(w):
            grid[y][x + i] = (54, 50, 46)
            if r.random() < 0.4 and y + 1 < S:
                grid[y + 1][x + i] = (78, 72, 66)
    return save('birch_log_side.png', grid)


def birch_log_top():
    return save('birch_log_top.png', rings(
        [(198, 190, 162), (212, 205, 178), (186, 177, 148), (220, 214, 190)],
        [(176, 170, 156), (188, 182, 168)], 1616))


def spruce_log():
    pal = [(74, 54, 34), (62, 44, 28), (86, 64, 40), (52, 37, 23)]
    return save('spruce_log.png', bark_stripes(pal, (42, 30, 19), 1717, knot=False))


def spruce_log_top():
    return save('spruce_log_top.png', rings(
        [(110, 82, 52), (124, 94, 62), (96, 70, 44), (132, 102, 68)],
        [(58, 42, 26), (68, 50, 32)], 1818))


def jungle_log():
    pal = [(107, 84, 48), (94, 73, 41), (124, 97, 58), (82, 63, 36), (138, 109, 66)]
    grid = bark_stripes(pal, (64, 48, 27), 1919, knot=False)
    r = rnd(2020)
    for _ in range(4):  # lighter stripes
        x = r.randrange(S)
        for y in range(S):
            if r.random() < 0.7:
                c = grid[y][x]
                grid[y][x] = (min(255, c[0] + 18), min(255, c[1] + 14), min(255, c[2] + 8))
    return save('jungle_log.png', grid)


def jungle_log_top():
    return save('jungle_log_top.png', rings(
        [(160, 120, 74), (176, 136, 86), (146, 108, 66), (186, 146, 94)],
        [(88, 66, 38), (100, 76, 46)], 2121))


# ---------------------------------------------------------------- planks

def oak_planks():
    seam = (96, 72, 42)
    grid = [[None] * S for _ in range(S)]
    r = rnd(2222)
    tones = [0, -10, 6, -4]
    for y in range(S):
        board = y // 4
        is_seam = y % 4 == 3
        for x in range(S):
            if is_seam:
                grid[y][x] = seam
                continue
            t = tones[board]
            grain = r.randrange(-6, 7)
            v = (162 + t + grain, 130 + t + grain, 78 + t + grain)
            grid[y][x] = (clamp(v[0]), clamp(v[1]), clamp(v[2]))
    # plank end joints, vanilla style
    grid[1][12] = seam; grid[2][12] = seam
    grid[9][3] = seam; grid[10][3] = seam
    grid[9][13] = seam; grid[10][13] = seam
    grid[13][7] = seam; grid[14][7] = seam
    return save('oak_planks.png', grid)


# ---------------------------------------------------------------- leaves

def leaves(name, seed, cutout, bright):
    """Desaturated grey-green sprig base; the biome tint supplies the hue."""
    if bright:
        pal = [(158, 166, 150), (172, 180, 162), (186, 194, 174), (200, 207, 187)]
    else:
        pal = [(134, 142, 128), (150, 158, 142), (166, 174, 156), (182, 189, 169)]
    grid = [[None] * S for _ in range(S)]
    dark = pal[0]
    r = rnd(seed)
    gap = [[False] * S for _ in range(S)]
    for y in range(S):
        for x in range(S):
            gap[y][x] = r.random() < 0.07
    # dense small sprigs: tiny scattered peep holes, near-solid mass
    for i in range(34):
        cx, cy = r.randrange(S), r.randrange(S)
        rad = 1.1 + r.random() * 1.0
        reach = int(rad) + 1
        import math
        for dy in range(-reach, reach + 1):
            for dx in range(-reach, reach + 1):
                d2 = dx * dx + dy * dy
                if d2 > rad * rad + 0.4:
                    continue
                x, y = (cx + dx) % S, (cy + dy) % S
                if gap[y][x]:
                    continue
                t = math.sqrt(d2) / (rad + 0.001)
                idx = max(0, min(len(pal) - 1,
                                 int((1.0 - t) * (len(pal) - 1) + (r.random() - 0.5) * 1.3 + 0.5)))
                grid[y][x] = pal[idx]
    # any survivor holes smaller than 2 px stay; larger clumps get filled
    for y in range(S):
        for x in range(S):
            if grid[y][x] is None:
                if cutout:
                    continue
                grid[y][x] = dark
    if cutout:
        for y in range(S):  # plug holes wider than one pixel
            for x in range(S):
                if grid[y][x] is not None:
                    continue
                neigh = sum(1 for dy in (-1, 0, 1) for dx in (-1, 0, 1)
                            if 0 <= y + dy < S and 0 <= x + dx < S
                            and grid[y + dy][x + dx] is None)
                if neigh > 2:
                    grid[y][x] = dark
    for _ in range(6):
        x, y = r.randrange(S), r.randrange(S)
        if grid[y][x] is not None:
            grid[y][x] = pal[-1]
    return save(name, grid)


def grass_side():
    """Dirt body with a grey grass rim; alpha 254 on dirt = never tinted,
    255 on the rim = biome tint applies (shader-masked)."""
    dirt_pal = [(134, 96, 67), (121, 85, 58), (102, 70, 47), (87, 60, 42)]
    grid = [[None] * S for _ in range(S)]
    alpha = [[254] * S for _ in range(S)]
    r = rnd(2323)
    for y in range(S):
        for x in range(S):
            c = dirt_pal[r.randrange(len(dirt_pal))]
            grid[y][x] = c
    # ragged rim, 3-5 px deep
    import math
    for x in range(S):
        rim = 3 + (int(math.sin(x * 1.3) * 1.5) + 2) % 3
        for y in range(rim):
            v = 200 + r.randrange(-20, 16)
            grid[y][x] = (v, v, v)
            alpha[y][x] = 255
    return save('grass_side.png', grid, alpha=alpha)


# ---------------------------------------------------------------- main

def main():
    made = []
    made.append(dirt())
    for v in range(4):
        made.append(stone(v))
    made.append(cobble())
    for v in range(4):
        made.append(sand(v))
    made.append(gravel())
    made.append(snow())
    made.append(oak_log())
    made.append(oak_log_top())
    made.append(birch_log_side())
    made.append(birch_log_top())
    made.append(spruce_log())
    made.append(spruce_log_top())
    made.append(jungle_log())
    made.append(jungle_log_top())
    made.append(oak_planks())
    made.append(grass_side())
    for v in range(4):
        made.append(leaves(f'oak_leaves.png' if v == 0 else f'oak_leaves#{v}.png',
                           3000 + v, cutout=True, bright=False))
        made.append(leaves(f'oak_leaves_opaque.png' if v == 0 else f'oak_leaves_opaque#{v}.png',
                           3100 + v, cutout=False, bright=False))
    made.append(leaves('birch_leaves.png', 3300, cutout=True, bright=True))
    print(f"generated {len(made)} textures")

    # contact sheet
    names = ['dirt.png', 'stone.png', 'cobblestone.png', 'sand.png', 'gravel.png',
             'snow.png', 'oak_log.png', 'oak_log_top.png', 'birch_log_side.png',
             'birch_log_top.png', 'spruce_log.png', 'spruce_log_top.png',
             'jungle_log.png', 'jungle_log_top.png', 'oak_planks.png',
             'grass_side.png', 'oak_leaves.png', 'oak_leaves_opaque.png',
             'birch_leaves.png']
    ts = 72
    cols = 10
    sheet = Image.new('RGBA', (cols * (ts + 4), ((len(names) + cols - 1) // cols + 1) * (ts + 4)),
                      (44, 48, 60, 255))
    for i, n in enumerate(names):
        im = Image.open(os.path.join(SRC, n)).convert('RGBA').resize((ts, ts), Image.NEAREST)
        sheet.paste(im, ((i % cols) * (ts + 4) + 2, (i // cols) * (ts + 4) + 2))
    sheet.save('tools/texture_sheet.png')
    print('sheet written')


if __name__ == '__main__':
    main()
