package com.voxelgame.ui.widget;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.UIRenderer;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Single-line text input, themed to match the menu panels. The field has a
 * dark inset panel with an amber focus ring when active.
 */
public class TextField extends Widget {

    private final StringBuilder text = new StringBuilder();
    private final int maxLength;

    private boolean focused = false;
    private int cursor = 0;
    private double blinkTimer = 0;

    /** Shown in grey when the field is empty. */
    private String placeholder = "";

    public TextField(int x, int y, int width, int height, int maxLength) {
        super(x, y, width, height);
        this.maxLength = maxLength;
    }

    public String getText() { return text.toString(); }

    public void setText(String s) {
        text.setLength(0);
        if (s != null) text.append(s.length() > maxLength ? s.substring(0, maxLength) : s);
        cursor = text.length();
    }

    public void setPlaceholder(String s) { this.placeholder = s; }

    public boolean isFocused() { return focused; }
    public void setFocused(boolean f) { this.focused = f; }

    public void update(double deltaTime) {
        blinkTimer += deltaTime;
    }

    @Override
    public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
        if (!visible) return;

        int fadeAlpha = (alpha >>> 24) & 0xFF;

        // Rounded glass inset field
        int field = focused ? 0xFF0A0F18 : 0xFF0C111C;
        ui.drawNineSlice(tex.glassField, x, y, width, height, 6,
            GuiAssets.GLASS_WIDGET, MenuTheme.col(fadeAlpha << 24, 255, field));

        // Focus accents: amber top line + soft echoed underline
        if (focused) {
            int ac = MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.ACCENT);
            ui.fillRect(x + 6, y + 1, Math.max(0, width - 12), 1, ac);
            ui.fillRect(x + 6, y + height - 2, Math.max(0, width - 12), 1,
                MenuTheme.col(fadeAlpha << 24, 150, MenuTheme.ACCENT));
            ui.fillRect(x + 2, y + 6, 1, Math.max(0, height - 12), ac);
            ui.fillRect(x + width - 3, y + 6, 1, Math.max(0, height - 12), ac);
        }

        String shown = text.toString();
        int textY = y + (height - FontRenderer.GLYPH_H) / 2;

        if (shown.isEmpty() && !focused) {
            font.draw(ui, font.trimToWidth(placeholder, width - 8),
                x + 4, textY, MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.TEXT_DIM));
        } else {
            // Scroll horizontally so the cursor stays in view
            String visibleText = shown;
            while (font.width(visibleText) > width - 8 && visibleText.length() > 1) {
                visibleText = visibleText.substring(1);
            }
            font.draw(ui, visibleText, x + 4, textY,
                MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.TEXT_LABEL));

            if (focused && ((int) (blinkTimer * 2) % 2 == 0)) {
                int cx = x + 4 + font.width(visibleText);
                ui.fillRect(cx, textY - 1, 1, FontRenderer.GLYPH_H + 2,
                    MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.ACCENT_LIGHT));
            }
        }
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (!visible || !enabled) return false;
        focused = contains(mx, my);
        return focused;
    }

    /** A printable character from the GLFW character callback. */
    public boolean charTyped(char c) {
        if (!focused || text.length() >= maxLength) return false;
        if (c < 32 || c == 127) return false;

        text.insert(Math.min(cursor, text.length()), c);
        cursor++;
        blinkTimer = 0;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (!focused) return false;

        switch (key) {
            case GLFW_KEY_BACKSPACE -> {
                if (text.length() > 0 && cursor > 0) {
                    text.deleteCharAt(cursor - 1);
                    cursor--;
                }
                blinkTimer = 0;
                return true;
            }
            case GLFW_KEY_DELETE -> {
                if (cursor < text.length()) text.deleteCharAt(cursor);
                return true;
            }
            case GLFW_KEY_LEFT -> {
                if (cursor > 0) cursor--;
                return true;
            }
            case GLFW_KEY_RIGHT -> {
                if (cursor < text.length()) cursor++;
                return true;
            }
            case GLFW_KEY_HOME -> { cursor = 0; return true; }
            case GLFW_KEY_END -> { cursor = text.length(); return true; }
            case GLFW_KEY_ESCAPE -> { focused = false; return true; }
            default -> { return false; }
        }
    }
}
