package com.voxelgame;

import com.voxelgame.core.Game;
import com.voxelgame.debug.HeadlessTestRunner;

public class Main {
    public static void main(String[] args) {
        for (String arg : args) {
            if ("--headless".equals(arg)) {
                System.exit(HeadlessTestRunner.run(args));
                return;
            }
        }
        Game game = new Game();
        game.run();
    }
}