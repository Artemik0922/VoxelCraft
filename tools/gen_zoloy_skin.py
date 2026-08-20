import random
from PIL import Image

random.seed(117)
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
px = img.load()

ASH = [(118, 118, 122), (104, 104, 108), (92, 92, 96), (132, 132, 136), (80, 80, 84)]
DARK = (58, 58, 62)
EMBER = (255, 110, 15)
HOT = (255, 190, 70)
CLOAK = (38, 34, 40)

R = {
    "head_top": (8, 0, 8, 8), "head_bot": (16, 0, 8, 8), "head_r": (0, 8, 8, 8), "head_f": (8, 8, 8, 8),
    "head_l": (16, 8, 8, 8), "head_b": (24, 8, 8, 8),
    "body_top": (20, 16, 8, 4), "body_bot": (28, 16, 8, 4), "body_r": (16, 20, 4, 12), "body_f": (20, 20, 8, 12),
    "body_l": (28, 20, 4, 12), "body_b": (32, 20, 8, 12),
    "rarm_top": (44, 16, 4, 4), "rarm_bot": (48, 16, 4, 4), "rarm_r": (40, 20, 4, 12), "rarm_f": (44, 20, 4, 12),
    "rarm_l": (48, 20, 4, 12), "rarm_b": (52, 20, 4, 12),
    "larm_top": (36, 48, 4, 4), "larm_bot": (40, 48, 4, 4), "larm_r": (32, 52, 4, 12), "larm_f": (36, 52, 4, 12),
    "larm_l": (40, 52, 4, 12), "larm_b": (44, 52, 4, 12),
    "rleg_top": (4, 16, 4, 4), "rleg_bot": (8, 16, 4, 4), "rleg_r": (0, 20, 4, 12), "rleg_f": (4, 20, 4, 12),
    "rleg_l": (8, 20, 4, 12), "rleg_b": (12, 20, 4, 12),
    "lleg_top": (20, 48, 4, 4), "lleg_bot": (24, 48, 4, 4), "lleg_r": (16, 52, 4, 12), "lleg_f": (20, 52, 4, 12),
    "lleg_l": (24, 52, 4, 12), "lleg_b": (28, 52, 4, 12),
    "cloak_r": (16, 32, 4, 12), "cloak_f": (20, 32, 8, 12), "cloak_l": (28, 32, 4, 12), "cloak_b": (32, 32, 8, 12),
}


def fill(n, pal, noise=0.35):
    x, y, w, h = R[n]
    for j in range(y, y + h):
        for i in range(x, x + w):
            c = random.choice(pal)
            if random.random() < noise:
                c = tuple(max(0, v - random.randint(10, 30)) for v in c)
            px[i, j] = (*c, 255)


def cracks(n, cnt=3):
    x, y, w, h = R[n]
    for _ in range(cnt):
        cx, cy = random.randint(x, x + w - 1), random.randint(y, y + h - 1)
        for _ in range(random.randint(2, 5)):
            px[min(max(cx, x), x + w - 1), min(max(cy, y), y + h - 1)] = (*random.choice([EMBER, HOT]), 255)
            cx += random.choice([-1, 0, 1])
            cy += random.choice([0, 1])


for p in list(R):
    if p.startswith("cloak"):
        continue
    fill(p, ASH)
    cracks(p, random.randint(1, 3))

for ex in (9, 10, 13, 14):
    for ey in (11, 12):
        px[ex, ey] = (*HOT, 255)
px[11, 14] = (*DARK, 255)
px[12, 14] = (*DARK, 255)

for n in ["cloak_r", "cloak_f", "cloak_l", "cloak_b"]:
    x, y, w, h = R[n]
    for j in range(y, y + h):
        for i in range(x, x + w):
            if random.random() < (j - y) / h * 0.6:
                continue
            c = CLOAK
            if random.random() < 0.25:
                c = tuple(max(0, v - 15) for v in c)
            px[i, j] = (*c, 255)

import os
out_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "src", "main", "resources", "textures", "entities")
os.makedirs(out_dir, exist_ok=True)
img.save(os.path.join(out_dir, "zoloy.png"))
preview_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools")
img.resize((512, 512), Image.NEAREST).save(os.path.join(preview_dir, "zoloy_preview.png"))
print(f"Done: {out_dir}/zoloy.png + {preview_dir}/zoloy_preview.png")
