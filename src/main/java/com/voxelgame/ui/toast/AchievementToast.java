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

        // Background
        ui.drawNineSlice(gui.panel, x, y, WIDTH, HEIGHT,
            GuiAssets.BORDER, GuiAssets.WIDGET, 0xEE202028);
        ui.useSolidColor();
        ui.drawRectOutline(x, y, WIDTH, HEIGHT, 0xFF444444);
        ui.drawRectOutline(x + 1, y + 1, WIDTH - 2, HEIGHT - 2, 0xFF555555);

        // Icon (colored square if no block icon)
        int iconSize = 20;
        int iconX = x + 6;
        int iconY = y + (HEIGHT - iconSize) / 2;
        ui.fillRect(iconX, iconY, iconSize, iconSize, color);
        ui.drawRectOutline(iconX, iconY, iconSize, iconSize, 0xFF000000);

        // Star icon overlay
        int starX = iconX + iconSize / 2 - 3;
        int starY = iconY + iconSize / 2 - 3;
        ui.fillRect(starX, starY, 6, 6, 0xFFFFFF00);

        // Text lines
        int textX = iconX + iconSize + 6;
        font.drawWithShadow(ui, title, textX, y + 5, 0xFFFFAA00); // Gold title
        font.drawWithShadow(ui, description, textX, y + 17, 0xFFFFFFFF);
    }

    private static double easeOut(double t) {
        return 1 - (1 - t) * (1 - t);
    }

    private static double easeIn(double t) {
        return t * t;
    }
}
