package com.voxelgame.rendering;

import com.voxelgame.world.BlockType;

/**
 * Procedural 16x16 block tiles in the style of the original textures.
 *
 * The classic look comes from three things, all reproduced here:
 *   - a tight palette of 4-6 shades per material, never a smooth gradient
 *   - ordered dithering between neighbouring shades, so surfaces read as
 *     noisy pixel art rather than filtered noise
 *   - a few hand-placed features (mortar lines, ore blobs, plank seams)
 *
 * Everything is a pure function of (tile, x, y, variant), so tiles are
 * stable across runs and variants stay distinct.
 */
public final class TileGenerator {

    public static final int SIZE = 16;

    /**
     * Alpha values used as a tint mask on otherwise opaque tiles.
     * 255 = tint this texel, 254 = leave it alone (visually identical).
     */
    public static final int TINT_FULL = 255;
    public static final int TINT_NONE = 254;

    /** 4x4 Bayer matrix, the classic ordered-dither kernel. */
    private static final int[][] BAYER = {
        { 0,  8,  2, 10},
        {12,  4, 14,  6},
        { 3, 11,  1,  9},
        {15,  7, 13,  5}
    };

    private TileGenerator() {}

    // ------------------------------------------------------------------
    // Palettes: darkest to lightest
    // ------------------------------------------------------------------

    /**
     * Grass and leaves are drawn almost desaturated on purpose.
     *
     * The biome tint is a MULTIPLIER (base * tint), so a base that is already
     * green multiplies green by green and produces the acid look. Keeping the
     * base near-grey with only a slight warm cast means the tint alone decides
     * the hue, exactly as the vanilla colormaps do.
     */
    private static final int[] P_GRASS_TOP = {0xA8AC9C, 0xB6BAA8, 0xC2C6B4, 0xCED2BE, 0xD8DCC8};
    private static final int[] P_DIRT   = {0x6B4A2A, 0x785331, 0x866038, 0x936C40, 0x9E7748};
    private static final int[] P_STONE  = {0x6E6E6E, 0x7B7B7B, 0x888888, 0x959595, 0xA0A0A0};
    private static final int[] P_COBBLE = {0x5A5A5A, 0x6B6B6B, 0x7E7E7E, 0x909090, 0xA2A2A2};
    private static final int[] P_SAND   = {0xC8BC7E, 0xD4C88C, 0xDFD59B, 0xE8DFA9, 0xF0E8B8};
    private static final int[] P_WOOD   = {0x6B4A28, 0x7A5730, 0x8A6538, 0x977140};
    private static final int[] P_PLANK  = {0x8A6738, 0x9A7440, 0xA88049, 0xB58C52};
    /** Near-grey like the grass palette, for the same multiply-tint reason. */
    private static final int[] P_LEAF   = {0x8E9484, 0x9CA292, 0xAAB09E, 0xB6BCAA};
    private static final int[] P_GRAVEL = {0x5E5E5E, 0x707070, 0x828282, 0x949494, 0xA6A6A6};
    private static final int[] P_SNOW   = {0xDCE6E8, 0xE7EFF1, 0xF1F7F8, 0xFBFDFD};
    private static final int[] P_CLAY   = {0x9098A6, 0x9CA4B1, 0xA8B0BC, 0xB4BCC7};
    private static final int[] P_NETHER = {0x5A1F1F, 0x6B2626, 0x7C2D2D, 0x8B3434};
    private static final int[] P_OBSID  = {0x100A1A, 0x18102A, 0x201638, 0x281C46};
    private static final int[] P_ICE    = {0x8FB8E8, 0x9FC6F0, 0xB0D2F5, 0xC2DEFA};

    /**
     * Generate one tile.
     *
     * @param tile    logical tile name, e.g. "grass_top"
     * @param variant 0..3, picks a different noise phase for tile variety
     */
    public static int[] generate(String tile, BlockType type, int variant) {
        return switch (tile) {
            case "grass_top"    -> noisy(P_GRASS_TOP, variant, 3);
            case "grass_side"   -> grassSide(variant);
            case "dirt"         -> noisy(P_DIRT, variant, 3);
            case "podzol"       -> podzol(variant);
            case "stone"        -> noisy(P_STONE, variant, 2);
            case "cobblestone"  -> cobblestone(variant);
            case "mossy_cobblestone" -> mossyCobble(variant);
            case "sand"         -> noisy(P_SAND, variant, 2);
            case "gravel"       -> noisy(P_GRAVEL, variant, 4);
            case "clay"         -> noisy(P_CLAY, variant, 2);
            case "snow"         -> noisy(P_SNOW, variant, 2);
            case "ice"          -> noisy(P_ICE, variant, 2);
            case "netherrack"   -> noisy(P_NETHER, variant, 4);
            case "obsidian"     -> noisy(P_OBSID, variant, 3);
            case "oak_log"      -> logSide(variant);
            case "oak_log_top"  -> logTop(variant);
            case "oak_planks"   -> planks(variant);
            case "oak_leaves"   -> leaves(variant, true);
            case "oak_leaves_opaque" -> leaves(variant, false);
            case "brick"        -> bricks();
            case "stone_bricks" -> stoneBricks(variant);
            case "glass"        -> glass();
            case "water"        -> water(variant);
            case "lava"         -> lava(variant);
            case "bedrock"      -> bedrock(variant);
            case "tnt"          -> tnt();
            case "crafting_table" -> craftingTable(variant);
            case "glowstone"    -> glowstone(variant);
            case "ore_coal"     -> ore(0x1A1A1A, 0x2E2E2E, variant);
            case "ore_iron"     -> ore(0xC8A882, 0xDBBE9C, variant);
            case "ore_gold"     -> ore(0xF0C020, 0xFCE060, variant);
            case "ore_diamond"  -> ore(0x3AD8E0, 0x7FEEF5, variant);
            case "gold_block"   -> metalBlock(0xF0C020, 0xFCE060, 0xC89A18);
            case "iron_block"   -> metalBlock(0xD8D8D8, 0xF0F0F0, 0xA8A8A8);
            case "diamond_block"-> metalBlock(0x5DECF5, 0x9FF6FA, 0x3AB8C4);
            case "coal_block"   -> noisy(new int[]{0x121212, 0x1C1C1C, 0x262626}, variant, 3);
            case "granite"      -> noisy(new int[]{0x9A6B58, 0xA87765, 0xB58472, 0xC0907E}, variant, 3);
            case "sandstone"    -> sandstone();
            // tall_grass removed - uses PNG texture
            case "dandelion"    -> flower(0xF0D020, 0xFCEE60, variant);
            case "poppy"        -> flower(0xC81E1E, 0xE84040, variant);
            case "dead_bush"    -> deadBush(variant);
            case "cactus_side"  -> cactusSide(variant);
            case "cactus_top"   -> cactusTop(variant);
            // --- Biome blocks ---
            case "autumn_leaves" -> noisy(new int[]{0xB0541F, 0xC86822, 0xD97F26, 0xA04818}, variant, 3);
            case "cherry_leaves" -> noisy(new int[]{0xE8B7C8, 0xF2D0DA, 0xDDB0C0, 0xF5E0E8}, variant, 3);
            case "lavender"      -> flower(0x7E4BB5, 0xE0C8F0, variant); // purple flower
            case "crystal"       -> noisy(new int[]{0x5E2A8C, 0x7E3ABE, 0x9B59D0, 0x4A1A6C}, variant, 3);
            case "basalt"        -> noisy(new int[]{0x2A2A2E, 0x3A3A3E, 0x4A4A50, 0x1A1A1E}, variant, 2);
            case "salt"          -> noisy(new int[]{0xE8E8E4, 0xF0F0EC, 0xD8D8D4, 0xE0E0DC}, variant, 2);
            case "ash"           -> noisy(new int[]{0x9A9A9A, 0xA8A8A8, 0x8A8A8A, 0x7A7A7A}, variant, 3);
            case "charred_log"   -> logSide(variant); // reuse with dark colors
            case "charred_log_top" -> logTop(variant);
            case "petrified_log" -> logSide(variant); // reuse with gray
            case "petrified_log_top" -> logTop(variant);
            case "blue_ice"      -> noisy(new int[]{0x6AA7E8, 0x8CC0F0, 0xB8DCFA, 0x5090D0}, variant, 2);
            case "mycelium_top"  -> noisy(new int[]{0x6E5A6E, 0x807080, 0x5E4A5E, 0x7E6A7E}, variant, 3);
            case "terracotta"    -> noisy(new int[]{0xB0522B, 0xC8683A, 0x8C3F1F, 0xD97F45}, variant, 3);
            case "red_sand"      -> noisy(new int[]{0xC06A2A, 0xD07A3A, 0xE08A4A, 0xB05A1A}, variant, 2);
            case "lily_pad"      -> noisy(new int[]{0x2D6B1E, 0x3A8A2A, 0x1E5A15, 0x4A9A3A}, variant, 2);
            case "sunflower"     -> flower(0xF0D020, 0xFCEE4B, variant);
            case "vine"          -> tallGrass(variant);
            case "deepslate"     -> noisy(new int[]{0x3A3A3A, 0x4A4A4A, 0x2A2A2E, 0x5A5A5A}, variant, 2);

            // --- Missing blocks for creative inventory ---
            case "polished_granite" -> noisy(new int[]{0xB0957C, 0xC0A58C, 0xA0856C, 0xD0B59C}, variant, 3);
            case "diorite"       -> noisy(new int[]{0xD0D0D0, 0xE0E0E0, 0xC0C0C0, 0xF0F0F0}, variant, 2);
            case "polished_diorite" -> noisy(new int[]{0xE8E8E8, 0xF0F0F0, 0xD8D8D8, 0xFAFAFA}, variant, 2);
            case "andesite"      -> noisy(new int[]{0x8A8A8A, 0x9A9A9A, 0x7A7A7A, 0xAAAAAA}, variant, 2);
            case "polished_andesite" -> noisy(new int[]{0x9E9E9E, 0xAEAEAE, 0x8E8E8E, 0xBEBEBE}, variant, 2);
            case "coarse_dirt"   -> noisy(new int[]{0x6B4A2A, 0x7A5A3A, 0x5A3A1A, 0x8A6A4A}, variant, 4);

            // Planks
            case "spruce_planks" -> noisy(new int[]{0x7A5A3A, 0x8A6A4A, 0x6A4A2A, 0x9A7A5A}, variant, 2);
            case "birch_planks"  -> noisy(new int[]{0xD8C898, 0xE8D8A8, 0xC8B888, 0xF8E8B8}, variant, 2);
            case "jungle_planks" -> noisy(new int[]{0xB08050, 0xC09060, 0xA07040, 0xD0A070}, variant, 2);
            case "acacia_planks" -> noisy(new int[]{0xC08040, 0xD09050, 0xB07030, 0xE0A060}, variant, 2);
            case "dark_oak_planks" -> noisy(new int[]{0x5A3A20, 0x6A4A30, 0x4A2A10, 0x7A5A40}, variant, 2);

            // Logs
            case "spruce_log"    -> noisy(new int[]{0x4A3020, 0x5A4030, 0x3A2010, 0x6A5040}, variant, 3);
            case "spruce_log_top" -> noisy(new int[]{0x7A5A3A, 0x8A6A4A, 0x6A4A2A, 0x9A7A5A}, variant, 2);
            case "jungle_log"    -> noisy(new int[]{0x6A4020, 0x7A5030, 0x5A3010, 0x8A6040}, variant, 3);
            case "jungle_log_top" -> noisy(new int[]{0xB08050, 0xC09060, 0xA07040, 0xD0A070}, variant, 2);

            // Leaves
            case "spruce_leaves" -> noisy(new int[]{0x1A4A1A, 0x2A5A2A, 0x0A3A0A, 0x3A6A3A}, variant, 3);
            case "jungle_leaves" -> noisy(new int[]{0x3A7A2A, 0x4A8A3A, 0x2A6A1A, 0x5A9A4A}, variant, 3);

            // Ores
            case "emerald_ore"   -> ore(0x30E050, 0x50FF70, variant);
            case "redstone_ore"  -> ore(0xE02020, 0xFF4040, variant);
            case "lapis_ore"     -> ore(0x3050E0, 0x5070FF, variant);
            case "copper_ore"    -> ore(0xB87333, 0xD89353, variant);

            // Blocks
            case "emerald_block" -> metalBlock(0x30E050, 0x50FF70, 0x20A030);
            case "white_wool"    -> noisy(new int[]{0xF0F0F0, 0xE8E8E8, 0xF8F8F8, 0xE0E0E0}, variant, 1);
            case "furnace"       -> noisy(new int[]{0x696969, 0x797979, 0x595959, 0x898989}, variant, 2);
            case "furnace_side"  -> noisy(new int[]{0x595959, 0x696969, 0x494949, 0x797979}, variant, 2);
            case "chest"         -> noisy(new int[]{0x8B5A2B, 0x9B6A3B, 0x7B4A1B, 0xAB7A4B}, variant, 2);

            // Crops
            case "wheat"         -> tallGrass(variant);
            case "carrot"        -> tallGrass(variant);
            case "potato"        -> tallGrass(variant);

            // Others
            case "bookshelf"     -> noisy(new int[]{0x8B5A2B, 0x9B6A3B, 0x7B4A1B, 0xAB7A4B}, variant, 2);
            case "soul_sand"     -> noisy(new int[]{0x5A4030, 0x6A5040, 0x4A3020, 0x7A6050}, variant, 3);
            case "end_stone"     -> noisy(new int[]{0xE8E8A0, 0xF8F8B0, 0xD8D890, 0xFAFAC0}, variant, 2);
            case "purpur_block"  -> noisy(new int[]{0xA060A0, 0xB070B0, 0x905090, 0xC080C0}, variant, 2);
            case "white_concrete" -> noisy(new int[]{0xE0E0E0, 0xE8E8E8, 0xD8D8D8, 0xF0F0F0}, variant, 1);
            case "red_concrete"  -> noisy(new int[]{0xC03030, 0xD04040, 0xB02020, 0xE05050}, variant, 1);
            case "green_concrete" -> noisy(new int[]{0x3A8A3A, 0x4A9A4A, 0x2A7A2A, 0x5AAA5A}, variant, 1);
            case "blue_concrete" -> noisy(new int[]{0x3050C0, 0x4060D0, 0x2040B0, 0x5070E0}, variant, 1);
            case "glowing_obsidian" -> noisy(new int[]{0x4A2060, 0x5A3070, 0x3A1050, 0x6A4080}, variant, 3);
            case "nether_reactor" -> noisy(new int[]{0x202040, 0x303050, 0x101030, 0x404060}, variant, 2);
            case "nether_bricks" -> noisy(new int[]{0x3A1A1A, 0x4A2A2A, 0x2A0A0A, 0x5A3A3A}, variant, 2);

            // --- Item sprites ---
            case "iron_sword"   -> swordSprite();
            case "wooden_pickaxe" -> toolSprite(0xFF6B4423, 0xFF8A5C30, 0xFFAA7440, 0xFF5A3A18, 0xFF7A5228, "pickaxe");
            case "wooden_axe" -> toolSprite(0xFF6B4423, 0xFF8A5C30, 0xFFAA7440, 0xFF5A3A18, 0xFF7A5228, "axe");
            case "wooden_shovel" -> toolSprite(0xFF6B4423, 0xFF8A5C30, 0xFFAA7440, 0xFF5A3A18, 0xFF7A5228, "shovel");
            case "wooden_sword" -> toolSprite(0xFF6B4423, 0xFF8A5C30, 0xFFAA7440, 0xFF5A3A18, 0xFF7A5228, "sword_generic");
            case "wooden_hoe" -> toolSprite(0xFF6B4423, 0xFF8A5C30, 0xFFAA7440, 0xFF5A3A18, 0xFF7A5228, "hoe");
            case "stone_pickaxe" -> toolSprite(0xFF5A5A5A, 0xFF7A7A7A, 0xFF9A9A9A, 0xFF5A3A18, 0xFF7A5228, "pickaxe");
            case "stone_axe" -> toolSprite(0xFF5A5A5A, 0xFF7A7A7A, 0xFF9A9A9A, 0xFF5A3A18, 0xFF7A5228, "axe");
            case "stone_shovel" -> toolSprite(0xFF5A5A5A, 0xFF7A7A7A, 0xFF9A9A9A, 0xFF5A3A18, 0xFF7A5228, "shovel");
            case "stone_sword" -> toolSprite(0xFF5A5A5A, 0xFF7A7A7A, 0xFF9A9A9A, 0xFF5A3A18, 0xFF7A5228, "sword_generic");
            case "stone_hoe" -> toolSprite(0xFF5A5A5A, 0xFF7A7A7A, 0xFF9A9A9A, 0xFF5A3A18, 0xFF7A5228, "hoe");
            case "iron_pickaxe" -> toolSprite(0xFF808088, 0xFFA0A0B0, 0xFFC0C0D0, 0xFF5A3A18, 0xFF7A5228, "pickaxe");
            case "iron_axe" -> toolSprite(0xFF808088, 0xFFA0A0B0, 0xFFC0C0D0, 0xFF5A3A18, 0xFF7A5228, "axe");
            case "iron_shovel" -> toolSprite(0xFF808088, 0xFFA0A0B0, 0xFFC0C0D0, 0xFF5A3A18, 0xFF7A5228, "shovel");
            case "iron_sword_item" -> toolSprite(0xFF808088, 0xFFA0A0B0, 0xFFC0C0D0, 0xFF5A3A18, 0xFF7A5228, "sword_generic");
            case "iron_hoe" -> toolSprite(0xFF808088, 0xFFA0A0B0, 0xFFC0C0D0, 0xFF5A3A18, 0xFF7A5228, "hoe");
            case "diamond_pickaxe" -> toolSprite(0xFF2AA8A0, 0xFF4CC8C0, 0xFF6EE8E0, 0xFF5A3A18, 0xFF7A5228, "pickaxe");
            case "diamond_axe" -> toolSprite(0xFF2AA8A0, 0xFF4CC8C0, 0xFF6EE8E0, 0xFF5A3A18, 0xFF7A5228, "axe");
            case "diamond_shovel" -> toolSprite(0xFF2AA8A0, 0xFF4CC8C0, 0xFF6EE8E0, 0xFF5A3A18, 0xFF7A5228, "shovel");
            case "diamond_sword" -> toolSprite(0xFF2AA8A0, 0xFF4CC8C0, 0xFF6EE8E0, 0xFF5A3A18, 0xFF7A5228, "sword_generic");
            case "diamond_hoe" -> toolSprite(0xFF2AA8A0, 0xFF4CC8C0, 0xFF6EE8E0, 0xFF5A3A18, 0xFF7A5228, "hoe");
            case "golden_pickaxe" -> toolSprite(0xFFD4A820, 0xFFF0C840, 0xFFFCE060, 0xFF5A3A18, 0xFF7A5228, "pickaxe");
            case "golden_axe" -> toolSprite(0xFFD4A820, 0xFFF0C840, 0xFFFCE060, 0xFF5A3A18, 0xFF7A5228, "axe");
            case "golden_shovel" -> toolSprite(0xFFD4A820, 0xFFF0C840, 0xFFFCE060, 0xFF5A3A18, 0xFF7A5228, "shovel");
            case "golden_sword" -> toolSprite(0xFFD4A820, 0xFFF0C840, 0xFFFCE060, 0xFF5A3A18, 0xFF7A5228, "sword_generic");
            case "golden_hoe" -> toolSprite(0xFFD4A820, 0xFFF0C840, 0xFFFCE060, 0xFF5A3A18, 0xFF7A5228, "hoe");

            // --- Materials ---
            case "stick" -> stickSprite();
            case "coal" -> coalSprite();
            case "iron_ingot" -> ingotSprite(0xFF808088, 0xFFB0B0B8, 0xFFD8D8E0);
            case "gold_ingot" -> ingotSprite(0xFFD4A820, 0xFFF0C840, 0xFFFCE060);
            case "diamond" -> gemSprite(0xFF2AA8A0, 0xFF4CC8C0, 0xFF8EF0F8);
            case "leather" -> leatherSprite();
            case "string" -> stringSprite();
            case "feather" -> featherSprite();
            case "flint" -> flintSprite();
            case "gunpowder" -> gunpowderSprite();
            case "egg" -> eggSprite();

            // --- Armor ---
            case "leather_helmet" -> armorSprite(0xFF6B3A18, 0xFF8B5A28, 0xFFA87238, "helmet");
            case "leather_chestplate" -> armorSprite(0xFF6B3A18, 0xFF8B5A28, 0xFFA87238, "chestplate");
            case "leather_leggings" -> armorSprite(0xFF6B3A18, 0xFF8B5A28, 0xFFA87238, "leggings");
            case "leather_boots" -> armorSprite(0xFF6B3A18, 0xFF8B5A28, 0xFFA87238, "boots");
            case "iron_helmet" -> armorSprite(0xFF808088, 0xFFA8A8B0, 0xFFD0D0D8, "helmet");
            case "iron_chestplate" -> armorSprite(0xFF808088, 0xFFA8A8B0, 0xFFD0D0D8, "chestplate");
            case "iron_leggings" -> armorSprite(0xFF808088, 0xFFA8A8B0, 0xFFD0D0D8, "leggings");
            case "iron_boots" -> armorSprite(0xFF808088, 0xFFA8A8B0, 0xFFD0D0D8, "boots");
            case "diamond_helmet" -> armorSprite(0xFF2AA8A0, 0xFF4CC8C0, 0xFF8EF0F8, "helmet");
            case "diamond_chestplate" -> armorSprite(0xFF2AA8A0, 0xFF4CC8C0, 0xFF8EF0F8, "chestplate");
            case "diamond_leggings" -> armorSprite(0xFF2AA8A0, 0xFF4CC8C0, 0xFF8EF0F8, "leggings");
            case "diamond_boots" -> armorSprite(0xFF2AA8A0, 0xFF4CC8C0, 0xFF8EF0F8, "boots");

            // --- Food ---
            case "bread" -> breadSprite();
            case "raw_pork" -> meatSprite(0xFFC85050, 0xFFE07070, 0xFFF09090, false);
            case "cooked_pork" -> meatSprite(0xFF8A4020, 0xFFA85830, 0xFFC87040, true);
            case "raw_beef" -> meatSprite(0xFFC85050, 0xFFE07070, 0xFFF09090, false);
            case "cooked_beef" -> meatSprite(0xFF8A4020, 0xFFA85830, 0xFFC87040, true);
            case "raw_chicken" -> meatSprite(0xFFD09090, 0xFFE8B0B0, 0xFFF0D0D0, false);
            case "cooked_chicken" -> meatSprite(0xFF906030, 0xFFB07840, 0xFFD09050, true);
            case "raw_mutton" -> meatSprite(0xFFC05050, 0xFFD87070, 0xFFE89090, false);
            case "cooked_mutton" -> meatSprite(0xFF8A4020, 0xFFA85830, 0xFFC87040, true);
            case "apple" -> appleSprite();

            // --- Misc ---
            case "bow" -> bowSprite();
            case "arrow" -> arrowSprite();
            case "torch" -> torchSprite();
            case "rotten_flesh" -> rottenFleshSprite();
            case "bone" -> boneSprite();
            case "spider_eye" -> spiderEyeSprite();

            default             -> fromBlockColor(type, variant);
        };
    }

    // ------------------------------------------------------------------
    // Core: dithered noise fill
    // ------------------------------------------------------------------

    /**
     * Fill with palette shades chosen by hash noise, then push the choice up
     * or down one step using the Bayer matrix. That ordered dither is what
     * gives the original textures their characteristic speckle.
     *
     * @param roughness how far the noise can swing across the palette
     */
    private static int[] noisy(int[] palette, int variant, int roughness) {
        int[] out = new int[SIZE * SIZE];

        // Spread across the whole palette; roughness only controls how much
        // of the swing comes from noise versus the ordered dither. Scaling
        // the index by roughness instead would clamp quiet materials like
        // stone to the darkest shades and leave them visibly flat.
        double ditherWeight = 1.0 / (roughness + 1.0);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double n = hash(x, y, variant * 977 + 13);
                double d = BAYER[y & 3][x & 3] / 15.0;

                double v = n * (1.0 - ditherWeight) + d * ditherWeight;

                int idx = (int) (v * palette.length);
                idx = Math.max(0, Math.min(palette.length - 1, idx));

                out[y * SIZE + x] = 0xFF000000 | palette[idx];
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Specific tiles
    // ------------------------------------------------------------------

    /**
     * Dirt with a grass cap hanging over the top rows.
     *
     * Only the cap may be tinted - dirt must stay brown in every biome. The
     * mask is carried in the alpha channel: 255 where the biome tint applies,
     * 254 where it must not. The shader keys off that, so no second texture
     * or extra vertex attribute is needed and the tile stays fully opaque.
     */
    private static int[] grassSide(int variant) {
        int[] out = noisy(P_DIRT, variant, 3);
        int[] cap = noisy(P_GRASS_TOP, variant, 3);

        // Dirt is never tinted
        for (int i = 0; i < out.length; i++) {
            out[i] = (out[i] & 0x00FFFFFF) | (TINT_NONE << 24);
        }

        for (int x = 0; x < SIZE; x++) {
            // Ragged lower edge, 4-5px deep as the original
            int depth = 4 + (int) (hash(x, 0, variant * 31 + 7) * 2);
            for (int y = 0; y < depth; y++) {
                out[y * SIZE + x] = (cap[y * SIZE + x] & 0x00FFFFFF) | (TINT_FULL << 24);
            }
        }
        return out;
    }

    private static int[] podzol(int variant) {
        int[] out = noisy(P_DIRT, variant, 3);
        int[] top = noisy(new int[]{0x5A3A18, 0x6B4620, 0x7A5228}, variant, 3);
        for (int x = 0; x < SIZE; x++) {
            int depth = 2 + (int) (hash(x, 1, variant * 17) * 3);
            for (int y = 0; y < depth; y++) out[y * SIZE + x] = top[y * SIZE + x];
        }
        return out;
    }

    /** Irregular rounded stones separated by dark mortar. */
    private static int[] cobblestone(int variant) {
        int[] out = new int[SIZE * SIZE];
        int mortar = 0xFF4A4A4A;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // Two staggered rows of stones
                int cellY = y / 5;
                int offset = (cellY % 2 == 0) ? 0 : 3;
                int cellX = ((x + offset) % SIZE) / 5;

                int lx = (x + offset) % 5;
                int ly = y % 5;

                boolean edge = lx == 0 || ly == 0
                    || (lx == 4 && hash(cellX, cellY, variant) > 0.4)
                    || (ly == 4 && hash(cellY, cellX, variant + 5) > 0.4);

                if (edge) {
                    out[y * SIZE + x] = mortar;
                } else {
                    double n = hash(x, y, variant * 41 + cellX * 7 + cellY * 3);
                    int idx = (int) (n * P_COBBLE.length);
                    idx = Math.min(P_COBBLE.length - 1, idx);
                    out[y * SIZE + x] = 0xFF000000 | P_COBBLE[idx];
                }
            }
        }
        return out;
    }

    private static int[] mossyCobble(int variant) {
        int[] out = cobblestone(variant);
        for (int i = 0; i < out.length; i++) {
            int x = i % SIZE, y = i / SIZE;
            if (hash(x, y, variant * 61 + 3) > 0.55) {
                // Tint towards moss green
                int c = out[i];
                int r = ((c >> 16) & 0xFF) * 55 / 100;
                int g = Math.min(255, ((c >> 8) & 0xFF) * 105 / 100);
                int b = (c & 0xFF) * 50 / 100;
                out[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    /** Vertical bark grain. */
    private static int[] logSide(int variant) {
        int[] out = new int[SIZE * SIZE];
        for (int x = 0; x < SIZE; x++) {
            // Each column picks a shade and keeps it, giving vertical strands
            double col = hash(x, 0, variant * 23 + 11);
            for (int y = 0; y < SIZE; y++) {
                double n = col * 0.7 + hash(x, y, variant) * 0.3;
                int idx = (int) (n * P_WOOD.length);
                idx = Math.min(P_WOOD.length - 1, idx);
                out[y * SIZE + x] = 0xFF000000 | P_WOOD[idx];
            }
        }
        return out;
    }

    /** Concentric growth rings. */
    private static int[] logTop(int variant) {
        int[] out = new int[SIZE * SIZE];
        double cx = 7.5, cy = 7.5;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double dx = x - cx, dy = y - cy;
                double r = Math.sqrt(dx * dx + dy * dy);
                double wobble = hash(x, y, variant * 13) * 0.6;
                int ring = (int) ((r + wobble) / 1.6);
                int idx = ring % P_PLANK.length;
                out[y * SIZE + x] = 0xFF000000 | P_PLANK[idx];
            }
        }
        return out;
    }

    /** Horizontal boards with dark seams and grain. */
    private static int[] planks(int variant) {
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            boolean seam = (y % 4 == 0);
            int board = y / 4;
            for (int x = 0; x < SIZE; x++) {
                if (seam) {
                    out[y * SIZE + x] = 0xFF5E4224;
                    continue;
                }
                double n = hash(x, board, variant * 29 + 5) * 0.5
                         + hash(x, y, variant) * 0.5;
                int idx = (int) (n * P_PLANK.length);
                idx = Math.min(P_PLANK.length - 1, idx);
                out[y * SIZE + x] = 0xFF000000 | P_PLANK[idx];
            }
        }
        return out;
    }

    /**
     * Leaves: clumps of foliage separated by real gaps.
     *
     * Roughly a third of the tile is punched out, which is what lets you see
     * sky through a canopy. Holes are clustered rather than single stray
     * pixels - sampling the noise at half resolution groups them into 2x2-ish
     * gaps, so the leaves read as overlapping sprigs instead of TV static.
     */
    /**
     * @param cutout true for Fancy, punching real holes; false for Fast,
     *               where the gaps become dark foliage instead so the tile
     *               stays fully opaque
     */
    private static int[] leaves(int variant, boolean cutout) {
        int[] out = new int[SIZE * SIZE];

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // Coarse field decides gap vs foliage, fine field shades it
                double coarse = hash(x / 2, y / 2, variant * 37 + 19) * 0.7
                              + hash(x, y, variant * 11 + 3) * 0.3;

                if (coarse > 0.63) {
                    // Fast keeps the pattern but fills it in, so the canopy
                    // still reads as leaves rather than a flat green cube
                    out[y * SIZE + x] = cutout
                        ? 0x00000000
                        : (0xFF000000 | P_LEAF[0]);
                    continue;
                }

                double n = hash(x, y, variant * 53 + 7);
                int idx = (int) (n * P_LEAF.length);
                idx = Math.min(P_LEAF.length - 1, idx);

                // Darken pixels next to a gap, giving the clumps some depth
                boolean nearGap = hash((x + 1) / 2, y / 2, variant * 37 + 19) * 0.7
                                + hash(x + 1, y, variant * 11 + 3) * 0.3 > 0.63;
                if (nearGap && idx > 0) idx--;

                out[y * SIZE + x] = 0xFF000000 | P_LEAF[idx];
            }
        }
        return out;
    }

    private static int[] bricks() {
        int[] out = new int[SIZE * SIZE];
        int mortar = 0xFFB0B0B0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int row = y / 4;
                int offset = (row % 2 == 0) ? 0 : 4;
                boolean isMortar = (y % 4 == 0) || ((x + offset) % 8 == 0);
                if (isMortar) {
                    out[y * SIZE + x] = mortar;
                } else {
                    double n = hash(x, y, 3);
                    int r = 150 + (int) (n * 30);
                    int g = 60 + (int) (n * 22);
                    int b = 45 + (int) (n * 18);
                    out[y * SIZE + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }
        }
        return out;
    }

    private static int[] stoneBricks(int variant) {
        int[] out = noisy(P_STONE, variant, 2);
        int mortar = 0xFF5A5A5A;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int row = y / 8;
                int offset = (row % 2 == 0) ? 0 : 4;
                if (y % 8 == 0 || (x + offset) % 8 == 0) {
                    out[y * SIZE + x] = mortar;
                }
            }
        }
        return out;
    }

    private static int[] sandstone() {
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            // Horizontal sediment banding
            double band = Math.sin(y * 0.9) * 0.5 + 0.5;
            for (int x = 0; x < SIZE; x++) {
                double n = hash(x, y, 9) * 0.4 + band * 0.6;
                int idx = (int) (n * P_SAND.length);
                idx = Math.min(P_SAND.length - 1, idx);
                out[y * SIZE + x] = 0xFF000000 | P_SAND[idx];
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Cross-quad plants: mostly transparent, drawn white so the mesher's
    // biome tint does the colouring
    // ------------------------------------------------------------------

    /** Blades fanning up from the bottom edge. */
    private static int[] tallGrass(int variant) {
        int[] out = new int[SIZE * SIZE];
        // Near-grey like the other foliage: the biome tint multiplies this,
        // so a green base here would green-multiply into acid, which is
        // exactly what the atlas log caught (1.83x green dominance).
        int[] pal = {0x9EA492, 0xACB2A0, 0xB8BEAC, 0xC4CAB8};

        // Blades stay strictly 1px wide and curve as they rise, which keeps
        // the sprite lacy. Thickening them turned it into a solid sheet that
        // read as a leaf rather than grass.
        for (int blade = 0; blade < 6; blade++) {
            int rootX = 1 + blade * 3 + (int) (hash(blade, 0, variant) * 2);
            if (rootX >= SIZE - 1) continue;

            int height = 8 + (int) (hash(blade, 1, variant) * 6);
            float curve = (float) (hash(blade, 2, variant) - 0.5) * 4.0f;

            int shade = (int) (hash(blade, 3, variant) * pal.length);
            shade = Math.min(pal.length - 1, shade);

            for (int i = 0; i < height; i++) {
                int y = SIZE - 1 - i;
                if (y < 0) continue;

                // Curve accelerates towards the tip
                float t = i / (float) height;
                int x = rootX + Math.round(curve * t * t);
                if (x < 0 || x >= SIZE) continue;

                // Tips catch a little more light
                int s = (i > height - 3) ? Math.min(pal.length - 1, shade + 1) : shade;
                out[y * SIZE + x] = 0xFF000000 | pal[s];
            }
        }
        return out;
    }

    /**
     * A flower: thin green stem rising from the bottom edge, 6x6 blossom
     * sitting on top of it, everything else fully transparent.
     *
     * Rows are counted from the top of the tile, so the stem occupies the
     * LOWER rows (y = 9..15) and the head the upper ones. Getting that
     * inverted is what turned these into a solid red blob.
     */
    private static int[] flower(int petal, int highlight, int variant) {
        int[] out = new int[SIZE * SIZE];   // transparent by default

        final int stemDark = 0xFF3E6B24;
        final int stemLight = 0xFF4F8630;

        // Head occupies rows 2..7, stem rows 8..15
        final int headTop = 2;
        final int headSize = 6;
        final int stemTop = headTop + headSize;   // 8

        int cx = 7 + (int) (hash(0, 0, variant) * 2);   // 7 or 8

        // --- stem: 1-2 px wide, from below the head down to the ground ---
        for (int y = stemTop; y < SIZE; y++) {
            out[y * SIZE + cx] = stemDark;
            // Second column low down, so the base looks rooted
            if (y >= stemTop + 3) {
                int side = cx + ((variant % 2 == 0) ? 1 : -1);
                if (side >= 0 && side < SIZE) out[y * SIZE + side] = stemLight;
            }
        }

        // A pair of small leaves partway up
        int leafY = stemTop + 2;
        if (cx - 1 >= 0) out[leafY * SIZE + cx - 1] = stemLight;
        if (cx + 1 < SIZE) out[(leafY + 1) * SIZE + cx + 1] = stemLight;

        // --- blossom: 6x6 with the corners rounded off ---
        int hx = cx - headSize / 2;
        for (int dy = 0; dy < headSize; dy++) {
            for (int dx = 0; dx < headSize; dx++) {
                // Trim corners so the head reads round, not square
                boolean corner = (dx == 0 || dx == headSize - 1)
                              && (dy == 0 || dy == headSize - 1);
                if (corner) continue;

                int x = hx + dx;
                int y = headTop + dy;
                if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) continue;

                // Lighter core, darker rim
                boolean core = dx >= 2 && dx <= 3 && dy >= 2 && dy <= 3;
                out[y * SIZE + x] = 0xFF000000 | (core ? highlight : petal);
            }
        }
        return out;
    }

    private static int[] deadBush(int variant) {
        int[] out = new int[SIZE * SIZE];
        int[] pal = {0x6B5628, 0x7C6530, 0x8A7038};

        for (int branch = 0; branch < 5; branch++) {
            int x = 3 + branch * 2;
            int height = 6 + (int) (hash(branch, 0, variant) * 5);
            int shade = (int) (hash(branch, 1, variant) * pal.length);
            shade = Math.min(pal.length - 1, shade);

            for (int i = 0; i < height; i++) {
                int y = SIZE - 2 - i;
                int bx = x + ((i > height / 2) ? ((branch % 2 == 0) ? 1 : -1) : 0);
                if (bx < 0 || bx >= SIZE || y < 0) continue;
                out[y * SIZE + bx] = 0xFF000000 | pal[shade];
            }
        }
        return out;
    }

    private static int[] cactusSide(int variant) {
        int[] out = new int[SIZE * SIZE];
        int[] pal = {0x0F5A1E, 0x156A26, 0x1B7A2E, 0x228A36};

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int idx;
                if (x == 0 || x == SIZE - 1) {
                    idx = 0;                       // dark edge
                } else {
                    double n = hash(x, y, variant * 13);
                    idx = 1 + (int) (n * (pal.length - 1));
                    idx = Math.min(pal.length - 1, idx);
                }
                out[y * SIZE + x] = 0xFF000000 | pal[idx];
            }
        }
        // Vertical spine ridges
        for (int y = 0; y < SIZE; y++) {
            if (y % 4 != 0) continue;
            out[y * SIZE + 3] = 0xFF0A4A16;
            out[y * SIZE + 12] = 0xFF0A4A16;
        }
        return out;
    }

    private static int[] cactusTop(int variant) {
        int[] out = new int[SIZE * SIZE];
        int[] pal = {0x156A26, 0x1B7A2E, 0x228A36, 0x2A9A3E};

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean rim = x < 2 || y < 2 || x > SIZE - 3 || y > SIZE - 3;
                double n = hash(x, y, variant * 17);
                int idx = rim ? 0 : 1 + (int) (n * (pal.length - 1));
                idx = Math.min(pal.length - 1, idx);
                out[y * SIZE + x] = 0xFF000000 | pal[idx];
            }
        }
        return out;
    }

    private static int[] glass() {
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean border = x == 0 || y == 0 || x == SIZE - 1 || y == SIZE - 1;
                if (border) {
                    out[y * SIZE + x] = 0xFFC8E4F0;
                } else if ((x == 1 && y < 6) || (y == 1 && x < 6)) {
                    out[y * SIZE + x] = 0x60FFFFFF;   // corner glint
                } else {
                    out[y * SIZE + x] = 0x14C8E4F0;
                }
            }
        }
        return out;
    }

    private static int[] water(int variant) {
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double wave = Math.sin((x + y * 0.6) * 0.8) * 0.5 + 0.5;
                double n = hash(x, y, variant * 7) * 0.35 + wave * 0.65;
                int r = 40 + (int) (n * 24);
                int g = 90 + (int) (n * 40);
                int b = 180 + (int) (n * 50);
                out[y * SIZE + x] = 0xB4000000 | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    private static int[] lava(int variant) {
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double n = hash(x / 2, y / 2, variant * 11) * 0.6 + hash(x, y, variant) * 0.4;
                int r = 200 + (int) (n * 55);
                int g = 60 + (int) (n * 110);
                int b = 10 + (int) (n * 30);
                out[y * SIZE + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    private static int[] bedrock(int variant) {
        int[] out = new int[SIZE * SIZE];
        int[] pal = {0x1E1E1E, 0x2C2C2C, 0x3A3A3A, 0x4A4A4A, 0x585858};
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // Blocky clumps rather than fine speckle
                double n = hash(x / 2, y / 2, variant * 3 + 1);
                int idx = (int) (n * pal.length);
                idx = Math.min(pal.length - 1, idx);
                out[y * SIZE + x] = 0xFF000000 | pal[idx];
            }
        }
        return out;
    }

    private static int[] tnt() {
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int c;
                if (y < 4) {
                    // White band with a hint of grain
                    c = hash(x, y, 2) > 0.5 ? 0xF0F0F0 : 0xE2E2E2;
                } else if (y > 11) {
                    c = hash(x, y, 4) > 0.5 ? 0x9A2020 : 0x8A1C1C;
                } else {
                    c = hash(x, y, 6) > 0.5 ? 0xC42B2B : 0xB52626;
                }
                out[y * SIZE + x] = 0xFF000000 | c;
            }
        }
        // Dark separator lines
        for (int x = 0; x < SIZE; x++) {
            out[4 * SIZE + x] = 0xFF6E1414;
            out[11 * SIZE + x] = 0xFF6E1414;
        }
        return out;
    }

    private static int[] craftingTable(int variant) {
        int[] out = planks(variant);
        // Tool grid on the top face
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (x == 0 || y == 0 || x == SIZE - 1 || y == SIZE - 1
                    || x == 7 || x == 8 || y == 7 || y == 8) {
                    out[y * SIZE + x] = 0xFF4A3418;
                }
            }
        }
        return out;
    }

    private static int[] glowstone(int variant) {
        int[] out = new int[SIZE * SIZE];
        int[] pal = {0x8A6A24, 0xB08A30, 0xD4A93E, 0xF0C858, 0xFCE49A};
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double n = hash(x / 2, y / 2, variant * 5) * 0.7 + hash(x, y, variant) * 0.3;
                int idx = (int) (n * pal.length);
                idx = Math.min(pal.length - 1, idx);
                out[y * SIZE + x] = 0xFF000000 | pal[idx];
            }
        }
        return out;
    }

    /**
     * Stone base with mineral blobs. Blobs are clustered rather than single
     * pixels, matching how the originals read at a distance.
     */
    private static int[] ore(int dark, int light, int variant) {
        int[] out = noisy(P_STONE, variant, 2);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // Sample at half resolution so blobs are 2x2-ish
                double n = hash(x / 2, y / 2, variant * 53 + 17);
                if (n > 0.80) {
                    boolean rim = hash(x, y, variant * 3) > 0.55;
                    out[y * SIZE + x] = 0xFF000000 | (rim ? light : dark);
                }
            }
        }
        return out;
    }

    private static int[] metalBlock(int base, int light, int dark) {
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean edge = x == 0 || y == 0 || x == SIZE - 1 || y == SIZE - 1;
                boolean inner = x == 1 || y == 1;
                int c = edge ? dark : inner ? light : base;
                if (!edge && !inner && hash(x, y, 8) > 0.85) c = light;
                out[y * SIZE + x] = 0xFF000000 | c;
            }
        }
        return out;
    }

    /** Fallback: flat colour from the block definition, lightly dithered. */
    private static int[] fromBlockColor(BlockType type, int variant) {
        int base = type == null ? 0x808080 : type.color;
        int r = (base >> 16) & 0xFF, g = (base >> 8) & 0xFF, b = base & 0xFF;

        int[] pal = new int[4];
        for (int i = 0; i < 4; i++) {
            double f = 0.82 + i * 0.09;
            pal[i] = (clamp((int) (r * f)) << 16) | (clamp((int) (g * f)) << 8) | clamp((int) (b * f));
        }
        return noisy(pal, variant, 2);
    }

    // ------------------------------------------------------------------
    // Item sprites (flat, transparent background)
    // ------------------------------------------------------------------

    /**
     * Iron sword sprite in the classic Minecraft style.
     *
     * The blade runs diagonally (top-right to bottom-left) with a lighter
     * centre highlight and darker edges. Gold crossguard, brown grip, dark
     * pommel. Everything outside the sword is fully transparent.
     */
    private static int[] swordSprite() {
        int[] out = new int[SIZE * SIZE]; // transparent by default

        // --- Iron/steel palette ---
        int bladeDark = 0xFF606068;
        int bladeMid = 0xFF9090A0;
        int bladeLight = 0xFFC8C8D8;
        int bladeHighlight = 0xFFE8E8F0;

        // --- Gold guard ---
        int guardDark = 0xFF8A6810;
        int guardMid = 0xFFD4A820;
        int guardLight = 0xFFF0D060;

        // --- Wooden grip ---
        int gripDark = 0xFF4A2C10;
        int gripMid = 0xFF6B4420;
        int gripLight = 0xFF8A5C2C;

        // --- Pommel ---
        int pommel = 0xFF3A2010;

        // Blade: diagonal from (2,13) to (13,2)
        // Center line equation: x + y = 15 (approx)
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // Distance from the diagonal center line x + y = 15
                int dist = Math.abs(x + y - 15);

                // Blade spans dist 0..1 (2px wide), from (1,14) to (14,1)
                if (dist <= 1 && x >= 1 && x <= 14 && y >= 1 && y <= 14) {
                    if (dist == 0) {
                        // Center highlight
                        out[y * SIZE + x] = 0xFF000000 | bladeHighlight;
                    } else {
                        // Edges
                        out[y * SIZE + x] = 0xFF000000 | bladeDark;
                    }
                }

                // Blade tip (top-right corner area) - taper to point
                if (x + y <= 16 && x >= 12 && y <= 3) {
                    if (x + y == 14 || x + y == 15) {
                        out[y * SIZE + x] = 0xFF000000 | bladeLight;
                    }
                }
                if (x >= 13 && y >= 1 && x + y >= 15 && x + y <= 16) {
                    out[y * SIZE + x] = 0xFF000000 | bladeMid;
                }
            }
        }

        // Clean blade: draw a proper 2px-wide diagonal
        for (int i = 1; i <= 14; i++) {
            int x = i, y = 15 - i;
            if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) {
                out[y * SIZE + x] = 0xFF000000 | bladeHighlight;
            }
            // Second pixel offset for width
            int x2 = x + 1;
            if (x2 >= 0 && x2 < SIZE && y >= 0 && y < SIZE) {
                out[y * SIZE + x2] = 0xFF000000 | bladeDark;
            }
        }

        // Gold crossguard: horizontal bar at rows 10-11, cols 4-11
        for (int x = 3; x <= 12; x++) {
            out[10 * SIZE + x] = 0xFF000000 | guardMid;
            out[11 * SIZE + x] = 0xFF000000 | guardDark;
        }
        // Guard highlights at tips
        out[10 * SIZE + 3] = 0xFF000000 | guardLight;
        out[10 * SIZE + 12] = 0xFF000000 | guardLight;

        // Brown grip: rows 12-14, cols 7-8 (centered)
        for (int y = 12; y <= 14; y++) {
            out[y * SIZE + 7] = 0xFF000000 | gripDark;
            out[y * SIZE + 8] = 0xFF000000 | gripMid;
        }
        // Grip highlight
        out[12 * SIZE + 8] = 0xFF000000 | gripLight;
        out[13 * SIZE + 8] = 0xFF000000 | gripLight;

        // Pommel: bottom center
        out[15 * SIZE + 7] = 0xFF000000 | pommel;
        out[15 * SIZE + 8] = 0xFF000000 | pommel;
        out[14 * SIZE + 7] = 0xFF000000 | guardDark;
        out[14 * SIZE + 8] = 0xFF000000 | guardMid;

        return out;
    }

    // --- Generic tool sprite: head + handle, diagonal like Minecraft items ---
    private static int[] toolSprite(int headDark, int headMid, int headLight,
                                     int handleDark, int handleMid, String shape) {
        int[] out = new int[SIZE * SIZE];

        // Handle: vertical strip at x=7-8, rows 8-15
        for (int y = 8; y < SIZE; y++) {
            out[y * SIZE + 7] = 0xFF000000 | handleDark;
            out[y * SIZE + 8] = 0xFF000000 | handleMid;
        }

        switch (shape) {
            case "pickaxe" -> {
                // 3-head pickaxe: horizontal bar at top
                for (int x = 4; x <= 11; x++) {
                    out[2 * SIZE + x] = 0xFF000000 | headMid;
                    out[3 * SIZE + x] = 0xFF000000 | headLight;
                    out[4 * SIZE + x] = 0xFF000000 | headDark;
                }
                // Connect head to handle
                out[5 * SIZE + 7] = 0xFF000000 | headDark;
                out[5 * SIZE + 8] = 0xFF000000 | headDark;
                out[6 * SIZE + 7] = 0xFF000000 | handleMid;
                out[6 * SIZE + 8] = 0xFF000000 | handleDark;
                // Highlight
                out[3 * SIZE + 5] = 0xFF000000 | headLight;
                out[3 * SIZE + 10] = 0xFF000000 | headLight;
            }
            case "axe" -> {
                // Diagonal axe head
                for (int i = 0; i < 6; i++) {
                    int x = 4 + i, y = 2 + i;
                    out[y * SIZE + x] = 0xFF000000 | headMid;
                    if (x + 1 < SIZE) out[y * SIZE + x + 1] = 0xFF000000 | headDark;
                    if (y + 1 < SIZE) out[(y + 1) * SIZE + x] = 0xFF000000 | headLight;
                }
                // Handle connection
                out[7 * SIZE + 7] = 0xFF000000 | headDark;
                out[7 * SIZE + 8] = 0xFF000000 | headDark;
                out[6 * SIZE + 7] = 0xFF000000 | handleMid;
            }
            case "shovel" -> {
                // Rounded shovel head at top
                for (int x = 5; x <= 10; x++) {
                    out[2 * SIZE + x] = 0xFF000000 | headLight;
                    out[3 * SIZE + x] = 0xFF000000 | headMid;
                }
                out[4 * SIZE + 6] = 0xFF000000 | headMid;
                out[4 * SIZE + 7] = 0xFF000000 | headMid;
                out[4 * SIZE + 8] = 0xFF000000 | headMid;
                out[4 * SIZE + 9] = 0xFF000000 | headMid;
                out[5 * SIZE + 7] = 0xFF000000 | headDark;
                out[5 * SIZE + 8] = 0xFF000000 | headDark;
                // Connect
                out[6 * SIZE + 7] = 0xFF000000 | handleMid;
                out[6 * SIZE + 8] = 0xFF000000 | handleDark;
                // Tip highlight
                out[2 * SIZE + 7] = 0xFF000000 | 0xFFFFFF;
            }
            case "hoe" -> {
                // Hoe: horizontal bar top-right
                for (int x = 6; x <= 12; x++) {
                    out[3 * SIZE + x] = 0xFF000000 | headMid;
                    out[4 * SIZE + x] = 0xFF000000 | headDark;
                }
                out[5 * SIZE + 7] = 0xFF000000 | headDark;
                out[6 * SIZE + 7] = 0xFF000000 | handleMid;
                out[6 * SIZE + 8] = 0xFF000000 | handleDark;
                out[3 * SIZE + 7] = 0xFF000000 | headLight;
            }
            case "sword_generic" -> {
                // Diagonal blade from top-right to bottom
                for (int i = 0; i < 10; i++) {
                    int x = 3 + i, y = 12 - i;
                    if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) {
                        out[y * SIZE + x] = 0xFF000000 | headLight;
                        if (x + 1 < SIZE) out[y * SIZE + x + 1] = 0xFF000000 | headMid;
                    }
                }
                // Crossguard
                for (int x = 5; x <= 10; x++) {
                    out[9 * SIZE + x] = 0xFF000000 | headDark;
                    out[10 * SIZE + x] = 0xFF000000 | headDark;
                }
                // Handle
                out[11 * SIZE + 7] = 0xFF000000 | handleDark;
                out[11 * SIZE + 8] = 0xFF000000 | handleMid;
                out[12 * SIZE + 7] = 0xFF000000 | handleDark;
                out[12 * SIZE + 8] = 0xFF000000 | handleMid;
                out[13 * SIZE + 7] = 0xFF000000 | handleDark;
            }
        }

        return out;
    }

    // --- Material sprites ---
    private static int[] stickSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF5A3A18;
        int mid = 0xFF7A5228;
        int light = 0xFF9A6838;
        for (int y = 3; y < SIZE; y++) {
            out[y * SIZE + 7] = 0xFF000000 | dark;
            out[y * SIZE + 8] = 0xFF000000 | mid;
            if (y < 6) out[y * SIZE + 8] = 0xFF000000 | light;
        }
        return out;
    }

    private static int[] ingotSprite(int dark, int mid, int light) {
        int[] out = new int[SIZE * SIZE];
        for (int x = 4; x <= 11; x++) {
            out[5 * SIZE + x] = 0xFF000000 | light;
            out[6 * SIZE + x] = 0xFF000000 | mid;
            out[7 * SIZE + x] = 0xFF000000 | mid;
            out[8 * SIZE + x] = 0xFF000000 | mid;
            out[9 * SIZE + x] = 0xFF000000 | dark;
        }
        // Rounded ends
        out[6 * SIZE + 3] = 0xFF000000 | mid;
        out[7 * SIZE + 3] = 0xFF000000 | mid;
        out[8 * SIZE + 3] = 0xFF000000 | mid;
        out[9 * SIZE + 3] = 0xFF000000 | dark;
        out[6 * SIZE + 12] = 0xFF000000 | mid;
        out[7 * SIZE + 12] = 0xFF000000 | mid;
        out[8 * SIZE + 12] = 0xFF000000 | mid;
        out[9 * SIZE + 12] = 0xFF000000 | dark;
        // Highlight
        out[6 * SIZE + 5] = 0xFF000000 | light;
        return out;
    }

    private static int[] gemSprite(int dark, int mid, int light) {
        int[] out = new int[SIZE * SIZE];
        // Diamond shape
        for (int i = 0; i < 5; i++) {
            int x = 6 - i + i, y = 4 + i;
            int width = i;
            for (int dx = -width; dx <= width; dx++) {
                int px = 7 + dx, py = y;
                if (px >= 0 && px < SIZE && py >= 0 && py < SIZE) {
                    int c = dx == 0 ? light : (Math.abs(dx) == width ? dark : mid);
                    out[py * SIZE + px] = 0xFF000000 | c;
                }
            }
        }
        for (int i = 5; i >= 0; i--) {
            int y = 9 + (5 - i);
            for (int dx = -i; dx <= i; dx++) {
                int px = 7 + dx, py = y;
                if (px >= 0 && px < SIZE && py >= 0 && py < SIZE) {
                    int c = dx == 0 ? light : (Math.abs(dx) == i ? dark : mid);
                    out[py * SIZE + px] = 0xFF000000 | c;
                }
            }
        }
        return out;
    }

    private static int[] coalSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF1A1A1A;
        int mid = 0xFF2A2A2A;
        int light = 0xFF3A3A3A;
        // Irregular blob
        for (int y = 4; y <= 12; y++) {
            for (int x = 4; x <= 11; x++) {
                double n = hash(x, y, 42);
                if (n > 0.3) {
                    int c = n > 0.7 ? light : (n > 0.5 ? mid : dark);
                    out[y * SIZE + x] = 0xFF000000 | c;
                }
            }
        }
        // Scatter some pixels
        out[3 * SIZE + 6] = 0xFF000000 | mid;
        out[4 * SIZE + 5] = 0xFF000000 | dark;
        out[11 * SIZE + 10] = 0xFF000000 | light;
        out[12 * SIZE + 9] = 0xFF000000 | dark;
        return out;
    }

    private static int[] leatherSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF6B3A18;
        int mid = 0xFF8B5A28;
        int light = 0xFFA87238;
        for (int y = 3; y <= 13; y++) {
            for (int x = 3; x <= 13; x++) {
                double n = hash(x, y, 99);
                int c = n > 0.6 ? light : (n > 0.3 ? mid : dark);
                out[y * SIZE + x] = 0xFF000000 | c;
            }
        }
        return out;
    }

    private static int[] stringSprite() {
        int[] out = new int[SIZE * SIZE];
        int c = 0xFFE8E8E8;
        for (int y = 4; y <= 12; y++) {
            int x = 7 + (int)(Math.sin(y * 0.8) * 2);
            if (x >= 0 && x < SIZE) out[y * SIZE + x] = 0xFF000000 | c;
            if (x + 1 < SIZE) out[y * SIZE + x + 1] = 0xFF000000 | c;
        }
        return out;
    }

    private static int[] featherSprite() {
        int[] out = new int[SIZE * SIZE];
        int c = 0xFFE8E8E8;
        int dark = 0xFFC8C8C8;
        for (int y = 2; y <= 14; y++) {
            int x = 7 + (y > 8 ? (y - 8) / 2 : 0);
            if (x >= 0 && x < SIZE) out[y * SIZE + x] = 0xFF000000 | c;
            if (x > 0) out[y * SIZE + x - 1] = 0xFF000000 | dark;
        }
        return out;
    }

    private static int[] flintSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF2A2A2A;
        int mid = 0xFF4A4A4A;
        int light = 0xFF5A5A5A;
        for (int y = 5; y <= 12; y++) {
            for (int x = 4; x <= 10; x++) {
                double n = hash(x, y, 77);
                if (n > 0.3) {
                    int c = n > 0.6 ? light : (n > 0.4 ? mid : dark);
                    out[y * SIZE + x] = 0xFF000000 | c;
                }
            }
        }
        out[4 * SIZE + 7] = 0xFF000000 | mid;
        out[12 * SIZE + 9] = 0xFF000000 | dark;
        return out;
    }

    private static int[] gunpowderSprite() {
        int[] out = new int[SIZE * SIZE];
        int c = 0xFF505050;
        for (int y = 5; y <= 11; y++) {
            for (int x = 4; x <= 11; x++) {
                if (hash(x, y, 33) > 0.4) {
                    out[y * SIZE + x] = 0xFF000000 | c;
                }
            }
        }
        return out;
    }

    private static int[] wheatSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF8A7020;
        int mid = 0xFFB89830;
        int light = 0xFFD8B840;
        // Wheat head at top
        for (int x = 5; x <= 10; x++) {
            out[3 * SIZE + x] = 0xFF000000 | light;
            out[4 * SIZE + x] = 0xFF000000 | mid;
        }
        // Stalk
        for (int y = 5; y <= 14; y++) {
            out[y * SIZE + 7] = 0xFF000000 | dark;
            out[y * SIZE + 8] = 0xFF000000 | mid;
        }
        return out;
    }

    private static int[] eggSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFFC8B898;
        int mid = 0xFFE8D8B8;
        int light = 0xFFF8EED8;
        for (int y = 4; y <= 12; y++) {
            for (int x = 5; x <= 10; x++) {
                int dx = Math.abs(x - 7);
                int dy = Math.abs(y - 8);
                if (dx + dy < 5) {
                    int c = (dx + dy < 2) ? light : (dx + dy < 3 ? mid : dark);
                    out[y * SIZE + x] = 0xFF000000 | c;
                }
            }
        }
        return out;
    }

    // --- Armor sprites ---
    private static int[] armorSprite(int dark, int mid, int light, String type) {
        int[] out = new int[SIZE * SIZE];
        switch (type) {
            case "helmet" -> {
                for (int x = 4; x <= 11; x++) {
                    out[3 * SIZE + x] = 0xFF000000 | light;
                    out[4 * SIZE + x] = 0xFF000000 | mid;
                    out[5 * SIZE + x] = 0xFF000000 | mid;
                    out[6 * SIZE + x] = 0xFF000000 | dark;
                }
                out[7 * SIZE + 5] = 0xFF000000 | dark;
                out[7 * SIZE + 10] = 0xFF000000 | dark;
                out[4 * SIZE + 7] = 0xFF000000 | light;
            }
            case "chestplate" -> {
                for (int x = 4; x <= 11; x++) {
                    out[4 * SIZE + x] = 0xFF000000 | light;
                    out[5 * SIZE + x] = 0xFF000000 | mid;
                    out[6 * SIZE + x] = 0xFF000000 | mid;
                    out[7 * SIZE + x] = 0xFF000000 | mid;
                    out[8 * SIZE + x] = 0xFF000000 | dark;
                }
                // Arm holes
                out[5 * SIZE + 3] = 0xFF000000 | dark;
                out[6 * SIZE + 3] = 0xFF000000 | dark;
                out[5 * SIZE + 12] = 0xFF000000 | dark;
                out[6 * SIZE + 12] = 0xFF000000 | dark;
                out[5 * SIZE + 7] = 0xFF000000 | light;
            }
            case "leggings" -> {
                for (int x = 4; x <= 11; x++) {
                    out[4 * SIZE + x] = 0xFF000000 | light;
                    out[5 * SIZE + x] = 0xFF000000 | mid;
                }
                for (int y = 6; y <= 12; y++) {
                    out[y * SIZE + 5] = 0xFF000000 | mid;
                    out[y * SIZE + 6] = 0xFF000000 | dark;
                    out[y * SIZE + 9] = 0xFF000000 | mid;
                    out[y * SIZE + 10] = 0xFF000000 | dark;
                }
            }
            case "boots" -> {
                for (int x = 4; x <= 11; x++) {
                    out[4 * SIZE + x] = 0xFF000000 | light;
                    out[5 * SIZE + x] = 0xFF000000 | mid;
                }
                for (int y = 6; y <= 11; y++) {
                    out[y * SIZE + 5] = 0xFF000000 | mid;
                    out[y * SIZE + 6] = 0xFF000000 | dark;
                    out[y * SIZE + 9] = 0xFF000000 | mid;
                    out[y * SIZE + 10] = 0xFF000000 | dark;
                }
                out[12 * SIZE + 5] = 0xFF000000 | dark;
                out[12 * SIZE + 6] = 0xFF000000 | dark;
                out[12 * SIZE + 9] = 0xFF000000 | dark;
                out[12 * SIZE + 10] = 0xFF000000 | dark;
            }
        }
        return out;
    }

    // --- Food sprites ---
    private static int[] breadSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF8A5A20;
        int mid = 0xFFB87A30;
        int light = 0xFFD8A050;
        for (int y = 4; y <= 12; y++) {
            for (int x = 3; x <= 12; x++) {
                int dx = Math.abs(x - 7);
                int dy = Math.abs(y - 8);
                if (dx + dy < 6) {
                    int c = (dx + dy < 2) ? light : (dx + dy < 4 ? mid : dark);
                    out[y * SIZE + x] = 0xFF000000 | c;
                }
            }
        }
        return out;
    }

    private static int[] meatSprite(int dark, int mid, int light, boolean cooked) {
        int[] out = new int[SIZE * SIZE];
        for (int y = 4; y <= 12; y++) {
            for (int x = 3; x <= 12; x++) {
                double n = hash(x, y, cooked ? 55 : 22);
                if (n > 0.25) {
                    int c = n > 0.65 ? light : (n > 0.4 ? mid : dark);
                    out[y * SIZE + x] = 0xFF000000 | c;
                }
            }
        }
        return out;
    }

    private static int[] appleSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF8A1010;
        int mid = 0xFFC82020;
        int light = 0xFFE84040;
        for (int y = 4; y <= 12; y++) {
            for (int x = 4; x <= 11; x++) {
                int dx = Math.abs(x - 7);
                int dy = Math.abs(y - 8);
                if (dx * dx + dy * dy < 16) {
                    int c = (dx + dy < 2) ? light : (dx + dy < 4 ? mid : dark);
                    out[y * SIZE + x] = 0xFF000000 | c;
                }
            }
        }
        // Stem
        out[3 * SIZE + 7] = 0xFF000000 | 0xFF4A3018;
        out[2 * SIZE + 7] = 0xFF000000 | 0xFF3A2810;
        // Leaf
        out[3 * SIZE + 8] = 0xFF000000 | 0xFF3A8A2A;
        return out;
    }

    private static int[] bowSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF5A3A18;
        int mid = 0xFF7A5228;
        int light = 0xFF9A6838;
        // Bow curve
        for (int i = 0; i < 12; i++) {
            int x = 4 + i;
            int y = 3 + Math.abs(i - 6) / 2;
            if (y >= 0 && y < SIZE && x >= 0 && x < SIZE) {
                out[y * SIZE + x] = 0xFF000000 | mid;
                if (y + 1 < SIZE) out[(y + 1) * SIZE + x] = 0xFF000000 | dark;
            }
        }
        // String
        for (int y = 3; y <= 13; y++) {
            out[y * SIZE + 10] = 0xFF000000 | 0xFFE8E8E8;
        }
        return out;
    }

    private static int[] arrowSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF5A3A18;
        int mid = 0xFF7A5228;
        // Shaft
        for (int y = 2; y <= 14; y++) {
            out[y * SIZE + 7] = 0xFF000000 | dark;
            out[y * SIZE + 8] = 0xFF000000 | mid;
        }
        // Arrowhead
        for (int i = 0; i < 4; i++) {
            int y = 2 + i;
            for (int dx = -i; dx <= i; dx++) {
                int x = 7 + dx;
                if (x >= 0 && x < SIZE) out[y * SIZE + x] = 0xFF000000 | 0xFFC8C8C8;
            }
        }
        // Fletching
        for (int i = 0; i < 3; i++) {
            int y = 11 + i;
            if (y < SIZE) {
                out[y * SIZE + 5] = 0xFF000000 | 0xFFE8E8E8;
                out[y * SIZE + 6] = 0xFF000000 | 0xFFE8E8E8;
                out[y * SIZE + 9] = 0xFF000000 | 0xFFE8E8E8;
                out[y * SIZE + 10] = 0xFF000000 | 0xFFE8E8E8;
            }
        }
        return out;
    }

    private static int[] torchSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF5A3A18;
        int mid = 0xFF7A5228;
        // Stick
        for (int y = 5; y <= 15; y++) {
            out[y * SIZE + 7] = 0xFF000000 | dark;
            out[y * SIZE + 8] = 0xFF000000 | mid;
        }
        // Flame
        for (int x = 5; x <= 10; x++) {
            out[2 * SIZE + x] = 0xFF000000 | 0xFFF0C020;
            out[3 * SIZE + x] = 0xFF000000 | 0xFFF8D040;
            out[4 * SIZE + x] = 0xFF000000 | 0xFFF8E060;
        }
        out[1 * SIZE + 7] = 0xFF000000 | 0xFFE8A010;
        out[1 * SIZE + 8] = 0xFF000000 | 0xFFF0B020;
        out[3 * SIZE + 6] = 0xFF000000 | 0xFFF8F080;
        out[3 * SIZE + 9] = 0xFF000000 | 0xFFF8F080;
        return out;
    }

    private static int[] rottenFleshSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF5A3A2A;
        int mid = 0xFF7A5A4A;
        int light = 0xFF9A7A6A;
        for (int y = 4; y <= 12; y++) {
            for (int x = 3; x <= 12; x++) {
                double n = hash(x, y, 123);
                if (n > 0.3) {
                    int c = n > 0.65 ? light : (n > 0.45 ? mid : dark);
                    out[y * SIZE + x] = 0xFF000000 | c;
                }
            }
        }
        return out;
    }

    private static int[] boneSprite() {
        int[] out = new int[SIZE * SIZE];
        int c = 0xFFE8E8D8;
        int dark = 0xFFC8C8B8;
        for (int y = 5; y <= 11; y++) {
            out[y * SIZE + 7] = 0xFF000000 | dark;
            out[y * SIZE + 8] = 0xFF000000 | c;
        }
        // Knobs
        for (int x = 5; x <= 10; x++) {
            out[4 * SIZE + x] = 0xFF000000 | c;
            out[5 * SIZE + x] = 0xFF000000 | dark;
            out[11 * SIZE + x] = 0xFF000000 | dark;
            out[12 * SIZE + x] = 0xFF000000 | c;
        }
        return out;
    }

    private static int[] spiderEyeSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF6A1010;
        int mid = 0xFF9A2020;
        int light = 0xFFC84040;
        for (int y = 4; y <= 12; y++) {
            for (int x = 4; x <= 11; x++) {
                int dx = Math.abs(x - 7);
                int dy = Math.abs(y - 8);
                if (dx * dx + dy * dy < 16) {
                    int c = (dx + dy < 2) ? 0xFF101010 : (dx + dy < 4 ? mid : dark);
                    out[y * SIZE + x] = 0xFF000000 | c;
                }
            }
        }
        return out;
    }

    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    /** Stable 0..1 hash. */
    private static double hash(int x, int y, int salt) {
        int h = x * 374761393 + y * 668265263 + salt * 1442695040;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= (h >> 16);
        return (h & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }
}
