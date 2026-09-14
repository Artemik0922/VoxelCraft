package com.voxelgame.ui.widget;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.GuiAssets;

/**
 * Horizontal slider with a draggable knob, restyled to match the menu
 * theme. The groove is drawn as a panel and the knob as a smaller panel
 * with an amber highlight when hovered or dragged.
 */
public class Slider extends Widget {

    public interface Listener {
        void onChanged(Slider source, double value);
    }

    private final String prefix;
    private final double min;
    private final double max;
    private final boolean integer;
    private final Listener listener;

    private double normalized;
    private boolean dragging = false;

    private static final int KNOB_WIDTH = 10;

    public Slider(int x, int y, int width, int height, String prefix,
                  double min, double max, double value, boolean integer, Listener listener) {
        super(x, y, width, height);
        this.prefix = prefix;
        this.min = min;
        this.max = max;
        this.integer = integer;
        this.listener = listener;
        this.normalized = clamp01((value - min) / (max - min));
    }

    public double getValue() {
        double v = min + (max - min) * normalized;
        return integer ? Math.round(v) : v;
    }

    public int getIntValue() { return (int) Math.round(getValue()); }

    public void setValue(double value) {
        normalized = clamp01((value - min) / (max - min));
    }

    @Override
    public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
        if (!visible) return;

        int fadeAlpha = (alpha >>> 24) & 0xFF;

        // Draw groove as a rounded glass inset
        ui.drawNineSlice(tex.glassTrack, x, y, width, height, 4,
            GuiAssets.GLASS_WIDGET, MenuTheme.col(fadeAlpha << 24, 255, 0xFF10141F));

        // Knob - a glass bead riding on the groove
        int knobX = x + 1 + (int) (normalized * (width - KNOB_WIDTH - 2));
        boolean hot = hovered || dragging;
        MenuTheme.drawPanel(ui, fadeAlpha << 24, knobX, y + 1, KNOB_WIDTH, height - 2, hot);

        // Amber accent strip on the left of the knob when hot
        if (hot) {
            ui.fillRect(knobX + 1, y + 3, 3, height - 6,
                MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.ACCENT));
        }

        // Text label on top
        String text = prefix + ": " + formatValue();
        float textY = y + (height - FontRenderer.GLYPH_H) / 2.0f;
        int textCol = enabled
            ? MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.TEXT_LABEL)
            : MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.TEXT_DIM);
        font.drawCenteredWithShadow(ui, text, x + width / 2.0f, textY, textCol);
    }

    private String formatValue() {
        double v = getValue();
        if (integer) return String.valueOf((int) v);
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (!visible || !enabled || button != 0 || !contains(mx, my)) return false;
        dragging = true;
        applyMouse(mx);
        return true;
    }

    @Override
    public boolean mouseDragged(float mx, float my, int button) {
        if (!dragging) return false;
        applyMouse(mx);
        return true;
    }

    @Override
    public void mouseReleased(float mx, float my, int button) {
        dragging = false;
    }

    private void applyMouse(float mx) {
        double before = getValue();
        normalized = clamp01((mx - x - KNOB_WIDTH / 2.0) / (width - KNOB_WIDTH));

        double after = getValue();
        if (listener != null && before != after) {
            listener.onChanged(this, after);
        }
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
