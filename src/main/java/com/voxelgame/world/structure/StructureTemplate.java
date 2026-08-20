package com.voxelgame.world.structure;

import com.voxelgame.world.BlockType;

/**
 * A structure template defines a set of block placements relative to an origin.
 * Structures are placed deterministically based on world position and seed,
 * so the same structure always appears at the same location.
 */
public class StructureTemplate {

    public final String name;
    public final int width;
    public final int height;
    public final int depth;
    private final short[] blocks;

    /** True when the structure may sit underwater (ocean monuments, wrecks). */
    public boolean underwater = false;

    /** Loot entries: {lx, ly, lz, itemId, count} placed at the chest cell. */
    private final java.util.List<int[]> loot = new java.util.ArrayList<>();

    /**
     * Create a structure template.
     * @param name structure identifier
     * @param width X size
     * @param height Y size
     * @param depth Z size
     */
    public StructureTemplate(String name, int width, int height, int depth) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.blocks = new short[width * height * depth];
    }

    /** Mark the template as allowed to sit underwater. */
    public StructureTemplate allowUnderwater() {
        this.underwater = true;
        return this;
    }

    /** Add an item to the structure's loot chest (item id from ItemRegistry). */
    public StructureTemplate addLoot(int lx, int ly, int lz, int itemId, int count) {
        loot.add(new int[]{lx, ly, lz, itemId, count});
        return this;
    }

    public java.util.List<int[]> getLoot() { return loot; }

    /**
     * Set a block at local coordinates.
     */
    public void setBlock(int x, int y, int z, BlockType type) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth) return;
        blocks[(y * depth + z) * width + x] = (short) type.id;
    }

    /**
     * Get a block at local coordinates.
     */
    public int getBlock(int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth) return 0;
        return blocks[(y * depth + z) * width + x] & 0xFFFF;
    }

    /**
     * Check if a block is solid at local coordinates.
     */
    public boolean isSolid(int x, int y, int z) {
        return BlockType.isSolidFast(getBlock(x, y, z));
    }

    /**
     * Get the lowest Y at which there is a solid block (for ground placement).
     */
    public int getMinSolidY() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    if (isSolid(x, y, z)) return y;
                }
            }
        }
        return 0;
    }

    /**
     * Get the highest Y at which there is a solid block.
     */
    public int getMaxSolidY() {
        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    if (isSolid(x, y, z)) return y;
                }
            }
        }
        return height - 1;
    }
}
