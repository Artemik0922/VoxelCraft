"""One-off: navigator-style arrow + ring ticks + inner shadow on Minimap."""

p = 'src/main/java/com/voxelgame/ui/Minimap.java'
s = open(p, encoding='utf-8').read()

# --- Replace the needle with a navigator arrow (filled polygon + outline) ---
old_needle_start = s.index('    /** Compass needle from the centre, pointing along the view yaw. */')
old_needle_end = s.index('    private void drawCaptions(')
new_needle = '''    /** Navigator-style heading arrow: filled pointer with an outline. */
    private void drawNeedle(UIRenderer ui, double pdx, double pdz,
                            float ccx, float ccy, float yaw, float innerR) {
        float rad = (float) java.lang.Math.toRadians(yaw);
        float ax = (float) java.lang.Math.cos(rad);
        float az = (float) java.lang.Math.sin(rad);

        float L = innerR - 9f;  // tip distance from the pivot
        float W = 5.5f;         // half width of the arrow base
        float B = 6f;           // how far the base sits behind the pivot
        float N = 2.5f;         // depth of the base notch

        fillArrow(ui, ccx, ccy, ax, az, L, W, B, N, OUTLINE, true);
        fillArrow(ui, ccx, ccy, ax, az, L, W, B, N, NEEDLE, false);

        // Engraved pivot dot at the player's position
        for (int oy = -2; oy <= 2; oy++) {
            for (int ox = -2; ox <= 2; ox++) {
                if (ox * ox + oy * oy <= 4) {
                    ui.fillRect((int) ccx + ox, (int) ccy + oy, 1, 1, OUTLINE);
                }
            }
        }
        ui.fillRect((int) ccx, (int) ccy, 1, 1, NEEDLE);
    }

    /**
     * Rasterises the heading arrow: two triangles (tip-right-notch and
     * tip-notch-left) scanned over their bounding box. The outline pass
     * dilates the shape by one pixel so it reads against any terrain.
     */
    private void fillArrow(UIRenderer ui, float ccx, float ccy,
                           float ax, float az, float L, float W, float B, float N,
                           int color, boolean outline) {
        float px = -az, pz = ax;
        float tipX = ccx + ax * L,          tipY = ccy + az * L;
        float rBX  = ccx - ax * B + px * W, rBY  = ccy - az * B + pz * W;
        float lBX  = ccx - ax * B - px * W, lBY  = ccy - az * B - pz * W;
        float nX   = ccx - ax * (B - N),    nY   = ccy - az * (B - N);

        int minX = (int) java.lang.Math.floor(Math.min(Math.min(tipX, rBX), Math.min(lBX, nX))) - 1;
        int maxX = (int) java.lang.Math.ceil(Math.max(Math.max(tipX, rBX), Math.max(lBX, nX))) + 1;
        int minY = (int) java.lang.Math.floor(Math.min(Math.min(tipY, rBY), Math.min(lBY, nY))) - 1;
        int maxY = (int) java.lang.Math.ceil(Math.max(Math.max(tipY, rBY), Math.max(lBY, nY))) + 1;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (arrowContains(x + 0.5f, y + 0.5f, tipX, tipY, rBX, rBY, nX, nY, lBX, lBY)
                    || (outline && (arrowContains(x + 1.5f, y + 0.5f, tipX, tipY, rBX, rBY, nX, nY, lBX, lBY)
                        || arrowContains(x - 0.5f, y + 0.5f, tipX, tipY, rBX, rBY, nX, nY, lBX, lBY)
                        || arrowContains(x + 0.5f, y + 1.5f, tipX, tipY, rBX, rBY, nX, nY, lBX, lBY)
                        || arrowContains(x + 0.5f, y - 0.5f, tipX, tipY, rBX, rBY, nX, nY, lBX, lBY)))) {
                    ui.fillRect(x, y, 1, 1, color);
                }
            }
        }
    }

    /** Convex quad test via two triangles: (tip,right,notch) + (tip,notch,left). */
    private static boolean arrowContains(float x, float y,
                                         float tipX, float tipY,
                                         float rBX, float rBY,
                                         float nX, float nY,
                                         float lBX, float lBY) {
        return pointInTri(x, y, tipX, tipY, rBX, rBY, nX, nY)
            || pointInTri(x, y, tipX, tipY, nX, nY, lBX, lBY);
    }

    private static boolean pointInTri(float px, float py,
                                      float ax, float ay, float bx, float by, float cx, float cy) {
        float d1 = triSign(px, py, ax, ay, bx, by);
        float d2 = triSign(px, py, bx, by, cx, cy);
        float d3 = triSign(px, py, cx, cy, ax, ay);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0;
        boolean pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    private static float triSign(float px, float py, float ax, float ay, float bx, float by) {
        return (px - bx) * (ay - by) - (ax - bx) * (py - by);
    }

'''
s = s[:old_needle_start] + new_needle + s[old_needle_end:]

# --- Ring ticks (engraved, every 45 degrees) + inner shadow for depth ---
s = s.replace('''        // Cardinal letters on the ring
        drawRingLetter(ui, font, cx, cy - outerR - 1, tr("hud.map.north"));''',
'''        // Engraved 45-degree ticks on the ring band
        for (int i = 0; i < 8; i++) {
            float ang = (float) (Math.PI / 4 * i);
            float tx = (float) Math.sin(ang), ty = -(float) Math.cos(ang);
            for (int r = (int) innerR + 1; r <= (int) outerR - 2; r++) {
                ui.fillRect((int) (cx + tx * r), (int) (cy + ty * r), 1, 1, RING_DARK);
            }
        }

        // Inner shadow: 3px translucent band just inside the map edge
        for (int row = -(int) innerR; row <= (int) innerR; row++) {
            float outer = (float) java.lang.Math.sqrt(
                java.lang.Math.max(0, innerR * innerR - row * row));
            float inner = (float) java.lang.Math.sqrt(
                java.lang.Math.max(0, (innerR - 3) * (innerR - 3) - row * row));
            if (outer > inner) {
                ui.fillRect(cx - outer, cy + row, outer - inner, 1, 0x3C261A10);
                ui.fillRect(cx + inner, cy + row, outer - inner, 1, 0x3C261A10);
            }
        }

        // Cardinal letters on the ring
        drawRingLetter(ui, font, cx, cy - outerR - 1, tr("hud.map.north"));''')

open(p, 'w', encoding='utf-8').write(s)
print('Minimax upgraded')
