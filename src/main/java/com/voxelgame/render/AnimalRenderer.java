package com.voxelgame.render;

import com.voxelgame.rendering.Camera;
import com.voxelgame.rendering.Shader;
import com.voxelgame.world.entity.Animal;
import com.voxelgame.world.entity.AnimalModel;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * Renders farm animals using per-type part models and skin sheets. Each
 * animal is drawn with translate(pos + feet offset) * rotateY(yaw) *
 * Y-flip * scale(model scale * growth scale).
 */
public class AnimalRenderer {

    private final Shader shader;
    private final int[] textures;
    private final AnimalModel[] models;

    private final Matrix4f baseMatrix = new Matrix4f();
    private final Matrix4f viewProj = new Matrix4f();

    public AnimalRenderer() {
        shader = new Shader("shaders/mob.vert", "shaders/mob.frag");
        Animal.AnimalType[] types = Animal.AnimalType.values();
        textures = new int[types.length];
        models = new AnimalModel[types.length];
        for (int i = 0; i < types.length; i++) {
            String name = types[i].name().toLowerCase();
            textures[i] = TextureLoader.loadTexture("textures/entities/" + name + ".png");
            models[i] = new AnimalModel(types[i]);
        }
        System.out.println("[AnimalRenderer] ready (" + types.length + " types)");
    }

    public void render(List<Animal> animals, Camera camera, float daylight) {
        if (animals.isEmpty()) return;

        glEnable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);

        shader.bind();
        shader.setUniform1i("skin", 0);
        shader.setUniform1f("lightLevel", 0.15f + 0.85f * daylight);
        shader.setUniform3f("sunColor", new Vector3f(
            (1.0f - daylight) * 0.55f + daylight, (1.0f - daylight) * 0.62f + daylight,
            (1.0f - daylight) * 0.80f + daylight));
        shader.setUniform1f("hurtFlash", 0.0f);
        shader.setUniform2f("tileScale", new Vector2f(1.0f, 1.0f));
        shader.setUniform2f("uvOffset", new Vector2f(0.0f, 0.0f));

        glActiveTexture(GL_TEXTURE0);

        viewProj.set(camera.getProjectionMatrix()).mul(camera.getViewMatrix());

        for (Animal a : animals) {
            int t = a.getType().ordinal();
            glBindTexture(GL_TEXTURE_2D, textures[t]);

            Vector3f pos = a.getPosition();
            float scale = a.getModelScale();
            // Feet sit at pos.y: the leg boxes end 12 px below the origin,
            // so the origin floats 12 * scale / 16 = 0.75 * scale up
            baseMatrix.identity();
            baseMatrix.translate(pos.x, pos.y + 0.75f * scale, pos.z);
            baseMatrix.rotateY(a.getYaw());
            baseMatrix.scale(1f, -1f, 1f); // Y flip: model Y-down → world Y-up
            baseMatrix.scale(scale);

            shader.setUniform1f("hurtFlash", a.getHurtProgress());

            models[t].render(baseMatrix, viewProj, shader,
                a.getWalkPhase(), a.getLimbSwingAmount());
        }

        shader.unbind();
    }

    public void cleanup() {
        for (int i = 0; i < models.length; i++) {
            models[i].cleanup();
            glDeleteTextures(textures[i]);
        }
        shader.cleanup();
    }
}