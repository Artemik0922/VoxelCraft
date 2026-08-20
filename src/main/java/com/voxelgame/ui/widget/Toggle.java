package com.voxelgame.ui.widget;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.GuiAssets;

/**
 * On/off toggle rendered as a small switch next to a label. The switch has
 * two positions with an amber indicator on the active side.
 */
public class Toggle extends Widget {

    public interface Listener {
        void onToggled(Toggle source, boolean value);
    }

    private final String label;
    private final Listener listener;
    private boolean value;

    private static final int SWITCH_W = 24;
    private static final int SWITCH_H = 10;

    public Toggle(int x, int y, int width, int height, String label,
                  boolean value, Listener listener) {
        super(x, y, width, height);
        this.label = label;
        this.value = value;
        this.listener = listener;
    }

    public boolean getValue() { return value; }

    public void setValue(boolean v) { this.value = v; }

    @Override
    public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
        if (!visible) return;

        int fadeAlpha = (alpha >>> 24) & 0xFF;
        int textY = y + (height - FontRenderer.GLYPH_H) / 2;

        // Background panel
        MenuTheme.drawPanel(ui, fadeAlpha << 24, x, y, width, height, hovered);
        
        // Switch on the right side
        int swX = x + width - SWITCH_W - 6;
        int swY = y + (height - SWITCH_H) / 2;

        // Switch groove
        int grooveCol = value
            ? MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.ACCENT_DIM)
            : MenuTheme.col(fadeAlpha << 24, 255, 0x1A1F35);
        ui.fillRect(swX, swY, SWITCH_W, SWITCH_H, grooveCol);

        // Switch border
        int bdr = MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.PANEL_BORDER);
        ui.fillRect(swX, swY, SWITCH_W, 1, bdr);
        ui.fillRect(swX, swY + SWITCH_H - 1, SWITCH_W, 1, bdr);
        ui.fillRect(swX, swY, 1, SWITCH_H, bdr);
        ui.fillRect(swX + SWITCH_W - 1, swY, 1, SWITCH_H, bdr);

        // Knob inside the switch
        int knobX = value ? swX + SWITCH_W - SWITCH_H + 1 : swX + 1;
        ui.fillRect(knobX, swY + 1, SWITCH_H - 2, SWITCH_H - 2,
            MenuTheme.col(fadeAlpha << 24, 255, value ? MenuTheme.SUNSET_HI : MenuTheme.PANEL_EDGE_L));
        ui.fillRect(knobX + 1, swY + 2, SWITCH_H - 4, 1,
            MenuTheme.col(fadeAlpha << 24, 255, value ? MenuTheme.ACCENT : MenuTheme.PANEL_INNER));

        // Label
        int textW = width - SWITCH_W - 16;
        String shown = font.trimToWidth(label, textW);
        String state = value ? ": ON" : ": OFF";
        int stateW = font.width(state);
        int labelCol = hovered
            ? MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.TEXT_BRIGHT)
            : MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.TEXT_LABEL);
        int stateCol = value
            ? MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.ACCENT_LIGHT)
            : MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.TEXT_DIM);

        if (!enabled) {
            labelCol = MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.TEXT_DIM);
            stateCol = MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.TEXT_DIM);
        }

        font.drawWithShadow(ui, shown, x + 8, textY, labelCol);
        font.drawWithShadow(ui, state, swX - stateW - 4, textY, stateCol);
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (!visible || !enabled || button != 0 || !contains(mx, my)) return false;
        value = !value;
        if (listener != null) listener.onToggled(this, value);
        return true;
    }
}
