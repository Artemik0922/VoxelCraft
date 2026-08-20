package com.voxelgame.ui.screen;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.GuiAssets;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Screen stack.
 *
 * Pushing keeps the previous screen underneath so Escape (or a Done button)
 * can return to it - Options opens over both the main menu and the pause
 * menu without either knowing about the other.
 *
 * An empty stack means the player is in-game.
 */
public class ScreenManager {

    private final Deque<Screen> stack = new ArrayDeque<>();
    private int width = 1;
    private int height = 1;

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        for (Screen s : stack) s.resize(width, height);
    }

    /**
     * Re-run layout on every screen. Widgets capture their labels when they
     * are built, so a language change has to rebuild them all.
     */
    public void rebuildAll() {
        for (Screen s : stack) s.resize(width, height);
    }

    /** Replace the whole stack with one screen. */
    public void open(Screen screen) {
        closeAll();
        if (screen != null) {
            screen.init(width, height);
            stack.push(screen);
        }
    }

    /** Open on top of the current screen, keeping it for later. */
    public void push(Screen screen) {
        if (screen == null) return;
        screen.init(width, height);
        stack.push(screen);
    }

    /** Close the topmost screen and reveal whatever was underneath. */
    public void pop() {
        if (stack.isEmpty()) return;
        stack.pop().onClosed();
    }

    public void closeAll() {
        while (!stack.isEmpty()) stack.pop().onClosed();
    }

    public Screen current() { return stack.peek(); }

    public boolean isOpen() { return !stack.isEmpty(); }

    public boolean shouldPauseGame() {
        Screen s = current();
        return s != null && s.pausesGame();
    }

    public boolean shouldShowCursor() {
        Screen s = current();
        return s != null && s.showsCursor();
    }

    public boolean shouldRenderWorld() {
        Screen s = current();
        return s == null || s.rendersWorld();
    }

    // ------------------------------------------------------------------

    public void update(double deltaTime) {
        Screen s = current();
        if (s != null) s.update(deltaTime);
    }

    public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
        Screen s = current();
        if (s != null) s.render(ui, font, tex, mx, my);
    }

    public boolean mouseClicked(float mx, float my, int button) {
        Screen s = current();
        return s != null && s.mouseClicked(mx, my, button);
    }

    public void mouseReleased(float mx, float my, int button) {
        Screen s = current();
        if (s != null) s.mouseReleased(mx, my, button);
    }

    public boolean mouseDragged(float mx, float my, int button) {
        Screen s = current();
        return s != null && s.mouseDragged(mx, my, button);
    }

    public boolean mouseScrolled(float mx, float my, double delta) {
        Screen s = current();
        return s != null && s.mouseScrolled(mx, my, delta);
    }

    /** @return true when the key was consumed by the interface */
    public boolean keyPressed(int key, int mods) {
        Screen s = current();
        if (s == null) return false;
        return s.keyPressed(key, mods);
    }
}
