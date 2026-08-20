package com.voxelgame.world.container;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.World;
import com.voxelgame.world.BlockType;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages all container data in the world.
 * Provides access to containers by block position.
 * Handles double chest detection.
 */
public class ContainerManager {

    /** Key: packed block position (long), Value: container data */
    private final Map<Long, ContainerData> containers = new HashMap<>();

    /** Reference to world for block lookups. */
    private World world;

    public void setWorld(World world) {
        this.world = world;
    }

    /**
     * Get or create a container at a position.
     * Creating one marks the owning chunk modified so its contents
     * survive the next save (opening a world-gen furnace must not lose
     * the items the player put into it).
     */
    public ContainerData getOrCreate(int x, int y, int z, ContainerData.Type type) {
        long key = key(x, y, z);
        ContainerData data = containers.get(key);
        if (data == null) {
            data = new ContainerData(type);
            containers.put(key, data);
            if (world != null) {
                Chunk chunk = world.getChunk(x >> 4, z >> 4);
                if (chunk != null) chunk.markModified();
            }
        }
        return data;
    }

    /**
     * Get container at position, or null if none exists.
     */
    public ContainerData get(int x, int y, int z) {
        return containers.get(key(x, y, z));
    }

    /**
     * Get a double chest container. If two chests are adjacent, returns
     * a merged view. Otherwise returns the single chest.
     */
    public ContainerData getChest(int x, int y, int z) {
        // Check for adjacent chest
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};

        for (int i = 0; i < 4; i++) {
            int nx = x + dx[i];
            int nz = z + dz[i];
            if (world != null && world.getBlock(nx, y, nz) == BlockType.CHEST.id) {
                // Found adjacent chest - create double chest
                return getDoubleChest(x, y, z, nx, nz);
            }
        }

        // Single chest
        return getOrCreate(x, y, z, ContainerData.Type.CHEST);
    }

    /**
     * Create a double chest view combining two adjacent chests.
     */
    private ContainerData getDoubleChest(int x1, int y, int z1, int x2, int z2) {
        // Use the lower position as the "master"
        ContainerData chest1 = getOrCreate(x1, y, z1, ContainerData.Type.CHEST);
        ContainerData chest2 = getOrCreate(x2, y, z2, ContainerData.Type.CHEST);

        // Create a wrapper that combines both
        return new DoubleChestData(chest1, chest2);
    }

    /**
     * Remove a container at position.
     */
    public void remove(int x, int y, int z) {
        containers.remove(key(x, y, z));
    }

    /**
     * Check if a container exists at position.
     */
    public boolean hasContainer(int x, int y, int z) {
        return containers.containsKey(key(x, y, z));
    }

    /**
     * Clear all containers (world unload).
     */
    public void clear() {
        containers.clear();
    }

    public Map<Long, ContainerData> getContainers() {
        return containers;
    }

    /**
     * All containers whose block position lies inside the chunk (cx, cz).
     * Each entry: {x, y, z, ContainerData} in world coordinates.
     */
    public java.util.List<Object[]> getContainersInChunk(int cx, int cz) {
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        int x0 = cx << 4;
        int z0 = cz << 4;
        for (Map.Entry<Long, ContainerData> e : containers.entrySet()) {
            long k = e.getKey();
            int x = (int) (k >> 42);
            int y = (int) ((k >> 21) & 0x1FFFFF);
            int z = (int) (k & 0x1FFFFF);
            if ((x & 0x100000) != 0) x |= ~0x1FFFFF;
            if ((y & 0x100000) != 0) y |= ~0x1FFFFF;
            if ((z & 0x100000) != 0) z |= ~0x1FFFFF;
            if (x >= x0 && x < x0 + Chunk.SIZE && z >= z0 && z < z0 + Chunk.SIZE) {
                out.add(new Object[]{x, y, z, e.getValue()});
            }
        }
        return out;
    }

    /** Restore a container loaded from disk. */
    public void restore(int x, int y, int z, ContainerData data) {
        containers.put(key(x, y, z), data);
    }

    /**
     * Pack block position into a long key.
     */
    private static long key(int x, int y, int z) {
        // Use 21 bits per coordinate (range ~±1 million)
        return ((long)(x & 0x1FFFFF) << 42) | ((long)(y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }
}
