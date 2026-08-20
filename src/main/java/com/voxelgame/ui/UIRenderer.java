package com.voxelgame.ui;

import com.voxelgame.rendering.Shader;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Immediate-mode 2D renderer for the interface.
 *
 * Quads are accumulated into a single streaming buffer and flushed whenever
 * the bound texture changes, so a whole screen usually costs a handful of
 * draw calls. Coordinates are in virtual GUI pixels (see {@link #getScale()}),
 * with the origin at the top-left corner.
 */
public class UIRenderer {

    /** floats per vertex: x, y, u, v, r, g, b, a */
    private static final int VERTEX_SIZE = 8;
    private static final int MAX_QUADS = 4096;
    private static final int MAX_VERTS = MAX_QUADS * 6;

    private final Shader shader;
    private final int vao;
    private final int vbo;

    private final FloatBuffer buffer;
    private int vertexCount = 0;

    private int boundTexture = 0;
    private boolean texturingEnabled = false;

    private final Matrix4f projection = new Matrix4f();

    private int screenWidth = 1;
    private int screenHeight = 1;
    private int scale = 3;
    private int guiScaleSetting = 0; // 0 = auto

    public UIRenderer() {
        shader = new Shader("shaders/ui.vert", "shaders/ui.frag");

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) MAX_VERTS * VERTEX_SIZE * Float.BYTES, GL_STREAM_DRAW);

        int stride = VERTEX_SIZE * Float.BYTES;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 4 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);

        buffer = MemoryUtil.memAllocFloat(MAX_VERTS * VERTEX_SIZE);
    }

    // ------------------------------------------------------------------
    // Frame lifecycle
    // ------------------------------------------------------------------

    /**
     * Recompute the ortho projection. Call on resize and when GUI scale changes.
     */
    public void resize(int width, int height) {
        this.screenWidth = Math.max(1, width);
        this.screenHeight = Math.max(1, height);
        this.scale = computeScale();

        // Map the FULL framebuffer onto screenSize/scale GUI units, keeping the
        // fractional part. One GUI pixel is then exactly `scale` screen pixels.
        //
        // Projecting onto the rounded-down canvas instead (as before) stretched
        // every unit by scale + remainder/canvas - at 1280x720/3 that is 3.0047,
        // so texels drifted off the pixel grid and the whole UI looked soft.
        // Layout still uses the floored getWidth()/getHeight(); the sub-pixel
        // remainder is left as margin at the right and bottom edges.
        projection.identity().ortho(
            0, screenWidth / (float) scale,
            screenHeight / (float) scale, 0,
            -1, 1);
    }

    /** Auto scale keeps the UI readable from 720p up to 4K. */
    private int computeScale() {
        if (guiScaleSetting > 0) {
            return Math.max(1, Math.min(guiScaleSetting, maxScale()));
        }
        return Math.max(1, Math.min(4, maxScale()));
    }

    private int maxScale() {
        // Never let the virtual canvas drop below 320x240
        int byWidth = Math.max(1, screenWidth / 320);
        int byHeight = Math.max(1, screenHeight / 240);
        return Math.max(1, Math.min(byWidth, byHeight));
    }

    public void setGuiScaleSetting(int value) {
        this.guiScaleSetting = value;
        resize(screenWidth, screenHeight);
    }

    public int getGuiScaleSetting() { return guiScaleSetting; }
    public int getScale() { return scale; }

    /** Virtual canvas width in GUI pixels. */
    public int getWidth() { return Math.max(1, screenWidth / scale); }
    /** Virtual canvas height in GUI pixels. */
    public int getHeight() { return Math.max(1, screenHeight / scale); }

    /** Convert a physical cursor position into virtual GUI coordinates. */
    public float toGuiX(double physicalX) { return (float) (physicalX / scale); }
    public float toGuiY(double physicalY) { return (float) (physicalY / scale); }

    /**
     * Enter 2D mode: depth test off, alpha blending on.
     */
    public void begin() {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        shader.bind();
        shader.setUniformMat4("projection", projection);
        shader.setUniform1i("uiTexture", 0);
        shader.setUniform1i("useTexture", 0);

        vertexCount = 0;
        boundTexture = 0;
        texturingEnabled = false;
        buffer.clear();
    }

    /**
     * Leave 2D mode and restore the state the world renderer expects.
     */
    public void end() {
        flush();
        shader.unbind();

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
    }

    public void flush() {
        if (vertexCount == 0) return;

        buffer.flip();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);

        buffer.clear();
        vertexCount = 0;
    }

    // ------------------------------------------------------------------
    // Texture state
    // ------------------------------------------------------------------

    /** Switch to untextured (solid colour) drawing. */
    public void useSolidColor() {
        if (texturingEnabled) {
            flush();
            texturingEnabled = false;
            shader.setUniform1i("useTexture", 0);
        }
    }

    public void bindTexture(int textureId) {
        if (!texturingEnabled || boundTexture != textureId) {
            flush();
            texturingEnabled = true;
            boundTexture = textureId;
            shader.setUniform1i("useTexture", 1);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, textureId);
        }
    }

    // ------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------

    public void fillRect(float x, float y, float w, float h, int argb) {
        useSolidColor();
        // Snap to the GUI pixel grid: fractional edges would be resolved by
        // the rasteriser and show up as uneven 1px seams once scaled up.
        pushQuad(Math.round(x), Math.round(y), Math.round(w), Math.round(h),
            0, 0, 1, 1, argb);
    }

    /**
     * Draw a sprite at its native size, one source texel per GUI pixel.
     * This is the only correct way to draw the slot / heart / crosshair art.
     */
    public void drawSprite(int textureId, int x, int y, int w, int h) {
        drawSprite(textureId, x, y, w, h, 0xFFFFFFFF);
    }

    public void drawSprite(int textureId, int x, int y, int w, int h, int tintArgb) {
        bindTexture(textureId);
        pushQuad(x, y, w, h, 0, 0, 1, 1, tintArgb);
    }

    /** 1px outline (in GUI pixels). */
    public void drawRectOutline(float x, float y, float w, float h, int argb) {
        fillRect(x, y, w, 1, argb);
        fillRect(x, y + h - 1, w, 1, argb);
        fillRect(x, y + 1, 1, h - 2, argb);
        fillRect(x + w - 1, y + 1, 1, h - 2, argb);
    }

    /** Vertical two-stop gradient, used for menu backdrops. */
    public void fillGradientV(float x, float y, float w, float h, int topArgb, int bottomArgb) {
        useSolidColor();
        ensureSpace(6);

        float[] t = unpack(topArgb);
        float[] b = unpack(bottomArgb);

        vertex(x, y, 0, 0, t);
        vertex(x, y + h, 0, 1, b);
        vertex(x + w, y + h, 1, 1, b);

        vertex(x, y, 0, 0, t);
        vertex(x + w, y + h, 1, 1, b);
        vertex(x + w, y, 1, 0, t);
    }

    public void drawTexture(int textureId, float x, float y, float w, float h) {
        drawTexture(textureId, x, y, w, h, 0, 0, 1, 1, 0xFFFFFFFF);
    }

    public void drawTexture(int textureId, float x, float y, float w, float h,
                            float u0, float v0, float u1, float v1, int tintArgb) {
        bindTexture(textureId);
        pushQuad(x, y, w, h, u0, v0, u1, v1, tintArgb);
    }

    /**
     * Nine-slice: corners keep their size, edges stretch, centre fills.
     * Used for buttons, slots and panels so they scale without distortion.
     *
     * @param border corner size in source texture pixels
     * @param texSize source texture dimension (assumed square)
     */
    public void drawNineSlice(int textureId, float x, float y, float w, float h,
                              int border, int texSize, int tintArgb) {
        // Whole GUI pixels only, so corner art lands on the grid
        x = Math.round(x);
        y = Math.round(y);
        w = Math.round(w);
        h = Math.round(h);

        float b = border;
        float uv = border / (float) texSize;

        // Shrink the border if the target is too small to fit two of them
        float bw = Math.min(b, w * 0.5f);
        float bh = Math.min(b, h * 0.5f);

        float midW = Math.max(0, w - bw * 2);
        float midH = Math.max(0, h - bh * 2);

        bindTexture(textureId);

        // top row
        pushQuad(x, y, bw, bh, 0, 0, uv, uv, tintArgb);
        pushQuad(x + bw, y, midW, bh, uv, 0, 1 - uv, uv, tintArgb);
        pushQuad(x + bw + midW, y, bw, bh, 1 - uv, 0, 1, uv, tintArgb);

        // middle row
        pushQuad(x, y + bh, bw, midH, 0, uv, uv, 1 - uv, tintArgb);
        pushQuad(x + bw, y + bh, midW, midH, uv, uv, 1 - uv, 1 - uv, tintArgb);
        pushQuad(x + bw + midW, y + bh, bw, midH, 1 - uv, uv, 1, 1 - uv, tintArgb);

        // bottom row
        pushQuad(x, y + bh + midH, bw, bh, 0, 1 - uv, uv, 1, tintArgb);
        pushQuad(x + bw, y + bh + midH, midW, bh, uv, 1 - uv, 1 - uv, 1, tintArgb);
        pushQuad(x + bw + midW, y + bh + midH, bw, bh, 1 - uv, 1 - uv, 1, 1, tintArgb);
    }

    /**
     * Tile a texture across an area instead of stretching it - the classic
     * dirt background behind menus.
     */
    public void drawTiled(int textureId, float x, float y, float w, float h,
                          float tileSize, int tintArgb) {
        bindTexture(textureId);
        pushQuad(x, y, w, h, 0, 0, w / tileSize, h / tileSize, tintArgb);
    }

    // ------------------------------------------------------------------
    // Buffer plumbing
    // ------------------------------------------------------------------

    private void pushQuad(float x, float y, float w, float h,
                          float u0, float v0, float u1, float v1, int argb) {
        if (w <= 0 || h <= 0) return;
        ensureSpace(6);

        float[] c = unpack(argb);

        vertex(x, y, u0, v0, c);
        vertex(x, y + h, u0, v1, c);
        vertex(x + w, y + h, u1, v1, c);

        vertex(x, y, u0, v0, c);
        vertex(x + w, y + h, u1, v1, c);
        vertex(x + w, y, u1, v0, c);
    }

    private void ensureSpace(int verts) {
        if (vertexCount + verts > MAX_VERTS) flush();
    }

    private void vertex(float x, float y, float u, float v, float[] c) {
        buffer.put(x).put(y).put(u).put(v).put(c[0]).put(c[1]).put(c[2]).put(c[3]);
        vertexCount++;
    }

    private static float[] unpack(int argb) {
        return new float[]{
            ((argb >> 16) & 0xFF) / 255.0f,
            ((argb >> 8) & 0xFF) / 255.0f,
            (argb & 0xFF) / 255.0f,
            ((argb >>> 24) & 0xFF) / 255.0f
        };
    }

    // ------------------------------------------------------------------
    // New modern UI primitives
    // ------------------------------------------------------------------

    /** Draw a rounded rectangle using multiple quads to approximate corners. */
    public void fillRoundedRect(float x, float y, float w, float h, float radius, int argb) {
        useSolidColor();
        radius = Math.min(radius, Math.min(w, h) / 2);

        // Center rectangle
        fillRect(x + radius, y, w - radius * 2, h, argb);
        fillRect(x, y + radius, w, h - radius * 2, argb);

        // Four corners (approximated with small rects)
        float step = radius / 4;
        for (float i = 0; i < radius; i += step) {
            float j = radius - i;
            // Top-left
            fillRect(x + i, y + j, step, step, argb);
            // Top-right
            fillRect(x + w - radius + i, y + j, step, step, argb);
            // Bottom-left
            fillRect(x + i, y + h - j, step, step, argb);
            // Bottom-right
            fillRect(x + w - radius + i, y + h - j, step, step, argb);
        }
    }

    /** Draw a glow effect (semi-transparent rectangle around element). */
    public void drawGlow(float x, float y, float w, float h, int color, float intensity) {
        int alpha = (int) (intensity * 60) & 0xFF;
        int glowColor = (alpha << 24) | (color & 0xFFFFFF);
        float expand = 4 + intensity * 6;
        fillRoundedRect(x - expand, y - expand, w + expand * 2, h + expand * 2,
            6, glowColor);
    }

    /** Draw a neon border line. */
    public void drawNeonBorder(float x, float y, float w, float h, int color) {
        int glow = (0x40 << 24) | (color & 0xFFFFFF);
        // Outer glow
        for (int i = 3; i > 0; i--) {
            drawRectOutline(x - i, y - i, w + i * 2, h + i * 2,
                ((0x15 + i * 5) << 24) | (color & 0xFFFFFF));
        }
        // Main border
        drawRectOutline(x, y, w, h, color);
    }

    /** Draw a small animated particle dot. */
    public void drawParticle(float x, float y, float size, int color) {
        fillRoundedRect(x - size / 2, y - size / 2, size, size, size / 2, color);
    }

    /** Multi-stop vertical gradient for richer backgrounds. */
    public void fillGradientMultiV(float x, float y, float w, float h,
                                    int... colors) {
        if (colors.length < 2) {
            fillRect(x, y, w, h, colors.length == 1 ? colors[0] : 0xFF000000);
            return;
        }
        useSolidColor();
        int segments = colors.length - 1;
        float segH = h / (float) segments;

        for (int i = 0; i < segments; i++) {
            float sy = y + segH * i;
            float[] top = unpack(colors[i]);
            float[] bot = unpack(colors[i + 1]);

            ensureSpace(6);
            vertex(x, sy, 0, 0, top);
            vertex(x, sy + segH, 0, 1, bot);
            vertex(x + w, sy + segH, 1, 1, bot);

            vertex(x, sy, 0, 0, top);
            vertex(x + w, sy + segH, 1, 1, bot);
            vertex(x + w, sy, 1, 0, top);
        }
    }

    public void cleanup() {
        MemoryUtil.memFree(buffer);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        shader.cleanup();
    }
}
