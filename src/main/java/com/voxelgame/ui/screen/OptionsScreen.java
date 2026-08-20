package com.voxelgame.ui.screen;

import com.voxelgame.core.Settings;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;
import com.voxelgame.ui.widget.Slider;
import com.voxelgame.ui.widget.Toggle;

import static com.voxelgame.core.Language.tr;

/**
 * Settings screen. Every control applies immediately through
 * {@link Listener} so the effect is visible while the slider is dragged;
 * the file is written once on close.
 */
public class OptionsScreen extends Screen {

    public interface Listener {
        void onRenderDistanceChanged(int chunks);
        void onFovChanged(float degrees);
        void onSensitivityChanged(float value);
        void onGuiScaleChanged(int scale);
        void onVsyncChanged(boolean enabled);
        void onCloudsChanged(boolean enabled);
        void onAoChanged(boolean enabled);
        void onShadowsChanged(boolean enabled);
        void onDayLengthChanged(float minutes);
        void onAutoSaveChanged(double seconds);
        void onLanguage();
        void onVideoSettings();
        void onControls();
        void onSound();
        void onDifficulty();
        void onClosed();
    }

    private final Settings settings;
    private final Listener listener;
    private final boolean overlayWorld;

    public OptionsScreen(Settings settings, Listener listener, boolean overlayWorld) {
        this.settings = settings;
        this.listener = listener;
        this.overlayWorld = overlayWorld;
    }

    @Override
    public boolean rendersWorld() { return overlayWorld; }

    @Override
    protected void layout() {
        int colW = 150;
        int rowH = 22;
        int gap = 4;

        int leftX = width / 2 - colW - gap / 2;
        int rightX = width / 2 + gap / 2;
        int cardTop = Math.max(30, height / 6);
        // Controls start below the title + separator to avoid overlap
        int top = cardTop + 42;

        // --- row 0: graphics fundamentals
        add(new Slider(leftX, top, colW, rowH, tr("options.renderDistance"),
            Settings.MIN_RENDER_DISTANCE, Settings.MAX_RENDER_DISTANCE,
            settings.renderDistance, true,
            (s, v) -> listener.onRenderDistanceChanged((int) v)));

        add(new Slider(rightX, top, colW, rowH, tr("options.fov"),
            Settings.MIN_FOV, Settings.MAX_FOV, settings.fov, true,
            (s, v) -> listener.onFovChanged((float) v)));

        // --- row 1: input and UI
        add(new Slider(leftX, top + (rowH + gap), colW, rowH, tr("options.sensitivity"),
            Settings.MIN_SENSITIVITY, Settings.MAX_SENSITIVITY,
            settings.mouseSensitivity, false,
            (s, v) -> listener.onSensitivityChanged((float) v)));

        add(new Slider(rightX, top + (rowH + gap), colW, rowH, tr("options.guiScale"),
            0, Settings.MAX_GUI_SCALE, settings.guiScale, true,
            (s, v) -> listener.onGuiScaleChanged((int) v)));

        // --- row 2: visuals toggles
        add(new Toggle(leftX, top + 2 * (rowH + gap), colW, rowH, tr("options.vsync"),
            settings.vsync, (t, v) -> listener.onVsyncChanged(v)));

        add(new Toggle(rightX, top + 2 * (rowH + gap), colW, rowH, tr("options.clouds"),
            settings.clouds, (t, v) -> listener.onCloudsChanged(v)));

        // --- row 3: world pacing
        add(new Slider(leftX, top + 3 * (rowH + gap), colW, rowH, tr("options.dayLength"),
            Settings.MIN_DAY_LENGTH, Settings.MAX_DAY_LENGTH,
            settings.dayLengthMinutes, false,
            (s, v) -> listener.onDayLengthChanged((float) v)));

        add(new Slider(rightX, top + 3 * (rowH + gap), colW, rowH, tr("options.autoSave"),
            (float) Settings.MIN_AUTOSAVE, (float) Settings.MAX_AUTOSAVE,
            (float) settings.autoSaveSeconds, false,
            (s, v) -> listener.onAutoSaveChanged((double) v)));

        // --- row 4: lighting
        add(new Toggle(leftX, top + 4 * (rowH + gap), colW, rowH, tr("options.ao"),
            settings.ambientOcclusion, (t, v) -> listener.onAoChanged(v)));

        add(new Toggle(rightX, top + 4 * (rowH + gap), colW, rowH, tr("options.shadows"),
            settings.shadows, (t, v) -> listener.onShadowsChanged(v)));

        // --- row 5: sub-screen shortcuts
        add(new Button(leftX, top + 5 * (rowH + gap), colW, rowH,
            tr("options.video"), b -> listener.onVideoSettings()));
        add(new Button(rightX, top + 5 * (rowH + gap), colW, rowH,
            tr("options.controls"), b -> listener.onControls()));

        // --- row 6: sound + difficulty
        add(new Button(leftX, top + 6 * (rowH + gap), colW, rowH,
            tr("options.sound"), b -> listener.onSound()));
        add(new Button(rightX, top + 6 * (rowH + gap), colW, rowH,
            tr("options.difficulty"), b -> listener.onDifficulty()));

        // --- row 7: language + done
        add(new Button(leftX, top + 7 * (rowH + gap), colW, rowH,
            tr("options.language"), b -> listener.onLanguage()));

        // Done button at the bottom of the card
        int cardH = Math.max(280, height - cardTop - 20);
        int cardBottom = cardTop + cardH;
        int bw = 200;
        add(new Button((width - bw) / 2, cardBottom - 30, bw, rowH, tr("options.done"),
            b -> listener.onClosed()));
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
        int cardW = Math.min(340, width - 20);
        int cardX = (width - cardW) / 2;
        int cardTop = Math.max(30, height / 6);
        int cardH = Math.max(280, height - cardTop - 20);
        MenuTheme.drawCard(ui, 0xFF000000, cardX, cardTop, cardW, cardH);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {
        int cardTop = Math.max(30, height / 6);
        String title = tr("options.title");
        int tw = font.scaledWidth(title, 2);
        font.drawScaledWithShadow(ui, title, (width - tw) / 2f,
            cardTop + 8, 2, 0xFFFFFFFF);
        MenuTheme.drawSeparator(ui, 0xFF000000, width / 2f, cardTop + 30, tw / 2f + 10);

        String hint = tr("options.guiScale") + " 0 = " + tr("options.guiScale.auto");
        font.drawCenteredWithShadow(ui, hint, width / 2.0f, height - 14,
            0xFF000000 | MenuTheme.TEXT_DIM);
    }

    @Override
    public void onClosed() {
        settings.save();
    }
}
