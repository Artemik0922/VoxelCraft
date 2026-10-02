#!/usr/bin/env python3
"""Minecraft-style 16x16 item sprites for VoxelCraft.

Every item is painted procedurally on a 16x16 grid, upscaled x4 to the
64x64 PNG slots under src/main/resources/textures/blocks/ (same convention
as the tool generator and the block texture scripts). The sprites are used
both as flat inventory icons and as the voxel layer for the 3D held-item
model (ItemModel3D voxelises non-transparent pixels).

Design notes: silhouettes are chunky (the 3D model needs volume), light
comes from the upper-left, each object uses a base + highlight + shade.
"""
from PIL import Image
import os, math

SRC = 'src/main/resources/textures/blocks'
S = 16
UP = 4
OUT = 'tools/item_preview.png'


class Canvas:
    def __init__(self):
        # None = transparent
        self.pix = [[None] * S for _ in range(S)]

    def set(self, x, y, c):
        if 0 <= x < S and 0 <= y < S:
            self.pix[y][x] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(max(0, y0), min(S, y1 + 1)):
            for x in range(max(0, x0), min(S, x1 + 1)):
                self.set(x, y, c)

    def disc(self, cx, cy, r, c):
        x0, x1 = int(cx - r), int(cx + r)
        y0, y1 = int(cy - r), int(cy + r)
        for y in range(max(0, y0), min(S, y1 + 1)):
            for x in range(max(0, x0), min(S, x1 + 1)):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 0.5:
                    self.set(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        x0, x1, y0, y1 = round(x0), round(x1), round(y0), round(y1)
        n = max(abs(x1 - x0), abs(y1 - y0))
        if n == 0:
            self.set(x0, y0, c)
            return
        for i in range(n + 1):
            t = i / n
            self.set(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), c)

    def thline(self, x0, y0, x1, y1, c, w=2):
        """Thick (w-pixel) diagonal band: draw w parallel lines."""
        dx, dy = x1 - x0, y1 - y0
        n = max(abs(dx), abs(dy))
        if n == 0:
            return
        for k in range(w):
            ux = -dy / n
            uy = dx / n
            off = (k - (w - 1) / 2)
            self.line(x0 + ux * off, y0 + uy * off, x1 + ux * off, y1 + uy * off, c)

    def erase(self, x0, y0, x1, y1):
        for y in range(max(0, y0), min(S, y1 + 1)):
            for x in range(max(0, x0), min(S, x1 + 1)):
                self.pix[y][x] = None


def render(c, ck=None):
    out = []
    for row in c.pix:
        out.append([ (v + (255,)) if v is not None else (0, 0, 0, 0) for v in row ])
    return out


def to_image(grid):
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    for y in range(S):
        for x in range(S):
            c = grid[y][x]
            if c is not None:
                img.putpixel((x, y), c)
    return img


# ---------------------------------------------------------------- palette --
WOOD = ((0xB0, 0x6A, 0x2E), (0xE8, 0xA0, 0x50), (0x5A, 0x33, 0x12))
STEEL = ((0xC8, 0xC8, 0xD0), (0xEE, 0xEE, 0xF2), (0x6E, 0x6E, 0x78))
GOLD = ((0xF0, 0xC0, 0x20), (0xFF, 0xEE, 0x8C), (0x8A, 0x66, 0x10))
DIA = ((0x4C, 0xC8, 0xC0), (0xA0, 0xF4, 0xF0), (0x1A, 0x6E, 0x6A))
METEOR = ((0x4A, 0x4F, 0x72), (0x9A, 0xA0, 0xCC), (0x1E, 0x21, 0x33))
LEATH = ((0xA8, 0x6D, 0x3A), (0xC8, 0x92, 0x50), (0x5A, 0x38, 0x1E))
DARK = ((0x2A, 0x2A, 0x2A), (0x4E, 0x4E, 0x4E), (0x12, 0x12, 0x12))


def _branch(base, mid, hi, shade):
    return (base, hi, shade, mid)


def ingot(c, pal):
    base, hi, sh = pal
    c.rect(4, 6, 11, 9, sh)
    c.rect(4, 7, 11, 8, base)
    c.rect(4, 6, 11, 6, hi)
    c.rect(5, 7, 5, 8, hi)          # left-notch highlight
    c.rect(9, 8, 10, 8, sh)         # right-end shading


def gem(c, pal):
    base, hi, sh = pal
    for y, (lo, hi2, role) in enumerate([
        (7, 8, 'hi'), (6, 9, 'hi'), (5, 10, 'hi'), (4, 11, 'b'),
        (5, 10, 'b'), (6, 9, 'sh'), (7, 8, 'sh'), (8, 8, 'sh')], start=2):
        col = {'hi': hi, 'b': base, 'sh': sh}[role]
        for x in range(lo, hi2 + 1):
            c.set(x, y, col)
    c.line(6, 5, 8, 7, mid) if False else None
    # inner facet: brighter top-left half
    c.rect(5, 4, 8, 5, hi)
    c.rect(6, 6, 7, 6, base)
    c.set(8, 6, base); c.set(8, 7, sh)


# ------------------------------------------------------------- items -----
def draw(c, fn):
    fn(c)


def stick(c):
    base, hi, sh = WOOD
    c.rect(7, 1, 8, 14, base)
    c.rect(7, 1, 7, 14, sh)
    c.set(8, 1, hi); c.set(8, 8, hi)


def coal(c):
    c.disc(7, 7, 4, (0x1C, 0x1C, 0x20))
    c.disc(7, 7, 3, (0x30, 0x30, 0x36))
    c.rect(5, 4, 8, 5, (0x58, 0x58, 0x60))
    c.set(9, 6, (0x48, 0x48, 0x50))
    c.set(5, 9, (0x14, 0x14, 0x18))


def iron_ingot(c): ingot(c, STEEL)
def gold_ingot(c): ingot(c, GOLD)
def meteorite_ingot(c): ingot(c, METEOR)
def diamond(c): gem(c, DIA)
def emerald(c): gem(c, ((0x2F, 0xB2, 0x3F), (0x8A, 0xF0, 0x8A), (0x14, 0x6E, 0x20)))
def refined_iron(c): ingot(c, STEEL)


def leather(c):
    base, hi, sh = LEATH
    for y in range(2, 12):
        x0 = 3 + (12 - y) // 3
        for x in range(x0, x0 + 6):
            c.set(x, y, base)
        c.set(x0, y, sh)
        c.set(x0 + 5, y, sh)
    # stitching
    for y in range(3, 11):
        c.set(3, y, hi)
    c.set(4, 2, hi); c.set(5, 2, hi)


def string(c):
    c.line(4, 11, 12, 3, (0xE8, 0xE8, 0xE0))
    c.line(5, 12, 13, 4, (0xA8, 0xA8, 0xA0))
    c.set(7, 7, (0xF4, 0xF4, 0xEC))
    c.set(7, 8, (0x88, 0x88, 0x80))


def feather(c):
    c.thline(4, 12, 10, 5, (0xF0, 0xF0, 0xEF), 2)
    c.line(5, 13, 11, 6, (0xC0, 0xC0, 0xBE))
    c.line(3, 13, 4, 12, (0xB8, 0xB8, 0xB6))
    c.set(3, 12, (0x98, 0x98, 0x96))


def flint(c):
    base, hi, sh = DARK
    c.disc(7, 7, 4, sh)
    c.rect(5, 4, 9, 7, base)
    c.rect(5, 4, 7, 5, (0x6E, 0x6E, 0x7A))
    c.set(9, 3, hi); c.set(6, 9, (0x18, 0x18, 0x20))


def flint_and_steel(c):
    c.thline(3, 4, 9, 10, (0xE8, 0xE8, 0xF0), 2)
    c.line(4, 3, 8, 7, (0xFF, 0xFF, 0xFF))
    c.disc(7, 12, 2, (0x2E, 0x2E, 0x34))
    c.disc(11, 12, 2, (0x22, 0x22, 0x28))
    c.set(12, 10, (0xFF, 0x9A, 0x3C)); c.set(10, 11, (0xFF, 0xD0, 0x50)); c.set(9, 13, (0xFF, 0x9A, 0x3C))


def gunpowder(c):
    c.disc(6, 6, 2, (0x30, 0x30, 0x34)); c.disc(9, 8, 2, (0x40, 0x40, 0x44))
    c.disc(8, 4, 1, (0x24, 0x24, 0x28)); c.disc(11, 11, 2, (0x38, 0x38, 0x3C))
    c.disc(5, 10, 1, (0x28, 0x28, 0x2C)); c.disc(7, 12, 1, (0x34, 0x34, 0x38))


def wheat(c):
    c.rect(7, 6, 8, 14, (0x6A, 0x8A, 0x34))
    c.rect(7, 12, 7, 14, (0x57, 0x74, 0x2A))
    c.disc(7, 3, 2, (0xE8, 0xC4, 0x3A))
    c.disc(9, 4, 1, (0xE8, 0xC4, 0x3A))
    c.disc(6, 4, 1, (0xC9, 0xA6, 0x28))
    c.set(8, 2, (0xF2, 0xD8, 0x66)); c.set(6, 2, (0xC9, 0xA6, 0x28))
    c.set(7, 0, (0xE8, 0xC4, 0x3A)); c.set(8, 1, (0xF2, 0xD8, 0x66))


def egg(c):
    c.disc(7, 7, 2, (0xF2, 0xEE, 0xE0)); c.disc(8, 7, 2, (0xF2, 0xEE, 0xE0))
    c.set(7, 4, (0xF2, 0xEE, 0xE0)); c.set(8, 4, (0xE2, 0xDC, 0xC8))
    c.set(7, 9, (0xE2, 0xDC, 0xC8)); c.set(8, 9, (0xD8, 0xD2, 0xBE))
    c.set(9, 6, (0xD8, 0xD2, 0xBE))


def sugar(c):
    c.rect(6, 6, 10, 10, (0xF8, 0xF8, 0xF0))
    c.rect(7, 7, 9, 9, (0xFF, 0xFF, 0xFF))
    c.set(5, 8, (0xE0, 0xE0, 0xD8)); c.set(11, 7, (0xE0, 0xE0, 0xD8))
    c.set(8, 5, (0xF0, 0xF0, 0xE8)); c.set(7, 11, (0xC8, 0xC8, 0xC0))


def slime_ball(c):
    c.disc(7, 7, 3, (0x6E, 0xE8, 0x54))
    c.disc(8, 7, 2, (0x8A, 0xF0, 0x6E))
    c.set(6, 4, (0x4A, 0xC8, 0x3C)); c.set(10, 8, (0x3E, 0xA8, 0x32))
    c.set(7, 4, (0xB2, 0xF8, 0x96))


def shears(c):
    c.thline(3, 3, 8, 8, (0xC0, 0xC0, 0xC8), 2)
    c.thline(5, 3, 10, 8, (0x8A, 0x8A, 0x94), 2)
    for cx, cy in [(10, 11), (13, 13)]:
        c.disc(cx, cy, 2, (0xA8, 0xA8, 0xB0))
        c.disc(cx, cy, 1, (0xE8, 0xE8, 0xF0))


def bucket(c):
    base, hi, sh = STEEL
    c.rect(4, 4, 11, 4, base)          # rim
    c.rect(5, 5, 10, 10, base)         # body
    c.rect(6, 6, 9, 10, sh)
    c.rect(5, 11, 10, 11, sh)          # bottom
    c.set(4, 5, hi); c.set(11, 5, hi)
    c.line(6, 3, 9, 3, base)           # handle
    c.set(6, 2, base); c.set(9, 2, base)


def milk(c):
    bucket(c)
    c.rect(5, 4, 10, 6, (0xE8, 0xE8, 0xE0))
    c.rect(6, 5, 9, 8, (0xF8, 0xF8, 0xF0))


def bottle(c, liq, liq_hi):
    base, hi, sh = STEEL
    c.rect(7, 1, 8, 5, (0xD8, 0xE4, 0xE8))       # neck
    c.rect(6, 5, 9, 5, (0xC0, 0xCC, 0xD4))       # shoulder
    c.disc(7, 9, 3, (0xC8, 0xD4, 0xDC))
    c.disc(8, 9, 3, (0xC8, 0xD4, 0xDC))
    c.rect(5, 7, 10, 10, (0xC8, 0xD4, 0xDC))     # body glass
    c.rect(6, 7, 9, 10, liq)                     # liquid
    c.rect(6, 7, 8, 8, liq_hi)
    c.set(5, 6, hi); c.set(10, 11, sh)
    c.rect(6, 1, 7, 2, (0x8C, 0x4A, 0x24))       # cork


def water_bottle(c): bottle(c, (0x3C, 0x88, 0xC8), (0x7A, 0xC0, 0xEA))
def potion_healing(c): bottle(c, (0xB8, 0x28, 0x28), (0xE8, 0x5A, 0x5A))
def potion_speed(c): bottle(c, (0xD0, 0xA0, 0x20), (0xF4, 0xD8, 0x5A))
def potion_strength(c): bottle(c, (0x8A, 0x28, 0x20), (0xC8, 0x4A, 0x3A))
def potion_fire_resistance(c): bottle(c, (0xE0, 0x54, 0x10), (0xF8, 0x9A, 0x3C))


def resin(c):
    c.disc(8, 8, 3, (0xC8, 0x7A, 0x18))
    c.disc(8, 8, 2, (0xE8, 0xA0, 0x2E))
    c.set(7, 6, (0xFC, 0xC8, 0x5A))


def wax(c):
    c.rect(6, 5, 10, 10, (0xE4, 0xD0, 0x9A))
    c.rect(6, 5, 8, 6, (0xF4, 0xE4, 0xBE))
    c.rect(6, 10, 10, 10, (0xC4, 0xA8, 0x74))


def bread(c):
    base, hi, sh = ((0xD2, 0xA4, 0x58), (0xEA, 0xC4, 0x76), (0x8A, 0x64, 0x30))
    c.disc(7, 6, 3, base); c.disc(8, 6, 3, base)
    c.rect(5, 5, 10, 8, base)
    c.rect(4, 6, 11, 8, base)
    c.disc(7, 5, 3, hi); c.disc(8, 5, 3, hi)
    c.rect(5, 4, 10, 4, sh)
    c.rect(4, 5, 4, 7, sh); c.rect(11, 5, 11, 7, sh)
    c.rect(6, 9, 9, 9, sh)
    for x in range(5, 12, 2):
        c.set(x, 5, (0xF2, 0xD6, 0x8A))
    for x in range(4, 11, 2):
        c.set(x, 7, (0xA8, 0x7A, 0x3E))


def apple(c):
    c.disc(7, 8, 3, (0x9E, 0x24, 0x20)); c.disc(8, 8, 3, (0x9E, 0x24, 0x20))
    c.disc(8, 7, 3, (0xC8, 0x34, 0x2E))
    c.set(6, 5, (0xE0, 0x5A, 0x44)); c.set(7, 5, (0xE8, 0x64, 0x4C))
    c.set(5, 7, (0x7A, 0x18, 0x16)); c.set(8, 10, (0x7A, 0x18, 0x16))
    c.rect(8, 3, 8, 4, (0x6A, 0x44, 0x24)); c.set(7, 3, (0x6A, 0x44, 0x24))
    c.set(8, 2, (0x4A, 0x7A, 0x24)); c.set(9, 2, (0x4A, 0x7A, 0x24)); c.set(10, 2, (0x6A, 0x9A, 0x34))


def meat_oval(c, base, hi, sh, fat=None, grills=None, bone=None):
    c.disc(8, 7, 4, base); c.disc(9, 7, 3, base)
    c.rect(5, 4, 11, 9, base)
    c.set(5, 5, hi); c.set(6, 4, hi); c.set(7, 3, hi)
    c.set(5, 9, sh); c.set(11, 8, sh); c.set(10, 9, sh)
    if fat:
        for (fx, fy) in fat:
            c.set(fx, fy, (0xF4, 0xE4, 0xD0))
    if grills:
        for (gx, gy) in grills:
            c.set(gx, gy, (0x3A, 0x1C, 0x0C))
    if bone:
        for (bx, by) in bone:
            c.set(bx, by, (0xE0, 0xD8, 0xC8))


def raw_pork(c):
    meat_oval(c, (0xF0, 0x9A, 0x9A), (0xFA, 0xC8, 0xC0), (0xB2, 0x5E, 0x5E),
              fat=[(7, 5), (8, 6), (10, 4)], bone=[(5, 8), (6, 8), (6, 9)])


def cooked_pork(c):
    meat_oval(c, (0xCC, 0x8A, 0x4A), (0xEA, 0xB8, 0x70), (0x7A, 0x48, 0x22),
              grills=[(6, 5), (8, 7), (10, 5)], bone=[(5, 8), (6, 8)])


def raw_beef(c):
    meat_oval(c, (0xB8, 0x34, 0x30), (0xE0, 0x5A, 0x4E), (0x70, 0x1C, 0x1A),
              fat=[(7, 4), (9, 6), (11, 5), (6, 7)], bone=[(5, 8), (6, 8), (6, 9)])


def cooked_beef(c):
    meat_oval(c, (0x8A, 0x4E, 0x28), (0xC8, 0x78, 0x42), (0x4A, 0x28, 0x14),
              grills=[(6, 5), (8, 6), (10, 4), (7, 8), (9, 7)], bone=[(5, 8), (6, 8)])


def drumstick(c, base, hi, sh):
    c.disc(8, 5, 3, base); c.disc(9, 4, 2, base)
    c.rect(6, 3, 10, 7, base)
    c.set(7, 3, hi); c.set(6, 4, hi)
    c.set(5, 6, sh); c.set(10, 7, sh)
    c.rect(8, 8, 8, 11, (0xE0, 0xD0, 0xB8))     # bone shaft
    c.set(7, 8, (0xC0, 0xB0, 0x98)); c.set(9, 8, (0xC0, 0xB0, 0x98))
    c.rect(7, 11, 9, 11, (0xC0, 0xB0, 0x98))     # knob


def raw_chicken(c):
    drumstick(c, (0xF0, 0xC4, 0xB0), (0xFA, 0xE2, 0xD4), (0xB8, 0x7E, 0x64))


def cooked_chicken(c):
    drumstick(c, (0xD8, 0x9A, 0x46), (0xF2, 0xC8, 0x6E), (0x7E, 0x4E, 0x1E))


def mutton(c, base, hi, sh):
    c.disc(7, 7, 3, base); c.disc(8, 7, 3, base)
    c.rect(5, 5, 10, 8, base)
    c.set(5, 5, hi); c.set(6, 4, hi)
    c.set(5, 8, sh); c.set(10, 8, sh); c.set(9, 5, sh)


def raw_mutton(c):
    mutton(c, (0xE8, 0x6E, 0x7C), (0xF8, 0xA0, 0xAA), (0xA0, 0x3C, 0x4C))


def cooked_mutton(c):
    mutton(c, (0xA8, 0x5E, 0x34), (0xD8, 0x8A, 0x52), (0x5A, 0x30, 0x1A))


def rotten_flesh(c):
    c.disc(7, 7, 4, (0x6E, 0x9A, 0x4A)); c.disc(8, 7, 3, (0x78, 0xA4, 0x52))
    c.disc(7, 4, 2, (0x8A, 0xB8, 0x5E))
    c.set(5, 6, (0x4E, 0x6E, 0x36)); c.set(9, 9, (0x4E, 0x6E, 0x36))
    c.set(6, 8, (0x3C, 0x5A, 0x2A)); c.set(8, 6, (0xA2, 0xCC, 0x72))


def bone(c):
    # top bone (diagonal)
    c.thline(5, 4, 10, 8, (0xE8, 0xE0, 0xD0), 2)
    c.disc(5, 4, 2, (0xE8, 0xE0, 0xD0)); c.disc(5, 4, 1, (0xFA, 0xF4, 0xE8))
    c.disc(10, 8, 2, (0xE8, 0xE0, 0xD0)); c.disc(10, 8, 1, (0xFA, 0xF4, 0xE8))
    c.line(5, 4, 10, 8, (0xC0, 0xB4, 0xA0))
    # bottom bone (diagonal)
    c.thline(6, 13, 11, 9, (0xD8, 0xD0, 0xC0), 2)
    c.disc(6, 13, 2, (0xD8, 0xD0, 0xC0)); c.disc(11, 9, 2, (0xD8, 0xD0, 0xC0))
    c.line(6, 13, 11, 9, (0xB0, 0xA4, 0x90))


def spider_eye(c):
    c.disc(8, 8, 4, (0xA8, 0x1C, 0x1C)); c.disc(9, 8, 3, (0xC8, 0x2A, 0x2A))
    c.set(6, 6, (0xF0, 0x54, 0x40))
    c.disc(8, 8, 1, (0x1E, 0x08, 0x08))
    c.line(6, 6, 9, 4, (0x6A, 0x10, 0x10)); c.line(7, 10, 4, 11, (0x6A, 0x10, 0x10))


def bow(c):
    base, hi, sh = WOOD
    # curved limb: bulge right in the middle
    for y in range(0, 16):
        t = y / 15.0
        bulge = round(3.5 * math.sin(math.pi * t))
        x = 3 + bulge
        c.set(x, y, base); c.set(x + 1, y, base)
        c.set(x, y, hi)
    c.set(2, 0, sh); c.set(3, 0, sh); c.set(1, 15, sh); c.set(2, 15, sh)
    # string
    c.line(14, 1, 14, 14, (0xF0, 0xF0, 0xE8))
    c.set(13, 1, (0xC8, 0xC8, 0xC0)); c.set(13, 14, (0xC8, 0xC8, 0xC0))
    # tips
    c.line(2, 0, 3, 0, (0x6A, 0x44, 0x24)); c.line(2, 15, 3, 15, (0x6A, 0x44, 0x24))


def arrow(c):
    # shaft
    c.thline(5, 14, 11, 6, (0xB0, 0x78, 0x3E), 2)
    c.line(5, 14, 11, 6, (0xE0, 0xA8, 0x5A))
    # head
    c.line(12, 2, 12, 5, (0xC8, 0xC8, 0xD0)); c.line(11, 3, 11, 6, (0x8A, 0x8A, 0x94))
    c.set(13, 3, (0xEE, 0xEE, 0xF2))
    # fletching
    c.rect(3, 13, 5, 14, (0xF0, 0xF0, 0xEC))
    c.rect(5, 10, 6, 12, (0xC8, 0xC8, 0xC0))


# ----------------------------------------------------------------- armor --
def helmet(c, base, hi, sh):
    c.set(7, 1, base); c.set(8, 1, base)
    c.set(6, 2, base); c.set(7, 2, hi); c.set(8, 2, hi); c.set(9, 2, base)
    for y in range(3, 6):
        c.rect(4, y, 11, y, base)
    c.set(4, 3, hi); c.set(4, 4, hi)
    c.rect(5, 3, 6, 3, hi)
    c.rect(6, 4, 9, 4, sh)          # face shadow
    c.rect(4, 6, 11, 6, sh)
    c.set(3, 5, base); c.set(12, 5, base); c.set(3, 6, sh); c.set(12, 6, sh)


def chestplate(c, base, hi, sh):
    for y in range(2, 6):
        c.rect(4, y, 11, y, base)
    c.rect(5, 6, 10, 7, base)
    c.rect(4, 8, 11, 8, base)
    c.set(4, 2, hi); c.set(4, 3, hi); c.set(5, 2, hi)
    c.set(11, 8, sh); c.set(10, 8, sh)
    c.set(6, 4, hi); c.set(8, 5, hi)
    c.set(7, 3, sh); c.set(7, 7, sh)


def leggings(c, base, hi, sh):
    c.rect(5, 2, 10, 4, base)       # belt
    c.set(5, 2, hi); c.set(6, 2, hi)
    c.set(10, 4, sh)
    c.rect(5, 5, 6, 11, base)       # left leg
    c.rect(9, 5, 10, 11, base)      # right leg
    c.set(5, 5, hi); c.set(6, 5, hi); c.set(5, 6, hi)
    c.set(10, 5, sh); c.set(9, 11, sh); c.set(10, 11, sh)
    c.rect(4, 9, 4, 11, base); c.rect(11, 9, 11, 11, base)
    c.rect(5, 12, 6, 12, sh); c.rect(9, 12, 10, 12, sh)


def boots(c, base, hi, sh):
    c.rect(4, 3, 6, 8, base)        # left boot
    c.rect(9, 3, 11, 8, base)       # right boot
    c.set(4, 3, hi); c.set(5, 3, hi); c.set(4, 4, hi)
    c.set(11, 3, sh); c.set(11, 4, sh)
    c.rect(4, 9, 7, 9, base)        # soles
    c.rect(8, 9, 11, 9, base)
    c.rect(4, 10, 7, 10, sh); c.rect(8, 10, 11, 10, sh)
    c.set(3, 8, base); c.set(12, 8, base)


ARMS = {
    'leather': LEATH, 'iron': STEEL, 'diamond': DIA, 'meteorite': METEOR,
}
ARM_SHAPES = {
    'helmet': helmet, 'chestplate': chestplate, 'leggings': leggings, 'boots': boots,
}


def build(name, fn):
    c = Canvas()
    fn(c)
    return render(c)


def main():
    os.makedirs(SRC, exist_ok=True)

    items = [
        ('stick', stick), ('coal', coal), ('iron_ingot', iron_ingot),
        ('gold_ingot', gold_ingot), ('meteorite_ingot', meteorite_ingot),
        ('diamond', diamond), ('emerald', emerald), ('leather', leather),
        ('string', string), ('feather', feather), ('flint', flint),
        ('flint_and_steel', flint_and_steel), ('gunpowder', gunpowder),
        ('wheat', wheat), ('egg', egg), ('sugar', sugar), ('slime_ball', slime_ball),
        ('shears', shears), ('bucket', bucket), ('milk', milk),
        ('resin', resin), ('wax', wax),
        ('water_bottle', water_bottle), ('potion_healing', potion_healing),
        ('potion_speed', potion_speed), ('potion_strength', potion_strength),
        ('potion_fire_resistance', potion_fire_resistance),
        ('bread', bread), ('apple', apple),
        ('raw_pork', raw_pork), ('cooked_pork', cooked_pork),
        ('raw_beef', raw_beef), ('cooked_beef', cooked_beef),
        ('raw_chicken', raw_chicken), ('cooked_chicken', cooked_chicken),
        ('raw_mutton', raw_mutton), ('cooked_mutton', cooked_mutton),
        ('rotten_flesh', rotten_flesh), ('bone', bone), ('spider_eye', spider_eye),
        ('bow', bow), ('arrow', arrow),
    ]

    def armor_item(pal, shape):
        base, hi, sh = ARMS[pal]
        return (f'{pal}_{shape}', lambda c: ARM_SHAPES[shape](c, base, hi, sh))

    for pal in ARMS:
        for shp in ARM_SHAPES:
            items.append(armor_item(pal, shp))

    written = []
    for name, fn in items:
        grid = build(name, fn)
        img = upscale(to_image(grid))
        img.save(os.path.join(SRC, f'{name}.png'))
        written.append((name, img))

    cols = 10
    rows = (len(written) + cols - 1) // cols
    tile = S * UP
    sheet = Image.new('RGBA', (cols * tile, rows * tile), (20, 20, 28, 255))
    from PIL import ImageDraw
    d = ImageDraw.Draw(sheet)
    for i, (name, img_) in enumerate(written):
        x, y = (i % cols) * tile, (i // cols) * tile
        sheet.paste(img_, (x, y), img_)
        d.text((x + 2, y + tile - 13), name, fill=(255, 255, 255, 255))
    sheet.save(OUT)
    print(f'{len(written)} textures written; sheet -> {OUT}')


def upscale(img):
    return img.resize((S * UP, S * UP), Image.NEAREST)


if __name__ == '__main__':
    main()