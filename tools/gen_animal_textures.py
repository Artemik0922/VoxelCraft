"""Generate 64x64 skin sheets for the farm animals.

The UV layout mirrors ModelBox: for a box (u, v, w, h, d) the sheet holds
    [top ][bot ]
 [rt][frt][lft][bck]
with the side row starting at (u, v + d) and the crown at (u + d, v).

Outputs to src/main/resources/textures/entities/{cow,pig,chicken,sheep}.png
"""
import math
import os
import random

from PIL import Image, ImageDraw

SIZE = 64
OUT_DIR = os.path.join(os.path.dirname(__file__), "..",
                       "src", "main", "resources", "textures", "entities")


def box_faces(u, v, w, h, d):
    """Rectangles of each face on the sheet, in ModelBox convention."""
    return {
        "right": (u, v + d, w, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, w, h),
        "back": (u + d + w + d, v + d, w, h),
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
    }


def paint(img, rect, color):
    x, y, w, h = rect
    draw = ImageDraw.Draw(img)
    draw.rectangle([x, y, x + w - 1, y + h - 1], fill=color)


def noise_paint(img, rect, base, amount=14, spread=0.35):
    """Fill a region with the base color plus random speckles."""
    x, y, w, h = rect
    paint(img, rect, base)
    draw = ImageDraw.Draw(img)
    for _ in range(int(w * h * spread)):
        px = x + random.randrange(w)
        py = y + random.randrange(h)
        jitter = random.randint(-amount, amount)
        c = tuple(max(0, min(255, base[i] + jitter)) for i in range(3))
        draw.point((px, py), fill=c)


def patch_blobs(img, rect, color, count=5, radius=4, seed=None):
    """Random organic blobs inside a region."""
    x, y, w, h = rect
    rng = random.Random(seed)
    draw = ImageDraw.Draw(img)
    for _ in range(count):
        cx = x + rng.randint(0, max(0, w - 1))
        cy = y + rng.randint(0, max(0, h - 1))
        r = rng.randint(1, radius)
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)


def new_sheet(base):
    img = Image.new("RGB", (SIZE, SIZE), base)
    return img


def cow():
    img = new_sheet((255, 255, 255))
    body = box_faces(0, 0, 8, 8, 16)
    head = box_faces(0, 24, 8, 8, 8)
    legs = box_faces(22, 40, 4, 12, 4)

    black = (30, 30, 32)
    # Body: white with black patches
    patch_blobs(img, body["front"], black, count=3, radius=4, seed=1)
    patch_blobs(img, body["left"], black, count=4, radius=5, seed=2)
    patch_blobs(img, body["back"], black, count=5, radius=5, seed=3)
    patch_blobs(img, body["top"], black, count=3, radius=4, seed=4)
    noise_paint(img, body["bottom"], (235, 228, 218), amount=6, spread=0.2)

    # Head: eyes in black patches, pink muzzle with nostrils, horns on the
    # crown, ears on the sides
    fx, fy, fw, fh = head["front"]
    draw = ImageDraw.Draw(img)
    draw.rectangle([fx, fy, fx + fw - 1, fy + fh - 1], fill=(250, 250, 248))
    draw.rectangle([fx, fy + fh - 1, fx + fw - 1, fy + fh - 1], fill=(214, 206, 194))
    for ex in (fx + 1, fx + 5):
        draw.ellipse([ex, fy + 1, ex + 2, fy + 3], fill=(28, 26, 28))
        draw.ellipse([ex + 1, fy + 1, ex + 2, fy + 2], fill=(252, 252, 252))
        draw.point((ex + 1, fy + 2), fill=(16, 14, 16))
    draw.ellipse([fx + 2, fy + 5, fx + 5, fy + 7], fill=(226, 170, 156))
    draw.rectangle([fx + 3, fy + 6, fx + 4, fy + 7], fill=(150, 96, 88))
    draw.point((fx + 3, fy + 6), fill=(112, 66, 60))
    draw.point((fx + 4, fy + 6), fill=(112, 66, 60))
    draw.line([fx + 3, fy + 7, fx + 4, fy + 7], fill=(140, 90, 80))
    # Horns on the crown (tips at the front edge)
    tx, ty, tw, th = head["top"]
    draw.rectangle([tx, ty, tx + tw - 1, ty + th - 1], fill=(252, 252, 250))
    draw.polygon([(tx + 1, ty + th - 2), (tx + 1, ty + 1), (tx + 3, ty + th - 2)],
                 fill=(178, 156, 118))
    draw.polygon([(tx + tw - 2, ty + th - 2), (tx + tw - 2, ty + 1), (tx + tw - 4, ty + th - 2)],
                 fill=(178, 156, 118))
    # Ears on the side faces
    for rect in (head["right"], head["left"]):
        x, y, w, h = rect
        draw.ellipse([x + 1, y + 1, x + w - 2, y + 3], fill=(158, 128, 86))
    paint(img, head["bottom"], (238, 236, 232))

    # Legs: white with black hooves
    for face in ("right", "front", "left", "back"):
        x, y, w, h = legs[face]
        paint(img, legs[face], (255, 255, 255))
        draw = ImageDraw.Draw(img)
        draw.rectangle([x, y + h - 3, x + w - 1, y + h - 1], fill=black)
    paint(img, legs["top"], (250, 250, 250))
    paint(img, legs["bottom"], (40, 40, 42))
    return img


def pig():
    pink = (232, 176, 160)
    img = new_sheet(pink)
    body = box_faces(0, 0, 8, 8, 16)
    head = box_faces(0, 24, 8, 8, 8)
    legs = box_faces(22, 40, 4, 12, 4)

    noise_paint(img, body["right"], pink, amount=8, spread=0.3)
    noise_paint(img, body["front"], pink, amount=8, spread=0.3)
    noise_paint(img, body["left"], pink, amount=8, spread=0.3)
    noise_paint(img, body["back"], pink, amount=8, spread=0.3)
    # Belly slightly darker
    x, y, w, h = body["front"]
    draw = ImageDraw.Draw(img)
    draw.rectangle([x, y + h - 2, x + w - 1, y + h - 1], fill=(206, 148, 134))
    paint(img, body["top"], (226, 170, 154))
    paint(img, body["bottom"], (206, 148, 134))

    noise_paint(img, head["right"], pink, amount=8, spread=0.3)
    noise_paint(img, head["left"], pink, amount=8, spread=0.3)
    noise_paint(img, head["back"], pink, amount=8, spread=0.3)
    fx, fy, fw, fh = head["front"]
    draw = ImageDraw.Draw(img)
    draw.rectangle([fx, fy, fx + fw - 1, fy + fh - 1], fill=(224, 166, 150))
    draw.rectangle([fx, fy + fh - 1, fx + fw - 1, fy + fh - 1], fill=(198, 140, 126))
    # Eyes: small dark dots with a glint
    for ex in (fx + 1, fx + 5):
        draw.point((ex + 1, fy + 1), fill=(240, 236, 232))
        draw.point((ex + 1, fy + 2), fill=(58, 42, 40))
        draw.point((ex + 2, fy + 2), fill=(58, 42, 40))
    # Snout: darker disc with nostrils
    draw.ellipse([fx + 2, fy + 4, fx + 5, fy + 7], fill=(206, 144, 130))
    draw.ellipse([fx + 3, fy + 5, fx + 4, fy + 6], fill=(140, 88, 76))
    draw.ellipse([fx + 4, fy + 5, fx + 5, fy + 6], fill=(140, 88, 76))
    # Crown: folded ears at the front corners
    tx, ty, tw, th = head["top"]
    draw.rectangle([tx, ty, tx + tw - 1, ty + th - 1], fill=(226, 170, 154))
    draw.polygon([(tx + 1, ty + th - 2), (tx + 1, ty + 1), (tx + 3, ty + th - 2)],
                 fill=(196, 138, 124))
    draw.polygon([(tx + tw - 2, ty + th - 2), (tx + tw - 2, ty + 1), (tx + tw - 4, ty + th - 2)],
                 fill=(196, 138, 124))
    # Ears on the side faces
    for rect in (head["right"], head["left"]):
        x, y, w, h = rect
        draw.ellipse([x + 1, y + 1, x + w - 2, y + 3], fill=(206, 148, 132))
    paint(img, head["bottom"], (206, 148, 134))

    for face in ("right", "front", "left", "back"):
        noise_paint(img, legs[face], pink, amount=8, spread=0.3)
    paint(img, legs["top"], (226, 170, 154))
    paint(img, legs["bottom"], (206, 148, 134))
    return img


def chicken():
    white = (250, 250, 250)
    img = new_sheet(white)
    body = box_faces(0, 16, 6, 7, 8)
    head = box_faces(0, 0, 4, 4, 4)
    beak = box_faces(0, 8, 2, 2, 2)
    wings = box_faces(16, 8, 2, 4, 6)
    legs = box_faces(32, 0, 2, 8, 2)

    noise_paint(img, body["right"], white, amount=5, spread=0.25)
    noise_paint(img, body["front"], white, amount=5, spread=0.25)
    noise_paint(img, body["left"], white, amount=5, spread=0.25)
    # Brown tail feathers on the back
    x, y, w, h = body["back"]
    paint(img, body["back"], (198, 168, 118))
    draw = ImageDraw.Draw(img)
    draw.rectangle([x, y + h - 2, x + w - 1, y + h - 1], fill=(140, 110, 70))
    paint(img, body["top"], (245, 245, 245))
    paint(img, body["bottom"], (235, 235, 235))

    # Head: white with a red comb on top, a red wattle and an eye on the face
    noise_paint(img, head["right"], white, amount=4, spread=0.2)
    noise_paint(img, head["left"], white, amount=4, spread=0.2)
    noise_paint(img, head["back"], white, amount=4, spread=0.2)
    paint(img, head["top"], (232, 44, 36))
    draw = ImageDraw.Draw(img)
    hx, hy, hw, hh = head["top"]
    draw.rectangle([hx + 1, hy, hx + 2, hy + 2], fill=(232, 44, 36))
    draw.rectangle([hx + 2, hy, hx + 3, hy + 2], fill=(232, 44, 36))
    fx, fy, fw, fh = head["front"]
    draw.rectangle([fx, fy + fh - 1, fx + fw - 1, fy + fh - 1], fill=(232, 44, 36))
    draw.rectangle([fx, fy + fh - 2, fx + 1, fy + fh - 1], fill=(200, 30, 28))
    # Eye: dark dot with a white glint
    draw.point((fx + 2, fy + 1), fill=(255, 255, 255))
    draw.point((fx + 2, fy + 2), fill=(44, 42, 42))
    paint(img, head["bottom"], (235, 235, 235))

    # Beak: yellow
    for face in ("right", "front", "left", "back"):
        paint(img, beak[face], (240, 200, 60))
    paint(img, beak["top"], (246, 212, 84))
    paint(img, beak["bottom"], (214, 172, 44))

    # Wings: white with brown tips
    for face in ("right", "front", "left", "back"):
        noise_paint(img, wings[face], white, amount=4, spread=0.2)
        x, y, w, h = wings[face]
        draw = ImageDraw.Draw(img)
        draw.rectangle([x, y + h - 2, x + w - 1, y + h - 1], fill=(176, 146, 98))
    paint(img, wings["top"], (245, 245, 245))
    paint(img, wings["bottom"], (235, 235, 235))

    # Legs: orange
    for face in ("right", "front", "left", "back"):
        paint(img, legs[face], (232, 168, 72))
    paint(img, legs["top"], (238, 180, 92))
    paint(img, legs["bottom"], (206, 140, 52))
    return img


def deer():
    brown = (139, 90, 43)
    belly = (210, 180, 140)
    antler = (160, 82, 45)
    leg = (101, 67, 33)
    img = new_sheet(brown)
    body = box_faces(0, 0, 8, 8, 16)
    head = box_faces(0, 24, 8, 8, 8)
    legs = box_faces(24, 40, 3, 12, 3)
    antlers = box_faces(24, 0, 2, 6, 2)

    # Body: brown fur with a lighter belly and a white rump patch
    for name in ("right", "front", "left"):
        noise_paint(img, body[name], brown, amount=12, spread=0.5)
    paint(img, body["top"], (124, 79, 37))
    x, y, w, h = body["back"]
    paint(img, body["back"], brown)
    draw = ImageDraw.Draw(img)
    draw.rectangle([x, y + h - 3, x + w - 1, y + h - 1], fill=(250, 246, 236))
    x, y, w, h = body["front"]
    draw.rectangle([x, y + h - 2, x + w - 1, y + h - 1], fill=belly)
    paint(img, body["bottom"], belly)

    # Head: brown, lighter muzzle, eyes, nostrils, side ears
    fx, fy, fw, fh = head["front"]
    draw = ImageDraw.Draw(img)
    draw.rectangle([fx, fy, fx + fw - 1, fy + fh - 1], fill=brown)
    draw.rectangle([fx, fy + fh - 4, fx + fw - 1, fy + fh - 1], fill=(196, 166, 122))
    for ex in (fx + 1, fx + 5):
        draw.ellipse([ex, fy + 1, ex + 2, fy + 3], fill=(30, 26, 22))
        draw.point((ex + 2, fy + 1), fill=(250, 250, 250))
    draw.ellipse([fx + 2, fy + 6, fx + 3, fy + 7], fill=(60, 44, 32))
    draw.ellipse([fx + 4, fy + 6, fx + 5, fy + 7], fill=(60, 44, 32))
    noise_paint(img, head["right"], brown, amount=10, spread=0.4)
    noise_paint(img, head["left"], brown, amount=10, spread=0.4)
    noise_paint(img, head["back"], brown, amount=10, spread=0.4)
    paint(img, head["top"], (124, 79, 37))
    for rect in (head["right"], head["left"]):
        ex, ey, ew, eh = rect
        draw.ellipse([ex, ey + 1, ex + ew - 1, ey + 3], fill=brown)
        draw.ellipse([ex, ey + 2, ex + ew - 1, ey + 3], fill=(226, 200, 170))
    paint(img, head["bottom"], belly)

    # Antlers: pale bone with darker tine edges
    for name in ("right", "front", "left", "back"):
        noise_paint(img, antlers[name], antler, amount=8, spread=0.35)
    paint(img, antlers["top"], (178, 96, 54))
    paint(img, antlers["bottom"], (138, 66, 34))

    # Legs: dark fur with near-black hooves
    for name in ("right", "front", "left", "back"):
        noise_paint(img, legs[name], leg, amount=10, spread=0.4)
        x, y, w, h = legs[name]
        draw = ImageDraw.Draw(img)
        draw.rectangle([x, y + h - 2, x + w - 1, y + h - 1], fill=(38, 28, 20))
    paint(img, legs["top"], (116, 76, 38))
    paint(img, legs["bottom"], (38, 28, 20))
    return img


def fox():
    orange = (230, 120, 40)
    white = (240, 240, 240)
    dark = (60, 40, 20)
    paw = (80, 50, 30)
    img = new_sheet(orange)
    body = box_faces(0, 0, 8, 8, 10)
    head = box_faces(0, 16, 8, 8, 6)
    snout = box_faces(16, 16, 4, 4, 3)
    ears = box_faces(0, 24, 2, 3, 2)
    front = box_faces(20, 24, 2, 6, 2)
    hind = box_faces(28, 24, 2, 4, 2)
    tail = box_faces(32, 0, 6, 6, 6)

    # Body: chest and belly are white
    for name in ("right", "left", "back"):
        noise_paint(img, body[name], orange, amount=10, spread=0.45)
    x, y, w, h = body["front"]
    paint(img, body["front"], orange)
    draw = ImageDraw.Draw(img)
    draw.rectangle([x, y + h - 3, x + w - 1, y + h - 1], fill=white)
    paint(img, body["top"], (214, 108, 32))
    paint(img, body["bottom"], white)

    # Head: white cheeks, dark eyes, black nose on the snout
    for name in ("right", "left", "back"):
        noise_paint(img, head[name], orange, amount=10, spread=0.4)
    fx, fy, fw, fh = head["front"]
    draw = ImageDraw.Draw(img)
    draw.rectangle([fx, fy, fx + fw - 1, fy + fh - 1], fill=orange)
    draw.rectangle([fx, fy + fh - 4, fx + fw - 1, fy + fh - 1], fill=white)
    for ex in (fx + 1, fx + 5):
        draw.point((ex + 1, fy + 1), fill=(250, 250, 250))
        draw.point((ex + 1, fy + 2), fill=(30, 24, 18))
        draw.point((ex + 2, fy + 2), fill=(30, 24, 18))
    paint(img, head["top"], (214, 108, 32))
    paint(img, head["bottom"], white)

    # Snout: white wedge with a black nose
    for name in ("right", "front", "left", "back"):
        paint(img, snout[name], white)
    sx, sy, sw, sh = snout["front"]
    draw.ellipse([sx + 1, sy, sx + sw - 2, sy + 1], fill=(26, 22, 18))

    # Ears: orange with black tips
    for name in ("right", "front", "left", "back"):
        x, y, w, h = ears[name]
        paint(img, ears[name], orange)
        draw.rectangle([x, y, x + w - 1, y + 1], fill=dark)
    paint(img, ears["top"], dark)
    paint(img, ears["bottom"], orange)

    # Front legs: dark paws, hind legs folded with dark feet
    for name in ("right", "front", "left", "back"):
        noise_paint(img, front[name], orange, amount=8, spread=0.35)
        x, y, w, h = front[name]
        draw.rectangle([x, y + h - 2, x + w - 1, y + h - 1], fill=dark)
    paint(img, front["top"], (214, 108, 32))
    paint(img, front["bottom"], dark)
    for name in ("right", "front", "left", "back"):
        noise_paint(img, hind[name], orange, amount=8, spread=0.35)
        x, y, w, h = hind[name]
        draw.rectangle([x, y + h - 1, x + w - 1, y + h - 1], fill=dark)
    paint(img, hind["top"], (214, 108, 32))
    paint(img, hind["bottom"], dark)

    # Tail: orange with a white tip
    for name in ("right", "front", "left", "top"):
        noise_paint(img, tail[name], orange, amount=10, spread=0.4)
    tx, ty, tw, th = tail["back"]
    paint(img, tail["back"], orange)
    draw.rectangle([tx + tw - 2, ty, tx + tw - 1, ty + th - 1], fill=white)
    paint(img, tail["bottom"], white)
    return img


def bear():
    brown = (101, 67, 33)
    belly = (139, 90, 43)
    snout_c = (160, 120, 80)
    claw = (200, 200, 200)
    img = new_sheet(brown)
    body = box_faces(0, 0, 12, 10, 14)
    head = box_faces(0, 24, 8, 8, 8)
    ears = box_faces(24, 0, 2, 2, 2)
    legs = box_faces(24, 40, 5, 6, 5)

    # Body: shaggy dark fur, lighter belly
    for name in ("right", "front", "left", "back", "top"):
        noise_paint(img, body[name], brown, amount=14, spread=0.6)
    paint(img, body["bottom"], belly)
    x, y, w, h = body["front"]
    draw = ImageDraw.Draw(img)
    draw.rectangle([x, y + h - 2, x + w - 1, y + h - 1], fill=belly)

    # Head: light muzzle, dark nose, eyes, ears with dark inner
    for name in ("right", "left", "back", "top"):
        noise_paint(img, head[name], brown, amount=12, spread=0.5)
    fx, fy, fw, fh = head["front"]
    draw = ImageDraw.Draw(img)
    draw.rectangle([fx, fy, fx + fw - 1, fy + fh - 1], fill=brown)
    draw.rectangle([fx, fy + fh - 5, fx + fw - 1, fy + fh - 1], fill=snout_c)
    for ex in (fx + 1, fx + 5):
        draw.point((ex + 1, fy + 1), fill=(250, 250, 250))
        draw.point((ex + 1, fy + 2), fill=(26, 22, 18))
        draw.point((ex + 2, fy + 2), fill=(26, 22, 18))
    draw.ellipse([fx + 2, fy + fh - 3, fx + 3, fy + fh - 2], fill=(40, 30, 22))
    draw.ellipse([fx + 4, fy + fh - 3, fx + 5, fy + fh - 2], fill=(40, 30, 22))
    paint(img, head["bottom"], belly)

    # Ears: brown with dark inner dots
    for name in ("right", "front", "left", "back"):
        noise_paint(img, ears[name], brown, amount=6, spread=0.3)
    paint(img, ears["top"], (86, 54, 26))
    for name in ("right", "left"):
        x, y, w, h = ears[name]
        draw.ellipse([x + w // 2, y + h // 2, x + w // 2, y + h // 2], fill=(40, 28, 18))
    paint(img, ears["bottom"], brown)

    # Legs: dark fur with pale claws on the bottom row
    for name in ("right", "front", "left", "back"):
        noise_paint(img, legs[name], brown, amount=12, spread=0.5)
        x, y, w, h = legs[name]
        draw = ImageDraw.Draw(img)
        draw.rectangle([x, y + h - 1, x + w - 1, y + h - 1], fill=(60, 40, 20))
        for cx in (x + 1, x + w // 2, x + w - 2):
            draw.point((cx, y + h - 1), fill=claw)
    paint(img, legs["top"], (116, 78, 38))
    paint(img, legs["bottom"], (60, 40, 20))
    return img


def parrot():
    blue = (30, 100, 200)
    red = (200, 50, 50)
    beak_c = (240, 220, 50)
    tail_c = (30, 80, 180)
    gray = (150, 150, 150)
    img = new_sheet(blue)
    body = box_faces(0, 16, 6, 7, 8)
    head = box_faces(0, 0, 4, 4, 4)
    beak = box_faces(0, 8, 2, 2, 2)
    wings = box_faces(16, 8, 2, 4, 6)
    legs = box_faces(32, 0, 2, 8, 2)
    tail = box_faces(24, 0, 4, 6, 4)

    # Body: blue with a white belly
    for name in ("right", "left", "back", "top"):
        noise_paint(img, body[name], blue, amount=8, spread=0.35)
    x, y, w, h = body["front"]
    paint(img, body["front"], blue)
    draw = ImageDraw.Draw(img)
    draw.rectangle([x, y + h - 3, x + w - 1, y + h - 1], fill=(245, 245, 245))
    paint(img, body["bottom"], (225, 225, 225))

    # Head: blue, red crown, white eye patch, black eye
    for name in ("right", "left", "back"):
        noise_paint(img, head[name], blue, amount=6, spread=0.3)
    paint(img, head["top"], red)
    draw = ImageDraw.Draw(img)
    hx, hy, hw, hh = head["top"]
    draw.rectangle([hx + 1, hy + 2, hx + 2, hy + 3], fill=(240, 240, 240))
    fx, fy, fw, fh = head["front"]
    draw.rectangle([fx, fy, fx + fw - 1, fy + fh - 1], fill=blue)
    draw.rectangle([fx + 1, fy + 1, fx + 2, fy + 2], fill=(245, 245, 245))
    draw.point((fx + 2, fy + 1), fill=(20, 20, 20))
    paint(img, head["bottom"], (225, 225, 225))

    # Beak: yellow with a darker lower mandible
    for name in ("right", "front", "left"):
        paint(img, beak[name], beak_c)
    bx, by, bw, bh = beak["front"]
    draw.rectangle([bx, by + bh - 1, bx + bw - 1, by + bh - 1], fill=(206, 176, 40))
    paint(img, beak["top"], (250, 236, 110))
    paint(img, beak["bottom"], (206, 176, 40))

    # Wings: red with dark tips
    for name in ("right", "front", "left", "back"):
        noise_paint(img, wings[name], red, amount=8, spread=0.35)
        x, y, w, h = wings[name]
        draw.rectangle([x, y + h - 1, x + w - 1, y + h - 1], fill=(150, 30, 30))
    paint(img, wings["top"], (214, 64, 60))
    paint(img, wings["bottom"], (150, 30, 30))

    # Legs: gray
    for name in ("right", "front", "left", "back"):
        paint(img, legs[name], gray)
    paint(img, legs["top"], (166, 166, 166))
    paint(img, legs["bottom"], (128, 128, 128))

    # Tail: dark blue with a red tip
    for name in ("right", "front", "left", "top"):
        noise_paint(img, tail[name], tail_c, amount=8, spread=0.3)
    tx, ty, tw, th = tail["back"]
    paint(img, tail["back"], tail_c)
    draw.rectangle([tx, ty + th - 2, tx + tw - 1, ty + th - 1], fill=red)
    paint(img, tail["bottom"], (20, 60, 140))
    return img


def sheep():
    wool = (243, 243, 243)
    img = new_sheet(wool)
    body = box_faces(0, 0, 8, 8, 16)
    head = box_faces(0, 24, 8, 8, 8)
    legs = box_faces(22, 40, 4, 12, 4)

    face_gray = (186, 186, 186)
    dark = (120, 120, 120)
    for name in ("right", "front", "left", "back"):
        noise_paint(img, body[name], wool, amount=10, spread=0.6)
    noise_paint(img, body["top"], wool, amount=10, spread=0.6)
    noise_paint(img, body["bottom"], (226, 226, 226), amount=8, spread=0.4)

    noise_paint(img, head["right"], face_gray, amount=8, spread=0.4)
    noise_paint(img, head["left"], face_gray, amount=8, spread=0.4)
    noise_paint(img, head["back"], face_gray, amount=8, spread=0.4)
    # Face: dark gray with light eyes and a lighter muzzle with nostrils
    fx, fy, fw, fh = head["front"]
    paint(img, head["front"], dark)
    draw = ImageDraw.Draw(img)
    for ex in (fx + 1, fx + 5):
        draw.ellipse([ex, fy + 1, ex + 2, fy + 3], fill=(228, 226, 224))
        draw.point((ex + 1, fy + 2), fill=(46, 46, 48))
    draw.rectangle([fx + 1, fy + 4, fx + fw - 2, fy + fh - 1], fill=face_gray)
    draw.ellipse([fx + 2, fy + 5, fx + 3, fy + 6], fill=(80, 80, 80))
    draw.ellipse([fx + 4, fy + 5, fx + 5, fy + 6], fill=(80, 80, 80))
    # Fluffy ears on the crown and on the sides
    tx, ty, tw, th = head["top"]
    paint(img, head["top"], wool)
    draw.rectangle([tx + 1, ty + 2, tx + 2, ty + 3], fill=face_gray)
    draw.rectangle([tx + 5, ty + 2, tx + 6, ty + 3], fill=face_gray)
    for rect in (head["right"], head["left"]):
        x, y, w, h = rect
        draw.ellipse([x + 1, y + 1, x + w - 2, y + 3], fill=face_gray)
    paint(img, head["bottom"], (216, 216, 216))

    for name in ("right", "front", "left", "back"):
        noise_paint(img, legs[name], wool, amount=8, spread=0.4)
    paint(img, legs["top"], (238, 238, 238))
    paint(img, legs["bottom"], (216, 216, 216))
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, fn in (("cow", cow), ("pig", pig), ("chicken", chicken),
                     ("sheep", sheep), ("deer", deer), ("fox", fox),
                     ("bear", bear), ("parrot", parrot)):
        path = os.path.join(OUT_DIR, name + ".png")
        fn().save(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()