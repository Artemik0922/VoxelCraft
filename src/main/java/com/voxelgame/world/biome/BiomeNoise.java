package com.voxelgame.world.biome;

import com.voxelgame.world.generator.NoiseGenerator;

/**
 * Five noise fields mirroring Minecraft 1.18+ multi-noise biome system.
 * T=temperature, H=humidity, C=continentalness, E=erosion, W=weirdness.
 * Each is an fBm field derived from the world seed with its own frequency.
 */
public final class BiomeNoise {

    private final NoiseGenerator tempNoise;
    private final NoiseGenerator humidNoise;
    private final NoiseGenerator contNoise;
    private final NoiseGenerator erodeNoise;
    private final NoiseGenerator weirdNoise;
    private final NoiseGenerator pvNoise; // For peaks/valleys detection

    // [WG] World personality: deterministic per-seed shifts so that different
    // seeds produce visibly different planets, not just re-rolled noise.
    private final double tempShift;
    private final double humidShift;
    private final double contShift;
    private final double erodeShift;
    private final double weirdShift;
    /** Multiplier for the continentalness frequency: small worlds with many
     *  islands vs. one giant landmass. */
    private final double contScale;
    /** Terrain ruggedness: flatter worlds vs. jagged mountains. */
    private final double relief;

    public BiomeNoise(long seed) {
        tempNoise = new NoiseGenerator(seed);
        humidNoise = new NoiseGenerator(seed + 100);
        contNoise = new NoiseGenerator(seed + 200);
        erodeNoise = new NoiseGenerator(seed + 300);
        weirdNoise = new NoiseGenerator(seed + 400);
        pvNoise = new NoiseGenerator(seed + 500);

        NoiseGenerator personality = new NoiseGenerator(seed + 9000);
        // noise2D peaks around +/-0.54, so the raw factor is boosted to
        // make the extremes reachable
        tempShift = personality.noise2D(0.37, 0.91) * 0.60;
        humidShift = personality.noise2D(0.11, 0.73) * 0.45;
        contShift = personality.noise2D(0.83, 0.29) * 0.38;
        erodeShift = personality.noise2D(0.57, 0.44) * 0.28;
        weirdShift = personality.noise2D(0.21, 0.67) * 0.22;
        contScale = 0.75 + 0.70 * (0.5 + 0.5 * personality.noise2D(0.92, 0.05));
        relief = 0.70 + 0.60 * (0.5 + 0.5 * personality.noise2D(0.48, 0.81));
    }

    public double tempShift() { return tempShift; }
    public double humidShift() { return humidShift; }
    public double contShift() { return contShift; }
    public double erodeShift() { return erodeShift; }
    public double weirdShift() { return weirdShift; }
    public double contScale() { return contScale; }
    public double relief() { return relief; }

    // Raw noise values 0..1
    // Higher frequency = more biome variation across the world
    public double T(int x, int z) { return clamp(norm(tempNoise.fbm(x, z, 4, 1.0/400, 0.5)) + tempShift); }
    public double H(int x, int z) { return clamp(norm(humidNoise.fbm(x, z, 4, 1.0/350, 0.5)) + humidShift); }
    public double C(int x, int z) {
        // [WG] A second, higher-frequency continental layer creates bays,
        // islands and shorelines inside every world, not only on the
        // planet-wide scale of the 1/600 octave.
        double local = contNoise.fbm(x, z, 2, 1.0/140, 0.5) * 0.35;
        return clamp(norm(contNoise.fbm(x * contScale, z * contScale, 4, 1.0/600, 0.5))
            + local + contShift);
    }
    public double E(int x, int z) { return clamp(norm(erodeNoise.fbm(x, z, 4, 1.0/350, 0.5)) + erodeShift); }
    public double W(int x, int z) { return clamp(norm(weirdNoise.fbm(x, z, 4, 1.0/200, 0.5)) + weirdShift); }

    /**
     * Peaks-and-valleys parameter: 0 at valleys, 1 at peaks.
     * PV = 1 - abs(3*abs(W) - 2) gives the characteristic MC ridge shape.
     */
    public double PV(int x, int z) {
        double w = W(x, z);
        return 1.0 - Math.abs(3.0 * Math.abs(w - 0.5) - 1.0);
    }

    // Domain-warped continentalness (for mushroom islands)
    public double C_warped(int x, int z) {
        double warp = contNoise.fbm(x + 1000, z + 1000, 2, 1.0/200, 0.5);
        return C(x, z) + warp * 0.06;
    }

    // Level lookups (0-indexed)
    public int T_level(double t) {
        if (t < 0.15) return 0;      // frozen
        if (t < 0.45) return 1;      // cold
        if (t < 0.75) return 2;      // temperate
        if (t < 0.9) return 3;       // warm
        return 4;                     // hot
    }

    public int H_level(double h) {
        if (h < 0.25) return 0;      // arid
        if (h < 0.5) return 1;       // dry
        if (h < 0.75) return 2;      // moderate
        if (h < 0.9) return 3;       // humid
        return 4;                     // wet
    }

    public int E_level(double e) {
        if (e < 0.1) return 0;
        if (e < 0.25) return 1;
        if (e < 0.4) return 2;
        if (e < 0.55) return 3;
        if (e < 0.7) return 4;
        if (e < 0.85) return 5;
        return 6;
    }

    public String PV_level(double pv) {
        if (pv < -0.5) return "valleys";
        if (pv < -0.2) return "low";
        if (pv < 0.2) return "mid";
        if (pv < 0.5) return "high";
        return "peaks";
    }

    private static double norm(double v) {
        return Math.max(0, Math.min(1, (v + 1) * 0.5));
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
