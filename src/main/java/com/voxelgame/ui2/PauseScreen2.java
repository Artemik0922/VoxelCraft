package com.voxelgame.ui2;

import com.voxelgame.ui.FontRenderer;

import static com.voxelgame.core.Language.tr;

/**
 * In-game pause menu over the frosted world frame: a walnut board with the
 * brass-stamped title, action planks, and a paper confirmation modal for
 * save-and-quit.
 */
public class PauseScreen2 extends Scene {

    public interface Callbacks {
        void onResume();

        void onOptions();

        void onAchievements();

        void onStatistics();

        void onQuitToTitle();
    }

    private final Callbacks callbacks;
    private Modal quitModal;

    public PauseScreen2(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    @Override public boolean rendersWorld() { return true; }

    @Override
    protected void build() {
        int bw = Math.min(260, width - 40);
        int bx = (width - bw) / 2;
        int y = height / 4 + 56;
        int btnH = 26;
        int gap = 9;

        Button2 resume = add(new Button2(tr("menu.returnToGame"), bw, btnH,
            callbacks::onResume));
        resume.x = bx;
        resume.y = y;

        Button2 achievements = add(new Button2(tr("menu.achievements"),
            (bw - 8) / 2, btnH, callbacks::onAchievements));
        achievements.x = bx;
        achievements.y = y + btnH + gap;

        Button2 statistics = add(new Button2(tr("menu.statistics"),
            (bw - 8) / 2, btnH, callbacks::onStatistics));
        statistics.x = bx + bw / 2 + 8;
        statistics.y = y + btnH + gap;

        Button2 options = add(new Button2(tr("menu.options"), bw, btnH,
            callbacks::onOptions));
        options.x = bx;
        options.y = y + (btnH + gap) * 2;
        options.appearDelay = 0.08f;

        Button2 quit = add(new Button2(tr("menu.saveAndQuit"), bw, btnH,
            () -> quitModal.open()));
        quit.x = bx;
        quit.y = y + (btnH + gap) * 3;
        quit.appearDelay = 0.16f;

        quitModal = add(Modal.confirm(
            tr("menu.quitQuestion"),
            tr("menu.quitWarning"),
            tr("gui.yes"), tr("gui.cancel"),
            callbacks::onQuitToTitle));
    }

    @Override
    protected void renderBackground(UiDraw d) {
        if (com.voxelgame.ui.MenuTheme.blurredBackdrop != 0) {
            d.ui.drawTexture(com.voxelgame.ui.MenuTheme.blurredBackdrop, 0, 0, width, height);
        }
        d.fill(0, 0, width, height, 0x90261A10);
    }

    @Override
    protected void renderForeground(UiDraw d) {
        String title = tr("menu.game");
        d.textScaledCentered(title, width / 2f, height / 4f, 2, UiTheme.INK);
        d.textScaledCentered(title, width / 2f, height / 4f - 1, 2, UiTheme.TEXT_ON_WOOD);
        int tw = d.font.scaledWidth(title, 2);
        float lineY = height / 4f + 2 * FontRenderer.GLYPH_H + 6;
        d.brassLine(width / 2f - tw / 2f - 10, lineY, tw + 20);
    }
}
