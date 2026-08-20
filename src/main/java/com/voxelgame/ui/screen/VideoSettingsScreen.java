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
 * Video settings. Every control applies immediately; the file is written
 * when the screen closes.
 */
public class VideoSettingsScreen extends Screen {

    public interface Listener {
        void onRenderDistance(int chunks);
        void onGraphicsChanged(boolean fancy);
        void onGuiScaleChanged(int scale);
        void onGammaChanged(float gamma);
        void onCloudsChanged(boolean on);
        void onParticlesChanged(int level);
        void onViewBobbingChanged(boolean on);
        void onFovChanged(float fov);
        void onVsyncChanged(boolean on);
        void onFullscreenChanged(boolean on);
        void onShadowsChanged(boolean on);
        void onMenuThemeChanged(int theme);
        void onClosed();
    }

    private final Settings settings;
    private final Listener listener;
    private final boolean overlayWorld;

    private Button graphicsButton;
    private Button guiScaleButton;
    private Button particlesButton;
    private Button presetButton;
    private Button themeButton;

    public VideoSettingsScreen(Settings settings, Listener listener, boolean overlayWorld) {
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
        int cardTop = Math.max(30, height / 8);
        // Controls start below the title + separator to avoid overlap
        int top = cardTop + 42;

        int row = 0;

        // [GR-065] One-tap quality presets. Choosing one rewrites the
        // individual settings below and rebuilds the row of controls.
        presetButton = add(new Button(leftX, top, 2 * colW + gap, rowH,
            presetLabel(), b -> cyclePreset()));

        row++;
        add(new Slider(leftX, top + row * (rowH + gap), colW, rowH,
            tr("options.renderDistance"),
            Settings.MIN_RENDER_DISTANCE, Settings.MAX_RENDER_DISTANCE,
            settings.renderDistance, true,
            (s, v) -> listener.onRenderDistance((int) v)));

        graphicsButton = add(new Button(rightX, top + row * (rowH + gap), colW, rowH,
            graphicsLabel(), b -> {
                settings.fancyGraphics = !settings.fancyGraphics;
                graphicsButton.setLabel(graphicsLabel());
                listener.onGraphicsChanged(settings.fancyGraphics);
            }));

        row++;
        guiScaleButton = add(new Button(leftX, top + row * (rowH + gap), colW, rowH,
            guiScaleLabel(), b -> {
                settings.guiScale = (settings.guiScale + 1) % (Settings.MAX_GUI_SCALE + 1);
                guiScaleButton.setLabel(guiScaleLabel());
                listener.onGuiScaleChanged(settings.guiScale);
            }));

        add(new Slider(rightX, top + row * (rowH + gap), colW, rowH,
            tr("options.gamma"), 0.0, 1.0, settings.gamma, false,
            (s, v) -> listener.onGammaChanged((float) v)));

        row++;
        add(new Slider(leftX, top + row * (rowH + gap), colW, rowH,
            tr("options.fov"), Settings.MIN_FOV, Settings.MAX_FOV,
            settings.fov, true,
            (s, v) -> listener.onFovChanged((float) v)));

        particlesButton = add(new Button(rightX, top + row * (rowH + gap), colW, rowH,
            particlesLabel(), b -> {
                settings.particleLevel = (settings.particleLevel + 1) % 3;
                particlesButton.setLabel(particlesLabel());
                listener.onParticlesChanged(settings.particleLevel);
            }));

        row++;
        add(new Toggle(leftX, top + row * (rowH + gap), colW, rowH,
            tr("options.clouds"), settings.clouds,
            (t, v) -> listener.onCloudsChanged(v)));

        add(new Toggle(rightX, top + row * (rowH + gap), colW, rowH,
            tr("options.viewBobbing"), settings.viewBobbing,
            (t, v) -> listener.onViewBobbingChanged(v)));

        row++;
        add(new Toggle(leftX, top + row * (rowH + gap), colW, rowH,
            tr("options.vsync"), settings.vsync,
            (t, v) -> listener.onVsyncChanged(v)));

        add(new Toggle(rightX, top + row * (rowH + gap), colW, rowH,
            tr("options.fullscreen"), settings.fullscreen,
            (t, v) -> listener.onFullscreenChanged(v)));

        row++;
        add(new Toggle(leftX, top + row * (rowH + gap), colW, rowH,
            tr("options.ao"), settings.ambientOcclusion,
            (t, v) -> {
                settings.ambientOcclusion = v;
                listener.onGraphicsChanged(settings.fancyGraphics);
            }));

        add(new Toggle(rightX, top + row * (rowH + gap), colW, rowH,
            tr("options.shadows"), settings.shadows,
            (t, v) -> {
                settings.shadows = v;
                listener.onShadowsChanged(v);
            }));

        row++;
        themeButton = add(new Button(leftX, top + row * (rowH + gap), colW, rowH,
            themeLabel(), b -> {
                settings.menuTheme = (settings.menuTheme + 1) % Settings.MENU_THEME_COUNT;
                themeButton.setLabel(themeLabel());
                listener.onMenuThemeChanged(settings.menuTheme);
            }));

        // Done button at the bottom of the card
        int cardH = Math.max(240, height - cardTop - 20);
        int cardBottom = cardTop + cardH;
        int bw = 200;
        add(new Button((width - bw) / 2, cardBottom - 30, bw, rowH,
            tr("options.done"), b -> listener.onClosed()));
    }

    private String graphicsLabel() {
        return tr("options.graphics") + ": "
            + tr(settings.fancyGraphics ? "options.graphics.fancy" : "options.graphics.fast");
    }

    private String guiScaleLabel() {
        String v = switch (settings.guiScale) {
            case 0 -> tr("options.guiScale.auto");
            case 1 -> tr("options.guiScale.small");
            case 2 -> tr("options.guiScale.normal");
            default -> tr("options.guiScale.large");
        };
        return tr("options.guiScale") + ": " + v;
    }

    private String particlesLabel() {
        String v = switch (settings.particleLevel) {
            case 0 -> tr("options.particles.all");
            case 1 -> tr("options.particles.decreased");
            default -> tr("options.particles.minimal");
        };
        return tr("options.particles") + ": " + v;
    }

    private String themeLabel() {
        String v = switch (settings.menuTheme) {
            case 1 -> tr("options.menuTheme.amoled");
            case 2 -> tr("options.menuTheme.day");
            case 3 -> tr("options.menuTheme.night");
            case 4 -> tr("options.menuTheme.aurora");
            case 5 -> tr("options.menuTheme.desert");
            case 6 -> tr("options.menuTheme.dawn");
            case 7 -> tr("options.menuTheme.void");
            default -> tr("options.menuTheme.sunset");
        };
        return tr("options.menuTheme") + ": " + v;
    }

    /**
     * [GR-065] Cycle to the next preset and apply it. Every affected
     * setting is written to {@link Settings} and pushed to the game
     * through the existing listeners; the screen is then rebuilt so the
     * toggles and labels show the new values.
     */
    private void cyclePreset() {
        settings.graphicsPreset = (settings.graphicsPreset + 1) % 4;
        applyPreset(settings.graphicsPreset);
    }

    private void applyPreset(int preset) {
        switch (preset) {
            case 0: // Fast
                settings.renderDistance = 3;
                settings.fancyGraphics = false;
                settings.shadows = false;
                settings.ambientOcclusion = false;
                settings.clouds = false;
                settings.particleLevel = 2;
                break;
            case 1: // Medium
                settings.renderDistance = 4;
                settings.fancyGraphics = true;
                settings.shadows = false;
                settings.ambientOcclusion = true;
                settings.clouds = true;
                settings.particleLevel = 1;
                break;
            case 2: // Fancy
                settings.renderDistance = 6;
                settings.fancyGraphics = true;
                settings.shadows = true;
                settings.ambientOcclusion = true;
                settings.clouds = true;
                settings.particleLevel = 0;
                break;
            default: // Ultra
                settings.renderDistance = 10;
                settings.fancyGraphics = true;
                settings.shadows = true;
                settings.ambientOcclusion = true;
                settings.clouds = true;
                settings.particleLevel = 0;
                break;
        }

        listener.onRenderDistance(settings.renderDistance);
        listener.onGraphicsChanged(settings.fancyGraphics);
        listener.onShadowsChanged(settings.shadows);
        listener.onCloudsChanged(settings.clouds);
        listener.onParticlesChanged(settings.particleLevel);

        // Rebuild so every widget reflects the applied values
        init(width, height);
    }

    private String presetLabel() {
        String v = switch (settings.graphicsPreset) {
            case 0 -> tr("options.preset.fast");
            case 1 -> tr("options.preset.medium");
            case 2 -> tr("options.preset.fancy");
            default -> tr("options.preset.ultra");
        };
        return tr("options.preset") + ": " + v;
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
        int cardTop = Math.max(30, height / 8);
        int cardH = Math.max(240, height - cardTop - 20);
        MenuTheme.drawCard(ui, 0xFF000000, cardX, cardTop, cardW, cardH);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {
        int cardTop = Math.max(30, height / 8);
        String title = tr("options.video.title");
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
