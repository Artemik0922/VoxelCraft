package com.voxelgame.render;

import com.voxelgame.rendering.Camera;
import com.voxelgame.rendering.Shader;
import com.voxelgame.world.entity.Villager;
import com.voxelgame.world.entity.VillagerModel;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * Renders villagers using the part-based VillagerModel and the HD profession
 * atlas (9216x4096: 17 profession tiles of 1024x1024 in a 9x2 grid, twice
 * for the two skin variants — a single 17-wide row would exceed
 * GL_MAX_TEXTURE_SIZE = 16384 on the Radeon RX 580). Each villager is drawn
 * with its own model matrix: translate(pos) * rotateY(yaw) * scale, and the
 * mob shader offsets the UVs by the profession tile + variant row so a
 * single texture bind covers every profession.
 */
public class VillagerRenderer {

    /** Profession tiles per atlas row (9 columns of 1024px in 9216px). */
    private static final int ATLAS_COLS = 9;
    /** Profession rows in the atlas (17 professions -> 2 rows of 9). */
    private static final int ATLAS_ROWS = 2;
    /** Skin variants per profession (variant rows stacked below). */
    private static final int ATLAS_VARIANTS = 2;
    /**
     * Tile size as a fraction of the atlas. Only fractions matter here, so
     * the same values work at 1024px tiles (9216x4096) and at the legacy
     * 64px tiles (1024x64).
     */
    private static final float TILE_U = 1.0f / ATLAS_COLS;
    private static final float TILE_V = 1.0f / (ATLAS_ROWS * ATLAS_VARIANTS);

    private final Shader shader;
    private final int textureId;
    private final VillagerModel model;

    // Scratch matrices
    private final Matrix4f baseMatrix = new Matrix4f();
    private final Matrix4f viewProj = new Matrix4f();

    public VillagerRenderer() {
        shader = new Shader("shaders/mob.vert", "shaders/mob.frag");
        textureId = TextureLoader.loadTexture("textures/entities/villager_atlas.png");
        model = new VillagerModel();
        System.out.println("[VillagerRenderer] Texture ID: " + textureId);
        System.out.println("[VillagerRenderer] GL_MAX_TEXTURE_SIZE: "
            + glGetInteger(GL_MAX_TEXTURE_SIZE)
            + " (HD atlas requires >= 16384)");
    }

    public void render(List<Villager> villagers, Camera camera, float daylight) {
        if (villagers.isEmpty()) return;

        glEnable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE); // Vanilla humanoid: no culling
        glDisable(GL_BLEND);

        shader.bind();
        shader.setUniform1i("skin", 0);
        // Fade with the day: full bright at noon, a dim moonlight sliver at
        // midnight. This was hardcoded to 1.0, which is why villagers glowed
        // in the dark.
        shader.setUniform1f("lightLevel", 0.15f + 0.85f * daylight);
        shader.setUniform3f("sunColor", new Vector3f(
            (1.0f - daylight) * 0.55f + daylight, (1.0f - daylight) * 0.62f + daylight,
            (1.0f - daylight) * 0.80f + daylight));
        shader.setUniform1f("hurtFlash", 0.0f);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);

        viewProj.set(camera.getProjectionMatrix()).mul(camera.getViewMatrix());

        for (Villager v : villagers) {
            Vector3f pos = v.getPosition();

            // textureIndex is the profession ordinal, which matches the tile
            // order in villager_atlas.json / villager_atlas.png. The HD atlas
            // is 9216px wide with 9 tiles per row, 2 profession rows, and
            // 2 variant rows below them: v = row*TILE_V + variant*2*TILE_V.
            int idx = v.getTextureIndex();
            int col = idx % ATLAS_COLS;
            int row = idx / ATLAS_COLS;
            float u = col * TILE_U;
            float vv = (row + (v.getVariant() % ATLAS_VARIANTS) * ATLAS_ROWS) * TILE_V;
            shader.setUniform2f("tileScale", new Vector2f(TILE_U, TILE_V));
            shader.setUniform2f("uvOffset", new Vector2f(u, vv));

            baseMatrix.identity();
            // Модель висит вниз от точки перевода (шея = origin). pos.y — это
            // ноги на земле, поэтому шея на 1.5*scale выше ног.
            float s = v.isBaby() ? 0.45f : 0.9f;
            if (v.isSleeping()) {
                // Сон: лежит на спине лицом вверх. Поворот Rx(π/2) кладёт
                // модель вдоль оси Z (голова к +Z, ноги к -Z), yaw определяет
                // направление тела. Шея на 0.22*scale над землёй.
                baseMatrix.translate(pos.x, pos.y + 0.22f * s, pos.z);
                baseMatrix.rotateX((float) (Math.PI / 2.0));
                baseMatrix.rotateY(v.getYaw());
            } else {
                baseMatrix.translate(pos.x, pos.y + 1.5f * s, pos.z);
                baseMatrix.rotateY(v.getYaw());
            }
            baseMatrix.scale(1f, -1f, 1f); // Y flip: model Y-down -> world Y-up
            baseMatrix.scale(s);

            // Голова следит за игроком: разница lookYaw - yaw, обёрнутая и
            // ограниченная, чтобы шея не выкручивалась
            float headYawOffset = 0;
            float headPitch = 0;
            if (!v.isSleeping()) {
                float dy = v.getLookYaw() - v.getYaw();
                while (dy > Math.PI) dy -= 2 * Math.PI;
                while (dy < -Math.PI) dy += 2 * Math.PI;
                if (dy > 0.8f) dy = 0.8f;
                else if (dy < -0.8f) dy = -0.8f;
                headYawOffset = dy;
                headPitch = v.getLookPitch();
            }

            model.render(baseMatrix, viewProj, shader,
                v.getWalkPhase(), v.getLimbSwingAmount(),
                v.isTalking(), v.isSleeping(), v.isBaby(),
                v.getProfession() == Villager.Profession.WARRIOR,
                v.getAttackPhase(),
                headYawOffset, headPitch, v.getHeadBob());
        }

        shader.unbind();
    }

    public void cleanup() {
        model.cleanup();
        shader.cleanup();
        glDeleteTextures(textureId);
    }
}
