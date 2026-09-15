package com.voxelgame.rendering.model;

import com.voxelgame.rendering.Shader;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.world.BlockType;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * A flat double-sided quad for rendering item sprites (swords, tools, etc.)
 * in first-person view. Uses the same shader and vertex layout as BlockCube
 * so it can share the held-block pipeline.
 *
 * The quad faces the camera directly and is textured from a single atlas
 * layer (the item's sprite tile), with transparency for the background.
 */
public class ItemSprite {

    /** floats per vertex: x, y, z, u, v, shade, faceIndex */
    private static final int VERTEX_SIZE = 7;
    private static final int VERTEX_COUNT = 6;

    private final int vao;
    private final int vbo;

    public ItemSprite() {
        FloatBuffer buf = MemoryUtil.memAllocFloat(VERTEX_COUNT * VERTEX_SIZE);

        // Flat quad facing the camera (in view space, -Z is forward)
        // Centered on origin, sized to match a held block visually
        float a = -0.5f, b = 0.5f;
        float shade = 1.0f;

        // Two triangles forming a quad at z=0
        vertex(buf, a, b, 0, 0, 0, shade, 0);
        vertex(buf, b, b, 0, 1, 0, shade, 0);
        vertex(buf, b, a, 0, 1, 1, shade, 0);

        vertex(buf, a, b, 0, 0, 0, shade, 0);
        vertex(buf, b, a, 0, 1, 1, shade, 0);
        vertex(buf, a, a, 0, 0, 1, shade, 0);

        buf.flip();

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);

        int stride = VERTEX_SIZE * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5 * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6 * Float.BYTES);
        glEnableVertexAttribArray(3);

        glBindVertexArray(0);
        MemoryUtil.memFree(buf);
    }

    private void vertex(FloatBuffer b, float x, float y, float z,
                        float u, float v, float shade, int face) {
        b.put(x).put(y).put(z).put(u).put(v).put(shade).put(face);
    }

    /**
     * Draw the flat sprite using the atlas layer for this item type.
     */
    public void render(TextureAtlas atlas, BlockType type, Shader shader) {
        render(atlas, atlas.getSlot(type.id, 2), shader); // top face slot
    }

    /**
     * Draw the flat sprite from a specific atlas array layer.
     */
    public void render(TextureAtlas atlas, int layer, Shader shader) {
        int[] layers = new int[6];
        java.util.Arrays.fill(layers, layer);
        shader.setUniform1iv("faceLayers", layers);

        glDisable(GL_CULL_FACE); // double-sided so it's visible from both angles
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, VERTEX_COUNT);
        glBindVertexArray(0);
        glEnable(GL_CULL_FACE);
    }

    public void cleanup() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
