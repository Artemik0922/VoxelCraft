#!/usr/bin/env python3
"""
Генератор текстур зачарованного стола, книги и лазурита (16x16, формат Minecraft).

Сетка 8x8 ячеек (2x2 px), как в gen_chest_furnace_textures.py. Фиксированный
сид — текстуры воспроизводимы.

Зачарованный стол (бок): обсидиановая база, тёмно-фиолетовая поверхность
с золотистыми рунами и светящейся кромкой.
Зачарованный стол (верх): обсидиан с рунной окружностью и светящимся
центром.
Книга (предмет): тёмно-красная обложка с бледной полосой страниц.
Лазурит (предмет): голубой самоцвет с фасками.

Использование:
    python tools/gen_enchanting_textures.py
"""

import os
import random
from PIL import Image, ImageDraw

random.seed(2026)

SIZE = 16
CELL = 2
GRID = SIZE // CELL

# ── Палитры ────────────────────────────────────────────────────
# Обсидиан
OBS_L = [(30, 22, 36), (34, 26, 40), (26, 18, 32)]
OBS_D = [(18, 12, 24), (14, 10, 20), (22, 16, 28)]
OBS_X = [(40, 32, 52)]
# Тёмно-фиолетовая поверхность
PUR_L = [(64, 36, 104), (72, 42, 114), (56, 30, 94)]
PUR_D = [(44, 22, 74), (50, 26, 84), (38, 18, 64)]
# Рунные глифы
RUNE_G = [(208, 180, 64), (228, 200, 90), (188, 160, 40)]
RUNE_P = [(150, 80, 220), (170, 100, 240)]
# Светящаяся кромка
GLOW = [(120, 60, 190), (140, 80, 210)]

PAL = {
    "o": OBS_L,
    "O": OBS_D,
    "x": OBS_X,
    "p": PUR_L,
    "P": PUR_D,
    "g": RUNE_G,
    "r": RUNE_P,
}

# ── Сетки ──────────────────────────────────────────────────────
# Бок стола: обсидиановая база, руны по центру, светящаяся кромка сверху
TABLE_SIDE = [
    "gggggggg",   # светящаяся кромка
    "ppprrppp",   # рунный ряд
    "pPrPPrPp",   # руны вразброс
    "PpPggPpP",
    "PrPgPgPr",
    "ppPggPpp",
    "xOxOxOxO",   # обсидиановая база
    "OxOxOxOx",
]

# Верх стола: обсидиан с рунной окружностью и светящимся центром
TABLE_TOP = [
    "xOxOxOxO",
    "OgPrrPgO",
    "xPrggrPx",
    "OggggggO",
    "xrggggrx",
    "xPrggrPx",
    "OgPrrPgO",
    "xOxOxOxO",
]

# Предмет-иконка стола (вид сверху)
TABLE_ITEM = [
    "PPPPPPPP",
    "PPprrPPP",
    "PpgggpPP",
    "prgggrPP",
    "prgggrPP",
    "PpgggpPP",
    "PPprrPPP",
    "PPPPPPPP",
]

# Книга (предмет): красная обложка, бледные страницы
BOOK = [
    "........",
    "..kKKk..",
    ".KccccK.",
    "KcpppcK.",
    "KcpppcK.",
    "KcpppcK.",
    ".KccccK.",
    "..kKKk..",
]

# ── Палитры для книги ──────────────────────────────────────────
BOOK_PAL = {
    "k": [(90, 20, 20), (110, 28, 28), (70, 14, 14)],
    "K": [(130, 40, 40), (150, 52, 52), (110, 30, 30)],
    "c": [(232, 224, 208), (240, 232, 216), (224, 216, 200)],
    "p": [(232, 224, 208), (240, 232, 216), (224, 216, 200)],
}

# Лазурит (предмет): голубой самоцвет
LAPIS = [
    "........",
    "...LLLL.",
    "..LlLLL.",
    "..LllLL.",
    ".LlLLlL.",
    ".LLLlLL.",
    "..LLLL..",
    "........",
]

LAPIS_PAL = {
    "L": [(48, 80, 224), (64, 96, 240), (32, 64, 208)],
    "l": [(144, 168, 255), (160, 184, 255), (128, 152, 245)],
}


def jitter(color, amount=8):
    return (
        max(0, min(255, color[0] + random.randint(-amount, amount))),
        max(0, min(255, color[1] + random.randint(-amount, amount))),
        max(0, min(255, color[2] + random.randint(-amount, amount))),
    )


def draw_cell(px, gx, gy, palette, amount=8):
    for j in range(CELL):
        for i in range(CELL):
            c = jitter(random.choice(palette), amount)
            px[gx * CELL + i, gy * CELL + j] = (*c, 255)


def generate(grid, pal, special=None):
    special = special or {}
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    for gy, row in enumerate(grid):
        assert len(row) == GRID, f"строка {gy} не {GRID} символов: {row!r}"
        for gx, key in enumerate(row):
            if key == ".":
                continue
            if key in special:
                special[key](px, gx, gy)
            else:
                draw_cell(px, gx, gy, pal[key])
    return img


def main():
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = os.path.join(project_root, "src", "main", "resources", "textures", "blocks")
    os.makedirs(out_dir, exist_ok=True)

    textures = [
        ("enchanting_table",      generate(TABLE_SIDE, PAL)),
        ("enchanting_table_top",  generate(TABLE_TOP, PAL)),
        ("enchanting_table_item", generate(TABLE_ITEM, PAL)),
        ("book",                  generate(BOOK, BOOK_PAL)),
        ("lapis_lazuli",          generate(LAPIS, LAPIS_PAL)),
    ]

    for name, img in textures:
        img.save(os.path.join(out_dir, f"{name}.png"))
        print(f"OK {name}.png")

    ZOOM = 8
    label_h = 14
    preview = Image.new("RGBA",
        (SIZE * ZOOM * len(textures), SIZE * ZOOM + label_h), (40, 40, 40, 255))
    draw = ImageDraw.Draw(preview)
    for i, (name, img) in enumerate(textures):
        big = img.resize((SIZE * ZOOM, SIZE * ZOOM), Image.NEAREST)
        preview.paste(big, (i * SIZE * ZOOM, label_h), big)
        draw.text((i * SIZE * ZOOM + 4, 3), name, fill=(255, 255, 255))
    preview_path = os.path.join(project_root, "tools", "enchanting_preview.png")
    preview.save(preview_path)
    print(f"OK preview: {preview_path}")


if __name__ == "__main__":
    main()