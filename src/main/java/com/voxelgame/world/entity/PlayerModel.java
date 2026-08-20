package com.voxelgame.world.entity;

import com.voxelgame.rendering.Shader;
import com.voxelgame.rendering.model.ModelBox;
import org.joml.Matrix4f;

/**
 * Полноценный хуманоид игрока (уровень жителя деревни).
 *
 * В отличие от базового ZoloyModel:
 * - поворот головы по pitch + покачивание при ходьбе (bob)
 * - наклон тела вперёд при ходьбе
 * - умеренная амплитуда маха конечностей (руки не выкручиваются)
 * - поза крадущегося (приседание)
 * - замах правой рукой при атаке
 * - расчёт матрицы кисти для предмета в руке
 *
 * Модель в координатах Y-down, origin = шея (как у жителя).
 * UV-раскладка — классическая 64x64 скин-схема игрока (SkinTexture).
 */
public class PlayerModel {

    private final ModelBox head;
    private final ModelBox body;
    private final ModelBox rarm;
    private final ModelBox larm;
    private final ModelBox rleg;
    private final ModelBox lleg;

    private static final float[] P_HEAD = {0, 0, 0};
    private static final float[] P_BODY = {0, 0, 0};
    private static final float[] P_RARM = {-5, 2, 0};
    private static final float[] P_LARM = {5, 2, 0};
    private static final float[] P_RLEG = {-1.9f, 12, 0};
    private static final float[] P_LLEG = {1.9f, 12, 0};

    // Базовые позы
    private static final float BASE_BODY_TILT = 0.05f;
    private static final float BASE_HEAD_TILT = -0.1f;
    private static final float BASE_ARM = 0.05f;

    private final Matrix4f scratch = new Matrix4f();
    private final Matrix4f handScratch = new Matrix4f();

    public PlayerModel() {
        // Классическая раскладка скина: голова (0,0), тело (16,16),
        // рука правая (40,16), левая (32,48), ноги (0,16)/(16,48).
        head  = new ModelBox(0, 0, 8, 8, 8, -4, -8, -4, 0, true);
        body  = new ModelBox(16, 16, 8, 12, 4, -4, 0, -2, 0, true);
        rarm  = new ModelBox(40, 16, 4, 12, 4, -7, 0, -2, 0, true);
        larm  = new ModelBox(32, 48, 4, 12, 4, 3, 0, -2, 0, true);
        rleg  = new ModelBox(0, 16, 4, 12, 4, -5.9f, 12, -2, 0, true);
        lleg  = new ModelBox(16, 48, 4, 12, 4, 1.9f, 12, -2, 0, true);
    }

    private void renderPart(ModelBox part, float[] pivot, float rotX,
                            float rotZ, float rotY, float bobY,
                            Matrix4f base, Matrix4f viewProj, Shader shader) {
        final float P = 1.0f / 16.0f;
        scratch.identity();
        scratch.translate(pivot[0] * P, pivot[1] * P, pivot[2] * P);
        scratch.rotateY(rotY);
        scratch.rotateX(-rotX);
        scratch.rotateZ(-rotZ);
        scratch.translate(-pivot[0] * P, -pivot[1] * P, -pivot[2] * P);
        scratch.translate(0, -bobY, 0);

        Matrix4f mvp = viewProj.mul(base, new Matrix4f()).mul(scratch);
        shader.setUniformMat4("mvp", mvp);
        part.render();
    }

    /**
     * @param base              translate + rotateY(yaw) + Y-flip + scale
     * @param viewProj          view-projection
     * @param shader            mob шейдер
     * @param limbSwing         фаза ходьбы
     * @param limbSwingAmount   амплитуда 0..1
     * @param attackPhase       замах правой рукой 0..1 (0 = нет)
     * @param headPitch         наклон головы вверх/вниз (радианы)
     * @param sneaking          приседание (красться)
     * @param bobY              покачивание головы при ходьбе
     */
    public void render(Matrix4f base, Matrix4f viewProj, Shader shader,
                       float limbSwing, float limbSwingAmount,
                       float attackPhase, float headPitch, boolean sneaking, float bobY) {

        float a = limbSwingAmount;

        // === Ноги ===
        float legBase = sneaking ? 0.35f : 0.75f;
        float rLegRot = (float) Math.cos(limbSwing * 0.6662f) * legBase * a;
        float lLegRot = (float) Math.cos(limbSwing * 0.6662f + Math.PI) * legBase * a;

        // === Руки ===
        float rArmRot;
        float lArmRot;
        if (attackPhase > 0.01f) {
            // Замах: рука из-за плеча вперёд-вниз
            float swing = (float) Math.sin(attackPhase * Math.PI);
            rArmRot = -1.7f + 2.4f * swing;
            lArmRot = BASE_ARM;
        } else if (sneaking) {
            // Красться: руки слегка выставлены вперёд, прижаты
            rArmRot = 0.5f;
            lArmRot = 0.5f;
        } else if (a > 0.01f) {
            // Ходьба: лёгкое покачивание рук в противофазе ногам
            rArmRot = BASE_ARM + (float) Math.cos(limbSwing * 0.6662f + Math.PI) * 0.4f * a;
            lArmRot = BASE_ARM + (float) Math.cos(limbSwing * 0.6662f) * 0.4f * a;
        } else {
            // Покой: едва заметное покачивание
            float sway = (float) Math.sin(limbSwing * 0.10f) * 0.04f;
            rArmRot = BASE_ARM + sway;
            lArmRot = BASE_ARM - sway;
        }

        // === Тело ===
        float bodyTilt = (sneaking ? 0.18f : 0) + BASE_BODY_TILT + 0.05f * a;

        // === Голова ===
        float headRot = BASE_HEAD_TILT + headPitch + (sneaking ? -0.15f : 0);

        renderPart(rleg, P_RLEG, rLegRot, 0, 0, 0, base, viewProj, shader);
        renderPart(lleg, P_LLEG, lLegRot, 0, 0, 0, base, viewProj, shader);
        renderPart(body, P_BODY, bodyTilt, 0, 0, 0, base, viewProj, shader);
        renderPart(rarm, P_RARM, rArmRot, 0, 0, 0, base, viewProj, shader);
        renderPart(larm, P_LARM, lArmRot, 0, 0, 0, base, viewProj, shader);
        renderPart(head, P_HEAD, headRot, 0, 0, bobY, base, viewProj, shader);
    }

    /**
     * Матрица кисти правой руки в мировом пространстве (для предмета в
     * руке). Дублирует поворот правой руки из {@link #render}: те же
     * пивот и rotX, затем сдвиг к концу кисти (низ бокса руки).
     */
    public void computeHandMatrix(Matrix4f base, float attackPhase,
                                  boolean sneaking, float limbSwing,
                                  float limbSwingAmount, Matrix4f out) {
        final float P = 1.0f / 16.0f;
        float rArmRot = BASE_ARM;
        if (attackPhase > 0.01f) {
            float swing = (float) Math.sin(attackPhase * Math.PI);
            rArmRot = -1.7f + 2.4f * swing;
        } else if (sneaking) {
            rArmRot = 0.5f;
        } else if (limbSwingAmount > 0.01f) {
            rArmRot = BASE_ARM + (float) Math.cos(limbSwing * 0.6662f + Math.PI) * 0.4f * limbSwingAmount;
        }

        out.set(base);
        handScratch.identity();
        handScratch.translate(P_RARM[0] * P, P_RARM[1] * P, P_RARM[2] * P);
        handScratch.rotateX(-rArmRot);
        handScratch.translate(-P_RARM[0] * P, -P_RARM[1] * P, -P_RARM[2] * P);
        // Конец кисти: 10px ниже плечевого пивота по Y модели (= вниз в мире)
        handScratch.translate(0, 10f / 16f, 0);
        out.mul(handScratch);
    }

    public void cleanup() {
        head.cleanup();
        body.cleanup();
        rarm.cleanup();
        larm.cleanup();
        rleg.cleanup();
        lleg.cleanup();
    }
}