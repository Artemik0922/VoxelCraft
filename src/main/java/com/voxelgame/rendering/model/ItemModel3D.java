package com.voxelgame.rendering.model;

import com.voxelgame.rendering.Shader;
import com.voxelgame.rendering.TextureAtlas;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * A chunky 3D item model built from the tile's 16x16 sprite: every opaque
 * texel becomes a small box in a thin slab along Z, so tools, food and the
 * like get real depth in first and third person instead of a flat sticker.
 * Built once per atlas layer and cached.
 *
 * Reuses the held-block shader pipeline (attributes pos/uv/shade/face plus
 * the faceLayers uniform), where every face points at the item's own atlas
 * layer and the per-texel UV sub-rects stay inside the tile, giving each
 * box face its pixel colour.
 */
public class ItemModel3D {

    /** Slab thickness: vanilla extrudes the sprite exactly 1 texel deep. */
    private static final float DEPTH = 1.0f / 16.0f;

    /** floats per vertex: x, y, z, u, v, shade, face */
    private static final int VERTEX_SIZE = 7;
    /** Largest possible vertex count: every texel emitting all six faces. */
    private static final int MAX_VERTICES = 16 * 16 * 6 * 6;

    private static final Map<Integer, ItemModel3D> CACHE = new HashMap<>();

    private final int vao;
    private final int vbo;
    private final int vertexCount;
    private final int layer;

    private ItemModel3D(TextureAtlas atlas, int layer) {
        this.layer = layer;
        int[] px = atlas.getLayerPixels(layer);

        List<Float> verts = new ArrayList<>();
        if (px != null) {
            build(px, verts);
        }
        vertexCount = verts.size() / VERTEX_SIZE;

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        FloatBuffer buf = MemoryUtil.memAllocFloat(Math.max(1, verts.size()));
        for (float f : verts) buf.put(f);
        buf.flip();

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

    // ------------------------------------------------------------------
    // Geometry: voxelise the sprite into a thin slab.
    // ------------------------------------------------------------------

    private void build(int[] pixels, List<Float> out) {
        int n = 16;
        boolean[][] solid = new boolean[n][n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                solid[y][x] = ((pixels[y * n + x] >>> 24) & 0xFF) >= 128;
            }
        }

        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                if (!solid[py][px]) continue;

                float xL = px / 16.0f - 0.5f;
                float xR = (px + 1) / 16.0f - 0.5f;
                float yB = 0.5f - (py + 1) / 16.0f;
                float yT = 0.5f - py / 16.0f;
                float zB = -DEPTH / 2;
                float zF = DEPTH / 2;

                float u0 = px / 16.0f;
                float u1 = (px + 1) / 16.0f;
                float v0 = (15 - py) / 16.0f;
                float v1 = (16 - py) / 16.0f;

                boolean up = py > 0 && solid[py - 1][px];
                boolean down = py < 15 && solid[py + 1][px];
                boolean left = px > 0 && solid[py][px - 1];
                boolean right = px < 15 && solid[py][px + 1];

                quad(out, xL, yB, zF, xR, yT, zF, u0, u1, v0, v1, 1.00f); // +Z
                quadBack(out, xL, yB, zB, xR, yT, zB, u0, u1, v0, v1, 0.85f); // -Z
                if (!up)    quadTop(out, xL, xR, yT, zB, zF, u0, u1, v0, v1, 1.00f);
                if (!down)  quadBottom(out, xL, xR, yB, zB, zF, u0, u1, v0, v1, 0.60f);
                if (!left)  quadLeft(out, xL, yB, yT, zB, zF, u0, u1, v0, v1, 0.70f);
                if (!right) quadRight(out, xR, yB, yT, zB, zF, u0, u1, v0, v1, 0.85f);
            }
        }
    }

    /**
     * Front/back faces: the whole pixel area, wound CCW from outside so the
     * normal points along +Z (front) or -Z (back).
     */
    private void quad(List<Float> out, float xL, float yB, float z,
                      float xR, float yT, float z2,
                      float u0, float u1, float v0, float v1, float shade) {
        // a=(xL,yB,z) b=(xR,yB,z) c=(xR,yT,z) d=(xL,yT,z), normal +Z
        tri(out, xL, yB, z, xR, yB, z, xR, yT, z, u0, v0, u1, v0, u1, v1, shade);
        tri(out, xL, yB, z, xR, yT, z, xL, yT, z, u0, v0, u1, v1, u0, v1, shade);
    }

    /** Back face wound so the normal points along -Z (seen from behind). */
    private void quadBack(List<Float> out, float xL, float yB, float z,
                          float xR, float yT, float z2,
                          float u0, float u1, float v0, float v1, float shade) {
        // a=(xL,yB,z) b=(xL,yT,z) c=(xR,yT,z) d=(xR,yB,z)
        tri(out, xL, yB, z, xL, yT, z, xR, yT, z, u0, v0, u0, v1, u1, v1, shade);
        tri(out, xL, yB, z, xR, yT, z, xR, yB, z, u0, v0, u1, v1, u1, v0, shade);
    }

    private void quadTop(List<Float> out, float xL, float xR, float y,
                         float zB, float zF,
                         float u0, float u1, float v0, float v1, float shade) {
        tri(out, xL, y, zF, xR, y, zF, xR, y, zB, u0, v0, u1, v0, u1, v1, shade);
        tri(out, xL, y, zF, xR, y, zB, xL, y, zB, u0, v0, u1, v1, u0, v1, shade);
    }

    private void quadBottom(List<Float> out, float xL, float xR, float y,
                            float zB, float zF,
                            float u0, float u1, float v0, float v1, float shade) {
        tri(out, xL, y, zB, xR, y, zB, xR, y, zF, u0, v0, u1, v0, u1, v1, shade);
        tri(out, xL, y, zB, xR, y, zF, xL, y, zF, u0, v0, u1, v1, u0, v1, shade);
    }

    private void quadRight(List<Float> out, float x, float yB, float yT,
                           float zB, float zF,
                           float u0, float u1, float v0, float v1, float shade) {
        tri(out, x, yT, zF, x, yB, zF, x, yB, zB, u0, v1, u0, v0, u1, v0, shade);
        tri(out, x, yT, zF, x, yB, zB, x, yT, zB, u0, v1, u1, v0, u1, v1, shade);
    }

    private void quadLeft(List<Float> out, float x, float yB, float yT,
                          float zB, float zF,
                          float u0, float u1, float v0, float v1, float shade) {
        tri(out, x, yT, zB, x, yB, zB, x, yB, zF, u0, v1, u0, v0, u1, v0, shade);
        tri(out, x, yT, zB, x, yB, zF, x, yT, zF, u0, v1, u1, v0, u1, v1, shade);
    }

    private void tri(List<Float> out,
                     float ax, float ay, float az,
                     float bx, float by, float bz,
                     float cx, float cy, float cz,
                     float au, float av, float bu, float bv, float cu, float cv,
                     float shade) {
        out.add(ax); out.add(ay); out.add(az); out.add(au); out.add(av); out.add(shade); out.add(0.0f);
        out.add(bx); out.add(by); out.add(bz); out.add(bu); out.add(bv); out.add(shade); out.add(0.0f);
        out.add(cx); out.add(cy); out.add(cz); out.add(cu); out.add(cv); out.add(shade); out.add(0.0f);
    }

    // ------------------------------------------------------------------
    // Render + lifecycle
    // ------------------------------------------------------------------

    /** Draw the voxel model with every face pointing at its atlas layer. */
    public void render(Shader shader) {
        if (vertexCount == 0) return;
        int[] layers = new int[6];
        Arrays.fill(layers, layer);
        shader.setUniform1iv("faceLayers", layers);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    /** Cached per-atlas-layer model, shared by every renderer. */
    public static ItemModel3D get(TextureAtlas atlas, int layer) {
        if (layer < 0) return null;
        return CACHE.computeIfAbsent(layer, l -> new ItemModel3D(atlas, l));
    }

    public int getVertexCount() { return vertexCount; }

    public static void cleanupAll() {
        for (ItemModel3D m : CACHE.values()) m.cleanup();
        CACHE.clear();
    }

    public void cleanup() {
        if (vbo != 0) glDeleteBuffers(vbo);
        if (vao != 0) glDeleteVertexArrays(vao);
    }
}