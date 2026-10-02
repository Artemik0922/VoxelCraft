package com.voxelgame.ui2;

import java.util.ArrayList;
import java.util.List;

/**
 * Base of the new interface tree. A scene builds a tree of elements in
 * {@code build()}; the framework then ticks animations, routes input and
 * renders the tree every frame.
 *
 * Coordinates are virtual GUI pixels. Containers (Column/Row/Stack)
 * position their children in {@link #arrangeChildren()}; leaves keep the
 * position they were given.
 *
 * Animation: every element eases {@code hover01}/{@code press01} towards
 * its pressed/hovered state and plays an entrance slide driven by
 * {@code appearDelay} + the scene clock. Alpha multiplies down the tree.
 */
public abstract class Element {

    public int x, y, width, height;
    public boolean visible = true;
    public boolean enabled = true;
    /** Elements the keyboard navigation can reach (text fields, buttons). */
    public boolean focusable = false;
    /** Set by the scene when keyboard navigation selects this element. */
    public boolean focused;
    /** Tree-level opacity multiplier (1 = opaque), applied in render(). */
    public float alpha = 1f;

    public Element parent;
    protected final List<Element> children = new ArrayList<>();

    // Animation state
    public float hover01;
    public float press01;
    /** Seconds to wait (from scene open) before the entrance slide starts. */
    public float appearDelay;
    public float appear01 = 1f;
    /** Extra horizontal entrance offset magnitude, GUI pixels. */
    public float appearSlide = 10f;

    /** Convenience hook: when set, a plain click on this element fires it. */
    private Runnable onPress;

    public <T extends Element> T add(T child) {
        child.parent = this;
        children.add(child);
        return child;
    }

    public void remove(Element child) {
        children.remove(child);
        child.parent = null;
    }

    public void clearChildren() {
        for (Element c : children) c.parent = null;
        children.clear();
    }

    public List<Element> children() {
        return children;
    }

    public Element onClick(Runnable action) {
        this.onPress = action;
        this.focusable = true;
        return this;
    }

    // ------------------------------------------------------------------
    // Tree passes
    // ------------------------------------------------------------------

    /** Positions children; containers override. Default: no-op. */
    public void arrangeChildren() {
    }

    /** Recursive arrange: containers first position their own children. */
    public final void arrangeTree() {
        arrangeChildren();
        for (Element c : children) c.arrangeTree();
    }

    /** Per-frame animation update. */
    public void tick(double dt, double sceneTime) {
        float t = (float) (sceneTime - appearDelay);
        appear01 = UiTheme.easeOutCubic(t / 0.45f);
        hover01 += (hoverTarget - hover01) * (float) Math.min(1.0, dt * 14);
        press01 += (pressTarget - press01) * (float) Math.min(1.0, dt * 20);
        for (Element c : children) c.tick(dt, sceneTime);
    }

    /** Hover/press targets are set by the input pass, eased in tick(). */
    protected float hoverTarget;
    protected float pressTarget;

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public final void render(UiDraw d) {
        if (!visible) return;
        float savedAlpha = d.alpha;
        float savedOffX = d.offX, savedOffY = d.offY;
        d.alpha *= alpha * appear01;
        float slide = (1f - appear01) * appearSlide;
        d.offX += slide;
        d.offY += (1f - appear01) * 6f;
        if (d.alpha > 0.01f) {
            draw(d);
            for (Element c : children) c.render(d);
        }
        d.alpha = savedAlpha;
        d.offX = savedOffX;
        d.offY = savedOffY;
    }

    protected abstract void draw(UiDraw d);

    // ------------------------------------------------------------------
    // Input (return true = consumed). Children get the chance first,
    // iterating from the last added (topmost) backwards.
    // ------------------------------------------------------------------

    public boolean contains(float mx, float my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    protected boolean interactive() {
        return visible && enabled;
    }

    public boolean mouseClicked(float mx, float my, int button) {
        if (!visible) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseClicked(mx, my, button)) return true;
        }
        if (!interactive() || !contains(mx, my)) return false;
        if (onPress != null) {
            pressTarget = 1f;
            onPress.run();
        }
        return onPress != null || focusable;
    }

    public void mouseReleased(float mx, float my, int button) {
        pressTarget = 0f;
        for (Element c : children) c.mouseReleased(mx, my, button);
    }

    public boolean mouseDragged(float mx, float my, int button) {
        if (!visible) return false;
        for (Element c : children) {
            if (c.mouseDragged(mx, my, button)) return true;
        }
        return false;
    }

    public boolean mouseScrolled(float mx, float my, double delta) {
        if (!visible) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseScrolled(mx, my, delta)) return true;
        }
        return false;
    }

    public boolean keyPressed(int key, int mods) {
        if (!visible) return false;
        for (Element c : children) {
            if (c.keyPressed(key, mods)) return true;
        }
        return false;
    }

    public boolean charTyped(char c) {
        if (!visible) return false;
        for (Element child : children) {
            if (child.charTyped(c)) return true;
        }
        return false;
    }

    /** Updates hover targets from the cursor (called every render pass). */
    public void updateHover(float mx, float my) {
        hoverTarget = interactive() && contains(mx, my) ? 1f : 0f;
        for (Element c : children) c.updateHover(mx, my);
    }

    /** Depth-first collect of focusable elements, in build order. */
    public void collectFocusables(List<Element> out) {
        if (!visible) return;
        if (focusable && enabled) out.add(this);
        for (Element c : children) c.collectFocusables(out);
    }
}
