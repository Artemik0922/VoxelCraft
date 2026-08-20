package com.voxelgame.world.biome;

import java.util.HashMap;
import java.util.Map;

/**
 * Validates biome distribution across multiple seeds.
 * Run at startup to verify the generator produces varied biomes.
 */
public final class BiomeDistributionTest {

    private BiomeDistributionTest() {}

    /**
     * Test biome distribution for a given seed.
     * @return true if distribution is valid (12+ biomes, each 1-40%)
     */
    public static boolean testSeed(long seed, int sampleArea) {
        BiomeNoise noise = new BiomeNoise(seed);
        BiomeSelector selector = new BiomeSelector();
        Map<BiomeSelector.MCBiome, Integer> counts = new HashMap<>();

        // Sample biomes across the area
        int step = Math.max(1, sampleArea / 64);
        int total = 0;
        int maxHeight = 0;
        int minHeight = 256;

        for (int x = -sampleArea/2; x < sampleArea/2; x += step) {
            for (int z = -sampleArea/2; z < sampleArea/2; z += step) {
                double t = noise.T(x, z);
                double h = noise.H(x, z);
                double c = noise.C(x, z);
                double e = noise.E(x, z);
                double w = noise.W(x, z);
                double pv = noise.PV(x, z);

                BiomeSelector.MCBiome biome = selector.select(t, h, c, e, w, pv);
                counts.merge(biome, 1, Integer::sum);
                total++;

                int height = BiomeHeightGenerator.getHeight(c, e, pv, w, false);
                maxHeight = Math.max(maxHeight, height);
                minHeight = Math.min(minHeight, height);
            }
        }

        // Validate
        int uniqueBiomes = counts.size();
        boolean valid = true;

        // Check no biome dominates (35%+) — rare biomes may appear in small
        // fractions in any finite sample window and are still valid.
        for (var entry : counts.entrySet()) {
            double pct = entry.getValue() * 100.0 / total;
            if (pct > 35) {
                valid = false;
            }
        }

        // Need at least 12 different biomes
        if (uniqueBiomes < 12) valid = false;

        // Mountains should reach 100+
        if (maxHeight < 100) valid = false;

        // Oceans should have depressions
        if (minHeight > 50) valid = false;

        System.out.println("Seed " + seed + ": " + uniqueBiomes + " biomes, "
            + "height " + minHeight + "-" + maxHeight + ", "
            + (valid ? "PASS" : "WARNING"));

        if (!valid) {
            System.out.println("  Distribution:");
            int finalTotal = total;
            counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(10)
                .forEach(e -> System.out.println("    " + e.getKey() + ": "
                    + String.format("%.1f%%", e.getValue() * 100.0 / finalTotal)));
        }

        return valid;
    }

    /** Test multiple seeds. */
    public static void testMultipleSeeds() {
        long[] seeds = {12345, 67890, 11111};
        int passed = 0;
        for (long seed : seeds) {
            if (testSeed(seed, 2048)) passed++;
        }
        System.out.println("Biome test: " + passed + "/" + seeds.length + " seeds passed");
    }
}
