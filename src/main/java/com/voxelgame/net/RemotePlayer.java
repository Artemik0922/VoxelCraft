package com.voxelgame.net;

import org.joml.Vector3f;

/**
 * A snapshot of another player on the same co-op server.
 *
 * It holds the authoritative target state received from the network plus a
 * smoothed render position so the avatar glides instead of stepping every
 * network tick.
 */
public class RemotePlayer {

    public final int id;
    public final String name;

    /** Target (network) position — the player's feet level. */
    public final Vector3f position = new Vector3f();
    /** Smoothed position used for rendering. */
    public final Vector3f renderPos = new Vector3f();

    public float yaw;
    public float pitch;
    public float health = 20f;
    public int selected = 0;
    public boolean walking;
    public boolean sneak;

    /** Colour tint index for the avatar, so players are distinguishable. */
    public int colorIndex = 0;

    public RemotePlayer(int id, String name) {
        this.id = id;
        this.name = name;
    }

    /** Record a fresh network state. */
    public void apply(float x, float y, float z, float yaw, float pitch,
                      float health, int selected, boolean walking, boolean sneak) {
        position.set(x, y, z);
        this.yaw = yaw;
        this.pitch = pitch;
        this.health = health;
        this.selected = selected;
        this.walking = walking;
        this.sneak = sneak;
    }
}
