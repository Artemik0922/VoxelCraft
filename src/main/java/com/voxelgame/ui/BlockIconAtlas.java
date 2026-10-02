package com.voxelgame.ui;

import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.world.BlockType;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * Vanilla-style isometric 3D icons for cube blocks: top rhombus plus two
 * shaded side faces, rasterised on the CPU from the atlas tiles on first
 * use and served from a single GL texture. Sprite-only types (tools, food)
 * and non-cubes (plants, doors) stay flat sprites.
 */
public final class BlockIconAtlas {

    private static final int ICON = 32;   // pixels per icon, like vanilla
    private static final int COLS = 16;   // icons per atlas row

    private static BlockIconAtlas instance;

    public static synchronized BlockIconAtlas get(TextureAtlas atlas) {
        if (instance == null) instance = new BlockIconAtlas(atlas);
        return instance;
    }

    private final int textureId;
    private final int gridW;
    private final int gridH;
    private final Map<Integer, float[]> uvs = new HashMap<>();

    private BlockIconAtlas(TextureAtlas atlas) {
        List<BlockType> cubes = new ArrayList<>();
        for (BlockType t : BlockType.values()) {
            if (t != BlockType.AIR && t.solid && !t.isItemSprite()) cubes.add(t);
        }

        int rows = Math.max(1, (cubes.size() + COLS - 1) / COLS);
        gridW = COLS * ICON;
        gridH = rows * ICON;
        int[] pixels = new int[gridW * gridH]; // ARGB, 0 = transparent

        for (int i = 0; i < cubes.size(); i++) {
            BlockType t = cubes.get(i);
            int x0 = (i % COLS) * ICON;
            int y0 = (i / COLS) * ICON;
            drawIsoIcon(atlas, t, pixels, gridW, x0, y0);
            uvs.put(t.id, new float[]{
                (float) x0 / gridW, (float) y0 / gridH,
                (float) (x0 + ICON) / gridW, (float) (y0 + ICON) / gridH});
        }
        textureId = upload(pixels, gridW, gridH);
        System.out.println("[BlockIconAtlas] built " + cubes.size() + " cube icons, atlas "
            + gridW + "x" + gridH);
    }

    // ------------------------------------------------------------------
    // Isometric rasterisation
    // ------------------------------------------------------------------

    /** A parallelogram face: origin + two edge vectors, tile-shaded. */
    private int drawFace(int[] out, int outW, int x0, int y0,
                         float[] o, float[] u, float[] v,
                         int[] tile, float shade) {
        // bounding box of the parallelogram
        float minX = Math.min(Math.min(o[0], o[0] + u[0]), Math.min(o[0] + v[0], o[0] + u[0] + v[0]));
        float maxX = Math.max(Math.max(o[0], o[0] + u[0]), Math.max(o[0] + v[0], o[0] + u[0] + v[0]));
        float minY = Math.min(Math.min(o[1], o[1] + u[1]), Math.min(o[1] + v[1], o[1] + u[1] + v[1]));
        float maxY = Math.max(Math.max(o[1], o[1] + u[1]), Math.max(o[1] + v[1], o[1] + u[1] + v[1]));

        float det = u[0] * v[1] - u[1] * v[0];
        if (Math.abs(det) < 1e-6f) return 0;

        int written = 0;
        for (int py = (int) Math.floor(minY); py <= maxY; py++) {
            for (int px = (int) Math.floor(minX); px <= maxX; px++) {
                float dx = px + 0.5f - o[0];
                float dy = py + 0.5f - o[1];
                float s = (dx * v[1] - dy * v[0]) / det;
                float t = (u[0] * dy - u[1] * dx) / det;
                if (s < 0 || s >= 1 || t < 0 || t >= 1) continue;

                // o/u/v are already in icon-atlas space (the cell origin is
                // baked into them by drawIsoIcon), so the scan position is
                // the destination pixel: adding x0/y0 again shifted every
                // icon but the first one out of its own cell, where the
                // clip below threw it away.
                int sx = px;
                int sy = py;
                // hard-clip to this icon's 32x32 cell so faces never bleed
                if (sx < x0 || sy < y0 || sx >= x0 + ICON || sy >= y0 + ICON) continue;

                // Tile arrays come in any square resolution (16x16
                // procedural, 64x64 PNG-backed) - sample proportionally
                int tw = Math.max(1, (int) Math.round(Math.sqrt(tile.length)));
                int tx = Math.max(0, Math.min(tw - 1, (int) (s * tw)));
                int ty = Math.max(0, Math.min(tw - 1, (int) (t * tw)));
                int argb = tile[ty * tw + tx];
                int a = (argb >>> 24) & 0xFF;
                if (a < 128) continue;

                int r = (int) (((argb >> 16) & 0xFF) * shade);
                int g = (int) (((argb >> 8) & 0xFF) * shade);
                int b = (int) ((argb & 0xFF) * shade);
                if (sx < 0 || sy < 0 || sx >= outW || sy >= out.length / outW) continue;
                out[sy * outW + sx] = (a << 24) | (r << 16) | (g << 8) | b;
                written++;
            }
        }
        return written;
    }

    private int drawIsoIcon(TextureAtlas atlas, BlockType t,
                             int[] out, int outW, int x0, int y0) {
        int[] top = atlas.getLayerPixels(atlas.getSlot(t.id, TextureAtlas.FACE_TOP));
        int[] side1 = atlas.getLayerPixels(atlas.getSlot(t.id, 0));
        int[] side2 = atlas.getLayerPixels(atlas.getSlot(t.id, 1));
        if (top == null) top = side1;
        if (side1 == null) side1 = top;
        if (side2 == null) side2 = side1;

        // Icon layout on a 32x32 grid, vanilla proportions: top rhombus
        // (half-height 8), two side faces 15px tall below it
        float cx = x0 + 16;
        float topY = y0, midY = y0 + 8, splitY = y0 + 16, botY = y0 + 31;

        // top face: L(0,8) + U*(16,-8) + V*(16,8)
        int written = drawFace(out, outW, x0, y0,
            new float[]{x0, midY}, new float[]{16, topY - midY}, new float[]{16, splitY - midY},
            top, 1.0f);
        // left-front face: L(0,8) + U*(16,8) + V*(0,15)
        written += drawFace(out, outW, x0, y0,
            new float[]{x0, midY}, new float[]{16, splitY - midY}, new float[]{0, botY - splitY + 1},
            side1, 0.80f);
        // right-front face: B(16,16) + U*(15,-8) + V*(0,15)
        written += drawFace(out, outW, x0, y0,
            new float[]{cx, splitY}, new float[]{x0 + 31 - cx, midY - splitY}, new float[]{0, botY - splitY + 1},
            side2, 0.60f);
        return written;
    }

    // ------------------------------------------------------------------
    // Draw + upload
    // ------------------------------------------------------------------

    /** True when an isometric icon exists for this block. */
    public boolean has(int blockId) {
        return uvs.containsKey(blockId);
    }

    /** Draw the icon scaled into the given rect. */
    public void draw(UIRenderer ui, int blockId, int x, int y, int size) {
        float[] uv = uvs.get(blockId);
        if (uv == null) return;
        // arg pattern matches StackIcons' atlas draws: (left, vScreenTop, right, vScreenBottom)
        float vTop = 1f - uv[1];
        float vBottom = 1f - uv[3];
        ui.drawTexture(textureId, x, y, size, size, uv[0], vTop, uv[2], vBottom, 0xFFFFFFFF);
    }

    /** Debug: save the icon texture to a PNG. */
    public static void dumpTo(String path) {
        if (instance == null) return;
        glBindTexture(GL_TEXTURE_2D, instance.textureId);
        ByteBuffer buf = MemoryUtil.memAlloc(instance.gridW * instance.gridH * 4);
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
            instance.gridW, instance.gridH, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < instance.gridH; y++) {
            for (int x = 0; x < instance.gridW; x++) {
                int i = (y * instance.gridW + x) * 4;
                int r = buf.get(i) & 0xFF, g = buf.get(i + 1) & 0xFF;
                int b = buf.get(i + 2) & 0xFF, a = buf.get(i + 3) & 0xFF;
                // GL rows are bottom-up
                img.setRGB(x, instance.gridH - 1 - y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        MemoryUtil.memFree(buf);
        try {
            javax.imageio.ImageIO.write(img, "png", new java.io.File(path));
        } catch (Exception e) {
            System.err.println("icon dump failed: " + e);
        }
    }

    private int upload(int[] pixels, int w, int h) {
        ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int p = pixels[y * w + x];
                buf.put((byte) ((p >> 16) & 0xFF));
                buf.put((byte) ((p >> 8) & 0xFF));
                buf.put((byte) (p & 0xFF));
                buf.put((byte) ((p >>> 24) & 0xFF));
            }
        }
        buf.flip();

        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        MemoryUtil.memFree(buf);
        return id;
    }
}
