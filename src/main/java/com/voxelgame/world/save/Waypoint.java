package com.voxelgame.world.save;

/**
 * A player-placed map marker, persisted with the world metadata and drawn
 * on the minimap. Waypoints are 2D (x/z) — the y is kept for display only.
 */
public class Waypoint {

    public final String name;
    public final int x, y, z;
    public final int color;

    public Waypoint(String name, int x, int y, int z, int color) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
    }
}
