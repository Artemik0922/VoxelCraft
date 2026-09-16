package com.voxelgame.world.biome;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads biomes from biomes.json and selects the best match for a climate.
 */
public final class BiomeRegistry {

    private static final List<BiomeData> BIOMES = new ArrayList<>();
    private static boolean loaded = false;

    public static void load() {
        if (loaded) return;

        try (InputStream in = BiomeRegistry.class.getClassLoader()
                .getResourceAsStream("biomes/biomes.json")) {
            if (in == null) {
                System.err.println("Missing biomes.json");
                return;
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            parse(text);
            loaded = true;
            System.out.println("Loaded " + BIOMES.size() + " biomes");
        } catch (Exception e) {
            System.err.println("Could not load biomes: " + e.getMessage());
        }
    }

    private static void parse(String text) {
        // Minimal JSON parser for our flat biome objects
        // Find each biome object in the "biomes" array
        int arrayStart = text.indexOf('[');
        int arrayEnd = text.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd < 0) return;

        String arrayContent = text.substring(arrayStart + 1, arrayEnd);
        List<String> objects = splitObjects(arrayContent);

        for (String obj : objects) {
            BiomeData biome = parseBiome(obj);
            if (biome != null) BIOMES.add(biome);
        }
    }

    private static List<String> splitObjects(String content) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    result.add(content.substring(start + 1, i));
                    start = -1;
                }
            }
        }
        return result;
    }

    private static String getString(String text, String key) {
        if (text == null) return "";
        int idx = text.indexOf("\"" + key + "\"");
        if (idx < 0) return "";
        int colon = text.indexOf(':', idx);
        int quote1 = text.indexOf('"', colon + 1);
        if (quote1 < 0) return "";
        int quote2 = text.indexOf('"', quote1 + 1);
        if (quote2 < 0) return "";
        return text.substring(quote1 + 1, quote2);
    }

    private static float getFloat(String text, String key) {
        if (text == null) return 0;
        int idx = text.indexOf("\"" + key + "\"");
        if (idx < 0) return 0;
        int colon = text.indexOf(':', idx);
        int comma = text.indexOf(',', colon + 1);
        int brace = text.indexOf('}', colon + 1);
        // Find the nearest terminator: comma or brace (whichever comes first)
        int end;
        if (comma >= 0 && brace >= 0) end = Math.min(comma, brace);
        else if (comma >= 0) end = comma;
        else if (brace >= 0) end = brace;
        else end = text.length();
        String val = text.substring(colon + 1, end).trim();
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Hex or decimal value; the value itself may be quoted or bare. */
    private static int getHexInt(String text, String key) {
        if (text == null) return 0;
        int idx = text.indexOf("\"" + key + "\"");
        if (idx < 0) return 0;
        int colon = text.indexOf(':', idx);
        int comma = text.indexOf(',', colon + 1);
        int brace = text.indexOf('}', colon + 1);
        int end;
        if (comma >= 0 && brace >= 0) end = Math.min(comma, brace);
        else if (comma >= 0) end = comma;
        else if (brace >= 0) end = brace;
        else end = text.length();
        String val = text.substring(colon + 1, end).trim();
        if (val.startsWith("\"")) {
            int q2 = val.indexOf('"', 1);
            if (q2 >= 0) val = val.substring(1, q2);
        }
        if (val.startsWith("0x") || val.startsWith("0X")) {
            try {
                return Integer.parseInt(val.substring(2), 16);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int getInt(String text, String key) {
        return (int) getFloat(text, key);
    }

    /**
     * Extract the contents of the first object under a key:
     * "{ "tints": { ... } }" -> the inner "{ ... }" text.
     */
    private static String objectFor(String text, String key) {
        int idx = text.indexOf("\"" + key + "\"");
        if (idx < 0) return null;
        int brace = text.indexOf('{', idx);
        if (brace < 0) return null;
        int depth = 0;
        for (int i = brace; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return text.substring(brace + 1, i);
            }
        }
        return null;
    }

    private static String getNestedString(String text, String parent, String child) {
        String nested = objectFor(text, parent);
        return nested == null ? "" : getString(nested, child);
    }

    private static float getNestedFloat(String text, String parent, String child) {
        String nested = objectFor(text, parent);
        return nested == null ? 0 : getFloat(nested, child);
    }

    private static int getNestedHexInt(String text, String parent, String child) {
        String nested = objectFor(text, parent);
        return nested == null ? 0 : getHexInt(nested, child);
    }

    private static BiomeData parseBiome(String obj) {
        try {
            String id = getString(obj, "id");
            String nameKey = getString(obj, "name");
            String climate = objectFor(obj, "climate");
            String tints = objectFor(obj, "tints");
            String features = objectFor(obj, "features");
            float tempMin = getFloat(climate, "temp_min");
            float tempMax = getFloat(climate, "temp_max");
            float humidMin = getFloat(climate, "humid_min");
            float humidMax = getFloat(climate, "humid_max");
            float contMin = getFloat(climate, "cont_min");
            float contMax = getFloat(climate, "cont_max");
            int grassTint = getHexInt(tints, "grass");
            int foliageTint = getHexInt(tints, "foliage");
            int waterTint = getHexInt(tints, "water");
            String surfaceBlock = getString(obj, "surface");
            String fillerBlock = getString(obj, "filler");
            String treeType = getNestedString(features, "trees", "type");
            float treeDensity = getNestedFloat(features, "trees", "density");
            String flowerType = getNestedString(features, "flowers", "type");
            float flowerDensity = getNestedFloat(features, "flowers", "density");
            String grassType = getNestedString(features, "grass", "type");
            float grassDensity = getNestedFloat(features, "grass", "density");
            String particleType = getNestedString(features, "particles", "type");
            float particleDensity = getNestedFloat(features, "particles", "density");
            int fogColor = getHexInt(obj, "fog_color");

            return new BiomeData(id, nameKey,
                tempMin, tempMax, humidMin, humidMax, contMin, contMax,
                grassTint, foliageTint, waterTint,
                surfaceBlock, fillerBlock,
                treeType, treeDensity, flowerType, flowerDensity,
                grassType, grassDensity, particleType, particleDensity,
                fogColor);
        } catch (Exception e) {
            System.err.println("Failed to parse biome: " + e.getMessage());
            return null;
        }
    }

    /** Look up a biome by its JSON id, or null. */
    public static BiomeData get(String id) {
        if (id == null) return null;
        for (BiomeData b : BIOMES) {
            if (b.id.equals(id)) return b;
        }
        return null;
    }

    /** Maps every selector biome to its biomes.json id. */
    private static final Map<BiomeSelector.MCBiome, String> MC_JSON = new HashMap<>();
    static {
        MC_JSON.put(BiomeSelector.MCBiome.FROZEN_OCEAN, "frozen_ocean");
        MC_JSON.put(BiomeSelector.MCBiome.COLD_OCEAN, "cold_ocean");
        MC_JSON.put(BiomeSelector.MCBiome.DEEP_OCEAN, "deep_ocean");
        MC_JSON.put(BiomeSelector.MCBiome.OCEAN, "ocean");
        MC_JSON.put(BiomeSelector.MCBiome.WARM_OCEAN, "warm_ocean");
        MC_JSON.put(BiomeSelector.MCBiome.LUKEWARM_OCEAN, "lukewarm_ocean");
        MC_JSON.put(BiomeSelector.MCBiome.MUSHROOM_FIELDS, "mushroom");
        MC_JSON.put(BiomeSelector.MCBiome.BEACH, "beach");
        MC_JSON.put(BiomeSelector.MCBiome.SNOWY_BEACH, "snowy_beach");
        MC_JSON.put(BiomeSelector.MCBiome.STONY_SHORE, "stony_shore");
        MC_JSON.put(BiomeSelector.MCBiome.JAGGED_PEAKS, "jagged_peaks");
        MC_JSON.put(BiomeSelector.MCBiome.FROZEN_PEAKS, "frozen_peaks");
        MC_JSON.put(BiomeSelector.MCBiome.STONY_PEAKS, "stony_peaks");
        MC_JSON.put(BiomeSelector.MCBiome.SNOWY_PLAINS, "snowy_plains");
        MC_JSON.put(BiomeSelector.MCBiome.SNOWY_TAIGA, "snowy_taiga");
        MC_JSON.put(BiomeSelector.MCBiome.GROVE, "grove");
        MC_JSON.put(BiomeSelector.MCBiome.PLAINS, "plains");
        MC_JSON.put(BiomeSelector.MCBiome.FOREST, "forest");
        MC_JSON.put(BiomeSelector.MCBiome.BIRCH_FOREST, "birch");
        MC_JSON.put(BiomeSelector.MCBiome.TAIGA, "taiga");
        MC_JSON.put(BiomeSelector.MCBiome.OLD_GROWTH_PINE_TAIGA, "giant_tree_taiga");
        MC_JSON.put(BiomeSelector.MCBiome.OLD_GROWTH_SPRUCE_TAIGA, "giant_spruce_taiga");
        MC_JSON.put(BiomeSelector.MCBiome.FLOWER_FOREST, "flower_forest");
        MC_JSON.put(BiomeSelector.MCBiome.DARK_FOREST, "dark_forest");
        MC_JSON.put(BiomeSelector.MCBiome.SAVANNA, "savanna");
        MC_JSON.put(BiomeSelector.MCBiome.SAVANNA_PLATEAU, "savanna_plateau");
        MC_JSON.put(BiomeSelector.MCBiome.DESERT, "desert");
        MC_JSON.put(BiomeSelector.MCBiome.SWAMP, "swamp");
        MC_JSON.put(BiomeSelector.MCBiome.JUNGLE, "jungle");
        MC_JSON.put(BiomeSelector.MCBiome.BAMBOO_JUNGLE, "bamboo_jungle");
        MC_JSON.put(BiomeSelector.MCBiome.ICE_SPIKES, "ice_spikes");
        MC_JSON.put(BiomeSelector.MCBiome.MANGROVE_SWAMP, "mangrove_swamp");
        MC_JSON.put(BiomeSelector.MCBiome.WINDSWEPT_HILLS, "windswept_hills");
        MC_JSON.put(BiomeSelector.MCBiome.WINDSWEPT_FOREST, "windswept_forest");
        MC_JSON.put(BiomeSelector.MCBiome.BADLANDS, "badlands");
        MC_JSON.put(BiomeSelector.MCBiome.WOODED_BADLANDS, "wooded_badlands");
        MC_JSON.put(BiomeSelector.MCBiome.PLATEAU, "plateau");
        MC_JSON.put(BiomeSelector.MCBiome.SHATTERED_SAVANNA, "shattered_savanna");
        MC_JSON.put(BiomeSelector.MCBiome.LAVENDER_MEADOW, "lavender_meadow");
        MC_JSON.put(BiomeSelector.MCBiome.CRYSTAL_PEAKS, "crystal_peaks");
        MC_JSON.put(BiomeSelector.MCBiome.GLACIER, "glacier");
        MC_JSON.put(BiomeSelector.MCBiome.VOLCANO, "volcanic_highlands");
        MC_JSON.put(BiomeSelector.MCBiome.AUTUMN_FOREST, "autumn_forest");
        MC_JSON.put(BiomeSelector.MCBiome.PETRIFIED_FOREST, "petrified_forest");
        MC_JSON.put(BiomeSelector.MCBiome.MOOR, "misty_moor");
        MC_JSON.put(BiomeSelector.MCBiome.SALT_FLATS, "salt_flats");
        MC_JSON.put(BiomeSelector.MCBiome.ASH_WASTES, "ash_wastes");
    }

    /** biomes.json id for a selector biome, or null if unmapped. */
    public static String jsonIdFor(BiomeSelector.MCBiome biome) {
        return biome == null ? null : MC_JSON.get(biome);
    }

    /** Find the best-matching biome for a climate using nearest-center distance. */
    public static BiomeData select(float temp, float humid, float cont) {
        BiomeData best = null;
        double bestDist = Double.MAX_VALUE;

        for (BiomeData b : BIOMES) {
            double ct = (b.tempMin + b.tempMax) / 2.0;
            double ch = (b.humidMin + b.humidMax) / 2.0;
            double cc = (b.contMin + b.contMax) / 2.0;
            double dt = temp - ct;
            double dh = humid - ch;
            double dc = cont - cc;
            double dist = dt * dt + dh * dh + dc * dc;

            if (dist < bestDist) {
                bestDist = dist;
                best = b;
            }
        }

        return best;
    }

    public static List<BiomeData> all() {
        return BIOMES;
    }
}
