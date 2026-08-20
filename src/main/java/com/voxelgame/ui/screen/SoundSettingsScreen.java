package com.voxelgame.ui.screen;

import com.voxelgame.core.Settings;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;
import com.voxelgame.ui.widget.Slider;

import static com.voxelgame.core.Language.tr;

/**
 * Sound settings. Music and sound sliders apply live and persist on close.
 */
public class SoundSettingsScreen extends Screen {

    public interface Listener {
        void onMusicVolume(float volume);
        void onSoundVolume(float volume);
        void onClosed();
    }

    private final Settings settings;
    private final Listener listener;
    private final boolean overlayWorld;

    public SoundSettingsScreen(Settings settings, Listener listener, boolean overlayWorld) {
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

        add(new Slider(leftX, top, colW, rowH, tr("options.sound.music"),
            0.0, 1.0, settings.musicVolume, false,
            (s, v) -> listener.onMusicVolume((float) v)));

        add(new Slider(rightX, top, colW, rowH, tr("options.sound.sound"),
            0.0, 1.0, settings.soundVolume, false,
            (s, v) -> listener.onSoundVolume((float) v)));

        // Done button at the bottom of the card
        int cardH = Math.max(130, height - cardTop - 20);
        int cardBottom = cardTop + cardH;
        int bw = 200;
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
        int cardW = Math.min(340, width - 20);
        int cardX = (width - cardW) / 2;
        int cardTop = Math.max(30, height / 6);
        int cardH = Math.max(130, height - cardTop - 20);
        MenuTheme.drawCard(ui, 0xFF000000, cardX, cardTop, cardW, cardH);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {
        int cardTop = Math.max(30, height / 6);

        String title = tr("options.sound.title");
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
