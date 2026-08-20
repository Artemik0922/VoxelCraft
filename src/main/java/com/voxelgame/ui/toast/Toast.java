package com.voxelgame.ui.toast;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.UIRenderer;

/** Common interface for all toast notification types. */
public interface Toast {
    /** Update animation state. Returns false when the toast should be removed. */
    boolean update(double dt);

    /** Render the toast on screen. */
    void render(UIRenderer ui, FontRenderer font, GuiAssets gui, int screenW);
}
