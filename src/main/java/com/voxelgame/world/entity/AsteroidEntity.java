package com.voxelgame.world.entity;

import com.voxelgame.audio.AudioManager;
import com.voxelgame.player.Player;
import com.voxelgame.rendering.ParticleSystem;
import com.voxelgame.world.BlockType;
import com.voxelgame.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * [AST] A burning asteroid falling from the sky.
 *
 * A jumbled boulder built from cube "parts" accelerates under gravity while
 * tumbling, shedding a trail of smoke and embers. On impact it carves a
 * crater, throws up a rim, sets the centre on fire, queues a camera shake
 * ("earthquake") and deals blast damage to everyone nearby. Villagers that
 * see it fall panic and scatter.
 */
public class AsteroidEntity {

    /** One cube of the boulder, placed in the rock's local space. */
    public static class Part {
        public final float ox, oy, oz;
        public final BlockType block;
        public final float scale;

        Part(float ox, float oy, float oz, BlockType block, float scale) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.block = block;
            this.scale = scale;
        }
    }

    private final World world;
    private final Vector3f position;
    private final Vector3f velocity;
    private final int size;
    private float yaw, pitch, roll;
    private final float spinYaw, spinPitch, spinRoll;
    private boolean dead = false;
    private float trailTimer = 0;
    private final List<Part> parts = new ArrayList<>();

    /** Set by Game so the falling rock can shed sparks. */
    public static ParticleSystem particles;

    /** Camera shake strength queued by the most recent impact, consumed by Game. */
    private static float shakeImpulse = 0f;

    private static final float GRAVITY = 40.0f;          // blocks/s^2
    private static final float MAX_FALL_SPEED = 60.0f;   // blocks/s
    private static final float TRAIL_INTERVAL = 0.05f;   // s between puffs

    /** Half the boulder's span, used for the ground-impact test. */
    private final float halfExtent;

    public AsteroidEntity(World world, float x, float y, float z, int size) {
        this.world = world;
        this.position = new Vector3f(x, y, z);
        this.size = Math.max(1, Math.min(4, size));
        // Nearly straight down, with just a touch of drift so it still
        // lands on the spot it was aimed at
        this.velocity = new Vector3f(
            (float) (Math.random() - 0.5f) * 0.7f,
            0,
            (float) (Math.random() - 0.5f) * 0.7f);
        this.yaw = (float) (Math.random() * 6.28318f);
        this.pitch = (float) (Math.random() * 6.28318f);
        this.roll = (float) (Math.random() * 6.28318f);
        this.spinYaw = (float) (Math.random() - 0.5f) * 1.4f;
        this.spinPitch = (float) (Math.random() - 0.5f) * 1.4f;
        this.spinRoll = (float) (Math.random() - 0.5f) * 1.4f;
        this.halfExtent = this.size * 1.15f;
        buildBoulder();
        // Distant rumble announcing the fall
        AudioManager.play("sounds/explosion", 0.5f, 0.35f);
        // Villagers in the landing zone hear the rumble and panic
        world.panicVillagersNear(position.x, position.z, 34.0f + this.size * 8.0f);
    }

    /** Carve a rough boulder out of cube parts on a block-sized grid. */
    private void buildBoulder() {
        float radius = size * 0.95f;
        int ir = (int) Math.ceil(radius);
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dy = -ir; dy <= ir; dy++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    float d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > radius * radius) continue;
                    // Cull a few cells so the boulder looks rough, not smooth
                    if (Math.random() < 0.16f) continue;
                    BlockType block;
                    if (size >= 3 && d2 < (radius * 0.5f) * (radius * 0.5f)) {
                        block = BlockType.OBSIDIAN;
                    } else if (size >= 2) {
                        block = Math.random() < 0.5f
                            ? BlockType.COBBLESTONE : BlockType.STONE;
                    } else {
                        block = Math.random() < 0.5f
                            ? BlockType.STONE : BlockType.COBBLESTONE;
                    }
                    float jx = (float) (Math.random() - 0.5f) * 0.15f;
                    float jy = (float) (Math.random() - 0.5f) * 0.15f;
                    float jz = (float) (Math.random() - 0.5f) * 0.15f;
                    float scale = 0.85f + (float) Math.random() * 0.3f;
                    parts.add(new Part(dx + jx, dy + jy, dz + jz, block, scale));
                }
            }
        }
    }

    /** Called once per game frame. */
    public void update(float dt, Player player) {
        if (dead) return;

        velocity.y = Math.min(velocity.y - GRAVITY * dt, MAX_FALL_SPEED);
        position.add(velocity.x * dt, velocity.y * dt, velocity.z * dt);
        yaw += spinYaw * dt;
        pitch += spinPitch * dt;
        roll += spinRoll * dt;

        // Fiery trail behind the rock
        trailTimer -= dt;
        if (trailTimer <= 0 && particles != null) {
            trailTimer = TRAIL_INTERVAL;
            particles.emitMeteorTrail(position.x, position.y, position.z);
        }

        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y);
        int bz = (int) Math.floor(position.z);
        if (by < -16) {
            dead = true; // lost in the void
            return;
        }

        // Impact when the boulder's underside reaches the terrain or it
        // clips a solid block
        float groundY = world.getGroundHeight(bx, bz);
        if (position.y - halfExtent <= groundY || isSolid(bx, by, bz)) {
            impact(player);
            dead = true;
        }
    }

    private boolean isSolid(int bx, int by, int bz) {
        int block = world.getBlock(bx, by, bz);
        return block != BlockType.AIR.id
            && block != BlockType.WATER.id
            && block != BlockType.LAVA.id;
    }

    private void impact(Player player) {
        int cx = (int) Math.floor(position.x);
        int cy = (int) Math.floor(position.y);
        int cz = (int) Math.floor(position.z);
        world.makeCrater(cx, cy, cz, size * 2 + 2);
        if (particles != null) {
            particles.emitImpact(cx, cy, cz, size);
        }
        // Powerful double-layer boom: a deep thud plus the crack
        AudioManager.play("sounds/explosion", 0.45f, 1.0f);
        AudioManager.play("sounds/explosion", 0.95f, 0.6f);
        // Earthquake: the impact shakes the camera
        shakeImpulse = Math.max(shakeImpulse, 0.5f + size * 0.12f);
    }

    /** Queue a camera shake for Game to consume this frame. */
    public static float consumeShakeImpulse() {
        float s = shakeImpulse;
        shakeImpulse = 0f;
        return s;
    }

    public Vector3f getPosition() { return position; }
    public int getSize() { return size; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public float getRoll() { return roll; }
    public boolean isDead() { return dead; }
    public List<Part> getParts() { return parts; }
}
