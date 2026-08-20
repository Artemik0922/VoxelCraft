package com.voxelgame.world.biome;

import java.util.HashMap;
import java.util.Map;

/**
 * Selects biomes using the MC 1.18+ multi-noise table.
 * Order: C-strip (ocean/mushroom) -> inland by (E, PV) -> middle by (T,H) -> variants -> custom.
 */
public final class BiomeSelector {

    public enum MCBiome {
        // Oceans
        FROZEN_OCEAN, DEEP_OCEAN, OCEAN,
        MUSHROOM_FIELDS,
        // Shore
        BEACH, SNOWY_BEACH, STONY_SHORE,
        // Peaks
        JAGGED_PEAKS, FROZEN_PEAKS, STONY_PEAKS,
        // Middle biomes
        SNOWY_PLAINS, SNOWY_TAIGA, GROVE,
        PLAINS, FOREST, BIRCH_FOREST, TAIGA, OLD_GROWTH_PINE_TAIGA, OLD_GROWTH_SPRUCE_TAIGA,
        FLOWER_FOREST, DARK_FOREST,
        SAVANNA, SAVANNA_PLATEAU,
        DESERT, SWAMP, JUNGLE,
        // Highlands
        WINDSWEPT_HILLS, WINDSWEPT_FOREST,
        // Plateaus
        BADLANDS, WOODED_BADLANDS, PLATEAU,
        // Shattered
        SHATTERED_SAVANNA,
        // Original biomes
        LAVENDER_MEADOW, CRYSTAL_PEAKS, GLACIER, VOLCANO, AUTUMN_FOREST,
        PETRIFIED_FOREST, MOOR, SALT_FLATS, ASH_WASTES,
        // [WG] World-gen expansion
        BAMBOO_JUNGLE, ICE_SPIKES, MANGROVE_SWAMP, WARM_OCEAN, LUKEWARM_OCEAN, COLD_OCEAN
    }

    private final Map<String, MCBiome> cache = new HashMap<>();

    /**
     * Select biome for a climate point.
     * @param t temperature 0..1
     * @param h humidity 0..1
     * @param c continentalness 0..1
     * @param e erosion 0..1
     * @param w weirdness 0..1
     * @param pv peaks-and-valleys
     */
    public MCBiome select(double t, double h, double c, double e, double w, double pv) {
        // Step 1: C-strip (ocean/mushroom/shore)
        MCBiome biome = selectCStrip(c, t, pv);
        if (biome != null) return biome;

        // Step 2: Inland by (E, PV) for peaks and highlands
        biome = selectInland(e, pv, t, w);
        if (biome != null) return biome;

        // Step 3: Middle biomes by (T, H)
        biome = selectMiddle(t, h, w);

        // Step 4: Variants by W
        biome = selectVariant(biome, w);

        // Step 5: Custom original biomes at climate extremes
        biome = selectCustom(biome, t, h, c, e, w, pv);

        return biome;
    }

    private MCBiome selectCStrip(double c, double t, double pv) {
        // Mushroom islands: very low continentalness, isolated
        if (c < 0.03) return MCBiome.MUSHROOM_FIELDS;

        // Deep ocean (rare) — temperature-graded
        if (c < 0.12) return oceanByTemp(t, true);

        // Ocean — reduced area
        if (c < 0.2) return oceanByTemp(t, false);

        // Beach/shore — thin strip
        if (c < 0.25 && pv < 0.15) return t < 0.2 ? MCBiome.SNOWY_BEACH : MCBiome.BEACH;

        return null; // Continue to inland selection
    }

    /** Ocean type graded by temperature. */
    private MCBiome oceanByTemp(double t, boolean deep) {
        if (t < 0.2) return MCBiome.FROZEN_OCEAN;
        if (t < 0.45) return MCBiome.COLD_OCEAN;
        if (t < 0.65) return deep ? MCBiome.DEEP_OCEAN : MCBiome.OCEAN;
        if (t < 0.85) return MCBiome.LUKEWARM_OCEAN;
        return MCBiome.WARM_OCEAN;
    }

    private MCBiome selectInland(double e, double pv, double t, double w) {
        int eL = eLevel(e);
        String pvL = pvLevel(pv);

        // Peaks: low E + high PV — rare so most worlds aren't all mountains
        if (eL <= 2 && pv > 0.55) {
            if (t < 0.25) return w < 0.5 ? MCBiome.JAGGED_PEAKS : MCBiome.FROZEN_PEAKS;
            if (t < 0.5) return MCBiome.STONY_PEAKS;
            return MCBiome.BADLANDS;
        }

        // High hills: E0-2 + very high PV
        if (eL <= 2 && pv > 0.7) {
            return t < 0.3 ? MCBiome.GROVE : MCBiome.WINDSWEPT_HILLS;
        }

        // Windswept: narrow erosion band + mid PV
        if (eL == 4 && pv > 0.5) {
            return t < 0.4 ? MCBiome.WINDSWEPT_FOREST : MCBiome.WINDSWEPT_HILLS;
        }

        // Stony shore: low E + coast
        if (eL == 0 && pvL.equals("low")) {
            return MCBiome.STONY_SHORE;
        }

        // Plateaus: E5+ + warm + mid PV (rare)
        if (eL >= 5 && t >= 0.4 && pv > 0.2) {
            return MCBiome.PLATEAU;
        }

        // Snowy slopes/grove: E mid + cold + mid PV
        if (eL >= 2 && eL <= 3 && t < 0.3 && Math.abs(pv) < 0.2) {
            return MCBiome.GROVE;
        }

        // Shattered: E5+ + weird
        if (eL >= 5 && w > 0.55) {
            return MCBiome.SHATTERED_SAVANNA;
        }

        return null; // Continue to middle biomes
    }

    private MCBiome selectMiddle(double t, double h, double w) {
        int tL = tLevel(t);
        int hL = hLevel(h);

        // Expanded table for more biome diversity
        return switch (tL) {
            case 0 -> switch (hL) { // frozen
                case 0, 1 -> MCBiome.SNOWY_PLAINS;
                case 2 -> MCBiome.GROVE;
                case 3 -> MCBiome.SNOWY_TAIGA;
                default -> w > 0.6 ? MCBiome.SNOWY_TAIGA : MCBiome.SNOWY_PLAINS;
            };
            case 1 -> switch (hL) { // cold
                case 0 -> MCBiome.PLAINS;
                case 1 -> w > 0.4 ? MCBiome.OLD_GROWTH_SPRUCE_TAIGA : MCBiome.FOREST;
                case 2 -> w > 0.3 ? MCBiome.TAIGA : MCBiome.FOREST;
                case 3 -> MCBiome.TAIGA;
                default -> MCBiome.OLD_GROWTH_PINE_TAIGA;
            };
            case 2 -> switch (hL) { // temperate
                case 0 -> w > 0.5 ? MCBiome.FLOWER_FOREST : MCBiome.PLAINS;
                case 1 -> w > 0.4 ? MCBiome.FLOWER_FOREST : MCBiome.FOREST;
                case 2 -> w > 0.3 ? MCBiome.BIRCH_FOREST : MCBiome.FOREST;
                case 3 -> MCBiome.DARK_FOREST;
                default -> h > 0.8 ? MCBiome.SWAMP : MCBiome.DARK_FOREST;
            };
            case 3 -> switch (hL) { // warm
                case 0 -> MCBiome.SAVANNA;
                case 1 -> w > 0.5 ? MCBiome.SAVANNA : MCBiome.PLAINS;
                case 2 -> MCBiome.PLAINS;
                case 3 -> w > 0.4 ? MCBiome.BIRCH_FOREST : MCBiome.FOREST;
                default -> MCBiome.JUNGLE;
            };
            case 4 -> switch (hL) { // hot
                case 0, 1 -> MCBiome.DESERT;
                case 2 -> w > 0.5 ? MCBiome.SAVANNA_PLATEAU : MCBiome.SAVANNA;
                case 3 -> MCBiome.SWAMP;
                default -> w > 0.3 ? MCBiome.JUNGLE : MCBiome.SAVANNA;
            };
            default -> MCBiome.PLAINS;
        };
    }

    private MCBiome selectVariant(MCBiome base, double w) {
        if (w < 0.6) return base;
        // High weirdness variants
        return switch (base) {
            case PLAINS -> MCBiome.PLAINS; // could add sunflower
            case FOREST -> MCBiome.FLOWER_FOREST;
            case TAIGA -> MCBiome.OLD_GROWTH_PINE_TAIGA;
            case OLD_GROWTH_PINE_TAIGA -> MCBiome.OLD_GROWTH_SPRUCE_TAIGA;
            // [WG] JUNGLE/SNOWY_PLAINS/SWAMP variants at high weirdness
            case JUNGLE -> MCBiome.BAMBOO_JUNGLE;
            case SNOWY_PLAINS -> MCBiome.ICE_SPIKES;
            case SWAMP -> MCBiome.MANGROVE_SWAMP;
            default -> base;
        };
    }

    private MCBiome selectCustom(MCBiome current, double t, double h, double c, double e, double w, double pv) {
        // Lavender meadow: high weirdness + temperate
        if (w > 0.75 && t >= 0.4 && t <= 0.7 && h >= 0.3 && h <= 0.7) return MCBiome.LAVENDER_MEADOW;

        // Crystal peaks: low weirdness (valleys) + peaks slot
        if (w < 0.25 && pvLevel(pv).equals("peaks")) return MCBiome.CRYSTAL_PEAKS;

        // Glacier: cold + high weirdness
        if (t < 0.2 && w > 0.6) return MCBiome.GLACIER;

        // Volcano: hot + arid + high weirdness
        if (t > 0.8 && h < 0.25 && w > 0.6) return MCBiome.VOLCANO;

        // Autumn forest: temperate + humid + high weirdness
        if (t >= 0.4 && t <= 0.7 && h >= 0.6 && w > 0.7) return MCBiome.AUTUMN_FOREST;

        // Petrified forest: temperate + dry + high weirdness
        if (t >= 0.3 && t <= 0.6 && h < 0.4 && w > 0.7) return MCBiome.PETRIFIED_FOREST;

        // Moor: swamp + medium-high weirdness
        if (current == MCBiome.SWAMP && w > 0.5) return MCBiome.MOOR;

        // Salt flats: beach + hot
        if (current == MCBiome.BEACH && t > 0.7) return MCBiome.SALT_FLATS;

        // Ash wastes: cold + dry + low weirdness (valleys)
        if (t < 0.3 && h < 0.3 && w < 0.3) return MCBiome.ASH_WASTES;

        return current;
    }

    private int tLevel(double t) {
        // [WG] Slightly relaxed extremes so desert and frozen land actually
        // appear inside world-climate-shifted seeds
        if (t < 0.18) return 0;
        if (t < 0.45) return 1;
        if (t < 0.75) return 2;
        if (t < 0.85) return 3;
        return 4;
    }

    private int hLevel(double h) {
        // [WG] Slightly relaxed wetness bands so jungles and swamps can
        // actually appear within world-climate-shifted seeds
        if (h < 0.22) return 0;
        if (h < 0.45) return 1;
        if (h < 0.68) return 2;
        if (h < 0.82) return 3;
        return 4;
    }

    private int eLevel(double e) {
        if (e < 0.1) return 0;
        if (e < 0.25) return 1;
        if (e < 0.4) return 2;
        if (e < 0.55) return 3;
        if (e < 0.7) return 4;
        if (e < 0.85) return 5;
        return 6;
    }

    private String pvLevel(double pv) {
        if (pv < -0.5) return "valleys";
        if (pv < -0.2) return "low";
        if (pv < 0.2) return "mid";
        if (pv < 0.5) return "high";
        return "peaks";
    }
}
