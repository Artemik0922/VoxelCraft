package com.voxelgame.world.entity;

import com.voxelgame.rendering.Shader;
import com.voxelgame.rendering.model.ModelBox;
import org.joml.Matrix4f;

/**
 * Модель жителя деревни (Villager).
 *
 * Отличия от ZoloyModel:
 * - Большой нос (2 куба спереди головы)
 * - Скрещенные руки при IDLE (vanilla villager pose)
 * - Другая UV-раскладка
 * - Модель совместима с 64x64 текстурой атласа
 *
 * Пропорции:
 * - Голова: 8x10x8 (с носом)
 * - Тело: 8x12x4
 * - Руки: 4x12x4
 * - Ноги: 4x12x4
 */
public class VillagerModel {

    private final ModelBox head;
    private final ModelBox body;
    private final ModelBox rarm;
    private final ModelBox larm;
    private final ModelBox rleg;
    private final ModelBox lleg;
    /** Отдельный бокс для носа (выступает из головы). */
    private final ModelBox nose;
    /** Меч воина — висит от кисти правой руки (регион 36,32,2,14,1). */
    private final ModelBox sword;

    // Пивоты (как в ZoloyModel — Y down в модели, Y up в мире)
    private static final float[] P_HEAD = {0, 0, 0};
    private static final float[] P_BODY = {0, 0, 0};
    private static final float[] P_RARM = {-5, 2, 0};
    private static final float[] P_LARM = {5, 2, 0};
    private static final float[] P_RLEG = {-1.9f, 12, 0};
    private static final float[] P_LLEG = {1.9f, 12, 0};

    // Базовые позы
    private static final float BASE_BODY_TILT = 0.05f;
    private static final float BASE_HEAD_TILT = -0.1f;
    /** Руки скрещены перед телом — классическая поза жителя. */
    private static final float BASE_RARM_CROSSED = 0.7f;
    private static final float BASE_LARM_CROSSED = 0.7f;

    private final Matrix4f scratch = new Matrix4f();

    public VillagerModel() {
        // Модель в Y-down координатах: верх бокса (crown/плечи) = minY,
        // поэтому flipV=true — иначе текстуры всего тела вверх ногами.
        head  = new ModelBox(0, 0, 8, 8, 8, -4, -8, -4, 0, true);
        body  = new ModelBox(16, 16, 8, 12, 4, -4, 0, -2, 0, true);
        rarm  = new ModelBox(40, 16, 4, 12, 4, -7, 0, -2, 0, true);
        larm  = new ModelBox(32, 48, 4, 12, 4, 3, 0, -2, 0, true);
        rleg  = new ModelBox(0, 16, 4, 12, 4, -5.9f, 12, -2, 0, true);
        lleg  = new ModelBox(16, 48, 4, 12, 4, 1.9f, 12, -2, 0, true);
        // Нос — маленький кубик, выступает на 1px вперёд (по центру лица)
        nose  = new ModelBox(24, 0, 2, 4, 2, -1, -6, -6, 0, true);
        // Меч: клинок вниз от кисти правой руки (origin -5,12,-2.5),
        // UV-регион (36,32) в свободной зоне скина
        sword = new ModelBox(36, 32, 2, 14, 1, -5, 12, -2.5f, 0, true);
    }

    /**
     * Отрендерить часть тела с вращением вокруг пивота.
     *
     * Пивот задан в пикселях скина, а геометрия боксов — в блочных
     * единицах (1px = 1/16 блока), поэтому пивот делится на 16. Вращение
     * выполняется вокруг точки (pivot[0], pivot[1], pivot[2]) в модельном
     * пространстве (Y down): для ног y=12 — это бедро, для рук y=2 —
     * низ плеча. Без деления на 16 ноги вращались вокруг точки в 12
     * блоках под телом и отрывались от корпуса.
     */
    private void renderPart(ModelBox part, float[] pivot, float rotX,
                            float rotZ, float rotY, float bobY,
                            Matrix4f base, Matrix4f viewProj, Shader shader) {
        final float P = 1.0f / 16.0f;
        scratch.identity();
        scratch.translate(pivot[0] * P, pivot[1] * P, pivot[2] * P);
        // Порядок: yaw вокруг вертикали модели, затем наклон, затем крен
        scratch.rotateY(rotY);
        scratch.rotateX(-rotX);
        scratch.rotateZ(-rotZ);
        scratch.translate(-pivot[0] * P, -pivot[1] * P, -pivot[2] * P);
        // Покачивание (подъём в мире = минус Y модели из-за Y-flip)
        scratch.translate(0, -bobY, 0);

        Matrix4f mvp = viewProj.mul(base, new Matrix4f()).mul(scratch);
        shader.setUniformMat4("mvp", mvp);
        part.render();
    }

    /**
     * Отрендерить жителя.
     *
     * @param base              базовая матрица (translate + Y-flip)
     * @param viewProj          view-projection
     * @param shader            mob шейдер
     * @param limbSwing         фаза ходьбы
     * @param limbSwingAmount   амплитуда (0-1)
     * @param isTalking         говорит ли (для анимации рук)
     * @param isSleeping        спит ли
     * @param isBaby            ребёнок ли (масштаб 0.5)
     * @param hasSword          держит ли меч (воин)
     * @param attackPhase       фаза замаха мечом 0..1 (0 = нет удара)
     * @param headYawOffset     поворот головы по yaw (мировая разница, обёрнутая)
     * @param headPitch         наклон головы вверх/вниз (+, вверх)
     * @param bobY              покачивание головы при ходьбе (шаг в мире)
     */
    public void render(Matrix4f base, Matrix4f viewProj, Shader shader,
                       float limbSwing, float limbSwingAmount,
                       boolean isTalking, boolean isSleeping, boolean isBaby,
                       boolean hasSword, float attackPhase,
                       float headYawOffset, float headPitch, float bobY) {

        float a = limbSwingAmount;

        // === Ноги ===
        // Умеренный шаг (как спокойная ходьба игрока): ~43° при полной
        // амплитуде — конечности вращаются вокруг сустава и не отрываются.
        float rLegRot = (float) Math.cos(limbSwing * 0.6662f) * 0.75f * a;
        float lLegRot = (float) Math.cos(limbSwing * 0.6662f + Math.PI) * 0.75f * a;

        // === Руки ===
        float rArmRot;
        float lArmRot;

        if (hasSword && attackPhase > 0.01f) {
            // Замах мечом: рука из-за плеча вперёд-вниз (как у ZoloyModel)
            float swing = (float) Math.sin(attackPhase * Math.PI);
            rArmRot = -1.7f + 2.4f * swing;
            lArmRot = BASE_LARM_CROSSED;
        } else if (isSleeping) {
            // Лежат — руки расслаблены вдоль тела
            rArmRot = 0.05f;
            lArmRot = 0.05f;
        } else if (a > 0.01f) {
            // Идут — руки слегка покачиваются, не отрываясь от плеча
            rArmRot = BASE_RARM_CROSSED + (float) Math.cos(limbSwing * 0.6662f + Math.PI) * 0.35f * a;
            lArmRot = BASE_LARM_CROSSED + (float) Math.cos(limbSwing * 0.6662f) * 0.35f * a;
        } else if (isTalking) {
            // Жест разговора — периодическое взмахивание руками
            float gesture = (float) Math.sin(limbSwing * 0.35f) * 0.12f;
            rArmRot = BASE_RARM_CROSSED - 0.3f + gesture;
            lArmRot = BASE_LARM_CROSSED - 0.3f - gesture;
        } else {
            // В покое — лёгкое покачивание, скрещенные руки
            float sway = (float) Math.sin(limbSwing * 0.10f) * 0.04f;
            rArmRot = BASE_RARM_CROSSED + sway;
            lArmRot = BASE_LARM_CROSSED - sway;
        }

        // === Тело ===
        // Лёгкий наклон вперёд при ходьбе
        float bodyTilt = isSleeping ? 0 : BASE_BODY_TILT + 0.05f * a;

        // === Голова ===
        float headRot;
        float headYaw;
        if (isSleeping) {
            headRot = 0;
            headYaw = 0;
        } else {
            // headPitch > 0 = взгляд вверх (rotateX(-rotX) даёт мир-вверх)
            headRot = BASE_HEAD_TILT + headPitch;
            // rotY без отрицания: rotateY коммутирует с Y-flip
            headYaw = headYawOffset;
        }

        // === Рендер ===
        renderPart(rleg, P_RLEG, rLegRot, 0, 0, 0, base, viewProj, shader);
        renderPart(lleg, P_LLEG, lLegRot, 0, 0, 0, base, viewProj, shader);
        renderPart(body, P_BODY, bodyTilt, 0, 0, 0, base, viewProj, shader);
        renderPart(rarm, P_RARM, rArmRot, 0, 0, 0, base, viewProj, shader);
        if (hasSword) {
            // Меч едет вместе с правой рукой (тот же пивот и вращение)
            renderPart(sword, P_RARM, rArmRot, 0, 0, 0, base, viewProj, shader);
        }
        renderPart(larm, P_LARM, lArmRot, 0, 0, 0, base, viewProj, shader);

        // Голова + нос (нос наследует вращение головы)
        renderPart(head, P_HEAD, headRot, 0, headYaw, bobY, base, viewProj, shader);
        renderPart(nose, P_HEAD, headRot, 0, headYaw, bobY, base, viewProj, shader);
    }

    public void cleanup() {
        head.cleanup();
        body.cleanup();
        rarm.cleanup();
        larm.cleanup();
        rleg.cleanup();
        lleg.cleanup();
        nose.cleanup();
        sword.cleanup();
    }
}
