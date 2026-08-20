package com.voxelgame.world.generator;

import java.util.Random;

/**
 * Perlin noise with the fractal helpers the terrain generator needs.
 *
 * All methods are pure functions of their arguments, so generation can run in
 * any order (and later on any thread) and still be reproducible from the seed.
 */
public class NoiseGenerator {
    private final int[] perm;

    public NoiseGenerator(long seed) {
        Random random = new Random(seed);
        perm = new int[512];
        int[] p = new int[256];

        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    // ------------------------------------------------------------------
    // Fractal helpers
    // ------------------------------------------------------------------

    /**
     * Fractal Brownian motion. Result is normalised to roughly -1..1.
     */
    public double fbm(double x, double z, int octaves, double frequency, double persistence) {
        double sum = 0, amp = 1, max = 0;
        for (int i = 0; i < octaves; i++) {
            sum += noise2D(x * frequency, z * frequency) * amp;
            max += amp;
            amp *= persistence;
            frequency *= 2.0;
        }
        return sum / max;
    }

    public double fbm3(double x, double y, double z, int octaves, double frequency, double persistence) {
        double sum = 0, amp = 1, max = 0;
        for (int i = 0; i < octaves; i++) {
            sum += noise3D(x * frequency, y * frequency, z * frequency) * amp;
            max += amp;
            amp *= persistence;
            frequency *= 2.0;
        }
        return sum / max;
    }

    /**
     * Ridged multifractal - sharp crests, used for mountain peaks.
     * Returns 0..1.
     */
    public double ridged(double x, double z, int octaves, double frequency, double persistence) {
        double sum = 0, amp = 1, max = 0;
        for (int i = 0; i < octaves; i++) {
            double n = 1.0 - Math.abs(noise2D(x * frequency, z * frequency));
            sum += n * n * amp;
            max += amp;
            amp *= persistence;
            frequency *= 2.0;
        }
        return sum / max;
    }

    // ------------------------------------------------------------------
    // Base Perlin
    // ------------------------------------------------------------------

    public double noise2D(double x, double y) {
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;

        x -= Math.floor(x);
        y -= Math.floor(y);

        double u = fade(x);
        double v = fade(y);

        int A = perm[X] + Y;
        int B = perm[X + 1] + Y;

        return lerp(v,
            lerp(u, grad(perm[A], x, y), grad(perm[B], x - 1, y)),
            lerp(u, grad(perm[A + 1], x, y - 1), grad(perm[B + 1], x - 1, y - 1))
        );
    }

    public double noise3D(double x, double y, double z) {
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;
        int Z = (int) Math.floor(z) & 255;

        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);

        double u = fade(x);
        double v = fade(y);
        double w = fade(z);

        int A = perm[X] + Y;
        int AA = perm[A] + Z;
        int AB = perm[A + 1] + Z;
        int B = perm[X + 1] + Y;
        int BA = perm[B] + Z;
        int BB = perm[B + 1] + Z;

        return lerp(w,
            lerp(v,
                lerp(u, grad(perm[AA], x, y, z), grad(perm[BA], x - 1, y, z)),
                lerp(u, grad(perm[AB], x, y - 1, z), grad(perm[BB], x - 1, y - 1, z))
            ),
            lerp(v,
                lerp(u, grad(perm[AA + 1], x, y, z - 1), grad(perm[BA + 1], x - 1, y, z - 1)),
                lerp(u, grad(perm[AB + 1], x, y - 1, z - 1), grad(perm[BB + 1], x - 1, y - 1, z - 1))
            )
        );
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double t, double a, double b) { return a + t * (b - a); }

    private static double grad(int hash, double x, double y) {
        int h = hash & 7;
        double u = h < 4 ? x : y;
        double v = h < 4 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
