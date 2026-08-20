package com.voxelgame.world.entity;

import com.voxelgame.audio.AudioManager;
import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * Primed TNT block with a 4-second fuse. When the fuse runs out, it
 * destroys blocks in a sphere and plays an explosion sound.
 *
 * The entity sits at the exact position where the TNT block stood, so the
 * explosion is centred on the original block.
 */
public class TntEntity {

    private final World world;
    private final Vector3f position;
    private int fuseTimer;
    private boolean dead = false;

    /** 4 seconds at ~60 fps. */
    private static final int FUSE_TICKS = 240;
    /** TNT flashes white every ~0.5s while primed. */
    private static final int FLASH_INTERVAL = 30;

    public TntEntity(World world, float x, float y, float z) {
        this.world = world;
        this.position = new Vector3f(x, y, z);
        this.fuseTimer = FUSE_TICKS;
    }

    /**
     * Tick the fuse. Called once per game frame.
     */
    public void update(float dt) {
        if (dead) return;
        fuseTimer--;

        // Hissing fuse sound at the halfway point
        if (fuseTimer == FUSE_TICKS / 2) {
            AudioManager.play("sounds/fuse", 1.0f, 0.5f);
        }

        if (fuseTimer <= 0) {
            explode();
            dead = true;
        }
    }

    private void explode() {
        world.explode(
            (int) position.x,
            (int) position.y,
            (int) position.z,
            4);
        // Random pitch so chained explosions don't sound mechanical
        float pitch = 0.85f + (float) Math.random() * 0.3f;
        AudioManager.play("sounds/explosion", pitch, 0.8f);
    }

    public Vector3f getPosition() { return position; }
    public boolean isDead() { return dead; }

    /** 0..1 fuse progress, used for the flashing visual. */
    public float getFuseProgress() {
        return 1.0f - (fuseTimer / (float) FUSE_TICKS);
    }

    /** True when the TNT should flash white this tick. */
    public boolean isFlashing() {
        return (fuseTimer % FLASH_INTERVAL) < FLASH_INTERVAL / 2;
    }
}
