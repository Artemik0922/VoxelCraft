package com.voxelgame.ui.screen;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.widget.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for full-screen interfaces.
 *
 * A screen owns a flat list of widgets and is rebuilt via {@link #layout}
 * whenever the virtual canvas size changes, so everything stays centred
 * across resizes and GUI scale changes.
 */
public abstract class Screen {

    protected final List<Widget> widgets = new ArrayList<>();
    protected int width;
    protected int height;

    /** Whether the world keeps ticking while this screen is open. */
    public boolean pausesGame() { return true; }

    /** Whether the mouse cursor should be visible. */
    public boolean showsCursor() { return true; }

    /** Whether the world is still drawn behind this screen. */
    public boolean rendersWorld() { return true; }

    /**
     * Whether the live scene behind this screen should be blurred into a
     * frosted-glass backdrop. Defaults to {@link #rendersWorld()}; overlay
     * screens that draw their own procedural backdrop return false and
     * inventory screens opt in explicitly.
     */
    public boolean usesBlurredBackdrop() { return rendersWorld(); }

    /** Whether Escape closes this screen. */
    public boolean closableWithEscape() { return true; }

    /**
     * Menu-flow screens (main menu, options, world screens) render in the
     * light "airy" style: white glass panels with dark text. In-game screens
     * keep the dark glass. Swaps the whole {@link com.voxelgame.ui.MenuTheme}
     * base palette for the duration of this render pass.
     */
    protected boolean lightTheme() { return false; }

    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        widgets.clear();
        layout();
    }

    public void resize(int width, int height) {
        init(width, height);
    }

    /** Create and position widgets. Called on init and on every resize. */
    protected abstract void layout();

    protected <T extends Widget> T add(T widget) {
        widgets.add(widget);
        return widget;
    }

    /** Ticks every widget (hover animations and the like). Override and
     *  call super so widget motion keeps running. */
    public void update(double deltaTime) {
        for (Widget w : widgets) w.update(deltaTime);
    }

    public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
        com.voxelgame.ui.MenuTheme.beginFrame(lightTheme());
        try {
            renderBackground(ui, font, tex);

            for (Widget w : widgets) {
                w.updateHover(mx, my);
                w.render(ui, font, tex, mx, my);
            }

            renderForeground(ui, font, tex, mx, my);
        } finally {
            com.voxelgame.ui.MenuTheme.endFrame();
        }
    }

    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets tex) {}

    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {}

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    public boolean mouseClicked(float mx, float my, int button) {
        // Iterate backwards so widgets drawn on top get the click first
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).mouseClicked(mx, my, button)) return true;
        }
        return false;
    }

    public void mouseReleased(float mx, float my, int button) {
        for (Widget w : widgets) w.mouseReleased(mx, my, button);
    }

    public boolean mouseDragged(float mx, float my, int button) {
        for (Widget w : widgets) {
            if (w.mouseDragged(mx, my, button)) return true;
        }
        return false;
    }

    public boolean mouseScrolled(float mx, float my, double delta) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).mouseScrolled(mx, my, delta)) return true;
        }
        return false;
    }

    public boolean keyPressed(int key, int mods) {
        for (Widget w : widgets) {
            if (w.keyPressed(key, mods)) return true;
        }
        return false;
    }

    public void onClosed() {}
}
