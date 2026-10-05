package com.voxelgame.world.structure;

import com.voxelgame.world.BlockType;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.World;
import com.voxelgame.world.entity.Villager;
import com.voxelgame.world.generator.TerrainGenerator;
import com.voxelgame.world.biome.BiomeSelector;

import java.util.*;

/**
 * РЈР»СѓС‡С€РµРЅРЅС‹Р№ РіРµРЅРµСЂР°С‚РѕСЂ РґРµСЂРµРІРµРЅСЊ.
 *
 * Р”РµСЂРµРІРЅСЏ = С†РµРЅС‚СЂ (РєРѕР»РѕРґРµС†/РїР»РѕС‰Р°РґСЊ) + РґРѕСЂРѕРіРё + Р·РґР°РЅРёСЏ + Р¶РёС‚РµР»Рё.
 *
 * РћСЃРѕР±РµРЅРЅРѕСЃС‚Рё:
 * - Р‘РёРѕРј-Р·Р°РІРёСЃРёРјС‹Рµ РјР°С‚РµСЂРёР°Р»С‹ (РґРµСЂРµРІРѕ/РєР°РјРµРЅСЊ/РїРµСЃС‡Р°РЅРёРє)
 * - Р Р°Р·РЅС‹Рµ С‚РёРїС‹ Р·РґР°РЅРёР№ (РґРѕРјР°, С„РµСЂРјС‹, РєСѓР·РЅРёС†Р°, Р±РёР±Р»РёРѕС‚РµРєР°, С†РµСЂРєРѕРІСЊ, СЂС‹РЅРѕРє,
 *   РєРѕРЅСЋС€РЅСЏ, РїРµРєР°СЂРЅСЏ, С‚Р°РІРµСЂРЅР°)
 * - РџРµСЂРµРґ СЃС‚СЂРѕРёС‚РµР»СЊСЃС‚РІРѕРј С‚РµСЂСЂРёС‚РѕСЂРёСЏ РІС‹СЂР°РІРЅРёРІР°РµС‚СЃСЏ: С…РѕР»РјС‹ СЃСЂРµР·Р°СЋС‚СЃСЏ,
 *   РІРїР°РґРёРЅС‹ Р·Р°СЃС‹РїР°СЋС‚СЃСЏ, РґРµСЂРµРІСЊСЏ/РєР°РјРЅРё/СЂР°СЃС‚РёС‚РµР»СЊРЅРѕСЃС‚СЊ СѓР±РёСЂР°СЋС‚СЃСЏ
 * - Р”РѕСЂРѕРіРё РјРµР¶РґСѓ Р·РґР°РЅРёСЏРјРё (РіСЂР°РІРёР№/РґРѕСЃРєРё), С„РѕРЅР°СЂРё РІРґРѕР»СЊ РґРѕСЂРѕРі
 * - Р¦РµРЅС‚СЂР°Р»СЊРЅР°СЏ РїР»РѕС‰Р°РґСЊ СЃ РєРѕР»РѕРґС†РµРј, Р»Р°РІРѕС‡РєР°РјРё, С†РІРµС‚Р°РјРё Рё РґРµСЂРµРІСЊСЏРјРё
 * - Р”РІСѓСЃРєР°С‚РЅС‹Рµ РєСЂС‹С€Рё, РґРІРµСЂРё, РѕРєРЅР° РІ СЂР°РјР°С…, РёРЅС‚РµСЂСЊРµСЂС‹ (РєСЂРѕРІР°С‚Рё, РїРµС‡Рё,
 *   СЃСѓРЅРґСѓРєРё, С„Р°РєРµР»С‹), С‚СЂСѓР±С‹
 * - РЎР°РґС‹ Сѓ РґРѕРјРѕРІ, С„РµСЂРјС‹ СЃ РѕРіСЂР°РґР°РјРё Рё РѕРіРѕСЂРѕРґРЅС‹Рј РїСѓРіР°Р»РѕРј
 * - Р–РёС‚РµР»Рё СЃРїР°РІРЅСЏС‚СЃСЏ РІ Р·Р°РІРёСЃРёРјРѕСЃС‚Рё РѕС‚ С‚РёРїР° Р·РґР°РЅРёСЏ
 */
public class VillageGenerator {

    private final long seed;
    private final TerrainGenerator terrainGen;

    /** [SD] РњРёСЂ, РІ РєРѕС‚РѕСЂРѕРј СЃС‚СЂРѕСЏС‚СЃСЏ РґРµСЂРµРІРЅРё (РґР»СЏ СѓСЃС‚Р°РЅРѕРІРєРё РґРІРµСЂРµР№). */
    private World worldRef;

    /** Р Р°СЃСЃС‚РѕСЏРЅРёРµ РјРµР¶РґСѓ РґРµСЂРµРІРЅСЏРјРё (РІ С‡Р°РЅРєР°С…). */
    private static final int VILLAGE_SPACING = 20;
    /** РњРёРЅРёРјР°Р»СЊРЅРѕРµ СЂР°СЃСЃС‚РѕСЏРЅРёРµ РјРµР¶РґСѓ РґРµСЂРµРІРЅСЏРјРё. */
    private static final int VILLAGE_SEPARATION = 8;

    public VillageGenerator(long seed, TerrainGenerator terrainGen) {
        this.seed = seed;
        this.terrainGen = terrainGen;
    }

    /**
     * РџСЂРѕРІРµСЂРёС‚СЊ, РґРѕР»Р¶РЅР° Р»Рё РґРµСЂРµРІРЅСЏ Р±С‹С‚СЊ РІ СЌС‚РѕРј С‡Р°РЅРєРµ.
     * РСЃРїРѕР»СЊР·СѓРµС‚ СЃРµС‚РєСѓ СЃ С€СѓРјРѕРј РґР»СЏ РµСЃС‚РµСЃС‚РІРµРЅРЅРѕРіРѕ СЂР°СЃРїСЂРµРґРµР»РµРЅРёСЏ.
     */
    public boolean shouldGenerateVillage(int chunkX, int chunkZ) {
        int gridX = Math.floorDiv(chunkX, VILLAGE_SPACING);
        int gridZ = Math.floorDiv(chunkZ, VILLAGE_SPACING);

        long hash = hashPos(gridX, gridZ, 0);
        int offsetX = (int) ((hash & 0xFFFF) % (VILLAGE_SPACING - VILLAGE_SEPARATION));
        int offsetZ = (int) (((hash >> 16) & 0xFFFF) % (VILLAGE_SPACING - VILLAGE_SEPARATION));

        int targetX = gridX * VILLAGE_SPACING + offsetX;
        int targetZ = gridZ * VILLAGE_SPACING + offsetZ;

        return chunkX == targetX && chunkZ == targetZ;
    }

    /**
     * РќР°Р№С‚Рё С‡Р°РЅРє Р±Р»РёР¶Р°Р№С€РµР№ РґРµСЂРµРІРЅРё (РїРѕ С‚РѕР№ Р¶Рµ РґРµС‚РµСЂРјРёРЅРёСЂРѕРІР°РЅРЅРѕР№ СЃРµС‚РєРµ,
     * С‡С‚Рѕ Рё {@link #shouldGenerateVillage}).
     *
     * @return {chunkX, chunkZ} РґРµСЂРµРІРЅРё, Р±Р»РёР¶Р°Р№С€РµР№ Рє fromChunkX/fromChunkZ,
     *         РёР»Рё null, РµСЃР»Рё РІ РѕРєРЅРµ РїРѕРёСЃРєР° РґРµСЂРµРІРµРЅСЊ РЅРµС‚
     */
    public int[] nearestVillageChunk(int fromChunkX, int fromChunkZ, int searchCells) {
        int gridX = Math.floorDiv(fromChunkX, VILLAGE_SPACING);
        int gridZ = Math.floorDiv(fromChunkZ, VILLAGE_SPACING);

        int[] best = null;
        double bestDist = Double.MAX_VALUE;
        for (int gx = gridX - searchCells; gx <= gridX + searchCells; gx++) {
            for (int gz = gridZ - searchCells; gz <= gridZ + searchCells; gz++) {
                long hash = hashPos(gx, gz, 0);
                int offsetX = (int) ((hash & 0xFFFF) % (VILLAGE_SPACING - VILLAGE_SEPARATION));
                int offsetZ = (int) (((hash >> 16) & 0xFFFF) % (VILLAGE_SPACING - VILLAGE_SEPARATION));

                int tx = gx * VILLAGE_SPACING + offsetX;
                int tz = gz * VILLAGE_SPACING + offsetZ;

                double d = (double) (tx - fromChunkX) * (tx - fromChunkX)
                         + (double) (tz - fromChunkZ) * (tz - fromChunkZ);
                if (d < bestDist) {
                    bestDist = d;
                    best = new int[]{tx, tz};
                }
            }
        }
        return best;
    }

    /**
     * РЎРіРµРЅРµСЂРёСЂРѕРІР°С‚СЊ РґРµСЂРµРІРЅСЋ РІ С‡Р°РЅРєРµ.
     *
     * @param chunk        С‚РµРєСѓС‰РёР№ С‡Р°РЅРє
     * @param chunks       РІСЃРµ Р·Р°РіСЂСѓР¶РµРЅРЅС‹Рµ С‡Р°РЅРєРё
     * @param world        РјРёСЂ (РґР»СЏ СЃРїР°РІРЅР° Р¶РёС‚РµР»РµР№)
     */
    public void generateVillage(Chunk chunk, Map<Long, Chunk> chunks, World world) {
        this.worldRef = world;
        int cx = chunk.getChunkX();
        int cz = chunk.getChunkZ();

        // Р¦РµРЅС‚СЂ РґРµСЂРµРІРЅРё вЂ” С†РµРЅС‚СЂ С‡Р°РЅРєР°
        int centerX = cx * Chunk.SIZE + 8;
        int centerZ = cz * Chunk.SIZE + 8;
        int centerY = terrainGen.getHeight(centerX, centerZ);

        if (centerY < TerrainGenerator.SEA_LEVEL + 2) return; // РџРѕРґ РІРѕРґРѕР№

        BiomeSelector.MCBiome biome = terrainGen.getMCBiome(centerX, centerZ);
        VillageMaterials materials = getVillageMaterials(biome);

        Random rng = new Random(seed ^ hashPos(cx, cz, 42));

        // === РћРїСЂРµРґРµР»СЏРµРј СЂР°Р·РјРµСЂ РґРµСЂРµРІРЅРё ===
        int buildingCount = 5 + rng.nextInt(11); // 5-15 Р·РґР°РЅРёР№
        int villageRadius = 20 + rng.nextInt(15); // 20-35 Р±Р»РѕРєРѕРІ

        // === РџРѕРґРіРѕС‚РѕРІРєР° С‚РµСЂСЂРёС‚РѕСЂРёРё ===
        // РЎРЅР°С‡Р°Р»Р° РґРѕ-РіРµРЅРµСЂРёСЂСѓРµРј РІСЃРµ С‡Р°РЅРєРё РґРµСЂРµРІРЅРё, С‡С‚РѕР±С‹ РІС‹СЂР°РІРЅРёРІР°РЅРёРµ
        // Р·Р°С‚СЂРѕРЅСѓР»Рѕ РёС… С†РµР»РёРєРѕРј (РёРЅР°С‡Рµ РґРµСЂРµРІСЊСЏ РїРѕСЏРІРёР»РёСЃСЊ Р±С‹ РїРѕР·Р¶Рµ).
        int flattenRadius = villageRadius + 22;
        ensureChunks(chunks, centerX - flattenRadius - 3, centerZ - flattenRadius - 3,
                     centerX + flattenRadius + 3, centerZ + flattenRadius + 3);

        // РЎСЂРµР·Р°РµРј С…РѕР»РјС‹, Р·Р°СЃС‹РїР°РµРј РІРїР°РґРёРЅС‹, СѓР±РёСЂР°РµРј РґРµСЂРµРІСЊСЏ/РєР°РјРЅРё/РєСѓСЃС‚С‹
        int flatY = flattenTerrain(chunks, centerX, centerZ, flattenRadius, materials);

        // === РЎРѕР·РґР°С‘Рј РїР»Р°РЅ РґРµСЂРµРІРЅРё (РІСЃРµ Р·РґР°РЅРёСЏ РЅР° СЂРѕРІРЅРѕР№ Р·РµРјР»Рµ) ===
        List<BuildingPlacement> buildings = planVillage(rng, centerX, flatY, centerZ,
                buildingCount, villageRadius, biome);

        // === Р“РµРЅРµСЂРёСЂСѓРµРј С†РµРЅС‚СЂР°Р»СЊРЅСѓСЋ РїР»РѕС‰Р°РґСЊ ===
        generateTownSquare(chunks, centerX, flatY, centerZ, materials, rng);

        // === [CR] РђРјР±Р°СЂ вЂ” РѕР±С‰РёР№ СЃРєР»Р°Рґ РґР»СЏ РІСЃРµС… С„РµСЂРјРµСЂРѕРІ РґРµСЂРµРІРЅРё ===
        BuildingPlacement barn = null;
        for (BuildingPlacement bp : buildings) {
            if (bp.type == BuildingType.BARN) {
                barn = bp;
                break;
            }
        }

        // === РЎС‚СЂРѕРёРј Р·РґР°РЅРёСЏ ===
        List<Villager> newVillagers = new ArrayList<>();
        // [PERF] Все грядки деревни (фермы + сады) — фермерам вместо скана мира
        List<int[]> villageCrops = new ArrayList<>();
        for (BuildingPlacement bp : buildings) {
            generateBuilding(chunks, bp, materials, rng);

            // РЎР°РґРёРє Сѓ Р¶РёР»С‹С… РґРѕРјРѕРІ (С‚РѕР»СЊРєРѕ РЅРµР±РѕР»СЊС€РёС… вЂ” Сѓ Р±РѕР»СЊС€РёС… СЃР°Рґ
            // РјРѕРі Р±С‹ РЅР°Р»РµР·С‚СЊ РЅР° СЃРѕСЃРµРґРЅРµРµ Р·РґР°РЅРёРµ)
            if (bp.type == BuildingType.SMALL_HOUSE || bp.type == BuildingType.BAKERY) {
                generateGarden(chunks, bp.wx, flatY, bp.wz, materials, rng, villageCrops);
            }

            // РЎРїР°РІРЅРёРј Р¶РёС‚РµР»РµР№ РїРµСЂРµРґ РІС…РѕРґРѕРј Р·РґР°РЅРёСЏ (РґРІРµСЂСЊ РІСЃРµРіРґР° СЃ -Z СЃС‚РѕСЂРѕРЅС‹)
            Villager.Profession prof = getProfessionForBuilding(bp.type);
            if (prof != null && world != null) {
                int count = bp.type == BuildingType.LARGE_HOUSE ? 2 : 1;
                for (int i = 0; i < count; i++) {
                    int vx = bp.wx + i * 2;
                    int vz = bp.wz - 6;
                    int vy = flatY;
                    if (vy > TerrainGenerator.SEA_LEVEL) {
                        Villager v = new Villager(world, vx + 0.5f, vy + 1.0f, vz + 0.5f, prof);
                        v.setHome(vx + 0.5f, vy + 1.0f, vz + 0.5f);
                        // Р Р°Р±РѕС‡РµРµ РјРµСЃС‚Рѕ вЂ” Сѓ РґРІРµСЂРё Р·РґР°РЅРёСЏ (РґРІРµСЂСЊ СЃ -Z СЃС‚РѕСЂРѕРЅС‹, halfD 2-3)
                        v.setWorkstation(vx + 0.5f, vy + 1.0f, vz - 3.5f);
                        // [CR] Р¤РµСЂРјРµСЂР°Рј РЅР°Р·РЅР°С‡Р°РµРј СЃРєР»Р°Рґ вЂ” Р°РјР±Р°СЂ РґРµСЂРµРІРЅРё
                        if (prof == Villager.Profession.FARMER && barn != null) {
                            v.setStorage(barn.wx + 2.5f, flatY + 1.0f, barn.wz + 1.5f);
                        }
                        v.setBiome(getBiomeName(biome));
                        newVillagers.add(v);
                    }
                }
            }
        }

        // === Р”РѕСЂРѕРіРё ===
        generateRoads(chunks, centerX, flatY, centerZ, buildings, materials, rng);

        // === Р¤РѕРЅР°СЂРё ===
        generateLamps(chunks, centerX, flatY, centerZ, buildings, materials, rng);

        // === Р¤РµСЂРјС‹ ===
        generateFarms(chunks, centerX, flatY, centerZ, buildings, materials, rng, biome, villageCrops);

        // [PERF] Фермы сгенерированы после спавна жителей — раздаём список грядок фермерам
        if (world != null && !villageCrops.isEmpty()) {
            for (Villager v : newVillagers) {
                if (v.getProfession() == Villager.Profession.FARMER) {
                    v.setFarmCrops(villageCrops);
                }
            }
        }

        // === Р”РµРєРѕСЂ РїР»РѕС‰Р°РґРё ===
        generateSquareDecor(chunks, centerX, flatY, centerZ, materials, rng, biome);

        // === Р’РѕРёРЅ: РѕС…СЂР°РЅСЏРµС‚ РїР»РѕС‰Р°РґСЊ РґРµСЂРµРІРЅРё (СЂР°Р±РѕС‡РµРµ РјРµСЃС‚Рѕ = С†РµРЅС‚СЂ) ===
        if (world != null && flatY > TerrainGenerator.SEA_LEVEL) {
            Villager warrior = new Villager(world, centerX + 0.5f, flatY + 1.0f,
                    centerZ + 0.5f, Villager.Profession.WARRIOR);
            warrior.setHome(centerX + 0.5f, flatY + 1.0f, centerZ + 0.5f);
            warrior.setWorkstation(centerX + 0.5f, flatY + 1.0f, centerZ + 0.5f);
            warrior.setBiome(getBiomeName(biome));
            newVillagers.add(warrior);
        }

        // Р”РѕР±Р°РІР»СЏРµРј Р¶РёС‚РµР»РµР№ РІ РјРёСЂ
        if (world != null) {
            for (Villager v : newVillagers) {
                world.getVillagers().add(v);
            }
        }

        // РћС‚РјРµС‡Р°РµРј С‡Р°РЅРє РєР°Рє "СЃ РґРµСЂРµРІРЅРµР№"
        chunk.markStructure("village");
    }

    // ==================== РџРћР”Р“РћРўРћР’РљРђ РўР•Р Р РРўРћР РР ====================

    /**
     * РЎРёРЅС…СЂРѕРЅРЅРѕ СЃРіРµРЅРµСЂРёСЂРѕРІР°С‚СЊ РѕС‚СЃСѓС‚СЃС‚РІСѓСЋС‰РёРµ С‡Р°РЅРєРё РІ РїСЂСЏРјРѕСѓРіРѕР»СЊРЅРёРєРµ.
     * РћР±С‹С‡РЅРѕ С‡Р°РЅРєРё СЃРѕР·РґР°СЋС‚СЃСЏ С„РѕРЅРѕРІС‹РјРё СЂР°Р±РѕС‡РёРјРё РїРѕС‚РѕРєР°РјРё; Р·РґРµСЃСЊ РґРµСЂРµРІРЅСЏ
     * РіР°СЂР°РЅС‚РёСЂРѕРІР°РЅРЅРѕ РїРѕР»СѓС‡Р°РµС‚ РІСЃРµ СЃРІРѕРё С‡Р°РЅРєРё СЃСЂР°Р·Сѓ, С‡С‚РѕР±С‹ РІС‹СЂР°РІРЅРёРІР°РЅРёРµ
     * Рё Р·РґР°РЅРёСЏ РЅРµ РѕР±СЂРµР·Р°Р»РёСЃСЊ РЅР° РіСЂР°РЅРёС†Р°С….
     */
    private void ensureChunks(Map<Long, Chunk> chunks, int x0, int z0, int x1, int z1) {
        for (int cx = x0 >> 4; cx <= (x1 >> 4); cx++) {
            for (int cz = z0 >> 4; cz <= (z1 >> 4); cz++) {
                long key = Chunk.key(cx, cz);
                if (chunks.containsKey(key)) continue;
                Chunk c = new Chunk(cx, cz);
                terrainGen.generate(c);
                terrainGen.generateStructures(c);
                chunks.put(key, c);
                c.setDirty(true);
                c.setLightDirty(true);
                // РЎРѕСЃРµРґРё С‚РѕР¶Рµ РґРѕР»Р¶РЅС‹ РїРµСЂРµСЃС‡РёС‚Р°С‚СЊ СЃРІРµС‚ РЅР° РіСЂР°РЅРёС†Рµ
                for (int nx = cx - 1; nx <= cx + 1; nx++) {
                    for (int nz = cz - 1; nz <= cz + 1; nz++) {
                        Chunk n = chunks.get(Chunk.key(nx, nz));
                        if (n != null) {
                            n.setDirty(true);
                            n.setLightDirty(true);
                        }
                    }
                }
            }
        }
    }

    /**
     * Р’С‹СЂРѕРІРЅСЏС‚СЊ СЂРµР»СЊРµС„: СЃСЂРµР·Р°С‚СЊ С…РѕР»РјС‹, Р·Р°СЃС‹РїР°С‚СЊ РІРїР°РґРёРЅС‹, СѓР±СЂР°С‚СЊ РґРµСЂРµРІСЊСЏ,
     * РєСѓСЃС‚С‹, РєР°РјРЅРё Рё РїСЂРѕС‡РёРµ РЅР°РіСЂРѕРјРѕР¶РґРµРЅРёСЏ РІ СЂР°РґРёСѓСЃРµ РґРµСЂРµРІРЅРё.
     *
     * @return РёС‚РѕРіРѕРІС‹Р№ СѓСЂРѕРІРµРЅСЊ Р·РµРјР»Рё (flatY), РЅР° РєРѕС‚РѕСЂРѕРј СЃС‚СЂРѕРёС‚СЃСЏ РґРµСЂРµРІРЅСЏ
     */
    private int flattenTerrain(Map<Long, Chunk> chunks, int cx, int cz, int radius,
                               VillageMaterials mat) {
        boolean desert = mat.floor == BlockType.SAND.id;
        int fillTop = desert ? BlockType.SAND.id : BlockType.GRASS_BLOCK.id;
        int fill = desert ? BlockType.SAND.id : BlockType.DIRT.id;
        int fillDeep = desert ? BlockType.SANDSTONE.id : BlockType.STONE.id;

        // Р¦РµР»РµРІРѕР№ СѓСЂРѕРІРµРЅСЊ вЂ” СЃСЂРµРґРЅСЏСЏ РІС‹СЃРѕС‚Р° СЂРµР»СЊРµС„Р° РІРѕРєСЂСѓРі С†РµРЅС‚СЂР°
        long sum = 0;
        int n = 0;
        for (int x = cx - radius; x <= cx + radius; x += 4) {
            for (int z = cz - radius; z <= cz + radius; z += 4) {
                int h = terrainGen.getHeight(x, z);
                if (h >= TerrainGenerator.SEA_LEVEL) {
                    sum += h;
                    n++;
                }
            }
        }
        int flatY = n == 0 ? terrainGen.getHeight(cx, cz)
                           : (int) Math.round((double) sum / n);
        if (flatY < TerrainGenerator.SEA_LEVEL + 1) flatY = TerrainGenerator.SEA_LEVEL + 1;

        // РљР°Р¶РґР°СЏ РєРѕР»РѕРЅРєР°: РІСЃС‘ РІС‹С€Рµ flatY СЃРЅРѕСЃРёС‚СЃСЏ (РґРµСЂРµРІСЊСЏ, РєР°РјРЅРё, РІРѕРґР° РЅР°
        // РїСЂРёРіРѕСЂРєР°С…), РІСЃС‘ РЅРёР¶Рµ вЂ” Р·Р°СЃС‹РїР°РµС‚СЃСЏ (РІРїР°РґРёРЅС‹, РїСЂСѓРґС‹, РѕРІСЂР°РіРё).
        for (int x = cx - radius - 3; x <= cx + radius + 3; x++) {
            for (int z = cz - radius - 3; z <= cz + radius + 3; z++) {
                int surface = terrainGen.getHeight(x, z);

                // РЎРЅРѕСЃ РІСЃРµРіРѕ РІС‹С€Рµ СѓСЂРѕРІРЅСЏ (СЃС‚РІРѕР»С‹+РєСЂРѕРЅС‹ РґРµСЂРµРІСЊРµРІ РґРѕ +16)
                int top = Math.max(surface, flatY) + 16;
                for (int y = flatY + 1; y <= top && y < Chunk.HEIGHT; y++) {
                    setBlock(chunks, x, y, z, BlockType.AIR);
                }

                // Р—Р°СЃС‹РїРєР° РІРїР°РґРёРЅ
                if (surface < flatY) {
                    for (int y = surface + 1; y <= flatY; y++) {
                        int b;
                        if (y >= flatY - 1) b = fillTop;
                        else if (y >= flatY - 5) b = fill;
                        else b = fillDeep;
                        setBlock(chunks, x, y, z, b);
                    }
                }
            }
        }

        // Trees at the boundary: the flat clear line slices their canopies
        // in half, leaving half a canopy hanging in the air. Sweep an
        // extended band around the site and remove every affected tree
        // WHOLE (trunk + canopy), touching only log and leaf cells.
        int minX = cx - radius - 3, maxX = cx + radius + 3;
        int minZ = cz - radius - 3, maxZ = cz + radius + 3;
        int margin = 6; // largest canopy overhang past the trunk
        for (int x = minX - margin; x <= maxX + margin; x++) {
            for (int z = minZ - margin; z <= maxZ + margin; z++) {
                boolean inCore = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
                if (inCore) continue; // already cleared by the flatten pass

                int surface = terrainGen.getHeight(x, z);
                int scanTop = Math.max(surface, flatY) + 20;
                int scanBottom = Math.min(surface, flatY) + 1;
                for (int y = scanBottom; y <= scanTop && y < Chunk.HEIGHT; y++) {
                    if (isLogId(blockAt(chunks, x, y, z))) {
                        removeWholeTree(chunks, x, y, z);
                        break;
                    }
                }
            }
        }
        return flatY;
    }

    private int blockAt(Map<Long, Chunk> chunks, int x, int y, int z) {
        if (y < 0 || y >= Chunk.HEIGHT) return 0;
        Chunk chunk = chunks.get(Chunk.key(x >> 4, z >> 4));
        if (chunk == null) return 0;
        return chunk.getBlock(x & 15, y, z & 15);
    }

    private static boolean isLogId(int id) {
        return id == BlockType.OAK_LOG.id || id == BlockType.SPRUCE_LOG.id
            || id == BlockType.BIRCH_LOG.id || id == BlockType.JUNGLE_LOG.id
            || id == BlockType.CHARRED_LOG.id || id == BlockType.CHARRED_LOG_TOP.id
            || id == BlockType.PETRIFIED_LOG.id || id == BlockType.PETRIFIED_LOG_TOP.id;
    }

    private static boolean isLeafId(int id) {
        return id == BlockType.OAK_LEAVES.id || id == BlockType.SPRUCE_LEAVES.id
            || id == BlockType.BIRCH_LEAVES.id || id == BlockType.JUNGLE_LEAVES.id
            || id == BlockType.AUTUMN_LEAVES.id || id == BlockType.CHERRY_LEAVES.id;
    }

    /** Remove a whole tree: trunk and canopy, leaving the terrain alone. */
    private void removeWholeTree(Map<Long, Chunk> chunks, int tx, int ty, int tz) {
        int top = ty;
        while (top < Chunk.HEIGHT - 1
                && (isLogId(blockAt(chunks, tx, top + 1, tz))
                    || isLeafId(blockAt(chunks, tx, top + 1, tz)))) {
            top++;
        }
        for (int y = ty; y <= top; y++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    int id = blockAt(chunks, tx + dx, y, tz + dz);
                    if (isLogId(id) || isLeafId(id)) {
                        setBlock(chunks, tx + dx, y, tz + dz, BlockType.AIR);
                    }
                }
            }
        }
    }

    /**
     * РЎРїР»Р°РЅРёСЂРѕРІР°С‚СЊ СЂР°СЃРїРѕР»РѕР¶РµРЅРёРµ Р·РґР°РЅРёР№ РІРѕРєСЂСѓРі С†РµРЅС‚СЂР°.
     */
    private List<BuildingPlacement> planVillage(Random rng, int cx, int cy, int cz,
                                                 int count, int radius,
                                                 BiomeSelector.MCBiome biome) {
        List<BuildingPlacement> result = new ArrayList<>();

        // РўРёРїС‹ Р·РґР°РЅРёР№ СЃ РІРµСЃР°РјРё
        List<BuildingType> types = Arrays.asList(
            BuildingType.SMALL_HOUSE, BuildingType.SMALL_HOUSE, BuildingType.SMALL_HOUSE,
            BuildingType.LARGE_HOUSE, BuildingType.LARGE_HOUSE,
            BuildingType.LIBRARY, BuildingType.BLACKSMITH, BuildingType.BUTCHER,
            BuildingType.CHURCH, BuildingType.MARKET_STALL, BuildingType.MARKET_STALL,
            BuildingType.STABLE, BuildingType.TAVERN, BuildingType.BAKERY,
            BuildingType.BARN, BuildingType.BARN
        );

        Collections.shuffle(types, rng);

        for (int i = 0; i < count; i++) {
            // Р Р°Р·РјРµС‰Р°РµРј РїРѕ РєСЂСѓРіСѓ СЃ С€СѓРјРѕРј
            float angle = (float) (rng.nextFloat() * Math.PI * 2);
            float dist = 8 + rng.nextFloat() * (radius - 8);
            int wx = cx + (int) (Math.cos(angle) * dist);
            int wz = cz + (int) (Math.sin(angle) * dist);
            // РўРµСЂСЂРёС‚РѕСЂРёСЏ СѓР¶Рµ РІС‹СЂРѕРІРЅРµРЅР° вЂ” РІСЃРµ Р·РґР°РЅРёСЏ РЅР° РѕРґРЅРѕРј СѓСЂРѕРІРЅРµ
            int wy = cy;

            // РќРµ СЃР»РёС€РєРѕРј Р±Р»РёР·РєРѕ Рє РґСЂСѓРіРёРј Р·РґР°РЅРёСЏРј
            boolean tooClose = false;
            for (BuildingPlacement existing : result) {
                float dx = existing.wx - wx;
                float dz = existing.wz - wz;
                if (dx * dx + dz * dz < 49) { // 7 Р±Р»РѕРєРѕРІ РјРёРЅРёРјСѓРј
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) continue;

            BuildingType type = types.get(i % types.size());
            result.add(new BuildingPlacement(wx, wy, wz, type, rng.nextInt(4) * 90));
        }

        return result;
    }

    // ==================== Р“Р•РќР•Р РђР¦РРЇ Р—Р”РђРќРР™ ====================

    private void generateTownSquare(Map<Long, Chunk> chunks, int cx, int cy, int cz,
                                     VillageMaterials mat, Random rng) {
        // РљСЂСѓРіР»Р°СЏ РїР»РѕС‰Р°РґСЊ 7x7
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 10) continue;
                setBlock(chunks, cx + dx, cy, cz + dz, mat.floor);
            }
        }

        // РљРѕР»РѕРґРµС† РІ С†РµРЅС‚СЂРµ
        generateWell(chunks, cx, cy + 1, cz, mat);
    }

    private void generateWell(Map<Long, Chunk> chunks, int wx, int wy, int wz,
                               VillageMaterials mat) {
        // РљРѕР»СЊС†Рѕ РёР· РєР°РјРЅСЏ
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    setBlock(chunks, wx, wy - 1, wz, BlockType.WATER);
                    setBlock(chunks, wx, wy, wz, BlockType.AIR);
                } else {
                    setBlock(chunks, wx + dx, wy, wz + dz, mat.stone);
                    setBlock(chunks, wx + dx, wy + 1, wz + dz, mat.stone);
                }
            }
        }

        // РЎС‚РѕР№РєРё Рё РєСЂС‹С€Р°
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                for (int y = 2; y <= 4; y++) {
                    setBlock(chunks, wx + dx, wy + y, wz + dz, mat.wood);
                }
            }
        }

        // РљСЂС‹С€Р°
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlock(chunks, wx + dx, wy + 5, wz + dz, mat.planks);
            }
        }

        // Р’РµРґСЂРѕ РЅР°Рґ РєРѕР»РѕРґС†РµРј
        setBlock(chunks, wx, wy + 5, wz, BlockType.IRON_BLOCK);
    }

    private void generateBuilding(Map<Long, Chunk> chunks, BuildingPlacement bp,
                                   VillageMaterials mat, Random rng) {
        switch (bp.type) {
            case SMALL_HOUSE:
                generateSmallHouse(chunks, bp.wx, bp.wy, bp.wz, mat, rng, bp.rotation);
                break;
            case LARGE_HOUSE:
                generateLargeHouse(chunks, bp.wx, bp.wy, bp.wz, mat, rng, bp.rotation);
                break;
            case LIBRARY:
                generateLibrary(chunks, bp.wx, bp.wy, bp.wz, mat, rng);
                break;
            case BLACKSMITH:
                generateBlacksmith(chunks, bp.wx, bp.wy, bp.wz, mat, rng);
                break;
            case BUTCHER:
                generateButcher(chunks, bp.wx, bp.wy, bp.wz, mat, rng);
                break;
            case CHURCH:
                generateChurch(chunks, bp.wx, bp.wy, bp.wz, mat, rng);
                break;
            case MARKET_STALL:
                generateMarketStall(chunks, bp.wx, bp.wy, bp.wz, mat, rng);
                break;
            case STABLE:
                generateStable(chunks, bp.wx, bp.wy, bp.wz, mat, rng);
                break;
            case TAVERN:
                generateTavern(chunks, bp.wx, bp.wy, bp.wz, mat, rng);
                break;
            case BAKERY:
                generateBakery(chunks, bp.wx, bp.wy, bp.wz, mat, rng);
                break;
            case BARN:
                generateBarn(chunks, bp.wx, bp.wy, bp.wz, mat, rng);
                break;
        }
    }

    /** Р”РІСѓСЃРєР°С‚РЅР°СЏ РєСЂС‹С€Р°: РєРѕРЅС‘Рє РІРґРѕР»СЊ РѕСЃРё X, СЃРєР°С‚С‹ РїРѕ Z. */
    private void generateGableRoof(Map<Long, Chunk> chunks, int wx, int wz, int baseY,
                                   int halfW, int halfD, int roof) {
        for (int i = 0; i <= halfD + 1; i++) {
            int y = baseY + i;
            for (int dx = -halfW - 1; dx <= halfW + 1; dx++) {
                for (int dz = -(halfD + 1 - i); dz <= (halfD + 1 - i); dz++) {
                    setBlock(chunks, wx + dx, y, wz + dz, roof);
                }
            }
        }
    }

    /** РљСЂРѕРІР°С‚СЊ: РЅРѕРіР° РІ СЌС‚РѕР№ РєР»РµС‚РєРµ, РіРѕР»РѕРІР° (РїРѕРґСѓС€РєР°) РІ СЃРѕСЃРµРґРЅРµР№ РїРѕ +X. */
    private void generateBed(Map<Long, Chunk> chunks, int x, int y, int z) {
        setBlock(chunks, x, y, z, BlockType.BED);
        setBlock(chunks, x + 1, y, z, BlockType.BED_HEAD);
    }

    /** РўСЂСѓР±Р° РЅР° СѓРіР»Сѓ РєСЂС‹С€Рё. */
    private void generateChimney(Map<Long, Chunk> chunks, int x, int baseY, int z,
                                 int stone) {
        for (int y = baseY; y <= baseY + 2; y++) {
            setBlock(chunks, x, y, z, stone);
        }
    }

    private void generateSmallHouse(Map<Long, Chunk> chunks, int wx, int wy, int wz,
                                     VillageMaterials mat, Random rng, int rotation) {
        int w = 5 + rng.nextInt(3); // 5-7
        int h = 4;
        int d = 5 + rng.nextInt(3); // 5-7
        int halfW = w / 2;
        int halfD = d / 2;

        // Р¤СѓРЅРґР°РјРµРЅС‚
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfD; dz <= halfD; dz++) {
                setBlock(chunks, wx + dx, wy, wz + dz, mat.floor);
            }
        }

        // РЎС‚РµРЅС‹
        for (int y = 1; y <= h - 1; y++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                setBlock(chunks, wx + dx, wy + y, wz - halfD, mat.wall);
                setBlock(chunks, wx + dx, wy + y, wz + halfD, mat.wall);
            }
            for (int dz = -halfD + 1; dz < halfD; dz++) {
                setBlock(chunks, wx - halfW, wy + y, wz + dz, mat.wall);
                setBlock(chunks, wx + halfW, wy + y, wz + dz, mat.wall);
            }
        }

        // Р”РІРµСЂСЊ (РѕС‚РєСЂС‹С‚Р°СЏ, РїСЂРѕС…РѕРґРёРјР°СЏ)
        installSlidingDoor(chunks, wx, wy + 1, wz - halfD);

        // РћРєРЅР° РІ СЂР°РјР°С…
        int[] winZ = {wx - 1, wx + 1};
        for (int dx : winZ) {
            if (Math.abs(dx - wx) <= halfW && Math.abs(dx - wx) > 0) {
                setBlock(chunks, dx, wy + 1, wz - halfD, mat.wood);
                setBlock(chunks, dx, wy + 2, wz - halfD, BlockType.GLASS);
                setBlock(chunks, dx, wy + 3, wz - halfD, mat.wood);
            }
        }
        for (int dz : new int[]{-1, 1}) {
            if (Math.abs(dz) < halfD) {
                setBlock(chunks, wx - halfW, wy + 2, wz + dz, BlockType.GLASS);
                setBlock(chunks, wx + halfW, wy + 2, wz + dz, BlockType.GLASS);
            }
        }

        // РРЅС‚РµСЂСЊРµСЂ: РєСЂРѕРІР°С‚СЊ, РІРµСЂСЃС‚Р°Рє, РїРµС‡СЊ, СЃСѓРЅРґСѓРє, С„Р°РєРµР»
        int bx = wx - halfW + 1;
        int bz = wz + halfD - 1;
        generateBed(chunks, bx, wy, bz);
        setBlock(chunks, wx, wy + 1, wz + halfD - 1, BlockType.CRAFTING_TABLE);
        setBlock(chunks, wx + halfW - 1, wy + 1, wz - halfD + 1, BlockType.FURNACE);
        setBlock(chunks, wx - halfW + 1, wy + 1, wz - halfD + 1, BlockType.CHEST);
        setBlock(chunks, wx - halfW + 1, wy + 2, wz - halfD + 1, BlockType.TORCH_PLACEHOLDER);
        setBlock(chunks, wx + halfW - 1, wy + 2, wz + halfD - 1, BlockType.TORCH_PLACEHOLDER);

        // РљРѕРІС‘СЂ РѕС‚ РґРІРµСЂРё, РїРѕР»РєР° РЅР°Рґ РєСЂРѕРІР°С‚СЏРјРё, С†РІРµС‚С‹ Сѓ СЃС‚РµРЅС‹
        setBlock(chunks, wx, wy + 1, wz - halfD + 1, BlockType.WHITE_WOOL);
        setBlock(chunks, wx, wy + 2, wz - halfD + 1, BlockType.TORCH_PLACEHOLDER);
        setBlock(chunks, bx, wy + 2, bz, BlockType.BOOKSHELF);
        setBlock(chunks, wx + halfW - 2, wy + 1, wz + halfD - 2, BlockType.DANDELION);

        // Р”РІСѓСЃРєР°С‚РЅР°СЏ РєСЂС‹С€Р°
        generateGableRoof(chunks, wx, wz, wy + h, halfW, halfD, mat.roof);

        // РўСЂСѓР±Р°
        generateChimney(chunks, wx + halfW, wy + h + 1, wz + halfD, mat.stone);
    }

    private void generateLargeHouse(Map<Long, Chunk> chunks, int wx, int wy, int wz,
                                     VillageMaterials mat, Random rng, int rotation) {
        int w = 7 + rng.nextInt(3);
        int h = 5;
        int d = 7 + rng.nextInt(3);
        int halfW = w / 2;
        int halfD = d / 2;

        // РљР°РјРµРЅРЅС‹Р№ С„СѓРЅРґР°РјРµРЅС‚
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfD; dz <= halfD; dz++) {
                setBlock(chunks, wx + dx, wy, wz + dz, mat.stone);
            }
        }

        // Р’С‚РѕСЂРѕР№ СЌС‚Р°Р¶
        for (int y = 1; y <= h; y++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                setBlock(chunks, wx + dx, wy + y, wz - halfD, mat.wall);
                setBlock(chunks, wx + dx, wy + y, wz + halfD, mat.wall);
            }
            for (int dz = -halfD + 1; dz < halfD; dz++) {
                setBlock(chunks, wx - halfW, wy + y, wz + dz, mat.wall);
                setBlock(chunks, wx + halfW, wy + y, wz + dz, mat.wall);
            }
        }

        // РџРѕР» 2-РіРѕ СЌС‚Р°Р¶Р°
        for (int dx = -halfW + 1; dx < halfW; dx++) {
            for (int dz = -halfD + 1; dz < halfD; dz++) {
                setBlock(chunks, wx + dx, wy + 3, wz + dz, mat.planks);
            }
        }

        // Р‘РѕР»СЊС€Р°СЏ РґРІРµСЂСЊ
        installSlidingDoor(chunks, wx - 1, wy + 1, wz - halfD);
        installSlidingDoor(chunks, wx, wy + 1, wz - halfD);

        // РћРєРЅР° (Р±РѕР»СЊС€РёРµ) СЃ СЂР°РјР°РјРё
        for (int i = -halfW + 2; i <= halfW - 2; i += 2) {
            setBlock(chunks, wx + i, wy + 1, wz - halfD, mat.wood);
            setBlock(chunks, wx + i, wy + 2, wz - halfD, BlockType.GLASS);
            setBlock(chunks, wx + i, wy + 3, wz - halfD, mat.wood);
            setBlock(chunks, wx + i, wy + 4, wz - halfD, BlockType.GLASS);
            setBlock(chunks, wx + i, wy + 5, wz - halfD, mat.wood);
        }

        // РРЅС‚РµСЂСЊРµСЂ 1-РіРѕ СЌС‚Р°Р¶Р°: СЃС‚РѕР», СЃСѓРЅРґСѓРєРё, РїРµС‡СЊ, С„Р°РєРµР»С‹
        setBlock(chunks, wx - halfW + 2, wy + 1, wz - halfD + 1, BlockType.CRAFTING_TABLE);
        setBlock(chunks, wx + halfW - 2, wy + 1, wz - halfD + 1, BlockType.FURNACE);
        setBlock(chunks, wx - halfW + 1, wy + 1, wz + halfD - 1, BlockType.CHEST);
        setBlock(chunks, wx + halfW - 1, wy + 1, wz + halfD - 1, BlockType.CHEST);
        setBlock(chunks, wx - halfW + 2, wy + 2, wz + halfD - 2, BlockType.TORCH_PLACEHOLDER);
        setBlock(chunks, wx + halfW - 2, wy + 2, wz + halfD - 2, BlockType.TORCH_PLACEHOLDER);

        // РРЅС‚РµСЂСЊРµСЂ 2-РіРѕ СЌС‚Р°Р¶Р°: РєСЂРѕРІР°С‚Рё
        int bx = wx - halfW + 1;
        generateBed(chunks, bx, wy + 3, wz - halfD + 1);
        generateBed(chunks, bx, wy + 3, wz + halfD - 2);
        setBlock(chunks, wx + halfW - 1, wy + 4, wz, BlockType.TORCH_PLACEHOLDER);

        // РљРѕРІСЂС‹ РЅР° СЌС‚Р°Р¶Р°С…, РїРѕР»РєР° Рё СЃСѓРЅРґСѓРє РЅР°РІРµСЂС…Сѓ, С†РІРµС‚С‹ Сѓ СЃСѓРЅРґСѓРєРѕРІ
        setBlock(chunks, wx, wy + 1, wz - halfD + 1, BlockType.WHITE_WOOL);
        setBlock(chunks, wx, wy + 4, wz, BlockType.WHITE_WOOL);
        setBlock(chunks, wx + halfW - 2, wy + 4, wz + halfD - 2, BlockType.BOOKSHELF);
        setBlock(chunks, wx + halfW - 1, wy + 3, wz + halfD - 2, BlockType.CHEST);
        setBlock(chunks, wx - halfW + 1, wy + 1, wz + halfD - 2, BlockType.POPPY);

        // Р”РІСѓСЃРєР°С‚РЅР°СЏ РєСЂС‹С€Р°
        generateGableRoof(chunks, wx, wz, wy + h + 1, halfW, halfD, mat.roof);

        // Р”РІРµ С‚СЂСѓР±С‹
        generateChimney(chunks, wx + halfW, wy + h + 2, wz + halfD, mat.stone);
        generateChimney(chunks, wx - halfW, wy + h + 2, wz - halfD, mat.stone);
    }

    private void generateLibrary(Map<Long, Chunk> chunks, int wx, int wy, int wz,
                                  VillageMaterials mat, Random rng) {
        int w = 7, h = 5, d = 6;
        int halfW = w / 2, halfD = d / 2;

        // РЎС‚РµРЅС‹
        for (int y = 1; y <= h; y++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                setBlock(chunks, wx + dx, wy + y, wz - halfD, mat.wall);
                setBlock(chunks, wx + dx, wy + y, wz + halfD, mat.wall);
            }
            for (int dz = -halfD; dz <= halfD; dz++) {
                setBlock(chunks, wx - halfW, wy + y, wz + dz, mat.wall);
                setBlock(chunks, wx + halfW, wy + y, wz + dz, mat.wall);
            }
        }

        // РџРѕР»
        for (int dx = -halfW + 1; dx < halfW; dx++) {
            for (int dz = -halfD + 1; dz < halfD; dz++) {
                setBlock(chunks, wx + dx, wy, wz + dz, mat.planks);
            }
        }

        // РљРѕРІС‘СЂ-РґРѕСЂРѕР¶РєР° РїРѕ С†РµРЅС‚СЂСѓ
        for (int dz = -halfD + 1; dz < halfD; dz++) {
            setBlock(chunks, wx, wy + 1, wz + dz, BlockType.WHITE_WOOL);
        }

        // РљРЅРёР¶РЅС‹Рµ РїРѕР»РєРё РІРЅСѓС‚СЂРё
        for (int y = 1; y <= 3; y++) {
            setBlock(chunks, wx - halfW + 1, wy + y, wz + halfD - 1, BlockType.BOOKSHELF);
            setBlock(chunks, wx - halfW + 1, wy + y, wz + halfD - 2, BlockType.BOOKSHELF);
            setBlock(chunks, wx + halfW - 1, wy + y, wz + halfD - 1, BlockType.BOOKSHELF);
            setBlock(chunks, wx + halfW - 1, wy + y, wz + halfD - 2, BlockType.BOOKSHELF);
            setBlock(chunks, wx - halfW + 1, wy + y, wz - halfD + 1, BlockType.BOOKSHELF);
            setBlock(chunks, wx + halfW - 1, wy + y, wz - halfD + 1, BlockType.BOOKSHELF);
        }

        // Р”РІРµСЂСЊ
        installSlidingDoor(chunks, wx, wy + 1, wz - halfD);

        // Р‘РѕР»СЊС€РёРµ РѕРєРЅР° РІ СЂР°РјР°С…
        for (int i = -2; i <= 2; i += 4) {
            setBlock(chunks, wx + i, wy + 1, wz - halfD, mat.wood);
            setBlock(chunks, wx + i, wy + 2, wz - halfD, BlockType.GLASS);
            setBlock(chunks, wx + i, wy + 3, wz - halfD, BlockType.GLASS);
            setBlock(chunks, wx + i, wy + 4, wz - halfD, mat.wood);
        }

        // РЎС‚РѕР» Рё С„Р°РєРµР»С‹
        setBlock(chunks, wx + halfW - 2, wy + 1, wz - halfD + 1, BlockType.CRAFTING_TABLE);
        setBlock(chunks, wx - halfW + 1, wy + 2, wz - halfD + 1, BlockType.TORCH_PLACEHOLDER);
        setBlock(chunks, wx + halfW - 1, wy + 2, wz + halfD - 1, BlockType.TORCH_PLACEHOLDER);

        // РЎСѓРЅРґСѓРє Рё РїРµС‡СЊ Р±РёР±Р»РёРѕС‚РµРєР°СЂСЏ
        setBlock(chunks, wx + halfW - 2, wy + 1, wz, BlockType.CHEST);
        setBlock(chunks, wx - halfW + 2, wy + 1, wz + halfD - 3, BlockType.FURNACE);

        // Р”РІСѓСЃРєР°С‚РЅР°СЏ РєСЂС‹С€Р°
        generateGableRoof(chunks, wx, wz, wy + h + 1, halfW, halfD, mat.roof);
    }

    private void generateBlacksmith(Map<Long, Chunk> chunks, int wx, int wy, int wz,
                                     VillageMaterials mat, Random rng) {
        int w = 6, h = 4, d = 5;
        int halfW = w / 2, halfD = d / 2;

        // РЎС‚РµРЅС‹
        for (int y = 1; y <= h; y++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                setBlock(chunks, wx + dx, wy + y, wz - halfD, mat.stone);
                setBlock(chunks, wx + dx, wy + y, wz + halfD, mat.stone);
            }
            for (int dz = -halfD; dz <= halfD; dz++) {
                setBlock(chunks, wx - halfW, wy + y, wz + dz, mat.stone);
                setBlock(chunks, wx + halfW, wy + y, wz + dz, mat.stone);
            }
        }

        // РџРѕР»
        for (int dx = -halfW + 1; dx < halfW; dx++) {
            for (int dz = -halfD + 1; dz < halfD; dz++) {
                setBlock(chunks, wx + dx, wy, wz + dz, mat.stone);
            }
        }

        // Р“РѕСЂРЅ: РїРµС‡СЊ + Р»Р°РІР°, РЅР°РєРѕРІР°Р»СЊРЅСЏ, РІРµСЂСЃС‚Р°Рє, СЃСѓРЅРґСѓРєРё
        setBlock(chunks, wx - halfW + 1, wy + 1, wz + halfD - 1, BlockType.FURNACE);
        setBlock(chunks, wx + halfW - 1, wy + 1, wz, BlockType.IRON_BLOCK);
        setBlock(chunks, wx + halfW - 1, wy + 2, wz, BlockType.IRON_BLOCK);
        setBlock(chunks, wx - halfW + 1, wy + 1, wz - halfD + 1, BlockType.CRAFTING_TABLE);
        setBlock(chunks, wx - halfW + 1, wy + 1, wz, BlockType.CHEST);
        setBlock(chunks, wx + halfW - 1, wy + 1, wz + halfD - 1, BlockType.CHEST);
        setBlock(chunks, wx - halfW + 1, wy + 2, wz, BlockType.TORCH_PLACEHOLDER);

        // Р›Р°РІР° РґР»СЏ РїРµС‡Рё
        setBlock(chunks, wx - halfW + 1, wy, wz + halfD - 1, BlockType.LAVA);

        // Р”РІРµСЂСЊ
        installSlidingDoor(chunks, wx, wy + 1, wz - halfD);

        // Р”РІСѓСЃРєР°С‚РЅР°СЏ РєСЂС‹С€Р°
        generateGableRoof(chunks, wx, wz, wy + h + 1, halfW, halfD, mat.stone);

        // Р”С‹РјРѕС…РѕРґ
        for (int y = h + 1; y <= h + 3; y++) {
            setBlock(chunks, wx - halfW + 1, wy + y, wz + halfD - 1, mat.stone);
        }

        // Р¤РѕРЅР°СЂСЊ Сѓ РІС…РѕРґР°
        setBlock(chunks, wx, wy + 1, wz - halfD - 2, mat.wood);
        setBlock(chunks, wx, wy + 2, wz - halfD - 2, mat.wood);
        setBlock(chunks, wx, wy + 3, wz - halfD - 2, BlockType.GLOWSTONE);
    }

    private void generateButcher(Map<Long, Chunk> chunks, int wx, int wy, int wz,
                                  VillageMaterials mat, Random rng) {
        // РњР°Р»РµРЅСЊРєР°СЏ Р»Р°РІРєР° 4x3x4
        int w = 4, h = 3, d = 4;
        int halfW = w / 2, halfD = d / 2;

        for (int y = 1; y <= h; y++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                setBlock(chunks, wx + dx, wy + y, wz - halfD, mat.planks);
                setBlock(chunks, wx + dx, wy + y, wz + halfD, mat.planks);
            }
            for (int dz = -halfD; dz <= halfD; dz++) {
                setBlock(chunks, wx - halfW, wy + y, wz + dz, mat.planks);
                setBlock(chunks, wx + halfW, wy + y, wz + dz, mat.planks);
            }
        }

        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfD; dz <= halfD; dz++) {
                setBlock(chunks, wx + dx, wy, wz + dz, mat.floor);
            }
        }

        // Р”РІРµСЂСЊ
        installSlidingDoor(chunks, wx, wy + 1, wz - halfD);

        // РџСЂРёР»Р°РІРѕРє, СЂР°Р·РґРµР»РѕС‡РЅР°СЏ РєРѕР»РѕРґР°, РїРµС‡СЊ, СЃСѓРЅРґСѓРє
        setBlock(chunks, wx - halfW + 1, wy + 1, wz + halfD - 1, BlockType.FURNACE);
        setBlock(chunks, wx + halfW - 1, wy + 1, wz + halfD - 1, BlockType.OAK_LOG);
        setBlock(chunks, wx, wy + 1, wz + halfD - 1, BlockType.CRAFTING_TABLE);
        setBlock(chunks, wx + halfW - 1, wy + 1, wz - halfD + 1, BlockType.CHEST);
        setBlock(chunks, wx - halfW + 1, wy + 2, wz - halfD + 1, BlockType.TORCH_PLACEHOLDER);

        // Р”РІСѓСЃРєР°С‚РЅР°СЏ РєСЂС‹С€Р°
        generateGableRoof(chunks, wx, wz, wy + h + 1, halfW, halfD, mat.roof);
    }

    private void generateChurch(Map<Long, Chunk> chunks, int wx, int wy, int wz,
                                 VillageMaterials mat, Random rng) {
        int w = 7, h = 6, d = 10;
        int halfW = w / 2, halfD = d / 2;

        // РЎС‚РµРЅС‹ РІС‹СЃРѕРєРёРµ
        for (int y = 1; y <= h; y++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                setBlock(chunks, wx + dx, wy + y, wz - halfD, mat.stone);
                setBlock(chunks, wx + dx, wy + y, wz + halfD, mat.stone);
            }
            for (int dz = -halfD; dz <= halfD; dz++) {
                setBlock(chunks, wx - halfW, wy + y, wz + dz, mat.stone);
                setBlock(chunks, wx + halfW, wy + y, wz + dz, mat.stone);
            }
        }

        // РџРѕР»
        for (int dx = -halfW + 1; dx < halfW; dx++) {
            for (int dz = -halfD + 1; dz < halfD; dz++) {
                setBlock(chunks, wx + dx, wy, wz + dz, mat.stone);
            }
        }

        // РЎРєР°РјСЊРё (СЂСЏРґС‹ РїР»РёС‚)
        for (int dz = -halfD + 1; dz < halfD; dz += 2) {
            for (int dx = -halfW + 1; dx < halfW; dx += 2) {
                setBlock(chunks, wx + dx, wy + 1, wz + dz, BlockType.OAK_SLAB);
                setBlock(chunks, wx + dx + 1, wy + 1, wz + dz, BlockType.OAK_SLAB);
            }
        }

        // РђР»С‚Р°СЂСЊ
        setBlock(chunks, wx, wy + 1, wz + halfD - 1, BlockType.STONE_BRICKS);
        setBlock(chunks, wx, wy + 2, wz + halfD - 1, BlockType.STONE_BRICKS);
        setBlock(chunks, wx, wy + 3, wz + halfD - 1, BlockType.GOLD_BLOCK);

        // Р‘РѕР»СЊС€РёРµ РІРёС‚СЂР°Р¶РЅС‹Рµ РѕРєРЅР°
        for (int i = -halfW + 1; i < halfW; i += 2) {
            for (int y = 2; y <= 4; y++) {
                setBlock(chunks, wx + i, wy + y, wz - halfD, BlockType.GLASS);
                setBlock(chunks, wx + i, wy + y, wz + halfD, BlockType.GLASS);
            }
        }

        // Р”РІРѕР№РЅР°СЏ РґРІРµСЂСЊ РІС‹СЃРѕС‚РѕР№ 2 Р±Р»РѕРєР° (РєР°Рє РІ MC), СЃРІРµСЂС…Сѓ вЂ” СЃС‚РµРЅР°
        installSlidingDoor(chunks, wx - 1, wy + 1, wz - halfD);
        installSlidingDoor(chunks, wx, wy + 1, wz - halfD);
        setBlock(chunks, wx - 1, wy + 3, wz - halfD, mat.stone);
        setBlock(chunks, wx, wy + 3, wz - halfD, mat.stone);

        // Р¤Р°РєРµР»С‹ РЅР° РєРѕР»РѕРЅРЅР°С…
        setBlock(chunks, wx - halfW + 1, wy + 3, wz, BlockType.TORCH_PLACEHOLDER);
        setBlock(chunks, wx + halfW - 1, wy + 3, wz, BlockType.TORCH_PLACEHOLDER);

        // Р‘Р°С€РЅСЏ-РєРѕР»РѕРєРѕР»СЊРЅСЏ СЃ РєРѕР»РѕРєРѕР»РѕРј
        for (int y = h + 1; y <= h + 5; y++) {
            setBlock(chunks, wx, wy + y, wz + halfD, mat.stone);
            setBlock(chunks, wx - 1, wy + y, wz + halfD, mat.stone);
            setBlock(chunks, wx + 1, wy + y, wz + halfD, mat.stone);
            setBlock(chunks, wx, wy + y, wz + halfD - 1, mat.stone);
        }
        setBlock(chunks, wx, wy + h + 6, wz + halfD, BlockType.GOLD_BLOCK);

        // Р”РІСѓСЃРєР°С‚РЅР°СЏ РєСЂС‹С€Р°
        generateGableRoof(chunks, wx, wz, wy + h + 1, halfW, halfD, mat.roof);
    }

    private void generateMarketStall(Map<Long, Chunk> chunks, int wx, int wy, int wz,
                                      VillageMaterials mat, Random rng) {
        // Р С‹РЅРѕС‡РЅС‹Р№ РїСЂРёР»Р°РІРѕРє 3x3
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlock(chunks, wx + dx, wy, wz + dz, mat.planks);
            }
        }

        // РЎС‚РѕР»Р±С‹
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                setBlock(chunks, wx + dx, wy + 1, wz + dz, mat.wood);
                setBlock(chunks, wx + dx, wy + 2, wz + dz, mat.wood);
            }
        }

        // РќР°РІРµСЃ (СЃР»СѓС‡Р°Р№РЅС‹Р№ С†РІРµС‚)
        int[] awningColors = {
            BlockType.WHITE_WOOL.id, BlockType.RED_CONCRETE.id,
            BlockType.BLUE_CONCRETE.id, BlockType.GREEN_CONCRETE.id,
            BlockType.WHITE_CONCRETE.id
        };
        int awning = awningColors[rng.nextInt(awningColors.length)];
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                setBlock(chunks, wx + dx, wy + 3, wz + dz, awning);
            }
        }

        // РўРѕРІР°СЂС‹ РЅР° РїСЂРёР»Р°РІРєРµ
        setBlock(chunks, wx - 1, wy + 1, wz, BlockType.CRAFTING_TABLE);
        setBlock(chunks, wx + 1, wy + 1, wz, BlockType.FURNACE);
        setBlock(chunks, wx, wy + 1, wz + 1, BlockType.CHEST);
        setBlock(chunks, wx - 1, wy + 1, wz - 1, BlockType.WHEAT);
        setBlock(chunks, wx + 1, wy + 1, wz - 1, BlockType.CARROT);

        // Р›Р°РјРїР° РЅР° СЃС‚РѕР»Р±Рµ Сѓ РїСЂРёР»Р°РІРєР°
        setBlock(chunks, wx + 2, wy + 1, wz + 2, mat.wood);
        setBlock(chunks, wx + 2, wy + 2, wz + 2, mat.wood);
        setBlock(chunks, wx + 2, wy + 3, wz + 2, BlockType.GLOWSTONE);
    }

    private void generateStable(Map<Long, Chunk> chunks, int wx, int wy, int wz,
                                 VillageMaterials mat, Random rng) {
        int w = 8, h = 4, d = 5;
        int halfW = w / 2, halfD = d / 2;

        // РЎС‚РµРЅС‹ СЃ РїСЂРѕС‘РјР°РјРё
        for (int y = 1; y <= h; y++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                if (dx != 0 && dx != 1 && dx != -1) { // РћС‚РєСЂС‹С‚С‹Р№ С„Р°СЃР°Рґ
                    setBlock(chunks, wx + dx, wy + y, wz - halfD, mat.wood);
                }
                setBlock(chunks, wx + dx, wy + y, wz + halfD, mat.wood);
            }
            for (int dz = -halfD; dz <= halfD; dz++) {
                setBlock(chunks, wx - halfW, wy + y, wz + dz, mat.wood);
                setBlock(chunks, wx + halfW, wy + y, wz + dz, mat.wood);
            }
        }

        // РџРѕР»
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfD; dz <= halfD; dz++) {
                setBlock(chunks, wx + dx, wy, wz + dz, BlockType.DIRT);
            }
        }

        // Р”РІСѓСЃРєР°С‚РЅР°СЏ РєСЂС‹С€Р°
        generateGableRoof(chunks, wx, wz, wy + h + 1, halfW, halfD, mat.planks);

        // РљРѕСЂРјСѓС€РєР° СЃ РјРѕСЂРєРѕРІСЊСЋ Рё СЃСѓРЅРґСѓРє
        setBlock(chunks, wx, wy + 1, wz + halfD - 1, BlockType.OAK_PLANKS);
        setBlock(chunks, wx - 1, wy + 1, wz + halfD - 1, BlockType.OAK_PLANKS);
        setBlock(chunks, wx + 1, wy + 1, wz + halfD - 1, BlockType.OAK_PLANKS);
        setBlock(chunks, wx, wy + 2, wz + halfD - 1, BlockType.CARROT);
        setBlock(chunks, wx + halfW - 1, wy + 1, wz - halfD + 1, BlockType.CHEST);
        setBlock(chunks, wx - halfW + 1, wy + 1, wz - halfD + 1, BlockType.WATER);

        // РџРµС‡РєР° Рё РІРµСЂСЃС‚Р°Рє Сѓ Р·Р°РґРЅРµР№ СЃС‚РµРЅС‹
        setBlock(chunks, wx - halfW + 1, wy + 1, wz + halfD - 2, BlockType.FURNACE);
        setBlock(chunks, wx - halfW + 1, wy + 1, wz + halfD - 1, BlockType.CRAFTING_TABLE);

        // РЎС‚РѕР№Р»Р°-РїРµСЂРµРіРѕСЂРѕРґРєРё
        setBlock(chunks, wx - 2, wy + 1, wz + 1, mat.wood);
        setBlock(chunks, wx + 2, wy + 1, wz + 1, mat.wood);
    }

    private void generateTavern(Map<Long, Chunk> chunks, int wx, int wy, int wz,
                                 VillageMaterials mat, Random rng) {
        int w = 6, h = 4, d = 5;
        int halfW = w / 2, halfD = d / 2;

        // РЎС‚РµРЅС‹
        for (int y = 1; y <= h; y++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                setBlock(chunks, wx + dx, wy + y, wz - halfD, mat.wall);
                setBlock(chunks, wx + dx, wy + y, wz + halfD, mat.wall);
            }
            for (int dz = -halfD + 1; dz < halfD; dz++) {
                setBlock(chunks, wx - halfW, wy + y, wz + dz, mat.wall);
                setBlock(chunks, wx + halfW, wy + y, wz + dz, mat.wall);
            }
        }

        // РџРѕР»
        for (int dx = -halfW + 1; dx < halfW; dx++) {
            for (int dz = -halfD + 1; dz < halfD; dz++) {
                setBlock(chunks, wx + dx, wy, wz + dz, mat.planks);
            }
        }

        // Р”РІРµСЂСЊ
        installSlidingDoor(chunks, wx, wy + 1, wz - halfD);

        // РћРєРЅР° СЃ СЂР°РјР°РјРё
        setBlock(chunks, wx - 2, wy + 1, wz - halfD, mat.wood);
        setBlock(chunks, wx - 2, wy + 2, wz - halfD, BlockType.GLASS);
        setBlock(chunks, wx - 2, wy + 3, wz - halfD, mat.wood);
        setBlock(chunks, wx + 2, wy + 1, wz - halfD, mat.wood);
        setBlock(chunks, wx + 2, wy + 2, wz - halfD, BlockType.GLASS);
        setBlock(chunks, wx + 2, wy + 3, wz - halfD, mat.wood);

        // Р‘Р°СЂРЅР°СЏ СЃС‚РѕР№РєР° РІРґРѕР»СЊ СЃС‚РµРЅС‹
        for (int dz = -halfD + 1; dz < halfD; dz++) {
            setBlock(chunks, wx + halfW - 1, wy + 1, wz + dz, BlockType.OAK_SLAB);
        }
        // РўР°Р±СѓСЂРµС‚С‹
        for (int dz = -halfD + 1; dz < halfD; dz += 2) {
            setBlock(chunks, wx + halfW - 3, wy + 1, wz + dz, BlockType.STONE_SLAB);
        }
        // Р‘РѕС‡РєРё Рё СЃС‚РѕР»С‹
        setBlock(chunks, wx - halfW + 1, wy + 1, wz - halfD + 1, BlockType.CHEST);
        setBlock(chunks, wx - halfW + 1, wy + 1, wz + halfD - 2, BlockType.CRAFTING_TABLE);
        setBlock(chunks, wx - halfW + 1, wy + 1, wz + halfD - 1, BlockType.FURNACE);
        setBlock(chunks, wx, wy + 1, wz + halfD - 1, BlockType.CRAFTING_TABLE);
        setBlock(chunks, wx, wy + 1, wz + halfD - 2, BlockType.STONE_SLAB);
        setBlock(chunks, wx + 1, wy + 1, wz + halfD - 2, BlockType.STONE_SLAB);

        // Р¤Р°РєРµР»С‹
        setBlock(chunks, wx - halfW + 1, wy + 2, wz, BlockType.TORCH_PLACEHOLDER);
        setBlock(chunks, wx + halfW - 1, wy + 2, wz + halfD - 1, BlockType.TORCH_PLACEHOLDER);

        // РљРѕРІС‘СЂ Сѓ СЃС‚РѕР№РєРё Рё С†РІРµС‚С‹ РЅР° СЃС‚РѕР»Р°С…
        setBlock(chunks, wx + halfW - 2, wy + 1, wz, BlockType.WHITE_WOOL);
        setBlock(chunks, wx, wy + 2, wz + halfD - 2, BlockType.POPPY);
        setBlock(chunks, wx + 1, wy + 2, wz + halfD - 2, BlockType.DANDELION);

        // Р”РІСѓСЃРєР°С‚РЅР°СЏ РєСЂС‹С€Р°
        generateGableRoof(chunks, wx, wz, wy + h + 1, halfW, halfD, mat.roof);

        // РўСЂСѓР±Р°
        generateChimney(chunks, wx - halfW, wy + h + 2, wz + halfD, mat.stone);
    }

    private void generateBakery(Map<Long, Chunk> chunks, int wx, int wy, int wz,
                                 VillageMaterials mat, Random rng) {
        int w = 5, h = 4, d = 4;
        int halfW = w / 2, halfD = d / 2;

        // РЎС‚РµРЅС‹ РёР· РєРёСЂРїРёС‡Р°
        for (int y = 1; y <= h; y++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                setBlock(chunks, wx + dx, wy + y, wz - halfD, BlockType.BRICK);
                setBlock(chunks, wx + dx, wy + y, wz + halfD, BlockType.BRICK);
            }
            for (int dz = -halfD + 1; dz < halfD; dz++) {
                setBlock(chunks, wx - halfW, wy + y, wz + dz, BlockType.BRICK);
                setBlock(chunks, wx + halfW, wy + y, wz + dz, BlockType.BRICK);
            }
        }

        // РџРѕР»
        for (int dx = -halfW + 1; dx < halfW; dx++) {
            for (int dz = -halfD + 1; dz < halfD; dz++) {
                setBlock(chunks, wx + dx, wy, wz + dz, BlockType.STONE_BRICKS);
            }
        }

        // Р”РІРµСЂСЊ
        installSlidingDoor(chunks, wx, wy + 1, wz - halfD);

        // РћРєРЅР°
        setBlock(chunks, wx - 1, wy + 1, wz - halfD, BlockType.BRICK);
        setBlock(chunks, wx - 1, wy + 2, wz - halfD, BlockType.GLASS);
        setBlock(chunks, wx - 1, wy + 3, wz - halfD, BlockType.BRICK);
        setBlock(chunks, wx + 1, wy + 1, wz - halfD, BlockType.BRICK);
        setBlock(chunks, wx + 1, wy + 2, wz - halfD, BlockType.GLASS);
        setBlock(chunks, wx + 1, wy + 3, wz - halfD, BlockType.BRICK);

        // Р‘РѕР»СЊС€Р°СЏ РїРµС‡СЊ
        setBlock(chunks, wx + halfW - 1, wy + 1, wz + halfD - 1, BlockType.FURNACE);
        setBlock(chunks, wx + halfW - 2, wy + 1, wz + halfD - 1, BlockType.STONE_BRICKS);
        setBlock(chunks, wx + halfW - 1, wy + 1, wz + halfD - 2, BlockType.STONE_BRICKS);
        setBlock(chunks, wx + halfW - 2, wy + 1, wz + halfD - 2, BlockType.FURNACE);
        setBlock(chunks, wx + halfW - 1, wy + 2, wz + halfD - 1, BlockType.STONE_BRICKS);
        setBlock(chunks, wx + halfW - 2, wy + 2, wz + halfD - 1, BlockType.STONE_BRICKS);

        // РњРµС€РєРё СЃ Р·РµСЂРЅРѕРј (СЃСѓРЅРґСѓРєРё) Рё СЃС‚РѕР»
        setBlock(chunks, wx - halfW + 1, wy + 1, wz + halfD - 1, BlockType.CHEST);
        setBlock(chunks, wx - halfW + 1, wy + 1, wz - halfD + 1, BlockType.CRAFTING_TABLE);
        setBlock(chunks, wx, wy + 1, wz + halfD - 1, BlockType.CRAFTING_TABLE);
        setBlock(chunks, wx - halfW + 1, wy + 2, wz, BlockType.TORCH_PLACEHOLDER);

        // Р”РІСѓСЃРєР°С‚РЅР°СЏ РєСЂС‹С€Р°
        generateGableRoof(chunks, wx, wz, wy + h + 1, halfW, halfD, mat.roof);

        // РўСЂСѓР±Р° РїРµС‡Рё
        generateChimney(chunks, wx + halfW - 1, wy + h + 2, wz + halfD - 1, BlockType.BRICK.id);
    }

    /**
     * [CR] РђРјР±Р°СЂ: Р±РѕР»СЊС€РѕР№ РѕС‚РєСЂС‹С‚С‹Р№ Р·Р°Р», РєСѓРґР° С„РµСЂРјРµСЂС‹ СЃРЅРѕСЃСЏС‚ СѓСЂРѕР¶Р°Р№.
     * Р’РґРѕР»СЊ Р·Р°РґРЅРµР№ СЃС‚РµРЅС‹ СЃС‚РѕСЏС‚ С‚СЋРєРё СЃРµРЅР° (РґРµРєРѕСЂ), Сѓ РІС…РѕРґР° вЂ” СЃРІРѕР±РѕРґРЅР°СЏ
     * РїР»РѕС‰Р°РґРєР°, РЅР° РєРѕС‚РѕСЂРѕР№ С„РµСЂРјРµСЂ СЃРєР»Р°РґС‹РІР°РµС‚ РЅРѕРІС‹Рµ С‚СЋРєРё (HAY_BALE).
     */
    private void generateBarn(Map<Long, Chunk> chunks, int wx, int wy, int wz,
                              VillageMaterials mat, Random rng) {
        int w = 10, h = 4, d = 7;
        int halfW = w / 2, halfD = d / 2;

        // РЎС‚РµРЅС‹ СЃ РѕРєРЅР°РјРё РїРѕ Р±РѕРєР°Рј
        for (int y = 1; y <= h; y++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                setBlock(chunks, wx + dx, wy + y, wz - halfD, mat.wood);
                setBlock(chunks, wx + dx, wy + y, wz + halfD, mat.wood);
            }
            for (int dz = -halfD; dz <= halfD; dz++) {
                setBlock(chunks, wx - halfW, wy + y, wz + dz, mat.wood);
                setBlock(chunks, wx + halfW, wy + y, wz + dz, mat.wood);
            }
        }

        // РџРѕР»
        for (int dx = -halfW + 1; dx < halfW; dx++) {
            for (int dz = -halfD + 1; dz < halfD; dz++) {
                setBlock(chunks, wx + dx, wy, wz + dz, mat.planks);
            }
        }

        // Р”РІРµСЂСЊ (РІРѕСЂРѕС‚Р°)
        installSlidingDoor(chunks, wx, wy + 1, wz - halfD);

        // РћРєРЅР°
        for (int ox : new int[]{-3, 3}) {
            setBlock(chunks, wx + ox, wy + 1, wz - halfD, mat.wall);
            setBlock(chunks, wx + ox, wy + 2, wz - halfD, BlockType.GLASS);
            setBlock(chunks, wx + ox, wy + 3, wz - halfD, mat.wall);
        }

        // РўСЋРєРё СЃРµРЅР° РІРґРѕР»СЊ Р·Р°РґРЅРµР№ СЃС‚РµРЅС‹ (РґРµРєРѕСЂ, РґРІРµ РІС‹СЃРѕС‚С‹)
        for (int dx = -4; dx <= 4; dx++) {
            setBlock(chunks, wx + dx, wy + 1, wz + halfD - 1, BlockType.HAY_BALE);
            if (Math.abs(dx) != 4 && rng.nextBoolean()) {
                setBlock(chunks, wx + dx, wy + 2, wz + halfD - 1, BlockType.HAY_BALE);
            }
        }

        // РЎСѓРЅРґСѓРє СЃ РёРЅСЃС‚СЂСѓРјРµРЅС‚Р°РјРё, РІРµСЂСЃС‚Р°Рє Рё С„РѕРЅР°СЂСЊ
        setBlock(chunks, wx - halfW + 1, wy + 1, wz + halfD - 2, BlockType.CHEST);
        setBlock(chunks, wx - halfW + 1, wy + 1, wz + halfD - 3, BlockType.CRAFTING_TABLE);
        setBlock(chunks, wx + halfW - 1, wy + 2, wz - halfD + 1, BlockType.TORCH_PLACEHOLDER);

        // Р”РІСѓСЃРєР°С‚РЅР°СЏ РєСЂС‹С€Р°
        generateGableRoof(chunks, wx, wz, wy + h + 1, halfW, halfD, mat.roof);
    }

    // ==================== Р”РћР РћР“Р Р Р”Р•РљРћР  ====================

    private void generateRoads(Map<Long, Chunk> chunks, int cx, int cy, int cz,
                                List<BuildingPlacement> buildings,
                                VillageMaterials mat, Random rng) {
        // Р”РѕСЂРѕРіРё РѕС‚ С†РµРЅС‚СЂР° Рє РґРІРµСЂРё РєР°Р¶РґРѕРіРѕ Р·РґР°РЅРёСЏ
        for (BuildingPlacement bp : buildings) {
            generateRoad(chunks, cx, cy, cz, bp.wx, bp.wz - 4, mat, rng);
        }
    }

    private void generateRoad(Map<Long, Chunk> chunks, int x1, int y1, int z1,
                               int x2, int z2, VillageMaterials mat, Random rng) {
        int dx = x2 - x1;
        int dz = z2 - z1;
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps == 0) return;

        float fx = x1, fz = z1;
        float stepX = (float) dx / steps;
        float stepZ = (float) dz / steps;

        for (int i = 0; i <= steps; i++) {
            int wx = (int) fx;
            int wz = (int) fz;
            // РўРµСЂСЂРёС‚РѕСЂРёСЏ СѓР¶Рµ РІС‹СЂРѕРІРЅРµРЅР° вЂ” РґРѕСЂРѕРіР° РІСЃРµРіРґР° РЅР° РѕРґРЅРѕРј СѓСЂРѕРІРЅРµ
            setBlock(chunks, wx, y1 + 1, wz, mat.road);
            setBlock(chunks, wx + 1, y1 + 1, wz, mat.road);
            if (i % 4 == 0) {
                // РђРєС†РµРЅС‚РЅС‹Рµ РїР»РёС‚С‹ РґР»СЏ С‚РµРєСЃС‚СѓСЂС‹
                setBlock(chunks, wx, y1 + 1, wz + 1, BlockType.STONE_SLAB);
            }
            fx += stepX;
            fz += stepZ;
        }
    }

    private void generateLamps(Map<Long, Chunk> chunks, int cx, int cy, int cz,
                                List<BuildingPlacement> buildings,
                                VillageMaterials mat, Random rng) {
        // Р¤РѕРЅР°СЂРё РІРґРѕР»СЊ РґРѕСЂРѕРі
        for (BuildingPlacement bp : buildings) {
            int dx = bp.wx - cx;
            int dz = bp.wz - cz;
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            int steps = Math.max(1, dist / 5);

            for (int i = 1; i < steps; i++) {
                float t = (float) i / steps;
                int lx = cx + (int) (dx * t);
                int lz = cz + (int) (dz * t);

                // Р¤РѕРЅР°СЂСЊ: Р·Р°Р±РѕСЂ + glowstone
                setBlock(chunks, lx, cy + 1, lz, mat.wood);
                setBlock(chunks, lx, cy + 2, lz, mat.wood);
                setBlock(chunks, lx, cy + 3, lz, BlockType.GLOWSTONE);
            }
        }
    }

    /** РћРіРѕСЂРѕРґ Сѓ РґРѕРјР°: РёР·РіРѕСЂРѕРґСЊ + РіСЂСЏРґРєРё + С†РІРµС‚С‹. */
    private void generateGarden(Map<Long, Chunk> chunks, int hx, int hy, int hz,
                                 VillageMaterials mat, Random rng, List<int[]> cropSink) {
        // РЎР°Рґ СЃ +X СЃС‚РѕСЂРѕРЅС‹ РґРѕРјР° (РґРІРµСЂСЊ СЃ -Z); СЃС‚РµРЅР° РґРѕРјР° РјР°РєСЃРёРјСѓРј РЅР°
        // halfW <= 3, РїРѕСЌС‚РѕРјСѓ СЃР°Рґ РЅР°С‡РёРЅР°РµС‚СЃСЏ СЃ hx + 4
        int gx = hx + 6;
        int gz = hz;
        int half = 2;

        // Р—РµРјР»СЏ
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                setBlock(chunks, gx + dx, hy, gz + dz, BlockType.DIRT);
            }
        }

        // Р“СЂСЏРґРєРё (СЃРјРµСЃСЊ РєСѓР»СЊС‚СѓСЂ)
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if (Math.abs(dx) == half || Math.abs(dz) == half) continue;
                float r = rng.nextFloat();
                int crop = r < 0.45f ? BlockType.WHEAT.id
                          : r < 0.72f ? BlockType.CARROT.id
                          : BlockType.POTATO.id;
                setBlock(chunks, gx + dx, hy + 1, gz + dz, crop);
                cropSink.add(new int[]{gx + dx, hy + 1, gz + dz});
            }
        }

        // Р¦РІРµС‚С‹ РїРѕ СѓРіР»Р°Рј
        setBlock(chunks, gx - half, hy + 1, gz - half, BlockType.DANDELION);
        setBlock(chunks, gx + half, hy + 1, gz - half, BlockType.POPPY);
        setBlock(chunks, gx - half, hy + 1, gz + half, BlockType.POPPY);
        setBlock(chunks, gx + half, hy + 1, gz + half, BlockType.DANDELION);

        // РР·РіРѕСЂРѕРґСЊ РїРѕ РїРµСЂРёРјРµС‚СЂСѓ (СЃ РїСЂРѕС…РѕРґРѕРј)
        for (int dx = -half - 1; dx <= half + 1; dx++) {
            setBlock(chunks, gx + dx, hy + 1, gz - half - 1, mat.wood);
            setBlock(chunks, gx + dx, hy + 1, gz + half + 1, mat.wood);
        }
        for (int dz = -half - 1; dz <= half + 1; dz++) {
            if (dz == -1 || dz == 0) continue; // РїСЂРѕС…РѕРґ
            setBlock(chunks, gx - half - 1, hy + 1, gz + dz, mat.wood);
            setBlock(chunks, gx + half + 1, hy + 1, gz + dz, mat.wood);
        }
    }

    /** Р”РµСЂРµРІРѕ РґР»СЏ СѓРєСЂР°С€РµРЅРёСЏ РґРµСЂРµРІРЅРё. */
    private void generateDecorativeTree(Map<Long, Chunk> chunks, int tx, int ty, int tz,
                                        int log, int leaves, Random rng) {
        int h = 3 + rng.nextInt(2);
        for (int y = 1; y <= h; y++) {
            setBlock(chunks, tx, ty + y, tz, log);
        }
        // РљСЂРѕРЅР°: 3x3 РІ РґРІР° СЃР»РѕСЏ + РјР°РєСѓС€РєР°
        for (int y = h - 1; y <= h; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0 && y == h) continue;
                    if (Math.abs(dx) == 1 && Math.abs(dz) == 1 && y == h) continue;
                    setBlock(chunks, tx + dx, ty + y + 1, tz + dz, leaves);
                }
            }
        }
        setBlock(chunks, tx, ty + h + 2, tz, leaves);
    }

    private void generateSquareDecor(Map<Long, Chunk> chunks, int cx, int cy, int cz,
                                      VillageMaterials mat, Random rng,
                                      BiomeSelector.MCBiome biome) {
        // Р›Р°РІРѕС‡РєРё Сѓ РїР»РѕС‰Р°РґРё
        for (int dz : new int[]{-5, 5}) {
            for (int dx = -1; dx <= 1; dx++) {
                setBlock(chunks, cx + dx, cy + 1, cz + dz, BlockType.STONE_SLAB);
            }
        }
        for (int dx : new int[]{-5, 5}) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlock(chunks, cx + dx, cy + 1, cz + dz, BlockType.STONE_SLAB);
            }
        }

        // Р¦РІРµС‚С‹ РІРѕРєСЂСѓРі РїР»РѕС‰Р°РґРё
        int[][] spots = {
            {-4, -4}, {4, -4}, {-4, 4}, {4, 4},
            {-6, -2}, {6, -2}, {-6, 2}, {6, 2},
            {-2, -6}, {2, -6}, {-2, 6}, {2, 6}
        };
        for (int[] s : spots) {
            if (rng.nextFloat() < 0.7f) {
                int flower = rng.nextFloat() < 0.5f ? BlockType.DANDELION.id
                                                      : BlockType.POPPY.id;
                setBlock(chunks, cx + s[0], cy + 1, cz + s[1], flower);
            }
        }

        // Р”РµСЂРµРІСЊСЏ/РєР°РєС‚СѓСЃС‹ РїРѕ СѓРіР»Р°Рј РґРµСЂРµРІРЅРё (Р·Р°РІРёСЃРёС‚ РѕС‚ Р±РёРѕРјР°)
        boolean desert = mat.floor == BlockType.SAND.id;
        int log, leaves;
        switch (getBiomeName(biome)) {
            case "snowy":
                log = BlockType.SPRUCE_LOG.id;
                leaves = BlockType.SPRUCE_LEAVES.id;
                break;
            case "jungle":
                log = BlockType.JUNGLE_LOG.id;
                leaves = BlockType.JUNGLE_LEAVES.id;
                break;
            default:
                log = BlockType.OAK_LOG.id;
                leaves = BlockType.OAK_LEAVES.id;
                break;
        }
        if (desert) {
            if (rng.nextFloat() < 0.6f) {
                setBlock(chunks, cx - 8, cy + 1, cz - 4, BlockType.CACTUS);
                setBlock(chunks, cx - 8, cy + 2, cz - 4, BlockType.CACTUS);
            }
            if (rng.nextFloat() < 0.6f) {
                setBlock(chunks, cx + 8, cy + 1, cz + 4, BlockType.CACTUS);
                setBlock(chunks, cx + 8, cy + 2, cz + 4, BlockType.CACTUS);
            }
        } else {
            generateDecorativeTree(chunks, cx - 8, cy, cz - 4, log, leaves, rng);
            generateDecorativeTree(chunks, cx + 8, cy, cz + 4, log, leaves, rng);
            if (rng.nextFloat() < 0.6f) {
                generateDecorativeTree(chunks, cx + 8, cy, cz - 6, log, leaves, rng);
            }
            if (rng.nextFloat() < 0.6f) {
                generateDecorativeTree(chunks, cx - 8, cy, cz + 6, log, leaves, rng);
            }
        }

        // Р¤РѕРЅР°СЂРё РїРѕ СѓРіР»Р°Рј РїР»РѕС‰Р°РґРё
        int[][] lamps = {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}};
        for (int[] l : lamps) {
            setBlock(chunks, cx + l[0], cy + 1, cz + l[1], mat.wood);
            setBlock(chunks, cx + l[0], cy + 2, cz + l[1], mat.wood);
            setBlock(chunks, cx + l[0], cy + 3, cz + l[1], BlockType.GLOWSTONE);
        }
    }

    private void generateFarms(Map<Long, Chunk> chunks, int cx, int cy, int cz,
                                List<BuildingPlacement> buildings,
                                VillageMaterials mat, Random rng,
                                BiomeSelector.MCBiome biome, List<int[]> cropSink) {
        int farmCount = 2;
        if (buildings.size() > 10) farmCount = 3;

        for (int i = 0; i < farmCount; i++) {
            // Р¤РµСЂРјС‹ РІРѕРєСЂСѓРі РґРµСЂРµРІРЅРё РЅР° СЃР»СѓС‡Р°Р№РЅС‹С… РЅР°РїСЂР°РІР»РµРЅРёСЏС…
            float angle = (float) (rng.nextFloat() * Math.PI * 2) + (float) i * 2.1f;
            int dist = 24 + rng.nextInt(9); // 24-32 Р±Р»РѕРєР° РѕС‚ С†РµРЅС‚СЂР°
            int farmX = cx + (int) (Math.cos(angle) * dist);
            int farmZ = cz + (int) (Math.sin(angle) * dist);

            // РќРµ СЃР»РёС€РєРѕРј Р±Р»РёР·РєРѕ Рє Р·РґР°РЅРёСЏРј
            boolean tooClose = false;
            for (BuildingPlacement bp : buildings) {
                float dx = bp.wx - farmX;
                float dz = bp.wz - farmZ;
                if (dx * dx + dz * dz < 144) { // 12 Р±Р»РѕРєРѕРІ РјРёРЅРёРјСѓРј
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) continue;

            generateFarm(chunks, farmX, cy, farmZ, mat, rng, cropSink);
        }
    }

    private void generateFarm(Map<Long, Chunk> chunks, int fx, int fy, int fz,
                              VillageMaterials mat, Random rng, List<int[]> cropSink) {
        int farmW = 9 + rng.nextInt(5); // 9-13
        int farmD = 9 + rng.nextInt(5);
        int halfW = farmW / 2;
        int halfD = farmD / 2;

        // Р“СЂСЏРґРєРё СЃ РІРѕРґСЏРЅС‹Рј РєР°РЅР°Р»РѕРј РїРѕ С†РµРЅС‚СЂСѓ
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfD; dz <= halfD; dz++) {
                if (dz == 0) {
                    setBlock(chunks, fx + dx, fy, fz + dz, BlockType.WATER);
                } else {
                    setBlock(chunks, fx + dx, fy, fz + dz, BlockType.DIRT);
                    // РЎРјРµСЃСЊ РєСѓР»СЊС‚СѓСЂ РїРѕ СЂСЏРґР°Рј
                    float r = rng.nextFloat();
                    int crop = r < 0.5f ? BlockType.WHEAT.id
                              : r < 0.75f ? BlockType.CARROT.id
                              : BlockType.POTATO.id;
                    setBlock(chunks, fx + dx, fy + 1, fz + dz, crop);
                    cropSink.add(new int[]{fx + dx, fy + 1, fz + dz});
                }
            }
        }

        // РћРіСЂР°РґР° СЃ РІРѕСЂРѕС‚Р°РјРё (РІРѕСЂРѕС‚Р° РЅР° РїРµСЂРµРґРЅРµР№ Рё Р·Р°РґРЅРµР№ СЃС‚РѕСЂРѕРЅР°С…)
        for (int dz = -halfD - 1; dz <= halfD + 1; dz++) {
            setBlock(chunks, fx - halfW - 1, fy + 1, fz + dz, mat.wood);
            setBlock(chunks, fx + halfW + 1, fy + 1, fz + dz, mat.wood);
        }
        for (int dx = -halfW - 1; dx <= halfW + 1; dx++) {
            if (dx == 0) continue; // РІРѕСЂРѕС‚Р°
            setBlock(chunks, fx + dx, fy + 1, fz - halfD - 1, mat.wood);
            setBlock(chunks, fx + dx, fy + 1, fz + halfD + 1, mat.wood);
        }

        // РћРіРѕСЂРѕРґРЅРѕРµ РїСѓРіР°Р»Рѕ РІ СѓРіР»Сѓ
        setBlock(chunks, fx + halfW - 1, fy + 1, fz + halfD - 1, mat.wood);
        setBlock(chunks, fx + halfW - 1, fy + 2, fz + halfD - 1, mat.wood);
        setBlock(chunks, fx + halfW, fy + 2, fz + halfD - 1, mat.wood);
        setBlock(chunks, fx + halfW - 2, fy + 2, fz + halfD - 1, mat.wood);
    }

    // ==================== РњРђРўР•Р РРђР›Р« ====================

    private VillageMaterials getVillageMaterials(BiomeSelector.MCBiome biome) {
        return switch (biome) {
            case DESERT, BADLANDS, WOODED_BADLANDS -> new VillageMaterials(
                BlockType.SANDSTONE, BlockType.SANDSTONE, BlockType.SANDSTONE,
                BlockType.OAK_PLANKS, BlockType.SAND, BlockType.OAK_LOG,
                BlockType.SANDSTONE
            );
            case TAIGA, OLD_GROWTH_PINE_TAIGA, OLD_GROWTH_SPRUCE_TAIGA, SNOWY_PLAINS, SNOWY_TAIGA ->
                new VillageMaterials(
                    BlockType.SPRUCE_PLANKS, BlockType.SPRUCE_LOG, BlockType.SPRUCE_LOG,
                    BlockType.SPRUCE_PLANKS, BlockType.DIRT, BlockType.SPRUCE_LOG,
                    BlockType.SPRUCE_PLANKS
                );
            case SAVANNA, SAVANNA_PLATEAU -> new VillageMaterials(
                BlockType.ACACIA_PLANKS, BlockType.ACACIA_PLANKS, BlockType.OAK_LOG,
                BlockType.ACACIA_PLANKS, BlockType.DIRT, BlockType.OAK_LOG,
                BlockType.ACACIA_PLANKS
            );
            case JUNGLE -> new VillageMaterials(
                BlockType.JUNGLE_PLANKS, BlockType.JUNGLE_LOG, BlockType.JUNGLE_LOG,
                BlockType.JUNGLE_PLANKS, BlockType.DIRT, BlockType.JUNGLE_LOG,
                BlockType.JUNGLE_PLANKS
            );
            case BIRCH_FOREST -> new VillageMaterials(
                BlockType.BIRCH_PLANKS, BlockType.BIRCH_LOG, BlockType.BIRCH_LOG,
                BlockType.BIRCH_PLANKS, BlockType.DIRT, BlockType.BIRCH_LOG,
                BlockType.BIRCH_PLANKS
            );
            default -> new VillageMaterials(
                BlockType.OAK_PLANKS, BlockType.OAK_LOG, BlockType.COBBLESTONE,
                BlockType.OAK_PLANKS, BlockType.COBBLESTONE, BlockType.OAK_LOG,
                BlockType.OAK_PLANKS
            );
        };
    }

    private Villager.Profession getProfessionForBuilding(BuildingType type) {
        return switch (type) {
            case SMALL_HOUSE, TAVERN -> Villager.Profession.values()[
                (int) (Math.random() * Villager.Profession.values().length)];
            case LARGE_HOUSE -> Villager.Profession.LIBRARIAN;
            case LIBRARY -> Villager.Profession.LIBRARIAN;
            case BLACKSMITH -> Villager.Profession.BLACKSMITH;
            case BUTCHER -> Villager.Profession.BUTCHER;
            case CHURCH -> Villager.Profession.PRIEST;
            case MARKET_STALL -> Villager.Profession.FARMER;
            case STABLE -> Villager.Profession.SHEPHERD;
            case BAKERY -> Villager.Profession.FARMER;
            case BARN -> Villager.Profession.FARMER;
        };
    }

    private String getBiomeName(BiomeSelector.MCBiome biome) {
        return switch (biome) {
            case DESERT, BADLANDS, WOODED_BADLANDS -> "desert";
            case TAIGA, OLD_GROWTH_PINE_TAIGA, OLD_GROWTH_SPRUCE_TAIGA, SNOWY_PLAINS, SNOWY_TAIGA -> "snowy";
            case SAVANNA, SAVANNA_PLATEAU -> "savanna";
            case JUNGLE -> "jungle";
            case SWAMP, MOOR -> "swamp";
            default -> "plains";
        };
    }

    // ==================== Р’РЎРџРћРњРћР“РђРўР•Р›Р¬РќРћР• ====================

    private void setBlock(Map<Long, Chunk> chunks, int x, int y, int z, BlockType type) {
        setBlock(chunks, x, y, z, type.id);
    }

    private void setBlock(Map<Long, Chunk> chunks, int x, int y, int z, int block) {
        if (y < 0 || y >= Chunk.HEIGHT) return;
        int cx = x >> 4;
        int cz = z >> 4;
        Chunk chunk = chunks.get(Chunk.key(cx, cz));
        if (chunk == null) return;
        int lx = x & 15;
        int lz = z & 15;
        chunk.setBlock(lx, y, lz, block);
    }

    /**
     * [MC] Ставит обычную дубовую дверь в проём дома: обе половины (низ +
     * верх) занимают клетки проёма. Раньше здесь стояли раздвижные двери
     * [SD]; теперь деревни используют ванильную дверь с поворотом.
     */
    private void installSlidingDoor(Map<Long, Chunk> chunks, int wx, int wy, int wz) {
        setBlock(chunks, wx, wy + 1, wz, (byte) 0);
        setBlock(chunks, wx, wy + 2, wz, (byte) 0);
        setBlock(chunks, wx, wy + 1, wz, BlockType.OAK_DOOR.id);
        setBlock(chunks, wx, wy + 2, wz, BlockType.OAK_DOOR.id);
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

    // ==================== Р’РќРЈРўР Р•РќРќРР• РљР›РђРЎРЎР« ====================

    enum BuildingType {
        SMALL_HOUSE, LARGE_HOUSE, LIBRARY, BLACKSMITH,
        BUTCHER, CHURCH, MARKET_STALL, STABLE, TAVERN, BAKERY, BARN
    }

    static class BuildingPlacement {
        final int wx, wy, wz;
        final BuildingType type;
        final int rotation;

        BuildingPlacement(int wx, int wy, int wz, BuildingType type, int rotation) {
            this.wx = wx;
            this.wy = wy;
            this.wz = wz;
            this.type = type;
            this.rotation = rotation;
        }
    }

    static class VillageMaterials {
        final int wall;
        final int roof;
        final int stone;
        final int planks;
        final int floor;
        final int wood;
        final int road;

        VillageMaterials(BlockType wall, BlockType roof, BlockType stone,
                          BlockType planks, BlockType floor, BlockType wood, BlockType road) {
            this.wall = wall.id;
            this.roof = roof.id;
            this.stone = stone.id;
            this.planks = planks.id;
            this.floor = floor.id;
            this.wood = wood.id;
            this.road = road.id;
        }
    }
}