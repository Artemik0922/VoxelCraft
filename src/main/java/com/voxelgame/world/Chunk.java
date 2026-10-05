package com.voxelgame.world;

import com.voxelgame.world.container.ContainerData;

/**
 * A chunk is a 16x16x256 section of the world
 */
public class Chunk {
    public static final int SIZE = 16;
    public static final int HEIGHT = 256;
    public static final int SEA_LEVEL = 62;
    
    public static final int MAX_LIGHT = 15;
    
    private final int chunkX;
    private final int chunkZ;
    /** Block ids (0..511, extended base blocks use 256+). */
    private final short[] blocks;
    /** High nibble = skylight (0-15), low nibble = block light (0-15). */
    private final byte[] light;
    /** Highest non-air block per column; -1 means "needs a rescan". */
    private final short[] heightMap;
    private boolean dirty = true;
    private boolean lightDirty = true;
    /** Names of structures placed in this chunk (for spacing checks). */
    private final java.util.Set<String> structures = new java.util.HashSet<>();

    /**
     * Structure blocks written on worker threads, applied by the world on
     * the main thread when the chunk is published. Entries: {x, y, z, id}.
     */
    private java.util.List<int[]> pendingWrites;

    /** Structure loot: {wx, wy, wz, itemId, count}, applied at publish. */
    private java.util.List<int[]> pendingLoot;

    /** Mob spawner positions inside this chunk, local coords. */
    private java.util.List<int[]> spawners;

    /**
     * [SD] Sliding-door state, keyed by the BOTTOM cell's local index.
     * Value: packed meta (axis | slide direction | state). The top cell has
     * no entry: it links to the bottom half through this map. Survives world
     * reloads because it is saved with the chunk.
     */
    private final java.util.Map<Integer, Byte> doorMeta = new java.util.concurrent.ConcurrentHashMap<>(); // [PERF-ASYNC] воркер меша читает мету параллельно с главным потоком

    /**
     * [CR] Crop growth stages, keyed by the cell's local index.
     * Value: growth stage (0-7). Only crops whose stage differs from the
     * position-hash default get an entry, so untouched world-gen fields stay
     * sparse. Survives world reloads because it is saved with the chunk.
     */
    private final java.util.Map<Integer, Byte> cropMeta = new java.util.concurrent.ConcurrentHashMap<>(); // [PERF-ASYNC]
    
    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blocks = new short[SIZE * HEIGHT * SIZE];
        this.light = new byte[SIZE * HEIGHT * SIZE];
        this.heightMap = new short[SIZE * SIZE];
        java.util.Arrays.fill(this.heightMap, (short) -1);
    }
    
    public int getBlock(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) {
            return 0;
        }
        int id = blocks[index(x, y, z)] & 0xFFFF;
        // Safety net: ids above the table size would index out of bounds in
        // the light/mesh lookup tables. Treat them as air instead of crashing.
        if (id >= 512) {
            if (badBlockWarn.get() < 5) {
                System.err.println("BAD_BLOCK_ID=" + id + " at chunk " + chunkX + "," + chunkZ
                    + " local=(" + x + "," + y + "," + z + ") world=("
                    + (chunkX * SIZE + x) + "," + y + "," + (chunkZ * SIZE + z) + ")");
                badBlockWarn.incrementAndGet();
            }
            return 0;
        }
        return id;
    }

    /** Global one-shot diagnostic for garbage block ids (capped). */
    private static final java.util.concurrent.atomic.AtomicInteger badBlockWarn =
        new java.util.concurrent.atomic.AtomicInteger();
    
    public void setBlock(int x, int y, int z, int id) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return;
        if (id < 0 || id >= 512) return;
        blocks[index(x, y, z)] = (short) id;
        dirty = true;
        lightDirty = true;
        meshVersion++; // [PERF-ASYNC] блок читается асинхронным мешером
        
        // Keep the heightmap current instead of rescanning the column later
        int hi = x * SIZE + z;
        if (id != 0) {
            if (y > heightMap[hi]) heightMap[hi] = (short) y;
        } else if (y == heightMap[hi]) {
            heightMap[hi] = -1; // force a rescan on next query
        }
    }
    
    /**
     * True once the player has changed this chunk, so only modified chunks
     * are written to disk; the rest regenerate from the seed.
     */
    private boolean modified = false;
    
    public boolean isModified() { return modified; }
    public void markModified() { modified = true; }
    public void clearModified() { modified = false; }

    /** [PERF] Чанк восстановлен с диска (а не сгенерирован заново). */
    private boolean restoredFromDisk = false;
    public boolean isRestoredFromDisk() { return restoredFromDisk; }
    public void setRestoredFromDisk(boolean v) { restoredFromDisk = v; }

    // [PERF-ASYNC] Счётчик мутаций данных, из которых мешер строит геометрию
    // (блоки, свет, мета). Асинхронная сборка геометрии в Renderer сверяет
    // счётчик до и после построения: изменился — результат выбрасывается.
    private volatile long meshVersion = 0;
    public long getMeshVersion() { return meshVersion; }
    public void bumpMeshVersion() { meshVersion++; }
    
    /** Restore blocks from a save (format 6: short ids). */
    public void loadBlocks(short[] data) {
        for (int i = 0; i < Math.min(data.length, blocks.length); i++) {
            int v = data[i] & 0xFFFF;
            if (v >= 512) {
                System.err.println("LOAD_BLOCKS_BAD_ID=" + v + " at chunk " + chunkX + "," + chunkZ
                    + " idx=" + i);
                badBlockWarn.incrementAndGet();
                blocks[i] = 0;
            } else {
                blocks[i] = data[i];
            }
            // [PERF] Спавнеры собираем в этом же проходе — отдельный
            // 65 536-ячеечный скан на главном потоке больше не нужен
            if (v == BlockType.MOB_SPAWNER.id) {
                addSpawner(i % SIZE, i / (SIZE * SIZE), (i / SIZE) % SIZE);
            }
        }
        java.util.Arrays.fill(heightMap, (short) -1);
        dirty = true;
        lightDirty = true;
        meshVersion++; // [PERF-ASYNC]
        modified = true;
    }

    /** Restore blocks from a legacy save (formats 1-5: byte ids). */
    public void loadBlocks(byte[] data) {
        for (int i = 0; i < Math.min(data.length, blocks.length); i++) {
            blocks[i] = (short) (data[i] & 0xFF);
            // [PERF] Спавнеры собираем в этом же проходе
            if (blocks[i] == BlockType.MOB_SPAWNER.id) {
                addSpawner(i % SIZE, i / (SIZE * SIZE), (i / SIZE) % SIZE);
            }
        }
        java.util.Arrays.fill(heightMap, (short) -1);
        dirty = true;
        lightDirty = true;
        meshVersion++; // [PERF-ASYNC]
        modified = true;
    }
    
    /**
     * Y of the highest non-air block in a column, or -1 when empty.
     *
     * Lighting used to walk all 256 levels of every column; this bounds that
     * to the part of the world that actually contains blocks.
     */
    public int getHighestBlock(int x, int z) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE) return -1;
        
        int hi = x * SIZE + z;
        short cached = heightMap[hi];
        if (cached >= 0) return cached;
        
        for (int y = HEIGHT - 1; y >= 0; y--) {
            if (blocks[index(x, y, z)] != 0) {
                heightMap[hi] = (short) y;
                return y;
            }
        }
        heightMap[hi] = 0;
        return 0;
    }
    
    // ------------------------------------------------------------------
    // Lighting
    // ------------------------------------------------------------------
    
    public int getSkyLight(int x, int y, int z) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE) return MAX_LIGHT;
        if (y < 0) return 0;
        if (y >= HEIGHT) return MAX_LIGHT;
        return (light[index(x, y, z)] >> 4) & 0xF;
    }
    
    public int getBlockLight(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return 0;
        return light[index(x, y, z)] & 0xF;
    }
    
    public void setSkyLight(int x, int y, int z, int value) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return;
        int i = index(x, y, z);
        light[i] = (byte) ((light[i] & 0x0F) | ((value & 0xF) << 4));
    }
    
    public void setBlockLight(int x, int y, int z, int value) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return;
        int i = index(x, y, z);
        light[i] = (byte) ((light[i] & 0xF0) | (value & 0xF));
    }
    
    /** Combined light level used for rendering. */
    public int getLight(int x, int y, int z) {
        return Math.max(getSkyLight(x, y, z), getBlockLight(x, y, z));
    }
    
    public void clearLight() {
        java.util.Arrays.fill(light, (byte) 0);
    }
    
    public byte[] getLightData() { return light; }
    public boolean isLightDirty() { return lightDirty; }
    public void setLightDirty(boolean v) { this.lightDirty = v; }
    
    public boolean isSolid(int x, int y, int z) {
        int id = getBlock(x, y, z);
        if (id == 0) return false;
        BlockType type = BlockType.fromId(id);
        return type.solid;
    }
    
    public boolean isTransparent(int x, int y, int z) {
        int id = getBlock(x, y, z);
        if (id == 0) return true;
        BlockType type = BlockType.fromId(id);
        return !type.solid || type == BlockType.WATER || type == BlockType.GLASS || type == BlockType.ICE;
    }
    
    private int index(int x, int y, int z) {
        return (y * SIZE + z) * SIZE + x;
    }
    
    public int getWorldX() { return chunkX * SIZE; }
    public int getWorldZ() { return chunkZ * SIZE; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public short[] getBlocks() { return blocks; }
    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }
    
    public static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    // --- Structure tracking ---

    /** Mark that a structure was placed in this chunk. */
    public void markStructure(String name) {
        structures.add(name);
    }

    /** Check if a structure of this type exists in this chunk. */
    public boolean hasStructure(String name) {
        return structures.contains(name);
    }

    // --- Deferred structure writes (worker -> main thread) ---

    /** Queue a structure block, applied by the world at publish time. */
    public void addPendingWrite(int x, int y, int z, int block) {
        if (pendingWrites == null) pendingWrites = new java.util.ArrayList<>();
        pendingWrites.add(new int[]{x, y, z, block});
    }

    /** Queue a loot item (world coords, item id from ItemRegistry). */
    public void addPendingLoot(int wx, int wy, int wz, int itemId, int count) {
        if (pendingLoot == null) pendingLoot = new java.util.ArrayList<>();
        pendingLoot.add(new int[]{wx, wy, wz, itemId, count});
    }

    /** Take the queued writes; the caller applies them on the main thread. */
    public java.util.List<int[]> drainPendingWrites() {
        java.util.List<int[]> out = pendingWrites;
        pendingWrites = null;
        return out;
    }

    /** Take the queued loot; the caller applies it on the main thread. */
    public java.util.List<int[]> drainPendingLoot() {
        java.util.List<int[]> out = pendingLoot;
        pendingLoot = null;
        return out;
    }

    /** Record a mob spawner at local coordinates (main thread only). */
    public void addSpawner(int x, int y, int z) {
        if (spawners == null) spawners = new java.util.ArrayList<>();
        spawners.add(new int[]{x, y, z});
    }

    /**
     * Find all spawner blocks in this chunk. Used once at publish time so
     * chunks loaded from disk (whose spawner blocks are baked into the
     * save) get a spawner list too. A one-time 16x16x256 pass per chunk.
     */
    public void scanSpawners() {
        if (spawners != null) return;
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (getBlock(x, y, z) == BlockType.MOB_SPAWNER.id) {
                        addSpawner(x, y, z);
                    }
                }
            }
        }
    }

    /** Spawner positions in local coords, or null when none. */
    public java.util.List<int[]> getSpawners() {
        return spawners;
    }

    // --- [SD] Sliding-door meta ---

    /**
     * Door meta of the cell (bottom half expected), or 0 when absent.
     * Meta does not mark the chunk modified: state transitions mark it
     * explicitly so untouched chunks still regenerate from the seed.
     */
    public byte getDoorMeta(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return 0;
        Byte v = doorMeta.get(index(x, y, z));
        return v == null ? 0 : v;
    }

    /** Store door meta; 0 clears the entry. Chunk NOT marked modified here. */
    public void setDoorMeta(int x, int y, int z, byte meta) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return;
        meshVersion++; // [PERF-ASYNC]
        if (meta == 0) {
            doorMeta.remove(index(x, y, z));
        } else {
            doorMeta.put(index(x, y, z), meta);
        }
    }

    /** All door entries as {lx, ly, lz, meta}, for the chunk save format. */
    public java.util.List<int[]> doorMetaEntries() {
        java.util.List<int[]> out = new java.util.ArrayList<>(doorMeta.size());
        for (java.util.Map.Entry<Integer, Byte> e : doorMeta.entrySet()) {
            int i = e.getKey();
            out.add(new int[]{i & 15, (i >> 8) & 255, (i >> 4) & 15, e.getValue() & 0xFF});
        }
        return out;
    }

    /** Restore one door entry from a save file. */
    public void loadDoorMeta(int x, int y, int z, byte meta) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return;
        if (meta != 0) {
            doorMeta.put(index(x, y, z), meta);
        }
    }

    // --- [CR] Crop growth meta ---

    /** Growth stage (0-7) of a crop cell, or -1 when no entry is stored. */
    public int getCropStage(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return -1;
        Byte v = cropMeta.get(index(x, y, z));
        return v == null ? -1 : (v & 0xFF);
    }

    /**
     * Store a crop growth stage; a value < 0 clears the entry so untouched
     * crops fall back to the position-hash default. Chunk NOT marked
     * modified here: callers mark it explicitly.
     */
    public void setCropStage(int x, int y, int z, int stage) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return;
        meshVersion++; // [PERF-ASYNC]
        if (stage < 0 || stage > 7) {
            cropMeta.remove(index(x, y, z));
        } else {
            cropMeta.put(index(x, y, z), (byte) stage);
        }
    }

    /** All crop entries as {lx, ly, lz, stage}, for the chunk save format. */
    public java.util.List<int[]> cropMetaEntries() {
        java.util.List<int[]> out = new java.util.ArrayList<>(cropMeta.size());
        for (java.util.Map.Entry<Integer, Byte> e : cropMeta.entrySet()) {
            int i = e.getKey();
            out.add(new int[]{i & 15, (i >> 8) & 255, (i >> 4) & 15, e.getValue() & 0xFF});
        }
        return out;
    }

    /** Restore one crop entry from a save file. */
    public void loadCropMeta(int x, int y, int z, int stage) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return;
        if (stage >= 0 && stage <= 7) {
            cropMeta.put(index(x, y, z), (byte) stage);
        }
    }

    // --- [WQ] Water levels ---

    /**
     * Flowing-water levels (0-7), keyed by the cell's local index. Level 0
     * is a source / static water (oceans, bucket placements, falling
     * columns) and needs NO entry; entries exist only for water that spread
     * away from a source, so untouched oceans stay sparse. Survives world
     * reloads because it is saved with the chunk.
     */
    private final java.util.Map<Integer, Byte> waterMeta = new java.util.concurrent.ConcurrentHashMap<>(); // [PERF-ASYNC]

    /** Water level (0-7) of a cell, 0 = source / static water. */
    public int getWaterLevel(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return 0;
        Byte v = waterMeta.get(index(x, y, z));
        return v == null ? 0 : (v & 0xFF);
    }

    /** True when this cell has an explicit level entry (reflowable water). */
    public boolean hasWaterMeta(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return false;
        return waterMeta.containsKey(index(x, y, z));
    }

    /**
     * Store a water level; a level of 0 clears the entry, turning the cell
     * into static source water. Chunk NOT marked modified here: callers
     * mark it explicitly.
     */
    public void setWaterLevel(int x, int y, int z, int level) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return;
        meshVersion++; // [PERF-ASYNC]
        if (level < 1 || level > 7) {
            waterMeta.remove(index(x, y, z));
        } else {
            waterMeta.put(index(x, y, z), (byte) level);
        }
    }

    /** All water entries as {lx, ly, lz, level}, for the chunk save format. */
    public java.util.List<int[]> waterMetaEntries() {
        java.util.List<int[]> out = new java.util.ArrayList<>(waterMeta.size());
        for (java.util.Map.Entry<Integer, Byte> e : waterMeta.entrySet()) {
            int i = e.getKey();
            out.add(new int[]{i & 15, (i >> 8) & 255, (i >> 4) & 15, e.getValue() & 0xFF});
        }
        return out;
    }

    /** Restore one water entry from a save file. */
    public void loadWaterMeta(int x, int y, int z, int level) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return;
        if (level >= 1 && level <= 7) {
            waterMeta.put(index(x, y, z), (byte) level);
        }
    }

    // --- [CF] Container data restored from disk ---

    /**
     * Containers read by {@code WorldSave.loadChunk}: {lx, ly, lz, ContainerData}.
     * The world consumes them into its ContainerManager once the chunk is
     * published, so the chunk itself never owns container state.
     */
    private java.util.List<Object[]> pendingContainers;

    /** Restore one container entry from a save file. */
    public void loadContainerEntry(int lx, int ly, int lz, int typeOrdinal, String data) {
        if (lx < 0 || lx >= SIZE || ly < 0 || ly >= HEIGHT || lz < 0 || lz >= SIZE) return;
        if (typeOrdinal < 0 || typeOrdinal >= ContainerData.Type.values().length) return;
        if (pendingContainers == null) pendingContainers = new java.util.ArrayList<>();
        pendingContainers.add(new Object[]{lx, ly, lz,
            ContainerData.deserialize(data, ContainerData.Type.values()[typeOrdinal])});
    }

    /** Containers loaded from disk, transferred to the world exactly once. */
    public java.util.List<Object[]> takePendingContainers() {
        java.util.List<Object[]> out = pendingContainers;
        pendingContainers = null;
        return out;
    }
}
