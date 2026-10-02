package com.voxelgame.ui2;

/**
 * Workshop plank button: a wood board with corner rivets, a label in ink
 * that adapts to the timber's brightness, and a brass underline easing in
 * on hover. Hovering lifts the board 1px; pressing sinks it 2px. A focused
 * button wears a pulsing brass frame.
 */
public class Button2 extends Element {

    private String label;
    private Runnable action;
    public boolean danger;

    public Button2(String label, int w, int h, Runnable action) {
        this.label = label;
        this.width = w;
        this.height = h;
        this.action = action;
        this.focusable = true;
    }

    public Button2 label(String l) {
        this.label = l;
        return this;
    }

    public String label() {
        return label;
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (!interactive() || button != 0 || !contains(mx, my)) return false;
        UiSounds.click();
        if (action != null) action.run();
        return true;
    }

    @Override
    protected void draw(UiDraw d) {
        // Hover lifts, press sinks - net vertical offset in GUI pixels
        float lift = hover01 - press01 * 2f;
        float x = this.x;
        float y = this.y + 1 - lift;
        float hover = hover01;

        if (!enabled) {
            d.plank(x, y, width, height, 0);
            d.textCentered(label, x + width / 2.0f, labelY(y),
                UiTheme.fade(UiTheme.INSTANCE.buttonTextDimColor(), 0.5f));
            return;
        }

        d.plank(x, y, width, height, hover);

        // Ember wash on dangerous buttons
        if (danger) {
            d.fill(x + 2, y + 2, width - 4, height - 4,
                UiTheme.fade(0x28C4552F, 0.4f + 0.6f * hover));
        }

        // Brass underline eases in from the left
        if (hover > 0.02f) {
            float lw = (width - 20) * UiTheme.easeOutCubic(hover);
            d.fill(x + 10, y + height - 4, lw, 1,
                UiTheme.fade(danger ? UiTheme.EMBER_BRIGHT : UiTheme.BRASS,
                    0.55f + 0.45f * hover));
        }

        d.cornerRivets(x, y, width, height);

        int ink = UiTheme.INSTANCE.buttonTextColor();
        int dim = UiTheme.INSTANCE.buttonTextDimColor();
        int labelColor = danger
            ? UiTheme.lerp(ink, UiTheme.EMBER_BRIGHT, hover)
            : UiTheme.lerp(dim, ink, Math.max(hover, 1f - press01));
        d.textCentered(label, x + width / 2.0f, labelY(y), labelColor);

        if (focused) {
            d.ui.drawRectOutline(x - 1, y - 1, width + 2, height + 2,
                d.aAlpha(UiTheme.fade(UiTheme.BRASS, 0.55f + 0.35f * (float) Math.sin(d.time * 5))));
        }
    }

    private int labelY(float y) {
        return Math.round(y + (height - com.voxelgame.ui.FontRenderer.GLYPH_H) / 2f);
    }
}
