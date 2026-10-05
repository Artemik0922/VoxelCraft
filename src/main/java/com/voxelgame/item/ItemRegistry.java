package com.voxelgame.item;

import com.voxelgame.world.BlockType;

/**
 * Registry of all Minecraft-style items.
 *
 * IDs 0-127 reserved for blocks (BlockType).
 * IDs 128+ are for items.
 */
public final class ItemRegistry {

    // --- Materials ---
    public static final Item STICK = Item.builder(128, "stick")
            .displayName("Stick").sprite("stick").build();
    public static final Item COAL = Item.builder(129, "coal")
            .displayName("Coal").sprite("coal").build();
    public static final Item IRON_INGOT = Item.builder(130, "iron_ingot")
            .displayName("Iron Ingot").sprite("iron_ingot").build();
    public static final Item GOLD_INGOT = Item.builder(131, "gold_ingot")
            .displayName("Gold Ingot").sprite("gold_ingot").build();
    public static final Item DIAMOND = Item.builder(132, "diamond")
            .displayName("Diamond").sprite("diamond").build();
    public static final Item LEATHER = Item.builder(133, "leather")
            .displayName("Leather").sprite("leather").build();
    public static final Item STRING = Item.builder(134, "string")
            .displayName("String").sprite("string").build();
    public static final Item FEATHER = Item.builder(135, "feather")
            .displayName("Feather").sprite("feather").build();
    public static final Item FLINT = Item.builder(136, "flint")
            .displayName("Flint").sprite("flint").build();
    public static final Item GUNPOWDER = Item.builder(137, "gunpowder")
            .displayName("Gunpowder").sprite("gunpowder").build();
    public static final Item WHEAT = Item.builder(138, "wheat")
            .displayName("Wheat").sprite("wheat").build();
    public static final Item EGG = Item.builder(139, "egg")
            .displayName("Egg").stackSize(16).sprite("egg").build();

    // --- Wooden Tools ---
    public static final Item WOODEN_PICKAXE = Item.builder(140, "wooden_pickaxe")
            .displayName("Wooden Pickaxe").stackSize(1)
            .tier(ToolTier.WOOD).toolType(ToolType.PICKAXE).attackSpeed(1.2f)
            .sprite("wooden_pickaxe").build();
    public static final Item WOODEN_AXE = Item.builder(141, "wooden_axe")
            .displayName("Wooden Axe").stackSize(1)
            .tier(ToolTier.WOOD).toolType(ToolType.AXE).attackSpeed(0.8f)
            .sprite("wooden_axe").build();
    public static final Item WOODEN_SHOVEL = Item.builder(142, "wooden_shovel")
            .displayName("Wooden Shovel").stackSize(1)
            .tier(ToolTier.WOOD).toolType(ToolType.SHOVEL).attackSpeed(1.0f)
            .sprite("wooden_shovel").build();
    public static final Item WOODEN_SWORD = Item.builder(143, "wooden_sword")
            .displayName("Wooden Sword").stackSize(1)
            .tier(ToolTier.WOOD).toolType(ToolType.SWORD).attackDamage(4).attackSpeed(1.6f)
            .sprite("wooden_sword").build();
    public static final Item WOODEN_HOE = Item.builder(144, "wooden_hoe")
            .displayName("Wooden Hoe").stackSize(1)
            .tier(ToolTier.WOOD).toolType(ToolType.HOE).attackSpeed(1.0f)
            .sprite("wooden_hoe").build();

    // --- Stone Tools ---
    public static final Item STONE_PICKAXE = Item.builder(145, "stone_pickaxe")
            .displayName("Stone Pickaxe").stackSize(1)
            .tier(ToolTier.STONE).toolType(ToolType.PICKAXE).attackSpeed(1.2f)
            .sprite("stone_pickaxe").build();
    public static final Item STONE_AXE = Item.builder(146, "stone_axe")
            .displayName("Stone Axe").stackSize(1)
            .tier(ToolTier.STONE).toolType(ToolType.AXE).attackSpeed(0.8f)
            .sprite("stone_axe").build();
    public static final Item STONE_SHOVEL = Item.builder(147, "stone_shovel")
            .displayName("Stone Shovel").stackSize(1)
            .tier(ToolTier.STONE).toolType(ToolType.SHOVEL).attackSpeed(1.0f)
            .sprite("stone_shovel").build();
    public static final Item STONE_SWORD = Item.builder(148, "stone_sword")
            .displayName("Stone Sword").stackSize(1)
            .tier(ToolTier.STONE).toolType(ToolType.SWORD).attackDamage(5).attackSpeed(1.6f)
            .sprite("stone_sword").build();
    public static final Item STONE_HOE = Item.builder(149, "stone_hoe")
            .displayName("Stone Hoe").stackSize(1)
            .tier(ToolTier.STONE).toolType(ToolType.HOE).attackSpeed(1.0f)
            .sprite("stone_hoe").build();

    // --- Iron Tools ---
    public static final Item IRON_PICKAXE = Item.builder(150, "iron_pickaxe")
            .displayName("Iron Pickaxe").stackSize(1)
            .tier(ToolTier.IRON).toolType(ToolType.PICKAXE).attackSpeed(1.2f)
            .sprite("iron_pickaxe").build();
    public static final Item IRON_AXE = Item.builder(151, "iron_axe")
            .displayName("Iron Axe").stackSize(1)
            .tier(ToolTier.IRON).toolType(ToolType.AXE).attackSpeed(0.9f)
            .sprite("iron_axe").build();
    public static final Item IRON_SHOVEL = Item.builder(152, "iron_shovel")
            .displayName("Iron Shovel").stackSize(1)
            .tier(ToolTier.IRON).toolType(ToolType.SHOVEL).attackSpeed(1.0f)
            .sprite("iron_shovel").build();
    public static final Item IRON_SWORD = Item.builder(153, "iron_sword")
            .displayName("Iron Sword").stackSize(1)
            .tier(ToolTier.IRON).toolType(ToolType.SWORD).attackDamage(6).attackSpeed(1.6f)
            .sprite("iron_sword").build();
    public static final Item IRON_HOE = Item.builder(154, "iron_hoe")
            .displayName("Iron Hoe").stackSize(1)
            .tier(ToolTier.IRON).toolType(ToolType.HOE).attackSpeed(1.0f)
            .sprite("iron_hoe").build();

    // --- Diamond Tools ---
    public static final Item DIAMOND_PICKAXE = Item.builder(155, "diamond_pickaxe")
            .displayName("Diamond Pickaxe").stackSize(1)
            .tier(ToolTier.DIAMOND).toolType(ToolType.PICKAXE).attackSpeed(1.2f)
            .sprite("diamond_pickaxe").build();
    public static final Item DIAMOND_AXE = Item.builder(156, "diamond_axe")
            .displayName("Diamond Axe").stackSize(1)
            .tier(ToolTier.DIAMOND).toolType(ToolType.AXE).attackSpeed(1.0f)
            .sprite("diamond_axe").build();
    public static final Item DIAMOND_SHOVEL = Item.builder(157, "diamond_shovel")
            .displayName("Diamond Shovel").stackSize(1)
            .tier(ToolTier.DIAMOND).toolType(ToolType.SHOVEL).attackSpeed(1.0f)
            .sprite("diamond_shovel").build();
    public static final Item DIAMOND_SWORD = Item.builder(158, "diamond_sword")
            .displayName("Diamond Sword").stackSize(1)
            .tier(ToolTier.DIAMOND).toolType(ToolType.SWORD).attackDamage(7).attackSpeed(1.6f)
            .sprite("diamond_sword").build();
    public static final Item DIAMOND_HOE = Item.builder(159, "diamond_hoe")
            .displayName("Diamond Hoe").stackSize(1)
            .tier(ToolTier.DIAMOND).toolType(ToolType.HOE).attackSpeed(1.0f)
            .sprite("diamond_hoe").build();

    // --- Gold Tools ---
    public static final Item GOLDEN_PICKAXE = Item.builder(160, "golden_pickaxe")
            .displayName("Golden Pickaxe").stackSize(1)
            .tier(ToolTier.GOLD).toolType(ToolType.PICKAXE).attackSpeed(1.2f)
            .sprite("golden_pickaxe").build();
    public static final Item GOLDEN_AXE = Item.builder(161, "golden_axe")
            .displayName("Golden Axe").stackSize(1)
            .tier(ToolTier.GOLD).toolType(ToolType.AXE).attackSpeed(1.0f)
            .sprite("golden_axe").build();
    public static final Item GOLDEN_SHOVEL = Item.builder(162, "golden_shovel")
            .displayName("Golden Shovel").stackSize(1)
            .tier(ToolTier.GOLD).toolType(ToolType.SHOVEL).attackSpeed(1.0f)
            .sprite("golden_shovel").build();
    public static final Item GOLDEN_SWORD = Item.builder(163, "golden_sword")
            .displayName("Golden Sword").stackSize(1)
            .tier(ToolTier.GOLD).toolType(ToolType.SWORD).attackDamage(4).attackSpeed(1.6f)
            .sprite("golden_sword").build();
    public static final Item GOLDEN_HOE = Item.builder(164, "golden_hoe")
            .displayName("Golden Hoe").stackSize(1)
            .tier(ToolTier.GOLD).toolType(ToolType.HOE).attackSpeed(1.0f)
            .sprite("golden_hoe").build();

    // --- Armor ---
    public static final Item LEATHER_HELMET = Item.builder(165, "leather_helmet")
            .displayName("Leather Cap").stackSize(1)
            .armorSlot(ArmorSlot.HELMET).armorPoints(1)
            .sprite("leather_helmet").build();
    public static final Item LEATHER_CHESTPLATE = Item.builder(166, "leather_chestplate")
            .displayName("Leather Tunic").stackSize(1)
            .armorSlot(ArmorSlot.CHESTPLATE).armorPoints(3)
            .sprite("leather_chestplate").build();
    public static final Item LEATHER_LEGGINGS = Item.builder(167, "leather_leggings")
            .displayName("Leather Pants").stackSize(1)
            .armorSlot(ArmorSlot.LEGGINGS).armorPoints(2)
            .sprite("leather_leggings").build();
    public static final Item LEATHER_BOOTS = Item.builder(168, "leather_boots")
            .displayName("Leather Boots").stackSize(1)
            .armorSlot(ArmorSlot.BOOTS).armorPoints(1)
            .sprite("leather_boots").build();

    public static final Item IRON_HELMET = Item.builder(169, "iron_helmet")
            .displayName("Iron Helmet").stackSize(1)
            .armorSlot(ArmorSlot.HELMET).armorPoints(2)
            .sprite("iron_helmet").build();
    public static final Item IRON_CHESTPLATE = Item.builder(170, "iron_chestplate")
            .displayName("Iron Chestplate").stackSize(1)
            .armorSlot(ArmorSlot.CHESTPLATE).armorPoints(6)
            .sprite("iron_chestplate").build();
    public static final Item IRON_LEGGINGS = Item.builder(171, "iron_leggings")
            .displayName("Iron Leggings").stackSize(1)
            .armorSlot(ArmorSlot.LEGGINGS).armorPoints(5)
            .sprite("iron_leggings").build();
    public static final Item IRON_BOOTS = Item.builder(172, "iron_boots")
            .displayName("Iron Boots").stackSize(1)
            .armorSlot(ArmorSlot.BOOTS).armorPoints(2)
            .sprite("iron_boots").build();

    public static final Item DIAMOND_HELMET = Item.builder(173, "diamond_helmet")
            .displayName("Diamond Helmet").stackSize(1)
            .armorSlot(ArmorSlot.HELMET).armorPoints(3)
            .sprite("diamond_helmet").build();
    public static final Item DIAMOND_CHESTPLATE = Item.builder(174, "diamond_chestplate")
            .displayName("Diamond Chestplate").stackSize(1)
            .armorSlot(ArmorSlot.CHESTPLATE).armorPoints(8)
            .sprite("diamond_chestplate").build();
    public static final Item DIAMOND_LEGGINGS = Item.builder(175, "diamond_leggings")
            .displayName("Diamond Leggings").stackSize(1)
            .armorSlot(ArmorSlot.LEGGINGS).armorPoints(6)
            .sprite("diamond_leggings").build();
    public static final Item DIAMOND_BOOTS = Item.builder(176, "diamond_boots")
            .displayName("Diamond Boots").stackSize(1)
            .armorSlot(ArmorSlot.BOOTS).armorPoints(3)
            .sprite("diamond_boots").build();

    // --- Food ---
    public static final Item BREAD = Item.builder(177, "bread")
            .displayName("Bread").food(5, 6.0f).sprite("bread").build();
    public static final Item RAW_PORK = Item.builder(178, "raw_pork")
            .displayName("Raw Porkchop").food(3, 0.3f).sprite("raw_pork").build();
    public static final Item COOKED_PORK = Item.builder(179, "cooked_pork")
            .displayName("Cooked Porkchop").food(8, 0.8f).sprite("cooked_pork").build();
    public static final Item RAW_BEEF = Item.builder(180, "raw_beef")
            .displayName("Raw Beef").food(3, 0.3f).sprite("raw_beef").build();
    public static final Item COOKED_BEEF = Item.builder(181, "cooked_beef")
            .displayName("Steak").food(8, 0.8f).sprite("cooked_beef").build();
    public static final Item RAW_CHICKEN = Item.builder(182, "raw_chicken")
            .displayName("Raw Chicken").food(2, 0.3f).sprite("raw_chicken").build();
    public static final Item COOKED_CHICKEN = Item.builder(183, "cooked_chicken")
            .displayName("Cooked Chicken").food(6, 0.6f).sprite("cooked_chicken").build();
    public static final Item RAW_MUTTON = Item.builder(184, "raw_mutton")
            .displayName("Raw Mutton").food(2, 0.3f).sprite("raw_mutton").build();
    public static final Item COOKED_MUTTON = Item.builder(185, "cooked_mutton")
            .displayName("Cooked Mutton").food(6, 0.6f).sprite("cooked_mutton").build();
    public static final Item APPLE_ITEM = Item.builder(186, "apple")
            .displayName("Apple").food(4, 0.3f).sprite("apple").build();

    // --- [FISH] Fishing ---
    public static final Item FISHING_ROD = Item.builder(223, "fishing_rod")
            .displayName("Fishing Rod").stackSize(1).durability(64)
            .sprite("fishing_rod").build();
    public static final Item RAW_FISH = Item.builder(224, "raw_fish")
            .displayName("Raw Fish").food(2, 0.3f).sprite("raw_fish").build();
    public static final Item COOKED_FISH = Item.builder(225, "cooked_fish")
            .displayName("Cooked Fish").food(5, 0.6f).sprite("cooked_fish").build();

    // --- Misc ---
    public static final Item BOW = Item.builder(187, "bow")
            .displayName("Bow").stackSize(1).attackSpeed(1.0f)
            .sprite("bow").build();
    public static final Item ARROW = Item.builder(188, "arrow")
            .displayName("Arrow").sprite("arrow").build();
    public static final Item TORCH_ITEM = Item.builder(189, "torch")
            .displayName("Torch").block(BlockType.TORCH_PLACEHOLDER).sprite("torch").build();
    public static final Item CRAFTING_TABLE_ITEM = Item.builder(190, "crafting_table")
            .displayName("Crafting Table").block(BlockType.CRAFTING_TABLE).sprite("crafting_table").build();
    public static final Item FURNACE_ITEM = Item.builder(191, "furnace")
            .displayName("Furnace").block(BlockType.FURNACE).sprite("furnace").build();
    public static final Item CHEST_ITEM = Item.builder(192, "chest")
            .displayName("Chest").block(BlockType.CHEST).sprite("chest").build();

    // --- Mob Drops ---
    public static final Item ROTTEN_FLESH = Item.builder(193, "rotten_flesh")
            .displayName("Rotten Flesh").food(4, 0.1f).sprite("rotten_flesh").build();
    public static final Item BONE = Item.builder(194, "bone")
            .displayName("Bone").sprite("bone").build();
    public static final Item SPIDER_EYE = Item.builder(195, "spider_eye")
            .displayName("Spider Eye").food(2, 0.3f).sprite("spider_eye").build();

    // --- Tools ---
    public static final Item FLINT_AND_STEEL = Item.builder(196, "flint_and_steel")
            .displayName("Flint and Steel").stackSize(1).attackSpeed(1.0f)
            .sprite("flint_and_steel").build();

    // --- [GP-PLANKS] Substitute materials for plank treatments ---
    public static final Item RESIN = Item.builder(197, "resin")
            .displayName("Resin").sprite("resin").build();
    public static final Item WAX = Item.builder(198, "wax")
            .displayName("Wax").sprite("wax").build();

    // --- [ENCH] Enchanting ---
    public static final Item BOOK = Item.builder(199, "book")
            .displayName("Book").sprite("book").build();
    public static final Item LAPIS_LAZULI = Item.builder(200, "lapis_lazuli")
            .displayName("Lapis Lazuli").sprite("lapis_lazuli").build();
    public static final Item ENCHANTING_TABLE_ITEM = Item.builder(201, "enchanting_table")
            .displayName("Enchanting Table").stackSize(1)
            .block(BlockType.ENCHANTING_TABLE).sprite("enchanting_table_item").build();

    // --- [ANM] Animal care ---
    public static final Item SHEARS = Item.builder(202, "shears")
            .displayName("Shears").stackSize(1).attackSpeed(1.0f)
            .sprite("shears").build();
    public static final Item BUCKET = Item.builder(203, "bucket")
            .displayName("Bucket").stackSize(16).sprite("bucket").build();
    public static final Item MILK = Item.builder(204, "milk")
            .displayName("Milk").stackSize(1).food(4, 0.4f).sprite("milk").build();

    // --- [GP-009] Slime ---
    public static final Item SLIME_BALL = Item.builder(205, "slime_ball")
            .displayName("Slime Ball").stackSize(64).sprite("slime_ball").build();

    // --- [POT] Potions and brewing ---
    public static final Item WATER_BOTTLE = Item.builder(206, "water_bottle")
            .displayName("Water Bottle").stackSize(1).sprite("water_bottle").build();
    public static final Item POTION_HEALING = Item.builder(207, "potion_healing")
            .displayName("Potion of Regeneration").stackSize(1)
            .potion(StatusEffect.REGENERATION).sprite("potion_healing").build();
    public static final Item POTION_SPEED = Item.builder(208, "potion_speed")
            .displayName("Potion of Speed").stackSize(1)
            .potion(StatusEffect.SPEED).sprite("potion_speed").build();
    public static final Item POTION_STRENGTH = Item.builder(209, "potion_strength")
            .displayName("Potion of Strength").stackSize(1)
            .potion(StatusEffect.STRENGTH).sprite("potion_strength").build();
    public static final Item POTION_FIRE_RESISTANCE = Item.builder(210, "potion_fire_resistance")
            .displayName("Potion of Fire Resistance").stackSize(1)
            .potion(StatusEffect.FIRE_RESISTANCE).sprite("potion_fire_resistance").build();

    // --- [POT] Brewing ingredients ---
    public static final Item SUGAR = Item.builder(211, "sugar")
            .displayName("Sugar").stackSize(64).sprite("sugar").build();

    // --- [ECO] Economy & space metals ---
    public static final Item EMERALD = Item.builder(212, "emerald")
            .displayName("Emerald").sprite("emerald").build();
    public static final Item METEORITE_INGOT = Item.builder(213, "meteorite_ingot")
            .displayName("Meteorite Ingot").sprite("meteorite_ingot").build();

    // Meteorite tools
    public static final Item METEORITE_PICKAXE = Item.builder(214, "meteorite_pickaxe")
            .displayName("Meteorite Pickaxe").stackSize(1)
            .tier(ToolTier.METEORITE).toolType(ToolType.PICKAXE).attackSpeed(1.2f)
            .sprite("meteorite_pickaxe").build();
    public static final Item METEORITE_AXE = Item.builder(215, "meteorite_axe")
            .displayName("Meteorite Axe").stackSize(1)
            .tier(ToolTier.METEORITE).toolType(ToolType.AXE).attackSpeed(1.1f)
            .sprite("meteorite_axe").build();
    public static final Item METEORITE_SHOVEL = Item.builder(216, "meteorite_shovel")
            .displayName("Meteorite Shovel").stackSize(1)
            .tier(ToolTier.METEORITE).toolType(ToolType.SHOVEL).attackSpeed(1.0f)
            .sprite("meteorite_shovel").build();
    public static final Item METEORITE_SWORD = Item.builder(217, "meteorite_sword")
            .displayName("Meteorite Sword").stackSize(1)
            .tier(ToolTier.METEORITE).toolType(ToolType.SWORD).attackDamage(8).attackSpeed(1.6f)
            .sprite("meteorite_sword").build();
    public static final Item METEORITE_HOE = Item.builder(218, "meteorite_hoe")
            .displayName("Meteorite Hoe").stackSize(1)
            .tier(ToolTier.METEORITE).toolType(ToolType.HOE).attackSpeed(1.0f)
            .sprite("meteorite_hoe").build();

    // Meteorite armour
    public static final Item METEORITE_HELMET = Item.builder(219, "meteorite_helmet")
            .displayName("Meteorite Helmet").stackSize(1)
            .armorSlot(ArmorSlot.HELMET).armorPoints(4)
            .sprite("meteorite_helmet").build();
    public static final Item METEORITE_CHESTPLATE = Item.builder(220, "meteorite_chestplate")
            .displayName("Meteorite Chestplate").stackSize(1)
            .armorSlot(ArmorSlot.CHESTPLATE).armorPoints(9)
            .sprite("meteorite_chestplate").build();
    public static final Item METEORITE_LEGGINGS = Item.builder(221, "meteorite_leggings")
            .displayName("Meteorite Leggings").stackSize(1)
            .armorSlot(ArmorSlot.LEGGINGS).armorPoints(7)
            .sprite("meteorite_leggings").build();
    public static final Item METEORITE_BOOTS = Item.builder(222, "meteorite_boots")
            .displayName("Meteorite Boots").stackSize(1)
            .armorSlot(ArmorSlot.BOOTS).armorPoints(4)
            .sprite("meteorite_boots").build();

    /** All items indexed by ID for lookup. */
    private static final Item[] BY_ID = new Item[256];
    static {
        for (Item item : new Item[]{
            STICK, COAL, IRON_INGOT, GOLD_INGOT, DIAMOND, LEATHER, STRING, FEATHER,
            FLINT, GUNPOWDER, WHEAT, EGG,
            WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SHOVEL, WOODEN_SWORD, WOODEN_HOE,
            STONE_PICKAXE, STONE_AXE, STONE_SHOVEL, STONE_SWORD, STONE_HOE,
            IRON_PICKAXE, IRON_AXE, IRON_SHOVEL, IRON_SWORD, IRON_HOE,
            DIAMOND_PICKAXE, DIAMOND_AXE, DIAMOND_SHOVEL, DIAMOND_SWORD, DIAMOND_HOE,
            GOLDEN_PICKAXE, GOLDEN_AXE, GOLDEN_SHOVEL, GOLDEN_SWORD, GOLDEN_HOE,
            LEATHER_HELMET, LEATHER_CHESTPLATE, LEATHER_LEGGINGS, LEATHER_BOOTS,
            IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS,
            DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS,
            BREAD, RAW_PORK, COOKED_PORK, RAW_BEEF, COOKED_BEEF,
            RAW_CHICKEN, COOKED_CHICKEN, RAW_MUTTON, COOKED_MUTTON,
            APPLE_ITEM, BOW, ARROW, TORCH_ITEM, CRAFTING_TABLE_ITEM,
            FURNACE_ITEM, CHEST_ITEM, ROTTEN_FLESH, BONE, SPIDER_EYE,
            FLINT_AND_STEEL, RESIN, WAX,
            BOOK, LAPIS_LAZULI, ENCHANTING_TABLE_ITEM,
            SHEARS, BUCKET, MILK,
            SLIME_BALL, WATER_BOTTLE, POTION_HEALING, POTION_SPEED,
            POTION_STRENGTH, POTION_FIRE_RESISTANCE, SUGAR,
            EMERALD, METEORITE_INGOT,
            METEORITE_PICKAXE, METEORITE_AXE, METEORITE_SHOVEL, METEORITE_SWORD, METEORITE_HOE,
            METEORITE_HELMET, METEORITE_CHESTPLATE, METEORITE_LEGGINGS, METEORITE_BOOTS,
            FISHING_ROD, RAW_FISH, COOKED_FISH
        }) {
            if (item != null) {
                BY_ID[item.id & 0xFF] = item;
            }
        }
    }

    /** Get item by ID. */
    public static Item getById(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
    }

    /** Every registered item, in registry order (creative inventory listing). */
    public static Item[] all() {
        java.util.List<Item> out = new java.util.ArrayList<>();
        for (Item item : BY_ID) if (item != null) out.add(item);
        return out.toArray(new Item[0]);
    }

    private ItemRegistry() {}
}
