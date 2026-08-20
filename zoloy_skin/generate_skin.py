# pip install pillow
import random
from PIL import Image

random.seed(117)  # фиксированный сид — скин воспроизводим

img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
px = img.load()

# ── Палитра Золоя ──────────────────────────────────────────
ASH_GRAY   = [(118,118,122), (104,104,108), (92,92,96), (132,132,136), (80,80,84)]
DARK_ASH   = (58, 58, 62)
EMBER      = (255, 110, 15)    # тлеющие трещины
EMBER_HOT  = (255, 190, 70)    # раскалённые точки
CLOAK_DARK = (38, 34, 40)      # рваный плащ

# ── Раскладка скина 1.8 (x, y, w, h) ───────────────────────
R = {
    "head_top": (8,0,8,8),   "head_bot": (16,0,8,8),
    "head_r":   (0,8,8,8),   "head_f":   (8,8,8,8),
    "head_l":   (16,8,8,8),  "head_b":   (24,8,8,8),

    "body_top": (20,16,8,4), "body_bot": (28,16,8,4),
    "body_r":   (16,20,4,12),"body_f":   (20,20,8,12),
    "body_l":   (28,20,4,12),"body_b":   (32,20,8,12),

    "rarm_top": (44,16,4,4), "rarm_bot": (48,16,4,4),
    "rarm_r":   (40,20,4,12),"rarm_f":   (44,20,4,12),
    "rarm_l":   (48,20,4,12),"rarm_b":   (52,20,4,12),

    "larm_top": (36,48,4,4), "larm_bot": (40,48,4,4),
    "larm_r":   (32,52,4,12),"larm_f":   (36,52,4,12),
    "larm_l":   (40,52,4,12),"larm_b":   (44,52,4,12),

    "rleg_top": (4,16,4,4),  "rleg_bot": (8,16,4,4),
    "rleg_r":   (0,20,4,12), "rleg_f":   (4,20,4,12),
    "rleg_l":   (8,20,4,12), "rleg_b":   (12,20,4,12),

    "lleg_top": (20,48,4,4), "lleg_bot": (24,48,4,4),
    "lleg_r":   (16,52,4,12),"lleg_f":   (20,52,4,12),
    "lleg_l":   (24,52,4,12),"lleg_b":   (28,52,4,12),

    # второй слой (оверлей) — плащ на спине
    "cloak_r":  (16,32,4,12),"cloak_f":  (20,32,8,12),
    "cloak_l":  (28,32,4,12),"cloak_b":  (32,32,8,12),
}

def fill(name, palette, noise=0.35):
    x, y, w, h = R[name]
    for j in range(y, y+h):
        for i in range(x, x+w):
            c = random.choice(palette)
            if random.random() < noise:
                c = tuple(max(0, v - random.randint(10, 30)) for v in c)
            px[i, j] = (*c, 255)

def cracks(name, count=3):
    """Случайные светящиеся трещинки внутри региона."""
    x, y, w, h = R[name]
    for _ in range(count):
        cx, cy = random.randint(x, x+w-1), random.randint(y, y+h-1)
        for _ in range(random.randint(2, 5)):
            px[min(max(cx, x), x+w-1), min(max(cy, y), y+h-1)] = (
                *random.choice([EMBER, EMBER_HOT]), 255)
            cx += random.choice([-1, 0, 1]); cy += random.choice([0, 1])

# ── Тело: пепельная кожа + трещины ─────────────────────────
for part in ["head_top","head_bot","head_r","head_f","head_l","head_b",
             "body_top","body_bot","body_r","body_f","body_l","body_b",
             "rarm_top","rarm_r","rarm_f","rarm_l","rarm_b",
             "larm_top","larm_r","larm_f","larm_l","larm_b",
             "rleg_top","rleg_r","rleg_f","rleg_l","rleg_b",
             "lleg_top","lleg_r","lleg_f","lleg_l","lleg_b"]:
    fill(part, ASH_GRAY)
    cracks(part, count=random.randint(1, 3))

# ── Лицо: светящиеся глаза 2×2 и тёмный рот ────────────────
for ex in (9, 10, 13, 14):
    for ey in (11, 12):
        px[ex, ey] = (*EMBER_HOT, 255)
px[11, 14] = (*DARK_ASH, 255); px[12, 14] = (*DARK_ASH, 255)

# ── Рваный плащ (оверлей) ──────────────────────────────────
for name in ["cloak_r", "cloak_f", "cloak_l", "cloak_b"]:
    x, y, w, h = R[name]
    for j in range(y, y+h):
        for i in range(x, x+w):
            # низ плаща рваный: чем ниже, тем больше прозрачных пикселей
            tear = (j - y) / h
            if random.random() < tear * 0.6:
                continue  # прозрачная дыра
            c = CLOAK_DARK
            if random.random() < 0.25:
                c = tuple(max(0, v - 15) for v in c)
            px[i, j] = (*c, 255)

# ── Сохранение ─────────────────────────────────────────────
img.save("zoloy_skin.png")
img.resize((512, 512), Image.NEAREST).save("zoloy_preview.png")
print("Готово: zoloy_skin.png + превью zoloy_preview.png")
