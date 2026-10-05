package com.voxelgame.debug;

import com.voxelgame.item.ItemRegistry;
import com.voxelgame.item.ItemStack;
import com.voxelgame.item.RecipeRegistry;
import com.voxelgame.rendering.DayNightCycle;
import com.voxelgame.world.BlockType;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.World;
import com.voxelgame.world.container.ContainerData;
import com.voxelgame.world.entity.AsteroidEntity;
import com.voxelgame.world.entity.Animal;
import com.voxelgame.world.entity.Villager;
import com.voxelgame.world.entity.Zoloy;
import com.voxelgame.world.save.WorldMeta;
import com.voxelgame.world.save.WorldSave;
import com.voxelgame.player.Player;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Headless test suite: exercises world logic without a window or OpenGL.
 *
 * Run with:
 *   java com.voxelgame.Main --headless [--seed N]
 *
 * Every check is logged to stdout and to {@code debug/headless_report.txt}.
 * The process exits with code 0 when all checks pass, 1 otherwise.
 */
public final class HeadlessTestRunner {

    private static final Path REPORT = Paths.get("debug", "headless_report.txt");

    private static int passed = 0;
    private static int failed = 0;

    private HeadlessTestRunner() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        long seed = 424242L;
        for (int i = 1; i < args.length - 1; i++) {
            if ("--seed".equals(args[i])) {
                try { seed = Long.parseLong(args[i + 1]); } catch (NumberFormatException e) {}
            }
        }

        long start = System.currentTimeMillis();
        log("=== Headless test run (seed=" + seed + ") ===");

        try {
            runSuite(seed);
        } catch (Throwable t) {
            t.printStackTrace();
            check(false, "suite crashed", String.valueOf(t));
        }

        long seconds = (System.currentTimeMillis() - start) / 1000;
        log("=== RESULT: " + passed + " passed, " + failed + " failed (" + seconds + "s) ===");
        writeReport();
        return failed == 0 ? 0 : 1;
    }

    // ------------------------------------------------------------------

    private static void runSuite(long seed) throws Exception {
        World world = null;
        WorldSave save = null;
        WorldMeta meta = null;
        try {
            world = new World(seed);
            meta = new WorldMeta("Headless_" + seed, seed, WorldMeta.GameMode.SURVIVAL, false);
            save = new WorldSave(meta);

            testGeneration(world, seed);
            testBlockEdits(world);
            testSaveLoadRoundtrip(world, save, meta);
            testCropGrowth(world);
            testFarmerHarvest(world);
            testContainers(world, save);
            testPlayerPhysics(world);
            testAsteroidImpact(world);
            testVillagerTraversal(world);
            testMobTraversal(world);
            testAnimals(world);
            testAnimalsV2(world);
            testBed(world);
            testDayNightCycle();
            testItemsAndCrafting();
            testXpAndEnchanting(world);
            testWeather();
            testTransparency(world);
            testWorldList(meta);
            testStatistics(save, meta);
            testFishing(world, save, meta);
        } finally {
            if (world != null) {
                try { world.cleanup(); } catch (Throwable ignored) {}
            }
            if (meta != null) {
                try { WorldSave.delete(meta.folderName); } catch (Throwable ignored) {}
            }
        }
    }

    private static void testGeneration(World world, long seed) throws Exception {
        log("-- test: world generation");
        world.setRenderDistance(4);
        Vector3f center = new Vector3f(8.5f, 70f, 8.5f);
        world.preloadChunks(center, 4);
        boolean loaded = waitForChunks(world, center, 120_000);

        check(loaded, "chunks finished loading",
            "pending=" + world.getLoader().getPendingCount());
        check(world.getChunk(0, 0) != null, "chunk (0,0) exists", "");

        int bedrock = world.getBlock(8, 0, 8);
        check(bedrock == BlockType.BEDROCK.id, "bedrock at y=0",
            "got " + BlockType.fromId(bedrock).name);

        int ground = world.getGroundHeight(8, 8);
        check(ground > 5 && ground < 200, "ground height sane", "ground=" + ground);
        check(world.isSolid(8, ground, 8), "surface block solid",
            "y=" + ground + " type=" + BlockType.fromId(world.getBlock(8, ground, 8)).name);
        check(BlockType.fromId(world.getBlock(8, ground + 3, 8)) == BlockType.AIR,
            "air above surface", "");

        String biome = world.getBiomeDisplayName(8, 8);
        check(biome != null && !biome.isEmpty(), "biome lookup works", String.valueOf(biome));

        log("gen: seed=" + seed + " chunks=" + world.getChunks().size()
            + " ground@spawn=" + ground);
    }

    private static void testBlockEdits(World world) {
        log("-- test: block edits");
        int ground = world.getGroundHeight(8, 8);
        int y = ground + 1;

        world.setBlock(8, y, 8, BlockType.DIAMOND_BLOCK.id);
        check(world.getBlock(8, y, 8) == BlockType.DIAMOND_BLOCK.id,
            "setBlock/getBlock round-trip",
            "got " + BlockType.fromId(world.getBlock(8, y, 8)).name);
        check(world.isSolid(8, y, 8), "placed block is solid", "");

        world.setBlock(8, y, 8, BlockType.AIR.id);
        check(world.getBlock(8, y, 8) == BlockType.AIR.id, "block removed", "");
    }

    private static void testSaveLoadRoundtrip(World world, WorldSave save, WorldMeta meta) throws Exception {
        log("-- test: save/load round-trip");
        world.setSave(save);
        save.saveMeta();

        int ground = world.getGroundHeight(8, 8);
        int y = ground + 1;
        world.setBlock(8, y, 8, BlockType.DIAMOND_BLOCK.id);
        world.setBlock(10, y, 10, BlockType.OAK_LOG.id);

        // [CR] Crop growth stages must survive the round-trip too
        world.setBlock(12, y, 12, BlockType.WHEAT.id);
        world.setCropStage(12, y, 12, 5);

        int flushed = world.flushToDisk();
        check(flushed > 0, "flushToDisk writes chunks", "flushed=" + flushed);

        Chunk fromDisk = save.loadChunk(0, 0);
        check(fromDisk != null, "loadChunk returns chunk", "");
        if (fromDisk == null) return;

        Chunk inMem = world.getChunk(0, 0);
        boolean blocksEqual = inMem != null
            && Arrays.equals(inMem.getBlocks(), fromDisk.getBlocks());
        check(blocksEqual, "chunk blocks identical after re-load",
            "inMem=" + (inMem == null ? "null" : inMem.getChunkX() + "," + inMem.getChunkZ()));

        check(fromDisk.getBlock(8, y, 8) == BlockType.DIAMOND_BLOCK.id,
            "edited block survived save/load",
            "got " + BlockType.fromId(fromDisk.getBlock(8, y, 8)).name);

        check(fromDisk.getBlock(12, y, 12) == BlockType.WHEAT.id,
            "crop block survived save/load", "");
        check(fromDisk.getCropStage(12, y, 12) == 5,
            "crop growth stage survived save/load",
            "stage=" + fromDisk.getCropStage(12, y, 12));
    }

    /** [CR] Crops grow over time, cap at stage 7, and need soil + light. */
    private static void testCropGrowth(World world) {
        log("-- test: crop growth");
        // Use a column away from the (8,8) sandbox other tests rely on,
        // and give it real dirt so the soil check passes.
        int cx = 24, cz = 24;
        int ground = world.getGroundHeight(cx, cz);
        world.setBlock(cx, ground, cz, BlockType.DIRT.id);
        world.setBlock(cx, ground + 1, cz, BlockType.WHEAT.id);
        world.setCropStage(cx, ground + 1, cz, 0);

        check(world.getCropStage(cx, ground + 1, cz) == 0, "fresh crop starts at stage 0", "");

        boolean grew = false;
        for (int i = 0; i < 80 && !grew; i++) {
            world.updateCrops(2.0f, 0, 0);
            grew = world.getCropStage(cx, ground + 1, cz) > 0;
        }
        check(grew, "crop advances a stage over time",
            "stage=" + world.getCropStage(cx, ground + 1, cz));

        for (int i = 0; i < 200; i++) {
            world.updateCrops(2.0f, 0, 0);
        }
        int stage = world.getCropStage(cx, ground + 1, cz);
        check(stage == 7, "crop matures at stage 7", "stage=" + stage);

        // Solid but not soil below: a crop on glass must not grow
        int fx = 28, fz = 8;
        int fground = world.getGroundHeight(fx, fz);
        world.setBlock(fx, fground + 1, fz, BlockType.WHEAT.id);
        world.setBlock(fx, fground, fz, BlockType.GLASS.id);
        world.setCropStage(fx, fground + 1, fz, 0);
        for (int i = 0; i < 40; i++) {
            world.updateCrops(2.0f, 0, 0);
        }
        check(world.getCropStage(fx, fground + 1, fz) == 0,
            "crop without soil does not grow",
            "stage=" + world.getCropStage(fx, fground + 1, fz));
    }

    /** [CR] The farmer harvests a mature crop and replants it (stage 0). */
    private static void testFarmerHarvest(World world) {
        log("-- test: farmer harvests and replants");
        int ground = world.getGroundHeight(16, 16);
        int y = ground + 1;
        world.setBlock(16, y, 16, BlockType.WHEAT.id);
        world.setCropStage(16, y, 16, 7);

        Villager farmer = new Villager(world, 14.5f, ground + 1.0f, 16.5f,
            Villager.Profession.FARMER);
        farmer.setWorkstation(14.5f, ground + 1.0f, 15.5f);
        farmer.setStorage(20.0f, ground + 1.0f, 20.0f);
        world.getVillagers().add(farmer);

        Player tmp = new Player(new Vector3f(8.5f, ground + 2f, 8.5f), world);
        for (int i = 0; i < 2000; i++) {
            world.updateVillagers(1.0f / 60.0f, tmp, 6000);
        }

        check(farmer.getFarmerCropCount() >= 1,
            "farmer collected the mature crop",
            "crops=" + farmer.getFarmerCropCount());

        check(world.getBlock(16, y, 16) == BlockType.WHEAT.id,
            "farmer replanted the crop cell", "");
        int stage = world.getCropStage(16, y, 16);
        check(stage >= 0 && stage < 7, "replanted crop is young again",
            "stage=" + stage);
    }

    /** [CF] Chest/furnace contents and furnace state survive a save/load round-trip. */
    private static void testContainers(World world, WorldSave save) throws Exception {
        log("-- test: container save/load + smelting");
        int ground = world.getGroundHeight(20, 20);
        int y = ground + 1;

        // Chest with a block stack and an item stack
        world.setBlock(20, y, 20, BlockType.CHEST.id);
        ContainerData chest = world.getContainerManager()
            .getOrCreate(20, y, 20, ContainerData.Type.CHEST);
        chest.setSlot(0, new ItemStack(ItemRegistry.DIAMOND, 3));
        chest.setSlot(5, new ItemStack(BlockType.OAK_LOG, 2));

        // Furnace mid-smelt: iron ore + coal, a few ticks of progress
        world.setBlock(22, y, 20, BlockType.FURNACE.id);
        ContainerData furnace = world.getContainerManager()
            .getOrCreate(22, y, 20, ContainerData.Type.FURNACE);
        furnace.setSlot(ContainerData.FURNACE_INPUT,
            new ItemStack(BlockType.IRON_ORE, 1));
        furnace.setSlot(ContainerData.FURNACE_FUEL,
            new ItemStack(ItemRegistry.COAL, 1));
        for (int i = 0; i < 50; i++) {
            world.updateContainers();
        }
        int progress = furnace.getSmeltTime();
        check(progress > 0, "furnace smelting progressed", "smeltTime=" + progress);
        check(furnace.getFuelTime() > 0, "furnace fuel burning",
            "fuelTime=" + furnace.getFuelTime());

        int flushed = world.flushToDisk();
        check(flushed > 0, "flushToDisk writes container chunks", "flushed=" + flushed);

        // Both containers sit in chunk (1,1): reload and restore them
        Chunk fromDisk = save.loadChunk(1, 1);
        check(fromDisk != null, "loadChunk(1,1) returns chunk", "");
        java.util.List<Object[]> pending =
            fromDisk == null ? null : fromDisk.takePendingContainers();
        check(pending != null && pending.size() == 2,
            "two containers restored from disk",
            "count=" + (pending == null ? "null" : pending.size()));

        if (pending != null) {
            for (Object[] c : pending) {
                int x = 16 + (Integer) c[0];
                int z = 16 + (Integer) c[2];
                ContainerData cd = (ContainerData) c[3];
                if (x == 20 && z == 20) {
                    check(cd.type == ContainerData.Type.CHEST, "chest type restored", "");
                    ItemStack s0 = cd.getSlot(0);
                    check(s0 != null && s0.isItem() && s0.getItem() == ItemRegistry.DIAMOND
                            && s0.getCount() == 3,
                        "chest item stack survived", "slot0=" + (s0 == null ? "null" : s0.getCount()));
                    ItemStack s5 = cd.getSlot(5);
                    check(s5 != null && s5.isBlock() && s5.getBlockType() == BlockType.OAK_LOG
                            && s5.getCount() == 2,
                        "chest block stack survived",
                        "slot5=" + (s5 == null ? "null" : s5.getCount()));
                } else if (x == 22 && z == 20) {
                    check(cd.type == ContainerData.Type.FURNACE, "furnace type restored", "");
                    check(cd.getFuelTime() == furnace.getFuelTime(),
                        "furnace fuel time survived",
                        "saved=" + furnace.getFuelTime() + " loaded=" + cd.getFuelTime());
                    check(cd.getSmeltTime() == progress,
                        "furnace smelt progress survived",
                        "saved=" + progress + " loaded=" + cd.getSmeltTime());
                } else {
                    check(false, "unexpected container position", x + "," + z);
                }
            }
        }

        // A full smelt: ore + coal in a fresh furnace yields an iron ingot
        ContainerData smelt = world.getContainerManager()
            .getOrCreate(24, y, 20, ContainerData.Type.FURNACE);
        smelt.setSlot(ContainerData.FURNACE_INPUT,
            new ItemStack(BlockType.IRON_ORE, 1));
        smelt.setSlot(ContainerData.FURNACE_FUEL,
            new ItemStack(ItemRegistry.COAL, 1));
        for (int i = 0; i < 400; i++) {
            world.updateContainers();
        }
        ItemStack out = smelt.getSlot(ContainerData.FURNACE_OUTPUT);
        check(out != null && out.isItem() && out.getItem() == ItemRegistry.IRON_INGOT
                && out.getCount() == 1,
            "furnace smelted iron ore into an ingot",
            "out=" + (out == null ? "null" : out.getCount()));

        // Breaking a container forgets its data (the drop itself is Game-side)
        world.getContainerManager().remove(20, y, 20);
        check(!world.getContainerManager().hasContainer(20, y, 20),
            "removed container is forgotten", "");
    }

    private static void testPlayerPhysics(World world) {
        log("-- test: player physics (fall + land)");
        int ground = world.getGroundHeight(8, 8);
        Player p = new Player(new Vector3f(8.5f, ground + 5f, 8.5f), world);

        boolean landed = false;
        for (int i = 0; i < 600; i++) {
            world.update(p.getPosition());
            p.update(1.0 / 60.0);
            if (p.isOnGround()) {
                landed = true;
                break;
            }
        }

        float y = p.getPosition().y;
        check(landed, "player lands within 10s", "y=" + y);
        check(y >= ground + 0.9f && y <= ground + 1.6f, "rests on the surface",
            "y=" + y + " ground=" + ground);
    }

    /** [AST] Drop an asteroid and check it carves a crater with fire. */
    private static void testAsteroidImpact(World world) throws Exception {
        log("-- test: asteroid fall + crater + panic");
        // Chunk (0,0) is already loaded by the earlier generation test.
        // The rock drifts while falling, so snapshot the whole window.
        int gx = 8, gz = 8;
        int[] before = new int[21 * 21];
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                before[(dx + 10) * 21 + (dz + 10)] =
                    world.getGroundHeight(gx + dx, gz + dz);
            }
        }

        // A villager outside the blast radius but inside the panic radius
        int vgx = gx + 12, vgz = gz + 12;
        int vgy = world.getGroundHeight(vgx, vgz);
        Villager villager = new Villager(world, vgx + 0.5f, vgy + 1.0f, vgz + 0.5f,
            Villager.Profession.FARMER);
        world.getVillagers().add(villager);

        world.spawnAsteroidAt(gx, gz, 2);
        AsteroidEntity rock = world.getAsteroids().isEmpty()
            ? null : world.getAsteroids().get(0);
        check(rock != null && rock.getParts().size() > 3,
            "asteroid is a boulder of blocks",
            "parts=" + (rock == null ? 0 : rock.getParts().size()));

        for (int i = 0; i < 900; i++) {
            world.updateAsteroids(1.0f / 60.0f, null);
            if (world.getAsteroids().isEmpty()) break;
        }
        check(world.getAsteroids().isEmpty(), "asteroid impacts and despawns", "");

        check(villager.getState() == Villager.State.FLEE,
            "villager panics when the asteroid falls",
            "state=" + villager.getState());
        check(AsteroidEntity.consumeShakeImpulse() > 0,
            "impact queues an earthquake shake", "");

        float vx0 = villager.getPosition().x;
        float vz0 = villager.getPosition().z;
        Player tmp = new Player(new Vector3f(gx + 0.5f, vgy + 2f, gz + 0.5f), world);
        for (int i = 0; i < 150; i++) {
            world.updateVillagers(1.0f / 60.0f, tmp, 6000);
        }
        float dist0 = (float) Math.sqrt((vx0 - gx) * (vx0 - gx) + (vz0 - gz) * (vz0 - gz));
        float dist1 = (float) Math.sqrt(
            (villager.getPosition().x - gx) * (villager.getPosition().x - gx)
                + (villager.getPosition().z - gz) * (villager.getPosition().z - gz));
        check(dist1 > dist0 + 0.3f, "villager runs away from the crater",
            "dist " + String.format("%.1f -> %.1f", dist0, dist1));

        int rimBlocks = 0;
        int sunkCells = 0;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                int gy = world.getGroundHeight(gx + dx, gz + dz);
                if (world.getBlock(gx + dx, gy, gz + dz) == BlockType.COBBLESTONE.id) {
                    rimBlocks++;
                }
                if (gy + 2 <= before[(dx + 10) * 21 + (dz + 10)]) {
                    sunkCells++;
                }
            }
        }
        check(rimBlocks > 8, "crater carved with a cobblestone rim", "rim=" + rimBlocks);
        check(sunkCells > 8, "terrain sank into a crater", "sunk=" + sunkCells);

        check(!world.getFireBlocks().isEmpty(), "fire burns in the crater",
            "fires=" + world.getFireBlocks().size());
    }

    /**
     * [CF] Р–РёС‚РµР»Рё РїРµСЂРµС€Р°РіРёРІР°СЋС‚ Р±Р»РѕРє РІС‹СЃРѕС‚РѕР№ 1 (Р°РІС‚Рѕ-С€Р°Рі) Рё РІС‹РїСЂС‹РіРёРІР°СЋС‚
     * РёР· СЏРјС‹ РіР»СѓР±РёРЅРѕР№ 2: РґР»СЏ СЌС‚РѕРіРѕ СЃС‚Р°РІРёРј РґРѕРј Р·Р° СЃС‚РµРЅРѕР№/СЏРјРѕР№ Рё РїСѓРіР°РµРј
     * РјРѕРЅСЃС‚СЂРѕРј вЂ” Р¶РёС‚РµР»СЊ Р±РµР¶РёС‚ РґРѕРјРѕР№ С‡РµСЂРµР· РїСЂРµРїСЏС‚СЃС‚РІРёРµ.
     */
    private static void testVillagerTraversal(World world) {
        log("-- test: villager climbs steps and climbs out of pits");

        // РџР»РѕСЃРєР°СЏ РїР»РѕС‰Р°РґРєР°, С‡С‚РѕР±С‹ Р¶РёС‚РµР»СЏ РЅРёС‡С‚Рѕ РЅРµ РѕС‚РІР»РµРєР°Р»Рѕ
        int flatX = -1, flatZ = -1, gy = -1;
        outer:
        for (int cx = 2; cx <= 4; cx++) {
            for (int cz = 2; cz <= 4; cz++) {
                for (int dx = 0; dx < 16; dx += 4) {
                    for (int dz = 0; dz < 16; dz += 4) {
                        int x = cx * 16 + dx, z = cz * 16 + dz;
                        int h = world.getGroundHeight(x, z);
                        for (int a = -3; a <= 3; a++) {
                            for (int b = -3; b <= 3; b++) {
                                if (Math.abs(world.getGroundHeight(x + a, z + b) - h) > 1) {
                                    continue outer;
                                }
                            }
                        }
                        flatX = x;
                        flatZ = z;
                        gy = h;
                        break outer;
                    }
                }
            }
        }
        check(flatX > 0, "found a flat test area",
            "x=" + flatX + " z=" + flatZ + " gy=" + gy);
        if (flatX <= 0) return;

        // Р Р°СЃС‡РёС‰Р°РµРј РІРѕР·РґСѓС… РЅР°Рґ РєРѕСЂРёРґРѕСЂРѕРј: РєСЂРѕРЅС‹ РґРµСЂРµРІСЊРµРІ Рё РЅРёР¶РЅРёРµ РєР»РµС‚РєРё
        // СЃС‚РІРѕР»РѕРІ РЅРµ РґРѕР»Р¶РЅС‹ РјРµС€Р°С‚СЊ С€Р°РіСѓ/РїСЂС‹Р¶РєСѓ
        for (int y = gy + 1; y <= gy + 8; y++) {
            for (int dx = -4; dx <= 14; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (world.isSolid(flatX + dx, y, flatZ + dz)) {
                        world.setBlock(flatX + dx, y, flatZ + dz, BlockType.AIR.id);
                    }
                }
            }
        }

        Player tmp = new Player(new Vector3f(flatX + 8.5f, gy + 2f, flatZ + 8.5f), world);

        // РЎС‚СѓРїРµРЅСЊРєР°: Р±Р»РѕРє РІС‹СЃРѕС‚РѕР№ 1 РЅР° Р·РµРјР»Рµ, Р·Р° РЅРµР№ РґРѕРј вЂ” Р±РµР¶РёС‚ РґРѕРјРѕР№ С‡РµСЂРµР· РЅРµС‘
        int sx = flatX, sz = flatZ;
        for (int dz = -1; dz <= 1; dz++) {
            world.setBlock(sx + 1, gy + 1, sz + dz, BlockType.STONE.id);
        }
        Villager climber = new Villager(world, sx + 0.5f, gy + 1.0f, sz + 0.5f,
            Villager.Profession.FARMER);
        climber.setHome(sx + 4.5f, gy + 1.0f, sz + 0.5f);
        world.getVillagers().add(climber);
        world.getMobs().add(new Zoloy(world, sx - 3.5f, gy + 1.0f, sz + 0.5f));
        for (int i = 0; i < 600; i++) {
            world.updateVillagers(1.0f / 60.0f, tmp, 6000);
        }
        float feet = climber.getPosition().y;
        check(feet > gy + 1.4f || climber.getPosition().x > sx + 1.2f,
            "villager climbed the 1-block step",
            "y=" + String.format("%.2f", feet)
                + " x=" + String.format("%.2f", climber.getPosition().x));
        world.getVillagers().remove(climber);
        world.getMobs().clear();

        // РЇРјР° РіР»СѓР±РёРЅРѕР№ 2: Р¶РёС‚РµР»СЊ РїР°РґР°РµС‚ Рё РІС‹РїСЂС‹РіРёРІР°РµС‚ С‡РµСЂРµР· Р°РІС‚Рѕ-С€Р°Рі РІ РїСЂС‹Р¶РєРµ
        int px = flatX + 6, pz = flatZ;
        world.setBlock(px, gy - 2, pz, BlockType.STONE.id); // РґРЅРѕ СЏРјС‹
        for (int dy = -1; dy <= 2; dy++) {
            world.setBlock(px, gy + dy, pz, BlockType.AIR.id);
        }
        Villager jumper = new Villager(world, px - 1.5f, gy + 1.0f, pz + 0.5f,
            Villager.Profession.FARMER);
        jumper.setHome(px + 3.5f, gy + 1.0f, pz + 0.5f);
        world.getVillagers().add(jumper);
        world.getMobs().add(new Zoloy(world, px - 5.5f, gy + 1.0f, pz + 0.5f));
        for (int i = 0; i < 1200; i++) {
            world.updateVillagers(1.0f / 60.0f, tmp, 6000);
        }
        feet = jumper.getPosition().y;
        check(feet > gy + 0.5f, "villager jumped out of the 2-deep pit",
            "y=" + String.format("%.2f", feet));
        world.getVillagers().remove(jumper);
        world.getMobs().clear();
    }

    /**
     * [CF] РњРѕР±С‹ Р»Р°Р·Р°СЋС‚ РєР°Рє РёРіСЂРѕРє: Zoloy РІС‹РїСЂС‹РіРёРІР°РµС‚ РёР· 2-Р±Р»РѕС‡РЅРѕР№ СЏРјС‹,
     * РґРѕРіРѕРЅСЏСЏ РёРіСЂРѕРєР° (A* С‚РµРїРµСЂСЊ РїР»Р°РЅРёСЂСѓРµС‚ РїРѕРґСЉС‘Рј РІ 2 Р±Р»РѕРєР°, Р° РїСЂС‹Р¶РѕРє +
     * Р°РІС‚Рѕ-С€Р°Рі РІС‹РїРѕР»РЅСЏСЋС‚ РµРіРѕ С„РёР·РёС‡РµСЃРєРё).
     */
    private static void testMobTraversal(World world) {
        log("-- test: mob climbs out of a pit");
        int flatX = -1, flatZ = -1, gy = -1;
        outer:
        for (int cx = 2; cx <= 4; cx++) {
            for (int cz = 2; cz <= 4; cz++) {
                for (int dx = 0; dx < 16; dx += 4) {
                    for (int dz = 0; dz < 16; dz += 4) {
                        int x = cx * 16 + dx, z = cz * 16 + dz;
                        int h = world.getGroundHeight(x, z);
                        for (int a = -3; a <= 3; a++) {
                            for (int b = -3; b <= 3; b++) {
                                if (Math.abs(world.getGroundHeight(x + a, z + b) - h) > 1) {
                                    continue outer;
                                }
                            }
                        }
                        flatX = x;
                        flatZ = z;
                        gy = h;
                        break outer;
                    }
                }
            }
        }
        check(flatX > 0, "found a flat test area for the mob",
            "x=" + flatX + " z=" + flatZ + " gy=" + gy);
        if (flatX <= 0) return;

        for (int y = gy + 1; y <= gy + 8; y++) {
            for (int dx = -4; dx <= 14; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (world.isSolid(flatX + dx, y, flatZ + dz)) {
                        world.setBlock(flatX + dx, y, flatZ + dz, BlockType.AIR.id);
                    }
                }
            }
        }

        // РЇРјР° РіР»СѓР±РёРЅРѕР№ 2 (РєР°Рє РІ testVillagerTraversal), РјРѕР± РЅР° РґРЅРµ,
        // РёРіСЂРѕРє РЅР° РєСЂР°СЋ вЂ” РјРѕР± РІС‹РїСЂС‹РіРёРІР°РµС‚ Рё Р±РµР¶РёС‚ Рє РЅРµРјСѓ
        int px = flatX + 6, pz = flatZ;
        world.setBlock(px, gy - 2, pz, BlockType.STONE.id);
        for (int dy = -1; dy <= 2; dy++) {
            world.setBlock(px, gy + dy, pz, BlockType.AIR.id);
        }
        Player tmp = new Player(new Vector3f(px + 3.5f, gy + 1.0f, pz + 0.5f), world);
        Zoloy mob = new Zoloy(world, px + 0.5f, gy - 1.0f, pz + 0.5f);
        world.getMobs().add(mob);
        for (int i = 0; i < 1200; i++) {
            world.updateMobs(1.0f / 60.0f, tmp);
        }
        float feet = mob.getPosition().y;
        check(feet > gy + 0.5f, "mob jumped out of the 2-deep pit",
            "y=" + String.format("%.2f", feet)
                + " x=" + String.format("%.2f", mob.getPosition().x));
        world.getMobs().clear();
    }

    /**
     * [BED] Beds: placed pairs, passability, flammability, the head half
     * dropping the bed item, the crafting recipe and the night skip.
     */
    private static void testBed(World world) {
        log("-- test: bed and sleep data");
        int fx = 2, fz = 2;
        int gy = world.getGroundHeight(fx, fz);
        for (int dy = -2; dy <= 3; dy++) {
            for (int dx = -1; dx <= 4; dx++) {
                for (int dz = -1; dz <= 4; dz++) {
                    world.setBlock(fx + dx, gy + dy, fz + dz,
                        dy < 0 ? BlockType.STONE.id : BlockType.AIR.id);
                }
            }
        }
        world.setBlock(fx, gy, fz, BlockType.BED.id);
        world.setBlock(fx + 1, gy, fz, BlockType.BED_HEAD.id);
        check(world.getBlock(fx, gy, fz) == BlockType.BED.id, "bed foot placed", "");
        check(world.getBlock(fx + 1, gy, fz) == BlockType.BED_HEAD.id, "bed head placed", "");
        check(!BlockType.isSolidFast(BlockType.BED.id), "bed is passable", "");
        check(BlockType.isFlammable(BlockType.BED.id), "bed is flammable", "");
        check(BlockType.flammability(BlockType.BED_HEAD.id) == 0.9f,
            "bed flammability is wool-like", "");

        // Head half drops the bed item, never a second bed
        com.voxelgame.item.BlockHarvest.Requirement req =
            com.voxelgame.item.BlockHarvest.get(BlockType.BED_HEAD);
        check(req != null && req.drop == BlockType.BED,
            "bed head drops the bed item", "");

        // The bed is craftable from 3 wool + 3 planks
        boolean found = false;
        for (com.voxelgame.item.Recipe r : RecipeRegistry.RECIPES) {
            ItemStack res = r.getResult();
            if (res.isBlock() && res.getBlockType() == BlockType.BED) found = true;
        }
        check(found, "bed has a crafting recipe", "");

        // Night detection and the sunrise the sleep mechanic uses
        DayNightCycle cyc = new DayNightCycle();
        cyc.setTime(0.8);
        check(cyc.isNight(), "night detected at 0.8", "");
        cyc.setTime(0.25);
        check(!cyc.isNight(), "sunrise after sleep", "");
    }

    /**
     * [GP-045] Farm animals: they stand on the ground, wander safely, flee
     * from a nearby player, breed into babies and drop loot on death.
     */
    private static void testAnimals(World world) {
        log("-- test: farm animals");
        int flatX = -1, flatZ = -1, gy = -1;
        outer:
        for (int cx = 0; cx <= 3; cx++) {
            for (int cz = 0; cz <= 3; cz++) {
                for (int dx = 1; dx < 16; dx++) {
                    for (int dz = 1; dz < 16; dz++) {
                        int x = cx * 16 + dx, z = cz * 16 + dz;
                        int h = world.getGroundHeight(x, z);
                        boolean flat = true;
                        for (int a = -3; a <= 3; a++) {
                            for (int b = -3; b <= 3; b++) {
                                if (Math.abs(world.getGroundHeight(x + a, z + b) - h) > 1) {
                                    flat = false;
                                    break;
                                }
                            }
                            if (!flat) break;
                        }
                        if (flat) {
                            flatX = x;
                            flatZ = z;
                            gy = h;
                            break outer;
                        }
                    }
                }
            }
        }
        check(flatX > 0, "found a flat area for the animals",
            "x=" + flatX + " z=" + flatZ + " gy=" + gy);
        if (flatX <= 0) return;

        // Build a guaranteed-flat platform so cliffs can't swallow the test
        for (int dy = -4; dy <= 8; dy++) {
            for (int dx = -6; dx <= 12; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    world.setBlock(flatX + dx, gy + dy, flatZ + dz,
                        dy < 0 ? BlockType.STONE.id : BlockType.AIR.id);
                }
            }
        }

        Player far = new Player(new Vector3f(flatX + 30.0f, gy + 2.0f, flatZ + 30.0f), world);

        Animal cowA = world.spawnAnimal(Animal.AnimalType.COW, flatX + 1.5f, gy + 1.0f, flatZ + 1.5f);
        Animal cowB = world.spawnAnimal(Animal.AnimalType.COW, flatX + 3.5f, gy + 1.0f, flatZ + 1.5f);
        check(world.getAnimals().size() == 2, "animals spawn", "size=" + world.getAnimals().size());

        for (int i = 0; i < 120; i++) {
            world.updateAnimals(1.0f / 60.0f, far);
        }
        check(!cowA.isDead() && !cowB.isDead(), "animals survive walking", "");
        check(Math.abs(cowA.getPosition().y - gy) < 0.5f,
            "animal stands on the ground",
            "y=" + String.format("%.2f", cowA.getPosition().y) + " ground=" + gy);

        // Breeding: feeding a ready adult pair spawns a baby at the midpoint
        boolean bred = cowA.tryBreed(world, far);
        check(bred, "breeding succeeds with a ready pair", "");
        Animal baby = null;
        for (Animal a : world.getAnimals()) {
            if (a.isBaby()) baby = a;
        }
        check(baby != null, "breeding spawns a baby", "");

        // Fleeing: a nearby player scares the animal away
        Player close = new Player(new Vector3f(flatX + 1.5f, gy + 2.0f, flatZ + 3.5f), world);
        float dx0 = cowA.getPosition().x - flatX - 1.5f;
        float dz0 = cowA.getPosition().z - flatZ - 3.5f;
        float d0 = (float) Math.sqrt(dx0 * dx0 + dz0 * dz0);
        for (int i = 0; i < 120; i++) {
            world.updateAnimals(1.0f / 60.0f, close);
        }
        float dx1 = cowA.getPosition().x - flatX - 1.5f;
        float dz1 = cowA.getPosition().z - flatZ - 3.5f;
        float d1 = (float) Math.sqrt(dx1 * dx1 + dz1 * dz1);
        check(d1 > d0 + 0.2f, "animal flees from a nearby player",
            "d=" + String.format("%.2f", d0) + " -> " + String.format("%.2f", d1));

        // Death drops loot through the world update
        int dropsBefore = world.getItemEntities().size();
        cowB.takeDamage(99.0f, new Vector3f(1, 0, 0));
        check(cowB.isDead(), "animal dies from damage", "");
        world.updateAnimals(1.0f / 60.0f, far);
        check(world.getAnimals().size() == 2, "dead animal removed from the world",
            "size=" + world.getAnimals().size());
        check(world.getItemEntities().size() > dropsBefore,
            "death drops loot",
            "drops=" + (world.getItemEntities().size() - dropsBefore));

        world.getAnimals().clear();

        // The wild animals (deer, fox, bear, parrot) spawn, survive and drop
        int before = world.getItemEntities().size();
        Animal deer = world.spawnAnimal(Animal.AnimalType.DEER, flatX + 2.0f, gy + 1.0f, flatZ + 2.0f);
        Animal fox = world.spawnAnimal(Animal.AnimalType.FOX, flatX + 4.0f, gy + 1.0f, flatZ + 2.0f);
        Animal bear = world.spawnAnimal(Animal.AnimalType.BEAR, flatX + 2.0f, gy + 1.0f, flatZ + 4.0f);
        Animal parrot = world.spawnAnimal(Animal.AnimalType.PARROT, flatX + 4.0f, gy + 1.0f, flatZ + 4.0f);
        check(world.getAnimals().size() == 4, "wild animals spawn", "size=" + world.getAnimals().size());
        check(deer.getModelScale() > 0.9f && bear.getModelScale() > 1.0f
                && fox.getModelScale() < 0.6f && parrot.getModelScale() < 0.5f,
            "model scales vary per type", "");
        for (int i = 0; i < 120; i++) {
            world.updateAnimals(1.0f / 60.0f, far);
        }
        check(!deer.isDead() && !fox.isDead() && !bear.isDead() && !parrot.isDead(),
            "wild animals survive walking", "");
        for (Animal a : new Animal[]{deer, fox, bear, parrot}) {
            a.takeDamage(99.0f, new Vector3f(1, 0, 0));
        }
        world.updateAnimals(1.0f / 60.0f, far);
        check(world.getAnimals().isEmpty(), "wild animals removed after death", "");
        check(world.getItemEntities().size() >= before + 2,
            "wild animals drop loot",
            "drops=" + (world.getItemEntities().size() - before));
    }

    /** [ANM] Animal v2: type-specific food, shearing, eggs, bear attacks,
     *  fox hunting, taming, swimming, new items and recipes. */
    private static void testAnimalsV2(World world) {
        log("-- test: animal system v2");
        int gx = -1, gz = -1, gy = -1;
        outer:
        for (int cx = 0; cx <= 3; cx++) {
            for (int cz = 0; cz <= 3; cz++) {
                for (int dx = 3; dx < 15; dx += 3) {
                    for (int dz = 3; dz < 15; dz += 3) {
                        int x = cx * 16 + dx, z = cz * 16 + dz;
                        int h = world.getGroundHeight(x, z);
                        boolean flat = true;
                        for (int a = -3; a <= 3; a++) {
                            for (int b = -3; b <= 3; b++) {
                                if (Math.abs(world.getGroundHeight(x + a, z + b) - h) > 1) {
                                    flat = false;
                                }
                            }
                        }
                        if (flat) {
                            gx = x;
                            gz = z;
                            gy = h;
                            break outer;
                        }
                    }
                }
            }
        }
        check(gx > 0, "found a flat area for animal v2 tests", "x=" + gx + " z=" + gz + " gy=" + gy);
        if (gx <= 0) return;
        for (int dy = -3; dy <= 6; dy++) {
            for (int dx = -4; dx <= 8; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    world.setBlock(gx + dx, gy + dy, gz + dz,
                        dy < 0 ? BlockType.STONE.id : BlockType.AIR.id);
                }
            }
        }
        Player far = new Player(new Vector3f(gx + 30.0f, gy + 2.0f, gz + 30.0f), world);

        // --- Type-specific food ---
        check(Animal.isFoodFor(Animal.AnimalType.COW, new ItemStack(ItemRegistry.WHEAT, 1)),
            "cows eat wheat", "");
        check(!Animal.isFoodFor(Animal.AnimalType.PIG, new ItemStack(ItemRegistry.WHEAT, 1)),
            "pigs refuse wheat", "");
        check(Animal.isFoodFor(Animal.AnimalType.PIG, new ItemStack(BlockType.CARROT, 1)),
            "pigs eat carrots", "");
        check(Animal.isFoodFor(Animal.AnimalType.PIG, new ItemStack(BlockType.POTATO, 1)),
            "pigs eat potatoes", "");
        check(Animal.isFoodFor(Animal.AnimalType.FOX, new ItemStack(ItemRegistry.RAW_CHICKEN, 1)),
            "foxes eat chicken", "");
        check(!Animal.isFoodFor(Animal.AnimalType.BEAR, new ItemStack(ItemRegistry.WHEAT, 1)),
            "bears never breed", "");

        // --- Baby feeding ---
        Animal piglet = world.spawnAnimalBaby(Animal.AnimalType.PIG, gx + 1.5f, gy + 1.0f, gz + 1.5f);
        check(piglet.feedBaby(), "feeding a baby works", "");
        check(piglet.feedBaby(), "baby can be fed repeatedly", "");
        check(!Animal.isFoodFor(Animal.AnimalType.PIG, new ItemStack(ItemRegistry.WHEAT, 1)),
            "a pig baby won't take wheat", "");

        // --- Shearing ---
        Animal sheep = world.spawnAnimal(Animal.AnimalType.SHEEP, gx + 3.5f, gy + 1.0f, gz + 1.5f);
        check(!sheep.isSheared(), "sheep starts woolly", "");
        check(sheep.shear(), "shearing a sheep works", "");
        check(sheep.isSheared(), "sheep is now sheared", "");
        check(!sheep.shear(), "sheared sheep can't be sheared again", "");
        sheep.takeDamage(99.0f, new Vector3f(1, 0, 0));
        world.updateAnimals(1.0f / 60.0f, far);
        boolean woolDropped = false;
        for (com.voxelgame.world.entity.ItemEntity ie : world.getItemEntities()) {
            if (ie.getStack().isBlock()
                    && ie.getStack().getBlockType() == BlockType.WHITE_WOOL) woolDropped = true;
        }
        check(!woolDropped, "sheared sheep drops no wool", "");

        // --- Chickens lay eggs ---
        Animal hen = world.spawnAnimal(Animal.AnimalType.CHICKEN, gx + 1.5f, gy + 1.0f, gz + 3.5f);
        hen.forceEggSoon();
        int eggsBefore = countDrops(world, BlockType.ITEM_EGG);
        for (int i = 0; i < 120; i++) {
            world.updateAnimals(1.0f / 60.0f, far);
        }
        check(countDrops(world, BlockType.ITEM_EGG) > eggsBefore,
            "chicken lays an egg",
            "eggs=" + countDrops(world, BlockType.ITEM_EGG));

        // --- Bears attack ---
        Animal bear = world.spawnAnimal(Animal.AnimalType.BEAR, gx + 5.5f, gy + 1.0f, gz + 1.5f);
        Player victim = new Player(new Vector3f(gx + 5.2f, gy + 2.0f, gz + 1.5f), world);
        int hpBefore = victim.getHealth();
        for (int i = 0; i < 150; i++) {
            world.updateAnimals(1.0f / 60.0f, victim);
        }
        check(victim.getHealth() < hpBefore, "bear attacks the player",
            "hp=" + hpBefore + " -> " + victim.getHealth());
        check(bear.getMaxHp() == 20.0f, "bear is the tough animal",
            "maxHp=" + bear.getMaxHp());

        // --- Foxes hunt chickens ---
        Animal hen2 = world.spawnAnimal(Animal.AnimalType.CHICKEN, gx + 1.5f, gy + 1.0f, gz + 5.5f);
        Animal fox = world.spawnAnimal(Animal.AnimalType.FOX, gx + 5.5f, gy + 1.0f, gz + 5.5f);
        for (int i = 0; i < 900; i++) {
            world.updateAnimals(1.0f / 60.0f, far);
        }
        check(hen2.isDead(), "fox hunts and kills a chicken", "");

        // --- Parrot taming ---
        Animal parrot = world.spawnAnimal(Animal.AnimalType.PARROT, gx + 3.5f, gy + 1.0f, gz + 5.5f);
        check(parrot.tryTame(new ItemStack(ItemRegistry.WHEAT, 1)), "parrot tames with wheat", "");
        check(parrot.isTamed(), "parrot is tamed", "");
        check(!parrot.tryTame(new ItemStack(ItemRegistry.WHEAT, 1)), "tamed parrot stays tamed", "");

        // --- Swimming ---
        world.setBlock(gx + 1, gy + 1, gz - 4, BlockType.WATER.id);
        Animal swimmer = world.spawnAnimal(Animal.AnimalType.CHICKEN, gx + 1.5f, gy + 0.4f, gz - 3.5f);
        float y0 = swimmer.getPosition().y;
        for (int i = 0; i < 120; i++) {
            world.updateAnimals(1.0f / 60.0f, far);
        }
        check(swimmer.getPosition().y >= y0 - 0.5f,
            "animal floats in water",
            "y=" + String.format("%.2f", swimmer.getPosition().y));

        // --- New items and recipes ---
        check(ItemRegistry.SHEARS != null && ItemRegistry.BUCKET != null && ItemRegistry.MILK != null,
            "shears/bucket/milk registered", "");
        check(ItemRegistry.MILK.isFood(), "milk is food", "");
        boolean shearsRecipe = false, bucketRecipe = false;
        for (com.voxelgame.item.Recipe r : RecipeRegistry.RECIPES) {
            ItemStack res = r.getResult();
            if (res.isItem() && res.getItem() == ItemRegistry.SHEARS) shearsRecipe = true;
            if (res.isItem() && res.getItem() == ItemRegistry.BUCKET) bucketRecipe = true;
        }
        check(shearsRecipe && bucketRecipe, "shears and bucket have recipes", "");

        world.getAnimals().clear();
        world.getItemEntities().clear();
    }

    private static int countDrops(World world, BlockType type) {
        int n = 0;
        for (com.voxelgame.world.entity.ItemEntity ie : world.getItemEntities()) {
            if (ie.getStack().isBlock() && ie.getStack().getBlockType() == type) n++;
        }
        return n;
    }

    private static void testDayNightCycle() {
        log("-- test: day/night cycle");
        DayNightCycle dnc = new DayNightCycle();
        dnc.setTime(0.25);
        float day = dnc.getDaylight();
        dnc.setTime(0.75);
        float night = dnc.getDaylight();
        check(day > 0.5f && night < 0.3f, "daylight differs between day and night",
            "day=" + day + " night=" + night);
    }

    private static void testItemsAndCrafting() {
        log("-- test: items, crafting, smelting");
        ItemStack stack = new ItemStack(BlockType.OAK_LOG, 64);
        check(stack.getCount() == 64 && stack.getBlockType() == BlockType.OAK_LOG,
            "ItemStack holds block + count", "");

        check(RecipeRegistry.RECIPES != null && RecipeRegistry.RECIPES.length > 0,
            "crafting recipes registered", "count="
                + (RecipeRegistry.RECIPES == null ? 0 : RecipeRegistry.RECIPES.length));

        ItemStack smelted = RecipeRegistry.getSmeltingResult(BlockType.IRON_ORE.id);
        check(smelted != null && !smelted.isEmpty(), "iron ore smelts into something",
            smelted == null ? "null" : String.valueOf(smelted));
    }

    /** [ENCH] Experience levels, enchantment storage and XP orbs. */
    private static void testXpAndEnchanting(World world) {
        log("-- test: experience and enchanting");

        Player player = new Player(new Vector3f(8.5f, 80f, 8.5f), world);
        check(player.getXpToNext() == 7, "first level needs 7 xp",
            "needed=" + player.getXpToNext());
        player.addXp(10);
        check(player.getXpLevel() == 1 && player.getXpProgress() == 3,
            "level up after 7 xp", "lvl=" + player.getXpLevel() + " prog=" + player.getXpProgress());
        check(player.getXpToNext() == 9, "level cost grows", "needed=" + player.getXpToNext());
        player.addXp(9);
        check(player.getXpLevel() == 2 && player.getXpProgress() == 3,
            "second level carries the overflow", "lvl=" + player.getXpLevel() + " prog=" + player.getXpProgress());
        check(player.spendXp(2), "spending 2 levels succeeds", "");
        check(player.getXpLevel() == 0 && player.getXpProgress() == 0,
            "xp consumed by spending", "lvl=" + player.getXpLevel() + " prog=" + player.getXpProgress());
        check(!player.spendXp(1), "cannot overspend", "");

        ItemStack pick = new ItemStack(ItemRegistry.IRON_PICKAXE, 1);
        pick.addEnchantment(com.voxelgame.item.Enchantment.EFFICIENCY, 3);
        check(pick.getEnchantLevel(com.voxelgame.item.Enchantment.EFFICIENCY) == 3,
            "enchantment stored on a tool", "");
        check(pick.hasEnchantments(), "enchantment flag set", "");
        check(!pick.getEnchantmentSummary().isEmpty(), "enchantment summary text", "");
        ItemStack copy = pick.copy();
        check(copy.getEnchantLevel(com.voxelgame.item.Enchantment.EFFICIENCY) == 3,
            "enchantments survive copy", "");
        check(!pick.canMerge(new ItemStack(ItemRegistry.IRON_PICKAXE, 1)),
            "enchanted tool never merges", "");
        check(!pick.canMerge(copy), "two enchanted stacks never merge", "");

        com.voxelgame.item.Enchantment rolled =
            com.voxelgame.item.Enchantment.rollFor(com.voxelgame.item.ToolType.PICKAXE,
                new java.util.Random(7));
        check(rolled != null && rolled.appliesTo(com.voxelgame.item.ToolType.PICKAXE),
            "enchantment roll fits the tool", String.valueOf(rolled));

        check(com.voxelgame.item.BlockHarvest.get(BlockType.LAPIS_ORE) != null
                && com.voxelgame.item.BlockHarvest.get(BlockType.LAPIS_ORE).drop == BlockType.ITEM_LAPIS,
            "lapis ore drops lapis lazuli", "");

        int before = world.getXpOrbs().size();
        world.spawnXpOrb(8.5f, 81f, 8.5f, 5);
        world.spawnXpOrb(9.5f, 81f, 8.5f, 10);
        check(world.getXpOrbs().size() == before + 3,
            "xp orbs spawn (5 -> 1 orb, 10 -> 2 orbs)",
            "count=" + (world.getXpOrbs().size() - before));
        for (int i = 0; i < 180; i++) {
            world.updateXpOrbs(1.0f / 60.0f);
        }
        check(world.getXpOrbs().size() == before + 3,
            "orbs stay alive on the ground", "count=" + world.getXpOrbs().size());
        world.getXpOrbs().clear();
    }

    /** [TRAN] Glass pane: thin passable panel, blended pass, recipe. */
    private static void testTransparency(World world) {
        log("-- test: transparency (glass pane)");
        check(com.voxelgame.rendering.ChunkMeshBuilder.isTransparent(BlockType.GLASS_PANE.id),
            "glass pane lets sight through", "");
        check(com.voxelgame.rendering.ChunkMeshBuilder.needsBlendPass(BlockType.GLASS_PANE.id),
            "glass pane renders in the blended pass", "");
        check(com.voxelgame.rendering.ChunkMeshBuilder.isPane(BlockType.GLASS_PANE.id),
            "glass pane recognised by the mesher", "");
        check(!BlockType.GLASS_PANE.solid, "glass pane is passable", "");

        world.setBlock(10, 80, 10, BlockType.GLASS_PANE.id);
        world.setBlock(10, 80, 11, BlockType.GLASS_PANE.id);
        world.setBlock(11, 80, 10, BlockType.GLASS_PANE.id);
        check(world.getBlock(10, 80, 10) == BlockType.GLASS_PANE.id
                && world.getBlock(10, 80, 11) == BlockType.GLASS_PANE.id,
            "glass panes place and persist in the world", "");

        ItemStack expected = null;
        for (com.voxelgame.item.Recipe r : RecipeRegistry.RECIPES) {
            ItemStack res = r.getResult();
            if (res.isBlock() && res.getBlockType() == BlockType.GLASS_PANE) expected = res;
        }
        check(expected != null && expected.getCount() == 16,
            "glass pane recipe yields 16 panes",
            expected == null ? "no recipe" : "count=" + expected.getCount());
        check(com.voxelgame.item.BlockHarvest.get(BlockType.GLASS_PANE) == null,
            "glass pane drops itself when mined", "");
    }

    /** [WX] Automatic weather rolls: snow only in cold biomes. */
    private static void testWeather() {
        log("-- test: weather");
        boolean sawSnow = false, sawThunder = false, sawRain = false;
        for (int i = 0; i < 500; i++) {
            String w = com.voxelgame.core.Game.rollNextWeather(true);
            if ("snow".equals(w)) sawSnow = true;
            if ("thunder".equals(w)) sawThunder = true;
            if ("rain".equals(w)) sawRain = true;
        }
        check(sawSnow && sawThunder && sawRain,
            "cold biome rolls snow, rain and thunder",
            "snow=" + sawSnow + " thunder=" + sawThunder + " rain=" + sawRain);

        boolean neverSnow = true;
        for (int i = 0; i < 500; i++) {
            if ("snow".equals(com.voxelgame.core.Game.rollNextWeather(false))) {
                neverSnow = false;
                break;
            }
        }
        check(neverSnow, "warm biome never rolls snow", "");
    }

    private static void testWorldList(WorldMeta meta) {
        log("-- test: world registry");
        boolean listed = WorldSave.listWorlds().stream()
            .anyMatch(m -> meta.folderName.equals(m.folderName));
        check(listed, "new world appears in saves list", "folder=" + meta.folderName);
    }

    private static void testStatistics(WorldSave save, WorldMeta meta) throws Exception {
        log("-- test: statistics roundtrip");
        Path dir = save.getDirectory();
        com.voxelgame.stats.WorldStats stats = new com.voxelgame.stats.WorldStats(dir);
        stats.load();

        stats.add(com.voxelgame.stats.WorldStats.BLOCKS_MINED, 42);
        stats.add(com.voxelgame.stats.WorldStats.FISH_CAUGHT, 3);
        stats.addPlayTime(120.0f);
        stats.getAchievements().add("first_fish");
        stats.save();

        com.voxelgame.stats.WorldStats loaded = new com.voxelgame.stats.WorldStats(dir);
        loaded.load();
        check(loaded.get(com.voxelgame.stats.WorldStats.BLOCKS_MINED) == 42, "blocksMined persists", "");
        check(loaded.get(com.voxelgame.stats.WorldStats.FISH_CAUGHT) == 3, "fishCaught persists", "");
        check(loaded.getPlayTimeMs() == 120000, "playTimeMs persists", "ms=" + loaded.getPlayTimeMs());
        check(loaded.isAchievementUnlocked("first_fish"), "achievement persists", "");
    }

    private static void testFishing(World world, WorldSave save, WorldMeta meta) throws Exception {
        log("-- test: fishing mechanics");
        com.voxelgame.stats.WorldStats stats = new com.voxelgame.stats.WorldStats(save.getDirectory());
        stats.load();
        com.voxelgame.achievement.AchievementRegistry.restoreFrom(stats);

        // A 3x3 pond at surface level so the bobber has somewhere to land
        check(waitForChunks(world, new Vector3f(8, 70, 8), 20000), "pond area chunks loaded", "");
        int gy = world.getGroundHeight(8, 8);
        for (int dx = 7; dx <= 9; dx++) {
            for (int dz = 7; dz <= 9; dz++) {
                world.setBlock(dx, gy + 1, dz, BlockType.WATER.id);
            }
        }

        com.voxelgame.world.fishing.FishingController fc =
            new com.voxelgame.world.fishing.FishingController(world, stats, null);
        int[] biteFx = {0};
        fc.onBite = () -> biteFx[0]++;

        com.voxelgame.item.ItemStack rod = new com.voxelgame.item.ItemStack(
            com.voxelgame.item.ItemRegistry.FISHING_ROD, 1);
        int dur0 = rod.getDurability();

        // Cast straight down into the pond
        boolean cast = fc.cast(new Vector3f(8.5f, gy + 3.5f, 8.5f),
            new Vector3f(0, -1, 0), rod);
        check(cast, "fishing rod casts", "");
        check(fc.hasBobber(), "bobber exists after cast", "");

        com.voxelgame.world.entity.FishingBobberEntity bobber = fc.getBobber();
        // Pump up to ~15 s of sim time: the random bite delay is 5..15 s
        int ticks = 0;
        while (bobber.getState() != com.voxelgame.world.entity.FishingBobberEntity.State.BITE
                && ticks < 1000) {
            bobber.update(0.05f);
            ticks++;
        }
        check(bobber.getState() == com.voxelgame.world.entity.FishingBobberEntity.State.BITE,
            "bobber bites within 15 s", "ticks=" + ticks);
        check(biteFx[0] == 1, "onBite fired once", "fired=" + biteFx[0]);

        long fish0 = stats.get(com.voxelgame.stats.WorldStats.FISH_CAUGHT);
        String result = fc.reelIn();
        check("fish".equals(result) || "junk".equals(result),
            "bite reels in loot", "result=" + result);
        check(stats.get(com.voxelgame.stats.WorldStats.FISH_CAUGHT) == fish0 + 1,
            "fishCaught incremented", "");
        check(com.voxelgame.achievement.AchievementRegistry.isUnlocked("first_fish"),
            "first_fish unlocks on catch", "");
        check(rod.getDurability() < dur0, "rod durability spent on catch", "");
        check(!fc.hasBobber(), "bobber cleared after reel in", "");

        // Reeling with no bobber in the water is a no-op
        check(fc.reelIn() == null, "reel without bobber is a no-op", "");

        // The rod recipe: 3 sticks + 2 string (vanilla layout)
        boolean rodRecipe = false;
        for (com.voxelgame.item.Recipe r : RecipeRegistry.RECIPES) {
            if (r.getResult().isItem()
                    && r.getResult().getItem() == com.voxelgame.item.ItemRegistry.FISHING_ROD) {
                int sticks = 0, strings = 0;
                for (ItemStack in : r.getInputs()) {
                    if (in == null) continue;
                    if (in.isItem() && in.getItem() == com.voxelgame.item.ItemRegistry.STICK) sticks++;
                    if (in.isItem() && in.getItem() == com.voxelgame.item.ItemRegistry.STRING) strings++;
                }
                rodRecipe = sticks == 3 && strings == 2;
            }
        }
        check(rodRecipe, "fishing rod recipe is 3 sticks + 2 string", "");

        // Raw fish smelts into cooked fish
        world.setBlock(12, gy + 1, 12, BlockType.FURNACE.id);
        ContainerData fishSmelt = world.getContainerManager()
            .getOrCreate(12, gy + 1, 12, ContainerData.Type.FURNACE);
        fishSmelt.setSlot(ContainerData.FURNACE_INPUT,
            new ItemStack(com.voxelgame.item.ItemRegistry.RAW_FISH, 1));
        fishSmelt.setSlot(ContainerData.FURNACE_FUEL,
            new ItemStack(com.voxelgame.item.ItemRegistry.COAL, 1));
        for (int i = 0; i < 400; i++) {
            world.updateContainers();
        }
        ItemStack fishOut = fishSmelt.getSlot(ContainerData.FURNACE_OUTPUT);
        check(fishOut != null && fishOut.isItem()
                && fishOut.getItem() == com.voxelgame.item.ItemRegistry.COOKED_FISH,
            "raw fish smelts into cooked fish",
            "out=" + (fishOut == null ? "null" : fishOut.getCount()));
        world.getContainerManager().remove(12, gy + 1, 12);
    }

    // ------------------------------------------------------------------

    /** Pump the chunk loader until everything around center is ready. */
    private static boolean waitForChunks(World world, Vector3f center, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            world.update(center);
            if (!world.isLoadingChunks() && world.getLoader().getPendingCount() == 0) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    private static void check(boolean ok, String name, String detail) {
        if (ok) {
            passed++;
            log("[PASS] " + name + (detail.isEmpty() ? "" : " (" + detail + ")"));
        } else {
            failed++;
            log("[FAIL] " + name + (detail.isEmpty() ? "" : " (" + detail + ")"));
        }
    }

    private static void log(String line) {
        System.out.println("[Headless] " + line);
    }

    private static void writeReport() {
        try {
            Path parent = REPORT.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            StringBuilder sb = new StringBuilder();
            sb.append("headless test report\n");
            sb.append("timestamp: ").append(java.time.LocalDateTime.now()).append('\n');
            sb.append("passed: ").append(passed).append('\n');
            sb.append("failed: ").append(failed).append('\n');
            sb.append("result: ").append(failed == 0 ? "PASS" : "FAIL").append('\n');
            Files.writeString(REPORT, sb.toString());
        } catch (java.io.IOException e) {
            System.err.println("[Headless] cannot write report: " + e.getMessage());
        }
    }
}