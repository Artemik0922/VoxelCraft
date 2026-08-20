#!/usr/bin/env python3
"""Проверка текстур жителей (numpy-генератор): регионы заполнены,
размеры атласа, обе строки вариантов, глаза и нос на месте."""
import os, sys
from PIL import Image

TILE = 1024
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources", "textures", "entities")

UV = {
    "head_top": (8,0,8,8), "head_bot": (16,0,8,8), "head_right": (0,8,8,8),
    "head_front": (8,8,8,8), "head_left": (16,8,8,8), "head_back": (24,8,8,8),
    "body_top": (20,16,8,4), "body_bot": (28,16,8,4), "body_right": (16,20,4,12),
    "body_front": (20,20,8,12), "body_left": (28,20,4,12), "body_back": (32,20,8,12),
    "rarm_front": (44,20,4,12), "rarm_bot": (48,16,4,4),
    "larm_front": (36,52,4,12), "larm_bot": (40,48,4,4),
    "rleg_front": (4,20,4,12), "lleg_front": (20,52,4,12),
    "nose_front": (26,2,2,4), "nose_top": (26,0,2,2), "nose_bot": (28,0,2,2),
    "nose_right": (24,2,2,4), "nose_left": (28,2,2,4), "nose_back": (30,2,2,4),
}

PROFS = ["farmer","librarian","blacksmith","butcher","priest","fisherman",
         "fletcher","leatherworker","shepherd","toolsmith","armorer",
         "weaponsmith","cartographer","cleric","mason","nitwit","warrior"]

def stats(img, key):
    x, y, w, h = (v * 16 for v in UV[key])
    xs, ys, xe, ye = x, y, x+w, y+h
    px = img.load()
    n = opaque = 0
    rsum = gsum = bsum = 0.0
    for yy in range(ys, ye):
        for xx in range(xs, xe):
            r, g, b, a = px[xx, yy]
            n += 1
            if a > 200:
                opaque += 1
                rsum += r; gsum += g; bsum += b
    if opaque == 0:
        return None
    return (opaque / n, rsum/opaque, gsum/opaque, bsum/opaque)

def check_face(img):
    x, y = 8*16, 8*16
    px = img.load()
    found = 0
    for ex in (1, 5):
        hit = False
        for yy in range(y+2*16+10, y+3*16+22):
            for xx in range(x+ex*16+10, x+(ex+2)*16-10):
                r, g, b, a = px[xx, yy]
                if a > 200 and r > 225 and g > 225 and b > 220:
                    hit = True
                    break
            if hit:
                break
        if hit:
            found += 1
    return found >= 2

fails = []
for prof in PROFS:
    for variant in (0, 1):
        img = Image.open(os.path.join(OUT, f"villager_{prof}_{variant}.png"))
        if img.size != (TILE, TILE):
            fails.append(f"{prof}#{variant}: размер {img.size}")
            continue
        res = {k: stats(img, k) for k in UV}
        empty = [k for k, v in res.items() if v is None]
        if empty:
            fails.append(f"{prof}#{variant}: пустые регионы {empty}")
        if res["head_front"] is None or res["body_front"] is None:
            fails.append(f"{prof}#{variant}: нет лица/тела")
        if res["larm_front"] is None or res["lleg_front"] is None:
            fails.append(f"{prof}#{variant}: нет левой руки/ноги")
        if res["nose_front"] is None or res["nose_top"] is None:
            fails.append(f"{prof}#{variant}: нет бокса носа")
        if not check_face(img):
            fails.append(f"{prof}#{variant}: не найдены белки глаз")
        if variant == 0:
            print(f"{prof:15s} лицо={res['head_front'][0]:.0%} "
                  f"тело={res['body_front'][0]:.0%} нос={res['nose_front'][0]:.0%} "
                  f"L-рука={res['larm_front'][0]:.0%} L-нога={res['lleg_front'][0]:.0%}")

atlas = Image.open(os.path.join(OUT, "villager_atlas.png"))
print(f"\natlas: {atlas.size[0]}x{atlas.size[1]} (ожидается 9216x4096)")
if atlas.size != (9216, 4096):
    fails.append(f"атлас {atlas.size}")
COLS, ROWS = 9, 2
for i, prof in enumerate(PROFS):
    col, row = i % COLS, i // COLS
    for v in (0, 1):
        tile = atlas.crop((col*TILE, (row + v*ROWS)*TILE,
                           (col+1)*TILE, (row + v*ROWS + 1)*TILE))
        if tile.getbbox() is None:
            fails.append(f"атлас: тайл {prof} вариант {v} пуст")
# Меч воина: регион (36,32,4,16) в скине warrior должен быть заполнен
sw = Image.open(os.path.join(OUT, "villager_warrior_0.png"))
swpx = sw.load()
REGION = (48 - 32) * 16 * (40 - 36) * 16
sw_filled = sum(1 for y in range(32*16, 48*16) for x in range(36*16, 40*16)
                if swpx[x, y][3] > 200)
if sw_filled < REGION * 0.5:
    fails.append(f"warrior: регион меча почти пуст ({sw_filled})")
elif sw_filled == REGION:
    fails.append("warrior: регион меча полностью закрашен (нет формы)")

print()
if fails:
    print("ОШИБКИ:")
    for f in fails:
        print(" -", f)
    sys.exit(1)
print("OK: все проверки пройдены")