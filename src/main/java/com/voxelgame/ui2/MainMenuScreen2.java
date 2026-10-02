package com.voxelgame.ui2;

import com.voxelgame.ui.FontRenderer;

import java.util.Random;

import static com.voxelgame.core.Language.tr;

/**
 * The title screen, Workshop edition.
 *
 * Deliberately NOT the vanilla centred stack: the interface lives on a tall
 * workbench board hugging the left edge — brass plaque on top, the action
 * planks anchored to its bottom — while the orbiting world panorama owns
 * the whole right side of the frame. Paper tags (studio, credits) float
 * over the scenery on the right.
 */
public class MainMenuScreen2 extends Scene {

    public interface Callbacks {
        void onSingleplayer();

        void onMultiplayer();

        void onOptions();

        void onLanguage();

        void onQuit();

        String versionString();

        /** Selected wood-finish index (see Settings.menuTheme). */
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

    private static final float OPEN_FADE = 1.1f;

    private final Callbacks callbacks;
    private final String splash;
    /** Warm cream fade that hides the first chunk-streaming frames. */
    private float openTime = 0;

    private Panel board;

    public MainMenuScreen2(Callbacks callbacks) {
        this.callbacks = callbacks;
        splash = SPLASHES[new Random().nextInt(SPLASHES.length)];
    }

    @Override public boolean rendersWorld() { return false; }

    @Override public boolean closableWithEscape() { return false; }

    @Override
    protected void build() {
        UiTheme.INSTANCE.setFinishIndex(callbacks.menuTheme());

        int boardW = Math.min(300, Math.max(240, width - 240));
        int boardX = 20;
        int boardY = 20;
        int boardH = height - 40;

        board = new Panel(boardX, boardY, boardW, boardH);
        add(board);

        // Action planks anchored to the bottom of the bench
        int btnH = height < 300 ? 24 : 28;
        int gap = 8;
        String[] keys = {"menu.singleplayer", "menu.multiplayer", "menu.options",
            "menu.language", "menu.quit"};
        Runnable[] actions = {
            callbacks::onSingleplayer, callbacks::onMultiplayer,
            callbacks::onOptions, callbacks::onLanguage, callbacks::onQuit
        };
        int btnW = boardW - 28;
        int y = boardY + boardH - 14 - btnH;
        float delay = 0.25f;
        for (int i = keys.length - 1; i >= 0; i--) {
            Button2 b = new Button2(tr(keys[i]), btnW, btnH, actions[i]);
            b.x = boardX + 14;
            b.y = y;
            b.danger = i == keys.length - 1;
            b.appearDelay = delay;
            board.add(b);
            y -= btnH + gap;
            delay += 0.06f;
        }

        // Timber-finish chip floats over the panorama, top right
        String badge = UiTheme.INSTANCE.finish().badge;
        Button2 chip = new Button2(badge, UiDraw.fontWidth(badge) + 34, 22,
            callbacks::onOptions);
        chip.x = width - chip.width - 12;
        chip.y = 12;
        chip.appearDelay = 0.5f;
        add(chip);
    }

    @Override
    public void update(double dt) {
        super.update(dt);
        openTime += (float) dt;
    }

    @Override
    protected void renderBackground(UiDraw d) {
        // Warm wash over the panorama so the left bench reads, but the
        // world stays the star on the right
        d.ui.fillGradientV(0, 0, width, height,
            d.aAlpha(0x5A261A10), d.aAlpha(0x8A261A10));

        // Fade in from warm cream while the spawn chunks stream in
        float t = Math.min(1f, openTime / OPEN_FADE);
        int a = (int) ((1f - UiTheme.easeOutCubic(t)) * 255);
        if (a > 0) d.ui.fillRect(0, 0, width, height, (a << 24) | 0xE8D9B8);
    }

    @Override
    protected void renderForeground(UiDraw d) {
        drawBench(d);
        drawRightSideTags(d);
    }

    /** Brass plaque with the wordmark, top of the bench. */
    private void drawBench(UiDraw d) {
        float px = board.x + (1f - board.appear01) * 6;
        float py = board.y + (1f - board.appear01) * 6;
        float pw = board.width;
        int scale = pw >= 280 ? 3 : 2;

        // Wordmark carved into the plaque
        d.textScaled(TITLE, board.x + 14, py + 14, scale, UiTheme.INK);
        d.textScaled(TITLE, board.x + 13, py + 13, scale, UiTheme.TEXT_ON_WOOD);
        int lw = d.font.scaledWidth(TITLE, scale);
        d.brassLine(board.x + 14, py + 17 + scale * FontRenderer.GLYPH_H,
            Math.min(lw, pw - 28));

        String sub = spaced(SUBTITLE, 2);
        d.text(sub, board.x + 14, py + 23 + scale * FontRenderer.GLYPH_H,
            UiTheme.BRASS_LIGHT);

        // Splash pulses on the plaque
        float pulse = 0.7f + 0.3f * (float) Math.sin(d.time * 2.2);
        d.textShadow(splash, board.x + 14, py + 36 + scale * FontRenderer.GLYPH_H,
            UiTheme.fade(UiTheme.BRASS_LIGHT, pulse));
    }

    /** Paper tags over the panorama: studio top-right, credits bottom-right. */
    private void drawRightSideTags(UiDraw d) {
        // Studio tag under the timber chip
        String studio = spaced(STUDIO, 1);
        int tagW = d.font.width(studio) + 16;
        d.paper(width - tagW - 12, 40, tagW, 18);
        d.text(studio, width - tagW - 4, 45, UiTheme.INK);

        // Credits bottom-right, floating over the world
        d.textShadow(callbacks.versionString(),
            width - 12 - d.font.width(callbacks.versionString()),
            height - 26, UiTheme.TEXT_ON_WOOD);
        d.textShadow(COPYRIGHT,
            width - 12 - d.font.width(COPYRIGHT), height - 14, UiTheme.TEXT_ON_WOOD_DIM);
    }

    /** Letter-spaced copy of a string (for the subtitle/tags). */
    private static String spaced(String text, int extra) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (i < text.length() - 1) {
                sb.append(" ".repeat(Math.max(1, extra)));
            }
        }
        return sb.toString();
    }
}
