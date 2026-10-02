package com.voxelgame.ui2;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.UIRenderer;

/**
 * Per-frame drawing facade passed down the element tree. Bundles the
 * renderer, font and material pack with the frame's mouse position and
 * time, and carries the accumulated element alpha plus the entrance
 * animation offset so widgets don't thread those through every call.
 */
public final class UiDraw {

    public final UIRenderer ui;
    public final FontRenderer font;
    public final UiMaterials mat;
    public float mx, my;
    public double time;

    /** Multiplied down the tree: 1 = fully opaque. */
    public float alpha = 1f;
    /** Entrance offset in GUI pixels, applied by animated containers. */
    public float offX, offY;

    public UiDraw(UIRenderer ui, FontRenderer font, UiMaterials mat) {
        this.ui = ui;
        this.font = font;
        this.mat = mat;
    }

    /** Applies the tree alpha to a full-alpha ARGB colour. */
    public int a(int rgb) {
        return UiTheme.fade(0xFF000000 | rgb, alpha);
    }

    /** Font width shortcut for layout code without a UiDraw instance. */
    public static int fontWidth(String s) {
        return INSTANCE_FONT.width(s);
    }

    private static FontRenderer INSTANCE_FONT;

    /** Called once by the scene manager after the font is available. */
    public static void initFont(FontRenderer font) {
        INSTANCE_FONT = font;
    }

    public int aAlpha(int argb) {
        return UiTheme.fade(argb, alpha);
    }

    // ------------------------------------------------------------------
    // Workshop primitives
    // ------------------------------------------------------------------

    /** Wood board 9-slice, tinted with the active finish. */
    public void plank(float x, float y, float w, float h, float hover01) {
        UiTheme.Finish f = UiTheme.INSTANCE.finish();
        int tint = UiTheme.lerp(f.base, f.light, hover01 * 0.5f);
        ui.drawNineSlice(mat.plank, x + offX, y + offY, w, h,
            UiTheme.WOOD_BORDER, UiTheme.WIDGET_TEX, a(tint));
    }

    /** Deep card backing 9-slice, tinted darker than the boards. */
    public void card(float x, float y, float w, float h) {
        UiTheme.Finish f = UiTheme.INSTANCE.finish();
        ui.drawNineSlice(mat.card, x + offX, y + offY, w, h,
            8, 32, a(f.deep));
    }

    /** Tiled plank wall backdrop, tinted dark by the caller. */
    public void wall(float x, float y, float w, float h, int tint) {
        ui.drawTiled(mat.wall, x + offX, y + offY, w, h, 32, aAlpha(tint));
    }

    /** Kraft paper 9-slice. */
    public void paper(float x, float y, float w, float h) {
        ui.drawNineSlice(mat.paper, x + offX, y + offY, w, h,
            UiTheme.PAPER_BORDER, UiTheme.PAPER_TEX, aAlpha(0xFFFFFFFF));
    }

    /** Paper card with an ink hairline just inside the edge. */
    public void paperCard(float x, float y, float w, float h) {
        paper(x, y, w, h);
        ui.drawRectOutline(x + offX + 2, y + offY + 2, w - 4, h - 4, aAlpha(0x502A1D12));
    }

    /** Horizontal brass trim line. */
    public void brassLine(float x, float y, float w) {
        ui.fillRect(x + offX, y + offY, w, 1, a(UiTheme.BRASS_DIM));
        ui.fillRect(x + offX, y + offY + 1, w, 1, aAlpha(0x48EDD9A0));
    }

    /** Corner rivet (5x5), the workshop's signature fastener. */
    public void rivet(float x, float y) {
        ui.drawSprite(mat.rivet, Math.round(x + offX), Math.round(y + offY), 5, 5, a(0xFFFFFF));
    }

    /** Rivets in all four corners of the given rect (3px inset). */
    public void cornerRivets(float x, float y, float w, float h) {
        rivet(x + 3, y + 3);
        rivet(x + w - 8, y + 3);
        rivet(x + 3, y + h - 8);
        rivet(x + w - 8, y + h - 8);
    }

    /** Slot socket at 1:1 (18px). */
    public void socket(float x, float y, boolean hover) {
        ui.drawSprite(hover ? mat.slotSocketHover : mat.slotSocket,
            Math.round(x + offX), Math.round(y + offY),
            UiTheme.SLOT_SIZE, UiTheme.SLOT_SIZE, a(0xFFFFFF));
    }

    /** Text helper with the tree alpha applied. */
    public int text(String s, float x, float y, int rgb) {
        return font.draw(ui, s, x + offX, y + offY, a(rgb));
    }

    public int textShadow(String s, float x, float y, int rgb) {
        return font.drawWithShadow(ui, s, x + offX, y + offY, a(rgb));
    }

    public int textCentered(String s, float cx, float y, int rgb) {
        return font.draw(ui, s, cx - font.width(s) / 2.0f + offX, y + offY, a(rgb));
    }

    public void textScaled(String s, float x, float y, int scale, int rgb) {
        font.drawScaled(ui, s, x + offX, y + offY, scale, a(rgb));
    }

    public void textScaledShadow(String s, float x, float y, int scale, int rgb) {
        font.drawScaledWithShadow(ui, s, x + offX, y + offY, scale, a(rgb));
    }

    public void textScaledCentered(String s, float cx, float y, int scale, int rgb) {
        font.drawScaled(ui, s, cx - font.scaledWidth(s, scale) / 2.0f + offX,
            y + offY, scale, a(rgb));
    }

    public void fill(float x, float y, float w, float h, int argb) {
        ui.fillRect(x + offX, y + offY, w, h, aAlpha(argb));
    }
}
