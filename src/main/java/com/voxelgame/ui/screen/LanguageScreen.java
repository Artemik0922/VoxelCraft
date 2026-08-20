package com.voxelgame.ui.screen;

import com.voxelgame.core.Language;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;

import static com.voxelgame.core.Language.tr;

/**
 * Language selection. Switching rebuilds every open screen so the change is
 * visible immediately rather than after a restart.
 */
public class LanguageScreen extends Screen {

    public interface Callbacks {
        void onLanguageChosen(String code);
        void onDone();
    }

    private final Callbacks callbacks;
    private final boolean overlayWorld;

    private int selected = 0;

    public LanguageScreen(Callbacks callbacks, boolean overlayWorld) {
        this.callbacks = callbacks;
        this.overlayWorld = overlayWorld;

        Language.Entry[] all = Language.available();
        for (int i = 0; i < all.length; i++) {
            if (all[i].code().equals(Language.getCode())) selected = i;
        }
    }

    @Override
    public boolean rendersWorld() { return overlayWorld; }

    @Override
    protected void layout() {
        Language.Entry[] all = Language.available();

        int rowW = 220;
        int rowH = 22;
        int x = (width - rowW) / 2;
        int cardTop = Math.max(30, height / 6);
        // Controls start below the title + separator to avoid overlap
        int top = cardTop + 42;

        for (int i = 0; i < all.length; i++) {
            final int index = i;
            Language.Entry e = all[i];

            String label = e.name() + " (" + e.region() + ")";
            Button b = add(new Button(x, top + i * (rowH + 2), rowW, rowH, label,
                btn -> {
                    selected = index;
                    callbacks.onLanguageChosen(e.code());
                }));

            // The active language reads as selected (with arrow prefix)
            if (e.code().equals(Language.getCode())) {
                b.setLabel("\u25B6 " + label);
                b.enabled = false;
            }
        }

        // Done button at the bottom of the card
        int cardH = Math.max(140, height - cardTop - 20);
        int cardBottom = cardTop + cardH;
        add(new Button(x, cardBottom - 30, rowW, rowH, tr("gui.done"),
            b -> callbacks.onDone()));
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
        int cardW = Math.min(280, width - 20);
        int cardX = (width - cardW) / 2;
        int cardTop = Math.max(30, height / 6);
        int cardH = Math.max(140, height - cardTop - 20);
        MenuTheme.drawCard(ui, 0xFF000000, cardX, cardTop, cardW, cardH);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {
        int cardTop = Math.max(30, height / 6);

        String title = tr("options.language.title");
        int tw = font.scaledWidth(title, 2);
        font.drawScaledWithShadow(ui, title, (width - tw) / 2f,
            cardTop + 8, 2, 0xFFFFFFFF);
        MenuTheme.drawSeparator(ui, 0xFF000000, width / 2f, cardTop + 30, tw / 2f + 10);

        font.drawCenteredWithShadow(ui, tr("options.language.warning"),
            width / 2.0f, height - 18, 0xFF000000 | MenuTheme.TEXT_DIM);
    }
}
