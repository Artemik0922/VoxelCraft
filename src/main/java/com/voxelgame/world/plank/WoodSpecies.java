package com.voxelgame.world.plank;

import com.voxelgame.world.BlockType;

/**
 * Wood species available for the improved plank system.
 *
 * Hardness/resistance are in the engine's block scale (oak planks = 2.0).
 * Flammability is the block-burn chance from {@link BlockType#flammability}.
 * Water resistance is metadata only: the engine has no rot/water damage.
 */
public enum WoodSpecies {

    OAK("oak", 2.0f, 2.0f, 0.15f, 0, BlockType.OAK_LOG, 0xB8905A),
    SPRUCE("spruce", 2.0f, 2.0f, 0.15f, 0, BlockType.SPRUCE_LOG, 0x7A5A3A),
    BIRCH("birch", 2.0f, 2.0f, 0.15f, 0, BlockType.BIRCH_LOG, 0xD8C898),
    JUNGLE("jungle", 2.0f, 2.0f, 0.15f, 0, BlockType.JUNGLE_LOG, 0xB08050);

    public final String id;
    public final String langKey;
    public final float hardness;
    public final float resistance;
    public final float flammability;
    public final int waterResistance;
    public final BlockType logBlock;
    public final int baseColor;

    WoodSpecies(String id, float hardness, float resistance, float flammability,
                int waterResistance, BlockType logBlock, int baseColor) {
        this.id = id;
        this.langKey = "wood." + id;
        this.hardness = hardness;
        this.resistance = resistance;
        this.flammability = flammability;
        this.waterResistance = waterResistance;
        this.logBlock = logBlock;
        this.baseColor = baseColor;
    }

    /** Look up by registry id ("oak"), or null. */
    public static WoodSpecies fromId(String id) {
        for (WoodSpecies s : values()) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }
}