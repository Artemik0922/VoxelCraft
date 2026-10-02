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

    private float knobProgress = 0;

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
    public void update(double deltaTime) {
        float target = value ? 1.0f : 0.0f;
        knobProgress += (target - knobProgress) * (float) Math.min(1.0, deltaTime * 12);
    }

    @Override
    public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
        if (!visible) return;

        int fadeAlpha = (alpha >>> 24) & 0xFF;
        int textY = y + (height - FontRenderer.GLYPH_H) / 2;

        // Background glass panel
        MenuTheme.drawPanel(ui, fadeAlpha << 24, x, y, width, height, hovered);

        // Switch on the right side - rounded glass pill
        int swX = x + width - SWITCH_W - 6;
        int swY = y + (height - SWITCH_H) / 2;

        // Pill groove: accent when on, quiet inset when off
        int groove = value ? MenuTheme.ACCENT_DIM
                           : (MenuTheme.lightContext ? 0xFFDDE4EE : 0xFF141A28);
        ui.drawNineSlice(tex.glassTrack, swX, swY, SWITCH_W, SWITCH_H, 4,
            GuiAssets.GLASS_WIDGET, MenuTheme.col(fadeAlpha << 24, 255, groove));
        if (value) {
            // Accent fill strength follows the knob so it fades in smoothly
            ui.drawNineSlice(tex.glassTrack, swX, swY, SWITCH_W, SWITCH_H, 4,
                GuiAssets.GLASS_WIDGET, MenuTheme.col(fadeAlpha << 24, (int) (110 * knobProgress), MenuTheme.ACCENT));
        }

        // Knob - a small round bead sliding between the two ends
        int bead = SWITCH_H - 2;
        int knobX = swX + 1 + (int) (knobProgress * (SWITCH_W - bead - 2));
        int knobCol = MenuTheme.lightContext
            ? MenuTheme.lerp(0xFFFFFFFF, MenuTheme.ACCENT, knobProgress)
            : MenuTheme.lerp(0xFFB8C2DC, MenuTheme.SUNSET_HI, knobProgress);
        ui.drawNineSlice(tex.glassPanel, knobX, swY + 1, bead, bead,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET,
            MenuTheme.col(fadeAlpha << 24, 255, knobCol));

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
