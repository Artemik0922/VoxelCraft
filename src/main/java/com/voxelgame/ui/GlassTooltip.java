package com.voxelgame.ui;

import java.util.List;

/**
 * Shared glass tooltip used by every inventory screen, replacing the old
 * per-screen hand-rolled boxes (which drifted in style). Renders a small
 * rounded glass card with a soft shadow, a light rim and white/grey text
 * lines, clamped to the screen edges.
 */
public final class GlassTooltip {

    private GlassTooltip() {}

    /**
     * @param lines   text lines; the first renders bright, the rest grey
     * @param mx,my   cursor position in GUI pixels
     * @param screenW, screenH canvas size for edge clamping
     */
    public static void draw(UIRenderer ui, FontRenderer font, List<String> lines,
                            int mx, int my, int screenW, int screenH) {
        draw(ui, font, lines, null, mx, my, screenW, screenH);
    }

    /**
     * @param colors  optional per-line ARGB colours; defaults to bright first
     *                line then muted grey, and transparent entries fall back
     *                to that default too
     */
    public static void draw(UIRenderer ui, FontRenderer font, List<String> lines,
                            int[] colors, int mx, int my, int screenW, int screenH) {
        int maxW = 0;
        for (String l : lines) maxW = Math.max(maxW, font.width(l));

        int lh = FontRenderer.LINE_HEIGHT;
        int tw = maxW + 10;
        int th = lines.size() * lh + 8;

        int tx = mx + 12;
        int ty = my - th - 6;
        if (tx + tw > screenW) tx = mx - tw - 6;
        if (ty < 2) ty = my + 14;
        if (tx < 2) tx = 2;

        GuiAssets g = GuiAssets.INSTANCE;
        ui.drawNineSlice(g.glassShadow, tx + 2, ty + 3, tw, th,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xAA000000);
        ui.drawNineSlice(g.glassPanel, tx, ty, tw, th,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xF8202432);
        ui.fillRect(tx + 8, ty + 1, tw - 16, 1, 0x66FFFFFF);

        int y = ty + 4;
        for (int i = 0; i < lines.size(); i++) {
            int col;
            if (colors != null && i < colors.length && colors[i] != 0) {
                col = colors[i];
            } else {
                col = (i == 0) ? 0xFFFFFFFF : 0xFFB8C2DC;
            }
            font.drawWithShadow(ui, lines.get(i), tx + 5, y, col);
            y += lh;
        }
    }
}