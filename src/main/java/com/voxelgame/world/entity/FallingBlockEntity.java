package com.voxelgame.world.entity;

import com.voxelgame.audio.AudioManager;
import com.voxelgame.world.BlockType;
import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * [GP-022] A sand/gravel block that lost its support and is tumbling down.
 *
 * The entity behaves like a solid block moving under gravity: it falls
 * until the cell below it is solid, then it becomes a regular world block
 * again. It falls straight through water, replacing it on landing.
 */
public class FallingBlockEntity {

    private final World world;
    /** Block origin: the cell (x,y,z) the cube started from, y is the bottom. */
    private final Vector3f position;
    private final int blockId;
    private float vy = 0;
    private boolean dead = false;

    private static final float GRAVITY = 26.0f;      // blocks/s^2
    private static final float MAX_FALL_SPEED = 30.0f;

    public FallingBlockEntity(World world, float x, float y, float z, int blockId) {
        this.world = world;
        this.position = new Vector3f(x, y, z);
        this.blockId = blockId;
    }

    /**
     * Move the block down under gravity and check the landing spot.
     * Called once per game frame.
     */
    public void update(float dt) {
        if (dead) return;

        vy = Math.min(vy - GRAVITY * dt, MAX_FALL_SPEED);
        position.y += vy * dt;

        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y);
        int bz = (int) Math.floor(position.z);
        if (by < 0) {
            // Fell into the void: destroy it, do not respawn
            dead = true;
            return;
        }

        // The block rests when the cell below is not air/fluid
        int below = world.getBlock(bx, by - 1, bz);
        if (below == BlockType.AIR.id
            || below == BlockType.WATER.id
            || below == BlockType.LAVA.id) {
            return; // keep falling
        }

        // Land: occupy the cell we are in (water/air get replaced)
        int cell = world.getBlock(bx, by, bz);
        if (cell == BlockType.AIR.id || cell == BlockType.WATER.id || cell == BlockType.LAVA.id) {
            world.setBlock(bx, by, bz, blockId);
            String surface = (blockId == BlockType.GRAVEL.id) ? "dirt" : "sand";
            AudioManager.play("sounds/steps/" + surface, 1.0f, 0.4f);
        }
        dead = true;
    }

    public Vector3f getPosition() { return position; }
    public int getBlockId() { return blockId; }
    public boolean isDead() { return dead; }
}