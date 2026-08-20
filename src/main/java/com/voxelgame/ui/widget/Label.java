package com.voxelgame.ui.widget;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.GuiAssets;

/**
 * Static text. Non-interactive.
 */
public class Label extends Widget {

    public enum Align { LEFT, CENTER, RIGHT }

    private String text;
    private Align align;
    private int color;
    private boolean shadow = true;

    public Label(int x, int y, String text) {
        this(x, y, text, Align.LEFT, 0xFFFFFFFF);
    }

    public Label(int x, int y, String text, Align align, int color) {
        super(x, y, 0, FontRenderer.GLYPH_H);
        this.text = text;
        this.align = align;
        this.color = color;
    }

    public void setText(String text) { this.text = text; }
    public String getText() { return text; }
    public void setColor(int color) { this.color = color; }
    public void setShadow(boolean shadow) { this.shadow = shadow; }
    public void setAlign(Align align) { this.align = align; }

    @Override
    public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
        if (!visible || text == null) return;

        float drawX = switch (align) {
            case LEFT -> x;
            case CENTER -> x - font.width(text) / 2.0f;
            case RIGHT -> x - font.width(text);
        };

        if (shadow) {
            font.drawWithShadow(ui, text, drawX, y, color);
        } else {
            font.draw(ui, text, drawX, y, color);
        }
    }
}
