package com.voxelgame.ui;

/**
 * Shared visual language for every screen in the game.
 *
 * Rebuilt around a glassmorphism look: rounded, translucent panels with a
 * crisp light rim and a soft drop shadow, drawn over either the live world
 * (frosted through a blurred copy of the scene) or the animated theme
 * backdrop. The amber accent and the twilight/sunset identity are kept so
 * every screen still belongs to the same world.
 */
public final class MenuTheme {

    // Sky / backdrop (sunset identity retained)
    public static final int SKY_TOP        = 0xFF141B33;
    public static final int SKY_BOTTOM     = 0xFF6E4128;
    public static final int SUNSET_CORE    = 0xFFE9B35B;
    public static final int SUNSET_GLOW    = 0xD98A3A;
    public static final int SUNSET_HI      = 0xFFF4D08A;

    // Accent (theme-driven; the whole UI follows this)
    public static int ACCENT       = 0xFFB454;
    public static int ACCENT_LIGHT = 0xFFD9A066;
    public static int ACCENT_DIM   = 0xFF8A3D1F;

    /**
     * One accent per menu theme (index matches Settings.menuTheme).
     * Each row: { core, light, dim }.
     */
    private static final int[][] ACCENT_SETS = {
        { 0xFFB454, 0xFFD9A066, 0xFF8A3D1F }, // 0 Sunset  - amber
        { 0xFFC4CDE8, 0xFFFFFFFF, 0xFF5A6A9A }, // 1 AMOLED  - pale steel
        { 0xFFE0A848, 0xFFFFD98A, 0xFF90641F }, // 2 Day     - sun gold
        { 0xFF9FB6E8, 0xFFE0EAFF, 0xFF4A5A9A }, // 3 Night   - moon blue
        { 0xFF6FFFB0, 0xFFC9FFE0, 0xFF2A8A5A }, // 4 Aurora  - bioluminescent teal
        { 0xFFE0B868, 0xFFFFE0A0, 0xFFA07030 }, // 5 Desert  - sandy tan
        { 0xFFF2A0A8, 0xFFFFE0E0, 0xFFA85866 }, // 6 Dawn    - soft pink
        { 0xFFC88AF0, 0xFFF0D8FF, 0xFF7A3AA8 }, // 7 Void    - violet
    };

    /** Switch the interface accent to one of the 8 menu themes. */
    public static void setThemeIndex(int index) {
        int i = Math.max(0, Math.min(ACCENT_SETS.length - 1, index));
        int[] set = ACCENT_SETS[i];
        ACCENT = set[0];
        ACCENT_LIGHT = set[1];
        ACCENT_DIM = set[2];
    }

    // Glass panels (RGB keeps its hue; alpha comes from col()/texture).
    // Mutable: beginFrame() swaps the whole base between the dark glass
    // (in-game screens) and the light "airy" set (menu flow screens).
    public static int GLASS_PANEL       = 0xFF141826;
    public static int GLASS_PANEL_HOVER = 0xFF252F49;
    public static int GLASS_CARD        = 0xFF0F141E;
    public static int GLASS_ACTIVE      = 0xFF0D1119;

    // Rims / edges
    public static int GLASS_EDGE_TOP    = 0xFFFFFFFF;
    public static int GLASS_EDGE_SIDE   = 0x3CFFFFFF;
    public static int GLASS_EDGE_BOTTOM = 0x1EFFFFFF;
    public static int GLASS_BORDER      = 0xB0FFFFFF;
    public static int GLASS_INNER       = 0x1AFFFFFF;
    public static int GLASS_SHADOW      = 0xFF06080C;

    // Legacy slate palette (still read by custom rows in screens that have
    // not been migrated to the glass look yet)
    public static final int PANEL_BG       = 0xFF1B2440;
    public static final int PANEL_BG_HOVER = 0xFF26335A;
    public static final int PANEL_EDGE_L   = 0x4A5A8C;
    public static final int PANEL_EDGE_D   = 0x0C1122;
    public static final int PANEL_BORDER   = 0x0A0E1C;
    public static final int PANEL_INNER    = 0x44547F;

    // Text (mutable, swapped by beginFrame)
    public static int TEXT_BRIGHT    = 0xFFFFFFFF;
    public static int TEXT_LABEL     = 0xFFDCE4F5;
    public static int TEXT_SECONDARY = 0xFF9AA8CC;
    public static int TEXT_DIM       = 0xFF66739A;
    public static int TEXT_WARNING   = 0xFFE06040;

    // Misc (mutable, swapped by beginFrame)
    public static int OVERLAY        = 0xC0141828;   // world wash (fallback)
    public static int OVERLAY_SOFT   = 0x8C141828;   // light wash over the blur
    public static int GLASS_WASH     = 0x4C09111C;   // soft tint over the blurred world
    public static int SEPARATOR      = 0x40A0B0D0;

    /**
     * True while a menu-flow screen (main menu, options, world screens) is
     * being rendered: panels switch to translucent white glass with dark
     * text, and the world wash turns to a bright veil. In-game screens keep
     * the dark glass. Set from {@link com.voxelgame.ui.screen.Screen#render}.
     */
    public static boolean lightContext = false;

    /** Swaps the base palette for the current frame's screen style. */
    public static void beginFrame(boolean light) {
        lightContext = light;
        if (light) {
            GLASS_PANEL       = 0xFFFDFEFF;
            GLASS_PANEL_HOVER = 0xFFFFFFFF;
            GLASS_CARD        = 0xFFFAFBFE;
            GLASS_ACTIVE      = 0xFFE9EDF4;

            GLASS_EDGE_TOP    = 0xFFFFFFFF;
            GLASS_EDGE_SIDE   = 0xFFD9E0EA;
            GLASS_EDGE_BOTTOM = 0xFFC9D2DF;
            GLASS_BORDER      = 0xFFC4CEDC;
            GLASS_SHADOW      = 0xFF5A6880;

            TEXT_BRIGHT       = 0xFF101A2C;
            TEXT_LABEL        = 0xFF24324A;
            TEXT_SECONDARY    = 0xFF5C6A84;
            TEXT_DIM          = 0xFF96A0B6;
            TEXT_WARNING      = 0xFFC0392B;

            OVERLAY           = 0xAAE8EEF6;
            OVERLAY_SOFT      = 0x66FFFFFF;
            GLASS_WASH        = 0x52FFFFFF;
            SEPARATOR         = 0x38708CA8;
        } else {
            // Workshop walnut: the in-game set serves the container screens
            GLASS_PANEL       = 0xFF4A3626;
            GLASS_PANEL_HOVER = 0xFF5C4530;
            GLASS_CARD        = 0xFF3A2A1C;
            GLASS_ACTIVE      = 0xFF332314;

            GLASS_EDGE_TOP    = 0xFFEDD9A0;
            GLASS_EDGE_SIDE   = 0x50C9973B;
            GLASS_EDGE_BOTTOM = 0xFF6E5220;
            GLASS_BORDER      = 0xFF8A6420;
            GLASS_SHADOW      = 0xFF1C1208;

            TEXT_BRIGHT       = 0xFFF2E6C8;
            TEXT_LABEL        = 0xFFE8D9B8;
            TEXT_SECONDARY    = 0xFFC9B68F;
            TEXT_DIM          = 0xFFA89268;
            TEXT_WARNING      = 0xFFE8764A;

            OVERLAY           = 0xC0261A10;
            OVERLAY_SOFT      = 0x8C261A10;
            GLASS_WASH        = 0x4C261A10;
            SEPARATOR         = 0x66C9973B;
        }
    }

    /** Restores the dark in-game base; call when a light frame is done. */
    public static void endFrame() {
        beginFrame(false);
    }

    /**
     * Blurred copy of the live scene, set every frame by Game when a menu is
     * open over the world. 0 = no world behind (procedural backdrop).
     */
    public static int blurredBackdrop = 0;

    private MenuTheme() {}

    // ------------------------------------------------------------------
    // Drawing primitives
    // ------------------------------------------------------------------

    /**
     * Glass panel: rounded translucent body tinted with the theme colour, a
     * crisp light rim on top, a soft offset shadow, and an amber accent line
     * on hover. The rounded shape lives in the generated nine-slice texture,
     * so corners stay crisp at any GUI scale.
     */
    public static void drawPanel(UIRenderer ui, int alpha, float x, float y,
                                  float w, float h, boolean hovered) {
        drawPanel(ui, alpha, x, y, w, h, hovered ? 1f : 0f);
    }

    /** Animated variant: {@code hover01} eases 0..1 so panels fade between
     *  states instead of snapping (fed from Widget hover animations). */
    public static void drawPanel(UIRenderer ui, int alpha, float x, float y,
                                  float w, float h, float hover01) {
        GuiAssets tex = GuiAssets.INSTANCE;
        int r = GuiAssets.GLASS_BORDER;
        boolean light = lightContext;

        // Soft offset drop shadow, growing slightly as the panel lifts
        ui.drawNineSlice(tex.glassShadow, x + 2, y + 4, w, h,
            r, GuiAssets.GLASS_WIDGET, col(alpha, light ? 110 : 255, GLASS_SHADOW));

        // Glass body tinted toward the accent; brightens smoothly on hover
        int fill = blend(GLASS_PANEL, ACCENT, light ? 0.04f + 0.05f * hover01
                                                    : 0.09f + 0.07f * hover01);
        if (hover01 > 0.01f) fill = blend(fill, GLASS_PANEL_HOVER, hover01);
        ui.drawNineSlice(tex.glassPanel, x, y, w, h,
            r, GuiAssets.GLASS_WIDGET, col(alpha, light ? 225 : 200, fill));

        // Vertical light falloff inside the rounded body (top-lit material)
        if (h > 2 * r + 2) {
            if (light) {
                ui.fillGradientV(x + r, y + r, w - 2 * r, h - 2 * r - 1,
                    col(alpha, 70, 0xFFFFFF), col(alpha, 26, 0x9FB0C8));
            } else {
                ui.fillGradientV(x + r, y + r, w - 2 * r, h - 2 * r - 1,
                    col(alpha, 26, 0xFFFFFF), col(alpha, 0, 0x000000));
            }
        }

        // Light rim along the top and bottom edges (kept inside the corners)
        int spanW = Math.max(0, (int) w - 2 * r);
        int spanH = Math.max(0, (int) h - 2 * r);
        ui.fillRect(x + r, y, spanW, 1, col(alpha, light ? 255 : 235, GLASS_EDGE_TOP));
        ui.fillRect(x + r, y + h - 1, spanW, 1,
            col(alpha, light ? 230 : 70, GLASS_EDGE_BOTTOM));
        if (spanH > 0) {
            ui.fillRect(x, y + r, 1, spanH, col(alpha, light ? 255 : 90, GLASS_EDGE_SIDE));
            ui.fillRect(x + w - 1, y + r, 1, spanH, col(alpha, light ? 255 : 90, GLASS_EDGE_SIDE));
        }

        // Hover accents fade in: a glowing top hairline and a left bar
        if (hover01 > 0.01f) {
            int a = (int) (hover01 * 255);
            ui.fillRect(x + r + 2, y + 1, Math.max(0, spanW - 4), 1,
                col(alpha, a, ACCENT));
            ui.fillRect(x + r + 3, y + 2, Math.max(0, spanW - 6), 1,
                col(alpha, a * 40 / 255, ACCENT));
            float barH = Math.max(0, h - 2 * r - 4);
            ui.fillRect(x + 2, y + r + 2, 2, barH, col(alpha, a, ACCENT));
            ui.fillRect(x + 4, y + r + 2, 1, barH, col(alpha, a / 3, ACCENT));
        }
    }

    /**
     * Large glass card used by full-screen menus (options, create world). A
     * deep top-lit body with a crisp rim and soft shadow.
     */
    public static void drawCard(UIRenderer ui, int alpha, float x, float y,
                                 float w, float h) {
        GuiAssets tex = GuiAssets.INSTANCE;
        int r = GuiAssets.GLASS_BORDER;
        boolean light = lightContext;

        // Soft offset drop shadow
        ui.drawNineSlice(tex.glassShadow, x + 3, y + 6, w, h,
            r, GuiAssets.GLASS_WIDGET, col(alpha, light ? 120 : 255, GLASS_SHADOW));

        ui.drawNineSlice(tex.glassPanel, x, y, w, h,
            r, GuiAssets.GLASS_WIDGET,
            col(alpha, light ? 235 : 190, blend(GLASS_CARD, ACCENT, light ? 0.03f : 0.07f)));

        // Top-lit falloff inside the body
        if (h > 2 * r + 2) {
            if (light) {
                ui.fillGradientV(x + r, y + r, Math.max(0, (int) w - 2 * r), h - 2 * r - 1,
                    col(alpha, 60, 0xFFFFFF), col(alpha, 20, 0xA8B8CC));
            } else {
                ui.fillGradientV(x + r, y + r, Math.max(0, (int) w - 2 * r), h - 2 * r - 1,
                    col(alpha, 18, 0xFFFFFF), col(alpha, 0, 0x000000));
            }
        }

        ui.fillRect(x + r, y, Math.max(0, (int) w - 2 * r), 1,
            col(alpha, 255, GLASS_EDGE_TOP));
        ui.fillRect(x + r, y + h - 1, Math.max(0, (int) w - 2 * r), 1,
            col(alpha, light ? 220 : 50, GLASS_EDGE_BOTTOM));

        // Quiet accent baseline along the bottom inner edge
        int ix = (int) x + r + 4, iw = (int) w - 2 * (r + 4);
        if (iw > 0) {
            ui.fillRect(ix, y + h - r - 5, iw, 1, col(alpha, light ? 45 : 70, ACCENT_DIM));
        }
    }

    /**
     * Horizontal separator line with diamond ornaments at the ends.
     */
    public static void drawSeparator(UIRenderer ui, int alpha, float cx, float y, float halfW) {
        int c = col(alpha, 200, ACCENT);
        int c2 = col(alpha, 80, ACCENT);
        ui.fillRect(cx - halfW, y, halfW * 2, 1, c2);
        ui.fillRect(cx - halfW - 2, y - 1, 2, 3, c);
        ui.fillRect(cx + halfW, y - 1, 2, 3, c);
        ui.fillRect(cx - 1, y - 2, 2, 1, c);
        ui.fillRect(cx - 1, y + 2, 2, 1, c);
        ui.fillRect(cx - 1, y - 1, 2, 3, c);
    }

    /**
     * Full-screen sunset backdrop shared by menu screens. Multi-band sky
     * gradient, soft sun glow, two rows of hills and a vignette, dimmed so
     * UI content in front stays readable.
     */
    public static void drawBackdrop(UIRenderer ui, int w, int h, double time) {
        if (lightContext) {
            // Airy daylight fallback: pale blue sky, soft sun, light hills
            ui.fillGradientMultiV(0, 0, w, h, 0xFFBFD9F2, 0xFFDCEBFA, 0xFFEFF6FC, 0xFFF8FBFD);

            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 1.1);
            int sx = (int) (w * 0.72f);
            int sy = h - 170;
            for (int r = 4; r >= 1; r--) {
                int a = (int) ((10 + 4 * pulse) / r) << 24;
                ui.fillRect(sx - r * 6, sy - r * 6, r * 12, r * 12, a | ACCENT);
            }
            ui.fillRect(sx - 9, sy - 9, 18, 18, 0xFF000000 | ACCENT_LIGHT);

            // Rolling hill silhouettes, quiet and low-contrast
            ui.fillRect(0, h - 120, w, 120, 0xFFD4E2EE);
            int ridge = h - 108;
            for (int i = -1; i <= (w >> 5) + 1; i++) {
                ui.fillRect(i * 32, ridge, 32, 2, 0xFFC2D4E4);
            }

            // Barely-there veil so UI text stays readable
            ui.fillRect(0, 0, w, h, 0x10FFFFFF);
            return;
        }

        ui.fillGradientMultiV(0, 0, w, h, SKY_TOP, 0xFF1A2240, SKY_BOTTOM, 0xFF4A2E24);

        // Soft pulsing sun (tinted by the theme accent)
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 1.1);
        int sx = (int) (w * 0.72f);
        int sy = h - 180;
        for (int r = 4; r >= 1; r--) {
            int a = (int) ((6 + 3 * pulse) / r) << 24;
            ui.fillRect(sx - r * 6, sy - r * 6, r * 12, r * 12, a | ACCENT_DIM);
        }
        ui.fillRect(sx - 10, sy - 10, 20, 20, 0xFF000000 | ACCENT);
        ui.fillRect(sx - 10, sy - 10, 20, 4, 0xFF000000 | ACCENT_LIGHT);

        // Rolling hill silhouettes
        ui.fillRect(0, h - 130, w, 130, 0xFF1A2038);
        int ridge = h - 118;
        for (int i = -1; i <= (w >> 5) + 1; i++) {
            ui.fillRect(i * 32, ridge, 32, 2, 0xFF141A30);
        }

        // Soft vignette: darkens the edges, lighter in the centre
        int vg = 90;
        ui.fillRect(0, 0, w, 6, 0x00000000 | (vg + 40));
        ui.fillRect(0, 0, 6, h, 0x00000000 | (vg + 40));
        ui.fillRect(w - 6, 0, 6, h, 0x00000000 | (vg + 40));
        ui.fillRect(0, h - 6, w, 6, 0x00000000 | (vg + 40));

        // Dimming overlay so the UI is always readable
        ui.fillRect(0, 0, w, h, 0x26000000);
    }

    /**
     * A dark wash used when a menu is overlaid on top of the live world. If
     * Game produced a blurred backdrop this frame it is drawn first, giving
     * the frosted-glass look; otherwise a plain wash is used.
     */
    public static void drawWorldOverlay(UIRenderer ui, int w, int h) {
        if (blurredBackdrop != 0) {
            ui.drawTexture(blurredBackdrop, 0, 0, w, h);
            ui.fillRect(0, 0, w, h, GLASS_WASH);
        } else {
            ui.fillRect(0, 0, w, h, OVERLAY);
        }
    }

    // ------------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------------

    /** Apply alpha scaling to an RGB colour. */
    public static int col(int alpha, int base, int rgb) {
        int a = ((alpha >>> 24) * base) / 255;
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Linearly interpolate two ARGB colours. */
    public static int lerp(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ra = (a >> 16) & 0xFF, ga = (a >> 8) & 0xFF, ba2 = a & 0xFF;
        int ab = (b >>> 24) & 0xFF, rb = (b >> 16) & 0xFF, gb = (b >> 8) & 0xFF, bb = b & 0xFF;
        int aO = (int) (aa + (ab - aa) * t);
        int rO = (int) (ra + (rb - ra) * t);
        int gO = (int) (ga + (gb - ga) * t);
        int bO = (int) (ba2 + (bb - ba2) * t);
        return (aO << 24) | (rO << 16) | (gO << 8) | bO;
    }

    /** Blend two RGB colours towards a fraction t of the second. */
    private static int blend(int base, int accent, float t) {
        int r = (int) (((base >> 16) & 0xFF) + (((accent >> 16) & 0xFF) - ((base >> 16) & 0xFF)) * t);
        int g = (int) (((base >> 8) & 0xFF) + (((accent >> 8) & 0xFF) - ((base >> 8) & 0xFF)) * t);
        int b = (int) ((base & 0xFF) + ((accent & 0xFF) - (base & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }
}