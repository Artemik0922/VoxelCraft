package com.voxelgame.ui;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Pixel bitmap font, generated at runtime. Nothing is loaded from disk.
 *
 * Glyphs are hand-defined as 5x7 bit patterns and baked into a texture at
 * startup. Characters are variable width, so 'i' does not occupy the same
 * space as 'W'.
 *
 * Latin and Cyrillic both fit because glyphs are addressed through a
 * codepoint-to-slot map rather than by masking the character to a byte:
 * masking collapsed U+0410 onto 0x10 and made Cyrillic unrenderable.
 */
public class FontRenderer {

    public static final int GLYPH_W = 5;
    public static final int GLYPH_H = 7;
    /** Cell size in the atlas, leaving a guard pixel so glyphs never bleed. */
    private static final int CELL = 8;
    /** 32x32 cells = 1024 glyph slots, enough for Latin plus Cyrillic. */
    private static final int GRID = 32;
    private static final int TEX_SIZE = CELL * GRID; // 256

    /** Baseline line height including descender space. */
    public static final int LINE_HEIGHT = 9;

    private final int textureId;
    private final int[] glyphWidth = new int[GRID * GRID];
    /** Unicode codepoint -> atlas slot. */
    private final Map<Character, Integer> slotOf = new HashMap<>();

    private int nextSlot = 0;

    public FontRenderer() {
        int[] pixels = new int[TEX_SIZE * TEX_SIZE];
        bakeGlyphs(pixels);
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
        if (c == ' ') return 3;
        Integer slot = slotOf.get(c);
        if (slot == null) return GLYPH_W;
        return glyphWidth[slot] > 0 ? glyphWidth[slot] : GLYPH_W;
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

            if (c != ' ') {
                Integer slot = slotOf.get(c);
                if (slot != null) {
                    drawGlyphQuad(ui, slot, w, penX, y, 1, argb);
                }
            }
            penX += w + 1;
        }
        return (int) penX;
    }

    /**
     * Draw one glyph scaled up, used for the oversized menu title.
     *
     * Screens used to duplicate this UV arithmetic inline, which broke the
     * moment the atlas grew from 16x16 cells to 32x32.
     */
    public void drawScaled(UIRenderer ui, String text, float x, float y,
                           int scale, int argb) {
        if (text == null || text.isEmpty()) return;

        ui.bindTexture(textureId);
        float penX = x;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int w = charWidth(c);

            if (c != ' ') {
                Integer slot = slotOf.get(c);
                if (slot != null) {
                    drawGlyphQuad(ui, slot, w, penX, y, scale, argb);
                }
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

    private void drawGlyphQuad(UIRenderer ui, int slot, int w,
                               float x, float y, int scale, int argb) {
        int gx = slot % GRID;
        int gy = slot / GRID;

        float u0 = (gx * CELL) / (float) TEX_SIZE;
        float v0 = (gy * CELL) / (float) TEX_SIZE;
        float u1 = (gx * CELL + w) / (float) TEX_SIZE;
        float v1 = (gy * CELL + GLYPH_H) / (float) TEX_SIZE;

        ui.drawTexture(textureId, x, y, w * scale, GLYPH_H * scale,
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

    private void bakeGlyphs(int[] pixels) {
        Map<Character, String[]> glyphs = new java.util.LinkedHashMap<>();
        addLatin(glyphs);
        addDigitsAndPunctuation(glyphs);
        addCyrillic(glyphs);

        for (Map.Entry<Character, String[]> e : glyphs.entrySet()) {
            char c = e.getKey();
            String[] rows = e.getValue();

            int slot = nextSlot++;
            slotOf.put(c, slot);

            int gx = (slot % GRID) * CELL;
            int gy = (slot / GRID) * CELL;

            int used = 0;
            for (int ry = 0; ry < rows.length && ry < GLYPH_H; ry++) {
                String row = rows[ry];
                for (int rx = 0; rx < row.length() && rx < GLYPH_W; rx++) {
                    if (row.charAt(rx) == '#') {
                        pixels[(gy + ry) * TEX_SIZE + (gx + rx)] = 0xFFFFFFFF;
                        used = Math.max(used, rx + 1);
                    }
                }
            }
            glyphWidth[slot] = Math.max(1, used);
        }
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

    // ------------------------------------------------------------------
    // Glyph shapes. '#' is an opaque pixel.
    // ------------------------------------------------------------------

    private void addLatin(Map<Character, String[]> g) {
        g.put('A', new String[]{" ### ", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"});
        g.put('B', new String[]{"#### ", "#   #", "#   #", "#### ", "#   #", "#   #", "#### "});
        g.put('C', new String[]{" ### ", "#   #", "#    ", "#    ", "#    ", "#   #", " ### "});
        g.put('D', new String[]{"#### ", "#   #", "#   #", "#   #", "#   #", "#   #", "#### "});
        g.put('E', new String[]{"#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####"});
        g.put('F', new String[]{"#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#    "});
        g.put('G', new String[]{" ### ", "#   #", "#    ", "#  ##", "#   #", "#   #", " ### "});
        g.put('H', new String[]{"#   #", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"});
        g.put('I', new String[]{"###", " # ", " # ", " # ", " # ", " # ", "###"});
        g.put('J', new String[]{"    #", "    #", "    #", "    #", "#   #", "#   #", " ### "});
        g.put('K', new String[]{"#   #", "#  # ", "# #  ", "##   ", "# #  ", "#  # ", "#   #"});
        g.put('L', new String[]{"#    ", "#    ", "#    ", "#    ", "#    ", "#    ", "#####"});
        g.put('M', new String[]{"#   #", "## ##", "# # #", "#   #", "#   #", "#   #", "#   #"});
        g.put('N', new String[]{"#   #", "##  #", "# # #", "#  ##", "#   #", "#   #", "#   #"});
        g.put('O', new String[]{" ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "});
        g.put('P', new String[]{"#### ", "#   #", "#   #", "#### ", "#    ", "#    ", "#    "});
        g.put('Q', new String[]{" ### ", "#   #", "#   #", "#   #", "# # #", "#  # ", " ## #"});
        g.put('R', new String[]{"#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #"});
        g.put('S', new String[]{" ####", "#    ", "#    ", " ### ", "    #", "    #", "#### "});
        g.put('T', new String[]{"#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  "});
        g.put('U', new String[]{"#   #", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "});
        g.put('V', new String[]{"#   #", "#   #", "#   #", "#   #", "#   #", " # # ", "  #  "});
        g.put('W', new String[]{"#   #", "#   #", "#   #", "#   #", "# # #", "## ##", "#   #"});
        g.put('X', new String[]{"#   #", "#   #", " # # ", "  #  ", " # # ", "#   #", "#   #"});
        g.put('Y', new String[]{"#   #", "#   #", " # # ", "  #  ", "  #  ", "  #  ", "  #  "});
        g.put('Z', new String[]{"#####", "    #", "   # ", "  #  ", " #   ", "#    ", "#####"});

        g.put('a', new String[]{"     ", "     ", " ### ", "    #", " ####", "#   #", " ####"});
        g.put('b', new String[]{"#    ", "#    ", "#### ", "#   #", "#   #", "#   #", "#### "});
        g.put('c', new String[]{"     ", "     ", " ### ", "#    ", "#    ", "#   #", " ### "});
        g.put('d', new String[]{"    #", "    #", " ####", "#   #", "#   #", "#   #", " ####"});
        g.put('e', new String[]{"     ", "     ", " ### ", "#   #", "#####", "#    ", " ### "});
        g.put('f', new String[]{"  ## ", " #   ", "#### ", " #   ", " #   ", " #   ", " #   "});
        g.put('g', new String[]{"     ", " ####", "#   #", "#   #", " ####", "    #", " ### "});
        g.put('h', new String[]{"#    ", "#    ", "#### ", "#   #", "#   #", "#   #", "#   #"});
        g.put('i', new String[]{"#", " ", "#", "#", "#", "#", "#"});
        g.put('j', new String[]{"   #", "    ", "   #", "   #", "   #", "#  #", " ## "});
        g.put('k', new String[]{"#    ", "#    ", "#   #", "#  # ", "###  ", "#  # ", "#   #"});
        g.put('l', new String[]{"##", " #", " #", " #", " #", " #", " #"});
        g.put('m', new String[]{"     ", "     ", "## # ", "# # #", "# # #", "# # #", "# # #"});
        g.put('n', new String[]{"     ", "     ", "#### ", "#   #", "#   #", "#   #", "#   #"});
        g.put('o', new String[]{"     ", "     ", " ### ", "#   #", "#   #", "#   #", " ### "});
        g.put('p', new String[]{"     ", "#### ", "#   #", "#   #", "#### ", "#    ", "#    "});
        g.put('q', new String[]{"     ", " ####", "#   #", "#   #", " ####", "    #", "    #"});
        g.put('r', new String[]{"     ", "     ", "# ## ", "##   ", "#    ", "#    ", "#    "});
        g.put('s', new String[]{"     ", "     ", " ####", "#    ", " ### ", "    #", "#### "});
        g.put('t', new String[]{" #   ", " #   ", "#### ", " #   ", " #   ", " #  #", "  ## "});
        g.put('u', new String[]{"     ", "     ", "#   #", "#   #", "#   #", "#   #", " ####"});
        g.put('v', new String[]{"     ", "     ", "#   #", "#   #", "#   #", " # # ", "  #  "});
        g.put('w', new String[]{"     ", "     ", "#   #", "#   #", "# # #", "# # #", " # # "});
        g.put('x', new String[]{"     ", "     ", "#   #", " # # ", "  #  ", " # # ", "#   #"});
        g.put('y', new String[]{"     ", "#   #", "#   #", "#   #", " ####", "    #", " ### "});
        g.put('z', new String[]{"     ", "     ", "#####", "   # ", "  #  ", " #   ", "#####"});
    }

    private void addDigitsAndPunctuation(Map<Character, String[]> g) {
        g.put('0', new String[]{" ### ", "#   #", "#  ##", "# # #", "##  #", "#   #", " ### "});
        g.put('1', new String[]{"  #  ", " ##  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "});
        g.put('2', new String[]{" ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####"});
        g.put('3', new String[]{"#####", "   # ", "  #  ", "   # ", "    #", "#   #", " ### "});
        g.put('4', new String[]{"   # ", "  ## ", " # # ", "#  # ", "#####", "   # ", "   # "});
        g.put('5', new String[]{"#####", "#    ", "#### ", "    #", "    #", "#   #", " ### "});
        g.put('6', new String[]{"  ## ", " #   ", "#    ", "#### ", "#   #", "#   #", " ### "});
        g.put('7', new String[]{"#####", "    #", "   # ", "  #  ", " #   ", " #   ", " #   "});
        g.put('8', new String[]{" ### ", "#   #", "#   #", " ### ", "#   #", "#   #", " ### "});
        g.put('9', new String[]{" ### ", "#   #", "#   #", " ####", "    #", "   # ", " ##  "});

        g.put('.', new String[]{"  ", "  ", "  ", "  ", "  ", "##", "##"});
        g.put(',', new String[]{"  ", "  ", "  ", "  ", "  ", "##", " #"});
        g.put(':', new String[]{"  ", "##", "##", "  ", "  ", "##", "##"});
        g.put(';', new String[]{"  ", "##", "##", "  ", "##", " #", "  "});
        g.put('!', new String[]{"#", "#", "#", "#", "#", " ", "#"});
        g.put('?', new String[]{" ### ", "#   #", "    #", "   # ", "  #  ", "     ", "  #  "});
        g.put('/', new String[]{"    #", "    #", "   # ", "  #  ", " #   ", "#    ", "#    "});
        g.put('\\', new String[]{"#    ", "#    ", " #   ", "  #  ", "   # ", "    #", "    #"});
        g.put('-', new String[]{"     ", "     ", "     ", "#####", "     ", "     ", "     "});
        g.put('+', new String[]{"     ", "  #  ", "  #  ", "#####", "  #  ", "  #  ", "     "});
        g.put('=', new String[]{"     ", "     ", "#####", "     ", "#####", "     ", "     "});
        g.put('_', new String[]{"     ", "     ", "     ", "     ", "     ", "     ", "#####"});
        g.put('(', new String[]{"  #", " # ", "#  ", "#  ", "#  ", " # ", "  #"});
        g.put(')', new String[]{"#  ", " # ", "  #", "  #", "  #", " # ", "#  "});
        g.put('[', new String[]{"###", "#  ", "#  ", "#  ", "#  ", "#  ", "###"});
        g.put(']', new String[]{"###", "  #", "  #", "  #", "  #", "  #", "###"});
        g.put('<', new String[]{"   #", "  # ", " #  ", "#   ", " #  ", "  # ", "   #"});
        g.put('>', new String[]{"#   ", " #  ", "  # ", "   #", "  # ", " #  ", "#   "});
        g.put('%', new String[]{"##  #", "##  #", "   # ", "  #  ", " #   ", "#  ##", "#  ##"});
        g.put('*', new String[]{"     ", "# # #", " ### ", "#####", " ### ", "# # #", "     "});
        g.put('#', new String[]{" # # ", " # # ", "#####", " # # ", "#####", " # # ", " # # "});
        g.put('\'', new String[]{"#", "#", " ", " ", " ", " ", " "});
        g.put('"', new String[]{"# #", "# #", "   ", "   ", "   ", "   ", "   "});
        g.put('@', new String[]{" ### ", "#   #", "# ###", "# # #", "# ###", "#    ", " ### "});
        g.put('&', new String[]{" ##  ", "#  # ", " ##  ", " ##  ", "#  ##", "#  # ", " ## #"});
        g.put('|', new String[]{"#", "#", "#", "#", "#", "#", "#"});
    }

    /**
     * Cyrillic. Letters shared with Latin (А, В, Е, К, М, Н, О, Р, С, Т, Х)
     * still get their own slot, so metrics stay independent.
     */
    private void addCyrillic(Map<Character, String[]> g) {
        g.put('А', new String[]{" ### ", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"});
        g.put('Б', new String[]{"#####", "#    ", "#    ", "#### ", "#   #", "#   #", "#### "});
        g.put('В', new String[]{"#### ", "#   #", "#   #", "#### ", "#   #", "#   #", "#### "});
        g.put('Г', new String[]{"#####", "#    ", "#    ", "#    ", "#    ", "#    ", "#    "});
        g.put('Д', new String[]{"  ###", " #  #", " #  #", " #  #", " #  #", "#####", "#   #"});
        g.put('Е', new String[]{"#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####"});
        g.put('Ё', new String[]{"# # #", "     ", "#####", "#    ", "#### ", "#    ", "#####"});
        g.put('Ж', new String[]{"# # #", "# # #", "# # #", " ### ", "# # #", "# # #", "# # #"});
        g.put('З', new String[]{" ### ", "#   #", "    #", "  ## ", "    #", "#   #", " ### "});
        g.put('И', new String[]{"#   #", "#   #", "#  ##", "# # #", "##  #", "#   #", "#   #"});
        g.put('Й', new String[]{"# # #", " ### ", "#   #", "#  ##", "# # #", "##  #", "#   #"});
        g.put('К', new String[]{"#   #", "#  # ", "# #  ", "##   ", "# #  ", "#  # ", "#   #"});
        g.put('Л', new String[]{"  ###", " #  #", " #  #", " #  #", " #  #", "#   #", "#   #"});
        g.put('М', new String[]{"#   #", "## ##", "# # #", "#   #", "#   #", "#   #", "#   #"});
        g.put('Н', new String[]{"#   #", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"});
        g.put('О', new String[]{" ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "});
        g.put('П', new String[]{"#####", "#   #", "#   #", "#   #", "#   #", "#   #", "#   #"});
        g.put('Р', new String[]{"#### ", "#   #", "#   #", "#### ", "#    ", "#    ", "#    "});
        g.put('С', new String[]{" ### ", "#   #", "#    ", "#    ", "#    ", "#   #", " ### "});
        g.put('Т', new String[]{"#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  "});
        g.put('У', new String[]{"#   #", "#   #", "#   #", " ####", "    #", "#   #", " ### "});
        g.put('Ф', new String[]{"  #  ", " ### ", "# # #", "# # #", "# # #", " ### ", "  #  "});
        g.put('Х', new String[]{"#   #", "#   #", " # # ", "  #  ", " # # ", "#   #", "#   #"});
        g.put('Ц', new String[]{"#  # ", "#  # ", "#  # ", "#  # ", "#  # ", "#####", "    #"});
        g.put('Ч', new String[]{"#   #", "#   #", "#   #", " ####", "    #", "    #", "    #"});
        g.put('Ш', new String[]{"# # #", "# # #", "# # #", "# # #", "# # #", "# # #", "#####"});
        g.put('Щ', new String[]{"# # #", "# # #", "# # #", "# # #", "# # #", "#####", "    #"});
        g.put('Ъ', new String[]{"##   ", " #   ", " #   ", " ### ", " #  #", " #  #", " ### "});
        g.put('Ы', new String[]{"#   #", "#   #", "#   #", "### #", "#  ##", "#   #", "### #"});
        g.put('Ь', new String[]{"#    ", "#    ", "#    ", "#### ", "#   #", "#   #", "#### "});
        g.put('Э', new String[]{" ### ", "#   #", "    #", " ####", "    #", "#   #", " ### "});
        g.put('Ю', new String[]{"#  ##", "# #  ", "# #  ", "###  ", "# #  ", "# #  ", "#  ##"});
        g.put('Я', new String[]{" ####", "#   #", "#   #", " ####", "  # #", " #  #", "#   #"});

        g.put('а', new String[]{"     ", "     ", " ### ", "    #", " ####", "#   #", " ####"});
        g.put('б', new String[]{"   ##", "  #  ", " #   ", " ### ", " #  #", " #  #", " ### "});
        g.put('в', new String[]{"     ", "     ", "###  ", "#  # ", "###  ", "#  # ", "###  "});
        g.put('г', new String[]{"     ", "     ", "#### ", "#    ", "#    ", "#    ", "#    "});
        g.put('д', new String[]{"     ", "     ", "  ## ", " #  #", " #  #", "#####", "#   #"});
        g.put('е', new String[]{"     ", "     ", " ### ", "#   #", "#####", "#    ", " ### "});
        g.put('ё', new String[]{"# # #", "     ", " ### ", "#   #", "#####", "#    ", " ### "});
        g.put('ж', new String[]{"     ", "     ", "# # #", "# # #", " ### ", "# # #", "# # #"});
        g.put('з', new String[]{"     ", "     ", " ### ", "    #", "  ## ", "    #", " ### "});
        g.put('и', new String[]{"     ", "     ", "#   #", "#  ##", "# # #", "##  #", "#   #"});
        g.put('й', new String[]{" ### ", "     ", "#   #", "#  ##", "# # #", "##  #", "#   #"});
        g.put('к', new String[]{"     ", "     ", "#   #", "#  # ", "###  ", "#  # ", "#   #"});
        g.put('л', new String[]{"     ", "     ", "  ###", " #  #", " #  #", "#   #", "#   #"});
        g.put('м', new String[]{"     ", "     ", "#   #", "## ##", "# # #", "#   #", "#   #"});
        g.put('н', new String[]{"     ", "     ", "#   #", "#   #", "#####", "#   #", "#   #"});
        g.put('о', new String[]{"     ", "     ", " ### ", "#   #", "#   #", "#   #", " ### "});
        g.put('п', new String[]{"     ", "     ", "#####", "#   #", "#   #", "#   #", "#   #"});
        g.put('р', new String[]{"     ", "     ", "#### ", "#   #", "#### ", "#    ", "#    "});
        g.put('с', new String[]{"     ", "     ", " ### ", "#    ", "#    ", "#   #", " ### "});
        g.put('т', new String[]{"     ", "     ", "#####", "  #  ", "  #  ", "  #  ", "  #  "});
        g.put('у', new String[]{"     ", "     ", "#   #", "#   #", " ####", "    #", " ### "});
        g.put('ф', new String[]{"  #  ", " ### ", "# # #", "# # #", " ### ", "  #  ", "  #  "});
        g.put('х', new String[]{"     ", "     ", "#   #", " # # ", "  #  ", " # # ", "#   #"});
        g.put('ц', new String[]{"     ", "     ", "#  # ", "#  # ", "#  # ", "#####", "    #"});
        g.put('ч', new String[]{"     ", "     ", "#   #", "#   #", " ####", "    #", "    #"});
        g.put('ш', new String[]{"     ", "     ", "# # #", "# # #", "# # #", "# # #", "#####"});
        g.put('щ', new String[]{"     ", "     ", "# # #", "# # #", "# # #", "#####", "    #"});
        g.put('ъ', new String[]{"     ", "     ", "##   ", " #   ", " ### ", " #  #", " ### "});
        g.put('ы', new String[]{"     ", "     ", "#   #", "#   #", "### #", "#  ##", "### #"});
        g.put('ь', new String[]{"     ", "     ", "#    ", "#    ", "#### ", "#   #", "#### "});
        g.put('э', new String[]{"     ", "     ", " ### ", "    #", " ####", "    #", " ### "});
        g.put('ю', new String[]{"     ", "     ", "#  ##", "# #  ", "###  ", "# #  ", "#  ##"});
        g.put('я', new String[]{"     ", "     ", " ####", "#   #", " ####", "  # #", " #  #"});

        // Guillemets, common in Russian typography
        g.put('«', new String[]{"     ", "  # #", " # # ", "# #  ", " # # ", "  # #", "     "});
        g.put('»', new String[]{"     ", "# #  ", " # # ", "  # #", " # # ", "# #  ", "     "});
        g.put('№', new String[]{"#  # ", "## # ", "# ## ", "#  # ", "     ", " ### ", " ### "});
    }
}
