package com.voxelgame.stats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-world statistics and persistent achievements.
 * Persists to saves/<world>/stats.json next to meta.json.
 *
 * Counters are simple string->long pairs; the achievement unlock set is
 * stored alongside so unlocks survive restarts (previously they were
 * memory-only and reset on every launch).
 *
 * File layout — flat "key": "value" lines, same minimal style as meta.json:
 *   "stat:blocksMined": "42"
 *   "achievement:first_biome": "1"
 */
public final class WorldStats {

    // --- Counter keys (see HeadlessTestRunner for roundtrip coverage) ---
    public static final String PLAY_TIME_MS = "playTimeMs";
    public static final String BLOCKS_MINED = "blocksMined";
    public static final String BLOCKS_PLACED = "blocksPlaced";
    public static final String ITEMS_CRAFTED = "itemsCrafted";
    public static final String ITEMS_SMELTED = "itemsSmelted";
    public static final String MOBS_KILLED = "mobsKilled";
    public static final String DEATHS = "deaths";
    /** Metres walked on the ground, rounded to long on write. */
    public static final String DISTANCE_WALKED = "distanceWalked";
    public static final String FISH_CAUGHT = "fishCaught";
    public static final String TRADES_MADE = "tradesMade";
    public static final String ITEMS_EATEN = "itemsEaten";

    private static final String STAT_PREFIX = "stat:";
    private static final String ACHIEVEMENT_PREFIX = "achievement:";
    private static final Pattern LINE = Pattern.compile("\"([^\"]+)\": \"([^\"]*)\"");

    private final Path file;
    private final Map<String, Long> counters = new LinkedHashMap<>();
    private final Set<String> achievements = new LinkedHashSet<>();

    public WorldStats(Path worldDir) {
        this.file = worldDir.resolve("stats.json");
    }

    /** Load from disk. Missing file = fresh counters (a new world). */
    public void load() {
        counters.clear();
        achievements.clear();
        if (!Files.isRegularFile(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Matcher m = LINE.matcher(line);
                if (!m.find()) continue;
                String key = m.group(1);
                String value = m.group(2);
                if (key.startsWith(STAT_PREFIX)) {
                    try {
                        counters.put(key.substring(STAT_PREFIX.length()), Long.parseLong(value));
                    } catch (NumberFormatException ignored) {
                    }
                } else if (key.startsWith(ACHIEVEMENT_PREFIX) && value.equals("1")) {
                    achievements.add(key.substring(ACHIEVEMENT_PREFIX.length()));
                }
            }
        } catch (IOException e) {
            System.err.println("Could not load world stats: " + e.getMessage());
        }
    }

    /** Write to disk atomically-ish (single small file). */
    public void save() {
        try {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder("{\n");
            java.util.List<String> lines = new java.util.ArrayList<>();
            for (Map.Entry<String, Long> e : counters.entrySet()) {
                lines.add("  \"" + STAT_PREFIX + e.getKey() + "\": \"" + e.getValue() + "\"");
            }
            for (String id : achievements) {
                lines.add("  \"" + ACHIEVEMENT_PREFIX + id + "\": \"1\"");
            }
            for (int i = 0; i < lines.size(); i++) {
                sb.append(lines.get(i));
                if (i < lines.size() - 1) sb.append(',');
                sb.append('\n');
            }
            sb.append("}\n");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save world stats: " + e.getMessage());
        }
    }

    /** Add an integral amount to a counter. */
    public void add(String key, long amount) {
        if (amount == 0) return;
        counters.merge(key, amount, Long::sum);
    }

    /** Add a fractional amount (walked distance) rounded on write. */
    public void add(String key, double amount) {
        add(key, Math.round(amount));
    }

    public long get(String key) {
        return counters.getOrDefault(key, 0L);
    }

    public long getPlayTimeMs() {
        return get(PLAY_TIME_MS);
    }

    /** Accumulate real played seconds (called at a fixed tick while playing). */
    public void addPlayTime(float dtSeconds) {
        add(PLAY_TIME_MS, (long) (dtSeconds * 1000f));
    }

    // --- Achievements persistence ---

    public Set<String> getAchievements() {
        return achievements;
    }

    public void setAchievements(Set<String> unlocked) {
        achievements.clear();
        achievements.addAll(unlocked);
    }

    public boolean isAchievementUnlocked(String id) {
        return achievements.contains(id);
    }
}
