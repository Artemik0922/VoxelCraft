package com.voxelgame.rendering.model;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * Procedurally generated 64x64 character skin.
 *
 * Laid out like the classic skin sheet so one texture serves both the
 * first-person arm and the third-person body:
 *
 *   head   at (0,0),  8x8 faces
 *   body   at (16,16) 8x12 front/back, 4x12 sides
 *   arm    at (40,16) 4x12
 *   leg    at (0,16)  4x12
 *
 * Nothing is copied from an existing skin - every pixel is drawn here from
 * a small palette with a little per-pixel noise for fabric texture.
 */
public class SkinTexture {

    public static final int SIZE = 64;

    // Palette
    private static final int SKIN      = 0xFFE0AC7A;
    private static final int SKIN_DARK = 0xFFC98F5E;
    private static final int HAIR      = 0xFF4A3220;
    private static final int HAIR_LIT  = 0xFF5C4029;
    private static final int EYE_WHITE = 0xFFF2F2F2;
    private static final int EYE_IRIS  = 0xFF3A5DA8;
    private static final int MOUTH     = 0xFF8C5540;
    private static final int SHIRT     = 0xFF3F7FBF;
    private static final int SHIRT_DK  = 0xFF33689C;
    private static final int PANTS     = 0xFF3A4054;
    private static final int PANTS_DK  = 0xFF2E3343;
    private static final int SHOE      = 0xFF33291F;

    private final int[] pixels = new int[SIZE * SIZE];
    private final int textureId;

    public SkinTexture() {
        drawHead();
        drawBody();
        drawArm();
        drawLeg();
        textureId = upload();
    }

    public int getTextureId() { return textureId; }

    // ------------------------------------------------------------------
    // Regions. Each limb is an unwrapped box: right, front, left, back
    // across the top row, with top and bottom caps above it.
    // ------------------------------------------------------------------

    private void drawHead() {
        // caps
        fillNoisy(8, 0, 8, 8, HAIR, HAIR_LIT);      // top - hair
        fillNoisy(16, 0, 8, 8, SKIN_DARK, SKIN);    // bottom - jaw

        // right, front, left, back
        face(0, 8, 8, 8, true);
        face(8, 8, 8, 8, false);   // front carries the face
        face(16, 8, 8, 8, true);
        face(24, 8, 8, 8, true);
    }

    /** One head side; the front variant gets eyes and a mouth. */
    private void face(int x, int y, int w, int h, boolean plain) {
        fillNoisy(x, y, w, h, SKIN, SKIN_DARK);

        // Hair covers the top rows and runs down the sides
        for (int py = 0; py < 3; py++) {
            for (int px = 0; px < w; px++) {
                set(x + px, y + py, ((px + py) % 5 == 0) ? HAIR_LIT : HAIR);
            }
        }
        set(x, y + 3, HAIR);
        set(x + w - 1, y + 3, HAIR);

        if (plain) return;

        // Eyes at row 4, two pixels wide each
        set(x + 2, y + 4, EYE_WHITE);
        set(x + 3, y + 4, EYE_IRIS);
        set(x + 5, y + 4, EYE_IRIS);
        set(x + 6, y + 4, EYE_WHITE);

        // Mouth
        set(x + 3, y + 6, MOUTH);
        set(x + 4, y + 6, MOUTH);
        set(x + 5, y + 6, MOUTH);
    }

    private void drawBody() {
        // top/bottom caps
        fillNoisy(20, 16, 8, 4, SHIRT, SHIRT_DK);
        fillNoisy(28, 16, 8, 4, PANTS, PANTS_DK);

        // right (4 wide), front (8), left (4), back (8)
        torsoPanel(16, 20, 4, 12);
        torsoPanel(20, 20, 8, 12);
        torsoPanel(28, 20, 4, 12);
        torsoPanel(32, 20, 8, 12);
    }

    /** Shirt on top, belt line, trousers below. */
    private void torsoPanel(int x, int y, int w, int h) {
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int c;
                if (py < 8) {
                    c = ((px + py) % 4 == 0) ? SHIRT_DK : SHIRT;
                } else if (py == 8) {
                    c = 0xFF2A2A2A;                      // belt
                } else {
                    c = ((px + py) % 4 == 0) ? PANTS_DK : PANTS;
                }
                set(x + px, y + py, c);
            }
        }
    }

    private void drawArm() {
        fillNoisy(44, 16, 4, 4, SHIRT, SHIRT_DK);   // shoulder cap
        fillNoisy(48, 16, 4, 4, SKIN, SKIN_DARK);   // hand cap

        for (int side = 0; side < 4; side++) {
            armPanel(40 + side * 4, 20, 4, 12);
        }
    }

    /** Sleeve for the upper two thirds, bare hand below. */
    private void armPanel(int x, int y, int w, int h) {
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int c;
                if (py < 8) {
                    c = ((px + py) % 4 == 0) ? SHIRT_DK : SHIRT;
                } else {
                    c = ((px * 3 + py) % 5 == 0) ? SKIN_DARK : SKIN;
                }
                set(x + px, y + py, c);
            }
        }
    }

    private void drawLeg() {
        fillNoisy(4, 16, 4, 4, PANTS, PANTS_DK);
        fillNoisy(8, 16, 4, 4, SHOE, SHOE);

        for (int side = 0; side < 4; side++) {
            legPanel(side * 4, 20, 4, 12);
        }
    }

    private void legPanel(int x, int y, int w, int h) {
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int c;
                if (py < 9) {
                    c = ((px + py) % 4 == 0) ? PANTS_DK : PANTS;
                } else {
                    c = SHOE;
                }
                set(x + px, y + py, c);
            }
        }
    }

    // ------------------------------------------------------------------

    private void fillNoisy(int x, int y, int w, int h, int a, int b) {
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                set(x + px, y + py, ((px + py) % 3 == 0) ? b : a);
            }
        }
    }

    private void set(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
        pixels[y * SIZE + x] = argb;
    }

    private int upload() {
        ByteBuffer buf = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        // Flip vertically: OpenGL's first row is the bottom one
        for (int y = SIZE - 1; y >= 0; y--) {
            for (int x = 0; x < SIZE; x++) {
                int p = pixels[y * SIZE + x];
                buf.put((byte) ((p >> 16) & 0xFF));
                buf.put((byte) ((p >> 8) & 0xFF));
                buf.put((byte) (p & 0xFF));
                buf.put((byte) ((p >>> 24) & 0xFF));
            }
        }
        buf.flip();

        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, SIZE, SIZE, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        MemoryUtil.memFree(buf);
        return id;
    }

    public void cleanup() {
        glDeleteTextures(textureId);
    }
}
