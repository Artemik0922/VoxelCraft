package com.voxelgame.ui2;

import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.screen.ChestScreen;
import com.voxelgame.ui.screen.CraftingScreen;
import com.voxelgame.ui.screen.CreativeInventoryScreen;
import com.voxelgame.ui.screen.FurnaceScreen;
import com.voxelgame.ui.screen.Screen;
import com.voxelgame.ui.screen.SurvivalInventoryScreen;

/**
 * Adapter that runs the remaining legacy container screens inside the new
 * scene stack. Their visuals already follow the Workshop theme; they will
 * migrate to real ui2 scenes in the final cleanup phase.
 */
public final class LegacyScene extends Scene {

    private final Screen screen;

    public LegacyScene(Screen screen) {
        this.screen = screen;
    }

    public Screen wrapped() {
        return screen;
    }

    @Override public boolean pausesGame() { return screen.pausesGame(); }
    @Override public boolean showsCursor() { return screen.showsCursor(); }
    @Override public boolean rendersWorld() { return screen.rendersWorld(); }
    @Override public boolean usesBlurredBackdrop() { return screen.usesBlurredBackdrop(); }
    @Override public boolean closableWithEscape() { return screen.closableWithEscape(); }

    @Override
    public boolean closesWithInventoryKey() {
        return screen instanceof CreativeInventoryScreen
            || screen instanceof SurvivalInventoryScreen
            || screen instanceof CraftingScreen
            || screen instanceof ChestScreen
            || screen instanceof FurnaceScreen;
    }

    @Override
    public void init(int width, int height) {
        super.init(width, height);
        screen.init(width, height);
    }

    @Override
    protected void build() {
        // The wrapped screen builds its own widgets
    }

    @Override
    public void update(double dt) {
        super.update(dt);
        screen.update(dt);
    }

    @Override
    public void render(UiDraw d) {
        screen.render(d.ui, d.font, GuiAssets.INSTANCE, d.mx, d.my);
    }

    @Override
    public void onClosed() {
        screen.onClosed();
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        return screen.mouseClicked(mx, my, button);
    }

    @Override
    public void mouseReleased(float mx, float my, int button) {
        screen.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(float mx, float my, int button) {
        return screen.mouseDragged(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(float mx, float my, double delta) {
        return screen.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        return screen.keyPressed(key, mods);
    }

    @Override
    public boolean charTyped(char c) {
        if (screen instanceof CreativeInventoryScreen ci) return ci.charTyped(c);
        if (screen instanceof CraftingScreen cs) return cs.charTyped(c);
        return false;
    }
}
