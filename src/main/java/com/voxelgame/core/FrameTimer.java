package com.voxelgame.core;

/**
 * Per-frame timing buckets surfaced in the F3 debug panel.
 *
 * All timers are recorded on the render thread, so plain longs are safe.
 * Buckets are reset once per frame and accumulate nanos for the whole frame
 * (a world tick may run several times, and the scene pass spans many draws).
 */
public final class FrameTimer {

    public static final int REBUILD = 0;   // chunk relight + remesh
    public static final int SHADOW = 1;    // shadow depth pass
    public static final int WORLD = 2;     // updateWorld ticks (incl. entity AI)
    public static final int SCENE = 3;     // skybox + world + entities + particles
    public static final int POST = 4;      // post-processing + blurred backdrop
    public static final int UI = 5;        // 2D interface pass
    public static final int COUNT = 6;

    private static final String[] NAMES = {
        "rebuild", "shadow", "world", "scene", "post", "ui"
    };

    private static final long[] us = new long[COUNT];

    private FrameTimer() {}

    /** Zero all buckets before a frame starts. */
    public static void reset() {
        java.util.Arrays.fill(us, 0);
    }

    /** Accumulate a measured slice into a bucket. Nanos, stored in us. */
    public static void add(int key, long nanos) {
        us[key] += nanos / 1000L;
    }

    public static long us(int key) { return us[key]; }

    public static String name(int key) { return NAMES[key]; }
}