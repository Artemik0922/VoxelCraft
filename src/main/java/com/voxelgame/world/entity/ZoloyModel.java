package com.voxelgame.world.entity;

import com.voxelgame.rendering.Shader;
import com.voxelgame.rendering.model.ModelBox;
import org.joml.Matrix4f;

/**
 * Zoloy — vanilla humanoid model (ModelBiped).
 *
 * Model space: Y DOWN, origin = neck. World space: Y UP.
 * Base matrix includes Y-flip to convert between spaces.
 * Pivots per part (vanilla style). Culling disabled (spec).
 */
public class ZoloyModel {

    private final ModelBox head;
    private final ModelBox body;
    private final ModelBox rarm;
    private final ModelBox larm;
    private final ModelBox rleg;
    private final ModelBox lleg;
    private final ModelBox cloak;

    // Pivots in model space (Y down) — spec values
    private static final float[] P_HEAD = {0, 0, 0};
    private static final float[] P_BODY = {0, 0, 0};
    private static final float[] P_RARM = {-5, 2, 0};
    private static final float[] P_LARM = {5, 2, 0};
    private static final float[] P_RLEG = {-1.9f, 12, 0};
    private static final float[] P_LLEG = {1.9f, 12, 0};
    private static final float[] P_CLOAK = {0, 2, 0};

    // Base posture
    private static final float BASE_BODY = 0.35f;
    private static final float BASE_RARM = 0.25f;
    private static final float BASE_LARM = 0.25f;
    private static final float BASE_HEAD = -0.15f;

    private final Matrix4f scratch = new Matrix4f();

    public ZoloyModel() {
        // Boxes: ModelBox(u, v, w, h, d, minX, minY, minZ, inflate, flipV)
        // Model space is Y down (crown at minY), so flipV=true keeps the
        // sheet upright; otherwise faces render upside down.
        head  = new ModelBox(0, 0, 8, 8, 8, -4, -8, -4, 0, true);
        body  = new ModelBox(16, 16, 8, 12, 4, -4, 0, -2, 0, true);
        rarm  = new ModelBox(40, 16, 4, 12, 4, -7, 0, -2, 0, true);
        larm  = new ModelBox(32, 48, 4, 12, 4, 3, 0, -2, 0, true);
        rleg  = new ModelBox(0, 16, 4, 12, 4, -5.9f, 12, -2, 0, true);
        lleg  = new ModelBox(16, 48, 4, 12, 4, 1.9f, 12, -2, 0, true);
        cloak = new ModelBox(16, 28, 8, 12, 4, -4.5f, -0.5f, -2.5f, 0.5f, true);
    }

    /**
     * Render part with pivot rotation.
     * pivot in skin pixels; geometry is in block units (1px = 1/16 block),
     * so the pivot is scaled by 1/16 before use. Without this the limbs
     * rotated about a point 16x too far away and tore off the body.
     */
    private void renderPart(ModelBox part, float[] pivot, float rotX,
                           Matrix4f base, Matrix4f viewProj, Shader shader) {
        // Pivot transform: T(pivot) * Rx(rotX) * T(-pivot)
        final float P = 1.0f / 16.0f;
        scratch.identity();
        scratch.translate(pivot[0] * P, pivot[1] * P, pivot[2] * P);
        scratch.rotateX(-rotX); // negate due to Y-flip
        scratch.translate(-pivot[0] * P, -pivot[1] * P, -pivot[2] * P);

        Matrix4f mvp = viewProj.mul(base, new Matrix4f()).mul(scratch);
        shader.setUniformMat4("mvp", mvp);
        part.render();
    }

    public void render(Matrix4f base, Matrix4f viewProj, Shader shader,
                       float limbSwing, float limbSwingAmount,
                       float attackPhase, boolean debugTpose) {

        float a = limbSwingAmount;

        // Vanilla formulas
        float rLegRot = (float) Math.cos(limbSwing * 0.6662f) * 1.4f * a;
        float lLegRot = (float) Math.cos(limbSwing * 0.6662f + Math.PI) * 1.4f * a;
        float rArmRot = (float) Math.cos(limbSwing * 0.6662f + Math.PI) * 1.0f * a;
        float lArmRot = (float) Math.cos(limbSwing * 0.6662f) * 1.0f * a;

        if (attackPhase > 0f) {
            rArmRot = -2.2f + 2.8f * (float) Math.sin(attackPhase * Math.PI);
        }

        if (debugTpose) {
            rLegRot = lLegRot = rArmRot = lArmRot = 0;
        }

        renderPart(rleg, P_RLEG, rLegRot, base, viewProj, shader);
        renderPart(lleg, P_LLEG, lLegRot, base, viewProj, shader);
        renderPart(body, P_BODY, debugTpose ? 0 : BASE_BODY, base, viewProj, shader);
        renderPart(rarm, P_RARM, (debugTpose ? 0 : BASE_RARM) + rArmRot, base, viewProj, shader);
        renderPart(larm, P_LARM, (debugTpose ? 0 : BASE_LARM) + lArmRot, base, viewProj, shader);
        renderPart(head, P_HEAD, debugTpose ? 0 : BASE_HEAD, base, viewProj, shader);
        renderPart(cloak, P_CLOAK, debugTpose ? 0 : BASE_BODY * 0.5f, base, viewProj, shader);
    }

    public void cleanup() {
        head.cleanup();
        body.cleanup();
        rarm.cleanup();
        larm.cleanup();
        rleg.cleanup();
        lleg.cleanup();
        cloak.cleanup();
    }
}
