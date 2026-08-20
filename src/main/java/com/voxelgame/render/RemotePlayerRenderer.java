package com.voxelgame.render;

import com.voxelgame.net.RemotePlayer;
import com.voxelgame.rendering.Camera;
import com.voxelgame.rendering.Shader;
import com.voxelgame.world.entity.ZoloyModel;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * Renders the other players in a local co-op session as Zoloy-style
 * humanoid avatars using the shared mob shader and the zoloy skin.
 */
public class RemotePlayerRenderer {

    private final Shader shader;
    private final int textureId;
    private final ZoloyModel model;

    // Scratch matrices
    private final Matrix4f baseMatrix = new Matrix4f();
    private final Matrix4f viewProj = new Matrix4f();

    public RemotePlayerRenderer() {
        shader = new Shader("shaders/mob.vert", "shaders/mob.frag");
        textureId = TextureLoader.loadTexture("textures/entities/zoloy.png");
        model = new ZoloyModel();
        System.out.println("[RemotePlayerRenderer] Texture ID: " + textureId);
    }

    public void render(List<RemotePlayer> players, Camera camera, float daylight) {
        if (players.isEmpty()) return;

        glEnable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE); // Vanilla humanoid: no culling
        glDisable(GL_BLEND);

        shader.bind();
        shader.setUniform1i("skin", 0);
        // Fade with the day like the mobs, so nobody glows at midnight
        shader.setUniform1f("lightLevel", 0.15f + 0.85f * daylight);
        shader.setUniform3f("sunColor", new Vector3f(
            (1.0f - daylight) * 0.55f + daylight, (1.0f - daylight) * 0.62f + daylight,
            (1.0f - daylight) * 0.80f + daylight));
        shader.setUniform1f("hurtFlash", 0.0f);
        // Plain 64x64 sheet: no tile scaling or offset
        shader.setUniform2f("tileScale", new Vector2f(1.0f, 1.0f));
        shader.setUniform2f("uvOffset", new Vector2f(0.0f, 0.0f));

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);

        viewProj.set(camera.getProjectionMatrix()).mul(camera.getViewMatrix());

        for (RemotePlayer p : players) {
            Vector3f pos = p.renderPos;
            // Feet at pos: the model's origin sits at the neck, 1.35 above feet
            baseMatrix.identity();
            baseMatrix.translate(pos.x, pos.y + 1.35f, pos.z);
            baseMatrix.rotateY((float) Math.toRadians(p.yaw));
            baseMatrix.scale(1f, -1f, 1f); // Y flip: model Y-down → world Y-up
            baseMatrix.scale(0.9f);

            model.render(baseMatrix, viewProj, shader,
                p.walking ? 1.0f : 0.0f, p.walking ? 1.0f : 0.0f, 0.0f, false);
        }

        shader.unbind();
    }

    public void cleanup() {
        model.cleanup();
        shader.cleanup();
        glDeleteTextures(textureId);
    }
}