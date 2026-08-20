package com.voxelgame.ui.screen;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;
import com.voxelgame.ui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.voxelgame.core.Language.tr;

public class MainMenuScreen extends Screen {

    public interface Callbacks {
        void onSingleplayer();
        void onMultiplayer();
        void onOptions();
        void onLanguage();
        void onQuit();
        String versionString();
        /** Selected main-menu theme index (see {@link Settings#menuTheme}). */
        int menuTheme();
    }

    private static final String TITLE = "VOXELCRAFT";
    private static final String SUBTITLE = "VOXEL ENGINE";
    private static final String STUDIO = "VOXEL STUDIOS";
    private static final String COPYRIGHT = "\u00A9 2026 Voxel Studios";

    private static final String[] SPLASHES = {
        "Procedurally generated!",
        "No Mojang assets!",
        "Watch out for caves!",
        "Dig deeper!",
        "Blocks, blocks, blocks",
        "Hand-drawn font!",
        "100% organic voxels!",
        "Now with more biomes!",
        "Made with LWJGL!",
        "Also try Terraria!",
        "OpenGL 3.3!",
        "Java powered!",
        "Greedy meshing!",
        "Infinite worlds!",
        "Now with shadows!",
        "Water simulation!",
    };

    private static final float ANIM_DURATION = 0.6f;
    private static final float ANIM_STAGGER = 0.1f;
    private static final int BLOCK = 8;

    private static final String[] GLOBE = {
            "..######..",
            ".#..##..#.",
            "#...##...#",
            "#...##...#",
            "##########",
            "#...##...#",
            "#...##...#",
            ".#..##..#.",
            "..######..",
    };

    /**
     * A complete look for the main-menu backdrop: sky, stars, sun/moon,
     * clouds, hill silhouettes, fireflies and vignette strength. Theme 0
     * (Sunset) is the original look, theme 1 is the AMOLED true-black look.
     */
    private static final class Theme {
        final int skyTop, skyBottom;
        final int starColor, starAlpha;
        final boolean hasSun;
        final int sunCore, sunGlow, sunHi;
        final int cloud1, cloud2;
        final float cloudFactor;
        final int hillFar, hillMid, hillMidTop, hillNear, hillNearTop;
        final int fireflyColor, fireflyAlpha;
        final int vigTop, vigSide, vigRange;
        final int glowA, glowB;
        final String badge;
        Theme(int skyTop, int skyBottom, int starColor, int starAlpha,
              boolean hasSun, int sunCore, int sunGlow, int sunHi,
              int cloud1, int cloud2, float cloudFactor,
              int hillFar, int hillMid, int hillMidTop, int hillNear, int hillNearTop,
              int fireflyColor, int fireflyAlpha,
              int vigTop, int vigSide, int vigRange,
              int glowA, int glowB, String badge) {
            this.skyTop = skyTop; this.skyBottom = skyBottom;
            this.starColor = starColor; this.starAlpha = starAlpha;
            this.hasSun = hasSun;
            this.sunCore = sunCore; this.sunGlow = sunGlow; this.sunHi = sunHi;
            this.cloud1 = cloud1; this.cloud2 = cloud2; this.cloudFactor = cloudFactor;
            this.hillFar = hillFar;
            this.hillMid = hillMid; this.hillMidTop = hillMidTop;
            this.hillNear = hillNear; this.hillNearTop = hillNearTop;
            this.fireflyColor = fireflyColor; this.fireflyAlpha = fireflyAlpha;
            this.vigTop = vigTop; this.vigSide = vigSide; this.vigRange = vigRange;
            this.glowA = glowA; this.glowB = glowB;
            this.badge = badge;
        }
    }

    private static final Theme[] THEMES = {
        // 0 - Sunset: original twilight look
        new Theme(0xFF141B33, 0xFF6E4128,
            0xE8ECF7, 160,
            true, 0xE9B35B, 0xD98A3A, 0xF4D08A,
            0xE8D8CC, 0xD8C4B4, 1.0f,
            0xFF35406B, 0xFF272F52, 0xFF3A4670, 0xFF1A2038, 0xFF3E5A50,
            0xE9B35B, 200,
            80, 60, 120,
            16, 8, "SUNSET"),
        // 1 - AMOLED: every pixel off except faint stars and near-black hills
        new Theme(0xFF000000, 0xFF000000,
            0xB8C4DC, 56,
            false, 0, 0, 0,
            0x000000, 0x000000, 0.0f,
            0xFF05060C, 0xFF080A14, 0xFF0C1020, 0xFF0B0E1A, 0xFF131A2E,
            0xE9B35B, 40,
            110, 80, 140,
            5, 3, "AMOLED"),
        // 2 - Day: bright clear noon sky
        new Theme(0xFF4FA3E8, 0xFFCBE8F5,
            0xFFFFFF, 0,
            true, 0xFFFFFF, 0xE8F0FF, 0xFFF8E8,
            0xFFFFFF, 0xE8ECF5, 1.0f,
            0xFF6E9FC4, 0xFF5E9A6E, 0xFF8CB45C, 0xFF3E6A46, 0xFF6AAE52,
            0xE9B35B, 0,
            50, 35, 120,
            16, 8, "DAY"),
        // 3 - Night: deep navy with a bright moon and dense stars
        new Theme(0xFF04060E, 0xFF0C1224,
            0xE8EEFF, 230,
            true, 0xD8E0F0, 0x5A6A9A, 0xF0F4FF,
            0x5A6478, 0x4A5468, 0.6f,
            0xFF10182E, 0xFF0A1020, 0xFF182440, 0xFF070C18, 0xFF101A30,
            0xE9B35B, 60,
            90, 65, 140,
            10, 6, "NIGHT"),
        // 4 - Aurora: arctic night with a bioluminescent teal-green glow
        new Theme(0xFF050E1E, 0xFF0E2A2E,
            0xE0F0E8, 200,
            false, 0, 0, 0,
            0x2A4A52, 0x223A42, 0.5f,
            0xFF0A1A22, 0xFF0E2428, 0xFF1A3A36, 0xFF08141A, 0xFF0F2E28,
            0x7FFF8F, 120,
            90, 60, 130,
            12, 7, "AURORA"),
        // 5 - Desert: hot haze over a clear noon sky
        new Theme(0xFF4F9FD8, 0xFFF2D9A0,
            0xFFFFFF, 0,
            true, 0xFFFFFF, 0xFFE8B060, 0xFFFFF0E0,
            0xFFFFFF, 0xEFE8D8, 1.0f,
            0xFFC8A060, 0xFFD4A84E, 0xFFE8C060, 0xFFA87A38, 0xFFE0B858,
            0xE9B35B, 0,
            40, 25, 110,
            16, 8, "DESERT"),
        // 6 - Dawn: soft pink-purple morning sky
        new Theme(0xFF8A5AA8, 0xFFF2A98C,
            0xFFFFFF, 30,
            true, 0xF2D8B0, 0xF0A878, 0xFFFFF0E0,
            0xFFFFFF, 0xF2E8E0, 1.0f,
            0xFF7A5A78, 0xFF6E4A6A, 0xFF8A6A8A, 0xFF523A56, 0xFF7A5A72,
            0xE9B35B, 60,
            60, 40, 120,
            14, 7, "DAWN"),
        // 7 - Void: alien dusk, violet stars and a magenta sun
        new Theme(0xFF0A0418, 0xFF1E0E3E,
            0xD0C8F0, 200,
            true, 0xC88AF0, 0x7A3AA8, 0xF0D8FF,
            0x3A2A5A, 0x32264E, 0.5f,
            0xFF140A2C, 0xFF1C1040, 0xFF2A1A5A, 0xFF0E0820, 0xFF241448,
            0xC88AF0, 160,
            100, 70, 150,
            10, 6, "VOID"),
    };

    private final Callbacks callbacks;
    private double time = 0;
    private final Random rnd = new Random(42);
    private float[] buttonAnimProgress;

    private final List<Star> stars = new ArrayList<>();
    private final List<Mote> fireflies = new ArrayList<>();
    private final List<Cloud> clouds = new ArrayList<>();
    private int[] hillFar, hillMid, hillNear;

    private String splash;
    private float splashPhase = 0;

    private static class Star { float x, y, size, tw, phase; }
    private static class Mote { float x, y, vx, vy, size, tw, phase; }
    private static class Cloud { float x, y, w, speed, alpha; }

    public MainMenuScreen(Callbacks callbacks) {
        this.callbacks = callbacks;
        splash = SPLASHES[rnd.nextInt(SPLASHES.length)];
    }

    @Override public boolean rendersWorld() { return false; }
    @Override public boolean closableWithEscape() { return false; }

    private static String label(String key, String fallback) {
        String s = tr(key);
        return (s == null || s.isEmpty()) ? fallback : s;
    }

    // ----- Button styles -------------------------------------------------

    private class MenuButton extends Button {
        private final String index;
        MenuButton(int x, int y, int w, int h, String text, String index, Button.Action onClick) {
            super(x, y, w, h, text, onClick);
            this.index = index;
        }
        @Override
        public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
            boolean hov = mx >= x && mx < x + width && my >= y && my < y + height;
            MenuTheme.drawPanel(ui, alpha, x, y, width, height, hov);
            String text = getLabel();
            int tw = font.scaledWidth(text, 1);
            int ty = y + (height - FontRenderer.GLYPH_H) / 2;
            int textCol = hov ? MenuTheme.TEXT_BRIGHT : MenuTheme.TEXT_LABEL;
            textCol = MenuTheme.col(alpha, 255, textCol & 0xFFFFFF);
            font.drawWithShadow(ui, text, x + (width - tw) / 2, ty, textCol);
            if (hov) {
                int ac = MenuTheme.col(alpha, 255, MenuTheme.ACCENT);
                font.drawWithShadow(ui, "\u25B6", x + width - 16, ty, ac);
            }
        }
    }

    private class DisabledMenuButton extends Button {
        DisabledMenuButton(int x, int y, int w, int h, String text) {
            super(x, y, w, h, text, b -> {});
            this.enabled = false;
        }
        @Override
        public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
            ui.fillRect(x, y, width, height, MenuTheme.col(alpha, 160, MenuTheme.PANEL_BG));
            int border = MenuTheme.col(alpha, 255, MenuTheme.PANEL_BORDER);
            ui.fillRect(x, y, width, 1, border);
            ui.fillRect(x, y + height - 1, width, 1, border);
            ui.fillRect(x, y, 1, height, border);
            ui.fillRect(x + width - 1, y, 1, height, border);
            String text = getLabel();
            int tw = font.scaledWidth(text, 1);
            int ty = y + (height - FontRenderer.GLYPH_H) / 2;
            font.drawWithShadow(ui, text, x + (width - tw) / 2, ty,
                MenuTheme.col(alpha, 120, MenuTheme.TEXT_DIM & 0xFFFFFF));
        }
    }

    private class IconButton extends Button {
        IconButton(int x, int y, int w, int h, Button.Action onClick) {
            super(x, y, w, h, "", onClick);
        }
        @Override
        public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
            boolean hov = mx >= x && mx < x + width && my >= y && my < y + height;
            MenuTheme.drawPanel(ui, alpha, x, y, width, height, hov);
            int px = 2;
            int ox = x + (width - GLOBE[0].length() * px) / 2;
            int oy = y + (height - GLOBE.length * px) / 2;
            int c = MenuTheme.col(alpha, 255, hov ? MenuTheme.ACCENT : 0x9FB4CE);
            for (int r = 0; r < GLOBE.length; r++) {
                String row = GLOBE[r];
                for (int i = 0; i < row.length(); i++) {
                    if (row.charAt(i) == '#') ui.fillRect(ox + i * px, oy + r * px, px, px, c);
                }
            }
        }
    }

    // ----- Layout -------------------------------------------------------

    @Override
    protected void layout() {
        int bw = 300, bh = 34, spacing = 42;
        int cx = (width - bw) / 2;
        int startY = height / 2 + 10;
        widgets.clear();

        add(new MenuButton(cx, startY, bw, bh,
                label("menu.singleplayer", "Singleplayer"), "01",
                b -> callbacks.onSingleplayer())).animDelay = 0;
        add(new MenuButton(cx, startY + spacing, bw, bh,
                label("menu.multiplayer", "Multiplayer"), "02",
                b -> callbacks.onMultiplayer())).animDelay = ANIM_STAGGER;
        add(new MenuButton(cx, startY + spacing * 2, bw, bh,
                label("menu.options", "Options"), "03",
                b -> callbacks.onOptions())).animDelay = ANIM_STAGGER * 2;
        add(new MenuButton(cx, startY + spacing * 3, bw, bh,
                label("menu.quit", "Quit"), "04",
                b -> callbacks.onQuit())).animDelay = ANIM_STAGGER * 3;
        add(new IconButton(width - 40, height - 40, 28, 28,
                b -> callbacks.onLanguage())).animDelay = ANIM_STAGGER * 4;

        buttonAnimProgress = new float[widgets.size()];
        initScene();
    }

    private void initScene() {
        stars.clear();
        for (int i = 0; i < 70; i++) {
            Star s = new Star();
            s.x = rnd.nextFloat() * width;
            s.y = rnd.nextFloat() * height * 0.55f;
            s.size = 1 + rnd.nextFloat();
            s.tw = 0.5f + rnd.nextFloat() * 2.5f;
            s.phase = rnd.nextFloat() * 6.28f;
            stars.add(s);
        }
        fireflies.clear();
        for (int i = 0; i < 22; i++) {
            Mote m = new Mote();
            m.x = rnd.nextFloat() * width;
            m.y = height * 0.55f + rnd.nextFloat() * height * 0.4f;
            m.vx = (rnd.nextFloat() - 0.5f) * 6;
            m.vy = (rnd.nextFloat() - 0.5f) * 4;
            m.size = 1 + rnd.nextFloat();
            m.tw = 1 + rnd.nextFloat() * 2;
            m.phase = rnd.nextFloat() * 6.28f;
            fireflies.add(m);
        }
        clouds.clear();
        for (int i = 0; i < 4; i++) {
            Cloud c = new Cloud();
            c.x = rnd.nextFloat() * width;
            c.y = 40 + rnd.nextFloat() * height * 0.3f;
            c.w = 60 + rnd.nextFloat() * 80;
            c.speed = 4 + rnd.nextFloat() * 6;
            c.alpha = 0.10f + rnd.nextFloat() * 0.12f;
            clouds.add(c);
        }
        int count = width / BLOCK + 6;
        hillFar = genHeights(count, 150, 34);
        hillMid = genHeights(count, 104, 28);
        hillNear = genHeights(count, 58, 20);
    }

    private int[] genHeights(int count, int base, int vary) {
        int[] h = new int[count];
        int cur = base;
        for (int i = 0; i < count; i++) {
            cur += rnd.nextInt(5) - 2;
            cur = Math.max(base - vary, Math.min(base + vary, cur));
            h[i] = cur;
        }
        for (int i = 0; i < count; i++) {
            h[i] = (h[i] + h[(i + 1) % count]) / 2;
        }
        return h;
    }

    @Override
    public void update(double deltaTime) {
        time += deltaTime;
        splashPhase += (float) deltaTime;
        for (int i = 0; i < buttonAnimProgress.length && i < widgets.size(); i++) {
            if (widgets.get(i) instanceof Button btn) {
                float t = (float) (time - btn.animDelay);
                buttonAnimProgress[i] = easeOutCubic(Math.max(0, Math.min(1, t / ANIM_DURATION)));
            }
        }
        float dt = (float) deltaTime;
        for (Cloud c : clouds) {
            c.x -= c.speed * dt;
            if (c.x < -c.w - 40) c.x = width + 40;
        }
        for (Mote m : fireflies) {
            m.x += (m.vx + (float) Math.sin(time * 0.7 + m.phase) * 6) * dt;
            m.y += (m.vy + (float) Math.cos(time * 0.6 + m.phase) * 5) * dt;
            if (m.x < -10) m.x = width + 10;
            if (m.x > width + 10) m.x = -10;
            if (m.y < height * 0.5f) m.y = height * 0.5f;
            if (m.y > height + 10) m.y = height - 10;
        }
    }

    private float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 3);
    }

    private float logoY() {
        return Math.max(26, height / 2f - 165);
    }

    private Theme currentTheme() {
        return THEMES[Math.max(0, callbacks.menuTheme()) % THEMES.length];
    }

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets tex) {
        Theme t = currentTheme();
        ui.fillGradientV(0, 0, width, height, t.skyTop, t.skyBottom);

        if (t.starAlpha > 0) {
            for (Star s : stars) {
                float tw = 0.5f + 0.5f * (float) Math.sin(time * s.tw + s.phase);
                int a = (int) (tw * t.starAlpha) << 24;
                ui.fillRect(s.x, s.y, s.size, s.size, a | t.starColor);
            }
        }

        if (t.hasSun) drawSun(ui, t);

        if (t.cloudFactor > 0) {
            for (Cloud c : clouds) {
                int a = (int) (c.alpha * t.cloudFactor * 255) << 24;
                int cw = (int) c.w;
                ui.fillRect(c.x, c.y, cw, 6, a | t.cloud1);
                ui.fillRect(c.x + 8, c.y - 5, (int) (cw * 0.6f), 5, a | t.cloud1);
                ui.fillRect(c.x + (int) (cw * 0.3f), c.y + 6, (int) (cw * 0.5f), 4, a | t.cloud2);
            }
        }

        drawHills(ui, hillFar, 0.4f, t.hillFar, 0);
        drawHills(ui, hillMid, 1.0f, t.hillMid, t.hillMidTop);
        drawHills(ui, hillNear, 2.0f, t.hillNear, t.hillNearTop);

        if (t.fireflyAlpha > 0) {
            for (Mote m : fireflies) {
                float tw = 0.5f + 0.5f * (float) Math.sin(time * m.tw + m.phase);
                int a = (int) (tw * tw * t.fireflyAlpha) << 24;
                ui.drawParticle(m.x, m.y, m.size, a | t.fireflyColor);
            }
        }

        // Vignette
        int band = 8, range = t.vigRange;
        for (int i = 0; i < range; i += band) {
            float f = 1 - i / (float) range;
            ui.fillRect(0, i, width, band, (int) (f * f * t.vigTop) << 24);
            ui.fillRect(0, height - i - band, width, band, (int) (f * f * t.vigTop) << 24);
            ui.fillRect(i, 0, band, height, (int) (f * f * t.vigSide) << 24);
            ui.fillRect(width - i - band, 0, band, height, (int) (f * f * t.vigSide) << 24);
        }
    }

    private void drawSun(UIRenderer ui, Theme t) {
        int sx = (int) (width * 0.74f);
        int sy = height - 200;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 1.2);
        for (int r = 5; r >= 1; r--) {
            int a = (int) ((8 + 4 * pulse) / r) << 24;
            ui.fillRect(sx - r * 7, sy - r * 7, r * 14, r * 14, a | t.sunGlow);
        }
        ui.fillRect(sx - 12, sy - 12, 24, 24, 0xFF000000 | t.sunCore);
        ui.fillRect(sx - 12, sy - 12, 24, 5, 0xFF000000 | t.sunHi);
    }

    private void drawHills(UIRenderer ui, int[] heights, float speed, int color, int topColor) {
        int count = heights.length;
        int off = (int) (time * speed) % count;
        for (int i = 0; i * BLOCK < width + BLOCK; i++) {
            int hgt = heights[(i + off) % count];
            int x = i * BLOCK;
            int y = height - hgt;
            ui.fillRect(x, y, BLOCK, hgt, color);
            if (topColor != 0) ui.fillRect(x, y, BLOCK, 2, topColor);
        }
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {
        // Studio mark (top-left)
        ui.fillRect(10, 12, 6, 6, 0xFF000000 | MenuTheme.ACCENT);
        ui.fillRect(6, 14, 3, 2, 0xCC000000 | MenuTheme.ACCENT);
        ui.fillRect(17, 14, 3, 2, 0xCC000000 | MenuTheme.ACCENT);
        ui.fillRect(12, 8, 2, 3, 0xCC000000 | MenuTheme.ACCENT);
        ui.fillRect(12, 19, 2, 3, 0xCC000000 | MenuTheme.ACCENT);
        drawSpaced(ui, font, STUDIO, 26, 12, 1, 2,
            0xFF000000 | MenuTheme.TEXT_DIM);

        // Small badge naming the active theme so it is visible and
        // discoverable as a setting
        drawSpaced(ui, font, currentTheme().badge, width - 64, 14, 1, 1,
            (0x66 << 24) | (MenuTheme.TEXT_DIM & 0xFFFFFF));

        drawLogoChunky(ui, font);

        // Splash text - angled, pulsing, right of the logo
        drawSplash(ui, font);

        // Buttons with staggered slide-in animation
        for (int i = 0; i < widgets.size(); i++) {
            Widget w = widgets.get(i);
            float pr = buttonAnimProgress[i];
            w.y = (int) (w.baseY + (1f - pr) * 24);
            w.alpha = (int) (pr * 255) << 24;
            w.updateHover(mx, my);
            w.render(ui, font, tex, mx, my);

            if (w instanceof MenuButton mb) {
                boolean hov = mx >= w.x && mx < w.x + w.width && my >= w.y && my < w.y + w.height;
                int a = (int) (pr * 255) << 24;
                font.drawWithShadow(ui, mb.index, w.x - 30,
                        w.y + (w.height - FontRenderer.GLYPH_H) / 2,
                        a | (hov ? MenuTheme.ACCENT : MenuTheme.TEXT_DIM));
            }
        }

        // Bottom bar
        ui.fillRect(0, height - 48, width, 1, 0x660A0E1C);
        font.drawWithShadow(ui, callbacks.versionString(), 10, height - 30,
            0xFF000000 | MenuTheme.TEXT_DIM);
        int cw = font.scaledWidth(COPYRIGHT, 1);
        font.drawWithShadow(ui, COPYRIGHT, width - 56 - cw, height - 30,
            0xFF000000 | MenuTheme.TEXT_DIM);
    }

    private void drawSplash(UIRenderer ui, FontRenderer font) {
        int scale = 4;
        float logoX = (width - font.scaledWidth(TITLE, scale)) / 2f;
        float logoW = font.scaledWidth(TITLE, scale);
        float logoY = logoY();
        float logoBottom = logoY + scale * FontRenderer.GLYPH_H;

        // Splash pulsing text below the subtitle
        float splashPulse = 0.7f + 0.3f * (float) Math.sin(splashPhase * 2.2);
        int sw = font.scaledWidth(splash, 1);

        // Position: below subtitle, right of center
        float sx = logoX + logoW + 6;
        float sy = logoBottom + 16;

        // If it would run off the right edge, move it below instead
        if (sx + sw > width - 20) {
            sx = (width - sw) / 2f;
            sy = logoBottom + 32;
        }

        int a = (int) (splashPulse * 230) << 24;
        int sc = a | MenuTheme.SUNSET_HI;
        font.drawWithShadow(ui, splash, sx, sy, sc);
    }

    private void drawLogoChunky(UIRenderer ui, FontRenderer font) {
        int scale = 4;
        float x = (width - font.scaledWidth(TITLE, scale)) / 2f;
        float y = logoY();

        font.drawScaled(ui, TITLE, x + 3, y + 3, scale, 0xAA0A0E1C);
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 1.6);
        Theme t = currentTheme();
        int glowBase = t.glowA + t.glowB * (int) pulse;
        for (int i = 2; i > 0; i--) {
            int g = (glowBase / i << 24) | MenuTheme.SUNSET_GLOW;
            font.drawScaled(ui, TITLE, x - i, y - i, scale, g);
            font.drawScaled(ui, TITLE, x + i, y + i, scale, g);
        }
        font.drawScaledWithShadow(ui, TITLE, x, y, scale, 0xFFFFFFFF);

        int extra = 3;
        int subW = spacedWidth(font, SUBTITLE, 1, extra);
        float sx = (width - subW) / 2f;
        float sy = y + scale * FontRenderer.GLYPH_H + 12;
        drawSpaced(ui, font, SUBTITLE, sx, sy, 1, extra,
            0xFF000000 | MenuTheme.TEXT_SECONDARY);

        float ly = sy + FontRenderer.GLYPH_H / 2f;
        ui.fillRect(sx - 44, ly, 30, 2, 0x66000000 | MenuTheme.ACCENT);
        ui.fillRect(sx + subW + 14, ly, 30, 2, 0x66000000 | MenuTheme.ACCENT);
    }

    private int spacedWidth(FontRenderer font, String text, int scale, int extra) {
        int w = -extra;
        for (int i = 0; i < text.length(); i++) {
            w += font.scaledWidth(String.valueOf(text.charAt(i)), scale) + extra;
        }
        return w;
    }

    private void drawSpaced(UIRenderer ui, FontRenderer font, String text,
                            float x, float y, int scale, int extra, int color) {
        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            font.drawScaled(ui, ch, cx, y, scale, color);
            cx += font.scaledWidth(ch, scale) + extra;
        }
    }
}
