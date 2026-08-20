#!/usr/bin/env python3
"""
Генератор скинов жителей (numpy, стиль «крупные плоскости + шум ткани»).

Основа — авторский концепт-скрипт (сид 7 = «те самые скины»):
  6 концепт-профессий (farmer, librarian, blacksmith, butcher, priest,
  shepherd) рисуются тем же кодом и в том же порядке вызовов rng, поэтому
  вариант #0 совпадает с концептом попиксельно.

Интеграция с игрой (добавляется после концепт-арта, не влияя на rng):
  - UV-раскладка дополнена левыми рукой/ногой (зеркальная копия правых)
    и боксом носа (отдельный кубик в модели);
  - 17 профессий в порядке enum Villager.Profession (последняя — warrior);
  - атлас 9216x4096: 9 тайлов 1024x1024 в строке, 2 строки профессий x
    2 строки вариантов (17*1024 = 17408 > 16384, поэтому сетка 9x4 —
    ширина не превышает GL_MAX_TEXTURE_SIZE = 16384);
  - воин: шлем-helm, металлическая отделка и меч в руке (регион 36,32,4,16);
  - manifest villager_atlas.json (u/v для каждой профессии и варианта).

Использование:
    python generate_villager_numpy.py
"""

import os
import sys

import numpy as np
from PIL import Image, ImageDraw

S = 16                     # HD: 1 пиксель скина = S px (S=16 -> 1024x1024)
G = 64
W = H = G * S
VARIANTS = 2               # вариантов на профессию

rng = np.random.default_rng(7)   # фиксированный сид = те самые скины

# Классическая раскладка скина (x,y,w,h в grid-пикселях) + левые
# конечности и нос-кубик, которые использует VillagerModel.
L = dict(
    head_top=(8, 0, 8, 8),     head_bottom=(16, 0, 8, 8),
    head_right=(0, 8, 8, 8), head_front=(8, 8, 8, 8), head_left=(16, 8, 8, 8),
    head_back=(24, 8, 8, 8),
    nose_top=(26, 0, 2, 2),    nose_bottom=(28, 0, 2, 2),
    nose_right=(24, 2, 2, 4), nose_front=(26, 2, 2, 4),
    nose_left=(28, 2, 2, 4),   nose_back=(30, 2, 2, 4),
    body_top=(20, 16, 8, 4),   body_bottom=(28, 16, 8, 4),
    body_right=(16, 20, 4, 12), body_front=(20, 20, 8, 12),
    body_left=(28, 20, 4, 12),  body_back=(32, 20, 8, 12),
    armR_top=(44, 16, 4, 4),   armR_bottom=(48, 16, 4, 4),
    armR_right=(40, 20, 4, 12), armR_front=(44, 20, 4, 12),
    armR_left=(48, 20, 4, 12),  armR_back=(52, 20, 4, 12),
    armL_top=(36, 48, 4, 4),   armL_bottom=(40, 48, 4, 4),
    armL_right=(32, 52, 4, 12), armL_front=(36, 52, 4, 12),
    armL_left=(40, 52, 4, 12),  armL_back=(44, 52, 4, 12),
    legR_top=(4, 16, 4, 4),    legR_bottom=(8, 16, 4, 4),
    legR_right=(0, 20, 4, 12), legR_front=(4, 20, 4, 12),
    legR_left=(8, 20, 4, 12),  legR_back=(12, 20, 4, 12),
    legL_top=(20, 48, 4, 4),   legL_bottom=(24, 48, 4, 4),
    legL_right=(16, 52, 4, 12), legL_front=(20, 52, 4, 12),
    legL_left=(24, 52, 4, 12),  legL_back=(28, 52, 4, 12),
)

# Концепт-профессии: палитры сняты с концепта (рисуются байт-в-байт).
CONCEPT = {
    'farmer':        dict(skin=(214, 178, 142), robe=(110, 125, 55), legs='pants', pants=(120, 100, 70),
                          apron=('long', (105, 70, 45)), hat='straw', beard=(120, 80, 40)),
    'librarian':     dict(skin=(232, 204, 170), robe=(232, 226, 205), legs='robe',
                          hat='hood', hood=(232, 226, 205), glasses=True, book=True, beard=(150, 100, 50)),
    'blacksmith':    dict(skin=(168, 122, 88), robe=(150, 148, 145), legs='pants', pants=(110, 100, 75),
                          apron=('buckles', (90, 58, 38)), hat='bald', soot=True, mottle=True,
                          gloves=True, beard=(45, 40, 35)),
    'butcher':       dict(skin=(232, 204, 170), robe=(200, 40, 40), legs='robe',
                          apron=('tie', (240, 240, 238)), hat='bald', beard=(150, 100, 60)),
    'priest':        dict(skin=(214, 178, 142), robe=(125, 60, 190), legs='robe',
                          hat='hood', hood=(125, 60, 190), trim=(215, 180, 60), beard=(120, 80, 45)),
    'shepherd':      dict(skin=(196, 150, 110), robe=(235, 226, 196), legs='robe',
                          hat='hat', beard=(110, 70, 40)),
}

# Остальные профессии (в том же стиле, свои цвета).
EXTRA = {
    'fisherman':     dict(skin=(196, 150, 110), robe=(90, 112, 150), legs='pants', pants=(72, 78, 84),
                          hat='hood', hood=(90, 112, 150), beard=(110, 80, 50)),
    'fletcher':      dict(skin=(196, 150, 110), robe=(100, 130, 80), legs='pants', pants=(88, 72, 50),
                          apron=('long', (150, 118, 70)), hat='hair', hair=(70, 50, 30), beard=(90, 60, 35)),
    'leatherworker': dict(skin=(168, 122, 88), robe=(138, 96, 56), legs='pants', pants=(96, 70, 46),
                          apron=('long', (112, 76, 42)), hat='hair', hair=(45, 40, 35), beard=(80, 55, 35)),
    'toolsmith':     dict(skin=(168, 122, 88), robe=(86, 86, 92), legs='pants', pants=(72, 72, 76),
                          apron=('buckles', (116, 106, 90)), hat='hair', hair=(45, 40, 35),
                          soot=True, mottle=True, gloves=True, beard=(60, 50, 45)),
    'armorer':       dict(skin=(168, 122, 88), robe=(96, 101, 110), legs='pants', pants=(78, 82, 90),
                          apron=('buckles', (132, 138, 148)), hat='bald', soot=True, mottle=True,
                          gloves=True, beard=(60, 50, 45)),
    'weaponsmith':   dict(skin=(196, 150, 110), robe=(70, 75, 82), legs='pants', pants=(60, 64, 70),
                          apron=('buckles', (102, 96, 86)), hat='bald', soot=True, mottle=True,
                          gloves=True, beard=(60, 50, 45)),
    'cartographer':  dict(skin=(214, 178, 142), robe=(152, 126, 96), legs='pants', pants=(112, 92, 72),
                          hat='hat', trim=(230, 200, 120), book=True, beard=(120, 90, 60)),
    'cleric':        dict(skin=(214, 178, 142), robe=(218, 208, 192), legs='pants', pants=(152, 122, 92),
                          hat='bald', trim=(230, 200, 90), beard=(120, 90, 60)),
    'mason':         dict(skin=(196, 150, 110), robe=(132, 127, 122), legs='pants', pants=(106, 101, 96),
                          apron=('long', (116, 111, 106)), hat='bald', beard=(90, 70, 50)),
    'nitwit':        dict(skin=(214, 178, 142), robe=(90, 120, 70), legs='pants', pants=(80, 90, 60),
                          hat='hair', hair=(120, 100, 60), beard=(110, 80, 50)),
    'warrior':       dict(skin=(196, 150, 110), robe=(96, 102, 112), legs='pants', pants=(62, 66, 74),
                          hat='helm', gloves=True, trim=(205, 210, 220), beard=(45, 40, 35)),
}

PROFESSIONS = {**CONCEPT, **EXTRA}

# Порядок = порядку enum Villager.Profession в игре (важно для атласа!)
PROF_ORDER = ['farmer', 'librarian', 'blacksmith', 'butcher', 'priest',
              'fisherman', 'fletcher', 'leatherworker', 'shepherd',
              'toolsmith', 'armorer', 'weaponsmith', 'cartographer',
              'cleric', 'mason', 'nitwit', 'warrior']

# Концепт-профессии рисуются первыми в исходном порядке авторского скрипта,
# чтобы rng-последовательность совпала и скины были «те самые».
GEN_ORDER = [p for p in ['farmer', 'librarian', 'blacksmith', 'butcher',
                         'priest', 'shepherd']] + \
            [p for p in PROF_ORDER if p not in CONCEPT]

img = None


def stretch(small, fy, fx, h, w):
    return np.repeat(np.repeat(small, fy, axis=0), fx, axis=1)[:h, :w]


def fabric(h, w, color, rough=0.16):
    a = stretch(rng.random((h // 3 + 1, w // 3 + 1)), 3, 3, h, w)
    t = 0.7 * a + 0.3 * rng.random((h, w))
    t = (t - t.min()) / (t.max() - t.min() + 1e-9)
    return np.array(color, float)[None, None, :] * (0.85 + rough * 2 * t[..., None])


def paste_arr(gx, gy, f):
    h, w, _ = f.shape
    img[gy * S:gy * S + h, gx * S:gx * S + w, :3] = f
    img[gy * S:gy * S + h, gx * S:gx * S + w, 3] = 255


def fill(key, color, rough=0.14):
    gx, gy, gw, gh = L[key]
    paste_arr(gx, gy, fabric(gh * S, gw * S, color, rough))


def px(x, y, color, f=1.0):
    c = np.clip(np.array(color, float) * f, 0, 255)
    img[y * S:(y + 1) * S, x * S:(x + 1) * S, :3] = c
    img[y * S:(y + 1) * S, x * S:(x + 1) * S, 3] = 255


def shade(x0, y0, x1, y1, f):
    img[y0 * S:y1 * S, x0 * S:x1 * S, :3] *= f


def speckle(x0, y0, x1, y1, color, density):
    for y in range(y0, y1):
        for x in range(x0, x1):
            if rng.random() < density:
                px(x, y, color, rng.uniform(0.7, 1.15))


def mottle(key):  # пятна сажи/ткани (рубашка кузнеца)
    gx, gy, gw, gh = L[key]
    speckle(gx, gy, gx + gw, gy + gh, (60, 60, 62), 0.22)
    speckle(gx, gy, gx + gw, gy + gh, (75, 55, 40), 0.12)


def draw_face(cfg):
    skin = cfg['skin']
    if cfg.get('soot'):                      # сажа на лице
        speckle(8, 8, 16, 10, (55, 50, 50), 0.30)
        speckle(8, 11, 10, 15, (55, 50, 50), 0.15)
        speckle(14, 8, 16, 12, (55, 50, 50), 0.15)
    if not cfg.get('glasses'):               # бровь
        for x in range(9, 15):
            px(x, 10, (70, 50, 30), rng.uniform(0.9, 1.1))
    for (xo, xi) in ((9, 10), (13, 14)):     # глаза
        px(xo, 11, (235, 235, 225)); px(xi, 11, (60, 140, 70))
        img[11 * S + S // 2:12 * S, xi * S + S // 2:(xi + 1) * S, :3] *= 0.4
    if cfg.get('glasses'):                   # чёрная оправа
        for x in (9, 10, 13, 14):
            px(x, 10, (35, 35, 40)); px(x, 12, (35, 35, 40))
        px(8, 11, (35, 35, 40)); px(15, 11, (35, 35, 40))
        shade(11, 11, 13, 12, 0.55)
    b = cfg['beard']                         # борода: бакенбарды + подбородок
    for y in range(11, 15):
        px(8, y, b, 0.9); px(15, y, b, 0.9)
    for x in range(9, 15):
        px(x, 15, b)
    px(9, 14, b); px(14, 14, b)
    for x in (11, 12):                       # нос с объёмом
        px(x, 12, tuple(int(v * 0.82) for v in skin), 1.18 if x == 11 else 0.85)
        px(x, 13, tuple(int(v * 0.82) for v in skin), 1.18 if x == 11 else 0.85)
    for x in (9, 10, 13, 14):
        px(x, 13, b, 1.1)                    # усы
    for x in range(10, 14):
        px(x, 14, b, 1.1)


def draw_head(cfg):
    for k in ('head_front', 'head_right', 'head_left', 'head_back', 'head_top'):
        fill(k, cfg['skin'], 0.08)
    draw_face(cfg)
    hat = cfg['hat']
    if hat in ('straw', 'hat'):
        c = (200, 170, 80) if hat == 'straw' else (110, 75, 45)
        fill('head_top', c, 0.25)
        shade(8, 0, 16, 1, 1.15); shade(8, 3, 16, 4, 0.8)   # тулья + лента
        for x in range(8, 16):
            px(x, 8, c, rng.uniform(0.9, 1.1))
        for kx in (0, 16, 24):
            for x in range(kx, kx + 8):
                px(x, 8, c); px(x, 9, c, 0.88)
    elif hat == 'hood':
        h = cfg['hood']; g = cfg.get('trim')
        fill('head_top', h); fill('head_back', h)
        for k in ('head_right', 'head_left'):
            fill(k, h)
        for x in range(8, 16):
            px(x, 8, g if g else h)
        for y in range(9, 16):
            px(8, y, g if g else h); px(15, y, g if g else h)
    elif hat == 'hair':
        hair = cfg['hair']
        fill('head_top', hair, 0.25)
        fill('head_back', hair, 0.25)
        for x in range(8, 16):
            px(x, 8, hair)
        for y in range(8, 12):
            for kx in (0, 16, 24):
                px(kx, y, hair); px(kx + 7, y, hair)
    elif hat == 'helm':
        hm = (178, 184, 194)
        fill('head_top', hm, 0.12)
        fill('head_back', hm, 0.12)
        for k in ('head_right', 'head_left'):
            fill(k, hm, 0.12)
        for x in range(8, 16):
            px(x, 8, hm, 0.92)
        for y in range(8, 12):  # нащёчники
            px(8, y, hm, 0.8); px(15, y, hm, 0.8)
        shade(8, 0, 16, 1, 1.18)  # блик сверху
    else:  # bald
        shade(8, 0, 16, 2, 1.08)
        if cfg.get('soot'):
            speckle(8, 0, 16, 4, (55, 50, 50), 0.25)
    # Игровые дополнения (не потребляют rng): низ головы + бокс носа
    gx, gy, gw, gh = L['head_bottom']
    c = np.clip(np.array(cfg['skin'], float) * 0.78, 0, 255)
    img[gy * S:(gy + gh) * S, gx * S:(gx + gw) * S, :3] = c
    img[gy * S:(gy + gh) * S, gx * S:(gx + gw) * S, 3] = 255
    paint_nose(cfg['skin'])


def paint_nose(skin):
    """Бокс носа (отдельный кубик 2x4x2 в модели): свет сверху/слева,
    тень снизу/справа, ноздри у нижней кромки."""
    nose = tuple(int(v * 0.82) for v in skin)
    gx, gy, gw, gh = L['nose_front']
    for y in range(gy, gy + gh):
        for x in range(gx, gx + gw):
            f = 1.18 if x == gx else 0.85
            if y == gy + gh - 1:
                f *= 0.78
            img[y * S:(y + 1) * S, x * S:(x + 1) * S, :3] = \
                np.clip(np.array(nose, float) * f, 0, 255)
            img[y * S:(y + 1) * S, x * S:(x + 1) * S, 3] = 255
    for key, f in (('nose_right', 0.85), ('nose_left', 0.85),
                   ('nose_back', 0.80), ('nose_top', 1.12),
                   ('nose_bottom', 0.60)):
        gx, gy, gw, gh = L[key]
        col = np.clip(np.array(nose, float) * f, 0, 255)
        img[gy * S:(gy + gh) * S, gx * S:(gx + gw) * S, :3] = col
        img[gy * S:(gy + gh) * S, gx * S:(gx + gw) * S, 3] = 255


def draw_apron(cfg):
    style, col = cfg['apron']
    gx, gy = 20, 20
    paste_arr(gx + 1, gy + 2, fabric(10 * S, 6 * S, col, 0.2))
    for y in (gy, gy + 1):
        px(gx + 2, y, col, 0.85); px(gx + 5, y, col, 0.85)
    shade(gx + 1, gy + 2, gx + 2, gy + 12, 0.85)
    shade(gx + 6, gy + 2, gx + 7, gy + 12, 0.85)
    shade(gx + 1, gy + 2, gx + 7, gy + 3, 1.1)
    for y in range(gy + 3, gy + 12, 2):  # строчка
        px(gx + 1, y, (240, 240, 230), 0.6)
        px(gx + 6, y, (240, 240, 230), 0.6)
    if style == 'long':   # карман фермера
        for x in range(gx + 2, gx + 6):
            px(x, gy + 4, tuple(int(v * 0.6) for v in col))
    if style == 'buckles':  # пряжки кузнеца
        for y in range(gy + 4, gy + 7):
            for x in range(gx + 3, gx + 5):
                px(x, y, tuple(int(v * 0.7) for v in col))
        px(gx + 2, gy + 2, (200, 200, 205)); px(gx + 5, gy + 2, (200, 200, 205))
    if style == 'tie':    # завязка мясника
        for x in range(gx + 1, gx + 7):
            px(x, gy + 6, (215, 215, 220)); px(x, gy + 7, (190, 190, 198))


def draw_body(cfg):
    for k in ('body_front', 'body_back', 'body_left', 'body_right',
              'body_top', 'body_bottom'):
        fill(k, cfg['robe'], 0.14)
    if cfg.get('mottle'):
        for k in ('body_front', 'body_back', 'body_left', 'body_right'):
            mottle(k)
    if cfg.get('apron'):
        draw_apron(cfg)
    gx, gy = 20, 20
    if cfg.get('trim'):  # золотая полоса и ворот
        t = cfg['trim']
        for y in range(gy, gy + 12):
            px(gx + 3, y, t, rng.uniform(0.9, 1.1))
            px(gx + 4, y, t, rng.uniform(0.9, 1.1))
        px(gx + 2, gy + 1, t); px(gx + 5, gy + 1, t)
        px(gx + 3, gy + 2, t); px(gx + 4, gy + 2, t)
    if cfg.get('book'):
        for y in range(gy + 4, gy + 8):
            px(gx + 5, y, (160, 30, 30)); px(gx + 6, y, (235, 230, 215))


def draw_arms(cfg):
    hand = (70, 50, 35) if cfg.get('gloves') else cfg['skin']
    for k in ('armR_front', 'armR_back', 'armR_left', 'armR_right'):
        fill(k, cfg['robe'], 0.14)
        if cfg.get('mottle'):
            mottle(k)
        gx, gy, gw, gh = L[k]
        if cfg.get('trim'):  # металлический манжет
            for x in range(gx, gx + gw):
                px(x, gy + 9, cfg['trim'])
            paste_arr(gx, gy + 10, fabric(2 * S, gw * S, hand, 0.1))
        elif cfg.get('gloves'):
            paste_arr(gx, gy + 9, fabric(3 * S, gw * S, hand, 0.2))
        else:
            paste_arr(gx, gy + 10, fabric(2 * S, gw * S, hand, 0.1))
    fill('armR_top', cfg['robe']); fill('armR_bottom', hand)


def draw_legs(cfg):
    base = cfg['robe'] if cfg['legs'] == 'robe' else cfg['pants']
    cuff, shoe = (170, 175, 182), (35, 28, 24)
    for k in ('legR_front', 'legR_back', 'legR_left', 'legR_right'):
        fill(k, base, 0.16)
        if cfg['legs'] == 'pants' and cfg.get('mottle'):
            mottle(k)
        gx, gy, gw, gh = L[k]
        for x in range(gx, gx + gw):
            px(x, gy + 10, cuff); px(x, gy + 11, shoe)
    fill('legR_top', base); fill('legR_bottom', shoe)


def draw_sword():
    """Меч в руке: свободный регион (36,32,4,16). Сверху рукоять и гарда,
    ниже клинок. Бокс модели 2x14x1 с origin (-5,12,-2.5) — висит от кисти
    правой руки; UV по классической раскладке ModelBox."""
    handle = (82, 55, 36)
    handle_dk = (58, 38, 26)
    guard = (228, 196, 92)
    steel_hi = (208, 218, 228)
    steel = (152, 162, 172)
    steel_dk = (104, 114, 124)
    edge = (238, 243, 248)
    # передняя грань (37..39, строки 33..47): рукоять, гарда, клинок, остриё
    for y in range(33, 37):
        px(37, y, handle)
        px(38, y, handle, 0.85)
    px(37, 35, handle_dk)
    px(38, 35, handle_dk)      # обмотка рукояти
    px(37, 37, guard)
    px(38, 37, guard)          # гарда
    for y in range(38, 45):    # клинок с бликом слева
        px(37, y, steel_hi)
        px(38, y, steel, 0.92 if y < 43 else 0.8)
    px(37, 45, edge)
    px(38, 45, steel_dk)
    px(37, 46, steel_dk)       # остриё
    # тонкие грани: правая/левая/задняя
    for y in range(33, 47):
        px(36, y, steel_dk, 0.9)
        px(39, y, steel, 0.85)
        px(40, y, steel_dk, 0.8)
    # крышки: навершие сверху, сечение клинка снизу
    px(37, 32, handle_dk)
    px(38, 32, handle_dk)
    px(39, 32, steel_dk)
    px(40, 32, steel_dk)


def jit(c, lo=0.88, hi=1.12):
    return tuple(int(np.clip(v * rng.uniform(lo, hi), 0, 255)) for v in c)


# ====================== сборка файлов ======================

def get_output_dir():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, "src", "main", "resources", "textures", "entities")
    os.makedirs(out, exist_ok=True)
    return out


def make_cfg(cfg, exact):
    """Рабочая копия конфига. exact=True — цвета байт-в-байт (концепт #0)."""
    c = dict(cfg)
    if not exact:
        for k in ('skin', 'robe', 'pants', 'beard', 'hood', 'hair', 'trim'):
            if c.get(k) is not None:
                c[k] = jit(c[k])
    return c


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    out_dir = get_output_dir()
    here = os.path.dirname(os.path.abspath(__file__))
    global img
    TILE = G * S
    # 17 тайлов в один ряд не влезают в 16384, поэтому сетка 9x4:
    # 2 строки профессий x 2 строки вариантов -> атлас 9216x4096
    COLS = 9
    ROWS = (len(PROF_ORDER) + COLS - 1) // COLS
    ATLAS_W = TILE * COLS
    ATLAS_H = TILE * VARIANTS * ROWS
    atlas = np.zeros((ATLAS_H, ATLAS_W, 4))
    atlas[:, :, 3] = 255
    import json
    manifest = {
        "version": 5,
        "skin_width": TILE, "skin_height": TILE,
        "atlas_width": ATLAS_W, "atlas_height": ATLAS_H,
        "tiles": len(PROF_ORDER), "variants": VARIANTS,
        "cols": COLS, "rows": ROWS,
        "professions": {},
    }
    preview = []
    print(f"Генерация {len(PROF_ORDER)} профессий x {VARIANTS} варианта"
          f" ({TILE}x{TILE}, атлас {ATLAS_W}x{ATLAS_H}, сетка {COLS}x{ROWS})...")
    # Порядок рисования: сначала концепт-варианты #0 в исходном порядке
    # авторского скрипта (rng-последовательность совпадает -> «те самые»
    # скины попиксельно), затем #0 остальных, затем все варианты #1.
    jobs = [(p, 0) for p in CONCEPT]
    jobs += [(p, 0) for p in PROF_ORDER if p not in CONCEPT]
    jobs += [(p, 1) for p in PROF_ORDER]
    for prof, i in jobs:
        cfg0 = PROFESSIONS[prof]
        idx = PROF_ORDER.index(prof)
        col = idx % COLS
        row = idx // COLS
        exact = prof in CONCEPT and i == 0
        cfg = make_cfg(cfg0, exact)
        img = np.zeros((H, W, 4))
        draw_head(cfg)
        draw_body(cfg)
        draw_arms(cfg)
        draw_legs(cfg)
        # Игровые дополнения (без rng): левые конечности = зеркало правых
        img[48 * S:64 * S, 32 * S:52 * S] = img[16 * S:32 * S, 40 * S:60 * S]
        img[48 * S:64 * S, 16 * S:32 * S] = img[16 * S:32 * S, 0 * S:16 * S]
        draw_sword()
        out = np.clip(img, 0, 255).astype(np.uint8)
        path = os.path.join(out_dir, f"villager_{prof}_{i}.png")
        Image.fromarray(out).save(path)
        atlas[(row + i * ROWS) * TILE:(row + i * ROWS + 1) * TILE,
              col * TILE:(col + 1) * TILE] = out
        if i == 0:
            Image.fromarray(out).save(
                os.path.join(out_dir, f"villager_{prof}.png"))
        manifest["professions"].setdefault(prof, {})[i] = {
            "atlas_index": idx, "u": col * TILE, "v": (row + i * ROWS) * TILE,
            "width": TILE, "height": TILE,
        }
        preview.append((prof, i, out))
        tag = " концепт" if exact else ""
        print(f"  ✓ {prof} #{i}{tag}")
    Image.fromarray(np.clip(atlas, 0, 255).astype(np.uint8)).save(
        os.path.join(out_dir, "villager_atlas.png"))
    with open(os.path.join(out_dir, "villager_atlas.json"), "w",
              encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
    print(f"✅ Атлас: {ATLAS_W}x{ATLAS_H} + манифест")

    # --- превью всех жителей (вид спереди) ---
    slot_w, slot_h = 10 * S, 32 * S
    cols = 9
    rows = (len(preview) + cols - 1) // cols
    pv = np.zeros((rows * (slot_h + 2 * S) + S,
                   (slot_w * cols) + 2 * S, 4))
    pv[:, :, :3] = 42
    pv[:, :, 3] = 255
    for n, (prof, variant, out) in enumerate(preview):
        r, c = divmod(n, cols)
        ox = S + c * slot_w
        oy = S + r * (slot_h + 2 * S)

        def reg(key):
            gx, gy, gw, gh = L[key]
            return out[gy * S:(gy + gh) * S, gx * S:(gx + gw) * S]

        pv[oy:oy + 8 * S, ox:ox + 8 * S] = reg('head_front')
        pv[oy + 8 * S:oy + 20 * S, ox:ox + 8 * S] = reg('body_front')
        pv[oy + 20 * S:oy + 32 * S, ox + 2 * S:ox + 6 * S] = reg('legR_front')
        pv[oy + 20 * S:oy + 32 * S, ox + 2 * S:ox + 6 * S] = reg('legL_front')
    preview_img = Image.fromarray(np.clip(pv, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(preview_img)
    for n, (prof, variant, out) in enumerate(preview):
        r, c = divmod(n, cols)
        label = f"{prof} #{variant}"
        tw = d.textlength(label)
        d.text((S + c * slot_w + (slot_w - tw) / 2,
                S + r * (slot_h + 2 * S) + slot_h + 8),
               label, fill=(235, 235, 235))
    preview_img.save(os.path.join(here, "villagers_preview.png"))
    print(f"✅ Превью: {os.path.join(here, 'villagers_preview.png')}")


if __name__ == "__main__":
    main()
