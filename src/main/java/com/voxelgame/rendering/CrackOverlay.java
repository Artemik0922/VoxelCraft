package com.voxelgame.rendering;

import com.voxelgame.rendering.model.BlockCube;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;

/**
 * [GP-013] Black crack overlay drawn on the block currently being mined.
 *
 * Renders the 5-stage crack tile from the atlas on a cube slightly larger
 * than the block, using a polygon offset so the cracks sit exactly on the
 * block's surface without z-fighting. The tile's transparent background is
 * cut out by the shader's alpha test.
 */
public class CrackOverlay {

    private Shader shader;
    private BlockCube cube;
    private boolean initialized = false;

    private final Matrix4f model = new Matrix4f();

    private void init() {
        if (initialized) return;
        initialized = true;
        shader = new Shader("shaders/heldblock.vert", "shaders/heldblock.frag");
        cube = new BlockCube();
    }

    /**
     * Draw the crack stage on the block at (x, y, z).
     *
     * @param stage 0..4 — how far the block has crumbled
     */
    public void render(int x, int y, int z, int stage, Camera camera, TextureAtlas atlas) {
        if (stage < 0 || stage > 4) return;
        init();

        int layer = atlas.getLayerOf("crack_stage" + stage);
        if (layer < 0) return;

        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glPolygonOffset(-1.0f, -1.0f);
        glEnable(GL_POLYGON_OFFSET_FILL);

        shader.bind();
        shader.setUniformMat4("projection", camera.getProjectionMatrix());
        shader.setUniformMat4("view", camera.getViewMatrix());
        model.identity().translate(x, y, z).scale(1.002f);
        shader.setUniformMat4("model", model);
        shader.setUniform1i("blockTextures", 0);
        shader.setUniform1f("lightLevel", 1.0f);
        shader.setUniform3f("sunColor", new Vector3f(1.0f, 1.0f, 1.0f));

        atlas.bindArray();
        cube.render(atlas, layer, shader);
        atlas.unbindArray();

        shader.unbind();

        glDisable(GL_POLYGON_OFFSET_FILL);
    }

    public void cleanup() {
        if (!initialized) return;
        cube.cleanup();
        shader.cleanup();
        initialized = false;
    }
}
