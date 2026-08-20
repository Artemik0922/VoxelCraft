package com.voxelgame.ui.toast;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.UIRenderer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Manages a queue of biome discovery toasts.
 * Shows one at a time, advancing when the current one finishes.
 */
public final class ToastManager {

    private final Deque<Toast> queue = new ArrayDeque<>();
    private Toast current = null;

    /** Add a new toast to the queue. */
    public void enqueue(Toast toast) {
        queue.add(toast);
    }

    /** Update current toast and advance queue. */
    public void update(double dt) {
        if (current != null) {
            if (!current.update(dt)) {
                current = null;
            }
        }
        if (current == null && !queue.isEmpty()) {
            current = queue.poll();
        }
    }

    /** Draw the current toast. */
    public void render(UIRenderer ui, FontRenderer font, GuiAssets gui, int screenW) {
        if (current != null) {
            current.render(ui, font, gui, screenW);
        }
    }
}
