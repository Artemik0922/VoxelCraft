package com.voxelgame.rendering;

import com.voxelgame.world.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds chunk meshes with per-vertex smooth lighting and ambient occlusion.
 *
 * Produces two meshes: opaque geometry and transparent geometry (water, glass,
 * leaves) so the renderer can draw them in separate passes.
 */
public class ChunkMeshBuilder {

    // Face directions: +X, -X, +Y, -Y, +Z, -Z
    private static final int[][] FACE_OFFSETS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    // Vertices per face, wound COUNTER-CLOCKWISE seen from outside the block.
    // Verified via cross((v1-v0), (v2-v0)) pointing along the face normal.
    private static final float[][][] FACE_VERTICES = {
        {{1,0,1},{1,0,0},{1,1,0},{1,1,1}}, // +X
        {{0,0,0},{0,0,1},{0,1,1},{0,1,0}}, // -X
        {{0,1,1},{1,1,1},{1,1,0},{0,1,0}}, // +Y
        {{0,0,0},{1,0,0},{1,0,1},{0,0,1}}, // -Y
        {{0,0,1},{1,0,1},{1,1,1},{0,1,1}}, // +Z
        {{1,0,0},{0,0,0},{0,1,0},{1,1,0}}  // -Z
    };

    private static final float[][] FACE_NORMALS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    /**
     * For each face, the two in-plane axes used to sample the 3x3 neighbourhood
     * around a vertex: {tangentAxis, bitangentAxis}. 0=x 1=y 2=z
     */
    /** Darkest an ambient-occluded vertex may get. */
    public static final float AO_MIN = 0.5f;

    /**
     * Fancy draws leaves as cut-outs you can see through; Fast makes them
     * solid, which also lets neighbouring leaf faces be culled.
     *
     * Both are baked at mesh time, so changing either forces a rebuild.
     */
    private static boolean fancyGraphics = true;

    public static boolean isFancyGraphics() { return fancyGraphics; }
    public static void setFancyGraphics(boolean fancy) { fancyGraphics = fancy; }

    /** AO is baked at mesh time; changing this requires a full rebuild. */
    private static boolean aoEnabled = true;

    public static boolean isAmbientOcclusionEnabled() { return aoEnabled; }
    public static void setAmbientOcclusionEnabled(boolean enabled) { aoEnabled = enabled; }

    private static final int[][] FACE_TANGENTS = {
        {2, 1}, // +X -> spans Z,Y
        {2, 1}, // -X
        {0, 2}, // +Y -> spans X,Z
        {0, 2}, // -Y
        {0, 1}, // +Z -> spans X,Y
        {0, 1}  // -Z
    };

    public static class MeshData {
        public final Mesh opaque;
        public final Mesh transparent;
        /** Alpha-tested geometry drawn with culling disabled. */
        public final Mesh leaves;

        MeshData(Mesh opaque, Mesh transparent, Mesh leaves) {
            this.opaque = opaque;
            this.transparent = transparent;
            this.leaves = leaves;
        }
    }

    private static class Buffers {
        final List<Float> positions = new ArrayList<>();
        final List<Float> texCoords = new ArrayList<>();
        final List<Float> normals = new ArrayList<>();
        final List<Float> colors = new ArrayList<>();
        final List<Integer> indices = new ArrayList<>();

        boolean isEmpty() { return positions.isEmpty(); }

        Mesh toMesh() {
            if (isEmpty()) return null;
            return new Mesh(positions, texCoords, normals, colors, indices);
        }
    }

    /**
     * Build opaque + transparent meshes for a chunk.
     */
    public static MeshData build(Chunk chunk, World world, TextureAtlas atlas) {
        GreedyMesher.Buffers opaque = new GreedyMesher.Buffers();
        GreedyMesher.Buffers transparent = new GreedyMesher.Buffers();
        GreedyMesher.Buffers leaves = new GreedyMesher.Buffers();

        GreedyMesher.build(chunk, world, atlas, opaque, transparent, leaves);

        return new MeshData(toMesh(opaque), toMesh(transparent), toMesh(leaves));
    }

    private static Mesh toMesh(GreedyMesher.Buffers b) {
        if (b.isEmpty()) return null;
        return new Mesh(b.positions, b.texCoords, b.normals, b.colors,
            b.layers, b.ao, b.wave, b.indices);
    }

    /** Legacy entry point - opaque geometry only. */
    public static Mesh buildMesh(Chunk chunk, World world, TextureAtlas atlas) {
        return build(chunk, world, atlas).opaque;
    }

    /**
     * Does this block let sight through, i.e. must the neighbouring block
     * still draw the face it shares with this one?
     */
    public static boolean isTransparent(byte blockId) {
        if (blockId == 0) return true;
        BlockType t = BlockType.fromId(blockId);

        // Non-solid props (flowers, grass, crops) never hide a neighbour's face
        if (!t.solid) return true;

        // On Fast, leaves are opaque cubes: neighbouring leaf faces are
        // culled and nothing shows through the canopy
        if (!fancyGraphics && isLeaves(blockId)) return false;

        return t == BlockType.WATER || t == BlockType.GLASS || t == BlockType.ICE
            || t == BlockType.OAK_LEAVES || t == BlockType.SPRUCE_LEAVES
            || t == BlockType.BIRCH_LEAVES || t == BlockType.JUNGLE_LEAVES;
    }

    /**
     * Should this block's own geometry go into the alpha-blended pass?
     * Distinct from {@link #isTransparent}: a flower is see-through for
     * visibility purposes but is drawn with alpha testing, not blending.
     */
    public static boolean needsBlendPass(byte blockId) {
        if (blockId == 0) return false;
        BlockType t = BlockType.fromId(blockId);
        return t == BlockType.WATER || t == BlockType.GLASS || t == BlockType.ICE;
    }

    /**
     * Leaves are alpha-tested, not blended, but must be drawn with culling
     * off: their cut-outs expose the inside of the canopy, and a back face
     * has to be there to fill it.
     */
    /** O(1) lookup table for leaf blocks. */
    private static final boolean[] IS_LEAVES = new boolean[256];
    static {
        for (BlockType t : BlockType.values()) {
            int idx = t.id & 0xFF;
            IS_LEAVES[idx] = (t == BlockType.OAK_LEAVES || t == BlockType.SPRUCE_LEAVES
                || t == BlockType.BIRCH_LEAVES || t == BlockType.JUNGLE_LEAVES
                || t == BlockType.AUTUMN_LEAVES || t == BlockType.CHERRY_LEAVES);
        }
    }

    public static boolean isLeaves(byte blockId) {
        return IS_LEAVES[blockId & 0xFF];
    }

    /**
     * Does this block belong in the double-sided cut-out pass?
     *
     * Only on Fancy: solid Fast leaves have nothing to see through, so they
     * go in the opaque pass and keep their back faces culled.
     */
    public static boolean needsCutoutPass(byte blockId) {
        if (blockId == 0) return false;
        if (fancyGraphics && isLeaves(blockId)) return true;
        // Cherry and autumn leaves are always cutout even on Fast
        BlockType t = BlockType.fromId(blockId);
        return t == BlockType.CHERRY_LEAVES || t == BlockType.AUTUMN_LEAVES;
    }

    private static byte getNeighborBlock(Chunk chunk, World world, int x, int y, int z) {
        if (x >= 0 && x < Chunk.SIZE && z >= 0 && z < Chunk.SIZE && y >= 0 && y < Chunk.HEIGHT) {
            return chunk.getBlock(x, y, z);
        }
        return world.getBlock(chunk.getWorldX() + x, y, chunk.getWorldZ() + z);
    }

    /**
     * Plants drawn as two crossed quads instead of a cube: flowers, tall
     * grass, saplings and dead bushes.
     */
    public static boolean isCrossPlant(byte blockId) {
        if (blockId == 0) return false;
        BlockType t = BlockType.fromId(blockId);
        return t == BlockType.GRASS_PLANT
            || t == BlockType.DANDELION
            || t == BlockType.POPPY
            || t == BlockType.DEAD_BUSH
            || t == BlockType.LAVENDER
            || t == BlockType.SUNFLOWER
            || t == BlockType.WHEAT
            || t == BlockType.CARROT
            || t == BlockType.POTATO;
    }

    /** Public visibility test used by GreedyMesher. */
    public static boolean facePasses(byte blockId, byte neighborId) {
        return shouldRenderFace(blockId, neighborId);
    }

    /**
     * Corner light for GreedyMesher, addressed by slice-space axes and the
     * sign of the corner along each of them.
     */
    public static float cornerLightAt(World world, int wx, int wy, int wz,
                                      int face, int uAxis, int vAxis,
                                      int uSign, int vSign) {
        int[] n = FACE_OFFSETS[face];
        int ox = wx + n[0];
        int oy = wy + n[1];
        int oz = wz + n[2];

        int[] dA = axisVector(uAxis, uSign);
        int[] dB = axisVector(vAxis, vSign);

        return sampleLightInternal(world, ox, oy, oz, dA, dB);
    }

    /**
     * A face exists if and only if the neighbouring cell lets light/sight
     * through. This is the single rule for all 6 directions - no special
     * casing per axis, which is what previously left holes in cliff walls.
     */
    private static boolean shouldRenderFace(byte blockId, byte neighborId) {
        // Air neighbour: always visible
        if (neighborId == 0) return true;

        // Use O(1) lookup tables instead of fromId()
        boolean blockTransparent = BlockType.isTransparentFast(blockId);
        boolean neighborTransparent = BlockType.isTransparentFast(neighborId);

        // Two cells of the same transparent material share no visible face
        if (blockId == neighborId && blockTransparent) return false;

        // Two leaf blocks share no visible face
        if (blockTransparent && neighborTransparent) return false;

        // Otherwise the face is drawn when the neighbour is see-through
        return neighborTransparent;
    }

    private static void addFace(Buffers buf, World world, int originX, int originZ,
                                int x, int y, int z, int face, byte blockId,
                                TextureAtlas atlas) {
        int vertexIndex = buf.positions.size() / 3;

        TextureAtlas.TextureCoords uv = atlas.getCoords(blockId, face);
        float faceShade = getFaceBrightness(face);

        int wx = originX + x;
        int wz = originZ + z;

        float[] vertexLight = new float[4];

        for (int i = 0; i < 4; i++) {
            float vx = FACE_VERTICES[face][i][0];
            float vy = FACE_VERTICES[face][i][1];
            float vz = FACE_VERTICES[face][i][2];

            buf.positions.add(vx + x);
            buf.positions.add(vy + y);
            buf.positions.add(vz + z);

            float u = (i == 0 || i == 3) ? uv.u1 : uv.u2;
            float v = (i == 0 || i == 1) ? uv.v1 : uv.v2;
            buf.texCoords.add(u);
            buf.texCoords.add(v);

            buf.normals.add(FACE_NORMALS[face][0]);
            buf.normals.add(FACE_NORMALS[face][1]);
            buf.normals.add(FACE_NORMALS[face][2]);

            // Smooth light + AO sampled around this corner
            float light = cornerLight(world, wx, y, wz, face, vx, vy, vz);
            float shade = light * faceShade;
            vertexLight[i] = shade;

            buf.colors.add(shade);
            buf.colors.add(shade);
            buf.colors.add(shade);
        }

        // Flip the quad diagonal towards the darker corners; this removes the
        // classic AO triangulation seam on block corners.
        boolean flip = (vertexLight[0] + vertexLight[2]) < (vertexLight[1] + vertexLight[3]);

        if (flip) {
            buf.indices.add(vertexIndex + 1);
            buf.indices.add(vertexIndex + 2);
            buf.indices.add(vertexIndex + 3);
            buf.indices.add(vertexIndex + 1);
            buf.indices.add(vertexIndex + 3);
            buf.indices.add(vertexIndex);
        } else {
            buf.indices.add(vertexIndex);
            buf.indices.add(vertexIndex + 1);
            buf.indices.add(vertexIndex + 2);
            buf.indices.add(vertexIndex);
            buf.indices.add(vertexIndex + 2);
            buf.indices.add(vertexIndex + 3);
        }
    }

    /**
     * Average the light of the four cells touching this vertex on the outside
     * of the face, and darken it by how many of them are solid (ambient
     * occlusion). This is Minecraft's "smooth lighting".
     */
    private static float cornerLight(World world, int wx, int wy, int wz,
                                     int face, float vx, float vy, float vz) {
        int[] n = FACE_OFFSETS[face];

        // Cell directly outside the face
        int ox = wx + n[0];
        int oy = wy + n[1];
        int oz = wz + n[2];

        int axisA = FACE_TANGENTS[face][0];
        int axisB = FACE_TANGENTS[face][1];

        // Vertex coords are 0 or 1; map to -1 / +1 step along each tangent
        int stepA = componentStep(axisA, vx, vy, vz);
        int stepB = componentStep(axisB, vx, vy, vz);

        int[] dA = axisVector(axisA, stepA);
        int[] dB = axisVector(axisB, stepB);

        return sampleLightInternal(world, ox, oy, oz, dA, dB);
    }

    /**
     * Average light of the four cells touching a vertex, darkened by how many
     * of them are solid (ambient occlusion).
     *
     * Combines light sampling and AO into a single pass to avoid duplicate
     * getBlock/isOccluder calls (each call does a chunk map lookup).
     */
    /** Light-only sampling with face-space coordinates. */
    public static float sampleLight(World world, int wx, int wy, int wz,
                                     int face, int uAxis, int vAxis,
                                     int uSign, int vSign) {
        int[] n = FACE_OFFSETS[face];
        int[] dA = axisVector(uAxis, uSign);
        int[] dB = axisVector(vAxis, vSign);
        return sampleLightInternal(world, wx + n[0], wy + n[1], wz + n[2], dA, dB);
    }

    /** AO-only sampling with face-space coordinates. */
    public static float sampleAO(World world, int wx, int wy, int wz,
                                  int face, int uAxis, int vAxis,
                                  int uSign, int vSign) {
        int[] n = FACE_OFFSETS[face];
        int[] dA = axisVector(uAxis, uSign);
        int[] dB = axisVector(vAxis, vSign);
        return sampleAOInternal(world, wx + n[0], wy + n[1], wz + n[2], dA, dB);
    }

    /** Light-only sampling at a world position. Returns normalized 0..1. */
    private static float sampleLightInternal(World world, int ox, int oy, int oz,
                                              int[] dA, int[] dB) {
        int lightCenter = world.getLight(ox, oy, oz);
        int lightA = world.getLight(ox + dA[0], oy + dA[1], oz + dA[2]);
        int lightB = world.getLight(ox + dB[0], oy + dB[1], oz + dB[2]);
        int lightC = world.getLight(ox + dA[0] + dB[0], oy + dA[1] + dB[1], oz + dA[2] + dB[2]);

        boolean solidA = isOccluder(world, ox + dA[0], oy + dA[1], oz + dA[2]);
        boolean solidB = isOccluder(world, ox + dB[0], oy + dB[1], oz + dB[2]);
        boolean solidC = isOccluder(world,
            ox + dA[0] + dB[0], oy + dA[1] + dB[1], oz + dA[2] + dB[2]);

        int sum = lightCenter;
        int count = 1;
        if (!solidA) { sum += lightA; count++; }
        if (!solidB) { sum += lightB; count++; }
        if (!solidC) { sum += lightC; count++; }
        // Normalize 0..15 to 0..1 and apply ambient floor (matches old behavior)
        float avgLight = (sum / (float) count) / Chunk.MAX_LIGHT;
        return 0.12f + 0.88f * avgLight;
    }

    /** AO-only sampling at a world position. */
    private static float sampleAOInternal(World world, int ox, int oy, int oz,
                                           int[] dA, int[] dB) {
        if (!aoEnabled) return 1.0f;
        boolean solidA = isOccluder(world, ox + dA[0], oy + dA[1], oz + dA[2]);
        boolean solidB = isOccluder(world, ox + dB[0], oy + dB[1], oz + dB[2]);
        boolean solidC = isOccluder(world,
            ox + dA[0] + dB[0], oy + dA[1] + dB[1], oz + dA[2] + dB[2]);

        int level;
        if (solidA && solidB) {
            level = 0;
        } else {
            level = 3 - ((solidA ? 1 : 0) + (solidB ? 1 : 0) + (solidC ? 1 : 0));
        }
        return AO_MIN + (1.0f - AO_MIN) * (level / 3.0f);
    }

    /**
     * Classic 3-neighbour vertex AO.
     *
     * Inspects the two edge-adjacent cells and the diagonal one sitting around
     * this corner on the outside of the face. Two opposing edge occluders seal
     * the corner completely, so the diagonal stops mattering.
     *
     * Returns a brightness multiplier in the range {@value #AO_MIN}..1.0.
     */
    public static float sampleCornerAO(World world, int ox, int oy, int oz,
                                       int[] dA, int[] dB) {
        if (!aoEnabled) return 1.0f;

        boolean side1 = isOccluder(world, ox + dA[0], oy + dA[1], oz + dA[2]);
        boolean side2 = isOccluder(world, ox + dB[0], oy + dB[1], oz + dB[2]);
        boolean corner = isOccluder(world,
            ox + dA[0] + dB[0], oy + dA[1] + dB[1], oz + dA[2] + dB[2]);

        int level;
        if (side1 && side2) {
            level = 0; // fully enclosed corner
        } else {
            level = 3 - ((side1 ? 1 : 0) + (side2 ? 1 : 0) + (corner ? 1 : 0));
        }

        // level 0..3  ->  AO_MIN..1.0, evenly spaced
        return AO_MIN + (1.0f - AO_MIN) * (level / 3.0f);
    }

    /** Smooth light term only, with the occluded cells excluded from the mean. */
    private static float sampleCornerLight(World world, int ox, int oy, int oz,
                                           int[] dA, int[] dB) {
        int lightCenter = world.getLight(ox, oy, oz);
        int lightA = world.getLight(ox + dA[0], oy + dA[1], oz + dA[2]);
        int lightB = world.getLight(ox + dB[0], oy + dB[1], oz + dB[2]);
        int lightC = world.getLight(ox + dA[0] + dB[0], oy + dA[1] + dB[1], oz + dA[2] + dB[2]);

        boolean solidA = isOccluder(world, ox + dA[0], oy + dA[1], oz + dA[2]);
        boolean solidB = isOccluder(world, ox + dB[0], oy + dB[1], oz + dB[2]);
        boolean solidC = isOccluder(world,
            ox + dA[0] + dB[0], oy + dA[1] + dB[1], oz + dA[2] + dB[2]);

        // Average only the cells that actually let light through
        int sum = lightCenter;
        int count = 1;
        if (!solidA) { sum += lightA; count++; }
        if (!solidB) { sum += lightB; count++; }
        if (!solidC && !(solidA && solidB)) { sum += lightC; count++; }

        float avgLight = (sum / (float) count) / Chunk.MAX_LIGHT;
        return 0.12f + 0.88f * avgLight;
    }

    /** AO for GreedyMesher, addressed in slice-space like cornerLightAt. */
    public static float cornerAOAt(World world, int wx, int wy, int wz,
                                   int face, int uAxis, int vAxis,
                                   int uSign, int vSign) {
        int[] n = FACE_OFFSETS[face];
        return sampleCornerAO(world, wx + n[0], wy + n[1], wz + n[2],
            axisVector(uAxis, uSign), axisVector(vAxis, vSign));
    }

    private static int componentStep(int axis, float vx, float vy, float vz) {
        float c = (axis == 0) ? vx : (axis == 1) ? vy : vz;
        return (c > 0.5f) ? 1 : -1;
    }

    private static int[] axisVector(int axis, int step) {
        int[] v = new int[3];
        v[axis] = step;
        return v;
    }

    /** O(1) occluder test using precomputed lookup table. */
    private static final boolean[] OCCLUDER = new boolean[256];
    static {
        for (BlockType t : BlockType.values()) {
            int idx = t.id & 0xFF;
            // Occluder = solid AND not transparent (leaves don't occlude AO)
            OCCLUDER[idx] = t.solid && t != BlockType.WATER && t != BlockType.GLASS
                && t != BlockType.ICE && t != BlockType.OAK_LEAVES
                && t != BlockType.SPRUCE_LEAVES && t != BlockType.BIRCH_LEAVES
                && t != BlockType.AUTUMN_LEAVES && t != BlockType.CHERRY_LEAVES;
        }
    }

    private static boolean isOccluder(World world, int x, int y, int z) {
        return OCCLUDER[world.getBlock(x, y, z) & 0xFF];
    }

    /**
     * Directional face shading (Minecraft applies a fixed tint per direction).
     */
    private static float getFaceBrightness(int face) {
        switch (face) {
            case 2: return 1.0f;   // top
            case 3: return 0.55f;  // bottom
            case 0:
            case 1: return 0.82f;  // east / west
            default: return 0.68f; // north / south
        }
    }
}
