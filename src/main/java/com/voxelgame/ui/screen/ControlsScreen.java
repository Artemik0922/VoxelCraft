package com.voxelgame.ui.screen;

import com.voxelgame.core.KeyBindings;
import com.voxelgame.core.Settings;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;
import com.voxelgame.ui.widget.Slider;
import com.voxelgame.ui.widget.Toggle;

import static com.voxelgame.core.Language.tr;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * Controls: mouse sensitivity and the rebindable key list.
 */
public class ControlsScreen extends Screen {

    public interface Listener {
        void onSensitivityChanged(float value);
        void onInvertYChanged(boolean on);
        void onBindingsChanged();
        void onClosed();
    }

    private static final int ROW_H = 22;

    private final Settings settings;
    private final KeyBindings keys;
    private final Listener listener;
    private final boolean overlayWorld;

    private KeyBindings.Action awaiting = null;

    private int listX, listY, listW, listH;
    private float scroll = 0;

    public ControlsScreen(Settings settings, KeyBindings keys,
                          Listener listener, boolean overlayWorld) {
        this.settings = settings;
        this.keys = keys;
        this.listener = listener;
        this.overlayWorld = overlayWorld;
    }

    @Override
    public boolean rendersWorld() { return overlayWorld; }

    @Override
    public boolean closableWithEscape() { return awaiting == null; }

    @Override
    protected void layout() {
        int cardW = Math.min(340, width - 20);
        int cardX = (width - cardW) / 2;
        int pad = 14;
        int innerW = cardW - pad * 2;
        int innerX = cardX + pad;

        add(new Slider(innerX, 44, innerW, ROW_H, tr("options.sensitivity"),
            Settings.MIN_SENSITIVITY, Settings.MAX_SENSITIVITY,
            settings.mouseSensitivity, false,
            (s, v) -> listener.onSensitivityChanged((float) v)));

        add(new Toggle(innerX, 44 + ROW_H + 6, innerW, ROW_H, tr("options.invertY"),
            settings.invertY, (t, v) -> listener.onInvertYChanged(v)));

        listX = innerX;
        listY = 44 + ROW_H + 6 + ROW_H + 8;
        listW = innerW;
        listH = Math.max(ROW_H * 3, height - listY - 60);

        int bw = (innerW - 8) / 2;
        add(new Button(innerX, height - 30, bw, ROW_H, tr("controls.resetAll"),
            b -> {
                keys.resetAll();
                listener.onBindingsChanged();
            }));
        add(new Button(innerX + bw + 8, height - 30, bw, ROW_H, tr("options.done"),
            b -> listener.onClosed()));
    }

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets tex) {
        if (overlayWorld) {
            MenuTheme.drawWorldOverlay(ui, width, height);
        } else {
            MenuTheme.drawBackdrop(ui, width, height, 0);
        }
        // Draw card BEFORE widgets so it doesn't darken them with its
        // semi-transparent fill (was previously drawn in renderForeground).
        int cardW = Math.min(340, width - 20);
        int cardX = (width - cardW) / 2;
        int cardTop = 30;
        int cardH = Math.max(200, height - cardTop - 40);
        MenuTheme.drawCard(ui, 0xFF000000, cardX, cardTop, cardW, cardH);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {
        int cardTop = 30;

        String title = tr("options.controls.title");
        int tw = font.scaledWidth(title, 2);
        font.drawScaledWithShadow(ui, title, (width - tw) / 2f,
            cardTop + 8, 2, 0xFFFFFFFF);
        MenuTheme.drawSeparator(ui, 0xFF000000, width / 2f, cardTop + 30, tw / 2f + 10);

        // List area
        ui.fillRect(listX, listY, listW, listH,
            MenuTheme.col(0xFF000000, 200, MenuTheme.PANEL_BG));
        int bdr = MenuTheme.col(0xFF000000, 255, MenuTheme.PANEL_BORDER);
        ui.fillRect(listX, listY, listW, 1, bdr);
        ui.fillRect(listX, listY + listH - 1, listW, 1, bdr);
        ui.fillRect(listX, listY, 1, listH, bdr);
        ui.fillRect(listX + listW - 1, listY, 1, listH, bdr);

        KeyBindings.Action[] all = KeyBindings.Action.values();
        int visible = listH / ROW_H;
        int first = (int) (scroll / ROW_H);

        for (int i = 0; i < visible; i++) {
            int index = first + i;
            if (index >= all.length) break;

            KeyBindings.Action action = all[index];
            int y = listY + i * ROW_H;

            // Row highlight
            if (index % 2 == 0) {
                ui.fillRect(listX + 1, y, listW - 2, ROW_H,
                    MenuTheme.col(0xFF000000, 30, 0xFFFFFF));
            }

            font.drawWithShadow(ui, tr(action.translationKey),
                listX + 4, y + (ROW_H - FontRenderer.GLYPH_H) / 2,
                0xFF000000 | MenuTheme.TEXT_LABEL);

            int bw = 90;
            int bx = listX + listW - bw - 4;
            boolean hover = mx >= bx && mx < bx + bw && my >= y + 1 && my < y + ROW_H - 1;
            boolean capturing = awaiting == action;

            MenuTheme.drawPanel(ui, 0xFF000000, bx, y + 1, bw, ROW_H - 2,
                capturing || hover);

            String label = capturing
                ? "> " + tr("controls.pressKey") + " <"
                : KeyBindings.keyName(keys.get(action));

            int colour;
            if (capturing) {
                colour = 0xFF000000 | MenuTheme.SUNSET_HI;
            } else if (keys.conflictFor(action, keys.get(action)) != null) {
                colour = 0xFF000000 | MenuTheme.TEXT_WARNING;
            } else {
                colour = 0xFF000000 | MenuTheme.TEXT_LABEL;
            }

            font.drawCenteredWithShadow(ui, font.trimToWidth(label, bw - 6),
                bx + bw / 2.0f, y + 1 + (ROW_H - 2 - FontRenderer.GLYPH_H) / 2.0f, colour);
        }

        if (awaiting != null) {
            font.drawCenteredWithShadow(ui, tr("controls.pressKey"),
                width / 2.0f, height - 44, 0xFF000000 | MenuTheme.SUNSET_HI);
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
                int bw = 90;
                int bx = listX + listW - bw - 4;
                if (mx >= bx) {
                    awaiting = all[index];
                    return true;
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
        if (awaiting == null) return false;

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
