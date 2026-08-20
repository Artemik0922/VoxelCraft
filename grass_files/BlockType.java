package com.voxelgame.world;

/**
 * Block types matching Minecraft's block system
 */
public enum BlockType {
    // Basic blocks
    AIR(0, "air", false, 0xFFFF00FF),
    STONE(1, "stone", true, 0x808080),
    GRANITE(2, "granite", true, 0xA0846C),
    POLISHED_GRANITE(3, "polished_granite", true, 0xB0957C),
    DIORITE(4, "diorite", true, 0xE0E0E0),
    POLISHED_DIORITE(5, "polished_diorite", true, 0xF0F0F0),
    ANDESITE(6, "andesite", true, 0x8A8A8A),
    POLISHED_ANDESITE(7, "polished_andesite", true, 0x9A9A9A),
    GRASS_BLOCK(8, "grass_block", true, 0x5D9B3A),
    DIRT(9, "dirt", true, 0x8B6914),
    COARSE_DIRT(10, "coarse_dirt", true, 0x7A5A2A),
    PODZOL(11, "podzol", true, 0x5A4A2A),
    COBBLESTONE(12, "cobblestone", true, 0x6B6B6B),
    
    // Wood & Planks
    OAK_PLANKS(13, "oak_planks", true, 0xB8905A),
    SPRUCE_PLANKS(14, "spruce_planks", true, 0x7A5A3A),
    BIRCH_PLANKS(15, "birch_planks", true, 0xD8C898),
    JUNGLE_PLANKS(16, "jungle_planks", true, 0xB08050),
    ACACIA_PLANKS(17, "acacia_planks", true, 0xC08040),
    DARK_OAK_PLANKS(18, "dark_oak_planks", true, 0x5A3A20),
    OAK_LOG(19, "oak_log", true, 0x6B4423),
    SPRUCE_LOG(20, "spruce_log", true, 0x4A3020),
    BIRCH_LOG(21, "birch_log", true, 0xD8D0C0),
    JUNGLE_LOG(22, "jungle_log", true, 0x6A4020),
    
    // Leaves
    OAK_LEAVES(23, "oak_leaves", true, 0x2D6B1E),
    SPRUCE_LEAVES(24, "spruce_leaves", true, 0x1A4A1A),
    BIRCH_LEAVES(25, "birch_leaves", true, 0x4A8A2A),
    JUNGLE_LEAVES(26, "jungle_leaves", true, 0x3A7A2A),
    
    // Ores & Minerals
    COAL_ORE(27, "coal_ore", true, 0x3A3A3A),
    IRON_ORE(28, "iron_ore", true, 0xC8A882),
    GOLD_ORE(29, "gold_ore", true, 0xFCEE4B),
    DIAMOND_ORE(30, "diamond_ore", true, 0x5DECF5),
    EMERALD_ORE(31, "emerald_ore", true, 0x30E050),
    REDSTONE_ORE(32, "redstone_ore", true, 0xE02020),
    LAPIS_ORE(33, "lapis_ore", true, 0x3050E0),
    COAL_BLOCK(34, "coal_block", true, 0x2A2A2A),
    IRON_BLOCK(35, "iron_block", true, 0xD8D8D8),
    GOLD_BLOCK(36, "gold_block", true, 0xFCEE4B),
    DIAMOND_BLOCK(37, "diamond_block", true, 0x5DECF5),
    EMERALD_BLOCK(38, "emerald_block", true, 0x30E050),
    
    // Natural blocks
    SAND(39, "sand", true, 0xE8D8A0),
    GRAVEL(40, "gravel", true, 0x7B7B7B),
    CLAY(41, "clay", true, 0xA0A0B0),
    TERRACOTTA(42, "terracotta", true, 0xB0522B),
    WHITE_WOOL(43, "white_wool", true, 0xF0F0F0),
    GLASS(44, "glass", true, 0xC8E8FF),
    
    // Water & Liquids
    WATER(45, "water", false, 0x3070D0),
    LAVA(46, "lava", false, 0xE25822),
    
    // Special blocks
    BEDROCK(47, "bedrock", true, 0x2A2A2A),
    OBSIDIAN(48, "obsidian", true, 0x1A0A2A),
    ICE(49, "ice", true, 0xA5D8F0),
    SNOW(50, "snow", true, 0xF8F8F8),
    CRAFTING_TABLE(51, "crafting_table", true, 0x8B5A2B),
    FURNACE(52, "furnace", true, 0x696969),
    CHEST(53, "chest", true, 0x8B6914),
    
    // Flowers & Plants
    DANDELION(54, "dandelion", false, 0xFCEE4B),
    POPPY(55, "poppy", false, 0xE02020),
    GRASS_PLANT(56, "grass_plant", false, 0x3A8A2A),
    DEAD_BUSH(57, "dead_bush", false, 0x8B6914),
    CACTUS(58, "cactus", true, 0x2A7A2A),
    
    // Crops
    WHEAT(59, "wheat", false, 0xD8B020),
    CARROT(60, "carrot", false, 0xE88020),
    POTATO(61, "potato", false, 0xC8A040),
    
    // Misc
    TNT(62, "tnt", true, 0xE03020),
    BOOKSHELF(63, "bookshelf", true, 0x8B5A2B),
    MOSSY_COBBLESTONE(64, "mossy_cobblestone", true, 0x5A7A5A),
    STONE_BRICKS(65, "stone_bricks", true, 0x7A7A7A),
    BRICK(66, "brick", true, 0xB05040),
    
    // Food
    APPLE(79, "apple", false, 0xE02020),
    
    // Nether blocks
    NETHERRACK(67, "netherrack", true, 0x6A2020),
    SOUL_SAND(68, "soul_sand", true, 0x5A4030),
    GLOWSTONE(69, "glowstone", true, 0xFCEE80),
    NETHER_BRICKS(70, "nether_bricks", true, 0x3A1A1A),
    
    // End blocks
    END_STONE(71, "end_stone", true, 0xE8E8A0),
    PURPUR_BLOCK(72, "purpur_block", true, 0xA060A0),
    
    // Concrete
    WHITE_CONCRETE(73, "white_concrete", true, 0xE0E0E0),
    RED_CONCRETE(74, "red_concrete", true, 0xC03030),
    GREEN_CONCRETE(75, "green_concrete", true, 0x3A8A3A),
    BLUE_CONCRETE(76, "blue_concrete", true, 0x3050C0),
    
    // Extra
    GLOWING_OBSIDIAN(77, "glowing_obsidian", true, 0x4A2060),
    NETHER_REACTOR(78, "nether_reactor", true, 0x202040),
    
    // --- Biome blocks ---
    AUTUMN_LEAVES(80, "autumn_leaves", false, 0xB0541F),
    CHERRY_LEAVES(81, "cherry_leaves", false, 0xE8B7C8),
    LAVENDER(82, "lavender", false, 0x7E4BB5),
    CRYSTAL(83, "crystal", true, 0x9B59D0),
    BASALT(84, "basalt", true, 0x3A3A3E),
    SALT(85, "salt", true, 0xE8E8E4),
    ASH(86, "ash", true, 0x9A9A9A),
    CHARRED_LOG(87, "charred_log", true, 0x1F1713),
    CHARRED_LOG_TOP(88, "charred_log_top", true, 0x0F0A08),
    PETRIFIED_LOG(89, "petrified_log", true, 0x7F7F7F),
    PETRIFIED_LOG_TOP(90, "petrified_log_top", true, 0x5F5F5F),
    BLUE_ICE(91, "blue_ice", true, 0x6AA7E8),
    MYCELIUM(92, "mycelium", true, 0x6E5A6E),
    RED_SAND(94, "red_sand", true, 0xC06A2A),
    CACTUS_SIDE(95, "cactus_side", true, 0x3B7A1F),
    LILY_PAD(96, "lily_pad", false, 0x2D6B1E),
    SUNFLOWER(97, "sunflower", false, 0xFCEE4B),
    VINE(98, "vine", false, 0x2D6B1E),
    DEEPSLATE(99, "deepslate", true, 0x4A4A4A),
    COPPER_ORE(100, "copper_ore", true, 0xB87333),

    // --- Items (non-solid, rendered as flat sprites) ---
    IRON_SWORD(101, "iron_sword", false, 0xC0C0C8),
    TORCH_PLACEHOLDER(102, "torch", false, 0xFFF0C0),

    // --- Tool & Item IDs (for atlas registration, IDs > 127 use HashMap) ---
    ITEM_STICK(103, "stick", false, 0xFFAA7440),
    ITEM_COAL(104, "coal", false, 0xFF2A2A2A),
    ITEM_IRON_INGOT(105, "iron_ingot", false, 0xFFB0B0B8),
    ITEM_GOLD_INGOT(106, "gold_ingot", false, 0xFFF0C840),
    ITEM_DIAMOND(107, "diamond", false, 0xFF4CC8C0),
    ITEM_LEATHER(108, "leather", false, 0xFF8B5A28),
    ITEM_STRING(109, "string", false, 0xFFE8E8E8),
    ITEM_FEATHER(110, "feather", false, 0xFFE8E8E8),
    ITEM_FLINT(111, "flint", false, 0xFF4A4A4A),
    ITEM_GUNPOWDER(112, "gunpowder", false, 0xFF505050),
    ITEM_WHEAT(113, "wheat", false, 0xFFD8B840),
    ITEM_EGG(114, "egg", false, 0xFFF8EED8),

    // Wooden tools
    ITEM_WOODEN_PICKAXE(115, "wooden_pickaxe", false, 0xFF8A5C30),
    ITEM_WOODEN_AXE(116, "wooden_axe", false, 0xFF8A5C30),
    ITEM_WOODEN_SHOVEL(117, "wooden_shovel", false, 0xFF8A5C30),
    ITEM_WOODEN_SWORD(118, "wooden_sword", false, 0xFF8A5C30),
    ITEM_WOODEN_HOE(119, "wooden_hoe", false, 0xFF8A5C30),

    // Stone tools
    ITEM_STONE_PICKAXE(120, "stone_pickaxe", false, 0xFF7A7A7A),
    ITEM_STONE_AXE(121, "stone_axe", false, 0xFF7A7A7A),
    ITEM_STONE_SHOVEL(122, "stone_shovel", false, 0xFF7A7A7A),
    ITEM_STONE_SWORD(123, "stone_sword", false, 0xFF7A7A7A),
    ITEM_STONE_HOE(124, "stone_hoe", false, 0xFF7A7A7A),

    // Iron tools (IDs continue past 127 — atlas uses HashMap)
    ITEM_IRON_PICKAXE(130, "iron_pickaxe", false, 0xFFA0A0B0),
    ITEM_IRON_AXE(131, "iron_axe", false, 0xFFA0A0B0),
    ITEM_IRON_SHOVEL(132, "iron_shovel", false, 0xFFA0A0B0),
    ITEM_IRON_SWORD(133, "iron_sword_item", false, 0xFFA0A0B0),
    ITEM_IRON_HOE(134, "iron_hoe", false, 0xFFA0A0B0),

    // Diamond tools
    ITEM_DIAMOND_PICKAXE(135, "diamond_pickaxe", false, 0xFF4CC8C0),
    ITEM_DIAMOND_AXE(136, "diamond_axe", false, 0xFF4CC8C0),
    ITEM_DIAMOND_SHOVEL(137, "diamond_shovel", false, 0xFF4CC8C0),
    ITEM_DIAMOND_SWORD(138, "diamond_sword", false, 0xFF4CC8C0),
    ITEM_DIAMOND_HOE(139, "diamond_hoe", false, 0xFF4CC8C0),

    // Gold tools
    ITEM_GOLDEN_PICKAXE(140, "golden_pickaxe", false, 0xFFF0C840),
    ITEM_GOLDEN_AXE(141, "golden_axe", false, 0xFFF0C840),
    ITEM_GOLDEN_SHOVEL(142, "golden_shovel", false, 0xFFF0C840),
    ITEM_GOLDEN_SWORD(143, "golden_sword", false, 0xFFF0C840),
    ITEM_GOLDEN_HOE(144, "golden_hoe", false, 0xFFF0C840),

    // Armor
    ITEM_LEATHER_HELMET(145, "leather_helmet", false, 0xFF8B5A28),
    ITEM_LEATHER_CHESTPLATE(146, "leather_chestplate", false, 0xFF8B5A28),
    ITEM_LEATHER_LEGGINGS(147, "leather_leggings", false, 0xFF8B5A28),
    ITEM_LEATHER_BOOTS(148, "leather_boots", false, 0xFF8B5A28),
    ITEM_IRON_HELMET(149, "iron_helmet", false, 0xFFA8A8B0),
    ITEM_IRON_CHESTPLATE(150, "iron_chestplate", false, 0xFFA8A8B0),
    ITEM_IRON_LEGGINGS(151, "iron_leggings", false, 0xFFA8A8B0),
    ITEM_IRON_BOOTS(152, "iron_boots", false, 0xFFA8A8B0),
    ITEM_DIAMOND_HELMET(153, "diamond_helmet", false, 0xFF4CC8C0),
    ITEM_DIAMOND_CHESTPLATE(154, "diamond_chestplate", false, 0xFF4CC8C0),
    ITEM_DIAMOND_LEGGINGS(155, "diamond_leggings", false, 0xFF4CC8C0),
    ITEM_DIAMOND_BOOTS(156, "diamond_boots", false, 0xFF4CC8C0),

    // Food
    ITEM_BREAD(157, "bread", false, 0xFFD8A050),
    ITEM_RAW_PORK(158, "raw_pork", false, 0xFFE07070),
    ITEM_COOKED_PORK(159, "cooked_pork", false, 0xFFC87040),
    ITEM_RAW_BEEF(160, "raw_beef", false, 0xFFE07070),
    ITEM_COOKED_BEEF(161, "cooked_beef", false, 0xFFC87040),
    ITEM_RAW_CHICKEN(162, "raw_chicken", false, 0xFFE8B0B0),
    ITEM_COOKED_CHICKEN(163, "cooked_chicken", false, 0xFFD09050),
    ITEM_RAW_MUTTON(164, "raw_mutton", false, 0xFFD87070),
    ITEM_COOKED_MUTTON(165, "cooked_mutton", false, 0xFFC87040),
    ITEM_APPLE(166, "apple", false, 0xFFE84040),

    // Misc
    ITEM_BOW(167, "bow", false, 0xFF7A5228),
    ITEM_ARROW(168, "arrow", false, 0xFFC8C8C8),
    ITEM_TORCH(169, "torch", false, 0xFFF8D040),
    ITEM_ROTTEN_FLESH(170, "rotten_flesh", false, 0xFF7A5A4A),
    ITEM_BONE(171, "bone", false, 0xFFE8E8D8),
    ITEM_SPIDER_EYE(172, "spider_eye", false, 0xFFC84040);
    
    public final byte id;
    public final String name;
    public final boolean solid;
    public final int color;
    
    BlockType(int id, String name, boolean solid, int color) {
        this.id = (byte) id;
        this.name = name;
        this.solid = solid;
        this.color = color;
    }
    
    /** O(1) lookup table: id -> BlockType. Built once at class init. */
    private static final BlockType[] BY_ID = new BlockType[256];
    static {
        for (BlockType t : values()) {
            BY_ID[t.id & 0xFF] = t;
        }
    }

    /** O(1) solid lookup for mesher/lighting hot paths. */
    private static final boolean[] SOLID = new boolean[256];
    private static final boolean[] TRANSPARENT = new boolean[256];
    static {
        for (BlockType t : values()) {
            int idx = t.id & 0xFF;
            SOLID[idx] = t.solid;
            TRANSPARENT[idx] = !t.solid || t == WATER || t == GLASS || t == ICE
                || t == OAK_LEAVES || t == SPRUCE_LEAVES || t == BIRCH_LEAVES
                || t == AUTUMN_LEAVES || t == CHERRY_LEAVES;
        }
    }

    public static BlockType fromId(byte id) {
        return BY_ID[id & 0xFF];
    }

    public static boolean isSolidFast(byte id) {
        return SOLID[id & 0xFF];
    }

    public static boolean isTransparentFast(byte id) {
        return TRANSPARENT[id & 0xFF];
    }
    
    /**
     * Check if this block should render a face against another block
     */
    public boolean shouldRenderFace(byte neighborId) {
        if (this == AIR) return false;
        if (neighborId == 0) return true; // Air
        
        BlockType neighbor = fromId(neighborId);
        
        // Transparent blocks don't render against same type
        if (this == GLASS && neighbor == GLASS) return false;
        if (this == WATER && neighbor == WATER) return false;
        if (this == OAK_LEAVES && neighbor == OAK_LEAVES) return false;
        
        // Transparent blocks don't render against solid blocks
        if (neighbor.solid && !isTransparent(this)) return false;
        if (neighbor.solid && isTransparent(this)) return true;
        
        return !neighbor.solid;
    }
    
    private boolean isTransparent(BlockType type) {
        return type == WATER || type == GLASS || type == ICE || type == AIR;
    }
    
    /**
     * Get the top color for this block (for atlas generation)
     */
    public int getTopColor() {
        if (this == GRASS_BLOCK) return 0x5D9B3A;
        if (this == OAK_LOG) return 0xB8905A; // Bark color
        return color;
    }
    
    /**
     * Get the side color for this block
     */
    public int getSideColor() {
        if (this == GRASS_BLOCK) return 0x8B6914; // Dirt sides
        if (this == OAK_LOG) return 0x6B4423; // Wood interior
        return color;
    }
    
    /**
     * Get the bottom color for this block
     */
    public int getBottomColor() {
        if (this == GRASS_BLOCK) return 0x8B6914; // Dirt bottom
        if (this == OAK_LOG) return 0xB8905A; // Bark bottom
        return color;
    }
}
