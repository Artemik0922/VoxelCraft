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

    // ---- Fade-to-black transition ----
    private static final double FADE_DURATION = 0.16;
    /** 0 = none, 1 = fading out (old → black), 2 = fading in (black → new). */
    private int fadePhase = 0;
    /** 0..1 progress within the current fade phase. */
    private double fadeProgress = 1.0;
    /** Runs at the darkest point (phase 1 end). */
    private Runnable fadeAction = null;

    /**
     * Increments whenever the visually-visible top screen may change
     * (push, or the deferred phase-1 callback running for open/pop).
     * The host uses this to re-apply cursor mode after fade transitions,
     * because {@link #current()} is not updated until the fade completes.
     */
    private int revision = 0;

    public int revision() { return revision; }

    private void startFadeOut(Runnable atBlack) {
        if (fadePhase == 1) {
            // Already darkening – complete current transition instantly
            if (fadeAction != null) { fadeAction.run(); fadeAction = null; }
            fadePhase = 0; fadeProgress = 1.0;
        }
        fadePhase = 1; fadeProgress = 0.0; fadeAction = atBlack;
    }

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

    /** Replace the whole stack with one screen (fades to black then in). */
    public void open(Screen screen) {
        startFadeOut(() -> {
            closeAll();
            if (screen != null) {
                screen.init(width, height);
                stack.push(screen);
            }
        });
    }

    /** Open on top of the current screen, keeping it for later.
     *  The new screen fades in immediately from black. */
    public void push(Screen screen) {
        if (screen == null) return;
        if (fadePhase != 0) {
            // finish any pending fade instantly
            if (fadePhase == 1 && fadeAction != null) { fadeAction.run(); fadeAction = null; }
            fadePhase = 0; fadeProgress = 1.0;
        }
        screen.init(width, height);
        stack.push(screen);
        revision++;
        // fade-in from black
        fadePhase = 2; fadeProgress = 0.0;
    }

    /** Close the topmost screen and reveal whatever was underneath.
     *  Fades the current screen to black, pops it, then fades the next
     *  screen in from black. */
    public void pop() {
        if (stack.isEmpty()) return;
        startFadeOut(() -> stack.pop().onClosed());
    }

    public void closeAll() {
        if (stack.isEmpty()) return;
        while (!stack.isEmpty()) stack.pop().onClosed();
        revision++;
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
        if (fadePhase != 0) {
            fadeProgress += deltaTime / FADE_DURATION;
            if (fadeProgress >= 1.0) {
                fadeProgress = 0.0;
                if (fadePhase == 1 && fadeAction != null) {
                    fadeAction.run();
                    fadeAction = null;
                    revision++;
                    fadePhase = 2;   // now fade in
                } else if (fadePhase == 2) {
                    fadePhase = 0;
                    fadeProgress = 1.0;
                }
            }
            // clamp to avoid overshoot
            if (fadeProgress > 1.0) fadeProgress = 1.0;
        } else {
            Screen s = current();
            if (s != null) s.update(deltaTime);
        }
    }

    public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
        Screen s = current();
        if (s != null) s.render(ui, font, tex, mx, my);

        // Fade overlay
        if (fadePhase != 0) {
            double a = (fadePhase == 1) ? fadeProgress : 1.0 - fadeProgress;
            int alpha = (int) (0xFF * Math.min(1.0, Math.max(0.0, a)));
            ui.useSolidColor();
            ui.fillRect(0, 0, width, height, alpha << 24);
        }
    }

    public boolean mouseClicked(float mx, float my, int button) {
        if (fadePhase != 0) return true;
        Screen s = current();
        return s != null && s.mouseClicked(mx, my, button);
    }

    public void mouseReleased(float mx, float my, int button) {
        if (fadePhase != 0) return;
        Screen s = current();
        if (s != null) s.mouseReleased(mx, my, button);
    }

    public boolean mouseDragged(float mx, float my, int button) {
        if (fadePhase != 0) return true;
        Screen s = current();
        return s != null && s.mouseDragged(mx, my, button);
    }

    public boolean mouseScrolled(float mx, float my, double delta) {
        if (fadePhase != 0) return true;
        Screen s = current();
        return s != null && s.mouseScrolled(mx, my, delta);
    }

    /** @return true when the key was consumed by the interface */
    public boolean keyPressed(int key, int mods) {
        if (fadePhase != 0) return true;
        Screen s = current();
        if (s == null) return false;
        return s.keyPressed(key, mods);
    }
}
