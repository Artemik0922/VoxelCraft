package com.voxelgame.world.block;

import com.voxelgame.world.BlockType;
import com.voxelgame.world.World;

/**
 * [GP-020] Oak door: right-click toggles between closed (solid cube) and
 * open (passable panel). The two states are distinct block IDs because the
 * chunk storage has no per-block metadata.
 */
public final class DoorBlock implements Interactable {

    public static final DoorBlock INSTANCE = new DoorBlock();

    private DoorBlock() {}

    @Override
    public boolean onInteract(World world, int x, int y, int z) {
        int id = world.getBlock(x, y, z);
        if (id == BlockType.OAK_DOOR.id) {
            world.setBlock(x, y, z, BlockType.OAK_DOOR_OPEN.id);
            return true;
        }
        if (id == BlockType.OAK_DOOR_OPEN.id) {
            world.setBlock(x, y, z, BlockType.OAK_DOOR.id);
            return true;
        }
        return false;
    }

    /** True for either door state. */
    public static boolean isDoor(BlockType type) {
        return type == BlockType.OAK_DOOR || type == BlockType.OAK_DOOR_OPEN;
    }
}