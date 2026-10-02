package com.voxelgame.ui.toast;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.UIRenderer;

/**
 * A toast notification for unlocking an achievement.
 * Similar to BiomeToast but with achievement-specific styling.
 */
public final class AchievementToast implements Toast {

    private final String title;
    private final String description;
    private final int iconBlockId; // BlockType id, or -1 for no icon
    private final int color; // ARGB accent color
    private double timer = 0;
    private boolean visible = true;

    // Animation timing (seconds)
    private static final double SLIDE_IN = 0.3;
    private static final double HOLD = 4.0;
    private static final double SLIDE_OUT = 0.3;
    private static final double TOTAL = SLIDE_IN + HOLD + SLIDE_OUT;

    private static final int WIDTH = 160;
    private static final int HEIGHT = 32;

    public AchievementToast(String title, String description, int iconBlockId, int color) {
        this.title = title;
        this.description = description;
        this.iconBlockId = iconBlockId;
        this.color = color;
    }

    /** Update animation. Returns false when the toast should be removed. */
    public boolean update(double dt) {
        timer += dt;
        if (timer >= TOTAL) {
            visible = false;
            return false;
        }
        return true;
    }

    /** Draw the toast at the top-right of the screen. */
    public void render(UIRenderer ui, FontRenderer font, GuiAssets gui, int screenW) {
        if (!visible) return;

        // Calculate slide offset
        int xOffset;
        double t = timer;
        if (t < SLIDE_IN) {
            double progress = t / SLIDE_IN;
            xOffset = (int) ((1 - easeOut(progress)) * (WIDTH + 10));
        } else if (t < SLIDE_IN + HOLD) {
            xOffset = 0;
        } else {
            double progress = (t - SLIDE_IN - HOLD) / SLIDE_OUT;
            xOffset = (int) (easeIn(progress) * (WIDTH + 10));
        }

        int x = screenW - WIDTH - 6 + xOffset;
        int y = 6;

        // Background with frosted glass
ui.drawNineSlice(com.voxelgame.ui2.UiMaterials.INSTANCE.paper, x, y, WIDTH, HEIGHT,
            com.voxelgame.ui2.UiTheme.PAPER_BORDER, com.voxelgame.ui2.UiTheme.PAPER_TEX,
            0xFFFFFFFF);
        ui.fillRect(x + 6, y + 1, WIDTH - 12, 1, 0xFF8A6420);

        // Icon (colored square if no block icon)
        int iconSize = 20;
        int iconX = x + 6;
        int iconY = y + (HEIGHT - iconSize) / 2;
        ui.useSolidColor();
        ui.fillRect(iconX, iconY, iconSize, iconSize, color);
        ui.fillRect(iconX + 1, iconY + 1, iconSize - 2, 1, 0x60FFFFFF);

        // Star icon overlay
        int starX = iconX + iconSize / 2 - 3;
        int starY = iconY + iconSize / 2 - 3;
        ui.fillRect(starX, starY, 6, 6, 0xFFFFFF00);

        // Text lines
        int textX = iconX + iconSize + 6;
        font.drawWithShadow(ui, title, textX, y + 5, 0xFF8A6420); // Engraved brass title
        font.drawWithShadow(ui, description, textX, y + 17, 0xFF2A1D12);
    }

    private static double easeOut(double t) {
        return 1 - (1 - t) * (1 - t);
    }

    private static double easeIn(double t) {
        return t * t;
    }
}
