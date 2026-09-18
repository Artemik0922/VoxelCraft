package com.voxelgame.world;

import com.voxelgame.item.Inventory;
import com.voxelgame.item.ItemStack;
import com.voxelgame.player.Player;
import com.voxelgame.physics.Raycast;
import com.voxelgame.world.entity.AsteroidEntity;
import com.voxelgame.world.entity.Animal;
import com.voxelgame.world.entity.FallingBlockEntity;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.entity.XpOrb;
import com.voxelgame.world.entity.TntEntity;
import com.voxelgame.world.entity.Villager;
import com.voxelgame.world.entity.Zoloy;
import com.voxelgame.world.generator.TerrainGenerator;
import com.voxelgame.world.biome.BiomeData;
import com.voxelgame.world.biome.BiomeSelector;
import com.voxelgame.world.water.WaterSimulation;
import com.voxelgame.world.structure.VillageGenerator;
import com.voxelgame.world.container.ContainerManager;
import com.voxelgame.world.container.ContainerData;
import org.joml.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages the voxel world - chunks, blocks, terrain generation
 */
public class World {
    private Map<Long, Chunk> chunks = new ConcurrentHashMap<>();
    private TerrainGenerator generator;
    private final ChunkLoader loader = new ChunkLoader();
    
    /** Backing store; null for a world that is never written to disk. */
    private com.voxelgame.world.save.WorldSave save;
    
    public void setSave(com.voxelgame.world.save.WorldSave save) { this.save = save; }
    public com.voxelgame.world.save.WorldSave getSave() { return save; }

    /**
     * [GP-073] Set the cell on fire and start tracking it. Only empty cells
     * with a solid support underneath ignite, so fire never floats.
     */
    public void igniteFire(int x, int y, int z) {
        if (!canFireStand(x, y, z)) return;
        if (getBlock(x, y, z) != BlockType.AIR.id) return;
        setBlock(x, y, z, BlockType.FIRE.id);
        addFire(x, y, z);
    }

    /** Register a fire cell that is already set in the world. */
    private void addFire(int x, int y, int z) {
        int life = FIRE_MIN_LIFE + (int) (java.lang.Math.random() * (FIRE_MAX_LIFE - FIRE_MIN_LIFE));
        fireBlocks.add(new FireBlock(x, y, z, life));
    }

    /** A fire needs a non-transparent block underneath to stand on. */
    private boolean canFireStand(int x, int y, int z) {
        int below = getBlock(x, y - 1, z);
        if (below == BlockType.AIR.id) return false;
        if (below == BlockType.WATER.id || below == BlockType.LAVA.id) return false;
        return !BlockType.isTransparentFast(below);
    }

    /** True when any of the six neighbours of (x,y,z) is flammable. */
    private boolean hasFlammableNeighbor(int x, int y, int z) {
        return BlockType.isFlammable(getBlock(x + 1, y, z))
            || BlockType.isFlammable(getBlock(x - 1, y, z))
            || BlockType.isFlammable(getBlock(x, y + 1, z))
            || BlockType.isFlammable(getBlock(x, y - 1, z))
            || BlockType.isFlammable(getBlock(x, y, z + 1))
            || BlockType.isFlammable(getBlock(x, y, z - 1));
    }

    /**
     * [GP-074] Fire life cycle. Called once per frame.
     *
     * Fire burns out over time (embers have a second chance), is put out by
     * water and by rain, and spreads to nearby flammable blocks. TNT hit by
     * fire is primed and its fuse starts.
     */
    public void updateFire(float dt, String weather) {
        if (fireBlocks.isEmpty()) return;

        boolean raining = weather != null
            && (weather.equals("rain") || weather.equals("thunder"));

        fireSpreadTimer += dt;
        boolean trySpread = fireSpreadTimer >= FIRE_SPREAD_INTERVAL;
        if (trySpread) fireSpreadTimer = 0;

        java.util.Iterator<FireBlock> it = fireBlocks.iterator();
        while (it.hasNext()) {
            FireBlock fb = it.next();

            // The cell no longer holds fire (broken / replaced by water etc.)
            if (getBlock(fb.x, fb.y, fb.z) != BlockType.FIRE.id) {
                it.remove();
                continue;
            }

            fb.life--;

            // Water adjacent douses the flame outright
            if (getBlock(fb.x + 1, fb.y, fb.z) == BlockType.WATER.id
                || getBlock(fb.x - 1, fb.y, fb.z) == BlockType.WATER.id
                || getBlock(fb.x, fb.y + 1, fb.z) == BlockType.WATER.id
                || getBlock(fb.x, fb.y - 1, fb.z) == BlockType.WATER.id
                || getBlock(fb.x, fb.y, fb.z + 1) == BlockType.WATER.id
                || getBlock(fb.x, fb.y, fb.z - 1) == BlockType.WATER.id) {
                setBlock(fb.x, fb.y, fb.z, (byte) 0);
                it.remove();
                continue;
            }

            // Rain slowly kills fires
            if (raining && java.lang.Math.random() < 0.20f) {
                setBlock(fb.x, fb.y, fb.z, (byte) 0);
                it.remove();
                continue;
            }

            // Out of life: usually dies, sometimes lingers as an ember
            if (fb.life <= 0) {
                if (java.lang.Math.random() < 0.5f) {
                    setBlock(fb.x, fb.y, fb.z, (byte) 0);
                    it.remove();
                } else {
                    fb.life = 300;
                }
                continue;
            }

            if (trySpread) {
                trySpreadFire(fb.x, fb.y, fb.z);
            }
        }
    }

    /**
     * [GP-074] One fire cell tries to catch neighbours alight. Checks the
     * six adjacent cells; a flammable block catches fire in place, an air
     * cell above/next to flammables becomes fire. TNT is primed instead.
     */
    private void trySpreadFire(int x, int y, int z) {
        int[][] dirs = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
        };
        int ignited = 0;

        // Shuffle the order so spread is not biased to one direction
        for (int i = dirs.length - 1; i > 0; i--) {
            int j = (int) (java.lang.Math.random() * (i + 1));
            int[] tmp = dirs[i]; dirs[i] = dirs[j]; dirs[j] = tmp;
        }

        for (int[] d : dirs) {
            if (ignited >= 2) break;

            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            int b = getBlock(nx, ny, nz);
            if (b == BlockType.AIR.id) continue;

            // Fire touching TNT primes it
            if (b == BlockType.TNT.id) {
                primeTnt(nx, ny, nz);
                continue;
            }

            if (!BlockType.isFlammable(b)) continue;

            if (java.lang.Math.random() < BlockType.flammability(b)) {
                setBlock(nx, ny, nz, BlockType.FIRE.id);
                addFire(nx, ny, nz);
                ignited++;
            }
        }

        // Also ignite empty cells sitting on top of flammables, so fire
        // climbs wooden walls and jumps between log piles
        if (ignited < 2) {
            int upX = x, upY = y + 1, upZ = z;
            if (getBlock(upX, upY, upZ) == BlockType.AIR.id
                && canFireStand(upX, upY, upZ)
                && hasFlammableNeighbor(upX, upY, upZ)
                && java.lang.Math.random() < 0.4f) {
                setBlock(upX, upY, upZ, BlockType.FIRE.id);
                addFire(upX, upY, upZ);
            }
        }
    }

    /** Fire sets off a TNT block: prime it and remove the block. */
    private void primeTnt(int x, int y, int z) {
        setBlock(x, y, z, (byte) 0);
        tntEntities.add(new TntEntity(this, x + 0.5f, y + 0.5f, z + 0.5f));
    }

    /** [GP-022] Tick all falling blocks, land the ones that hit ground. */
    public void updateFallingBlocks(float dt) {
        if (fallingBlocks.isEmpty()) return;
        java.util.Iterator<FallingBlockEntity> it = fallingBlocks.iterator();
        while (it.hasNext()) {
            FallingBlockEntity fb = it.next();
            fb.update(dt);
            if (fb.isDead()) it.remove();
        }
    }

    /** [AST] Tick every falling asteroid; remove the ones that impacted. */
    public void updateAsteroids(float dt, Player player) {
        if (asteroids.isEmpty()) return;
        java.util.Iterator<AsteroidEntity> it = asteroids.iterator();
        while (it.hasNext()) {
            AsteroidEntity a = it.next();
            a.update(dt, player);
            if (a.isDead()) it.remove();
        }
    }

    /**
     * [AST] Drop an asteroid from high above (x, z). The spawn height comes
     * from the terrain generator, so it works even over unloaded chunks.
     */
    public void spawnAsteroidAt(float x, float z, int size) {
        int hx = (int) java.lang.Math.floor(x);
        int hz = (int) java.lang.Math.floor(z);
        float height = generator.getHeight(hx, hz);
        asteroids.add(new AsteroidEntity(this, x, height + 150.0f, z, size));
    }
    
    /** Loose items waiting to be picked up. */
    private final List<ItemEntity> itemEntities = new ArrayList<>();
    public List<ItemEntity> getItemEntities() { return itemEntities; }

    /** Hostile mobs in the world. */
    private final List<Zoloy> mobs = new ArrayList<>();
    public List<Zoloy> getMobs() { return mobs; }

    /** Peaceful villagers living in villages. */
    private final List<Villager> villagers = new ArrayList<>();
    public List<Villager> getVillagers() { return villagers; }

    /** [GP-045] Farm animals: cows, pigs, chickens, sheep. */
    private final List<Animal> animals = new ArrayList<>();
    public List<Animal> getAnimals() { return animals; }

    /**
     * [SD] Sliding doors in the loaded area, keyed "x,y,z" of the BOTTOM
     * cell. State changes here persist through the chunk's door meta.
     */
    private final java.util.Map<String, SlidingDoor> slidingDoors = new java.util.HashMap<>();
    public java.util.Collection<SlidingDoor> getSlidingDoors() { return slidingDoors.values(); }

    /** Primed TNT blocks with a ticking fuse. */
    private final List<TntEntity> tntEntities = new ArrayList<>();
    public List<TntEntity> getTntEntities() { return tntEntities; }

    // [GP-073] One burning cell. Life is in 60fps ticks: 10..30 seconds.
    public static final class FireBlock {
        public final int x, y, z;
        public int life;
        FireBlock(int x, int y, int z, int life) {
            this.x = x; this.y = y; this.z = z; this.life = life;
        }
    }

    /** All currently burning cells, so fire ticks don't scan the world. */
    private final List<FireBlock> fireBlocks = new ArrayList<>();
    public List<FireBlock> getFireBlocks() { return fireBlocks; }

    /** Spread accumulator: fire tries to spread about once per second. */
    private float fireSpreadTimer = 0;
    private static final float FIRE_SPREAD_INTERVAL = 1.0f;
    private static final int FIRE_MIN_LIFE = 600;    // 10 s
    private static final int FIRE_MAX_LIFE = 1800;   // 30 s

    /** [GP-022] Sand/gravel currently tumbling down. */
    private final List<FallingBlockEntity> fallingBlocks = new ArrayList<>();
    public List<FallingBlockEntity> getFallingBlocks() { return fallingBlocks; }

    /** [AST] Burning asteroids currently falling from the sky. */
    private final List<AsteroidEntity> asteroids = new ArrayList<>();
    public List<AsteroidEntity> getAsteroids() { return asteroids; }

    /** The player, used for explosion damage and mob despawn checks. */
    private Player playerRef = null;
    public void setPlayer(Player player) { this.playerRef = player; }

    /** Water simulation for flowing water. */
    private WaterSimulation waterSimulation;

    /** Coordinates of the most recent successful block placement (for co-op sync). */
    private int lastPlacedX = Integer.MIN_VALUE;
    private int lastPlacedY = Integer.MIN_VALUE;
    private int lastPlacedZ = Integer.MIN_VALUE;

    /** Village generator вЂ” places full villages with buildings, roads, and villagers. */
    private final VillageGenerator villageGenerator;

    /** Container manager for chests, furnaces, etc. */
    private final ContainerManager containerManager = new ContainerManager() {{
        setWorld(World.this);
    }};

    /**
     * Write every modified chunk. Called on autosave and on exit.
     *
     * @return how many chunks were written
     */
    public int flushToDisk() {
        if (save == null) return 0;

        int written = 0;
        for (Chunk chunk : chunks.values()) {
            if (chunk.isModified()) {
                // [CF] Containers inside the chunk travel with its save file
                save.saveChunk(chunk,
                    containerManager.getContainersInChunk(
                        chunk.getChunkX(), chunk.getChunkZ()));
                chunk.clearModified();
                written++;
            }
        }
        return written;
    }

    /**
     * Snapshot all modified chunks for background saving.
     * This copies the block arrays and metadata on the main thread so the
     * background thread only does I/O — avoiding data races with chunk edits.
     *
     * @return immutable list of chunk snapshots, empty if no save is set
     */
    public java.util.List<com.voxelgame.world.save.WorldSave.ChunkSnapshot> snapshotDirtyChunks() {
        java.util.List<com.voxelgame.world.save.WorldSave.ChunkSnapshot> out =
            new java.util.ArrayList<>();
        if (save == null) return out;
        for (Chunk chunk : chunks.values()) {
            if (!chunk.isModified()) continue;
            java.util.List<int[]> doorList = chunk.doorMetaEntries();
            java.util.List<int[]> cropList = chunk.cropMetaEntries();
            java.util.List<int[]> waterList = chunk.waterMetaEntries();
            java.util.List<Object[]> rawContainers = containerManager.getContainersInChunk(
                chunk.getChunkX(), chunk.getChunkZ());
            Object[][] containers = new Object[rawContainers.size()][];
            for (int i = 0; i < rawContainers.size(); i++) {
                Object[] c = rawContainers.get(i);
                com.voxelgame.world.container.ContainerData cd =
                    (com.voxelgame.world.container.ContainerData) c[3];
                containers[i] = new Object[] {
                    c[0], c[1], c[2],
                    cd.type.ordinal(),
                    cd.serialize()
                };
            }
            out.add(new com.voxelgame.world.save.WorldSave.ChunkSnapshot(
                chunk.getChunkX(), chunk.getChunkZ(),
                chunk.getBlocks().clone(),  // shallow copy of the array (short elements are immutable)
                doorList.toArray(new int[0][]),
                cropList.toArray(new int[0][]),
                waterList.toArray(new int[0][]),
                containers
            ));
            chunk.clearModified();
        }
        return out;
    }
    
    /** Reused request scratch so streaming allocates nothing per frame. */
    private final List<long[]> pending = new ArrayList<>();

    /** [UI-009] Chunk generations still in flight on the worker threads. */
    private int pendingRequests = 0;

    /** [UI-009] True while the world is still filling in around the player. */
    public boolean isLoadingChunks() {
        return pendingRequests > 0;
    }
    
    /** Number of chunks currently held in memory (loaded or generated). */
    public int getLoadedChunkCount() {
        return chunks.size();
    }

    /** Number of chunks inside the render-distance circle around the player. */
    public int getTargetChunkCount() {
        int r = renderDistance;
        int count = 0;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z <= r * r) count++;
            }
        }
        return count;
    }

    /** 0..1 how much of the render-distance circle is filled in. */
    public float getChunkLoadProgress() {
        int target = getTargetChunkCount();
        if (target <= 0) return 1.0f;
        return java.lang.Math.min(1.0f, chunks.size() / (float) target);
    }
    
    private int renderDistance = 8;
    
    public World() {
        generator = new TerrainGenerator(System.currentTimeMillis());
        waterSimulation = new WaterSimulation(this);
        villageGenerator = new VillageGenerator(generator.getSeed(), generator);
    }

    public World(long seed) {
        generator = new TerrainGenerator(seed);
        waterSimulation = new WaterSimulation(this);
        villageGenerator = new VillageGenerator(seed, generator);
    }

    /**
     * РСЃРєР°С‚СЊ Р±Р»РёР¶Р°Р№С€СѓСЋ РґРµСЂРµРІРЅСЋ Рє С‚РѕС‡РєРµ (px, pz). РСЃРїРѕР»СЊР·СѓРµС‚ С‚Сѓ Р¶Рµ
     * РґРµС‚РµСЂРјРёРЅРёСЂРѕРІР°РЅРЅСѓСЋ СЃРµС‚РєСѓ, С‡С‚Рѕ Рё РіРµРЅРµСЂР°С‚РѕСЂ РґРµСЂРµРІРµРЅСЊ, РїРѕСЌС‚РѕРјСѓ РґРµСЂРµРІРЅСЏ
     * Р±СѓРґРµС‚ СЃРіРµРЅРµСЂРёСЂРѕРІР°РЅР° РїСЂРё Р·Р°РіСЂСѓР·РєРµ СЌС‚РёС… С‡Р°РЅРєРѕРІ.
     *
     * @return {x, y, z} С‚РѕС‡РєРё С‚РµР»РµРїРѕСЂС‚Р°С†РёРё (С†РµРЅС‚СЂ РґРµСЂРµРІРЅРё, С‡СѓС‚СЊ РЅР°Рґ Р·РµРјР»С‘Р№)
     *         РёР»Рё null, РµСЃР»Рё РїРѕР±Р»РёР·РѕСЃС‚Рё РґРµСЂРµРІРµРЅСЊ РЅРµС‚
     */
    public float[] findNearestVillage(float px, float pz) {
        int pcx = (int) java.lang.Math.floor(px / Chunk.SIZE);
        int pcz = (int) java.lang.Math.floor(pz / Chunk.SIZE);

        int[] vc = villageGenerator.nearestVillageChunk(pcx, pcz, 4);
        if (vc == null) return null;

        int cx = vc[0] * Chunk.SIZE + 8;
        int cz = vc[1] * Chunk.SIZE + 8;
        int cy = generator.getHeight(cx, cz);
        if (cy < TerrainGenerator.SEA_LEVEL + 2) return null; // РїРѕРґ РІРѕРґРѕР№ вЂ” РґРµСЂРµРІРЅРё РЅРµС‚

        return new float[]{cx + 0.5f, cy + 3.0f, cz + 0.5f};
    }
    
    /**
     * Stream chunks around the player.
     *
     * Requests are dispatched to background workers nearest-first and
     * published a couple per frame, so crossing a chunk boundary no longer
     * blocks the render thread while 17 chunks are generated inline.
     */
    public void update(Vector3f playerPos) {
        int playerChunkX = (int) java.lang.Math.floor(playerPos.x / Chunk.SIZE);
        int playerChunkZ = (int) java.lang.Math.floor(playerPos.z / Chunk.SIZE);
        
        // Publish whatever the workers finished
        int published = 0;
        for (Chunk chunk : loader.collect(chunks)) {
            published++;
            // Apply structure blocks and loot deferred from the worker threads
            applyPendingStructureWrites(chunk);
            // Fresh chunks light+mesh from scratch; queue them themselves
            markRemeshChunk(chunk);
            // Neighbours must re-light and re-mesh now this one exists
            markNeighboursDirty(chunk.getChunkX(), chunk.getChunkZ());

            // Villages spawn villagers, so they stay on the main thread
            if (generator.isStructuresEnabled()) {
                if (villageGenerator.shouldGenerateVillage(chunk.getChunkX(), chunk.getChunkZ())) {
                    villageGenerator.generateVillage(chunk, chunks, this);
                }
                markRemeshChunk(chunk);
            }

            // [SD] Restore sliding-door state saved with this chunk
            syncDoorsForChunk(chunk);
        }
        // [UI-009] Keep an in-flight count so the HUD can show "loading"
        pendingRequests = java.lang.Math.max(0, pendingRequests - published);
        
        // Request missing chunks, closest first so the view fills outward
        pending.clear();
        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                int distSq = x * x + z * z;
                if (distSq > renderDistance * renderDistance) continue;
                
                int cx = playerChunkX + x;
                int cz = playerChunkZ + z;
                if (chunks.containsKey(Chunk.key(cx, cz))) continue;
                
                pending.add(new long[]{distSq, cx, cz});
            }
        }
        pending.sort((a, b) -> Long.compare(a[0], b[0]));
        
        // Limit chunk requests per frame to prevent stuttering
        int maxRequestsPerFrame = 2;
        int requested = 0;
        for (long[] req : pending) {
            if (requested >= maxRequestsPerFrame) break;
            int cx = (int) req[1];
            int cz = (int) req[2];
            
            // A chunk the player edited is restored from disk; everything
            // else is cheaper to regenerate than to read back
            if (save != null && save.hasChunk(cx, cz)) {
                Chunk stored = save.loadChunk(cx, cz);
                if (stored != null) {
                    chunks.put(Chunk.key(cx, cz), stored);
                    markRemeshChunk(stored);
                    markNeighboursDirty(cx, cz);
                    syncDoorsForChunk(stored);
                    // [CF] Containers restored from disk rejoin the manager
                    java.util.List<Object[]> pending = stored.takePendingContainers();
                    if (pending != null) {
                        int wx0 = cx << 4;
                        int wz0 = cz << 4;
                        for (Object[] c : pending) {
                            containerManager.restore(
                                wx0 + (Integer) c[0],
                                (Integer) c[1],
                                wz0 + (Integer) c[2],
                                (com.voxelgame.world.container.ContainerData) c[3]);
                        }
                    }
                    requested++;
                    continue;
                }
            }
            
            loader.request(cx, cz, generator);
            pendingRequests++;
            requested++;
        }
        
        // Unload distant chunks, flushing any edits first
        int unloadRadius = (renderDistance + 2) * (renderDistance + 2);
        chunks.entrySet().removeIf(entry -> {
            Chunk chunk = entry.getValue();
            int dx = chunk.getChunkX() - playerChunkX;
            int dz = chunk.getChunkZ() - playerChunkZ;
            
            if (dx * dx + dz * dz <= unloadRadius) return false;

            // [SD] Doors leave with their chunk; meta is already in the save
            dropDoorsForChunk(chunk);
            
            if (save != null && chunk.isModified()) {
                save.saveChunk(chunk,
                    containerManager.getContainersInChunk(
                        chunk.getChunkX(), chunk.getChunkZ()));
            }
            return true;
        });

        // [SD] Animation, villager triggers and auto-close
        updateSlidingDoors(playerPos);
    }
    
    /** A new chunk changes its neighbours' lighting and border faces. */
    private void markNeighboursDirty(int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Chunk n = chunks.get(Chunk.key(cx + dx, cz + dz));
                if (n != null) {
                    markDirtyChunk(n);
                }
            }
        }
    }
    
    public ChunkLoader getLoader() { return loader; }
    
    public void preloadChunks(Vector3f center, int radius) {
        int cx = (int) java.lang.Math.floor(center.x / Chunk.SIZE);
        int cz = (int) java.lang.Math.floor(center.z / Chunk.SIZE);
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    int chunkX = cx + x;
                    int chunkZ = cz + z;
                    long key = Chunk.key(chunkX, chunkZ);
                    if (!chunks.containsKey(key)) {
                        Chunk chunk = new Chunk(chunkX, chunkZ);
                        generator.generate(chunk);
                        chunks.put(key, chunk);
                        markRemeshChunk(chunk);
                    }
                }
            }
        }
    }
    
    /** Last-resolved chunk for spatially-coherent access (mesher, raycast). */
    private Chunk lastChunk = null;
    private long lastChunkKey = Long.MIN_VALUE;

    public int getBlock(int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        Chunk chunk;
        if (key == lastChunkKey) {
            chunk = lastChunk;
        } else {
            chunk = chunks.get(key);
            if (chunk == null) return 0;
            lastChunkKey = key;
            lastChunk = chunk;
        }
        return chunk.getBlock(x & 15, y, z & 15);
    }
    
    public void setBlock(int x, int y, int z, int id) {
        int cx = x >> 4;
        int cz = z >> 4;
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        Chunk chunk;
        if (key == lastChunkKey) {
            chunk = lastChunk;
        } else {
            chunk = chunks.get(key);
            if (chunk == null) return;
            lastChunkKey = key;
            lastChunk = chunk;
        }
        int lx = x & 15;
        int lz = z & 15;
        int oldId = chunk.getBlock(lx, y, lz);
        chunk.setBlock(lx, y, lz, id);
        chunk.markModified();

        // [SD] Sliding doors occupy TWO cells: placing the bottom half
        // installs the top; clearing either half removes the whole door.
        if (id == BlockType.SLIDING_DOOR.id && oldId != BlockType.SLIDING_DOOR.id) {
            if (!installSlidingDoor(x, y, z)) {
                // No room for the top cell: cancel the placement entirely
                chunk.setBlock(lx, y, lz, (byte) 0);
                markDirty(cx, cz);
            }
        } else if (id != BlockType.SLIDING_DOOR.id && oldId == BlockType.SLIDING_DOOR.id) {
            removeSlidingDoor(x, y, z);
        }

        // Incremental light update for this single block change
        LightEngine.updateBlock(this, x, y, z, id);

        // [GP-022] The cell above may have lost its support and start falling
        if (id == BlockType.AIR.id || id == BlockType.WATER.id) {
            maybeFall(x, y + 1, z);
        } else if (isFallingBlock(id)) {
            // A block placed with empty space below tumbles down
            maybeFall(x, y, z);
        }

        // Mark neighboring chunks dirty if on edge
        if (lx == 0) markDirty(cx - 1, cz);
        if (lx == 15) markDirty(cx + 1, cz);
        if (lz == 0) markDirty(cx, cz - 1);
        if (lz == 15) markDirty(cx, cz + 1);
    }

    // === [CR] Crop growth ===

    /** Growth stage (0-7) of a crop at world coords, or -1 when unmanaged. */
    public int getCropStage(int x, int y, int z) {
        Chunk chunk = getChunk(x >> 4, z >> 4);
        if (chunk == null) return -1;
        return chunk.getCropStage(x & 15, y, z & 15);
    }

    // === [WQ] Water levels ===

    /** Water level (0-7) at world coords; 0 = source / static water. */
    public int getWaterLevel(int x, int y, int z) {
        if (y < 0 || y >= Chunk.HEIGHT) return 0;
        Chunk chunk = getChunk(x >> 4, z >> 4);
        if (chunk == null) return 0;
        return chunk.getWaterLevel(x & 15, y, z & 15);
    }

    /** True when the cell has an explicit flow level (reflowable water). */
    public boolean hasWaterMeta(int x, int y, int z) {
        if (y < 0 || y >= Chunk.HEIGHT) return false;
        Chunk chunk = getChunk(x >> 4, z >> 4);
        if (chunk == null) return false;
        return chunk.hasWaterMeta(x & 15, y, z & 15);
    }

    /**
     * Store a water level at world coords; 0 clears the entry, turning the
     * cell into static source water. The chunk is re-meshed.
     */
    public void setWaterLevel(int x, int y, int z, int level) {
        if (y < 0 || y >= Chunk.HEIGHT) return;
        int cx = x >> 4;
        int cz = z >> 4;
        Chunk chunk = getChunk(cx, cz);
        if (chunk == null) return;
        int lx = x & 15;
        int lz = z & 15;
        chunk.setWaterLevel(lx, y, lz, level);
        chunk.markModified();
        markRemeshChunk(chunk);

        // Flowing water surfaces cross chunk borders: neighbours re-mesh too
        if (lx == 0) markDirty(cx - 1, cz);
        if (lx == 15) markDirty(cx + 1, cz);
        if (lz == 0) markDirty(cx, cz - 1);
        if (lz == 15) markDirty(cx, cz + 1);
    }

    /** Store a crop growth stage at world coords; the chunk is re-meshed. */
    public void setCropStage(int x, int y, int z, int stage) {
        Chunk chunk = getChunk(x >> 4, z >> 4);
        if (chunk == null) return;
        chunk.setCropStage(x & 15, y, z & 15, stage);
        chunk.markModified();
        markRemeshChunk(chunk);
    }

    /** Crop growth tick cadence: a pass every few seconds near the player. */
    private static final float CROP_TICK_INTERVAL = 2.0f;
    /** Chunks around the player whose crops get to grow. */
    private static final int CROP_UPDATE_RADIUS = 8;
    private float cropTickAccum = 0.0f;
    /** Pass counter: growth chance varies per pass, not per position. */
    private int cropPass = 0;

    /**
     * [CR] Grows every crop in loaded chunks around the player. Growth is
     * probabilistic per crop (light and soil must be right, water nearby
     * roughly doubles the rate), so a field ripens unevenly. Chunks are
     * re-meshed only when something actually changed.
     */
    public void updateCrops(float dt, int centerCx, int centerCz) {
        cropTickAccum += dt;
        if (cropTickAccum < CROP_TICK_INTERVAL) return;
        cropTickAccum = 0.0f;
        cropPass++;

        for (int cx = centerCx - CROP_UPDATE_RADIUS; cx <= centerCx + CROP_UPDATE_RADIUS; cx++) {
            for (int cz = centerCz - CROP_UPDATE_RADIUS; cz <= centerCz + CROP_UPDATE_RADIUS; cz++) {
                Chunk chunk = getChunk(cx, cz);
                if (chunk == null) continue;
                if (growChunkCrops(chunk)) {
                    markRemeshChunk(chunk);
                    chunk.markModified();
                }
            }
        }
    }

    /** One growth pass over a chunk; true when any crop advanced. */
    private boolean growChunkCrops(Chunk chunk) {
        int ox = chunk.getWorldX();
        int oz = chunk.getWorldZ();
        boolean changed = false;
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = ox + x;
                int wz = oz + z;
                int top = getGroundHeight(wx, wz);
                for (int y = top; y <= top + 1; y++) {
                    int id = chunk.getBlock(x, y, z);
                    if (id != BlockType.WHEAT.id
                            && id != BlockType.CARROT.id
                            && id != BlockType.POTATO.id) continue;
                    if (growCrop(chunk, wx, y, wz, id)) changed = true;
                }
            }
        }
        return changed;
    }

    /** Try to advance one crop; true when its stage changed. */
    private boolean growCrop(Chunk chunk, int wx, int wy, int wz, int id) {
        if (getLight(wx, wy, wz) < 8) return false;

        int below = chunk.getBlock(wx & 15, wy - 1, wz & 15);
        boolean soil = below == BlockType.DIRT.id
                    || below == BlockType.GRASS_BLOCK.id
                    || below == BlockType.PODZOL.id
                    || below == BlockType.COARSE_DIRT.id;
        if (!soil) return false;

        // Chance varies by pass, so every crop eventually grows
        boolean watered = waterNear(wx, wy, wz);
        double chance = watered ? 0.66 : 0.33;
        if (hashCrop(wx, wy, wz, cropPass) >= chance) return false;

        int stage = chunk.getCropStage(wx & 15, wy, wz & 15);
        if (stage < 0) {
            stage = com.voxelgame.rendering.TextureAtlas.stageOf(id, wx, wy, wz);
        }
        if (stage >= 7) return false;

        chunk.setCropStage(wx & 15, wy, wz & 15, stage + 1);
        return true;
    }

    /** Water within a 3-block radius of the crop cell speeds growth. */
    private boolean waterNear(int wx, int wy, int wz) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 10) continue;
                if (getBlock(wx + dx, wy, wz + dz) == BlockType.WATER.id) return true;
            }
        }
        return false;
    }

    private static double hashCrop(int x, int y, int z, int salt) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177 + salt * 1442695040;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= (h >> 16);
        return (h & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }

    /** [GP-022] True for blocks that fall when unsupported. */
    public static boolean isFallingBlock(int id) {
        return id == BlockType.SAND.id
            || id == BlockType.GRAVEL.id
            || id == BlockType.RED_SAND.id;
    }

    /** [GP-022] If the cell holds falling material with air beneath, drop it. */
    private void maybeFall(int x, int y, int z) {
        if (y < 0 || y >= Chunk.HEIGHT) return;
        int b = getBlock(x, y, z);
        if (!isFallingBlock(b)) return;
        int below = getBlock(x, y - 1, z);
        if (below != BlockType.AIR.id
            && below != BlockType.WATER.id
            && below != BlockType.LAVA.id) return;
        setBlock(x, y, z, (byte) 0);
        fallingBlocks.add(new FallingBlockEntity(this, x, y, z, b));
    }
    
    private void markDirty(int cx, int cz) {
        Chunk chunk = chunks.get(Chunk.key(cx, cz));
        if (chunk != null) {
            markDirtyChunk(chunk);
        }
    }

    // ------------------------------------------------------------------
    // [OPT] Dirty-chunk queue.
    //
    // The renderer used to scan the whole chunk map every frame to find
    // work; with a queue it just drains these lists, and a settled world
    // does zero chunk-map iteration per frame.
    // ------------------------------------------------------------------

    private final LinkedHashSet<Chunk> dirtyChunks = new LinkedHashSet<>();

    /** Flags both dirty + lightDirty and queues the chunk for relight+remesh. */
    public void markDirtyChunk(Chunk chunk) {
        chunk.setDirty(true);
        chunk.setLightDirty(true);
        dirtyChunks.add(chunk);
    }

    /** Flags the chunk dirty (remesh only; light already correct). */
    public void markRemeshChunk(Chunk chunk) {
        chunk.setDirty(true);
        dirtyChunks.add(chunk);
    }

    /**
     * Collects every chunk queued since the last call. Returns the freshly
     * queued chunks, or null when nothing is pending.
     */
    public List<Chunk> drainDirtyChunks() {
        if (dirtyChunks.isEmpty()) return null;
        List<Chunk> out = new ArrayList<>(dirtyChunks.size());
        out.addAll(dirtyChunks);
        dirtyChunks.clear();
        return out;
    }
    
    /**
     * Combined light level (sky vs block, whichever is stronger) at world coords.
     * Unloaded chunks and everything above the world count as full daylight.
     */
    public int getLight(int x, int y, int z) {
        if (y >= Chunk.HEIGHT) return Chunk.MAX_LIGHT;
        if (y < 0) return 0;
        
        int cx = x >> 4;
        int cz = z >> 4;
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        Chunk chunk;
        if (key == lastChunkKey) {
            chunk = lastChunk;
        } else {
            chunk = chunks.get(key);
            if (chunk == null) return Chunk.MAX_LIGHT;
            lastChunkKey = key;
            lastChunk = chunk;
        }
        return chunk.getLight(x & 15, y, z & 15);
    }

    /**
     * Sky light (sunlight) channel at world coords. Unloaded chunks and
     * everything above the world count as full daylight, like getLight.
     */
    public int getSkyLight(int x, int y, int z) {
        if (y >= Chunk.HEIGHT) return Chunk.MAX_LIGHT;
        if (y < 0) return 0;

        int cx = x >> 4;
        int cz = z >> 4;
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        Chunk chunk;
        if (key == lastChunkKey) {
            chunk = lastChunk;
        } else {
            chunk = chunks.get(key);
            if (chunk == null) return Chunk.MAX_LIGHT;
            lastChunkKey = key;
            lastChunk = chunk;
        }
        return chunk.getSkyLight(x & 15, y, z & 15);
    }

    /**
     * Block light channel (torches, lava, glowstone) at world coords.
     * Zero outside loaded chunks and beyond the world.
     */
    public int getBlockLight(int x, int y, int z) {
        if (y >= Chunk.HEIGHT || y < 0) return 0;

        int cx = x >> 4;
        int cz = z >> 4;
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        Chunk chunk;
        if (key == lastChunkKey) {
            chunk = lastChunk;
        } else {
            chunk = chunks.get(key);
            if (chunk == null) return 0;
            lastChunkKey = key;
            lastChunk = chunk;
        }
        return chunk.getBlockLight(x & 15, y, z & 15);
    }
    
    public void breakBlock(Vector3f origin, Vector3f direction) {
        Raycast.Hit hit = Raycast.cast(origin, direction, this, 6.0f);
        if (hit != null) {
            int current = getBlock(hit.x, hit.y, hit.z);
            setBlock(hit.x, hit.y, hit.z, 0);

            // [WQ] Water physics: breaking water drains/re-levels the pool;
            // breaking anything else lets touching water pour into the gap.
            if (current == BlockType.WATER.id) {
                waterSimulation.removeWater(hit.x, hit.y, hit.z);
            } else {
                waterSimulation.pourFromNeighbors(hit.x, hit.y, hit.z);
            }
            waterSimulation.processSpread();
        }
    }

    /**
     * Place a block with optional player collision check.
     * [GP-012] Prevents placing blocks inside the player's hitbox.
     * 
     * @param origin ray origin (usually camera position)
     * @param direction ray direction
     * @param blockId block type to place
     * @param playerPos player position for collision check (null to skip check)
     * @param playerWidth player width (usually 0.6f)
     * @param playerHeight player height (usually 1.8f)
     * @return true if block was placed, false if placement was blocked
     */
    public boolean placeBlock(Vector3f origin, Vector3f direction, int blockId,
                              Vector3f playerPos, float playerWidth, float playerHeight) {
        Raycast.Hit hit = Raycast.cast(origin, direction, this, 6.0f);
        if (hit == null) return false;
        
        // [GP-012] Check if placing block would intersect with player
        if (playerPos != null && BlockType.isSolidFast(blockId)) {
            com.voxelgame.physics.AABB blockAABB = new com.voxelgame.physics.AABB(
                hit.placeX, hit.placeY, hit.placeZ,
                1.0f, 1.0f, 1.0f
            );
            
            float halfWidth = playerWidth / 2.0f;
            com.voxelgame.physics.AABB playerAABB = new com.voxelgame.physics.AABB(
                playerPos.x - halfWidth, playerPos.y, playerPos.z - halfWidth,
                playerWidth, playerHeight, playerWidth
            );
            
            if (blockAABB.intersects(playerAABB)) {
                // Cannot place block inside player
                return false;
            }
        }

        // [GP-022] No floating blocks: the placement cell must touch at
        // least one solid block or a liquid, or the block is left hovering
        // in mid-air. This also stops blocks being wedged into hollows.
        if (!hasSupport(hit.placeX, hit.placeY, hit.placeZ)) {
            return false;
        }
        
        // [WQ] Placing a block into water displaces it: the pool drains or
        // re-levels around the new obstacle.
        if (getBlock(hit.placeX, hit.placeY, hit.placeZ) == BlockType.WATER.id) {
            waterSimulation.removeWater(hit.placeX, hit.placeY, hit.placeZ);
        }

        setBlock(hit.placeX, hit.placeY, hit.placeZ, blockId);

        // If water was placed, trigger spreading
        if (blockId == BlockType.WATER.id) {
            waterSimulation.addSource(hit.placeX, hit.placeY, hit.placeZ);
        }
        waterSimulation.processSpread();

        // [GP-073] Player-placed fire starts ticking and spreading
        if (blockId == BlockType.FIRE.id) {
            addFire(hit.placeX, hit.placeY, hit.placeZ);
        }
        
        lastPlacedX = hit.placeX;
        lastPlacedY = hit.placeY;
        lastPlacedZ = hit.placeZ;
        
        return true;
    }
    
    // ------------------------------------------------------------------
    // [SD] Sliding doors
    // ------------------------------------------------------------------

    private static String doorKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /** Door whose bottom cell is (x,y,z), or null. */
    public SlidingDoor getSlidingDoor(int x, int y, int z) {
        return slidingDoors.get(doorKey(x, y, z));
    }

    /**
     * Door covering the cell (x,y,z) вЂ” either half resolves to the bottom.
     */
    public SlidingDoor getSlidingDoorAt(int x, int y, int z) {
        SlidingDoor d = slidingDoors.get(doorKey(x, y, z));
        if (d != null) return d;
        return slidingDoors.get(doorKey(x, y - 1, z));
    }

    /** Block lookup that works through an arbitrary chunk map. */
    private static int blockAt(java.util.Map<Long, Chunk> chunkMap, int x, int y, int z) {
        Chunk c = chunkMap.get(Chunk.key(x >> 4, z >> 4));
        return c == null ? 0 : c.getBlock(x & 15, y, z & 15);
    }

    private static boolean solidAt(java.util.Map<Long, Chunk> chunkMap, int x, int y, int z) {
        int b = blockAt(chunkMap, x, y, z);
        return b != 0 && BlockType.fromId(b).solid;
    }

    /**
     * Install a sliding door: bottom cell (x,y,z), top cell auto-created at
     * (x,y+1,z). If the top cell is not free the whole placement is
     * cancelled. Axis and slide direction come from the solid neighbours.
     * Works through any chunk map (village generator may not have all
     * chunks published yet).
     */
    public boolean installSlidingDoor(java.util.Map<Long, Chunk> chunkMap, int x, int y, int z) {
        if (y < 0 || y + 1 >= Chunk.HEIGHT) return false;
        if (blockAt(chunkMap, x, y, z) != 0) return false;
        if (blockAt(chunkMap, x, y + 1, z) != 0) return false;

        // [SD] diagnostic
        System.out.println("[SD] install attempt " + x + "," + y + "," + z
            + " worldRef?" + (chunkMap == chunks));

        // Axis: walls along X mean the panel normal is Z; walls along Z
        // mean the normal is X. Default Z when both/neither.
        boolean xWall = solidAt(chunkMap, x - 1, y, z) || solidAt(chunkMap, x + 1, y, z);
        boolean zWall = solidAt(chunkMap, x, y, z - 1) || solidAt(chunkMap, x, y, z + 1);
        int axis = (zWall && !xWall) ? 1 : 0;

        // Slide toward the solid side when there is one, so the panel hides
        // in the wall. Double doors thus slide away from each other.
        int slideSign = 0;
        if (axis == 0) {
            if (solidAt(chunkMap, x - 1, y, z)) slideSign = 1;
            else if (solidAt(chunkMap, x + 1, y, z)) slideSign = 0;
            else slideSign = 0;
        } else {
            if (solidAt(chunkMap, x, y, z - 1)) slideSign = 1;
            else if (solidAt(chunkMap, x, y, z + 1)) slideSign = 0;
            else slideSign = 0;
        }

        SlidingDoor door = new SlidingDoor(x, y, z, axis, slideSign);
        int id = BlockType.SLIDING_DOOR.id;
        Chunk c = chunkMap.get(Chunk.key(x >> 4, z >> 4));
        if (c == null) return false;
        c.setBlock(x & 15, y, z & 15, id);
        c.setBlock(x & 15, y + 1, z & 15, id);
        c.setDoorMeta(x & 15, y, z & 15, door.toMeta());
        slidingDoors.put(doorKey(x, y, z), door);

        if (chunkMap == chunks) {
            // Player path: keep light and neighbours in sync for both cells
            LightEngine.updateBlock(this, x, y + 1, z, id);
            markNeighboursDirty(x >> 4, z >> 4);
        }
        return true;
    }

    /** Player-facing install through the world's own chunk map. */
    public boolean installSlidingDoor(int x, int y, int z) {
        return installSlidingDoor(chunks, x, y, z);
    }

    /**
     * Remove a sliding door entirely when either half is cleared. Both
     * cells and the meta are dropped in one go.
     */
    public void removeSlidingDoor(int x, int y, int z) {
        SlidingDoor d = getSlidingDoorAt(x, y, z);
        if (d == null) return;
        Chunk c = chunks.get(Chunk.key(d.x >> 4, d.z >> 4));
        if (c != null) {
            c.setBlock(d.x & 15, d.y, d.z & 15, (byte) 0);
            c.setBlock(d.x & 15, d.y + 1, d.z & 15, (byte) 0);
            c.setDoorMeta(d.x & 15, d.y, d.z & 15, (byte) 0);
            c.markModified();
            markDirtyChunk(c);
        }
        LightEngine.updateBlock(this, d.x, d.y, d.z, (byte) 0);
        LightEngine.updateBlock(this, d.x, d.y + 1, d.z, (byte) 0);
        slidingDoors.remove(doorKey(d.x, d.y, d.z));
        markNeighboursDirty(d.x >> 4, d.z >> 4);
    }

    /** Persist the door's state into the chunk's door meta. */
    private void persistDoorState(SlidingDoor d) {
        Chunk c = chunks.get(Chunk.key(d.x >> 4, d.z >> 4));
        if (c == null) return;
        c.setDoorMeta(d.x & 15, d.y, d.z & 15, d.toMeta());
        c.markModified();
        markRemeshChunk(c);
    }

    /**
     * Player click on either half: reverse the door. Returns true when a
     * door was toggled.
     */
    public boolean toggleSlidingDoor(int x, int y, int z) {
        SlidingDoor d = getSlidingDoorAt(x, y, z);
        if (d == null) return false;
        if (d.isPassable()) d.startClose();
        else d.startOpen();
        persistDoorState(d);
        com.voxelgame.audio.AudioManager.play("sounds/steps/wood", 1.0f, 0.5f);
        return true;
    }

    /** Rebuild the door registry for a freshly published/loaded chunk. */
    public void syncDoorsForChunk(Chunk chunk) {
        for (int[] e : chunk.doorMetaEntries()) {
            int wx = chunk.getWorldX() + e[0];
            int wy = e[1];
            int wz = chunk.getWorldZ() + e[2];
            if (getBlock(wx, wy, wz) != BlockType.SLIDING_DOOR.id
                || getBlock(wx, wy + 1, wz) != BlockType.SLIDING_DOOR.id) {
                continue; // stale meta, e.g. a door that was broken
            }
            // [MC] Villages now use vanilla oak doors: migrate any sliding
            // door blocks found in older saves once, on first load
            chunk.setBlock(wx & 15, wy, wz & 15, BlockType.OAK_DOOR.id);
            chunk.setBlock(wx & 15, wy + 1, wz & 15, BlockType.OAK_DOOR.id);
            markRemeshChunk(chunk);
        }
    }

    /** Forget doors belonging to a chunk being unloaded. */
    private void dropDoorsForChunk(Chunk chunk) {
        int cx = chunk.getChunkX();
        int cz = chunk.getChunkZ();
        slidingDoors.entrySet().removeIf(e -> {
            SlidingDoor d = e.getValue();
            return (d.x >> 4) == cx && (d.z >> 4) == cz;
        });
    }

    private long lastDoorTick = 0;

    /**
     * Step door animation, villager triggers and auto-close. Called every
     * world tick from {@link #update(Vector3f)}.
     */
    public void updateSlidingDoors(Vector3f playerPos) {
        if (slidingDoors.isEmpty()) return;
        long now = System.nanoTime();
        float dt = lastDoorTick == 0 ? 1f / 60f : java.lang.Math.min(0.1f, (now - lastDoorTick) / 1e9f);
        lastDoorTick = now;

        java.util.Iterator<java.util.Map.Entry<String, SlidingDoor>> it =
            slidingDoors.entrySet().iterator();
        while (it.hasNext()) {
            SlidingDoor d = it.next().getValue();
            if (getBlock(d.x, d.y, d.z) != BlockType.SLIDING_DOOR.id
                || getBlock(d.x, d.y + 1, d.z) != BlockType.SLIDING_DOOR.id) {
                it.remove();
                continue;
            }

            // A villager within one block of either half opens the door
            // and refreshes the auto-close timer (last trigger wins).
            boolean villagerNear = false;
            for (Villager v : villagers) {
                Vector3f p = v.getPosition();
                if (java.lang.Math.abs(p.x - (d.x + 0.5f)) <= 1.0f
                    && java.lang.Math.abs(p.z - (d.z + 0.5f)) <= 1.0f
                    && p.y > d.y - 1 && p.y < d.y + 3) {
                    villagerNear = true;
                    break;
                }
            }
            if (villagerNear) {
                if (!d.isPassable()) {
                    d.startOpen();
                    persistDoorState(d);
                    com.voxelgame.audio.AudioManager.play("sounds/steps/wood", 1.0f, 0.5f);
                } else if (d.getState() == SlidingDoor.STATE_CLOSING) {
                    d.startOpen(); // reverse mid-flight, keeps the animation smooth
                }
                d.setCloseTimer(SlidingDoor.CLOSE_DELAY);
            }

            byte next = d.update(dt);
            if (next != -1) {
                persistDoorState(d);
                if (next == SlidingDoor.STATE_OPENING || next == SlidingDoor.STATE_CLOSING) {
                    com.voxelgame.audio.AudioManager.play("sounds/steps/wood", 1.0f, 0.5f);
                }
            }

            // Auto-close a while after the last trigger, once the doorway
            // is empty; retry every half second while someone is inside.
            if (d.getState() == SlidingDoor.STATE_OPEN && d.getCloseTimer() <= 0) {
                if (doorwayClear(d, playerPos)) {
                    d.startClose();
                    persistDoorState(d);
                    com.voxelgame.audio.AudioManager.play("sounds/steps/wood", 1.0f, 0.5f);
                } else {
                    d.setCloseTimer(SlidingDoor.RETRY_DELAY);
                }
            }
        }
    }

    /** True when no entity (player, villager, mob) is inside the doorway. */
    private boolean doorwayClear(SlidingDoor d, Vector3f playerPos) {
        float minX = d.x - 0.2f, maxX = d.x + 1.2f;
        float minY = d.y - 0.2f, maxY = d.y + 2.2f;
        float minZ = d.z - 0.2f, maxZ = d.z + 1.2f;
        if (playerPos != null
            && playerPos.x > minX - 0.3f && playerPos.x < maxX + 0.3f
            && playerPos.z > minZ - 0.3f && playerPos.z < maxZ + 0.3f
            && playerPos.y > minY && playerPos.y < maxY) {
            return false;
        }
        for (Villager v : villagers) {
            Vector3f p = v.getPosition();
            if (p.x > minX - 0.3f && p.x < maxX + 0.3f
                && p.z > minZ - 0.3f && p.z < maxZ + 0.3f
                && p.y > minY && p.y < maxY) {
                return false;
            }
        }
        for (Zoloy m : mobs) {
            Vector3f p = m.getPosition();
            if (p.x > minX - 0.3f && p.x < maxX + 0.3f
                && p.z > minZ - 0.3f && p.z < maxZ + 0.3f
                && p.y > minY && p.y < maxY) {
                return false;
            }
        }
        return true;
    }

    /**
     * Legacy placeBlock without collision check.
     * @deprecated Use {@link #placeBlock(Vector3f, Vector3f, byte, Vector3f, float, float)} instead
     */
    @Deprecated
    public void placeBlock(Vector3f origin, Vector3f direction, int blockId) {
        placeBlock(origin, direction, blockId, null, 0, 0);
    }
    
    public boolean isSolid(int x, int y, int z) {
        return BlockType.isSolidFast(getBlock(x, y, z));
    }

    /**
     * [GP-002] Solid collision AABB of the block at (x,y,z), or null when
     * the cell is passable. Full blocks fill the whole cell; slabs only
     * their lower half. The result is written into {@code out}
     * ({minX,minY,minZ,maxX,maxY,maxZ}) to avoid per-call allocation.
     */
    public float[] getBlockAabb(int x, int y, int z, float[] out) {
        int id = getBlock(x, y, z);
        if (id == 0) return null;
        BlockType t = BlockType.fromId(id);
        if (t == BlockType.SLIDING_DOOR) {
            // [SD] Thin panel spanning BOTH cells (y .. y+2) while closed.
            // Once the door starts opening, collision is off entirely.
            SlidingDoor d = getSlidingDoorAt(x, y, z);
            if (d == null || d.isPassable()) return null;
            float h = SlidingDoor.PANEL_THICKNESS / 2f;
            if (d.axis == 0) {
                out[0] = x;         out[1] = d.y; out[2] = z - h;
                out[3] = x + 1;     out[4] = d.y + 2; out[5] = z + h;
            } else {
                out[0] = x - h;     out[1] = d.y; out[2] = z;
                out[3] = x + h;     out[4] = d.y + 2; out[5] = z + 1;
            }
            return out;
        }
        if (!BlockType.isSolidFast(id)) return null;
        if (BlockType.isSlab(id)) {
            out[0] = x; out[1] = y; out[2] = z;
            out[3] = x + 1; out[4] = y + 0.5f; out[5] = z + 1;
            return out;
        }
        out[0] = x; out[1] = y; out[2] = z;
        out[3] = x + 1; out[4] = y + 1; out[5] = z + 1;
        return out;
    }

    /**
     * [GP-022] True when a block can be placed at this cell without
     * floating: at least one of the six neighbours is solid ground or a
     * liquid (water/lava carry blocks the way sand does in Minecraft).
     */
    public boolean hasSupport(int x, int y, int z) {
        if (isSolid(x, y - 1, z)) return true;
        if (isSolid(x, y + 1, z)) return true;
        if (isSolid(x - 1, y, z)) return true;
        if (isSolid(x + 1, y, z)) return true;
        if (isSolid(x, y, z - 1)) return true;
        if (isSolid(x, y, z + 1)) return true;
        int b = getBlock(x, y - 1, z);
        if (b == BlockType.WATER.id || b == BlockType.LAVA.id) return true;
        b = getBlock(x, y, z);
        return b == BlockType.WATER.id || b == BlockType.LAVA.id;
    }

    public boolean isTransparent(int x, int y, int z) {
        return BlockType.isTransparentFast(getBlock(x, y, z));
    }
    
    public int getGroundHeight(int x, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        Chunk chunk = chunks.get(key);
        if (chunk == null) return Chunk.SEA_LEVEL;
        return chunk.getHighestBlock(x & 15, z & 15);
    }
    
    /**
     * Find a safe spawn position near the given coordinates.
     * Searches for a position with 2 free blocks above solid ground.
     * If no safe position found, returns the original position with Y pushed up.
     * 
     * @param x suggested X coordinate
     * @param z suggested Z coordinate
     * @return safe spawn position as Vector3f
     */
    public org.joml.Vector3f findSafeSpawnPosition(int x, int z) {
        // First, ensure chunks are loaded
        int groundY = getGroundHeight(x, z);
        
        // Search in a spiral pattern for a safe spot
        for (int radius = 0; radius < 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (java.lang.Math.abs(dx) != radius && java.lang.Math.abs(dz) != radius) continue;
                    
                    int checkX = x + dx;
                    int checkZ = z + dz;
                    int checkGroundY = getGroundHeight(checkX, checkZ);
                    
                    // Check if this position is safe (2 blocks of air above ground)
                    if (isSafeSpawnPosition(checkX, checkGroundY, checkZ)) {
                        return new org.joml.Vector3f(checkX + 0.5f, checkGroundY + 1.0f, checkZ + 0.5f);
                    }
                }
            }
        }
        
        // Fallback: just use ground + 2
        return new org.joml.Vector3f(x + 0.5f, groundY + 2.0f, z + 0.5f);
    }
    
    /**
     * Check if a position is safe for spawning:
     * - Ground block is solid
     * - 2 blocks above are non-solid (air/water)
     * - Not in lava or fire
     */
    public boolean isSafeSpawnPosition(int x, int groundY, int z) {
        // Ground must be solid
        if (!isSolid(x, groundY, z)) return false;
        
        // Check 2 blocks above ground for player height
        for (int y = groundY + 1; y <= groundY + 2; y++) {
            if (y >= Chunk.HEIGHT) return false;
            int block = getBlock(x, y, z);
            // Must be passable (air, water, non-solid blocks)
            if (isSolid(x, y, z)) return false;
            // Avoid dangerous blocks
            if (block == BlockType.LAVA.id) return false;
        }
        
        return true;
    }
    
    /**
     * Push player out of solid blocks if stuck.
     * Called when player collides unexpectedly.
     * 
     * @param position current player position (modified in place)
     * @return true if position was adjusted
     */
    public boolean pushOutOfBlocks(org.joml.Vector3f position) {
        float width = 0.3f; // Half-width of player
        
        // Check if player center is inside a solid block
        int bx = (int) java.lang.Math.floor(position.x);
        int by = (int) java.lang.Math.floor(position.y);
        int bz = (int) java.lang.Math.floor(position.z);
        
        // Check feet and head levels
        for (int checkY = by; checkY <= by + 1; checkY++) {
            if (checkY < 0 || checkY >= Chunk.HEIGHT) continue;
            
            if (isSolid(bx, checkY, bz)) {
                // Player is stuck - push up
                position.y = checkY + 1.01f;
                return true;
            }
        }
        
        // Check horizontal collisions
        int[] offsets = {-1, 1};
        for (int dx : offsets) {
            if (isSolid(bx + dx, by, bz) && isSolid(bx + dx, by + 1, bz)) {
                // Wall on this side - check if we're too close
                float blockEdge = bx + (dx > 0 ? 1.0f : 0.0f);
                float dist = java.lang.Math.abs(position.x - blockEdge);
                if (dist < width) {
                    position.x = blockEdge + dx * width;
                    return true;
                }
            }
        }
        for (int dz : offsets) {
            if (isSolid(bx, by, bz + dz) && isSolid(bx, by + 1, bz + dz)) {
                float blockEdge = bz + (dz > 0 ? 1.0f : 0.0f);
                float dist = java.lang.Math.abs(position.z - blockEdge);
                if (dist < width) {
                    position.z = blockEdge + dz * width;
                    return true;
                }
            }
        }
        
        return false;
    }
    
    public Map<Long, Chunk> getChunks() { return chunks; }
    public int getRenderDistance() { return renderDistance; }
    public TerrainGenerator getGenerator() { return generator; }
    public WaterSimulation getWaterSimulation() { return waterSimulation; }
    public int getLastPlacedX() { return lastPlacedX; }
    public int getLastPlacedY() { return lastPlacedY; }
    public int getLastPlacedZ() { return lastPlacedZ; }
    public ContainerManager getContainerManager() { return containerManager; }

    /**
     * Apply structure blocks and loot that workers deferred to the main
     * thread. Called once when the chunk is published.
     */
    private void applyPendingStructureWrites(Chunk chunk) {
        java.util.List<int[]> writes = chunk.drainPendingWrites();
        if (writes != null) {
            for (int[] w : writes) {
                chunk.setBlock(w[0], w[1], w[2], w[3]);
                if (w[3] == BlockType.MOB_SPAWNER.id) {
                    chunk.addSpawner(w[0], w[1], w[2]);
                }
            }
        }

        java.util.List<int[]> loot = chunk.drainPendingLoot();
        if (loot != null) {
            for (int[] l : loot) {
                // Item ids live above 127, so no byte cast here - (byte) 130
                // wraps negative and getById used to return null, leaving
                // every structure chest empty
                com.voxelgame.item.Item item = com.voxelgame.item.ItemRegistry.getById(l[3]);
                if (item == null) continue;

                com.voxelgame.world.container.ContainerData chest =
                    containerManager.getChest(l[0], l[1], l[2]);
                chest.insertItem(new com.voxelgame.item.ItemStack(item, l[4]));

                // Bonus rolls on top of the guaranteed template loot, seeded
                // by position so a given chest always rolls the same way
                java.util.Random rng = new java.util.Random(
                    31L * l[0] + 1543L * l[1] + 1299709L * l[2]);
                int rolls = 2 + rng.nextInt(3);
                for (com.voxelgame.item.ItemStack stack :
                        com.voxelgame.item.LootTable.STRUCTURE_CHEST.roll(rolls, rng)) {
                    chest.insertItem(stack);
                }
            }
        }

        // Disk-loaded chunks have spawner blocks but no spawner list; scan
        // the column once so dungeons found in old saves keep working.
        if (chunk.getSpawners() == null) {
            chunk.scanSpawners();
        }
    }

    /** Cooldown per spawner cell: {cx << 32 | cz} -> seconds remaining. */
    private final java.util.Map<Long, Float> spawnerTimers = new java.util.HashMap<>();

    /**
     * Mob spawner cores summon a Zoloy while the player is near and the
     * room is dark. Called every tick from the game loop.
     */
    public void updateSpawners(float dt, Player player) {
        if (player == null) return;
        Vector3f p = player.getPosition();
        int px = (int) java.lang.Math.floor(p.x);
        int py = (int) java.lang.Math.floor(p.y);
        int pz = (int) java.lang.Math.floor(p.z);

        // Cap: don't let spawners flood the area around the player
        int nearCount = 0;
        for (Zoloy m : mobs) {
            Vector3f mp = m.getPosition();
            float dx = mp.x - px, dz = mp.z - pz;
            if (dx * dx + dz * dz < 100.0f) nearCount++;
        }
        if (nearCount > 5) return;

        for (Chunk chunk : chunks.values()) {
            java.util.List<int[]> spawners = chunk.getSpawners();
            if (spawners == null || spawners.isEmpty()) continue;
            for (int[] s : spawners) {
                int wx = s[0] + chunk.getWorldX();
                int wz = s[2] + chunk.getWorldZ();
                long key = ((long) wx << 32) | (wz & 0xFFFFFFFFL);
                float timer = spawnerTimers.getOrDefault(key, 0f);
                if (timer > 0) {
                    spawnerTimers.put(key, timer - dt);
                    continue;
                }

                int dx = wx - px, dy = s[1] - py, dz = wz - pz;
                if (dx * dx + dy * dy + dz * dz > 16 * 16) continue;

                // Spawn only in the dark: skylight or block light below 8
                int light = chunk.getLight(s[0], s[1], s[2]);
                if (light >= 8) {
                    spawnerTimers.put(key, 5f);
                    continue;
                }

                spawnerTimers.put(key, 15f + (float) java.lang.Math.random() * 15f);
                mobs.add(new Zoloy(this, s[0] + 0.5f, s[1] + 1.0f, s[2] + 0.5f));
            }
        }
    }

    /** Update all containers (furnace smelting, etc). */
    public void updateContainers() {
        for (ContainerData container : containerManager.getContainers().values()) {
            if (container.type == ContainerData.Type.FURNACE) {
                container.updateFurnace();
            }
        }
    }

    /**
     * Enable or disable structure generation (from WorldMeta).
     */
    public void setStructuresEnabled(boolean enabled) {
        generator.setStructuresEnabled(enabled);
    }
    
    /**
     * Chunk owning a world column, or null when it is not loaded.
     * Used by the light engine to propagate across chunk borders.
     */
    /** Chunk by chunk coordinate, or null when not loaded. */
    public Chunk getChunk(int cx, int cz) {
        return chunks.get(Chunk.key(cx, cz));
    }
    
    public Chunk getChunkAt(int worldX, int worldZ) {
        int cx = (int) java.lang.Math.floor((double) worldX / Chunk.SIZE);
        int cz = (int) java.lang.Math.floor((double) worldZ / Chunk.SIZE);
        return chunks.get(Chunk.key(cx, cz));
    }
    
    /** MC 1.18+ biome at a world column, used by the mesher for foliage tinting. */
    public BiomeSelector.MCBiome getMCBiomeAt(int x, int z) {
        return generator.getMCBiome(x, z);
    }

    /** Data-driven biome at a world column. */
    public com.voxelgame.world.biome.BiomeData getDataBiomeAt(int x, int z) {
        return generator.getDataBiome(x, z);
    }

    /** Localized display name of the biome at a column. */
    public String getBiomeDisplayName(int x, int z) {
        BiomeSelector.MCBiome biome = getMCBiomeAt(x, z);
        return switch (biome) {
            case FROZEN_OCEAN -> "biome.frozen_ocean";
            case DEEP_OCEAN -> "biome.deep_ocean";
            case OCEAN -> "biome.ocean";
            case WARM_OCEAN -> "biome.warm_ocean";
            case LUKEWARM_OCEAN -> "biome.lukewarm_ocean";
            case COLD_OCEAN -> "biome.cold_ocean";
            case MUSHROOM_FIELDS -> "biome.mushroom";
            case BEACH -> "biome.beach";
            case SNOWY_BEACH -> "biome.snowy_beach";
            case STONY_SHORE -> "biome.stony_shore";
            case JAGGED_PEAKS -> "biome.jagged_peaks";
            case FROZEN_PEAKS -> "biome.frozen_peaks";
            case STONY_PEAKS -> "biome.stony_peaks";
            case SNOWY_PLAINS -> "biome.snowy_plains";
            case SNOWY_TAIGA -> "biome.snowy_taiga";
            case GROVE -> "biome.grove";
            case WINDSWEPT_HILLS -> "biome.windswept_hills";
            case WINDSWEPT_FOREST -> "biome.windswept_forest";
            case BADLANDS -> "biome.badlands";
            case WOODED_BADLANDS -> "biome.wooded_badlands";
            case PLATEAU -> "biome.plateau";
            case DESERT -> "biome.desert";
            case SAVANNA -> "biome.savanna";
            case SAVANNA_PLATEAU -> "biome.savanna_plateau";
            case SWAMP -> "biome.swamp";
            case MANGROVE_SWAMP -> "biome.mangrove_swamp";
            case JUNGLE -> "biome.jungle";
            case BAMBOO_JUNGLE -> "biome.bamboo_jungle";
            case FOREST -> "biome.forest";
            case BIRCH_FOREST -> "biome.birch";
            case TAIGA -> "biome.taiga";
            case OLD_GROWTH_PINE_TAIGA -> "biome.giant_tree_taiga";
            case OLD_GROWTH_SPRUCE_TAIGA -> "biome.giant_spruce_taiga";
            case FLOWER_FOREST -> "biome.flower_forest";
            case DARK_FOREST -> "biome.dark_forest";
            case ICE_SPIKES -> "biome.ice_spikes";
            case CRYSTAL_PEAKS -> "biome.crystal";
            case GLACIER -> "biome.glacier";
            case VOLCANO -> "biome.volcano";
            case AUTUMN_FOREST -> "biome.autumn";
            case PETRIFIED_FOREST -> "biome.petrified";
            case MOOR -> "biome.moor";
            case SALT_FLATS -> "biome.salt";
            case ASH_WASTES -> "biome.ash";
            case LAVENDER_MEADOW -> "biome.lavender";
            case SHATTERED_SAVANNA -> "biome.shattered_savanna";
            default -> "biome.plains";
        } + " (" + biome.name() + ")";
    }
    public long getSeed() { return generator.getSeed(); }
    
    /** Keep the loaded radius in sync with the render distance slider. */
    public void setRenderDistance(int chunks) {
        this.renderDistance = java.lang.Math.max(2, chunks);
    }
    
    public void cleanup() {
        flushToDisk();
        loader.shutdown();
        chunks.clear();
        mobs.clear();
        villagers.clear();
        tntEntities.clear();
        fireBlocks.clear();
        fallingBlocks.clear();
        asteroids.clear();
    }

    // --- Mob management ---

    /** Update all mobs (AI + physics). */
    public void updateMobs(float dt, Player player) {
        // [GP-040] Despawn far-away mobs: >128 blocks immediately, >64 with
        // a 10%-per-second random chance so the world thins out naturally
        if (playerRef != null) {
            Vector3f p = playerRef.getPosition();
            mobs.removeIf(mob -> {
                Vector3f m = mob.getPosition();
                float dx = m.x - p.x;
                float dz = m.z - p.z;
                float distSq = dx * dx + dz * dz;
                if (distSq > 128.0f * 128.0f) return true;
                if (distSq > 64.0f * 64.0f && java.lang.Math.random() < 0.1f * dt) return true;
                return false;
            });
        }

        for (Zoloy mob : mobs) {
            mob.update(dt, player);
        }
        mobs.removeIf(mob -> {
            if (mob.isDead()) {
                // [GP-039] Drop loot where the mob fell
                dropMobLoot(mob);
                return true;
            }
            return false;
        });
    }

    /**
     * [GP-039] Scatter a Zoloy's loot around its corpse.
     * Rotten flesh is the bread-and-butter drop; bones, arrows and
     * gunpowder are rarer. Each drop becomes a loose item entity.
     */
    private void dropMobLoot(Zoloy mob) {
        Vector3f p = mob.getPosition();
        if (java.lang.Math.random() < 0.80f) {
            spawnDrop(p, BlockType.ITEM_ROTTEN_FLESH, 1 + (int) (java.lang.Math.random() * 2));
        }
        if (java.lang.Math.random() < 0.25f) {
            spawnDrop(p, BlockType.ITEM_BONE, 1 + (int) (java.lang.Math.random() * 2));
        }
        if (java.lang.Math.random() < 0.20f) {
            spawnDrop(p, BlockType.ITEM_ARROW, 1 + (int) (java.lang.Math.random() * 3));
        }
        if (java.lang.Math.random() < 0.15f) {
            spawnDrop(p, BlockType.ITEM_GUNPOWDER, 1);
        }
        // [POT] Slimy Zoloys occasionally drop slime balls
        if (java.lang.Math.random() < 0.15f) {
            spawnItemDrop(p, com.voxelgame.item.ItemRegistry.SLIME_BALL, 1 + (int) (java.lang.Math.random() * 2));
        }
    }

    /** Drop a loose item entity near a position. */
    public void spawnDrop(Vector3f at, BlockType type, int count) {
        Vector3f pos = new Vector3f(
            at.x + (float) (java.lang.Math.random() - 0.5) * 0.4f,
            at.y + 0.3f,
            at.z + (float) (java.lang.Math.random() - 0.5) * 0.4f);
        itemEntities.add(new ItemEntity(this, pos, new ItemStack(type, count)));
    }

    /** [POT] Drop an item (not a block) as a loose entity. */
    public void spawnItemDrop(Vector3f at, com.voxelgame.item.Item item, int count) {
        Vector3f pos = new Vector3f(
            at.x + (float) (java.lang.Math.random() - 0.5) * 0.4f,
            at.y + 0.3f,
            at.z + (float) (java.lang.Math.random() - 0.5) * 0.4f);
        itemEntities.add(new ItemEntity(this, pos, new ItemStack(item, count)));
    }

    // --- [ENCH] Experience orbs ---

    private final List<XpOrb> xpOrbs = new ArrayList<>();

    public List<XpOrb> getXpOrbs() { return xpOrbs; }

    /** Spawn an experience orb near a position. */
    public void spawnXpOrb(float x, float y, float z, int value) {
        if (value <= 0) return;
        int chunks = (value + 5) / 6;
        int remaining = value;
        for (int i = 0; i < chunks && remaining > 0; i++) {
            int chunk = java.lang.Math.min(6, remaining);
            remaining -= chunk;
            xpOrbs.add(new XpOrb(this,
                x + (float) (java.lang.Math.random() - 0.5) * 0.6f,
                y + 0.2f,
                z + (float) (java.lang.Math.random() - 0.5) * 0.6f,
                chunk));
        }
    }

    /** Advance all experience orbs and cull dead ones. */
    public void updateXpOrbs(float dt) {
        for (XpOrb orb : xpOrbs) {
            orb.update(dt);
        }
        xpOrbs.removeIf(XpOrb::isDead);
    }

    /**
     * Update all villagers (AI + physics).
     * @param timeOfDay 0-24000 (0=6:00, 6000=12:00, 12000=18:00, 18000=0:00)
     */
    public void updateVillagers(float dt, Player player, int timeOfDay) {
        for (Villager v : villagers) {
            v.update(dt, player, timeOfDay);
        }
        villagers.removeIf(Villager::isDead);
    }

    /**
     * [GP-045] Update all animals (AI + physics). Unlike hostile mobs they
     * never despawn вЂ” the player farms them.
     */
    public void updateAnimals(float dt, Player player) {
        for (Animal a : animals) {
            a.update(dt, player);
        }
        animals.removeIf(a -> {
            if (a.isDead()) {
                dropAnimalLoot(a);
                return true;
            }
            return false;
        });
    }

    /**
     * [GP-045] Scatter an animal's loot. Meat is dropped cooked when the
     * animal died in fire or lava; wool comes from sheep.
     */
    private void dropAnimalLoot(Animal a) {
        Vector3f p = a.getPosition();
        boolean cooked = a.isBurning();
        switch (a.getType()) {
            case COW:
                spawnDrop(p, cooked ? BlockType.ITEM_COOKED_BEEF : BlockType.ITEM_RAW_BEEF,
                    1 + (int) (java.lang.Math.random() * 3));
                if (java.lang.Math.random() < 0.70f) {
                    spawnDrop(p, BlockType.ITEM_LEATHER, 1 + (int) (java.lang.Math.random() * 2));
                }
                break;
            case PIG:
                spawnDrop(p, cooked ? BlockType.ITEM_COOKED_PORK : BlockType.ITEM_RAW_PORK,
                    1 + (int) (java.lang.Math.random() * 3));
                break;
            case CHICKEN:
                spawnDrop(p, cooked ? BlockType.ITEM_COOKED_CHICKEN : BlockType.ITEM_RAW_CHICKEN, 1);
                if (java.lang.Math.random() < 0.80f) {
                    spawnDrop(p, BlockType.ITEM_FEATHER, 1 + (int) (java.lang.Math.random() * 2));
                }
                // [ANM] Chickens also drop an egg
                if (java.lang.Math.random() < 0.50f) {
                    spawnDrop(p, BlockType.ITEM_EGG, 1);
                }
                break;
            case SHEEP:
                spawnDrop(p, cooked ? BlockType.ITEM_COOKED_MUTTON : BlockType.ITEM_RAW_MUTTON,
                    1 + (int) (java.lang.Math.random() * 2));
                // [ANM] A sheared sheep yields no wool on death
                if (!a.isSheared()) {
                    spawnDrop(p, BlockType.WHITE_WOOL, 1 + (int) (java.lang.Math.random() * 3));
                }
                break;
            case DEER:
                spawnDrop(p, cooked ? BlockType.ITEM_COOKED_BEEF : BlockType.ITEM_RAW_BEEF,
                    1 + (int) (java.lang.Math.random() * 3));
                if (java.lang.Math.random() < 0.70f) {
                    spawnDrop(p, BlockType.ITEM_LEATHER, 1 + (int) (java.lang.Math.random() * 2));
                }
                break;
            case FOX:
                spawnDrop(p, cooked ? BlockType.ITEM_COOKED_CHICKEN : BlockType.ITEM_RAW_CHICKEN, 1);
                if (java.lang.Math.random() < 0.50f) {
                    spawnDrop(p, BlockType.ITEM_FEATHER, 1);
                }
                break;
            case BEAR:
                // [ANM] Bears are the big game: more meat, often hides
                spawnDrop(p, cooked ? BlockType.ITEM_COOKED_BEEF : BlockType.ITEM_RAW_BEEF,
                    2 + (int) (java.lang.Math.random() * 3));
                if (java.lang.Math.random() < 0.80f) {
                    spawnDrop(p, BlockType.ITEM_LEATHER, 1 + (int) (java.lang.Math.random() * 2));
                }
                break;
            case PARROT:
                spawnDrop(p, BlockType.ITEM_FEATHER, 1 + (int) (java.lang.Math.random() * 2));
                break;
        }
    }

    /** [GP-045] Spawn a fresh animal (used by breeding and auto-spawn). */
    public Animal spawnAnimal(Animal.AnimalType type, float x, float y, float z) {
        Animal a = new Animal(this, type, x, y, z);
        animals.add(a);
        return a;
    }

    /** [GP-045] Spawn a baby animal (breeding result). */
    public Animal spawnAnimalBaby(Animal.AnimalType type, float x, float y, float z) {
        Animal a = new Animal(this, type, x, y, z);
        a.setBaby(true);
        animals.add(a);
        return a;
    }

    /** Tick every primed TNT; remove any that have detonated. */
    public void updateTnt(float dt) {
        // Snapshot iteration prevents ConcurrentModificationException when
        // explode() removes other TNT from the list during the loop.
        for (TntEntity tnt : new java.util.ArrayList<>(tntEntities)) {
            if (!tnt.isDead()) tnt.update(dt);
        }
        tntEntities.removeIf(TntEntity::isDead);
    }

    /**
     * Destroy every non-air, non-bedrock block in a sphere centred on
     * (cx, cy, cz). Each destroyed block has a 30% chance of dropping a
     * loose item entity, so the player can salvage some of the wreckage.
     *
     * Chain reactions are possible: if another TNT block sits inside the
     * radius it is simply removed (its fuse was already spent when it was
     * primed). Water and bedrock are immune.
     */
    public void explode(int cx, int cy, int cz, int radius) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;

                    int bx = cx + dx;
                    int by = cy + dy;
                    int bz = cz + dz;

                    int block = getBlock(bx, by, bz);
                    if (block == BlockType.AIR.id) continue;
                    if (block == BlockType.BEDROCK.id) continue;
                    if (block == BlockType.WATER.id) continue;

                    setBlock(bx, by, bz, (byte) 0);

                    // [GP-022] Sand/gravel above the crater loses its support
                    maybeFall(bx, by + 1, bz);

                    // Remove any primed TNT sitting in the blast radius
                    // without re-triggering its explosion
                    tntEntities.removeIf(t -> {
                        Vector3f p = t.getPosition();
                        return (int) p.x == bx && (int) p.y == by && (int) p.z == bz;
                    });

                    // 30% drop chance вЂ” keeps the crater from being barren
                    if (java.lang.Math.random() < 0.30) {
                        Vector3f dropPos = new Vector3f(bx + 0.5f, by + 0.5f, bz + 0.5f);
                        itemEntities.add(new ItemEntity(this, dropPos,
                            new ItemStack(BlockType.fromId(block), 1)));
                    }
                }
            }
        }

        // [GP-032] The blast reaches the player too: damage falls off with
        // distance from the centre, and the push shoves the player away.
        if (playerRef != null) {
            Vector3f p = playerRef.getPosition();
            float dx = p.x - (cx + 0.5f);
            float dy = p.y - (cy + 0.5f);
            float dz = p.z - (cz + 0.5f);
            float dist = (float) java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz);
            float reach = radius + 2.0f;
            if (dist < reach) {
                float exposure = 1.0f - dist / reach;
                int damage = (int) java.lang.Math.ceil(12.0f * exposure);
                if (damage > 0) {
                    playerRef.takeDamage(damage, Player.DeathCause.EXPLOSION);
                }
                // Knockback away from the blast centre
                if (dist > 0.001f) {
                    org.joml.Vector3f vel = playerRef.getVelocity();
                    vel.add(dx / dist * exposure * 9.0f,
                            2.0f * exposure,
                            dz / dist * exposure * 9.0f);
                }
            }
        }
    }

    /**
     * [AST] An asteroid impact: a sphere of destroyed blocks, a raised
     * cobblestone rim, fire in the centre and blast damage that falls off
     * with distance. Bedrock and water are immune, like TNT.
     */
    public void makeCrater(int cx, int cy, int cz, int radius) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;

                    int bx = cx + dx;
                    int by = cy + dy;
                    int bz = cz + dz;

                    int block = getBlock(bx, by, bz);
                    if (block == BlockType.AIR.id) continue;
                    if (block == BlockType.BEDROCK.id) continue;
                    if (block == BlockType.WATER.id) continue;

                    setBlock(bx, by, bz, (byte) 0);

                    // [GP-022] Sand/gravel above the crater loses its support
                    maybeFall(bx, by + 1, bz);
                }
            }
        }

        // Raised cobblestone rim: a ring just outside the destroyed sphere
        int rimR = radius + 1;
        int rimR2 = rimR * rimR;
        for (int dx = -rimR; dx <= rimR; dx++) {
            for (int dz = -rimR; dz <= rimR; dz++) {
                int d = dx * dx + dz * dz;
                if (d < radius * radius || d > rimR2) continue;
                int bx = cx + dx;
                int bz = cz + dz;
                int gy = getGroundHeight(bx, bz);
                setBlock(bx, gy, bz, BlockType.COBBLESTONE.id);
            }
        }

        // [ECO] The impact buries meteorite ore in the crater floor: the
        // bigger the rock, the richer the deposit. The centre cell stays
        // free for the fire below.
        int oreCount = 1 + radius / 2;
        int placed = 0;
        for (int bx = cx - radius / 2; bx <= cx + radius / 2 && placed < oreCount; bx++) {
            for (int bz = cz - radius / 2; bz <= cz + radius / 2 && placed < oreCount; bz++) {
                if (bx == cx && bz == cz) continue;
                int fy = cy;
                while (fy > 0 && getBlock(bx, fy, bz) == BlockType.AIR.id) fy--;
                if (getBlock(bx, fy, bz) == BlockType.AIR.id) continue;
                if (java.lang.Math.random() > 0.5f) continue;
                setBlock(bx, fy, bz, BlockType.METEORITE_ORE.id);
                placed++;
            }
        }

        // Fire on the crater floor: the centre cell hangs over empty space,
        // so scan down to the first solid block and ignite just above it
        int fy = cy;
        while (fy > 0 && getBlock(cx, fy, cz) == BlockType.AIR.id) fy--;
        if (getBlock(cx, fy + 1, cz) == BlockType.AIR.id) {
            igniteFire(cx, fy + 1, cz);
        }

        // Blast damage: player, mobs and villagers, falling off with distance
        float reach = radius + 3.0f;
        if (playerRef != null) {
            Vector3f p = playerRef.getPosition();
            float dx = p.x - (cx + 0.5f);
            float dy = p.y - (cy + 0.5f);
            float dz = p.z - (cz + 0.5f);
            float dist = (float) java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < reach) {
                float exposure = 1.0f - dist / reach;
                int damage = (int) java.lang.Math.ceil(20.0f * exposure);
                if (damage > 0) {
                    playerRef.takeDamage(damage, Player.DeathCause.EXPLOSION);
                }
                if (dist > 0.001f) {
                    org.joml.Vector3f vel = playerRef.getVelocity();
                    vel.add(dx / dist * exposure * 12.0f,
                            3.0f * exposure,
                            dz / dist * exposure * 12.0f);
                }
            }
        }
        for (Zoloy mob : mobs) {
            Vector3f m = mob.getPosition();
            float dx = m.x - (cx + 0.5f);
            float dy = m.y - (cy + 0.5f);
            float dz = m.z - (cz + 0.5f);
            float dist = (float) java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < reach) {
                float exposure = 1.0f - dist / reach;
                int damage = (int) java.lang.Math.ceil(15.0f * exposure);
                if (damage > 0) {
                    mob.takeDamage(damage, new Vector3f(dx, dy, dz));
                }
            }
        }
        for (Villager v : villagers) {
            Vector3f p = v.getPosition();
            float dx = p.x - (cx + 0.5f);
            float dy = p.y - (cy + 0.5f);
            float dz = p.z - (cz + 0.5f);
            float dist = (float) java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < reach) {
                float exposure = 1.0f - dist / reach;
                int damage = (int) java.lang.Math.ceil(15.0f * exposure);
                if (damage > 0) {
                    v.takeDamage(damage);
                }
            }
        }

        // The blast scares the whole neighbourhood: villagers panic and run
        panicVillagersNear(cx + 0.5f, cz + 0.5f, radius + 14.0f);
    }

    /**
     * [AST] Villagers within range panic: they drop what they are doing and
     * run away from the given point, occasionally hopping. Used when an
     * asteroid starts to fall and again when it lands.
     */
    public void panicVillagersNear(float x, float z, float radius) {
        if (villagers.isEmpty()) return;
        for (Villager v : villagers) {
            Vector3f p = v.getPosition();
            float dx = p.x - x;
            float dz = p.z - z;
            if (dx * dx + dz * dz <= radius * radius) {
                v.panic(new Vector3f(x, p.y, z),
                    7.0f + (float) java.lang.Math.random() * 7.0f);
            }
        }
    }

}
