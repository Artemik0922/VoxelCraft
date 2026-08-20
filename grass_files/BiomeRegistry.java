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
        int idx = text.indexOf("\"" + key + "\"");
        if (idx < 0) return "";
        int colon = text.indexOf(':', idx);
        int quote1 = text.indexOf('"', colon + 1);
        if (quote1 < 0) return "";
        int quote2 = text.indexOf('"', quote1 + 1);
        if (quote2 < 0) return "";
        String val = text.substring(quote1 + 1, quote2);
        // Remove trailing comma if present
        return val.replace(",", "");
    }

    private static float getFloat(String text, String key) {
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

    private static int getHexInt(String text, String key) {
        int idx = text.indexOf("\"" + key + "\"");
        if (idx < 0) return 0;
        int colon = text.indexOf(':', idx);
        int quote1 = text.indexOf('"', colon + 1);
        if (quote1 < 0) return 0;
        int quote2 = text.indexOf('"', quote1 + 1);
        if (quote2 < 0) return 0;
        String val = text.substring(quote1 + 1, quote2);
        if (val.startsWith("0x") || val.startsWith("0X")) {
            return Integer.parseInt(val.substring(2), 16);
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

    private static String getNestedString(String text, String parent, String child) {
        int idx = text.indexOf("\"" + parent + "\"");
        if (idx < 0) return "";
        int brace1 = text.indexOf('{', idx);
        if (brace1 < 0) return "";
        // Find matching closing brace
        int depth = 1;
        int brace2 = brace1 + 1;
        for (; brace2 < text.length() && depth > 0; brace2++) {
            char c = text.charAt(brace2);
            if (c == '{') depth++;
            else if (c == '}') depth--;
        }
        String nested = text.substring(brace1 + 1, brace2 - 1);
        return getString(nested, child);
    }

    private static float getNestedFloat(String text, String parent, String child) {
        // Find the parent key
        int idx = text.indexOf("\"" + parent + "\"");
        if (idx < 0) return 0;
        // Find the opening brace of the parent object
        int brace1 = text.indexOf('{', idx);
        if (brace1 < 0) return 0;
        // Find the matching closing brace
        int depth = 1;
        int brace2 = brace1 + 1;
        for (; brace2 < text.length() && depth > 0; brace2++) {
            char c = text.charAt(brace2);
            if (c == '{') depth++;
            else if (c == '}') depth--;
        }
            // Extract content between braces (exclusive)
        String nested = text.substring(brace1 + 1, brace2 - 1);
        return getFloat(nested, child);
    }

    private static BiomeData parseBiome(String obj) {
        try {
            String id = getString(obj, "id");
            String nameKey = getString(obj, "name");
            float tempMin = getNestedFloat(obj, "climate", "temp_min");
            float tempMax = getNestedFloat(obj, "climate", "temp_max");
            float humidMin = getNestedFloat(obj, "climate", "humid_min");
            float humidMax = getNestedFloat(obj, "climate", "humid_max");
            float contMin = getNestedFloat(obj, "climate", "cont_min");
            float contMax = getNestedFloat(obj, "climate", "cont_max");
            int grassTint = getHexInt(obj, "grass");
            int foliageTint = getHexInt(obj, "foliage");
            int waterTint = getHexInt(obj, "water");
            String surfaceBlock = getString(obj, "surface");
            String fillerBlock = getString(obj, "filler");
            String treeType = getNestedString(obj, "features", "type");
            float treeDensity = getNestedFloat(obj, "features", "density");
            String flowerType = getNestedString(obj, "flowers", "type");
            float flowerDensity = getNestedFloat(obj, "flowers", "density");
            String grassType = getNestedString(obj, "grass", "type");
            float grassDensity = getNestedFloat(obj, "grass", "density");
            String particleType = getNestedString(obj, "particles", "type");
            float particleDensity = getNestedFloat(obj, "particles", "density");
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
