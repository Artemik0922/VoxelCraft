package com.voxelgame.ui.widget;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.GuiAssets;

public abstract class Widget {
    public int x, y, width, height;
    public boolean visible = true;
    public boolean enabled = true;

    public int baseY;
    /** Full 32-bit ARGB; only the alpha channel (high byte) is used by
     *  the widgets. Default is fully opaque. */
    public int alpha = 0xFF000000;
    public float animDelay = 0;

    protected boolean hovered = false;

    protected Widget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.baseY = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(float mx, float my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    public void updateHover(float mx, float my) {
        hovered = visible && enabled && contains(mx, my);
    }

    public boolean isHovered() { return hovered; }

    public void update(double deltaTime) {}

    public abstract void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my);

    public boolean mouseClicked(float mx, float my, int button) {
        return false;
    }

    public void mouseReleased(float mx, float my, int button) {}

    public boolean mouseDragged(float mx, float my, int button) {
        return false;
    }

    public boolean mouseScrolled(float mx, float my, double delta) {
        return false;
    }

    public boolean keyPressed(int key, int mods) {
        return false;
    }
}
