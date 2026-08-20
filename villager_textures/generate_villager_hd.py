#!/usr/bin/env python3
"""
Генератор HD-текстур жителей в НОВОМ стиле.

Каждый скин рисуется в 1024x1024 px (SCALE=16 — в 16 раз детальнее
ванильных 64x64). Атлас из 16 профессий: 16384x1024 — это физический
потолок GL_MAX_TEXTURE_SIZE = 16384 (Radeon RX 580).

Новый стиль:
  - согласованный внешний вид одного жителя (тон кожи/цвет глаз/причёска
    выбираются один раз на скин, а не переслушиваются для каждой части);
  - проработанные лица: склера, радужка, зрачок, блик, веки, брови,
    румянец, губы;
  - 3D-нос с освещением (верх светлее, бока темнее, низ в тени);
  - ткань с плетением нитей, вертикальным градиентом света и шумом;
  - фартуки со швами и карманами, ремни с пряжками, пуговицы, подолы;
  - головные уборы: соломенная шляпа, капюшон, колпак, митра, бандана;
  - лёгкие AO-затемнения по краям UV-регионов (эффект «швов» кубов).

UV-раскладка совместима с VillagerModel (Minecraft-формат 64x64),
все координаты масштабируются на SCALE.

Использование:
    python generate_villager_hd.py

Результат (в src/main/resources/textures/entities/):
    villager_atlas.png           16384x1024 — атлас всех профессий
    villager_<profession>.png    1024x1024  — отдельные скины
    villager_biomes_atlas.png    биом-вариации фермера
    villager_variants_atlas.png  baby + zombie
    villager_atlas.json          манифест
"""

import json
import os
import random
import sys
from PIL import Image, ImageDraw

SCALE = 16                      # 64 логических пикселя -> 1024
TILE = 64 * SCALE               # размер одного скина (1024)
ATLAS_TILES = 16
ATLAS_W = TILE * ATLAS_TILES    # 16384 — максимум текстуры GPU
ATLAS_H = TILE                  # 1024

# --- UV-раскладка в логических пикселях (как в VillagerModel) ---------------
UV = {
    "head_top":    (8, 0, 8, 8),
    "head_bot":    (16, 0, 8, 8),
    "head_right":  (0, 8, 8, 8),
    "head_front":  (8, 8, 8, 8),
    "head_left":   (16, 8, 8, 8),
    "head_back":   (24, 8, 8, 8),
    "body_top":    (20, 16, 8, 4),
    "body_bot":    (28, 16, 8, 4),
    "body_right":  (16, 20, 4, 12),
    "body_front":  (20, 20, 8, 12),
    "body_left":   (28, 20, 4, 12),
    "body_back":   (32, 20, 8, 12),
    "rarm_top":    (44, 16, 4, 4),
    "rarm_bot":    (48, 16, 4, 4),
    "rarm_right":  (40, 20, 4, 12),
    "rarm_front":  (44, 20, 4, 12),
    "rarm_left":   (48, 20, 4, 12),
    "rarm_back":   (52, 20, 4, 12),
    "larm_top":    (36, 48, 4, 4),
    "larm_bot":    (40, 48, 4, 4),
    "larm_right":  (32, 52, 4, 12),
    "larm_front":  (36, 52, 4, 12),
    "larm_left":   (40, 52, 4, 12),
    "larm_back":   (44, 52, 4, 12),
    "rleg_top":    (4, 16, 4, 4),
    "rleg_bot":    (8, 16, 4, 4),
    "rleg_right":  (0, 20, 4, 12),
    "rleg_front":  (4, 20, 4, 12),
    "rleg_left":   (8, 20, 4, 12),
    "rleg_back":   (12, 20, 4, 12),
    "lleg_top":    (20, 48, 4, 4),
    "lleg_bot":    (24, 48, 4, 4),
    "lleg_right":  (16, 52, 4, 12),
    "lleg_front":  (20, 52, 4, 12),
    "lleg_left":   (24, 52, 4, 12),
    "lleg_back":   (28, 52, 4, 12),
    # Нос — отдельный кубик 2x4x2 впереди лица
    "nose_top":    (26, 0, 2, 2),
    "nose_bot":    (28, 0, 2, 2),
    "nose_right":  (24, 2, 2, 4),
    "nose_front":  (26, 2, 2, 4),
    "nose_left":   (28, 2, 2, 4),
    "nose_back":   (30, 2, 2, 4),
}

# Порядок профессий = порядку в атласе = ordinal в Villager.Profession
PROFESSIONS = ["farmer", "librarian", "blacksmith", "butcher", "priest",
               "fisherman", "fletcher", "leatherworker", "shepherd",
               "toolsmith", "armorer", "weaponsmith", "cartographer",
               "cleric", "mason", "nitwit"]

BIOMES = ["plains", "desert", "snowy", "taiga", "savanna"]

BIOME_TINTS = {
    "desert": ((222, 192, 122), 60),
    "snowy":  ((204, 214, 232), 40),
    "taiga":  ((64, 84, 62), 32),
    "savanna": ((186, 144, 64), 36),
}

SKIN_TONES = [
    (216, 178, 142),
    (202, 158, 122),
    (182, 140, 106),
    (152, 114, 84),
    (124, 94, 68),
]

EYE_COLORS = [
    (94, 74, 46),
    (58, 98, 50),
    (64, 92, 130),
    (104, 88, 48),
    (66, 74, 72),
]

HAIR_COLORS = [
    (58, 40, 26),
    (30, 24, 20),
    (198, 168, 126),
    (150, 94, 54),
    (170, 170, 170),
    (88, 92, 96),
]

HAT_STRAW = ((214, 184, 112), (178, 150, 86))
HAT_CAP_WHITE = ((232, 232, 234), (198, 198, 204))
HAT_CAP_GRAY = ((130, 126, 122), (104, 100, 98))
HAT_MITRE = ((234, 232, 224), (206, 202, 190))
HAT_BANDANA = ((190, 60, 50), (150, 44, 36))

# Стили профессий: robe=(база, тень), trim=отделка, belt=ремень,
# apron=(цвет, тень) или None, hat=убор, glasses/vest/…, skin=предпочт.
STYLES = {
    "farmer": dict(robe=((118, 86, 54), (86, 62, 38)), trim=(68, 112, 46),
                   belt=(52, 36, 20), apron=((98, 66, 42), (74, 50, 32)),
                   hat="straw", skin=2, hair=0, buttons=True),
    "librarian": dict(robe=((98, 72, 134), (72, 52, 102)), trim=(218, 198, 152),
                      belt=(48, 34, 66), apron=None, hat=None, glasses=True,
                      vest=True, skin=1, hair=4),
    "blacksmith": dict(robe=((88, 88, 94), (66, 66, 70)), trim=(150, 108, 44),
                       belt=(46, 36, 30), apron=((72, 56, 40), (54, 42, 30)),
                       hat=None, skin=3, hair=1, soot=True, rolled=True),
    "butcher": dict(robe=((228, 228, 230), (198, 198, 202)), trim=(200, 60, 50),
                    belt=(120, 80, 42), apron=((226, 58, 48), (192, 46, 38)),
                    hat="cap", skin=0, hair=2, stains=True, buttons=True),
    "priest": dict(robe=((126, 76, 130), (94, 54, 98)), trim=(230, 198, 92),
                   belt=(182, 152, 42), apron=None, hat="mitre", skin=1, hair=4),
    "fisherman": dict(robe=((76, 112, 150), (58, 86, 118)), trim=(216, 182, 88),
                      belt=(122, 98, 50), apron=None, hat="hood",
                      hood=(66, 96, 128), skin=3, hair=1),
    "fletcher": dict(robe=((80, 112, 64), (60, 86, 48)), trim=(152, 118, 76),
                     belt=(90, 62, 34), apron=None, hat="hood",
                     hood=(72, 100, 58), strap=(124, 92, 58), skin=3, hair=1),
    "leatherworker": dict(robe=((142, 98, 52), (110, 76, 40)), trim=(80, 52, 28),
                          belt=(78, 50, 26), apron=((98, 64, 34), (76, 50, 26)),
                          hat=None, skin=3, hair=0, stitched=True),
    "shepherd": dict(robe=((224, 214, 200), (198, 188, 174)), trim=(102, 76, 52),
                     belt=(90, 66, 42), apron=None, hat="straw", wool=True,
                     skin=0, hair=2, buttons=True),
    "toolsmith": dict(robe=((98, 100, 106), (74, 76, 80)), trim=(120, 92, 44),
                      belt=(112, 88, 46), apron=((88, 78, 66), (64, 56, 46)),
                      hat=None, skin=2, hair=3, stitched=True),
    "armorer": dict(robe=((106, 110, 118), (80, 84, 90)), trim=(162, 166, 174),
                    belt=(100, 76, 36), apron=((68, 72, 78), (50, 54, 60)),
                    hat=None, skin=3, hair=1, plates=True),
    "weaponsmith": dict(robe=((80, 84, 90), (60, 64, 68)), trim=(172, 52, 44),
                        belt=(56, 48, 40), apron=((72, 68, 62), (54, 50, 46)),
                        hat="bandana", skin=4, hair=1, stitched=True),
    "cartographer": dict(robe=((130, 106, 80), (100, 80, 60)), trim=(212, 182, 112),
                         belt=(82, 62, 38), apron=None, hat=None, glasses=True,
                         scroll=(214, 192, 142), skin=1, hair=4, buttons=True),
    "cleric": dict(robe=((228, 226, 216), (198, 194, 182)), trim=(226, 196, 92),
                   belt=(62, 42, 32), apron=None, hat=None, skin=1, hair=4),
    "mason": dict(robe=((142, 138, 132), (108, 104, 100)), trim=(120, 96, 62),
                  belt=(80, 64, 46), apron=((106, 104, 102), (80, 78, 76)),
                  hat="cap", cap=HAT_CAP_GRAY, skin=2, hair=0, speckle=True),
    "nitwit": dict(robe=((94, 130, 70), (74, 104, 56)), trim=(128, 152, 84),
                   belt=(64, 88, 46), apron=None, hat=None, skin=1, hair=3,
                   messy=True, buttons=True),
}

BABY_UV = {
    "head_top":    (8, 0, 10, 10),
    "head_bot":    (18, 0, 10, 10),
    "head_right":  (0, 10, 10, 8),
    "head_front":  (8, 10, 10, 8),
    "head_left":   (18, 10, 10, 8),
    "head_back":   (28, 10, 10, 8),
    "body_top":    (20, 20, 6, 3),
    "body_bot":    (26, 20, 6, 3),
    "body_right":  (16, 23, 3, 6),
    "body_front":  (19, 23, 6, 6),
    "body_left":   (25, 23, 3, 6),
    "body_back":   (28, 23, 6, 6),
    "rarm_top":    (44, 20, 2, 3),
    "rarm_bot":    (46, 20, 2, 3),
    "rarm_right":  (42, 23, 2, 6),
    "rarm_front":  (44, 23, 2, 6),
    "rarm_left":   (46, 23, 2, 6),
    "rarm_back":   (48, 23, 2, 6),
    "larm_top":    (36, 32, 2, 3),
    "larm_bot":    (38, 32, 2, 3),
    "larm_right":  (34, 35, 2, 6),
    "larm_front":  (36, 35, 2, 6),
    "larm_left":   (38, 35, 2, 6),
    "larm_back":   (40, 35, 2, 6),
    "rleg_top":    (4, 20, 2, 3),
    "rleg_bot":    (6, 20, 2, 3),
    "rleg_right":  (2, 23, 2, 6),
    "rleg_front":  (4, 23, 2, 6),
    "rleg_left":   (6, 23, 2, 6),
    "rleg_back":   (8, 23, 2, 6),
    "lleg_top":    (12, 20, 2, 3),
    "lleg_bot":    (14, 20, 2, 3),
    "lleg_right":  (10, 23, 2, 6),
    "lleg_front":  (12, 23, 2, 6),
    "lleg_left":   (14, 23, 2, 6),
    "lleg_back":   (16, 23, 2, 6),
}


# ====================== мелкие утилиты ======================

def _c(v):
    return max(0, min(255, int(round(v))))


def lerp(a, b, t):
    return (_c(a[0] + (b[0] - a[0]) * t),
            _c(a[1] + (b[1] - a[1]) * t),
            _c(a[2] + (b[2] - a[2]) * t))


def mulc(c, f):
    return (_c(c[0] * f), _c(c[1] * f), _c(c[2] * f))


def addc(c, d):
    return (_c(c[0] + d[0]), _c(c[1] + d[1]), _c(c[2] + d[2]))


def blend_existing(existing, rgb, t):
    """Смешать существующий RGBA пиксель к rgb с весом t."""
    if existing[3] == 0:
        return (rgb[0], rgb[1], rgb[2], 255)
    return (*lerp(existing[:3], rgb, t), 255)


class Canvas:
    """Полотно скина: Image + быстрый доступ к пикселям + ImageDraw."""

    def __init__(self, size=TILE):
        self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        self.px = self.img.load()
        self.draw = ImageDraw.Draw(self.img)
        self.size = size

    def put(self, x, y, rgb):
        self.px[x, y] = (rgb[0], rgb[1], rgb[2], 255)

    def puta(self, x, y, rgb, a):
        self.px[x, y] = (rgb[0], rgb[1], rgb[2], a)

    def blend(self, x, y, rgb, t):
        self.px[x, y] = blend_existing(self.px[x, y], rgb, t)

    def rect(self, x, y, w, h, rgb):
        self.draw.rectangle([x, y, x + w - 1, y + h - 1], fill=(*rgb, 255))

    def rrect(self, x, y, w, h, r, rgb):
        self.draw.rounded_rectangle(
            [x, y, x + w - 1, y + h - 1], radius=r, fill=(*rgb, 255))

    def ellipse(self, cx, cy, rx, ry, rgb, a=255):
        self.draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry],
                          fill=(*rgb, a))

    def line(self, x0, y0, x1, y1, rgb, width=1, a=255):
        self.draw.line([x0, y0, x1, y1], fill=(*rgb, a), width=width)

    def ring(self, cx, cy, r, width, rgb, a=255):
        self.draw.ellipse([cx - r, cy - r, cx + r, cy + r],
                          outline=(*rgb, a), width=width)


def rgn(c, key, painter):
    """Вызвать painter на увеличенном регионе UV-карты."""
    x, y, w, h = (v * SCALE for v in UV[key])
    painter(c, x, y, w, h)


def cloth(c, x, y, w, h, base, dark, rng, noise=0.05, thread=0.09,
          gradient=1.0, side_shade=0.10):
    """Ткань: вертикальный свет, нити, шум, лёгкое затенение боков."""
    midx = (w - 1) / 2.0
    for py in range(y, y + h):
        t = ((py - y) / max(1, h - 1)) * gradient
        row = lerp(base, dark, t)
        row_t = (py - y) % (2 * SCALE)
        for px in range(x, x + w):
            col = row
            ss = 1.0 - side_shade * abs((px - x) - midx) / max(1.0, midx)
            if ss < 1.0:
                col = lerp(col, mulc(col, ss), 1.0 - ss)
            if thread and ((px - x) % (2 * SCALE) == 0):
                col = lerp(col, mulc(col, 0.88), thread)
            if row_t < 3:
                col = lerp(col, mulc(col, 1.07), thread * 0.6)
            j = rng.uniform(-noise, noise)
            col = mulc(col, 1.0 + j)
            c.put(px, py, col)


def skin_fill(c, x, y, w, h, skin, rng, noise=0.04):
    """Кожа: светлее сверху, тень к низу, лёгкий шум."""
    light, dark = mulc(skin, 1.10), mulc(skin, 0.80)
    for py in range(y, y + h):
        t = ((py - y) / max(1, h - 1)) ** 1.15
        row = lerp(light, dark, t)
        for px in range(x, x + w):
            j = rng.uniform(-noise, noise)
            c.put(px, py, mulc(row, 1.0 + j))


def ao(c, x, y, w, h, strength=0.30, depth=SCALE):
    """AO-затемнение по краям региона (швы кубов)."""
    for d in range(depth):
        f = strength * (1.0 - d / depth)
        for i in range(w):
            c.blend(x + i, y + d, (0, 0, 0), f * 0.5)
            c.blend(x + i, y + h - 1 - d, (0, 0, 0), f)
        for i in range(h):
            c.blend(x + d, y + i, (0, 0, 0), f * 0.6)
            c.blend(x + w - 1 - d, y + i, (0, 0, 0), f * 0.8)


def stitches(c, x, y, w, h, rgb, dash=12, gap=10, width=3, inset=6):
    """Пунктирная строчка по периметру прямоугольника."""
    for i in range(inset, w - inset, dash + gap):
        c.line(x + i, y + inset, x + min(i + dash, w - inset), y + inset,
               rgb, width)
        c.line(x + i, y + h - inset, x + min(i + dash, w - inset),
               y + h - inset, rgb, width)
    for i in range(inset, h - inset, dash + gap):
        c.line(x + inset, y + i, x + inset, y + min(i + dash, h - inset),
               rgb, width)
        c.line(x + w - inset, y + i, x + w - inset,
               y + min(i + dash, h - inset), rgb, width)


# ====================== лицо и голова ======================

def paint_eye(c, cx, cy, eye, rng):
    """Один глаз: веко, склера, радужка, зрачок, блик. cx,cy — центр в px."""
    sclera = (244, 244, 242)
    eyelid = (150, 118, 92)
    r = SCALE * 0.82
    c.rect(int(cx - r), int(cy - r * 0.62), int(r * 2), int(r * 1.24), sclera)
    # лёгкая тень века сверху и ресницы снизу
    c.line(int(cx - r), int(cy - r * 0.62), int(cx + r), int(cy - r * 0.62),
           eyelid, max(2, SCALE // 6))
    c.line(int(cx - r), int(cy + r * 0.62), int(cx + r), int(cy + r * 0.62),
           mulc(eyelid, 0.8), max(2, SCALE // 6))
    ir = r * 0.42
    c.ellipse(int(cx + ir * 0.25), int(cy + ir * 0.15), int(ir), int(ir), eye)
    c.ellipse(int(cx + ir * 0.2), int(cy + ir * 0.1), int(ir * 0.55),
              int(ir * 0.55), (24, 22, 20))
    c.ellipse(int(cx - ir * 0.25), int(cy - ir * 0.3), int(ir * 0.28),
              int(ir * 0.28), (252, 252, 252))


def paint_face(c, x, y, w, h, s, rng):
    """Перед головы 8x8: кожа, брови, глаза, румянец, рот, AO под носом."""
    skin, eye, hair = s["skin"], s["eye"], s["hair"]
    skin_fill(c, x, y, w, h, skin, rng)
    u, v = x, y  # логические координаты внутри лица (делим на SCALE)
    L = SCALE

    # Брови (уголком)
    for side, bx in ((0, 0), (1, 5)):
        if side == 0:
            brow_x, brow_y = u + bx * L, v + 1 * L
        else:
            brow_x, brow_y = u + bx * L, v + 1 * L
        c.rect(brow_x, brow_y, 3 * L, int(L * 0.9), hair)
        c.rect(brow_x + int(L * 1.4), brow_y + int(L * 0.5),
               int(L * 1.6), int(L * 0.5), hair)

    # Глаза
    for ex in (1, 5):
        paint_eye(c, u + (ex + 1) * L, v + int(2.6 * L), eye, rng)

    # Румянец
    for sx in (0, 6):
        c.ellipse(u + (sx + 0.5) * L, v + int(4.7 * L), int(L * 0.85),
                  int(L * 0.45), (214, 120, 108), a=90)

    # AO под носом (нос-кубик закрывает центр строк 2..6)
    shadow = mulc(skin, 0.72)
    c.rect(u + 3 * L, v + 2 * L, 2 * L, 4 * L, shadow)
    c.rect(u + 2 * L, v + 3 * L, L, 3 * L, shadow)
    c.rect(u + 5 * L, v + 3 * L, L, 3 * L, shadow)

    # Рот — чуть ниже кончика носа
    c.rect(u + 3 * L, v + int(6.35 * L), 2 * L, int(L * 0.35),
           mulc(skin, 0.75))
    c.rect(u + 3 * L, v + int(6.8 * L), 2 * L, int(L * 0.65),
           (176, 96, 78))
    c.line(u + 3 * L, v + int(6.8 * L) + int(L * 0.3),
           u + 5 * L, v + int(6.8 * L) + int(L * 0.3), (128, 66, 54),
           max(2, L // 8))

    # Подбородок — блик
    c.rect(u + 3 * L, v + int(7.35 * L), 2 * L, int(L * 0.35),
           mulc(skin, 1.08))

    # Очки (librarian / cartographer)
    if s.get("glasses"):
        frame = (52, 46, 42)
        for ex in (1, 5):
            gx = u + ex * L
            gy = v + int(2 * L)
            c.draw.rectangle([gx + 2, gy + 2, gx + 3 * L - 2,
                              gy + 2 * L - 2], outline=(*frame, 255),
                             width=max(2, L // 6))
        c.line(u + 4 * L, v + int(2 * L) + 2, u + 4 * L,
               v + int(2.9 * L), frame, max(2, L // 6))  # переносица
        c.line(u + 1 * L, v + int(2.2 * L), u + 1 * L - L // 2,
               v + int(2.6 * L), frame, max(2, L // 6))  # заушник
        c.line(u + 7 * L, v + int(2.2 * L), u + 7 * L + L // 2,
               v + int(2.6 * L), frame, max(2, L // 6))


def paint_head_sides(c, key, s, rng):
    """Бока головы: убор/волосы сверху, кожа снизу, бакенбарды."""
    x, y, w, h = (v * SCALE for v in UV[key])
    hat = s["hat"]
    skin_fill(c, x, y, w, h, s["skin"], rng)
    L = SCALE
    if hat == "straw":
        hat_rows = 3
        col = HAT_STRAW
    elif hat == "cap":
        hat_rows = 2
        col = s.get("cap", HAT_CAP_WHITE)
    elif hat == "hood":
        hat_rows = 4
        col = (s.get("hood", s["robe"][0]), mulc(s.get("hood", s["robe"][0]), 0.72))
    elif hat == "mitre":
        hat_rows = 3
        col = HAT_MITRE
    elif hat == "bandana":
        hat_rows = 1
        col = HAT_BANDANA
    else:
        hat_rows = 0
        col = None
    if col:
        cloth(c, x, y, w, hat_rows * L, col[0], col[1], rng, noise=0.04,
              thread=0.05)
    if s.get("hair") and not hat:
        hair_rows = 2 if not s.get("messy") else 3
        cloth(c, x, y, w, hair_rows * L, s["hair"], mulc(s["hair"], 0.72),
              rng, noise=0.04, thread=0.08)
    # бакенбарды
    if s.get("hair") and hat != "hood":
        hb = max(0, hat_rows - 1) if hat else 2
        if not hat:
            c.rect(x, y + hb * L, int(L * 0.8), 2 * L, s["hair"])
            c.rect(x + w - int(L * 0.8), y + hb * L, int(L * 0.8), 2 * L,
                   s["hair"])
    # бандана — узел сзади и на боках
    if hat == "bandana" and key in ("head_back", "head_right", "head_left"):
        c.rect(x + int(w / 2 - L * 0.6), y + L, int(L * 1.2), L,
               mulc(HAT_BANDANA[0], 0.85))
        if key == "head_back":
            c.line(x + int(w / 2), y + 2 * L, x + int(w / 2 - L), y + 4 * L,
                   mulc(HAT_BANDANA[0], 0.8), max(2, L // 5))
            c.line(x + int(w / 2), y + 2 * L, x + int(w / 2 + L), y + 4 * L,
                   mulc(HAT_BANDANA[0], 0.8), max(2, L // 5))
    ao(c, x, y, w, h)


def paint_head_top(c, x, y, w, h, s, rng):
    """Макушка: волосы или убор (вид сверху)."""
    hat = s["hat"]
    L = SCALE
    if hat == "straw":
        base, dark = HAT_STRAW
        cloth(c, x, y, w, h, base, dark, rng, noise=0.04, thread=0.06)
        # плетение соломы: концентрические кольца
        ring_r = int(w / 2)
        for rr in range(ring_r - L, L, -L * 2):
            c.ring(x + w / 2, y + h / 2, rr, max(2, L // 6),
                   mulc(dark, 0.92))
        # поля — тёмное кольцо по краю
        c.ring(x + w / 2, y + h / 2, int(w / 2 - L * 0.4), max(2, L // 5),
               mulc(dark, 0.8))
    elif hat == "cap":
        col = s.get("cap", HAT_CAP_WHITE)
        cloth(c, x, y, w, h, col[0], col[1], rng, noise=0.03, thread=0.05)
        c.ellipse(x + w / 2, y + h / 2, L * 0.55, L * 0.55,
                  mulc(col[1], 0.9))
        c.line(x + w / 2, y + L, x + w / 2, y + h - L, mulc(col[1], 0.92),
               max(2, L // 6))
    elif hat == "hood":
        col = (s.get("hood", s["robe"][0]), mulc(s.get("hood", s["robe"][0]), 0.72))
        cloth(c, x, y, w, h, col[0], col[1], rng, noise=0.04, thread=0.06)
        c.line(x + w / 2, y + L, x + w / 2, y + h - L, mulc(col[1], 0.85),
               max(2, L // 5))
    elif hat == "mitre":
        cloth(c, x, y, w, h, HAT_MITRE[0], HAT_MITRE[1], rng, noise=0.03,
              thread=0.05)
        c.line(x + w / 2, y, x + w / 2, y + h - 1, HAT_MITRE[1],
               max(2, L // 5))
        c.rect(x + L, y + int(h / 2 - L * 0.4), w - 2 * L, int(L * 0.8),
               (232, 200, 94))
    elif hat == "bandana":
        cloth(c, x, y, w, h, HAT_BANDANA[0], HAT_BANDANA[1], rng, noise=0.04,
              thread=0.06)
        c.line(x + L, y + L, x + w - L, y + h - L, mulc(HAT_BANDANA[1], 0.8),
               max(2, L // 6))
        c.line(x + w - L, y + L, x + L, y + h - L, mulc(HAT_BANDANA[1], 0.8),
               max(2, L // 6))
    elif s.get("hair"):
        cloth(c, x, y, w, h, s["hair"], mulc(s["hair"], 0.72), rng,
              noise=0.04, thread=0.10)
        if s.get("messy"):
            # взъерошенные пряди
            for i in range(5):
                sx = x + rng.randint(0, w - L)
                c.line(sx, y + rng.randint(0, h - L), sx + rng.randint(-L, L),
                       y + rng.randint(0, h - L), mulc(s["hair"], 0.65),
                       max(2, L // 6))
        else:
            # пробор
            c.line(x + w / 2, y, x + w / 2, y + h, mulc(s["hair"], 0.7),
                   max(2, L // 8))
    else:
        skin_fill(c, x, y, w, h, mulc(s["skin"], 0.82), rng)
    ao(c, x, y, w, h, strength=0.22)


def paint_head_back(c, x, y, w, h, s, rng):
    """Затылок: убор/волосы, потом кожа."""
    hat = s["hat"]
    skin_fill(c, x, y, w, h, mulc(s["skin"], 0.9), rng)
    L = SCALE
    if hat == "straw":
        cloth(c, x, y, w, 3 * L, *HAT_STRAW, rng, noise=0.04, thread=0.05)
        c.rect(x + int(w / 2 - L * 0.5), y + 3 * L, L, L,
               mulc(HAT_STRAW[0], 0.9))
    elif hat == "cap":
        cloth(c, x, y, w, 2 * L, *(s.get("cap", HAT_CAP_WHITE)), rng,
              noise=0.03, thread=0.05)
    elif hat == "hood":
        col = s.get("hood", s["robe"][0])
        cloth(c, x, y, w, 4 * L, col, mulc(col, 0.72), rng, noise=0.04,
              thread=0.06)
        c.line(x + int(w / 2), y, x + int(w / 2), y + 4 * L,
               mulc(col, 0.85), max(2, L // 5))
    elif hat == "mitre":
        cloth(c, x, y, w, 3 * L, *HAT_MITRE, rng, noise=0.03, thread=0.05)
        c.rect(x, y + 2 * L, w, int(L * 0.7), (232, 200, 94))
    elif hat == "bandana":
        c.rect(x, y, w, L, HAT_BANDANA[0])
    elif s.get("hair"):
        hair_rows = 2 if not s.get("messy") else 3
        cloth(c, x, y, w, hair_rows * L, s["hair"], mulc(s["hair"], 0.72),
              rng, noise=0.04, thread=0.08)
        if s.get("messy"):
            for i in range(4):
                sx = x + rng.randint(0, w - L)
                c.line(sx, y + hair_rows * L, sx + rng.randint(-L, L),
                       y + (hair_rows + 2) * L, mulc(s["hair"], 0.6),
                       max(2, L // 6))
    ao(c, x, y, w, h)


def paint_head_bot(c, x, y, w, h, s, rng):
    """Низ головы (вид снизу) — тёмная кожа."""
    skin_fill(c, x, y, w, h, mulc(s["skin"], 0.62), rng, noise=0.03)
    ao(c, x, y, w, h, strength=0.25)


def paint_nose(c, s, rng):
    """Нос-кубик: верх светлый, бока темнее, низ в глубокой тени."""
    skin = s["skin"]
    light, mid, dark = mulc(skin, 1.12), skin, mulc(skin, 0.72)
    deeper = mulc(skin, 0.58)

    def f(key, base, mult):
        x, y, w, h = (v * SCALE for v in UV[key])
        col = mulc(base, mult)
        skin_fill(c, x, y, w, h, col, rng, noise=0.05)
        ao(c, x, y, w, h, strength=0.28)

    f("nose_top", light, 1.0)
    # перед: градиент от светлого верха к тени у ноздрей
    x, y, w, h = (v * SCALE for v in UV["nose_front"])
    for py in range(h):
        t = py / max(1, h - 1)
        row = lerp(light, mid, t ** 1.2)
        for px in range(w):
            j = rng.uniform(-0.04, 0.04)
            c.put(x + px, y + py, mulc(row, 1.0 + j))
    # блик на переносице
    c.line(x + 2, y + 2, x + 2, y + int(h * 0.45), mulc(light, 1.08),
           max(2, SCALE // 5))
    # ноздри — тень у нижней кромки
    c.rect(x, y + int(h * 0.72), w, int(h * 0.28), deeper)
    ao(c, x, y, w, h, strength=0.3)
    f("nose_right", dark, 0.92)
    f("nose_left", dark, 0.92)
    f("nose_back", dark, 0.78)
    f("nose_bot", deeper, 1.0)


# ====================== тело ======================

def paint_body(c, s, rng):
    """Туника + ремень + фартук/отделка/пуговицы/спецэффекты."""
    robe, dark = s["robe"]
    trim = s["trim"]
    L = SCALE
    apron = s.get("apron")
    ap_dark = mulc(apron[0], 0.8) if apron else None

    def face(key, is_front):
        x, y, w, h = (v * SCALE for v in UV[key])
        cloth(c, x, y, w, h, robe, dark, rng, noise=0.045, thread=0.08)
        if is_front:
            paint_body_front(c, x, y, w, h, s, rng)
        else:
            # ремень и на задней стороне, но без пряжки
            c.rect(x, y + 4 * L, w, int(L * 0.9), s["belt"])
            if s.get("trim"):
                c.rect(x, y + h - int(L * 0.5), w, int(L * 0.5), trim)
        ao(c, x, y, w, h)

    def side(key):
        x, y, w, h = (v * SCALE for v in UV[key])
        cloth(c, x, y, w, h, mulc(robe, 0.92), mulc(dark, 0.92), rng,
              noise=0.045, thread=0.08)
        c.rect(x, y + 4 * L, w, int(L * 0.9), mulc(s["belt"], 0.85))
        ao(c, x, y, w, h)

    face("body_front", True)
    face("body_back", False)
    side("body_right")
    side("body_left")

    # плечи (верх тела) — светлее
    x, y, w, h = (v * SCALE for v in UV["body_top"])
    cloth(c, x, y, w, h, lerp(robe, (255, 255, 255), 0.12),
          lerp(dark, (255, 255, 255), 0.10), rng, noise=0.04, thread=0.07)
    ao(c, x, y, w, h)
    # низ тела (вид снизу) — в тени
    x, y, w, h = (v * SCALE for v in UV["body_bot"])
    cloth(c, x, y, w, h, mulc(dark, 0.7), mulc(dark, 0.5), rng, noise=0.04,
          thread=0.07)
    ao(c, x, y, w, h)


def paint_body_front(c, x, y, w, h, s, rng):
    """Детали передней стороны туники."""
    robe, dark = s["robe"]
    trim = s["trim"]
    L = SCALE
    belt = s["belt"]
    apron = s.get("apron")

    # воротник
    collar = lerp(robe, (255, 255, 255), 0.16)
    c.rrect(x + int(1.5 * L), y, w - 3 * L, int(1.3 * L), int(L * 0.5),
            collar)
    c.line(x + int(1.5 * L), y + int(1.2 * L), x + w - int(1.5 * L),
           y + int(1.2 * L), mulc(collar, 0.8), max(2, L // 7))

    # жилет (librarian)
    if s.get("vest"):
        vest = lerp(s["robe"][0], (20, 20, 40), 0.25)
        c.rect(x + L, y + L, int(6 * L), int(10.5 * L), vest)
        cloth(c, x + L, y + L, int(6 * L), int(10.5 * L), vest,
              mulc(vest, 0.8), rng, noise=0.04, thread=0.09)
        # кромка жилета
        c.line(x + L + int(L * 0.9), y + L, x + L + int(L * 0.9),
               y + int(10.5 * L), mulc(vest, 1.3), max(2, L // 7))

    # ремень с пряжкой
    c.rect(x, y + 4 * L, w, int(L * 0.95), belt)
    buckle = (212, 178, 92)
    bx = x + int(3 * L)
    c.rect(bx, y + 4 * L + 3, 2 * L, int(L * 0.75), mulc(buckle, 1.1))
    c.rect(bx + int(L * 0.4), y + 4 * L + int(L * 0.2), int(L * 1.2),
           int(L * 0.35), mulc(buckle, 0.7))
    c.line(bx + 3, y + 4 * L + 4, bx + 3, y + 4 * L + int(L * 0.72),
           (250, 240, 200), max(1, L // 12))

    # пуговицы (швы по краю)
    if s.get("buttons") and not apron and not s.get("vest"):
        for row in (2, 4, 6):
            c.ellipse(x + int(0.7 * L), y + row * L, L * 0.28, L * 0.28,
                      mulc(robe, 0.6))
            c.ellipse(x + int(0.7 * L) - L * 0.09,
                      y + row * L - L * 0.09, L * 0.12, L * 0.12,
                      mulc(robe, 1.25))

    # фартук
    if apron:
        ap0, ap1 = apron
        strap = mulc(ap0, 0.85)
        # лямки к плечам
        for sx0, sx1 in ((int(1.1 * L), int(1.6 * L)),
                         (w - int(1.6 * L), w - int(1.1 * L))):
            c.line(x + sx0, y + int(0.6 * L), x + sx1, y + int(5 * L),
                   strap, max(2, L // 5))
        panel_x, panel_y = x + L, y + int(5.2 * L)
        panel_w, panel_h = w - 2 * L, int(6.5 * L)
        c.rrect(panel_x, panel_y, panel_w, panel_h, int(L * 0.4), ap0)
        cloth(c, panel_x + int(L * 0.3), panel_y + int(L * 0.3),
              panel_w - int(L * 0.6), panel_h - int(L * 0.6), ap0, ap1,
              rng, noise=0.05, thread=0.09)
        # строчка по периметру
        if s.get("stitched") or s.get("stains") or s.get("soot") \
                or s.get("plates") or True:
            stitches(c, panel_x + int(L * 0.25), panel_y + int(L * 0.25),
                     panel_w - int(L * 0.5), panel_h - int(L * 0.5),
                     lerp(ap0, (0, 0, 0), 0.35), dash=10, gap=8, width=2,
                     inset=6)
        # карман
        pk_x, pk_y = panel_x + int(1.5 * L), panel_y + int(3.4 * L)
        pk_w, pk_h = panel_w - 3 * L, int(2 * L)
        c.rrect(pk_x, pk_y, pk_w, pk_h, 6, mulc(ap1, 1.05))
        stitches(c, pk_x + 4, pk_y + 4, pk_w - 8, pk_h - 8,
                 mulc(ap1, 0.8), dash=8, gap=7, width=2, inset=4)

    # пятна крови (мясник)
    if s.get("stains"):
        for _ in range(14):
            bx = x + rng.randint(int(1.2 * L), w - int(2 * L))
            by = y + rng.randint(int(4.8 * L), int(10.5 * L))
            rr = rng.randint(3, int(L * 0.9))
            c.ellipse(bx, by, rr, rr, (150, 24, 20), a=rng.randint(60, 110))

    # каменная крошка (каменщик)
    if s.get("speckle"):
        for _ in range(60):
            bx = x + rng.randint(2, w - 2)
            by = y + rng.randint(2, h - 2)
            c.put(bx, by, rng.choice([mulc(apron[0], 1.3), mulc(apron[0], 0.65)]))

    # клёпки брони (оружейник)
    if s.get("plates"):
        for row in (2, 6, 9):
            for i in range(7):
                c.ellipse(x + (i + 0.5) * L, y + row * L, L * 0.22,
                          L * 0.22, (176, 180, 188))
                c.ellipse(x + (i + 0.5) * L - L * 0.06,
                          y + row * L - L * 0.06, L * 0.07, L * 0.07,
                          (226, 230, 238))

    # сажа (кузнец)
    if s.get("soot"):
        for _ in range(30):
            bx = x + rng.randint(0, w - 1)
            by = y + rng.randint(4, h - 1)
            c.put(bx, by, rng.choice([(34, 32, 30), (24, 22, 20)]))

    # свиток на поясе (картограф)
    if s.get("scroll"):
        sc = s["scroll"]
        c.rrect(x + int(5.6 * L), y + int(5.2 * L), int(L * 1.4),
                int(L * 3.2), 4, sc)
        c.line(x + int(5.6 * L), y + int(5.2 * L) + 3,
               x + int(7 * L), y + int(5.2 * L) + 3, mulc(sc, 0.8), 3)
        c.line(x + int(5.6 * L), y + int(8.4 * L) - 3,
               x + int(7 * L), y + int(8.4 * L) - 3, mulc(sc, 0.8), 3)

    # шерстяной ворот (пастух)
    if s.get("wool"):
        c.ellipse(x + int(4 * L), y + int(0.6 * L), int(2.6 * L),
                  int(1.4 * L), (242, 238, 228))
        c.ellipse(x + int(1.8 * L), y + int(1.1 * L), int(1.3 * L),
                  int(1.0 * L), (238, 232, 220))
        c.ellipse(x + int(6.2 * L), y + int(1.1 * L), int(1.3 * L),
                  int(1.0 * L), (238, 232, 220))

    # закатанные рукава на груди (кузнец)
    if s.get("rolled"):
        pass  # рукава обрабатываются в руках


# ====================== руки и ноги ======================

def paint_arm(c, key, s, rng, left=False):
    """Рукав + манжета + кисть."""
    robe, dark = s["robe"]
    trim = s["trim"]
    L = SCALE
    x, y, w, h = (v * SCALE for v in UV[key])
    sleeve = robe if not key.startswith(("larm", "rarm")) else robe
    is_side = key.endswith(("right", "left"))
    is_topbot = key.endswith(("top", "bot"))
    if is_topbot:
        # плечо/ладонь
        if key.endswith("top"):
            cloth(c, x, y, w, h, lerp(robe, (255, 255, 255), 0.10),
                  lerp(dark, (255, 255, 255), 0.08), rng, noise=0.04,
                  thread=0.06)
        else:  # низ руки — ладонь
            skin_fill(c, x, y, w, h, mulc(s["skin"], 0.85), rng)
        ao(c, x, y, w, h)
        return
    if is_side:
        sleeve = mulc(robe, 0.92)
    cloth(c, x, y, w, h, sleeve, mulc(dark, 0.95), rng, noise=0.05,
          thread=0.08)
    # манжета
    c.rect(x, y + 7 * L, w, int(L * 0.8), trim)
    c.line(x, y + 7 * L + int(L * 0.4), x + w, y + 7 * L + int(L * 0.4),
           mulc(trim, 0.7), max(1, L // 12))
    # кисть
    skin_fill(c, x, y + 8 * L, w, 4 * L, s["skin"], rng)
    c.line(x + L, y + int(8.7 * L), x + L, y + int(11 * L),
           mulc(s["skin"], 0.85), max(1, L // 10))
    c.line(x + 2 * L, y + int(8.7 * L), x + 2 * L, y + int(11 * L),
           mulc(s["skin"], 0.85), max(1, L // 10))
    ao(c, x, y, w, h)


def paint_leg(c, key, s, rng, left=False):
    """Штанина + башмак."""
    L = SCALE
    x, y, w, h = (v * SCALE for v in UV[key])
    pants = (72, 62, 50)
    pants_d = (52, 44, 34)
    shoe = (46, 36, 28)
    is_side = key.endswith(("right", "left"))
    if key.endswith("top"):
        cloth(c, x, y, w, h, mulc(pants, 1.1), mulc(pants_d, 1.1), rng,
              noise=0.04, thread=0.07)
        ao(c, x, y, w, h)
        return
    if key.endswith("bot"):
        # подошва
        cloth(c, x, y, w, h, mulc(shoe, 0.55), mulc(shoe, 0.4), rng,
              noise=0.04, thread=0.05)
        ao(c, x, y, w, h)
        return
    if is_side:
        pants = mulc(pants, 0.92)
    cloth(c, x, y, w, h, pants, pants_d, rng, noise=0.05, thread=0.09)
    # башмак
    cloth(c, x, y + 9 * L, w, 3 * L, shoe, mulc(shoe, 0.78), rng,
          noise=0.04, thread=0.06)
    c.line(x, y + 9 * L, x + w, y + 9 * L, mulc(shoe, 1.3), max(1, L // 12))
    # язычок и шнурки
    c.line(x + L, y + 9 * L + int(L * 0.5), x + 2 * L,
           y + 9 * L + int(L * 0.5), mulc(shoe, 1.35), max(2, L // 7))
    c.line(x + L, y + int(10.4 * L), x + 2 * L, y + int(10.4 * L),
           mulc(shoe, 1.35), max(2, L // 7))
    ao(c, x, y, w, h)


# ====================== сборка скина ======================

def make_skin(profession, seed, biome="plains"):
    """Собрать скин 1024x1024 для профессии."""
    rng = random.Random(seed)
    style = dict(STYLES[profession])
    style["skin"] = SKIN_TONES[max(0, min(len(SKIN_TONES) - 1,
                                          style.get("skin", 2) + rng.randint(-1, 1)))]
    style["eye"] = rng.choice(EYE_COLORS)
    if style.get("hair") is not None:
        style["hair"] = HAIR_COLORS[style["hair"]]
    c = Canvas()

    # --- голова ---
    paint_face(c, *(v * SCALE for v in UV["head_front"]), style, rng)
    for side in ("head_right", "head_left"):
        paint_head_sides(c, side, style, rng)
    paint_head_top(c, *(v * SCALE for v in UV["head_top"]), style, rng)
    paint_head_back(c, *(v * SCALE for v in UV["head_back"]), style, rng)
    paint_head_bot(c, *(v * SCALE for v in UV["head_bot"]), style, rng)
    paint_nose(c, style, rng)

    # --- тело ---
    paint_body(c, style, rng)

    # --- руки ---
    for arm in ("rarm", "larm"):
        for k in ("front", "back", "right", "left", "top", "bot"):
            paint_arm(c, f"{arm}_{k}", style, rng, left=(arm == "larm"))

    # --- ноги ---
    for leg in ("rleg", "lleg"):
        for k in ("front", "back", "right", "left", "top", "bot"):
            paint_leg(c, f"{leg}_{k}", style, rng, left=(leg == "lleg"))

    # --- биом-тинт ---
    tint = BIOME_TINTS.get(biome)
    if tint:
        color, alpha = tint
        for key in ("body_front", "body_back", "body_right", "body_left",
                    "rarm_front", "rarm_back", "larm_front", "larm_back",
                    "head_top", "head_back", "head_front", "head_right",
                    "head_left"):
            x, y, w, h = (v * SCALE for v in UV[key])
            for py in range(y, y + h):
                for px in range(x, x + w):
                    ex = c.px[px, py]
                    if ex[3] > 0:
                        c.px[px, py] = blend_existing(ex, color, alpha / 255)
    return c.img


# ====================== baby и zombie ======================

def rgn_baby(c, key, painter):
    x, y, w, h = (v * SCALE for v in BABY_UV[key])
    painter(c, x, y, w, h)


def make_baby(seed=42):
    """Малыш: большая голова 10x10, зелёная роба, огромные глаза."""
    rng = random.Random(seed)
    c = Canvas()
    L = SCALE
    skin = (226, 190, 160)
    robe = (102, 152, 82)
    robe_d = (78, 116, 62)

    def face_region():
        return (v * SCALE for v in BABY_UV["head_front"])

    # голова
    x, y, w, h = face_region()
    skin_fill(c, x, y, w, h, skin, rng, noise=0.035)
    # волосы — хохолок
    cloth(c, x, y, w, L, (58, 40, 26), mulc((58, 40, 26), 0.7), rng,
          noise=0.04, thread=0.08)
    c.ellipse(x + int(4.5 * L), y - L, L * 1.3, L * 0.9, (58, 40, 26))
    # глаза — большие
    for ex in (2, 6):
        cx, cy = x + (ex + 1) * L, y + int(3 * L)
        c.rect(cx - L, cy - L, 2 * L, 2 * L, (246, 246, 244))
        c.ellipse(cx + L * 0.2, cy + L * 0.1, L * 0.55, L * 0.55,
                  (52, 92, 130))
        c.ellipse(cx + L * 0.15, cy + L * 0.05, L * 0.3, L * 0.3, (24, 22, 20))
        c.ellipse(cx - L * 0.25, cy - L * 0.35, L * 0.16, L * 0.16,
                  (252, 252, 252))
        c.line(cx - L, cy - L, cx + L, cy - L, (150, 120, 96), max(2, L // 8))
    # брови
    for bx in (2, 6):
        c.rect(x + bx * L, y + int(1.6 * L), int(1.8 * L), L // 3,
               (58, 40, 26))
    # нос-кнопка
    c.ellipse(x + int(4.5 * L), y + int(5 * L), L * 0.55, L * 0.5,
              mulc(skin, 0.9))
    c.ellipse(x + int(4.5 * L) - L * 0.15, y + int(5 * L) - L * 0.15,
              L * 0.2, L * 0.18, mulc(skin, 1.1))
    # рот — улыбка
    c.line(x + int(3.8 * L), y + int(6.3 * L), x + int(5.2 * L),
           y + int(6.3 * L), (150, 90, 76), max(2, L // 6))
    c.line(x + int(3.8 * L), y + int(6.3 * L), x + int(4.5 * L),
           y + int(6.8 * L), (150, 90, 76), max(2, L // 6))
    c.line(x + int(5.2 * L), y + int(6.3 * L), x + int(4.5 * L),
           y + int(6.8 * L), (150, 90, 76), max(2, L // 6))
    # румянец
    for sx in (0, 7):
        c.ellipse(x + (sx + 0.6) * L, y + int(5 * L), L * 0.7, L * 0.4,
                  (222, 128, 112), a=80)
    for key in ("head_top", "head_bot", "head_right", "head_left", "head_back"):
        rgn_baby(c, key, lambda cc, x2, y2, w2, h2, k=key:
                 (cloth(cc, x2, y2, w2, h2, skin, mulc(skin, 0.8), rng,
                        noise=0.03, thread=0.06)
                  if k != "head_top" else
                  cloth(cc, x2, y2, w2, h2, (58, 40, 26),
                        mulc((58, 40, 26), 0.7), rng, noise=0.04,
                        thread=0.08)))
    ao(c, x, y, w, h)

    # тело + роба
    for key in ("body_front", "body_back"):
        x, y, w, h = (v * SCALE for v in BABY_UV[key])
        cloth(c, x, y, w, h, robe, robe_d, rng, noise=0.045, thread=0.08)
        if key == "body_front":
            c.rect(x, y + 3 * L, w, int(L * 0.8), (64, 88, 46))
            c.ellipse(x + int(3 * L), y + int(1.8 * L), L * 0.3, L * 0.3,
                      mulc(robe, 0.7))
        ao(c, x, y, w, h)
    for key in ("body_right", "body_left"):
        x, y, w, h = (v * SCALE for v in BABY_UV[key])
        cloth(c, x, y, w, h, mulc(robe, 0.92), mulc(robe_d, 0.92), rng,
              noise=0.045, thread=0.08)
        ao(c, x, y, w, h)
    for key in ("body_top", "body_bot"):
        x, y, w, h = (v * SCALE for v in BABY_UV[key])
        cloth(c, x, y, w, h, *(mulc(robe, 1.1), mulc(robe_d, 0.8)), rng,
              noise=0.04, thread=0.06)
        ao(c, x, y, w, h)

    # руки, ноги
    for arm in ("rarm", "larm"):
        for k in ("front", "back", "right", "left", "top", "bot"):
            x, y, w, h = (v * SCALE for v in BABY_UV[f"{arm}_{k}"])
            if k == "bot":
                skin_fill(c, x, y, w, h, skin, rng)
            else:
                cloth(c, x, y, w, h, robe, robe_d, rng, noise=0.045,
                      thread=0.08)
            ao(c, x, y, w, h)
    for leg in ("rleg", "lleg"):
        for k in ("front", "back", "right", "left", "top", "bot"):
            x, y, w, h = (v * SCALE for v in BABY_UV[f"{leg}_{k}"])
            if k == "bot":
                cloth(c, x, y, w, h, (80, 70, 55), (60, 52, 40), rng,
                      noise=0.04, thread=0.06)
            else:
                cloth(c, x, y, w, h, (84, 72, 56), (64, 54, 42), rng,
                      noise=0.05, thread=0.09)
            ao(c, x, y, w, h)
    return c.img


def make_zombie(seed=77):
    """Зомби-житель: зелёная кожа, красные глаза, раны и кровь."""
    rng = random.Random(seed)
    c = Canvas()
    L = SCALE
    zskin = (74, 116, 66)
    zskin_d = mulc(zskin, 0.75)
    robe = (52, 56, 58)
    robe_d = (40, 42, 44)

    def head_front():
        return (v * SCALE for v in UV["head_front"])

    x, y, w, h = head_front()
    skin_fill(c, x, y, w, h, zskin, rng, noise=0.06)
    # глаза — светящиеся красные
    for ex in (1, 5):
        cx, cy = x + (ex + 1) * L, y + int(2.8 * L)
        c.rect(cx - L + 4, cy - int(L * 0.7), 2 * L - 8, int(L * 1.4),
               (210, 40, 24))
        c.ellipse(cx, cy, L * 0.42, L * 0.42, (255, 120, 60))
        c.ellipse(cx - L * 0.15, cy - L * 0.15, L * 0.16, L * 0.16,
                  (255, 230, 200))
        # тёмные круги под глазами
        c.ellipse(cx, cy + L * 0.75, L * 0.9, L * 0.45, (44, 70, 40), a=120)
    # брови — хмурые
    c.line(x + L, y + int(1.4 * L), x + int(3.2 * L), y + int(2 * L),
           (30, 46, 28), max(2, L // 6))
    c.line(x + w - L, y + int(1.4 * L), x + w - int(3.2 * L),
           y + int(2 * L), (30, 46, 28), max(2, L // 6))
    # нос с гнилой ноздрёй
    c.ellipse(x + int(4 * L), y + int(4.2 * L), L * 0.9, L * 1.1,
              mulc(zskin, 1.05))
    c.ellipse(x + int(4 * L), y + int(5 * L), L * 0.4, L * 0.5,
              (40, 60, 36))
    # рот — кривой оскал
    c.line(x + int(2.8 * L), y + int(6.6 * L), x + int(5.4 * L),
           y + int(6.6 * L), (30, 40, 28), max(3, L // 5))
    c.line(x + int(3.6 * L), y + int(6.6 * L), x + int(3.2 * L),
           y + int(7.2 * L), (30, 40, 28), max(3, L // 5))
    c.line(x + int(4.6 * L), y + int(6.6 * L), x + int(5.0 * L),
           y + int(7.0 * L), (30, 40, 28), max(3, L // 5))
    # раны
    for _ in range(6):
        bx = rng.randint(0, w - 2 * L)
        by = rng.randint(L, h - 2 * L)
        c.ellipse(x + bx, y + by, L * 0.7, L * 0.5, (52, 84, 46))
        c.ellipse(x + bx + L * 0.2, y + by + L * 0.15, L * 0.3, L * 0.2,
                  (120, 30, 24), a=150)
    # кровь у рта
    c.line(x + int(2.8 * L), y + int(7.0 * L), x + int(1.8 * L),
           y + int(7.6 * L), (120, 22, 18), max(2, L // 6))
    c.line(x + int(5.4 * L), y + int(7.0 * L), x + int(6.4 * L),
           y + int(7.5 * L), (120, 22, 18), max(2, L // 6))
    # лысина с проплешинами
    x, y, w, h = (v * SCALE for v in UV["head_top"])
    cloth(c, x, y, w, h, mulc(zskin, 0.8), mulc(zskin, 0.62), rng,
          noise=0.07, thread=0.08)
    for _ in range(8):
        c.put(x + rng.randint(0, w - 1), y + rng.randint(0, h - 1),
              mulc(zskin, 0.55))
    ao(c, x, y, w, h)
    for side in ("head_right", "head_left"):
        x, y, w, h = (v * SCALE for v in UV[side])
        skin_fill(c, x, y, w, h, mulc(zskin, 0.92), rng, noise=0.06)
        ao(c, x, y, w, h)
    x, y, w, h = (v * SCALE for v in UV["head_back"])
    skin_fill(c, x, y, w, h, mulc(zskin, 0.88), rng, noise=0.06)
    ao(c, x, y, w, h)
    x, y, w, h = (v * SCALE for v in UV["head_bot"])
    skin_fill(c, x, y, w, h, mulc(zskin, 0.6), rng, noise=0.05)
    ao(c, x, y, w, h)
    # нос-кубик
    for key, mult in (("nose_top", 1.1), ("nose_front", 1.0),
                      ("nose_right", 0.85), ("nose_left", 0.85),
                      ("nose_back", 0.75), ("nose_bot", 0.6)):
        x, y, w, h = (v * SCALE for v in UV[key])
        skin_fill(c, x, y, w, h, mulc(zskin, mult), rng, noise=0.06)
        ao(c, x, y, w, h, strength=0.3)
    # роба — рваная
    for key in ("body_front", "body_back"):
        x, y, w, h = (v * SCALE for v in UV[key])
        cloth(c, x, y, w, h, robe, robe_d, rng, noise=0.07, thread=0.10)
        if key == "body_front":
            c.rect(x, y + 4 * L, w, int(L * 0.9), (44, 38, 34))
            for _ in range(25):
                c.put(x + rng.randint(0, w - 1), y + rng.randint(0, h - 1),
                      rng.choice([(38, 40, 42), (60, 62, 60)]))
        ao(c, x, y, w, h)
    for side in ("body_right", "body_left"):
        x, y, w, h = (v * SCALE for v in UV[side])
        cloth(c, x, y, w, h, mulc(robe, 0.9), mulc(robe_d, 0.9), rng,
              noise=0.07, thread=0.10)
        ao(c, x, y, w, h)
    x, y, w, h = (v * SCALE for v in UV["body_top"])
    cloth(c, x, y, w, h, mulc(robe, 1.1), mulc(robe_d, 0.9), rng, noise=0.06,
          thread=0.08)
    ao(c, x, y, w, h)
    x, y, w, h = (v * SCALE for v in UV["body_bot"])
    cloth(c, x, y, w, h, mulc(robe_d, 0.7), mulc(robe_d, 0.5), rng,
          noise=0.06, thread=0.08)
    ao(c, x, y, w, h)
    for arm in ("rarm", "larm"):
        for k in ("front", "back", "right", "left", "top", "bot"):
            x, y, w, h = (v * SCALE for v in UV[f"{arm}_{k}"])
            if k == "bot":
                skin_fill(c, x, y, w, h, mulc(zskin, 0.85), rng, noise=0.06)
            else:
                cloth(c, x, y, w, h, robe, robe_d, rng, noise=0.07,
                      thread=0.10)
            ao(c, x, y, w, h)
    for leg in ("rleg", "lleg"):
        for k in ("front", "back", "right", "left", "top", "bot"):
            x, y, w, h = (v * SCALE for v in UV[f"{leg}_{k}"])
            if k == "bot":
                cloth(c, x, y, w, h, (40, 34, 28), (30, 26, 22), rng,
                      noise=0.05, thread=0.06)
            else:
                cloth(c, x, y, w, h, (58, 52, 44), (44, 40, 34), rng,
                      noise=0.06, thread=0.09)
            ao(c, x, y, w, h)
    return c.img


# ====================== сборка файлов ======================

def get_output_dir():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, "src", "main", "resources", "textures", "entities")
    os.makedirs(out, exist_ok=True)
    return out


def build_atlas():
    out_dir = get_output_dir()
    atlas = Image.new("RGBA", (ATLAS_W, ATLAS_H), (0, 0, 0, 0))
    manifest = {
        "version": 2,
        "skin_width": TILE,
        "skin_height": TILE,
        "atlas_width": ATLAS_W,
        "atlas_height": ATLAS_H,
        "scale": SCALE,
        "professions": {},
    }
    print(f"Генерация {len(PROFESSIONS)} профессий (1024x1024 каждая)...")
    for i, prof in enumerate(PROFESSIONS):
        skin = make_skin(prof, seed=1000 + i)
        atlas.paste(skin, (i * TILE, 0))
        skin.save(os.path.join(out_dir, f"villager_{prof}.png"))
        manifest["professions"][prof] = {
            "atlas_index": i, "u": i * TILE, "v": 0,
            "width": TILE, "height": TILE,
        }
        print(f"  ✓ {prof}")
    atlas_path = os.path.join(out_dir, "villager_atlas.png")
    atlas.save(atlas_path)
    print(f"✅ Атлас: {atlas_path} ({ATLAS_W}x{ATLAS_H})")
    with open(os.path.join(out_dir, "villager_atlas.json"), "w",
              encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)

    # биом-вариации фермера
    bio_atlas = Image.new("RGBA", (TILE * len(BIOMES), TILE), (0, 0, 0, 0))
    for i, biome in enumerate(BIOMES):
        skin = make_skin("farmer", seed=2000 + i, biome=biome)
        bio_atlas.paste(skin, (i * TILE, 0))
        skin.save(os.path.join(out_dir, f"villager_farmer_{biome}.png"))
    bio_atlas.save(os.path.join(out_dir, "villager_biomes_atlas.png"))
    print(f"✅ Биом-атлас: {len(BIOMES)} вариаций фермера")

    # baby + zombie
    baby = make_baby(42)
    zombie = make_zombie(77)
    baby.save(os.path.join(out_dir, "villager_baby.png"))
    zombie.save(os.path.join(out_dir, "villager_zombie.png"))
    var_atlas = Image.new("RGBA", (TILE * 2, TILE), (0, 0, 0, 0))
    var_atlas.paste(baby, (0, 0))
    var_atlas.paste(zombie, (TILE, 0))
    var_atlas.save(os.path.join(out_dir, "villager_variants_atlas.png"))
    print(f"✅ Варианты: baby + zombie ({TILE}x{TILE})")

    # предпросмотры (сетка 4x4, 512px на скин)
    preview_dir = os.path.dirname(os.path.abspath(__file__))
    grid = Image.new("RGBA", (4 * 512, 4 * 512), (48, 48, 52, 255))
    for i, prof in enumerate(PROFESSIONS):
        tile = Image.open(os.path.join(out_dir, f"villager_{prof}.png"))
        tile = tile.resize((512, 512), Image.NEAREST)
        grid.paste(tile, ((i % 4) * 512, (i // 4) * 512))
    grid_path = os.path.join(preview_dir, "atlas_preview.png")
    grid.save(grid_path)
    print(f"✅ Предпросмотр: {grid_path}")

    var_preview = Image.new("RGBA", (1024, 512), (48, 48, 52, 255))
    for i, img in enumerate((baby, zombie)):
        var_preview.paste(img.resize((512, 512), Image.NEAREST), (i * 512, 0))
    var_preview.save(os.path.join(preview_dir, "variants_preview.png"))

    bio_preview = Image.new("RGBA", (512 * len(BIOMES), 512), (48, 48, 52, 255))
    for i, biome in enumerate(BIOMES):
        tile = Image.open(os.path.join(out_dir, f"villager_farmer_{biome}.png"))
        bio_preview.paste(tile.resize((512, 512), Image.NEAREST), (i * 512, 0))
    bio_preview.save(os.path.join(preview_dir, "biomes_preview.png"))
    print(f"✅ Предпросмотры сохранены в {preview_dir}")
    return atlas_path


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    print("=" * 64)
    print("  HD-ТЕКСТУРЫ ЖИТЕЛЕЙ — НОВЫЙ СТИЛЬ")
    print(f"  {TILE}x{TILE} на профессию, атлас {ATLAS_W}x{ATLAS_H}")
    print("=" * 64)
    try:
        from PIL import Image
    except ImportError:
        print("❌ Pillow не установлен: pip install Pillow")
        sys.exit(1)
    build_atlas()
    print()
    print("=" * 64)
    print("  ГОТОВО! Все текстуры перегенерированы в HD.")
    print("=" * 64)


if __name__ == "__main__":
    main()
