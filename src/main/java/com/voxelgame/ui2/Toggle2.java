package com.voxelgame.ui2;

/**
 * Setting toggle: label on the left, a wooden switch with a sliding brass
 * lever on the right. Full-row height 20; Space/Enter toggles when focused.
 */
public class Toggle2 extends Element {

    public interface Listener {
        void onToggled(Toggle2 toggle, boolean state);
    }

    private static final int SWITCH_W = 26;
    private static final int SWITCH_H = 12;

    private final String label;
    private boolean state;
    private Listener listener;
    /** Hint text drawn dimmer under the label when non-null. */
    public String hint;

    public Toggle2(String label, boolean state) {
        this.label = label;
        this.state = state;
        this.height = 20;
        this.focusable = true;
        this.width = 200;
    }

    public Toggle2 listener(Listener l) {
        this.listener = l;
        return this;
    }

    public boolean getState() {
        return state;
    }

    public void setState(boolean s) {
        this.state = s;
    }

    private float lever01() {
        return UiTheme.easeOutCubic(hover01 * 0f + (state ? 1f : 0f));
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (!interactive() || button != 0 || !contains(mx, my)) return false;
        toggle();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (!interactive()) return false;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
            || key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            toggle();
            return true;
        }
        return false;
    }

    private void toggle() {
        state = !state;
        if (listener != null) listener.onToggled(this, state);
    }

    @Override
    protected void draw(UiDraw d) {
        int textCol = enabled
            ? UiTheme.lerp(UiTheme.TEXT_ON_WOOD_DIM, UiTheme.TEXT_ON_WOOD, hover01)
            : UiTheme.fade(UiTheme.TEXT_ON_WOOD_DIM, 0.55f);
        d.textShadow(label, x, y + 1, textCol);
        if (hint != null) {
            d.textShadow(hint, x, y + 12, UiTheme.fade(UiTheme.TEXT_ON_WOOD_DIM, 0.9f));
        }

        int sw = SWITCH_W, sh = SWITCH_H;
        int sx = x + width - sw;
        int sy = y + (height - sh) / 2;

        // Switch body: sunken wooden channel
        d.fill(sx, sy, sw, sh, 0xFF26180E);
        d.ui.drawRectOutline(sx + d.offX, sy + d.offY, sw, sh, d.a(UiTheme.BRASS_DIM));

        // Brass lever slides between the ends
        float t = lever01();
        int lx = Math.round(sx + 2 + t * (sw - 4 - 8));
        d.ui.drawSprite(d.mat.knob, Math.round(lx + d.offX), Math.round(sy + sh / 2f - 5 + d.offY),
            10, 10, d.a(0xFFFFFF));
        // Lit end cap: brass when on, dark when off
        d.fill(state ? sx + sw - 3 : sx + 1, sy + 2, 2, sh - 4,
            state ? UiTheme.BRASS : 0xFF33220F);

        if (focused) {
            d.ui.drawRectOutline(x - 1, y - 1, width + 2, height + 2,
                d.aAlpha(UiTheme.fade(UiTheme.BRASS, 0.5f)));
        }
    }
}
