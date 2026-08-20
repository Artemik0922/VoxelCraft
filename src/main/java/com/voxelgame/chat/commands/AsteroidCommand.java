package com.voxelgame.chat.commands;

import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * /asteroid [x z] [size] — drop a burning asteroid on (x, z).
 *
 * Size 1-4 (default 2). Without coordinates the rock falls on the player.
 */
public class AsteroidCommand implements Command {

    /** Access to the current world — set by game initialization. */
    public static World currentWorld;
    public static Vector3f currentPlayerPos;

    @Override
    public String getName() {
        return "asteroid";
    }

    @Override
    public String getUsage() {
        return "/asteroid [x z] [size]";
    }

    @Override
    public String getDescription() {
        return "уронить астероид (size 1-4)";
    }

    @Override
    public String execute(String[] args) {
        if (currentWorld == null) {
            throw new IllegalArgumentException("мир ещё не загружен");
        }

        float x = currentPlayerPos.x;
        float z = currentPlayerPos.z;
        int size = 2;

        if (args.length >= 2) {
            try {
                x = Float.parseFloat(args[0]);
                z = Float.parseFloat(args[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "координаты должны быть числами: /asteroid [x z] [size]");
            }
        } else {
            // Drop the rock 40-80 blocks away in a random direction, so the
            // fall is visible and the impact stays near the player
            float angle = (float) (Math.random() * Math.PI * 2.0);
            float dist = 40.0f + (float) (Math.random() * 40.0f);
            x += (float) Math.cos(angle) * dist;
            z += (float) Math.sin(angle) * dist;
        }
        if (args.length >= 3) {
            try {
                size = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("размер должен быть числом 1-4");
            }
        }
        size = Math.max(1, Math.min(4, size));

        currentWorld.spawnAsteroidAt(x, z, size);
        return "Астероид (размер " + size + ") падает на ("
            + (int) x + ", " + (int) z + ")";
    }
}