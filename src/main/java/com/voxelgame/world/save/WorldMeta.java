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

    public long seed;
    public GameMode gameMode = GameMode.SURVIVAL;
    public boolean generateStructures = true;
    public boolean dead = false;
    public int difficulty = 2;

    public long created = System.currentTimeMillis();
    public long lastPlayed = System.currentTimeMillis();

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

    /** Time of day, so a world resumes at the hour it was left. */
    public double dayTime = 0.25;

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

    // ------------------------------------------------------------------

    public void write(Path file) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("displayName", displayName);
        m.put("seed", Long.toString(seed));
        m.put("gameMode", gameMode.name());
        m.put("generateStructures", Boolean.toString(generateStructures));
        m.put("dead", Boolean.toString(dead));
        m.put("difficulty", Integer.toString(difficulty));
        m.put("created", Long.toString(created));
        m.put("lastPlayed", Long.toString(lastPlayed));
        m.put("playerX", Float.toString(playerX));
        m.put("playerY", Float.toString(playerY));
        m.put("playerZ", Float.toString(playerZ));
        m.put("playerYaw", Float.toString(playerYaw));
        m.put("playerPitch", Float.toString(playerPitch));
        m.put("playerHealth", Integer.toString(playerHealth));
        m.put("playerHunger", Integer.toString(playerHunger));
        m.put("selectedSlot", Integer.toString(selectedSlot));
        m.put("dayTime", Double.toString(dayTime));
        m.put("hasSpawn", Boolean.toString(hasSpawn));
        m.put("spawnX", Float.toString(spawnX));
        m.put("spawnY", Float.toString(spawnY));
        m.put("spawnZ", Float.toString(spawnZ));
        m.put("inventoryData", inventoryData);

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
            // Pre-mode saves default to creative rather than losing progress
            w.gameMode = GameMode.parse(m.getOrDefault("gameMode", "CREATIVE"));
            w.generateStructures = !"false".equals(m.get("generateStructures"));
            w.dead = "true".equals(m.get("dead"));
            w.difficulty = (int) parseFloat(m.get("difficulty"), 2);
            w.created = parseLong(m.get("created"), System.currentTimeMillis());
            w.lastPlayed = parseLong(m.get("lastPlayed"), w.created);
            w.playerX = parseFloat(m.get("playerX"), 0);
            w.playerY = parseFloat(m.get("playerY"), 70);
            w.playerZ = parseFloat(m.get("playerZ"), 0);
            w.playerYaw = parseFloat(m.get("playerYaw"), -90);
            w.playerPitch = parseFloat(m.get("playerPitch"), 0);
            w.playerHealth = (int) parseFloat(m.get("playerHealth"), 20);
            w.playerHunger = (int) parseFloat(m.get("playerHunger"), 20);
            w.selectedSlot = (int) parseFloat(m.get("selectedSlot"), 0);
            w.dayTime = parseFloat(m.get("dayTime"), 0.25f);
            w.hasSpawn = "true".equals(m.get("hasSpawn"));
            w.spawnX = parseFloat(m.get("spawnX"), 0);
            w.spawnY = parseFloat(m.get("spawnY"), 70);
            w.spawnZ = parseFloat(m.get("spawnZ"), 0);
            w.inventoryData = m.getOrDefault("inventoryData", "");
            return w;

        } catch (Exception e) {
            System.err.println("Could not read " + file + ": " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------

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
