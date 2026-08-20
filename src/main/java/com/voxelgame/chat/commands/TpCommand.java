package com.voxelgame.chat.commands;

import com.voxelgame.chat.commands.Command;
import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * /tp <x> <y> <z> — телепортация игрока.
 * Supports ~ relative coordinates (e.g. ~ ~10 ~).
 */
public class TpCommand implements Command {

    /** Access to the current player position — set by Game. */
    public static Vector3f currentPlayerPos;
    public static PlayerTeleporter teleporter;

    /** Interface to actually move the player. */
    public interface PlayerTeleporter {
        void teleport(float x, float y, float z);
    }

    @Override
    public String getName() { return "tp"; }

    @Override
    public String getUsage() { return "/tp <x> <y> <z>"; }

    @Override
    public String getDescription() { return "телепортация к координатам"; }

    @Override
    public String execute(String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException("нужно 3 координаты (x y z)");
        }

        if (currentPlayerPos == null) {
            return "Нет активного игрока.";
        }

        float x = parseCoord(args[0], currentPlayerPos.x);
        float y = parseCoord(args[1], currentPlayerPos.y);
        float z = parseCoord(args[2], currentPlayerPos.z);

        if (teleporter != null) {
            teleporter.teleport(x, y, z);
        }

        return String.format("Телепортация на %.1f, %.1f, %.1f", x, y, z);
    }

    private static float parseCoord(String s, float playerCoord) {
        if (s.startsWith("~")) {
            if (s.length() == 1) return playerCoord;
            return playerCoord + Float.parseFloat(s.substring(1));
        }
        return Float.parseFloat(s);
    }
}
