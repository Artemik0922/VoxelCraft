package com.voxelgame.ui2;

import com.voxelgame.ui.FontRenderer;

/**
 * Shared shell for the settings screens: a tall walnut board anchored to
 * the left with a paper title stamp overlapping its top edge, over the
 * blurred world (when opened in-game) or the plank wall.
 *
 * The board is drawn in renderBackground so the element tree (sliders,
 * toggles, planks) renders ON TOP of it.
 */
public abstract class SettingsScene extends Scene {

    protected final boolean overlayWorld;

    protected int boardX, boardY, boardW, boardH;

    protected SettingsScene(boolean overlayWorld) {
        this.overlayWorld = overlayWorld;
    }

    @Override
    public boolean rendersWorld() { return overlayWorld; }

    @Override
    protected final void build() {
        boardX = 20;
        boardY = 48;
        boardW = Math.min(500, Math.max(260, width - 40));
        boardH = height - 48 - 20;
        buildContent();
    }

    /** Add the scene's controls inside the board. */
    protected abstract void buildContent();

    /** Stamp text over the board's top edge. */
    protected abstract String title();

    @Override
    protected void renderBackground(UiDraw d) {
        if (overlayWorld && com.voxelgame.ui.MenuTheme.blurredBackdrop != 0) {
            d.ui.drawTexture(com.voxelgame.ui.MenuTheme.blurredBackdrop,
                0, 0, width, height);
            d.fill(0, 0, width, height, 0x73261A10);
        } else {
            d.wall(0, 0, width, height, 0xFF6B4A2B);
            d.fill(0, 0, width, height, 0x73261A10);
        }

        d.card(boardX, boardY, boardW, boardH);
        d.ui.drawRectOutline(boardX + d.offX + 2, boardY + d.offY + 2,
            boardW - 4, boardH - 4, d.aAlpha(UiTheme.fade(UiTheme.BRASS_DIM, 0.8f)));
        d.cornerRivets(boardX, boardY, boardW, boardH);

        String title = title();
        int stampW = d.font.width(title) + 20;
        d.paper(boardX + 8, boardY - 9, stampW, 18);
        d.fill(boardX + 14, boardY - 4, 3, 1, UiTheme.BRASS);
        d.fill(boardX + 15, boardY - 5, 1, 3, UiTheme.BRASS);
        d.fill(boardX + 15, boardY - 4, 1, 1, UiTheme.BRASS_LIGHT);
        d.text(title, boardX + 22, boardY - 4, UiTheme.INK);
    }

    /** Standard Done plank, bottom-right inside the board. */
    protected Button2 addDoneButton(String label, Runnable onClose) {
        Button2 done = add(new Button2(label, Math.min(160, boardW - 28), 24, onClose));
        done.x = boardX + boardW - done.width - 14;
        done.y = boardY + boardH - 38;
        done.appearDelay = 0.2f;
        return done;
    }

    /** Column x positions for the standard two-column grid. */
    protected int colLeft() { return boardX + 14; }

    protected int colRight() { return boardX + boardW / 2 + 5; }

    protected int colWidth() { return (boardW - 28 - 10) / 2; }
}
