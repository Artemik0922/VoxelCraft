package com.voxelgame.rendering;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders a wireframe outline around the targeted block.
 * Uses GL_LINES with a simple color shader.
 */
public class BlockOutline {

    private int vao;
    private int vbo;
    private Shader shader;
    private boolean initialized = false;

    // 12 edges of a cube = 24 vertices (each edge has 2 endpoints)
    // Slightly expanded (1.002) to avoid z-fighting with the block surface
    private static final float S = 1.002f;
    private static final float O = -0.001f; // offset to center the expansion
    private static final float[] VERTICES = {
        // Bottom face edges
        O, O, O,   S+O, O, O,
        S+O, O, O,   S+O, O, S+O,
        S+O, O, S+O,   O, O, S+O,
        O, O, S+O,   O, O, O,
        // Top face edges
        O, S+O, O,   S+O, S+O, O,
        S+O, S+O, O,   S+O, S+O, S+O,
        S+O, S+O, S+O,   O, S+O, S+O,
        O, S+O, S+O,   O, S+O, O,
        // Vertical edges
        O, O, O,   O, S+O, O,
        S+O, O, O,   S+O, S+O, O,
        S+O, O, S+O,   S+O, S+O, S+O,
        O, O, S+O,   O, S+O, S+O,
    };

    public void init() {
        if (initialized) return;
        initialized = true;

        shader = new Shader("shaders/outline.vert", "shaders/outline.frag");

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(VERTICES.length);
            buf.put(VERTICES);
            buf.flip();
            glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);
        }

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    /**
     * Render a wireframe cube at the given block position.
     *
     * @param x block X coordinate
     * @param y block Y coordinate
     * @param z block Z coordinate
     * @param camera the camera for view/projection matrices
     * @param color RGBA color (0xRRGGBBAA format)
     */
    public void render(int x, int y, int z, Camera camera, int color) {
        if (!initialized) init();

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glLineWidth(2.0f);

        shader.bind();
        shader.setUniformMat4("projection", camera.getProjectionMatrix());
        shader.setUniformMat4("view", camera.getViewMatrix());

        Matrix4f model = new Matrix4f().translate(x, y, z);
        shader.setUniformMat4("model", model);

        // Convert 0xRRGGBBAA to normalized floats
        float r = ((color >> 24) & 0xFF) / 255.0f;
        float g = ((color >> 16) & 0xFF) / 255.0f;
        float b = ((color >> 8) & 0xFF) / 255.0f;
        float a = (color & 0xFF) / 255.0f;
        shader.setUniform4f("color", r, g, b, a);

        glBindVertexArray(vao);
        glDrawArrays(GL_LINES, 0, 24);
        glBindVertexArray(0);

        shader.unbind();

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    public void cleanup() {
        if (!initialized) return;
        if (vbo != 0) glDeleteBuffers(vbo);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (shader != null) shader.cleanup();
        initialized = false;
    }
}
