"""Generate 64x64 bed textures: frame, mattress, pillow, head/foot boards.

Outputs to src/main/resources/textures/bed_*.png
"""
import math
import os
import random

from PIL import Image, ImageDraw

SIZE = 64
OUT_DIR = os.path.join(os.path.dirname(__file__), "..",
                       "src", "main", "resources", "textures")


def new_img(color):
    img = Image.new("RGB", (SIZE, SIZE), color)
    return img


def noisy(img, rect, base, amount=12, spread=0.5):
    x, y, w, h = rect
    draw = ImageDraw.Draw(img)
    for _ in range(int(w * h * spread)):
        px = x + random.randrange(w)
        py = y + random.randrange(h)
        j = random.randint(-amount, amount)
        c = tuple(max(0, min(255, base[i] + j)) for i in range(3))
        draw.point((px, py), fill=c)


def bed_frame():
    """Wooden frame side: oak plank band with grain."""
    img = new_img((0x63, 0x50, 0x30))
    noisy(img, (0, 0, SIZE, SIZE), (0x63, 0x50, 0x30), amount=14, spread=0.6)
    draw = ImageDraw.Draw(img)
    for i in range(0, SIZE, 16):
        draw.line([(0, i), (SIZE - 1, i)], fill=(0x45, 0x38, 0x20))
        draw.line([(0, i + 1), (SIZE - 1, i + 1)], fill=(0x72, 0x5E, 0x3C))
    for _ in range(60):
        x = random.randrange(SIZE)
        y = random.randrange(SIZE)
        ln = random.randrange(4, 14)
        c = (0x4A, 0x3C, 0x24) if random.random() < 0.7 else (0x74, 0x62, 0x40)
        draw.line([(x, y), (x, y + ln)], fill=c)
    return img


def bed_mattress_side():
    """Quilted red mattress band with stitch rows and white trim."""
    red = (0x8A, 0x1E, 0x1C)
    img = new_img(red)
    noisy(img, (0, 0, SIZE, SIZE), red, amount=10, spread=0.5)
    draw = ImageDraw.Draw(img)
    # Top and bottom white trim
    draw.rectangle([0, 0, SIZE - 1, 2], fill=(0xE8, 0xE8, 0xE6))
    draw.rectangle([0, SIZE - 3, SIZE - 1, SIZE - 1], fill=(0xC0, 0xC0, 0xBE))
    # Stitch rows with tick marks
    for y in range(8, SIZE - 8, 9):
        draw.line([(0, y), (SIZE - 1, y)], fill=(0x6A, 0x14, 0x12))
        for x in range(2, SIZE, 6):
            draw.point((x, y + 1), fill=(0x9E, 0x2E, 0x2C))
    return img


def bed_mattress_top():
    """Quilted red top with diamond stitching."""
    red = (0x8A, 0x1E, 0x1C)
    img = new_img(red)
    noisy(img, (0, 0, SIZE, SIZE), red, amount=9, spread=0.5)
    draw = ImageDraw.Draw(img)
    # Diamond quilting: crossing diagonal stitch lines
    step = 12
    for k in range(-SIZE, SIZE * 2, step):
        draw.line([(k, 0), (k + SIZE, SIZE)], fill=(0x6A, 0x14, 0x12), width=0)
        draw.line([(k, SIZE), (k + SIZE, 0)], fill=(0x6A, 0x14, 0x12), width=0)
    # Soft center glow
    for i in range(8):
        t = i / 8.0
        a = int(14 * (1 - t))
        c = (0x8A + a, 0x1E + a // 4, 0x1C + a // 4)
        inset = int(t * 30)
        draw.rectangle([inset, inset, SIZE - 1 - inset, SIZE - 1 - inset],
                       outline=c)
    return img


def bed_pillow():
    """White puffy pillow with a center crease."""
    img = new_img((0xEE, 0xEE, 0xEC))
    noisy(img, (0, 0, SIZE, SIZE), (0xEE, 0xEE, 0xEC), amount=6, spread=0.4)
    draw = ImageDraw.Draw(img)
    # Rounded shading toward the edges
    for i in range(10):
        t = i / 10.0
        shade = int(34 * (1 - t))
        inset = int(t * 24)
        c = (0xEE - shade, 0xEE - shade, 0xEC - shade)
        draw.rectangle([inset, inset, SIZE - 1 - inset, SIZE - 1 - inset],
                       outline=c)
    # Center crease
    draw.line([(SIZE // 2, 4), (SIZE // 2, SIZE - 5)], fill=(0xD4, 0xD4, 0xD2))
    draw.line([(SIZE // 2 + 1, 4), (SIZE // 2 + 1, SIZE - 5)],
              fill=(0xF8, 0xF8, 0xF6))
    return img


def bed_headboard():
    """Oak headboard: wood panel with rounded corners and a carved line."""
    img = new_img((0x6E, 0x58, 0x36))
    noisy(img, (0, 0, SIZE, SIZE), (0x6E, 0x58, 0x36), amount=14, spread=0.6)
    draw = ImageDraw.Draw(img)
    # Frame border
    draw.rectangle([1, 1, SIZE - 2, SIZE - 2], outline=(0x4A, 0x3A, 0x22), width=3)
    draw.rectangle([4, 4, SIZE - 5, SIZE - 5], outline=(0x8A, 0x72, 0x4E), width=1)
    # Carved inner panel
    draw.rectangle([10, 10, SIZE - 11, SIZE - 11], outline=(0x52, 0x42, 0x28), width=2)
    # Vertical grain
    for _ in range(40):
        x = random.randrange(2, SIZE - 2)
        ln = random.randrange(6, 20)
        y0 = random.randrange(2, SIZE - 4)
        draw.line([(x, y0), (x, y0 + ln)], fill=(0x58, 0x46, 0x2A))
    return img


def bed_footboard():
    """Oak footboard: lower version of the headboard."""
    img = bed_headboard()
    draw = ImageDraw.Draw(img)
    # Slightly darker, with a simple carved stripe instead of the panel
    draw.rectangle([0, 0, SIZE - 1, SIZE - 1], fill=None)
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, fn in (("bed_frame", bed_frame),
                     ("bed_mattress_side", bed_mattress_side),
                     ("bed_mattress_top", bed_mattress_top),
                     ("bed_pillow", bed_pillow),
                     ("bed_headboard", bed_headboard),
                     ("bed_footboard", bed_footboard)):
        path = os.path.join(OUT_DIR, name + ".png")
        fn().save(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    random.seed(2026)
    main()