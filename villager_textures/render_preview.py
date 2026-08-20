#!/usr/bin/env python3
"""
Рендер-превью: собирает из скинов 1024x1024 фронтальный вид жителя
(голова, нос, тело, руки, ноги) так, как модель будет выглядеть в игре
(с коэффициентами затенения граней из ModelBox: front = 0.80).

Использование:
    python render_preview.py
Результат: preview_all.png (17 профессий) и preview_variants.png (baby+zombie)
"""

import os
import sys
from PIL import Image, ImageDraw, ImageEnhance

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

TILE = 1024
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources", "textures", "entities")
HERE = os.path.dirname(os.path.abspath(__file__))

PROFESSIONS = ["farmer", "librarian", "blacksmith", "butcher", "priest",
               "fisherman", "fletcher", "leatherworker", "shepherd",
               "toolsmith", "armorer", "weaponsmith", "cartographer",
               "cleric", "mason", "nitwit", "warrior"]

RUS = {"farmer": "Фермер", "librarian": "Библиотекарь", "blacksmith": "Кузнец",
       "butcher": "Мясник", "priest": "Священник", "fisherman": "Рыбак",
       "fletcher": "Лучник", "leatherworker": "Кожевник",
       "shepherd": "Пастух", "toolsmith": "Инструментальщик",
       "armorer": "Оружейник", "weaponsmith": "Оружейник",
       "cartographer": "Картограф", "cleric": "Клирик",
       "mason": "Каменщик", "nitwit": "Бездельник",
       "warrior": "Воин"}

# Части модели в единицах скина (1 unit = 1/16 блока), вид спереди.
# (region_key, x_off, y_off, w, h) — offset в блочных единицах от центра/верха.
PARTS = [
    ("head_front", -4, 0, 8, 8),
    ("nose_front", -1, 2, 2, 4),
    ("body_front", -4, 8, 8, 12),
    ("rarm_front", -7, 8, 4, 12),
    ("larm_front", 3, 8, 4, 12),
    ("rleg_front", -5.9, 20, 4, 12),
    ("lleg_front", 1.9, 20, 4, 12),
]

def shade_tile(tile, factor):
    return ImageEnhance.Brightness(tile).enhance(factor)

def render_villager(skin_path, scale, scale_factor=1.0):
    """Фронтальный вид жителя; scale_factor — уменьшение (baby)."""
    skin = Image.open(skin_path)
    s = scale * scale_factor
    w = int(16 * s)
    h = int(34 * s)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for key, x0, y0, rw, rh in PARTS:
        sx, sy, sw, sh = {
            "head_front": (8, 8, 8, 8), "nose_front": (26, 2, 2, 4),
            "body_front": (20, 20, 8, 12), "rarm_front": (44, 20, 4, 12),
            "larm_front": (36, 52, 4, 12), "rleg_front": (4, 20, 4, 12),
            "lleg_front": (20, 52, 4, 12),
        }[key]
        tile = skin.crop((sx * 16, sy * 16, (sx + sw) * 16, (sy + sh) * 16))
        tile = tile.resize((int(rw * s), int(rh * s)), Image.NEAREST)
        tile = shade_tile(tile, 0.80)
        px = int(w / 2 + x0 * s)
        py = int(y0 * s)
        img.alpha_composite(tile, (px, py))
    return img

def render_group(files, labels, out_name, scale=40, per_row=8):
    cols = min(per_row, len(files))
    rows = (len(files) + cols - 1) // cols
    cell_w = int(16 * scale) + 2 * 24
    cell_h = int(34 * scale) + 48
    canvas = Image.new("RGBA", (cols * cell_w, rows * cell_h), (36, 38, 44, 255))
    draw = ImageDraw.Draw(canvas)
    for i, (path, label) in enumerate(zip(files, labels)):
        r, c = divmod(i, cols)
        img = render_villager(path, scale)
        x = c * cell_w + (cell_w - img.width) // 2
        y = r * cell_h + 20
        # мягкая тень под ногами
        draw.ellipse([x + img.width * 0.15, y + img.height - 14,
                      x + img.width * 0.85, y + img.height + 2],
                     fill=(0, 0, 0, 110))
        canvas.alpha_composite(img, (x, y))
        tw = draw.textlength(label, font=None)
        draw.text((c * cell_w + (cell_w - tw) / 2, y + img.height + 8),
                  label, fill=(232, 232, 232))
    canvas.save(os.path.join(HERE, out_name))
    print(f"OK: {os.path.join(HERE, out_name)} ({canvas.width}x{canvas.height})")

def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    files = [os.path.join(OUT, f"villager_{p}.png") for p in PROFESSIONS]
    labels = [RUS[p] for p in PROFESSIONS]
    render_group(files, labels, "preview_all.png")
    render_group([os.path.join(OUT, "villager_baby.png"),
                  os.path.join(OUT, "villager_zombie.png")],
                 ["Малыш", "Зомби-житель"], "preview_variants.png",
                 scale=60, per_row=2)

if __name__ == "__main__":
    main()
