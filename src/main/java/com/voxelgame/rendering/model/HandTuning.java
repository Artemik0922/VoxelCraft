package com.voxelgame.rendering.model;

import com.voxelgame.core.Settings;

/**
 * Live-adjustable fine offsets for the first-person view model.
 *
 * The base pose is the vanilla Minecraft chain (HeldItemRenderer); the
 * values below are small nudges on top of it, editable in game (F6) and
 * persisted to options.properties. Defaults are zero — the vanilla pose
 * needs no correction.
 */
public class HandTuning {

    public float offsetX = 0.0f;
    public float offsetY = 0.0f;
    public float offsetZ = 0.0f;

    /** Which set of values the arrow keys currently drive. */
    public boolean editingArm = false;

    public void load(Settings s) {
        offsetX = s.handItemX;
        offsetY = s.handItemY;
        offsetZ = s.handItemZ;
    }

    public void save(Settings s) {
        s.handItemX = offsetX;
        s.handItemY = offsetY;
        s.handItemZ = offsetZ;
        s.save();
    }

    // ------------------------------------------------------------------
    // Adjustment, driven by the key handler
    // ------------------------------------------------------------------

    public void nudgeX(float d) { offsetX += d; }
    public void nudgeY(float d) { offsetY += d; }
    public void nudgeZ(float d) { offsetZ += d; }

    public void nudgeRotA(float d) { }
    public void nudgeRotB(float d) { }
    public void nudgeScale(float d) { }

    public void toggleTarget() { editingArm = !editingArm; }

    /** Lines shown on the HUD while tuning. */
    public String[] describe() {
        return new String[]{
            "HAND TUNING  (F6 to exit, TAB switches target)",
            "editing: " + (editingArm ? "ARM" : "ITEM"),
            String.format(java.util.Locale.ROOT,
                "offset %.3f %.3f %.3f (on top of the vanilla pose)",
                offsetX, offsetY, offsetZ),
            "arrows = X/Y   PgUp/PgDn = Z"
        };
    }

    /** Copy-pasteable defaults, printed on exit. */
    public String toCode() {
        return String.format(java.util.Locale.ROOT,
            "offset(%.3ff, %.3ff, %.3ff)", offsetX, offsetY, offsetZ);
    }
}
