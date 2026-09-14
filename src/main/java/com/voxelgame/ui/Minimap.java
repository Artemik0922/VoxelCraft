package com.voxelgame.ui;

import com.voxelgame.rendering.Texture;
import com.voxelgame.world.BlockType;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.World;
import com.voxelgame.world.entity.Animal;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.entity.TntEntity;
import com.voxelgame.world.entity.Villager;
import com.voxelgame.world.entity.Zoloy;

import static com.voxelgame.core.Language.tr;

/**
 * [MINIMAP] Round corner map, Xaero-style: the terrain raster is painted
 * into a dynamic GL texture and masked to a circle, framed by a brushed
 * metal ring with the N/W/E/S letters on it.
 *
 * Terrain cells carry hillshading (light from the north-west), depth-shaded
 * water and real per-block canopy colours. Live entities are drawn as pixel
 * mob heads (per-type faces for animals, villagers and zoloy). Village and
 * world-spawn markers clamp to the ring edge with the walking distance.
 *
 * The map is north-locked and glides with sub-block precision: the raster
 * is rebuilt centred on the player's block and the quad is offset by the
 * fractional remainder. Mouse wheel over the map steps through zoom levels.
 */
public final class Minimap {

    /** Zoom table: map diameter in blocks and gui pixels per block. */
    private static final int[] STRIDES = { 25, 41, 61, 121 };
    private static final int[] SCALES = { 5, 3, 2, 1 };
    private static final int MAX_STRIDE = STRIDES[STRIDES.length - 1];

    /** Terrain refresh cadence while standing still (block edits, water...). */
    private static final long REBUILD_NANOS = 500_000_000L;

    private static final int PAD = 8;            // map inset from screen corner
    private static final int RING = 3;           // metal ring thickness, gui px
    private static final int COLOR_UNKNOWN = 0xFF141824;
    private static final int OUTLINE = 0xE012141E;

    private static final int RING_EDGE = 0xFF23252F;
    private static final int RING_INNER = 0xFF2A2D3A;
    private static final int RING_LIGHT = 0xFFD4D9E4;
    private static final int RING_DARK = 0xFF61667A;

    private static final int WATER_SHALLOW = 0x3E8AE0;
    private static final int WATER_DEEP = 0x16388C;
    private static final int LOG_COLOR = 0xFF8B5A2B;

    // Marker palette
    private static final int MARK_ITEM = 0xFFF8E8A0;
    private static final int MARK_VILLAGE = 0xFFFFD24A;
    private static final int MARK_SPAWN = 0xFF7CDCFF;
    private static final int MARK_FIRE = 0xFFFF8030;
    private static final int NEEDLE = 0xFFF4F7FF;

    private static final float TOAST_SECONDS = 1.4f;

    private final Texture[] textures = new Texture[STRIDES.length];
    private int[] heights = new int[(MAX_STRIDE + 2) * (MAX_STRIDE + 2)];

    private int zoom = 1;
    private int centerX = Integer.MIN_VALUE;
    private int centerZ = Integer.MIN_VALUE;
    private long lastRebuild;
    private float toast;

    // Geometry of the last rendered map, for hit-testing and label clamping
    private int mapX, mapY, mapSize;
    private float cx, cy, outerR;

    public int pixelSize() { return STRIDES[zoom] * SCALES[zoom]; }

    /** Total gui height of the map block: circle, ring and top padding. */
    public int totalHeight() { return PAD + pixelSize() + 2 * RING + 2; }

    /** True when the gui-space cursor is over the map circle. */
    public boolean hover(float mx, float my) {
        float dx = mx - cx, dy = my - cy;
        return dx * dx + dy * dy <= outerR * outerR;
    }

    /** Step through zoom levels; positive = farther out. */
    public void zoomBy(int step) {
        int next = Math.max(0, Math.min(STRIDES.length - 1, zoom + step));
        if (next != zoom) {
            zoom = next;
            centerX = Integer.MIN_VALUE; // force rebuild
            toast = TOAST_SECONDS;
        }
    }

    public void update(double deltaTime) {
        if (toast > 0) toast = (float) Math.max(0, toast - deltaTime);
    }

    public void cleanup() {
        for (int i = 0; i < textures.length; i++) {
            if (textures[i] != null) textures[i].cleanup();
            textures[i] = null;
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * @param biomeLabel localized biome name ("" to hide the caption row)
     * @param clock      world time caption ("" to hide)
     */
    public void render(UIRenderer ui, FontRenderer font, World world,
                       double pdx, double pdy, double pdz, float yaw,
                       float daylight, String biomeLabel, String clock) {
        int stride = STRIDES[zoom];
        int scale = SCALES[zoom];
        int size = stride * scale;
        int radius = stride / 2;

        Texture tex = textures[zoom];
        if (tex == null) {
            tex = new Texture(stride, stride, new int[stride * stride]);
            textures[zoom] = tex;
        }

        long now = System.nanoTime();
        int px = (int) java.lang.Math.floor(pdx);
        int pz = (int) java.lang.Math.floor(pdz);

        int w = ui.getWidth();
        mapX = w - PAD - size;
        mapY = PAD + RING + 1;
        mapSize = size;
        cx = mapX + size / 2f;
        cy = mapY + size / 2f;
        outerR = size / 2f - 1;
        float innerR = outerR - RING;

        if (px != centerX || pz != centerZ || now - lastRebuild > REBUILD_NANOS) {
            rebuild(world, tex, px, pz, stride, radius);
        }

        ui.useSolidColor();

        // Drop shadow under the whole disc
        for (int row = -(int) outerR; row <= (int) outerR; row++) {
            float half = (float) java.lang.Math.sqrt(
                java.lang.Math.max(0, outerR * outerR - row * row));
            ui.fillRect(cx - half + 2, cy + row + 3, half * 2, 1, 0x740A0E18);
        }

        // Terrain quad (circular alpha mask lives in the texture itself),
        // gliding with the sub-block remainder
        float fracX = (float) (pdx - px);
        float fracZ = (float) (pdz - pz);
        ui.drawTexture(tex.getId(), mapX - fracX * scale, mapY - fracZ * scale,
            size, size, 0, 0, 1f, 1f, dayTint(daylight));

        // Brushed-metal ring: shaded top-to-bottom with dark edge rows
        for (int row = -(int) outerR; row <= (int) outerR; row++) {
            float half = (float) java.lang.Math.sqrt(
                java.lang.Math.max(0, outerR * outerR - row * row));
            float rr = row;
            float inner = 0;
            if (java.lang.Math.abs(rr) < innerR) {
                inner = (float) java.lang.Math.sqrt(innerR * innerR - rr * rr);
            }
            int col = (row <= -(int) outerR || row >= (int) outerR) ? RING_EDGE
                : (java.lang.Math.abs(rr) > innerR - 1) ? RING_INNER
                : ringShade(row / outerR);
            if (half <= inner) {
                ui.fillRect(cx - half, cy + row, half * 2, 1, col);
            } else {
                ui.fillRect(cx - half, cy + row, half - inner, 1, col);
                ui.fillRect(cx + inner, cy + row, half - inner, 1, col);
            }
        }

        // Cardinal letters on the ring
        drawRingLetter(ui, font, cx, cy - outerR - 1, tr("hud.map.north"));
        drawRingLetter(ui, font, cx, cy + outerR - FontRenderer.GLYPH_H + 1, tr("hud.map.south"));
        drawRingLetter(ui, font, cx - outerR - 3, cy - FontRenderer.GLYPH_H / 2f, tr("hud.map.west"));
        drawRingLetter(ui, font, cx + outerR - 3, cy - FontRenderer.GLYPH_H / 2f, tr("hud.map.east"));

        // World markers, then the compass needle
        drawSpawn(ui, world, pdx, pdz, scale, innerR);
        drawVillage(ui, world, pdx, pdz, scale, innerR, font);
        drawEntities(ui, world, pdx, pdz, scale, innerR);
        drawNeedle(ui, pdx, pdz, cx, cy, yaw, innerR);

        // Captions under the disc
        drawCaptions(ui, font, pdx, pdy, pdz, biomeLabel, clock);

        // Zoom toast fading near the bottom of the map
        if (toast > 0) {
            String name = tr("hud.map.zoom." + zoom);
            String text = String.format(tr("hud.map.zoom"), name);
            int tw = font.scaledWidth(text, 1);
            int tx = (int) (cx - tw / 2f);
            int ty = mapY + mapSize - 12;
            ui.fillRect(tx - 4, ty - 2, tw + 8, FontRenderer.LINE_HEIGHT, 0x900A0E18);
            int a = Math.min(255, (int) (toast / 0.4f * 255));
            font.drawScaledWithShadow(ui, text, tx, ty, 1, (a << 24) | 0xFFE8C860);
        }
    }

    /** Metal shade for one ring row: light at the top, dark at the bottom. */
    private static int ringShade(float t) {
        float k = Math.max(0f, Math.min(1f, (t + 1f) / 2f));
        int r = (int) (((RING_LIGHT >> 16) & 0xFF) + (((RING_DARK >> 16) & 0xFF) - ((RING_LIGHT >> 16) & 0xFF)) * k);
        int g = (int) (((RING_LIGHT >> 8) & 0xFF) + (((RING_DARK >> 8) & 0xFF) - ((RING_LIGHT >> 8) & 0xFF)) * k);
        int b = (int) ((RING_LIGHT & 0xFF) + ((RING_DARK & 0xFF) - (RING_LIGHT & 0xFF)) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void drawRingLetter(UIRenderer ui, FontRenderer font, float x, float y, String letter) {
        font.drawScaledWithShadow(ui, letter, x, (int) y, 1, 0xFFEDF0F8);
    }

    // ------------------------------------------------------------------
    // Raster
    // ------------------------------------------------------------------

    private void rebuild(World world, Texture tex, int px, int pz, int stride, int radius) {
        centerX = px;
        centerZ = pz;
        lastRebuild = System.nanoTime();

        // Height field one ring larger than the raster for edge hillshading
        int hSpan = stride + 2;
        if (heights.length < hSpan * hSpan) heights = new int[hSpan * hSpan];
        for (int r = 0; r < hSpan; r++) {
            for (int c = 0; c < hSpan; c++) {
                heights[r * hSpan + c] =
                    world.getGroundHeight(px + c - radius - 1, pz + r - radius - 1);
            }
        }

        // Everything outside the inscribed circle is transparent; the mask
        // is baked into the texture so the quad needs no clipping
        float half = stride / 2f;
        float maskR = (mapSize > 0 ? (mapSize / 2f - RING - 1) : half * 3)
            / Math.max(1, SCALES[zoom]); // texel-space radius of the disc

        int[] raster = new int[stride * stride];
        for (int r = 0; r < stride; r++) {
            for (int c = 0; c < stride; c++) {
                float ox = c + 0.5f - half;
                float oy = r + 0.5f - half;
                if (ox * ox + oy * oy > maskR * maskR) {
                    raster[r * stride + c] = 0;
                    continue;
                }
                int wx = px + c - radius;
                int wz = pz + r - radius;
                int gy = heights[(r + 1) * hSpan + (c + 1)];
                raster[r * stride + c] = cellColor(world, wx, wz, gy, r, c, hSpan);
            }
        }
        tex.update(raster);
    }

    /** Surface colour of one cell: block tint, canopy, then hillshade. */
    private int cellColor(World world, int wx, int wz, int gy, int r, int c, int hSpan) {
        if (gy < 0 || gy >= Chunk.HEIGHT) return COLOR_UNKNOWN;
        int top = world.getBlock(wx, gy, wz);
        if (top == 0) return COLOR_UNKNOWN; // unloaded chunk or void

        BlockType t = BlockType.fromId(top);
        if (t == null) return COLOR_UNKNOWN;

        int color;
        if (t == BlockType.WATER) {
            int depth = 0;
            int y = gy;
            while (y > 0 && depth < 8 && world.getBlock(wx, y - 1, wz) == BlockType.WATER.id) {
                y--;
                depth++;
            }
            color = mix(WATER_DEEP, WATER_SHALLOW, 1f - depth / 8f);
        } else {
            color = 0xFF000000 | (t.color & 0xFFFFFF);
            // Canopy above the ground: forests read as leaves, trunks as wood
            for (int yy = gy + 1; yy <= gy + 6 && yy < Chunk.HEIGHT; yy++) {
                int above = world.getBlock(wx, yy, wz);
                if (above == 0) break;
                if (BlockType.isTransparentFast(above)) continue;
                BlockType at = BlockType.fromId(above);
                if (at == null) break;
                if (isLeaf(at)) { color = 0xFF000000 | (at.color & 0xFFFFFF); break; }
                if (isLog(at)) { color = LOG_COLOR; break; }
                color = 0xFF000000 | (at.color & 0xFFFFFF);
            }
        }

        // Hillshade: light from the north-west, slopes away shaded
        int hNW = heights[r * hSpan + c];
        int hSE = heights[(r + 2) * hSpan + (c + 2)];
        float shade = java.lang.Math.max(0.72f,
            java.lang.Math.min(1.28f, 1f + (hNW - hSE) * 0.055f));
        return scale(color, shade);
    }

    private static boolean isLeaf(BlockType t) {
        return t == BlockType.OAK_LEAVES || t == BlockType.SPRUCE_LEAVES
            || t == BlockType.BIRCH_LEAVES || t == BlockType.JUNGLE_LEAVES
            || t == BlockType.AUTUMN_LEAVES || t == BlockType.CHERRY_LEAVES;
    }

    private static boolean isLog(BlockType t) {
        return t == BlockType.OAK_LOG || t == BlockType.SPRUCE_LOG
            || t == BlockType.BIRCH_LOG || t == BlockType.JUNGLE_LOG;
    }

    // ------------------------------------------------------------------
    // Markers
    // ------------------------------------------------------------------

    private void drawEntities(UIRenderer ui, World world, double pdx, double pdz,
                              int scale, float innerR) {
        int iconPx = scale >= 2 ? 2 : 1;

        for (Zoloy m : world.getMobs()) {
            if (m.isDead()) continue;
            face(ui, FACEZOLOY, pdx, pdz, m.getPosition(), scale, iconPx, innerR);
        }
        for (Villager v : world.getVillagers()) {
            face(ui, FACEVILLAGER, pdx, pdz, v.getPosition(), scale, iconPx, innerR);
        }
        for (Animal a : world.getAnimals()) {
            face(ui, animalFace(a.getType()), pdx, pdz, a.getPosition(), scale, iconPx, innerR);
        }
        for (ItemEntity e : world.getItemEntities()) {
            dot(ui, pdx, pdz, e.getPosition(), scale, innerR, MARK_ITEM);
        }
        for (TntEntity t : world.getTntEntities()) {
            // Blinking so primed TNT is unmissable
            int c = (System.nanoTime() / 150_000_000L) % 2 == 0 ? 0xFFFFFFFF : 0xFFE04838;
            dot(ui, pdx, pdz, t.getPosition(), scale, innerR, c);
        }
        // Fires flicker gently
        long flick = System.nanoTime() / 200_000_000L;
        for (World.FireBlock f : world.getFireBlocks()) {
            int c = flick % 2 == 0 ? MARK_FIRE : 0xFFFFC060;
            dot(ui, pdx, pdz, new org.joml.Vector3f(f.x + 0.5f, 0, f.z + 0.5f),
                scale, innerR, c);
        }
    }

    /** A pixel mob head pinned to the map position, with a drop shadow. */
    private void face(UIRenderer ui, String[] mask, double pdx, double pdz,
                      org.joml.Vector3f pos, int scale, int iconPx, float innerR) {
        float mx = (float) (cx + (pos.x - pdx) * scale);
        float my = (float) (cy + (pos.z - pdz) * scale);
        float dx = mx - cx, dy = my - cy;
        if (dx * dx + dy * dy > (innerR - iconPx * 4) * (innerR - iconPx * 4)) return;

        int w = mask[0].length() * iconPx;
        int h = mask.length * iconPx;
        ui.fillRect(mx - w / 2f + 1, my - h / 2f + 2, w, h, 0x66000000); // shadow
        for (int r = 0; r < mask.length; r++) {
            String row = mask[r];
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch == '.') continue;
                int col = faceColor(ch);
                ui.fillRect(mx - w / 2f + c * iconPx, my - h / 2f + r * iconPx,
                    iconPx, iconPx, col);
            }
        }
    }

    // Face palette: o outline, g zoloy skin, k eyes, v zoloy mouth,
    // s villager skin, b brow, n nose, h cow/deer hide, q cow muzzle,
    // d snout/nostril, p pig pink, w wool/white, t sheep face, y beak,
    // r wattle/parrot red, f fox coat, e fox cheek, u bear fur,
    // l bear muzzle, j parrot green.
    private static int faceColor(char ch) {
        switch (ch) {
            case 'o': return 0xFF1A1420;
            case 'g': return 0xFF5FA348; // zoloy skin
            case 'k': return 0xFF141420; // eyes
            case 'v': return 0xFF2A4A22; // zoloy mouth
            case 's': return 0xFFC89878; // villager skin
            case 'b': return 0xFF4A3428; // unibrow
            case 'n': return 0xFFB08058; // nose
            case 'h': return 0xFF6B4A33; // cow/deer hide
            case 'p': return 0xFFE8A0A0; // pig pink
            case 'q': return 0xFFD8A8A0; // cow muzzle
            case 'd': return 0xFFC87878; // snout
            case 'w': return 0xFFEDEDE6; // wool / chicken white
            case 't': return 0xFFD8C0A8; // sheep face
            case 'y': return 0xFFE89030; // beak
            case 'r': return 0xFFC03838; // wattle / parrot red
            case 'f': return 0xFFD87830; // fox coat
            case 'e': return 0xFFF0E8E0; // fox cheek
            case 'u': return 0xFF5A4028; // bear fur
            case 'l': return 0xFF8A6A48; // bear muzzle
            case 'j': return 0xFF40A850; // parrot green
            default: return 0xFF9A9A9A;
        }
    }

    private static final String[] FACEZOLOY = {
            "oooooooo",
            "oggggggo",
            "ogkggkgo",
            "oggggggo",
            "oggggggo",
            "oggvvggo",
            "oggggggo",
            "oooooooo",
    };
    private static final String[] FACEVILLAGER = {
            "oooooooo",
            "osssssso",
            "osbbbbso",
            "osksskso",
            "osnnnnso",
            "ossnnsso",
            "osssssso",
            "oooooooo",
    };
    private static final String[] FACECOW = {
            "oooooooo",
            "ohhhhhho",
            "ohkhhkho",
            "ohhhhhho",
            "ohhhhhho",
            "oqqdqqdo",
            "oqqqqqqo",
            "oooooooo",
    };
    private static final String[] FACEPIG = {
            "oooooooo",
            "oppppppo",
            "opkppkpo",
            "oppppppo",
            "opddddpo",
            "opdnndpo",
            "oppppppo",
            "oooooooo",
    };
    private static final String[] FACESHEEP = {
            "oooooooo",
            "owwwwwwo",
            "otttttto",
            "otkttkto",
            "otttttto",
            "ottkktto",
            "otttttto",
            "oooooooo",
    };
    private static final String[] FACECHICKEN = {
            "oooooooo",
            "owwwwwwo",
            "owkwwkwo",
            "owwwwwwo",
            "owwyywwo",
            "owwrrwwo",
            "owwwwwwo",
            "oooooooo",
    };
    private static final String[] FACEDEER = {
            "oooooooo",
            "ohhhhhho",
            "ohkhhkho",
            "ohhhhhho",
            "ohhhhhho",
            "ohhnnhho",
            "ohhhhhho",
            "oooooooo",
    };
    private static final String[] FACEFOX = {
            "oooooooo",
            "offffffo",
            "ofkffkfo",
            "offffffo",
            "ofeeeefo",
            "ofennefo",
            "offffffo",
            "oooooooo",
    };
    private static final String[] FACEBEAR = {
            "oooooooo",
            "ouuuuuuo",
            "oukuukuo",
            "ouuuuuuo",
            "ouuuuuuo",
            "oulllubo",
            "ouuuuuuo",
            "oooooooo",
    };
    private static final String[] FACEPARROT = {
            "oooooooo",
            "orrrrrro",
            "orkrrkro",
            "orrrrrro",
            "oryyyyro",
            "orjrrjro",
            "orrrrrro",
            "oooooooo",
    };

    private static String[] animalFace(Animal.AnimalType type) {
        switch (type) {
            case COW: return FACECOW;
            case PIG: return FACEPIG;
            case SHEEP: return FACESHEEP;
            case CHICKEN: return FACECHICKEN;
            case DEER: return FACEDEER;
            case FOX: return FACEFOX;
            case BEAR: return FACEBEAR;
            case PARROT: return FACEPARROT;
            default: return FACECOW;
        }
    }

    /** 3x3 outlined dot for small markers (drops, TNT, fire). */
    private void dot(UIRenderer ui, double pdx, double pdz, org.joml.Vector3f pos,
                     int scale, float innerR, int color) {
        float mx = (float) (cx + (pos.x - pdx) * scale);
        float my = (float) (cy + (pos.z - pdz) * scale);
        float dx = mx - cx, dy = my - cy;
        if (dx * dx + dy * dy > (innerR - 3) * (innerR - 3)) return;
        ui.fillRect(mx - 2, my - 2, 5, 5, OUTLINE);
        ui.fillRect(mx - 1, my - 1, 3, 3, color);
    }

    private void drawSpawn(UIRenderer ui, World world, double pdx, double pdz,
                           int scale, float innerR) {
        if (world.getSave() == null || world.getSave().getMeta() == null) return;
        var meta = world.getSave().getMeta();
        drawClampedDiamond(ui, meta.spawnX - pdx, meta.spawnZ - pdz, scale, innerR, MARK_SPAWN);
    }

    /** Village marker with distance, clamped to the ring edge when far. */
    private void drawVillage(UIRenderer ui, World world, double pdx, double pdz,
                             int scale, float innerR, FontRenderer font) {
        float[] v = world.findNearestVillage((float) pdx, (float) pdz);
        if (v == null) return;
        double dx = v[0] - pdx;
        double dz = v[2] - pdz;
        double dist = java.lang.Math.sqrt(dx * dx + dz * dz);

        float mx = (float) (cx + dx * scale);
        float my = (float) (cy + dz * scale);
        float rdx = mx - cx, rdy = my - cy;
        float d = (float) java.lang.Math.sqrt(rdx * rdx + rdy * rdy);
        boolean clamped = d > innerR - 6;
        if (clamped) {
            float k = (innerR - 6) / d;
            mx = cx + rdx * k;
            my = cy + rdy * k;
        }
        diamond(ui, mx, my, MARK_VILLAGE);

        String text = formatDistance(dist);
        int tw = font.scaledWidth(text, 1);
        int tx = (int) (mx + (dx > 0 ? -tw - 5 : 5));
        int ty = (int) (my + (dz > 0 ? -FontRenderer.LINE_HEIGHT - 2 : 2));
        // Keep the caption on the disc
        float lx = java.lang.Math.max(cx - innerR + 2, java.lang.Math.min(tx, cx + innerR - 2 - tw));
        float ly = java.lang.Math.max(cy - innerR + 2, java.lang.Math.min(ty, cy + innerR - 2 - FontRenderer.LINE_HEIGHT));
        font.drawScaledWithShadow(ui, text, lx, ly, 1, 0xFF000000 | MARK_VILLAGE);
    }

    private void drawClampedDiamond(UIRenderer ui, double dx, double dz,
                                    int scale, float innerR, int color) {
        if (java.lang.Math.abs(dx) * scale > innerR - 6
            || java.lang.Math.abs(dz) * scale > innerR - 6) return;
        float mx = (float) (cx + dx * scale);
        float my = (float) (cy + dz * scale);
        diamond(ui, mx, my, color);
    }

    private static String formatDistance(double blocks) {
        if (blocks < 1000) return (int) blocks + tr("hud.map.m");
        return String.format(java.util.Locale.ROOT, "%.1f", blocks / 1000.0) + tr("hud.map.km");
    }

    /** Five-rect pixel diamond with an outline pass behind it. */
    private void diamond(UIRenderer ui, float x, float y, int color) {
        for (int ox = -1; ox <= 1; ox += 2) {
            for (int oy = -1; oy <= 1; oy += 2) {
                diamondShape(ui, x + ox, y + oy, OUTLINE);
            }
        }
        diamondShape(ui, x, y, color);
    }

    private void diamondShape(UIRenderer ui, float x, float y, int color) {
        ui.fillRect(x, y - 2, 1, 1, color);
        ui.fillRect(x - 1, y - 1, 3, 1, color);
        ui.fillRect(x - 2, y, 5, 1, color);
        ui.fillRect(x - 1, y + 1, 3, 1, color);
        ui.fillRect(x, y + 2, 1, 1, color);
    }

    /** Compass needle from the centre, pointing along the view yaw. */
    private void drawNeedle(UIRenderer ui, double pdx, double pdz,
                            float ccx, float ccy, float yaw, float innerR) {
        float rad = (float) java.lang.Math.toRadians(yaw);
        float ax = (float) java.lang.Math.cos(rad);
        float az = (float) java.lang.Math.sin(rad);
        int len = (int) (innerR - 9);

        // Outline pass: the same sweep nudged one pixel in each direction
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox == 0 && oy == 0) continue;
                needleLine(ui, (int) ccx + ox, (int) ccy + oy, ax, az, len, OUTLINE);
            }
        }
        needleLine(ui, (int) ccx, (int) ccy, ax, az, len, NEEDLE);
        ui.fillRect(ccx - 1, ccy - 1, 3, 3, 0xFF0D111C);
        ui.fillRect(ccx, ccy, 1, 1, NEEDLE);
    }

    private void needleLine(UIRenderer ui, int cx0, int cy0, float ax, float az, int len, int color) {
        for (int t = 1; t <= len; t++) {
            ui.fillRect(cx0 + java.lang.Math.round(ax * t),
                cy0 + java.lang.Math.round(az * t), 2, 2, color);
        }
    }

    private void drawCaptions(UIRenderer ui, FontRenderer font,
                              double pdx, double pdy, double pdz,
                              String biomeLabel, String clock) {
        String coords = String.format("X %d  Y %d  Z %d",
            (int) java.lang.Math.floor(pdx), (int) java.lang.Math.floor(pdy),
            (int) java.lang.Math.floor(pdz));
        int cy0 = mapY + mapSize + RING + 4;
        font.drawScaledWithShadow(ui, coords, cx - font.scaledWidth(coords, 1) / 2f, cy0,
            1, 0xFFE6ECF8);

        // Second line: biome and clock
        String bio = prettyBiome(biomeLabel);
        StringBuilder sub = new StringBuilder();
        if (!bio.isEmpty()) sub.append(bio);
        if (clock != null && !clock.isEmpty()) {
            if (sub.length() > 0) sub.append("  \u00B7  ");
            sub.append(clock);
        }
        if (sub.length() > 0) {
            font.drawScaledWithShadow(ui, sub.toString(),
                cx - font.scaledWidth(sub.toString(), 1) / 2f,
                cy0 + FontRenderer.LINE_HEIGHT, 1, 0xFF000000 | MenuTheme.TEXT_SECONDARY);
        }
    }

    /**
     * The world reports names like "biome.savanna_plateau (SAVANNA_PLATEAU)";
     * try the lang key first, otherwise present the enum part as words.
     */
    private static String prettyBiome(String label) {
        if (label == null || label.isEmpty()) return "";
        String s = label.contains(" (") ? label.substring(0, label.indexOf(" (")) : label;
        if (s.indexOf('.') >= 0) {
            String localized = tr(s);
            if (localized != null && !localized.equals(s)) return localized;
            s = s.substring(s.lastIndexOf('.') + 1);
        }
        String[] parts = s.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Colour helpers
    // ------------------------------------------------------------------

    /** Day-night tint: bright neutral at noon, dim and blue at midnight. */
    private static int dayTint(float daylight) {
        float d = java.lang.Math.max(0f, java.lang.Math.min(1f, daylight));
        float k = 0.52f + 0.48f * d;
        int r = (int) (255 * k * 0.92f);
        int g = (int) (255 * k * 0.97f);
        int b = (int) java.lang.Math.min(255, 255 * k * 1.10f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int scale(int argb, float f) {
        int r = (int) java.lang.Math.min(255, ((argb >> 16) & 0xFF) * f);
        int g = (int) java.lang.Math.min(255, ((argb >> 8) & 0xFF) * f);
        int b = (int) java.lang.Math.min(255, (argb & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float t) {
        t = java.lang.Math.max(0f, java.lang.Math.min(1f, t));
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
