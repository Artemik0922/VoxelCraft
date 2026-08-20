package com.voxelgame.render;

import com.voxelgame.rendering.Camera;
import com.voxelgame.rendering.Shader;
import com.voxelgame.world.entity.Zoloy;
import com.voxelgame.world.entity.ZoloyModel;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * Renders Zoloy mobs using the part-based model and a dedicated texture.
 * Each mob is drawn with its own model matrix: translate(pos) * rotateY(yaw) * scale(0.8).
 */
public class MobRenderer {

    private final Shader shader;
    private final int textureId;
    private final ZoloyModel model;

    // Scratch matrices
    private final Matrix4f baseMatrix = new Matrix4f();
    private final Matrix4f viewProj = new Matrix4f();

    public MobRenderer() {
        shader = new Shader("shaders/mob.vert", "shaders/mob.frag");
        textureId = TextureLoader.loadTexture("textures/entities/zoloy.png");
        model = new ZoloyModel();
        System.out.println("[MobRenderer] Texture ID: " + textureId);
    }

    public void render(List<Zoloy> mobs, Camera camera, float daylight) {
        if (mobs.isEmpty()) return;

        glEnable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE); // Vanilla humanoid: no culling (fixes holes)
        glDisable(GL_BLEND);

        shader.bind();
        shader.setUniform1i("skin", 0);
        // Mobs fade with the day: full bright at noon, a dim moonlight sliver
        // at midnight so they no longer glow in the dark
        shader.setUniform1f("lightLevel", 0.15f + 0.85f * daylight);
        shader.setUniform3f("sunColor", new Vector3f(
            (1.0f - daylight) * 0.55f + daylight, (1.0f - daylight) * 0.62f + daylight,
            (1.0f - daylight) * 0.80f + daylight));
        shader.setUniform1f("hurtFlash", 0.0f);
        // Zoloy uses a plain 64x64 sheet: no tile scaling or offset
        shader.setUniform2f("tileScale", new Vector2f(1.0f, 1.0f));
        shader.setUniform2f("uvOffset", new Vector2f(0.0f, 0.0f));

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);

        viewProj.set(camera.getProjectionMatrix()).mul(camera.getViewMatrix());

        for (Zoloy mob : mobs) {
            Vector3f pos = mob.getPosition();
            // Base: translate to pos, rotate Y (yaw), flip Y (model→world), scale
            // Модель висит вниз от точки перевода (шея = origin). pos.y — это
            // ноги на земле, поэтому шея на 1.35 выше ног (1.5 * 0.9).
            baseMatrix.identity();
            baseMatrix.translate(pos.x, pos.y + 1.35f, pos.z);
            baseMatrix.rotateY(mob.getYaw());
            baseMatrix.scale(1f, -1f, 1f); // Y flip: model Y-down → world Y-up
            baseMatrix.scale(0.9f);

            // [GP-038] Per-mob hurt flash: red flash while recently hit
            shader.setUniform1f("hurtFlash", mob.getHurtProgress());

            model.render(baseMatrix, viewProj, shader,
                mob.getWalkPhase(), mob.getLimbSwingAmount(),
                mob.getAttackPhase(), mob.isDebugTpose());
        }

        shader.unbind();
    }

    public void cleanup() {
        model.cleanup();
        shader.cleanup();
        glDeleteTextures(textureId);
    }
}
