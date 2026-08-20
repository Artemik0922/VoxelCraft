package com.voxelgame.world.biome;

import org.joml.Vector3f;

/** Immutable view of a biome loaded from biomes.json. */
public final class BiomeData {

    public final String id;
    public final String nameKey;
    public final float tempMin, tempMax;
    public final float humidMin, humidMax;
    public final float contMin, contMax;
    public final int grassTint;
    public final int foliageTint;
    public final int waterTint;
    public final String surfaceBlock;
    public final String fillerBlock;
    public final String treeType;
    public final float treeDensity;
    public final String flowerType;
    public final float flowerDensity;
    public final String grassType;
    public final float grassDensity;
    public final String particleType;
    public final float particleDensity;
    public final int fogColor;

    public BiomeData(String id, String nameKey,
                     float tempMin, float tempMax,
                     float humidMin, float humidMax,
                     float contMin, float contMax,
                     int grassTint, int foliageTint, int waterTint,
                     String surfaceBlock, String fillerBlock,
                     String treeType, float treeDensity,
                     String flowerType, float flowerDensity,
                     String grassType, float grassDensity,
                     String particleType, float particleDensity,
                     int fogColor) {
        this.id = id;
        this.nameKey = nameKey;
        this.tempMin = tempMin;
        this.tempMax = tempMax;
        this.humidMin = humidMin;
        this.humidMax = humidMax;
        this.contMin = contMin;
        this.contMax = contMax;
        this.grassTint = grassTint;
        this.foliageTint = foliageTint;
        this.waterTint = waterTint;
        this.surfaceBlock = surfaceBlock;
        this.fillerBlock = fillerBlock;
        this.treeType = treeType;
        this.treeDensity = treeDensity;
        this.flowerType = flowerType;
        this.flowerDensity = flowerDensity;
        this.grassType = grassType;
        this.grassDensity = grassDensity;
        this.particleType = particleType;
        this.particleDensity = particleDensity;
        this.fogColor = fogColor;
    }

    /** Validated surface block (never null). */
    public String validSurface() {
        return (surfaceBlock != null && !surfaceBlock.isEmpty()) ? surfaceBlock : "stone";
    }

    /** Validated filler block (never null). */
    public String validFiller() {
        return (fillerBlock != null && !fillerBlock.isEmpty()) ? fillerBlock : "dirt";
    }

    /** Validated tree type. */
    public String validTreeType() {
        return (treeType != null && !treeType.isEmpty()) ? treeType : "none";
    }

    /** Validated flower type. */
    public String validFlowerType() {
        return (flowerType != null && !flowerType.isEmpty()) ? flowerType : "none";
    }

    /** Validated grass type. */
    public String validGrassType() {
        return (grassType != null && !grassType.isEmpty()) ? grassType : "none";
    }

    @Override
    public String toString() {
        return id;
    }
}
