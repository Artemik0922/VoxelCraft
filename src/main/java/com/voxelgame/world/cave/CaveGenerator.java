package com.voxelgame.world.cave;

import com.voxelgame.world.generator.NoiseGenerator;

/**
 * 3D cave carving using multi-noise approach (MC 1.18+ style).
 * Combines cheese (large halls), spaghetti (worm tunnels), and noodle (small passages).
 */
public final class CaveGenerator {

    private final NoiseGenerator cheeseNoise;
    private final NoiseGenerator spaghettiA;
    private final NoiseGenerator spaghettiB;
    private final NoiseGenerator noodleNoise;
    private final NoiseGenerator pillarNoise;
    private final NoiseGenerator aquiferNoise;
    private final NoiseGenerator aquiferNoise2;

    public CaveGenerator(long seed) {
        cheeseNoise = new NoiseGenerator(seed + 1000);
        spaghettiA = new NoiseGenerator(seed + 1001);
        spaghettiB = new NoiseGenerator(seed + 1002);
        noodleNoise = new NoiseGenerator(seed + 1003);
        pillarNoise = new NoiseGenerator(seed + 1004);
        aquiferNoise = new NoiseGenerator(seed + 1005);
        aquiferNoise2 = new NoiseGenerator(seed + 1006);
    }

    /**
     * Determine if a block should be carved as cave air.
     * @param x world X
     * @param y world Y
     * @param z world Z
     * @param surfaceHeight surface Y at this column
     * @return true if this block should be cave air
     */
    public boolean isCave(int x, int y, int z, int surfaceHeight) {
        // Only carve below surface (with some margin for entrances)
        if (y > surfaceHeight - 3) return false;

        // Don't carve too deep (bedrock floor)
        if (y < 4) return false;

        // In mountains (high surface), carve more aggressively
        boolean inMountain = surfaceHeight > 80;
        double depthFactor = inMountain ? Math.min(1.0, (surfaceHeight - 60) / 40.0) : 0.5;

        // Cheese: large halls (more common in mountains)
        double cheese = cheeseNoise.fbm3(x, y * 1.5, z, 3, 1.0 / 90, 0.5);
        double cheeseThreshold = inMountain ? 0.45 : 0.55;
        if (cheese > cheeseThreshold) {
            // Pillars: preserve columns where pillar noise is high
            double pillar = pillarNoise.noise3D(x * 0.1, y * 0.1, z * 0.1);
            if (pillar > 0.7) return false;
            return true;
        }

        // Spaghetti: worm tunnels (intersection of two zero-crossings)
        double a = spaghettiA.fbm3(x, y * 1.6, z, 2, 1.0 / 60, 0.5);
        double b = spaghettiB.fbm3(x, y * 1.6, z, 2, 1.0 / 60, 0.5);
        double spaghettiThreshold = inMountain ? 0.15 : 0.12;
        if (Math.abs(a) < spaghettiThreshold && Math.abs(b) < spaghettiThreshold) {
            return true;
        }

        // Noodle: small additional passages gated by noise
        double noodle = noodleNoise.fbm3(x, y * 1.5, z, 2, 1.0 / 100, 0.5);
        double noodleThreshold = inMountain ? 0.25 : 0.3;
        if (noodle > noodleThreshold) {
            double a2 = spaghettiA.fbm3(x + 100, y * 1.6, z + 100, 2, 1.0 / 60, 0.5);
            double b2 = spaghettiB.fbm3(x + 100, y * 1.6, z + 100, 2, 1.0 / 60, 0.5);
            if (Math.abs(a2) < 0.18 && Math.abs(b2) < 0.18) {
                return true;
            }
        }

        // Mountain-specific: deep caverns near surface in high peaks
        if (inMountain && y > surfaceHeight - 20 && y < surfaceHeight - 5) {
            double cavern = cheeseNoise.fbm3(x, y * 1.2, z, 2, 1.0 / 70, 0.5);
            if (cavern > 0.5) return true;
        }

        // Vertical shafts in mountains (connect cave levels)
        if (isVerticalShaft(x, y, z, surfaceHeight)) {
            return true;
        }

        // Underground lakes in deep caves
        if (inMountain && y < 25 && y > 8) {
            double lake = cheeseNoise.fbm3(x, y * 0.8, z, 2, 1.0 / 60, 0.5);
            if (lake > 0.6) return true;
        }

        return false;
    }

    /**
     * Get aquifer water level for a column.
     * @return water Y level, or -100 if no aquifer
     */
    public int getAquiferLevel(int x, int z) {
        double noise = aquiferNoise.noise2D(x * 0.015, z * 0.015);
        if (noise > 0.7) return -100; // No aquifer

        double level = aquiferNoise2.noise2D(x * 0.02, z * 0.02);
        // Map to range -5..30
        return (int) Math.round(-5 + level * 35);
    }

    /**
     * Determine if a block should be water (aquifer or underground lake).
     */
    public boolean isWater(int x, int y, int z, int surfaceHeight) {
        int aquiferLevel = getAquiferLevel(x, z);
        if (aquiferLevel != -100) {
            // Water fills below aquifer level and above lava floor
            if (y <= aquiferLevel && y > 5) return true;
        }

        // Underground lakes in mountains
        if (surfaceHeight > 70 && y < 20 && y > 5) {
            double lake = cheeseNoise.fbm3(x, y * 0.8, z, 2, 1.0 / 50, 0.5);
            if (lake > 0.65) return true;
        }

        return false;
    }

    /**
     * Determine if a block should be lava (deep underground, above bedrock).
     */
    public boolean isLava(int y) {
        return y <= 10 && y >= 4;
    }

    /**
     * Check if surface should have a cave entrance (more common in mountains).
     * Returns 0 = no entrance, 1 = small entrance, 2 = large entrance
     */
    public int isCaveEntrance(int x, int z, int surfaceHeight) {
        if (surfaceHeight < 40) return 0;
        double cheese = cheeseNoise.fbm3(x, (surfaceHeight - 2) * 1.5, z, 3, 1.0 / 90, 0.5);
        // Much lower threshold for more entrances
        double threshold = surfaceHeight > 70 ? 0.35 : 0.5;
        if (surfaceHeight > 90) threshold = 0.25;
        if (surfaceHeight > 100) threshold = 0.2;
        if (cheese > threshold) {
            return cheese > threshold + 0.2 ? 2 : 1; // 2 = large, 1 = small
        }
        return 0;
    }

    /**
     * Multi-level cave system: vertical shafts connecting cavern levels.
     */
    public boolean isVerticalShaft(int x, int y, int z, int surfaceHeight) {
        if (surfaceHeight < 70) return false;
        if (y < 15 || y > surfaceHeight - 10) return false;
        double shaft = cheeseNoise.noise3D(x * 0.08, y * 0.15, z * 0.08);
        return shaft > 0.75;
    }
}
