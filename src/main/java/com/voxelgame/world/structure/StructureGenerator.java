package com.voxelgame.world.structure;

import com.voxelgame.world.BlockType;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.generator.TerrainGenerator;
import com.voxelgame.world.biome.BiomeSelector;

import java.util.Map;

/**
 * Generates structures in the world.
 *
 * Slots are anchored in world space on a grid of {@code MIN_SPACING} chunks,
 * so every chunk that overlaps a structure places the same blocks, no matter
 * which chunk generated first. Writes are deferred to the main thread via
 * {@link Chunk#addPendingWrite} and applied at publish time.
 */
public class StructureGenerator {

    private final long seed;
    private final TerrainGenerator terrainGenerator;

    /** Minimum distance between structures of the same type (in chunks) */
    private static final int MIN_SPACING = 3;

    /** Candidate slots per grid cell. */
    private static final int CANDIDATES_PER_CELL = 8;

    /** Minimum distance between two anchors inside one cell, in blocks. */
    private static final int MIN_ANCHOR_DIST = 16;

    public StructureGenerator(long seed, TerrainGenerator terrainGenerator) {
        this.seed = seed;
        this.terrainGenerator = terrainGenerator;
    }

    /**
     * Generate the structures intersecting a chunk. Worker-thread safe: only
     * touches this chunk, writing deferred placements.
     */
    public void generate(Chunk chunk, Map<Long, Chunk> chunks) {
        int cx = chunk.getChunkX();
        int cz = chunk.getChunkZ();

        // Grid cells whose structures could reach into this chunk. Templates
        // are at most 9 blocks wide, so one cell in each direction suffices.
        int gx0 = Math.floorDiv(cx - 1, MIN_SPACING);
        int gx1 = Math.floorDiv(cx + 1, MIN_SPACING);
        int gz0 = Math.floorDiv(cz - 1, MIN_SPACING);
        int gz1 = Math.floorDiv(cz + 1, MIN_SPACING);

        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                placeCellStructures(chunk, gx, gz);
            }
        }
    }

    /** Place up to a few structures inside one grid cell, clipped to the chunk. */
    private void placeCellStructures(Chunk chunk, int gx, int gz) {
        int placed = 0;
        long[] anchors = new long[CANDIDATES_PER_CELL];
        int anchorCount = 0;

        for (int i = 0; i < CANDIDATES_PER_CELL && placed < 3; i++) {
            long hash = hashPos(gx, gz, i);
            // ~half of the candidates are rejected outright
            double r = ((hash >> 8) & 0xFF) / 255.0;
            if (r > 0.55) continue;

            int lx = (int) (hash & 0xF);
            int lz = (int) ((hash >> 4) & 0xF);
            int wx = gx * MIN_SPACING * Chunk.SIZE + lx;
            int wz = gz * MIN_SPACING * Chunk.SIZE + lz;

            // Keep anchors inside a cell apart from each other
            boolean tooClose = false;
            for (int a = 0; a < anchorCount; a++) {
                long ax = anchors[a] >> 32;
                long az = anchors[a] & 0xFFFFFFFFL;
                int ddx = (int) (wx - ax);
                int ddz = (int) (wz - az);
                if (ddx * ddx + ddz * ddz < MIN_ANCHOR_DIST * MIN_ANCHOR_DIST) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) continue;

            BiomeSelector.MCBiome biome = terrainGenerator.getMCBiome(wx, wz);
            int height = terrainGenerator.getHeight(wx, wz);

            StructureTemplate template = pickStructure(biome, hash >> 16);
            if (template == null) continue;

            if (template.underwater) {
                if (height >= TerrainGenerator.SEA_LEVEL - 2) continue;
            } else {
                if (height < TerrainGenerator.SEA_LEVEL) continue;
            }
            if (!isTerrainSuitable(wx, wz, template)) continue;

            anchors[anchorCount++] = ((long) wx << 32) | (wz & 0xFFFFFFFFL);
            placeStructure(chunk, wx, height + 1, wz, template);
            placed++;
        }
    }

    private StructureTemplate pickStructure(BiomeSelector.MCBiome biome, long hash) {
        double r = ((hash & 0xFF) / 255.0);

        return switch (biome) {
            case PLAINS -> {
                if (r < 0.4) yield StructureRegistry.createVillageHouse();
                else if (r < 0.6) yield StructureRegistry.createLargeHouse();
                else if (r < 0.75) yield StructureRegistry.createWell();
                else if (r < 0.9) yield StructureRegistry.createFarmPlot();
                else yield StructureRegistry.createWatchtower();
            }
            case FOREST, BIRCH_FOREST, DARK_FOREST -> {
                if (r < 0.35) yield StructureRegistry.createVillageHouse();
                else if (r < 0.55) yield StructureRegistry.createWatchtower();
                else if (r < 0.7) yield StructureRegistry.createRuinedWall();
                else if (r < 0.85) yield StructureRegistry.createRuins();
                else yield StructureRegistry.createFarmPlot();
            }
            case DESERT -> {
                if (r < 0.3) yield StructureRegistry.createVillageHouse();
                else if (r < 0.5) yield StructureRegistry.createWell();
                else if (r < 0.65) yield StructureRegistry.createRuinedWall();
                else if (r < 0.85) yield StructureRegistry.createDesertTemple();
                // [BASE] Survivor base: an oasis camp with warm lanterns
                else if (r < 0.97) yield StructureRegistry.createOasisCamp();
                else yield null;
            }
            case TAIGA, OLD_GROWTH_PINE_TAIGA, OLD_GROWTH_SPRUCE_TAIGA, SNOWY_TAIGA, GROVE -> {
                if (r < 0.3) yield StructureRegistry.createVillageHouse();
                else if (r < 0.55) yield StructureRegistry.createWatchtower();
                else if (r < 0.7) yield StructureRegistry.createRuinedWall();
                else if (r < 0.85) yield StructureRegistry.createIgloo();
                // [BASE] Survivor base: an ice mine drift with a stash
                else if (r < 0.97) yield StructureRegistry.createIceMine();
                else yield null;
            }
            case SAVANNA, SAVANNA_PLATEAU, SHATTERED_SAVANNA -> {
                if (r < 0.5) yield StructureRegistry.createVillageHouse();
                else if (r < 0.7) yield StructureRegistry.createFarmPlot();
                else if (r < 0.85) yield StructureRegistry.createWell();
                else yield null;
            }
            case SWAMP, MOOR, MANGROVE_SWAMP -> {
                if (r < 0.3) yield StructureRegistry.createRuinedWall();
                else if (r < 0.5) yield StructureRegistry.createRuins();
                else if (r < 0.65) yield StructureRegistry.createWell();
                // [BASE] Survivor base: a plank hut on stilts over the mire
                else if (r < 0.92) yield StructureRegistry.createSwampHut();
                else yield null;
            }
            case JUNGLE, BAMBOO_JUNGLE -> {
                if (r < 0.25) yield StructureRegistry.createRuinedWall();
                else if (r < 0.45) yield StructureRegistry.createWatchtower();
                else if (r < 0.7) yield StructureRegistry.createJungleTemple();
                // [BASE] Survivor base: a log platform in the canopy
                else if (r < 0.95) yield StructureRegistry.createJungleCamp();
                else yield null;
            }
            case BADLANDS, WOODED_BADLANDS -> {
                if (r < 0.3) yield StructureRegistry.createRuinedWall();
                else if (r < 0.55) yield StructureRegistry.createWell();
                else yield null;
            }
            case FROZEN_OCEAN, COLD_OCEAN, OCEAN, WARM_OCEAN, LUKEWARM_OCEAN, DEEP_OCEAN -> {
                if (r < 0.4) yield StructureRegistry.createShipwreck();
                else if (r < 0.7) yield StructureRegistry.createOceanMonument();
                // [BASE] Survivor base: a glass dome on the sea floor
                else if (r < 0.95) yield StructureRegistry.createUnderwaterBase();
                else yield null;
            }
            case SNOWY_PLAINS, ICE_SPIKES -> {
                if (r < 0.4) yield StructureRegistry.createIgloo();
                else if (r < 0.6) yield StructureRegistry.createRuinedWall();
                // [BASE] Survivor base: an ice outpost with icy lanterns
                else if (r < 0.95) yield StructureRegistry.createIceOutpost();
                else yield null;
            }
            // [BASE] Survivor base in the peaks: a stone bunker
            case STONY_PEAKS, JAGGED_PEAKS, FROZEN_PEAKS, CRYSTAL_PEAKS -> {
                if (r < 0.45) yield StructureRegistry.createRuinedWall();
                else if (r < 0.95) yield StructureRegistry.createMountainShelter();
                else yield null;
            }
            default -> {
                if (r < 0.1) yield StructureRegistry.createRuinedWall();
                else yield null;
            }
        };
    }

    private boolean isTerrainSuitable(int wx, int wz, StructureTemplate template) {
        int halfW = template.width / 2;
        int halfD = template.depth / 2;
        int centerHeight = terrainGenerator.getHeight(wx, wz);
        int maxDiff = 2;

        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfD; dz <= halfD; dz++) {
                int h = terrainGenerator.getHeight(wx + dx, wz + dz);
                if (Math.abs(h - centerHeight) > maxDiff) return false;
            }
        }
        return true;
    }

    /** Clip the template to the given chunk, writing deferred blocks and loot. */
    private void placeStructure(Chunk chunk, int wx, int baseY, int wz,
                                StructureTemplate template) {
        int halfW = template.width / 2;
        int halfD = template.depth / 2;
        int cwX = chunk.getWorldX();
        int cwZ = chunk.getWorldZ();

        boolean any = false;
        for (int lx = 0; lx < template.width; lx++) {
            for (int ly = 0; ly < template.height; ly++) {
                for (int lz = 0; lz < template.depth; lz++) {
                    int block = template.getBlock(lx, ly, lz);
                    if (block == BlockType.AIR.id) continue;

                    int px = wx + lx - halfW;
                    int py = baseY + ly;
                    int pz = wz + lz - halfD;

                    int lpx = px - cwX;
                    int lpz = pz - cwZ;
                    if (lpx < 0 || lpx >= Chunk.SIZE || lpz < 0 || lpz >= Chunk.SIZE) continue;
                    if (py < 0 || py >= Chunk.HEIGHT) continue;

                    chunk.addPendingWrite(lpx, py, lpz, block);
                    any = true;
                }
            }
        }

        if (any) {
            chunk.markStructure(template.name);
            // Loot goes to the world at publish time, with the chest position
            for (int[] entry : template.getLoot()) {
                int px = wx + entry[0] - halfW;
                int py = baseY + entry[1];
                int pz = wz + entry[2] - halfD;
                chunk.addPendingLoot(px, py, pz, entry[3], entry[4]);
            }
        }
    }

    private long hashPos(int x, int z, int salt) {
        long h = seed;
        h = h * 6364136223846793005L + x * 341873128712L;
        h = h * 6364136223846793005L + z * 132897987541L;
        h = h * 6364136223846793005L + salt * 2654435761L;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return h;
    }
}