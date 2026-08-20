package com.voxelgame.world.generator;

import com.voxelgame.world.*;
import com.voxelgame.world.biome.BiomeData;
import com.voxelgame.world.biome.BiomeHeightGenerator;
import com.voxelgame.world.biome.BiomeNoise;
import com.voxelgame.world.biome.BiomeRegistry;
import com.voxelgame.world.biome.BiomeSelector;
import com.voxelgame.world.cave.CaveGenerator;
import com.voxelgame.world.cave.OreGenerator;
import com.voxelgame.world.cave.UndergroundBiomeGenerator;

/**
 * World generator: heightmap, biomes, caves, ores and decoration.
 *
 * Everything is a pure function of (seed, world coordinate). Nothing depends
 * on the order chunks are visited, which is what makes the world reproducible
 * from its seed and safe to generate off the main thread later.
 *
 * Decoration reaches across chunk borders by re-running the placement test
 * for every nearby column, so a tree rooted in a neighbouring chunk still
 * drops its canopy into this one. That is why leaves are no longer clipped
 * at the chunk edge, which used to shear canopies in half and make trunks
 * look like they were leaning.
 */
public class TerrainGenerator {

    private final long seed;

    private final NoiseGenerator continentNoise;
    private final NoiseGenerator heightNoise;
    private final NoiseGenerator mountainNoise;
    private final NoiseGenerator temperatureNoise;
    private final NoiseGenerator humidityNoise;
    private final NoiseGenerator erosionNoise;
    private final NoiseGenerator caveNoise;
    private final NoiseGenerator caveNoise2;
    private final NoiseGenerator oreNoise;
    private final NoiseGenerator riverNoise;

    /** MC 1.18+ multi-noise biome system */
    private final BiomeNoise biomeNoise;
    private final BiomeSelector biomeSelector;

    /** Cave generation */
    private final CaveGenerator caveGenerator;
    private final OreGenerator oreGenerator;
    private final UndergroundBiomeGenerator undergroundBiomeGenerator;

    public static final int SEA_LEVEL = Chunk.SEA_LEVEL;

    /** How far decoration may reach into a neighbouring chunk. */
    private static final int DECORATION_MARGIN = 4;

    public TerrainGenerator(long seed) {
        this.seed = seed;
        continentNoise   = new NoiseGenerator(seed);
        heightNoise      = new NoiseGenerator(seed + 1);
        mountainNoise    = new NoiseGenerator(seed + 2);
        temperatureNoise = new NoiseGenerator(seed + 3);
        humidityNoise    = new NoiseGenerator(seed + 4);
        erosionNoise     = new NoiseGenerator(seed + 5);
        caveNoise        = new NoiseGenerator(seed + 6);
        caveNoise2       = new NoiseGenerator(seed + 7);
        oreNoise         = new NoiseGenerator(seed + 8);
        riverNoise       = new NoiseGenerator(seed + 9);

        // MC 1.18+ biome system
        biomeNoise = new BiomeNoise(seed);
        biomeSelector = new BiomeSelector();

        // Cave systems
        caveGenerator = new CaveGenerator(seed);
        oreGenerator = new OreGenerator(seed);
        undergroundBiomeGenerator = new UndergroundBiomeGenerator(seed);
    }

    public long getSeed() { return seed; }

    /** Map a block name (from JSON) to a BlockType. */
    private static final java.util.Map<String, BlockType> BLOCK_BY_NAME = new java.util.HashMap<>();
    static {
        for (BlockType t : BlockType.values()) {
            BLOCK_BY_NAME.put(t.name, t);
        }
    }

    private static BlockType blockByName(String name) {
        if (name == null || name.isEmpty()) return BlockType.STONE;
        BlockType t = BLOCK_BY_NAME.get(name);
        return t != null ? t : BlockType.STONE;
    }

    /** Base height for a biome (JSON or fallback). */
    private int biomeBaseHeight(BiomeData biome) {
        if (biome == null) return 68;
        return switch (biome.id) {
            case "ocean", "beach" -> 58;
            case "desert", "savanna", "salt_flats" -> 67;
            case "plains", "cherry_grove", "lavender_meadow", "autumn_forest" -> 68;
            case "forest", "birch", "jungle", "misty_moor" -> 70;
            case "taiga", "petrified_forest" -> 72;
            case "swamp" -> 63;
            case "mountains", "crystal_peaks", "volcanic_highlands" -> 96;
            case "snowy_plains", "glacier" -> 72;
            case "badlands", "ash_wastes" -> 70;
            case "mushroom" -> 68;
            default -> 68;
        };
    }

    private int biomeVariance(BiomeData biome) {
        if (biome == null) return 6;
        return switch (biome.id) {
            case "mountains", "crystal_peaks", "volcanic_highlands" -> 48;
            case "taiga" -> 14;
            case "forest", "jungle", "autumn_forest" -> 10;
            case "swamp", "ocean", "beach" -> 3;
            case "desert", "savanna" -> 5;
            default -> 6;
        };
    }

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    public void generate(Chunk chunk) {
        int originX = chunk.getWorldX();
        int originZ = chunk.getWorldZ();

        // Terrain + caves + ores
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                buildColumn(chunk, x, z, originX + x, originZ + z);
            }
        }

        decorate(chunk, originX, originZ);

        // Generate water sources after terrain is complete
        generateWater(chunk, originX, originZ);
    }

    /**
     * Generate water for oceans, rivers, and lakes.
     * Water fills air blocks below sea level — no water columns above surface.
     */
    private void generateWater(Chunk chunk, int originX, int originZ) {
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = originX + x;
                int wz = originZ + z;
                int height = getHeight(wx, wz);

                // Fill all air blocks below sea level with water
                // This creates oceans, lakes, and flooded caves naturally
                if (height < SEA_LEVEL) {
                    for (int y = SEA_LEVEL; y > height; y--) {
                        byte existing = chunk.getBlock(x, y, wz);
                        if (existing == BlockType.AIR.id) {
                            chunk.setBlock(x, y, wz, BlockType.WATER.id);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Climate
    // ------------------------------------------------------------------

    /** 0..1, low is ocean. */
    public double continentalness(int x, int z) {
        return contrast(norm(continentNoise.fbm(x, z, 3, 0.00055, 0.5)));
    }

    public double temperature(int x, int z) {
        return contrast(norm(temperatureNoise.fbm(x, z, 3, 0.0011, 0.5)));
    }

    public double humidity(int x, int z) {
        return contrast(norm(humidityNoise.fbm(x, z, 3, 0.0013, 0.5)));
    }

    public double erosion(int x, int z) {
        return contrast(norm(erosionNoise.fbm(x, z, 3, 0.0009, 0.5)));
    }

    /**
     * Averaging octaves pulls fBm towards the middle: the raw field only
     * spans about 0.18..0.81, so threshold-based biome picks in the tails
     * (desert, taiga, mountains) would almost never fire.
     *
     * Rescale that working range back onto 0..1 and apply a mild S-curve, so
     * extremes are reachable while most terrain still sits in the middle.
     */
    private static double contrast(double v) {
        final double lo = 0.20, hi = 0.80;
        double t = (v - lo) / (hi - lo);
        t = Math.max(0.0, Math.min(1.0, t));
        // smoothstep sharpens the transition without hard-clipping
        return t * t * (3.0 - 2.0 * t) * 0.85 + t * 0.15;
    }

    /** Legacy: pick from the hardcoded enum. Use getDataBiome for JSON biomes. */
    public Biome getBiome(int x, int z) {
        return Biome.select(continentalness(x, z), temperature(x, z),
                            humidity(x, z), erosion(x, z));
    }

    /** MC 1.18+ biome selection using multi-noise. */
    public BiomeSelector.MCBiome getMCBiome(int x, int z) {
        double t = biomeNoise.T(x, z);
        double h = biomeNoise.H(x, z);
        double c = biomeNoise.C(x, z);
        double e = biomeNoise.E(x, z);
        double w = biomeNoise.W(x, z);
        double pv = biomeNoise.PV(x, z);
        return biomeSelector.select(t, h, c, e, w, pv);
    }

    /** Data-driven biome selection from biomes.json, cached per column. */
    private final java.util.Map<Long, BiomeData> biomeCache = new java.util.HashMap<>();

    public BiomeData getDataBiome(int x, int z) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        BiomeData cached = biomeCache.get(key);
        if (cached != null) return cached;

        float temp = (float) temperature(x, z);
        float humid = (float) humidity(x, z);
        float cont = (float) continentalness(x, z);
        BiomeData biome = BiomeRegistry.select(temp, humid, cont);

        // Limit cache size to prevent memory issues
        if (biomeCache.size() > 10000) biomeCache.clear();
        biomeCache.put(key, biome);
        return biome;
    }

    // ------------------------------------------------------------------
    // Heightmap
    // ------------------------------------------------------------------

    /**
     * Surface height for a column. Generates Minecraft-like terrain with
     * varied biomes: plains, forests, mountains, deserts, oceans.
     */
    public int getHeight(int x, int z) {
        double c = biomeNoise.C(x, z);
        double e = biomeNoise.E(x, z);
        double pv = biomeNoise.PV(x, z);
        double w = biomeNoise.W(x, z);
        double t = biomeNoise.T(x, z);

        BiomeSelector.MCBiome biome = getMCBiome(x, z);
        boolean isRiver = pv < -0.3 && c > 0.3 && c < 0.6;

        // Base height from spline
        int height = BiomeHeightGenerator.getHeight(c, e, pv, w, isRiver);

        // Biome-specific terrain shaping
        switch (biome) {
            case DEEP_OCEAN, FROZEN_OCEAN -> {
                // Deep ocean floor
                height = 35 + (int)(heightNoise.fbm(x, z, 3, 0.01, 0.5) * 8);
            }
            case OCEAN -> {
                // Shallow ocean
                height = 45 + (int)(heightNoise.fbm(x, z, 3, 0.015, 0.5) * 6);
            }
            case BEACH, SNOWY_BEACH -> {
                // Flat beach at sea level
                height = SEA_LEVEL - 1 + (int)(heightNoise.noise2D(x * 0.1, z * 0.1) * 2);
            }
            case PLAINS -> {
                // Gentle rolling hills
                double plains = heightNoise.fbm(x, z, 3, 0.008, 0.5) * 6;
                height = 66 + (int)plains;
            }
            case FOREST, BIRCH_FOREST, DARK_FOREST -> {
                // Forest hills
                double forest = heightNoise.fbm(x, z, 4, 0.012, 0.5) * 10;
                height = 68 + (int)forest;
            }
            case TAIGA, OLD_GROWTH_PINE_TAIGA, OLD_GROWTH_SPRUCE_TAIGA -> {
                // Taiga with more variation
                double taiga = heightNoise.fbm(x, z, 4, 0.01, 0.5) * 12;
                height = 70 + (int)taiga;
            }
            case JUNGLE -> {
                // Jungle - low and flat with occasional hills
                double jungle = heightNoise.fbm(x, z, 3, 0.008, 0.5) * 8;
                height = 68 + (int)jungle;
            }
            case DESERT -> {
                // Desert - flat with dunes
                double dune = Math.sin(x * 0.08) * Math.cos(z * 0.08) * 4;
                double flat = heightNoise.fbm(x, z, 3, 0.005, 0.5) * 4;
                height = 67 + (int)(dune + flat);
            }
            case SAVANNA, SAVANNA_PLATEAU -> {
                // Savanna - flat with occasional plateaus
                double savanna = heightNoise.fbm(x, z, 3, 0.006, 0.5) * 5;
                height = 68 + (int)savanna;
            }
            case JAGGED_PEAKS, FROZEN_PEAKS -> {
                // Mountains - dramatic peaks
                double mountainDetail = heightNoise.fbm(x, z, 4, 0.015, 0.5) * 15;
                double ridge = Math.abs(heightNoise.fbm(x, z, 3, 0.008, 0.5)) * 20;
                height = 85 + (int)(mountainDetail + ridge);
                // Snow cap on highest peaks
                if (height > 100) height += 5;
            }
            case STONY_PEAKS -> {
                // Stone peaks - bare rock
                double stony = heightNoise.fbm(x, z, 3, 0.012, 0.5) * 12;
                height = 80 + (int)stony;
            }
            case WINDSWEPT_HILLS, WINDSWEPT_FOREST -> {
                // Windswept - eroded hills
                double wind = heightNoise.fbm(x, z, 4, 0.018, 0.5) * 18;
                height = 75 + (int)wind;
            }
            case SWAMP -> {
                // Swamp - low and flat, near water level
                double swamp = heightNoise.fbm(x, z, 3, 0.005, 0.5) * 3;
                height = 61 + (int)swamp;
            }
            case BADLANDS, WOODED_BADLANDS -> {
                // Badlands - terraced
                double terrace = Math.sin(height * 0.3) * 4;
                double badlands = heightNoise.fbm(x, z, 3, 0.008, 0.5) * 6;
                height = 72 + (int)(terrace + badlands);
            }
            case PLATEAU -> {
                // Plateau - flat top with steep sides
                double plateau = heightNoise.fbm(x, z, 2, 0.004, 0.5) * 4;
                height = 80 + (int)plateau;
            }
            case MUSHROOM_FIELDS -> {
                // Mushroom islands - gentle mounds
                double mush = heightNoise.fbm(x, z, 3, 0.01, 0.5) * 8;
                height = 70 + (int)mush;
            }
            default -> {
                // Default gentle terrain
                double detail = heightNoise.fbm(x, z, 3, 0.01, 0.5) * 6;
                height = 68 + (int)detail;
            }
        }

        // Beach smoothing near sea level
        if (height >= SEA_LEVEL - 2 && height <= SEA_LEVEL + 3) {
            double beachBlend = 1.0 - Math.abs(height - SEA_LEVEL) / 3.0;
            beachBlend = Math.max(0, Math.min(1, beachBlend));
            height = (int) Math.round(height * (1 - beachBlend * 0.5) + SEA_LEVEL * beachBlend * 0.5);
        }

        // River carving
        height = carveRiver(x, z, height);

        // Clamp
        return Math.max(4, Math.min(Chunk.HEIGHT - 40, height));
    }

    /** Height using data-driven biome. */
    private int getHeightDataBiome(int x, int z, BiomeData biome) {
        double cont = continentalness(x, z);
        double ero = erosion(x, z);

        double detail = heightNoise.fbm(x, z, 5, 0.0075, 0.5);
        int baseHeight = biomeBaseHeight(biome);
        int variance = biomeVariance(biome);
        double base = baseHeight + detail * variance;

        // Mountains use ridged noise
        if (biome != null && (biome.id.equals("mountains") || biome.id.equals("crystal_peaks")
                || biome.id.equals("volcanic_highlands"))) {
            double ridge = mountainNoise.ridged(x, z, 4, 0.0035, 0.5);
            double sharpness = 1.0 - Math.min(1.0, ero / 0.35);
            base += ridge * variance * 1.4 * sharpness;
        }

        // Ocean floor
        if (biome != null && biome.id.equals("ocean")) {
            double depth = (0.30 - cont) / 0.30;
            base = SEA_LEVEL - 3 - depth * 22 + detail * 4;
        }

        int height = (int) Math.round(base);
        height = carveRiver(x, z, height);
        return Math.max(4, Math.min(Chunk.HEIGHT - 40, height));
    }

    /**
     * Rivers follow the zero crossing of a low-frequency field. Using the
     * absolute value gives a valley that narrows to a channel.
     * Also creates lakes in low-lying areas.
     */
    private int carveRiver(int x, int z, int height) {
        // No rivers out at sea
        if (height < SEA_LEVEL - 6) return height;

        double r = riverNoise.fbm(x, z, 2, 0.00085, 0.5);
        double dist = Math.abs(r);

        final double width = 0.035;
        if (dist >= width) {
            // Check for lake: low area below sea level
            if (height < SEA_LEVEL && height > SEA_LEVEL - 8) {
                double lakeNoise = heightNoise.fbm(x, z, 2, 0.02, 0.5);
                if (lakeNoise < -0.2) {
                    // Depression becomes a lake bed
                    return SEA_LEVEL - 3 + (int)(lakeNoise * 3);
                }
            }
            return height;
        }

        double t = 1.0 - (dist / width);            // 1 at the centre line
        double carve = t * t * 9.0;

        int carved = (int) Math.round(height - carve);
        // Keep the riverbed just under the water line so it fills
        return Math.min(carved, Math.max(SEA_LEVEL - 5, carved));
    }

    // ------------------------------------------------------------------
    // Column
    // ------------------------------------------------------------------

    private void buildColumn(Chunk chunk, int lx, int lz, int wx, int wz) {
        BiomeSelector.MCBiome biome = getMCBiome(wx, wz);
        int height = getHeight(wx, wz);

        // Surface layer thickness varies a little so it is not a flat shell
        int soilDepth = 3 + (int) (norm(heightNoise.noise2D(wx * 0.3, wz * 0.3)) * 2);

        boolean isMountain = isMountainBiome(biome);

        for (int y = 0; y < Chunk.HEIGHT; y++) {
            byte block;

            // Bedrock floor (noise-based, bottom 2-5 layers)
            if (oreGenerator.isBedrock(wx, y, wz)) {
                block = BlockType.BEDROCK.id;
            } else if (y > height) {
                // Above surface: air (water added by simulation)
                block = BlockType.AIR.id;
            } else if (y == height) {
                block = surfaceBlockData(biome, wx, y, wz, height);
            } else if (y >= height - soilDepth) {
                block = subSurfaceBlockData(biome, height);
            } else if (OreGenerator.isDeepslate(y)) {
                block = BlockType.DEEPSLATE.id; // Will use deepslate texture
            } else {
                block = BlockType.STONE.id;
            }

            // Caves carve into solid blocks below surface
            if (block != BlockType.AIR.id && block != BlockType.WATER.id
                && block != BlockType.BEDROCK.id
                && caveGenerator.isCave(wx, y, wz, height)) {
                block = BlockType.AIR.id;
            }

            // Cave entrances: carve opening from cave to surface
            if (block != BlockType.AIR.id && block != BlockType.WATER.id
                && block != BlockType.BEDROCK.id && y >= height - 4) {
                int entranceSize = caveGenerator.isCaveEntrance(wx, wz, height);
                if (entranceSize > 0 && y >= height - entranceSize) {
                    block = BlockType.AIR.id;
                }
            }

            // Aquifer water fills caves below water level
            if (block == BlockType.AIR.id && caveGenerator.isWater(wx, y, wz, height)) {
                block = BlockType.WATER.id;
            }

            // Lava at very bottom
            if (block == BlockType.AIR.id && caveGenerator.isLava(y)) {
                block = BlockType.LAVA.id;
            }

            // Ores replace stone and deepslate
            if (block == BlockType.STONE.id || block == BlockType.DEEPSLATE.id) {
                BlockType ore = oreGenerator.getOre(wx, y, wz, isMountain);
                if (ore != BlockType.AIR) {
                    block = ore.id;
                }
            }

            // Underground biome decorations
            if (block == BlockType.AIR.id || block == BlockType.WATER.id) {
                UndergroundBiomeGenerator.UndergroundBiome ugBiome =
                    undergroundBiomeGenerator.getBiome(wx, y, wz, block == BlockType.AIR.id);
                // Lush cave: moss on ceiling
                if (ugBiome == UndergroundBiomeGenerator.UndergroundBiome.LUSH_CAVE
                    && undergroundBiomeGenerator.hasMoss(wx, y, wz, ugBiome)) {
                    // Check if there's solid block above (ceiling)
                    if (y + 1 <= height && chunk.getBlock(lx, y + 1, lz) != BlockType.AIR.id) {
                        // Place moss on ceiling - handled in decoration pass
                    }
                }
            }

            chunk.setBlock(lx, y, lz, block);
        }
    }

    private byte surfaceBlock(Biome biome, int wx, int y, int wz, int height) {
        // Underwater surfaces turn to sand/gravel regardless of biome
        if (height < SEA_LEVEL) {
            return hashNoise(wx, 0, wz, 5) > 0.75
                ? BlockType.GRAVEL.id : BlockType.SAND.id;
        }
        if (height == SEA_LEVEL && biome != Biome.DESERT) {
            return BlockType.SAND.id;
        }

        // Bare rock and snow high up
        if (biome == Biome.MOUNTAINS) {
            if (height > 118) return BlockType.SNOW.id;
            if (height > 104) return BlockType.STONE.id;
        }
        if (biome == Biome.SNOWY_PEAKS) return BlockType.SNOW.id;

        return biome.surface.id;
    }

    /** Surface block for MC 1.18+ biome. */
    private byte surfaceBlockData(BiomeSelector.MCBiome biome, int wx, int y, int wz, int height) {
        // Underwater surfaces
        if (height < SEA_LEVEL - 2) {
            return hashNoise(wx, 0, wz, 5) > 0.75
                ? BlockType.GRAVEL.id : BlockType.SAND.id;
        }
        // Beach/shore zone: sand near water
        if (height >= SEA_LEVEL - 2 && height <= SEA_LEVEL + 2) {
            return switch (biome) {
                case FROZEN_OCEAN, SNOWY_BEACH -> BlockType.SNOW.id;
                case STONY_SHORE -> BlockType.GRAVEL.id;
                default -> BlockType.SAND.id;
            };
        }

        return switch (biome) {
            case FROZEN_OCEAN, DEEP_OCEAN, OCEAN -> BlockType.SAND.id;
            case MUSHROOM_FIELDS -> BlockType.MYCELIUM.id;
            case BEACH, SNOWY_BEACH -> BlockType.SAND.id;
            case STONY_SHORE -> BlockType.GRAVEL.id;
            case JAGGED_PEAKS, FROZEN_PEAKS -> height > 100 ? BlockType.SNOW.id : BlockType.STONE.id;
            case STONY_PEAKS -> BlockType.STONE.id;
            case SNOWY_PLAINS, SNOWY_TAIGA -> BlockType.SNOW.id;
            case GROVE -> BlockType.SNOW.id;
            case WINDSWEPT_HILLS, WINDSWEPT_FOREST -> BlockType.STONE.id;
            case BADLANDS, WOODED_BADLANDS -> BlockType.TERRACOTTA.id;
            case PLATEAU -> BlockType.STONE.id;
            case DESERT -> BlockType.SAND.id;
            case SAVANNA, SAVANNA_PLATEAU -> BlockType.GRASS_BLOCK.id;
            case SWAMP, MOOR -> BlockType.GRASS_BLOCK.id;
            case JUNGLE -> BlockType.GRASS_BLOCK.id;
            case CRYSTAL_PEAKS -> BlockType.BASALT.id;
            case GLACIER -> BlockType.BLUE_ICE.id;
            case VOLCANO -> BlockType.BASALT.id;
            case AUTUMN_FOREST -> BlockType.GRASS_BLOCK.id;
            case PETRIFIED_FOREST -> BlockType.STONE.id;
            case SALT_FLATS -> BlockType.SALT.id;
            case ASH_WASTES -> BlockType.ASH.id;
            case LAVENDER_MEADOW -> BlockType.GRASS_BLOCK.id;
            case SHATTERED_SAVANNA -> BlockType.GRASS_BLOCK.id;
            default -> BlockType.GRASS_BLOCK.id;
        };
    }

    private byte subSurfaceBlock(Biome biome, int height) {
        if (biome == Biome.SNOWY_PEAKS || (biome == Biome.MOUNTAINS && height > 104)) {
            return BlockType.STONE.id;
        }
        return biome.filler.id;
    }

    /** Sub-surface for MC 1.18+ biome. */
    private byte subSurfaceBlockData(BiomeSelector.MCBiome biome, int height) {
        if (height > 104) {
            return switch (biome) {
                case JAGGED_PEAKS, FROZEN_PEAKS, STONY_PEAKS, CRYSTAL_PEAKS,
                     WINDSWEPT_HILLS, WINDSWEPT_FOREST, PLATEAU -> BlockType.STONE.id;
                default -> BlockType.DIRT.id;
            };
        }

        return switch (biome) {
            case FROZEN_OCEAN, DEEP_OCEAN, OCEAN, BEACH, SNOWY_BEACH -> BlockType.SAND.id;
            case MUSHROOM_FIELDS -> BlockType.DIRT.id;
            case STONY_SHORE -> BlockType.GRAVEL.id;
            case DESERT -> BlockType.SAND.id;
            case BADLANDS, WOODED_BADLANDS -> BlockType.TERRACOTTA.id;
            case SALT_FLATS -> BlockType.SALT.id;
            case ASH_WASTES -> BlockType.ASH.id;
            case GLACIER -> BlockType.BLUE_ICE.id;
            case VOLCANO -> BlockType.BASALT.id;
            case CRYSTAL_PEAKS -> BlockType.BASALT.id;
            case PETRIFIED_FOREST -> BlockType.STONE.id;
            default -> BlockType.DIRT.id;
        };
    }

    // ------------------------------------------------------------------
    // Caves
    // ------------------------------------------------------------------

    /**
     * Two-layer system: "cheese" caverns from a single 3D field, plus worm
     * tunnels where two independent fields both sit near zero, which traces
     * the intersection of two surfaces - a line.
     */
    private boolean isCave(int x, int y, int z) {
        if (y < 5 || y > 96) return false;

        // Tunnels
        double a = caveNoise.fbm3(x, y * 1.6, z, 2, 0.014, 0.5);
        double b = caveNoise2.fbm3(x, y * 1.6, z, 2, 0.014, 0.5);
        if (a * a + b * b < 0.0016) return true;

        // Larger caverns, biased to deeper ground
        double cheese = caveNoise.fbm3(x, y * 2.1, z, 3, 0.021, 0.5);
        double threshold = 0.62 - (1.0 - Math.min(1.0, y / 60.0)) * 0.12;
        return cheese > threshold;
    }

    // ------------------------------------------------------------------
    // Ores
    // ------------------------------------------------------------------

    /**
     * Ore bodies come from a 3D noise field thresholded per ore, so they form
     * small connected blobs instead of the isolated speckles a per-block
     * random gives. Depth bands follow the classic distribution.
     */
    private boolean isMountainBiome(BiomeSelector.MCBiome biome) {
        return switch (biome) {
            case JAGGED_PEAKS, FROZEN_PEAKS, STONY_PEAKS, CRYSTAL_PEAKS,
                 WINDSWEPT_HILLS, WINDSWEPT_FOREST, PLATEAU, VOLCANO -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------
    // Decoration
    // ------------------------------------------------------------------

    /**
     * Run decoration for every column that could reach into this chunk,
     * including a margin outside it. Features clip themselves to the chunk,
     * so a tree straddling the border is written correctly from both sides.
     */
    private void decorate(Chunk chunk, int originX, int originZ) {
        for (int dx = -DECORATION_MARGIN; dx < Chunk.SIZE + DECORATION_MARGIN; dx++) {
            for (int dz = -DECORATION_MARGIN; dz < Chunk.SIZE + DECORATION_MARGIN; dz++) {
                int wx = originX + dx;
                int wz = originZ + dz;

                BiomeSelector.MCBiome biome = getMCBiome(wx, wz);
                int height = getHeight(wx, wz);

                if (height < SEA_LEVEL) continue;

                if (shouldPlaceTreeData(wx, wz, biome)) {
                    placeTreeData(chunk, originX, originZ, wx, height + 1, wz, biome);
                } else if (dx >= 0 && dx < Chunk.SIZE && dz >= 0 && dz < Chunk.SIZE) {
                    placeGroundCoverData(chunk, dx, dz, wx, height + 1, wz, biome);
                }

                // Mountain boulders: scattered rocks on steep slopes
                if (isMountainBiome(biome) && height > 80 && dx >= 0 && dx < Chunk.SIZE
                    && dz >= 0 && dz < Chunk.SIZE) {
                    placeBoulder(chunk, dx, dz, wx, height + 1, wz);
                }
            }
        }
    }

    /** Tree placement test for data-driven biomes. */
    private boolean shouldPlaceTreeData(int wx, int wz, BiomeSelector.MCBiome biome) {
        double density = treeDensity(biome);
        if (density <= 0) return false;

        // Trees only grow on grass blocks, not on stone, sand, etc.
        int height = getHeight(wx, wz);
        byte surfaceBlock = getSurfaceBlockAt(wx, wz, height);
        if (surfaceBlock != BlockType.GRASS_BLOCK.id) return false;

        double self = hashNoise(wx, 0, wz, 91);
        if (self > density * 12) return false;

        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                if (ox == 0 && oz == 0) continue;
                if (hashNoise(wx + ox, 0, wz + oz, 91) < self) return false;
            }
        }
        return true;
    }

    /**
     * Get the surface block type at a column.
     */
    private byte getSurfaceBlockAt(int wx, int wz, int height) {
        BiomeSelector.MCBiome biome = getMCBiome(wx, wz);
        return surfaceBlockData(biome, wx, height, wz, height);
    }

    private double treeDensity(BiomeSelector.MCBiome biome) {
        return switch (biome) {
            case FOREST, DARK_FOREST -> 0.025;      // Reduced from 0.055
            case BIRCH_FOREST -> 0.02;              // Reduced from 0.045
            case TAIGA, OLD_GROWTH_PINE_TAIGA, OLD_GROWTH_SPRUCE_TAIGA -> 0.03; // Reduced
            case JUNGLE -> 0.04;                     // Reduced from 0.06
            case SWAMP, MOOR -> 0.015;
            case PLAINS -> 0.002;
            case SAVANNA -> 0.003;
            case WINDSWEPT_FOREST -> 0.008;
            case AUTUMN_FOREST -> 0.025;             // Reduced
            case PETRIFIED_FOREST -> 0.02;
            case SHATTERED_SAVANNA -> 0.005;
            default -> 0.0;
        };
    }

    private String treeType(BiomeSelector.MCBiome biome) {
        return switch (biome) {
            case FOREST, DARK_FOREST, WOODED_BADLANDS, WINDSWEPT_FOREST -> "oak";
            case BIRCH_FOREST -> "birch";
            case TAIGA, OLD_GROWTH_PINE_TAIGA, OLD_GROWTH_SPRUCE_TAIGA -> "spruce";
            case JUNGLE -> "jungle";
            case SWAMP, MOOR -> "oak";
            case SAVANNA, SAVANNA_PLATEAU, SHATTERED_SAVANNA -> "acacia";
            case PLAINS -> "oak";
            case AUTUMN_FOREST -> "autumn_oak";
            case PETRIFIED_FOREST -> "petrified";
            default -> "none";
        };
    }

    /**
     * Deterministic per-column test: same coordinate always gives the same
     * answer, no matter which chunk is asking.
     */
    private boolean shouldPlaceTree(int wx, int wz, Biome biome) {
        double density = switch (biome) {
            case FOREST -> 0.055;
            case BIRCH -> 0.045;
            case TAIGA -> 0.050;
            case SWAMP -> 0.020;
            case PLAINS -> 0.005;
            case MOUNTAINS -> 0.010;
            case DESERT -> 0.004;   // cactus
            default -> 0.0;
        };
        if (density <= 0) return false;

        // Enforce spacing: a tree may only root on a local maximum of a
        // scatter field, which keeps trunks from touching
        double self = hashNoise(wx, 0, wz, 91);
        if (self > density * 12) return false;

        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                if (ox == 0 && oz == 0) continue;
                if (hashNoise(wx + ox, 0, wz + oz, 91) < self) return false;
            }
        }
        return true;
    }

    /**
     * Trunks are written as a strictly vertical run at a single (x,z), so
     * they can never slant. Only the parts landing inside this chunk are
     * written; the neighbouring chunk writes its own share.
     */
    private void placeTree(Chunk chunk, int originX, int originZ,
                           int wx, int baseY, int wz, Biome biome) {
        if (biome == Biome.DESERT) {
            placeCactus(chunk, originX, originZ, wx, baseY, wz);
            return;
        }

        int variant = (int) (hashNoise(wx, 1, wz, 33) * 3);

        byte log, leaves;
        int trunkHeight;
        boolean conical = false;

        switch (biome) {
            case TAIGA -> {
                log = BlockType.SPRUCE_LOG.id;
                leaves = BlockType.SPRUCE_LEAVES.id;
                trunkHeight = 7 + variant * 2;
                conical = true;
            }
            case BIRCH -> {
                log = BlockType.BIRCH_LOG.id;
                leaves = BlockType.BIRCH_LEAVES.id;
                trunkHeight = 6 + variant;
            }
            case SWAMP -> {
                log = BlockType.OAK_LOG.id;
                leaves = BlockType.OAK_LEAVES.id;
                trunkHeight = 5 + variant;
            }
            default -> {
                log = BlockType.OAK_LOG.id;
                leaves = BlockType.OAK_LEAVES.id;
                trunkHeight = 4 + variant;
            }
        }

        if (baseY + trunkHeight + 3 >= Chunk.HEIGHT) return;

        // Canopy first so the trunk overwrites any leaf in its own column
        if (conical) {
            buildSpruceCanopy(chunk, originX, originZ, wx, baseY, wz, trunkHeight, leaves);
        } else {
            buildRoundCanopy(chunk, originX, originZ, wx, baseY, wz, trunkHeight, leaves);
        }

        for (int y = 0; y < trunkHeight; y++) {
            setWorld(chunk, originX, originZ, wx, baseY + y, wz, log, true);
        }

        // Podzol under spruce, matching the real taiga floor
        if (conical) {
            setWorld(chunk, originX, originZ, wx, baseY - 1, wz, BlockType.PODZOL.id, true);
        }
    }

    private void buildRoundCanopy(Chunk chunk, int originX, int originZ,
                                  int wx, int baseY, int wz, int trunkHeight, byte leaves) {
        int top = baseY + trunkHeight;

        for (int dy = -3; dy <= 1; dy++) {
            int y = top + dy;
            // Radius tapers towards the top of the crown
            int radius = switch (dy) {
                case 1 -> 1;
                case 0 -> 2;
                default -> (dy == -3) ? 2 : 3;
            };

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 1) continue;
                    // Trim the corners irregularly
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius
                        && hashNoise(wx + dx, y, wz + dz, 7) > 0.45) continue;

                    setWorld(chunk, originX, originZ, wx + dx, y, wz + dz, leaves, false);
                }
            }
        }
    }

    private void buildSpruceCanopy(Chunk chunk, int originX, int originZ,
                                   int wx, int baseY, int wz, int trunkHeight, byte leaves) {
        int top = baseY + trunkHeight;

        // Stacked rings that widen towards the bottom
        int radius = 0;
        for (int y = top + 1; y >= baseY + 2; y--) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 1) continue;
                    setWorld(chunk, originX, originZ, wx + dx, y, wz + dz, leaves, false);
                }
            }
            // Widen every other layer, reset periodically for the tiered look
            radius++;
            if (radius > 2) radius = 1;
        }
    }

    private void placeCactus(Chunk chunk, int originX, int originZ, int wx, int baseY, int wz) {
        int h = 2 + (int) (hashNoise(wx, 2, wz, 51) * 3);
        for (int y = 0; y < h; y++) {
            setWorld(chunk, originX, originZ, wx, baseY + y, wz, BlockType.CACTUS.id, true);
        }
    }

    // --- Data-driven tree placement ---

    private void placeTreeData(Chunk chunk, int originX, int originZ,
                                int wx, int baseY, int wz, BiomeSelector.MCBiome biome) {
        String type = treeType(biome);

        if (type.equals("cactus")) {
            placeCactus(chunk, originX, originZ, wx, baseY, wz);
            return;
        }
        if (type.equals("mushroom")) {
            placeMushroom(chunk, originX, originZ, wx, baseY, wz);
            return;
        }
        if (type.equals("charred")) {
            placeCharredStump(chunk, originX, originZ, wx, baseY, wz);
            return;
        }
        if (type.equals("petrified")) {
            placePetrifiedLog(chunk, originX, originZ, wx, baseY, wz);
            return;
        }

        int variant = (int) (hashNoise(wx, 1, wz, 33) * 3);
        byte log, leaves;
        int trunkHeight;
        boolean conical = false;
        boolean acacia = false;
        boolean jungle = false;

        switch (type) {
            case "spruce" -> {
                log = BlockType.SPRUCE_LOG.id;
                leaves = BlockType.SPRUCE_LEAVES.id;
                trunkHeight = 7 + variant * 2;
                conical = true;
            }
            case "birch" -> {
                log = BlockType.BIRCH_LOG.id;
                leaves = BlockType.BIRCH_LEAVES.id;
                trunkHeight = 6 + variant;
            }
            case "cherry" -> {
                log = BlockType.OAK_LOG.id;
                leaves = BlockType.CHERRY_LEAVES.id;
                trunkHeight = 6 + variant;
            }
            case "autumn_oak" -> {
                log = BlockType.OAK_LOG.id;
                leaves = BlockType.AUTUMN_LEAVES.id;
                trunkHeight = 5 + variant;
            }
            case "acacia" -> {
                log = BlockType.OAK_LOG.id;
                leaves = BlockType.OAK_LEAVES.id;
                trunkHeight = 6 + variant;
                acacia = true;
            }
            case "jungle" -> {
                log = BlockType.OAK_LOG.id;
                leaves = BlockType.OAK_LEAVES.id;
                trunkHeight = 10 + variant * 3;
                jungle = true;
            }
            default -> {
                log = BlockType.OAK_LOG.id;
                leaves = BlockType.OAK_LEAVES.id;
                trunkHeight = 4 + variant;
            }
        }

        if (baseY + trunkHeight + 3 >= Chunk.HEIGHT) return;

        if (conical) {
            buildSpruceCanopy(chunk, originX, originZ, wx, baseY, wz, trunkHeight, leaves);
        } else if (acacia) {
            buildAcaciaCanopy(chunk, originX, originZ, wx, baseY, wz, trunkHeight, leaves);
        } else if (jungle) {
            buildJungleCanopy(chunk, originX, originZ, wx, baseY, wz, trunkHeight, leaves);
        } else {
            buildRoundCanopy(chunk, originX, originZ, wx, baseY, wz, trunkHeight, leaves);
        }

        for (int y = 0; y < trunkHeight; y++) {
            setWorld(chunk, originX, originZ, wx, baseY + y, wz, log, true);
        }

        if (conical) {
            setWorld(chunk, originX, originZ, wx, baseY - 1, wz, BlockType.PODZOL.id, true);
        }
    }

    private void buildAcaciaCanopy(Chunk chunk, int originX, int originZ,
                                    int wx, int baseY, int wz, int trunkHeight, byte leaves) {
        int top = baseY + trunkHeight;
        // Flat, wide canopy
        for (int dy = -2; dy <= 0; dy++) {
            int y = top + dy;
            int radius = 3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > radius) continue;
                    setWorld(chunk, originX, originZ, wx + dx, y, wz + dz, leaves, false);
                }
            }
        }
    }

    private void buildJungleCanopy(Chunk chunk, int originX, int originZ,
                                    int wx, int baseY, int wz, int trunkHeight, byte leaves) {
        int top = baseY + trunkHeight;
        // Large round canopy
        for (int dy = -4; dy <= 1; dy++) {
            int y = top + dy;
            int radius = (dy >= -1) ? 2 : 4;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 2) continue;
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius
                            && hashNoise(wx + dx, y, wz + dz, 7) > 0.4) continue;
                    setWorld(chunk, originX, originZ, wx + dx, y, wz + dz, leaves, false);
                }
            }
        }
    }

    private void placeMushroom(Chunk chunk, int originX, int originZ, int wx, int baseY, int wz) {
        int h = 4 + (int) (hashNoise(wx, 2, wz, 51) * 3);
        for (int y = 0; y < h; y++) {
            setWorld(chunk, originX, originZ, wx, baseY + y, wz, BlockType.OAK_LOG.id, true);
        }
        // Cap
        int top = baseY + h;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                setWorld(chunk, originX, originZ, wx + dx, top, wz + dz, BlockType.CHARRED_LOG_TOP.id, false);
            }
        }
    }

    private void placeCharredStump(Chunk chunk, int originX, int originZ, int wx, int baseY, int wz) {
        int h = 2 + (int) (hashNoise(wx, 2, wz, 51) * 2);
        for (int y = 0; y < h; y++) {
            setWorld(chunk, originX, originZ, wx, baseY + y, wz, BlockType.CHARRED_LOG.id, true);
        }
    }

    private void placePetrifiedLog(Chunk chunk, int originX, int originZ, int wx, int baseY, int wz) {
        int h = 3 + (int) (hashNoise(wx, 2, wz, 51) * 3);
        for (int y = 0; y < h; y++) {
            setWorld(chunk, originX, originZ, wx, baseY + y, wz, BlockType.PETRIFIED_LOG.id, true);
        }
    }

    /** Ground cover for MC 1.18+ biomes: flowers, grass, lily pads, crystals. */
    private void placeGroundCoverData(Chunk chunk, int lx, int lz, int wx, int y, int wz, BiomeSelector.MCBiome biome) {
        if (y >= Chunk.HEIGHT) return;
        if (chunk.getBlock(lx, y, lz) != BlockType.AIR.id) return;

        byte ground = chunk.getBlock(lx, y - 1, lz);
        double r = hashNoise(wx, 3, wz, 23);

        // Desert: dead bushes only
        if (biome == BiomeSelector.MCBiome.DESERT) {
            if (ground == BlockType.SAND.id && r > 0.995) {
                chunk.setBlock(lx, y, lz, BlockType.DEAD_BUSH.id);
            }
            return;
        }

        // Lily pads on water (swamp/moor)
        if ((biome == BiomeSelector.MCBiome.SWAMP || biome == BiomeSelector.MCBiome.MOOR)
                && ground == BlockType.WATER.id && r > 0.96) {
            chunk.setBlock(lx, y, lz, BlockType.LILY_PAD.id);
        }

        // Flowers (lavender meadow, flower forest)
        if ((biome == BiomeSelector.MCBiome.LAVENDER_MEADOW || biome == BiomeSelector.MCBiome.FLOWER_FOREST)
                && ground == BlockType.GRASS_BLOCK.id && r > 0.93) {
            byte flower = biome == BiomeSelector.MCBiome.LAVENDER_MEADOW
                ? BlockType.LAVENDER.id
                : (hashNoise(wx, 4, wz, 29) > 0.5 ? BlockType.DANDELION.id : BlockType.POPPY.id);
            chunk.setBlock(lx, y, lz, flower);
            return;
        }

        // Tall grass
        if (grassBiome(biome) && (ground == BlockType.GRASS_BLOCK.id || ground == BlockType.PODZOL.id) && r > 0.85) {
            chunk.setBlock(lx, y, lz, BlockType.GRASS_PLANT.id);
        }

        // Wheat crops in plains and temperate biomes
        if ((biome == BiomeSelector.MCBiome.PLAINS || biome == BiomeSelector.MCBiome.FOREST
             || biome == BiomeSelector.MCBiome.SAVANNA)
                && ground == BlockType.GRASS_BLOCK.id && r > 0.96) {
            // Random growth stage (0-3)
            int stage = (int)(hashNoise(wx, 10, wz, 17) * 4);
            stage = Math.max(0, Math.min(3, stage));
            // Wheat uses stage as block data (simplified: place mature wheat most of the time)
            if (hashNoise(wx, 5, wz, 13) > 0.7) {
                chunk.setBlock(lx, y, lz, BlockType.WHEAT.id);
            }
        }

        // Crystals on stone in crystal peaks
        if (biome == BiomeSelector.MCBiome.CRYSTAL_PEAKS && ground == BlockType.BASALT.id && r > 0.97) {
            chunk.setBlock(lx, y, lz, BlockType.CRYSTAL.id);
        }
    }

    private boolean grassBiome(BiomeSelector.MCBiome biome) {
        return switch (biome) {
            case PLAINS, FOREST, BIRCH_FOREST, DARK_FOREST, TAIGA,
                 SAVANNA, JUNGLE, SWAMP, MOOR, AUTUMN_FOREST,
                 LAVENDER_MEADOW, FLOWER_FOREST, SHATTERED_SAVANNA -> true;
            default -> false;
        };
    }

    /** Flowers, tall grass, dead bushes and snow caps. */
    private void placeGroundCover(Chunk chunk, int lx, int lz, int wx, int y, int wz, Biome biome) {
        if (y >= Chunk.HEIGHT) return;
        if (chunk.getBlock(lx, y, lz) != BlockType.AIR.id) return;

        byte ground = chunk.getBlock(lx, y - 1, lz);
        double r = hashNoise(wx, 3, wz, 23);

        if (biome == Biome.DESERT) {
            if (ground == BlockType.SAND.id && r > 0.995) {
                chunk.setBlock(lx, y, lz, BlockType.DEAD_BUSH.id);
            }
            return;
        }

        if (ground != BlockType.GRASS_BLOCK.id && ground != BlockType.PODZOL.id) return;

        if (r > 0.88) {
            chunk.setBlock(lx, y, lz, BlockType.GRASS_PLANT.id);
        } else if (r > 0.868) {
            double which = hashNoise(wx, 4, wz, 29);
            chunk.setBlock(lx, y, lz,
                which > 0.5 ? BlockType.DANDELION.id : BlockType.POPPY.id);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Write a block using world coordinates, silently skipping anything that
     * falls outside the chunk currently being built.
     *
     * @param overwrite when false, only air is replaced (used for leaves so
     *                  they never eat an existing trunk)
     */
    private void setWorld(Chunk chunk, int originX, int originZ,
                          int wx, int y, int wz, byte block, boolean overwrite) {
        int lx = wx - originX;
        int lz = wz - originZ;

        if (lx < 0 || lx >= Chunk.SIZE || lz < 0 || lz >= Chunk.SIZE) return;
        if (y < 0 || y >= Chunk.HEIGHT) return;

        if (!overwrite && chunk.getBlock(lx, y, lz) != BlockType.AIR.id) return;

        chunk.setBlock(lx, y, lz, block);
    }

    /** Stable hash in 0..1 for a world position plus a salt. */
    private double hashNoise(int x, int y, int z, int salt) {
        long h = seed;
        h = h * 6364136223846793005L + x * 341873128712L;
        h = h * 6364136223846793005L + y * 132897987541L;
        h = h * 6364136223846793005L + z * 1274126177L;
        h = h * 6364136223846793005L + salt * 2654435761L;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return ((h >>> 11) & 0x1FFFFFFFFFFFFFL) / (double) 0x1FFFFFFFFFFFFFL;
    }

    /** Map roughly -1..1 into 0..1. */
    private static double norm(double v) {
        return Math.max(0.0, Math.min(1.0, (v + 1.0) * 0.5));
    }

    /**
     * Place a boulder (cluster of stone/gravel) on mountain slopes.
     * Uses deterministic hash so boulders are reproducible per position.
     */
    private void placeBoulder(Chunk chunk, int lx, int lz, int wx, int wy, int wz) {
        double hash = hashNoise(wx, 100, wz, 50);
        if (hash > 0.15) return; // ~15% of eligible columns get a boulder

        int radius = 1 + (int)(hashNoise(wx, 200, wz, 30) * 2);
        int height = 1 + (int)(hashNoise(wx, 300, wz, 40) * 2);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy <= height; dy++) {
                    int bx = wx + dx;
                    int bz = wz + dz;
                    int by = wy + dy;

                    // Check bounds and only place on solid blocks
                    if (by >= Chunk.HEIGHT) continue;
                    int blx = bx - chunk.getWorldX();
                    int blz = bz - chunk.getWorldZ();
                    if (blx < 0 || blx >= Chunk.SIZE || blz < 0 || blz >= Chunk.SIZE) continue;

                    // Spherical shape check
                    double dist = Math.sqrt(dx*dx + dz*dz + dy*dy*2);
                    if (dist > radius + 0.5) continue;

                    byte existing = chunk.getBlock(blx, by, blz);
                    if (existing == BlockType.AIR.id || existing == BlockType.WATER.id) {
                        // Place stone or gravel based on hash
                        byte boulderBlock = (hashNoise(bx, by, bz, 70) > 0.5)
                            ? BlockType.STONE.id : BlockType.GRAVEL.id;
                        chunk.setBlock(blx, by, blz, boulderBlock);
                    }
                }
            }
        }
    }
}
