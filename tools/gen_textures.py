# -*- coding: utf-8 -*-
"""
TextureAgent — генератор текстур 64x64 для клона Minecraft.
Запуск:  python gen_textures.py
Результат:  textures/<name>.png, atlas.png, atlas.json
"""
import os, re, json, math, zlib
import numpy as np
from PIL import Image, ImageDraw, ImageFont

SIZE = 64
OUT = "textures"

# ======================= ШУМ И УТИЛИТЫ =======================
def _rng(name):
    return np.random.default_rng(zlib.crc32(name.encode()))

def vnoise(rng, h, w, cx=4, cy=None):
    cy = cy or cx
    g = rng.random((cy + 1, cx + 1))
    xs = np.linspace(0, cx, w); ys = np.linspace(0, cy, h)
    x0 = np.clip(np.floor(xs).astype(int), 0, cx - 1)
    y0 = np.clip(np.floor(ys).astype(int), 0, cy - 1)
    fx = np.clip(xs - x0, 0, 1); fy = np.clip(ys - y0, 0, 1)
    fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy)
    v00 = g[np.ix_(y0, x0)]; v10 = g[np.ix_(y0, x0 + 1)]
    v01 = g[np.ix_(y0 + 1, x0)]; v11 = g[np.ix_(y0 + 1, x0 + 1)]
    fx = fx[None, :]; fy = fy[:, None]
    return (v00 * (1 - fx) + v10 * fx) * (1 - fy) + (v01 * (1 - fx) + v11 * fx) * fy

def fbm(rng, h=SIZE, w=SIZE, cx=4, oct=3):
    v = np.zeros((h, w)); a = 1.0; t = 0.0; c = cx
    for _ in range(oct):
        v += a * vnoise(rng, h, w, c); t += a; a *= 0.5; c *= 2
    return v / t

def to_img(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype('uint8'),
                           'RGBA' if a.shape[2] == 4 else 'RGB')

def shade(a, n, lo=0.8, hi=1.15):
    return a * (lo + (hi - lo) * n)[..., None]

def pick(rng, pal, h=SIZE, w=SIZE):
    return (np.array(pal) / 255)[rng.integers(0, len(pal), (h, w))]

def cracks(a, rng, n=4, dark=0.6):
    h, w = a.shape[:2]
    for _ in range(n):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        for _ in range(int(rng.integers(6, 18))):
            if 0 <= x < w and 0 <= y < h:
                a[y, x, :3] *= dark
            x += int(rng.integers(-1, 2)); y += int(rng.integers(0, 2))
    return a

def dilate(m, r):
    out = m.copy(); ys, xs = np.where(m)
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx * dx + dy * dy <= r * r:
                out[np.clip(ys + dy, 0, m.shape[0] - 1),
                    np.clip(xs + dx, 0, m.shape[1] - 1)] = True
    return out

def stone_base(rng, pal, cell=6, lo=0.82, hi=1.12):
    return shade(pick(rng, pal), fbm(rng, cx=cell), lo, hi)

def fluid(rng, ramp, alpha=1.0, cx=4):
    ramp = np.array(ramp) / 255
    v = fbm(rng, cx=cx)
    idx = np.clip((v * len(ramp)).astype(int), 0, len(ramp) - 1)
    a = ramp[idx]
    return to_img(np.dstack([a, np.full((SIZE, SIZE), alpha)]))

# ======================= ГЕНЕРАТОРЫ: КАМЕНЬ/ЗЕМЛЯ =======================
PALS = {
    "stone":    [(100,100,100),(112,112,112),(93,93,95),(105,103,101)],
    "granite":  [(150,100,80),(160,110,90),(130,90,75),(155,120,105)],
    "diorite":  [(180,180,185),(150,150,155),(200,200,205),(165,160,168)],
    "andesite": [(120,122,124),(110,112,114),(128,130,130),(116,116,120)],
    "dirt":     [(120,85,55),(105,75,48),(130,95,62),(98,70,45)],
    "sand":     [(222,208,160),(214,200,150),(228,216,170),(210,196,146)],
    "red_sand": [(200,100,50),(190,90,45),(210,110,58),(185,85,42)],
    "clay":     [(158,166,180),(150,158,172),(165,172,186),(154,162,176)],
}

def g_speckle(rng, key, cell=6):
    a = stone_base(rng, PALS[key], cell)
    if key == "stone": cracks(a, rng, 3, 0.75)
    return to_img(a)

def g_polished(rng, key):
    a = stone_base(rng, PALS[key], cell=3, lo=0.94, hi=1.06)
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    band = ((xs + ys) // 16) % 3 == 0
    a[band, :3] *= 1.06
    return to_img(a)

def g_cobble(rng, grid=4, rmin=6, rmax=9, tint=(1, 1, 1), mossy=False):
    a = np.full((SIZE, SIZE, 3), 0.20) * np.array(tint)
    n = fbm(rng, cx=5)
    cell = SIZE // grid
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    for gy in range(grid):
        for gx in range(grid):
            cx = gx * cell + cell // 2 + int(rng.integers(-3, 4))
            cy = gy * cell + cell // 2 + int(rng.integers(-3, 4))
            rx = int(rng.integers(rmin, rmax + 1)); ry = int(rng.integers(rmin, rmax + 1))
            m = ((xs - cx) / rx) ** 2 + ((ys - cy) / ry) ** 2 <= 1
            b = 0.42 + 0.38 * rng.random()
            col = (b * (0.85 + 0.30 * n))[..., None] * np.array(tint)
            a[m] = col[m]
            edge = m & ~np.roll(m, 1, axis=0)
            a[edge, :3] *= 1.25
    if mossy:
        moss = fbm(rng, cx=4) > 0.62
        a[moss] = a[moss] * 0.35 + np.array([0.25, 0.45, 0.18]) * 0.65
    return to_img(a)

def g_layers(rng, pal, horizontal=True, crack=True):
    c = np.array(pal) / 255
    band = vnoise(rng, 1, SIZE, cx=10)[0]
    row = c[0] + (c[-1] - c[0]) * band[:, None]
    a = np.tile(row[:, None, :], (1, SIZE, 1)) if horizontal else np.tile(row[None, :, :], (SIZE, 1, 1))
    a = shade(a, fbm(rng, cx=6), 0.85, 1.12)
    if crack: cracks(a, rng, 4, 0.7)
    return to_img(a)

def g_bedrock(rng):
    a = stone_base(rng, [(30,30,32),(45,45,48),(20,20,22),(55,55,58)], cell=9, lo=0.6, hi=1.4)
    cracks(a, rng, 10, 0.45)
    return to_img(a)

def g_dirt(rng, coarse=False):
    a = stone_base(rng, PALS["dirt"], cell=7)
    if coarse:
        for _ in range(20):
            x, y = int(rng.integers(0, 60)), int(rng.integers(0, 60))
            a[y:y+3, x:x+3, :3] = np.array([0.55, 0.52, 0.48]) * (0.8 + 0.4 * rng.random())
    else:
        for _ in range(30):
            x, y = int(rng.integers(0, SIZE)), int(rng.integers(0, SIZE))
            a[y, x, :3] *= 0.7
    return to_img(a)

def g_podzol(rng):
    a = stone_base(rng, [(70,45,25),(60,40,22),(80,52,30)], cell=7)
    for _ in range(120):
        x, y = int(rng.integers(0, 58)), int(rng.integers(0, SIZE))
        a[y, x:x+4, :3] = np.array([0.35, 0.24, 0.12])
    return to_img(a)

def g_mycelium(rng):
    a = stone_base(rng, [(110,100,120),(100,90,110),(120,108,128)], cell=7)
    for _ in range(8):
        x, y = int(rng.integers(2, 60)), int(rng.integers(2, 60))
        a[y, x:x+2, :3] = np.array([0.75, 0.25, 0.2])
        a[y-1, x:x+2, :3] = np.array([0.85, 0.75, 0.65])
    return to_img(a)

def g_gravel(rng):
    return g_cobble(rng, grid=8, rmin=3, rmax=5, tint=(0.95, 0.92, 0.88))

def g_terracotta(rng):
    return g_layers(rng, [(170,95,60),(160,88,55),(178,102,66)], crack=False)

def g_salt(rng):
    a = stone_base(rng, [(240,240,245),(230,232,238),(248,248,250)], cell=6, lo=0.95, hi=1.05)
    for _ in range(60):
        x, y = int(rng.integers(0, SIZE)), int(rng.integers(0, SIZE))
        a[y, x, :3] = 1.0
    return to_img(a)

def g_ash(rng):
    return to_img(stone_base(rng, [(175,175,175),(165,165,166),(185,184,184)], cell=4, lo=0.93, hi=1.05))

def g_obsidian(rng, glow=False):
    a = stone_base(rng, [(15,12,22),(22,18,32),(10,8,16),(30,22,44)], cell=5, lo=0.7, hi=1.3)
    n = fbm(rng, cx=4)
    a[..., 0] += n * 0.06; a[..., 2] += n * 0.10
    if glow:
        m = np.zeros((SIZE, SIZE), bool)
        for _ in range(5):
            x, y = int(rng.integers(5, 58)), int(rng.integers(5, 58))
            for _ in range(int(rng.integers(10, 22))):
                if 0 <= x < SIZE and 0 <= y < SIZE: m[y, x] = True
                x += int(rng.integers(-1, 2)); y += int(rng.integers(0, 2))
        halo = dilate(m, 2) & ~m
        a[m] = np.array([0.75, 0.35, 1.0])
        a[halo] = a[halo] * 0.4 + np.array([0.5, 0.2, 0.8]) * 0.6
    return to_img(a)

def g_snow(rng):
    a = stone_base(rng, [(245,248,250),(238,242,246),(250,252,254)], cell=5, lo=0.96, hi=1.03)
    for _ in range(30):
        x, y = int(rng.integers(0, SIZE)), int(rng.integers(0, SIZE))
        a[y, x, :3] = 1.0
    return to_img(a)

def g_ice(rng, kind="ice"):
    if kind == "ice":
        a = stone_base(rng, [(150,185,235),(160,195,240),(140,175,228)], cell=4, lo=0.92, hi=1.08)
        cracks(a, rng, 5, 0.8)
        alpha = 0.85
    elif kind == "packed":
        a = stone_base(rng, [(140,175,230),(148,182,236),(135,170,226)], cell=3, lo=0.95, hi=1.05)
        alpha = 1.0
    else:
        a = stone_base(rng, [(70,110,220),(80,120,230),(65,100,210)], cell=3, lo=0.93, hi=1.07)
        for _ in range(6):
            y = int(rng.integers(0, SIZE)); x = int(rng.integers(0, 40))
            a[y, x:x+20, :3] = np.array([0.85, 0.9, 1.0])
        alpha = 1.0
    return to_img(np.dstack([a, np.full((SIZE, SIZE), alpha)]))

def g_wool(rng):
    a = stone_base(rng, [(235,235,235),(228,228,228),(242,242,242)], cell=5, lo=0.93, hi=1.05)
    ys, _ = np.mgrid[0:SIZE, 0:SIZE]
    a[(ys % 4) == 0, :3] *= 0.96
    return to_img(a)

# Glass interior must stay above the shader's 0.5 alpha cutout, so it is
# translucent but never discarded; the frame stays fully opaque.
def g_glass(rng):
    a = np.zeros((SIZE, SIZE, 4))
    a[..., 3] = 190
    a[..., 0] = 0.75; a[..., 1] = 0.85; a[..., 2] = 0.92
    a[:2, :] = a[-2:, :] = a[:, :2] = a[:, -2:] = np.array([0.9, 0.95, 1.0, 1.0])
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    streak = ((xs - ys) // 8) % 7 == 0
    a[streak] = np.array([1, 1, 1, 166])
    return to_img(a)

# ======================= ДЕРЕВО =======================
WOOD = {
    "oak":      (0.63, 0.50, 0.30), "spruce":   (0.45, 0.33, 0.19),
    "birch":    (0.84, 0.80, 0.66), "jungle":   (0.66, 0.45, 0.24),
    "acacia":   (0.75, 0.37, 0.20), "dark_oak": (0.26, 0.18, 0.10),
}
STYLES = ("raw", "dried", "sealed", "charred", "reinforced", "composite", "waxed", "lacquered")

def planks_arr(rng, wood, style="normal"):
    base = np.array([0.09, 0.08, 0.08]) if style == "charred" else np.array(wood, float)
    g = np.repeat(vnoise(rng, SIZE, SIZE // 4, cx=2, cy=8), 4, axis=1)[:, :SIZE]
    a = np.tile(base, (SIZE, SIZE, 1)) * (0.80 + 0.40 * g)[..., None]
    if style == "raw":
        a *= (0.85 + 0.30 * fbm(rng, cx=10))[..., None]
    for sy in (15, 31, 47):
        a[sy, :, :3] *= 0.5
    for bi in range(4):
        sx = int(rng.integers(10, 54))
        a[bi * 16:bi * 16 + 15, sx, :3] *= 0.55
    if style == "dried":
        cracks(a, rng, 7, 0.6)
    if style in ("sealed", "waxed", "lacquered"):
        k = {"sealed": 0.10, "waxed": 0.16, "lacquered": 0.24}[style]
        ys, xs = np.mgrid[0:SIZE, 0:SIZE]
        band = ((xs + ys) // 10) % 4 == 0
        a[band, :3] *= 1 + k
    if style == "reinforced":
        for x0 in (9, 51):
            a[:, x0:x0 + 4, :3] = np.array([0.28, 0.28, 0.30])
            for yy in range(4, 60, 10):
                a[yy, x0 + 1:x0 + 3, :3] = np.array([0.5, 0.5, 0.55])
    if style == "composite":
        for bi in range(4):
            a[bi * 16:(bi + 1) * 16, :, :3] *= 0.9 if bi % 2 else 1.08
    return a

def g_planks(rng, wood, style="normal"):
    return to_img(planks_arr(rng, WOOD[wood], style))

def g_log_side(rng, wood, birch=False):
    base = np.array(WOOD[wood], float)
    if birch:
        base = np.array([0.90, 0.90, 0.88])
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    a = np.tile(base[None, None, :], (SIZE, SIZE, 1))
    # Цилиндрическое затенение: свет слева, тень справа
    a *= (0.84 + 0.22 * np.sin((xs / SIZE) * np.pi))[..., None]
    # Мелкое вертикальное волокно: шум по колонкам + тонкие прожилки
    col = vnoise(rng, 1, SIZE, cx=20)[0]
    a *= (0.85 + 0.30 * col[:, None])[..., None]
    for _ in range(34):
        x = float(rng.integers(1, SIZE - 1))
        w = 1 + int(rng.integers(0, 2))
        dark = rng.random() < 0.65
        wig = 0.0
        for i in range(SIZE):
            wig += float(rng.integers(-1, 2)) * 0.4
            xx = int(np.clip(x + wig, 0, SIZE - 1))
            x0 = max(0, xx - w // 2)
            a[i, x0:x0 + w, :3] *= 0.80 if dark else 1.18
    # Сучки: тёмное ядро в светлом кольце, как срезанные ветки
    for _ in range(3):
        kx, ky = float(rng.integers(5, 59)), float(rng.integers(5, 59))
        kr = 2.0 + float(rng.random() * 1.5)
        for i in range(SIZE):
            for j in range(SIZE):
                d = np.hypot((i - ky) * 0.6, j - kx)
                if d < kr:
                    if d < kr * 0.5:
                        a[i, j, :3] *= 0.42
                    elif d < kr * 0.85:
                        a[i, j, :3] *= 0.72
                    else:
                        a[i, j, :3] *= 1.12
    if birch:
        # Тонкие чёрные чёрточки белой коры, чуть наклонённые
        for _ in range(34):
            x, y = int(rng.integers(2, 58)), int(rng.integers(2, 58))
            ln = int(rng.integers(3, 9))
            step = 1 if rng.random() < 0.7 else -1
            for k in range(ln):
                yy = y + (k * step) // 3
                if 0 <= yy < SIZE and x + k < SIZE:
                    a[yy, x + k, :3] = np.array([0.10, 0.10, 0.09])
    else:
        cracks(a, rng, 4, 0.7)
    return to_img(a)

def g_log_top(rng, wood):
    wd = np.array(WOOD[wood]) * 0.6
    wl = np.array(WOOD[wood]) * 1.15
    bark = np.array(WOOD[wood]) * 0.45
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    r = np.hypot(xs - 31.5, ys - 31.5)
    v = 0.5 + 0.5 * np.sin(r * 0.9 + fbm(rng, cx=5) * 2.5)
    a = wd + (wl - wd) * v[..., None]
    a[r > 28] = bark
    a[(r > 25) & (r <= 28), :3] *= 0.85
    # Тонкие кольца годового прироста и радиальные трещины
    ring = np.abs(np.sin(r * 0.9 + fbm(rng, cx=5) * 2.5)) < 0.08
    a[ring & (r < 26), :3] *= 0.82
    for _ in range(3):
        ang = rng.random() * math.pi
        for rr in range(4, 26):
            xx = int(31.5 + rr * math.cos(ang))
            yy = int(31.5 + rr * math.sin(ang))
            if 0 <= xx < SIZE and 0 <= yy < SIZE:
                a[yy, xx, :3] *= 0.55
    return to_img(a)

def g_slab(rng, base_img):
    a = np.array(base_img.convert('RGB')) / 255
    a[SIZE // 2:, :, :] *= 0.82
    a[SIZE // 2, :, :] *= 0.6
    return to_img(a)

def g_door(rng, wood, top, sliding=False):
    if sliding:
        a = shade(np.tile(np.array([0.55, 0.56, 0.58]), (SIZE, SIZE, 1)), fbm(rng, cx=6), 0.9, 1.08)
        for yy in (6, 56):
            for xx in (6, 56):
                a[yy:yy+3, xx:xx+3, :3] = np.array([0.35, 0.35, 0.38])
    else:
        a = planks_arr(rng, WOOD[wood])
    a[:, :2, :3] *= 0.6; a[:, -2:, :3] *= 0.6
    a[:2, :, :3] *= 0.6; a[-2:, :, :3] *= 0.6
    if top:
        a[12:32, 16:48, :3] = np.array([0.70, 0.85, 0.95])
        a[21:23, 16:48, :3] = np.array([0.3, 0.25, 0.18])
        a[12:32, 31:33, :3] = np.array([0.3, 0.25, 0.18])
    else:
        a[30:36, 42:48, :3] = np.array([0.25, 0.25, 0.28])
    return to_img(a)

def g_bookshelf(rng):
    a = planks_arr(rng, WOOD["oak"])
    colors = [(0.7,0.2,0.2),(0.2,0.3,0.7),(0.25,0.55,0.25),(0.55,0.4,0.2),(0.5,0.25,0.6),(0.8,0.8,0.75)]
    for y0, y1 in ((6, 27), (36, 57)):
        x = 4
        while x < 58:
            w = int(rng.integers(4, 8))
            c = np.array(colors[int(rng.integers(0, len(colors)))])
            a[y0+1:y1, x:x+w-1, :3] = c * (0.8 + 0.4 * rng.random())
            x += w + 1
    return to_img(a)

def g_crafting(rng):
    a = planks_arr(rng, WOOD["oak"])
    a[:20, :, :3] *= 0.85
    a[10, 8:56, :3] = np.array([0.2, 0.15, 0.1])
    a[2:20, 32, :3] = np.array([0.2, 0.15, 0.1])
    a[5:8, 12:20, :3] = np.array([0.6, 0.6, 0.62])
    a[13:17, 40:46, :3] = np.array([0.5, 0.35, 0.2])
    return to_img(a)

def g_hay(rng, top=False):
    if top:
        ys, xs = np.mgrid[0:SIZE, 0:SIZE]
        ang = np.arctan2(ys - 31.5, xs - 31.5)
        v = 0.5 + 0.5 * np.sin(ang * 9 + fbm(rng, cx=5) * 3)
        c1, c2 = np.array([0.55, 0.45, 0.15]), np.array([0.9, 0.78, 0.3])
        return to_img(c1 + (c2 - c1) * v[..., None])
    g = np.repeat(vnoise(rng, SIZE // 4, SIZE, cx=8), 4, axis=0)[:SIZE]
    a = np.array([0.85, 0.70, 0.25]) * (0.8 + 0.4 * g)[..., None] * np.ones((SIZE, SIZE, 3))
    a[14:18, :, :3] *= 0.6; a[46:50, :, :3] *= 0.6
    return to_img(a)

# ======================= РУДЫ И МИНЕРАЛЫ =======================
ORES = {
    "coal": (0.13, 0.13, 0.13), "iron": (0.85, 0.70, 0.55),
    "gold": (0.98, 0.83, 0.25), "copper": (0.85, 0.50, 0.30),
    "diamond": (0.35, 0.90, 0.88), "emerald": (0.20, 0.80, 0.35),
    "redstone": (0.95, 0.15, 0.10), "lapis": (0.15, 0.25, 0.75),
}
# Файл (имя тайла в игре) -> вид руды
ORE_FILES = {
    "ore_coal": "coal", "ore_iron": "iron", "ore_gold": "gold", "ore_diamond": "diamond",
    "emerald_ore": "emerald", "redstone_ore": "redstone",
    "lapis_ore": "lapis", "copper_ore": "copper",
}

def g_ore(rng, kind):
    col = np.array(ORES[kind])
    a = stone_base(rng, PALS["stone"])
    m = np.zeros((SIZE, SIZE), bool)
    for _ in range(6):
        x, y = int(rng.integers(6, 58)), int(rng.integers(6, 58))
        for _ in range(int(rng.integers(5, 12))):
            if 0 <= x < SIZE and 0 <= y < SIZE:
                m[y, x] = True
                a[y, x, :3] = col * (0.75 + 0.5 * rng.random())
            x += int(rng.integers(-1, 2)); y += int(rng.integers(-1, 2))
    if kind == "redstone":
        halo = dilate(m, 2) & ~m
        a[halo] = a[halo] * 0.45 + col * 0.35
    return to_img(a)

def g_mineral(rng, col):
    a = shade(np.tile(np.array(col), (SIZE, SIZE, 1)), fbm(rng, cx=3), 0.92, 1.06)
    a[:2, :, :3] *= 1.25; a[:, :2, :3] *= 1.18
    a[-3:, :, :3] *= 0.75; a[:, -3:, :3] *= 0.8
    for _ in range(25):
        x, y = int(rng.integers(0, SIZE)), int(rng.integers(0, SIZE))
        a[y, x, :3] = np.minimum(1.0, a[y, x, :3] + 0.35)
    return to_img(a)

def g_bricks(rng, pal, mortar=(0.36, 0.34, 0.33), bh=16, bw=32):
    a = np.tile(np.array(mortar), (SIZE, SIZE, 1))
    row = 0
    for y0 in range(0, SIZE, bh):
        off = 0 if row % 2 == 0 else bw // 2
        for x0 in range(-bw, SIZE + bw, bw):
            x1 = x0 + off
            xa, xb = max(0, x1 + 1), min(SIZE, x1 + bw - 1)
            ya, yb = y0 + 1, min(SIZE, y0 + bh - 1)
            if xa < xb and ya < yb:
                c = np.array(pal[int(rng.integers(0, len(pal)))]) / 255
                n = fbm(rng, yb - ya, xb - xa, 3)
                a[ya:yb, xa:xb] = c[None, None, :] * (0.85 + 0.30 * n)[..., None]
        row += 1
    return to_img(a)

def g_concrete(rng, col):
    return to_img(shade(np.tile(np.array(col), (SIZE, SIZE, 1)), fbm(rng, cx=3), 0.97, 1.03))

def g_crystal(rng):
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(32, 3), (58, 32), (32, 61), (6, 32)], fill=(120, 40, 180, 255))
    d.polygon([(32, 3), (58, 32), (32, 32)], fill=(150, 70, 210, 255))
    d.line([(32, 3), (32, 61)], fill=(190, 100, 235, 255), width=2)
    d.line([(6, 32), (58, 32)], fill=(80, 20, 130, 255), width=2)
    for _ in range(12):
        d.point((int(rng.integers(10, 54)), int(rng.integers(10, 54))), fill=(255, 255, 255, 255))
    return img

def g_reactor(rng):
    a = stone_base(rng, [(40, 38, 50), (48, 45, 58), (35, 33, 44)], cell=5)
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    d = np.abs(xs - 31.5) + np.abs(ys - 31.5)
    core = d < 13
    a[core] = np.array([0.9, 0.2, 0.1]) * np.clip(1.2 - d[core] / 13, 0.4, 1.2)[..., None]
    a[d < 4] = np.array([1.0, 0.6, 0.2])
    return to_img(a)

def g_spawner(rng):
    a = stone_base(rng, [(25, 25, 30), (35, 35, 42), (18, 18, 22)], cell=6)
    a[:, ::8, :3] *= 0.5
    a[::8, :, :3] *= 0.5
    for t in np.linspace(0, 4 * math.pi, 140):
        rr = 2 + t * 1.9
        xx, yy = int(32 + rr * math.cos(t)), int(32 + rr * math.sin(t))
        if 0 <= xx < SIZE and 0 <= yy < SIZE:
            a[yy, xx, :3] = np.array([0.55, 0.25, 0.85])
    return to_img(a)

def g_portal_frame(rng):
    a = stone_base(rng, [(60, 70, 60), (50, 60, 52), (70, 80, 68)], cell=5)
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    r = np.hypot(xs - 32, ys - 32)
    eye = r < 10
    a[eye] = np.array([0.2, 0.9, 0.4]) * np.clip(1.3 - r[eye] / 10, 0.5, 1.3)[..., None]
    halo = (r < 14) & ~eye
    a[halo] = a[halo] * 0.5 + np.array([0.1, 0.5, 0.2]) * 0.5
    return to_img(a)

def g_end_portal(rng):
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    ang = np.arctan2(ys - 32, xs - 32)
    r = np.hypot(xs - 32, ys - 32)
    v = ((ang / math.pi + r / 9 + fbm(rng, cx=5)) % 2) / 2
    c1, c2 = np.array([0.02, 0.03, 0.02]), np.array([0.05, 0.4, 0.18])
    a = c1 + (c2 - c1) * v[..., None]
    for _ in range(20):
        x, y = int(rng.integers(0, SIZE)), int(rng.integers(0, SIZE))
        a[y, x, :3] = np.array([0.6, 1.0, 0.7])
    return to_img(a)

# ======================= РАСТЕНИЯ (прозрачный фон) =======================
def blank():
    return np.zeros((SIZE, SIZE, 4))

# Трава НЕ тинтуется биомом (BiomeColors.isFoliage исключает GRASS_BLOCK),
# поэтому цвет полностью задаёт PNG. Чистая сочная зелень MC-стиля: плавный
# шум + лёгкие светлые штрихи и тёмные пятна, без грязных тёмных зон.
def g_grass_top(rng):
    a = stone_base(rng, [(120,196,74),(106,182,62),(132,206,84),(96,170,54)], cell=7, lo=0.90, hi=1.12)
    streaks = vnoise(rng, SIZE, SIZE, cx=11) > 0.62
    a[streaks, :3] *= 1.10
    dark = vnoise(rng, SIZE, SIZE, cx=5) < 0.12
    a[dark, :3] *= 0.88
    return to_img(a)

def g_grass_side(rng):
    a = stone_base(rng, PALS["dirt"], cell=7)
    h = (10 + vnoise(rng, 1, SIZE, cx=6)[0] * 10).astype(int)
    for x in range(SIZE):
        a[:h[x], x, :3] = np.array([0.48, 0.82, 0.32]) * (0.85 + 0.3 * rng.random())
        a[h[x], x, :3] = np.array([0.62, 0.95, 0.40])
    return to_img(a)

def g_blades(rng, count, hmin, hmax, col, wavy=False):
    a = blank()
    c = np.array(col)
    for i in range(count):
        x = int(rng.integers(6, 58)); h = int(rng.integers(hmin, hmax))
        for t in range(h):
            y = SIZE - 1 - t
            xx = x + (int(round(3 * math.sin(t / 4))) if wavy else 0)
            if 0 <= xx < SIZE:
                a[y, xx] = [c[0] * (0.8 + 0.4 * t / h), c[1] * (0.8 + 0.4 * t / h), c[2], 1]
    return to_img(a)

def g_flower(rng, petal, head=6, h=36, center=(0.2, 0.15, 0.05)):
    a = blank()
    x = 32 + int(rng.integers(-6, 7))
    top = SIZE - 1 - h
    for y in range(top, SIZE):
        a[y, x] = [0.2, 0.5, 0.15, 1]
    a[SIZE - h // 2, x - 2:x] = [0.25, 0.55, 0.2, 1]
    p = np.array(petal); c0 = np.array(center)
    for dy in range(-head // 2, head // 2 + 1):
        for dx in range(-head // 2, head // 2 + 1):
            if dx * dx + dy * dy <= (head // 2) ** 2:
                yy, xx = top + dy, x + dx
                if 0 <= xx < SIZE and 0 <= yy < SIZE:
                    a[yy, xx] = [*(c0 if dx == dy == 0 else p), 1]
    return to_img(a)

def g_sunflower(rng):
    a = np.array(g_flower(rng, (1.0, 0.8, 0.1), head=11, h=52, center=(0.35, 0.22, 0.08))) / 255
    return to_img(a)

def g_lavender(rng):
    a = blank()
    for x in (22, 32, 42):
        h = int(rng.integers(30, 40)); top = SIZE - 1 - h
        for y in range(top, SIZE):
            a[y, x] = [0.25, 0.5, 0.2, 1]
        for y in range(top, top + 10):
            if 0 <= y < SIZE:
                a[y, x - 1:x + 2] = [0.55, 0.35, 0.8, 1]
    return to_img(a)

def g_dead_bush(rng):
    a = blank()
    for _ in range(6):
        x, y = 32 + int(rng.integers(-4, 5)), SIZE - 1
        for _ in range(int(rng.integers(18, 30))):
            if 0 <= x < SIZE and 0 <= y < SIZE:
                a[y, x] = [0.5, 0.35, 0.18, 1]
            x += int(rng.integers(-1, 2)); y -= int(rng.integers(0, 2))
    return to_img(a)

def g_crop(rng, kind, s):
    a = blank()
    n = min(2 + s, 7)
    for i in range(n):
        x = int(8 + i * (48 / max(1, n - 1)) + rng.integers(-2, 3)) if n > 1 else 32
        h = min(int(10 + s * 6.5 - rng.integers(0, 5)), 58)
        t = s / 7
        if kind == "wheat" and s >= 6: col = np.array([0.85, 0.75, 0.30])
        else: col = np.array([0.25 + 0.15 * t, 0.55 + 0.15 * t, 0.18])
        sway = int(rng.integers(-3, 4))
        for t2 in range(h):
            y = SIZE - 1 - t2
            xx = x + (t2 * sway) // h
            if 0 <= xx < SIZE:
                a[y, xx] = [*col * (0.75 + 0.5 * t2 / h), 1]
        if kind == "wheat" and s >= 5:
            for dy in range(4):
                yy = SIZE - 1 - h + dy
                if 0 <= yy < SIZE:
                    a[yy, max(0, x - 1):x + 2] = [0.9, 0.8, 0.35, 1]
        if s == 7 and kind == "carrot":
            a[SIZE - 2, x] = [0.95, 0.5, 0.1, 1]
        if s == 7 and kind == "potato":
            a[SIZE - h, min(63, x + 1)] = [0.9, 0.85, 0.7, 1]
    return to_img(a)

def g_cactus(rng, top=False):
    if top:
        a = blank()
        ys, xs = np.mgrid[0:SIZE, 0:SIZE]
        m = np.hypot(xs - 32, ys - 32) < 26
        a[m] = [0.25, 0.55, 0.2, 1]
        a[(np.hypot(xs - 32, ys - 32) > 22) & m] = [0.18, 0.42, 0.15, 1]
        return to_img(a)
    a = np.zeros((SIZE, SIZE, 4))
    g = np.repeat(vnoise(rng, SIZE, 8, cx=2, cy=8), 8, axis=1)[:, :SIZE]
    col = np.array([0.22, 0.52, 0.18]) * (0.8 + 0.4 * g)[..., None]
    a[:, 6:58, :3] = col[:, 6:58]
    a[:, 6:58, 3] = 255
    for xx in (6, 23, 40, 57):
        a[:, xx, :3] *= 0.6
    for _ in range(40):
        x, y = int(rng.choice([6, 57])), int(rng.integers(0, SIZE))
        a[y, x + (1 if x == 6 else -1) * 0] = [0.9, 0.95, 0.8, 1]
    return to_img(a)

def g_lily(rng):
    a = blank()
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    ang = np.arctan2(ys - 34, xs - 32)
    m = (np.hypot(xs - 32, ys - 34) < 26) & ~((ang > -0.25) & (ang < 0.25))
    n = fbm(rng, cx=6)
    sv = 0.8 + 0.4 * n[m]
    a[m] = np.stack([0.2 * sv, 0.55 * sv, 0.18 * sv, np.ones_like(sv)], axis=1)
    return to_img(a)

def g_vine(rng):
    a = blank()
    for _ in range(7):
        x = int(rng.integers(4, 60)); ln = int(rng.integers(18, 50))
        for t in range(ln):
            y = t
            xx = x + int(round(2 * math.sin(t / 5)))
            if 0 <= xx < SIZE:
                a[y, xx] = [0.2, 0.5, 0.15, 1]
                if t % 3 == 0:
                    a[y, xx + (1 if t % 6 else -1)] = [0.25, 0.58, 0.2, 1]
    return to_img(a)

def g_bamboo(rng):
    a = blank()
    n = vnoise(rng, SIZE, 1, cx=8)[:, 0]
    for y in range(SIZE):
        a[y, 28:36] = [0.3 * (0.8 + 0.4 * n[y]), 0.62 * (0.8 + 0.4 * n[y]), 0.18, 1]
    for y0 in range(6, SIZE, 14):
        a[y0, 27:37] = [0.15, 0.4, 0.12, 1]
    return to_img(a)

def g_coral(rng):
    a = blank()
    cols = [(1.0, 0.4, 0.5), (1.0, 0.55, 0.3), (0.95, 0.35, 0.6)]
    for _ in range(8):
        x, y = 32 + int(rng.integers(-8, 9)), 60
        c = np.array(cols[int(rng.integers(0, 3))])
        for _ in range(int(rng.integers(20, 36))):
            if 0 <= x < SIZE and 0 <= y < SIZE:
                a[y, x] = [*c, 1]; a[y, min(63, x + 1)] = [*c * 0.8, 1]
            x += int(rng.integers(-1, 2)); y -= 1
    return to_img(a)

def g_moss(rng):
    a = stone_base(rng, [(70, 120, 45), (60, 108, 40), (80, 132, 52)], cell=6)
    return to_img(a)

def g_dripstone(rng):
    a = blank()
    for cx, ln, w in ((18, 40, 7), (40, 52, 9), (54, 30, 5)):
        for y in range(ln):
            half = max(1, int(w * (1 - y / ln) + rng.integers(-1, 2)))
            shade_v = 0.75 + 0.3 * rng.random()
            a[y, cx - half:cx + half] = [0.45 * shade_v, 0.38 * shade_v, 0.33 * shade_v, 1]
    return to_img(a)

def g_torch(rng):
    a = blank()
    a[30:58, 30:34] = [0.45, 0.32, 0.15, 1]
    a[30:58, 30, :3] *= 0.7
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    d = np.hypot(xs - 32, ys - 22)
    flame = d < 8
    a[flame] = [1.0, 0.75, 0.2, 1]
    a[d < 4] = [1.0, 0.95, 0.55, 1]
    halo = (d < 13) & ~flame
    a[halo] = [1.0, 0.85, 0.3, 0.30]
    return to_img(a)

def g_fire(rng):
    a = blank()
    hn = 18 + fbm(rng, 1, SIZE, cx=5)[0] * 34
    for x in range(SIZE):
        for y in range(SIZE):
            t = (SIZE - 1 - y) / max(6, hn[x])
            if 0 <= t <= 1:
                c = np.array([1.0, 0.25 + 0.65 * (1 - t), 0.05 + 0.3 * (1 - t) ** 2])
                a[y, x] = [*c, 1 - 0.65 * t]
    return to_img(a)

def g_rails(rng):
    a = blank()
    for y0 in (10, 30, 50):
        a[y0:y0 + 5, 8:56] = [0.45, 0.33, 0.18, 1]
    for x0 in (20, 40):
        a[:, x0:x0 + 4] = [0.5, 0.5, 0.52, 1]
        a[:, x0 + 1] = [0.7, 0.7, 0.72, 1]
    return to_img(a)

# ======================= МЕХАНИКА =======================
def g_furnace(rng, top=False):
    a = stone_base(rng, [(95, 95, 97), (105, 105, 107), (88, 88, 90)], cell=6)
    if top:
        a[:3, :, :3] *= 0.8; a[-3:, :, :3] *= 0.8
        a[:, :3, :3] *= 0.8; a[:, -3:, :3] *= 0.8
    else:
        ys, xs = np.mgrid[0:SIZE, 0:SIZE]
        m = ((ys > 34) & (ys < 54) & (xs > 20) & (xs < 44)) | \
            (np.hypot(xs - 32, ys - 34) < 11)
        a[m] = [0.05, 0.05, 0.05]
        a[50:54, 22:42, :3] = [0.2, 0.2, 0.2]
    return to_img(a)

def g_chest(rng, top=False):
    a = planks_arr(rng, WOOD["oak"]) * 0.9
    a[:, :3, :3] *= 0.6; a[:, -3:, :3] *= 0.6
    a[:3, :, :3] *= 0.6; a[-3:, :, :3] *= 0.6
    if not top:
        a[29:32, :, :3] *= 0.5
        a[26:36, 29:35, :3] = np.array([0.45, 0.45, 0.48])
        a[30:33, 31:33, :3] = np.array([0.2, 0.2, 0.22])
    return to_img(a)

def g_tnt(rng):
    a = stone_base(rng, [(200, 60, 45), (190, 55, 40), (210, 68, 50)], cell=6)
    a[24:40, :, :3] = np.array([0.92, 0.90, 0.85])
    img = to_img(a).convert('RGBA')
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.load_default(size=13)
    except TypeError:
        font = ImageFont.load_default()
    d.text((21, 26), "TNT", fill=(20, 20, 20, 255), font=font)
    return img

# [BASE] Фонарь: металлическая рама вокруг светящегося стеклянного ядра.
def g_lantern(rng, glass=(0.35, 0.30, 0.15), glow=(1.0, 0.97, 0.82)):
    glass = np.array(glass, np.float64); glow = np.array(glow, np.float64)
    a = np.zeros((SIZE, SIZE, 3), np.float64)
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    frame = np.array([0.23, 0.23, 0.25]); dark = frame * 0.75; light = frame * 1.35
    ring = (xs <= 2) | (xs >= SIZE - 3) | (ys <= 2) | (ys >= SIZE - 3)
    post = ((xs == 4) | (xs == SIZE - 5)) & (ys >= 3) & (ys < SIZE - 3)
    a[ring] = dark[None, :] if ring.any() else 0
    a[post] = frame
    a[ys <= 2] = dark  # верхняя планка
    d = np.hypot(xs - SIZE / 2, ys - SIZE / 2)
    core = d < 4.2
    a[core] = glass
    hot = d < 1.6
    a[hot] = glow
    mid = d < 2.6
    a[mid & ~hot] = glass * 1.35
    rim = (d >= 4.2) & (d < 5.4)
    a[rim] = light
    a[1, SIZE // 2] = dark
    n = fbm(rng, cx=3) * 0.06
    a += n[..., None]
    return to_img(np.clip(a, 0, 1))

def g_ice_lantern(rng):
    return g_lantern(rng, glass=(0.45, 0.72, 0.95), glow=(0.92, 0.98, 1.0))

# [BASE] Ящик: деревянные доски с диагональной скобой.
def g_crate(rng):
    a = planks_arr(rng, WOOD["oak"])
    a[::8, :, :3] *= 0.6; a[7::8, :, :3] *= 0.6  # поперечные ленты
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    for off in (0, 8):
        m = np.abs((xs - ys) - off) <= 2
        a[m, :3] *= 0.55
        m2 = np.abs((xs + ys) - (SIZE + off)) <= 2
        a[m2, :3] *= 0.55
    return to_img(a)

# [BASE] Костёр: два скрещенных бревна и пламя над ними.
def g_campfire(rng):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    a = np.array(img)
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    log = np.array([0.42, 0.26, 0.15])
    logdark = log * 0.85
    for off in (-3, 0, 3):
        m = np.abs((xs - ys) - (SIZE // 2 + off)) <= 3
        m &= ys >= SIZE * 0.68
        a[m] = [int(logdark[0] * 255), int(logdark[1] * 255), int(logdark[2] * 255), 255]
        m2 = np.abs((xs + ys) - (SIZE // 2 + off)) <= 3
        m2 &= ys >= SIZE * 0.68
        a[m2] = [int(log[0] * 255), int(log[1] * 255), int(log[2] * 255), 255]
    flame = g_fire(_rng("campfire_fire")).convert("RGBA")
    fa = np.array(flame).astype(np.float64) / 255
    mask = fa[..., 3] > 0.1
    a[mask, :3] = (fa[mask, :3] * 255).astype(int)
    a[mask, 3] = 255
    return Image.fromarray(a)

# [BASE] Снежные кирпичи: ледяной камень с раствором.
def g_snow_bricks(rng):
    pal = [(232, 238, 242), (218, 228, 234), (244, 250, 252), (208, 220, 228)]
    return g_bricks(rng, pal, (180, 196, 208), 8, 16)

# [BASE] Песчаниковые кирпичи: тёплый песчаник.
def g_sandstone_bricks(rng):
    pal = [(216, 198, 150), (204, 186, 140), (228, 212, 165), (196, 178, 132)]
    return g_bricks(rng, pal, (168, 148, 105), 8, 16)
LEAVES_COL = {
    "oak": (0.52, 0.82, 0.40), "spruce": (0.45, 0.80, 0.48),
    "birch": (0.42, 0.70, 0.20), "jungle": (0.52, 0.82, 0.40),
    "autumn": (0.80, 0.35, 0.05), "cherry": (0.82, 0.45, 0.62),
}

def _leaf_mask(xs, ys, cx, cy, a, b, ang, pinch=0.30):
    """Вытянутая листовая пластина с прищипнутым кончиком."""
    c, s = math.cos(ang), math.sin(ang)
    u = (xs - cx) * c + (ys - cy) * s
    v = -(xs - cx) * s + (ys - cy) * c
    w = v / (1 - pinch * np.clip(u / a, -1, 1) ** 2)
    return (u / a) ** 2 + (w / b) ** 2 <= 1, u, v

def _blit_leaf(a, m, col, alpha, wrap=True):
    """Заливает маску листа с заворотом через края тайла (бесшовность)."""
    ys, xs = np.where(m)
    for dx in ((0, SIZE) if wrap else (0,)):
        for dy in ((0, SIZE) if wrap else (0,)):
            yy = (ys + dy) % SIZE
            xx = (xs + dx) % SIZE
            a[yy, xx, :3] = col
            a[yy, xx, 3] = alpha

def g_leaves(rng, kind, opaque=False):
    base = np.array(LEAVES_COL[kind])
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    # фон: тёмные щели между листьями (прозрачные для fancy-режима)
    a = np.zeros((SIZE, SIZE, 4))
    a[..., :3] = base * 0.30
    a[..., 3] = 255 if opaque else 0
    # задний слой: тёмные листья в глубине, дают густоту кроны
    for _ in range(24):
        cx, cy = rng.random() * SIZE, rng.random() * SIZE
        a0, b0 = int(rng.integers(10, 15)), int(rng.integers(9, 13))
        m, u, v = _leaf_mask(xs, ys, cx, cy, a0, b0, rng.random() * math.pi)
        d = np.sqrt((u / a0) ** 2 + (v / b0) ** 2)
        shade = np.clip(1.15 - 0.55 * d, 0.30, 1.05)
        _blit_leaf(a, m, (base * shade[..., None] * 0.78).clip(0, 1)[m], 255)
    # передний слой: яркие листья с бликом сверху и тёмной кромкой
    for _ in range(17):
        cx, cy = rng.random() * SIZE, rng.random() * SIZE
        a0, b0 = int(rng.integers(9, 13)), int(rng.integers(8, 12))
        m, u, v = _leaf_mask(xs, ys, cx, cy, a0, b0, rng.random() * math.pi)
        d = np.sqrt((u / a0) ** 2 + (v / b0) ** 2)
        shade = np.clip((1.45 - 0.60 * d) * (1 - 0.22 * (v / b0)), 0.35, 1.60)
        rim = m & (d > 0.78)
        shade[rim] *= 0.72
        _blit_leaf(a, m, (base * shade[..., None]).clip(0, 1)[m], 255)
    # Мелкая прорисовка: отдельные листики и тёмные щели между ними
    for _ in range(160):
        cx, cy = int(rng.integers(0, SIZE)), int(rng.integers(0, SIZE))
        if a[cy, cx, 3] == 0:
            continue
        if rng.random() < 0.55:
            a[cy, cx, :3] = (base * (1.30 + 0.35 * rng.random())).clip(0, 1)
        else:
            a[cy, cx, :3] = base * (0.45 + 0.15 * rng.random())
    for _ in range(60):
        cx, cy = int(rng.integers(2, SIZE - 2)), int(rng.integers(2, SIZE - 2))
        ln = int(rng.integers(1, 3))
        for k in range(ln):
            if a[cy, cx + k, 3] > 0:
                a[cy, cx + k, :3] = base * 0.22
            if a[cy + k, cx, 3] > 0:
                a[cy + k, cx, :3] = base * 0.22
    return to_img(a)

# ======================= СПЕЦИФИКАЦИЯ БЛОКОВ (промты) =======================
SPECS = []
def spec(name, prompt=""): SPECS.append((name, prompt))

for n, p in [
    ("stone","grey cracked stone with subtle noise"), ("granite","speckled grey-pink granite"),
    ("polished_granite","smooth polished granite"), ("diorite","speckled grey-white diorite"),
    ("polished_diorite","smooth polished diorite"), ("andesite","fine-grained grey andesite"),
    ("polished_andesite","smooth polished andesite"), ("cobblestone","rounded cobblestones with dark mortar"),
    ("mossy_cobblestone","cobblestones with moss"), ("deepslate","dark layered stone"),
    ("bedrock","very dark rough stone"), ("dirt","brown soil with pebbles"),
    ("coarse_dirt","dirt with gravel"), ("podzol","dark soil with needles"),
    ("mycelium_top","purple-grey mushroom soil"), ("sand","pale yellow sand"), ("red_sand","red-orange sand"),
    ("gravel","grey gravel"), ("clay","smooth light clay"), ("terracotta","orange-brown terracotta"),
    ("salt","white crystalline salt"), ("ash","light grey ash"), ("basalt","dark columnar basalt"),
    ("obsidian","black volcanic glass"), ("glowing_obsidian","black glass with purple cracks"),
    ("snow","white snow"), ("ice","pale blue ice"), ("packed_ice","solid pale ice"),
    ("blue_ice","deep blue ice"), ("white_wool","soft white wool"), ("glass","transparent glass"),
]: spec(n, p)

# Варианты для ломающих повторение тайлов (игра просит name#1..#3)
for n in ("stone#1", "stone#2", "stone#3", "sand#1", "sand#2", "sand#3"):
    spec(n)

for w in ("oak", "spruce", "birch", "jungle"):
    spec(f"{w}_log", f"{w} bark"); spec(f"{w}_log_top", f"{w} log rings")
    for st in ("",) + STYLES:
        spec(f"{w}{('_' + st) if st else ''}_planks")
spec("birch_log_side", "white birch bark")
for n in ("acacia_planks", "dark_oak_planks"): spec(n)
spec("oak_door_bottom"); spec("oak_door_top")
spec("sliding_door_bottom"); spec("sliding_door_top")
spec("bookshelf"); spec("crafting_table")
spec("hay_bale"); spec("hay_bale_top")
spec("charred_log"); spec("charred_log_top")
spec("petrified_log"); spec("petrified_log_top")

for k in ORE_FILES: spec(k)
for k in ("coal", "iron", "gold", "diamond", "emerald"): spec(f"{k}_block")
spec("sandstone"); spec("chiseled_sandstone")
spec("stone_bricks"); spec("brick")
spec("netherrack"); spec("soul_sand"); spec("nether_bricks"); spec("glowstone")
spec("end_stone"); spec("purpur_block")
for c in ("white", "red", "green", "blue"): spec(f"{c}_concrete")
spec("crystal"); spec("nether_reactor"); spec("mob_spawner")
spec("end_portal_frame"); spec("end_portal")

for n, p in [
    ("grass_top","neutral green grass"), ("grass_side","dirt with grass overhang"),
    ("grass_plant","thin grass blades"), ("dandelion","yellow flower"), ("poppy","red flower"),
    ("sunflower","tall sunflower"), ("lavender","purple sprig"), ("dead_bush","dry twigs"),
    ("cactus_side","green cactus"), ("cactus_top","cactus top"),
    ("lily_pad","lily pad"), ("vine","hanging vine"), ("bamboo","bamboo stalk"),
    ("coral","coral fan"), ("seagrass","underwater grass"), ("moss_block","green moss"),
    ("dripstone","stalactite"), ("torch","glowing torch"), ("fire","flames"), ("rails","metal rails"),
]: spec(n, p)
for kind in ("wheat", "carrot", "potato"):
    for s in range(8): spec(f"{kind}_stage{s}")
for n in ("oak_leaves", "spruce_leaves", "jungle_leaves",
          "oak_leaves#1", "oak_leaves#2", "oak_leaves#3",
          "oak_leaves_opaque", "oak_leaves_opaque#1", "oak_leaves_opaque#2", "oak_leaves_opaque#3",
          "spruce_leaves#1", "spruce_leaves#2", "spruce_leaves#3",
          "spruce_leaves_opaque", "spruce_leaves_opaque#1", "spruce_leaves_opaque#2", "spruce_leaves_opaque#3",
          "jungle_leaves#1", "jungle_leaves#2", "jungle_leaves#3",
          "jungle_leaves_opaque", "jungle_leaves_opaque#1", "jungle_leaves_opaque#2", "jungle_leaves_opaque#3"):
    spec(n)

spec("furnace_side"); spec("furnace_top")
spec("chest"); spec("chest_top"); spec("tnt")
for k in LEAVES_COL: spec(f"{k}_leaves")
spec("water", "translucent blue water"); spec("lava", "molten rock")

# [BASE] Блоки баз: светящиеся фонари, ящик, костёр, кирпичи биомов
spec("lantern", "glowing iron lantern with warm glass")
spec("ice_lantern", "glowing icy lantern of pale blue glass")
spec("crate", "wooden shipping crate with diagonal brace")
spec("campfire", "campfire logs with flames")
spec("snow_bricks", "white icy stone bricks")
spec("sandstone_bricks", "sandy stone bricks")

EMISSIVE = {"torch", "fire", "lava", "glowstone", "glowing_obsidian", "redstone_ore",
            "crystal", "nether_reactor", "end_portal_frame", "mob_spawner",
            "lantern", "ice_lantern", "campfire"}

# ======================= АГЕНТ =======================
class TextureAgent:
    def __init__(self):
        self.cache = {}

    def build(self, name):
        rng = _rng(name)
        # --- точные совпадения ---
        exact = {
            "stone": lambda: g_speckle(rng, "stone"), "granite": lambda: g_speckle(rng, "granite"),
            "diorite": lambda: g_speckle(rng, "diorite"), "andesite": lambda: g_speckle(rng, "andesite"),
            "polished_granite": lambda: g_polished(rng, "granite"),
            "polished_diorite": lambda: g_polished(rng, "diorite"),
            "polished_andesite": lambda: g_polished(rng, "andesite"),
            "cobblestone": lambda: g_cobble(rng), "mossy_cobblestone": lambda: g_cobble(rng, mossy=True),
            "deepslate": lambda: g_layers(rng, [(45, 47, 54), (38, 40, 47)]),
            "basalt": lambda: g_layers(rng, [(55, 55, 58), (42, 42, 46)], horizontal=False),
            "bedrock": g_bedrock, "dirt": lambda: g_dirt(rng), "coarse_dirt": lambda: g_dirt(rng, True),
            "podzol": g_podzol, "mycelium_top": g_mycelium,
            "sand": lambda: g_speckle(rng, "sand", 8), "red_sand": lambda: g_speckle(rng, "red_sand", 8),
            "gravel": g_gravel, "clay": lambda: g_speckle(rng, "clay", 3),
            "terracotta": g_terracotta, "salt": g_salt, "ash": g_ash,
            "obsidian": lambda: g_obsidian(rng), "glowing_obsidian": lambda: g_obsidian(rng, True),
            "snow": g_snow, "ice": lambda: g_ice(rng, "ice"),
            "packed_ice": lambda: g_ice(rng, "packed"), "blue_ice": lambda: g_ice(rng, "blue"),
            "white_wool": g_wool, "glass": g_glass,
            "oak_door_top": lambda: g_door(rng, "oak", True), "oak_door_bottom": lambda: g_door(rng, "oak", False),
            "sliding_door_top": lambda: g_door(rng, None, True, True),
            "sliding_door_bottom": lambda: g_door(rng, None, False, True),
            "bookshelf": g_bookshelf, "crafting_table": g_crafting,
            "hay_bale": lambda: g_hay(rng), "hay_bale_top": lambda: g_hay(rng, True),
            "charred_log": lambda: g_log_side(rng, "dark_oak"), "charred_log_top": lambda: g_log_top(rng, "dark_oak"),
            "petrified_log": lambda: g_layers(rng, [(110,110,112),(90,90,92)], horizontal=False),
            "petrified_log_top": lambda: g_log_top(rng, "birch"),
            "sandstone": lambda: g_layers(rng, [(218, 204, 155), (210, 196, 148)], crack=False),
            "chiseled_sandstone": self._chiseled,
            "stone_bricks": lambda: g_bricks(rng, [(115,115,117),(105,105,108),(122,122,124)]),
            "brick": lambda: g_bricks(rng, [(160,70,60),(150,65,55),(170,78,66)]),
            "netherrack": lambda: to_img(cracks(stone_base(rng, [(90,30,30),(80,25,25),(100,38,35)], 7, 0.6, 1.4), rng, 6, 0.5)),
            "soul_sand": self._soul, "nether_bricks": lambda: g_bricks(rng, [(55,25,28),(48,22,25),(62,28,30)], (25,12,14), 8, 16),
            "glowstone": lambda: to_img(stone_base(rng, [(250,210,90),(240,180,60),(255,230,130),(200,140,40)], 8, 0.7, 1.3)),
            "end_stone": self._end, "purpur_block": self._purpur,
            "crystal": g_crystal, "nether_reactor": g_reactor, "mob_spawner": g_spawner,
            "end_portal_frame": g_portal_frame, "end_portal": g_end_portal,
            "grass_top": g_grass_top, "grass_side": g_grass_side,
            "grass_plant": lambda: g_blades(rng, 24, 18, 40, (0.55, 0.85, 0.40)),
            "seagrass": lambda: g_blades(rng, 16, 30, 55, (0.25, 0.6, 0.4), wavy=True),
            "dandelion": lambda: g_flower(rng, (1.0, 0.85, 0.1), 5, 30, (0.8, 0.7, 0.1)),
            "poppy": lambda: g_flower(rng, (0.9, 0.15, 0.1), 6, 34, (0.15, 0.1, 0.05)),
            "sunflower": g_sunflower, "lavender": g_lavender, "dead_bush": g_dead_bush,
            "cactus_side": lambda: g_cactus(rng), "cactus_top": lambda: g_cactus(rng, True),
            "lily_pad": g_lily, "vine": g_vine, "bamboo": g_bamboo, "coral": g_coral,
            "moss_block": g_moss, "dripstone": g_dripstone, "torch": g_torch, "fire": g_fire,
            "rails": g_rails, "furnace_side": lambda: g_furnace(rng), "furnace_top": lambda: g_furnace(rng, True),
            "chest": lambda: g_chest(rng), "chest_top": lambda: g_chest(rng, True), "tnt": g_tnt,
            "lantern": g_lantern, "ice_lantern": g_ice_lantern, "crate": g_crate,
            "campfire": g_campfire, "snow_bricks": g_snow_bricks, "sandstone_bricks": g_sandstone_bricks,
            "oak_leaves_opaque": lambda: g_leaves(rng, "oak", True),
            "water": lambda: fluid(rng, [(25,60,180),(30,75,200),(40,95,220),(35,85,210)], 0.65),
            "lava": lambda: fluid(rng, [(80,15,5),(160,45,5),(235,120,10),(255,210,60)], 1.0, 6),
        }
        if name in exact:
            f = exact[name]
            try:
                return f(rng)
            except TypeError:
                return f()

        # --- вариации name#1..#3: тот же генератор, другой сид ---
        base, _, var = name.partition("#")
        if var:
            variant_of = {
                "stone": lambda r: g_speckle(r, "stone"),
                "sand": lambda r: g_speckle(r, "sand", 8),
                "oak_leaves": lambda r: g_leaves(r, "oak"),
                "spruce_leaves": lambda r: g_leaves(r, "spruce"),
                "jungle_leaves": lambda r: g_leaves(r, "jungle"),
                "oak_leaves_opaque": lambda r: g_leaves(r, "oak", True),
                "spruce_leaves_opaque": lambda r: g_leaves(r, "spruce", True),
                "jungle_leaves_opaque": lambda r: g_leaves(r, "jungle", True),
            }
            if base in variant_of:
                return variant_of[base](_rng(name))

        # --- руды: имена файлов = имена тайлов игры ---
        if name in ORE_FILES:
            return g_ore(rng, ORE_FILES[name])

        # --- правила по шаблону ---
        m = re.match(r"^(oak|spruce|birch|jungle)_(raw|dried|sealed|charred|reinforced|composite|waxed|lacquered)_planks$", name)
        if m: return g_planks(rng, m.group(1), m.group(2))
        m = re.match(r"^(oak|spruce|birch|jungle|acacia|dark_oak)_planks$", name)
        if m: return g_planks(rng, m.group(1))
        m = re.match(r"^(oak|spruce|birch|jungle|acacia|dark_oak)_log$", name)
        if m: return g_log_side(rng, m.group(1), m.group(1) == "birch")
        m = re.match(r"^(oak|spruce|birch|jungle|acacia|dark_oak)_log_top$", name)
        if m: return g_log_top(rng, m.group(1))
        m = re.match(r"^(coal|iron|gold|diamond|emerald)_block$", name)
        if m:
            cols = {"coal": (0.10,0.10,0.10), "iron": (0.85,0.85,0.85), "gold": (0.98,0.83,0.25),
                    "diamond": (0.35,0.90,0.88), "emerald": (0.20,0.80,0.35)}
            return g_mineral(rng, np.array(cols[m.group(1)]))
        m = re.match(r"^(white|red|green|blue)_concrete$", name)
        if m:
            cc = {"white": (0.85,0.85,0.85), "red": (0.65,0.15,0.12),
                  "green": (0.30,0.55,0.25), "blue": (0.25,0.35,0.75)}
            return g_concrete(rng, np.array(cc[m.group(1)]))
        m = re.match(r"^(wheat|carrot|potato)_stage(\d)$", name)
        if m: return g_crop(rng, m.group(1), int(m.group(2)))
        m = re.match(r"^(\w+)_leaves$", name)
        if m and m.group(1) in LEAVES_COL: return g_leaves(rng, m.group(1))

        # fallback — чтобы ни один блок не упал
        return to_img(stone_base(rng, [(128, 128, 128), (110, 110, 110)], 6))

    def _get(self, name):
        if name not in self.cache:
            self.cache[name] = self.build(name)
        return self.cache[name]

    def _chiseled(self):
        img = self.build("sandstone")
        a = np.array(img.convert('RGB')) / 255
        a[4:7, 4:60] *= 0.6; a[57:60, 4:60] *= 0.6
        a[4:60, 4:7] *= 0.6; a[4:60, 57:60] *= 0.6
        a[12:14, 12:52] *= 0.75; a[50:52, 12:52] *= 0.75
        return to_img(a)

    def _soul(self):
        rng = _rng("soul_sand")
        a = stone_base(rng, [(95, 70, 50), (85, 62, 44), (105, 78, 55)], 7)
        ys, xs = np.mgrid[0:SIZE, 0:SIZE]
        for cx, cy, r in ((20, 22, 5), (44, 20, 4), (32, 44, 6)):
            m = ((xs - cx) / r) ** 2 + ((ys - cy) / (r * 1.3)) ** 2 <= 1
            a[m] = [0.12, 0.09, 0.07]
        return to_img(a)

    def _end(self):
        rng = _rng("end_stone")
        a = stone_base(rng, [(225, 230, 160), (218, 222, 150), (232, 236, 170)], 5)
        ys, xs = np.mgrid[0:SIZE, 0:SIZE]
        for _ in range(10):
            cx, cy = int(rng.integers(6, 58)), int(rng.integers(6, 58))
            m = np.hypot(xs - cx, ys - cy) < rng.integers(2, 4)
            a[m, :3] *= 0.8
        return to_img(a)

    def _purpur(self):
        rng = _rng("purpur_block")
        a = stone_base(rng, [(160, 110, 170), (150, 100, 160), (170, 120, 178)], 5)
        for _ in range(40):
            x, y = int(rng.integers(0, SIZE)), int(rng.integers(0, SIZE))
            a[y, x, :3] = np.array([0.9, 0.85, 0.9])
        return to_img(a)

    def run(self, specs):
        os.makedirs(OUT, exist_ok=True)
        items = []
        for i, (name, prompt) in enumerate(specs):
            img = self._get(name)
            img.save(os.path.join(OUT, f"{name}.png"))
            items.append((name, img))
            if (i + 1) % 25 == 0:
                print(f"  сгенерировано {i + 1}/{len(specs)}")
        self._atlas(items)
        print(f"Готово: {len(items)} текстур -> {OUT}/, atlas.png, atlas.json")

    def _atlas(self, items):
        cols = 16
        rows = (len(items) + cols - 1) // cols
        atlas = np.zeros((rows * SIZE, cols * SIZE, 4), np.uint8)
        meta = {}
        for i, (name, img) in enumerate(items):
            r, c = divmod(i, cols)
            atlas[r * SIZE:(r + 1) * SIZE, c * SIZE:(c + 1) * SIZE] = np.array(img.convert('RGBA'))
            meta[name] = {"x": c * SIZE, "y": r * SIZE, "w": SIZE, "h": SIZE,
                          "u": round(c / cols, 6), "v": round(r / rows, 6),
                          "emissive": name in EMISSIVE}
        Image.fromarray(atlas).save("atlas.png")
        with open("atlas.json", "w") as f:
            json.dump(meta, f, indent=1)

if __name__ == "__main__":
    print(f"TextureAgent: генерация {len(SPECS)} текстур {SIZE}x{SIZE}...")
    TextureAgent().run(SPECS)


