package com.voxelgame.rendering;

import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Gradient skybox drawn as a unit cube at the far plane.
 *
 * Colors are computed in the fragment shader from the view direction, so no
 * cubemap texture is needed and the horizon can be matched exactly to the fog.
 */
public class Skybox {

    private static final float[] CUBE_VERTICES = {
        -1,  1, -1,  -1, -1, -1,   1, -1, -1,   1, -1, -1,   1,  1, -1,  -1,  1, -1,
        -1, -1,  1,  -1, -1, -1,  -1,  1, -1,  -1,  1, -1,  -1,  1,  1,  -1, -1,  1,
         1, -1, -1,   1, -1,  1,   1,  1,  1,   1,  1,  1,   1,  1, -1,   1, -1, -1,
        -1, -1,  1,  -1,  1,  1,   1,  1,  1,   1,  1,  1,   1, -1,  1,  -1, -1,  1,
        -1,  1, -1,   1,  1, -1,   1,  1,  1,   1,  1,  1,  -1,  1,  1,  -1,  1, -1,
        -1, -1, -1,  -1, -1,  1,   1, -1, -1,   1, -1, -1,  -1, -1,  1,   1, -1,  1
    };

    private final int vao;
    private final int vbo;
    private final Shader shader;

    // Horizon must match the clear color / fog color exactly, otherwise the
    // fogged-out terrain ends in a visible band against the sky.
    private final Vector3f horizonColor = new Vector3f(0.529f, 0.808f, 0.922f);
    private final Vector3f zenithColor = new Vector3f(0.35f, 0.60f, 0.90f);

    public Skybox() {
        shader = new Shader("shaders/sky.vert", "shaders/sky.frag");

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, CUBE_VERTICES, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindVertexArray(0);
    }

    /**
     * Draw the sky using the palette from the day/night cycle.
     *
     * @param overcast 0..1 weather dimming (rain/thunder/snow)
     * @param flash   0..1 lightning flash brightness
     */
    public void render(Camera camera, DayNightCycle cycle,
                       float overcast, float flash) {
        horizonColor.set(cycle.getHorizonColor());
        zenithColor.set(cycle.getZenithColor());

        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);

        shader.bind();
        shader.setUniformMat4("projection", camera.getProjectionMatrix());
        shader.setUniformMat4("view", camera.getViewMatrix());
        shader.setUniform3f("horizonColor", horizonColor);
        shader.setUniform3f("zenithColor", zenithColor);
        shader.setUniform3f("sunDirection", cycle.getSunDirection());
        shader.setUniform3f("moonDirection", cycle.getMoonDirection());
        shader.setUniform3f("sunColor", cycle.getSunColor());
        shader.setUniform1f("daylight", cycle.getDaylight());
        shader.setUniform1f("time", (float) glfwGetTime());
        shader.setUniform1f("overcast", overcast);
        shader.setUniform1f("flash", flash);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 36);
        glBindVertexArray(0);

        shader.unbind();

        glDepthMask(true);
        glDepthFunc(GL_LESS);
        glEnable(GL_CULL_FACE);
    }

    /** Legacy entry point kept for the fixed-palette path. */
    public void render(Camera camera, Vector3f skyColor, Vector3f sunDirection, Vector3f sunColor) {
        // The cube sits exactly on the far plane, so it needs LEQUAL to pass
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);

        shader.bind();
        shader.setUniformMat4("projection", camera.getProjectionMatrix());
        shader.setUniformMat4("view", camera.getViewMatrix());
        shader.setUniform3f("horizonColor", horizonColor);
        shader.setUniform3f("zenithColor", zenithColor);
        shader.setUniform3f("sunDirection", sunDirection);
        shader.setUniform3f("sunColor", sunColor);
        shader.setUniform1f("time", (float) glfwGetTime());

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 36);
        glBindVertexArray(0);

        shader.unbind();

        glDepthMask(true);
        glDepthFunc(GL_LESS);
        glEnable(GL_CULL_FACE);
    }

    /** Fog must be tinted with this so terrain dissolves into the horizon. */
    public Vector3f getHorizonColor() { return horizonColor; }

    public void cleanup() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        shader.cleanup();
    }
}
