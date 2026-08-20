"""
Генерация текстур раздвижной двери (sliding_door).

Приложение А из ТЗ + нарезка на два тайла 16x16:
  sliding_door.png            - HD 96x128, пурпурный ключ (0xFF,0x00,0xFF)
  sliding_door_16x32.png      - фолбэк 16x32 (NEAREST-даунскейл)
  sliding_door_top.png        - 16x16, верхняя половина (в resources)
  sliding_door_bottom.png     - 16x16, нижняя половина (в resources)

Дизайн повторяет ванильную дубовую дверь Minecraft: рама, вертикальные
доски, диагональные перекладины, остекление в верхней половине, ручка.
"""

import os
from PIL import Image

# ---------------------------------------------------------------------------
# Размеры (дверь 2 клетки: 2*16px внизу, 2*16px сверху, HD масштаб x16)
# ---------------------------------------------------------------------------
S = 16            # HD-масштаб: 1 клетка = 16x16 px
DW, DH = 2, 4     # дверь: 2x4 клетки (ширина 2, высота 4 -> 2 блока)
PAD = 2           # поля вокруг двери в клетках
OX = OY = PAD * 16
W = (DW + 2 * PAD) * 16   # 6*16 = 96
H = (DH + 2 * PAD) * 16   # 8*16 = 128

MAGENTA = (255, 0, 255)

# Палитра ванильной дубовой двери
PLANK_LIGHT = (168, 124, 66)
PLANK_DARK  = (140, 98, 50)
PLANK_LINE  = (122, 84, 42)
FRAME       = (96, 64, 30)
BEVEL       = (198, 150, 88)
KNOB        = (150, 105, 60)
GLASS_LIGHT = (205, 225, 235)
GLASS_DARK  = (150, 185, 205)
GLASS_LINE  = (110, 150, 175)


def draw_half(p: list, x0: int, y0: int, is_top: bool):
    """Рисует половину двери 16x16 в HD-масштабе (32x32 px) с углом (x0, y0).

    is_top=False — нижняя половина (ручка), is_top=True — верхняя (стекло).
    """
    def px(x, y, c):
        for yy in range(y * 2, y * 2 + 2):
            for xx in range(x * 2, x * 2 + 2):
                p[y0 + yy][x0 + xx] = (c[0], c[1], c[2], 255)

    def rect(x0p, y0p, x1, y1, c):
        for y in range(y0p, y1):
            for x in range(x0p, x1):
                px(x, y, c)

    # --- Рама (1px со всех сторон) ---
    for x in range(16):
        px(x, 0, FRAME)
        px(x, 15, FRAME)
    for y in range(16):
        px(0, y, FRAME)
        px(15, y, FRAME)

    # --- Поле досок с вертикальной текстурой ---
    for y in range(1, 15):
        for x in range(1, 15):
            base = PLANK_LIGHT if (x + y) % 8 < 6 else PLANK_DARK
            px(x, y, base)
    for y in range(1, 15):
        for x in range(1, 15):
            if (x + y) % 4 == 0:
                px(x, y, PLANK_LINE)

    # --- Горизонтальные перекладины (низ и верх каждой половины) ---
    rect(1, 1, 15, 2, FRAME)

    if not is_top:
        # Нижняя половина: диагональная перекладина (лев-верх -> прав-низ)
        for i in range(1, 15):
            px(i, i, FRAME)
        # Вторая диагональ (прав-верх -> лев-низ), ниже первой
        for i in range(1, 15):
            px(15 - i, i, FRAME)
        # Ручка: тёмный блок на правой кромке, внизу
        rect(12, 11, 15, 14, KNOB)
        px(11, 12, KNOB)
        px(11, 13, KNOB)
        # Ручка-скоба
        rect(9, 12, 11, 14, FRAME)
    else:
        # Верхняя половина: остекление 4x4 в центре
        rect(4, 5, 12, 13, GLASS_DARK)
        # Блики стекла
        rect(5, 6, 8, 8, GLASS_LIGHT)
        rect(9, 10, 11, 11, GLASS_LIGHT)
        # Стеклянная рама
        rect(4, 5, 12, 6, GLASS_LINE)
        rect(4, 12, 12, 13, GLASS_LINE)
        rect(4, 5, 5, 13, GLASS_LINE)
        rect(11, 5, 12, 13, GLASS_LINE)
        # Диагональные перекладины по бокам от стекла
        for i in range(1, 5):
            px(i, 14 - i, FRAME)
            px(15 - i, 14 - i, FRAME)
        # Бейвел на раме
        rect(1, 2, 2, 3, BEVEL)
        rect(13, 2, 15, 3, BEVEL)


def make_door_pixels() -> list:
    """Пиксели двери в масштабе HD (W x H), фон прозрачный."""
    px = [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]

    # Дверь: 2x4 клетки. Нижняя половина (2 клетки снизу), верхняя (2 сверху).
    # Нижняя клетка по Y: OY + 2*16 .. OY + 4*16, верхняя: OY .. OY + 2*16.
    draw_half(px, OX, OY, is_top=True)            # верхний блок
    draw_half(px, OX, OY + 32, is_top=False)      # нижний блок

    return px


def render_hd() -> Image.Image:
    """HD-текстура: дверь на пурпурном фоне, кратно 16x16."""
    px = make_door_pixels()
    img = Image.new("RGB", (W, H), MAGENTA)
    out = img.load()
    for yy in range(H):
        for xx in range(W):
            c = px[yy][xx]
            if c[3] == 255:
                out[xx, yy] = c[:3]
    return img


def render_16x32() -> Image.Image:
    """Фолбэк 16x32: дверь 16x32 без фона (BOX-даунскейл HD-двери)."""
    px = make_door_pixels()
    door_hd = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    out = door_hd.load()
    for yy in range(H):
        for xx in range(W):
            out[xx, yy] = px[yy][xx]
    door = door_hd.crop((OX, OY, OX + DW * 16, OY + DH * 16))
    return door.resize((16, 32), Image.BOX)


def split_tiles(img_16x32: Image.Image, out_dir: str) -> tuple:
    """Разрезание 16x32 на два тайла 16x16: верх (строки 0-15), низ (16-31)."""
    top = img_16x32.crop((0, 0, 16, 16))
    bottom = img_16x32.crop((0, 16, 16, 32))

    top_path = os.path.join(out_dir, "sliding_door_top.png")
    bottom_path = os.path.join(out_dir, "sliding_door_bottom.png")
    top.save(top_path)
    bottom.save(bottom_path)

    preview = Image.new("RGB", (32, 64), (0, 0, 0))
    preview.paste(top.convert("RGB"), (0, 0))
    preview.paste(bottom.convert("RGB"), (0, 32))
    preview = preview.resize((128, 256), Image.NEAREST)
    preview.save(os.path.join(out_dir, "sliding_door_preview.png"))
    return top_path, bottom_path


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    blocks_dir = os.path.join(
        project_root, "src", "main", "resources", "textures", "blocks")

    os.makedirs(blocks_dir, exist_ok=True)

    hd = render_hd()
    hd.save(os.path.join(script_dir, "sliding_door.png"))
    print("saved sliding_door.png (%dx%d)" % hd.size)

    fb = render_16x32()
    fb.save(os.path.join(script_dir, "sliding_door_16x32.png"))
    print("saved sliding_door_16x32.png (%dx%d)" % fb.size)

    top_path, bottom_path = split_tiles(fb, blocks_dir)
    print("saved %s" % top_path)
    print("saved %s" % bottom_path)


if __name__ == "__main__":
    main()