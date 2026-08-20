#!/usr/bin/env python3
"""
Генератор текстур сундука и печи (16x16, формат Minecraft).

Рисуем пиксель-арт по сетке 8x8 (каждая ячейка = 2x2 пикселя), как в
tools/gen_wheat_textures.py. Фиксированный сид — текстуры воспроизводимы.

Сундук: дубовые доски с металлическими полосами сверху/снизу, защёлка и
замок с замочной скважиной по центру (бок), доски с полосами (верх).
Печь: камень с тёмным устьем и углями (перед), камень (бок/верх).

Использование:
    python tools/gen_chest_furnace_textures.py
"""

import os
import random
from PIL import Image, ImageDraw

random.seed(2026)  # фиксированный сид — текстуры воспроизводимы

SIZE = 16       # Minecraft-текстура 16x16
CELL = 2        # ячейка сетки 2x2 px → сетка 8x8 символов
GRID = SIZE // CELL

# ── Палитры ────────────────────────────────────────────────────
# Доски дуба
PLANK_L  = [(152, 113, 71), (156, 117, 75), (148, 109, 67)]   # p светлая доска
PLANK_M  = [(140, 103, 63), (144, 107, 67), (136, 99, 59)]    # P средняя доска
PLANK_D  = [(122, 88, 51), (118, 84, 47), (126, 92, 55)]      # m шов между досками
# Металлическая оковка
METAL_D  = [(102, 102, 110), (106, 106, 114), (98, 98, 106)]  # b тёмная
METAL_L  = [(136, 136, 144), (140, 140, 148), (132, 132, 140)]  # B светлая
# Замок
LOCK_L   = [(110, 110, 118), (114, 114, 122)]                 # l пластина замка
LOCK_D   = [(58, 58, 66), (62, 62, 70)]                       # k замочная скважина
# Камень (печь)
STONE_L  = [(126, 126, 126), (130, 130, 130), (122, 122, 122)]  # S светлый
STONE_D  = [(108, 108, 108), (104, 104, 104), (112, 112, 112)]  # s тёмный
STONE_X  = [(94, 94, 102), (90, 90, 98)]                       # c трещина
# Устье печи
MOUTH_D  = [(24, 22, 26), (28, 26, 30), (20, 18, 22)]         # k тёмное устье
EMBER    = [(210, 90, 20), (230, 120, 30), (180, 70, 15), (250, 160, 40)]  # e угли

PAL = {
    "p": PLANK_L,
    "P": PLANK_M,
    "m": PLANK_D,
    "b": METAL_D,
    "B": METAL_L,
    "l": LOCK_L,
    "k": LOCK_D,
    "S": STONE_L,
    "s": STONE_D,
    "c": STONE_X,
}

# ── Сетки ──────────────────────────────────────────────────────
# Сундук: бок — полосы сверху/снизу, доски, защёлка с замком по центру.
CHEST = [
    "bbbbbbbb",   # верхняя металлическая полоса (тёмная)
    "BBBBBBBB",   # полоса (светлая)
    "mPmPmPmP",   # доски (шов через каждые 4px)
    "mPmbbmPm",   # защёлка: вертикальный стержень
    "mPllllmP",   # пластина замка
    "mPlkklmP",   # пластина с замочной скважиной
    "mPmbbmPm",   # стержень продолжается
    "bbbbbbbb",   # нижняя металлическая полоса
]

# Сундук: верх — доски со швами, окантовка полосой по краям.
CHEST_TOP = [
    "bbbbbbbb",
    "BBBBBBBB",
    "mPmPmPmP",
    "PmPmPmPm",
    "mPmPmPmP",
    "PmPmPmPm",
    "BBBBBBBB",
    "bbbbbbbb",
]

# Печь: перед — камень сверху, тёмное устье с углями снизу.
FURNACE = [
    "SSSSSSSS",   # камень
    "SsSSSSsS",
    "SSSSSSSS",
    "SsSSSSSs",
    "SSSSSSSS",
    "SskkkksS",   # верхняя кромка устья
    "kkkkkkkk",   # устье (угли появляются случайно)
    "kkkkkkkk",   # дно устья с углями
]


def jitter(color, amount=8):
    r = max(0, min(255, color[0] + random.randint(-amount, amount)))
    g = max(0, min(255, color[1] + random.randint(-amount, amount)))
    b = max(0, min(255, color[2] + random.randint(-amount, amount)))
    return (r, g, b)


def draw_cell(px, gx, gy, palette, amount=8):
    for j in range(CELL):
        for i in range(CELL):
            c = jitter(random.choice(palette), amount)
            px[gx * CELL + i, gy * CELL + j] = (*c, 255)


def generate(grid, special=None):
    """Отрисовать сетку. special — словарь {ключ: функция(px, gx, gy)}."""
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
                draw_cell(px, gx, gy, PAL[key])
    return img


def mouth_cell(px, gx, gy):
    """Ячейка устья: темнота, изредка раскалённый уголь."""
    if random.random() < 0.12:
        draw_cell(px, gx, gy, EMBER, 12)
    else:
        draw_cell(px, gx, gy, MOUTH_D, 4)


def stone_cell(px, gx, gy):
    """Ячейка камня: светлая, реже тёмная, изредка трещина."""
    roll = random.random()
    if roll < 0.62:
        draw_cell(px, gx, gy, STONE_L, 8)
    elif roll < 0.9:
        draw_cell(px, gx, gy, STONE_D, 6)
    else:
        draw_cell(px, gx, gy, STONE_X, 5)


def stone_texture(grid):
    """Камень: те же строки, но с вероятностным шумом вместо фиксированных."""
    return generate(grid, special={
        "S": stone_cell,
        "s": stone_cell,
        "c": stone_cell,
    })


def main():
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = os.path.join(project_root, "src", "main", "resources", "textures", "blocks")
    os.makedirs(out_dir, exist_ok=True)

    textures = [
        ("chest",         generate(CHEST)),
        ("chest_top",     generate(CHEST_TOP)),
        ("furnace",       generate(FURNACE, special={"k": mouth_cell})),
        ("furnace_side",  stone_texture(["SSSSSSSS", "sSsSSsSs", "SSsSSsSS",
                                         "sSSSssSS", "SSSSSSSS", "SsSSsSSs",
                                         "SSsSSsSS", "SSSSSSSS"])),
        ("furnace_top",   stone_texture(["SSSSSSSS", "SsSSSSsS", "SSSSSSSS",
                                         "sSSSSSSs", "SSSSSSSS", "SsSSSSsS",
                                         "SSSSSSSS", "sSSSSSSs"])),
    ]

    for name, img in textures:
        img.save(os.path.join(out_dir, f"{name}.png"))
        print(f"OK {name}.png")

    # Предпросмотр: все текстуры в ряд, увеличены в 8 раз, с подписями
    ZOOM = 8
    label_h = 14
    preview = Image.new("RGBA",
        (SIZE * ZOOM * len(textures), SIZE * ZOOM + label_h), (40, 40, 40, 255))
    draw = ImageDraw.Draw(preview)
    for i, (name, img) in enumerate(textures):
        big = img.resize((SIZE * ZOOM, SIZE * ZOOM), Image.NEAREST)
        preview.paste(big, (i * SIZE * ZOOM, label_h), big)
        draw.text((i * SIZE * ZOOM + 4, 3), name, fill=(255, 255, 255))
    preview_path = os.path.join(project_root, "tools", "chest_furnace_preview.png")
    preview.save(preview_path)
    print(f"OK preview: {preview_path}")


if __name__ == "__main__":
    main()