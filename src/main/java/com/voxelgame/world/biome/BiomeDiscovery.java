package com.voxelgame.world.biome;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which biomes a player has discovered in a world.
 * Persists to saves/<world>/discovered.json.
 */
public final class BiomeDiscovery {

    private final Path file;
    private final Set<String> discovered = new HashSet<>();

    public BiomeDiscovery(Path worldDir) {
        this.file = worldDir.resolve("discovered.json");
    }

    /** Load discovered biomes from disk. */
    public void load() {
        discovered.clear();
        if (!Files.isRegularFile(file)) return;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            // Minimal JSON array parser
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start < 0 || end < 0 || end <= start) return;
            String content = text.substring(start + 1, end);
            for (String item : content.split(",")) {
                String id = item.trim().replace("\"", "");
                if (!id.isEmpty()) discovered.add(id);
            }
        } catch (IOException e) {
            System.err.println("Could not load discovered biomes: " + e.getMessage());
        }
    }

    /** Save discovered biomes to disk. */
    public void save() {
        try {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder("[\n");
            int i = 0;
            for (String id : discovered) {
                sb.append("  \"").append(id).append("\"");
                if (++i < discovered.size()) sb.append(",");
                sb.append("\n");
            }
            sb.append("]\n");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save discovered biomes: " + e.getMessage());
        }
    }

    /** Check if a biome was already discovered. */
    public boolean has(String biomeId) {
        return discovered.contains(biomeId);
    }

    /** Mark a biome as discovered. Returns true if it was new. */
    public boolean discover(String biomeId) {
        return discovered.add(biomeId);
    }

    /** Number of discovered biomes. */
    public int count() {
        return discovered.size();
    }
}
