import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/** Replicate the new Java2D bake exactly and dump the atlas. */
public class FontBakeTest {

    static final int CELL = 12;
    static final int COLS = 21;
    static final int TEX_SIZE = 256;
    static final float FONT_SIZE = 8f;
    static final int CAP_TOP = 7;

    public static void main(String[] args) throws Exception {
        Font font;
        try (InputStream is = FontBakeTest.class.getClassLoader()
                .getResourceAsStream("assets/fonts/Monocraft.ttf")) {
            font = Font.createFont(Font.TRUETYPE_FONT, is)
                .deriveFont(Font.PLAIN, FONT_SIZE);
        }

        int margin = 8;
        BufferedImage scratch = new BufferedImage(CELL + margin * 2, CELL + margin * 2,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        g.setFont(font);
        g.setColor(Color.WHITE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
            RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_SPEED);
        FontRenderContext frc = g.getFontRenderContext();
        int ascent = g.getFontMetrics().getAscent();
        System.out.println("ascent=" + ascent);

        StringBuilder cps = new StringBuilder();
        for (int cp = 0x20; cp <= 0x7E; cp++) cps.appendCodePoint(cp);
        for (int cp = 0x400; cp <= 0x45F; cp++) cps.appendCodePoint(cp);
        cps.append("«»№×·−→±°…–—‘’‚“”„†‡•‰‹›€™¤¦¬µπ⚠☠");
        java.util.Map<Integer, Boolean> seen = new java.util.HashMap<>();
        int[] pixels = new int[TEX_SIZE * TEX_SIZE];
        int slot = 0;

        for (int i = 0; i < cps.length(); i++) {
            char c = cps.charAt(i);
            if (seen.putIfAbsent((int) c, Boolean.TRUE) != null) continue;

            java.awt.font.GlyphVector gv = font.createGlyphVector(frc, String.valueOf(c));
            java.awt.Rectangle b = gv.getPixelBounds(frc, 0, 0);

            int gx = (slot % COLS) * CELL;
            int gy = (slot / COLS) * CELL;
            slot++;

            if (b.width > 0 && b.height > 0 && b.width <= CELL - 1 && b.height <= CELL - 1) {
                g.setBackground(new Color(0, true));
                g.clearRect(0, 0, scratch.getWidth(), scratch.getHeight());
                g.drawString(String.valueOf(c), margin, margin + ascent);
                for (int ry = 0; ry < b.height; ry++) {
                    for (int rx = 0; rx < b.width; rx++) {
                        int argb = scratch.getRGB(margin + b.x + rx,
                            margin + ascent + b.y + ry);
                        if (((argb >>> 24) & 0xFF) >= 128) {
                            pixels[(gy + ry) * TEX_SIZE + (gx + rx)] = 0xFFFFFFFF;
                        }
                    }
                }
            }
        }
        g.dispose();
        System.out.println("baked " + slot);

        // show a few cells as ascii
        for (int s : new int[]{0, 1, 4, 33, 100}) {
            int gx = (s % COLS) * CELL, gy = (s / COLS) * CELL;
            System.out.println("--- slot " + s);
            for (int ry = 0; ry < 10; ry++) {
                StringBuilder row = new StringBuilder();
                for (int rx = 0; rx < 10; rx++) {
                    row.append(((pixels[(gy + ry) * TEX_SIZE + gx + rx] & 0x80000000) != 0) ? '#' : '.');
                }
                System.out.println("  " + row);
            }
        }

        BufferedImage img = new BufferedImage(TEX_SIZE, TEX_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < TEX_SIZE; y++)
            for (int x = 0; x < TEX_SIZE; x++)
                img.setRGB(x, y, (pixels[y * TEX_SIZE + x] & 0x80000000) != 0 ? 0xFFFFFF : 0);
        javax.imageio.ImageIO.write(img, "png", new java.io.File("debug/test_bake.png"));
        System.out.println("wrote debug/test_bake.png");
    }
}
