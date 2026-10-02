package com.voxelgame.ui2;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.UIRenderer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Scene stack for the new interface. Same transition and query contract as
 * the legacy ScreenManager: pushing keeps the previous scene underneath,
 * an empty stack means gameplay, fades swap the stack at the darkest point,
 * and {@code revision} lets the host re-apply the cursor mode after a
 * deferred swap.
 *
 * Legacy {@link Screen} instances are accepted by the open/push overloads
 * and wrapped in a {@link LegacyScene}, so screens migrate to the new
 * framework one at a time while the game stays playable.
 */
public class SceneManager2 {

    private final Deque<Scene> stack = new ArrayDeque<>();
    private int width = 1;
    private int height = 1;
    private double clock = 0;

    private static final double FADE_DURATION = 0.16;
    /** 0 = none, 1 = fading out (old → black), 2 = fading in (black → new). */
    private int fadePhase = 0;
    private double fadeProgress = 1.0;
    private Runnable fadeAction = null;
    private int revision = 0;

    private UiDraw draw;

    public int revision() { return revision; }

    private void startFadeOut(Runnable atBlack) {
        if (fadePhase == 1) {
            if (fadeAction != null) { fadeAction.run(); fadeAction = null; }
            fadePhase = 0;
            fadeProgress = 1.0;
        }
        fadePhase = 1;
        fadeProgress = 0.0;
        fadeAction = atBlack;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        for (Scene s : stack) s.resize(width, height);
    }

    /** Re-run build on every scene (language change). */
    public void rebuildAll() {
        for (Scene s : stack) s.resize(width, height);
    }

    public void open(Scene scene) {
        UiSounds.open();
        startFadeOut(() -> {
            closeAll();
            if (scene != null) {
                scene.init(width, height);
                stack.push(scene);
            }
        });
    }

    public void push(Scene scene) {
        if (scene == null) return;
        UiSounds.open();
        if (fadePhase != 0) {
            if (fadePhase == 1 && fadeAction != null) { fadeAction.run(); fadeAction = null; }
            fadePhase = 0;
            fadeProgress = 1.0;
        }
        scene.init(width, height);
        stack.push(scene);
        revision++;
        fadePhase = 2;
        fadeProgress = 0.0;
    }

    /** Legacy container screens join the stack through an adapter. */
    public void open(com.voxelgame.ui.screen.Screen screen) {
        open((Scene) new LegacyScene(screen));
    }

    public void push(com.voxelgame.ui.screen.Screen screen) {
        push((Scene) new LegacyScene(screen));
    }

    public void pop() {
        if (stack.isEmpty()) return;
        UiSounds.close();
        startFadeOut(() -> stack.pop().onClosed());
    }

    public void closeAll() {
        if (stack.isEmpty()) return;
        while (!stack.isEmpty()) stack.pop().onClosed();
        revision++;
    }

    public Scene current() { return stack.peek(); }

    public boolean isOpen() { return !stack.isEmpty(); }

    public boolean shouldPauseGame() {
        Scene s = current();
        return s != null && s.pausesGame();
    }

    public boolean shouldShowCursor() {
        Scene s = current();
        return s != null && s.showsCursor();
    }

    public boolean shouldRenderWorld() {
        Scene s = current();
        return s == null || s.rendersWorld();
    }

    // ------------------------------------------------------------------

    public void update(double deltaTime) {
        clock += deltaTime;
        if (fadePhase != 0) {
            fadeProgress += deltaTime / FADE_DURATION;
            if (fadeProgress >= 1.0) {
                fadeProgress = 0.0;
                if (fadePhase == 1 && fadeAction != null) {
                    fadeAction.run();
                    fadeAction = null;
                    revision++;
                    fadePhase = 2;
                } else if (fadePhase == 2) {
                    fadePhase = 0;
                    fadeProgress = 1.0;
                }
            }
            if (fadeProgress > 1.0) fadeProgress = 1.0;
        } else {
            Scene s = current();
            if (s != null) s.update(deltaTime);
        }
    }

    public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
        if (draw == null) {
            draw = new UiDraw(ui, font, UiMaterials.INSTANCE);
        }
        draw.mx = mx;
        draw.my = my;
        draw.time = clock;
        draw.alpha = 1f;

        Scene s = current();
        if (s != null) s.render(draw);

        if (fadePhase != 0) {
            double a = (fadePhase == 1) ? fadeProgress : 1.0 - fadeProgress;
            int alpha = (int) (0xFF * Math.min(1.0, Math.max(0.0, a)));
            ui.useSolidColor();
            ui.fillRect(0, 0, width, height, alpha << 24);
        }
    }

    public boolean mouseClicked(float mx, float my, int button) {
        if (fadePhase != 0) return true;
        Scene s = current();
        return s != null && s.mouseClicked(mx, my, button);
    }

    public void mouseReleased(float mx, float my, int button) {
        if (fadePhase != 0) return;
        Scene s = current();
        if (s != null) s.mouseReleased(mx, my, button);
    }

    public boolean mouseDragged(float mx, float my, int button) {
        if (fadePhase != 0) return true;
        Scene s = current();
        return s != null && s.mouseDragged(mx, my, button);
    }

    public boolean mouseScrolled(float mx, float my, double delta) {
        if (fadePhase != 0) return true;
        Scene s = current();
        return s != null && s.mouseScrolled(mx, my, delta);
    }

    public boolean keyPressed(int key, int mods) {
        if (fadePhase != 0) return true;
        Scene s = current();
        if (s == null) return false;
        return s.keyPressed(key, mods);
    }

    /** Typed text goes to the focused element of the top scene. */
    public boolean charTyped(char c) {
        if (fadePhase != 0) return false;
        Scene s = current();
        if (s == null) return false;
        return s.charTyped(c);
    }
}
