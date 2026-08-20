package com.voxelgame.chat.commands;

import com.voxelgame.world.save.WorldMeta;

/**
 * /gamemode <survival|creative|hardcore> — смена режима игры.
 */
public class GamemodeCommand implements Command {

    /** Interface to change the game mode. */
    public interface GameModeChanger {
        WorldMeta.GameMode currentMode();
        void setMode(WorldMeta.GameMode mode);
    }

    public static GameModeChanger changer;

    @Override
    public String getName() { return "gamemode"; }

    @Override
    public String getUsage() { return "/gamemode <survival|creative|hardcore>"; }

    @Override
    public String getDescription() { return "сменить режим игры"; }

    @Override
    public String execute(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("укажи режим: survival, creative или hardcore");
        }

        if (changer == null) {
            return "Нет активного мира.";
        }

        String modeName = args[0].toLowerCase();
        WorldMeta.GameMode mode;
        switch (modeName) {
            case "survival":
            case "s":
            case "0":
                mode = WorldMeta.GameMode.SURVIVAL;
                break;
            case "creative":
            case "c":
            case "1":
                mode = WorldMeta.GameMode.CREATIVE;
                break;
            case "hardcore":
            case "h":
            case "2":
                mode = WorldMeta.GameMode.HARDCORE;
                break;
            default:
                throw new IllegalArgumentException("неизвестный режим '" + modeName + "'");
        }

        changer.setMode(mode);
        return "Режим игры: " + mode.name().toLowerCase();
    }
}
