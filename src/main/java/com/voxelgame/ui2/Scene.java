package com.voxelgame.ui2;

import java.util.ArrayList;
import java.util.List;

/**
 * A full-screen interface state, built as a tree of {@link Element}s.
 *
 * Subclasses construct their tree in {@link #build()} (re-run on resize and
 * language change) and draw optional pre-tree backdrops in
 * {@link #renderBackground(UiDraw)}. The scene owns an animation clock that
 * starts at zero every time the scene opens, driving entrance animations.
 *
 * The flag contract mirrors the legacy screens so the host game can treat
 * both kinds uniformly: pause, cursor, world rendering, frosted backdrop,
 * Escape handling.
 */
public abstract class Scene {

    protected int width, height;
    /** Seconds since the scene was (re)built; drives entrance animations. */
    protected double sceneTime;

    protected Element root;
    /** The element that receives keyboard/typed input, or null. */
    protected Element focused;

    public boolean pausesGame() { return true; }

    public boolean showsCursor() { return true; }

    public boolean rendersWorld() { return true; }

    public boolean usesBlurredBackdrop() { return rendersWorld(); }

    public boolean closableWithEscape() { return true; }

    /** The inventory key closes container screens (mirrors how they opened). */
    public boolean closesWithInventoryKey() { return false; }

    /** Popping this scene when the stack empties returns to the title. */
    public boolean fallsBackToMainMenu() { return false; }

    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        this.sceneTime = 0;
        this.focused = null;
        root = new Layouts.Stack();
        build();
        root.x = 0;
        root.y = 0;
        root.width = width;
        root.height = height;
        root.arrangeTree();
    }

    public void resize(int width, int height) {
        init(width, height);
    }

    /** Construct the element tree. */
    protected abstract void build();

    /** Adds a top-level element to the scene root. */
    protected <T extends Element> T add(T element) {
        root.add(element);
        return element;
    }

    public void update(double dt) {
        sceneTime += dt;
        if (root != null) root.tick(dt, sceneTime);
    }

    /** Extra drawing under the element tree (backdrops, veils, scrims). */
    protected void renderBackground(UiDraw d) {
    }

    /** Extra drawing over the element tree. */
    protected void renderForeground(UiDraw d) {
    }

    public void render(UiDraw d) {
        renderBackground(d);
        if (root != null) {
            root.updateHover(d.mx, d.my);
            root.render(d);
        }
        renderForeground(d);
    }

    public void onClosed() {
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    public boolean mouseClicked(float mx, float my, int button) {
        if (root == null) return false;
        // Clicking a focusable element moves keyboard focus to it
        List<Element> focusables = new ArrayList<>();
        root.collectFocusables(focusables);
        for (Element f : focusables) {
            if (f.contains(mx, my)) {
                setFocused(f);
                break;
            }
        }
        return root.mouseClicked(mx, my, button);
    }

    public void mouseReleased(float mx, float my, int button) {
        if (root != null) root.mouseReleased(mx, my, button);
    }

    public boolean mouseDragged(float mx, float my, int button) {
        return root != null && root.mouseDragged(mx, my, button);
    }

    public boolean mouseScrolled(float mx, float my, double delta) {
        return root != null && root.mouseScrolled(mx, my, delta);
    }

    public boolean keyPressed(int key, int mods) {
        if (root == null) return false;
        // Tab cycles keyboard focus
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            cycleFocus();
            return true;
        }
        if (focused != null && focused.keyPressed(key, mods)) return true;
        return root.keyPressed(key, mods);
    }

    public boolean charTyped(char c) {
        if (root == null) return false;
        if (focused != null && focused.charTyped(c)) return true;
        return root.charTyped(c);
    }

    public void setFocused(Element e) {
        focused = e;
    }

    private void cycleFocus() {
        List<Element> focusables = new ArrayList<>();
        root.collectFocusables(focusables);
        if (focusables.isEmpty()) return;
        int index = focused == null ? -1 : focusables.indexOf(focused);
        setFocused(focusables.get(Math.floorMod(index + 1, focusables.size())));
    }
}
