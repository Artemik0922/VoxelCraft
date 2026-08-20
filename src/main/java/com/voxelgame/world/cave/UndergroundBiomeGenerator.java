package com.voxelgame.world.cave;

import com.voxelgame.world.generator.NoiseGenerator;

/**
 * Underground biome generation (MC 1.18+).
 * Lush caves, dripstone caves, deep dark.
 */
public final class UndergroundBiomeGenerator {

    private final NoiseGenerator humidityNoise;
    private final NoiseGenerator dripNoise;
    private final NoiseGenerator ddNoise;

    public UndergroundBiomeGenerator(long seed) {
        humidityNoise = new NoiseGenerator(seed + 3000);
        dripNoise = new NoiseGenerator(seed + 3001);
        ddNoise = new NoiseGenerator(seed + 3002);
    }

    public enum UndergroundBiome {
        NONE, LUSH_CAVE, DRIPSTONE_CAVE, DEEP_DARK
    }

    /**
     * Get underground biome at a position.
     * @param x world X
     * @param y world Y (must be below surface)
     * @param z world Z
     * @param isCave true if this is a cave air block
     */
    public UndergroundBiome getBiome(int x, int y, int z, boolean isCave) {
        if (!isCave) return UndergroundBiome.NONE;

        // Deep dark: deep underground + noise
        if (y < 20) {
            double dd = ddNoise.noise3D(x * 0.02, y * 0.02, z * 0.02);
            if (dd > 0.6) {
                return UndergroundBiome.DEEP_DARK;
            }
        }

        // Lush caves: high humidity + below surface
        if (y < 40) {
            double humid = humidityNoise.noise2D(x * 0.01, z * 0.01);
            if (humid > 0.5) {
                return UndergroundBiome.LUSH_CAVE;
            }
        }

        // Dripstone caves: drip noise
        double drip = dripNoise.noise3D(x * 0.03, y * 0.05, z * 0.03);
        if (drip > 0.4) {
            return UndergroundBiome.DRIPSTONE_CAVE;
        }

        return UndergroundBiome.NONE;
    }

    /**
     * Check if a lush cave should have moss at this position.
     */
    public boolean hasMoss(int x, int y, int z, UndergroundBiome biome) {
        if (biome != UndergroundBiome.LUSH_CAVE) return false;
        // Moss on ceiling (upper part of cave)
        return y > 25;
    }

    /**
     * Check if a dripstone cave should have dripstone.
     */
    public boolean hasDripstone(int x, int y, int z, UndergroundBiome biome) {
        if (biome != UndergroundBiome.DRIPSTONE_CAVE) return false;
        // Dripstone on floor and ceiling
        return y < 30 && y > 5;
    }

    /**
     * Check if surface above lush cave should have azalea (marker).
     */
    public boolean hasAzalea(int x, int z) {
        double humid = humidityNoise.noise2D(x * 0.01, z * 0.01);
        return humid > 0.6;
    }
}
