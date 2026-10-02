package com.voxelgame.ui2;

import com.voxelgame.world.World;

import static com.voxelgame.core.Language.tr;

/**
 * World loading screen: kraft note with a brass progress bar over the
 * plank wall. Does not pause the game - the world keeps streaming chunks
 * while it is up, and {@code onLoaded} fires at 100%.
 */
public class LoadingScreen2 extends Scene {

    private final World world;
    private final Runnable onLoaded;
    private final String worldName;
    private float progress;

    public LoadingScreen2(String worldName, World world, Runnable onLoaded) {
        this.worldName = worldName;
        this.world = world;
        this.onLoaded = onLoaded;
    }

    @Override public boolean pausesGame() { return false; }

    @Override public boolean showsCursor() { return false; }

    @Override public boolean rendersWorld() { return false; }

    @Override public boolean closableWithEscape() { return false; }

    @Override
    protected void build() {
        // No interactive elements; everything is drawn
    }

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        progress = world.getChunkLoadProgress();
        if (progress >= 1.0f) {
            onLoaded.run();
        }
    }

    @Override
    protected void renderBackground(UiDraw d) {
        d.wall(0, 0, width, height, 0xFF6B4A2B);
        d.fill(0, 0, width, height, 0x8A261A10);
    }

    @Override
    protected void renderForeground(UiDraw d) {
        int cardW = Math.min(360, width - 40);
        int cardH = 128;
        int cardX = (width - cardW) / 2;
        int cardY = (height - cardH) / 2;

        d.card(cardX, cardY, cardW, cardH);
        d.cornerRivets(cardX, cardY, cardW, cardH);

        String title = tr("loading.title");
        d.textScaledCentered(title, width / 2f, cardY + 12, 2, UiTheme.TEXT_ON_WOOD);

        String worldLine = d.font.trimToWidth(tr("loading.world", worldName), cardW - 24);
        d.textCentered(worldLine, width / 2f, cardY + 40, UiTheme.TEXT_ON_WOOD_DIM);

        // Progress: dark groove with a brass fill and a light top edge
        int barX = cardX + 14;
        int barY = cardY + 60;
        int barW = cardW - 28;
        int barH = 10;
        d.fill(barX, barY, barW, barH, 0xFF26180E);
        d.ui.drawRectOutline(barX + d.offX, barY + d.offY, barW, barH, d.aAlpha(UiTheme.BRASS_DIM));
        int fillW = (int) Math.max(0, Math.min(barW - 4, (barW - 4) * progress));
        if (fillW > 2) {
            d.fill(barX + 2, barY + 2, fillW, barH - 4, UiTheme.BRASS);
            d.fill(barX + 2, barY + 2, fillW, 2, UiTheme.BRASS_LIGHT);
        }

        d.textCentered(Math.round(progress * 100) + "%", width / 2f,
            barY + barH + 6, UiTheme.TEXT_ON_WOOD);

        d.textCentered(tr("loading.hint"), width / 2f, cardY + cardH - 20,
            UiTheme.fade(UiTheme.TEXT_ON_WOOD_DIM, 0.9f));
    }
}
