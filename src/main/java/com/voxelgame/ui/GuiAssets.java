package com.voxelgame.ui;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * Every GUI texture, generated once at startup from pixel masks written in
 * code. Nothing is loaded from disk and no original artwork is involved.
 *
 * Textures are uploaded with GL_NEAREST and are meant to be drawn at integer
 * coordinates multiplied by the GUI scale, so one source texel always covers
 * exactly scale*scale screen pixels.
 */
public class GuiAssets {

    // Classic GUI palette
    public static final int SLOT_FILL      = 0xFF8B8B8B;
    public static final int SLOT_SHADOW    = 0xFF373737;
    public static final int SLOT_LIGHT     = 0xFFFFFFFF;
    public static final int PANEL_FILL     = 0xFFC6C6C6;
    public static final int PANEL_LIGHT    = 0xFFFFFFFF;
    public static final int PANEL_DARK     = 0xFF555555;
    public static final int OUTLINE        = 0xFF000000;
    public static final int HOVER_BORDER   = 0xFFFFFFA0;

    public static final int TEXT_NORMAL    = 0xFFE0E0E0;
    public static final int TEXT_TITLE     = 0xFF404040;
    public static final int TEXT_HINT      = 0xFFA0A0A0;
    public static final int TEXT_HOVER     = 0xFFFFFFA0;

    public static final int TOOLTIP_BG     = 0xE6100010;
    public static final int TOOLTIP_EDGE   = 0xFF5000FF;

    // Modern palette
    public static final int MODERN_BG_DARK = 0xFF0A0A1A;
    public static final int MODERN_BG_LIGHT = 0xFF1A1A2E;
    public static final int MODERN_BUTTON = 0x33FFFFFF;
    public static final int MODERN_BUTTON_HOVER = 0x6600FFFF;
    public static final int MODERN_BORDER = 0xFF00FFFF;
    public static final int MODERN_GLOW = 0xFF00FFFF;

    /** Nine-slice corner size, in source texels, shared by all bevelled art. */
    public static final int BORDER = 3;
    /** Source size of the nine-slice widget textures. */
    public static final int WIDGET = 16;

    /** Slot is drawn 1:1 at this size. */
    public static final int SLOT_SIZE = 18;
    public static final int ICON_SIZE = 16;
    public static final int HEART_SIZE = 9;
    public static final int CROSSHAIR_SIZE = 16;

    // Nine-slice widgets
    public final int panel;
    public final int button;
    public final int buttonHover;
    public final int buttonDisabled;
    public final int hotbarPanel;
    public final int scrollTrack;
    public final int scrollThumb;

    // Fixed-size sprites
    public final int slot;
    public final int slotHover;
    public final int heartEmpty;
    public final int heartHalf;
    public final int heartFull;
    public final int crosshair;

    // Backgrounds
    public final int dirt;
    public final int white;

    public GuiAssets() {
        panel = makeBevelPanel(PANEL_FILL, PANEL_LIGHT, PANEL_DARK, OUTLINE);
        button = makeBevelPanel(0xFF8B8B8B, PANEL_LIGHT, PANEL_DARK, OUTLINE);
        buttonHover = makeBevelPanel(0xFF9BA5C0, PANEL_LIGHT, 0xFF5A6480, HOVER_BORDER);
        buttonDisabled = makeBevelPanel(0xFF6E6E6E, 0xFF909090, 0xFF454545, OUTLINE);
        hotbarPanel = makeHotbarPanel();
        scrollTrack = makeScrollTrack();
        scrollThumb = makeBevelPanel(PANEL_FILL, PANEL_LIGHT, PANEL_DARK, OUTLINE);

        slot = makeSlot(false);
        slotHover = makeSlot(true);

        heartEmpty = makeHeart(0);
        heartHalf = makeHeart(1);
        heartFull = makeHeart(2);
        crosshair = makeCrosshair();

        dirt = makeDirt();
        white = upload(new int[]{0xFFFFFFFF}, 1, 1, false);
    }

    // ------------------------------------------------------------------
    // Nine-slice widgets
    // ------------------------------------------------------------------

    /**
     * Raised bevel: black outline, light top/left, dark bottom/right.
     */
    private int makeBevelPanel(int fill, int light, int dark, int outline) {
        int s = WIDGET;
        int[] p = new int[s * s];

        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                int c;
                if (x == 0 || y == 0 || x == s - 1 || y == s - 1) {
                    c = outline;
                } else if (x == 1 || y == 1) {
                    c = light;
                } else if (x == s - 2 || y == s - 2) {
                    c = dark;
                } else {
                    c = fill;
                }
                p[y * s + x] = c;
            }
        }
        return upload(p, s, s, false);
    }

    /**
     * Hotbar backing: translucent black with a subtle raised edge, so the
     * sunken slots read as sitting inside one continuous bar.
     */
    private int makeHotbarPanel() {
        int s = WIDGET;
        int[] p = new int[s * s];

        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                int c;
                if (x == 0 || y == 0 || x == s - 1 || y == s - 1) {
                    c = 0xFF000000;
                } else if (x == 1 || y == 1) {
                    c = 0xB06A6A6A;
                } else if (x == s - 2 || y == s - 2) {
                    c = 0xC0202020;
                } else {
                    c = 0xA0000000;
                }
                p[y * s + x] = c;
            }
        }
        return upload(p, s, s, false);
    }

    /** Sunken groove for the scrollbar. */
    private int makeScrollTrack() {
        int s = WIDGET;
        int[] p = new int[s * s];

        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                int c;
                if (x <= 0 || y <= 0) {
                    c = 0xFF373737;
                } else if (x >= s - 1 || y >= s - 1) {
                    c = 0xFFFFFFFF;
                } else {
                    c = 0xFF5A5A5A;
                }
                p[y * s + x] = c;
            }
        }
        return upload(p, s, s, false);
    }

    // ------------------------------------------------------------------
    // Slot
    // ------------------------------------------------------------------

    /**
     * 18x18 sunken slot: 1px dark on top/left, 1px white on bottom/right,
     * flat grey face. Drawn 1:1, never stretched, so the bevel stays crisp.
     */
    private int makeSlot(boolean hover) {
        int s = SLOT_SIZE;
        int[] p = new int[s * s];
        int fill = hover ? 0xFFA7A7A7 : SLOT_FILL;

        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                int c;
                if (x == 0 || y == 0) {
                    c = SLOT_SHADOW;
                } else if (x == s - 1 || y == s - 1) {
                    c = SLOT_LIGHT;
                } else {
                    c = fill;
                }
                p[y * s + x] = c;
            }
        }

        // The corner where the two bevels meet reads better as the face colour
        p[(s - 1) * s] = fill;
        p[s - 1] = fill;

        return upload(p, s, s, false);
    }

    // ------------------------------------------------------------------
    // Hearts
    // ------------------------------------------------------------------

    /**
     * 9x9 heart sprite.
     *
     * @param mode 0 = empty container, 1 = half full, 2 = full
     */
    private int makeHeart(int mode) {
        // '.' transparent, 'o' outline, '#' body, '+' specular highlight
        String[] mask = {
            ".oo.oo...",
            "o##o##o..",
            "o#####o..",
            "o#####o..",
            ".o###o...",
            "..o#o....",
            "...o.....",
            ".........",
            "........."
        };

        // Redraw at full 9x9 with a rounder silhouette
        mask = new String[]{
            ".oo...oo.",
            "o##o.o##o",
            "o#+##.##o",
            "o#######o",
            "o#######o",
            ".o#####o.",
            "..o###o..",
            "...o#o...",
            "....o...."
        };

        int s = HEART_SIZE;
        int[] p = new int[s * s];

        int outline = 0xFF3A0A0A;
        int body = (mode == 0) ? 0xFF4A4A4A : 0xFFD03030;
        int spec = (mode == 0) ? 0xFF5E5E5E : 0xFFF06060;

        for (int y = 0; y < s; y++) {
            String row = mask[y];
            for (int x = 0; x < s; x++) {
                char ch = x < row.length() ? row.charAt(x) : '.';

                // Half hearts keep the container colour on the right side
                boolean rightHalf = x > (s / 2);
                int fillColor = body;
                int specColor = spec;
                if (mode == 1 && rightHalf) {
                    fillColor = 0xFF4A4A4A;
                    specColor = 0xFF5E5E5E;
                }

                int c = switch (ch) {
                    case 'o' -> outline;
                    case '#' -> fillColor;
                    case '+' -> specColor;
                    default -> 0x00000000;
                };
                p[y * s + x] = c;
            }
        }
        return upload(p, s, s, false);
    }

    // ------------------------------------------------------------------
    // Crosshair
    // ------------------------------------------------------------------

    /**
     * 15x15 crosshair in the classic Minecraft style: a 1px-wide white plus
     * with a 1px black outline so it stays readable against any background.
     */
    private int makeCrosshair() {
        int s = CROSSHAIR_SIZE;
        int[] p = new int[s * s];

        int c = s / 2;  // centre pixel (7 for 15x15)
        int armLen = 7; // length of each arm from centre
        int gap = 1;    // transparent gap around centre

        // First pass: draw black outline
        for (int i = -armLen; i <= armLen; i++) {
            if (Math.abs(i) <= gap) continue;
            int ax = c + i;
            // Horizontal arm outline
            set(p, s, ax, c - 1, 0xFF000000);
            set(p, s, ax, c + 1, 0xFF000000);
            // Vertical arm outline
            set(p, s, c - 1, ax, 0xFF000000);
            set(p, s, c + 1, ax, 0xFF000000);
        }

        // Second pass: draw white arms on top
        for (int i = -armLen; i <= armLen; i++) {
            if (Math.abs(i) <= gap) continue;
            int ax = c + i;
            set(p, s, ax, c, 0xFFFFFFFF); // horizontal
            set(p, s, c, ax, 0xFFFFFFFF); // vertical
        }

        return upload(p, s, s, false);
    }

    private void set(int[] p, int s, int x, int y, int color) {
        if (x < 0 || y < 0 || x >= s || y >= s) return;
        p[y * s + x] = color;
    }

    // ------------------------------------------------------------------
    // Backgrounds
    // ------------------------------------------------------------------

    private int makeDirt() {
        int s = 32;
        int[] p = new int[s * s];
        Random rnd = new Random(0xD147L);

        for (int i = 0; i < p.length; i++) {
            int n = rnd.nextInt(40) - 20;
            int r = clamp(134 + n), g = clamp(96 + n), b = clamp(58 + n);
            if (rnd.nextInt(11) == 0) {
                r = clamp(r - 26); g = clamp(g - 22); b = clamp(b - 16);
            }
            p[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return upload(p, s, s, true);
    }

    // ------------------------------------------------------------------

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
            panel, button, buttonHover, buttonDisabled, hotbarPanel,
            scrollTrack, scrollThumb, slot, slotHover,
            heartEmpty, heartHalf, heartFull, crosshair, dirt, white
        });
    }
}
