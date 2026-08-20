package com.voxelgame.chat.commands;

import com.voxelgame.chat.commands.Command;
import com.voxelgame.world.World;

/**
 * /village — телепортация к ближайшей деревне.
 */
public class VillageCommand implements Command {

    /** Активный мир — устанавливается Game каждый кадр. */
    public static World currentWorld;

    @Override
    public String getName() { return "village"; }

    @Override
    public String getUsage() { return "/village"; }

    @Override
    public String getDescription() { return "телепортация к ближайшей деревне"; }

    @Override
    public String execute(String[] args) {
        if (currentWorld == null || TpCommand.currentPlayerPos == null) {
            return "Нет активного мира или игрока.";
        }

        float[] v = currentWorld.findNearestVillage(
                TpCommand.currentPlayerPos.x, TpCommand.currentPlayerPos.z);
        if (v == null) {
            return "Поблизости нет деревень.";
        }

        if (TpCommand.teleporter != null) {
            TpCommand.teleporter.teleport(v[0], v[1], v[2]);
        }
        return String.format("Телепортация в деревню: %.1f, %.1f, %.1f", v[0], v[1], v[2]);
    }
}