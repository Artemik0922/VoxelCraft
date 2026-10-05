package com.voxelgame.world;

/**
 * Minecraft-style flood-fill lighting.
 *
 * Two independent 0-15 channels:
 *   skylight   - sunlight falling straight down, attenuated horizontally
 *   blocklight - emitted by glowstone / lava / torches
 *
 * Propagation crosses chunk borders, so shadows and lamps are continuous.
 *
 * The queue holds packed ints rather than objects and neighbour chunks are
 * resolved once into a 3x3 grid instead of per cell: the earlier version
 * cost 47 ms per chunk, which alone was a 963 ms stall whenever the player
 * crossed a chunk boundary at render distance 8.
 */
public class LightEngine {

    /** O(1) opacity lookup table. */
    private static final byte[] OPACITY = new byte[512];
    /** O(1) emission lookup table. */
    private static final byte[] EMISSION = new byte[512];
    static {
        for (BlockType t : BlockType.values()) {
            byte op;
            if (t == BlockType.WATER || t == BlockType.ICE) op = 2;
            else if (t == BlockType.OAK_LEAVES || t == BlockType.SPRUCE_LEAVES
                || t == BlockType.BIRCH_LEAVES || t == BlockType.JUNGLE_LEAVES
                || t == BlockType.AUTUMN_LEAVES || t == BlockType.CHERRY_LEAVES) op = 2;
            else if (t == BlockType.GLASS) op = 0;
            else if (!t.solid) op = 0;
            else op = 15;
            OPACITY[t.id] = op;

            byte emit;
            if (t == BlockType.GLOWSTONE || t == BlockType.LAVA) emit = 15;
            else if (t == BlockType.GLOWING_OBSIDIAN) emit = 12;
            else if (t == BlockType.NETHER_REACTOR) emit = 7;
            // [GP-067] Torches shed 14 levels of light, one short of the sun
            else if (t == BlockType.TORCH_PLACEHOLDER) emit = 14;
            // [GP-073] Fire glows as brightly as lava
            else if (t == BlockType.FIRE) emit = 15;
            // [BASE] Lanterns (glass, iron, snow) emit 14; campfire glows
            else if (t == BlockType.LANTERN || t == BlockType.ICE_LANTERN) emit = 14;
            else if (t == BlockType.CAMPFIRE) emit = 15;
            else emit = 0;
            EMISSION[t.id] = emit;
        }
    }

    /** How much light a block absorbs when light passes through it. */
    public static int opacity(int blockId) {
        return blockId >= 0 && blockId < OPACITY.length ? OPACITY[blockId] : 15;
    }

    /** Light emitted by a block itself. */
    public static int emission(int blockId) {
        return blockId >= 0 && blockId < EMISSION.length ? EMISSION[blockId] : 0;
    }

    // ------------------------------------------------------------------
    // Work area
    // ------------------------------------------------------------------

    /**
     * Scratch state for one chunk's light pass.
     *
     * Holds the 3x3 neighbourhood so a cell lookup is array indexing rather
     * than a map probe, plus a reusable int queue.
     */
    private static final class Work {
        final Chunk[] grid = new Chunk[9];
        int baseX, baseZ;

        int[] queue = new int[1 << 16];
        int head, tail;

        void reset(World world, Chunk centre) {
            baseX = centre.getChunkX() - 1;
            baseZ = centre.getChunkZ() - 1;
            for (int dz = 0; dz < 3; dz++) {
                for (int dx = 0; dx < 3; dx++) {
                    grid[dz * 3 + dx] = world.getChunk(baseX + dx, baseZ + dz);
                }
            }
            head = tail = 0;
        }

        /** Chunk owning a world column, or null if outside the 3x3 area. */
        Chunk chunkAt(int wx, int wz) {
            int cx = wx >> 4;
            int cz = wz >> 4;
            int dx = cx - baseX;
            int dz = cz - baseZ;
            if (dx < 0 || dx > 2 || dz < 0 || dz > 2) return null;
            return grid[dz * 3 + dx];
        }

        void push(int wx, int y, int wz) {
            if (tail == queue.length) {
                if (head > 0) {
                    // Compact instead of growing without bound
                    System.arraycopy(queue, head, queue, 0, tail - head);
                    tail -= head;
                    head = 0;
                } else {
                    queue = java.util.Arrays.copyOf(queue, queue.length * 2);
                }
            }
            // Local offsets fit in a few bits each: dx,dz are -16..31
            queue[tail++] = ((wx - (baseX << 4)) << 20)
                          | ((wz - (baseZ << 4)) << 12)
                          | (y & 0xFFF);
        }

        boolean hasWork() { return head < tail; }

        int popX() { return ((queue[head] >>> 20) & 0xFFF) + (baseX << 4); }
        int popZ() { return ((queue[head] >>> 12) & 0xFFF) + (baseZ << 4); }
        int popY() { return queue[head] & 0xFFF; }
        void advance() { head++; }
    }

    /** One work area per thread, so meshing can later run in parallel. */
    private static final ThreadLocal<Work> WORK = ThreadLocal.withInitial(Work::new);

    /** Flattened neighbour offsets for cache-friendly iteration. */
    private static final int[] NEIGHBOURS = {
        1, 0, 0, -1, 0, 0, 0, 1, 0, 0, -1, 0, 0, 0, 1, 0, 0, -1
    };

    // ------------------------------------------------------------------

    /**
     * Recompute both channels for a chunk, letting light spill in from and
     * out to the chunks already loaded around it.
     */
    public static void computeChunkLight(Chunk chunk, World world) {
        chunk.clearLight();

        Work w = WORK.get();
        w.reset(world, chunk);

        int originX = chunk.getWorldX();
        int originZ = chunk.getWorldZ();

        // ---- Skylight: cast straight down from the sky ----
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int level = Chunk.MAX_LIGHT;

                // Start at the highest solid block instead of the world top:
                // everything above is open sky and already at full strength.
                int top = chunk.getHighestBlock(x, z);

                for (int y = Math.min(Chunk.HEIGHT - 1, top + 1); y >= 0; y--) {
                    int op = opacity(chunk.getBlock(x, y, z));

                    if (op >= Chunk.MAX_LIGHT) {
                        level = 0;
                    } else {
                        level = Math.max(0, level - op);
                    }
                    if (level <= 0) break;

                    chunk.setSkyLight(x, y, z, level);
                    if (level > 1) w.push(originX + x, y, originZ + z);
                }
            }
        }

        seedBorder(w, originX, originZ, true);
        spread(w, true);

        // ---- Block light ----
        w.head = w.tail = 0;
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int top = chunk.getHighestBlock(x, z);
                for (int y = 0; y <= top; y++) {
                    int emit = emission(chunk.getBlock(x, y, z));
                    if (emit > 0) {
                        chunk.setBlockLight(x, y, z, emit);
                        w.push(originX + x, y, originZ + z);
                    }
                }
            }
        }

        seedBorder(w, originX, originZ, false);
        spread(w, false);

        chunk.setLightDirty(false);
        chunk.bumpMeshVersion(); // [PERF-ASYNC] свет читается асинхронным мешером
    }

    /**
     * Queue the ring of columns just outside the chunk, so a bright
     * neighbour bleeds in without waiting to be rebuilt itself.
     *
     * Only scans up to each neighbour column's highest block: above that the
     * sky is uniform and seeding it would add nothing.
     */
    private static void seedBorder(Work w, int originX, int originZ, boolean sky) {
        for (int i = -1; i <= Chunk.SIZE; i++) {
            seedColumn(w, originX + i, originZ - 1, sky);
            seedColumn(w, originX + i, originZ + Chunk.SIZE, sky);
            seedColumn(w, originX - 1, originZ + i, sky);
            seedColumn(w, originX + Chunk.SIZE, originZ + i, sky);
        }
    }

    private static void seedColumn(Work w, int wx, int wz, boolean sky) {
        Chunk c = w.chunkAt(wx, wz);
        if (c == null) return;

        int lx = wx & 15;
        int lz = wz & 15;

        int top = Math.min(Chunk.HEIGHT - 1, c.getHighestBlock(lx, lz) + 1);
        for (int y = top; y >= 0; y--) {
            int level = sky ? c.getSkyLight(lx, y, lz) : c.getBlockLight(lx, y, lz);
            if (level > 1) w.push(wx, y, wz);
        }
    }

    /**
     * Breadth-first propagation across chunk boundaries.
     */
    private static void spread(Work w, boolean sky) {
        while (w.hasWork()) {
            int x = w.popX();
            int y = w.popY();
            int z = w.popZ();
            w.advance();

            Chunk here = w.chunkAt(x, z);
            if (here == null) continue;

            int current = sky
                ? here.getSkyLight(x & 15, y, z & 15)
                : here.getBlockLight(x & 15, y, z & 15);
            if (current <= 1) continue;

            for (int i = 0; i < 18; i += 3) {
                int nx = x + NEIGHBOURS[i];
                int ny = y + NEIGHBOURS[i + 1];
                int nz = z + NEIGHBOURS[i + 2];
                if (ny < 0 || ny >= Chunk.HEIGHT) continue;

                Chunk target = w.chunkAt(nx, nz);
                if (target == null) continue;

                int tx = nx & 15;
                int tz = nz & 15;

                int op = opacity(target.getBlock(tx, ny, tz));
                if (op >= Chunk.MAX_LIGHT) continue;

                int value = (sky && NEIGHBOURS[i + 1] == -1 && current == Chunk.MAX_LIGHT)
                    ? current : current - 1 - op;
                if (value <= 0) continue;

                int existing = sky ? target.getSkyLight(tx, ny, tz)
                                   : target.getBlockLight(tx, ny, tz);
                if (existing >= value) continue;

                if (sky) {
                    target.setSkyLight(tx, ny, tz, value);
                } else {
                    target.setBlockLight(tx, ny, tz, value);
                }

                if (target != here) target.setDirty(true);
                w.push(nx, ny, nz);
            }
        }
    }

    // ------------------------------------------------------------------
    // Incremental single-block updates (much faster than full chunk relight)
    // ------------------------------------------------------------------

    /**
     * Incrementally update sky light when one block in a column changes.
     * Only recalculates the affected column, not the whole chunk.
     * Returns true if the column's light changed.
     */
    public static boolean updateSkyLightColumn(World world, int wx, int wz) {
        int cx = wx >> 4;
        int cz = wz >> 4;
        Chunk chunk = world.getChunk(cx, cz);
        if (chunk == null) return false;

        int lx = wx & 15;
        int lz = wz & 15;

        int level = Chunk.MAX_LIGHT;
        int top = chunk.getHighestBlock(lx, lz);
        boolean changed = false;

        for (int y = Math.min(Chunk.HEIGHT - 1, top + 1); y >= 0; y--) {
            int op = opacity(chunk.getBlock(lx, y, lz));
            int newLevel;
            if (op >= Chunk.MAX_LIGHT) {
                newLevel = 0;
            } else {
                newLevel = Math.max(0, level - op);
            }
            if (newLevel <= 0) {
                if (level > 0) changed = true;
                level = 0;
            }

            if (newLevel != level) {
                level = newLevel;
                changed = true;
            }

            int existing = chunk.getSkyLight(lx, y, lz);
            if (existing != level) {
                chunk.setSkyLight(lx, y, lz, level);
                changed = true;
            }
            if (level <= 0) break;
        }
        return changed;
    }

    /**
     * Incrementally spread block light from (wx, y, wz) into neighbours.
     * Does a bounded BFS so it completes in microseconds even if a big lamp
     * is placed. Marks affected neighbours dirty for full relight later.
     */
    public static void spreadBlockLightIncremental(World world, int wx, int y, int wz, int emitLevel) {
        if (emitLevel <= 0) return;

        Work w = WORK.get();
        w.reset(world, world.getChunk(wx >> 4, wz >> 4));
        if (w.chunkAt(wx, wz) == null) return;

        w.push(wx, y, wz);

        while (w.hasWork()) {
            int bx = w.popX();
            int by = w.popY();
            int bz = w.popZ();
            w.advance();

            int current = w.chunkAt(bx, bz).getBlockLight(bx & 15, by, bz & 15);
            if (current >= emitLevel) continue;

            for (int i = 0; i < 18; i += 3) {
                int nx = bx + NEIGHBOURS[i];
                int ny = by + NEIGHBOURS[i + 1];
                int nz = bz + NEIGHBOURS[i + 2];
                if (ny < 0 || ny >= Chunk.HEIGHT) continue;

                Chunk nc = w.chunkAt(nx, nz);
                if (nc == null) continue;

                int op = opacity(nc.getBlock(nx & 15, ny, nz & 15));
                if (op >= Chunk.MAX_LIGHT) continue;

                int next = Math.max(0, current - 1 - op);
                if (next <= 0) continue;

                int existing = nc.getBlockLight(nx & 15, ny, nz & 15);
                if (existing >= next) continue;

                nc.setBlockLight(nx & 15, ny, nz & 15, next);
                nc.setDirty(true);
                w.push(nx, ny, nz);
            }
        }
    }

    /**
     * Full light update for one block change.
     * Recalculates sky light column + spreads block light.
     */
    public static void updateBlock(World world, int wx, int y, int wz, int blockId) {
        // Sky light: recalculate this column from top
        updateSkyLightColumn(world, wx, wz);

        // Also recalculate 4 adjacent columns since they may be affected
        updateSkyLightColumn(world, wx + 1, wz);
        updateSkyLightColumn(world, wx - 1, wz);
        updateSkyLightColumn(world, wx, wz + 1);
        updateSkyLightColumn(world, wx, wz - 1);

        // Block light: if the block emits, spread from here
        int emit = emission(blockId);
        if (emit > 0) {
            spreadBlockLightIncremental(world, wx, y, wz, emit);
        }

        // If block removed or changed opacity, propagate to neighbours that had light from here
        int cx = wx >> 4;
        int cz = wz >> 4;
        Chunk chunk = world.getChunk(cx, cz);
        if (chunk != null) {
            // Mark any neighbour that needs full relight
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Chunk n = world.getChunk(cx + dx, cz + dz);
                    if (n != null && n.isLightDirty()) n.setDirty(true);
                }
            }
        }
    }
}
