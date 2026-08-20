package com.voxelgame.net;

import java.io.IOException;

/**
 * Headless co-op server entry point.
 *
 * Usage:
 *   ServerMain [port] [seed] [maxPlayers]
 *
 * Runs without any windowing/OpenGL so it can be launched on a separate
 * machine (or the same one) and shared by up to {@code maxPlayers} clients.
 */
public class ServerMain {

    public static void main(String[] args) {
        int port = Net.DEFAULT_PORT;
        long seed = System.currentTimeMillis();
        int maxPlayers = 4;

        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException e) {}
        }
        if (args.length > 1) {
            try { seed = Long.parseLong(args[1]); } catch (NumberFormatException e) {}
        }
        if (args.length > 2) {
            try { maxPlayers = Integer.parseInt(args[2]); } catch (NumberFormatException e) {}
        }

        GameServer server = new GameServer(port, seed, maxPlayers, System.out::println);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "coop-server-shutdown"));

        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Не удалось запустить сервер: " + e.getMessage());
        }
    }
}
