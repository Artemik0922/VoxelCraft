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
 * A unit cube textured from the block atlas, used for the block held in
 * the player's hand.
 *
 * The atlas is a texture array, so each face only needs its layer index;
 * that is uploaded per draw rather than baked in, letting one cube render
 * any block type.
 */
public class BlockCube {

    /** floats per vertex: x, y, z, u, v, shade, faceIndex */
    private static final int VERTEX_SIZE = 7;
    private static final int VERTEX_COUNT = 36;

    private final int vao;
    private final int vbo;

    public BlockCube() {
        FloatBuffer buf = MemoryUtil.memAllocFloat(VERTEX_COUNT * VERTEX_SIZE);

        // Centre the cube on its own origin so rotation looks natural
        float a = -0.5f, b = 0.5f;

        // face order matches the mesher: 0 +X, 1 -X, 2 +Y, 3 -Y, 4 +Z, 5 -Z
        quad(buf, b,a,b,  b,a,a,  b,b,a,  b,b,b, 0.80f, 0);
        quad(buf, a,a,a,  a,a,b,  a,b,b,  a,b,a, 0.80f, 1);
        quad(buf, a,b,b,  b,b,b,  b,b,a,  a,b,a, 1.00f, 2);
        quad(buf, a,a,a,  b,a,a,  b,a,b,  a,a,b, 0.55f, 3);
        quad(buf, a,a,b,  b,a,b,  b,b,b,  a,b,b, 0.68f, 4);
        quad(buf, b,a,a,  a,a,a,  a,b,a,  b,b,a, 0.68f, 5);

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

    private void quad(FloatBuffer b,
                      float ax, float ay, float az,
                      float bx, float by, float bz,
                      float cx, float cy, float cz,
                      float dx, float dy, float dz,
                      float shade, int face) {
        vertex(b, ax, ay, az, 0, 0, shade, face);
        vertex(b, bx, by, bz, 1, 0, shade, face);
        vertex(b, cx, cy, cz, 1, 1, shade, face);

        vertex(b, ax, ay, az, 0, 0, shade, face);
        vertex(b, cx, cy, cz, 1, 1, shade, face);
        vertex(b, dx, dy, dz, 0, 1, shade, face);
    }

    private void vertex(FloatBuffer b, float x, float y, float z,
                        float u, float v, float shade, int face) {
        b.put(x).put(y).put(z).put(u).put(v).put(shade).put(face);
    }

    /**
     * Draw the cube using the atlas layers belonging to this block type.
     */
    public void render(TextureAtlas atlas, BlockType type, Shader shader) {
        // One layer per face, resolved at a fixed position so the held item
        // always shows the same variant
        int[] layers = new int[6];
        for (int face = 0; face < 6; face++) {
            layers[face] = atlas.getSlot(type.id, face);
        }
        shader.setUniform1iv("faceLayers", layers);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, VERTEX_COUNT);
        glBindVertexArray(0);
    }

    /** Draw the cube with one single layer on every face (crack overlays). */
    public void render(TextureAtlas atlas, int layer, Shader shader) {
        int[] layers = new int[6];
        for (int face = 0; face < 6; face++) {
            layers[face] = layer;
        }
        shader.setUniform1iv("faceLayers", layers);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, VERTEX_COUNT);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
