package com.voxelgame.ui2;

import com.voxelgame.core.Language;

import java.util.ArrayList;
import java.util.List;

import static com.voxelgame.core.Language.tr;

/**
 * Language picker: kraft rows on the bench; the active one wears a brass
 * arrow and can't be re-picked. Switching rebuilds every open scene.
 */
public class LanguageScreen2 extends Scene {

    public interface Callbacks {
        void onLanguageChosen(String code);

        void onDone();
    }

    private final Callbacks callbacks;
    private final boolean overlayWorld;
    private final List<Button2> rows = new ArrayList<>();

    public LanguageScreen2(Callbacks callbacks, boolean overlayWorld) {
        this.callbacks = callbacks;
        this.overlayWorld = overlayWorld;

        for (Language.Entry e : Language.available()) {
            if (e.code().equals(Language.getCode())) {
                selectedCode = e.code();
            }
        }
    }

    private String selectedCode;

    @Override public boolean rendersWorld() { return overlayWorld; }

    @Override
    protected void build() {
        Language.Entry[] all = Language.available();
        int rowW = Math.min(260, width - 60);
        int rowH = 24;
        int x = (width - rowW) / 2;
        int top = Math.max(64, height / 2 - all.length * (rowH + 6) / 2);

        rows.clear();
        for (int i = 0; i < all.length; i++) {
            Language.Entry e = all[i];
            String label = e.name() + " (" + e.region() + ")";
            boolean active = e.code().equals(Language.getCode());
            Button2 b = add(new Button2(active ? "\u25B6 " + label : label, rowW, rowH,
                () -> {
                    selectedCode = e.code();
                    callbacks.onLanguageChosen(e.code());
                }));
            b.x = x;
            b.y = top + i * (rowH + 6);
            b.enabled = !active;
            b.appearDelay = 0.08f * i;
            rows.add(b);
        }

        Button2 done = add(new Button2(tr("gui.done"), rowW, 24, callbacks::onDone));
        done.x = x;
        done.y = top + all.length * (rowH + 6) + 12;
        done.appearDelay = 0.3f;
    }

    @Override
    protected void renderBackground(UiDraw d) {
        if (overlayWorld && com.voxelgame.ui.MenuTheme.blurredBackdrop != 0) {
            d.ui.drawTexture(com.voxelgame.ui.MenuTheme.blurredBackdrop, 0, 0, width, height);
            d.fill(0, 0, width, height, 0x73261A10);
        } else {
            d.wall(0, 0, width, height, 0xFF6B4A2B);
            d.fill(0, 0, width, height, 0x73261A10);
        }

        String title = tr("options.language.title");
        int stampW = d.font.width(title) + 20;
        int cardW = Math.min(300, width - 40);
        int cardX = (width - cardW) / 2;
        int cardTop = Math.max(40, height / 2 - 90);
        int cardH = height - cardTop * 2;

        d.card(cardX, cardTop, cardW, cardH);
        d.ui.drawRectOutline(cardX + d.offX + 2, cardTop + d.offY + 2,
            cardW - 4, cardH - 4, d.aAlpha(UiTheme.fade(UiTheme.BRASS_DIM, 0.8f)));
        d.cornerRivets(cardX, cardTop, cardW, cardH);

        d.paper(cardX + 8, cardTop - 9, stampW, 18);
        d.fill(cardX + 14, cardTop - 4, 3, 1, UiTheme.BRASS);
        d.fill(cardX + 15, cardTop - 5, 1, 3, UiTheme.BRASS);
        d.fill(cardX + 15, cardTop - 4, 1, 1, UiTheme.BRASS_LIGHT);
        d.text(title, cardX + 22, cardTop - 4, UiTheme.INK);
    }

    @Override
    protected void renderForeground(UiDraw d) {
        d.textCentered(tr("options.language.warning"), width / 2f, height - 22,
            UiTheme.fade(UiTheme.TEXT_ON_WOOD_DIM, 0.9f));
    }
}
