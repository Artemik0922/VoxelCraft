package com.voxelgame.ui.screen;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;

import static com.voxelgame.core.Language.tr;

/**
 * In-game pause menu, drawn over a darkened still frame of the world.
 *
 * Uses the shared twilight theme so it feels continuous with the main menu.
 * Buttons slide in with a subtle staggered animation.
 */
public class PauseScreen extends Screen {

    public interface Callbacks {
        void onResume();
        void onOptions();
        void onQuitToTitle();
    }

    private final Callbacks callbacks;
    private double time = 0;
    private float[] animProgress;
    /** True while the save-and-quit confirmation modal is showing. */
    private boolean confirmQuit = false;

    public PauseScreen(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    public boolean rendersWorld() { return true; }

    @Override
    protected void layout() {
        int bw = 220;
        int bh = 26;
        int cx = (width - bw) / 2;
        int startY = height / 4 + 48;
        int gap = 34;

        add(new Button(cx, startY, bw, bh, tr("menu.returnToGame"),
            b -> callbacks.onResume())).animDelay = 0f;

        Button achievements = add(new Button(cx, startY + gap, bw / 2 - 2, bh,
            tr("menu.achievements"), b -> {}));
        achievements.enabled = false;

        Button statistics = add(new Button(cx + bw / 2 + 2, startY + gap, bw / 2 - 2, bh,
            tr("menu.statistics"), b -> {}));
        statistics.enabled = false;

        add(new Button(cx, startY + gap * 2, bw, bh, tr("menu.options"),
            b -> callbacks.onOptions())).animDelay = 0.1f;
        add(new Button(cx, startY + gap * 3, bw, bh, tr("menu.saveAndQuit"),
            b -> confirmQuit = true)).animDelay = 0.2f;

        animProgress = new float[widgets.size()];
    }

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        time += deltaTime;
        for (int i = 0; i < animProgress.length && i < widgets.size(); i++) {
            if (widgets.get(i) instanceof Button btn) {
                float t = (float) (time - btn.animDelay);
                animProgress[i] = (float) Math.max(0, Math.min(1, t / 0.4));
                animProgress[i] = 1.0f - (float) Math.pow(1.0f - animProgress[i], 3);
            }
        }
    }

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets tex) {
        MenuTheme.drawWorldOverlay(ui, width, height);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {
        // Title with decorative separators
        String title = tr("menu.game");
        int tw = font.scaledWidth(title, 2);
        float tx = (width - tw) / 2f;
        float ty = height / 4.0f;
        font.drawScaledWithShadow(ui, title, tx, ty, 2, 0xFFFFFFFF);
        MenuTheme.drawSeparator(ui, 0xFF000000, width / 2f, ty + 28, tw / 2f + 20);

        // Render widgets with slide-in animation
        for (int i = 0; i < widgets.size(); i++) {
            var w = widgets.get(i);
            float pr = animProgress[i];
            w.y = (int) (w.baseY + (1f - pr) * 16);
            w.alpha = (int) (pr * 255) << 24;
            w.updateHover(mx, my);
            w.render(ui, font, tex, mx, my);
        }

        if (confirmQuit) drawQuitConfirm(ui, font, tex, mx, my);
    }

    /** Modal confirmation so a stray click never quits the world. */
    private void drawQuitConfirm(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                 float mx, float my) {
        ui.fillRect(0, 0, width, height, 0xC0000000);

        int bw = Math.min(320, width - 20);
        int bh = 110;
        int bx = (width - bw) / 2;
        int by = (height - bh) / 2;

        MenuTheme.drawCard(ui, 0xFF000000, bx, by, bw, bh);

        font.drawCenteredWithShadow(ui, tr("menu.quitQuestion"),
            width / 2.0f, by + 12, 0xFF000000 | MenuTheme.TEXT_BRIGHT);
        font.drawCenteredWithShadow(ui,
            font.trimToWidth(tr("menu.quitWarning"), bw - 16),
            width / 2.0f, by + 30, 0xFF000000 | MenuTheme.TEXT_WARNING);

        int cbw = (bw - 24) / 2;
        drawModalButton(ui, font, bx + 8, by + 60, cbw, 22,
            tr("gui.yes"), mx, my, true);
        drawModalButton(ui, font, bx + bw - 8 - cbw, by + 60, cbw, 22,
            tr("gui.cancel"), mx, my, false);
    }

    private void drawModalButton(UIRenderer ui, FontRenderer font,
                                 int x, int y, int w, int h, String label,
                                 float mx, float my, boolean danger) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        MenuTheme.drawPanel(ui, 0xFF000000, x, y, w, h, hover);
        int col = hover ? MenuTheme.TEXT_BRIGHT : MenuTheme.TEXT_LABEL;
        if (danger && hover) col = MenuTheme.TEXT_WARNING;
        font.drawCenteredWithShadow(ui, label, x + w / 2.0f,
            y + (h - FontRenderer.GLYPH_H) / 2.0f,
            0xFF000000 | (col & 0xFFFFFF));
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (confirmQuit) {
            int bw = Math.min(320, width - 20);
            int bh = 110;
            int bx = (width - bw) / 2;
            int by = (height - bh) / 2;
            int cbw = (bw - 24) / 2;

            int y = by + 60;
            if (my < y || my >= y + 22) {
                if (my < by || my >= by + bh) confirmQuit = false;
            } else if (mx >= bx + 8 && mx < bx + 8 + cbw) {
                confirmQuit = false;
                callbacks.onQuitToTitle();
            } else if (mx >= bx + bw - 8 - cbw && mx < bx + bw - 8) {
                confirmQuit = false;
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
