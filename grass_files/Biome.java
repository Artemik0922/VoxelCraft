package com.voxelgame.world.generator;

import com.voxelgame.world.BlockType;
import com.voxelgame.world.biome.BiomeData;
import com.voxelgame.world.biome.BiomeRegistry;

/**
 * Biome enum for internal use. For data-driven biomes, see BiomeRegistry/BiomeData.
 */
public enum Biome {

    OCEAN("Ocean", BlockType.SAND, BlockType.SAND, 42, 4, 0x8EB971, 0x71A74D),
    BEACH("Beach", BlockType.SAND, BlockType.SAND, 64, 2, 0x91BD59, 0x77AB2F),
    PLAINS("Plains", BlockType.GRASS_BLOCK, BlockType.DIRT, 68, 6, 0x91BD59, 0x77AB2F),
    FOREST("Forest", BlockType.GRASS_BLOCK, BlockType.DIRT, 70, 10, 0x79C05A, 0x59AE30),
    BIRCH("Birch Forest", BlockType.GRASS_BLOCK, BlockType.DIRT, 70, 9, 0x87C867, 0x6BA948),
    TAIGA("Taiga", BlockType.GRASS_BLOCK, BlockType.DIRT, 72, 14, 0x86B564, 0x6B9D4E),
    DESERT("Desert", BlockType.SAND, BlockType.SAND, 67, 5, 0xBFB755, 0xAEA42A),
    SWAMP("Swamp", BlockType.GRASS_BLOCK, BlockType.DIRT, 63, 3, 0x6A7039, 0x6A7039),
    MOUNTAINS("Mountains", BlockType.GRASS_BLOCK, BlockType.DIRT, 96, 48, 0x8AB689, 0x6DA36B),
    SNOWY_PEAKS("Snowy Peaks", BlockType.SNOW, BlockType.STONE, 118, 40, 0x80B497, 0x60917B);

    public final String displayName;
    public final BlockType surface;
    public final BlockType filler;
    public final int baseHeight;
    public final int variance;
    public final int grassTint;
    public final int foliageTint;

    Biome(String displayName, BlockType surface, BlockType filler,
          int baseHeight, int variance, int grassTint, int foliageTint) {
        this.displayName = displayName;
        this.surface = surface;
        this.filler = filler;
        this.baseHeight = baseHeight;
        this.variance = variance;
        this.grassTint = grassTint;
        this.foliageTint = foliageTint;
    }

    public boolean isWatery() { return this == OCEAN || this == SWAMP; }
    public boolean isSnowy() { return this == SNOWY_PEAKS; }

    /**
     * Pick a biome from climate values, all in 0..1.
     */
    public static Biome select(double continentalness, double temperature,
                               double humidity, double erosion) {
        if (continentalness < 0.30) return OCEAN;
        if (continentalness < 0.36) return BEACH;

        if (erosion < 0.22 && continentalness > 0.58) {
            return temperature < 0.35 ? SNOWY_PEAKS : MOUNTAINS;
        }

        if (temperature > 0.72 && humidity < 0.35) return DESERT;
        if (temperature < 0.28) return TAIGA;
        if (humidity > 0.72 && temperature > 0.45) return SWAMP;
        if (humidity > 0.52) return temperature > 0.55 ? FOREST : BIRCH;

        return PLAINS;
    }
}
