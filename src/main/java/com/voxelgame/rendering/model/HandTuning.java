package com.voxelgame.rendering.model;

import com.voxelgame.core.Settings;

/**
 * Live-adjustable placement for the first-person view model.
 *
 * Positioning a view model is a judgement call that cannot be made from
 * code alone, so every value is editable in game (F6) and persisted to
 * options.properties. Once a good pose is found the numbers can simply be
 * promoted to the defaults below.
 */
public class HandTuning {

    // Held item
    // Solved numerically so the item covers 17% of screen height with its
    // top edge below the centre line. At a 70 degree field of view the
    // spec's z=-0.95 put the cube at 57% of the screen.
    public float itemX = 0.58f;
    public float itemY = -0.48f;
    public float itemZ = -1.45f;
    public float itemRotY = -25.0f;
    public float itemRotX = 30.0f;
    public float itemScale = 0.40f;

    // Arm. Raised (armY -0.72 -> -0.52) so the hand clears the bottom
    // edge of a 16:9 screen at the view model's 70 degree projection.
    public float armX = 0.68f;
    public float armY = -0.52f;
    public float armZ = -1.05f;
    public float armRotZ = -35.0f;
    public float armRotX = 15.0f;

    /** Which set of values the arrow keys currently drive. */
    public boolean editingArm = false;

    public void load(Settings s) {
        itemX = s.handItemX;
        itemY = s.handItemY;
        itemZ = s.handItemZ;
        itemRotY = s.handItemRotY;
        itemRotX = s.handItemRotX;
        itemScale = s.handItemScale;

        armX = s.handArmX;
        armY = s.handArmY;
        armZ = s.handArmZ;
        armRotZ = s.handArmRotZ;
        armRotX = s.handArmRotX;
    }

    public void save(Settings s) {
        s.handItemX = itemX;
        s.handItemY = itemY;
        s.handItemZ = itemZ;
        s.handItemRotY = itemRotY;
        s.handItemRotX = itemRotX;
        s.handItemScale = itemScale;

        s.handArmX = armX;
        s.handArmY = armY;
        s.handArmZ = armZ;
        s.handArmRotZ = armRotZ;
        s.handArmRotX = armRotX;

        s.save();
    }

    // ------------------------------------------------------------------
    // Adjustment, driven by the key handler
    // ------------------------------------------------------------------

    public void nudgeX(float d) { if (editingArm) armX += d; else itemX += d; }
    public void nudgeY(float d) { if (editingArm) armY += d; else itemY += d; }
    public void nudgeZ(float d) { if (editingArm) armZ += d; else itemZ += d; }

    public void nudgeRotA(float d) {
        if (editingArm) armRotZ += d; else itemRotY += d;
    }

    public void nudgeRotB(float d) {
        if (editingArm) armRotX += d; else itemRotX += d;
    }

    public void nudgeScale(float d) {
        if (editingArm) return;                 // the arm is fixed size
        itemScale = Math.max(0.05f, Math.min(2.0f, itemScale + d));
    }

    public void toggleTarget() { editingArm = !editingArm; }

    /** Lines shown on the HUD while tuning. */
    public String[] describe() {
        return new String[]{
            "HAND TUNING  (F6 to exit, TAB switches target)",
            "editing: " + (editingArm ? "ARM" : "ITEM"),
            String.format(java.util.Locale.ROOT,
                "item  pos %.2f %.2f %.2f  rotY %.0f rotX %.0f scale %.2f",
                itemX, itemY, itemZ, itemRotY, itemRotX, itemScale),
            String.format(java.util.Locale.ROOT,
                "arm   pos %.2f %.2f %.2f  rotZ %.0f rotX %.0f",
                armX, armY, armZ, armRotZ, armRotX),
            "arrows = X/Y   PgUp/PgDn = Z   +/- = scale   R/F = rotA   T/G = rotB"
        };
    }

    /** Copy-pasteable defaults, printed on exit. */
    public String toCode() {
        return String.format(java.util.Locale.ROOT,
            "item(%.2ff, %.2ff, %.2ff) rotY %.0f rotX %.0f scale %.2ff | "
            + "arm(%.2ff, %.2ff, %.2ff) rotZ %.0f rotX %.0f",
            itemX, itemY, itemZ, itemRotY, itemRotX, itemScale,
            armX, armY, armZ, armRotZ, armRotX);
    }
}
