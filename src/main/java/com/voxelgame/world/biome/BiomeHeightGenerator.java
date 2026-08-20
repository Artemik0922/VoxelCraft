package com.voxelgame.world.biome;

/**
 * Terrain height generation following MC 1.18+ spline-based approach.
 * Uses the same climate noise to drive terrain shape.
 */
public final class BiomeHeightGenerator {

    /**
     * Calculate surface height for a column.
     * @param c continentalness 0..1
     * @param e erosion 0..1
     * @param pv peaks-and-valleys
     * @param w weirdness 0..1
     * @param isRiver true if this column is a river
     * @return world Y coordinate of surface
     */
    public static int getHeight(double c, double e, double pv, double w, boolean isRiver) {
        // Base height from continentalness spline
        double base = cSpline(c);

        // Hill amplitude: high E = flatter terrain
        double eNorm = e; // 0..1
        double hillAmp = lerp(8, 2, eNorm);

        // Mountains: PV contribution — compact peaks, not long ridges
        double mountain = Math.max(0, pv) * Math.max(0, c - 0.05) * 80 * (1 - eNorm);

        // Sharp peaks for high PV — concentrated at the top
        double jagged = 0;
        if (pv > 0.5) {
            jagged = Math.pow(pv - 0.5, 2) * 60 * (1 - eNorm);
        }

        // Ridged peaks — shorter, sharper ridges (not long mountain chains)
        double ridged = 0;
        if (w < 0.4 && pv > 0.4) {
            ridged = 15 * pv * (1 - eNorm) * (1 - w * 2.5);
        }

        // Snow-capped peaks bonus
        double snowBonus = 0;
        double preliminary = base + mountain + jagged + ridged;
        if (preliminary > 85) {
            snowBonus = (preliminary - 85) * 0.2;
        }

        double height = base + mountain + jagged + ridged + snowBonus;

        // Rivers carve down
        if (isRiver) {
            height = Math.min(height, 61);
        }

        return (int) Math.round(height);
    }

    /**
     * Spline interpolation for base height by continentalness.
     * Points: (-1,-40)(-0.5,-25)(-0.19,-8)(-0.11,62)(0,64)(0.3,68)(1,80)
     */
    private static double cSpline(double c) {
        // Map c (0..1) to spline parameter (-1..1)
        double t = c * 2 - 1;

        // Spline control points (x, y)
        double[] xs = {-1, -0.5, -0.19, -0.11, 0, 0.3, 1};
        double[] ys = {-40, -25, -8, 62, 64, 68, 80};

        // Find segment
        int seg = 0;
        for (int i = 0; i < xs.length - 1; i++) {
            if (t >= xs[i] && t <= xs[i + 1]) {
                seg = i;
                break;
            }
        }

        // Lerp within segment
        double segT = (t - xs[seg]) / (xs[seg + 1] - xs[seg]);
        return lerp(ys[seg], ys[seg + 1], segT);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
