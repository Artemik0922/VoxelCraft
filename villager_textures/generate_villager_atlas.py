#!/usr/bin/env python3
"""
Генератор текстур жителей деревень (Minecraft-style villagers).

Создаёт атлас текстур 64x64 для каждого типа жителя с разными профессиями
и биомами. Атлас сохраняется в textures/entities/ для использования игрой.

Профессии: Farmer, Librarian, Blacksmith, Butcher, Priest, Fisherman,
           Fletcher, Leatherworker, Shepherd, Toolsmith, Armorer, Weaponsmith,
           Cartographer, Cleric, Mason, Nitwit

Использование:
    python generate_villager_atlas.py

Результат:
    src/main/resources/textures/entities/villager_atlas.png - все профессии
    src/main/resources/textures/entities/villager_<profession>.png - отдельные текстуры
    villager_textures/atlas_preview.png - увеличенный предпросмотр
"""

import os
import sys
import random
import math
from PIL import Image, ImageDraw

# Seed для воспроизводимости
random.seed(42)

# Размеры скина жителя (как в Minecraft - 64x64)
SKIN_W = 64
SKIN_H = 64

# UV-маппинг частей тела (как в Minecraft villager model)
# Формат: (u, v, width, height) - координаты на атласе
UV_MAP = {
    # Голова 8x8x8
    "head_top":    (8, 0, 8, 8),
    "head_bot":    (16, 0, 8, 8),
    "head_right":  (0, 8, 8, 8),
    "head_front":  (8, 8, 8, 8),
    "head_left":   (16, 8, 8, 8),
    "head_back":   (24, 8, 8, 8),

    # Тело 8x12x4
    "body_top":    (20, 16, 8, 4),
    "body_bot":    (28, 16, 8, 4),
    "body_right":  (16, 20, 4, 12),
    "body_front":  (20, 20, 8, 12),
    "body_left":   (28, 20, 4, 12),
    "body_back":   (32, 20, 8, 12),

    # Правая рука 4x12x4
    "rarm_top":    (44, 16, 4, 4),
    "rarm_bot":    (48, 16, 4, 4),
    "rarm_right":  (40, 20, 4, 12),
    "rarm_front":  (44, 20, 4, 12),
    "rarm_left":   (48, 20, 4, 12),
    "rarm_back":   (52, 20, 4, 12),

    # Левая рука 4x12x4
    "larm_top":    (36, 48, 4, 4),
    "larm_bot":    (40, 48, 4, 4),
    "larm_right":  (32, 52, 4, 12),
    "larm_front":  (36, 52, 4, 12),
    "larm_left":   (40, 52, 4, 12),
    "larm_back":   (44, 52, 4, 12),

    # Правая нога 4x12x4
    "rleg_top":    (4, 16, 4, 4),
    "rleg_bot":    (8, 16, 4, 4),
    "rleg_right":  (0, 20, 4, 12),
    "rleg_front":  (4, 20, 4, 12),
    "rleg_left":   (8, 20, 4, 12),
    "rleg_back":   (12, 20, 4, 12),

    # Левая нога 4x12x4
    "lleg_top":    (20, 48, 4, 4),
    "lleg_bot":    (24, 48, 4, 4),
    "lleg_right":  (16, 52, 4, 12),
    "lleg_front":  (20, 52, 4, 12),
    "lleg_left":   (24, 52, 4, 12),
    "lleg_back":   (28, 52, 4, 12),
}

# ==================== ПАЛИТРЫ ЦВЕТОВ ====================

# Цвета кожи
SKIN_TONES = {
    "light": [(200, 155, 120), (210, 165, 130), (190, 145, 110), (180, 138, 105)],
    "tan":   [(180, 140, 100), (190, 148, 108), (170, 130, 92), (160, 125, 88)],
    "dark":  [(140, 100, 70), (150, 108, 78), (130, 92, 64), (120, 85, 58)],
}

# Цвета одежды по профессиям
PROFESSION_COLORS = {
    "farmer": {
        "robe": [(120, 85, 45), (110, 78, 40), (130, 92, 50), (100, 72, 36)],
        "accent": [(80, 140, 50), (70, 130, 45), (90, 150, 55)],  # зелёный фартук
        "hat": [(160, 120, 60), (150, 110, 55)],
        "belt": [(60, 40, 20)],
    },
    "librarian": {
        "robe": [(80, 60, 100), (70, 52, 90), (90, 68, 110), (75, 55, 95)],
        "accent": [(200, 180, 140), (210, 190, 150)],  # книжные страницы
        "hat": [(60, 40, 80), (50, 35, 70)],
        "belt": [(40, 30, 60)],
    },
    "blacksmith": {
        "robe": [(80, 80, 85), (70, 70, 75), (90, 90, 95), (75, 75, 80)],
        "accent": [(60, 60, 65), (50, 50, 55)],  # тёмный фартук кузнеца
        "hat": [(40, 40, 45), (35, 35, 40)],
        "belt": [(100, 80, 40)],
    },
    "butcher": {
        "robe": [(180, 180, 180), (170, 170, 170), (190, 190, 190)],
        "accent": [(200, 50, 50), (180, 40, 40)],  # красный фартук
        "hat": [(160, 160, 160), (150, 150, 150)],
        "belt": [(120, 80, 40)],
    },
    "priest": {
        "robe": [(160, 100, 160), (150, 90, 150), (170, 110, 170)],
        "accent": [(200, 180, 40), (220, 200, 50)],  # золотые детали
        "hat": [(140, 80, 140), (130, 70, 130)],
        "belt": [(180, 160, 30)],
    },
    "fisherman": {
        "robe": [(100, 120, 140), (90, 110, 130), (110, 130, 150)],
        "accent": [(60, 100, 140), (50, 90, 130)],  # синий
        "hat": [(80, 100, 120), (70, 90, 110)],
        "belt": [(80, 60, 40)],
    },
    "fletcher": {
        "robe": [(140, 110, 70), (130, 100, 65), (150, 120, 75)],
        "accent": [(100, 140, 60), (90, 130, 55)],  # зелёный
        "hat": [(120, 90, 50), (110, 80, 45)],
        "belt": [(70, 50, 30)],
    },
    "leatherworker": {
        "robe": [(130, 90, 50), (120, 82, 45), (140, 98, 55)],
        "accent": [(100, 70, 35), (90, 62, 30)],  # тёмная кожа
        "hat": [(110, 75, 40), (100, 68, 35)],
        "belt": [(80, 55, 28)],
    },
    "shepherd": {
        "robe": [(180, 170, 160), (170, 160, 150), (190, 180, 170)],
        "accent": [(160, 120, 80), (150, 110, 75)],  # коричневый
        "hat": [(170, 160, 150), (160, 150, 140)],
        "belt": [(90, 65, 35)],
    },
    "toolsmith": {
        "robe": [(100, 100, 105), (90, 90, 95), (110, 110, 115)],
        "accent": [(80, 80, 85), (70, 70, 75)],
        "hat": [(60, 60, 65), (55, 55, 60)],
        "belt": [(120, 90, 40)],
    },
    "armorer": {
        "robe": [(90, 95, 100), (80, 85, 90), (100, 105, 110)],
        "accent": [(140, 140, 145), (130, 130, 135)],  # стальной
        "hat": [(70, 75, 80), (65, 70, 75)],
        "belt": [(100, 75, 35)],
    },
    "weaponsmith": {
        "robe": [(85, 90, 95), (75, 80, 85), (95, 100, 105)],
        "accent": [(120, 120, 125), (110, 110, 115)],
        "hat": [(65, 70, 75), (60, 65, 70)],
        "belt": [(110, 85, 38)],
    },
    "cartographer": {
        "robe": [(120, 100, 80), (110, 92, 72), (130, 108, 88)],
        "accent": [(200, 170, 100), (190, 160, 90)],  # золотистый
        "hat": [(100, 82, 62), (92, 75, 55)],
        "belt": [(80, 60, 35)],
    },
    "cleric": {
        "robe": [(140, 80, 140), (130, 72, 130), (150, 88, 150)],
        "accent": [(180, 160, 40), (170, 150, 35)],  # золото
        "hat": [(120, 65, 120), (110, 58, 110)],
        "belt": [(160, 140, 30)],
    },
    "mason": {
        "robe": [(130, 125, 120), (120, 115, 110), (140, 135, 130)],
        "accent": [(100, 95, 90), (90, 85, 82)],  # каменный
        "hat": [(110, 105, 100), (100, 95, 92)],
        "belt": [(80, 60, 35)],
    },
    "nitwit": {
        "robe": [(100, 140, 80), (90, 130, 72), (110, 150, 88)],  # зелёный
        "accent": [(80, 120, 60), (72, 110, 55)],
        "hat": [(90, 130, 70), (82, 120, 64)],
        "belt": [(70, 100, 50)],
    },
}

# Биом-специфичные цвета одежды
BIOME_OVERLAYS = {
    "plains": None,  # базовые цвета
    "desert": {"tint": (220, 190, 120), "alpha": 60},
    "snowy": {"tint": (200, 210, 230), "alpha": 40},
    "taiga": {"tint": (60, 80, 60), "alpha": 30},
    "jungle": {"tint": (40, 100, 40), "alpha": 25},
    "savanna": {"tint": (180, 140, 60), "alpha": 35},
    "swamp": {"tint": (80, 100, 60), "alpha": 30},
}


def jitter(color, amount=15):
    """Добавить шум к цвету."""
    r = max(0, min(255, color[0] + random.randint(-amount, amount)))
    g = max(0, min(255, color[1] + random.randint(-amount, amount)))
    b = max(0, min(255, color[2] + random.randint(-amount, amount)))
    return (r, g, b)


def blend(base, overlay, alpha):
    """Смешать два цвета с прозрачностью."""
    a = alpha / 255.0
    r = int(base[0] * (1 - a) + overlay[0] * a)
    g = int(base[1] * (1 - a) + overlay[1] * a)
    b = int(base[2] * (1 - a) + overlay[2] * a)
    return (r, g, b)


def fill_region(img, region_name, palette, noise=20, pattern=None):
    """Заполнить UV-регион цветами из палитры с шумом."""
    pixels = img.load()
    u, v, w, h = UV_MAP[region_name]

    for py in range(h):
        for px in range(w):
            color = random.choice(palette)
            color = jitter(color, noise)
            if pattern == "stripes" and (py % 4 < 2):
                color = jitter(palette[0], noise + 5)
            elif pattern == "dots" and ((px + py) % 3 == 0):
                color = jitter(palette[-1], noise)
            pixels[u + px, v + py] = (*color, 255)


def draw_face(img, profession):
    """Нарисовать лицо жителя с большим носом (как в Minecraft)."""
    pixels = img.load()
    u, v, w, h = UV_MAP["head_front"]

    # Цвет кожи
    skin_tone = random.choice(list(SKIN_TONES.keys()))
    skin_palette = SKIN_TONES[skin_tone]

    # Заполнить лицо кожей
    for py in range(h):
        for px in range(w):
            color = jitter(random.choice(skin_palette), 10)
            pixels[u + px, v + py] = (*color, 255)

    # Глаза (пиксели 2-3 и 5-6 на строке 3)
    eye_white = (240, 240, 240)
    eye_pupil = (40, 80, 40)

    # Левый глаз
    pixels[u + 1, v + 3] = (*eye_white, 255)
    pixels[u + 2, v + 3] = (*eye_pupil, 255)

    # Правый глаз
    pixels[u + 5, v + 3] = (*eye_pupil, 255)
    pixels[u + 6, v + 3] = (*eye_white, 255)

    # Большой нос (характерная черта жителей!)
    nose_color = jitter(skin_palette[0], 20)
    nose_dark = (max(0, nose_color[0] - 30), max(0, nose_color[1] - 30), max(0, nose_color[2] - 30))

    # Нос 3x4 пикселя по центру
    for ny in range(4, 8):
        for nx in range(3, 6):
            pixels[u + nx, v + ny] = (*jitter(nose_color, 8), 255)

    # Тень носа
    pixels[u + 3, v + 7] = (*nose_dark, 255)
    pixels[u + 4, v + 7] = (*nose_dark, 255)
    pixels[u + 5, v + 7] = (*nose_dark, 255)

    # Рот
    mouth_color = (140, 80, 70)
    pixels[u + 3, v + 6] = (*mouth_color, 255)
    pixels[u + 4, v + 6] = (*mouth_color, 255)

    # Брови
    brow_color = (80, 60, 40)
    pixels[u + 1, v + 2] = (*brow_color, 255)
    pixels[u + 2, v + 2] = (*brow_color, 255)
    pixels[u + 5, v + 2] = (*brow_color, 255)
    pixels[u + 6, v + 2] = (*brow_color, 255)


def draw_head_details(img, profession):
    """Детали головы - шляпа/капюшон в зависимости от профессии."""
    pixels = img.load()
    prof_colors = PROFESSION_COLORS.get(profession, PROFESSION_COLORS["nitwit"])

    # Верх головы - шапка/капюшон
    fill_region(img, "head_top", prof_colors["hat"], noise=15)

    # Задняя часть - продолжение шапки
    fill_region(img, "head_back", prof_colors["hat"], noise=15)

    # Боковые стороны головы - шапка сверху, кожа снизу
    for side in ["head_right", "head_left"]:
        u, v, w, h = UV_MAP[side]
        for py in range(h):
            for px in range(w):
                if py < 3:  # верхняя часть - шапка
                    color = jitter(random.choice(prof_colors["hat"]), 12)
                else:
                    skin_tone = random.choice(list(SKIN_TONES.keys()))
                    color = jitter(random.choice(SKIN_TONES[skin_tone]), 10)
                pixels[u + px, v + py] = (*color, 255)


def draw_nose(img, skin_tone):
    """Нарисовать 3D-нос на его UV-регионе (24,0,2,4,2) — как в vanilla.

    Нос выпирает из головы отдельным кубиком 2x4x2. Без этого региона
    бокс носа в модели читает прозрачные пиксели и нос невидим.
    """
    pixels = img.load()
    base = SKIN_TONES[skin_tone][0]
    top = jitter(base, 12)
    dark = (max(0, base[0] - 35), max(0, base[1] - 35), max(0, base[2] - 35))

    # front (26,2,2,4)
    for py in range(4):
        for px in range(2):
            pixels[26 + px, 2 + py] = (*jitter(top, 8), 255)
    # right (24,2) и left (28,2) — темнее
    for py in range(4):
        pixels[24, 2 + py] = (*jitter(dark, 6), 255)
        pixels[28, 2 + py] = (*jitter(dark, 6), 255)
    # back (30,2) — самый тёмный
    for py in range(4):
        pixels[30, 2 + py] = (*jitter(dark, 6), 255)
    # top (26,0) / bottom (28,0)
    for px in range(2):
        pixels[26 + px, 0] = (*jitter(top, 10), 255)
        pixels[28 + px, 0] = (*jitter(dark, 8), 255)


def generate_villager_skin(profession, biome="plains", seed_val=None):
    """
    Создать текстуру жителя 64x64 для заданной профессии и биома.

    Args:
        profession: строка - название профессии
        biome: строка - биом (plains, desert, snowy, taiga, jungle, savanna, swamp)
        seed_val: для воспроизводимости

    Returns:
        PIL.Image 64x64 RGBA
    """
    if seed_val is not None:
        random.seed(seed_val)

    img = Image.new("RGBA", (SKIN_W, SKIN_H), (0, 0, 0, 0))

    prof_colors = PROFESSION_COLORS.get(profession, PROFESSION_COLORS["nitwit"])

    # === ГОЛОВА ===
    skin_tone = random.choice(list(SKIN_TONES.keys()))
    draw_face(img, profession)
    draw_head_details(img, profession)
    draw_nose(img, skin_tone)

    # Нижняя часть головы (подбородок)
    skin_tone = random.choice(list(SKIN_TONES.keys()))
    fill_region(img, "head_bot", SKIN_TONES[skin_tone], noise=10)

    # === ТЕЛО ===
    # Роб/туника
    fill_region(img, "body_front", prof_colors["robe"], noise=15, pattern="stripes")
    fill_region(img, "body_back", prof_colors["robe"], noise=15, pattern="stripes")
    fill_region(img, "body_right", prof_colors["robe"], noise=15)
    fill_region(img, "body_left", prof_colors["robe"], noise=15)
    fill_region(img, "body_top", prof_colors["accent"], noise=10)
    fill_region(img, "body_bot", prof_colors["robe"], noise=12)

    # Фартук/акцент на теле
    pixels = img.load()
    bu, bv, bw, bh = UV_MAP["body_front"]
    for py in range(bh):
        for px in range(bw):
            if py > 4 and 2 <= px <= 5:
                color = jitter(random.choice(prof_colors["accent"]), 10)
                pixels[bu + px, bv + py] = (*color, 255)

    # Пояс
    belt_y = 4
    for px in range(bw):
        color = jitter(random.choice(prof_colors["belt"]), 8)
        pixels[bu + px, bv + belt_y] = (*color, 255)

    # === РУКИ ===
    # Рукава роба
    for arm in ["rarm", "larm"]:
        fill_region(img, f"{arm}_front", prof_colors["robe"], noise=12)
        fill_region(img, f"{arm}_back", prof_colors["robe"], noise=12)
        fill_region(img, f"{arm}_right", prof_colors["robe"], noise=12)
        fill_region(img, f"{arm}_left", prof_colors["robe"], noise=12)
        fill_region(img, f"{arm}_top", prof_colors["robe"], noise=10)
        fill_region(img, f"{arm}_bot", SKIN_TONES[skin_tone], noise=10)  # кисть

    # Кисти рук - цвет кожи
    for arm in ["rarm", "larm"]:
        u, v, w, h = UV_MAP[f"{arm}_front"]
        for px in range(w):
            for py in range(h - 3, h):
                color = jitter(random.choice(SKIN_TONES[skin_tone]), 8)
                pixels[u + px, v + py] = (*color, 255)

    # === НОГИ ===
    # Штаны тёмного цвета
    pants_colors = [(60, 50, 40), (55, 45, 35), (65, 55, 45)]
    for leg in ["rleg", "lleg"]:
        fill_region(img, f"{leg}_front", pants_colors, noise=10)
        fill_region(img, f"{leg}_back", pants_colors, noise=10)
        fill_region(img, f"{leg}_right", pants_colors, noise=10)
        fill_region(img, f"{leg}_left", pants_colors, noise=10)
        fill_region(img, f"{leg}_top", pants_colors, noise=8)
        fill_region(img, f"{leg}_bot", [(50, 40, 30), (45, 35, 25)], noise=8)  # обувь

    # === БИОМ-ОВЕРЛЕЙ ===
    overlay = BIOME_OVERLAYS.get(biome)
    if overlay:
        tint = overlay["tint"]
        alpha = overlay["alpha"]
        for key in ["body_front", "body_back", "body_right", "body_left",
                     "rarm_front", "rarm_back", "larm_front", "larm_back",
                     "head_top", "head_back"]:
            u, v, w, h = UV_MAP[key]
            for py in range(h):
                for px in range(w):
                    existing = pixels[u + px, v + py]
                    if existing[3] > 0:
                        base = existing[:3]
                        blended = blend(base, tint, alpha)
                        pixels[u + px, v + py] = (*blended, existing[3])

    return img


def generate_atlas():
    """
    Создать полный атлас текстур жителей.

    Атлас содержит все профессии в строку, каждая текстура 64x64.
    Итоговый размер: (64 * N) x 64, где N = количество профессий.
    """
    professions = list(PROFESSION_COLORS.keys())
    biomes = ["plains", "desert", "snowy", "taiga", "savanna"]

    # === Атлас 1: Все профессии (plains биом) ===
    atlas_w = SKIN_W * len(professions)
    atlas_h = SKIN_H
    atlas = Image.new("RGBA", (atlas_w, atlas_h), (0, 0, 0, 0))

    print(f"Генерация атласа: {len(professions)} профессий")

    for i, prof in enumerate(professions):
        skin = generate_villager_skin(prof, "plains", seed_val=100 + i)
        atlas.paste(skin, (i * SKIN_W, 0))

        # Также сохранить отдельно
        out_dir = get_output_dir()
        skin.save(os.path.join(out_dir, f"villager_{prof}.png"))
        print(f"  ✓ {prof}")

    # Сохранить атлас
    out_dir = get_output_dir()
    atlas_path = os.path.join(out_dir, "villager_atlas.png")
    atlas.save(atlas_path)
    print(f"\n✅ Атлас сохранён: {atlas_path}")

    # === Атлас 2: Биом-вариации (farmer во всех биомах) ===
    biome_atlas_w = SKIN_W * len(biomes)
    biome_atlas_h = SKIN_H
    biome_atlas = Image.new("RGBA", (biome_atlas_w, biome_atlas_h), (0, 0, 0, 0))

    for i, biome in enumerate(biomes):
        skin = generate_villager_skin("farmer", biome, seed_val=200 + i)
        biome_atlas.paste(skin, (i * SKIN_W, 0))
        skin.save(os.path.join(out_dir, f"villager_farmer_{biome}.png"))

    biome_atlas_path = os.path.join(out_dir, "villager_biomes_atlas.png")
    biome_atlas.save(biome_atlas_path)
    print(f"✅ Биом-атлас сохранён: {biome_atlas_path}")

    # === Предпросмотр (увеличенный 4x) ===
    preview_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)))
    preview = atlas.resize((atlas_w * 4, atlas_h * 4), Image.NEAREST)
    preview.save(os.path.join(preview_dir, "atlas_preview.png"))
    print(f"✅ Превью: {preview_dir}/atlas_preview.png")

    # === Манифест (индекс профессий для Java кода) ===
    manifest_path = os.path.join(out_dir, "villager_atlas.json")
    import json
    manifest = {
        "version": 1,
        "skin_width": SKIN_W,
        "skin_height": SKIN_H,
        "professions": {},
    }
    for i, prof in enumerate(professions):
        manifest["professions"][prof] = {
            "atlas_index": i,
            "u": i * SKIN_W,
            "v": 0,
            "width": SKIN_W,
            "height": SKIN_H,
        }

    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
    print(f"✅ Манифест: {manifest_path}")

    # === Статистика ===
    total_files = len(professions) + 2  # атлас + биом-атлас + json + отдельные файлы
    print(f"\n📊 Всего создано файлов: {total_files + len(biomes) + len(professions)}")
    print(f"   - {len(professions)} отдельных текстур профессий")
    print(f"   - {len(biomes)} текстур биома farmer")
    print(f"   - 1 основной атлас")
    print(f"   - 1 биом-атлас")
    print(f"   - 1 JSON манифест")
    print(f"   - 1 предпросмотр")

    return atlas_path


def get_output_dir():
    """Путь для сохранения текстур."""
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = os.path.join(project_root, "src", "main", "resources", "textures", "entities")
    os.makedirs(out_dir, exist_ok=True)
    return out_dir


def main():
    print("=" * 60)
    print("  ГЕНЕРАТОР ТЕКСТУР ЖИТЕЛЕЙ ДЕРЕВЕНЬ")
    print("  Villager Texture Atlas Generator")
    print("=" * 60)
    print()

    # Проверить зависимости
    try:
        from PIL import Image
    except ImportError:
        print("❌ ОШИБКА: Pillow не установлен!")
        print("   Установите: pip install Pillow")
        sys.exit(1)

    atlas_path = generate_atlas()

    print()
    print("=" * 60)
    print("  ГОТОВО! Все текстуры созданы.")
    print("  Запустите игру чтобы увидеть жителей!")
    print("=" * 60)


if __name__ == "__main__":
    main()
