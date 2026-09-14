package com.voxelgame.world.block;

import com.voxelgame.world.BlockType;
import com.voxelgame.world.World;

/**
 * [GP-020] Oak door, vanilla mechanics: the door occupies two cells (lower
 * and upper), right-click toggles the whole door between closed (solid) and
 * open (passable), and both states are distinct block IDs because the chunk
 * storage has no per-block metadata.
 */
public final class DoorBlock implements Interactable {

    public static final DoorBlock INSTANCE = new DoorBlock();

    private DoorBlock() {}

    @Override
    public boolean onInteract(World world, int x, int y, int z) {
        int id = world.getBlock(x, y, z);
        if (!isDoorId(id)) return false;

        // Clicking either half toggles the whole door: resolve to the lower
        // cell, flip the state, and write it to both halves. Ids stay int -
        // casting 177 to byte makes it negative and Chunk.setBlock silently
        // rejects it, which is why the door once refused to open.
        int lowerY = isDoorId(world.getBlock(x, y - 1, z)) ? y - 1 : y;
        int newState = world.getBlock(x, lowerY, z) == BlockType.OAK_DOOR.id
            ? BlockType.OAK_DOOR_OPEN.id
            : BlockType.OAK_DOOR.id;
        world.setBlock(x, lowerY, z, newState);
        world.setBlock(x, lowerY + 1, z, newState);
        return true;
    }

    /** True for either door state. */
    public static boolean isDoor(BlockType type) {
        return type == BlockType.OAK_DOOR || type == BlockType.OAK_DOOR_OPEN;
    }

    /** True when the raw block id is either door state. */
    public static boolean isDoorId(int id) {
        return id == BlockType.OAK_DOOR.id || id == BlockType.OAK_DOOR_OPEN.id;
    }
}
