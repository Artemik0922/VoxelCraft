package com.voxelgame.chat.commands;

import com.voxelgame.world.World;
import com.voxelgame.world.entity.Villager;
import com.voxelgame.world.entity.Zoloy;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * /spawn <mob> [count] [x y z] — spawn mobs.
 *
 * Without coordinates: 3 blocks in front of the player on the ground.
 * With coordinates: at the specified position.
 * Count: how many to spawn (1-10).
 *
 * Supported mobs: zoloy, villager
 *
 * Villager usage: /spawn villager [count] [profession]
 *   profession: farmer, librarian, blacksmith, butcher, priest,
 *               fisherman, fletcher, shepherd, mason, nitwit (default: random)
 */
public class SpawnCommand implements Command {

    /** Mob name → factory. */
    private interface MobFactory {
        Zoloy create(World world, float x, float y, float z);
    }

    private static final Map<String, MobFactory> MOBS = new HashMap<>();

    /** Access to the current world — set by game initialization. */
    public static World currentWorld;
    public static Vector3f currentPlayerPos;
    public static Vector3f currentPlayerFront;

    static {
        MOBS.put("zoloy", Zoloy::new);
    }

    @Override
    public String getName() {
        return "spawn";
    }

    @Override
    public String getUsage() {
        return "/spawn <mob|villager> [count] [профессия|x y z]";
    }

    @Override
    public String getDescription() {
        return "призвать моба (zoloy, villager)";
    }

    @Override
    public String execute(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("укажи тип моба");
        }

        String mobName = args[0].toLowerCase();

        // === VILLAGER ===
        if (mobName.equals("villager") || mobName.equals("villagers")) {
            return spawnVillagers(args);
        }

        // === ZOLOy ===
        MobFactory factory = MOBS.get(mobName);
        if (factory == null) {
            throw new IllegalArgumentException("неизвестный моб '" + mobName + "'. Доступны: zoloy, villager");
        }

        int count = 1;
        float x = 0, y = 0, z = 0;
        boolean hasX = false, hasY = false, hasZ = false;

        // Parse: [count] [x y z]
        if (args.length >= 2) {
            if (isInteger(args[1]) && args.length >= 5) {
                // count + coordinates
                count = parseInt(args[1]);
                x = parseCoord(args[2], currentPlayerPos.x);
                y = parseCoord(args[3], currentPlayerPos.y);
                z = parseCoord(args[4], currentPlayerPos.z);
                hasX = hasY = hasZ = true;
            } else if (isNumber(args[1]) && args.length >= 4) {
                // coordinates only
                x = parseCoord(args[1], currentPlayerPos.x);
                y = parseCoord(args[2], currentPlayerPos.y);
                z = parseCoord(args[3], currentPlayerPos.z);
                hasX = hasY = hasZ = true;
            } else if (isInteger(args[1])) {
                // count only
                count = parseInt(args[1]);
            } else {
                throw new IllegalArgumentException("неправильный аргумент: " + args[1]);
            }
        }

        // Clamp count
        if (count < 1) count = 1;
        if (count > 10) count = 10;

        // Determine spawn position
        if (!hasX) {
            x = currentPlayerPos.x;
            z = currentPlayerPos.z;
        }
        if (!hasY) {
            y = currentPlayerPos.y;
        }

        // Check for debug flag
        boolean debug = args.length > 0 && args[args.length - 1].equalsIgnoreCase("debug");

        // Spawn around player in a circle
        if (currentWorld != null) {
            for (int i = 0; i < count; i++) {
                float angle = (float) (Math.PI * 2 * i / count);
                float radius = 3.0f + (float) Math.random() * 2.0f;
                float ox = x + (float) Math.cos(angle) * radius;
                float oz = z + (float) Math.sin(angle) * radius;
                int groundY = currentWorld.getGroundHeight((int) ox, (int) oz);
                float oy = groundY > 0 ? groundY : y;
                Zoloy mob = factory.create(currentWorld, ox, oy + 0.01f, oz);
                if (debug) mob.setDebugTpose(true);
                currentWorld.getMobs().add(mob);
            }
            System.out.println("[Spawn] Spawned " + count + " " + mobName + " around player. Total mobs: " + currentWorld.getMobs().size());
        } else {
            System.out.println("[Spawn] ERROR: currentWorld is null!");
        }

        return mobName + " заспавнен вокруг игрока (" + count + " шт)" + (debug ? " [DEBUG T-pose]" : "");
    }

    // ==================== VILLAGER SPAWNING ====================

    private String spawnVillagers(String[] args) {
        if (currentWorld == null) {
            return "ошибка: мир не загружен";
        }

        int count = 1;
        String profName = null;
        float x = currentPlayerPos.x;
        float y = currentPlayerPos.y;
        float z = currentPlayerPos.z;

        // Parse: /spawn villager [count] [profession]
        if (args.length >= 2) {
            if (isInteger(args[1])) {
                count = parseInt(args[1]);
            } else {
                profName = args[1].toLowerCase();
            }
        }
        if (args.length >= 3 && profName == null) {
            profName = args[2].toLowerCase();
        }

        if (count < 1) count = 1;
        if (count > 20) count = 20;

        Villager.Profession profession = null;
        if (profName != null) {
            profession = parseProfession(profName);
            if (profession == null) {
                throw new IllegalArgumentException("неизвестная профессия: " + profName +
                    ". Доступны: farmer, librarian, blacksmith, butcher, priest, fisherman, " +
                    "fletcher, shepherd, mason, nitwit, warrior");
            }
        }

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            float angle = (float) (Math.PI * 2 * i / count);
            float radius = 3.0f + (float) Math.random() * 2.0f;
            float ox = x + (float) Math.cos(angle) * radius;
            float oz = z + (float) Math.sin(angle) * radius;
            int groundY = currentWorld.getGroundHeight((int) ox, (int) oz);
            float oy = groundY > 0 ? groundY : y;

            // Случайная профессия если не указана
            Villager.Profession prof = profession;
            if (prof == null) {
                Villager.Profession[] all = Villager.Profession.values();
                prof = all[(int) (Math.random() * all.length)];
            }

            Villager v = new Villager(currentWorld, ox, oy + 0.01f, oz, prof);
            v.setHome(ox, oy + 0.01f, oz);
            currentWorld.getVillagers().add(v);
            spawned++;
        }

        String profLabel = profession != null ? profession.name() : "random";
        return "жители заспавнены (" + spawned + " шт, профессия: " + profLabel + ")";
    }

    private Villager.Profession parseProfession(String name) {
        return switch (name) {
            case "farmer" -> Villager.Profession.FARMER;
            case "librarian" -> Villager.Profession.LIBRARIAN;
            case "blacksmith", "smith" -> Villager.Profession.BLACKSMITH;
            case "butcher" -> Villager.Profession.BUTCHER;
            case "priest" -> Villager.Profession.PRIEST;
            case "fisherman", "fisher" -> Villager.Profession.FISHERMAN;
            case "fletcher" -> Villager.Profession.FLETCHER;
            case "leatherworker", "leather" -> Villager.Profession.LEATHERWORKER;
            case "shepherd" -> Villager.Profession.SHEPHERD;
            case "toolsmith" -> Villager.Profession.TOOLSMITH;
            case "armorer" -> Villager.Profession.ARMORER;
            case "weaponsmith" -> Villager.Profession.WEAPONSMITH;
            case "cartographer" -> Villager.Profession.CARTOGRAPHER;
            case "cleric" -> Villager.Profession.CLERIC;
            case "mason" -> Villager.Profession.MASON;
            case "nitwit", "idle" -> Villager.Profession.NITWIT;
            case "warrior", "guard" -> Villager.Profession.WARRIOR;
            default -> null;
        };
    }

    private static String fmt(float v) {
        if (v == (int) v) return String.valueOf((int) v);
        return String.format("%.2f", v);
    }

    private static boolean isNumber(String s) {
        try {
            Float.parseFloat(s.replace('~', '0'));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int parseInt(String s) {
        return Integer.parseInt(s);
    }

    /**
     * Parse a coordinate. Supports '~' relative to player: ~ = player pos,
     * ~5 = player pos + 5.
     */
    private static float parseCoord(String s, float playerCoord) {
        if (s.startsWith("~")) {
            if (s.length() == 1) return playerCoord;
            return playerCoord + Float.parseFloat(s.substring(1));
        }
        return Float.parseFloat(s);
    }
}
