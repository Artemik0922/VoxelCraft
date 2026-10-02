"""Replicate FontRenderer text drawing in PIL to compare with a real screenshot."""
import re
from PIL import Image

src = open(r'D:/VoxelCraft/src/main/java/com/voxelgame/ui/FontRenderer.java', encoding='utf-8').read()
glyphs = {}
for m in re.finditer(r"g\.put\('(.+?)', new String\[\]\{([^}]*)\}\)", src, re.S):
    ch = m.group(1)
    if ch == '\\\\':
        ch = '\\'
    elif ch == "\\'":
        ch = "'"
    rows = re.findall(r'"([^"]*)"', m.group(2))
    glyphs[ch] = rows


def render(text, scale=2):
    W = sum((len(glyphs.get(c, ['#####'])[0]) + 1) for c in text) * scale + 4
    H = 8 * scale + 4
    im = Image.new('L', (W, H), 255)
    px = im.load()
    x = 2
    for c in text:
        rows = glyphs.get(c)
        if rows and c != ' ':
            for shadow in (1, 0):
                off = 1 if shadow else 0
                col = 60 if shadow else 0
                for ry, row in enumerate(rows[:7]):
                    for rx, ch2 in enumerate(row[:len(rows[0])]):
                        if ch2 == '#':
                            for sy in range(scale):
                                for sx in range(scale):
                                    px[x + rx * scale + sx + off,
                                       2 + ry * scale + sy + off] = col
        x += ((len(rows[0]) if rows else 5) + 1) * scale
    return im


a = render('Выживание')
b = render('Выбор')
canvas = Image.new('L', (max(a.width, b.width), a.height + b.height + 8), 255)
canvas.paste(a, (0, 0))
canvas.paste(b, (0, a.height + 8))
canvas = canvas.resize((canvas.width * 4, canvas.height * 4), Image.NEAREST)
canvas.save('D:/VoxelCraft/debug/expected_render.png')
print('ok', a.size, b.size)
