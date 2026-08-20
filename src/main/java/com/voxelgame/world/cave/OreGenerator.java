package com.voxelgame.world.cave;

import com.voxelgame.world.BlockType;
import com.voxelgame.world.generator.NoiseGenerator;

/**
 * Ore generation with triangular height distribution (MC 1.18+ style).
 * Each ore has a peak Y level and falls off above/below it.
 */
public final class OreGenerator {

    private final NoiseGenerator oreNoise;

    // Ore config: blockType, minY, maxY, peakY, veinSize, countPerChunk
    public record OreConfig(BlockType type, int minY, int maxY, int peakY, int veinSize, int attempts) {}

    public static final OreConfig[] ORES = {
        new OreConfig(BlockType.COAL_ORE, 0, 192, 96, 17, 20),
        new OreConfig(BlockType.IRON_ORE, 8, 56, 16, 9, 20),
        new OreConfig(BlockType.COPPER_ORE, 8, 112, 48, 10, 16),
        new OreConfig(BlockType.GOLD_ORE, 8, 32, 12, 9, 4),
        new OreConfig(BlockType.LAPIS_ORE, 8, 64, 24, 7, 2),
        new OreConfig(BlockType.REDSTONE_ORE, 8, 48, 16, 8, 8),
        new OreConfig(BlockType.DIAMOND_ORE, 8, 48, 16, 8, 4),
    };

    // Emerald: only in mountain biomes
    public static final OreConfig EMERALD = new OreConfig(BlockType.EMERALD_ORE, 32, 256, 128, 4, 2);

    public OreGenerator(long seed) {
        oreNoise = new NoiseGenerator(seed + 2000);
    }

    /**
     * Get ore type at a position based on height distribution.
     * @return ore block type, or AIR if no ore here
     */
    public BlockType getOre(int x, int y, int z, boolean isMountain) {
        // Triangular distribution: probability peaks at peakY, zero at minY/maxY
        double noise = oreNoise.noise3D(x * 0.1, y * 0.1, z * 0.1);

        for (OreConfig ore : ORES) {
            if (y < ore.minY() || y > ore.maxY()) continue;

            // Triangular probability
            double dist = Math.abs(y - ore.peakY());
            double range = Math.max(ore.peakY() - ore.minY(), ore.maxY() - ore.peakY());
            double prob = 1.0 - dist / range;

            // Noise threshold based on probability
            if (noise > (1.0 - prob) * 1.8) {
                return ore.type();
            }
        }

        // Emerald only in mountains
        if (isMountain && y >= EMERALD.minY() && y <= EMERALD.maxY()) {
            double dist = Math.abs(y - EMERALD.peakY());
            double range = Math.max(EMERALD.peakY() - EMERALD.minY(), EMERALD.maxY() - EMERALD.peakY());
            double prob = 1.0 - dist / range;
            if (noise > (1.0 - prob) * 1.9) {
                return EMERALD.type();
            }
        }

        return BlockType.AIR;
    }

    /**
     * Check if a block is in the deepslate range (y < 8, transitioning to deepslate).
     */
    public static boolean isDeepslate(int y) {
        return y < 8;
    }

    /**
     * Check if a block should be bedrock (bottom 5 layers).
     */
    public boolean isBedrock(int x, int y, int z) {
        if (y > 4) return false;
        // Ragged bedrock floor using noise
        double noise = oreNoise.noise3D(x * 0.15, y * 0.3, z * 0.15);
        int maxBedrock = (int) (4 + noise * 2); // 2-5 layers
        return y < maxBedrock;
    }
}
