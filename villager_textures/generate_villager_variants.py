#!/usr/bin/env python3
"""
Генератор текстур маленьких жителей (Baby Villagers) и состояний.

Дополнительные текстуры:
    - Baby villager (голова больше, тело меньше)
    - Zombie villager (заражённый)
    - Villager sleeping (спящий)

Использование:
    python generate_villager_variants.py
"""

import os
import sys
import random
from PIL import Image

random.seed(123)

SKIN_W = 64
SKIN_H = 64

# UV-маппинг для baby жителя (голова больше 8x8x8 → 10x10x10)
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


def jitter(color, amount=15):
    r = max(0, min(255, color[0] + random.randint(-amount, amount)))
    g = max(0, min(255, color[1] + random.randint(-amount, amount)))
    b = max(0, min(255, color[2] + random.randint(-amount, amount)))
    return (r, g, b)


def fill_region(img, region_map, region_name, palette, noise=15):
    pixels = img.load()
    if region_name not in region_map:
        return
    u, v, w, h = region_map[region_name]
    for py in range(h):
        for px in range(w):
            color = jitter(random.choice(palette), noise)
            pixels[u + px, v + py] = (*color, 255)


def generate_baby_villager(seed_val=42):
    """Создать текстуру маленького жителя."""
    random.seed(seed_val)
    img = Image.new("RGBA", (SKIN_W, SKIN_H), (0, 0, 0, 0))
    pixels = img.load()

    # Детская кожа
    baby_skin = [(220, 185, 155), (225, 190, 160), (215, 180, 150)]

    # Детская одежда - простая зелёная роба
    baby_robe = [(100, 150, 80), (90, 140, 72), (110, 160, 88)]
    baby_pants = [(80, 70, 55), (75, 65, 50)]

    # Большая голова
    for key in BABY_UV:
        if key.startswith("head"):
            fill_region(img, BABY_UV, key, baby_skin, 8)
        elif key.startswith("body") or key.startswith("rarm") or key.startswith("larm"):
            fill_region(img, BABY_UV, key, baby_robe, 12)
        elif key.startswith("rleg") or key.startswith("lleg"):
            fill_region(img, BABY_UV, key, baby_pants, 10)

    # Лицо - большие глаза, маленький нос
    u, v, w, h = BABY_UV["head_front"]

    # Заполняем лицо
    for py in range(h):
        for px in range(w):
            color = jitter(random.choice(baby_skin), 6)
            pixels[u + px, v + py] = (*color, 255)

    # Большие глаза (характерно для baby)
    eye_w = (245, 245, 245)
    eye_p = (30, 70, 30)

    # Левый глаз 2x2
    pixels[u + 2, v + 3] = (*eye_w, 255)
    pixels[u + 3, v + 3] = (*eye_p, 255)
    pixels[u + 2, v + 4] = (*eye_w, 255)
    pixels[u + 3, v + 4] = (*eye_p, 255)

    # Правый глаз 2x2
    pixels[u + 6, v + 3] = (*eye_p, 255)
    pixels[u + 7, v + 3] = (*eye_w, 255)
    pixels[u + 6, v + 4] = (*eye_p, 255)
    pixels[u + 7, v + 4] = (*eye_w, 255)

    # Маленький носик
    nose = jitter(baby_skin[0], 15)
    pixels[u + 4, v + 5] = (*nose, 255)
    pixels[u + 5, v + 5] = (*nose, 255)

    # Маленький рот
    pixels[u + 4, v + 6] = (150, 90, 80, 255)
    pixels[u + 5, v + 6] = (150, 90, 80, 255)

    return img


def generate_zombie_villager(seed_val=77):
    """Создать текстуру зомби-жителя (заражённый)."""
    random.seed(seed_val)
    img = Image.new("RGBA", (SKIN_W, SKIN_H), (0, 0, 0, 0))
    pixels = img.load()

    # Зелёная гниющая кожа
    zombie_skin = [(60, 100, 55), (55, 90, 50), (65, 110, 60), (50, 85, 45)]
    zombie_robe = [(50, 50, 55), (45, 45, 50), (55, 55, 60)]

    from generate_villager_atlas import UV_MAP

    # Заполнить все части тела
    for key in UV_MAP:
        if key.startswith("head"):
            fill_region(img, UV_MAP, key, zombie_skin, 20)
        elif key.startswith("body") or key.startswith("rarm") or key.startswith("larm"):
            fill_region(img, UV_MAP, key, zombie_robe, 18)
        elif key.startswith("rleg") or key.startswith("lleg"):
            fill_region(img, UV_MAP, key, zombie_robe, 15)

    # Лицо - красные светящиеся глаза
    u, v, w, h = UV_MAP["head_front"]

    for py in range(h):
        for px in range(w):
            color = jitter(random.choice(zombie_skin), 12)
            pixels[u + px, v + py] = (*color, 255)

    # Красные глаза
    red_eye = (200, 20, 15)
    pixels[u + 1, v + 3] = (*red_eye, 255)
    pixels[u + 2, v + 3] = (*red_eye, 255)
    pixels[u + 5, v + 3] = (*red_eye, 255)
    pixels[u + 6, v + 3] = (*red_eye, 255)

    # Большой нос
    pixels[u + 3, v + 4] = (*jitter(zombie_skin[0], 25), 255)
    pixels[u + 4, v + 4] = (*jitter(zombie_skin[0], 25), 255)
    pixels[u + 3, v + 5] = (*jitter(zombie_skin[0], 25), 255)
    pixels[u + 4, v + 5] = (*jitter(zombie_skin[0], 25), 255)
    pixels[u + 5, v + 5] = (*jitter(zombie_skin[0], 25), 255)

    # Кровавые пятна
    blood = (120, 15, 10)
    for _ in range(8):
        bx = random.randint(0, w - 1)
        by = random.randint(0, h - 1)
        pixels[u + bx, v + by] = (*blood, 255)

    return img


def generate_villager_variants():
    """Создать все варианты текстур жителей."""

    out_dir = get_output_dir()

    # Baby villager
    baby = generate_baby_villager(42)
    baby_path = os.path.join(out_dir, "villager_baby.png")
    baby.save(baby_path)
    print(f"✅ Baby villager: {baby_path}")

    # Zombie villager
    zombie = generate_zombie_villager(77)
    zombie_path = os.path.join(out_dir, "villager_zombie.png")
    zombie.save(zombie_path)
    print(f"✅ Zombie villager: {zombie_path}")

    # Предпросмотр
    preview_dir = os.path.dirname(os.path.abspath(__file__))

    # Составное превью
    preview = Image.new("RGBA", (SKIN_W * 3 * 4, SKIN_H * 4), (40, 40, 40, 255))
    baby_big = baby.resize((SKIN_W * 4, SKIN_H * 4), Image.NEAREST)
    zombie_big = zombie.resize((SKIN_W * 4, SKIN_H * 4), Image.NEAREST)

    preview.paste(baby_big, (0, 0))
    preview.paste(zombie_big, (SKIN_W * 4, 0))

    preview_path = os.path.join(preview_dir, "variants_preview.png")
    preview.save(preview_path)
    print(f"✅ Превью вариантов: {preview_path}")

    # Атлас вариантов
    variants_atlas = Image.new("RGBA", (SKIN_W * 2, SKIN_H), (0, 0, 0, 0))
    variants_atlas.paste(baby, (0, 0))
    variants_atlas.paste(zombie, (SKIN_W, 0))

    atlas_path = os.path.join(out_dir, "villager_variants_atlas.png")
    variants_atlas.save(atlas_path)
    print(f"✅ Атлас вариантов: {atlas_path}")

    return [baby_path, zombie_path, atlas_path]


def get_output_dir():
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = os.path.join(project_root, "src", "main", "resources", "textures", "entities")
    os.makedirs(out_dir, exist_ok=True)
    return out_dir


def main():
    print("=" * 50)
    print("  ВАРИАНТЫ ТЕКСТУР ЖИТЕЛЕЙ")
    print("  Villager Variant Textures")
    print("=" * 50)
    print()

    try:
        from PIL import Image
    except ImportError:
        print("❌ Pillow не установлен! pip install Pillow")
        sys.exit(1)

    paths = generate_villager_variants()

    print()
    print("=" * 50)
    print(f"  Создано файлов: {len(paths)}")
    print("=" * 50)


if __name__ == "__main__":
    main()
