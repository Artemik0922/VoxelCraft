package com.voxelgame.ui2;

/**
 * Interface sound effects: procedural wooden click / thump, generated into
 * sounds/ui/ at build time and played through the shared AudioManager.
 * Safe to call from anywhere - a missing or uninitialised audio system
 * simply stays silent.
 */
public final class UiSounds {

    private UiSounds() {
    }

    public static void click() {
        play("sounds/ui/click", 1.0f, 0.5f);
    }

    public static void open() {
        play("sounds/ui/open", 1.0f, 0.55f);
    }

    public static void close() {
        play("sounds/ui/close", 1.0f, 0.5f);
    }

    private static void play(String path, float pitch, float gain) {
        try {
            com.voxelgame.audio.AudioManager.play(path, pitch, gain);
        } catch (Throwable ignored) {
            // headless or audio-less environments stay silent
        }
    }
}
