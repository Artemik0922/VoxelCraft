package com.voxelgame.world.block;

import com.voxelgame.world.World;

/**
 * [GP-020] A block the player can interact with by right-clicking.
 *
 * Implementations return true when the interaction was handled. The world
 * state is mutable through the {@link World} reference, so blocks that
 * toggle (doors) or use (chests, furnaces) can act on themselves.
 */
public interface Interactable {
    boolean onInteract(World world, int x, int y, int z);
}