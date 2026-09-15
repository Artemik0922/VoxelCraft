package com.voxelgame.rendering;

import com.voxelgame.world.BlockType;
import com.voxelgame.world.Chunk;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.EXTTextureFilterAnisotropic;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Texture Atlas - 16x16 tiles packed into a 256x256 atlas.
 *
 * Tiles are loaded from textures/blocks/*.png (CC0 pack) when available and
 * fall back to procedural pixel art when a PNG is missing.
 */
public class TextureAtlas {
    public static final int ATLAS_SIZE = 512;
    public static final int TEXTURE_SIZE = 16;
    public static final int TEXTURES_PER_ROW = ATLAS_SIZE / TEXTURE_SIZE;

    /** 16 -> 8 -> 4 -> 2 -> 1. Stop before tiles turn into a single averaged texel. */
    private static final int MIP_LEVELS = 5;
    private static final float ANISOTROPY = 8.0f;

    /** Variants generated for high-coverage tiles like grass and stone. */
    private static final int VARIANTS = 4;

    // Face indices used by ChunkMeshBuilder: 0=+X 1=-X 2=+Y 3=-Y 4=+Z 5=-Z
    public static final int FACE_TOP = 2;
    public static final int FACE_BOTTOM = 3;

    private Texture atlasTexture;
    private int arrayTextureId = 0;
    private float anisotropyUsed = 1.0f;
    private final int[] atlasPixels = new int[ATLAS_SIZE * ATLAS_SIZE];
    /** One 16x16 ARGB tile per layer, in slot order. Feeds the texture array. */
    private final List<int[]> layerPixels = new ArrayList<>();
    private final Map<String, Integer> slotByName = new HashMap<>();
    /** tile name -> how many variants exist */
    private final Map<String, Integer> variantCount = new HashMap<>();
    /** tile name -> slot of variant 0 (variants are contiguous) */
    private final Map<String, Integer> variantFirstSlot = new HashMap<>();
    /** face slot -> tile name, for resolving variants at mesh time */
    private final Map<Integer, String> nameBySlot = new HashMap<>();
    private ResourcePack resourcePack;
    private final Map<Integer, int[]> faceSlots = new HashMap<>(); // blockId -> {top, side, bottom}
    private int nextSlot = 0;
    private int loadedFromPng = 0;
    private int generated = 0;

    /**
     * UV rectangle of one tile inside the atlas.
     * v1 = bottom edge of the tile, v2 = top edge (atlas is uploaded flipped).
     */
    public static class TextureCoords {
        public final float u1, v1, u2, v2;

        public TextureCoords(int slot) {
            int x = slot % TEXTURES_PER_ROW;
            int y = slot / TEXTURES_PER_ROW;
            // Half-texel inset stops neighbouring tiles bleeding in at mip/edge
            float pad = 0.5f / ATLAS_SIZE;

            this.u1 = (x * TEXTURE_SIZE) / (float) ATLAS_SIZE + pad;
            this.u2 = ((x + 1) * TEXTURE_SIZE) / (float) ATLAS_SIZE - pad;
            this.v1 = 1.0f - ((y + 1) * TEXTURE_SIZE) / (float) ATLAS_SIZE + pad;
            this.v2 = 1.0f - (y * TEXTURE_SIZE) / (float) ATLAS_SIZE - pad;
        }
    }

    public TextureAtlas() {
        this("");
    }

    public TextureAtlas(String resourcePackName) {
        this.resourcePack = new ResourcePack(resourcePackName);

        registerTiles();
        mapBlockFaces();

        // Reverse index so getSlot() can find a tile's variant group
        for (Map.Entry<String, Integer> e : slotByName.entrySet()) {
            nameBySlot.put(e.getValue(), baseName(e.getKey()));
        }

        build();
    }

    private static String baseName(String key) {
        int hash = key.indexOf('#');
        return hash < 0 ? key : key.substring(0, hash);
    }

    // ------------------------------------------------------------------
    // Tile registration
    // ------------------------------------------------------------------

    private void registerTiles() {
        // Tiles with several variants: the mesher picks one per block
        // position, which breaks up the repetition on large flat surfaces.
        //
        // Foliage is generated rather than taken from a pack: the bundled
// [TA] GRASS_BLOCK is not biome-tinted (BiomeColors.isFoliage skips it), so
        // the PNG carries the full colour: a juicy saturated green.
        tile("grass_top", "grass_top.png", BlockType.GRASS_BLOCK);
        tile("dirt", "dirt.png", BlockType.DIRT);
        variants("stone", BlockType.STONE, VARIANTS);
        variants("sand", BlockType.SAND, VARIANTS);

        // Grass side: loads grass_side.png from pack, procedural fallback
        tile("grass_side", "grass_side.png", BlockType.GRASS_BLOCK);
        tile("cobblestone",       "cobblestone.png",       BlockType.COBBLESTONE);
        tile("mossy_cobblestone", "mossy_cobblestone.png", BlockType.MOSSY_COBBLESTONE);
        tile("oak_planks",        "oak_planks.png",        BlockType.OAK_PLANKS);
        // [GP-PLANKS] Improved planks: 32 PNG textures generated by
        // tools/PlankTextureGenerator (PNG primary, procedural fallback)
        for (com.voxelgame.world.plank.PlankVariant v
                : com.voxelgame.world.plank.PlankBlockRegistry.REGISTERED) {
            tile(v.getTextureBaseName(), v.getTextureBaseName() + ".png", BlockType.OAK_PLANKS);
        }
        tile("oak_log",           "oak_log.png",           BlockType.OAK_LOG);
        tile("oak_log_top",       "oak_log_top.png",       BlockType.OAK_LOG);
        // Birch logs
        tile("birch_log_side",    "birch_log_side.png",    BlockType.BIRCH_LOG);
        tile("birch_log_top",     "birch_log_top.png",     BlockType.BIRCH_LOG);
        // Birch leaves
        tile("birch_leaves",      "birch_leaves.png",      BlockType.BIRCH_LEAVES);
        // Generated for the same reason as grass, and because a pack tile
        // would not carry the ~30% cut-outs the canopy needs.
        // Both variants are uploaded so the graphics setting can switch
        // between them without rebuilding the atlas.
        variants("oak_leaves", BlockType.OAK_LEAVES, VARIANTS);
        variants("oak_leaves_opaque", BlockType.OAK_LEAVES, VARIANTS);
        variants("spruce_leaves", BlockType.SPRUCE_LEAVES, VARIANTS);
        variants("spruce_leaves_opaque", BlockType.SPRUCE_LEAVES, VARIANTS);
        variants("jungle_leaves", BlockType.JUNGLE_LEAVES, VARIANTS);
        variants("jungle_leaves_opaque", BlockType.JUNGLE_LEAVES, VARIANTS);
        tile("sandstone",         "sandstone.png",         BlockType.TERRACOTTA);
        tile("gravel",            "gravel.png",            BlockType.GRAVEL);
        // Overlays: mineral specks meant to sit on top of a stone tile
        tile("ore_coal",    "ore_coal.png",    BlockType.COAL_ORE);
        tile("ore_iron",    "ore_iron.png",    BlockType.IRON_ORE);
        tile("ore_gold",    "ore_gold.png",    BlockType.GOLD_ORE);
        tile("ore_diamond", "ore_diamond.png", BlockType.DIAMOND_ORE);
        tile("bedrock",           "bedrock.png",           BlockType.BEDROCK);
        tile("water",             "water.png",             BlockType.WATER);
        tile("lava",              "lava.png",              BlockType.LAVA);
        tile("glass",             "glass.png",             BlockType.GLASS);
        tile("brick",             "brick.png",             BlockType.BRICK);
        tile("stone_bricks",      "stone_bricks.png",      BlockType.STONE_BRICKS);
        tile("granite",           "granite.png",           BlockType.GRANITE);
        tile("tnt",               "tnt.png",               BlockType.TNT);
        tile("gold_block",        "gold_block.png",        BlockType.GOLD_BLOCK);
        tile("iron_block",        "iron_block.png",        BlockType.IRON_BLOCK);
        tile("diamond_block",     "diamond_block.png",     BlockType.DIAMOND_BLOCK);
        tile("coal_block",        "coal_block.png",        BlockType.COAL_BLOCK);
        tile("crafting_table",    "crafting_table.png",    BlockType.CRAFTING_TABLE);
        tile("snow",              "snow.png",              BlockType.SNOW);
        tile("ice",               "ice.png",               BlockType.ICE);
        tile("clay",              "clay.png",              BlockType.CLAY);
        tile("podzol",            "podzol.png",            BlockType.PODZOL);
        tile("obsidian",          "obsidian.png",          BlockType.OBSIDIAN);
        tile("netherrack",        "netherrack.png",        BlockType.NETHERRACK);
        tile("glowstone",         "glowstone.png",         BlockType.GLOWSTONE);

        // Cross-quad plants: cut-outs on a transparent background
        tile("grass_plant", "grass_plant.png", BlockType.GRASS_PLANT);
        tile("dandelion", "dandelion.png", BlockType.DANDELION);
        tile("poppy", "poppy.png", BlockType.POPPY);
        tile("dead_bush", "dead_bush.png", BlockType.DEAD_BUSH);
        tile("cactus_side",       "cactus_side.png",       BlockType.CACTUS);
        tile("cactus_top",        "cactus_top.png",        BlockType.CACTUS);

        // --- Biome blocks ---
        tile("autumn_leaves", "autumn_leaves.png", BlockType.AUTUMN_LEAVES);
        tile("cherry_leaves", "cherry_leaves.png", BlockType.CHERRY_LEAVES);
        tile("lavender", "lavender.png", BlockType.LAVENDER);
        tileGenerated("crystal", BlockType.CRYSTAL);
        tile("basalt", "basalt.png", BlockType.BASALT);
        tile("salt", "salt.png", BlockType.SALT);
        tile("ash", "ash.png", BlockType.ASH);
        tile("charred_log", "charred_log.png", BlockType.CHARRED_LOG);
        tile("charred_log_top", "charred_log_top.png", BlockType.CHARRED_LOG);
        tile("petrified_log", "petrified_log.png", BlockType.PETRIFIED_LOG);
        tile("petrified_log_top", "petrified_log_top.png", BlockType.PETRIFIED_LOG);
        tile("blue_ice", "blue_ice.png", BlockType.BLUE_ICE);
        tile("mycelium_top", "mycelium_top.png", BlockType.MYCELIUM);
        tile("terracotta", "terracotta.png", BlockType.TERRACOTTA);
        tile("red_sand", "red_sand.png", BlockType.RED_SAND);
        tile("lily_pad", "lily_pad.png", BlockType.LILY_PAD);
        tile("sunflower", "sunflower.png", BlockType.SUNFLOWER);
        tile("vine", "vine.png", BlockType.VINE);
        tile("deepslate", "deepslate.png", BlockType.DEEPSLATE);

        // [WG] World-gen expansion blocks
        tile("bamboo", "bamboo.png", BlockType.BAMBOO);
        tile("coral", "coral.png", BlockType.CORAL);
        tile("seagrass", "seagrass.png", BlockType.SEAGRASS);
        tile("packed_ice", "packed_ice.png", BlockType.PACKED_ICE);
        tile("mob_spawner", "mob_spawner.png", BlockType.MOB_SPAWNER);
        tile("rails", "rails.png", BlockType.RAILS);
        tile("chiseled_sandstone", "chiseled_sandstone.png", BlockType.CHISELED_SANDSTONE);
        tile("end_portal_frame", "end_portal_frame.png", BlockType.END_PORTAL_FRAME);
        tile("end_portal", "end_portal.png", BlockType.END_PORTAL);

        // --- Missing blocks for creative inventory ---
        tile("stone", "stone.png", BlockType.STONE);
        tile("dirt", "dirt.png", BlockType.DIRT);
        tile("sand", "sand.png", BlockType.SAND);
        tile("granite", "granite.png", BlockType.GRANITE);
        tile("diorite", "diorite.png", BlockType.DIORITE);
        tile("andesite", "andesite.png", BlockType.ANDESITE);
        tile("polished_granite", "polished_granite.png", BlockType.POLISHED_GRANITE);
        tile("polished_diorite", "polished_diorite.png", BlockType.POLISHED_DIORITE);
        tile("polished_andesite", "polished_andesite.png", BlockType.POLISHED_ANDESITE);
        tile("coarse_dirt", "coarse_dirt.png", BlockType.COARSE_DIRT);

        // Planks
tile("spruce_planks", "spruce_planks.png", BlockType.SPRUCE_PLANKS);
        tile("birch_planks", "birch_planks.png", BlockType.BIRCH_PLANKS);
        tile("jungle_planks", "jungle_planks.png", BlockType.JUNGLE_PLANKS);
        tile("acacia_planks", "acacia_planks.png", BlockType.ACACIA_PLANKS);
        tile("dark_oak_planks", "dark_oak_planks.png", BlockType.DARK_OAK_PLANKS);

        tile("spruce_log", "spruce_log.png", BlockType.SPRUCE_LOG);
        tile("spruce_log_top", "spruce_log_top.png", BlockType.SPRUCE_LOG);
        tile("jungle_log", "jungle_log.png", BlockType.JUNGLE_LOG);
        tile("jungle_log_top", "jungle_log_top.png", BlockType.JUNGLE_LOG);

        // Ores
        tile("emerald_ore", "emerald_ore.png", BlockType.EMERALD_ORE);
        tile("redstone_ore", "redstone_ore.png", BlockType.REDSTONE_ORE);
        tile("lapis_ore", "lapis_ore.png", BlockType.LAPIS_ORE);
        tile("copper_ore", "copper_ore.png", BlockType.COPPER_ORE);
        // [ECO] Meteorite ore: no PNG, purely procedural
        tileGenerated("meteorite_ore", BlockType.METEORITE_ORE);

        // Blocks
        tile("emerald_block", "emerald_block.png", BlockType.EMERALD_BLOCK);
        // [ECO] The emerald vault: a glowing procedural market block
        tileGenerated("emerald_vault", BlockType.EMERALD_VAULT);
        tile("white_wool", "white_wool.png", BlockType.WHITE_WOOL);
        // [CF] Chest and furnace: PNG pixel art generated by
        // tools/gen_chest_furnace_textures.py (PNG primary, procedural fallback)
        tile("furnace", "furnace.png", BlockType.FURNACE);
        tile("furnace_side", "furnace_side.png", BlockType.FURNACE);
        tile("furnace_top", "furnace_top.png", BlockType.FURNACE);
        tile("chest", "chest.png", BlockType.CHEST);
        tile("chest_top", "chest_top.png", BlockType.CHEST);
        // [ENCH] Enchanting table: rune sides and a glowing top
        tile("enchanting_table", "enchanting_table.png", BlockType.ENCHANTING_TABLE);
        tile("enchanting_table_top", "enchanting_table_top.png", BlockType.ENCHANTING_TABLE);
        tile("enchanting_table_item", "enchanting_table_item.png", BlockType.ENCHANTING_TABLE);
        tile("book", "book.png", BlockType.ITEM_BOOK);
        tile("lapis_lazuli", "lapis_lazuli.png", BlockType.ITEM_LAPIS);

        // Crops
        tile("wheat_stage0", "wheat_stage0.png", BlockType.WHEAT);
        tile("wheat_stage1", "wheat_stage1.png", BlockType.WHEAT);
        tile("wheat_stage2", "wheat_stage2.png", BlockType.WHEAT);
        tile("wheat_stage3", "wheat_stage3.png", BlockType.WHEAT);
        tile("wheat_stage4", "wheat_stage4.png", BlockType.WHEAT);
        tile("wheat_stage5", "wheat_stage5.png", BlockType.WHEAT);
        tile("wheat_stage6", "wheat_stage6.png", BlockType.WHEAT);
        tile("wheat_stage7", "wheat_stage7.png", BlockType.WHEAT);
        tileGenerated("carrot", BlockType.CARROT);
        tileGenerated("potato", BlockType.POTATO);
        // [TA] Carrot and potato grow through the same 8 stages as wheat;
        // TextureAgent ships stage PNGs for both
        for (int s = 0; s <= 7; s++) {
            tile("carrot_stage" + s, "carrot_stage" + s + ".png", BlockType.CARROT);
            tile("potato_stage" + s, "potato_stage" + s + ".png", BlockType.POTATO);
        }
        // [CR] Hay bale: the barn's produce, straw
        tile("hay_bale", "hay_bale.png", BlockType.HAY_BALE);
        tile("hay_bale_top", "hay_bale_top.png", BlockType.HAY_BALE);

        // Others
        tile("bookshelf", "bookshelf.png", BlockType.BOOKSHELF);
        tile("soul_sand", "soul_sand.png", BlockType.SOUL_SAND);
        tile("end_stone", "end_stone.png", BlockType.END_STONE);
        tile("purpur_block", "purpur_block.png", BlockType.PURPUR_BLOCK);
        tile("white_concrete", "white_concrete.png", BlockType.WHITE_CONCRETE);
        tile("red_concrete", "red_concrete.png", BlockType.RED_CONCRETE);
        tile("green_concrete", "green_concrete.png", BlockType.GREEN_CONCRETE);
        tile("blue_concrete", "blue_concrete.png", BlockType.BLUE_CONCRETE);
        tile("glowing_obsidian", "glowing_obsidian.png", BlockType.GLOWING_OBSIDIAN);
        tile("nether_reactor", "nether_reactor.png", BlockType.NETHER_REACTOR);
        tile("nether_bricks", "nether_bricks.png", BlockType.NETHER_BRICKS);

        // --- Items (flat sprites) ---
        tileGenerated("iron_sword", BlockType.IRON_SWORD);
        tileGenerated("stick", BlockType.ITEM_STICK);
        tileGenerated("coal", BlockType.ITEM_COAL);
        tileGenerated("iron_ingot", BlockType.ITEM_IRON_INGOT);
        tileGenerated("gold_ingot", BlockType.ITEM_GOLD_INGOT);
        tileGenerated("diamond", BlockType.ITEM_DIAMOND);
        // [ECO] Currency and the space metal
        tileGenerated("emerald", BlockType.ITEM_EMERALD);
        tileGenerated("meteorite_ingot", BlockType.ITEM_METEORITE);
        tileGenerated("leather", BlockType.ITEM_LEATHER);
        tileGenerated("string", BlockType.ITEM_STRING);
        tileGenerated("feather", BlockType.ITEM_FEATHER);
        tileGenerated("flint", BlockType.ITEM_FLINT);
        tileGenerated("flint_and_steel", BlockType.ITEM_FLINT_AND_STEEL);
        tileGenerated("gunpowder", BlockType.ITEM_GUNPOWDER);
        tileGenerated("wheat", BlockType.ITEM_WHEAT);
        tileGenerated("egg", BlockType.ITEM_EGG);
        // [ANM] Animal care items
        tileGenerated("shears", BlockType.ITEM_SHEARS);
        tileGenerated("bucket", BlockType.ITEM_BUCKET);
        tileGenerated("milk", BlockType.ITEM_MILK);
        // [GP-009] Slime ball item
        tileGenerated("slime_ball", BlockType.SLIME_BLOCK);
        // [POT] Potion bottles and HUD status-effect icons
        tileGenerated("water_bottle", BlockType.SLIME_BLOCK);
        tileGenerated("potion_healing", BlockType.SLIME_BLOCK);
        tileGenerated("potion_speed", BlockType.SLIME_BLOCK);
        tileGenerated("potion_strength", BlockType.SLIME_BLOCK);
        tileGenerated("potion_fire_resistance", BlockType.SLIME_BLOCK);
        tileGenerated("effect_regeneration", BlockType.SLIME_BLOCK);
        tileGenerated("effect_speed", BlockType.SLIME_BLOCK);
        tileGenerated("effect_strength", BlockType.SLIME_BLOCK);
        tileGenerated("effect_fire_resistance", BlockType.SLIME_BLOCK);
        // [POT] Sugar for brewing the Speed potion
        tileGenerated("sugar", BlockType.SLIME_BLOCK);

        // Tools
        tileGenerated("wooden_pickaxe", BlockType.ITEM_WOODEN_PICKAXE);
        tileGenerated("wooden_axe", BlockType.ITEM_WOODEN_AXE);
        tileGenerated("wooden_shovel", BlockType.ITEM_WOODEN_SHOVEL);
        tileGenerated("wooden_sword", BlockType.ITEM_WOODEN_SWORD);
        tileGenerated("wooden_hoe", BlockType.ITEM_WOODEN_HOE);
        tileGenerated("stone_pickaxe", BlockType.ITEM_STONE_PICKAXE);
        tileGenerated("stone_axe", BlockType.ITEM_STONE_AXE);
        tileGenerated("stone_shovel", BlockType.ITEM_STONE_SHOVEL);
        tileGenerated("stone_sword", BlockType.ITEM_STONE_SWORD);
        tileGenerated("stone_hoe", BlockType.ITEM_STONE_HOE);
        tileGenerated("iron_pickaxe", BlockType.ITEM_IRON_PICKAXE);
        tileGenerated("iron_axe", BlockType.ITEM_IRON_AXE);
        tileGenerated("iron_shovel", BlockType.ITEM_IRON_SHOVEL);
        tileGenerated("iron_sword_item", BlockType.ITEM_IRON_SWORD);
        tileGenerated("iron_hoe", BlockType.ITEM_IRON_HOE);
        tileGenerated("diamond_pickaxe", BlockType.ITEM_DIAMOND_PICKAXE);
        tileGenerated("diamond_axe", BlockType.ITEM_DIAMOND_AXE);
        tileGenerated("diamond_shovel", BlockType.ITEM_DIAMOND_SHOVEL);
        tileGenerated("diamond_sword", BlockType.ITEM_DIAMOND_SWORD);
        tileGenerated("diamond_hoe", BlockType.ITEM_DIAMOND_HOE);
        tileGenerated("golden_pickaxe", BlockType.ITEM_GOLDEN_PICKAXE);
        tileGenerated("golden_axe", BlockType.ITEM_GOLDEN_AXE);
        tileGenerated("golden_shovel", BlockType.ITEM_GOLDEN_SHOVEL);
        tileGenerated("golden_sword", BlockType.ITEM_GOLDEN_SWORD);
        tileGenerated("golden_hoe", BlockType.ITEM_GOLDEN_HOE);
        // [ECO] Meteorite tools
        tileGenerated("meteorite_pickaxe", BlockType.ITEM_METEORITE_PICKAXE);
        tileGenerated("meteorite_axe", BlockType.ITEM_METEORITE_AXE);
        tileGenerated("meteorite_shovel", BlockType.ITEM_METEORITE_SHOVEL);
        tileGenerated("meteorite_sword", BlockType.ITEM_METEORITE_SWORD);
        tileGenerated("meteorite_hoe", BlockType.ITEM_METEORITE_HOE);

        // Armor
        tileGenerated("leather_helmet", BlockType.ITEM_LEATHER_HELMET);
        tileGenerated("leather_chestplate", BlockType.ITEM_LEATHER_CHESTPLATE);
        tileGenerated("leather_leggings", BlockType.ITEM_LEATHER_LEGGINGS);
        tileGenerated("leather_boots", BlockType.ITEM_LEATHER_BOOTS);
        tileGenerated("iron_helmet", BlockType.ITEM_IRON_HELMET);
        tileGenerated("iron_chestplate", BlockType.ITEM_IRON_CHESTPLATE);
        tileGenerated("iron_leggings", BlockType.ITEM_IRON_LEGGINGS);
        tileGenerated("iron_boots", BlockType.ITEM_IRON_BOOTS);
        tileGenerated("diamond_helmet", BlockType.ITEM_DIAMOND_HELMET);
        tileGenerated("diamond_chestplate", BlockType.ITEM_DIAMOND_CHESTPLATE);
        tileGenerated("diamond_leggings", BlockType.ITEM_DIAMOND_LEGGINGS);
        tileGenerated("diamond_boots", BlockType.ITEM_DIAMOND_BOOTS);
        // [ECO] Meteorite armour
        tileGenerated("meteorite_helmet", BlockType.ITEM_METEORITE_HELMET);
        tileGenerated("meteorite_chestplate", BlockType.ITEM_METEORITE_CHESTPLATE);
        tileGenerated("meteorite_leggings", BlockType.ITEM_METEORITE_LEGGINGS);
        tileGenerated("meteorite_boots", BlockType.ITEM_METEORITE_BOOTS);

        // Food
        tileGenerated("bread", BlockType.ITEM_BREAD);
        tileGenerated("raw_pork", BlockType.ITEM_RAW_PORK);
        tileGenerated("cooked_pork", BlockType.ITEM_COOKED_PORK);
        tileGenerated("raw_beef", BlockType.ITEM_RAW_BEEF);
        tileGenerated("cooked_beef", BlockType.ITEM_COOKED_BEEF);
        tileGenerated("raw_chicken", BlockType.ITEM_RAW_CHICKEN);
        tileGenerated("cooked_chicken", BlockType.ITEM_COOKED_CHICKEN);
        tileGenerated("raw_mutton", BlockType.ITEM_RAW_MUTTON);
        tileGenerated("cooked_mutton", BlockType.ITEM_COOKED_MUTTON);
        tileGenerated("apple", BlockType.ITEM_APPLE);

        // Misc
        tileGenerated("bow", BlockType.ITEM_BOW);
        tileGenerated("arrow", BlockType.ITEM_ARROW);
        tile("torch", "torch.png", BlockType.ITEM_TORCH);
        tileGenerated("rotten_flesh", BlockType.ITEM_ROTTEN_FLESH);
        tileGenerated("bone", BlockType.ITEM_BONE);
        tileGenerated("spider_eye", BlockType.ITEM_SPIDER_EYE);

        // [GP-013] Mining cracks: ten damage stages drawn over the block
        // being broken. Black lines on a fully transparent background, so
        // the shader's alpha test keeps only the cracks themselves.
        for (int i = 0; i < 10; i++) {
            tileCrack("crack_stage" + i, i);
        }

        // [GP-020] Oak door, closed and open states.
        // РўРµРєСЃС‚СѓСЂС‹ РєР°Рє РІ MC: РІРµСЂС…РЅСЏСЏ Рё РЅРёР¶РЅСЏСЏ РїРѕР»РѕРІРёРЅР° РґРІРµСЂРё, СЃРіРµРЅРµСЂРёСЂРѕРІР°РЅС‹
        // tools/gen_door_textures.py (PNG primary, procedural fallback).
        tileGenerated("oak_door", BlockType.OAK_DOOR);
        tile("oak_door_bottom", "oak_door_bottom.png", BlockType.OAK_DOOR_OPEN);
        tile("oak_door_top", "oak_door_top.png", BlockType.OAK_DOOR_OPEN);

// [SD] Sliding door: 2-cell door (bottom + top). The 16x32 sheet is
        //      split in half at load time.
        tile("sliding_door_bottom", "sliding_door_bottom.png", BlockType.SLIDING_DOOR);
        tile("sliding_door_top", "sliding_door_top.png", BlockType.SLIDING_DOOR);

        // [BED] Bed furniture tiles: PNG primary, procedural fallback.
        tile("bed_frame", "bed_frame.png", BlockType.BED);
        tile("bed_mattress_side", "bed_mattress_side.png", BlockType.BED);
        tile("bed_mattress_top", "bed_mattress_top.png", BlockType.BED);
        tile("bed_pillow", "bed_pillow.png", BlockType.BED_HEAD);
        tile("bed_headboard", "bed_headboard.png", BlockType.BED_HEAD);
        tile("bed_footboard", "bed_footboard.png", BlockType.BED);

        // [GP-PLANKS] Resin and wax: substitute crafting materials
        tileGenerated("resin", BlockType.ITEM_GUNPOWDER);
        tileGenerated("wax", BlockType.ITEM_GUNPOWDER);

        // [GP-073] Fire: transparent flame sprite drawn as a cross-quad
        tile("fire", "fire.png", BlockType.FIRE);

        // [BASE] Base blocks: lanterns (glowing), crate, campfire and
        // biome brickwork. PNG primary (tools/gen_textures.py), procedural
        // fallback in TileGenerator.
        tile("lantern", "lantern.png", BlockType.LANTERN);
        tile("ice_lantern", "ice_lantern.png", BlockType.ICE_LANTERN);
        tile("crate", "crate.png", BlockType.CRATE);
        tile("campfire", "campfire.png", BlockType.CAMPFIRE);
        tile("snow_bricks", "snow_bricks.png", BlockType.SNOW_BRICKS);
        tile("sandstone_bricks", "sandstone_bricks.png", BlockType.SANDSTONE_BRICKS);
        // [GP-009] Slime block: procedural bouncy green gel tile
        tileGenerated("slime_block", BlockType.SLIME_BLOCK);

        // [SPACE] Rocket tower parts: procedural metal, hull and window tiles
        tileGenerated("rocket_launch_pad", BlockType.ROCKET_LAUNCH_PAD);
        tileGenerated("rocket_engine", BlockType.ROCKET_ENGINE);
        tileGenerated("rocket_fuel", BlockType.ROCKET_FUEL);
        tileGenerated("rocket_body", BlockType.ROCKET_BODY);
        tileGenerated("rocket_window", BlockType.ROCKET_WINDOW);
        tileGenerated("rocket_cone", BlockType.ROCKET_CONE);
    }

    /**
     * Register one procedurally generated crack tile. Each stage is denser
     * than the last, giving a smooth transition while a block crumbles.
     */
    private int tileCrack(String name, int stage) {
        int slot = nextSlot++;
        int[] pixels = generateCrackTile(stage);
        generated++;

        blit(slot, pixels);
        layerPixels.add(pixels);
        slotByName.put(name, slot);
        return slot;
    }

    /**
     * Crack lines wander from one edge of the tile towards the far side,
     * picking up a bit of perpendicular jitter so they read as fractures
     * rather than straight scratches. The seed is fixed per stage so the
     * tiles are stable across atlas rebuilds.
     */
    private int[] generateCrackTile(int stage) {
        int[] px = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        java.util.Random rnd = new java.util.Random(0x1A2B3C4DL + stage * 7919L);

        // 10 stages: cracks scale from 1 to 6, steps from 8 to 40
        int cracks = 1 + stage * 5 / 9;       // 1..6 fractures
        int steps = 8 + stage * 32 / 9;       // 8..40 path length
        int thickness = 1 + stage / 5;        // thicker cracks at higher stages

        for (int c = 0; c < cracks; c++) {
            int side = rnd.nextInt(4);
            int x, y, tx, ty;
            switch (side) {
                case 0: x = 0; y = rnd.nextInt(16); tx = 15; ty = y; break;
                case 1: x = 15; y = rnd.nextInt(16); tx = 0; ty = y; break;
                case 2: x = rnd.nextInt(16); y = 0; tx = x; ty = 15; break;
                default: x = rnd.nextInt(16); y = 15; tx = x; ty = 0; break;
            }

            for (int i = 0; i < steps; i++) {
                markCrack(px, x, y, thickness);

                // Branch at higher stages
                if (stage >= 4 && rnd.nextInt(100) < 15) {
                    int bx = x + (rnd.nextBoolean() ? 1 : -1);
                    int by = y + (rnd.nextBoolean() ? 1 : -1);
                    if (bx >= 0 && bx < 16 && by >= 0 && by < 16) {
                        markCrack(px, bx, by, Math.max(1, thickness - 1));
                    }
                }

                // Head towards the far edge with occasional jitter
                int wantX = Integer.compare(tx, x);
                int wantY = Integer.compare(ty, y);
                if (rnd.nextInt(100) < 35) {
                    if (rnd.nextBoolean()) wantX = 0;
                    else wantY = 0;
                    if (rnd.nextInt(100) < 40) {
                        if (rnd.nextBoolean()) wantX = rnd.nextBoolean() ? 1 : -1;
                        else wantY = rnd.nextBoolean() ? 1 : -1;
                    }
                }
                x = Math.max(0, Math.min(15, x + wantX));
                y = Math.max(0, Math.min(15, y + wantY));

                if (x == tx && y == ty) break;
            }
        }

        // Deep chips at high stages
        if (stage >= 6) {
            for (int i = 0; i < stage - 5; i++) {
                int cx = rnd.nextInt(16);
                int cy = rnd.nextInt(16);
                markCrack(px, cx, cy, 2);
            }
        }

        // Dark center at final stages
        if (stage >= 8) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = 8 + dx, ny = 8 + dy;
                    if (nx >= 0 && nx < 16 && ny >= 0 && ny < 16) {
                        px[ny * TEXTURE_SIZE + nx] = 0xFF050505;
                    }
                }
            }
        }

        return px;
    }

    /** 3px-wide dark fracture stroke centred on (x, y). */
    private void markCrack(int[] px, int x, int y) { markCrack(px, x, y, 1); }
    private void markCrack(int[] px, int x, int y, int thickness) {
        for (int dy = -thickness; dy <= thickness; dy++) {
            for (int dx = -thickness; dx <= thickness; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || nx >= TEXTURE_SIZE || ny < 0 || ny >= TEXTURE_SIZE) continue;
                int shade = (dx == 0 && dy == 0) ? 0x10 : 0x28;
                px[ny * TEXTURE_SIZE + nx] = 0xFF000000 | (shade << 16) | (shade << 8) | shade;
            }
        }
    }

    /**
     * Register several variants of one tile under "name", "name#1", ...
     *
     * A resource pack overrides variant 0 only; the remaining variants are
     * generated, so a pack that supplies a single grass texture still works
     * (all variants collapse to it visually).
     */
    private void variants(String name, BlockType type, int count) {
        int first = nextSlot;
        for (int i = 0; i < count; i++) {
            String key = (i == 0) ? name : name + "#" + i;

            int[] pixels = loadTilePng(key + ".png");
            if (pixels != null) {
                pixels = keepsAlpha(type) ? pixels : forceOpaque(pixels, type);
                loadedFromPng++;
            } else {
                pixels = TileGenerator.generate(name, type, i);
                generated++;
            }

            int slot = nextSlot++;
            blit(slot, pixels);
            layerPixels.add(pixels);
            slotByName.put(key, slot);
        }
        variantCount.put(name, count);
        variantFirstSlot.put(name, first);
    }

    /** Variant set that never consults a resource pack. */
    private void variantsGenerated(String name, BlockType type, int count) {
        int first = nextSlot;
        for (int i = 0; i < count; i++) {
            String key = (i == 0) ? name : name + "#" + i;
            int[] pixels = TileGenerator.generate(name, type, i);
            generated++;

            int slot = nextSlot++;
            blit(slot, pixels);
            layerPixels.add(pixels);
            slotByName.put(key, slot);
        }
        variantCount.put(name, count);
        variantFirstSlot.put(name, first);
    }

    /** Always procedural, bypassing resource packs and the opacity fixup. */
    private int tileGenerated(String name, BlockType type) {
        Integer existing = slotByName.get(name);
        if (existing != null) return existing;

        int[] pixels = TileGenerator.generate(name, type, 0);
        generated++;

        int slot = nextSlot++;
        blit(slot, pixels);
        layerPixels.add(pixels);
        slotByName.put(name, slot);
        return slot;
    }

    private int tile(String name, String pngFile, BlockType fallback) {
        return tile(name, pngFile, fallback, null);
    }

    /**
     * Register one atlas tile.
     *
     * @param baseUnder when non-null, the PNG is treated as an OVERLAY and is
     *                  composited over a procedural tile of this type. Several
     *                  textures in the CC0 pack (grass_side, the ore tiles) are
     *                  overlays with a mostly empty alpha channel - used raw
     *                  they would be discarded by the shader's alpha test and
     *                  punch see-through holes in solid blocks.
     */
    private int tile(String name, String pngFile, BlockType fallback, BlockType baseUnder) {
        Integer existing = slotByName.get(name);
        if (existing != null) return existing;

        int slot = nextSlot++;
        int[] pixels = loadTilePng(pngFile);

        if (pixels != null) {
            if (baseUnder != null) {
                pixels = compositeOver(generateTile(baseUnder), pixels);
            } else if (!keepsAlpha(fallback)) {
                pixels = forceOpaque(pixels, fallback);
            }
            loadedFromPng++;
        } else {
            pixels = generateTile(fallback);
            generated++;
        }

        blit(slot, pixels);
        layerPixels.add(pixels);
        slotByName.put(name, slot);
        return slot;
    }

    /** Blocks whose texture is legitimately see-through. */
    private boolean keepsAlpha(BlockType type) {
        return type == BlockType.GLASS
            || type == BlockType.WATER
            || type == BlockType.ICE
            || type == BlockType.OAK_LEAVES
            || type == BlockType.SPRUCE_LEAVES
            || type == BlockType.BIRCH_LEAVES
            || type == BlockType.JUNGLE_LEAVES
            // Cross-quad plants are mostly empty space by design
            || type == BlockType.GRASS_PLANT
            || type == BlockType.DANDELION
            || type == BlockType.POPPY
            || type == BlockType.DEAD_BUSH
            || type == BlockType.WHEAT
            // [WG] World-gen expansion plants keep their cut-outs
            || type == BlockType.BAMBOO
            || type == BlockType.CORAL
            || type == BlockType.SEAGRASS
            || type == BlockType.RAILS
            || type == BlockType.END_PORTAL
            // [TA] TextureAgent set: new plants and sprites keep their alpha
            || type == BlockType.LILY_PAD
            || type == BlockType.SUNFLOWER
            || type == BlockType.LAVENDER
            || type == BlockType.VINE
            || type == BlockType.AUTUMN_LEAVES
            || type == BlockType.CHERRY_LEAVES
            || type == BlockType.CRYSTAL
            || type == BlockType.FIRE
            // All item sprites have transparent backgrounds
            || type.name.startsWith("item_") || type.name.equals("iron_sword");
    }

    /**
     * Alpha-blend an overlay tile onto an opaque base tile.
     */
    private int[] compositeOver(int[] base, int[] overlay) {
        int[] out = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        for (int i = 0; i < out.length; i++) {
            int ov = overlay[i];
            int a = (ov >>> 24) & 0xFF;

            if (a == 255) { out[i] = ov; continue; }
            if (a == 0) { out[i] = base[i]; continue; }

            int bs = base[i];
            int r = (((ov >> 16) & 0xFF) * a + ((bs >> 16) & 0xFF) * (255 - a)) / 255;
            int g = (((ov >> 8) & 0xFF) * a + ((bs >> 8) & 0xFF) * (255 - a)) / 255;
            int b = ((ov & 0xFF) * a + (bs & 0xFF) * (255 - a)) / 255;
            out[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return out;
    }

    /**
     * Safety net for blocks that must be fully solid: if a supposedly opaque
     * PNG turns out to have holes, fill them from the procedural tile rather
     * than letting the shader discard those texels.
     */
    private int[] forceOpaque(int[] pixels, BlockType fallback) {
        int holes = 0;
        for (int p : pixels) {
            if (((p >>> 24) & 0xFF) < 13) holes++;
        }
        if (holes == 0) return pixels;

        System.out.println("  note: opaque tile had " + holes
            + " transparent texels, filling from procedural base");
        return compositeOver(generateTile(fallback), pixels);
    }

    private void blit(int slot, int[] pixels) {
        int startX = (slot % TEXTURES_PER_ROW) * TEXTURE_SIZE;
        int startY = (slot / TEXTURES_PER_ROW) * TEXTURE_SIZE;
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                atlasPixels[(startY + y) * ATLAS_SIZE + (startX + x)] = pixels[y * TEXTURE_SIZE + x];
            }
        }
    }

    /**
     * Decode a PNG from the classpath into a 16x16 ARGB tile.
     * Returns null when the file is not present.
     */
    /**
     * Read one tile, delegating the search order to the resource pack:
     * selected pack first, bundled CC0 textures second, null if neither has
     * it (the caller then generates the tile procedurally).
     */
    private int[] loadTilePng(String fileName) {
        String name = fileName.endsWith(".png")
            ? fileName.substring(0, fileName.length() - 4)
            : fileName;
        return resourcePack.loadTile(name, TEXTURE_SIZE);
    }

    // ------------------------------------------------------------------
    // Block -> face slot mapping
    // ------------------------------------------------------------------

    private void mapBlockFaces() {
        faces(BlockType.GRASS_BLOCK, "grass_top", "grass_side", "dirt");
        faces(BlockType.DIRT, "dirt");
        faces(BlockType.COARSE_DIRT, "coarse_dirt");
        faces(BlockType.PODZOL, "podzol", "dirt", "dirt");
        faces(BlockType.MYCELIUM, "mycelium_top", "dirt", "dirt");
        faces(BlockType.STONE, "stone");
        faces(BlockType.GRANITE, "granite");
        faces(BlockType.POLISHED_GRANITE, "polished_granite");
        faces(BlockType.POLISHED_DIORITE, "polished_diorite");
        faces(BlockType.POLISHED_ANDESITE, "polished_andesite");
        faces(BlockType.ANDESITE, "andesite");
        faces(BlockType.DIORITE, "diorite");
        faces(BlockType.COBBLESTONE, "cobblestone");
        faces(BlockType.MOSSY_COBBLESTONE, "mossy_cobblestone");
        faces(BlockType.STONE_BRICKS, "stone_bricks");
        faces(BlockType.BRICK, "brick");

        faces(BlockType.OAK_PLANKS, "oak_planks");
        faces(BlockType.SPRUCE_PLANKS, "spruce_planks");
        faces(BlockType.BIRCH_PLANKS, "birch_planks");
        faces(BlockType.JUNGLE_PLANKS, "jungle_planks");
        faces(BlockType.OAK_LOG, "oak_log_top", "oak_log", "oak_log_top");
        faces(BlockType.SPRUCE_LOG, "spruce_log_top", "spruce_log", "spruce_log_top");
        faces(BlockType.BIRCH_LOG, "birch_log_top", "birch_log_side", "birch_log_top");
        faces(BlockType.JUNGLE_LOG, "jungle_log_top", "jungle_log", "jungle_log_top");

        faces(BlockType.OAK_LEAVES, "oak_leaves");
        faces(BlockType.SPRUCE_LEAVES, "spruce_leaves");
        faces(BlockType.BIRCH_LEAVES, "birch_leaves");
        faces(BlockType.JUNGLE_LEAVES, "jungle_leaves");
        faces(BlockType.AUTUMN_LEAVES, "autumn_leaves");
        faces(BlockType.CHERRY_LEAVES, "cherry_leaves");

        faces(BlockType.SAND, "sand");
        faces(BlockType.GRAVEL, "gravel");
        faces(BlockType.CLAY, "clay");
        faces(BlockType.TERRACOTTA, "terracotta");
        faces(BlockType.RED_SAND, "red_sand");
        faces(BlockType.SNOW, "snow");
        faces(BlockType.ICE, "ice");
        faces(BlockType.BLUE_ICE, "blue_ice");
        faces(BlockType.PACKED_ICE, "packed_ice");
        faces(BlockType.DEEPSLATE, "deepslate");
        faces(BlockType.BASALT, "basalt");
        faces(BlockType.SALT, "salt");
        faces(BlockType.ASH, "ash");

        faces(BlockType.COAL_ORE, "ore_coal");
        faces(BlockType.IRON_ORE, "ore_iron");
        faces(BlockType.GOLD_ORE, "ore_gold");
        faces(BlockType.DIAMOND_ORE, "ore_diamond");
        faces(BlockType.EMERALD_ORE, "emerald_ore");
        faces(BlockType.REDSTONE_ORE, "redstone_ore");
        faces(BlockType.LAPIS_ORE, "lapis_ore");
        faces(BlockType.COPPER_ORE, "copper_ore");
        faces(BlockType.METEORITE_ORE, "meteorite_ore");
        faces(BlockType.COAL_BLOCK, "coal_block");
        faces(BlockType.IRON_BLOCK, "iron_block");
        faces(BlockType.GOLD_BLOCK, "gold_block");
        faces(BlockType.DIAMOND_BLOCK, "diamond_block");
        faces(BlockType.EMERALD_BLOCK, "emerald_block");
        faces(BlockType.EMERALD_VAULT, "emerald_vault");

        faces(BlockType.WATER, "water");
        faces(BlockType.LAVA, "lava");

        // Wheat crops with growth stages
        faces(BlockType.WHEAT, "wheat_stage0");
        // [CR] Hay bale: straw sides, woven top
        faces(BlockType.HAY_BALE, "hay_bale_top", "hay_bale", "hay_bale");
        faces(BlockType.GLASS, "glass");
        // [TRAN] The pane shares the glass tile; it never renders as a cube
        faces(BlockType.GLASS_PANE, "glass");
        faces(BlockType.BEDROCK, "bedrock");
        faces(BlockType.OBSIDIAN, "obsidian");
        faces(BlockType.TNT, "tnt");
        faces(BlockType.CRAFTING_TABLE, "crafting_table", "crafting_table", "oak_planks");
        // [CF] Furnace: stone sides, stone top, mouth on one horizontal face
        faces(BlockType.FURNACE, "furnace_top", "furnace_side", "furnace_top");
        // [CF] Chest: banded top, latched sides
        faces(BlockType.CHEST, "chest_top", "chest", "chest_top");
        // [ENCH] Enchanting table: rune sides, glowing top, obsidian base
        faces(BlockType.ENCHANTING_TABLE, "enchanting_table_top", "enchanting_table", "obsidian");
        faces(BlockType.NETHERRACK, "netherrack");

        faces(BlockType.GRASS_PLANT, "grass_plant");
        faces(BlockType.DANDELION, "dandelion");
        faces(BlockType.POPPY, "poppy");
        faces(BlockType.DEAD_BUSH, "dead_bush");
        faces(BlockType.CACTUS, "cactus_top", "cactus_side", "cactus_top");
        faces(BlockType.GLOWSTONE, "glowstone");
        faces(BlockType.NETHER_BRICKS, "nether_bricks");
        faces(BlockType.GLOWING_OBSIDIAN, "glowing_obsidian");
        faces(BlockType.NETHER_REACTOR, "nether_reactor");
        faces(BlockType.CRYSTAL, "crystal");
        faces(BlockType.WHITE_WOOL, "white_wool");
        faces(BlockType.WHITE_CONCRETE, "white_concrete");
        faces(BlockType.RED_CONCRETE, "red_concrete");
        faces(BlockType.GREEN_CONCRETE, "green_concrete");
        faces(BlockType.BLUE_CONCRETE, "blue_concrete");
        faces(BlockType.CHARRED_LOG, "charred_log_top", "charred_log", "charred_log_top");
        faces(BlockType.PETRIFIED_LOG, "petrified_log_top", "petrified_log", "petrified_log_top");

        // [GP-020] Doors. Open door (and closed, rendered as a panel) takes
        // the bottom tile as its fallback; the mesher picks top/bottom by
        // whether the block below is another door.
        faces(BlockType.OAK_DOOR, "oak_door");
        faces(BlockType.OAK_DOOR_OPEN, "oak_door_bottom");

        // [SD] Sliding door is never baked into a chunk mesh; the dynamic
        // renderer pass picks the tile by half. Bottom tile is the fallback.
        faces(BlockType.SLIDING_DOOR, "sliding_door_bottom");

        // [BED] Bed halves render custom furniture quads; the face mapping
        // only serves inventory icons and any accidental cube fallback.
        faces(BlockType.BED, "bed_mattress_top", "bed_mattress_side", "bed_frame");
        faces(BlockType.BED_HEAD, "bed_pillow", "bed_mattress_side", "bed_frame");

        // [GP-002] Slabs reuse their parent materials' tiles
        faces(BlockType.OAK_SLAB, "oak_planks");
        faces(BlockType.STONE_SLAB, "stone");

        // [GP-PLANKS] Improved planks: 32 blocks + 32 slabs, one texture each
        for (com.voxelgame.world.plank.PlankVariant v
                : com.voxelgame.world.plank.PlankBlockRegistry.REGISTERED) {
            BlockType b = BlockType.fromName(v.getBlockRegistryName());
            if (b != null) faces(b, v.getTextureBaseName());
        }

        // Items
        faces(BlockType.IRON_SWORD, "iron_sword");
        faces(BlockType.ITEM_STICK, "stick");
        faces(BlockType.ITEM_COAL, "coal");
        faces(BlockType.ITEM_IRON_INGOT, "iron_ingot");
        faces(BlockType.ITEM_GOLD_INGOT, "gold_ingot");
        faces(BlockType.ITEM_DIAMOND, "diamond");
        faces(BlockType.ITEM_LEATHER, "leather");
        faces(BlockType.ITEM_STRING, "string");
        faces(BlockType.ITEM_FEATHER, "feather");
        faces(BlockType.ITEM_FLINT, "flint");
        faces(BlockType.ITEM_FLINT_AND_STEEL, "flint_and_steel");
        faces(BlockType.ITEM_GUNPOWDER, "gunpowder");
        faces(BlockType.ITEM_WHEAT, "wheat");
        faces(BlockType.ITEM_EGG, "egg");
        // [ANM] Animal care items
        faces(BlockType.ITEM_SHEARS, "shears");
        faces(BlockType.ITEM_BUCKET, "bucket");
        faces(BlockType.ITEM_MILK, "milk");
        faces(BlockType.ITEM_WOODEN_PICKAXE, "wooden_pickaxe");
        faces(BlockType.ITEM_WOODEN_AXE, "wooden_axe");
        faces(BlockType.ITEM_WOODEN_SHOVEL, "wooden_shovel");
        faces(BlockType.ITEM_WOODEN_SWORD, "wooden_sword");
        faces(BlockType.ITEM_WOODEN_HOE, "wooden_hoe");
        faces(BlockType.ITEM_STONE_PICKAXE, "stone_pickaxe");
        faces(BlockType.ITEM_STONE_AXE, "stone_axe");
        faces(BlockType.ITEM_STONE_SHOVEL, "stone_shovel");
        faces(BlockType.ITEM_STONE_SWORD, "stone_sword");
        faces(BlockType.ITEM_STONE_HOE, "stone_hoe");
        faces(BlockType.ITEM_IRON_PICKAXE, "iron_pickaxe");
        faces(BlockType.ITEM_IRON_AXE, "iron_axe");
        faces(BlockType.ITEM_IRON_SHOVEL, "iron_shovel");
        faces(BlockType.ITEM_IRON_SWORD, "iron_sword_item");
        faces(BlockType.ITEM_IRON_HOE, "iron_hoe");
        faces(BlockType.ITEM_DIAMOND_PICKAXE, "diamond_pickaxe");
        faces(BlockType.ITEM_DIAMOND_AXE, "diamond_axe");
        faces(BlockType.ITEM_DIAMOND_SHOVEL, "diamond_shovel");
        faces(BlockType.ITEM_DIAMOND_SWORD, "diamond_sword");
        faces(BlockType.ITEM_DIAMOND_HOE, "diamond_hoe");
        faces(BlockType.ITEM_GOLDEN_PICKAXE, "golden_pickaxe");
        faces(BlockType.ITEM_GOLDEN_AXE, "golden_axe");
        faces(BlockType.ITEM_GOLDEN_SHOVEL, "golden_shovel");
        faces(BlockType.ITEM_GOLDEN_SWORD, "golden_sword");
        faces(BlockType.ITEM_GOLDEN_HOE, "golden_hoe");
        faces(BlockType.ITEM_LEATHER_HELMET, "leather_helmet");
        faces(BlockType.ITEM_LEATHER_CHESTPLATE, "leather_chestplate");
        faces(BlockType.ITEM_LEATHER_LEGGINGS, "leather_leggings");
        faces(BlockType.ITEM_LEATHER_BOOTS, "leather_boots");
        faces(BlockType.ITEM_IRON_HELMET, "iron_helmet");
        faces(BlockType.ITEM_IRON_CHESTPLATE, "iron_chestplate");
        faces(BlockType.ITEM_IRON_LEGGINGS, "iron_leggings");
        faces(BlockType.ITEM_IRON_BOOTS, "iron_boots");
        faces(BlockType.ITEM_DIAMOND_HELMET, "diamond_helmet");
        faces(BlockType.ITEM_DIAMOND_CHESTPLATE, "diamond_chestplate");
        faces(BlockType.ITEM_DIAMOND_LEGGINGS, "diamond_leggings");
        faces(BlockType.ITEM_DIAMOND_BOOTS, "diamond_boots");
        faces(BlockType.ITEM_BREAD, "bread");
        faces(BlockType.ITEM_RAW_PORK, "raw_pork");
        faces(BlockType.ITEM_COOKED_PORK, "cooked_pork");
        faces(BlockType.ITEM_RAW_BEEF, "raw_beef");
        faces(BlockType.ITEM_COOKED_BEEF, "cooked_beef");
        faces(BlockType.ITEM_RAW_CHICKEN, "raw_chicken");
        faces(BlockType.ITEM_COOKED_CHICKEN, "cooked_chicken");
        faces(BlockType.ITEM_RAW_MUTTON, "raw_mutton");
        faces(BlockType.ITEM_COOKED_MUTTON, "cooked_mutton");
        faces(BlockType.ITEM_APPLE, "apple");
        faces(BlockType.ITEM_BOW, "bow");
        faces(BlockType.ITEM_ARROW, "arrow");
        faces(BlockType.ITEM_TORCH, "torch");
        faces(BlockType.ITEM_ROTTEN_FLESH, "rotten_flesh");
        faces(BlockType.ITEM_BONE, "bone");
        faces(BlockType.ITEM_SPIDER_EYE, "spider_eye");
        // [ECO] Emerald, meteorite ingot and the meteorite tool/armour set
        faces(BlockType.ITEM_EMERALD, "emerald");
        faces(BlockType.ITEM_METEORITE, "meteorite_ingot");
        faces(BlockType.ITEM_METEORITE_PICKAXE, "meteorite_pickaxe");
        faces(BlockType.ITEM_METEORITE_AXE, "meteorite_axe");
        faces(BlockType.ITEM_METEORITE_SHOVEL, "meteorite_shovel");
        faces(BlockType.ITEM_METEORITE_SWORD, "meteorite_sword");
        faces(BlockType.ITEM_METEORITE_HOE, "meteorite_hoe");
        faces(BlockType.ITEM_METEORITE_HELMET, "meteorite_helmet");
        faces(BlockType.ITEM_METEORITE_CHESTPLATE, "meteorite_chestplate");
        faces(BlockType.ITEM_METEORITE_LEGGINGS, "meteorite_leggings");
        faces(BlockType.ITEM_METEORITE_BOOTS, "meteorite_boots");

        // [GP-067] A placed torch reuses the torch sprite on every face
        faces(BlockType.TORCH_PLACEHOLDER, "torch");
        // [GP-073] Fire is a cross-quad sprite, one slot for all faces
        faces(BlockType.FIRE, "fire");

        // [WG] World-gen expansion faces
        faces(BlockType.BAMBOO, "bamboo");
        faces(BlockType.CORAL, "coral");
        faces(BlockType.SEAGRASS, "seagrass");
        faces(BlockType.MOB_SPAWNER, "mob_spawner");
        faces(BlockType.RAILS, "rails");
        faces(BlockType.CHISELED_SANDSTONE, "chiseled_sandstone");
        faces(BlockType.END_PORTAL_FRAME, "end_portal_frame");
        faces(BlockType.END_PORTAL, "end_portal");
        // [TA] TextureAgent plants
        faces(BlockType.LILY_PAD, "lily_pad");
        faces(BlockType.SUNFLOWER, "sunflower");
        faces(BlockType.LAVENDER, "lavender");
        faces(BlockType.VINE, "vine");

        // [BASE] Base blocks: lanterns glow with a glassy tile, the crate
        // is banded wood, the campfire is a crossed-quad sprite, and the
        // brickwork is a cold/desert palette of stone bricks.
        faces(BlockType.LANTERN, "lantern");
        faces(BlockType.ICE_LANTERN, "ice_lantern");
        faces(BlockType.CRATE, "crate");
        faces(BlockType.CAMPFIRE, "campfire");
        faces(BlockType.SNOW_BRICKS, "snow_bricks");
        faces(BlockType.SANDSTONE_BRICKS, "sandstone_bricks");
        // [GP-009] Slime block: same gel tile on every face
        faces(BlockType.SLIME_BLOCK, "slime_block");
        // [SPACE] Rocket parts: one tile per block
        faces(BlockType.ROCKET_LAUNCH_PAD, "rocket_launch_pad");
        faces(BlockType.ROCKET_ENGINE, "rocket_engine");
        faces(BlockType.ROCKET_FUEL, "rocket_fuel");
        faces(BlockType.ROCKET_BODY, "rocket_body");
        faces(BlockType.ROCKET_WINDOW, "rocket_window");
        faces(BlockType.ROCKET_CONE, "rocket_cone");
    }

    private void faces(BlockType block, String all) {
        faces(block, all, all, all);
    }

    private void faces(BlockType block, String top, String side, String bottom) {
        int id = block.id;
        faceSlots.put(id, new int[]{
            slotByName.getOrDefault(top, 0),
            slotByName.getOrDefault(side, 0),
            slotByName.getOrDefault(bottom, 0)
        });
    }

    // ------------------------------------------------------------------
    // Public lookup
    // ------------------------------------------------------------------

    /**
     * Blocks whose texture is a growth stage chosen per block position.
     * The engine stores no per-block data, so the stage is derived from a
     * deterministic position hash: fields render as a natural mix of young
     * and mature plants, and the choice is stable across chunk rebuilds.
     */
    private static final java.util.Map<Integer, String[]> STAGE_TILES = new java.util.HashMap<>();
    static {
        STAGE_TILES.put(BlockType.WHEAT.id, new String[]{
            "wheat_stage0", "wheat_stage1", "wheat_stage2", "wheat_stage3",
            "wheat_stage4", "wheat_stage5", "wheat_stage6", "wheat_stage7"
        });
        // [CR] Carrot and potato grow through the same stage tile set
        STAGE_TILES.put(BlockType.CARROT.id, new String[]{
            "carrot_stage0", "carrot_stage1", "carrot_stage2", "carrot_stage3",
            "carrot_stage4", "carrot_stage5", "carrot_stage6", "carrot_stage7"
        });
        STAGE_TILES.put(BlockType.POTATO.id, new String[]{
            "potato_stage0", "potato_stage1", "potato_stage2", "potato_stage3",
            "potato_stage4", "potato_stage5", "potato_stage6", "potato_stage7"
        });
    }

    /** Growth stage (0..n-1) of a stage-tile block at a world position. */
    public static int stageOf(int blockId, int wx, int wy, int wz) {
        String[] stages = STAGE_TILES.get(blockId);
        if (stages == null) return 0;
        return positionHash(wx, wy, wz) % stages.length;
    }

    /**
     * [CR] Chunk-aware growth stage: a real stored stage (planted, grown or
     * harvested by the farmer) wins over the position-hash default, so the
     * mesh matches the crop's actual state.
     */
    public static int stageOf(int blockId, int wx, int wy, int wz, Chunk chunk) {
        String[] stages = STAGE_TILES.get(blockId);
        if (stages == null) return 0;
        if (chunk != null) {
            int real = chunk.getCropStage(wx & 15, wy, wz & 15);
            if (real >= 0) return Math.min(real, stages.length - 1);
        }
        return positionHash(wx, wy, wz) % stages.length;
    }

    public TextureCoords getCoords(int blockId) {
        return getCoords(blockId, -1);
    }

    /** UV for a specific face. face: 2 = top, 3 = bottom, anything else = side. */
    public TextureCoords getCoords(int blockId, int face) {
        return new TextureCoords(getSlot(blockId, face));
    }

    /**
     * [CF] UV for an inventory icon: the most recognizable face when one
     * exists (chest side with the latch, furnace front with the mouth),
     * otherwise the top face.
     */
    public TextureCoords getIconCoords(int blockId) {
        int id = blockId;
        if (id == BlockType.CHEST.id) {
            return new TextureCoords(slotByName.getOrDefault("chest", getSlot(blockId, FACE_TOP)));
        }
        if (id == BlockType.FURNACE.id) {
            return new TextureCoords(slotByName.getOrDefault("furnace", getSlot(blockId, FACE_TOP)));
        }
        return getCoords(blockId, FACE_TOP);
    }

    public int getSlot(int blockId) {
        return getSlot(blockId, -1);
    }

    public int getSlot(int blockId, int face) {
        int id = blockId;
        int[] fs = faceSlots.get(id);
        if (fs == null) {
            return slotByName.getOrDefault("stone", 0);
        }
        if (face == FACE_TOP) return fs[0];
        if (face == FACE_BOTTOM) return fs[2];
        return fs[1];
    }

    /**
     * Slot for a face, picking a texture variant from the block position.
     *
     * Large expanses of one material are the most obvious tiling artefact,
     * so grass/dirt/stone/sand each ship several variants and every block
     * deterministically takes one. Neighbours differ, the pattern does not
     * repeat, and the choice is stable across chunk rebuilds.
     */
    public int getSlot(int blockId, int face, int wx, int wy, int wz) {
        return getSlot(blockId, face, wx, wy, wz, null);
    }

    /**
     * [CR] Chunk-aware variant of {@link #getSlot(byte, int, int, int, int)}:
     * staged crops resolve their real growth stage from the chunk's cropMeta
     * (falling back to the position hash for untouched world-gen crops).
     */
    public int getSlot(int blockId, int face, int wx, int wy, int wz, Chunk chunk) {
        int slot = getSlot(blockId, face);

        String name = nameBySlot.get(slot);
        if (name == null) return slot;

        // [CF] Furnace: the mouth sits on one horizontal face, chosen
        // deterministically from the block position so it survives chunk
        // rebuilds. Face indices: 0=+x, 1=-x, 4=+z, 5=-z.
        if (blockId == BlockType.FURNACE.id
                && (face == 0 || face == 1 || face == 4 || face == 5)) {
            int front = positionHash(wx, wy, wz) % 4;
            int faceIdx = (face == 0) ? 0 : (face == 1) ? 1 : (face == 4) ? 2 : 3;
            return faceIdx == front
                ? slotByName.getOrDefault("furnace", slot)
                : slotByName.getOrDefault("furnace_side", slot);
        }

        // Fast graphics swaps in the hole-free leaf tiles
        if (!ChunkMeshBuilder.isFancyGraphics()) {
            if ("oak_leaves".equals(name)) name = "oak_leaves_opaque";
            else if ("spruce_leaves".equals(name)) name = "spruce_leaves_opaque";
            else if ("jungle_leaves".equals(name)) name = "jungle_leaves_opaque";
        }

        // Wheat and other staged crops pick their growth stage from the
        // position, so a field is a mix of young and mature plants.
        String[] stages = STAGE_TILES.get(blockId);
        if (stages != null) {
            return slotByName.getOrDefault(
                    stages[stageOf(blockId, wx, wy, wz, chunk) % stages.length], slot);
        }

        Integer count = variantCount.get(name);
        if (count == null || count <= 1) return slot;

        int pick = positionHash(wx, wy, wz) % count;
        return variantFirstSlot.get(name) + pick;
    }

    /** Slot of a tile registered by name, e.g. "oak_door_top". */
    public int getSlotByName(String name) {
        Integer slot = slotByName.get(name);
        return slot == null ? 0 : slot;
    }

    /**
     * True when this face's texture may be mirrored without looking wrong.
     * Directional art (grass fringe, planks, bricks) must not be flipped.
     */
    public boolean canFlip(int blockId, int face) {
        String name = nameBySlot.get(getSlot(blockId, face));
        if (name == null) return false;
        return switch (name) {
            // Non-directional tiles: mirroring them breaks up the repetition
            // without producing anything that reads as upside-down
            case "grass_top", "dirt", "stone", "sand", "gravel",
                 "cobblestone", "snow", "netherrack", "clay",
                 "oak_leaves" -> true;
            default -> false;
        };
    }

    /** 0..3: bit 0 flips U, bit 1 flips V. */
    public int flipFor(int wx, int wy, int wz) {
        return (positionHash(wx, wy, wz) >> 3) & 3;
    }

    private static int positionHash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= (h >> 16);
        return h & 0x7FFFFFFF;
    }

    // ------------------------------------------------------------------
    // Upload
    // ------------------------------------------------------------------

    public void build() {
        // OpenGL treats the first uploaded row as t=0 (bottom). Our atlas array
        // has row 0 at the TOP, so flip vertically here; the UV formula in
        // TextureCoords assumes this orientation.
        int[] flipped = new int[atlasPixels.length];
        for (int y = 0; y < ATLAS_SIZE; y++) {
            int src = y * ATLAS_SIZE;
            int dst = (ATLAS_SIZE - 1 - y) * ATLAS_SIZE;
            System.arraycopy(atlasPixels, src, flipped, dst, ATLAS_SIZE);
        }

        atlasTexture = new Texture(ATLAS_SIZE, ATLAS_SIZE, flipped);
        buildArrayTexture();

        // Nothing is cached between runs: the atlas is rebuilt from source
        // every startup, so a texture change always reaches the screen.
        System.out.println("Atlas regenerated (no cache)");
        logFoliageTiles();

        System.out.println("Texture atlas " + ATLAS_SIZE + "x" + ATLAS_SIZE
            + ": " + nextSlot + " tiles (" + loadedFromPng + " from PNG, "
            + generated + " procedural), array layers=" + layerPixels.size()
            + ", mips=" + MIP_LEVELS + ", aniso=" + anisotropyUsed + "x");
    }

    /**
     * Upload every tile as one layer of a GL_TEXTURE_2D_ARRAY.
     *
     * Greedy meshing emits quads spanning many blocks, so UVs run 0..width.
     * A texture array with GL_REPEAT tiles correctly across a merged quad,
     * which a packed atlas cannot do without bleeding into neighbouring tiles.
     */
    private void buildArrayTexture() {
        int layers = layerPixels.size();
        if (layers == 0) return;

        arrayTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, arrayTextureId);

        ByteBuffer buffer = MemoryUtil.memAlloc(TEXTURE_SIZE * TEXTURE_SIZE * 4 * layers);
        for (int[] tile : layerPixels) {
            // Flip each tile so it matches OpenGL's bottom-up row order
            for (int y = TEXTURE_SIZE - 1; y >= 0; y--) {
                for (int x = 0; x < TEXTURE_SIZE; x++) {
                    int p = tile[y * TEXTURE_SIZE + x];
                    buffer.put((byte) ((p >> 16) & 0xFF));
                    buffer.put((byte) ((p >> 8) & 0xFF));
                    buffer.put((byte) (p & 0xFF));
                    buffer.put((byte) ((p >> 24) & 0xFF));
                }
            }
        }
        buffer.flip();

        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA,
            TEXTURE_SIZE, TEXTURE_SIZE, layers, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);

        // Each array layer mips independently, so neighbouring tiles can never
        // bleed into one another - no guard border needed, unlike a 2D atlas.
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_BASE_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAX_LEVEL, MIP_LEVELS - 1);
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);

        // Crisp texels up close, mip-blended in the distance
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        applyAnisotropy();

        MemoryUtil.memFree(buffer);
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    /**
     * Request 8x anisotropic filtering. Ships as an extension on GL 3.3
     * (GL_EXT_texture_filter_anisotropic), so probe before using it.
     */
    private void applyAnisotropy() {
        var caps = org.lwjgl.opengl.GL.getCapabilities();
        if (!caps.GL_EXT_texture_filter_anisotropic) {
            System.out.println("  anisotropic filtering unavailable, skipping");
            return;
        }

        float max = glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
        float level = Math.min(ANISOTROPY, max);
        glTexParameterf(GL_TEXTURE_2D_ARRAY,
            EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, level);
        anisotropyUsed = level;
    }

    /**
     * Print what actually landed in the atlas for the cut-out tiles.
     *
     * This is the check that catches a resource pack silently overriding a
     * generated tile - the failure mode that kept the reworked foliage from
     * reaching the screen.
     */
    private void logFoliageTiles() {
        for (String name : new String[]{"oak_leaves", "tall_grass", "grass_top", "grass_side"}) {
            Integer slot = slotByName.get(name);
            if (slot == null) continue;

            int[] px = layerPixels.get(slot);
            int holes = 0;
            long r = 0, g = 0, b = 0, n = 0;
            for (int p : px) {
                if (((p >>> 24) & 0xFF) < 128) { holes++; continue; }
                r += (p >> 16) & 0xFF;
                g += (p >> 8) & 0xFF;
                b += p & 0xFF;
                n++;
            }
            n = Math.max(1, n);
            double bias = (g / (double) n) / (((r / (double) n) + (b / (double) n)) / 2.0);

            System.out.printf("  %-12s layer=%2d cutout=%4.1f%% avg=(%3d,%3d,%3d) greenBias=%.2f%n",
                name, slot, 100.0 * holes / px.length,
                r / n, g / n, b / n, bias);
        }
    }

    /** Layer of a named tile, for the debug atlas viewer. */
    public int getLayerOf(String tileName) {
        return slotByName.getOrDefault(tileName, -1);
    }

    public void bindArray() {
        glBindTexture(GL_TEXTURE_2D_ARRAY, arrayTextureId);
    }

    public void unbindArray() {
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    public int getLayerCount() { return layerPixels.size(); }

    /** Array layer of the water tile, so the shader can animate just that one. */
    public int getWaterLayer() {
        return slotByName.getOrDefault("water", -1);
    }

    /** [GR-015] Array layer of the lava tile, for shader-side animation. */
    public int getLavaLayer() {
        return slotByName.getOrDefault("lava", -1);
    }

    public int getGlassLayer() {
        return slotByName.getOrDefault("glass", -1);
    }

    public int getIceLayer() {
        return slotByName.getOrDefault("ice", -1);
    }

    public void bind() {
        if (atlasTexture != null) atlasTexture.bind();
    }

    public void unbind() {
        if (atlasTexture != null) atlasTexture.unbind();
    }

    public void cleanup() {
        if (atlasTexture != null) atlasTexture.cleanup();
        if (arrayTextureId != 0) {
            glDeleteTextures(arrayTextureId);
            arrayTextureId = 0;
        }
    }

    public Texture getTexture() { return atlasTexture; }

    // ------------------------------------------------------------------
    // Procedural fallback tiles
    // ------------------------------------------------------------------

    private int[] generateTile(BlockType block) {
        int[] out = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        int r = (block.color >> 16) & 0xFF;
        int g = (block.color >> 8) & 0xFF;
        int b = block.color & 0xFF;

        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                int seed = x * 17 + y * 31 + block.id * 127;
                int noise = ((seed * 13 * 17) % 41) - 20;
                out[y * TEXTURE_SIZE + x] = generatePixel(block, x, y, r, g, b, noise);
            }
        }
        return out;
    }

    private int generatePixel(BlockType block, int x, int y, int r, int g, int b, int noise) {
        switch (block) {
            case GRASS_BLOCK: return grassPixel(x, y, noise);
            case STONE:       return stonePixel(x, y, noise);
            case COBBLESTONE: return cobblePixel(x, y, noise);
            case DIRT:        return dirtPixel(x, y, noise);
            case OAK_LOG:     return logPixel(x, y, noise);
            case OAK_LEAVES:  return leavesPixel(x, y, noise);
            case SAND:        return sandPixel(x, y, noise);
            case GLASS:       return glassPixel(x, y);
            case WATER:       return waterPixel(x, y, noise);
            case LAVA:        return lavaPixel(x, y, noise);
            case BRICK:       return brickPixel(x, y);
            case OAK_PLANKS:  return planksPixel(x, y, noise);
            case BEDROCK:     return bedrockPixel(x, y, noise);
            case COAL_ORE:
            case IRON_ORE:
            case GOLD_ORE:
            case DIAMOND_ORE: return orePixel(x, y, noise, block.color);
            case TNT:         return tntPixel(x, y);
            case CRAFTING_TABLE: return craftingPixel(x, y, noise);
            default:          return defaultPixel(x, y, r, g, b, noise);
        }
    }

    private int grassPixel(int x, int y, int noise) {
        int r = 93 + noise, g = 130 + noise, b = 66 + noise;
        if ((x + y) % 7 == 0) { r -= 15; g -= 10; }
        if ((x * y) % 11 == 0) { r += 10; g += 15; }
        return clamp(r, g, b);
    }

    private int stonePixel(int x, int y, int noise) {
        int v = 128 + noise;
        if ((x + y * 3) % 23 == 0) v -= 30;
        if ((x * 7 + y * 5) % 31 == 0) v += 20;
        return clamp(v, v, v);
    }

    private int cobblePixel(int x, int y, int noise) {
        int base = (((x / 8) + (y / 8)) % 2 == 0) ? 110 : 90;
        int v = base + noise + ((x * 3 + y * 7) % 20) - 10;
        return clamp(v, v, v);
    }

    private int dirtPixel(int x, int y, int noise) {
        int r = 139 + noise, g = 90 + noise, b = 43 + noise;
        if ((x * 5 + y * 3) % 13 == 0) { r -= 20; g -= 15; b -= 10; }
        return clamp(r, g, b);
    }

    private int logPixel(int x, int y, int noise) {
        int r = 107 + noise, g = 68 + noise, b = 35 + noise;
        if (x % 4 == 0) { r -= 25; g -= 20; b -= 15; }
        if ((x + y) % 8 == 0) { r += 15; g += 10; }
        return clamp(r, g, b);
    }

    private int leavesPixel(int x, int y, int noise) {
        int r = 45 + noise, g = 100 + noise, b = 30 + noise;
        if ((x + y) % 5 == 0) { r -= 20; g -= 15; }
        if ((x * y) % 7 == 0) { g += 25; b += 10; }
        if ((x * 13 + y * 11) % 17 == 0) { r -= 30; g -= 20; b -= 15; }
        return clamp(r, g, b);
    }

    private int sandPixel(int x, int y, int noise) {
        int r = 220 + noise, g = 205 + noise, b = 150 + noise;
        if ((x + y) % 9 == 0) { r -= 10; g -= 10; b -= 5; }
        return clamp(r, g, b);
    }

    private int glassPixel(int x, int y) {
        if (x == 0 || x == 15 || y == 0 || y == 15) return 0xFFC0E8FF;
        return 0x18FFFFFF;
    }

    private int waterPixel(int x, int y, int noise) {
        int r = 48 + noise / 2, g = 100 + noise / 2, b = 200 + noise;
        if ((x + y) % 6 == 0) { b += 20; g += 10; }
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return (0xA0 << 24) | (r << 16) | (g << 8) | b;
    }

    private int lavaPixel(int x, int y, int noise) {
        int r = 220 + noise, g = 80 + noise / 2, b = 20;
        if ((x * 7 + y * 11) % 13 == 0) { r = 255; g = 180; b = 50; }
        if ((x + y * 3) % 17 == 0) { r = 180; g = 40; b = 10; }
        return clamp(r, g, b);
    }

    private int brickPixel(int x, int y) {
        boolean mortar = (y % 8 == 0) || ((x + (y / 8) * 4) % 8 == 0);
        if (mortar) return 0xFFC8C8C8;
        int noise = ((x * 17 + y * 31) % 30) - 15;
        return clamp(170 + noise, 70 + noise, 50 + noise);
    }

    private int planksPixel(int x, int y, int noise) {
        int r = 180 + noise, g = 140 + noise, b = 90 + noise;
        if (y % 4 == 0) { r -= 20; g -= 15; b -= 10; }
        if ((x + y) % 6 == 0) { r += 10; g += 8; b += 5; }
        return clamp(r, g, b);
    }

    private int bedrockPixel(int x, int y, int noise) {
        int v = 40 + noise;
        if ((x * 3 + y * 7) % 11 == 0) v += 30;
        if ((x + y) % 5 == 0) v -= 20;
        return clamp(v, v, v);
    }

    private int orePixel(int x, int y, int noise, int oreColor) {
        int base = 128 + noise;
        if ((x * 13 + y * 17) % 19 < 4) {
            return clamp(((oreColor >> 16) & 0xFF) + noise,
                         ((oreColor >> 8) & 0xFF) + noise,
                         (oreColor & 0xFF) + noise);
        }
        return clamp(base, base, base);
    }

    private int tntPixel(int x, int y) {
        if (y < 4 || y > 11) return 0xFFFFFFFF;
        if (x < 2 || x > 13) return 0xFF808080;
        return 0xFFC02020;
    }

    private int craftingPixel(int x, int y, int noise) {
        int r = 180 + noise, g = 140 + noise, b = 90 + noise;
        if (x % 8 == 0 || y % 8 == 0) { r -= 40; g -= 30; b -= 20; }
        return clamp(r, g, b);
    }

    private int defaultPixel(int x, int y, int r, int g, int b, int noise) {
        int dr = r + noise, dg = g + noise, db = b + noise;
        if (x == 0 || x == 15) { dr -= 25; dg -= 25; db -= 25; }
        if (y == 0 || y == 15) { dr -= 25; dg -= 25; db -= 25; }
        return clamp(dr, dg, db);
    }

    private int clamp(int r, int g, int b) {
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
