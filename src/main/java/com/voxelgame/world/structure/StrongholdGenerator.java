package com.voxelgame.world.structure;

import com.voxelgame.world.BlockType;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.generator.TerrainGenerator;

/**
 * Strongholds: large underground complexes leading to the End portal.
 *
 * Strongholds live in three rings around the world origin, two per ring.
 * The layout is a pure function of world position, so every chunk that
 * overlaps a stronghold computes and writes exactly its own slice.
 * Blocks are deferred via {@link Chunk#addPendingWrite} and applied on
 * the main thread at publish time.
 */
public class StrongholdGenerator {

    private final long seed;
    private final TerrainGenerator terrain;

    private static final int[] RING_DISTANCE = {700, 1100, 1600};
    private static final int PER_RING = 2;
    /** Half footprint of a stronghold, in blocks. */
    private static final int HALF_RADIUS = 24;

    public StrongholdGenerator(long seed, TerrainGenerator terrain) {
        this.seed = seed;
        this.terrain = terrain;
    }

    /** Generate the stronghold slices overlapping a chunk, if any. */
    public void generate(Chunk chunk) {
        int x0 = chunk.getWorldX();
        int z0 = chunk.getWorldZ();
        int x1 = x0 + Chunk.SIZE;
        int z1 = z0 + Chunk.SIZE;

        for (int ring = 0; ring < RING_DISTANCE.length; ring++) {
            for (int i = 0; i < PER_RING; i++) {
                long h = hash(ring, i);
                double angle = ((h & 0xFFFF) / 65536.0) * Math.PI * 2.0;
                double dist = RING_DISTANCE[ring];
                int sx = (int) Math.round(Math.cos(angle) * dist) + ((int) ((h >> 16) & 0x7F) - 64);
                int sz = (int) Math.round(Math.sin(angle) * dist) + ((int) ((h >> 24) & 0x7F) - 64);

                if (x1 < sx - HALF_RADIUS || x0 > sx + HALF_RADIUS) continue;
                if (z1 < sz - HALF_RADIUS || z0 > sz + HALF_RADIUS) continue;

                generateIntoChunk(chunk, sx, sz);
            }
        }
    }

    /** Write this chunk's slice of the stronghold centred at (sx, sz). */
    private void generateIntoChunk(Chunk chunk, int sx, int sz) {
        int groundY = terrain.getHeight(sx, sz);
        int baseY = Math.max(22, Math.min(48, groundY - 18));

        // Portal room: a hollow frame cell with the portal floor in the middle
        portalRoom(chunk, sx, baseY, sz);
        // Four corridors leaving the room
        corridor(chunk, sx, baseY, sz, 1, 0);
        corridor(chunk, sx, baseY, sz, -1, 0);
        corridor(chunk, sx, baseY, sz, 0, 1);
        corridor(chunk, sx, baseY, sz, 0, -1);
        // Library off to the side
        library(chunk, sx + 10, baseY, sz + 6);
        // Treasure vault across from the library
        vault(chunk, sx - 10, baseY, sz - 6);
    }

    /** 9x4x9 hollow box: end portal frame walls, portal floor in the middle. */
    private void portalRoom(Chunk chunk, int sx, int baseY, int sz) {
        for (int y = 0; y <= 3; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    boolean wall = Math.abs(x) == 4 || Math.abs(z) == 4 || y == 0 || y == 3;
                    if (!wall) continue;
                    // Entrance gaps on the corridor axes
                    if (y >= 1 && y <= 2 && Math.abs(x) <= 1 && Math.abs(z) == 4) continue;
                    if (y >= 1 && y <= 2 && Math.abs(z) <= 1 && Math.abs(x) == 4) continue;
                    set(chunk, sx + x, baseY + y, sz + z, BlockType.END_PORTAL_FRAME);
                }
            }
        }
        // The portal itself: a glowing 3x3 floor in the centre
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                set(chunk, sx + x, baseY + 1, sz + z, BlockType.END_PORTAL);
            }
        }
        // A couple of chests on the side walls
        set(chunk, sx + 3, baseY + 1, sz - 3, BlockType.CHEST);
        addLoot(chunk, sx + 3, baseY + 1, sz - 3, 132, 1);
        addLoot(chunk, sx + 3, baseY + 1, sz - 3, 130, 2);
        addLoot(chunk, sx + 3, baseY + 1, sz - 3, 177, 2);
        set(chunk, sx - 3, baseY + 1, sz + 3, BlockType.CHEST);
        addLoot(chunk, sx - 3, baseY + 1, sz + 3, 131, 3);
        addLoot(chunk, sx - 3, baseY + 1, sz + 3, 193, 2);
    }

    /** Straight 3-wide corridor, 12 blocks out from the room along (dx, dz). */
    private void corridor(Chunk chunk, int sx, int baseY, int sz, int dx, int dz) {
        for (int i = 1; i <= 12; i++) {
            for (int y = 0; y <= 2; y++) {
                for (int w = -1; w <= 1; w++) {
                    boolean wall = y == 0 || y == 2 || Math.abs(w) == 1;
                    if (!wall) continue;
                    // Leave a gap in the ceiling every few blocks for light
                    if (y == 2 && i % 5 == 0 && Math.abs(w) == 1) continue;
                    int ox = dx * i, oz = dz * i;
                    set(chunk, sx + ox + (dx == 0 ? w : 0),
                        baseY + y,
                        sz + oz + (dz == 0 ? w : 0),
                        y == 2 ? BlockType.STONE_BRICKS : BlockType.COBBLESTONE);
                }
            }
        }
        // Chest at the corridor end
        int ex = sx + dx * 12, ez = sz + dz * 12;
        set(chunk, ex, baseY + 1, ez, BlockType.CHEST);
        addLoot(chunk, ex, baseY + 1, ez, 129, 4);
        addLoot(chunk, ex, baseY + 1, ez, 194, 2);
        addLoot(chunk, ex, baseY + 1, ez, 177, 1);
    }

    /** 7x5x7 library: bookshelves along the walls, wooden floor. */
    private void library(Chunk chunk, int sx, int baseY, int sz) {
        for (int y = 0; y <= 4; y++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    boolean wall = Math.abs(x) == 3 || Math.abs(z) == 3 || y == 0 || y == 4;
                    if (!wall) continue;
                    if (y == 1 && x == 0 && z == 3) continue; // door
                    if (y == 2 && x == 0 && z == 3) continue;
                    set(chunk, sx + x, baseY + y, sz + z,
                        y == 0 ? BlockType.OAK_PLANKS
                               : (y >= 1 && y <= 2 && (Math.abs(x) == 3 || Math.abs(z) == 3))
                                   ? BlockType.BOOKSHELF : BlockType.STONE_BRICKS);
                }
            }
        }
    }

    /** 5x4x5 vault: double-thick stone walls with a diamond stash. */
    private void vault(Chunk chunk, int sx, int baseY, int sz) {
        for (int y = 0; y <= 3; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2 || y == 0 || y == 3;
                    if (!wall) continue;
                    if (y == 1 && x == 0 && z == 2) continue; // door
                    if (y == 2 && x == 0 && z == 2) continue;
                    set(chunk, sx + x, baseY + y, sz + z, BlockType.STONE_BRICKS);
                }
            }
        }
        set(chunk, sx, baseY + 1, sz, BlockType.CHEST);
        addLoot(chunk, sx, baseY + 1, sz, 132, 2);
        addLoot(chunk, sx, baseY + 1, sz, 131, 4);
        addLoot(chunk, sx, baseY + 1, sz, 130, 6);
        addLoot(chunk, sx, baseY + 1, sz, 187, 1);
        addLoot(chunk, sx, baseY + 1, sz, 188, 12);
    }

    /** Queue a block in this chunk if it lies inside it. */
    private void set(Chunk chunk, int wx, int wy, int wz, BlockType block) {
        int lx = wx - chunk.getWorldX();
        int lz = wz - chunk.getWorldZ();
        if (lx < 0 || lx >= Chunk.SIZE || lz < 0 || lz >= Chunk.SIZE) return;
        if (wy < 0 || wy >= Chunk.HEIGHT) return;
        chunk.addPendingWrite(lx, wy, lz, block.id);
    }

    /** Queue loot for a chest at world coords inside this chunk. */
    private void addLoot(Chunk chunk, int wx, int wy, int wz, int itemId, int count) {
        int lx = wx - chunk.getWorldX();
        int lz = wz - chunk.getWorldZ();
        if (lx < 0 || lx >= Chunk.SIZE || lz < 0 || lz >= Chunk.SIZE) return;
        if (wy < 0 || wy >= Chunk.HEIGHT) return;
        chunk.addPendingLoot(wx, wy, wz, itemId, count);
    }

    private long hash(int a, int b) {
        long h = seed;
        h = h * 6364136223846793005L + a * 341873128712L;
        h = h * 6364136223846793005L + b * 132897987541L;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return h;
    }
}