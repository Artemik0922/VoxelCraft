package com.voxelgame.world.entity;

import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * A fishing bobber: a projectile that flies with gravity, lands in water,
 * waits for a bite, and gives the player a short window to reel in.
 *
 * Not saved to disk — it disappears when the world unloads.
 */
public class FishingBobberEntity {

    public enum State {
        FLYING,     // travelling through the air
        WAITING,    // in water, waiting for a bite
        BITE,       // fish is biting — short window to reel in
        REELING     // reeled in, about to give loot
    }

    private final World world;
    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();

    private State state = State.FLYING;
    private int biteTimer = 0;       // ticks until next bite attempt
    private int biteWindow = 0;      // ticks left to reel in after a bite
    private boolean dead = false;

    /** Fired once when the bobber enters the bite window (splash FX hook). */
    public Runnable onBite;

    private static final float GRAVITY = 12.0f;
    private static final float WATER_CHECK_RADIUS = 0.3f;
    private static final int MIN_WAIT_TICKS = 300;   // 5 seconds
    private static final int MAX_WAIT_TICKS = 900;   // 15 seconds
    private static final int BITE_WINDOW_TICKS = 30; // 1.5 seconds at 20 tps

    public FishingBobberEntity(World world, Vector3f pos, Vector3f dir) {
        this.world = world;
        this.position.set(pos);
        this.velocity.set(dir.x * 12.0f, dir.y * 12.0f + 2.0f, dir.z * 12.0f);
        scheduleNextBite();
    }

    public void update(float dt) {
        if (dead) return;

        switch (state) {
            case FLYING -> updateFlying(dt);
            case WAITING -> updateWaiting(dt);
            case BITE -> updateBite(dt);
            case REELING -> { /* handled by controller */ }
        }
    }

    private void updateFlying(float dt) {
        velocity.y -= GRAVITY * dt;
        position.x += velocity.x * dt;
        position.y += velocity.y * dt;
        position.z += velocity.z * dt;

        if (isInWater()) {
            state = State.WAITING;
            velocity.set(0, 0, 0);
            scheduleNextBite();
        } else if (velocity.y < 0 && isBelowGround()) {
            dead = true;
        }
    }

    private void updateWaiting(float dt) {
        biteTimer--;
        if (biteTimer <= 0) {
            state = State.BITE;
            biteWindow = BITE_WINDOW_TICKS;
            if (onBite != null) onBite.run();
        }
    }

    private void updateBite(float dt) {
        biteWindow--;
        if (biteWindow <= 0) {
            state = State.WAITING;
            scheduleNextBite();
        }
    }

    private void scheduleNextBite() {
        int range = MAX_WAIT_TICKS - MIN_WAIT_TICKS;
        biteTimer = MIN_WAIT_TICKS + (int) (Math.random() * range);
    }

    private boolean isInWater() {
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y);
        int bz = (int) Math.floor(position.z);
        return world.getBlock(bx, by, bz) == com.voxelgame.world.BlockType.WATER.id;
    }

    private boolean isBelowGround() {
        int gy = world.getGroundHeight((int) position.x, (int) position.z);
        return position.y < gy;
    }

    public void reelIn() {
        state = State.REELING;
    }

    public Vector3f getPosition() { return position; }
    public State getState() { return state; }
    public boolean isDead() { return dead; }
    public void markDead() { dead = true; }
}
