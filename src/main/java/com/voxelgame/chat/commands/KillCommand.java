package com.voxelgame.chat.commands;

/**
 * /kill — убить игрока (для теста респавна).
 */
public class KillCommand implements Command {

    /** Interface to kill the player. */
    public interface PlayerKiller {
        void kill();
    }

    public static PlayerKiller killer;

    @Override
    public String getName() { return "kill"; }

    @Override
    public String getUsage() { return "/kill"; }

    @Override
    public String getDescription() { return "убить игрока"; }

    @Override
    public String execute(String[] args) {
        if (killer == null) {
            return "Нет активного игрока.";
        }

        killer.kill();
        return "Игрок убит.";
    }
}
