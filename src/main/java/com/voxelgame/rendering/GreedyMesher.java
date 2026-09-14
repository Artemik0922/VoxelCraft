package com.voxelgame.rendering;

import com.voxelgame.world.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Greedy meshing for chunk geometry.
 *
 * For each of the 6 face directions the chunk is swept slice by slice. Within a
 * slice, adjacent faces are merged into the largest possible rectangle. Two
 * faces may only merge when they share the same texture layer AND identical
 * per-corner lighting, otherwise smooth lighting and AO would be destroyed.
 *
 * UVs run 0..width / 0..height and rely on GL_REPEAT on a GL_TEXTURE_2D_ARRAY,
 * so a merged quad tiles its texture instead of stretching it.
 */
public class GreedyMesher {

    private static final int[][] FACE_OFFSETS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private static final float[][] FACE_NORMALS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    /** One visible face before merging. */
    private static final class Quad {
        int layer;
        boolean transparent;
        boolean leaves;
        // Corner lighting in slice-space order: (u0,v0) (u1,v0) (u1,v1) (u0,v1)
        float l00, l10, l11, l01;
        // Block-light corners (torch contribution), same ordering
        float b00, b10, b11, b01;
        // Per-corner ambient occlusion, same ordering
        float a00, a10, a11, a01;
        // Biome tint applied to this face
        float tintR = 1.0f, tintG = 1.0f, tintB = 1.0f;
        /** Texture mirroring: bit 0 flips U, bit 1 flips V. */
        int flip;
        /** Emissive: 1.0 for glowing blocks, 0.0 for normal. */
        float emissive;

        boolean mergeableWith(Quad o) {
            if (o == null) return false;
            // Different variant or mirroring means the merged quad would
            // repeat one tile across both blocks, undoing the variety
            if (layer != o.layer || flip != o.flip
                || transparent != o.transparent || leaves != o.leaves) return false;
            if (!eq(tintR, o.tintR) || !eq(tintG, o.tintG) || !eq(tintB, o.tintB)) return false;
            if (!eq(emissive, o.emissive)) return false;
            return eq(l00, o.l00) && eq(l10, o.l10) && eq(l11, o.l11) && eq(l01, o.l01)
                && eq(b00, o.b00) && eq(b10, o.b10) && eq(b11, o.b11) && eq(b01, o.b01)
                && eq(a00, o.a00) && eq(a10, o.a10) && eq(a11, o.a11) && eq(a01, o.a01);
        }

        private static boolean eq(float a, float b) {
            return Math.abs(a - b) < 0.002f;
        }
    }

    public static void build(Chunk chunk, World world, TextureAtlas atlas,
                             MeshGeom opaque, MeshGeom transparent, MeshGeom leaves) {
        for (int face = 0; face < 6; face++) {
            sweepFace(chunk, world, atlas, face, opaque, transparent, leaves);
        }
        // Cross plants go with the leaves: same alpha test, same need to be
        // visible from both sides
        emitCrossPlants(chunk, world, atlas, leaves);
        // [TRAN] Glass panes are thin panels in the blended pass: they must
        // sort with water and glass, not with the alpha-tested leaves
        emitGlassPanes(chunk, world, atlas, transparent);
        // [WQ] Flowing water gets a lowered surface panel; its cube top was
        // skipped in the sweep, so the panel is the only thing closing the
        // cell from above
        emitWaterSurfaces(chunk, world, atlas, transparent);
        // [GP-002] Half-height slab faces can't merge in the greedy sweep
        emitSlabs(chunk, world, atlas, opaque);
    }

    /**
     * Flowers and grass as two crossed quads.
     *
     * They go in the opaque buffer and rely on the shader's alpha test: in
     * the blended pass they would need back-to-front sorting per quad, and
     * with depth writes off they would show through each other.
     *
     * Both sides of each quad are emitted so the plant is visible from any
     * angle without disabling face culling for the whole pass.
     */
    private static void emitCrossPlants(Chunk chunk, World world, TextureAtlas atlas,
                                        MeshGeom buf) {
        int originX = chunk.getWorldX();
        int originZ = chunk.getWorldZ();

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    int id = chunk.getBlock(x, y, z);
                    if (!ChunkMeshBuilder.isCrossPlant(id)) continue;

                    int wx = originX + x;
                    int wz = originZ + z;

                    // [GP-020] Doors must not jitter, sway or get pushed
                    // by the player - they are furniture, not foliage.
                    boolean door = id == BlockType.OAK_DOOR_OPEN.id
                                || id == BlockType.OAK_DOOR.id;

                    // [BED] Beds render as furniture: frame, mattress,
                    // pillow and boards, oriented by the other half.
                    boolean bed = id == BlockType.BED.id
                               || id == BlockType.BED_HEAD.id;

                    if (bed) {
                        addBedFurniture(buf, x, y, z, world, wx, wz, atlas, id);
                        continue;
                    }

                    if (door) {
                        // Vanilla door: a 3/16-thick panel. Closed it stands
                        // in the wall plane; open it has swung 90 degrees
                        // and hugs a jamb of the doorway.
                        addDoorBox(buf, x, y, z, world, wx, wz, atlas,
                            id == BlockType.OAK_DOOR_OPEN.id);
                        continue;
                    }

                    int layer = atlas.getSlot(id, 2, wx, y, wz, chunk);

                    // Plants take the light of their own cell, no AO
                    float light = world.getLight(wx, y, wz) / (float) Chunk.MAX_LIGHT;
                    float shade = 0.18f + 0.82f * light;
                    float blockLight = world.getBlockLight(wx, y, wz) / (float) Chunk.MAX_LIGHT;

                    float[] tint = BiomeColors.tintFor(world, id, 2, wx, y, wz);

                    // Small offset so a field of grass is not a perfect grid.
                    // Kept well under the 0.15 margin so the plant never
                    // pokes outside its own block.
                    float jx = door ? 0.0f : (hash(wx, y, wz, 3) - 0.5f) * 0.12f;
                    float jz = door ? 0.0f : (hash(wx, y, wz, 7) - 0.5f) * 0.12f;

                    float height = plantHeight(id, wx, y, wz, chunk);

                    addCrossQuad(buf, x + jx, y, z + jz, layer, shade, blockLight, tint, false, height, !door);
                    addCrossQuad(buf, x + jx, y, z + jz, layer, shade, blockLight, tint, true, height, !door);
                }
            }
        }
    }

    /**
     * One diagonal of the cross, emitted double-sided.
     *
     * Both planes run corner to corner across the block footprint, from
     * 0.15 to 0.85, and stand on the block floor: the base sits exactly at
     * the block's own Y so nothing is centred or sunk.
     *
     * @param height plant height in blocks, measured up from the floor
     */
    private static void addCrossQuad(MeshGeom buf, float x, float y, float z,
                                     int layer, float shade, float blockLight, float[] tint,
                                     boolean secondDiagonal, float height,
                                     boolean sway) {
        final float LO = 0.08f;
        final float HI = 0.92f;

        float x0, z0, x1, z1;
        if (!secondDiagonal) {
            // P1: (0.15,0.15) -> (0.85,0.85)
            x0 = x + LO; z0 = z + LO;
            x1 = x + HI; z1 = z + HI;
        } else {
            // P2: (0.85,0.15) -> (0.15,0.85)
            x0 = x + HI; z0 = z + LO;
            x1 = x + LO; z1 = z + HI;
        }

        // Normal points along the quad's own diagonal
        float nx = (z1 - z0), nz = -(x1 - x0);
        float len = (float) Math.sqrt(nx * nx + nz * nz);
        if (len > 0) { nx /= len; nz /= len; }

        for (int side = 0; side < 2; side++) {
            int base = buf.positions.size() / 3;
            float sign = (side == 0) ? 1 : -1;

            // corners: bottom-left, bottom-right, top-right, top-left.
            // y is the block floor, so the plant stands on the ground.
            float[][] corners = {
                {x0, y,          z0},
                {x1, y,          z1},
                {x1, y + height, z1},
                {x0, y + height, z0}
            };
            // The atlas is uploaded bottom-up, so v=0 is the BOTTOM of the
            // source sprite. Bottom vertices therefore take v=0 and top
            // vertices v=1; the reverse drew every plant upside down, which
            // put the flower head on the ground and the stem in the air.
            float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

            int[] order = (side == 0) ? new int[]{0, 1, 2, 3} : new int[]{1, 0, 3, 2};

            for (int i = 0; i < 4; i++) {
                int c = order[i];
                buf.positions.add(corners[c][0]);
                buf.positions.add(corners[c][1]);
                buf.positions.add(corners[c][2]);

                buf.texCoords.add(uvs[c][0]);
                buf.texCoords.add(uvs[c][1]);

                buf.normals.add(nx * sign);
                buf.normals.add(0.0f);
                buf.normals.add(nz * sign);

                buf.colors.add(tint[0] * shade);
                buf.colors.add(tint[1] * shade);
                buf.colors.add(tint[2] * shade);

                buf.ao.add(1.0f);
                buf.layers.add((float) layer);

                // Roots stay planted, tips move: corners 2 and 3 are the top
                buf.wave.add((sway && (c == 2 || c == 3)) ? 1.0f : 0.0f);
                buf.blockLight.add(blockLight);
            }

            buf.indices.add(base);     buf.indices.add(base + 1); buf.indices.add(base + 2);
            buf.indices.add(base);     buf.indices.add(base + 2); buf.indices.add(base + 3);
        }
    }

    /**
     * [MC] Door panel: a thin (3/16) box of full block height, exactly like
     * a vanilla door.
     *
     * The wall is found by scanning the four horizontal neighbours of the
     * door cell at the door's own height: cells left/right of the door mean
     * the wall runs along X (closed panel normal along Z), cells front/back
     * mean the wall runs along Z (normal along X). Free-standing doors are
     * centred in their cell.
     *
     * An OPEN door has swung 90 degrees: its panel now spans the doorway
     * depth and hugs one jamb, the side where the wall continues - vanilla
     * reads the hinge from placement data the chunk does not store, so the
     * solid side wins and defaults to the near edge.
     *
     * The upper half (the block below is also a door) uses the window tile,
     * the lower half the handle tile.
     */
    private static final float DOOR_T = 0.1875f; // vanilla 3/16

    private static void addDoorBox(MeshGeom buf, int x, int y, int z,
                                   World world, int wx, int wz,
                                   TextureAtlas atlas, boolean open) {
        boolean sideXMinus = world.isSolid(wx - 1, y, wz);
        boolean sideXPlus  = world.isSolid(wx + 1, y, wz);
        boolean sideZMinus = world.isSolid(wx, y, wz - 1);
        boolean sideZPlus  = world.isSolid(wx, y, wz + 1);

        // true: the wall runs along X, the closed panel lies in the X-Y
        // plane (normal along Z)
        boolean facingZ;
        if (sideXMinus || sideXPlus) facingZ = true;
        else if (sideZMinus || sideZPlus) facingZ = false;
        else facingZ = true; // free-standing

        boolean top = world.getBlock(wx, y - 1, wz) == BlockType.OAK_DOOR_OPEN.id
                   || world.getBlock(wx, y - 1, wz) == BlockType.OAK_DOOR.id;
        int layer = atlas.getSlotByName(top ? "oak_door_top" : "oak_door_bottom");

        float light = world.getLight(wx, y, wz) / (float) Chunk.MAX_LIGHT;
        float shade = 0.18f + 0.82f * light;
        float blockLight = world.getBlockLight(wx, y, wz) / (float) Chunk.MAX_LIGHT;
        float[] tint = BiomeColors.tintFor(world, BlockType.OAK_DOOR_OPEN.id, 2, wx, y, wz);

        float x0, x1, z0, z1;
        if (!open) {
            if (facingZ) {
                x0 = x; x1 = x + 1;
                z0 = z; z1 = z + DOOR_T;
            } else {
                x0 = x; x1 = x + DOOR_T;
                z0 = z; z1 = z + 1;
            }
        } else {
            // Swung open: perpendicular to the wall, hugging a jamb
            if (facingZ) {
                if (sideXMinus)      { x0 = x; x1 = x + DOOR_T; }
                else if (sideXPlus)  { x0 = x + 1 - DOOR_T; x1 = x + 1; }
                else                 { x0 = x; x1 = x + DOOR_T; }
                z0 = z; z1 = z + 1;
            } else {
                x0 = x; x1 = x + 1;
                if (sideZMinus)      { z0 = z; z1 = z + DOOR_T; }
                else if (sideZPlus)  { z0 = z + 1 - DOOR_T; z1 = z + 1; }
                else                 { z0 = z; z1 = z + DOOR_T; }
            }
        }

        emitDoorBox(buf, x0, y, z0, x1, y + 1, z1,
            layer, shade, blockLight, tint);
    }

    /** Six faces of the door panel; the two broad faces carry the full
     *  door texture, the four rim faces thin slices of it. */
    private static void emitDoorBox(MeshGeom buf, float x0, float y0, float z0,
                                    float x1, float y1, float z1,
                                    int layer, float shade, float blockLight,
                                    float[] tint) {
        // +Z / -Z broad faces (drawn only when they are the wide ones)
        if (x1 - x0 > z1 - z0) {
            addDoorQuad(buf, layer, shade, blockLight, tint,
                new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                new float[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}},
                0f, 0f, 1f, false);
            addDoorQuad(buf, layer, shade, blockLight, tint,
                new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}},
                new float[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}},
                0f, 0f, -1f, false);
        } else {
            addDoorQuad(buf, layer, shade, blockLight, tint,
                new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}},
                new float[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}},
                1f, 0f, 0f, false);
            addDoorQuad(buf, layer, shade, blockLight, tint,
                new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}},
                new float[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}},
                -1f, 0f, 0f, false);
        }

        // Rim faces: thin strips sampling the tile edge
        float e = 0.03f;
        addDoorQuad(buf, layer, shade, blockLight, tint,
            new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}},
            new float[][]{{0, 0}, {e, 0}, {e, e}, {0, e}},
            0f, 1f, 0f, false);
        addDoorQuad(buf, layer, shade, blockLight, tint,
            new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}},
            new float[][]{{0, 1 - e}, {e, 1 - e}, {e, 1}, {0, 1}},
            0f, -1f, 0f, false);
        if (x1 - x0 > z1 - z0) {
            addDoorQuad(buf, layer, shade, blockLight, tint,
                new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}},
                new float[][]{{0, 0}, {0, 1}, {e, 1}, {e, 0}},
                -1f, 0f, 0f, false);
            addDoorQuad(buf, layer, shade, blockLight, tint,
                new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}},
                new float[][]{{1 - e, 0}, {1, 0}, {1, 1}, {1 - e, 1}},
                1f, 0f, 0f, false);
        } else {
            addDoorQuad(buf, layer, shade, blockLight, tint,
                new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}},
                new float[][]{{0, 0}, {e, 0}, {e, 1}, {0, 1}},
                0f, 0f, -1f, false);
            addDoorQuad(buf, layer, shade, blockLight, tint,
                new float[][]{{x1, y0, z1}, {x0, y0, z1}, {x0, y1, z1}, {x1, y1, z1}},
                new float[][]{{0, 0}, {e, 0}, {e, 1}, {0, 1}},
                0f, 0f, 1f, false);
        }
    }

    /** One textured quad with an explicit normal and UV corners. */
    private static void addDoorQuad(MeshGeom buf, int layer, float shade,
                                    float blockLight, float[] tint,
                                    float[][] corners, float[][] uvs,
                                    float nx, float ny, float nz,
                                    boolean flip) {
        int base = buf.positions.size() / 3;
        int[] order = flip ? new int[]{1, 0, 3, 2} : new int[]{0, 1, 2, 3};

        for (int i = 0; i < 4; i++) {
            int c = order[i];
            buf.positions.add(corners[c][0]);
            buf.positions.add(corners[c][1]);
            buf.positions.add(corners[c][2]);

            buf.texCoords.add(uvs[c][0]);
            buf.texCoords.add(uvs[c][1]);

            buf.normals.add(nx);
            buf.normals.add(ny);
            buf.normals.add(nz);

            buf.colors.add(tint[0] * shade);
            buf.colors.add(tint[1] * shade);
            buf.colors.add(tint[2] * shade);

            buf.ao.add(1.0f);
            buf.layers.add((float) layer);
            buf.wave.add(0.0f);
            buf.blockLight.add(blockLight);
        }

        buf.indices.add(base);     buf.indices.add(base + 1); buf.indices.add(base + 2);
        buf.indices.add(base);     buf.indices.add(base + 2); buf.indices.add(base + 3);
    }

    /**
     * [TRAN] Glass panes: thin, passable partitions drawn in the blended
     * pass. A pane connects to a neighbouring pane, to glass, or to any
     * solid block on the same level: the panel then spans the full cell
     * along that axis. A pane with connections on both axes becomes a
     * cross; a free-standing pane is a short stub along X.
     */
    private static void emitGlassPanes(Chunk chunk, World world, TextureAtlas atlas,
                                       MeshGeom buf) {
        int originX = chunk.getWorldX();
        int originZ = chunk.getWorldZ();

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    int id = chunk.getBlock(x, y, z);
                    if (!ChunkMeshBuilder.isPane(id)) continue;
                    addGlassPane(buf, x, y, z, world, originX + x, originZ + z, atlas);
                }
            }
        }
    }

    /**
     * [TRAN] One pane cell: a double-sided 2-px panel in the cell's X-Y or
     * Z-Y plane, spanning the cell when connected and shrinking to a stub
     * when free. Both faces are emitted (blend pass runs with culling off,
     * but this keeps the panel readable without relying on that).
     */
    private static void addGlassPane(MeshGeom buf, int x, int y, int z,
                                     World world, int wx, int wz,
                                     TextureAtlas atlas) {
        boolean connectXMinus = paneConnects(world, wx - 1, y, wz);
        boolean connectXPlus  = paneConnects(world, wx + 1, y, wz);
        boolean connectZMinus = paneConnects(world, wx, y, wz - 1);
        boolean connectZPlus  = paneConnects(world, wx, y, wz + 1);

        boolean alongX = connectXMinus || connectXPlus;
        boolean alongZ = connectZMinus || connectZPlus;

        final float T = 0.4375f; // 2 px half-thickness offset from centre
        final float STUB = 0.25f;

        int layer = atlas.getSlotByName("glass");

        float light = world.getLight(wx, y, wz) / (float) Chunk.MAX_LIGHT;
        float shade = 0.18f + 0.82f * light;
        float blockLight = world.getBlockLight(wx, y, wz) / (float) Chunk.MAX_LIGHT;
        float[] tint = BiomeColors.tintFor(world, BlockType.GLASS_PANE.id, 2, wx, y, wz);

        // A cross emits two panels (X plane and Z plane); otherwise one.
        int planes = (alongX && alongZ) ? 2 : 1;
        boolean[] planeIsX = {true, false};
        boolean[] planeAlong = {alongX, alongZ};

        for (int p = 0; p < planes; p++) {
            boolean planeX = planeIsX[p];   // true: panel in the X-Y plane
            boolean span = planeAlong[p];   // true: panel spans the full axis

            float x0, x1, z0, z1;
            if (planeX) {
                x0 = span ? x : x + 0.5f - STUB;
                x1 = span ? x + 1 : x + 0.5f + STUB;
                z0 = z + T; z1 = z + T;
            } else {
                x0 = x + T; x1 = x + T;
                z0 = span ? z : z + 0.5f - STUB;
                z1 = span ? z + 1 : z + 0.5f + STUB;
            }

            for (int side = 0; side < 2; side++) {
                int base = buf.positions.size() / 3;
                float sign = (side == 0) ? 1 : -1;
                float nx = planeX ? 0.0f : sign;
                float nz = planeX ? sign : 0.0f;

                float[][] corners = {
                    {x0, y,     z0},
                    {x1, y,     z1},
                    {x1, y + 1, z1},
                    {x0, y + 1, z0}
                };
                float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                int[] order = (side == 0) ? new int[]{0, 1, 2, 3} : new int[]{1, 0, 3, 2};

                for (int i = 0; i < 4; i++) {
                    int c = order[i];
                    buf.positions.add(corners[c][0]);
                    buf.positions.add(corners[c][1]);
                    buf.positions.add(corners[c][2]);
                    buf.texCoords.add(uvs[c][0]);
                    buf.texCoords.add(uvs[c][1]);
                    buf.normals.add(nx);
                    buf.normals.add(0.0f);
                    buf.normals.add(nz);
                    buf.colors.add(tint[0] * shade);
                    buf.colors.add(tint[1] * shade);
                    buf.colors.add(tint[2] * shade);
                    buf.ao.add(1.0f);
                    buf.layers.add((float) layer);
                    buf.wave.add(0.0f);
                    buf.blockLight.add(blockLight);
                }

                buf.indices.add(base);     buf.indices.add(base + 1); buf.indices.add(base + 2);
                buf.indices.add(base);     buf.indices.add(base + 2); buf.indices.add(base + 3);
            }
        }
    }

    /** [TRAN] Does the pane connect to the cell at (x,y,z)? */
    private static boolean paneConnects(World world, int x, int y, int z) {
        int id = world.getBlock(x, y, z);
        return id == BlockType.GLASS_PANE.id
            || id == BlockType.GLASS.id
            || world.isSolid(x, y, z);
    }

    /**
     * [WQ] Water surface panels. Flowing water (level 1-7) has no cube top
     * face; this pass closes the cell with a thin panel at
     * y + (1 - level/8), so a thin film sits visibly lower than a full
     * source block. Only the topmost water cell of a column gets a panel.
     */
    private static void emitWaterSurfaces(Chunk chunk, World world, TextureAtlas atlas,
                                          MeshGeom buf) {
        int ox = chunk.getWorldX();
        int oz = chunk.getWorldZ();

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    if (chunk.getBlock(x, y, z) != BlockType.WATER.id) continue;
                    int level = chunk.getWaterLevel(x, y, z);
                    if (level < 1 || level > 7) continue;
                    if (world.getBlock(ox + x, y + 1, oz + z) == BlockType.WATER.id) continue;

                    Quad q = buildQuad(chunk, world, atlas, 2, x, y, z, BlockType.WATER.id);
                    float h = 1.0f - level / 8.0f;
                    emitQuad(buf, 2, 1, 0, 2, y + h, x, z, 1, 1, q, 1.0f);
                }
            }
        }
    }

    /**
     * [BED] Bed furniture: the half-cell is rebuilt as a wooden frame, a
     * red mattress, a pillow and boards. The bed's direction is inferred
     * from the other half's position - the chunk storage keeps no metadata
     * - so a bed always shows the pillow at the head end.
     */
    private static void addBedFurniture(MeshGeom buf, int x, int y, int z,
                                        World world, int wx, int wz,
                                        TextureAtlas atlas, int id) {
        boolean head = id == BlockType.BED_HEAD.id;

        // Scan the four horizontal neighbours for the other half. The head
        // end of the head half points away from the foot half (and vice
        // versa for the foot end of the foot half).
        int endX = -1, endZ = 0;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int n = world.getBlock(wx + d[0], y, wz + d[1]);
            boolean other = head ? (n == BlockType.BED.id)
                                 : (n == BlockType.BED_HEAD.id);
            if (other) {
                endX = -d[0];
                endZ = -d[1];
                break;
            }
        }
        boolean axisX = endX != 0;
        float e = axisX ? (endX + 1) * 0.5f : (endZ + 1) * 0.5f;

        int frame = atlas.getSlotByName("bed_frame");
        int mattressSide = atlas.getSlotByName("bed_mattress_side");
        int mattressTop = atlas.getSlotByName("bed_mattress_top");
        int pillow = atlas.getSlotByName("bed_pillow");
        int headboard = atlas.getSlotByName("bed_headboard");
        int footboard = atlas.getSlotByName("bed_footboard");

        float light = world.getLight(wx, y, wz) / (float) Chunk.MAX_LIGHT;
        float shade = 0.18f + 0.82f * light;
        float blockLight = world.getBlockLight(wx, y, wz) / (float) Chunk.MAX_LIGHT;

        float x0 = x, x1 = x + 1, z0 = z, z1 = z + 1;
        float fLo = 0.0f, fHi = 0.28f;       // frame band
        float mLo = 0.28f, mHi = 0.85f;      // mattress band
        float plLo = 0.42f, plHi = 0.70f;    // pillow band

        // Frame sides
        addBedQuad(buf, new float[][]{{x0, fLo, z0}, {x1, fLo, z0}, {x1, fHi, z0}, {x0, fHi, z0}}, frame, shade, blockLight);
        addBedQuad(buf, new float[][]{{x1, fLo, z0}, {x1, fLo, z1}, {x1, fHi, z1}, {x1, fHi, z0}}, frame, shade, blockLight);
        addBedQuad(buf, new float[][]{{x1, fLo, z1}, {x0, fLo, z1}, {x0, fHi, z1}, {x1, fHi, z1}}, frame, shade, blockLight);
        addBedQuad(buf, new float[][]{{x0, fLo, z1}, {x0, fLo, z0}, {x0, fHi, z0}, {x0, fHi, z1}}, frame, shade, blockLight);
        // Mattress sides
        addBedQuad(buf, new float[][]{{x0, mLo, z0}, {x1, mLo, z0}, {x1, mHi, z0}, {x0, mHi, z0}}, mattressSide, shade, blockLight);
        addBedQuad(buf, new float[][]{{x1, mLo, z0}, {x1, mLo, z1}, {x1, mHi, z1}, {x1, mHi, z0}}, mattressSide, shade, blockLight);
        addBedQuad(buf, new float[][]{{x1, mLo, z1}, {x0, mLo, z1}, {x0, mHi, z1}, {x1, mHi, z1}}, mattressSide, shade, blockLight);
        addBedQuad(buf, new float[][]{{x0, mLo, z1}, {x0, mLo, z0}, {x0, mHi, z0}, {x0, mHi, z1}}, mattressSide, shade, blockLight);
        // Mattress top
        addBedQuad(buf, new float[][]{{x0, mHi, z0}, {x1, mHi, z0}, {x1, mHi, z1}, {x0, mHi, z1}}, mattressTop, shade, blockLight);

        if (head) {
            // Pillow at the head end
            float lo = e < 0.5f ? 0.08f : 0.38f;
            float hi = lo + 0.54f;
            float pw0, pw1, pd0, pd1;
            if (axisX) { pw0 = x0 + lo; pw1 = x0 + hi; pd0 = z0 + 0.22f; pd1 = z0 + 0.78f; }
            else       { pw0 = z0 + lo; pw1 = z0 + hi; pd0 = x0 + 0.22f; pd1 = x0 + 0.78f; }
            if (axisX) {
                addBedQuad(buf, new float[][]{{pw0, plLo, pd0}, {pw1, plLo, pd0}, {pw1, plHi, pd0}, {pw0, plHi, pd0}}, pillow, shade, blockLight);
                addBedQuad(buf, new float[][]{{pw1, plLo, pd0}, {pw1, plLo, pd1}, {pw1, plHi, pd1}, {pw1, plHi, pd0}}, pillow, shade, blockLight);
                addBedQuad(buf, new float[][]{{pw1, plLo, pd1}, {pw0, plLo, pd1}, {pw0, plHi, pd1}, {pw1, plHi, pd1}}, pillow, shade, blockLight);
                addBedQuad(buf, new float[][]{{pw0, plLo, pd1}, {pw0, plLo, pd0}, {pw0, plHi, pd0}, {pw0, plHi, pd1}}, pillow, shade, blockLight);
                addBedQuad(buf, new float[][]{{pw0, plHi, pd0}, {pw1, plHi, pd0}, {pw1, plHi, pd1}, {pw0, plHi, pd1}}, pillow, shade, blockLight);
            } else {
                addBedQuad(buf, new float[][]{{pd0, plLo, pw0}, {pd1, plLo, pw0}, {pd1, plHi, pw0}, {pd0, plHi, pw0}}, pillow, shade, blockLight);
                addBedQuad(buf, new float[][]{{pd1, plLo, pw0}, {pd1, plLo, pw1}, {pd1, plHi, pw1}, {pd1, plHi, pw0}}, pillow, shade, blockLight);
                addBedQuad(buf, new float[][]{{pd1, plLo, pw1}, {pd0, plLo, pw1}, {pd0, plHi, pw1}, {pd1, plHi, pw1}}, pillow, shade, blockLight);
                addBedQuad(buf, new float[][]{{pd0, plLo, pw1}, {pd0, plLo, pw0}, {pd0, plHi, pw0}, {pd0, plHi, pw1}}, pillow, shade, blockLight);
                addBedQuad(buf, new float[][]{{pd0, plHi, pw0}, {pd1, plHi, pw0}, {pd1, plHi, pw1}, {pd0, plHi, pw1}}, pillow, shade, blockLight);
            }
            // Headboard at the head end
            if (axisX) {
                float p = x0 + (e < 0.5f ? 0.0f : 0.90f);
                addBedQuad(buf, new float[][]{{p, mLo, z0}, {p, mLo, z1}, {p, 1.0f, z1}, {p, 1.0f, z0}}, headboard, shade, blockLight);
            } else {
                float p = z0 + (e < 0.5f ? 0.0f : 0.90f);
                addBedQuad(buf, new float[][]{{x0, mLo, p}, {x1, mLo, p}, {x1, 1.0f, p}, {x0, 1.0f, p}}, headboard, shade, blockLight);
            }
        } else {
            // Footboard at the foot end
            if (axisX) {
                float p = x0 + (e < 0.5f ? 0.0f : 0.92f);
                addBedQuad(buf, new float[][]{{p, mLo, z0}, {p, mLo, z1}, {p, 0.55f, z1}, {p, 0.55f, z0}}, footboard, shade, blockLight);
            } else {
                float p = z0 + (e < 0.5f ? 0.0f : 0.92f);
                addBedQuad(buf, new float[][]{{x0, mLo, p}, {x1, mLo, p}, {x1, 0.55f, p}, {x0, 0.55f, p}}, footboard, shade, blockLight);
            }
        }
    }

    /**
     * One furniture quad, emitted double-sided so the passable bed reads
     * correctly from inside its own cell. UVs span the whole tile, v=0 at
     * the bottom of the sprite.
     */
    private static void addBedQuad(MeshGeom buf, float[][] corners, int layer,
                                   float shade, float blockLight) {
        for (int side = 0; side < 2; side++) {
            int base = buf.positions.size() / 3;
            float sign = (side == 0) ? 1 : -1;

            float ax = corners[1][0] - corners[0][0];
            float ay = corners[1][1] - corners[0][1];
            float az = corners[1][2] - corners[0][2];
            float bx = corners[3][0] - corners[0][0];
            float by = corners[3][1] - corners[0][1];
            float bz = corners[3][2] - corners[0][2];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 0) { nx /= len; ny /= len; nz /= len; }

            float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
            int[] order = (side == 0) ? new int[]{0, 1, 2, 3} : new int[]{1, 0, 3, 2};

            for (int i = 0; i < 4; i++) {
                int c = order[i];
                buf.positions.add(corners[c][0]);
                buf.positions.add(corners[c][1]);
                buf.positions.add(corners[c][2]);
                buf.texCoords.add(uvs[c][0]);
                buf.texCoords.add(uvs[c][1]);
                buf.normals.add(nx * sign);
                buf.normals.add(ny * sign);
                buf.normals.add(nz * sign);
                buf.colors.add(shade);
                buf.colors.add(shade);
                buf.colors.add(shade);
                buf.ao.add(1.0f);
                buf.layers.add((float) layer);
                buf.wave.add(0.0f);
                buf.blockLight.add(blockLight);
            }

            buf.indices.add(base);     buf.indices.add(base + 1); buf.indices.add(base + 2);
            buf.indices.add(base);     buf.indices.add(base + 2); buf.indices.add(base + 3);
        }
    }
    private static float plantHeight(int blockId, int wx, int wy, int wz, Chunk chunk) {
        BlockType type = BlockType.fromId(blockId);
        float jitter = hash(wx, wy, wz, 41);

        return switch (type) {
            case DANDELION, POPPY -> 0.40f;
            case DEAD_BUSH -> 0.55f;
            // Vanilla tufts reach most of the block and vary in height, so
            // a meadow reads as uneven grass, not a crew-cut hedge
            case GRASS_PLANT -> 0.70f + jitter * 0.30f;
            // [GP-020] Full-height panel
            case OAK_DOOR_OPEN -> 1.0f;
            // Crops grow with their stage: 0.25 (sprouts) to 1.0 (mature).
            // [CR] The real stage from cropMeta drives the height, so a
            // ripened field stands tall and a freshly planted one is flat.
            case WHEAT, CARROT, POTATO ->
                    0.25f + TextureAtlas.stageOf(blockId, wx, wy, wz, chunk) / 7.0f * 0.75f;
            // [GP-067] Torch: a 1-block-tall standing sprite
            case TORCH_PLACEHOLDER -> 1.0f;
            // [GP-073] Flames reach most of the way up the cell
            case FIRE -> 0.85f + jitter * 0.15f;
            // [WG] Expansion plants: bamboo stands tall, rails lie low
            case BAMBOO, END_PORTAL -> 1.0f;
            case SEAGRASS -> 0.55f + jitter * 0.10f;
            case CORAL -> 0.45f + jitter * 0.10f;
            case RAILS -> 0.12f;
            // [BIOME] Crystal shards stand tall, heights vary per cluster
            case CRYSTAL -> 0.75f + jitter * 0.25f;
            default -> 0.90f;
        };
    }

    private static float hash(int x, int y, int z, int salt) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177 + salt * 1442695040;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= (h >> 16);
        return (h & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    /**
     * Sweep every slice perpendicular to one face direction.
     */
    private static void sweepFace(Chunk chunk, World world, TextureAtlas atlas, int face,
                                  MeshGeom opaque, MeshGeom transparent, MeshGeom leaves) {
        // Slice axis is the face's dominant axis; u/v are the two others.
        final int axis = (face <= 1) ? 0 : (face <= 3) ? 1 : 2;
        final int uAxis = (axis == 0) ? 2 : 0;
        final int vAxis = (axis == 1) ? 2 : 1;

        final int axisSize = sizeOf(axis);
        final int uSize = sizeOf(uAxis);
        final int vSize = sizeOf(vAxis);

        Quad[] mask = new Quad[uSize * vSize];

        for (int slice = 0; slice < axisSize; slice++) {
            // ---- Build the mask of visible faces for this slice ----
            boolean any = false;
            java.util.Arrays.fill(mask, null);

            // Reuse single position array to avoid per-cell allocation
            int[] p = new int[3];
            for (int v = 0; v < vSize; v++) {
                for (int u = 0; u < uSize; u++) {
                    p[axis] = slice;
                    p[uAxis] = u;
                    p[vAxis] = v;

                    int blockId = chunk.getBlock(p[0], p[1], p[2]);
                    if (blockId == 0) continue;
                    // [WQ] Flowing water (level >= 1) is closed from above by
                    // a lowered surface panel, not by the cube's top face
                    if (face == 2 && blockId == BlockType.WATER.id
                        && world.getWaterLevel(chunk.getWorldX() + p[0], p[1],
                                               chunk.getWorldZ() + p[2]) >= 1) continue;
                    // Cross plants are emitted separately by emitCrossPlants(); cube
                    // faces here would wrap them in an invisible sprite box.
                    if (ChunkMeshBuilder.isCrossPlant(blockId)) continue;
                    // [TRAN] Glass panes are emitted as thin panels, not cubes
                    if (ChunkMeshBuilder.isPane(blockId)) continue;
                    // [GP-002] Slabs are emitted by emitSlabs() with half-height faces
                    if (isSlab(blockId)) continue;

                    int nx = p[0] + FACE_OFFSETS[face][0];
                    int ny = p[1] + FACE_OFFSETS[face][1];
                    int nz = p[2] + FACE_OFFSETS[face][2];

                    int neighborId = neighbor(chunk, world, nx, ny, nz);
                    // [GP-002] Slabs don't occlude full blocks: the faces in
                    // front of their lower half stay visible.
                    if (isSlab(neighborId)) neighborId = 0;
                    if (!ChunkMeshBuilder.facePassesCrossChunk(
                            world, chunk.getWorldX() + p[0], p[1], chunk.getWorldZ() + p[2],
                            face, blockId, neighborId)) continue;

                    Quad q = buildQuad(chunk, world, atlas, face, p[0], p[1], p[2], blockId);
                    mask[v * uSize + u] = q;
                    any = true;
                }
            }

            if (!any) continue;

            // ---- Merge the mask into maximal rectangles ----
            for (int v = 0; v < vSize; v++) {
                for (int u = 0; u < uSize; ) {
                    Quad start = mask[v * uSize + u];
                    if (start == null) { u++; continue; }

                    // Grow along u
                    int width = 1;
                    while (u + width < uSize && start.mergeableWith(mask[v * uSize + u + width])) {
                        width++;
                    }

                    // Grow along v, one full row at a time
                    int height = 1;
                    outer:
                    while (v + height < vSize) {
                        for (int k = 0; k < width; k++) {
                            if (!start.mergeableWith(mask[(v + height) * uSize + u + k])) break outer;
                        }
                        height++;
                    }

                    emitQuad(start.transparent ? transparent : (start.leaves ? leaves : opaque),
                             face, axis, uAxis, vAxis,
                             slice + ((face == 0 || face == 2 || face == 4) ? 1.0f : 0.0f),
                             u, v, width, height, start, 1.0f);

                    // Consume the merged region
                    for (int dv = 0; dv < height; dv++) {
                        for (int du = 0; du < width; du++) {
                            mask[(v + dv) * uSize + u + du] = null;
                        }
                    }

                    u += width;
                }
            }
        }
    }

    /**
     * Lighting, tint, texture layer and flags for one visible face. Shared by
     * the greedy sweep and the slab pass.
     */
    private static Quad buildQuad(Chunk chunk, World world, TextureAtlas atlas, int face,
                                  int px, int py, int pz, int blockId) {
        final int axis = (face <= 1) ? 0 : (face <= 3) ? 1 : 2;
        final int uAxis = (axis == 0) ? 2 : 0;
        final int vAxis = (axis == 1) ? 2 : 1;

        Quad q = new Quad();

        float shade = faceBrightness(face);
        int wx = chunk.getWorldX() + px;
        int wy = py;
        int wz = chunk.getWorldZ() + pz;

        // Variant + mirroring are chosen from the block position,
        // so neighbouring blocks of one material differ and the
        // tiling pattern stops being visible on big surfaces.
        q.layer = atlas.getSlot(blockId, face, wx, wy, wz);
        q.flip = atlas.canFlip(blockId, face) ? atlas.flipFor(wx, wy, wz) : 0;

        // Only water/glass/ice get alpha blending; leaves and props
        // stay in the opaque pass and rely on alpha testing, so
        // they never turn see-through at distance.
        q.transparent = ChunkMeshBuilder.needsBlendPass(blockId);
        q.leaves = ChunkMeshBuilder.needsCutoutPass(blockId);
        q.emissive = BlockType.isEmissive(blockId) ? 1.0f : 0.0f;

        // Light sampling (single pass, shared occlusion data)
        q.l00 = ChunkMeshBuilder.sampleLight(world, wx, wy, wz, face, uAxis, vAxis, -1, -1) * shade;
        q.l10 = ChunkMeshBuilder.sampleLight(world, wx, wy, wz, face, uAxis, vAxis,  1, -1) * shade;
        q.l11 = ChunkMeshBuilder.sampleLight(world, wx, wy, wz, face, uAxis, vAxis,  1,  1) * shade;
        q.l01 = ChunkMeshBuilder.sampleLight(world, wx, wy, wz, face, uAxis, vAxis, -1,  1) * shade;

        // Block light corners: how much of the light comes from lamps
        q.b00 = ChunkMeshBuilder.sampleBlockLight(world, wx, wy, wz, face, uAxis, vAxis, -1, -1) * shade;
        q.b10 = ChunkMeshBuilder.sampleBlockLight(world, wx, wy, wz, face, uAxis, vAxis,  1, -1) * shade;
        q.b11 = ChunkMeshBuilder.sampleBlockLight(world, wx, wy, wz, face, uAxis, vAxis,  1,  1) * shade;
        q.b01 = ChunkMeshBuilder.sampleBlockLight(world, wx, wy, wz, face, uAxis, vAxis, -1,  1) * shade;

        float[] tint = BiomeColors.tintFor(world, blockId, face, wx, wy, wz);
        q.tintR = tint[0];
        q.tintG = tint[1];
        q.tintB = tint[2];

        // AO sampling (uses same cached getBlock results conceptually)
        q.a00 = ChunkMeshBuilder.sampleAO(world, wx, wy, wz, face, uAxis, vAxis, -1, -1);
        q.a10 = ChunkMeshBuilder.sampleAO(world, wx, wy, wz, face, uAxis, vAxis,  1, -1);
        q.a11 = ChunkMeshBuilder.sampleAO(world, wx, wy, wz, face, uAxis, vAxis,  1,  1);
        q.a01 = ChunkMeshBuilder.sampleAO(world, wx, wy, wz, face, uAxis, vAxis, -1,  1);

        return q;
    }

    /**
     * [GP-002] Slabs are half-height blocks: their faces cannot merge with
     * the full-cell quads of the greedy sweep, so each visible face is
     * emitted here as a single quad. Side faces span y..y+0.5; the top face
     * sits at y+0.5, the bottom at y. UVs still cover the whole tile.
     *
     * A face is hidden only when a neighbouring solid AABB reaches its
     * plane AND covers its whole rectangle: a slab below a full block still
     * shows its top (there is an air gap), but the same slab inside a wall
     * of full blocks hides every face it should.
     */
    private static void emitSlabs(Chunk chunk, World world, TextureAtlas atlas,
                                  MeshGeom opaque) {
        float[] nbb = new float[6];

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    int id = chunk.getBlock(x, y, z);
                    if (!isSlab(id)) continue;

                    for (int face = 0; face < 6; face++) {
                        final int axis = (face <= 1) ? 0 : (face <= 3) ? 1 : 2;
                        final int uAxis = (axis == 0) ? 2 : 0;
                        final int vAxis = (axis == 1) ? 2 : 1;

                        int nx = x + FACE_OFFSETS[face][0];
                        int ny = y + FACE_OFFSETS[face][1];
                        int nz = z + FACE_OFFSETS[face][2];

                        float axisPos;
                        float height;
                        if (axis == 1) {
                            axisPos = (face == 2) ? y + 0.5f : y;
                            height = 1.0f;
                        } else {
                            axisPos = (face == 0) ? x + 1 : (face == 1) ? x
                                    : (face == 4) ? z + 1 : z;
                            height = 0.5f;
                        }

                        float[] bb = neighborAabb(chunk, world, nx, ny, nz, nbb);
                        if (bb != null && covers(bb, axis, axisPos, uAxis, vAxis,
                                                 0, 0, 1, height)) {
                            continue;
                        }

                        int u = (uAxis == 0) ? x : (uAxis == 1) ? y : z;
                        int v = (vAxis == 0) ? x : (vAxis == 1) ? y : z;
                        Quad q = buildQuad(chunk, world, atlas, face, x, y, z, id);
                        emitQuad(opaque, face, axis, uAxis, vAxis, axisPos,
                                 u, v, 1, height, q, 2.0f);
                    }
                }
            }
        }
    }

    private static boolean isSlab(int blockId) {
        if (blockId == 0) return false;
        return BlockType.isSlab(blockId);
    }

    /** Solid AABB of the cell (x,y,z), like getBlockAabb but cross-chunk. */
    private static float[] neighborAabb(Chunk chunk, World world, int x, int y, int z,
                                        float[] out) {
        if (y < 0) {
            out[0] = x; out[1] = y; out[2] = z;
            out[3] = x + 1; out[4] = y + 1; out[5] = z + 1;
            return out;
        }
        if (y >= Chunk.HEIGHT) return null;
        return world.getBlockAabb(chunk.getWorldX() + x, y, chunk.getWorldZ() + z, out);
    }

    /**
     * True when {@code aabb} reaches the face plane (along the face axis)
     * and covers the whole face rectangle in both tangential axes.
     */
    private static boolean covers(float[] aabb, int axis, float axisPos,
                                  int uAxis, int vAxis,
                                  float u0, float v0, float u1, float v1) {
        float amin = aabb[axis];
        float amax = aabb[axis + 3];
        if (Math.abs(amin - axisPos) > 0.001f && Math.abs(amax - axisPos) > 0.001f) {
            return false;
        }
        return aabb[uAxis] <= u0 + 0.001f && aabb[uAxis + 3] >= u1 - 0.001f
            && aabb[vAxis] <= v0 + 0.001f && aabb[vAxis + 3] >= v1 - 0.001f;
    }

    /**
     * Emit one merged rectangle as two triangles.
     */
    private static void emitQuad(MeshGeom buf, int face, int axis, int uAxis, int vAxis,
                                 float axisPos, int u, int v, int width, float height,
                                 Quad q, float uvVScale) {
        int base = buf.positions.size() / 3;

        float[][] corners = new float[4][3];
        float[][] offsets = {{0, 0}, {width, 0}, {width, height}, {0, height}};

        for (int i = 0; i < 4; i++) {
            corners[i][axis] = axisPos;
            corners[i][uAxis] = u + offsets[i][0];
            corners[i][vAxis] = v + offsets[i][1];
        }

        // Emit counter-clockwise as seen from outside (glFrontFace(GL_CCW)).
        // Whether corners 0->1->2->3 already run CCW depends on the handedness
        // of this face's (u,v) basis versus its normal, so derive it instead of
        // assuming it follows the sign of the axis.
        int[] order = isCounterClockwise(face, uAxis, vAxis)
            ? new int[]{0, 1, 2, 3}
            : new int[]{0, 3, 2, 1};

        float[] light = {q.l00, q.l10, q.l11, q.l01};
        float[] block = {q.b00, q.b10, q.b11, q.b01};
        float[] ao = {q.a00, q.a10, q.a11, q.a01};
        // Mirroring is applied by swapping the UV extents rather than the
        // corner order, so the winding (and therefore culling) is untouched.
        // uvVScale stretches the V range: slab side faces are 0.5 tall but
        // still sample the whole tile.
        float u0 = ((q.flip & 1) != 0) ? width : 0;
        float u1 = ((q.flip & 1) != 0) ? 0 : width;
        float v0 = ((q.flip & 2) != 0) ? height * uvVScale : 0;
        float v1 = ((q.flip & 2) != 0) ? 0 : height * uvVScale;

        float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};

        // Emitted-order AO, used both for the vertex attribute and the flip test
        float[] emittedAO = new float[4];

        for (int i = 0; i < 4; i++) {
            int c = order[i];
            buf.positions.add(corners[c][0]);
            buf.positions.add(corners[c][1]);
            buf.positions.add(corners[c][2]);

            buf.texCoords.add(uvs[c][0]);
            buf.texCoords.add(uvs[c][1]);

            buf.normals.add(FACE_NORMALS[face][0]);
            buf.normals.add(FACE_NORMALS[face][1]);
            buf.normals.add(FACE_NORMALS[face][2]);

            // RGB carries the biome tint, which the shader multiplies into
            // the texture; the light term rides in the alpha slot.
            buf.colors.add(q.tintR * light[c]);
            buf.colors.add(q.tintG * light[c]);
            buf.colors.add(q.tintB * light[c]);

            emittedAO[i] = ao[c];
            buf.ao.add(ao[c]);

            buf.layers.add((float) q.layer);

            // Leaves rustle gently; solid blocks never move
            buf.wave.add(q.leaves ? 0.35f : 0.0f);

            // Emissive blocks glow
            buf.emissive.add(q.emissive);

            // Block light rides its own attribute so night shading can keep
            // torch-lit areas bright while the sky fades to black
            buf.blockLight.add(block[c]);
        }

        // A quad has one diagonal; splitting along the wrong one makes the AO
        // gradient bend visibly. Choose the split so the darker pair of corners
        // shares an edge: flip when a0 + a2 > a1 + a3.
        boolean flip = (emittedAO[0] + emittedAO[2]) > (emittedAO[1] + emittedAO[3]);

        if (flip) {
            buf.indices.add(base + 1); buf.indices.add(base + 2); buf.indices.add(base + 3);
            buf.indices.add(base + 1); buf.indices.add(base + 3); buf.indices.add(base);
        } else {
            buf.indices.add(base);     buf.indices.add(base + 1); buf.indices.add(base + 2);
            buf.indices.add(base);     buf.indices.add(base + 2); buf.indices.add(base + 3);
        }
    }

    /**
     * True when walking corners in +u then +v order already produces a
     * counter-clockwise loop when viewed from outside the face.
     *
     * Computed as sign(dot(cross(uDir, vDir), normal)): if the (u,v) basis is
     * right-handed with respect to the outward normal the order is already CCW.
     */
    private static boolean isCounterClockwise(int face, int uAxis, int vAxis) {
        float[] uDir = new float[3];
        float[] vDir = new float[3];
        uDir[uAxis] = 1.0f;
        vDir[vAxis] = 1.0f;

        float cx = uDir[1] * vDir[2] - uDir[2] * vDir[1];
        float cy = uDir[2] * vDir[0] - uDir[0] * vDir[2];
        float cz = uDir[0] * vDir[1] - uDir[1] * vDir[0];

        float[] n = FACE_NORMALS[face];
        return (cx * n[0] + cy * n[1] + cz * n[2]) > 0.0f;
    }

    private static int sizeOf(int axis) {
        return (axis == 1) ? Chunk.HEIGHT : Chunk.SIZE;
    }

    private static int neighbor(Chunk chunk, World world, int x, int y, int z) {
        if (x >= 0 && x < Chunk.SIZE && z >= 0 && z < Chunk.SIZE && y >= 0 && y < Chunk.HEIGHT) {
            return chunk.getBlock(x, y, z);
        }
        return world.getBlock(chunk.getWorldX() + x, y, chunk.getWorldZ() + z);
    }

    /**
     * Height of the neighbour column at the cross-chunk position (x,z), used
     * to fix seams between chunks of different heights. Returns -1 if the
     * neighbour chunk is not loaded (no height data available).
     */
    private static int neighborHeight(Chunk chunk, World world, int x, int z) {
        int cx = chunk.getChunkX();
        int cz = chunk.getChunkZ();
        int wx = chunk.getWorldX() + x;
        int wz = chunk.getWorldZ() + z;

        if (x < 0) { cx--; wx = cx * Chunk.SIZE + (Chunk.SIZE - 1); }
        else if (x >= Chunk.SIZE) { cx++; wx = cx * Chunk.SIZE; }
        if (z < 0) { cz--; wz = cz * Chunk.SIZE + (Chunk.SIZE - 1); }
        else if (z >= Chunk.SIZE) { cz++; wz = cz * Chunk.SIZE; }

        Chunk nc = world.getChunk(cx, cz);
        if (nc == null) return -1; // chunk not loaded

        int lx = wx & (Chunk.SIZE - 1);
        int lz = wz & (Chunk.SIZE - 1);
        return nc.getHighestBlock(lx, lz);
    }

    private static float faceBrightness(int face) {
        switch (face) {
            case 2: return 1.0f;
            case 3: return 0.55f;
            case 0:
            case 1: return 0.82f;
            default: return 0.68f;
        }
    }
}
