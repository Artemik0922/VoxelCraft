#!/usr/bin/env python3
"""
Генератор текстуры дубовой двери в стиле Minecraft (16x16 x 2 тайла).

Как в ванильном Minecraft дверь занимает 2 блока: нижняя половина
текстуры (с ручкой) рендерится на нижнем блоке, верхняя (с окном) —
на верхнем. Каждая половина — 16x16 пикселей:
  - тёмная дубовая рама по периметру,
  - дощатые филёнки с лёгким зерном,
  - ручка на правой филёнке нижней половины,
  - окно в нижней части верхней половины (стекло с переплётом).

Использование:
    python tools/gen_door_textures.py
"""

import os
import random
from PIL import Image

random.seed(2026)

SIZE = 16

# ── Палитра (как у дуба в MC) ───────────────────────────────
FRAME      = (64, 44, 28)    # тёмная рама
PLANK      = (140, 96, 52)   # доска
PLANK_DARK = (122, 82, 44)   # тень/зерно
PLANK_LIGHT= (154, 110, 64)  # блик
HANDLE     = (198, 170, 118) # латунная ручка
HANDLE_DK  = (120, 90, 55)   # тень ручки
GLASS      = (150, 182, 208) # стекло
GLASS_LT   = (172, 200, 224) # блик на стекле


def plank_px(x, y):
    """Доска с лёгким зерном: горизонтальные прожилки + шум."""
    r = random.random()
    if y % 4 == 0 and r < 0.35:
        return PLANK_DARK
    if r < 0.12:
        return PLANK_DARK
    if r < 0.22:
        return PLANK_LIGHT
    return PLANK


def draw_bottom():
    """Нижняя половина двери: три ряда филёнок + ручка."""
    px = [[0] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            # рама по периметру
            if x == 0 or x == SIZE - 1 or y == 0 or y == SIZE - 1:
                px[y][x] = FRAME
            # горизонтальные ригели на y=5 и y=10
            elif y == 5 or y == 10:
                px[y][x] = FRAME
            else:
                px[y][x] = plank_px(x, y)
    # Ручка: на правой филёнке среднего ряда
    for dy in range(3):
        for dx in range(2):
            px[7 + dy][12 + dx] = HANDLE
    # Тень ручки (нижний-правый пиксель)
    px[9][13] = HANDLE_DK
    # Блик на ручке
    px[7][12] = (224, 200, 150)
    return px


def draw_top():
    """Верхняя половина двери: два ряда филёнок + окно снизу."""
    px = [[0] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if x == 0 or x == SIZE - 1 or y == 0 or y == SIZE - 1:
                px[y][x] = FRAME
            elif y == 5 or y == 10:
                px[y][x] = FRAME
            elif y >= 11:
                # окно: стекло с переплётом
                if x == 7 or x == 8:          # вертикальный импост
                    px[y][x] = FRAME
                elif y == 13:                  # горизонтальный импост
                    px[y][x] = FRAME
                elif y == 11:                  # блик по верху стекла
                    px[y][x] = GLASS_LT
                else:
                    px[y][x] = GLASS
            else:
                px[y][x] = plank_px(x, y)
    return px


def to_image(grid, scale=1):
    img = Image.new("RGBA", (SIZE * scale, SIZE * scale))
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b = grid[y][x]
            for sy in range(scale):
                for sx in range(scale):
                    img.putpixel((x * scale + sx, y * scale + sy), (r, g, b, 255))
    return img


def main():
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = os.path.join(project_root, "src", "main", "resources", "textures", "blocks")

    random.seed(2026)
    bottom = draw_bottom()
    random.seed(2027)  # верхняя половина с другим зерном
    top = draw_top()

    os.makedirs(out_dir, exist_ok=True)
    to_image(bottom).save(os.path.join(out_dir, "oak_door_bottom.png"))
    to_image(top).save(os.path.join(out_dir, "oak_door_top.png"))
    print(f"OK: {os.path.join(out_dir, 'oak_door_bottom.png')}")
    print(f"OK: {os.path.join(out_dir, 'oak_door_top.png')}")

    # Превью 4x (верх над низом, как выглядит дверь)
    preview = Image.new("RGBA", (SIZE * 4, SIZE * 8))
    preview.paste(to_image(top, 4), (0, 0))
    preview.paste(to_image(bottom, 4), (0, SIZE * 4))
    preview_path = os.path.join(project_root, "tools", "door_preview.png")
    preview.save(preview_path)
    print(f"OK preview: {preview_path}")


if __name__ == "__main__":
    main()