package com.voxelgame.ui.screen;

import com.voxelgame.core.Settings;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;

import static com.voxelgame.core.Language.tr;

/**
 * Difficulty picker. Tapping a choice applies it live and closes.
 */
public class DifficultyScreen extends Screen {

    public interface Listener {
        void onDifficultyChanged(int difficulty);
        void onClosed();
    }

    private static final int[] LEVELS = {0, 1, 2, 3};
    private static final String[] KEYS = {
        "options.difficulty.peaceful",
        "options.difficulty.easy",
        "options.difficulty.normal",
        "options.difficulty.hard"
    };
    private static final int[] COLORS = {
        0xFF4CAF50,  // peaceful green
        0xFF66BB6A,  // easy green
        MenuTheme.ACCENT,
        0xFFE53935   // hard red
    };

    private final Settings settings;
    private final Listener listener;
    private final boolean overlayWorld;
    private final boolean locked;

    public DifficultyScreen(Settings settings, Listener listener, boolean overlayWorld) {
        this(settings, listener, overlayWorld, false);
    }

    public DifficultyScreen(Settings settings, Listener listener,
                            boolean overlayWorld, boolean locked) {
        this.settings = settings;
        this.listener = listener;
        this.overlayWorld = overlayWorld;
        this.locked = locked;
    }

    @Override
    public boolean rendersWorld() { return overlayWorld; }

    @Override
    protected void layout() {
        int rowH = 22;
        int gap = 4;
        int bw = 200;
        int cardTop = Math.max(30, height / 6);
        // Controls start below the title + separator to avoid overlap
        int top = cardTop + 42;

        for (int i = 0; i < LEVELS.length; i++) {
            int level = LEVELS[i];
            String label = tr(KEYS[i]);
            boolean active = settings.difficulty == level;
            boolean disabled = locked && level != 2;
            String prefix = (active ? "\u25B6 " : "") + (disabled ? "\u2717 " : "");
            add(new Button((width - bw) / 2, top + i * (rowH + gap), bw, rowH,
                prefix + label,
                disabled ? null : b -> {
                    listener.onDifficultyChanged(level);
                    listener.onClosed();
                }));
        }

        // Done button at the bottom of the card
        int cardH = Math.max(160, height - cardTop - 20);
        int cardBottom = cardTop + cardH;
        add(new Button((width - bw) / 2, cardBottom - 30, bw, rowH,
            tr("options.done"), b -> listener.onClosed()));
    }

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets tex) {
        if (overlayWorld) {
            MenuTheme.drawWorldOverlay(ui, width, height);
        } else {
            MenuTheme.drawBackdrop(ui, width, height, 0);
        }
        // Draw card BEFORE widgets so it doesn't darken them with its
        // semi-transparent fill (was previously drawn in renderForeground).
        int cardW = Math.min(260, width - 20);
        int cardX = (width - cardW) / 2;
        int cardTop = Math.max(30, height / 6);
        int cardH = Math.max(160, height - cardTop - 20);
        MenuTheme.drawCard(ui, 0xFF000000, cardX, cardTop, cardW, cardH);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {
        int cardTop = Math.max(30, height / 6);

        String title = tr("options.difficulty.title");
        int tw = font.scaledWidth(title, 2);
        font.drawScaledWithShadow(ui, title, (width - tw) / 2f,
            cardTop + 8, 2, 0xFFFFFFFF);
        MenuTheme.drawSeparator(ui, 0xFF000000, width / 2f, cardTop + 30, tw / 2f + 10);
    }

    @Override
    public void onClosed() {
        settings.save();
    }
}
