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

/**
 * The title screen.
 *
 * The backdrop is the live 3D world: a panorama camera slowly orbits the
 * spawn point (see PanoramaCamera) while the full scene pass — sky, sun,
 * clouds, terrain — renders underneath this screen. The screen itself only
 * lays cinematic scrims over that footage so the logo and menu stay crisp,
 * plus a fade-in from black that covers the first moments of chunk
 * streaming.
 *
 * Menu themes act as accent palettes for the interface; the backdrop itself
 * follows the world's day-night cycle.
 */
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
        "Копай глубже!",
        "Осторожно, пещеры!",
        "Бесконечные миры!",
        "Смотри под ноги!",
        "Сделано на Java!",
        "Вода теперь течёт!",
    };

    private static final float ANIM_DURATION = 0.6f;
    private static final float ANIM_STAGGER = 0.1f;
    /** Black fade-in duration that hides the first chunk-streaming frames. */
    private static final float OPEN_FADE = 1.1f;

    private static final String[] BADGES = {
        "SUNSET", "AMOLED", "DAY", "NIGHT", "AURORA", "DESERT", "DAWN", "VOID",
    };

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

    private final Callbacks callbacks;
    private double time = 0;
    private float openTime = 0;
    private final Random rnd = new Random(42);
    private float[] buttonAnimProgress;

    private String splash;
    private float splashPhase = 0;

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

    // Pixel glyphs drawn inside the menu tiles (block size = 2 px)
    private static final String[] ICON_PERSON = {
            "..##..",
            ".####.",
            "######",
            ".####.",
            "..##..",
            "..#...",
            ".#.#..",
            ".#.#..",
            ".#.#..",
    };
    private static final String[] ICON_TWO = {
            "..#..#..",
            ".###.###",
            "########",
            ".###.###",
            "..#..#..",
            ".#....#.",
            ".#.##.#.",
            ".#....#.",
            ".#....#.",
    };
    private static final String[] ICON_SLIDER = {
            "......##",
            "########",
            "......##",
            "........",
            "##......",
            "########",
            "##......",
            "........",
            "......##",
    };
    private static final String[] ICON_QUIT = {
            "........",
            ".##..##.",
            ".##..##.",
            "..####..",
            "..####..",
            ".##..##.",
            ".##..##.",
            "........",
            ".######.",
    };

    /**
     * Main-menu tile: a recessed icon socket on the left, the label, the
     * index on the right, and a fade-in accent bar + arrow on hover. All
     * hover motion is eased, nothing snaps.
     */
    private class TileButton extends Button {
        private final String index;
        private final String[] icon;
        TileButton(int x, int y, int w, int h, String text, String index,
                   String[] icon, Button.Action onClick) {
            super(x, y, w, h, text, onClick);
            this.index = index;
            this.icon = icon;
        }
        @Override
        public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
            float pr = hoverProgress();
            MenuTheme.drawPanel(ui, alpha, x, y, width, height, pr);

            // Recessed socket behind the pixel icon
            int px = 2;
            int socket = 26;
            int soX = x + 8;
            int soY = y + (height - socket) / 2;
            ui.fillRect(soX, soY, socket, socket, MenuTheme.col(alpha, 70, 0x0A0E1C));
            ui.fillRect(soX, soY + socket - 1, socket, 1, MenuTheme.col(alpha, 45, 0xFFFFFF));
            ui.fillRect(soX, soY, socket, 1, MenuTheme.col(alpha, 50, 0x000000));

            // Pixel icon, easing from steel to the accent colour on hover
            int ioX = soX + (socket - icon[0].length() * px) / 2;
            int ioY = soY + (socket - icon.length * px) / 2;
            int ic = MenuTheme.col(alpha, 255,
                MenuTheme.lerp(0xFF9FB4CE, MenuTheme.ACCENT, pr) & 0xFFFFFF);
            for (int r = 0; r < icon.length; r++) {
                String row = icon[r];
                for (int c = 0; c < row.length(); c++) {
                    if (row.charAt(c) == '#') ui.fillRect(ioX + c * px, ioY + r * px, px, px, ic);
                }
            }

            String text = getLabel();
            int zoneL = soX + socket + 8;
            int zoneR = x + width - 26;
            int tw = font.scaledWidth(text, 1);
            int tx = zoneL + Math.max(0, (zoneR - zoneL - tw) / 2);
            int ty = y + (height - FontRenderer.GLYPH_H) / 2;

            int textCol = MenuTheme.col(alpha, 255,
                MenuTheme.lerp(MenuTheme.TEXT_LABEL, MenuTheme.TEXT_BRIGHT, pr) & 0xFFFFFF);
            font.drawWithShadow(ui, text, tx, ty, textCol);

            int idxCol = MenuTheme.col(alpha, 255,
                MenuTheme.lerp(MenuTheme.TEXT_DIM, MenuTheme.ACCENT, pr) & 0xFFFFFF);
            font.drawWithShadow(ui, index, x + width - 26, ty, idxCol);
            if (pr > 0.05f) {
                int a = (int) (pr * 255);
                font.drawWithShadow(ui, "\u25B6", x + width - 14, ty,
                    (a << 24) | (MenuTheme.ACCENT & 0xFFFFFF));
            }
        }
    }

    /**
     * Compact chip at the top right showing the active theme with a swatch;
     * clicking it opens Options where the theme lives.
     */
    private class ThemeChipButton extends Button {
        ThemeChipButton(int x, int y, int w, int h, Button.Action onClick) {
            super(x, y, w, h, "", onClick);
        }
        @Override
        public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
            float pr = hoverProgress();
            MenuTheme.drawPanel(ui, alpha, x, y, width, height, pr);

            int s = 8;
            int swX = x + 8;
            int swY = y + (height - s) / 2;
            ui.fillRect(swX, swY, s, s, MenuTheme.col(alpha, 255, MenuTheme.ACCENT));
            ui.fillRect(swX, swY, s, 2, MenuTheme.col(alpha, 120, 0xFFFFFF));

            String name = currentTheme();
            int c = MenuTheme.col(alpha, 255,
                MenuTheme.lerp(MenuTheme.TEXT_LABEL, MenuTheme.ACCENT_LIGHT, pr) & 0xFFFFFF);
            font.drawWithShadow(ui, name, swX + s + 6,
                y + (height - FontRenderer.GLYPH_H) / 2, c);
            if (pr > 0.05f) {
                int a = (int) (pr * 255);
                font.drawWithShadow(ui, "\u25B6", x + 74,
                    y + (height - FontRenderer.GLYPH_H) / 2,
                    (a << 24) | (MenuTheme.ACCENT & 0xFFFFFF));
            }
        }
    }

    // ----- Layout -------------------------------------------------------

    private int cardX, cardY, cardW, cardH;

    @Override
    protected void layout() {
        widgets.clear();

        int tileW = 248, tileH = 42, gap = 12;
        int gridW = tileW * 2 + gap;
        int cardW = gridW + 32;
        int cardX = (width - cardW) / 2;

        float logoBottom = logoY() + 4 * FontRenderer.GLYPH_H + 12 + FontRenderer.GLYPH_H;
        int cardY = (int) Math.max(logoBottom + 44, height / 2 - 8);

        int cx = cardX + 16;
        int t0 = cardY + 16;
        int t1 = t0 + tileH + gap;
        int quitY = t1 + tileH + gap;

        addTile(cx, t0, tileW, tileH, "menu.singleplayer", "Singleplayer", "01", ICON_PERSON,
                b -> callbacks.onSingleplayer(), 0);
        addTile(cx + tileW + gap, t0, tileW, tileH, "menu.multiplayer", "Multiplayer", "02", ICON_TWO,
                b -> callbacks.onMultiplayer(), ANIM_STAGGER);
        addTile(cx, t1, tileW, tileH, "menu.options", "Options", "03", ICON_SLIDER,
                b -> callbacks.onOptions(), ANIM_STAGGER * 2);
        addTile(cx + tileW + gap, t1, tileW, tileH, "menu.language", "Language", "04", GLOBE,
                b -> callbacks.onLanguage(), ANIM_STAGGER * 3);

        add(new TileButton(cx, quitY, gridW, tileH,
                label("menu.quit", "Quit"), "05", ICON_QUIT,
                b -> callbacks.onQuit())).animDelay = ANIM_STAGGER * 4;

        ThemeChipButton chip = new ThemeChipButton(width - 112, 12, 100, 20,
                b -> callbacks.onOptions());
        chip.animDelay = ANIM_STAGGER * 5;
        add(chip);

        this.cardX = cardX;
        this.cardY = cardY;
        this.cardW = cardW;
        this.cardH = quitY + tileH + 16 - cardY;

        buttonAnimProgress = new float[widgets.size()];
    }

    private void addTile(int x, int y, int w, int h, String key, String fallback,
                         String index, String[] icon, Button.Action onClick, float delay) {
        add(new TileButton(x, y, w, h, label(key, fallback), index, icon, onClick)).animDelay = delay;
    }

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        time += deltaTime;
        openTime += (float) deltaTime;
        splashPhase += (float) deltaTime;
        for (int i = 0; i < buttonAnimProgress.length && i < widgets.size(); i++) {
            if (widgets.get(i) instanceof Button btn) {
                float t = (float) (time - btn.animDelay);
                buttonAnimProgress[i] = easeOutCubic(Math.max(0, Math.min(1, t / ANIM_DURATION)));
            }
        }
    }

    private float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 3);
    }

    private float logoY() {
        return Math.max(26, height / 2f - 165);
    }

    /** Applies this theme's accent palette and returns its badge name. */
    private String currentTheme() {
        int index = Math.max(0, callbacks.menuTheme()) % BADGES.length;
        MenuTheme.setThemeIndex(index);
        return BADGES[index];
    }

    // ----- Background: cinematic scrims over the live panorama ----------

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets tex) {
        currentTheme(); // keep the accent palette in sync

        // Cinematic scrims: shade the logo band and the menu band, leave the
        // middle of the orbiting world visible between them
        int mid = height * 38 / 100;
        ui.fillGradientV(0, 0, width, mid, 0x9E0A0E1C, 0x000A0E1C);
        ui.fillGradientV(0, mid, width, height - mid, 0x000A0E1C, 0xA20A0E1C);

        // Fade in from black while the spawn chunks stream in
        float t = Math.min(1f, openTime / OPEN_FADE);
        int a = (int) ((1f - easeOutCubic(t)) * 255);
        if (a > 0) ui.fillRect(0, 0, width, height, a << 24);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {
        // Studio mark (top-left) on a small dark plate so it stays readable
        // against any footage the panorama drifts past
        int studioW = spacedWidth(font, STUDIO, 1, 2);
        ui.fillRect(4, 8, studioW + 34, 19, 0x8C0A0E1C);
        ui.fillRect(4, 8, 2, 19, 0xFF000000 | MenuTheme.ACCENT);
        ui.fillRect(12, 12, 6, 6, 0xFF000000 | MenuTheme.ACCENT);
        ui.fillRect(8, 14, 3, 2, 0xCC000000 | MenuTheme.ACCENT);
        ui.fillRect(19, 14, 3, 2, 0xCC000000 | MenuTheme.ACCENT);
        ui.fillRect(14, 8, 2, 3, 0xCC000000 | MenuTheme.ACCENT);
        ui.fillRect(14, 19, 2, 3, 0xCC000000 | MenuTheme.ACCENT);
        drawSpaced(ui, font, STUDIO, 28, 12, 1, 2,
            0xFF000000 | MenuTheme.TEXT_LABEL);

        drawLogoChunky(ui, font);

        // Splash text - centred under the subtitle so it never collides
        drawSplash(ui, font);

        // Dark scrim fading in behind the menu zone: keeps tiles readable
        // against bright in-world footage without hiding it
        int scrimTop = cardY - 110;
        ui.fillGradientV(0, scrimTop, width, height - scrimTop, 0x000A0E1C, 0x780A0E1C);

        // Glass frame holding the action tiles
        MenuTheme.drawCard(ui, 0xB40A0E1C, cardX, cardY, cardW, cardH);

        // Tiles with staggered slide-in animation
        for (int i = 0; i < widgets.size(); i++) {
            Widget w = widgets.get(i);
            float pr = buttonAnimProgress[i];
            w.y = (int) (w.baseY + (1f - pr) * 24);
            w.alpha = (int) (pr * 255) << 24;
            w.updateHover(mx, my);
            w.render(ui, font, tex, mx, my);
        }

        // Bottom bar
        ui.fillRect(0, height - 48, width, 1, 0x660A0E1C);
        font.drawWithShadow(ui, callbacks.versionString(), 10, height - 30,
            0xFF000000 | MenuTheme.TEXT_SECONDARY);
        int cw = font.scaledWidth(COPYRIGHT, 1);
        font.drawWithShadow(ui, COPYRIGHT, width - 56 - cw, height - 30,
            0xFF000000 | MenuTheme.TEXT_SECONDARY);
    }

    private void drawSplash(UIRenderer ui, FontRenderer font) {
        // Centred below the subtitle divider, pulsing gently
        float pulse = 0.7f + 0.3f * (float) Math.sin(splashPhase * 2.2);
        int sw = font.scaledWidth(splash, 1);
        float sx = (width - sw) / 2f;
        float sy = subtitleY() + FontRenderer.GLYPH_H + 8;

        int a = (int) (pulse * 240) << 24;
        int sc = a | (MenuTheme.ACCENT_LIGHT & 0xFFFFFF);
        font.drawWithShadow(ui, splash, sx, sy, sc);
    }

    private void drawLogoChunky(UIRenderer ui, FontRenderer font) {
        int scale = 4;
        float x = (width - font.scaledWidth(TITLE, scale)) / 2f;
        float y = logoY();

        // One clean offset shadow - no diagonal "glow" fringing
        font.drawScaled(ui, TITLE, x + 3, y + 3, scale, 0x990A0E1C);
        font.drawScaledWithShadow(ui, TITLE, x, y, scale, 0xFFFFFFFF);

        // Accent bar centred under the title, between title and subtitle
        int lw = font.scaledWidth(TITLE, scale);
        float uy = y + scale * FontRenderer.GLYPH_H + 5;
        ui.fillRect(x + 4, uy, lw - 8, 2, 0x30000000 | MenuTheme.ACCENT);
        ui.fillRect(width / 2f - 14, uy - 1, 28, 4, 0xE6000000 | MenuTheme.ACCENT);

        int extra = 3;
        int subW = spacedWidth(font, SUBTITLE, 1, extra);
        float sx = (width - subW) / 2f;
        float sy = subtitleY();
        drawSpaced(ui, font, SUBTITLE, sx, sy, 1, extra,
            0xFF000000 | MenuTheme.TEXT_SECONDARY);

        float ly = sy + FontRenderer.GLYPH_H / 2f;
        ui.fillRect(sx - 44, ly, 30, 2, 0x88000000 | MenuTheme.ACCENT);
        ui.fillRect(sx + subW + 14, ly, 30, 2, 0x88000000 | MenuTheme.ACCENT);
    }

    private float subtitleY() {
        return logoY() + 4 * FontRenderer.GLYPH_H + 12;
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
