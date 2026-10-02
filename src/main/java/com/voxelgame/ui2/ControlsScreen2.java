package com.voxelgame.ui2;

import com.voxelgame.core.KeyBindings;
import com.voxelgame.core.Settings;
import com.voxelgame.ui.FontRenderer;

import java.util.ArrayList;
import java.util.List;

import static com.voxelgame.core.Language.tr;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * Controls: sensitivity, invert-Y and the rebindable key list. The list is
 * drawn as paper rows with brass bind planks; clicking a plank starts
 * capture ("press a key"), Escape while capturing resets the binding.
 */
public class ControlsScreen2 extends SettingsScene {

    public interface Listener {
        void onSensitivityChanged(float value);

        void onInvertYChanged(boolean on);

        void onBindingsChanged();

        void onClosed();
    }

    private static final int ROW_H = 22;
    private static final int BIND_W = 92;

    private final Settings settings;
    private final KeyBindings keys;
    private final Listener listener;

    private KeyBindings.Action awaiting = null;

    private int listX, listY, listW, listH;
    private float scroll = 0;

    public ControlsScreen2(Settings settings, KeyBindings keys,
                           Listener listener, boolean overlayWorld) {
        super(overlayWorld);
        this.settings = settings;
        this.keys = keys;
        this.listener = listener;
    }

    @Override
    public boolean closableWithEscape() { return awaiting == null; }

    @Override
    protected void buildContent() {
        int lw = boardW - 28;
        int lx = boardX + 14;

        Slider2 sensitivity = new Slider2(tr("options.sensitivity"),
            Settings.MIN_SENSITIVITY, Settings.MAX_SENSITIVITY, settings.mouseSensitivity);
        sensitivity.x = lx;
        sensitivity.y = boardY + 16;
        sensitivity.width = lw;
        sensitivity.listener((s, v) -> listener.onSensitivityChanged((float) v));
        add(sensitivity);

        Toggle2 invertY = new Toggle2(tr("options.invertY"), settings.invertY);
        invertY.x = lx;
        invertY.y = boardY + 16 + ROW_H + 6;
        invertY.width = lw;
        invertY.listener((t, v) -> listener.onInvertYChanged(v));
        add(invertY);

        listX = lx;
        listY = boardY + 16 + (ROW_H + 6) * 2 + 8;
        listW = lw;
        listH = boardY + boardH - 52 - listY;

        Button2 reset = new Button2(tr("controls.resetAll"), (lw - 8) / 2, 22,
            () -> {
                keys.resetAll();
                listener.onBindingsChanged();
            });
        reset.x = lx;
        reset.y = boardY + boardH - 38;
        add(reset);

        addDoneButton(tr("options.done"), listener::onClosed);
    }

    @Override
    protected String title() { return tr("options.controls.title"); }

    @Override
    public void onClosed() {
        settings.save();
    }

    @Override
    protected void renderForeground(UiDraw d) {
        // List inset
        d.fill(listX, listY, listW, listH, 0xFF26180E);

        KeyBindings.Action[] all = KeyBindings.Action.values();
        int visible = listH / ROW_H;
        int first = (int) (scroll / ROW_H);

        for (int i = 0; i < visible; i++) {
            int index = first + i;
            if (index >= all.length) break;

            KeyBindings.Action action = all[index];
            int y = listY + i * ROW_H;

            if (index % 2 == 0) {
                d.fill(listX + 1, y, listW - 2, ROW_H, 0x14E8D9B8);
            }

            d.textShadow(tr(action.translationKey), listX + 4,
                y + (ROW_H - FontRenderer.GLYPH_H) / 2, UiTheme.TEXT_ON_WOOD);

            int bx = listX + listW - BIND_W - 4;
            boolean hover = d.mx >= bx && d.mx < bx + BIND_W
                && d.my >= y + 1 && d.my < y + ROW_H - 1;
            boolean capturing = awaiting == action;

            d.plank(bx, y + 1, BIND_W, ROW_H - 2, capturing || hover ? 1f : 0f);

            String label = capturing
                ? "> " + tr("controls.pressKey") + " <"
                : KeyBindings.keyName(keys.get(action));

            int colour;
            if (capturing) {
                colour = UiTheme.BRASS_LIGHT;
            } else if (keys.conflictFor(action, keys.get(action)) != null) {
                colour = UiTheme.EMBER_BRIGHT;
            } else {
                colour = UiTheme.INSTANCE.buttonTextColor();
            }
            d.textCentered(d.font.trimToWidth(label, BIND_W - 6),
                bx + BIND_W / 2.0f, y + 1 + (ROW_H - 2 - FontRenderer.GLYPH_H) / 2.0f, colour);
        }

        if (awaiting != null) {
            d.textShadow(tr("controls.pressKey"),
                width / 2f - d.font.width(tr("controls.pressKey")) / 2f,
                height - 44, UiTheme.BRASS_LIGHT);
        }
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (awaiting != null) {
            awaiting = null;
            return true;
        }

        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int index = (int) ((my - listY) / ROW_H) + (int) (scroll / ROW_H);
            KeyBindings.Action[] all = KeyBindings.Action.values();
            if (index >= 0 && index < all.length) {
                int bx = listX + listW - BIND_W - 4;
                if (mx >= bx) {
                    awaiting = all[index];
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(float mx, float my, double delta) {
        int total = KeyBindings.Action.values().length * ROW_H;
        if (total <= listH) return true;
        scroll = Math.max(0, Math.min(total - listH, scroll - (float) delta * ROW_H));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (awaiting == null) {
            return super.keyPressed(key, mods);
        }

        if (key == GLFW_KEY_ESCAPE) {
            keys.reset(awaiting);
        } else {
            keys.set(awaiting, key);
        }
        awaiting = null;
        listener.onBindingsChanged();
        return true;
    }
}
