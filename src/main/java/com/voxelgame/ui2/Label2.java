package com.voxelgame.ui2;

import com.voxelgame.ui.FontRenderer;

/**
 * Static text. Width tracks the rendered string so Row/Column can align
 * by it; scale > 1 uses the scaled font path (menu headers).
 */
public class Label2 extends Element {

    public enum Align { LEFT, CENTER, RIGHT }

    private String text;
    private int color;
    private int scale;
    private boolean shadow;
    private Align align = Align.LEFT;
    /** When set, x is the anchor for CENTER/RIGHT instead of the element box. */
    private Float anchorX;

    public Label2(String text, int color) {
        this(text, color, 1, false);
    }

    public Label2(String text, int color, int scale, boolean shadow) {
        this.text = text;
        this.color = color;
        this.scale = scale;
        this.shadow = shadow;
        this.height = FontRenderer.GLYPH_H * scale;
    }

    public Label2 text(String t) {
        this.text = t;
        this.width = 0;
        return this;
    }

    public Label2 color(int c) {
        this.color = c;
        return this;
    }

    public Label2 align(Align a) {
        this.align = a;
        return this;
    }

    public Label2 anchor(float ax) {
        this.anchorX = ax;
        return this;
    }

    @Override
    public void arrangeChildren() {
        int w = scale == 1
            ? UiDraw.fontWidth(text)
            : UiDraw.fontWidth(text) * scale;
        if (anchorX != null) {
            x = Math.round(anchorX - (align == Align.CENTER ? w / 2f
                : align == Align.RIGHT ? w : 0));
        }
        this.width = w;
    }

    @Override
    protected void draw(UiDraw d) {
        if (text == null || text.isEmpty()) return;
        float dx = x;
        if (anchorX != null) {
            dx = anchorX - (align == Align.CENTER ? width / 2f : align == Align.RIGHT ? width : 0);
        }
        if (scale == 1) {
            if (shadow) d.textShadow(text, dx, y, color);
            else d.text(text, dx, y, color);
        } else if (shadow) {
            d.textScaledShadow(text, dx, y, scale, color);
        } else {
            d.textScaled(text, dx, y, scale, color);
        }
    }
}
