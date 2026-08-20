#!/usr/bin/env python3
"""
Генератор текстур пшеницы (8 стадий роста, формат Minecraft 16x16).

Рисуем пиксель-арт по сетке 8x8 (каждая ячейка = 2x2 пикселя), фон
прозрачный — спрайт рендерится cross-billboard, как в ванильном
Minecraft: стебли растут от нижнего края, колосья — сверху.

Стадии 0-3: зелёный рост, стадии 4-6: переход в золото,
стадия 7: полностью зрелые золотые колосья с остями.

Использование:
    python tools/gen_wheat_textures.py
"""

import os
import random
from PIL import Image

random.seed(2026)  # фиксированный сид — текстуры воспроизводимы

SIZE = 16       # Minecraft-текстура 16x16
CELL = 2        # ячейка сетки 2x2 px → сетка 8x8 символов
GRID = SIZE // CELL

# ── Палитра пшеницы ─────────────────────────────────────────
GREEN_LIGHT = (121, 183, 74)  # кончик молодого ростка
GREEN       = (111, 168, 63)  # стебель
GREEN_DARK  = (85, 138, 47)   # тёмное основание стебля
GOLD_LIGHT  = (234, 210, 90)  # ости (усы)
GOLD        = (212, 174, 60)  # зёрна
GOLD_DARK   = (184, 144, 42)  # тень зёрен
GOLDGREEN   = (168, 184, 60)  # переход зелёного в золото

PAL = {
    "l": [GREEN_LIGHT],
    "M": [GREEN],
    "m": [GREEN_DARK],
    "L": [GOLD_LIGHT],
    "K": [GOLD],
    "D": [GOLD_DARK],
    "G": [GOLDGREEN],
}

# ── 8 стадий ─────────────────────────────────────────────────
# Растение занимает всю высоту спрайта в каждой стадии: высота
# рендера задаётся квадом (0.25..1.0 блока), а спрайт масштабируется
# под неё, как в ванильном Minecraft. От стадии зависит толщина,
# форма и цвет (зелёный → золото).
# Три растения на колонках (0,1), (3,4), (6,7).
GRID0 = [
    "........",
    "ll.ll.ll",
    "ll.ll.ll",
    "ll.ll.ll",
    "lM.lM.lM",
    "lM.lM.lM",
    "lM.lM.lM",
    "mm.mm.mm",
]

GRID1 = [
    "........",
    "ll.ll.ll",
    "lMl.lMl.",
    "lMl.lMl.",
    "lMl.lMl.",
    "MMl.MMl.",
    "MMl.MMl.",
    "mmm.mmm.",
]

GRID2 = [
    "........",
    "lMl.lMl.",
    "MMl.MMl.",
    "MMM.MMM.",
    "MMM.MMM.",
    "MMM.MMM.",
    "MMM.MMM.",
    "mmm.mmm.",
]

GRID3 = [
    "........",
    "G..G..G.",
    "GG.GG.GG",
    "GM.GM.GM",
    "GM.GM.GM",
    "MM.MM.MM",
    "MM.MM.MM",
    "mm.mm.mm",
]

GRID4 = [
    "G..G..G.",
    "GG.GG.GG",
    "GG.GG.GG",
    "GM.GM.GM",
    "GM.GM.GM",
    "MM.MM.MM",
    "MM.MM.MM",
    "mm.mm.mm",
]

GRID5 = [
    "G..G..G.",
    "GG.GG.GG",
    "GK.GK.GK",
    "GK.GK.GK",
    "KM.KM.KM",
    "MM.MM.MM",
    "MM.MM.MM",
    "mm.mm.mm",
]

GRID6 = [
    "G..G..G.",
    "GK.GK.GK",
    "KK.KK.KK",
    "KD.KD.KD",
    "KD.KD.KD",
    "KM.KM.KM",
    "MM.MM.MM",
    "mm.mm.mm",
]

GRID7 = [
    "L..L..L.",
    "LK.LK.LK",
    "KK.KK.KK",
    "KD.KD.KD",
    "KD.KD.KD",
    "KD.KD.KD",
    "KM.KM.KM",
    "MM.MM.MM",
]

STAGES = [
    ("wheat_stage0", GRID0),
    ("wheat_stage1", GRID1),
    ("wheat_stage2", GRID2),
    ("wheat_stage3", GRID3),
    ("wheat_stage4", GRID4),
    ("wheat_stage5", GRID5),
    ("wheat_stage6", GRID6),
    ("wheat_stage7", GRID7),
]


def jitter(color, amount=10):
    r = max(0, min(255, color[0] + random.randint(-amount, amount)))
    g = max(0, min(255, color[1] + random.randint(-amount, amount)))
    b = max(0, min(255, color[2] + random.randint(-amount, amount)))
    return (r, g, b)


def draw_cell(px, gx, gy, palette):
    for j in range(CELL):
        for i in range(CELL):
            c = jitter(random.choice(palette))
            px[gx * CELL + i, gy * CELL + j] = (*c, 255)


def generate_stage(grid):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    for gy, row in enumerate(grid):
        assert len(row) == GRID, f"строка {gy} не {GRID} символов: {row!r}"
        for gx, key in enumerate(row):
            if key == ".":
                continue
            draw_cell(px, gx, gy, PAL[key])
    return img


def main():
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = os.path.join(project_root, "src", "main", "resources", "textures", "blocks")
    os.makedirs(out_dir, exist_ok=True)

    images = []
    for name, grid in STAGES:
        img = generate_stage(grid)
        img.save(os.path.join(out_dir, f"{name}.png"))
        images.append(img)
        print(f"OK {name}.png")

    # Предпросмотр: все стадии в ряд, увеличены в 8 раз
    ZOOM = 8
    preview = Image.new("RGBA", (SIZE * ZOOM * len(images), SIZE * ZOOM), (40, 40, 40, 255))
    for i, img in enumerate(images):
        big = img.resize((SIZE * ZOOM, SIZE * ZOOM), Image.NEAREST)
        preview.paste(big, (i * SIZE * ZOOM, 0), big)
    preview_path = os.path.join(project_root, "tools", "wheat_preview.png")
    preview.save(preview_path)
    print(f"OK preview: {preview_path}")


if __name__ == "__main__":
    main()