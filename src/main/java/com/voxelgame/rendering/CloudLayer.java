package com.voxelgame.rendering;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Minecraft-style cloud layer: a flat horizontal deck of square 12x12-block
 * cells at Y=128, each cell either fully present or fully absent.
 *
 * The pattern is a thresholded 2D value-noise field sampled per cell, so the
 * deck looks like crisp rectangular white squares with hard edges - no
 * gradients, no blur, no glow. The deck is rebuilt into a small grid region
 * centred on the player whenever the player (or the drifting pattern) crosses
 * a cell boundary, which keeps it seamless and cheap.
 *
 * Clouds are drawn after the opaque world, with depth writes off so they
 * blend at 80% opacity without occluding anything behind them.
 */
public class CloudLayer {

    /** Width/height of one cloud cell, matching Minecraft's 12x12 grid. */
    private static final float CELL_SIZE = 12.0f;
    /** Altitude of the deck's top plane, matching Minecraft. */
    private static final float CLOUD_Y = 128.0f;
    /** Deck thickness: bottom plane sits CLOUD_THICKNESS below the top. */
    private static final float CLOUD_THICKNESS = 4.0f;
    /** Clouds are drawn at 80% opacity like vanilla. */
    private static final float CLOUD_ALPHA = 0.8f;
    /** Drift speed in blocks per second, west (-X). */
    private static final float DRIFT_SPEED = 1.6f;
    /** Noise cutoff: cells above this are fully present, below fully absent. */
    private static final float NOISE_THRESHOLD = 0.5f;
    /** Region radius in cells; always well beyond the max fog distance. */
    private static final int REGION_CELLS = 32;
    /** Cells fade out beyond the fog so the region edge never shows. */
    private static final float FADE_START = 320.0f;
    private static final float FADE_END = REGION_CELLS * CELL_SIZE;

    // Top face and side faces are white, the underside is slightly grey
    private static final float WHITE_R = 1.0f, WHITE_G = 1.0f, WHITE_B = 1.0f;
    private static final float BOTTOM_SHADE = 0.80f;

    /** floats per vertex: x, y, z, r, g, b */
    private static final int VERTEX_SIZE = 6;
    private static final int CELLS_PER_SIDE = REGION_CELLS * 2 + 1;

    private final Shader shader;
    private final int vao;
    private final int vbo;
    private final FloatBuffer buffer;
    private int vertexCount = 0;

    // World-space drift accumulated since start; the whole pattern slides -X
    private final Vector2f drift = new Vector2f();
    /** Drift cell index the mesh was built against (floor(drift.x / CELL)). */
    private int builtDriftCellX = Integer.MIN_VALUE;
    /** Player cell the mesh was centred on. */
    private int builtCenterX = Integer.MIN_VALUE;
    private int builtCenterZ = Integer.MIN_VALUE;

    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        // Force a rebuild on the next frame so the grid resyncs with the drift
        builtDriftCellX = Integer.MIN_VALUE;
    }

    public boolean isEnabled() { return enabled; }

    public CloudLayer() {
        shader = new Shader("shaders/clouds.vert", "shaders/clouds.frag");

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER,
            (long) CELLS_PER_SIDE * CELLS_PER_SIDE * 6 * 6 * VERTEX_SIZE * Float.BYTES,
            GL_DYNAMIC_DRAW);

        int stride = VERTEX_SIZE * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);

        buffer = MemoryUtil.memAllocFloat(
            CELLS_PER_SIDE * CELLS_PER_SIDE * 6 * 6 * VERTEX_SIZE);
    }

    /**
     * Advance the drift and rebuild the grid when the player or the drifting
     * pattern crosses a cell boundary.
     */
    public void update(double deltaTime, Vector3f cameraPos) {
        drift.x += (float) (DRIFT_SPEED * deltaTime);

        int driftCellX = (int) Math.floor(drift.x / CELL_SIZE);
        int centerX = (int) Math.floor(cameraPos.x / CELL_SIZE);
        int centerZ = (int) Math.floor(cameraPos.z / CELL_SIZE);

        if (driftCellX != builtDriftCellX
            || centerX != builtCenterX
            || centerZ != builtCenterZ) {
            builtDriftCellX = driftCellX;
            builtCenterX = centerX;
            builtCenterZ = centerZ;
            rebuild();
        }
    }

    /**
     * Draw the deck. Must run after the opaque world pass; depth is tested so
     * terrain hides clouds, but depth writes stay off so the clouds blend.
     *
     * @param daylight 0..1 sun brightness: dims and blue-shifts the deck at
     *                 night so clouds don't glow white after sunset
     */
    public void render(Camera camera, float daylight) {
        if (!enabled || vertexCount == 0) return;

        shader.bind();
        shader.setUniformMat4("projection", camera.getProjectionMatrix());
        shader.setUniformMat4("view", camera.getViewMatrix());
        shader.setUniform3f("cameraPos", camera.getPosition());
        // Continuous sub-cell drift: the mesh is rebuilt at whole-cell steps,
        // this shifts it the remaining fraction so the motion never jumps
        float fracX = drift.x - builtDriftCellX * CELL_SIZE;
        shader.setUniform2f("drift", new Vector2f(fracX, 0.0f));
        shader.setUniform1f("alpha", CLOUD_ALPHA);
        // Night tint: dark blue-grey at midnight, pure white in full day
        float d = Math.max(0.0f, Math.min(1.0f, daylight));
        shader.setUniform3f("tint",
            new Vector3f(0.12f + 0.88f * d, 0.14f + 0.86f * d, 0.22f + 0.78f * d));

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);

        shader.unbind();

        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    // ------------------------------------------------------------------
    // Mesh building
    // ------------------------------------------------------------------

    /** Value noise sampled on the cell lattice (2D, smooth). */
    private static float valueNoise(float x, float z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        float fx = x - x0;
        float fz = z - z0;
        float ux = fx * fx * (3.0f - 2.0f * fx);
        float uz = fz * fz * (3.0f - 2.0f * fz);

        float a = hash(x0, z0);
        float b = hash(x0 + 1, z0);
        float c = hash(x0, z0 + 1);
        float d = hash(x0 + 1, z0 + 1);

        return lerp(lerp(a, b, ux), lerp(c, d, ux), uz);
    }

    private static float hash(int x, int z) {
        float n = (float) Math.sin(x * 127.1 + z * 311.7) * 43758.5453123f;
        return n - (float) Math.floor(n);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Two-octave value noise field, ~0..1. */
    private static float fbm(float x, float z) {
        return (valueNoise(x, z) + 0.5f * valueNoise(x * 2.03f + 7.31f, z * 2.03f - 3.17f)) / 1.5f;
    }

    /** A cell is either fully there or fully gone: hard threshold. */
    private boolean cellPresent(int cellX, int cellZ) {
        return fbm(cellX * 0.35f, cellZ * 0.35f) > NOISE_THRESHOLD;
    }

    /** Whether the neighbour at (+dx, +dz) exists (out of region = absent). */
    private boolean neighborPresent(int cellX, int cellZ, int dx, int dz) {
        int nx = cellX + dx;
        int nz = cellZ + dz;
        if (nx < -REGION_CELLS || nx > REGION_CELLS
            || nz < -REGION_CELLS || nz > REGION_CELLS) {
            return false;
        }
        return cellPresent(nx, nz);
    }

    private void rebuild() {
        buffer.clear();

        float yTop = CLOUD_Y;
        float yBot = CLOUD_Y - CLOUD_THICKNESS;

        for (int cz = -REGION_CELLS; cz <= REGION_CELLS; cz++) {
            for (int cx = -REGION_CELLS; cx <= REGION_CELLS; cx++) {
                // The drift shifts the pattern one cell west per whole-cell step
                if (!cellPresent(cx + builtDriftCellX, cz)) continue;

                float x0 = (builtCenterX + cx) * CELL_SIZE;
                float z0 = (builtCenterZ + cz) * CELL_SIZE;
                float x1 = x0 + CELL_SIZE;
                float z1 = z0 + CELL_SIZE;

                // Top: white
                topFace(x0, yTop, z0, x1, z1);
                // Bottom: slightly darkened grey underside
                bottomFace(x0, yBot, z0, x1, z1);

                // Sides: white, hidden where a neighbouring cell fills them
                if (!neighborPresent(cx, cz, 1, 0)) faceX1(x1, yBot, yTop, z0, z1);
                if (!neighborPresent(cx, cz, -1, 0)) faceX0(x0, yBot, yTop, z0, z1);
                if (!neighborPresent(cx, cz, 0, 1)) faceZ1(x0, x1, yBot, yTop, z1);
                if (!neighborPresent(cx, cz, 0, -1)) faceZ0(x0, x1, yBot, yTop, z0);
            }
        }

        buffer.flip();
        vertexCount = buffer.remaining() / VERTEX_SIZE;

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void vertex(float x, float y, float z, float r, float g, float b) {
        buffer.put(x).put(y).put(z).put(r).put(g).put(b);
    }

    private void quad(float[] p, float r, float g, float b) {
        for (int i = 0; i < p.length; i += 3) {
            vertex(p[i], p[i + 1], p[i + 2], r, g, b);
        }
    }

    private void topFace(float x0, float y, float z0, float x1, float z1) {
        quad(new float[] {
            x0, y, z1,  x1, y, z1,  x1, y, z0,
            x1, y, z0,  x0, y, z0,  x0, y, z1
        }, WHITE_R, WHITE_G, WHITE_B);
    }

    private void bottomFace(float x0, float y, float z0, float x1, float z1) {
        quad(new float[] {
            x0, y, z0,  x1, y, z0,  x1, y, z1,
            x1, y, z1,  x0, y, z1,  x0, y, z0
        }, BOTTOM_SHADE, BOTTOM_SHADE, BOTTOM_SHADE);
    }

    private void faceX1(float x, float y0, float y1, float z0, float z1) {
        quad(new float[] {
            x, y0, z0,  x, y1, z0,  x, y1, z1,
            x, y1, z1,  x, y0, z1,  x, y0, z0
        }, WHITE_R, WHITE_G, WHITE_B);
    }

    private void faceX0(float x, float y0, float y1, float z0, float z1) {
        quad(new float[] {
            x, y0, z1,  x, y1, z1,  x, y1, z0,
            x, y1, z0,  x, y0, z0,  x, y0, z1
        }, WHITE_R, WHITE_G, WHITE_B);
    }

    private void faceZ1(float x0, float x1, float y0, float y1, float z) {
        quad(new float[] {
            x1, y0, z,  x1, y1, z,  x0, y1, z,
            x0, y1, z,  x0, y0, z,  x1, y0, z
        }, WHITE_R, WHITE_G, WHITE_B);
    }

    private void faceZ0(float x0, float x1, float y0, float y1, float z) {
        quad(new float[] {
            x0, y0, z,  x0, y1, z,  x1, y1, z,
            x1, y1, z,  x1, y0, z,  x0, y0, z
        }, WHITE_R, WHITE_G, WHITE_B);
    }

    public void cleanup() {
        MemoryUtil.memFree(buffer);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        shader.cleanup();
    }
}
