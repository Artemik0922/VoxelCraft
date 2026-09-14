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

    /** Glassmorphism panel size and corner radius (nine-slice). */
    public static final int GLASS_WIDGET = 24;
    public static final int GLASS_BORDER = 8;

    /** Slot is drawn 1:1 at this size. */
    public static final int SLOT_SIZE = 18;
    public static final int ICON_SIZE = 16;
    public static final int HEART_SIZE = 9;
    public static final int CROSSHAIR_SIZE = 16;

    /** The single shared instance, referenced from MenuTheme helpers. */
    public static GuiAssets INSTANCE;

    // Nine-slice widgets
    public final int panel;
    public final int button;
    public final int buttonHover;
    public final int buttonDisabled;
    public final int hotbarPanel;
    public final int scrollTrack;
    public final int scrollThumb;

    // Glassmorphism widgets (rounded, alpha-masked, tinted at draw time)
    public final int glassPanel;
    public final int glassField;
    public final int glassTrack;
    public final int glassShadow;

    // Fixed-size sprites
    public final int slot;
    public final int slotHover;
    public final int glassSlot;
    public final int glassSlotHover;
    public final int heartEmpty;
    public final int heartHalf;
    public final int heartFull;
    /** Vanilla-style drumstick sprites for the hunger bar. */
    public final int drumstickEmpty;
    public final int drumstickHalf;
    public final int drumstickFull;
    /** Air bubble shown above the hearts while the head is underwater. */
    public final int bubble;
    public final int crosshair;

    // Backgrounds
    public final int dirt;
    public final int white;

    public GuiAssets() {
        INSTANCE = this;

        panel = makeBevelPanel(PANEL_FILL, PANEL_LIGHT, PANEL_DARK, OUTLINE);
        button = makeBevelPanel(0xFF8B8B8B, PANEL_LIGHT, PANEL_DARK, OUTLINE);
        buttonHover = makeBevelPanel(0xFF9BA5C0, PANEL_LIGHT, 0xFF5A6480, HOVER_BORDER);
        buttonDisabled = makeBevelPanel(0xFF6E6E6E, 0xFF909090, 0xFF454545, OUTLINE);
        hotbarPanel = makeHotbarPanel();
        scrollTrack = makeScrollTrack();
        scrollThumb = makeBevelPanel(PANEL_FILL, PANEL_LIGHT, PANEL_DARK, OUTLINE);

        glassPanel = makeGlassPanel();
        glassField = makeRoundedFlat(GLASS_WIDGET, 6, 200, 84);
        glassTrack = makeRoundedFlat(GLASS_WIDGET, 4, 92, 56);
        glassShadow = makeGlassShadow();

        slot = makeSlot(false);
        slotHover = makeSlot(true);
        glassSlot = makeRoundedFlat(SLOT_SIZE, 3, 176, 96);
        glassSlotHover = makeRoundedFlat(SLOT_SIZE, 3, 208, 150);

        heartEmpty = makeHeart(0);
        heartHalf = makeHeart(1);
        heartFull = makeHeart(2);
        drumstickEmpty = makeDrumstick(0);
        drumstickHalf = makeDrumstick(1);
        drumstickFull = makeDrumstick(2);
        bubble = makeBubble();
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
    // Glassmorphism textures
    // ------------------------------------------------------------------

    /**
     * Signed distance from a pixel to a rounded rectangle with the given
     * radius (negative inside). Returns the coverage (0..1) blurred over a
     * tiny feather for anti-aliased edges on the pixel grid.
     */
    private static float roundedCoverage(float x, float y, int size, float radius, float feather) {
        float hw = (size - 1) * 0.5f;
        float hr = hw - radius;
        float qx = Math.abs(x - hw) - hr;
        float qy = Math.abs(y - hw) - hr;
        float d = (float) (Math.hypot(Math.max(qx, 0), Math.max(qy, 0))
            + Math.min(Math.max(qx, qy), 0) - radius);
        if (d >= 0) return 0f;
        float f = feather >= 1f ? feather : 1f;
        float cov = -d / f;
        return cov > 1f ? 1f : cov;
    }

    /** Rounded white nine-slice with a vertical light-to-dark body gradient
     *  and a crisp rim - the glass card material. */
    private int makeGlassPanel() {
        int s = GLASS_WIDGET;
        float r = GLASS_BORDER;
        int[] p = new int[s * s];
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float cov = roundedCoverage(x, y, s, r, 1f);
                if (cov <= 0) continue;

                // Vertical glass gradient: brighter at the top edge
                float gy = y / (float) (s - 1);
                int body = (int) Math.round(255 * (0.92f - 0.10f * gy));

                // Outer rim reads crisper than the body
                boolean rim = x < 2 || y < 2 || x >= s - 2 || y >= s - 2;
                int a = rim ? 255 : body;
                p[y * s + x] = (int) (cov * a) << 24 | 0x00FFFFFF;
            }
        }
        return upload(p, s, s, false);
    }

    /** Flat rounded material (fields, tracks, slots). */
    private int makeRoundedFlat(int size, float radius, int bodyAlpha, int rimAlpha) {
        int[] p = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float cov = roundedCoverage(x, y, size, radius, 1f);
                if (cov <= 0) continue;
                boolean rim = x < 2 || y < 2 || x >= size - 2 || y >= size - 2;
                int a = rim ? rimAlpha : bodyAlpha;
                p[y * size + x] = (int) (cov * a) << 24 | 0x00FFFFFF;
            }
        }
        return upload(p, size, size, false);
    }

    /** Soft rounded shadow material: feathered edges, low alpha. */
    private int makeGlassShadow() {
        int s = GLASS_WIDGET;
        float r = GLASS_BORDER;
        int[] p = new int[s * s];
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float cov = roundedCoverage(x, y, s, r, 2.4f);
                if (cov <= 0) continue;
                int a = (int) (cov * 120);
                p[y * s + x] = a << 24 | 0x000000;
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
     * 9x9 heart sprite, restyled for the glass look: softer outline, a
     * light top specular, and a subtle translucent glow halo around the body.
     *
     * @param mode 0 = empty container, 1 = half full, 2 = full
     */
    private int makeHeart(int mode) {
        String[] mask = {
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
        boolean[] body = new boolean[s * s];

        int outline = 0xFF2E0E12;
        int bodyCol = (mode == 0) ? 0xFF4A4B52 : 0xFFD53342;
        int spec = (mode == 0) ? 0xFF5E5F66 : 0xFFF07A84;

        for (int y = 0; y < s; y++) {
            String row = mask[y];
            for (int x = 0; x < s; x++) {
                char ch = x < row.length() ? row.charAt(x) : '.';
                if (ch == '.') continue;

                boolean rightHalf = x > (s / 2);
                if (mode == 1 && rightHalf) {
                    bodyCol = 0xFF48494F;
                    spec = 0xFF5A5B62;
                }

                boolean isBody = ch == '#' || ch == '+';
                if (isBody) {
                    p[y * s + x] = ch == '#' ? bodyCol : spec;
                    body[y * s + x] = true;
                } else {
                    p[y * s + x] = outline;
                }
            }
        }

        // Soft glow halo around the filled part
        int glow = 0x40D53342;
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                if (body[y * s + x] || p[y * s + x] != 0) continue;
                boolean near = false;
                for (int dy = -1; dy <= 1 && !near; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && ny >= 0 && nx < s && ny < s && body[ny * s + nx]) {
                            near = true;
                            break;
                        }
                    }
                }
                if (near) p[y * s + x] = glow;
            }
        }
        return upload(p, s, s, false);
    }

    // ------------------------------------------------------------------
    // Hunger drumstick and air bubble
    // ------------------------------------------------------------------

    /**
     * 9x9 drumstick sprite for the hunger bar: meat blob up-right, bone
     * tip down-left, mirroring the vanilla layout.
     *
     * @param mode 0 = empty container, 1 = half (right half filled), 2 = full
     */
    private int makeDrumstick(int mode) {
        String[] mask = {
            "....ooo..",
            "...o###o.",
            "..o#+###o",
            "..o#####o",
            ".o#####o.",
            ".o###oo..",
            "obbo.....",
            "obbo.....",
            ".oo......"
        };

        int s = HEART_SIZE;
        int[] p = new int[s * s];
        boolean[] body = new boolean[s * s];

        int outline = 0xFF2B1508;
        int meat = (mode == 0) ? 0xFF4A4B52 : 0xFFB85C24;
        int meatHi = (mode == 0) ? 0xFF5E5F66 : 0xFFE08A4A;
        int bone = (mode == 0) ? 0xFF5E5F66 : 0xFFF2EAD8;
        int boneEdge = (mode == 0) ? 0xFF45464D : 0xFFC9BCA4;

        for (int y = 0; y < s; y++) {
            String row = mask[y];
            for (int x = 0; x < s; x++) {
                char ch = x < row.length() ? row.charAt(x) : '.';
                if (ch == '.') continue;

                boolean isBone = ch == 'b';
                boolean filledSide = x >= (s / 2); // half icons fill the right
                int col;
                if (mode == 1 && !filledSide) {
                    col = ch == 'o' ? outline : (isBone ? boneEdge : 0xFF48494F);
                } else if (isBone) {
                    col = bone;
                } else if (ch == '#') {
                    col = meat;
                } else if (ch == '+') {
                    col = meatHi;
                } else {
                    col = outline;
                }
                p[y * s + x] = col;
                body[y * s + x] = ch != 'o';
            }
        }

        // Soft warm halo around the filled part
        int glow = (mode == 0) ? 0x30201A14 : 0x40B85C24;
        halo(p, body, s, glow);
        return upload(p, s, s, false);
    }

    /** 9x9 air bubble: light blue disc with a white specular spot. */
    private int makeBubble() {
        String[] mask = {
            "...ooo...",
            "..o###o..",
            ".o#+###o.",
            ".o######o",
            "o#######o",
            "o#######o",
            ".o#####o.",
            "..o###o..",
            "...ooo..."
        };

        int s = HEART_SIZE;
        int[] p = new int[s * s];
        boolean[] body = new boolean[s * s];

        int outline = 0xFF22557A;
        int water = 0xFF8CC8EA;
        int spec = 0xFFE8FAFF;

        for (int y = 0; y < s; y++) {
            String row = mask[y];
            for (int x = 0; x < s; x++) {
                char ch = x < row.length() ? row.charAt(x) : '.';
                if (ch == '.') continue;
                p[y * s + x] = ch == 'o' ? outline : (ch == '+' ? spec : water);
                body[y * s + x] = ch != 'o';
            }
        }
        halo(p, body, s, 0x308CC8EA);
        return upload(p, s, s, false);
    }

    /** Paint a 1px translucent halo around every body pixel. */
    private void halo(int[] p, boolean[] body, int s, int glow) {
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                if (body[y * s + x] || p[y * s + x] != 0) continue;
                boolean near = false;
                for (int dy = -1; dy <= 1 && !near; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && ny >= 0 && nx < s && ny < s && body[ny * s + nx]) {
                            near = true;
                            break;
                        }
                    }
                }
                if (near) p[y * s + x] = glow;
            }
        }
    }

    // ------------------------------------------------------------------
    // Crosshair
    // ------------------------------------------------------------------

    /**
     * 15x15 glass crosshair: a slim 1px white plus with a faint translucent
     * halo instead of the harsh black outline, so it stays subtle over bright
     * sky but readable over dark terrain.
     */
    private int makeCrosshair() {
        int s = CROSSHAIR_SIZE;
        int[] p = new int[s * s];

        int c = s / 2;
        int armLen = 6;
        int gap = 2; // transparent gap around centre

        // Halo ring first (soft glow)
        for (int i = -armLen; i <= armLen; i++) {
            if (Math.abs(i) <= gap) continue;
            int ax = c + i;
            set(p, s, ax, c - 2, 0x20FFFFFF);
            set(p, s, ax, c + 2, 0x20FFFFFF);
            set(p, s, c - 2, ax, 0x20FFFFFF);
            set(p, s, c + 2, ax, 0x20FFFFFF);
            set(p, s, ax, c, 0x40FFFFFF);
            set(p, s, c, ax, 0x40FFFFFF);
        }
        // White centre dots
        for (int i = -armLen; i <= armLen; i++) {
            if (Math.abs(i) <= gap) continue;
            int ax = c + i;
            set(p, s, ax, c, 0xFFFFFFFF);
            set(p, s, c, ax, 0xFFFFFFFF);
        }
        set(p, s, c, c, 0xFFFFFFFF);

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
            scrollTrack, scrollThumb, glassPanel, glassField, glassTrack, glassShadow,
            slot, slotHover, glassSlot, glassSlotHover,
            heartEmpty, heartHalf, heartFull,
            drumstickEmpty, drumstickHalf, drumstickFull, bubble,
            crosshair, dirt, white
        });
    }
}
