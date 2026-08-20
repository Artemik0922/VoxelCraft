package com.voxelgame.rendering.texture;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Procedural texture generators for new biome blocks.
 * Each produces a 16x16 BufferedImage.
 */
public final class BiomeTextures {

    private BiomeTextures() {}

    private static BufferedImage create() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    private static void fillBackground(BufferedImage img, Color c) {
        for (int x = 0; x < 16; x++)
            for (int y = 0; y < 16; y++)
                img.setRGB(x, y, c.getRGB());
    }

    private static int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int hex(int hex) {
        return 0xFF000000 | hex;
    }

    // --- Autumn leaves: orange noise with rare red pixels, cutout ---
    public static BufferedImage autumn_leaves() {
        BufferedImage img = create();
        Random r = new Random(42);
        int orange1 = hex(0xB0541F), orange2 = hex(0xD97F26);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                float t = r.nextFloat();
                int c = lerpColor(orange1, orange2, t);
                if (r.nextFloat() < 0.05) c = hex(0xA02010); // rare red
                if (r.nextFloat() < 0.35) c = 0; // holes
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Cherry leaves: pink noise, cutout ---
    public static BufferedImage cherry_leaves() {
        BufferedImage img = create();
        Random r = new Random(43);
        int pink1 = hex(0xE8B7C8), pink2 = hex(0xF2D0DA);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                float t = r.nextFloat();
                int c = lerpColor(pink1, pink2, t);
                if (r.nextFloat() < 0.3) c = 0; // holes
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Lavender: transparent bg, green stem, purple flower spike ---
    public static BufferedImage lavender() {
        BufferedImage img = create();
        fillBackground(img, new Color(0, 0, 0, 0));
        // Stem
        for (int y = 10; y < 16; y++) img.setRGB(7, y, hex(0x4A7A2A));
        img.setRGB(8, 11, hex(0x4A7A2A));
        // Flower spike 3x5
        for (int x = 6; x <= 8; x++) {
            for (int y = 4; y <= 8; y++) {
                int c = hex(0x7E4BB5);
                if (y == 4) c = hex(0xE0C8F0); // light tips
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Crystal: purple gradient with light veins, emissive ---
    public static BufferedImage crystal() {
        BufferedImage img = create();
        int dark = hex(0x5E2A8C), light = hex(0x9B59D0);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                float t = (x + y) / 32.0f;
                int c = lerpColor(dark, light, t);
                img.setRGB(x, y, c);
            }
        }
        // Light veins
        int vein = hex(0xD9B3FF);
        for (int x = 2; x < 14; x += 3) {
            for (int y = 0; y < 16; y++) {
                img.setRGB(x, y, vein);
                if (y % 4 == 0) img.setRGB(x + 1, y, vein);
            }
        }
        return img;
    }

    // --- Basalt: dark gray vertical stripes + speckle ---
    public static BufferedImage basalt() {
        BufferedImage img = create();
        Random r = new Random(44);
        int d1 = hex(0x2A2A2E), d2 = hex(0x4A4A50);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int c = (x % 3 == 0) ? d2 : d1;
                if (r.nextFloat() < 0.1) c = hex(0x1A1A1E);
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Salt: near-white with rare gray speckles, few shiny pixels ---
    public static BufferedImage salt() {
        BufferedImage img = create();
        Random r = new Random(45);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int c = hex(0xE8E8E4);
                if (r.nextFloat() < 0.05) c = hex(0xA0A09C); // gray speckle
                if (r.nextFloat() < 0.01) c = hex(0xFFFFFF); // shiny
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Ash: light gray noise with dark speckles ---
    public static BufferedImage ash() {
        BufferedImage img = create();
        Random r = new Random(46);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int c = hex(0x9A9A9A);
                if (r.nextFloat() < 0.15) c = hex(0x6A6A6A);
                if (r.nextFloat() < 0.05) c = hex(0x4A4A4A);
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Charred log: near-black brown with deep cracks ---
    public static BufferedImage charred_log() {
        BufferedImage img = create();
        Random r = new Random(47);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int c = hex(0x1F1713);
                if (r.nextFloat() < 0.15) c = hex(0x0F0A08);
                if (x % 4 == 0 || y % 5 == 0) c = hex(0x0A0503);
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Charred log top: dark rings ---
    public static BufferedImage charred_log_top() {
        BufferedImage img = create();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int dx = x - 7, dy = y - 7;
                int dist = (int) Math.sqrt(dx * dx + dy * dy);
                int c = (dist % 3 == 0) ? hex(0x0A0503) : hex(0x1F1713);
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Petrified log: gray bark pattern ---
    public static BufferedImage petrified_log() {
        BufferedImage img = create();
        Random r = new Random(48);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int c = hex(0x7F7F7F);
                if (r.nextFloat() < 0.2) c = hex(0x5F5F5F);
                if (x % 3 == 0) c = hex(0x6F6F6F);
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Petrified log top: gray rings ---
    public static BufferedImage petrified_log_top() {
        BufferedImage img = create();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int dx = x - 7, dy = y - 7;
                int dist = (int) Math.sqrt(dx * dx + dy * dy);
                int c = (dist % 3 == 0) ? hex(0x5F5F5F) : hex(0x7F7F7F);
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Blue ice: blue with white diagonal strokes ---
    public static BufferedImage blue_ice() {
        BufferedImage img = create();
        int b1 = hex(0x6AA7E8), b2 = hex(0xB8DCFA);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int c = ((x + y) % 4 == 0) ? hex(0xFFFFFF) : lerpColor(b1, b2, (x + y) / 32.0f);
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Mycelium top: dirt noise with purple-gray speckles ---
    public static BufferedImage mycelium_top() {
        BufferedImage img = create();
        Random r = new Random(49);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int c = hex(0x8B6914);
                if (r.nextFloat() < 0.3) c = hex(0x6E5A6E);
                if (r.nextFloat() < 0.15) c = hex(0x5A4A5A);
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Terracotta: horizontal stripes ---
    public static BufferedImage terracotta() {
        BufferedImage img = create();
        int[] colors = {hex(0xB0522B), hex(0xD97F45), hex(0x8C3F1F)};
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int c = colors[y % 3];
                if (y % 4 == 0) c = hex(0xE8C8A0); // light line
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Red sand: orange sand noise ---
    public static BufferedImage red_sand() {
        BufferedImage img = create();
        Random r = new Random(50);
        int s1 = hex(0xC06A2A), s2 = hex(0xE08A3A);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int c = r.nextFloat() > 0.5 ? s1 : s2;
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Cactus side: green vertical ribs + spine dots ---
    public static BufferedImage cactus_side() {
        BufferedImage img = create();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int c = hex(0x3B7A1F);
                if (x % 4 == 0) c = hex(0x2A5A15); // ribs
                if ((x == 0 || x == 15) && y % 3 == 0) c = hex(0xE8E840); // spines
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Lily pad: green round leaf with notch, transparent corners ---
    public static BufferedImage lily_pad() {
        BufferedImage img = create();
        fillBackground(img, new Color(0, 0, 0, 0));
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int dx = x - 7, dy = y - 7;
                int dist = dx * dx + dy * dy;
                if (dist < 25 && !(x > 7 && y < 4)) { // round with notch
                    img.setRGB(x, y, hex(0x2D6B1E));
                }
            }
        }
        return img;
    }

    // --- Sunflower: stem + yellow head with dark center ---
    public static BufferedImage sunflower() {
        BufferedImage img = create();
        fillBackground(img, new Color(0, 0, 0, 0));
        // Stem
        for (int y = 8; y < 16; y++) img.setRGB(7, y, hex(0x4A7A2A));
        // Head 6x6
        for (int x = 5; x <= 10; x++) {
            for (int y = 2; y <= 7; y++) {
                int c = hex(0xFCEE4B);
                if (x >= 7 && x <= 8 && y >= 4 && y <= 5) c = hex(0x5A3A10);
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    // --- Vine: 3-4 vertical green strands with many holes ---
    public static BufferedImage vine() {
        BufferedImage img = create();
        fillBackground(img, new Color(0, 0, 0, 0));
        Random r = new Random(51);
        for (int x = 2; x < 14; x += 3) {
            for (int y = 0; y < 16; y++) {
                if (r.nextFloat() < 0.3) continue; // holes
                img.setRGB(x, y, hex(0x2D6B1E));
                if (r.nextFloat() < 0.5) img.setRGB(x + 1, y, hex(0x3A8A2A));
            }
        }
        return img;
    }
}
