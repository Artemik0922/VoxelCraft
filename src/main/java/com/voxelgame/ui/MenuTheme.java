package com.voxelgame.ui;

/**
 * Shared visual language for every screen in the game.
 *
 * The theme is built around a twilight/sunset palette introduced in the main
 * menu - deep blue-to-amber gradients, gold accents and dark slate panels.
 * Every overlay screen (options, create-world, select-world, pause, etc.)
 * draws the same darkened sky and uses the same panel drawing helper, so the
 * player never leaves the same visual world when they open a menu.
 */
public final class MenuTheme {

    // Sky / backdrop
    public static final int SKY_TOP        = 0xFF141B33;
    public static final int SKY_BOTTOM     = 0xFF6E4128;
    public static final int SUNSET_CORE    = 0xFFE9B35B;
    public static final int SUNSET_GLOW    = 0xD98A3A;
    public static final int SUNSET_HI      = 0xFFF4D08A;

    // Accent
    public static final int ACCENT         = 0xFFB454;
    public static final int ACCENT_LIGHT   = 0xFFD9A066;
    public static final int ACCENT_DIM     = 0xFF8A3D1F;

    // Panel
    public static final int PANEL_BG       = 0xFF1B2440;
    public static final int PANEL_BG_HOVER = 0xFF26335A;
    public static final int PANEL_EDGE_L   = 0x4A5A8C;
    public static final int PANEL_EDGE_D   = 0x0C1122;
    public static final int PANEL_BORDER   = 0x0A0E1C;
    public static final int PANEL_INNER    = 0x44547F;

    // Text
    public static final int TEXT_BRIGHT    = 0xFFFFFFFF;
    public static final int TEXT_LABEL     = 0xFFDCE4F5;
    public static final int TEXT_SECONDARY = 0xFF9AA8CC;
    public static final int TEXT_DIM       = 0xFF66739A;
    public static final int TEXT_WARNING   = 0xFFE06040;

    // Misc
    public static final int OVERLAY        = 0xD8141828;   // dark wash
    public static final int OVERLAY_SOFT   = 0xA0141828;   // softer wash
    public static final int SEPARATOR      = 0x40A0B0D0;

    private MenuTheme() {}

    // ------------------------------------------------------------------
    // Drawing primitives
    // ------------------------------------------------------------------

    /**
     * Draw a stylised panel that matches the main menu's buttons. The panel
     * has a dark slate fill, a 1px black border, a lighter top/left bevel and
     * a darker bottom/right bevel, plus an optional amber hover highlight.
     */
    public static void drawPanel(UIRenderer ui, int alpha, float x, float y,
                                  float w, float h, boolean hovered) {
        int bgFill = col(alpha, 240, hovered ? PANEL_BG_HOVER : PANEL_BG);
        ui.fillRect(x, y, w, h, bgFill);

        int border = col(alpha, 255, PANEL_BORDER);
        ui.fillRect(x, y, w, 1, border);
        ui.fillRect(x, y + h - 1, w, 1, border);
        ui.fillRect(x, y, 1, h, border);
        ui.fillRect(x + w - 1, y, 1, h, border);

        // Light bevel top & left
        ui.fillRect(x + 1, y + 1, w - 2, 2, col(alpha, 255, PANEL_EDGE_L));
        ui.fillRect(x + 1, y + 1, 2, h - 2, col(alpha, 255, PANEL_INNER));

        // Dark bevel bottom & right
        ui.fillRect(x + 1, y + h - 3, w - 2, 2, col(alpha, 255, PANEL_EDGE_D));
        ui.fillRect(x + w - 3, y + 1, 2, h - 2, col(alpha, 255, 0x101731));

        if (hovered) {
            ui.fillRect(x + 3, y + 3, 4, h - 6, col(alpha, 255, ACCENT));
            ui.fillRect(x + 3, y + 3, w - 6, h - 6, col(alpha, 26, ACCENT));
        }
    }

    /**
     * Large card-style panel used for full-screen menus (options, create
     * world, etc.). Has a subtle inner border and corner ornaments.
     */
    public static void drawCard(UIRenderer ui, int alpha, float x, float y,
                                 float w, float h) {
        drawPanel(ui, alpha, x, y, w, h, false);

        // Inner hairline border for a polished look
        int inner = col(alpha, 100, ACCENT_DIM);
        ui.fillRect(x + 6, y + 6, w - 12, 1, inner);
        ui.fillRect(x + 6, y + h - 7, w - 12, 1, inner);
        ui.fillRect(x + 6, y + 6, 1, h - 12, inner);
        ui.fillRect(x + w - 7, y + 6, 1, h - 12, inner);
    }

    /**
     * Horizontal separator line with diamond ornaments at the ends.
     */
    public static void drawSeparator(UIRenderer ui, int alpha, float cx, float y, float halfW) {
        int c = col(alpha, 180, ACCENT);
        int c2 = col(alpha, 90, ACCENT);
        ui.fillRect(cx - halfW, y, halfW * 2, 1, c2);
        ui.fillRect(cx - halfW - 2, y - 1, 2, 3, c);
        ui.fillRect(cx + halfW, y - 1, 2, 3, c);
        ui.fillRect(cx - 1, y - 2, 2, 1, c);
        ui.fillRect(cx - 1, y + 2, 2, 1, c);
        ui.fillRect(cx - 1, y - 1, 2, 3, c);
    }

    /**
     * Full-screen sunset backdrop shared by every menu screen. Includes the
     * sky gradient, a soft sun glow, and two rows of parallax hills. The
     * result is dimmed so UI content in front stays readable.
     */
    public static void drawBackdrop(UIRenderer ui, int w, int h, double time) {
        ui.fillGradientV(0, 0, w, h, SKY_TOP, SKY_BOTTOM);

        // Soft pulsing sun
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 1.1);
        int sx = (int) (w * 0.72f);
        int sy = h - 180;
        for (int r = 4; r >= 1; r--) {
            int a = (int) ((6 + 3 * pulse) / r) << 24;
            ui.fillRect(sx - r * 6, sy - r * 6, r * 12, r * 12, a | SUNSET_GLOW);
        }
        ui.fillRect(sx - 10, sy - 10, 20, 20, 0xFF000000 | SUNSET_CORE);
        ui.fillRect(sx - 10, sy - 10, 20, 4, 0xFF000000 | SUNSET_HI);

        // Horizon silhouette
        ui.fillRect(0, h - 130, w, 130, 0xFF000000 | 0x1A2038);

        // Soft dimming overlay so the UI is always readable
        ui.fillRect(0, 0, w, h, 0x30000000);
    }

    /**
     * A dark wash used when a menu is overlaid on top of the live world.
     */
    public static void drawWorldOverlay(UIRenderer ui, int w, int h) {
        ui.fillRect(0, 0, w, h, OVERLAY);
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
}
