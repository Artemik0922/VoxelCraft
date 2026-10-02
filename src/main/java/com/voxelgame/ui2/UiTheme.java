package com.voxelgame.ui2;

/**
 * Design tokens for the "Workshop" interface: a craftsman's bench of dark
 * walnut and oak boards, kraft-paper notes and brass fittings.
 *
 * One warm material language everywhere - no light/dark context switching.
 * The user's menuTheme setting (0-7) selects the wood finish the furniture
 * is made of; brass stays the working accent in every finish.
 */
public final class UiTheme {

    // ------------------------------------------------------------------
    // Fixed palette - shared by every finish
    // ------------------------------------------------------------------

    /** Brass fittings: knobs, rivets, trim, selection brackets. */
    public static final int BRASS        = 0xFFC9973B;
    public static final int BRASS_LIGHT  = 0xFFEDD9A0;
    public static final int BRASS_DARK   = 0xFF8A6420;
    public static final int BRASS_DIM    = 0xFF6E5220;

    /** Kraft paper: notes, forms, tooltips, the F3 sheet. */
    public static final int PAPER        = 0xFFE8D9B8;
    public static final int PAPER_DARK   = 0xFFD6C29A;
    public static final int PAPER_SHADOW = 0x5A2A1D12;

    /** Ink stamped on paper and labels carved into wood. */
    public static final int INK          = 0xFF2A1D12;
    public static final int INK_SOFT     = 0xFF4A3826;

    /** Text on wood: warm parchment tone. */
    public static final int TEXT_ON_WOOD = 0xFFF2E6C8;
    public static final int TEXT_ON_WOOD_DIM = 0xFFC9B68F;

    /** Ember red for destructive actions and low hearts. */
    public static final int EMBER        = 0xFFC4552F;
    public static final int EMBER_BRIGHT = 0xFFE8764A;

    /** Sky/air accent for bubbles and XP. */
    public static final int VERDIGRIS    = 0xFF7FA85A;

    /** Screen veil over gameplay (pause/death): warm dark, not grey. */
    public static final int VEIL         = 0x90261A10;

    // ------------------------------------------------------------------
    // Metrics (virtual GUI pixels)
    // ------------------------------------------------------------------

    public static final int WOOD_BORDER = 6;   // nine-slice corner of the boards
    public static final int WIDGET_TEX  = 24;  // source size of widget nine-slices
    public static final int PAPER_TEX   = 24;
    public static final int PAPER_BORDER = 5;
    public static final int SLOT_SIZE = 18;
    public static final int ICON_SIZE = 16;
    public static final int HEART_SIZE = 9;
    public static final int CROSSHAIR_SIZE = 16;

    // ------------------------------------------------------------------
    // Wood finishes - the 8 menu themes, now as timber
    // ------------------------------------------------------------------

    public static class Finish {
        public final String badge;
        public final int base;   // plank body
        public final int light;  // grain highlight / top bevel
        public final int dark;   // grain shadow / bottom bevel
        public final int deep;   // card backing (darker, cooler)

        Finish(String badge, int base, int light, int dark, int deep) {
            this.badge = badge;
            this.base = base;
            this.light = light;
            this.dark = dark;
            this.deep = deep;
        }
    }

    public static final Finish[] FINISHES = {
        new Finish("ОРЕХ",     0xFF6B4A2B, 0xFF8A6540, 0xFF4A3220, 0xFF332314),
        new Finish("ДУБ",      0xFF8A6535, 0xFFAB8552, 0xFF63451F, 0xFF453117),
        new Finish("БЕРЁЗА",   0xFFC9AE7C, 0xFFE4CE9E, 0xFF9A7D4C, 0xFF6E5836),
        new Finish("ВЕНГЕ",    0xFF4A3220, 0xFF684E34, 0xFF33220F, 0xFF221608),
        new Finish("КЕДР",     0xFF7D4E33, 0xFF9C6B48, 0xFF5A3520, 0xFF3D2314),
        new Finish("ПАЛИСАНДР",0xFF5C3830, 0xFF7C5248, 0xFF3E241E, 0xFF2A1712),
        new Finish("КЛЁН",     0xFFB98F55, 0xFFD9B17A, 0xFF8F6A38, 0xFF644925),
        new Finish("ЧЕРНОСЛИВ",0xFF43333E, 0xFF61495A, 0xFF2C2028, 0xFF1C1418),
    };

    /** Default finish; Game syncs this from the menuTheme setting per frame. */
    public static UiTheme INSTANCE = new UiTheme(0);

    private Finish finish = FINISHES[0];

    public UiTheme(int finishIndex) {
        setFinishIndex(finishIndex);
    }

    public void setFinishIndex(int index) {
        finish = FINISHES[Math.floorMod(index, FINISHES.length)];
    }

    public Finish finish() { return finish; }

    /** Smoothly mix two ARGB colours (t in 0..1). */
    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ab = (b >>> 24) & 0xFF;
        int ar = (a >> 16) & 0xFF, br = (b >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF, bg = (b >> 8) & 0xFF;
        int an = a & 0xFF, bn = b & 0xFF;
        int aOut = Math.round(aa + (ab - aa) * t);
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int n = Math.round(an + (bn - an) * t);
        return (aOut << 24) | (r << 16) | (g << 8) | n;
    }

    /** Scale an ARGB colour's alpha by a float factor (0..1). */
    public static int fade(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Perceived brightness of the active finish's base colour (0..1). */
    public float finishLuminance() {
        int rgb = finish.base;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
    }

    /** Label ink that stays readable on the active finish. */
    public int buttonTextColor() {
        return finishLuminance() > 0.55f ? INK : TEXT_ON_WOOD;
    }

    /** Dimmed variant of the adaptive label ink. */
    public int buttonTextDimColor() {
        return finishLuminance() > 0.55f ? INK_SOFT : TEXT_ON_WOOD_DIM;
    }

    public static float easeOutCubic(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (float) Math.pow(1f - t, 3);
    }
}
