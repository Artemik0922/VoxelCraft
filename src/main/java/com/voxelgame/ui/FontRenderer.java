package com.voxelgame.ui;

import org.lwjgl.system.MemoryUtil;

import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Bitmap font rasterised from Monocraft.ttf (SIL OFL 1.1, see
 * assets/fonts/Monocraft-OFL.txt) at startup.
 *
 * Rasterisation goes through Java2D (not stb_truetype): Monocraft's outlines
 * only land on the pixel grid after hinting, which stb does not do - without
 * it every glyph edge turns into partial coverage and the text looks grey
 * and frayed once scaled. Java2D with TEXT_ANTIALIAS_OFF + FRACTIONALMETRICS_OFF
 * grid-fits the font, so at its 8px design size every glyph is a crisp 5x7
 * bitmap with zero antialiased pixels.
 *
 * Latin, Cyrillic and typographic punctuation are baked with the metrics of
 * the old hand-drawn 5x7 glyphs (5px advance, caps 7px tall), so the existing
 * layout code keeps working unchanged. Glyphs are addressed through a
 * codepoint map rather than by masking the character to a byte: masking
 * collapsed U+0410 onto 0x10 and made Cyrillic unrenderable.
 */
public class FontRenderer {

    public static final int GLYPH_W = 5;
    public static final int GLYPH_H = 7;
    /** Cell size in the atlas, leaving guard pixels so glyphs never bleed. */
    private static final int CELL = 12;
    /** 21x21 cells = 441 glyph slots, enough for Latin, Cyrillic and symbols. */
    private static final int COLS = 21;
    private static final int GRID = 21;
    private static final int TEX_SIZE = 256;

    /** Rasterisation size: Monocraft's native pixel grid. */
    private static final float FONT_SIZE = 8f;
    /** Draw-line top to cap top: caps draw at y+0, like the old glyphs. */
    private static final int CAP_TOP = 7;
    private static final String FONT_RESOURCE = "assets/fonts/Monocraft.ttf";

    /** Baseline line height including descender space. */
    public static final int LINE_HEIGHT = 9;

    private static class Glyph {
        final int slot;
        final int width;
        final int height;
        final int dx; // offset from the pen position to the bitmap left
        final int dy; // offset from the draw y to the bitmap top

        Glyph(int slot, int width, int height, int dx, int dy) {
            this.slot = slot;
            this.width = width;
            this.height = height;
            this.dx = dx;
            this.dy = dy;
        }
    }

    private final int textureId;
    private final Map<Character, Glyph> glyphs = new HashMap<>();
    private int nextSlot = 0;

    public FontRenderer() {
        Font font = readFont(FONT_RESOURCE);
        int[] pixels = new int[TEX_SIZE * TEX_SIZE];
        bakeGlyphs(font, pixels);
        textureId = uploadTexture(pixels);
    }

    // ------------------------------------------------------------------
    // Measuring
    // ------------------------------------------------------------------

    /** Width of a string in GUI pixels, including 1px inter-glyph spacing. */
    public int width(String text) {
        if (text == null || text.isEmpty()) return 0;
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += charWidth(text.charAt(i)) + 1;
        }
        return Math.max(0, w - 1);
    }

    public int charWidth(char c) {
        if (c == ' ') return GLYPH_W;
        Glyph g = glyphs.get(c);
        return g != null && g.width > 0 ? g.width : GLYPH_W;
    }

    /** Trim a string to fit a pixel width, appending an ellipsis. */
    public String trimToWidth(String text, int maxWidth) {
        if (width(text) <= maxWidth) return text;

        String ellipsis = "...";
        int budget = maxWidth - width(ellipsis);
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            int cw = charWidth(text.charAt(i)) + 1;
            if (w + cw > budget) break;
            sb.append(text.charAt(i));
            w += cw;
        }
        return sb + ellipsis;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /** Draw with a 1px offset drop shadow, the classic look. */
    public int drawWithShadow(UIRenderer ui, String text, float x, float y, int argb) {
        draw(ui, text, x + 1, y + 1, darkenForShadow(argb));
        return draw(ui, text, x, y, argb);
    }

    public int drawCenteredWithShadow(UIRenderer ui, String text, float cx, float y, int argb) {
        return drawWithShadow(ui, text, cx - width(text) / 2.0f, y, argb);
    }

    public int drawCentered(UIRenderer ui, String text, float cx, float y, int argb) {
        return draw(ui, text, cx - width(text) / 2.0f, y, argb);
    }

    public int drawRight(UIRenderer ui, String text, float rightX, float y, int argb) {
        return drawWithShadow(ui, text, rightX - width(text), y, argb);
    }

    public int draw(UIRenderer ui, String text, float x, float y, int argb) {
        if (text == null || text.isEmpty()) return (int) x;

        ui.bindTexture(textureId);
        float penX = x;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int w = charWidth(c);
            Glyph g = glyphs.get(c);
            if (g != null) {
                drawGlyphQuad(ui, g, penX + g.dx, y + g.dy, 1, argb);
            }
            penX += w + 1;
        }
        return (int) penX;
    }

    /**
     * Draw one glyph run scaled up, used for the oversized menu title.
     */
    public void drawScaled(UIRenderer ui, String text, float x, float y,
                           int scale, int argb) {
        if (text == null || text.isEmpty()) return;

        ui.bindTexture(textureId);
        float penX = x;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int w = charWidth(c);
            Glyph g = glyphs.get(c);
            if (g != null) {
                drawGlyphQuad(ui, g, penX + g.dx * scale, y + g.dy * scale, scale, argb);
            }
            penX += (w + 1) * scale;
        }
    }

    public void drawScaledWithShadow(UIRenderer ui, String text, float x, float y,
                                     int scale, int argb) {
        drawScaled(ui, text, x + scale, y + scale, scale, darkenForShadow(argb));
        drawScaled(ui, text, x, y, scale, argb);
    }

    /** Width of a scaled string, for centring oversized text. */
    public int scaledWidth(String text, int scale) {
        return width(text) * scale;
    }

    private void drawGlyphQuad(UIRenderer ui, Glyph g, float x, float y,
                               int scale, int argb) {
        int gx = g.slot % COLS;
        int gy = g.slot / COLS;

        float u0 = (gx * CELL) / (float) TEX_SIZE;
        float v0 = (gy * CELL) / (float) TEX_SIZE;
        float u1 = (gx * CELL + g.width) / (float) TEX_SIZE;
        float v1 = (gy * CELL + g.height) / (float) TEX_SIZE;

        ui.drawTexture(textureId, x, y, g.width * scale, g.height * scale,
            u0, v0, u1, v1, argb);
    }

    /** Minecraft dims the shadow to a quarter of the text colour. */
    private static int darkenForShadow(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = ((argb >> 16) & 0xFF) / 4;
        int g = ((argb >> 8) & 0xFF) / 4;
        int b = (argb & 0xFF) / 4;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public int getTextureId() { return textureId; }

    public void cleanup() {
        org.lwjgl.opengl.GL11.glDeleteTextures(textureId);
    }

    // ------------------------------------------------------------------
    // Glyph baking
    // ------------------------------------------------------------------

    private static Font readFont(String path) {
        try (InputStream is = FontRenderer.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("font resource not found: " + path);
            }
            return Font.createFont(Font.TRUETYPE_FONT, is)
                .deriveFont(Font.PLAIN, FONT_SIZE);
        } catch (Exception e) {
            throw new RuntimeException("failed to load " + path, e);
        }
    }

    /** Codepoints covered by the font: ASCII, Cyrillic, common typography. */
    private static void addCharRange(StringBuilder sb, int from, int to) {
        for (int cp = from; cp <= to; cp++) sb.appendCodePoint(cp);
    }

    private void bakeGlyphs(Font font, int[] pixels) {
        // Scratch big enough for descenders and diacritics around a centred
        // baseline, so nothing clips at the cell edges
        final int margin = 8;
        BufferedImage scratch = new BufferedImage(CELL + margin * 2, CELL + margin * 2,
            BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = scratch.createGraphics();
        g.setFont(font);
        g.setColor(java.awt.Color.WHITE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
            RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_SPEED);
        FontRenderContext frc = g.getFontRenderContext();
        int ascent = g.getFontMetrics().getAscent();

        StringBuilder cps = new StringBuilder();
        addCharRange(cps, 0x20, 0x7E);   // printable ASCII
        addCharRange(cps, 0x400, 0x45F); // Cyrillic + Ё ё + ЂЃЅІЇЈЉЊЋЌЎЏ...
        // Typography used by menus, chat and toasts
        cps.append("«»№×·−→±°…–—‘’‚“”„†‡•‰‹›€™¤¦¬µπ⚠☠");
        Map<Integer, Boolean> seen = new HashMap<>();

        for (int i = 0; i < cps.length(); i++) {
            char c = cps.charAt(i);
            if (seen.putIfAbsent((int) c, Boolean.TRUE) != null) continue;

            java.awt.font.GlyphVector gv = font.createGlyphVector(frc, String.valueOf(c));
            java.awt.Rectangle b = gv.getPixelBounds(frc, 0, 0);

            int slot = nextSlot++;
            if (slot >= COLS * GRID) {
                throw new IllegalStateException("font atlas full at U+"
                    + Integer.toHexString(c));
            }
            int gx = (slot % COLS) * CELL;
            int gy = (slot / COLS) * CELL;

            Glyph glyph;
            if (b.width > 0 && b.height > 0
                    && b.width <= CELL - 1 && b.height <= CELL - 1) {
                // Baseline sits `margin + ascent` from the scratch top, so the
                // ink lands at (margin + b.x, margin + ascent + b.y)
                g.setBackground(new java.awt.Color(0, true));
                g.clearRect(0, 0, scratch.getWidth(), scratch.getHeight());
                g.drawString(String.valueOf(c), margin, margin + ascent);
                for (int ry = 0; ry < b.height; ry++) {
                    for (int rx = 0; rx < b.width; rx++) {
                        int argb = scratch.getRGB(margin + b.x + rx,
                            margin + ascent + b.y + ry);
                        if (((argb >>> 24) & 0xFF) >= 128) {
                            pixels[(gy + ry) * TEX_SIZE + (gx + rx)] =
                                0xFF000000 | 0x00FFFFFF;
                        }
                    }
                }
                glyph = new Glyph(slot, b.width, b.height, b.x, b.y + CAP_TOP);
            } else {
                // Whitespace or an uncovered codepoint: reserve the slot,
                // drawing skips width-0 glyphs
                glyph = new Glyph(slot, 0, 0, 0, 0);
            }
            glyphs.put(c, glyph);
        }
        g.dispose();
    }

    private int uploadTexture(int[] pixels) {
        ByteBuffer buf = MemoryUtil.memAlloc(TEX_SIZE * TEX_SIZE * 4);
        for (int p : pixels) {
            buf.put((byte) ((p >> 16) & 0xFF));
            buf.put((byte) ((p >> 8) & 0xFF));
            buf.put((byte) (p & 0xFF));
            buf.put((byte) ((p >>> 24) & 0xFF));
        }
        buf.flip();

        int id = org.lwjgl.opengl.GL11.glGenTextures();
        org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, id);
        org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0,
            org.lwjgl.opengl.GL11.GL_RGBA, TEX_SIZE, TEX_SIZE, 0,
            org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buf);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_NEAREST);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_NEAREST);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0);

        MemoryUtil.memFree(buf);
        return id;
    }
}
