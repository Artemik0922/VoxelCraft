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
            case "fire"         -> fire(variant);
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
            case "autumn_leaves" -> leaves(new int[]{0xA04818, 0xB0541F, 0xC86822, 0xD97F26}, variant, false);
            case "cherry_leaves" -> leaves(new int[]{0xDDB0C0, 0xE8B7C8, 0xF2D0DA, 0xF5E0E8}, variant, false);
            case "lavender"      -> flower(0x7E4BB5, 0xE0C8F0, variant); // purple flower
            case "crystal"       -> crystal(variant);
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

            // [WG] World-gen expansion tiles
            case "bamboo"     -> bamboo(variant);
            case "coral"      -> coral(variant);
            case "seagrass"   -> seagrass(variant);
            case "packed_ice" -> noisy(new int[]{0x98C8E8, 0xB0DCF4, 0x80B4D8, 0xC0E8FA}, variant, 2);
            case "mob_spawner" -> spawner(variant);
            case "rails"      -> rails(variant);
            case "chiseled_sandstone" -> noisy(new int[]{0xD8C890, 0xE4D4A0, 0xC8B880, 0xEAD8A8}, variant, 2);
            case "end_portal_frame" -> noisy(new int[]{0x2A5A3A, 0x3A6A4A, 0x1E4A2E, 0x4A7A5A}, variant, 2);
            case "end_portal" -> portal(variant);

            // [GP-020] Doors: closed = solid planks, open = panel with a gap.
            // oak_door_bottom/top are loaded from PNG by default; these are
            // only procedural fallbacks.
            case "oak_door"         -> door(false);
            case "oak_door_open"    -> door(true);
            case "oak_door_bottom"  -> door(false);
            case "oak_door_top"     -> door(false);

            // [SD] Sliding door: PNG tiles are primary; procedural fallback.
            case "sliding_door_bottom" -> door(false);
            case "sliding_door_top"    -> door(false);

            // [BED] Bed furniture: PNG tiles are primary; these fallbacks
            // keep the atlas alive if a pack ever drops the PNGs.
            case "bed_frame"        -> planks(variant);
            case "bed_mattress_side" -> noisy(new int[]{0x8A1E1C, 0x9E2E2C, 0x741412, 0xA83A38}, variant, 3);
            case "bed_mattress_top" -> noisy(new int[]{0x8A1E1C, 0x9E2E2C, 0x741412, 0xA83A38}, variant, 3);
            case "bed_pillow"       -> noisy(new int[]{0xEEEEEC, 0xFAFAF8, 0xE0E0DE, 0xF4F4F2}, variant, 3);
            case "bed_headboard"    -> planks(variant);
            case "bed_footboard"    -> planks(variant);

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

            // --- Improved planks [GP-PLANKS]: 4 species x 8 treatments ---
            case "oak_raw_planks" -> treatedPlanks("oak", "raw", variant);
            case "oak_dried_planks" -> treatedPlanks("oak", "dried", variant);
            case "oak_sealed_planks" -> treatedPlanks("oak", "sealed", variant);
            case "oak_charred_planks" -> treatedPlanks("oak", "charred", variant);
            case "oak_reinforced_planks" -> treatedPlanks("oak", "reinforced", variant);
            case "oak_composite_planks" -> treatedPlanks("oak", "composite", variant);
            case "oak_waxed_planks" -> treatedPlanks("oak", "waxed", variant);
            case "oak_lacquered_planks" -> treatedPlanks("oak", "lacquered", variant);
            case "spruce_raw_planks" -> treatedPlanks("spruce", "raw", variant);
            case "spruce_dried_planks" -> treatedPlanks("spruce", "dried", variant);
            case "spruce_sealed_planks" -> treatedPlanks("spruce", "sealed", variant);
            case "spruce_charred_planks" -> treatedPlanks("spruce", "charred", variant);
            case "spruce_reinforced_planks" -> treatedPlanks("spruce", "reinforced", variant);
            case "spruce_composite_planks" -> treatedPlanks("spruce", "composite", variant);
            case "spruce_waxed_planks" -> treatedPlanks("spruce", "waxed", variant);
            case "spruce_lacquered_planks" -> treatedPlanks("spruce", "lacquered", variant);
            case "birch_raw_planks" -> treatedPlanks("birch", "raw", variant);
            case "birch_dried_planks" -> treatedPlanks("birch", "dried", variant);
            case "birch_sealed_planks" -> treatedPlanks("birch", "sealed", variant);
            case "birch_charred_planks" -> treatedPlanks("birch", "charred", variant);
            case "birch_reinforced_planks" -> treatedPlanks("birch", "reinforced", variant);
            case "birch_composite_planks" -> treatedPlanks("birch", "composite", variant);
            case "birch_waxed_planks" -> treatedPlanks("birch", "waxed", variant);
            case "birch_lacquered_planks" -> treatedPlanks("birch", "lacquered", variant);
            case "jungle_raw_planks" -> treatedPlanks("jungle", "raw", variant);
            case "jungle_dried_planks" -> treatedPlanks("jungle", "dried", variant);
            case "jungle_sealed_planks" -> treatedPlanks("jungle", "sealed", variant);
            case "jungle_charred_planks" -> treatedPlanks("jungle", "charred", variant);
            case "jungle_reinforced_planks" -> treatedPlanks("jungle", "reinforced", variant);
            case "jungle_composite_planks" -> treatedPlanks("jungle", "composite", variant);
            case "jungle_waxed_planks" -> treatedPlanks("jungle", "waxed", variant);
            case "jungle_lacquered_planks" -> treatedPlanks("jungle", "lacquered", variant);

            // --- [GP-PLANKS] Resin and wax: substitute crafting materials ---
            case "resin" -> noisy(new int[]{0x8A5A20, 0xA8723A, 0xC08A4A, 0xE0B060}, variant, 3);
            case "wax" -> noisy(new int[]{0xE8E0C8, 0xF0EAD8, 0xDCD4B8, 0xF8F4E8}, variant, 2);

            // --- [BASE] Base blocks: lanterns, crate, campfire, biome bricks ---
            case "lantern" -> lantern(0xF0E0A0, 0xFFF8D0, variant);
            case "ice_lantern" -> lantern(0x98D0F0, 0xE0F8FF, variant);
            case "crate" -> crate(variant);
            case "campfire" -> campfire(variant);
            case "snow_bricks" -> noisy(new int[]{0xE8EEF2, 0xF2F6F8, 0xD8E2E8, 0xFFFFFF}, variant, 3);
            case "sandstone_bricks" -> noisy(new int[]{0xC8B488, 0xD4C098, 0xB8A478, 0xE0D0A8}, variant, 3);

            // --- [GP-009] Slime block: bouncy translucent green gel ---
            case "slime_block" -> slimeBlock(variant);

            // --- [SPACE] Rocket tower parts ---
            case "rocket_launch_pad" -> rocketPad();
            case "rocket_engine" -> rocketEngine();
            case "rocket_fuel" -> rocketFuel();
            case "rocket_body" -> rocketBody();
            case "rocket_window" -> rocketWindow();
            case "rocket_cone" -> rocketCone();

            // Logs
            case "spruce_log"    -> noisy(new int[]{0x4A3020, 0x5A4030, 0x3A2010, 0x6A5040}, variant, 3);
            case "spruce_log_top" -> noisy(new int[]{0x7A5A3A, 0x8A6A4A, 0x6A4A2A, 0x9A7A5A}, variant, 2);
            case "jungle_log"    -> noisy(new int[]{0x6A4020, 0x7A5030, 0x5A3010, 0x8A6040}, variant, 3);
            case "jungle_log_top" -> noisy(new int[]{0xB08050, 0xC09060, 0xA07040, 0xD0A070}, variant, 2);

            // Leaves
            case "spruce_leaves" -> leaves(new int[]{0x0A3A0A, 0x1A4A1A, 0x2A5A2A, 0x3A6A3A}, variant, false);
            case "jungle_leaves" -> leaves(new int[]{0x2A6A1A, 0x3A7A2A, 0x4A8A3A, 0x5A9A4A}, variant, false);

            // Ores
            case "emerald_ore"   -> ore(0x30E050, 0x50FF70, variant);
            case "redstone_ore"  -> ore(0xE02020, 0xFF4040, variant);
            case "lapis_ore"     -> ore(0x3050E0, 0x5070FF, variant);
            case "copper_ore"    -> ore(0xB87333, 0xD89353, variant);
            // [ECO] Meteorite ore: dark cobalt metal veined through pale stone
            case "meteorite_ore" -> ore(0x5A5F8A, 0x8A90C0, variant);

            // Blocks
            case "emerald_block" -> metalBlock(0x30E050, 0x50FF70, 0x20A030);
            case "emerald_vault" -> metalBlock(0x30E050, 0x58FF80, 0x1F8A30);
            case "white_wool"    -> noisy(new int[]{0xF0F0F0, 0xE8E8E8, 0xF8F8F8, 0xE0E0E0}, variant, 1);
            case "furnace"       -> noisy(new int[]{0x696969, 0x797979, 0x595959, 0x898989}, variant, 2);
            case "furnace_side"  -> noisy(new int[]{0x595959, 0x696969, 0x494949, 0x797979}, variant, 2);
            case "chest"         -> noisy(new int[]{0x8B5A2B, 0x9B6A3B, 0x7B4A1B, 0xAB7A4B}, variant, 2);

            // Crops
            case "wheat"         -> wheatSprite();
            case "carrot"        -> tallGrass(variant);
            case "potato"        -> tallGrass(variant);
            // [CR] Hay bale: solid block in the village barn
            case "hay_bale"      -> hayBaleSide(variant);
            case "hay_bale_top"  -> hayBaleTop(variant);

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
            // [ECO] Meteorite tools: forged cobalt, brighter than iron
            case "meteorite_pickaxe" -> toolSprite(0xFF2A2E44, 0xFF5A5F8A, 0xFF8A90C0, 0xFF5A3A18, 0xFF7A5228, "pickaxe");
            case "meteorite_axe" -> toolSprite(0xFF2A2E44, 0xFF5A5F8A, 0xFF8A90C0, 0xFF5A3A18, 0xFF7A5228, "axe");
            case "meteorite_shovel" -> toolSprite(0xFF2A2E44, 0xFF5A5F8A, 0xFF8A90C0, 0xFF5A3A18, 0xFF7A5228, "shovel");
            case "meteorite_sword" -> toolSprite(0xFF2A2E44, 0xFF5A5F8A, 0xFF8A90C0, 0xFF5A3A18, 0xFF7A5228, "sword_generic");
            case "meteorite_hoe" -> toolSprite(0xFF2A2E44, 0xFF5A5F8A, 0xFF8A90C0, 0xFF5A3A18, 0xFF7A5228, "hoe");

            // --- Materials ---
            case "stick" -> stickSprite();
            case "coal" -> coalSprite();
            case "iron_ingot" -> ingotSprite(0xFF808088, 0xFFB0B0B8, 0xFFD8D8E0);
            case "gold_ingot" -> ingotSprite(0xFFD4A820, 0xFFF0C840, 0xFFFCE060);
            case "diamond" -> gemSprite(0xFF2AA8A0, 0xFF4CC8C0, 0xFF8EF0F8);
            // [ECO] Emeralds and meteorite ingots
            case "emerald" -> gemSprite(0xFF30E050, 0xFF50FF70, 0xFFA8FFC8);
            case "meteorite_ingot" -> ingotSprite(0xFF3A3E5A, 0xFF5A5F8A, 0xFF8A90C0);
            case "leather" -> leatherSprite();
            case "string" -> stringSprite();
            case "feather" -> featherSprite();
            case "flint" -> flintSprite();
            case "flint_and_steel" -> flintAndSteelSprite();
            case "gunpowder" -> gunpowderSprite();
            case "egg" -> eggSprite();

            // --- [ANM] Animal care ---
            case "shears" -> shearsSprite();
            case "bucket" -> bucketSprite();
            case "milk" -> milkSprite();

            // --- [GP-009] Slime ---
            case "slime_ball" -> slimeBallSprite();

            // --- [POT] Potions and brewing ---
            case "water_bottle" -> potionSprite(0xFF80C8F0);
            case "potion_healing" -> potionSprite(0xFFE858A0);
            case "potion_speed" -> potionSprite(0xFF50E0C8);
            case "potion_strength" -> potionSprite(0xFFE05040);
            case "potion_fire_resistance" -> potionSprite(0xFFE89830);
            case "sugar" -> sugarSprite();

            // --- [POT] HUD status-effect icons ---
            case "effect_regeneration" -> effectIconSprite(0xFFE858A0, "heart");
            case "effect_speed" -> effectIconSprite(0xFF50E0C8, "chevron");
            case "effect_strength" -> effectIconSprite(0xFFE05040, "sword");
            case "effect_fire_resistance" -> effectIconSprite(0xFFE89830, "flame");

            // --- [ENCH] Enchanting ---
            case "enchanting_table" -> noisy(new int[]{0x3A1050, 0x4A2060, 0x2A0A40, 0x5A3070}, variant, 3);
            case "enchanting_table_top" -> noisy(new int[]{0x4A2060, 0x5A3070, 0x3A1050, 0x6A4080}, variant, 3);
            case "enchanting_table_item" -> noisy(new int[]{0x4A2060, 0x5A3070, 0x3A1050, 0x6A4080}, variant, 3);
            case "book" -> bookSprite();
            case "lapis_lazuli" -> gemSprite(0xFF3050E0, 0xFF5070FF, 0xFF90A8FF);

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
            // [ECO] Meteorite armour
            case "meteorite_helmet" -> armorSprite(0xFF2A2E44, 0xFF5A5F8A, 0xFF8A90C0, "helmet");
            case "meteorite_chestplate" -> armorSprite(0xFF2A2E44, 0xFF5A5F8A, 0xFF8A90C0, "chestplate");
            case "meteorite_leggings" -> armorSprite(0xFF2A2E44, 0xFF5A5F8A, 0xFF8A90C0, "leggings");
            case "meteorite_boots" -> armorSprite(0xFF2A2E44, 0xFF5A5F8A, 0xFF8A90C0, "boots");

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

            default             -> cropStageTile(tile, type, variant);
        };
    }

    /**
     * [CR] Carrot/potato growth stages: "carrot_stage0".."carrot_stage7"
     * and "potato_stage0".."potato_stage7" are generated per stage, so a
     * field matures visibly. Anything else falls back to block colours.
     */
    private static int[] cropStageTile(String tile, BlockType type, int variant) {
        int sep = tile.lastIndexOf('_');
        if (sep > 0 && tile.startsWith("carrot_stage")) {
            int stage = parseStage(tile, sep);
            if (stage >= 0) return cropStage("carrot", stage, variant);
        }
        if (sep > 0 && tile.startsWith("potato_stage")) {
            int stage = parseStage(tile, sep);
            if (stage >= 0) return cropStage("potato", stage, variant);
        }
        return fromBlockColor(type, variant);
    }

    private static int parseStage(String tile, int sep) {
        try {
            int s = Integer.parseInt(tile.substring(sep + 1));
            return (s >= 0 && s <= 7) ? s : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
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

    /**
     * Slime block: a block of translucent green gel with a darker border and
     * a few glossy highlights, so it reads as sticky and bouncy.
     */
    private static int[] slimeBlock(int variant) {
        int[] out = noisy(new int[]{0x2DD85A, 0x3FF06F, 0x26B84C, 0x57F08A}, variant, 3);
        java.util.Random rnd = new java.util.Random(0x51E + variant * 31);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int edge = Math.min(Math.min(x, SIZE - 1 - x), Math.min(y, SIZE - 1 - y));
                if (edge <= 1) {
                    blendPixel(out, y * SIZE + x, 0x1E, 0x9A, 0x3E, 170);
                }
            }
        }
        for (int h = 0; h < 3; h++) {
            int hx = 2 + rnd.nextInt(SIZE - 6);
            int hy = 2 + rnd.nextInt(SIZE - 6);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int px = hx + dx, py = hy + dy;
                    if (px < 0 || py < 0 || px >= SIZE || py >= SIZE) continue;
                    blendPixel(out, py * SIZE + px, 0xB4, 0xFF, 0xC8, 120);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Specific tiles
    // ------------------------------------------------------------------

    /** [SPACE] Launch pad: armoured plate with braces and corner rivets. */
    private static int[] rocketPad() {
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int c = (((x ^ y) & 1) == 0) ? 0x565E6E : 0x525A68;
                out[y * SIZE + x] = 0xFF000000 | c;
            }
        }
        for (int i = 0; i < SIZE; i++) {
            out[i * SIZE + 0] = 0xFF000000 | 0x8A94A6;
            out[i * SIZE + 15] = 0xFF000000 | 0x8A94A6;
            out[15 * SIZE + i] = 0xFF000000 | 0x98A2B2;
            out[0 * SIZE + i] = 0xFF000000 | 0x48505E;
        }
        for (int i = 2; i < 14; i++) {
            out[7 * SIZE + i] = 0xFF000000 | 0x464E5C;
            out[8 * SIZE + i] = 0xFF000000 | 0x464E5C;
            out[i * SIZE + 7] = 0xFF000000 | 0x464E5C;
            out[i * SIZE + 8] = 0xFF000000 | 0x464E5C;
        }
        rocketRivet(out, 2, 2);
        rocketRivet(out, 13, 2);
        rocketRivet(out, 2, 13);
        rocketRivet(out, 13, 13);
        return out;
    }

    /** [SPACE] Engine block: dark finned casing with a nozzle and flame rim. */
    private static int[] rocketEngine() {
        int[] out = new int[SIZE * SIZE];
        java.util.Random rnd = new java.util.Random(0xE97);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int c = 0x333D49;
                if (y <= 2) c = 0x475360;
                else if (y >= 13) c = 0x2A323C;
                if (rnd.nextInt(4) == 0) c = 0x232A33;
                out[y * SIZE + x] = 0xFF000000 | c;
            }
        }
        for (int y = 3; y <= 12; y++) {
            out[y * SIZE + 5] = 0xFF000000 | 0x1C222A;
            out[y * SIZE + 10] = 0xFF000000 | 0x1C222A;
        }
        for (int x = 5; x <= 10; x++) {
            out[13 * SIZE + x] = 0xFF000000 | 0x14181E;
            out[14 * SIZE + x] = 0xFF000000 | 0x14181E;
        }
        for (int x = 6; x <= 9; x++) {
            out[15 * SIZE + x] = 0xFF000000 | 0x14181E;
        }
        for (int x = 5; x <= 10; x++) out[12 * SIZE + x] = 0xFF000000 | 0xD85A1A;
        for (int x = 6; x <= 9; x++) out[11 * SIZE + x] = 0xFF000000 | 0xE8681A;
        return out;
    }

    /** [SPACE] Fuel tank: pale barrel with straps and rivets. */
    private static int[] rocketFuel() {
        int[] out = new int[SIZE * SIZE];
        java.util.Random rnd = new java.util.Random(0xF02);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int c = 0xC2CBD8;
                if (rnd.nextInt(6) == 0) c = 0xAEB8C6;
                out[y * SIZE + x] = 0xFF000000 | c;
            }
        }
        for (int x = 0; x < SIZE; x++) {
            out[4 * SIZE + x] = 0xFF000000 | 0x8690A0;
            out[11 * SIZE + x] = 0xFF000000 | 0x8690A0;
            out[3 * SIZE + x] = 0xFF000000 | 0x95A0B0;
            out[12 * SIZE + x] = 0xFF000000 | 0x95A0B0;
        }
        for (int x = 3; x <= 12; x += 3) {
            out[4 * SIZE + x] = 0xFF000000 | 0x6E7887;
            out[11 * SIZE + x] = 0xFF000000 | 0x6E7887;
        }
        for (int y = 7; y <= 8; y++)
            for (int x = 7; x <= 8; x++)
                out[y * SIZE + x] = 0xFF000000 | 0x77818F;
        return out;
    }

    /** [SPACE] Hull section: white plating with a specular edge and rivets. */
    private static int[] rocketBody() {
        int[] out = new int[SIZE * SIZE];
        java.util.Random rnd = new java.util.Random(0xB0D);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int c = 0xE2E7EF;
                if (x < 2) c = 0xF2F6FA;
                else if (x >= 13) c = 0xC8CFDA;
                if (rnd.nextInt(5) == 0) c = 0xD6DCE6;
                out[y * SIZE + x] = 0xFF000000 | c;
            }
        }
        for (int x = 0; x < SIZE; x++) out[7 * SIZE + x] = 0xFF000000 | 0xADB6C2;
        for (int x = 2; x <= 13; x += 3) {
            out[2 * SIZE + x] = 0xFF000000 | 0x9AA4B2;
            out[13 * SIZE + x] = 0xFF000000 | 0x9AA4B2;
        }
        return out;
    }

    /** [SPACE] Porthole: hull with a dark circular window and glint. */
    private static int[] rocketWindow() {
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int dx = x - 8, dy = y - 8;
                int d = dx * dx + dy * dy;
                int c;
                if (d < 16) {
                    c = (dy < -1 && Math.abs(dx) < 4) ? 0x4A69A0 : 0x31466E;
                } else if (d <= 25) {
                    c = 0x1C2638;
                } else {
                    c = 0xB9C2D0;
                }
                out[y * SIZE + x] = 0xFF000000 | c;
            }
        }
        out[5 * SIZE + 5] = 0xFF000000 | 0x9FD0F4;
        out[5 * SIZE + 6] = 0xFF000000 | 0x9FD0F4;
        return out;
    }

    /** [SPACE] Nose cone: red wedge that tapers toward the tip. */
    private static int[] rocketCone() {
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < 2; y++) {
            for (int x = 3; x <= 12; x++) {
                out[y * SIZE + x] = 0xFF000000 | 0xC8443A;
            }
        }
        for (int y = 2; y <= 14; y++) {
            int cx = 8;
            int half = (y - 2) * 8 / 12;
            int lo = Math.max(0, cx - half);
            int hi = Math.min(SIZE - 1, cx + half);
            for (int x = 0; x < SIZE; x++) {
                int c = (x >= lo && x <= hi) ? 0xD83830 : 0xE04038;
                out[y * SIZE + x] = 0xFF000000 | c;
            }
            if (lo <= hi) {
                out[y * SIZE + lo] = 0xFF000000 | 0xF4705E;
                out[y * SIZE + hi] = 0xFF000000 | 0xAD2A26;
            }
        }
        out[14 * SIZE + 7] = 0xFF000000 | 0xF2F4F8;
        out[14 * SIZE + 8] = 0xFF000000 | 0xF2F4F8;
        return out;
    }

    /** [SPACE] 2x2 rivet dot helper for the rocket tiles. */
    private static void rocketRivet(int[] out, int cx, int cy) {
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                out[(cy + dy) * SIZE + cx + dx] = 0xFF000000 | 0x9AA4B4;
            }
        }
    }

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

    /**
     * Tree bark: wide vertical ridges of differing browns, wandering dark
     * grooves between them, a knot or two and fine grain noise. Much richer
     * than the old per-pixel column stripes.
     */
    private static int[] logSide(int variant) {
        int[] out = new int[SIZE * SIZE];

        // Bark palette: groove shadow, two mid browns, lit ridge
        int groove = 0x4A3115;
        int[] ridge = {0x6B4A28, 0x7A5730, 0x8A6538, 0x977140, 0xA67E4C};

        // Ridge phase per column: smooth value noise over x gives bands
        // 1-3 px wide that keep their shade down the whole trunk
        int[] band = new int[SIZE];
        double prev = -1;
        int bandIdx = 2;
        for (int x = 0; x < SIZE; x++) {
            double n = hash(x / 2, 0, variant * 23 + 11) * 0.65
                     + hash(x, 0, variant * 23 + 11) * 0.35;
            if (n < prev - 0.13 || n > prev + 0.13) bandIdx = (int) (n * 3.99) % ridge.length;
            band[x] = bandIdx;
            prev = n;
        }

        // One wandering groove line and one knot, both seeded per variant
        int grooveX = 2 + (int) (hash(7, 3, variant * 31 + 5) * (SIZE - 5));
        int knotX = 3 + (int) (hash(11, 5, variant * 17 + 9) * (SIZE - 7));
        int knotY = 3 + (int) (hash(13, 7, variant * 17 + 9) * (SIZE - 7));

        for (int y = 0; y < SIZE; y++) {
            // The groove wanders sideways as it climbs
            int gx = grooveX + (int) Math.round((hash(y / 3, 1, variant * 41) - 0.5) * 3);
            for (int x = 0; x < SIZE; x++) {
                int idx = band[x];

                // Fine grain along the trunk
                double n = hash(x, y, variant) * 0.35;
                idx = (int) (band[x] + n - 0.17 + 0.5);
                idx = Math.max(0, Math.min(ridge.length - 1, idx));

                int c = ridge[idx];

                // Wandering groove: dark core with a soft shoulder
                int d = Math.abs(x - gx);
                if (d == 0) c = groove;
                else if (d == 1 && hash(x, y, variant * 3) > 0.4) c = ridge[0];

                // Knot: dark eye with a lit rim
                int kx = x - knotX, ky = y - knotY;
                int kd = kx * kx * 3 + ky * ky;
                if (kd <= 3) c = groove;
                else if (kd == 4) c = ridge[ridge.length - 1];

                out[y * SIZE + x] = 0xFF000000 | c;
            }
        }
        return out;
    }

    /**
     * Log top: a ring of bark around the faces, then growth rings with
     * wobble, a darker seam every few rings and radial grain.
     */
    private static int[] logTop(int variant) {
        int[] out = new int[SIZE * SIZE];
        double cx = 7.5, cy = 7.5;

        int[] bark = {0x5E4224, 0x6B4A28, 0x7A5730};
        int[] wood = {0x9A7440, 0xA88049, 0xB58C52, 0xC29A60};
        int seam = 0x87642F;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double dx = x - cx, dy = y - cy;
                double r = Math.sqrt(dx * dx + dy * dy);
                double wobble = hash(x, y, variant * 13) * 0.9 - 0.45;

                int c;
                if (r > 7.2) {
                    // Bark rim, using the side palette's mid shades
                    int idx = (int) (hash(x, y, variant) * bark.length);
                    idx = Math.min(bark.length - 1, idx);
                    c = bark[idx];
                } else {
                    int ring = (int) ((r + wobble) / 1.7);
                    c = wood[ring % wood.length];
                    // A dark seam every third ring reads as a year boundary
                    if (ring % 3 == 2) c = seam;
                    // Radial grain: streaks running out from the centre
                    double ang = Math.atan2(dy, dx);
                    if (hash((int) (ang * 4), (int) (r * 2), variant * 7) > 0.82) {
                        c = wood[0];
                    }
                }
                out[y * SIZE + x] = 0xFF000000 | c;
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
     * [GP-PLANKS] Improved planks: the species board plus the treatment's
     * colour ops. The math mirrors tools/PlankTextureGenerator.java so the
     * procedural fallback matches the generated PNGs (same ops, same seeds).
     */
    private static int[] treatedPlanks(String wood, String treatment, int variant) {
        int[] out = switch (wood) {
            case "spruce" -> noisy(new int[]{0x7A5A3A, 0x8A6A4A, 0x6A4A2A, 0x9A7A5A}, variant, 2);
            case "birch" -> noisy(new int[]{0xD8C898, 0xE8D8A8, 0xC8B888, 0xF8E8B8}, variant, 2);
            case "jungle" -> noisy(new int[]{0xB08050, 0xC09060, 0xA07040, 0xD0A070}, variant, 2);
            default -> planks(variant);
        };

        float brightness = 1.0f, saturation = 0.0f;
        int rAdd = 0, gAdd = 0, bAdd = 0;
        float noiseAmount = 0.0f, noiseStrength = 0.0f;
        boolean darkNoise = false;
        switch (treatment) {
            case "raw" -> { brightness = 0.94f; saturation = -0.16f; noiseAmount = 0.04f; noiseStrength = 16.0f; darkNoise = true; }
            case "dried" -> { brightness = 1.05f; saturation = 0.04f; }
            case "sealed" -> { saturation = 0.12f; rAdd = 14; gAdd = 7; bAdd = -5; }
            case "charred" -> { brightness = 0.56f; saturation = -0.64f; noiseAmount = 0.10f; noiseStrength = 30.0f; darkNoise = true; }
            case "reinforced" -> { brightness = 0.95f; saturation = -0.07f; }
            case "composite" -> { brightness = 0.97f; saturation = -0.26f; noiseAmount = 0.07f; noiseStrength = 18.0f; }
            case "waxed" -> { brightness = 1.06f; saturation = 0.06f; rAdd = 8; gAdd = 5; bAdd = -2; }
            case "lacquered" -> { brightness = 1.10f; saturation = 0.15f; rAdd = 4; gAdd = 4; bAdd = 4; }
            default -> { }
        }

        long seed = (wood + ":" + treatment).hashCode();
        for (int i = 0; i < out.length; i++) {
            int argb = out[i];
            int a = (argb >>> 24) & 255;
            if (a == 0) continue;
            float r = ((argb >> 16) & 255) * brightness + rAdd;
            float g = ((argb >> 8) & 255) * brightness + gAdd;
            float b = (argb & 255) * brightness + bAdd;
            r = clampF(r); g = clampF(g); b = clampF(b);
            float gray = 0.299f * r + 0.587f * g + 0.114f * b;
            float sat = 1.0f + saturation;
            r = clampF(gray + (r - gray) * sat);
            g = clampF(gray + (g - gray) * sat);
            b = clampF(gray + (b - gray) * sat);
            out[i] = (a << 24) | ((int) r << 16) | ((int) g << 8) | (int) b;
        }

        if (noiseAmount > 0.0f) {
            java.util.Random rnd = new java.util.Random(seed);
            int max = Math.max(1, (int) noiseStrength);
            for (int i = 0; i < out.length; i++) {
                if (rnd.nextFloat() >= noiseAmount) continue;
                int argb = out[i];
                int a = (argb >>> 24) & 255;
                if (a == 0) continue;
                int delta = darkNoise ? -rnd.nextInt(max) : rnd.nextInt(max);
                int r = clamp(((argb >> 16) & 255) + delta);
                int g = clamp(((argb >> 8) & 255) + delta);
                int b = clamp((argb & 255) + delta);
                out[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }

        java.util.Random overlay = new java.util.Random(seed ^ 0x5DEECE66DL);
        switch (treatment) {
            case "reinforced" -> {
                borderOverlay(out, 130, 130, 130, 120, 1);
                rivets(out, 230, 230, 230, 210);
            }
            case "sealed" -> speckles(out, overlay, 235, 190, 90, 28, 0.012f);
            case "composite" -> speckles(out, overlay, 190, 190, 190, 38, 0.02f);
            case "charred" -> speckles(out, overlay, 0, 0, 0, 35, 0.03f);
            default -> { }
        }
        return out;
    }

    private static float clampF(float v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    /** Random per-pixel colour speckles (mirrors PlankTextureGenerator). */
    private static void speckles(int[] px, java.util.Random rnd, int r, int g, int b, int a, float amount) {
        for (int i = 0; i < px.length; i++) {
            if (rnd.nextFloat() < amount) {
                blendPixel(px, i, r, g, b, a);
            }
        }
    }

    /** Iron frame around the tile edge (mirrors addBorderOverlay). */
    private static void borderOverlay(int[] px, int r, int g, int b, int a, int thickness) {
        int t = Math.max(0, thickness);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (x < t || y < t || x >= SIZE - t || y >= SIZE - t) {
                    blendPixel(px, y * SIZE + x, r, g, b, a);
                }
            }
        }
    }

    /** Corner + edge rivets (mirrors addRivets). */
    private static void rivets(int[] px, int r, int g, int b, int a) {
        int[][] points = {
            {1, 1}, {SIZE - 2, 1}, {1, SIZE - 2}, {SIZE - 2, SIZE - 2},
            {SIZE / 2, 1}, {SIZE / 2, SIZE - 2}, {1, SIZE / 2}, {SIZE - 2, SIZE / 2}
        };
        for (int[] p : points) {
            blendPixel(px, p[1] * SIZE + p[0], r, g, b, a);
        }
    }

    /** Straight alpha blend onto one pixel (mirrors blendPixel). */
    private static void blendPixel(int[] px, int i, int r, int g, int b, int a) {
        if (i < 0 || i >= px.length || a <= 0) return;
        int dst = px[i];
        int da = (dst >>> 24) & 255;
        if (da == 0) {
            px[i] = (a << 24) | (r << 16) | (g << 8) | b;
            return;
        }
        float af = a / 255.0f;
        int dr = (dst >> 16) & 255;
        int dg = (dst >> 8) & 255;
        int db = dst & 255;
        int nr = clamp((int) (r * af + dr * (1.0f - af)));
        int ng = clamp((int) (g * af + dg * (1.0f - af)));
        int nb = clamp((int) (b * af + db * (1.0f - af)));
        int na = Math.max(da, a);
        px[i] = (na << 24) | (nr << 16) | (ng << 8) | nb;
    }

    /**
     * [GP-020] Oak door tile. Closed: a solid plank door with a handle and
     * board seams. Open: two framed panels with a transparent gap, so the
     * shader's alpha test cuts everything outside the panels.
     */
    private static int[] door(boolean open) {
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean inPanel = open
                    ? (x >= 1 && x <= 6) || (x >= 9 && x <= 14)
                    : true;
                if (!inPanel) continue; // transparent gap

                int color;
                if (open && (x == 1 || x == 6 || x == 9 || x == 14)) {
                    color = 0xFF4A311A; // panel frame
                } else if (!open && (x % 4 == 0 || y == 0 || y == 15)) {
                    color = 0xFF4A311A; // board seams + frame
                } else {
                    double n = hash(x, y, 41) * 0.5 + hash(x * 3, y * 3, 17) * 0.5;
                    int idx = (int) (n * P_PLANK.length);
                    idx = Math.min(P_PLANK.length - 1, idx);
                    color = 0xFF000000 | P_PLANK[idx];
                }

                // Handle: small dark knob on the opening side
                if (open ? (x == 10 && (y == 7 || y == 8))
                         : (x == 12 && (y == 7 || y == 8))) {
                    color = 0xFF3A2A18;
                }

                out[y * SIZE + x] = color;
            }
        }
        return out;
    }

    /**
     * Leaves: overlapping sprigs stamped around the tile, separated by real
     * gaps, so the canopy reads as clusters of foliage instead of TV static.
     *
     * @param cutout true for Fancy, punching real holes; false for Fast,
     *               where the gaps become dark foliage instead so the tile
     *               stays fully opaque
     */
    private static int[] leaves(int variant, boolean cutout) {
        return leaves(P_LEAF, variant, cutout);
    }

    /** Sprig-based foliage in an arbitrary darkest-to-lightest palette. */
    private static int[] leaves(int[] palette, int variant, boolean cutout) {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF000000 | palette[0];

        // Coarse gap field: where sprigs refuse to grow, giving clumps room.
        // High threshold: vanilla canopies read as a near-solid mass with
        // only scattered peep holes, not as sparse cards
        boolean[] gap = new boolean[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double coarse = hash(x / 2, y / 2, variant * 37 + 19) * 0.7
                              + hash(x, y, variant * 11 + 3) * 0.3;
                gap[y * SIZE + x] = coarse > 0.86;
            }
        }

        // Stamp 24 small leaf sprigs, wrapping around the tile edges so the
        // texture stays seamless when the canopy repeats. Small + many keeps
        // the grain fine, like the busy vanilla leaf texture
        for (int i = 0; i < 24; i++) {
            int cx = (int) (hash(i, 1, variant * 53 + 7) * SIZE);
            int cy = (int) (hash(i, 2, variant * 53 + 7) * SIZE);
            double r = 1.3 + hash(i, 3, variant * 53 + 7) * 1.1;
            int reach = (int) r + 1;

            for (int dy = -reach; dy <= reach; dy++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    double d2 = dx * dx + dy * dy;
                    if (d2 > r * r + 0.4) continue;

                    int x = (cx + dx + SIZE) % SIZE;
                    int y = (cy + dy + SIZE) % SIZE;
                    if (gap[y * SIZE + x]) continue;

                    // Light towards the sprig centre, dark on the rim,
                    // with per-pixel jitter so no two leaves match
                    double t = Math.sqrt(d2) / (r + 0.001);
                    double n = hash(x, y, variant * 5 + 1) * 1.4 - 0.7;
                    int idx = (int) ((1.0 - t) * (palette.length - 1) + n + 0.5);
                    idx = Math.max(0, Math.min(palette.length - 1, idx));
                    out[y * SIZE + x] = 0xFF000000 | palette[idx];
                }
            }
        }

        // Uncovered pixels: real holes in Fancy, dark foliage in Fast
        for (int i = 0; i < out.length; i++) {
            if (out[i] == 0) out[i] = cutout ? 0x00000000 : dark;
        }

        // A few bright singles catching the light on top of the clumps
        for (int i = 0; i < 6; i++) {
            int x = (int) (hash(i, 9, variant * 71 + 3) * SIZE);
            int y = (int) (hash(i, 11, variant * 71 + 3) * SIZE);
            if (out[y * SIZE + x] != dark) {
                out[y * SIZE + x] = 0xFF000000 | palette[palette.length - 1];
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

    // [CR] Crops are not biome-tinted (BiomeColors.isFoliage skips them),
    // so these use real greens and root colours.

    /**
     * One crop growth stage: leaves fan out from the ground, the tuft grows
     * taller and denser with the stage, and the root (carrot orange / potato
     * brown) pokes above the soil from stage 5 on.
     */
    private static int[] cropStage(String crop, int stage, int variant) {
        int[] out = new int[SIZE * SIZE];
        boolean carrot = "carrot".equals(crop);
        int[] leaf = carrot ? new int[]{0x3A7418, 0x4E942A, 0x2C5C12, 0x62AE3A}
                            : new int[]{0x3A6A28, 0x4E8836, 0x2A541C, 0x5EA24A};
        int[] root = carrot ? new int[]{0xE07820, 0xF09038, 0xC86018}
                            : new int[]{0x9A6A3A, 0xB08450, 0x7A522C};

        if (stage == 0) {
            // Пара крошечных ростков
            for (int i = 0; i < 2; i++) {
                int x = 6 + i * 3 + (int) (hash(i, 0, variant) * 2);
                if (x >= SIZE) continue;
                out[(SIZE - 1) * SIZE + x] = 0xFF000000 | leaf[1];
                out[(SIZE - 2) * SIZE + x] = 0xFF000000 | leaf[0];
                if (x + 1 < SIZE) out[(SIZE - 2) * SIZE + x + 1] = 0xFF000000 | leaf[0];
            }
            return out;
        }

        // Пучок листьев: число и высота растут со стадией
        int blades = 2 + stage;
        int height = Math.min(SIZE - 1, 3 + stage * 2);
        for (int b = 0; b < blades; b++) {
            int rootX = 1 + (int) (hash(b, 1, variant) * (SIZE - 4));
            int tip = rootX + (int) (hash(b, 2, variant) * 3) - 1;
            int bh = height - (int) (hash(b, 3, variant) * 3);
            if (bh <= 1) bh = 1;
            int shade = (int) (hash(b, 4, variant) * leaf.length);
            shade = Math.min(leaf.length - 1, shade);
            for (int i = 0; i < bh; i++) {
                int y = SIZE - 1 - i;
                if (y < 0) break;
                int x = rootX + Math.round((tip - rootX) * (i / (float) bh));
                if (x < 0 || x >= SIZE) continue;
                int s = (i > bh - 3) ? Math.min(leaf.length - 1, shade + 1) : shade;
                out[y * SIZE + x] = 0xFF000000 | leaf[s];
            }
        }

        // Корнеплод у земли: пробивается на 5-й стадии, полностью на 7-й
        if (stage >= 5) {
            int grow = (stage - 4) * 2;
            int cx = 7 + (int) (hash(9, 0, variant) * 2);
            for (int i = 0; i < grow; i++) {
                int y = SIZE - 1 - i;
                if (y < 0) break;
                int half = 1 + i / 2;
                for (int x = cx - half; x <= cx + half; x++) {
                    if (x < 0 || x >= SIZE) continue;
                    int s = (int) (hash(x, y, variant + 3) * root.length);
                    s = Math.min(root.length - 1, s);
                    out[y * SIZE + x] = 0xFF000000 | root[s];
                }
            }
        }
        return out;
    }

    /** Hay bale side: horizontal straw bands with darker stem ticks. */
    private static int[] hayBaleSide(int variant) {
        int[] pal = {0xB89838, 0xC8A848, 0xD8B858, 0xE8C868};
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            double band = Math.sin(y * 1.1) * 0.5 + 0.5;
            for (int x = 0; x < SIZE; x++) {
                double n = hash(x, y, variant * 13 + y / 3) * 0.45 + band * 0.55;
                int idx = Math.min(pal.length - 1, (int) (n * pal.length));
                out[y * SIZE + x] = 0xFF000000 | pal[idx];
            }
        }
        // Тёмные штрихи стеблей
        for (int y = 0; y < SIZE; y++) {
            int x = (y % 2 == 0) ? 5 : 10;
            if (hash(y, 7, variant) > 0.45) out[y * SIZE + x] = 0xFF9A7C20;
        }
        return out;
    }

    /** Hay bale top: woven straw, diagonal bands. */
    private static int[] hayBaleTop(int variant) {
        int[] pal = {0xB89838, 0xC8A848, 0xD8B858, 0xE8C868};
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int cell = ((x + y) / 4) % 2;
                double n = hash(x, y, variant * 5) * 0.3;
                int idx = Math.min(pal.length - 1, (int) (cell * 2 + n * 2));
                out[y * SIZE + x] = 0xFF000000 | pal[idx];
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

    /** Bamboo: jointed green stalk, narrow and tall, transparent. */
    private static int[] bamboo(int variant) {
        int[] out = new int[SIZE * SIZE];
        int[] pal = {0x4E8A2A, 0x5EA03A, 0x3E701E, 0x6EB44A};
        int cx = 7 + (int) (hash(0, 0, variant) * 2);
        int sway = (int) (hash(1, 0, variant) * 2);

        // Stalk: 2px wide with a 1px gap for joints every 4 rows
        for (int y = 0; y < SIZE; y++) {
            int x = cx + Math.round(sway * (y / 16.0f));
            int shade = (int) (hash(2, y / 4, variant) * pal.length);
            shade = Math.min(pal.length - 1, shade);
            out[y * SIZE + x] = 0xFF000000 | pal[shade];
            if (x + 1 < SIZE) out[y * SIZE + x + 1] = 0xFF000000 | pal[Math.min(pal.length - 1, shade + 1)];
            // Joint rings
            if (y % 4 == 0 && y > 0) {
                if (x - 1 >= 0) out[y * SIZE + x - 1] = 0xFF2E5414;
                if (x + 2 < SIZE) out[y * SIZE + x + 2] = 0xFF2E5414;
            }
        }
        // A few leaves at the top
        for (int i = 0; i < 4; i++) {
            int y = SIZE - 2 - i * 3;
            int side = (i % 2 == 0) ? 1 : -1;
            if (y >= 0 && y < SIZE) {
                int lx = cx + side * (i + 1);
                if (lx >= 0 && lx < SIZE) out[y * SIZE + lx] = 0xFF3E701E;
                if (y - 1 >= 0 && lx + side >= 0 && lx + side < SIZE)
                    out[(y - 1) * SIZE + lx + side] = 0xFF4E8A2A;
            }
        }
        return out;
    }

    /** Coral: branching fan rising from the bottom, transparent. */
    private static int[] coral(int variant) {
        int[] out = new int[SIZE * SIZE];
        int[][] palettes = {
            {0xE05A5A, 0xF07A6A, 0xC03E3E},   // tube coral
            {0x5AA0E8, 0x7ABCF4, 0x3E82C8},   // brain coral
            {0xC07AE0, 0xD89AF4, 0xA25EC8},   // fire coral
            {0xE8D060, 0xF4E080, 0xC8B04A},   // horn coral
        };
        int[] pal = palettes[(variant + (int) hash(0, 1, variant) * 3) % palettes.length];
        int rootX = 7 + (int) (hash(0, 2, variant) * 3);

        for (int branch = 0; branch < 5; branch++) {
            int bx = rootX + (branch - 2) * 2;
            int height = 7 + (int) (hash(branch, 3, variant) * 4);
            int shade = (int) (hash(branch, 4, variant) * pal.length);
            shade = Math.min(pal.length - 1, shade);
            for (int i = 0; i < height; i++) {
                int y = SIZE - 1 - i;
                if (y < 0 || bx < 0 || bx >= SIZE) continue;
                int w = (i == 0) ? 2 : 1;   // thicken the base
                for (int ww = 0; ww < w; ww++) {
                    int x = bx + ww - (w - 1) / 2;
                    if (x >= 0 && x < SIZE) out[y * SIZE + x] = 0xFF000000 | pal[shade];
                }
            }
            // Tip knob
            int ty = SIZE - height;
            if (ty >= 0 && bx >= 0 && bx < SIZE) {
                out[ty * SIZE + bx] = 0xFF000000 | pal[Math.min(pal.length - 1, shade + 1)];
            }
        }
        return out;
    }

    /** Seagrass: tall grass blades with an underwater palette. */
    private static int[] seagrass(int variant) {
        int[] out = new int[SIZE * SIZE];
        int[] pal = {0x3E7A4A, 0x4A8E5A, 0x2E6638, 0x5AA26C};
        for (int blade = 0; blade < 5; blade++) {
            int rootX = 1 + blade * 3 + (int) (hash(blade, 0, variant) * 2);
            if (rootX >= SIZE - 1) continue;
            int height = 6 + (int) (hash(blade, 1, variant) * 8);
            float curve = (float) (hash(blade, 2, variant) - 0.5) * 5.0f;
            int shade = (int) (hash(blade, 3, variant) * pal.length);
            shade = Math.min(pal.length - 1, shade);
            for (int i = 0; i < height; i++) {
                int y = SIZE - 1 - i;
                if (y < 0) continue;
                float t = i / (float) height;
                int x = rootX + Math.round(curve * t * t);
                if (x < 0 || x >= SIZE) continue;
                int s = (i > height - 3) ? Math.min(pal.length - 1, shade + 1) : shade;
                out[y * SIZE + x] = 0xFF000000 | pal[s];
            }
        }
        return out;
    }

    /** Spawner cage: dark noisy shell with a burning core. */
    private static int[] spawner(int variant) {
        int[] out = new int[SIZE * SIZE];
        int[] shell = {0x2A2A2A, 0x3A3A3A, 0x1A1A1A, 0x4A4A4A};
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int s = (int) (hash(x, y, variant) * shell.length);
                s = Math.min(shell.length - 1, s);
                out[y * SIZE + x] = 0xFF000000 | shell[s];
            }
        }
        // Orange molten core
        int[] core = {0xFF8A30, 0xFFA850, 0xFF6A10, 0xFFC060};
        for (int dy = 4; dy < 12; dy++) {
            for (int dx = 3; dx < 13; dx++) {
                boolean edge = dx <= 4 || dx >= 11 || dy <= 5 || dy >= 10;
                int s = (int) (hash(dx, dy, variant) * core.length);
                s = Math.min(core.length - 1, s);
                out[dy * SIZE + dx] = 0xFF000000 | core[edge ? s : Math.min(core.length - 1, s + 1)];
            }
        }
        return out;
    }

    /** Rails: two horizontal strips near the bottom, transparent. */
    private static int[] rails(int variant) {
        int[] out = new int[SIZE * SIZE];
        int[] pal = {0x6A4A2A, 0x7A5A3A, 0x5A3A1E};
        for (int y = SIZE - 3; y < SIZE - 1; y++) {
            for (int x = 0; x < SIZE; x++) {
                int s = (int) (hash(x, y, variant) * pal.length);
                s = Math.min(pal.length - 1, s);
                out[y * SIZE + x] = 0xFF000000 | pal[s];
            }
        }
        // Sleepers
        for (int x = 1; x < SIZE; x += 4) {
            for (int y = SIZE - 4; y < SIZE - 1; y++) {
                out[y * SIZE + x] = 0xFF3E2A12;
            }
        }
        return out;
    }

    /** End portal: swirling void with bright star bursts. */
    private static int[] portal(int variant) {
        int[] out = new int[SIZE * SIZE];
        int[] dark = {0x0E0E1A, 0x1A1A2E, 0x080812, 0x262640};
        int[] bright = {0x40E0FF, 0x20C0E8, 0x70F0FF};
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float swirl = (float) Math.sin(x * 0.9 + y * 0.5 + variant) * (float) Math.cos(y * 0.8 - x * 0.4);
                if (swirl > 0.82) {
                    int s = (int) (hash(x, y, variant) * bright.length);
                    s = Math.min(bright.length - 1, s);
                    out[y * SIZE + x] = 0xFF000000 | bright[s];
                } else {
                    int s = (int) (hash(x, y, variant) * dark.length);
                    s = Math.min(dark.length - 1, s);
                    out[y * SIZE + x] = 0xFF000000 | dark[s];
                }
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

    /** Crystal shard: a faceted gem standing on the block below. */
    private static int[] crystal(int variant) {
        int[] out = new int[SIZE * SIZE];
        int[] pal = {0x3A1A5E, 0x5E2A8C, 0x7E3ABE, 0x9B59D0, 0xC99BF0};

        // Apex near the top of the cell, prism widening toward the base
        int top = 1 + (int) (hash(0, 0, variant) * 3);
        int apexX = 6 + (variant % 3) + (variant / 3);
        int lean = ((variant & 1) == 0) ? 1 : -1;
        int baseHalf = 3 + (variant % 2);

        for (int y = top; y <= SIZE - 2; y++) {
            float t = (y - top) / (float) (SIZE - 1 - top);
            int half = 1 + (int) (t * baseHalf);
            int cx = apexX + lean * (int) (t * 2);

            for (int dx = -half; dx <= half; dx++) {
                int x = cx + dx;
                if (x < 0 || x >= SIZE) continue;
                int c;
                if (dx <= 0 && dx >= -half / 2) c = pal[4];   // lit facet
                else if (dx < 0) c = pal[3];
                else if (dx < half - 1) c = pal[2];
                else c = pal[0];                               // dark rim
                // Occasional dark internal striation
                if (dx == -half / 2 && ((y + variant) & 1) == 0) c = pal[1];
                out[y * SIZE + x] = 0xFF000000 | c;
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

    /**
     * Vanilla glass: a thin frame and two diagonal glints, everything else
     * fully transparent so windows read as clear panes, not frosted blocks.
     */
    private static int[] glass() {
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean border = x == 0 || y == 0 || x == SIZE - 1 || y == SIZE - 1;
                if (border) {
                    // Frame: pale glass edge, slightly shaded at the corners
                    boolean corner = (x == 0 || x == SIZE - 1) && (y == 0 || y == SIZE - 1);
                    out[y * SIZE + x] = corner ? 0xFFB8D8E0 : 0xFFDCEFF4;
                } else if ((x - y == 3 && x < 11) || (x - y == 4 && x < 10)) {
                    out[y * SIZE + x] = 0x66FFFFFF;   // diagonal glint
                } else {
                    out[y * SIZE + x] = 0x00000000;   // clear pane
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

    private static int[] fire(int variant) {
        int[] out = new int[SIZE * SIZE];

        // Palette: base of the flame is dark red, tip is near-white yellow
        int baseC = 0xE05010;
        int midC = 0xF07818;
        int topC = 0xF8A020;
        int tipC = 0xFFE870;

        for (int x = 0; x < SIZE; x++) {
            // Each column is an independent flame strip: height varies
            // per column + variant so the shape is not a symmetric cone.
            double h = hash(x, variant * 31 + 7, 13);
            int flameH = 7 + (int) (h * 4);          // 7..10 px tall
            int topY = SIZE - flameH;                // flame top row

            double lean = (hash(x, variant * 17 + 3, 5) - 0.5) * 0.5; // 0..0.5 skew

            for (int y = topY; y < SIZE; y++) {
                int row = y - topY;                  // 0 at the tip
                double taper = row / (double) flameH;
                double width = 0.7 + taper * 3.2 + h * 0.8;
                int halfW = (int) Math.ceil(width / 2.0);
                int cx = SIZE / 2 + (int) (lean * row); // tips lean sideways

                for (int dx = -halfW; dx <= halfW; dx++) {
                    int px = cx + dx;
                    if (px < 0 || px >= SIZE) continue;

                    // Jitter the rim so the flame is ragged, not smooth
                    double j = hash(px, y, variant * 23 + 11);
                    if (j < 0.10) continue;

                    // Colour by height: darker at the base, brighter at the tip
                    int c;
                    if (row < 2) c = tipC;
                    else if (row < flameH * 0.35) c = topC;
                    else if (row < flameH * 0.7) c = midC;
                    else c = baseC;
                    // Heat shimmer: occasionally pick a neighbouring shade
                    if (j > 0.92) c = (row < flameH * 0.5) ? tipC : midC;

                    out[y * SIZE + px] = 0xFF000000 | c;
                }
            }
        }

        // Embers: bright single pixels floating just above the flames
        for (int i = 0; i < 5 + variant; i++) {
            int ex = (int) (hash(i, variant * 41, 3) * SIZE);
            int ey = (int) (hash(i, variant * 43, 4) * (SIZE / 3));
            out[ey * SIZE + ex] = 0xFF000000 | tipC;
        }
        return out;
    }

    // [BASE] A hanging lantern: metal frame around a glowing glass core.
    // glassC is the dark glass tint, glowC the bright centre.
    private static int[] lantern(int glassC, int glowC, int variant) {
        int[] out = new int[SIZE * SIZE];
        int frame = 0xFF3A3A3E;
        int frameDark = 0xFF2A2A2E;
        int frameLight = 0xFF56565C;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // Frame: outer ring, corner posts and a cross at top/bottom
                boolean ring = (x <= 1 || x >= SIZE - 2 || y <= 1 || y >= SIZE - 2);
                boolean post = (x == 3 || x == SIZE - 4) && (y >= 2 && y < SIZE - 2);
                if (ring || post) {
                    out[y * SIZE + x] = (x <= 1 || x >= SIZE - 2) ? frameDark : frame;
                    continue;
                }
                // Glass core with a hot centre and a glass rim
                int dx = x - SIZE / 2, dy = y - SIZE / 2;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < 4.2) {
                    if (d < 1.6) out[y * SIZE + x] = 0xFF000000 | glowC;
                    else if (d < 2.6) out[y * SIZE + x] = 0xFF000000 | glassC;
                    else out[y * SIZE + x] = 0xFF000000 | (glassC & 0xFEFEFE);
                } else if (d < 5.2) {
                    out[y * SIZE + x] = frameLight; // glass edge rim
                }
            }
        }
        // Little hanger nub on top
        out[0 * SIZE + SIZE / 2] = frameDark;
        out[1 * SIZE + SIZE / 2] = frameDark;
        return out;
    }

    /** A shipping crate: banded wood slats with a diagonal brace. */
    private static int[] crate(int variant) {
        int[] out = new int[SIZE * SIZE];
        int[] wood = {0x6B4A2A, 0x7A5A3A, 0x5A3A1A, 0x8A6A4A};
        for (int y = 0; y < SIZE; y++) {
            boolean band = (y % 8 == 0 || y % 8 == 7);
            for (int x = 0; x < SIZE; x++) {
                double n = hash(x, y, variant * 7 + 3);
                if (band) {
                    out[y * SIZE + x] = 0xFF000000 | ((n > 0.5) ? 0x9A7A52 : 0x8A6A42);
                } else {
                    int idx = (int) (n * wood.length);
                    idx = Math.min(wood.length - 1, idx);
                    out[y * SIZE + x] = 0xFF000000 | wood[idx];
                }
            }
        }
        // Diagonal brace planks
        for (int i = -SIZE; i < SIZE * 2; i++) {
            int x = i, y = i + 4;
            if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) out[y * SIZE + x] = 0xFF5A4226;
            x = i; y = i - 4;
            if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) out[y * SIZE + x] = 0xFF6A4E2E;
        }
        return out;
    }

    /**
     * A campfire: two crossed logs under a rising flame. Transparent
     * background so it reads as a crossed-quad sprite.
     */
    private static int[] campfire(int variant) {
        int[] out = new int[SIZE * SIZE];
        // Logs at the base (solid, opaque)
        for (int d = -2; d <= 2; d++) {
            for (int p = -SIZE; p < SIZE * 2; p++) {
                int x = p, y = p + d + 10;
                if (x >= 0 && x < SIZE && y >= 12 && y < SIZE) {
                    double n = hash(x, y, variant * 11 + 5);
                    out[y * SIZE + x] = 0xFF000000 | ((n > 0.5) ? 0x6A4226 : 0x7A522E);
                }
                int x2 = SIZE - 1 - p, y2 = p + d + 10;
                if (x2 >= 0 && x2 < SIZE && y2 >= 12 && y2 < SIZE) {
                    double n = hash(x2, y2, variant * 11 + 5);
                    out[y2 * SIZE + x2] = 0xFF000000 | ((n > 0.5) ? 0x6A4226 : 0x7A522E);
                }
            }
        }
        // Flame above the logs (transparent background)
        int baseC = 0xE05010, midC = 0xF07818, topC = 0xF8A020, tipC = 0xFFE870;
        int flameH = 9 + (variant % 3);
        for (int x = 3; x < SIZE - 3; x++) {
            double h = hash(x, variant * 31 + 7, 13);
            int colH = flameH + (int) (h * 3);
            int topY = SIZE - colH;
            double lean = (hash(x, variant * 17 + 3, 5) - 0.5) * 0.6;
            for (int y = topY; y < SIZE - 2; y++) {
                int row = y - topY;
                double taper = row / (double) colH;
                double width = 0.6 + taper * 2.6 + h * 0.6;
                int halfW = (int) Math.ceil(width / 2.0);
                int cx = SIZE / 2 + (int) (lean * row);
                for (int dx = -halfW; dx <= halfW; dx++) {
                    int px = cx + dx;
                    if (px < 0 || px >= SIZE) continue;
                    double j = hash(px, y, variant * 23 + 11);
                    if (j < 0.10) continue;
                    int c;
                    if (row < 2) c = tipC;
                    else if (row < colH * 0.35) c = topC;
                    else if (row < colH * 0.7) c = midC;
                    else c = baseC;
                    out[y * SIZE + px] = 0xFF000000 | c;
                }
            }
        }
        // Embers
        for (int i = 0; i < 4 + variant; i++) {
            int ex = (int) (hash(i, variant * 41, 3) * SIZE);
            int ey = (int) (hash(i, variant * 43, 4) * (SIZE / 2));
            out[ey * SIZE + ex] = 0xFF000000 | tipC;
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

    /** [ENCH] Closed book: dark red cover with a pale page band. */
    private static int[] bookSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF6B1F1F;
        int mid = 0xFF8A2F2F;
        int light = 0xFFA84545;
        int page = 0xFFE8E0D0;
        for (int y = 3; y <= 12; y++) {
            for (int x = 4; x <= 11; x++) {
                int c;
                if (x == 5 || x == 10) {
                    c = dark;
                } else if (y == 4 || y == 11) {
                    c = dark;
                } else if (x == 7) {
                    c = page;
                } else {
                    c = (x + y) % 2 == 0 ? mid : light;
                }
                out[y * SIZE + x] = 0xFF000000 | c;
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

    /** Flint and steel: dark flint nodule with a bright steel striker. */
    private static int[] flintAndSteelSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF2A2A2A;
        int steel = 0xFFB8B8C8;
        int steelDark = 0xFF808090;
        for (int y = 5; y <= 11; y++) {
            for (int x = 3; x <= 9; x++) {
                double n = hash(x, y, 91);
                if (n > 0.3) {
                    out[y * SIZE + x] = 0xFF000000 | (n > 0.6 ? steel : steelDark);
                }
            }
        }
        for (int y = 7; y <= 12; y++) {
            for (int x = 9; x <= 12; x++) {
                double n = hash(x, y, 92);
                if (n > 0.35) {
                    out[y * SIZE + x] = 0xFF000000 | (n > 0.7 ? dark : 0xFF3A3A3A);
                }
            }
        }
        out[6 * SIZE + 8] = 0xFF000000 | steel;
        out[12 * SIZE + 12] = 0xFF000000 | dark;
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

        /** [ANM] Shears: crossed blades with finger loops on top. */
    private static int[] shearsSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFFA8A8B0;
        int mid = 0xFFD0D0D8;
        int light = 0xFFF0F0F8;
        for (int i = 0; i < 6; i++) {
            out[(5 + i) * SIZE + (5 + i)] = 0xFF000000 | (i < 3 ? light : mid);
            out[(5 + i) * SIZE + (10 - i)] = 0xFF000000 | (i < 3 ? light : mid);
        }
        out[4 * SIZE + 5] = 0xFF000000 | dark;
        out[4 * SIZE + 10] = 0xFF000000 | dark;
        out[3 * SIZE + 5] = 0xFF000000 | mid;
        out[3 * SIZE + 10] = 0xFF000000 | mid;
        out[3 * SIZE + 4] = 0xFF000000 | dark;
        out[3 * SIZE + 11] = 0xFF000000 | dark;
        out[2 * SIZE + 4] = 0xFF000000 | mid;
        out[2 * SIZE + 11] = 0xFF000000 | mid;
        return out;
    }

    /** [ANM] Bucket: a metal pail with a handle. */
    private static int[] bucketSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF686870;
        int mid = 0xFF909098;
        int light = 0xFFB8B8C0;
        for (int x = 5; x <= 10; x++) {
            int span = x <= 7 ? x - 4 : 11 - x;
            for (int y = 6 + span; y <= 12; y++) {
                int c = (y == 6 + span) ? light : ((x == 5 || x == 10) ? dark : mid);
                out[y * SIZE + x] = 0xFF000000 | c;
            }
        }
        for (int x = 6; x <= 9; x++) out[5 * SIZE + x] = 0xFF000000 | light;
        out[4 * SIZE + 6] = 0xFF000000 | dark;
        out[4 * SIZE + 9] = 0xFF000000 | dark;
        out[3 * SIZE + 7] = 0xFF000000 | dark;
        out[3 * SIZE + 8] = 0xFF000000 | dark;
        return out;
    }

    /** [ANM] Milk: a white bottle with a red cap. */
    private static int[] milkSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFFD0D0C8;
        int mid = 0xFFF0F0E8;
        int light = 0xFFFCFCF8;
        int cap = 0xFFD04040;
        for (int y = 4; y <= 12; y++) {
            int half = (y < 6) ? 1 : (y < 9 ? 2 : 3);
            for (int x = 8 - half; x <= 7 + half; x++) {
                int c = (x == 8 - half || x == 7 + half || y == 12) ? dark
                    : (y <= 5 ? mid : (x == 8 ? light : mid));
                out[y * SIZE + x] = 0xFF000000 | c;
            }
        }
        out[2 * SIZE + 7] = 0xFF000000 | cap;
        out[3 * SIZE + 7] = 0xFF000000 | cap;
        out[2 * SIZE + 8] = 0xFF000000 | cap;
        out[3 * SIZE + 8] = 0xFF000000 | cap;
        return out;
    }

    /**
     * [GP-009] Slime ball: a small round translucent green blob with a
     * glossy highlight in the top-left.
     */
    private static int[] slimeBallSprite() {
        int[] out = new int[SIZE * SIZE];
        int dark = 0xFF1E9A3E;
        int mid = 0xFF2DD85A;
        int light = 0xFF57F08A;
        int shine = 0xFFB4FFC8;
        // Circular blob roughly rows 4..12, columns 4..12
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int dx = (x - 8) * 2, dy = (y - 8) * 2;
                int d2 = dx * dx + dy * dy;
                if (d2 <= 16) {
                    int c = (d2 > 11) ? dark : (d2 > 5 ? mid : light);
                    out[y * SIZE + x] = 0xFF000000 | c;
                }
            }
        }
        // Glossy highlight
        out[5 * SIZE + 5] = 0xFF000000 | shine;
        out[5 * SIZE + 6] = 0xFF000000 | shine;
        out[6 * SIZE + 5] = 0xFF000000 | shine;
        return out;
    }

    /**
     * [POT] A white sugar cube with a few grainy highlights and a soft
     * bottom shadow, used as the Speed potion ingredient.
     */
    private static int[] sugarSprite() {
        int[] out = new int[SIZE * SIZE];
        int[] grays = {0xFFF4F6F8, 0xFFE8ECF0, 0xFFDCE2E8, 0xFFC8D0D8};
        for (int y = 3; y < 13; y++) {
            for (int x = 3; x < 13; x++) {
                out[y * SIZE + x] = 0xFF000000 | grays[(x + y) % 4];
            }
        }
        // Grainy specks
        out[5 * SIZE + 5] = 0xFF000000 | 0xFFFFFFFF;
        out[6 * SIZE + 9] = 0xFF000000 | 0xFFFFFFFF;
        out[9 * SIZE + 7] = 0xFF000000 | 0xFFFFFFFF;
        out[7 * SIZE + 4] = 0xFF000000 | 0xDFE6EC;
        out[10 * SIZE + 10] = 0xFF000000 | 0xC0CCD4;
        // Bottom-right shadow
        for (int y = 11; y < 13; y++) {
            for (int x = 11; x < 13; x++) {
                out[y * SIZE + x] = 0xFF000000 | 0x98A2AA;
            }
        }
        return out;
    }

    /**
     * [POT] A glass bottle with a coloured liquid: round flask shape, a
     * cork stopper and a lighter glass shine. The liquid colour is passed
     * in as 0xFFrrggbb.
     */
    private static int[] potionSprite(int liquid) {
        int[] out = new int[SIZE * SIZE];
        int glass = 0xFFD8E8F0;
        int glassLight = 0xFFF8FFFF;
        int cork = 0xFFB88860;
        int r = (liquid >> 16) & 255, g = (liquid >> 8) & 255, b = liquid & 255;
        int liquidDark = 0xFF000000 | ((int) (r * 0.75f) << 16)
            | ((int) (g * 0.75f) << 8) | (int) (b * 0.75f);
        int liquidLight = 0xFF000000 | (Math.min(255, r + 40) << 16)
            | (Math.min(255, g + 40) << 8) | Math.min(255, b + 40);

        // Flask body: narrow neck at top, bulbous bottom
        for (int y = 5; y <= 13; y++) {
            int half = (y < 7) ? 1 : (y < 11 ? 3 : 2);
            for (int x = 8 - half; x <= 7 + half; x++) {
                boolean inLiquid = y >= 9;
                int c = (x == 8 - half || x == 7 + half || y == 13) ? (inLiquid ? liquidDark : glass)
                    : (y <= 6 ? glassLight : (inLiquid ? liquidLight : glass));
                out[y * SIZE + x] = (inLiquid ? 0xFF000000 : 0xF2000000) | c;
            }
        }
        // Cork: two tiny cork-coloured pixels on the neck
        out[5 * SIZE + 7] = 0xFF000000 | cork;
        out[5 * SIZE + 8] = 0xFF000000 | cork;
        // Glass shine on the left shoulder
        out[7 * SIZE + 5] = 0xFF000000 | glassLight;
        return out;
    }

    /**
     * [POT] HUD status-effect icon: a simple pictogram in the effect colour.
     * Shapes: heart (regeneration), chevrons (speed), two strokes (strength),
     * a flame (fire resistance).
     */
    private static int[] effectIconSprite(int color, String shape) {
        int[] out = new int[SIZE * SIZE];
        int c = 0xFF000000 | color;
        int cLight = 0xFF000000 | 0xFF | Math.max(0, Math.min(255, (color & 0xFF) + 60))
            | (Math.max(0, Math.min(255, ((color >> 8) & 255) + 60)) << 8)
            | (Math.max(0, Math.min(255, ((color >> 16) & 255) + 60)) << 16);
        switch (shape) {
            case "heart" -> {
                for (int y = 5; y <= 12; y++) {
                    for (int x = 4; x <= 11; x++) {
                        int dx = x - 8;
                        int dy = y - 8;
                        boolean inHeart = dy >= 0 ? Math.abs(dx) <= (y < 10 ? 2 + dy : 1) : (Math.abs(dx) <= 2 && Math.abs(dx + dy) >= 2);
                        if (inHeart) out[y * SIZE + x] = (Math.abs(dx) + dy == 1) ? cLight : c;
                    }
                }
                out[5 * SIZE + 8] = cLight;
            }
            case "chevron" -> {
                for (int i = 0; i < 3; i++) {
                    int base = 5 + i * 2;
                    for (int y = base; y <= base + 2; y++) {
                        out[y * SIZE + 7 - i] = c;
                        out[y * SIZE + 8 + i] = c;
                    }
                    out[(base + 1) * SIZE + 7 - i] = cLight;
                    out[(base + 1) * SIZE + 8 + i] = cLight;
                }
            }
            case "sword" -> {
                for (int i = 0; i < 7; i++) {
                    out[(8 + i) * SIZE + (8 - i)] = c;
                    out[(8 + i) * SIZE + (7 - i)] = c;
                    out[(6 + i) * SIZE + (8 - i)] = c;
                }
                out[14 * SIZE + 8] = c;
                out[14 * SIZE + 9] = c;
                out[13 * SIZE + 6] = c;
                out[13 * SIZE + 9] = c;
                out[7 * SIZE + 7] = cLight;
            }
            case "flame" -> {
                for (int y = 5; y <= 12; y++) {
                    int half = y < 7 ? y - 4 : (y < 10 ? 3 : 2);
                    for (int x = 8 - half; x <= 7 + half; x++) {
                        out[y * SIZE + x] = (y == 6 || y == 12) ? cLight : c;
                    }
                }
                out[5 * SIZE + 8] = cLight;
            }
            default -> { }
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
