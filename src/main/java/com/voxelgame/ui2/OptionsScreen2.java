package com.voxelgame.ui2;

import com.voxelgame.core.Settings;

import static com.voxelgame.core.Language.tr;

/**
 * Settings hub on the Workshop bench: sliders and switch-planks in a
 * two-column grid, sub-screen shortcuts below, Done anchored bottom-right.
 * Controls apply live; the file is written when the scene closes.
 */
public class OptionsScreen2 extends SettingsScene {

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

    public OptionsScreen2(Settings settings, Listener listener, boolean overlayWorld) {
        super(overlayWorld);
        this.settings = settings;
        this.listener = listener;
    }

    /** Options closed with Escape from the title falls back to the main menu. */
    @Override
    public boolean fallsBackToMainMenu() { return true; }

    @Override
    protected String title() { return tr("options.title"); }

    @Override
    public void onClosed() {
        settings.save();
    }

    @Override
    protected void buildContent() {
        int rowH = 22;
        int gap = 5;
        int lw = colWidth();
        int lx = colLeft();
        int rx = colRight();
        int top = boardY + 16;
        int row = 0;

        Slider2 renderDistance = new Slider2(tr("options.renderDistance"),
            Settings.MIN_RENDER_DISTANCE, Settings.MAX_RENDER_DISTANCE,
            settings.renderDistance);
        renderDistance.x = lx;
        renderDistance.y = top;
        renderDistance.width = lw;
        renderDistance.integer();
        renderDistance.listener((s, v) -> listener.onRenderDistanceChanged((int) v));
        add(renderDistance);

        Slider2 fov = new Slider2(tr("options.fov"), Settings.MIN_FOV, Settings.MAX_FOV,
            settings.fov);
        fov.x = rx;
        fov.y = top + row * (rowH + gap);
        fov.width = lw;
        fov.integer();
        fov.listener((s, v) -> listener.onFovChanged((float) v));
        add(fov);

        row++;
        Slider2 sensitivity = new Slider2(tr("options.sensitivity"),
            Settings.MIN_SENSITIVITY, Settings.MAX_SENSITIVITY, settings.mouseSensitivity);
        sensitivity.x = lx;
        sensitivity.y = top + row * (rowH + gap);
        sensitivity.width = lw;
        sensitivity.listener((s, v) -> listener.onSensitivityChanged((float) v));
        add(sensitivity);

        Slider2 guiScale = new Slider2(tr("options.guiScale"), 0, Settings.MAX_GUI_SCALE,
            settings.guiScale);
        guiScale.x = rx;
        guiScale.y = top + row * (rowH + gap);
        guiScale.width = lw;
        guiScale.integer();
        guiScale.listener((s, v) -> listener.onGuiScaleChanged((int) v));
        add(guiScale);

        row++;
        Toggle2 vsync = new Toggle2(tr("options.vsync"), settings.vsync);
        vsync.x = lx;
        vsync.y = top + row * (rowH + gap);
        vsync.width = lw;
        vsync.listener((t, v) -> listener.onVsyncChanged(v));
        add(vsync);

        Toggle2 clouds = new Toggle2(tr("options.clouds"), settings.clouds);
        clouds.x = rx;
        clouds.y = top + row * (rowH + gap);
        clouds.width = lw;
        clouds.listener((t, v) -> listener.onCloudsChanged(v));
        add(clouds);

        row++;
        Slider2 dayLength = new Slider2(tr("options.dayLength"),
            Settings.MIN_DAY_LENGTH, Settings.MAX_DAY_LENGTH, settings.dayLengthMinutes);
        dayLength.x = lx;
        dayLength.y = top + row * (rowH + gap);
        dayLength.width = lw;
        dayLength.listener((s, v) -> listener.onDayLengthChanged((float) v));
        add(dayLength);

        Slider2 autoSave = new Slider2(tr("options.autoSave"),
            Settings.MIN_AUTOSAVE, Settings.MAX_AUTOSAVE, settings.autoSaveSeconds);
        autoSave.x = rx;
        autoSave.y = top + row * (rowH + gap);
        autoSave.width = lw;
        autoSave.listener((s, v) -> listener.onAutoSaveChanged((double) v));
        add(autoSave);

        row++;
        Toggle2 ao = new Toggle2(tr("options.ao"), settings.ambientOcclusion);
        ao.x = lx;
        ao.y = top + row * (rowH + gap);
        ao.width = lw;
        ao.listener((t, v) -> listener.onAoChanged(v));
        add(ao);

        Toggle2 shadows = new Toggle2(tr("options.shadows"), settings.shadows);
        shadows.x = rx;
        shadows.y = top + row * (rowH + gap);
        shadows.width = lw;
        shadows.listener((t, v) -> listener.onShadowsChanged(v));
        add(shadows);

        row++;
        int btnY = top + row * (rowH + gap);
        Button2 video = new Button2(tr("options.video"), lw, rowH, listener::onVideoSettings);
        video.x = lx;
        video.y = btnY;
        add(video);

        Button2 controls = new Button2(tr("options.controls"), lw, rowH, listener::onControls);
        controls.x = rx;
        controls.y = btnY;
        add(controls);

        row++;
        btnY = top + row * (rowH + gap);
        Button2 sound = new Button2(tr("options.sound"), lw, rowH, listener::onSound);
        sound.x = lx;
        sound.y = btnY;
        add(sound);

        Button2 difficulty = new Button2(tr("options.difficulty"), lw, rowH,
            listener::onDifficulty);
        difficulty.x = rx;
        difficulty.y = btnY;
        add(difficulty);

        Button2 language = new Button2(tr("options.language"), lw, rowH, listener::onLanguage);
        language.x = lx;
        language.y = top + (row + 1) * (rowH + gap);
        add(language);

        addDoneButton(tr("options.done"), listener::onClosed);
    }

    @Override
    protected void renderForeground(UiDraw d) {
        d.textShadow(tr("options.guiScale") + " 0 = " + tr("options.guiScale.auto"),
            boardX + 14, boardY + boardH - 14, UiTheme.TEXT_ON_WOOD_DIM);
    }
}
