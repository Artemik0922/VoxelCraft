package com.voxelgame.rendering;

import com.voxelgame.world.BlockType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Camera-facing block particles for digging and breaking.
 *
 * Particles live in a fixed-size pool and are rebuilt into one streaming
 * buffer each frame, so nothing is allocated while the game is running.
 * Each one samples a small window of its source block's texture, which is
 * what makes the debris read as chips of that material.
 */
public class ParticleSystem {

    private static final int MAX_PARTICLES = 2048;
    private static final float GRAVITY = -14.0f;
    /** floats per vertex: x, y, z, u, v, layer, shade */
    private static final int VERTEX_SIZE = 7;
    private static final int VERTS_PER_PARTICLE = 6;

    private static final class Particle {
        boolean alive;
        float x, y, z;
        float vx, vy, vz;
        float life, maxLife;
        float size;
        int layer;
        float u, v;      // texture window origin, 0..1
        float shade;
    }

    private final Particle[] pool = new Particle[MAX_PARTICLES];
    private int aliveCount = 0;
    /** Stack of free particle indices for O(1) allocation. */
    private final int[] freeStack = new int[MAX_PARTICLES];
    private int freeTop = MAX_PARTICLES;

    private final Shader shader;
    private final int vao;
    private final int vbo;
    private final FloatBuffer buffer;

    /** Block atlas for debris particles — set by Game after init. */
    public TextureAtlas blockAtlas;

    private final java.util.Random random = new java.util.Random();

    public ParticleSystem() {
        for (int i = 0; i < pool.length; i++) {
            pool[i] = new Particle();
            freeStack[i] = i; // All indices start as free
        }

        shader = new Shader("shaders/particle.vert", "shaders/particle.frag");

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER,
            (long) MAX_PARTICLES * VERTS_PER_PARTICLE * VERTEX_SIZE * Float.BYTES,
            GL_STREAM_DRAW);

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

        buffer = MemoryUtil.memAllocFloat(
            MAX_PARTICLES * VERTS_PER_PARTICLE * VERTEX_SIZE);
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    /**
     * Burst thrown out when a block is destroyed.
     */
    public void emitBlockBreak(int bx, int by, int bz, BlockType type, TextureAtlas atlas) {
        int layer = atlas.getSlot(type.id, 2, bx, by, bz);
        // 4x4 grid of chips, as the original does
        for (int i = 0; i < 28; i++) {
            spawn(bx + random.nextFloat(), by + random.nextFloat(), bz + random.nextFloat(),
                (random.nextFloat() - 0.5f) * 3.5f,
                random.nextFloat() * 3.5f + 1.0f,
                (random.nextFloat() - 0.5f) * 3.5f,
                layer, 0.10f + random.nextFloat() * 0.06f,
                0.6f + random.nextFloat() * 0.4f);
        }
    }

    /**
     * Hit particles when attacking a mob.
     */
    /**
     * [ANM] Heart particles rising from a pair after breeding.
     */
    public void emitHearts(float x, float y, float z) {
        int layer = blockAtlas == null ? -1 : blockAtlas.getSlotByName("red_concrete");
        for (int i = 0; i < 8; i++) {
            spawn(x + (random.nextFloat() - 0.5f) * 0.8f,
                y + 0.8f + random.nextFloat() * 0.6f,
                z + (random.nextFloat() - 0.5f) * 0.8f,
                (random.nextFloat() - 0.5f) * 0.4f,
                1.4f + random.nextFloat() * 1.2f,
                (random.nextFloat() - 0.5f) * 0.4f,
                layer, 0.12f + random.nextFloat() * 0.06f,
                0.9f + random.nextFloat() * 0.5f);
        }
    }

    public void emitHit(float x, float y, float z) {
        for (int i = 0; i < 6; i++) {
            spawn(x, y, z,
                (random.nextFloat() - 0.5f) * 2.0f,
                random.nextFloat() * 2.0f,
                (random.nextFloat() - 0.5f) * 2.0f,
                -1, 0.1f, 0.3f); // -1 layer = white particle
        }
    }

    /**
     * Small trickle while a block is being mined, thrown from the hit face.
     */
    public void emitDigging(int bx, int by, int bz, BlockType type, TextureAtlas atlas) {
        int layer = atlas.getSlot(type.id, 2, bx, by, bz);
        for (int i = 0; i < 2; i++) {
            spawn(bx + random.nextFloat(), by + random.nextFloat(), bz + random.nextFloat(),
                (random.nextFloat() - 0.5f) * 1.6f,
                random.nextFloat() * 1.8f,
                (random.nextFloat() - 0.5f) * 1.6f,
                layer, 0.07f + random.nextFloat() * 0.04f,
                0.35f + random.nextFloat() * 0.25f);
        }
    }

    /**
     * [GP-073] Rising embers and smoke for a burning cell. Flames shoot
     * upward with a sideways drift, flicker via the life curve.
     */
    public void emitFlame(float x, float y, float z) {
        // Embers: hot orange pixels that float up and out
        for (int i = 0; i < 2; i++) {
            spawn(x + (random.nextFloat() - 0.5f) * 0.5f,
                y + 0.2f + random.nextFloat() * 0.4f,
                z + (random.nextFloat() - 0.5f) * 0.5f,
                (random.nextFloat() - 0.5f) * 0.6f,
                1.2f + random.nextFloat() * 1.4f,
                (random.nextFloat() - 0.5f) * 0.6f,
                -1, 0.10f + random.nextFloat() * 0.08f,
                0.4f + random.nextFloat() * 0.4f);
        }
        // Smoke: a darker, slower particle that lingers
        if (random.nextFloat() < 0.35f) {
            spawn(x + (random.nextFloat() - 0.5f) * 0.4f,
                y + 0.5f + random.nextFloat() * 0.3f,
                z + (random.nextFloat() - 0.5f) * 0.4f,
                (random.nextFloat() - 0.5f) * 0.4f,
                0.9f + random.nextFloat() * 0.6f,
                (random.nextFloat() - 0.5f) * 0.4f,
                -1, 0.16f + random.nextFloat() * 0.10f,
                0.8f + random.nextFloat() * 0.6f);
        }
    }

    // ------------------------------------------------------------------
    // Asteroid particles
    // ------------------------------------------------------------------

    /**
     * [AST] Fiery trail behind a falling asteroid: a smoke puff plus
     * glowing embers that drift up past the rock.
     */
    public void emitMeteorTrail(float x, float y, float z) {
        // Smoke: dark, slow, lingers
        for (int i = 0; i < 2; i++) {
            spawn(x + (random.nextFloat() - 0.5f) * 1.5f,
                y + (random.nextFloat() - 0.5f) * 1.5f,
                z + (random.nextFloat() - 0.5f) * 1.5f,
                (random.nextFloat() - 0.5f) * 2.0f,
                0.5f + random.nextFloat() * 1.5f,
                (random.nextFloat() - 0.5f) * 2.0f,
                -1, 0.18f + random.nextFloat() * 0.12f,
                0.9f + random.nextFloat() * 0.6f);
        }
        // Embers: hot sparks that flicker briefly
        if (random.nextFloat() < 0.6f) {
            spawn(x + (random.nextFloat() - 0.5f) * 1.0f,
                y + (random.nextFloat() - 0.5f) * 1.0f,
                z + (random.nextFloat() - 0.5f) * 1.0f,
                (random.nextFloat() - 0.5f) * 3.0f,
                1.5f + random.nextFloat() * 2.0f,
                (random.nextFloat() - 0.5f) * 3.0f,
                -1, 0.08f + random.nextFloat() * 0.06f,
                0.4f + random.nextFloat() * 0.3f);
        }
    }

    /**
     * [AST] Impact burst: stone debris, a white flash core and a smoke plume.
     */
    public void emitImpact(float x, float y, float z, int size) {
        // Chunks of stone blasted outward
        if (blockAtlas != null) {
            int layer = blockAtlas.getSlot(BlockType.STONE.id, 2, (int) x, (int) y, (int) z);
            int debris = 20 + size * 12;
            for (int i = 0; i < debris; i++) {
                spawn(x + (random.nextFloat() - 0.5f) * 2.0f,
                    y + (random.nextFloat() - 0.5f) * 2.0f,
                    z + (random.nextFloat() - 0.5f) * 2.0f,
                    (random.nextFloat() - 0.5f) * 14.0f,
                    2.0f + random.nextFloat() * 12.0f,
                    (random.nextFloat() - 0.5f) * 14.0f,
                    layer, 0.12f + random.nextFloat() * 0.08f,
                    0.7f + random.nextFloat() * 0.5f);
            }
        }
        // White flash core
        for (int i = 0; i < 30; i++) {
            spawn(x + (random.nextFloat() - 0.5f) * 3.0f,
                y + 0.5f + (random.nextFloat() - 0.5f) * 3.0f,
                z + (random.nextFloat() - 0.5f) * 3.0f,
                (random.nextFloat() - 0.5f) * 8.0f,
                random.nextFloat() * 8.0f,
                (random.nextFloat() - 0.5f) * 8.0f,
                -1, 0.10f + random.nextFloat() * 0.08f,
                0.4f + random.nextFloat() * 0.3f);
        }
        // Smoke plume
        for (int i = 0; i < 14; i++) {
            spawn(x + (random.nextFloat() - 0.5f) * size * 2.0f,
                y + random.nextFloat() * 2.0f,
                z + (random.nextFloat() - 0.5f) * size * 2.0f,
                (random.nextFloat() - 0.5f) * 1.5f,
                2.0f + random.nextFloat() * 3.0f,
                (random.nextFloat() - 0.5f) * 1.5f,
                -1, 0.25f + random.nextFloat() * 0.15f,
                1.2f + random.nextFloat() * 0.8f);
        }
    }

    // ------------------------------------------------------------------
    // Weather particles
    // ------------------------------------------------------------------

    /**
     * Spawn rain drops falling around the player.
     * Each drop is a small white/blue particle that falls quickly.
     */
    public void emitRain(Vector3f playerPos, int count) {
        for (int i = 0; i < count; i++) {
            float x = playerPos.x + (random.nextFloat() - 0.5f) * 32.0f;
            float z = playerPos.z + (random.nextFloat() - 0.5f) * 32.0f;
            float y = playerPos.y + 15.0f + random.nextFloat() * 10.0f;
            // Rain falls fast with slight wind
            spawn(x, y, z,
                -0.5f + random.nextFloat() * 0.2f,  // slight wind
                -12.0f - random.nextFloat() * 4.0f,  // fast fall
                -0.2f + random.nextFloat() * 0.1f,
                -1, 0.04f + random.nextFloat() * 0.02f,  // small size
                1.5f + random.nextFloat() * 0.5f);   // short life
        }
    }

    /**
     * Spawn snow flakes drifting slowly around the player.
     */
    public void emitSnow(Vector3f playerPos, int count) {
        for (int i = 0; i < count; i++) {
            float x = playerPos.x + (random.nextFloat() - 0.5f) * 32.0f;
            float z = playerPos.z + (random.nextFloat() - 0.5f) * 32.0f;
            float y = playerPos.y + 15.0f + random.nextFloat() * 10.0f;
            // Snow drifts slowly with wind
            spawn(x, y, z,
                (random.nextFloat() - 0.5f) * 1.5f,  // drift
                -1.5f - random.nextFloat() * 1.0f,    // slow fall
                (random.nextFloat() - 0.5f) * 1.5f,   // drift
                -1, 0.06f + random.nextFloat() * 0.04f, // slightly larger
                4.0f + random.nextFloat() * 2.0f);    // longer life
        }
    }

    /**
     * Rain splash on the ground.
     */
    public void emitRainSplash(float x, float y, float z) {
        for (int i = 0; i < 3; i++) {
            spawn(x, y + 0.1f, z,
                (random.nextFloat() - 0.5f) * 2.0f,
                random.nextFloat() * 1.5f,
                (random.nextFloat() - 0.5f) * 2.0f,
                -1, 0.03f, 0.3f + random.nextFloat() * 0.2f);
        }
    }

    private void spawn(float x, float y, float z, float vx, float vy, float vz,
                       int layer, float size, float life) {
        Particle p = findFree();
        if (p == null) return;

        p.alive = true;
        p.x = x; p.y = y; p.z = z;
        p.vx = vx; p.vy = vy; p.vz = vz;
        p.life = life;
        p.maxLife = life;
        p.size = size;
        p.layer = layer;

        // Pick a random 4x4-texel window of the source tile
        p.u = random.nextInt(4) / 4.0f;
        p.v = random.nextInt(4) / 4.0f;
        p.shade = 0.75f + random.nextFloat() * 0.25f;

        aliveCount++;
    }

    private Particle findFree() {
        if (freeTop == 0) return null;
        int idx = freeStack[--freeTop];
        return pool[idx];
    }

    // ------------------------------------------------------------------

    public void update(double deltaTime) {
        float dt = (float) deltaTime;

        for (int i = 0; i < pool.length; i++) {
            Particle p = pool[i];
            if (!p.alive) continue;

            p.life -= dt;
            if (p.life <= 0) {
                p.alive = false;
                aliveCount--;
                // Return index to free stack
                freeStack[freeTop++] = i;
                continue;
            }

            p.vy += GRAVITY * dt;

            // Air drag so chips settle instead of flying forever
            p.vx *= (1.0f - 2.4f * dt);
            p.vz *= (1.0f - 2.4f * dt);

            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
        }
    }

    /**
     * Draw all live particles as camera-facing quads.
     */
    public void render(Camera camera, TextureAtlas atlas, float daylight) {
        if (aliveCount == 0) return;

        // Billboard basis from the camera
        Vector3f right = camera.getRight();
        Vector3f up = camera.getUp();

        buffer.clear();
        int verts = 0;

        for (Particle p : pool) {
            if (!p.alive) continue;

            // Shrink as they expire
            float t = p.life / p.maxLife;
            float s = p.size * (0.55f + 0.45f * t);

            float rx = right.x * s, ry = right.y * s, rz = right.z * s;
            float ux = up.x * s, uy = up.y * s, uz = up.z * s;

            float shade = p.shade * (0.25f + 0.75f * daylight);

            // Quarter-tile UV window
            float u0 = p.u, v0 = p.v;
            float u1 = p.u + 0.25f, v1 = p.v + 0.25f;

            verts += quad(p, rx, ry, rz, ux, uy, uz, u0, v0, u1, v1, shade);
        }

        if (verts == 0) return;

        buffer.flip();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
        glDepthMask(false);

        shader.bind();
        shader.setUniformMat4("projection", camera.getProjectionMatrix());
        shader.setUniformMat4("view", camera.getViewMatrix());
        shader.setUniform1i("blockTextures", 0);

        glActiveTexture(GL_TEXTURE0);
        atlas.bindArray();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
        glDrawArrays(GL_TRIANGLES, 0, verts);
        glBindVertexArray(0);

        shader.unbind();

        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
    }

    private int quad(Particle p,
                     float rx, float ry, float rz, float ux, float uy, float uz,
                     float u0, float v0, float u1, float v1, float shade) {
        // bottom-left, bottom-right, top-right / bottom-left, top-right, top-left
        vertex(p.x - rx - ux, p.y - ry - uy, p.z - rz - uz, u0, v1, p.layer, shade);
        vertex(p.x + rx - ux, p.y + ry - uy, p.z + rz - uz, u1, v1, p.layer, shade);
        vertex(p.x + rx + ux, p.y + ry + uy, p.z + rz + uz, u1, v0, p.layer, shade);

        vertex(p.x - rx - ux, p.y - ry - uy, p.z - rz - uz, u0, v1, p.layer, shade);
        vertex(p.x + rx + ux, p.y + ry + uy, p.z + rz + uz, u1, v0, p.layer, shade);
        vertex(p.x - rx + ux, p.y - ry + uy, p.z - rz + uz, u0, v0, p.layer, shade);
        return 6;
    }

    private void vertex(float x, float y, float z, float u, float v, int layer, float shade) {
        buffer.put(x).put(y).put(z).put(u).put(v).put(layer).put(shade);
    }

    public int getAliveCount() { return aliveCount; }

    public void cleanup() {
        MemoryUtil.memFree(buffer);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        shader.cleanup();
    }
}
