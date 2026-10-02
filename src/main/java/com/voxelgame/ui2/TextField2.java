package com.voxelgame.ui2;

import com.voxelgame.ui.FontRenderer;

/**
 * Single-line text field on a paper strip with an ink caret. Click to
 * focus; the scene routes charTyped/key events to the focused element.
 * Supports backspace/delete, arrows, home/end; vertical size 20.
 */
public class TextField2 extends Element {

    public static final int MAX_LENGTH = 64;

    private final StringBuilder buffer = new StringBuilder();
    private String placeholder = "";
    private int cursor;
    private boolean focused;
    private int scroll = 0;

    public TextField2(int width) {
        this.width = width;
        this.height = 20;
        this.focusable = true;
    }

    public TextField2 placeholder(String p) {
        this.placeholder = p;
        return this;
    }

    public String getText() {
        return buffer.toString();
    }

    public void setText(String s) {
        buffer.setLength(0);
        buffer.append(s != null ? s : "");
        cursor = buffer.length();
        scroll = 0;
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (!interactive() || button != 0 || !contains(mx, my)) return false;
        return true; // consumed; the scene focuses this element via hit test
    }

    @Override
    public boolean charTyped(char c) {
        if (!focused || !interactive()) return false;
        if (c == '\n' || c == '\r' || c == '\t') return false;
        if (buffer.length() < MAX_LENGTH) {
            buffer.insert(cursor++, c);
            clampScroll();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (!focused || !interactive()) return false;
        switch (key) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0) {
                    buffer.deleteCharAt(cursor - 1);
                    cursor--;
                    clampScroll();
                }
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE -> {
                if (cursor < buffer.length()) buffer.deleteCharAt(cursor);
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> {
                if (cursor > 0) cursor--;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> {
                if (cursor < buffer.length()) cursor++;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> {
                cursor = buffer.length();
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE -> {
                return true; // let the scene close instead of falling through
            }
            default -> {
                return false;
            }
        }
    }

    private void clampScroll() {
        String text = buffer.toString();
        int inner = width - 10;
        while (cursor < scroll) scroll--;
        while (cursor > scroll
            && UiDraw.fontWidth(text.substring(0, cursor)) - scrollPx() > inner) {
            scroll++;
        }
    }

    private int scrollPx() {
        return scroll == 0 ? 0 : UiDraw.fontWidth(buffer.substring(0, scroll));
    }

    @Override
    protected void draw(UiDraw d) {
        // Paper strip with an ink frame; brass frame while focused
        d.paper(x, y, width, height);
        if (focused) {
            d.ui.drawRectOutline(x + d.offX - 1, y + d.offY - 1, width + 2, height + 2,
                d.aAlpha(UiTheme.fade(UiTheme.BRASS, 0.75f)));
        }

        int pad = 5;
        int textY = y + (height - FontRenderer.GLYPH_H) / 2;
        String text = buffer.toString();
        int inner = width - pad * 2;

        String visible = text;
        int visibleStart = 0;
        if (d.font.width(text) > inner) {
            visibleStart = scroll;
            visible = text.substring(Math.min(scroll, text.length()));
            while (!visible.isEmpty() && d.font.width(visible) > inner) {
                visible = visible.substring(0, visible.length() - 1);
            }
        }

        if (text.isEmpty() && !focused) {
            d.text(placeholder, x + pad, textY, UiTheme.fade(UiTheme.INK_SOFT, 0.6f));
        } else {
            d.text(visible, x + pad, textY, UiTheme.INK);
        }

        // Blinking ink caret
        if (focused && (d.time * 2 % 2) < 1.4) {
            String beforeText = text.substring(0, Math.min(cursor, text.length()));
            int cx = x + pad - scrollPx() + d.font.width(beforeText);
            if (cx > x + pad && cx < x + width - pad + 1) {
                d.fill(cx, textY - 1, 1, FontRenderer.GLYPH_H + 1, UiTheme.INK);
            }
        }
    }
}
