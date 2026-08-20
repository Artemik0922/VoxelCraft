package com.voxelgame.world.entity;

import com.voxelgame.item.ItemStack;
import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * A loose item lying in the world: a bobbing, spinning mini-block that the
 * player can pick up. Lives for five minutes if ignored.
 */
public class ItemEntity {

    private final World world;
    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private ItemStack stack;
    private float rotation;
    private float bobPhase;
    private int age = 0;
    private boolean dead = false;

    /** Pickup delay so the item doesn't instantly return to the thrower. */
    private int pickupDelay = 20;

    private static final int LIFETIME = 6000; // 5 minutes at 60fps (~30s real)
    private static final float GRAVITY = 12.0f;
    private static final float BOB_AMOUNT = 0.1f;

    public ItemEntity(World world, Vector3f pos, ItemStack stack) {
        this.world = world;
        this.position.set(pos);
        this.stack = stack;
        this.velocity.set(
            (float) (Math.random() - 0.5) * 2,
            4.0f,
            (float) (Math.random() - 0.5) * 2
        );
        this.rotation = (float) (Math.random() * 360);
    }

    public void update(float dt) {
        if (dead) return;
        age++;
        if (pickupDelay > 0) pickupDelay--;
        if (age > LIFETIME) {
            dead = true;
            return;
        }

        // Gravity
        velocity.y -= GRAVITY * dt;
        if (velocity.y < -20) velocity.y = -20;

        // Move with simple ground collision
        position.x += velocity.x * dt;
        position.y += velocity.y * dt;
        position.z += velocity.z * dt;

        int groundY = world.getGroundHeight((int) position.x, (int) position.z);
        if (position.y < groundY + 0.25f) {
            position.y = groundY + 0.25f;
            velocity.y = 0;
            velocity.x *= 0.8f;
            velocity.z *= 0.8f;
        }

        rotation += dt * 90;
        bobPhase += dt * 3;
    }

    /** Pull the item toward the player once it's close enough. */
    public void attractTo(Vector3f target, float dt) {
        float dx = target.x - position.x;
        float dy = target.y - position.y;
        float dz = target.z - position.z;
        float distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < 0.0001f) return;
        float dist = (float) Math.sqrt(distSq);
        float speed = 8.0f;
        position.x += dx / dist * speed * dt;
        position.y += dy / dist * speed * dt;
        position.z += dz / dist * speed * dt;
    }

    /** True once the player is close enough and the delay has elapsed. */
    public boolean canPickup() {
        return !dead && pickupDelay <= 0;
    }

    /** True once the player's proximity qualifies for a pickup. */
    public boolean isCloseTo(Vector3f playerPos, float radius) {
        float dx = position.x - playerPos.x;
        float dy = position.y - playerPos.y;
        float dz = position.z - playerPos.z;
        float distSq = dx * dx + dy * dy + dz * dz;
        return distSq < radius * radius;
    }

    public Vector3f getPosition() { return position; }
    public ItemStack getStack() { return stack; }
    public float getRotation() { return rotation; }
    public float getBobOffset() { return (float) Math.sin(bobPhase) * BOB_AMOUNT; }
    public boolean isDead() { return dead; }
    public void markDead() { dead = true; }
}
