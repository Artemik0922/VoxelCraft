package com.voxelgame.ui2;

import com.voxelgame.ui.FontRenderer;

/**
 * Setting slider: a brass knob riding a dark groove, the label carved to
 * the left and the value stamped to the right. Full-row height 22.
 */
public class Slider2 extends Element {

    public interface Listener {
        void onChanged(Slider2 slider, double value);
    }

    private static final int TRACK_H = 5;
    private static final int KNOB = 10;

    private final String label;
    private final double min, max;
    private double value;
    private boolean integerSteps;
    private String valueText = "";
    private Listener listener;
    private boolean dragging;

    public Slider2(String label, double min, double max, double value) {
        this.label = label;
        this.min = min;
        this.max = max;
        this.value = value;
        this.height = 22;
        this.focusable = true;
        this.width = 200;
    }

    public Slider2 listener(Listener l) {
        this.listener = l;
        return this;
    }

    public Slider2 integer() {
        this.integerSteps = true;
        return this;
    }

    public Slider2 valueText(String s) {
        this.valueText = s;
        return this;
    }

    public double getValue() {
        return value;
    }

    public int getIntValue() {
        return (int) Math.round(value);
    }

    public void setValue(double v) {
        value = Math.max(min, Math.min(max, v));
    }

    private float knobX() {
        float t = (float) ((value - min) / (max - min));
        int trackW = width - KNOB - 2;
        return x + 1 + t * trackW;
    }

    private void setFromMouse(float mx) {
        int trackW = width - KNOB - 2;
        float t = (mx - (x + 1 + KNOB / 2f)) / trackW;
        t = Math.max(0f, Math.min(1f, t));
        double v = min + t * (max - min);
        if (integerSteps) v = Math.round(v);
        if (v != value) {
            value = v;
            if (listener != null) listener.onChanged(this, value);
        }
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (!interactive() || button != 0) return false;
        if (my >= y && my < y + height && mx >= x && mx < x + width) {
            dragging = true;
            setFromMouse(mx);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(float mx, float my, int button) {
        if (dragging && interactive()) {
            setFromMouse(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button);
    }

    @Override
    public void mouseReleased(float mx, float my, int button) {
        dragging = false;
        super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (!interactive()) return false;
        double step = (max - min) / 20.0;
        boolean left = key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
        boolean right = key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
        if (!left && !right) return false;
        double v = value + (right ? step : -step);
        if (integerSteps) v = Math.round(v);
        value = Math.max(min, Math.min(max, v));
        if (listener != null) listener.onChanged(this, value);
        return true;
    }

    @Override
    protected void draw(UiDraw d) {
        // Label row: shadowed bright parchment so it reads on any timber
        d.textShadow(label, x, y + 1, UiTheme.TEXT_ON_WOOD);
        String vt = valueText.isEmpty() ? formatValue() : valueText;
        d.textShadow(vt, x + width - d.font.width(vt), y + 1, UiTheme.BRASS_LIGHT);

        // Groove: dark channel with a brass bottom hairline
        int gy = y + 14;
        d.fill(x, gy, width, TRACK_H, 0xFF26180E);
        d.fill(x, gy + TRACK_H - 1, width, 1, 0xFF5A4326);
        d.fill(x + 1, gy + 1, width - 2, 1, 0xFF33220F);

        // Knob
        float kx = knobX();
        d.ui.drawSprite(d.mat.knob, Math.round(kx + d.offX), Math.round(gy + TRACK_H / 2f - KNOB / 2f + d.offY),
            KNOB, KNOB, d.a(0xFFFFFF));
        if (focused) {
            d.ui.drawRectOutline(x - 1, y - 1, width + 2, height + 2,
                d.aAlpha(UiTheme.fade(UiTheme.BRASS, 0.5f)));
        }
    }

    private String formatValue() {
        if (integerSteps) return String.valueOf((int) Math.round(value));
        return String.format("%.0f%%", (value - min) / (max - min) * 100);
    }
}
