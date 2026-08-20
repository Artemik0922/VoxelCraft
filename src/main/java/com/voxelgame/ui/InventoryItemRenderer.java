package com.voxelgame.ui;

import com.voxelgame.rendering.Shader;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.rendering.model.BlockCube;
import com.voxelgame.world.BlockType;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * Renders block items in inventory as small 3D cubes (like Minecraft).
 * Saves and restores GL state to avoid breaking the UI renderer.
 */
public class InventoryItemRenderer {

    private static final Shader shader = new Shader("shaders/heldblock.vert", "shaders/heldblock.frag");
    private static final BlockCube cube = new BlockCube();
    private static final Matrix4f proj = new Matrix4f();
    private static final Matrix4f view = new Matrix4f();
    private static final Matrix4f model = new Matrix4f();

    /**
     * Render a block as a 3D cube at screen position (x, y) with given size.
     */
    public static void render(TextureAtlas atlas, BlockType block, float x, float y, float size, float daylight) {
        if (block == null || block == BlockType.AIR) return;

        // Save GL state
        boolean depthTest = glIsEnabled(GL_DEPTH_TEST);
        boolean cullFace = glIsEnabled(GL_CULL_FACE);
        boolean blend = glIsEnabled(GL_BLEND);

        // Setup for 3D rendering
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        glDisable(GL_BLEND);

        // Setup matrices for small viewport at (x, y)
        float aspect = 1.0f;
        float fov = 1.0f; // narrow FOV for GUI
        float dist = 2.2f;
        float scale = size / 16f; // pixel to world scale

        proj.identity().setPerspective(fov, aspect, 0.1f, 100f);
        view.identity().translate(0, 0, -dist);
        model.identity()
            .translate(0, 0, 0)
            .rotateX(-0.4f)              // tilt to show top
            .rotateY(0.7f)               // rotate to show side
            .scale(scale * 0.7f);        // scale to fit slot

        shader.bind();
        shader.setUniformMat4("projection", proj);
        shader.setUniformMat4("view", view);
        shader.setUniformMat4("model", model);
        shader.setUniform1i("blockTextures", 0);
        shader.setUniform1f("lightLevel", 0.6f + 0.4f * daylight);
        shader.setUniform3f("sunColor", new org.joml.Vector3f(1f, 1f, 1f));

        glActiveTexture(GL_TEXTURE0);
        atlas.bindArray();

        cube.render(atlas, block, shader);

        atlas.unbindArray();
        shader.unbind();

        // Restore GL state
        if (!depthTest) glDisable(GL_DEPTH_TEST);
        if (cullFace) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
        if (blend) glEnable(GL_BLEND); else glDisable(GL_BLEND);
    }

    public static void cleanup() {
        shader.cleanup();
        cube.cleanup();
    }
}
