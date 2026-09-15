package com.voxelgame.ui.screen;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.world.World;

import static com.voxelgame.core.Language.tr;

/**
 * Shown while a world's first render-distance circle of chunks is generated.
 *
 * Unlike pause screens it does NOT pause the game: {@link World#update} must
 * keep running so the chunk workers can stream terrain in. The host game sets
 * its own "loading" flag to freeze player input and world logic meanwhile.
 * Once {@link World#getChunkLoadProgress()} reaches 1.0 the {@code onLoaded}
 * callback fires (usually closing the screen stack to drop the player in).
 */
public class LoadingScreen extends Screen {

    private final World world;
    private final Runnable onLoaded;
    private final String worldName;
    private float progress;
    private double time;

    public LoadingScreen(String worldName, World world, Runnable onLoaded) {
        this.worldName = worldName;
        this.world = world;
        this.onLoaded = onLoaded;
    }

    /** Keep ticking so the world streams chunks behind this screen. */
    @Override
    public boolean pausesGame() { return false; }

    @Override
    public boolean showsCursor() { return false; }

    @Override
    public boolean rendersWorld() { return false; }

    /** Loading cannot be skipped. */
    @Override
    public boolean closableWithEscape() { return false; }

    @Override
    protected void layout() {}

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        time += deltaTime;
        progress = world.getChunkLoadProgress();
        if (progress >= 1.0f) {
            onLoaded.run();
        }
    }

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets tex) {
        MenuTheme.drawBackdrop(ui, width, height, time);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {
        int cardW = Math.min(360, width - 40);
        int cardH = 128;
        int cardX = (width - cardW) / 2;
        int cardY = (height - cardH) / 2;
        MenuTheme.drawCard(ui, 0xFF000000, cardX, cardY, cardW, cardH);

        String title = tr("loading.title");
        int tw = font.scaledWidth(title, 2);
        font.drawScaledWithShadow(ui, title, (width - tw) / 2f, cardY + 10, 2, 0xFFFFFFFF);

        String world = font.trimToWidth(tr("loading.world", worldName), cardW - 24);
        font.drawCenteredWithShadow(ui, world, width / 2.0f, cardY + 38,
            0xFF000000 | MenuTheme.TEXT_SECONDARY);

        // Progress bar: recessed glass track + amber fill
        int barX = cardX + 14;
        int barY = cardY + 58;
        int barW = cardW - 28;
        int barH = 10;
        ui.drawNineSlice(tex.glassTrack, barX, barY, barW, barH, 3,
            GuiAssets.GLASS_WIDGET,
            MenuTheme.col(0xFF000000, 255, 0xFF101623));
        int fillW = (int) Math.max(0, Math.min(barW, barW * progress));
        if (fillW > 2) {
            MenuTheme.drawPanel(ui, 0xFF000000, barX + 2, barY + 2,
                fillW - 2, barH - 4, false);
            ui.fillRect(barX + 2, barY + 2, fillW - 2, 2,
                0xFF000000 | MenuTheme.ACCENT_LIGHT);
        }

        int pct = Math.round(progress * 100);
        font.drawCenteredWithShadow(ui, pct + "%", width / 2.0f, barY + barH + 5,
            0xFF000000 | MenuTheme.TEXT_BRIGHT);

        font.drawCenteredWithShadow(ui, tr("loading.hint"),
            width / 2.0f, cardY + cardH - 20,
            0xFF000000 | MenuTheme.TEXT_DIM);
    }
}