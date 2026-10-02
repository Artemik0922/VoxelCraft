package com.voxelgame.ui2;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * "Workshop" material pack, generated once at startup from code. Nothing is
 * loaded from disk.
 *
 * The wood boards are baked as NEUTRAL GRAIN (greyscale around a mid tone)
 * and coloured at draw time by tinting with the active finish's base colour,
 * so one texture serves all eight wood finishes - the same trick the old
 * glass pack used with white textures. Paper and brass are baked in their
 * true colours; they do not change with the finish.
 *
 * Everything uploads GL_NEAREST and is drawn at integer GUI-pixel
 * coordinates, so one texel covers exactly scale*scale screen pixels.
 */
public final class UiMaterials {

    public static UiMaterials INSTANCE;

    /** Nine-slice board (plank button / panel): greyscale grain, tinted. */
    public final int plank;
    public final int card;        // deep 9-slice backing for cards, tinted
    public final int wall;        // tiled dark plank wall, tinted (32px)
    /** Kraft paper 9-slice with a deckled edge, true colours. */
    public final int paper;
    /** Dark sunken slot sockets, 1:1 at 18px. */
    public final int slotSocket;
    public final int slotSocketHover;
    /** Brass fittings. */
    public final int knob;        // 10x10 slider/toggle knob
    public final int rivet;       // 5x5 corner rivet
    /** Status sprites, 1:1. */
    public final int heartEmpty;
    public final int heartHalf;
    public final int heartFull;
    public final int drumstickEmpty;
    public final int drumstickHalf;
    public final int drumstickFull;
    public final int bubble;
    public final int crosshair;
    /** 1x1 white for flat fills. */
    public final int white;

    public UiMaterials() {
        INSTANCE = this;

        plank = makePlank();
        card = makeCard();
        wall = makeWall();
        paper = makePaper();
        slotSocket = makeSlotSocket(false);
        slotSocketHover = makeSlotSocket(true);
        knob = makeKnob();
        rivet = makeRivet();

        heartEmpty = makeHeart(0);
        heartHalf = makeHeart(1);
        heartFull = makeHeart(2);
        drumstickEmpty = makeDrumstick(0);
        drumstickHalf = makeDrumstick(1);
        drumstickFull = makeDrumstick(2);
        bubble = makeBubble();
        crosshair = makeCrosshair();

        white = upload(new int[]{0xFFFFFFFF}, 1, 1, false);
    }

    // ------------------------------------------------------------------
    // Wood - greyscale grain, tinted with the finish colour at draw time
    // ------------------------------------------------------------------

    /**
     * 24x24 nine-slice board. The interior carries vertical grain lines that
     * stretch into long fibres when the centre is widened; the border frame
     * adds the top-light/bottom-dark bevel and a dark outline.
     */
    private int makePlank() {
        int s = UiTheme.WIDGET_TEX;
        int[] p = new int[s * s];
        Random rnd = new Random(9101);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                int v;
                boolean edge = x == 0 || y == 0 || x == s - 1 || y == s - 1;
                boolean bevelLight = (x == 1 || y == 1) && !edge;
                boolean bevelDark = (x == s - 2 || y == s - 2) && !edge;
                if (edge) {
                    v = 45;                                   // near-black outline
                } else if (bevelLight) {
                    v = 248;                                  // light catches the top edge
                } else if (bevelDark) {
                    v = 105;                                  // shadow under the bottom edge
                } else {
                    v = 205;
                    boolean centre = x >= 7 && x < s - 7;
                    if (centre) {
                        // Stretched centre: constant vertical grain only -
                        // anything row-dependent smears into wide blobs
                        int col = (x * 7) % 13;
                        if (col == 0) v -= 26;
                        else if (col == 7) v -= 14;
                    } else {
                        // Border zone renders 1:1: wobble and flecks live here
                        int col = (x * 7 + (y / 5) * 3) % 13;
                        if (col == 0) v -= 26;
                        else if (col == 7) v -= 14;
                        if (((x * 31 + y * 17) % 23) == 0) v -= 10;
                        if (rnd.nextInt(48) == 0) v -= 8;
                    }
                }
                p[y * s + x] = grey(v, 255);
            }
        }
        return upload(p, s, s, false);
    }

    /**
     * 32x32 nine-slice card backing: darker, cooler board used behind menu
     * content. Same greyscale-grain trick, heavier frame.
     */
    private int makeCard() {
        int s = 32;
        int[] p = new int[s * s];
        Random rnd = new Random(4404);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                int v;
                boolean edge = x < 2 && y < 2 || x >= s - 2 && y < 2
                    || x < 2 && y >= s - 2 || x >= s - 2 && y >= s - 2;
                if (x == 0 || y == 0 || x == s - 1 || y == s - 1) {
                    v = 35;
                } else if (x == 1 || y == 1) {
                    v = 235;
                } else if (x == s - 2 || y == s - 2) {
                    v = 90;
                } else if (edge) {
                    v = 60; // corner rivet shadow ring
                } else {
                    v = 170;
                    boolean centre = x >= 9 && x < s - 9;
                    if (centre) {
                        int col = (x * 5) % 11;
                        if (col == 0) v -= 22;
                    } else {
                        int col = (x * 5 + (y / 7) * 2) % 11;
                        if (col == 0) v -= 22;
                        if (((x * 13 + y * 29) % 31) == 0) v -= 8;
                        if (rnd.nextInt(64) == 0) v -= 6;
                    }
                }
                p[y * s + x] = grey(v, 255);
            }
        }
        return upload(p, s, s, false);
    }

    /**
     * 32x32 TILED plank wall for full-screen menu backdrops (drawn with
     * drawTiled, never stretched, so free-form grain and joints are fine).
     */
    private int makeWall() {
        int s = 32;
        int[] p = new int[s * s];
        Random rnd = new Random(777);
        for (int y = 0; y < s; y++) {
            int row = y / 8; // horizontal planks, 8px tall
            for (int x = 0; x < s; x++) {
                int v = 200 + rnd.nextInt(12) - 6;
                // horizontal fibre
                if (((x * 3 + row * 5 + (y % 8)) % 9) == 0) v -= 22;
                if (((x * 11 + y) % 17) == 0) v -= 10;
                // plank joint at the bottom of each row, staggered end joint
                if (y % 8 == 7) v -= 70;
                if (x == (row % 2 == 0 ? 8 : 24) && y % 8 != 7) v -= 55;
                p[y * s + x] = grey(v, 255);
            }
        }
        return upload(p, s, s, true);
    }

    // ------------------------------------------------------------------
    // Paper
    // ------------------------------------------------------------------

    /**
     * 24x24 kraft-paper nine-slice with a deckled edge (the outermost pixels
     * drop alpha in a few places, so cards read as torn note paper).
     */
    private int makePaper() {
        int s = UiTheme.PAPER_TEX;
        int[] p = new int[s * s];
        Random rnd = new Random(2027);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                int r = 0xE8, g = 0xD9, b = 0xB8;
                boolean edge = x == 0 || y == 0 || x == s - 1 || y == s - 1;
                boolean rim = x <= 1 || y <= 1 || x >= s - 2 || y >= s - 2;
                if (edge) {
                    r -= 60; g -= 58; b -= 54;
                } else if (rim) {
                    r -= 28; g -= 26; b -= 24;
                } else if (x >= 7 && x < s - 7) {
                    // Stretched centre: constant faint vertical fibres only -
                    // random flecks would smear into wide streaks
                    if ((x * 5) % 9 == 0) { r -= 5; g -= 5; b -= 4; }
                } else {
                    // Border zone renders 1:1, fibre flecks are safe here
                    int fleck = rnd.nextInt(16);
                    if (fleck == 0) { r -= 12; g -= 12; b -= 10; }
                    else if (fleck == 1) { r += 8; g += 8; b += 6; }
                }
                int a = 255;
                // deckled edge: nibble a few edge pixels away
                if (edge && rnd.nextInt(6) == 0) a = 0;
                p[y * s + x] = (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
            }
        }
        return upload(p, s, s, false);
    }

    // ------------------------------------------------------------------
    // Slots and fittings
    // ------------------------------------------------------------------

    /** 18x18 sunken socket: inner shadow top/left, wood rim bottom/right. */
    private int makeSlotSocket(boolean hover) {
        int s = UiTheme.SLOT_SIZE;
        int[] p = new int[s * s];
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                boolean rim = x == 0 || y == 0 || x == s - 1 || y == s - 1;
                int v;
                if (rim) {
                    v = 120;                     // wood rim around the hole
                } else if (x == 1 || y == 1) {
                    v = 38;                      // deep inner shadow
                } else if (x == s - 2 || y == s - 2) {
                    v = 96;                      // soft bottom edge
                } else {
                    v = 58;                      // dark interior
                }
                if (hover && !rim && x >= 2 && y >= 2 && x < s - 2 && y < s - 2) {
                    v += 26;                     // brass glint wash on hover
                }
                p[y * s + x] = grey(v, 255);
            }
        }
        return upload(p, s, s, false);
    }

    /** 10x10 circular brass knob with a top-left highlight. */
    private int makeKnob() {
        int s = 10;
        int[] p = new int[s * s];
        float cx = (s - 1) / 2f, cy = (s - 1) / 2f;
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float dx = x - cx, dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                int argb;
                if (dist > 4.6f) {
                    argb = 0;
                } else if (dist > 3.9f) {
                    argb = 0xFF8A6420;           // dark rim
                } else if (dx < -0.5f && dy < -0.5f && dist < 3.2f) {
                    argb = 0xFFEDD9A0;           // highlight
                } else {
                    argb = 0xFFC9973B;           // brass body
                }
                p[y * s + x] = argb;
            }
        }
        return upload(p, s, s, false);
    }

    /** 5x5 brass rivet with a bright head. */
    private int makeRivet() {
        int[] p = {
            0, 0xFF8A6420, 0xFF8A6420, 0xFF8A6420, 0,
            0xFF8A6420, 0xFFEDD9A0, 0xFFEDD9A0, 0xFFC9973B, 0xFF8A6420,
            0xFF8A6420, 0xFFEDD9A0, 0xFFF6E8C0, 0xFFC9973B, 0xFF8A6420,
            0xFF8A6420, 0xFFC9973B, 0xFFC9973B, 0xFFB0822F, 0xFF8A6420,
            0, 0xFF8A6420, 0xFF8A6420, 0xFF8A6420, 0,
        };
        return upload(p, 5, 5, false);
    }

    // ------------------------------------------------------------------
    // Status sprites (string masks, '#' = ink, 'x' = fill, 'o' = mid)
    // ------------------------------------------------------------------

    private static final String[] HEART_MASK = {
        " ## ## ",
        "#xxxxx#",
        "#xxxxx#",
        "#xxxxx#",
        " #xxx# ",
        "  #x#  ",
        "   #   ",
    };

    private int makeHeart(int mode) {
        // 9x9: 1px margin around the 7x7 mask
        int s = 9;
        int[] p = new int[s * s];
        for (int y = 0; y < HEART_MASK.length; y++) {
            for (int x = 0; x < HEART_MASK[y].length(); x++) {
                char c = HEART_MASK[y].charAt(x);
                if (c == ' ') continue;
                int argb;
                if (c == '#') {
                    argb = 0xFF5A3A20;                       // carved wood outline
                } else {
                    boolean filled = mode == 2 || (mode == 1 && x < 4);
                    argb = filled ? (y < 2 ? 0xFFE8764A : 0xFFC4552F) : 0xFF3A2A1A;
                }
                p[(y + 1) * s + (x + 1)] = argb;
            }
        }
        return upload(p, s, s, false);
    }

    private static final String[] DRUMSTICK_MASK = {
        "   ###  ",
        "  #xxx# ",
        "  #xxx# ",
        " #xxx#  ",
        "#ox#    ",
        "#o#     ",
        "##      ",
    };

    private int makeDrumstick(int mode) {
        int s = 9;
        int[] p = new int[s * s];
        for (int y = 0; y < DRUMSTICK_MASK.length; y++) {
            for (int x = 0; x < DRUMSTICK_MASK[y].length(); x++) {
                char c = DRUMSTICK_MASK[y].charAt(x);
                if (c == ' ') continue;
                int argb;
                if (c == '#') {
                    argb = 0xFF5A3A20;
                } else if (c == 'o') {
                    argb = 0xFFE8D9B8;                   // bone
                } else {
                    boolean filled = mode == 2 || (mode == 1 && y < 3);
                    argb = filled ? 0xFFB8712F : 0xFF3A2A1A;
                }
                p[(y + 1) * s + (x + 1)] = argb;
            }
        }
        return upload(p, s, s, false);
    }

    private int makeBubble() {
        int s = 9;
        int[] p = new int[s * s];
        float c = 4f;
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float d = (float) Math.sqrt((x - c) * (x - c) + (y - c) * (y - c));
                int argb = 0;
                if (d < 3.6f) {
                    argb = 0xFFBFE3F2;
                    if (x - c < -1 && y - c < -1 && d < 2.4f) argb = 0xFFF2FBFF;
                } else if (d < 4.4f) {
                    argb = 0xFF6FA8C8;
                }
                p[y * s + x] = argb;
            }
        }
        return upload(p, s, s, false);
    }

    /** 16x16 thin plus with a dark outline so it reads on any scene. */
    private int makeCrosshair() {
        int s = UiTheme.CROSSHAIR_SIZE;
        int[] p = new int[s * s];
        for (int i = 0; i < s; i++) {
            // horizontal stroke
            p[7 * s + i] = i >= 5 && i <= 10 ? 0xFFF2E6C8 : 0x50261A10;
            p[8 * s + i] = i >= 5 && i <= 10 ? 0xFFF2E6C8 : 0x50261A10;
            // vertical stroke
            p[i * s + 7] = i >= 5 && i <= 10 ? 0xFFF2E6C8 : 0x50261A10;
            p[i * s + 8] = i >= 5 && i <= 10 ? 0xFFF2E6C8 : 0x50261A10;
        }
        return upload(p, s, s, false);
    }

    // ------------------------------------------------------------------
    // GL plumbing
    // ------------------------------------------------------------------

    private static int grey(int v, int a) {
        v = clamp(v);
        return (clamp(a) << 24) | (v << 16) | (v << 8) | v;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static int upload(int[] pixels, int w, int h, boolean repeat) {
        ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
        for (int p : pixels) {
            buf.put((byte) ((p >> 16) & 0xFF));
            buf.put((byte) ((p >> 8) & 0xFF));
            buf.put((byte) (p & 0xFF));
            buf.put((byte) ((p >>> 24) & 0xFF));
        }
        buf.flip();

        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        int wrap = repeat ? GL_REPEAT : GL_CLAMP_TO_EDGE;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap);
        glBindTexture(GL_TEXTURE_2D, 0);

        MemoryUtil.memFree(buf);
        return id;
    }

    public void cleanup() {
        glDeleteTextures(new int[]{
            plank, card, wall, paper, slotSocket, slotSocketHover,
            knob, rivet, heartEmpty, heartHalf, heartFull,
            drumstickEmpty, drumstickHalf, drumstickFull, bubble, crosshair, white,
        });
    }
}
