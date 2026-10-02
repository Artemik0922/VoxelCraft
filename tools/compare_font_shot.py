"""Diff a screenshot text line against the expected FontRenderer rendering."""
import re
import numpy as np
from PIL import Image

src = open(r'D:/VoxelCraft/src/main/java/com/voxelgame/ui/FontRenderer.java', encoding='utf-8').read()
glyphs = {}
for m in re.finditer(r"g\.put\('(.+?)', new String\[\]\{([^}]*)\}\)", src, re.S):
    ch = m.group(1)
    if ch == '\\\\':
        ch = '\\'
    elif ch == "\\'":
        ch = "'"
    glyphs[ch] = re.findall(r'"([^"]*)"', m.group(2))


def render_bw(text, scale=2):
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
                for ry, row in enumerate(rows[:7]):
                    for rx, ch2 in enumerate(row[:len(rows[0])]):
                        if ch2 == '#':
                            for sy in range(scale):
                                for sx in range(scale):
                                    px[x + rx * scale + sx + off,
                                       2 + ry * scale + sy + off] = 0
        x += ((len(rows[0]) if rows else 5) + 1) * scale
    return (np.array(im) < 140).astype(int)


def crop_top(a):
    rows = np.where(a.any(axis=1))[0]
    cols = np.where(a.any(axis=0))[0]
    return a[rows[0]:rows[-1] + 1, cols[0]:cols[-1] + 1]


SHOT = r'D:/dbltj/unknown/unknown_2026.09.27-18.39.png'
LINE = 'Выживание'
Y0, X0 = 458, 694   # top-left of the line's ink, found earlier

exp = render_bw(LINE)
im = np.array(Image.open(SHOT).convert('L'))
sub = (im[Y0:Y0 + exp.shape[0], X0:X0 + exp.shape[1]] < 140).astype(int)

e, s = crop_top(exp), crop_top(sub)
print('expected shape', e.shape, 'screenshot shape', s.shape)
h = min(e.shape[0], s.shape[0])
w = min(e.shape[1], s.shape[1])
diff = (e[:h, :w] != s[:h, :w])
print('mismatch pixels:', diff.sum(), '/', diff.size, f'({100 * diff.sum() / diff.size:.1f}%)')


def down2(a):
    h2, w2 = a.shape[0] // 2, a.shape[1] // 2
    return a[:h2 * 2, :w2 * 2].reshape(h2, 2, w2, 2).max(axis=(1, 3))


for name, arr in (('expected', e), ('screenshot', s)):
    print(f'--- {name} (GUI units) ---')
    for r in down2(arr):
        print(''.join('#' if v else '.' for v in r))
