package com.voxelgame.item;

import com.voxelgame.world.BlockType;

import java.util.HashMap;
import java.util.Map;

/**
 * [GP-054][GP-056] Harvest requirements and drop overrides per block.
 *
 * A block with a requirement only drops anything when the player mines it
 * with the right tool kind AND a tier at least as high as the minimum. Ores
 * with a drop override drop the raw item instead of the block itself.
 *
 * Hand-mining stone works exactly like Minecraft: the block breaks and the
 * progress bar fills, but nothing drops until you hold a pickaxe.
 */
public final class BlockHarvest {

    /** Requirements for one block; null entries mean "no requirement". */
    public static final class Requirement {
        public final ToolTier minTier;
        public final ToolType tool;
        /** What the block drops instead of itself; null = the block. */
        public final BlockType drop;
        /** What the block drops as a registry item instead of a block;
         *  null = use {@link #drop}. Lets ores hand out real Item stacks
         *  (emerald, meteorite ingot) instead of pseudo-block ITEM_* entries. */
        public final Item dropItem;

        Requirement(ToolTier minTier, ToolType tool, BlockType drop, Item dropItem) {
            this.minTier = minTier;
            this.tool = tool;
            this.drop = drop;
            this.dropItem = dropItem;
        }
    }

    private static final Map<Integer, Requirement> REQUIREMENTS = new HashMap<>();

    private static void req(BlockType block, ToolTier tier, ToolType tool, BlockType drop) {
        REQUIREMENTS.put(block.id, new Requirement(tier, tool, drop, null));
    }

    /** Requirement whose drop is a registry item, not a block. */
    private static void reqItem(BlockType block, ToolTier tier, ToolType tool, Item dropItem) {
        REQUIREMENTS.put(block.id, new Requirement(tier, tool, null, dropItem));
    }

    static {
        // Ores: pickaxe, tier gate, raw drop
        req(BlockType.COAL_ORE, ToolTier.WOOD, ToolType.PICKAXE, BlockType.ITEM_COAL);
        req(BlockType.IRON_ORE, ToolTier.STONE, ToolType.PICKAXE, BlockType.ITEM_IRON_INGOT);
        req(BlockType.GOLD_ORE, ToolTier.IRON, ToolType.PICKAXE, BlockType.ITEM_GOLD_INGOT);
        req(BlockType.DIAMOND_ORE, ToolTier.IRON, ToolType.PICKAXE, BlockType.ITEM_DIAMOND);
        // [ECO] Emerald is the currency: mined as a real registry item so
        // both crafting grids and village trade accept it
        reqItem(BlockType.EMERALD_ORE, ToolTier.STONE, ToolType.PICKAXE, ItemRegistry.EMERALD);
        req(BlockType.REDSTONE_ORE, ToolTier.STONE, ToolType.PICKAXE, null);
        // [ENCH] Lapis drops lapis lazuli instead of the ore block
        req(BlockType.LAPIS_ORE, ToolTier.STONE, ToolType.PICKAXE, BlockType.ITEM_LAPIS);
        req(BlockType.COPPER_ORE, ToolTier.STONE, ToolType.PICKAXE, null);
        // [ECO] Meteorite ore lands in asteroid craters; smelt the chunks
        reqItem(BlockType.METEORITE_ORE, ToolTier.IRON, ToolType.PICKAXE, ItemRegistry.METEORITE_INGOT);

        // Hard blocks: any pickaxe, nothing drops by hand
        req(BlockType.OBSIDIAN, ToolTier.DIAMOND, ToolType.PICKAXE, null);
        req(BlockType.STONE, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.COBBLESTONE, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.MOSSY_COBBLESTONE, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.STONE_BRICKS, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.BRICK, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.GRANITE, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.POLISHED_GRANITE, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.DIORITE, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.POLISHED_DIORITE, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.ANDESITE, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.POLISHED_ANDESITE, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.DEEPSLATE, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.BASALT, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.END_STONE, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.PURPUR_BLOCK, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.NETHERRACK, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.NETHER_BRICKS, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.COAL_BLOCK, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.IRON_BLOCK, ToolTier.STONE, ToolType.PICKAXE, null);
        req(BlockType.GOLD_BLOCK, ToolTier.IRON, ToolType.PICKAXE, null);
        req(BlockType.DIAMOND_BLOCK, ToolTier.IRON, ToolType.PICKAXE, null);
        req(BlockType.EMERALD_BLOCK, ToolTier.IRON, ToolType.PICKAXE, null);
        req(BlockType.TERRACOTTA, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.SALT, ToolTier.WOOD, ToolType.PICKAXE, null);
        req(BlockType.ASH, ToolTier.WOOD, ToolType.PICKAXE, null);

        // [GP-020] An open door always drops the closed door item
        req(BlockType.OAK_DOOR_OPEN, null, ToolType.NONE, BlockType.OAK_DOOR);

        // [GP-002] Stone slabs are stone: pickaxe required, drop themselves
        req(BlockType.STONE_SLAB, ToolTier.WOOD, ToolType.PICKAXE, null);

        // [GP-073] Fire drops nothing when broken
        req(BlockType.FIRE, null, ToolType.NONE, null);

        // [BED] The head half always drops the bed item, so a broken bed
        // never turns into two beds
        req(BlockType.BED_HEAD, null, ToolType.NONE, BlockType.BED);
    }

    /** Requirements for a block, or null when it drops freely. */
    public static Requirement get(BlockType block) {
        if (block == null) return null;
        return REQUIREMENTS.get(block.id);
    }
}