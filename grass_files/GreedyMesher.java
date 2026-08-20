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
        // Per-corner ambient occlusion, same ordering
        float a00, a10, a11, a01;
        // Biome tint applied to this face
        float tintR = 1.0f, tintG = 1.0f, tintB = 1.0f;
        /** Texture mirroring: bit 0 flips U, bit 1 flips V. */
        int flip;

        boolean mergeableWith(Quad o) {
            if (o == null) return false;
            // Different variant or mirroring means the merged quad would
            // repeat one tile across both blocks, undoing the variety
            if (layer != o.layer || flip != o.flip
                || transparent != o.transparent || leaves != o.leaves) return false;
            if (!eq(tintR, o.tintR) || !eq(tintG, o.tintG) || !eq(tintB, o.tintB)) return false;
            return eq(l00, o.l00) && eq(l10, o.l10) && eq(l11, o.l11) && eq(l01, o.l01)
                && eq(a00, o.a00) && eq(a10, o.a10) && eq(a11, o.a11) && eq(a01, o.a01);
        }

        private static boolean eq(float a, float b) {
            return Math.abs(a - b) < 0.002f;
        }
    }

    public static class Buffers {
        public final List<Float> positions = new ArrayList<>();
        public final List<Float> texCoords = new ArrayList<>();
        public final List<Float> normals = new ArrayList<>();
        public final List<Float> colors = new ArrayList<>();
        public final List<Float> ao = new ArrayList<>();
        /** Sway weight per vertex; 0 keeps the vertex pinned. */
        public final List<Float> wave = new ArrayList<>();
        public final List<Float> layers = new ArrayList<>();
        public final List<Integer> indices = new ArrayList<>();

        public boolean isEmpty() { return positions.isEmpty(); }
    }

    public static void build(Chunk chunk, World world, TextureAtlas atlas,
                             Buffers opaque, Buffers transparent, Buffers leaves) {
        for (int face = 0; face < 6; face++) {
            sweepFace(chunk, world, atlas, face, opaque, transparent, leaves);
        }
        // Cross plants go with the leaves: same alpha test, same need to be
        // visible from both sides
        emitCrossPlants(chunk, world, atlas, leaves);
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
                                        Buffers buf) {
        int originX = chunk.getWorldX();
        int originZ = chunk.getWorldZ();

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    byte id = chunk.getBlock(x, y, z);
                    if (!ChunkMeshBuilder.isCrossPlant(id)) continue;

                    int wx = originX + x;
                    int wz = originZ + z;

                    int layer = atlas.getSlot(id, 2, wx, y, wz);

                    // Plants take the light of their own cell, no AO
                    float light = world.getLight(wx, y, wz) / (float) Chunk.MAX_LIGHT;
                    float shade = 0.18f + 0.82f * light;

                    float[] tint = BiomeColors.tintFor(world, id, 2, wx, y, wz);

                    // Small offset so a field of grass is not a perfect grid.
                    // Kept well under the 0.15 margin so the plant never
                    // pokes outside its own block.
                    float jx = (hash(wx, y, wz, 3) - 0.5f) * 0.12f;
                    float jz = (hash(wx, y, wz, 7) - 0.5f) * 0.12f;

                    float height = plantHeight(id, wx, y, wz);

                    addCrossQuad(buf, x + jx, y, z + jz, layer, shade, tint, false, height);
                    addCrossQuad(buf, x + jx, y, z + jz, layer, shade, tint, true, height);
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
    private static void addCrossQuad(Buffers buf, float x, float y, float z,
                                     int layer, float shade, float[] tint,
                                     boolean secondDiagonal, float height) {
        final float LO = 0.15f;
        final float HI = 0.85f;

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
                buf.wave.add((c == 2 || c == 3) ? 1.0f : 0.0f);
            }

            buf.indices.add(base);     buf.indices.add(base + 1); buf.indices.add(base + 2);
            buf.indices.add(base);     buf.indices.add(base + 2); buf.indices.add(base + 3);
        }
    }

    /**
     * Plant height in blocks. Flowers sit low, grass reaches roughly half a
     * block with a little natural variation.
     */
    private static float plantHeight(byte blockId, int wx, int wy, int wz) {
        BlockType type = BlockType.fromId(blockId);
        float jitter = hash(wx, wy, wz, 41);

        return switch (type) {
            case DANDELION, POPPY -> 0.40f;
            case DEAD_BUSH -> 0.55f;
            // 0.50 .. 0.60, so a patch is not perfectly level
            case GRASS_PLANT -> 0.50f + jitter * 0.10f;
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
                                  Buffers opaque, Buffers transparent, Buffers leaves) {
        // Slice axis is the face's dominant axis; u/v are the two others.
        final int axis = (face <= 1) ? 0 : (face <= 3) ? 1 : 2;
        final int uAxis = (axis == 0) ? 2 : 0;
        final int vAxis = (axis == 1) ? 2 : 1;

        final int axisSize = sizeOf(axis);
        final int uSize = sizeOf(uAxis);
        final int vSize = sizeOf(vAxis);

        final int originX = chunk.getWorldX();
        final int originZ = chunk.getWorldZ();

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

                    byte blockId = chunk.getBlock(p[0], p[1], p[2]);
                    if (blockId == 0) continue;

                    int nx = p[0] + FACE_OFFSETS[face][0];
                    int ny = p[1] + FACE_OFFSETS[face][1];
                    int nz = p[2] + FACE_OFFSETS[face][2];

                    byte neighborId = neighbor(chunk, world, nx, ny, nz);
                    if (!ChunkMeshBuilder.facePasses(blockId, neighborId)) continue;

                    Quad q = new Quad();

                    float shade = faceBrightness(face);
                    int wx = originX + p[0];
                    int wy = p[1];
                    int wz = originZ + p[2];

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

                    // Light sampling (single pass, shared occlusion data)
                    q.l00 = ChunkMeshBuilder.sampleLight(world, wx, wy, wz, face, uAxis, vAxis, -1, -1) * shade;
                    q.l10 = ChunkMeshBuilder.sampleLight(world, wx, wy, wz, face, uAxis, vAxis,  1, -1) * shade;
                    q.l11 = ChunkMeshBuilder.sampleLight(world, wx, wy, wz, face, uAxis, vAxis,  1,  1) * shade;
                    q.l01 = ChunkMeshBuilder.sampleLight(world, wx, wy, wz, face, uAxis, vAxis, -1,  1) * shade;

                    float[] tint = BiomeColors.tintFor(world, blockId, face, wx, wy, wz);
                    q.tintR = tint[0];
                    q.tintG = tint[1];
                    q.tintB = tint[2];

                    // AO sampling (uses same cached getBlock results conceptually)
                    q.a00 = ChunkMeshBuilder.sampleAO(world, wx, wy, wz, face, uAxis, vAxis, -1, -1);
                    q.a10 = ChunkMeshBuilder.sampleAO(world, wx, wy, wz, face, uAxis, vAxis,  1, -1);
                    q.a11 = ChunkMeshBuilder.sampleAO(world, wx, wy, wz, face, uAxis, vAxis,  1,  1);
                    q.a01 = ChunkMeshBuilder.sampleAO(world, wx, wy, wz, face, uAxis, vAxis, -1,  1);

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
                             face, axis, uAxis, vAxis, slice, u, v, width, height, start);

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
     * Emit one merged rectangle as two triangles.
     */
    private static void emitQuad(Buffers buf, int face, int axis, int uAxis, int vAxis,
                                 int slice, int u, int v, int width, int height, Quad q) {
        int base = buf.positions.size() / 3;

        // Faces on the positive side sit at slice+1 along the axis
        float axisPos = slice + ((face == 0 || face == 2 || face == 4) ? 1.0f : 0.0f);

        float[][] corners = new float[4][3];
        int[][] offsets = {{0, 0}, {width, 0}, {width, height}, {0, height}};

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
        float[] ao = {q.a00, q.a10, q.a11, q.a01};
        // Mirroring is applied by swapping the UV extents rather than the
        // corner order, so the winding (and therefore culling) is untouched.
        float u0 = ((q.flip & 1) != 0) ? width : 0;
        float u1 = ((q.flip & 1) != 0) ? 0 : width;
        float v0 = ((q.flip & 2) != 0) ? height : 0;
        float v1 = ((q.flip & 2) != 0) ? 0 : height;

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

    private static byte neighbor(Chunk chunk, World world, int x, int y, int z) {
        if (x >= 0 && x < Chunk.SIZE && z >= 0 && z < Chunk.SIZE && y >= 0 && y < Chunk.HEIGHT) {
            return chunk.getBlock(x, y, z);
        }
        return world.getBlock(chunk.getWorldX() + x, y, chunk.getWorldZ() + z);
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
