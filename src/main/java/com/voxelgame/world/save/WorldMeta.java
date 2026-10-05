package com.voxelgame.world.save;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything about a world except its blocks.
 *
 * Stored as flat JSON, read with the same minimal parser used for language
 * files rather than pulling in a JSON library for a dozen fields.
 */
public class WorldMeta {

    public enum GameMode {
        SURVIVAL("gameMode.survival"),
        CREATIVE("gameMode.creative"),
        HARDCORE("gameMode.hardcore");

        public final String key;
        GameMode(String key) { this.key = key; }

        public static GameMode parse(String s) {
            try {
                return valueOf(s.toUpperCase());
            } catch (Exception e) {
                return SURVIVAL;
            }
        }
    }

    /** Directory name; may differ from the display name if it collided. */
    public String folderName = "world";
    public String displayName = "New World";

    /** Dimension key: "overworld" by default, "space" for rocket planets. */
    public String dimension = "overworld";
    /** For space worlds: folderName of the overworld this planet orbits. */
    public String homeWorld = null;

    public long seed;
    public GameMode gameMode = GameMode.SURVIVAL;
    public boolean generateStructures = true;
    public boolean dead = false;
    public int difficulty = 2;

    public long created = System.currentTimeMillis();
    public long lastPlayed = System.currentTimeMillis();

    /** Total time played in this world, mirrored from stats.json on save. */
    public long playTimeMs = 0;

    // Where the player was standing
    public float playerX, playerY = 70, playerZ;
    public float playerYaw = -90, playerPitch = 0;
    public int playerHealth = 20;
    public int playerHunger = 20;
    public int selectedSlot = 0;
    public boolean hasSpawn = false;
    public float spawnX, spawnY = 70, spawnZ;

    // Inventory data: "slot:id:count:durability;slot:id:count:durability;..."
    public String inventoryData = "";

    /** Player-placed minimap markers; persisted packed in waypointData. */
    public final java.util.List<Waypoint> waypoints = new java.util.ArrayList<>();

    /** Time of day, so a world resumes at the hour it was left. */
    public double dayTime = 0.25;

    /** [ECO] Reputation with the village; grows +1 per trade. */
    public int reputation = 0;

    public WorldMeta() {}

    public WorldMeta(String displayName, long seed, GameMode mode, boolean structures) {
        this.displayName = displayName;
        this.folderName = displayName;
        this.seed = seed;
        this.gameMode = mode;
        this.generateStructures = structures;
    }

    /** "12.03.2026 18:45", for the world list. */
    public String formattedLastPlayed() {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(lastPlayed));
    }

    /** "3 ч 05 мин" / "45 мин" / "0 мин", for the world list and pause menu. */
    public String formattedPlayTime() {
        return formatPlayTime(playTimeMs);
    }

    /** Same formatting for a raw millisecond value (live F3 read-out). */
    public static String formatPlayTime(long ms) {
        long totalMin = ms / 60000L;
        long h = totalMin / 60L;
        long m = totalMin % 60L;
        if (h > 0) return h + " ч " + String.format("%02d", m) + " мин";
        return m + " мин";
    }

    // ------------------------------------------------------------------

    public void write(Path file) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("displayName", displayName);
        m.put("seed", Long.toString(seed));
        m.put("dimension", dimension);
        m.put("homeWorld", homeWorld == null ? "" : homeWorld);
        m.put("gameMode", gameMode.name());
        m.put("generateStructures", Boolean.toString(generateStructures));
        m.put("dead", Boolean.toString(dead));
        m.put("difficulty", Integer.toString(difficulty));
        m.put("created", Long.toString(created));
        m.put("lastPlayed", Long.toString(lastPlayed));
        m.put("playTimeMs", Long.toString(playTimeMs));
        m.put("playerX", Float.toString(playerX));
        m.put("playerY", Float.toString(playerY));
        m.put("playerZ", Float.toString(playerZ));
        m.put("playerYaw", Float.toString(playerYaw));
        m.put("playerPitch", Float.toString(playerPitch));
        m.put("playerHealth", Integer.toString(playerHealth));
        m.put("playerHunger", Integer.toString(playerHunger));
        m.put("selectedSlot", Integer.toString(selectedSlot));
        m.put("dayTime", Double.toString(dayTime));
        m.put("reputation", Integer.toString(reputation));
        m.put("hasSpawn", Boolean.toString(hasSpawn));
        m.put("spawnX", Float.toString(spawnX));
        m.put("spawnY", Float.toString(spawnY));
        m.put("spawnZ", Float.toString(spawnZ));
        m.put("inventoryData", inventoryData);
        m.put("waypointData", encodeWaypoints());

        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, String> e : m.entrySet()) {
            sb.append("  \"").append(e.getKey()).append("\": \"")
              .append(escape(e.getValue())).append('"');
            if (++i < m.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("}\n");

        Files.createDirectories(file.getParent());
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    public static WorldMeta read(Path file) {
        if (!Files.isRegularFile(file)) return null;

        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, String> m = parseFlatJson(text);

            WorldMeta w = new WorldMeta();
            w.displayName = m.getOrDefault("displayName", "World");
            w.seed = parseLong(m.get("seed"), 0);
            w.dimension = m.getOrDefault("dimension", "overworld");
            w.homeWorld = m.getOrDefault("homeWorld", "");
            if (w.homeWorld.isEmpty()) w.homeWorld = null;
            // Pre-mode saves default to creative rather than losing progress
            w.gameMode = GameMode.parse(m.getOrDefault("gameMode", "CREATIVE"));
            w.generateStructures = !"false".equals(m.get("generateStructures"));
            w.dead = "true".equals(m.get("dead"));
            w.difficulty = (int) parseFloat(m.get("difficulty"), 2);
            w.created = parseLong(m.get("created"), System.currentTimeMillis());
            w.lastPlayed = parseLong(m.get("lastPlayed"), w.created);
            w.playTimeMs = parseLong(m.get("playTimeMs"), 0);
            w.playerX = parseFloat(m.get("playerX"), 0);
            w.playerY = parseFloat(m.get("playerY"), 70);
            w.playerZ = parseFloat(m.get("playerZ"), 0);
            w.playerYaw = parseFloat(m.get("playerYaw"), -90);
            w.playerPitch = parseFloat(m.get("playerPitch"), 0);
            w.playerHealth = (int) parseFloat(m.get("playerHealth"), 20);
            w.playerHunger = (int) parseFloat(m.get("playerHunger"), 20);
            w.selectedSlot = (int) parseFloat(m.get("selectedSlot"), 0);
            w.dayTime = parseFloat(m.get("dayTime"), 0.25f);
            w.reputation = (int) parseFloat(m.get("reputation"), 0);
            w.hasSpawn = "true".equals(m.get("hasSpawn"));
            w.spawnX = parseFloat(m.get("spawnX"), 0);
            w.spawnY = parseFloat(m.get("spawnY"), 70);
            w.spawnZ = parseFloat(m.get("spawnZ"), 0);
            w.inventoryData = m.getOrDefault("inventoryData", "");
            w.decodeWaypoints(m.getOrDefault("waypointData", ""));
            return w;

        } catch (Exception e) {
            System.err.println("Could not read " + file + ": " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------

    /** Palette cycled through when the player drops a new waypoint. */
    public static final int[] WAYPOINT_COLORS = {
        0xFFE4593B, 0xFF35A7E0, 0xFF3DBC5A, 0xFFE0A72B,
        0xFFB05AE0, 0xFFE05A96, 0xFF2BB9A2, 0xFF8A6A3B,
    };

    /**
     * Waypoints ride in one packed string field, same idea as
     * inventoryData: "name|x|y|z|color;name|x|y|z|color;...". Delimiters
     * are stripped from names on the way in.
     */
    public void addWaypoint(String name, int x, int y, int z, int color) {
        String clean = name.replace(";", " ").replace("|", " ").replace(":", " ").trim();
        if (clean.isEmpty()) clean = "Waypoint";
        waypoints.add(new Waypoint(clean, x, y, z, color));
    }

    /** Removes by (case-insensitive) name; true when something was removed. */
    public boolean removeWaypoint(String name) {
        for (int i = 0; i < waypoints.size(); i++) {
            if (waypoints.get(i).name.equalsIgnoreCase(name)) {
                waypoints.remove(i);
                return true;
            }
        }
        return false;
    }

    public void clearWaypoints() {
        waypoints.clear();
    }

    private String encodeWaypoints() {
        StringBuilder sb = new StringBuilder();
        for (Waypoint w : waypoints) {
            if (sb.length() > 0) sb.append(';');
            sb.append(w.name.replace(";", " ").replace("|", " "))
              .append('|').append(w.x)
              .append('|').append(w.y)
              .append('|').append(w.z)
              .append('|').append(Integer.toHexString(w.color));
        }
        return sb.toString();
    }

    private void decodeWaypoints(String data) {
        waypoints.clear();
        if (data == null || data.isEmpty()) return;
        for (String part : data.split(";")) {
            String[] f = part.split("\\|");
            if (f.length < 5) continue;
            try {
                waypoints.add(new Waypoint(f[0],
                    Integer.parseInt(f[1].trim()), Integer.parseInt(f[2].trim()),
                    Integer.parseInt(f[3].trim()),
                    (int) Long.parseLong(f[4].trim(), 16)));
            } catch (NumberFormatException e) {
                // Skip a corrupt row rather than dropping the rest
            }
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static long parseLong(String s, long fallback) {
        try {
            return s == null ? fallback : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float parseFloat(String s, float fallback) {
        try {
            return s == null ? fallback : Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Same flat reader as the language files. */
    private static Map<String, String> parseFlatJson(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        int i = 0, n = text.length();

        while (i < n) {
            while (i < n && text.charAt(i) != '"') i++;
            if (i >= n) break;

            StringBuilder key = new StringBuilder();
            i = readString(text, i + 1, key);
            if (i < 0) break;

            while (i < n && text.charAt(i) != ':') i++;
            i++;
            while (i < n && text.charAt(i) != '"') {
                if (text.charAt(i) == ',' || text.charAt(i) == '}') break;
                i++;
            }
            if (i >= n || text.charAt(i) != '"') continue;

            StringBuilder value = new StringBuilder();
            i = readString(text, i + 1, value);
            if (i < 0) break;

            out.put(key.toString(), value.toString());
        }
        return out;
    }

    private static int readString(String text, int i, StringBuilder out) {
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char esc = text.charAt(i + 1);
                switch (esc) {
                    case 'n' -> out.append('\n');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    default -> out.append(esc);
                }
                i += 2;
                continue;
            }
            if (c == '"') return i + 1;
            out.append(c);
            i++;
        }
        return -1;
    }
}
