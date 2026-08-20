package com.voxelgame.rendering.model;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * A textured box in the classic model format: 1 pixel equals 1/16 of a
 * block, and the six faces are unwrapped onto the skin sheet as
 *
 *      [top ][bot ]
 *   [rt][frt][lft][bck]
 *
 * with the row of sides starting at (u, v + depth).
 *
 * Geometry is baked once into a VBO. Placement, rotation and pivoting are
 * left to the caller's model matrix, so a limb can rotate about its
 * shoulder without rebuilding anything.
 */
public class ModelBox {

    /** floats per vertex: x, y, z, u, v, shade */
    private static final int VERTEX_SIZE = 6;
    private static final int VERTEX_COUNT = 36;

    private final int vao;
    private final int vbo;

    /**
     * @param u,v      top-left of this box's region on the skin sheet
     * @param w,h,d    size in skin pixels
     * @param originX  offset from the pivot, in skin pixels
     */
    public ModelBox(int u, int v, int w, int h, int d,
                    float originX, float originY, float originZ) {
        this(u, v, w, h, d, originX, originY, originZ, 0.0f, false);
    }

    /**
     * @param inflate grow the box slightly on every axis, used for hat and
     *                jacket overlays so they do not z-fight the base layer
     */
    public ModelBox(int u, int v, int w, int h, int d,
                    float originX, float originY, float originZ, float inflate) {
        this(u, v, w, h, d, originX, originY, originZ, inflate, false);
    }

    /**
     * @param flipV    flip the texture vertically on this box. Model space is
     *                 Y down (head crown at minY, feet at maxY), but the skin
     *                 sheet is drawn with the top of each region pointing up.
     *                 Boxes defined in Y-down space must set flipV=true so the
     *                 forehead lands on the crown and the chin on the neck;
     *                 boxes defined in Y-up space (e.g. first-person view
     *                 model) keep flipV=false.
     */
    public ModelBox(int u, int v, int w, int h, int d,
                    float originX, float originY, float originZ,
                    float inflate, boolean flipV) {

        final float P = 1.0f / 16.0f;   // one skin pixel in world units
        final float T = 1.0f / SkinTexture.SIZE;

        float x0 = originX * P - inflate * P;
        float y0 = originY * P - inflate * P;
        float z0 = originZ * P - inflate * P;
        float x1 = (originX + w) * P + inflate * P;
        float y1 = (originY + h) * P + inflate * P;
        float z1 = (originZ + d) * P + inflate * P;

        // Skin sheet coordinates for each face
        float uRight = u * T,            uFront = (u + d) * T;
        float uLeft  = (u + d + w) * T,  uBack  = (u + d + w + d) * T;
        float uEnd   = (u + d + w + d + w) * T;

        float vTop   = v * T;
        float vSides = (v + d) * T;
        float vEnd   = (v + d + h) * T;

        float uTopA = (u + d) * T,       uTopB = (u + d + w) * T;
        float uBotA = (u + d + w) * T,   uBotB = (u + d + w + w) * T;

        // Y-down space: the sheet's "top" region (crown) belongs on the
        // minY face, which is the crown in the model; flipV swaps the v
        // direction on the sides and the crown/underside regions.
        float vLo = flipV ? vSides : vEnd;   // v at minY
        float vHi = flipV ? vEnd : vSides;   // v at maxY
        float uMaxY_A = flipV ? uBotA : uTopA;   // region on maxY face
        float uMaxY_B = flipV ? uBotB : uTopB;
        float uMinY_A = flipV ? uTopA : uBotA;   // region on minY face
        float uMinY_B = flipV ? uTopB : uBotB;

        FloatBuffer buf = MemoryUtil.memAllocFloat(VERTEX_COUNT * VERTEX_SIZE);

        // Per-face shading, matching how block faces are lit
        final float SH_TOP = 1.0f, SH_BOTTOM = 0.55f;
        final float SH_NS = 0.80f, SH_EW = 0.68f;

        // front (-Z)
        quad(buf, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0,
             uFront, vLo, uLeft, vHi, SH_NS);
        // back (+Z)
        quad(buf, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1,
             uBack, vLo, uEnd, vHi, SH_NS);
        // right (-X)
        quad(buf, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1,
             uRight, vLo, uFront, vHi, SH_EW);
        // left (+X)
        quad(buf, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0,
             uLeft, vLo, uBack, vHi, SH_EW);
        // top (+Y = maxY face, underside in Y-down model space)
        quad(buf, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1,
             uMaxY_A, vTop, uMaxY_B, vSides, SH_TOP);
        // bottom (-Y = minY face, the crown in Y-down model space)
        quad(buf, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0,
             uMinY_A, vSides, uMinY_B, vTop, SH_BOTTOM);

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

        glBindVertexArray(0);
        MemoryUtil.memFree(buf);
    }

    /**
     * Emit one face as two triangles, counter-clockwise seen from outside.
     */
    private void quad(FloatBuffer b,
                      float ax, float ay, float az,
                      float bx, float by, float bz,
                      float cx, float cy, float cz,
                      float dx, float dy, float dz,
                      float u0, float v0, float u1, float v1, float shade) {
        // Corners are listed clockwise when seen from outside, so they are
        // emitted in reverse to produce the counter-clockwise winding that
        // glFrontFace(GL_CCW) expects. Emitting them in order made every
        // face inside-out, and the whole box was culled away.
        vertex(b, ax, ay, az, u0, v0, shade);
        vertex(b, cx, cy, cz, u1, v1, shade);
        vertex(b, bx, by, bz, u1, v0, shade);

        vertex(b, ax, ay, az, u0, v0, shade);
        vertex(b, dx, dy, dz, u0, v1, shade);
        vertex(b, cx, cy, cz, u1, v1, shade);
    }

    private void vertex(FloatBuffer b, float x, float y, float z,
                        float u, float v, float shade) {
        b.put(x).put(y).put(z).put(u).put(v).put(shade);
    }

    public void render() {
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, VERTEX_COUNT);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
