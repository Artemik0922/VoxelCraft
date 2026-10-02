package com.voxelgame.ui2;

import com.voxelgame.core.Settings;
import com.voxelgame.ui.MenuTheme;

import static com.voxelgame.core.Language.tr;

/**
 * Video settings on the Workshop bench. Cycling controls rewrite their own
 * plank labels; presets rewrite every setting and rebuild the scene so all
 * controls show the applied values.
 */
public class VideoSettingsScreen2 extends SettingsScene {

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

        void onBloomChanged(boolean on);

        void onFxaaChanged(boolean on);

        void onMenuThemeChanged(int theme);

        void onClosed();
    }

    private final Settings settings;
    private final Listener listener;

    private Button2 presetButton;
    private Button2 graphicsButton;
    private Button2 guiScaleButton;
    private Button2 particlesButton;
    private Button2 themeButton;

    public VideoSettingsScreen2(Settings settings, Listener listener, boolean overlayWorld) {
        super(overlayWorld);
        this.settings = settings;
        this.listener = listener;
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

        presetButton = new Button2(presetLabel(), 2 * lw + 10, rowH, this::cyclePreset);
        presetButton.x = lx;
        presetButton.y = top;
        add(presetButton);
        row++;

        Slider2 renderDistance = new Slider2(tr("options.renderDistance"),
            Settings.MIN_RENDER_DISTANCE, Settings.MAX_RENDER_DISTANCE,
            settings.renderDistance);
        renderDistance.x = lx;
        renderDistance.y = top + row * (rowH + gap);
        renderDistance.width = lw;
        renderDistance.integer();
        renderDistance.listener((s, v) -> listener.onRenderDistance((int) v));
        add(renderDistance);

        graphicsButton = new Button2(graphicsLabel(), lw, rowH, () -> {
            settings.fancyGraphics = !settings.fancyGraphics;
            graphicsButton.label(graphicsLabel());
            listener.onGraphicsChanged(settings.fancyGraphics);
        });
        graphicsButton.x = rx;
        graphicsButton.y = top + row * (rowH + gap);
        add(graphicsButton);
        row++;

        guiScaleButton = new Button2(guiScaleLabel(), lw, rowH, () -> {
            settings.guiScale = (settings.guiScale + 1) % (Settings.MAX_GUI_SCALE + 1);
            guiScaleButton.label(guiScaleLabel());
            listener.onGuiScaleChanged(settings.guiScale);
        });
        guiScaleButton.x = lx;
        guiScaleButton.y = top + row * (rowH + gap);
        add(guiScaleButton);

        Slider2 gamma = new Slider2(tr("options.gamma"), 0.0, 1.0, settings.gamma);
        gamma.x = rx;
        gamma.y = top + row * (rowH + gap);
        gamma.width = lw;
        gamma.listener((s, v) -> listener.onGammaChanged((float) v));
        add(gamma);
        row++;

        Slider2 fov = new Slider2(tr("options.fov"), Settings.MIN_FOV, Settings.MAX_FOV,
            settings.fov);
        fov.x = lx;
        fov.y = top + row * (rowH + gap);
        fov.width = lw;
        fov.integer();
        fov.listener((s, v) -> listener.onFovChanged((float) v));
        add(fov);

        particlesButton = new Button2(particlesLabel(), lw, rowH, () -> {
            settings.particleLevel = (settings.particleLevel + 1) % 3;
            particlesButton.label(particlesLabel());
            listener.onParticlesChanged(settings.particleLevel);
        });
        particlesButton.x = rx;
        particlesButton.y = top + row * (rowH + gap);
        add(particlesButton);
        row++;

        Toggle2 clouds = new Toggle2(tr("options.clouds"), settings.clouds);
        clouds.x = lx;
        clouds.y = top + row * (rowH + gap);
        clouds.width = lw;
        clouds.listener((t, v) -> listener.onCloudsChanged(v));
        add(clouds);

        Toggle2 viewBobbing = new Toggle2(tr("options.viewBobbing"), settings.viewBobbing);
        viewBobbing.x = rx;
        viewBobbing.y = top + row * (rowH + gap);
        viewBobbing.width = lw;
        viewBobbing.listener((t, v) -> listener.onViewBobbingChanged(v));
        add(viewBobbing);
        row++;

        Toggle2 vsync = new Toggle2(tr("options.vsync"), settings.vsync);
        vsync.x = lx;
        vsync.y = top + row * (rowH + gap);
        vsync.width = lw;
        vsync.listener((t, v) -> listener.onVsyncChanged(v));
        add(vsync);

        Toggle2 fullscreen = new Toggle2(tr("options.fullscreen"), settings.fullscreen);
        fullscreen.x = rx;
        fullscreen.y = top + row * (rowH + gap);
        fullscreen.width = lw;
        fullscreen.listener((t, v) -> listener.onFullscreenChanged(v));
        add(fullscreen);
        row++;

        Toggle2 ao = new Toggle2(tr("options.ao"), settings.ambientOcclusion);
        ao.x = lx;
        ao.y = top + row * (rowH + gap);
        ao.width = lw;
        ao.listener((t, v) -> {
            settings.ambientOcclusion = v;
            listener.onGraphicsChanged(settings.fancyGraphics);
        });
        add(ao);

        Toggle2 shadows = new Toggle2(tr("options.shadows"), settings.shadows);
        shadows.x = rx;
        shadows.y = top + row * (rowH + gap);
        shadows.width = lw;
        shadows.listener((t, v) -> {
            settings.shadows = v;
            listener.onShadowsChanged(v);
        });
        add(shadows);
        row++;

        Toggle2 bloom = new Toggle2(tr("options.bloom"), settings.bloomEnabled);
        bloom.x = lx;
        bloom.y = top + row * (rowH + gap);
        bloom.width = lw;
        bloom.listener((t, v) -> listener.onBloomChanged(v));
        add(bloom);

        Toggle2 fxaa = new Toggle2(tr("options.fxaa"), settings.fxaaEnabled);
        fxaa.x = rx;
        fxaa.y = top + row * (rowH + gap);
        fxaa.width = lw;
        fxaa.listener((t, v) -> listener.onFxaaChanged(v));
        add(fxaa);
        row++;

        themeButton = new Button2(themeLabel(), lw, rowH, () -> {
            settings.menuTheme = (settings.menuTheme + 1) % Settings.MENU_THEME_COUNT;
            MenuTheme.setThemeIndex(settings.menuTheme);
            themeButton.label(themeLabel());
            listener.onMenuThemeChanged(settings.menuTheme);
        });
        themeButton.x = lx;
        themeButton.y = top + row * (rowH + gap);
        add(themeButton);

        addDoneButton(tr("options.done"), listener::onClosed);
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

    private String presetLabel() {
        String v = switch (settings.graphicsPreset) {
            case 0 -> tr("options.preset.fast");
            case 1 -> tr("options.preset.medium");
            case 2 -> tr("options.preset.fancy");
            default -> tr("options.preset.ultra");
        };
        return tr("options.preset") + ": " + v;
    }

    private void cyclePreset() {
        settings.graphicsPreset = (settings.graphicsPreset + 1) % 4;
        applyPreset(settings.graphicsPreset);
    }

    private void applyPreset(int preset) {
        switch (preset) {
            case 0:
                settings.renderDistance = 3;
                settings.fancyGraphics = false;
                settings.shadows = false;
                settings.ambientOcclusion = false;
                settings.clouds = false;
                settings.particleLevel = 2;
                settings.bloomEnabled = false;
                settings.fxaaEnabled = false;
                break;
            case 1:
                settings.renderDistance = 4;
                settings.fancyGraphics = true;
                settings.shadows = false;
                settings.ambientOcclusion = true;
                settings.clouds = true;
                settings.particleLevel = 1;
                settings.bloomEnabled = false;
                settings.fxaaEnabled = false;
                break;
            case 2:
                settings.renderDistance = 6;
                settings.fancyGraphics = true;
                settings.shadows = true;
                settings.ambientOcclusion = true;
                settings.clouds = true;
                settings.particleLevel = 0;
                settings.bloomEnabled = true;
                settings.fxaaEnabled = false;
                break;
            default:
                settings.renderDistance = 10;
                settings.fancyGraphics = true;
                settings.shadows = true;
                settings.ambientOcclusion = true;
                settings.clouds = true;
                settings.particleLevel = 0;
                settings.bloomEnabled = true;
                settings.fxaaEnabled = true;
                break;
        }

        listener.onRenderDistance(settings.renderDistance);
        listener.onGraphicsChanged(settings.fancyGraphics);
        listener.onShadowsChanged(settings.shadows);
        listener.onCloudsChanged(settings.clouds);
        listener.onParticlesChanged(settings.particleLevel);

        // Rebuild so every control reflects the applied values
        init(width, height);
    }

    @Override
    protected String title() { return tr("options.video.title"); }

    @Override
    public void onClosed() {
        settings.save();
    }
}
