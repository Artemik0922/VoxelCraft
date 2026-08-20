package com.voxelgame.world.entity;

import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * [ENCH] A glowing experience orb dropped by ores and slain mobs.
 *
 * Bobs in place, is pulled toward the player once they come close, and
 * grants experience on pickup. Small orbs carry 1-2 XP, big ones up to 6.
 */
public class XpOrb {

    private final World world;
    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private final int value;
    private float bobPhase;
    private int age = 0;
    private boolean dead = false;

    /** Pickup delay so the orb doesn't instantly return to the killer. */
    private int pickupDelay = 15;

    private static final int LIFETIME = 7200; // 2 minutes at 60fps
    private static final float GRAVITY = 12.0f;

    public XpOrb(World world, float x, float y, float z, int value) {
        this.world = world;
        this.position.set(x, y, z);
        this.value = Math.max(1, value);
        this.velocity.set(
            (float) (Math.random() - 0.5) * 2.5f,
            3.5f,
            (float) (Math.random() - 0.5) * 2.5f
        );
        this.bobPhase = (float) (Math.random() * Math.PI * 2);
    }

    public void update(float dt) {
        if (dead) return;
        age++;
        if (pickupDelay > 0) pickupDelay--;
        if (age > LIFETIME) {
            dead = true;
            return;
        }

        velocity.y -= GRAVITY * dt;
        if (velocity.y < -14) velocity.y = -14;

        position.x += velocity.x * dt;
        position.y += velocity.y * dt;
        position.z += velocity.z * dt;

        int groundY = world.getGroundHeight((int) position.x, (int) position.z);
        if (position.y < groundY + 0.15f) {
            position.y = groundY + 0.15f;
            velocity.y = 0;
            velocity.x *= 0.7f;
            velocity.z *= 0.7f;
        }

        bobPhase += dt * 3;
    }

    /** Pull the orb toward the player once it's close enough. */
    public void attractTo(Vector3f target, float dt) {
        float dx = target.x - position.x;
        float dy = target.y + 1.0f - position.y;
        float dz = target.z - position.z;
        float distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < 0.0001f) return;
        float dist = (float) Math.sqrt(distSq);
        float speed = 9.0f;
        position.x += dx / dist * speed * dt;
        position.y += dy / dist * speed * dt;
        position.z += dz / dist * speed * dt;
    }

    public boolean canPickup() {
        return !dead && pickupDelay <= 0;
    }

    public boolean isCloseTo(Vector3f playerPos, float radius) {
        float dx = position.x - playerPos.x;
        float dy = position.y - playerPos.y;
        float dz = position.z - playerPos.z;
        float distSq = dx * dx + dy * dy + dz * dz;
        return distSq < radius * radius;
    }

    public Vector3f getPosition() { return position; }
    public int getValue() { return value; }
    public float getBobOffset() { return (float) Math.sin(bobPhase) * 0.08f; }
    public boolean isDead() { return dead; }
    public void markDead() { dead = true; }
}