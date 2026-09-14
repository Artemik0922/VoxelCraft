package com.voxelgame.rendering;

import com.voxelgame.world.BlockType;
import com.voxelgame.world.World;
import com.voxelgame.world.biome.BiomeData;
import com.voxelgame.world.biome.BiomeRegistry;
import com.voxelgame.world.biome.BiomeSelector;

/**
 * Foliage tinting, in the spirit of grasscolor.png / foliagecolor.png.
 *
 * Grass and leaf tiles are authored neutral and recoloured at mesh time, so
 * one texture serves every biome.
 *
 * The tint comes from the world generator's own biome map. It used to be
 * driven by a private noise field, which meant the colour could disagree
 * with the terrain underneath it - desert-yellow grass in the middle of a
 * taiga. Sampling the real biome keeps them consistent.
 *
 * Colours are blended across a 3x3 neighbourhood so biome borders fade
 * instead of showing a hard seam, then quantised: greedy meshing can only
 * merge faces whose attributes match exactly, and a continuously varying
 * tint would shatter every merged quad into single blocks.
 */
public final class BiomeColors {

    /** Tint steps per channel. Coarse enough to keep quads mergeable. */
    private static final int QUANT = 12;

    private static final float[] NEUTRAL = {1.0f, 1.0f, 1.0f};

    /** Pre-computed quantized tints for each biome: [grassR, grassG, grassB, foliageR, foliageG, foliageB] */
    private static final float[][] TINTS = new float[BiomeSelector.MCBiome.values().length][6];
    static {
        for (BiomeSelector.MCBiome b : BiomeSelector.MCBiome.values()) {
            int[] rgb = fallbackTint(b);
            // Data-driven tints from biomes.json win when present
            BiomeData data = BiomeRegistry.get(BiomeRegistry.jsonIdFor(b));
            if (data != null && data.grassTint != 0 && data.grassTint != 0xFFFFFF) {
                rgb[0] = data.grassTint;
            }
            if (data != null && data.foliageTint != 0 && data.foliageTint != 0xFFFFFF) {
                rgb[1] = data.foliageTint;
            }
            int idx = b.ordinal();
            TINTS[idx][0] = quantise(((rgb[0] >> 16) & 0xFF) / 255.0f);
            TINTS[idx][1] = quantise(((rgb[0] >> 8) & 0xFF) / 255.0f);
            TINTS[idx][2] = quantise((rgb[0] & 0xFF) / 255.0f);
            TINTS[idx][3] = quantise(((rgb[1] >> 16) & 0xFF) / 255.0f);
            TINTS[idx][4] = quantise(((rgb[1] >> 8) & 0xFF) / 255.0f);
            TINTS[idx][5] = quantise((rgb[1] & 0xFF) / 255.0f);
        }
    }

    /** Hardcoded fallback tints, used when biomes.json has no entry. */
    private static int[] fallbackTint(BiomeSelector.MCBiome b) {
        return switch (b) {
            case FROZEN_OCEAN, COLD_OCEAN, DEEP_OCEAN, OCEAN -> new int[]{0x80B497, 0x60917B};
            case WARM_OCEAN, LUKEWARM_OCEAN -> new int[]{0x91BD59, 0x77AB2F};
            case MUSHROOM_FIELDS -> new int[]{0x55C0A0, 0x22B14C};
            case BEACH, SNOWY_BEACH -> new int[]{0x91BD59, 0x77AB2F};
            case STONY_SHORE -> new int[]{0x8AB689, 0x6DA36B};
            case JAGGED_PEAKS, FROZEN_PEAKS -> new int[]{0x80B497, 0x60917B};
            case STONY_PEAKS -> new int[]{0x8AB689, 0x6DA36B};
            case SNOWY_PLAINS, SNOWY_TAIGA, ICE_SPIKES -> new int[]{0x80B497, 0x60917B};
            case GROVE -> new int[]{0x86B564, 0x6B9D4E};
            case WINDSWEPT_HILLS, WINDSWEPT_FOREST -> new int[]{0x8AB689, 0x6DA36B};
            case BADLANDS, WOODED_BADLANDS -> new int[]{0x90A44E, 0x9E8D4E};
            case PLATEAU -> new int[]{0x79C05A, 0x59AE30};
            case DESERT -> new int[]{0xBFB755, 0xAEA42A};
            case SAVANNA, SAVANNA_PLATEAU, SHATTERED_SAVANNA -> new int[]{0xBFB755, 0xAEA42A};
            case SWAMP, MOOR, MANGROVE_SWAMP -> new int[]{0x6A7039, 0x6A7039};
            case JUNGLE -> new int[]{0x59C93C, 0x30BB0B};
            case BAMBOO_JUNGLE -> new int[]{0x59C93C, 0x30BB0B};
            case CRYSTAL_PEAKS -> new int[]{0x8070A0, 0x605080};
            case GLACIER -> new int[]{0xA0C8E0, 0x80A8C0};
            case VOLCANO -> new int[]{0x4A4A4A, 0x3A3A3A};
            case AUTUMN_FOREST -> new int[]{0xC0A040, 0xD06020};
            case PETRIFIED_FOREST -> new int[]{0x90A090, 0x708070};
            case SALT_FLATS -> new int[]{0xD8D8D0, 0xC0C0B8};
            case ASH_WASTES -> new int[]{0x808080, 0x606060};
            case LAVENDER_MEADOW -> new int[]{0xC0A0D0, 0xA080C0};
            default -> new int[]{0x91BD59, 0x77AB2F};
        };
    }

    private BiomeColors() {}

    public static boolean isFoliage(int blockId) {
        BlockType t = BlockType.fromId(blockId);
        // Grass blocks and the leaf tiles now ship as desaturated bases: the
        // biome tint supplies the hue, exactly like the vanilla colormap
        return t == BlockType.OAK_LEAVES || t == BlockType.SPRUCE_LEAVES
            || t == BlockType.JUNGLE_LEAVES || t == BlockType.BIRCH_LEAVES
            || t == BlockType.GRASS_BLOCK
            || t == BlockType.GRASS_PLANT;
    }

    /**
     * Tint for a block face. Non-foliage returns white, leaving the texture
     * untouched.
     *
     * @param face mesher face index; the underside of grass is plain dirt
     */
    /**
     * Birch and spruce use a constant colour rather than the biome colormap,
     * which is what keeps a birch wood visibly paler than the oak forest
     * beside it regardless of climate.
     */
    private static final int BIRCH_TINT = 0x80A755;
    private static final int SPRUCE_TINT = 0x619961;

    /** Cached quantized tint for current column */
    private static int lastTintX = Integer.MIN_VALUE, lastTintZ = Integer.MIN_VALUE;
    private static float lastGrassR, lastGrassG, lastGrassB;
    private static float lastFoliageR, lastFoliageG, lastFoliageB;

    /** Pre-computed constant tints */
    private static final float[] BIRCH_TINT_F = precomputeTint(BIRCH_TINT);
    private static final float[] SPRUCE_TINT_F = precomputeTint(SPRUCE_TINT);

    private static float[] precomputeTint(int rgb) {
        return new float[]{
            quantise(((rgb >> 16) & 0xFF) / 255.0f),
            quantise(((rgb >> 8) & 0xFF) / 255.0f),
            quantise((rgb & 0xFF) / 255.0f)
        };
    }

    /** Get biome tint for a column, cached for spatially-coherent access. */
    private static void cacheBiomeTint(World world, int wx, int wz) {
        if (wx == lastTintX && wz == lastTintZ) return;
        lastTintX = wx;
        lastTintZ = wz;
        BiomeSelector.MCBiome biome = world.getMCBiomeAt(wx, wz);
        int idx = biome.ordinal();
        lastGrassR = TINTS[idx][0];
        lastGrassG = TINTS[idx][1];
        lastGrassB = TINTS[idx][2];
        lastFoliageR = TINTS[idx][3];
        lastFoliageG = TINTS[idx][4];
        lastFoliageB = TINTS[idx][5];
    }

    private static final float[] TINT_RESULT = new float[3];

    public static float[] tintFor(World world, int blockId, int face,
                                  int wx, int wy, int wz) {
        if (!isFoliage(blockId)) return NEUTRAL;

        BlockType type = BlockType.fromId(blockId);
        if (type == BlockType.GRASS_BLOCK && face == 3) return NEUTRAL;

        if (type == BlockType.SPRUCE_LEAVES) return SPRUCE_TINT_F;
        if (type == BlockType.BIRCH_LEAVES) return BIRCH_TINT_F;

        boolean grass = (type == BlockType.GRASS_BLOCK || type == BlockType.GRASS_PLANT);

        // Average the neighbourhood so borders blend, using cached biome lookup
        float r = 0, g = 0, b = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                cacheBiomeTint(world, wx + dx * 4, wz + dz * 4);
                if (grass) {
                    r += lastGrassR; g += lastGrassG; b += lastGrassB;
                } else {
                    r += lastFoliageR; g += lastFoliageG; b += lastFoliageB;
                }
            }
        }
        r /= 9; g /= 9; b /= 9;

        // Cooler and duller with altitude
        if (wy > 96) {
            float f = Math.min(1.0f, (wy - 96) / 90.0f);
            r *= (1 - f * 0.25f);
            g *= (1 - f * 0.12f);
            b = b * (1 - f * 0.05f) + 0.094f * f;
        }

        TINT_RESULT[0] = r;
        TINT_RESULT[1] = g;
        TINT_RESULT[2] = b;
        return TINT_RESULT;
    }

    private static float quantise(float v) {
        int step = Math.round(v * QUANT);
        return Math.max(0, Math.min(QUANT, step)) / (float) QUANT;
    }
}
