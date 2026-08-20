package com.voxelgame.ui.toast;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.UIRenderer;

/**
 * A toast notification shown when discovering a new biome.
 * Slides in from the right, holds, then slides out.
 */
public final class BiomeToast implements Toast {

    private final String title;
    private final String biomeName;
    private final int tint;
    private double timer = 0;
    private boolean visible = true;

    // Animation timing (seconds)
    private static final double SLIDE_IN = 0.3;
    private static final double HOLD = 3.5;
    private static final double SLIDE_OUT = 0.3;
    private static final double TOTAL = SLIDE_IN + HOLD + SLIDE_OUT;

    private static final int WIDTH = 160;
    private static final int HEIGHT = 32;

    public BiomeToast(String title, String biomeName, int tint) {
        this.title = title;
        this.biomeName = biomeName;
        this.tint = tint;
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
            // Slide in from right
            double progress = t / SLIDE_IN;
            xOffset = (int) ((1 - easeOut(progress)) * (WIDTH + 10));
        } else if (t < SLIDE_IN + HOLD) {
            xOffset = 0;
        } else {
            // Slide out to right
            double progress = (t - SLIDE_IN - HOLD) / SLIDE_OUT;
            xOffset = (int) (easeIn(progress) * (WIDTH + 10));
        }

        int x = screenW - WIDTH - 6 + xOffset;
        int y = 6;

        // Background with bevel
        ui.drawNineSlice(gui.panel, x, y, WIDTH, HEIGHT,
            GuiAssets.BORDER, GuiAssets.WIDGET, 0xEE202028);
        ui.useSolidColor();
        ui.drawRectOutline(x, y, WIDTH, HEIGHT, 0xFF444444);
        ui.drawRectOutline(x + 1, y + 1, WIDTH - 2, HEIGHT - 2, 0xFF555555);

        // Icon block (tinted square)
        int iconSize = 20;
        int iconX = x + 6;
        int iconY = y + (HEIGHT - iconSize) / 2;
        ui.fillRect(iconX, iconY, iconSize, iconSize, tint);
        ui.drawRectOutline(iconX, iconY, iconSize, iconSize, 0xFF000000);

        // Text lines
        int textX = iconX + iconSize + 6;
        font.drawWithShadow(ui, title, textX, y + 5, 0xFFAAAAAA);
        font.drawWithShadow(ui, biomeName, textX, y + 17, 0xFFFFFFFF);
    }

    private static double easeOut(double t) {
        return 1 - (1 - t) * (1 - t);
    }

    private static double easeIn(double t) {
        return t * t;
    }
}
