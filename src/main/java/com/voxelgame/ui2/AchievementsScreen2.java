package com.voxelgame.ui2;

import com.voxelgame.achievement.Achievement;
import com.voxelgame.achievement.AchievementRegistry;
import com.voxelgame.ui.FontRenderer;

import static com.voxelgame.core.Language.tr;

import java.util.List;

/**
 * [STATS] Per-world achievement gallery: two columns over the frosted
 * world frame; locked entries are greyed out, unlocked ones carry their
 * accent colour.
 */
public class AchievementsScreen2 extends Scene {

    public interface Callbacks {
        void onDone();
    }

    private final Callbacks callbacks;
    private final List<Achievement> all;

    /** Two columns fit 13+ achievements without scrolling. */
    private static final int COLS = 2;
    private static final int CARD_W = 460;

    public AchievementsScreen2(Callbacks callbacks) {
        this.callbacks = callbacks;
        this.all = new java.util.ArrayList<>(AchievementRegistry.getAll());
    }

    @Override
    protected void build() {
        int bw = Math.min(260, width - 40);

        Button2 done = add(new Button2(tr("gui.done"), bw, 26, callbacks::onDone));
        done.x = (width - bw) / 2;
        done.y = height - 80;
    }

    @Override
    protected void renderBackground(UiDraw d) {
        if (com.voxelgame.ui.MenuTheme.blurredBackdrop != 0) {
            d.ui.drawTexture(com.voxelgame.ui.MenuTheme.blurredBackdrop, 0, 0, width, height);
        }
        d.fill(0, 0, width, height, 0x90261A10);

        int rows = (all.size() + COLS - 1) / COLS;
        int cardH = 34 + rows * ROW_H + 10;
        d.paperCard((width - CARD_W) / 2f, height / 4f, CARD_W, cardH);
    }

    @Override
    protected void renderForeground(UiDraw d) {
        d.textScaledCentered(tr("menu.achievements"), width / 2f, height / 4f + 4, 2,
            UiTheme.TEXT_ON_WOOD);

        int rows = (all.size() + COLS - 1) / COLS;
        int colW = (CARD_W - 24) / COLS;
        int top = height / 4 + 28;
        int unlockedCount = 0;

        for (int i = 0; i < all.size(); i++) {
            Achievement a = all.get(i);
            boolean unlocked = AchievementRegistry.isUnlocked(a.id);
            if (unlocked) unlockedCount++;

            int col = i / rows;
            int row = i % rows;
            int x = (width - CARD_W) / 2 + 12 + col * colW;
            int y = top + row * ROW_H;

            // Accent chip: filled when unlocked, hollow when locked
            if (unlocked) {
                d.fill(x, y + 2, 8, 8, a.color == 0 ? UiTheme.BRASS : a.color);
            } else {
                d.ui.drawRectOutline(x, y + 2, 8, 8, UiTheme.fade(UiTheme.INK_SOFT, 0.5f));
            }

            String title = tr(a.titleKey);
            d.text(d.font.trimToWidth(title, colW - 16), x + 13, y,
                unlocked ? UiTheme.INK : UiTheme.fade(UiTheme.INK, 0.45f));
            String desc = tr(a.descriptionKey);
            d.text(d.font.trimToWidth(desc, colW - 16), x + 13, y + FontRenderer.LINE_HEIGHT,
                UiTheme.fade(UiTheme.INK_SOFT, unlocked ? 0.9f : 0.45f));
        }

        String summary = AchievementRegistry.getUnlockedCount() + " / "
            + AchievementRegistry.getTotalCount();
        d.text(summary, (width - CARD_W) / 2f + CARD_W - 12
            - UiDraw.fontWidth(summary), height / 4f + 6, UiTheme.INK_SOFT);
    }

    private static final int ROW_H = FontRenderer.LINE_HEIGHT * 2 + 4;
}
