"""One-off: restyle GameHud + Minimap to the Workshop theme."""

# ============ GameHud: warm dock, brass selection, Workshop sprites ============
p = 'src/main/java/com/voxelgame/ui/GameHud.java'
s = open(p, encoding='utf-8').read()

s = s.replace('''        ui.drawNineSlice(gui.glassPanel, px, py, pw, ph,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF10141E);''',
'''        ui.drawNineSlice(gui.glassPanel, px, py, pw, ph,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF332314);
        // Brass trim along the dock's top edge
        ui.useSolidColor();
        ui.fillRect(px + 2, py, pw - 4, 1, 0xFF8A6420);
        ui.fillRect(px + 2, py + 1, pw - 4, 1, 0x50EDD9A0);''')

s = s.replace('''            ui.drawNineSlice(gui.glassSlot, sx, sy, SLOT, SLOT,
                3, GuiAssets.SLOT_SIZE, 0xFFFFFFFF);''',
'''            ui.drawNineSlice(gui.glassSlot, sx, sy, SLOT, SLOT,
                3, GuiAssets.SLOT_SIZE, 0xFF3A2A1A);''')

s = s.replace('''        ui.fillRect(x - 1, y - 1, size + 2, size + 2, 0x2A000000 | MenuTheme.ACCENT);
        ui.fillRect(x, y, size, 1, MenuTheme.ACCENT_LIGHT);
        ui.fillRect(x, y + size - 1, size, 1, MenuTheme.ACCENT);
        ui.fillRect(x, y, 1, size, MenuTheme.ACCENT_LIGHT);
        ui.fillRect(x + size - 1, y, 1, size, MenuTheme.ACCENT);''',
'''        ui.fillRect(x - 1, y - 1, size + 2, size + 2, 0x2A000000 | 0xFFC9973B);
        ui.fillRect(x, y, size, 1, 0xFFEDD9A0);
        ui.fillRect(x, y + size - 1, size, 1, 0xFFC9973B);
        ui.fillRect(x, y, 1, size, 0xFFEDD9A0);
        ui.fillRect(x + size - 1, y, 1, size, 0xFFC9973B);''')

s = s.replace('''            int sprite;
            if (filled >= perHeart - 0.001f) {
                sprite = gui.heartFull;
            } else if (filled >= perHeart * 0.5f) {
                sprite = gui.heartHalf;
            } else {
                sprite = gui.heartEmpty;
            }

            // Always draw the container so the bar keeps its length
            if (sprite != gui.heartEmpty) {
                ui.drawSprite(gui.heartEmpty, x, y, HEART, HEART);
            }
            ui.drawSprite(sprite, x, y + (flash ? bump : 0), HEART, HEART, tint);''',
'''            var mat = com.voxelgame.ui2.UiMaterials.INSTANCE;
            int sprite;
            if (filled >= perHeart - 0.001f) {
                sprite = mat.heartFull;
            } else if (filled >= perHeart * 0.5f) {
                sprite = mat.heartHalf;
            } else {
                sprite = mat.heartEmpty;
            }

            // Always draw the container so the bar keeps its length
            if (sprite != mat.heartEmpty) {
                ui.drawSprite(mat.heartEmpty, x, y, HEART, HEART);
            }
            ui.drawSprite(sprite, x, y + (flash ? bump : 0), HEART, HEART, tint);''')

s = s.replace('''            int sprite;
            if (filled >= perBar - 0.001f) {
                sprite = gui.drumstickFull;
            } else if (filled >= perBar * 0.5f) {
                sprite = gui.drumstickHalf;
            } else {
                sprite = gui.drumstickEmpty;
            }
            ui.drawSprite(sprite, x, y, HEART, HEART);''',
'''            var mat = com.voxelgame.ui2.UiMaterials.INSTANCE;
            int sprite;
            if (filled >= perBar - 0.001f) {
                sprite = mat.drumstickFull;
            } else if (filled >= perBar * 0.5f) {
                sprite = mat.drumstickHalf;
            } else {
                sprite = mat.drumstickEmpty;
            }
            ui.drawSprite(sprite, x, y, HEART, HEART);''')

s = s.replace('ui.drawSprite(gui.bubble, x, y, HEART, HEART);',
              'ui.drawSprite(com.voxelgame.ui2.UiMaterials.INSTANCE.bubble, x, y, HEART, HEART);')

s = s.replace('''        ui.fillRect(x, y, barW, barH, 0xFF1B2332);
        ui.fillRect(x, y - 1, barW, 1, 0xFF2A3450);''',
'''        ui.fillRect(x, y, barW, barH, 0xFF26180E);
        ui.fillRect(x, y - 1, barW, 1, 0xFF5A4326);''')

open(p, 'w', encoding='utf-8').write(s)
print('GameHud patched')

# ============ Minimap: brass compass + proper arrow ============
p = 'src/main/java/com/voxelgame/ui/Minimap.java'
s = open(p, encoding='utf-8').read()

s = s.replace('''    private static final int COLOR_UNKNOWN = 0xFFD8DEE8;
    private static final int OUTLINE = 0xE012141E;
    private static final int SHADOW = 0x38303C50;''',
'''    private static final int COLOR_UNKNOWN = 0xFFD8CBB0;
    private static final int OUTLINE = 0xE0261A10;
    private static final int SHADOW = 0x38261A10;''')

s = s.replace('''    private static final int RING_EDGE = 0xFFC4CEDC;
    private static final int RING_INNER = 0xFFDDE4EE;
    private static final int RING_LIGHT = 0xFFFFFFFF;
    private static final int RING_DARK = 0xFFAEB9CC;
    private static final int RING_TEXT = 0xFF3A4258;
    private static final int PILL = 0x92FFFFFF;
    private static final int CAPTION = 0xFF24324A;
    private static final int CAPTION_SUB = 0xFF5C6A84;''',
'''    private static final int RING_EDGE = 0xFF8A6420;
    private static final int RING_INNER = 0xFFC9973B;
    private static final int RING_LIGHT = 0xFFEDD9A0;
    private static final int RING_DARK = 0xFF6E5220;
    private static final int RING_TEXT = 0xFF4A3220;
    private static final int PILL = 0x92E8D9B8;
    private static final int CAPTION = 0xFF2A1D12;
    private static final int CAPTION_SUB = 0xFF6E5A3A;''')

s = s.replace('private static final int NEEDLE = 0xFFF4F7FF;',
              'private static final int NEEDLE = 0xFFEDD9A0;')

s = s.replace('''        // Outline pass: the same sweep nudged one pixel in each direction
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox == 0 && oy == 0) continue;
                needleLine(ui, (int) ccx + ox, (int) ccy + oy, ax, az, len, OUTLINE);
            }
        }
        needleLine(ui, (int) ccx, (int) ccy, ax, az, len, NEEDLE);
        ui.fillRect(ccx - 1, ccy - 1, 3, 3, 0xFF0D111C);
        ui.fillRect(ccx, ccy, 1, 1, NEEDLE);
    }

    private void needleLine(UIRenderer ui, int cx0, int cy0, float ax, float az, int len, int color) {
        for (int t = 1; t <= len; t++) {
            ui.fillRect(cx0 + java.lang.Math.round(ax * t),
                cy0 + java.lang.Math.round(az * t), 2, 2, color);
        }
    }''',
'''        // Outline pass: the same arrow nudged one pixel in each direction
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox == 0 && oy == 0) continue;
                needleArrow(ui, (int) ccx + ox, (int) ccy + oy, ax, az, len, OUTLINE);
            }
        }
        needleArrow(ui, (int) ccx, (int) ccy, ax, az, len, NEEDLE);
        ui.fillRect(ccx - 1, ccy - 1, 3, 3, 0xFF26180E);
        ui.fillRect(ccx, ccy, 1, 1, NEEDLE);
    }

    /**
     * Compass needle as a real arrow: a thin shaft that tapers into a
     * triangular head over the last 6 pixels.
     */
    private void needleArrow(UIRenderer ui, int cx0, int cy0, float ax, float az, int len, int color) {
        float perpX = -az, perpZ = ax;
        int headLen = 6;
        // Shaft
        for (int t = 1; t <= len - headLen; t++) {
            ui.fillRect(cx0 + java.lang.Math.round(ax * t),
                cy0 + java.lang.Math.round(az * t), 2, 2, color);
        }
        // Head: widens towards the tip
        for (int k = 0; k < headLen; k++) {
            int t = len - headLen + k;
            int half = Math.round(k * 0.7f);
            for (int s = -half; s <= half; s++) {
                ui.fillRect(cx0 + java.lang.Math.round(ax * t + perpX * s),
                    cy0 + java.lang.Math.round(az * t + perpZ * s), 2, 2, color);
            }
        }
    }''')

open(p, 'w', encoding='utf-8').write(s)
print('Minimap patched')
