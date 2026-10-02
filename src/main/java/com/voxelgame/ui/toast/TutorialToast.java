package com.voxelgame.ui.toast;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.UIRenderer;

import static com.voxelgame.core.Language.tr;

/**
 * [UI-010] First-launch tutorial: a long-lived toast listing the essential
 * controls. Shown once per installation, then never again.
 */
public final class TutorialToast implements Toast {

    private double timer = 0;
    private boolean visible = true;

    // The toast lingers far longer than achievement toasts so a new player
    // has time to read every line and try the keys
    private static final double SLIDE_IN = 0.4;
    private static final double HOLD = 12.0;
    private static final double SLIDE_OUT = 0.4;
    private static final double TOTAL = SLIDE_IN + HOLD + SLIDE_OUT;

    private static final int WIDTH = 210;
    private static final int HEIGHT = 76;

    public boolean update(double dt) {
        timer += dt;
        if (timer >= TOTAL) {
            visible = false;
            return false;
        }
        return true;
    }

    public void render(UIRenderer ui, FontRenderer font, GuiAssets gui, int screenW) {
        if (!visible) return;

        int xOffset;
        double t = timer;
        if (t < SLIDE_IN) {
            double progress = t / SLIDE_IN;
            xOffset = (int) ((1 - (1 - progress) * (1 - progress)) * (WIDTH + 10));
        } else if (t < SLIDE_IN + HOLD) {
            xOffset = 0;
        } else {
            double progress = (t - SLIDE_IN - HOLD) / SLIDE_OUT;
            xOffset = (int) (progress * progress * (WIDTH + 10));
        }

        int x = screenW - WIDTH - 6 + xOffset;
        int y = 6;

ui.drawNineSlice(com.voxelgame.ui2.UiMaterials.INSTANCE.paper, x, y, WIDTH, HEIGHT,
            com.voxelgame.ui2.UiTheme.PAPER_BORDER, com.voxelgame.ui2.UiTheme.PAPER_TEX,
            0xFFFFFFFF);
        ui.fillRect(x + 6, y + 1, WIDTH - 12, 1, 0xFF8A6420);

        // Small book icon block on the left
        int iconSize = 20;
        int iconX = x + 6;
        int iconY = y + (HEIGHT - iconSize) / 2;
        ui.useSolidColor();
        ui.fillRect(iconX, iconY, iconSize, iconSize, 0xFF8B6B3A);
        ui.fillRect(iconX + 1, iconY + 1, iconSize - 2, 1, 0xFFB39A6A);
        ui.fillRect(iconX + 5, iconY + 5, 10, 10, 0xFFE8D8A8);

        int textX = iconX + iconSize + 6;
        font.drawWithShadow(ui, tr("tutorial.title"), textX, y + 4, 0xFF4A6A2A);
        font.drawWithShadow(ui, tr("tutorial.move"), textX, y + 17, 0xFF2A1D12);
        font.drawWithShadow(ui, tr("tutorial.jumpSprint"), textX, y + 29, 0xFF2A1D12);
        font.drawWithShadow(ui, tr("tutorial.minePlace"), textX, y + 41, 0xFF2A1D12);
        font.drawWithShadow(ui, tr("tutorial.inventory"), textX, y + 53, 0xFF2A1D12);
    }
}