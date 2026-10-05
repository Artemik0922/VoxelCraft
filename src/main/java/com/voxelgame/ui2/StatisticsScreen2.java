package com.voxelgame.ui2;

import com.voxelgame.stats.WorldStats;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.world.save.WorldMeta;

import static com.voxelgame.core.Language.tr;

/**
 * [STATS] Per-world statistics over the frosted world frame: a paper card
 * with counter rows (play time, mined/placed blocks, kills, ...).
 */
public class StatisticsScreen2 extends Scene {

    public interface Callbacks {
        void onDone();
    }

    private final Callbacks callbacks;
    private final WorldStats stats;
    private final WorldMeta meta;

    public StatisticsScreen2(WorldStats stats, WorldMeta meta, Callbacks callbacks) {
        this.stats = stats;
        this.meta = meta;
        this.callbacks = callbacks;
    }

    @Override
    protected void build() {
        int bw = Math.min(300, width - 60);
        int bx = (width - bw) / 2;

        Button2 done = add(new Button2(tr("gui.done"), bw, 26, callbacks::onDone));
        done.x = bx;
        done.y = height - 80;
    }

    @Override
    protected void renderBackground(UiDraw d) {
        if (com.voxelgame.ui.MenuTheme.blurredBackdrop != 0) {
            d.ui.drawTexture(com.voxelgame.ui.MenuTheme.blurredBackdrop, 0, 0, width, height);
        }
        d.fill(0, 0, width, height, 0x90261A10);

        // Paper card holds the whole list
        int cardH = 44 + 11 * FontRenderer.LINE_HEIGHT + 6;
        d.paperCard((width - 300) / 2f, height / 4f, 300, cardH);
    }

    @Override
    protected void renderForeground(UiDraw d) {
        int x = (width - 300) / 2 + 12;
        int y = height / 4 + 10;

        d.textScaledCentered(tr("stats.title"), width / 2f, height / 4f + 6, 2, UiTheme.TEXT_ON_WOOD);
        y += 26;
        d.brassLine(x, y, 300 - 24);
        y += 6;

        long walkedM = stats.get(WorldStats.DISTANCE_WALKED);
        Object[][] rows = {
            {tr("stats.playTime"), meta.formattedPlayTime()},
            {tr("stats.blocksMined"), stats.get(WorldStats.BLOCKS_MINED)},
            {tr("stats.blocksPlaced"), stats.get(WorldStats.BLOCKS_PLACED)},
            {tr("stats.distanceWalked"), walkedM >= 1000
                ? String.format("%.2f км", walkedM / 1000.0) : walkedM + " м"},
            {tr("stats.itemsCrafted"), stats.get(WorldStats.ITEMS_CRAFTED)},
            {tr("stats.itemsSmelted"), stats.get(WorldStats.ITEMS_SMELTED)},
            {tr("stats.mobsKilled"), stats.get(WorldStats.MOBS_KILLED)},
            {tr("stats.deaths"), stats.get(WorldStats.DEATHS)},
            {tr("stats.fishCaught"), stats.get(WorldStats.FISH_CAUGHT)},
            {tr("stats.tradesMade"), stats.get(WorldStats.TRADES_MADE)},
            {tr("stats.itemsEaten"), stats.get(WorldStats.ITEMS_EATEN)},
        };
        for (Object[] row : rows) {
            d.text((String) row[0], x, y, UiTheme.INK_SOFT);
            String value = String.valueOf(row[1]);
            d.text(value, x + 300 - 24 - UiDraw.fontWidth(value), y, UiTheme.INK);
            y += FontRenderer.LINE_HEIGHT;
        }
    }
}
