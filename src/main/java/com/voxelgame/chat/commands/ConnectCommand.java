package com.voxelgame.chat.commands;

import com.voxelgame.core.Game;
import com.voxelgame.net.Net;

/**
 * /connect <host> [port] — connect to a local co-op server.
 * /connect off — disconnect.
 */
public class ConnectCommand implements Command {

    /** Access to the game instance — set by Game initialization. */
    public static Game game;

    @Override
    public String getName() { return "connect"; }

    @Override
    public String getUsage() { return "/connect <host> [port]  |  /connect off"; }

    @Override
    public String getDescription() { return "подключиться к кооп-серверу (off — отключиться)"; }

    @Override
    public String execute(String[] args) {
        if (game == null) {
            return "ошибка: игра не инициализирована";
        }
        if (args.length < 1) {
            throw new IllegalArgumentException("укажи адрес хоста (или off)");
        }
        String a = args[0];
        if (a.equalsIgnoreCase("off")) {
            game.disconnectFromServer();
            return "Отключено от сервера";
        }
        int port = Net.DEFAULT_PORT;
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("неверный порт: " + args[1]);
            }
        }
        game.connectToServer(a, port);
        return null;
    }
}
