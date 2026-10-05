package com.voxelgame.item;

import com.voxelgame.world.BlockType;

/**
 * All crafting recipes for the game.
 */
public final class RecipeRegistry {

    public static final Recipe[] RECIPES = concat(
        com.voxelgame.world.plank.PlankResourceGenerator.buildRecipes(),
        new Recipe[] {
        // --- Basic ---
        Recipe.shaped2x2(new ItemStack(BlockType.OAK_PLANKS, 4),
            block(BlockType.OAK_LOG), null, null, null),

        Recipe.shaped2x2(new ItemStack(ItemRegistry.STICK, 4),
            block(BlockType.OAK_PLANKS), null,
            block(BlockType.OAK_PLANKS), null),

        Recipe.shaped(new ItemStack(ItemRegistry.STICK, 4),
            block(BlockType.OAK_PLANKS), null, null,
            block(BlockType.OAK_PLANKS), null, null,
            null, null, null),

        // --- Crafting Table ---
        Recipe.shaped2x2(new ItemStack(ItemRegistry.CRAFTING_TABLE_ITEM),
            block(BlockType.OAK_PLANKS), block(BlockType.OAK_PLANKS),
            block(BlockType.OAK_PLANKS), block(BlockType.OAK_PLANKS)),

        // --- Furnace ---
        Recipe.shaped(new ItemStack(ItemRegistry.FURNACE_ITEM),
            block(BlockType.COBBLESTONE), block(BlockType.COBBLESTONE), block(BlockType.COBBLESTONE),
            block(BlockType.COBBLESTONE), null, block(BlockType.COBBLESTONE),
            block(BlockType.COBBLESTONE), block(BlockType.COBBLESTONE), block(BlockType.COBBLESTONE)),

        // --- Chest ---
        Recipe.shaped(new ItemStack(ItemRegistry.CHEST_ITEM),
            block(BlockType.OAK_PLANKS), block(BlockType.OAK_PLANKS), block(BlockType.OAK_PLANKS),
            block(BlockType.OAK_PLANKS), null, block(BlockType.OAK_PLANKS),
            block(BlockType.OAK_PLANKS), block(BlockType.OAK_PLANKS), block(BlockType.OAK_PLANKS)),

        // --- Bed (3 wool + 3 planks) ---
        Recipe.shaped(new ItemStack(BlockType.BED, 1),
            null, null, null,
            block(BlockType.WHITE_WOOL), block(BlockType.WHITE_WOOL), block(BlockType.WHITE_WOOL),
            block(BlockType.OAK_PLANKS), block(BlockType.OAK_PLANKS), block(BlockType.OAK_PLANKS)),

        // --- Torch (4 from 1 coal + 1 stick) ---
        Recipe.shaped(new ItemStack(ItemRegistry.TORCH_ITEM, 4),
            item(ItemRegistry.COAL), null, null,
            item(ItemRegistry.STICK), null, null,
            null, null, null),

        // --- Wooden Pickaxe ---
        Recipe.shaped(new ItemStack(ItemRegistry.WOODEN_PICKAXE),
            block(BlockType.OAK_PLANKS), block(BlockType.OAK_PLANKS), block(BlockType.OAK_PLANKS),
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Wooden Axe ---
        Recipe.shaped(new ItemStack(ItemRegistry.WOODEN_AXE),
            block(BlockType.OAK_PLANKS), block(BlockType.OAK_PLANKS), null,
            block(BlockType.OAK_PLANKS), item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Wooden Shovel ---
        Recipe.shaped(new ItemStack(ItemRegistry.WOODEN_SHOVEL),
            null, block(BlockType.OAK_PLANKS), null,
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Wooden Sword ---
        Recipe.shaped(new ItemStack(ItemRegistry.WOODEN_SWORD),
            null, block(BlockType.OAK_PLANKS), null,
            null, block(BlockType.OAK_PLANKS), null,
            null, item(ItemRegistry.STICK), null),

        // --- Wooden Hoe ---
        Recipe.shaped(new ItemStack(ItemRegistry.WOODEN_HOE),
            block(BlockType.OAK_PLANKS), block(BlockType.OAK_PLANKS), null,
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Stone Pickaxe ---
        Recipe.shaped(new ItemStack(ItemRegistry.STONE_PICKAXE),
            block(BlockType.COBBLESTONE), block(BlockType.COBBLESTONE), block(BlockType.COBBLESTONE),
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Stone Axe ---
        Recipe.shaped(new ItemStack(ItemRegistry.STONE_AXE),
            block(BlockType.COBBLESTONE), block(BlockType.COBBLESTONE), null,
            block(BlockType.COBBLESTONE), item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Stone Shovel ---
        Recipe.shaped(new ItemStack(ItemRegistry.STONE_SHOVEL),
            null, block(BlockType.COBBLESTONE), null,
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Stone Sword ---
        Recipe.shaped(new ItemStack(ItemRegistry.STONE_SWORD),
            null, block(BlockType.COBBLESTONE), null,
            null, block(BlockType.COBBLESTONE), null,
            null, item(ItemRegistry.STICK), null),

        // --- Stone Hoe ---
        Recipe.shaped(new ItemStack(ItemRegistry.STONE_HOE),
            block(BlockType.COBBLESTONE), block(BlockType.COBBLESTONE), null,
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Iron Pickaxe ---
        Recipe.shaped(new ItemStack(ItemRegistry.IRON_PICKAXE),
            item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT),
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Iron Axe ---
        Recipe.shaped(new ItemStack(ItemRegistry.IRON_AXE),
            item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT), null,
            item(ItemRegistry.IRON_INGOT), item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Iron Shovel ---
        Recipe.shaped(new ItemStack(ItemRegistry.IRON_SHOVEL),
            null, item(ItemRegistry.IRON_INGOT), null,
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Iron Sword ---
        Recipe.shaped(new ItemStack(ItemRegistry.IRON_SWORD),
            null, item(ItemRegistry.IRON_INGOT), null,
            null, item(ItemRegistry.IRON_INGOT), null,
            null, item(ItemRegistry.STICK), null),

        // --- Iron Hoe ---
        Recipe.shaped(new ItemStack(ItemRegistry.IRON_HOE),
            item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT), null,
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Diamond Pickaxe ---
        Recipe.shaped(new ItemStack(ItemRegistry.DIAMOND_PICKAXE),
            item(ItemRegistry.DIAMOND), item(ItemRegistry.DIAMOND), item(ItemRegistry.DIAMOND),
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Diamond Axe ---
        Recipe.shaped(new ItemStack(ItemRegistry.DIAMOND_AXE),
            item(ItemRegistry.DIAMOND), item(ItemRegistry.DIAMOND), null,
            item(ItemRegistry.DIAMOND), item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Diamond Shovel ---
        Recipe.shaped(new ItemStack(ItemRegistry.DIAMOND_SHOVEL),
            null, item(ItemRegistry.DIAMOND), null,
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Diamond Sword ---
        Recipe.shaped(new ItemStack(ItemRegistry.DIAMOND_SWORD),
            null, item(ItemRegistry.DIAMOND), null,
            null, item(ItemRegistry.DIAMOND), null,
            null, item(ItemRegistry.STICK), null),

        // --- Diamond Hoe ---
        Recipe.shaped(new ItemStack(ItemRegistry.DIAMOND_HOE),
            item(ItemRegistry.DIAMOND), item(ItemRegistry.DIAMOND), null,
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Gold Pickaxe ---
        Recipe.shaped(new ItemStack(ItemRegistry.GOLDEN_PICKAXE),
            item(ItemRegistry.GOLD_INGOT), item(ItemRegistry.GOLD_INGOT), item(ItemRegistry.GOLD_INGOT),
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- Gold Sword ---
        Recipe.shaped(new ItemStack(ItemRegistry.GOLDEN_SWORD),
            null, item(ItemRegistry.GOLD_INGOT), null,
            null, item(ItemRegistry.GOLD_INGOT), null,
            null, item(ItemRegistry.STICK), null),

        // --- Leather Armor ---
        Recipe.shaped(new ItemStack(ItemRegistry.LEATHER_HELMET),
            item(ItemRegistry.LEATHER), item(ItemRegistry.LEATHER), item(ItemRegistry.LEATHER),
            item(ItemRegistry.LEATHER), null, item(ItemRegistry.LEATHER),
            null, null, null),

        Recipe.shaped(new ItemStack(ItemRegistry.LEATHER_CHESTPLATE),
            item(ItemRegistry.LEATHER), null, item(ItemRegistry.LEATHER),
            item(ItemRegistry.LEATHER), item(ItemRegistry.LEATHER), item(ItemRegistry.LEATHER),
            item(ItemRegistry.LEATHER), item(ItemRegistry.LEATHER), item(ItemRegistry.LEATHER)),

        Recipe.shaped(new ItemStack(ItemRegistry.LEATHER_LEGGINGS),
            item(ItemRegistry.LEATHER), item(ItemRegistry.LEATHER), item(ItemRegistry.LEATHER),
            item(ItemRegistry.LEATHER), null, item(ItemRegistry.LEATHER),
            item(ItemRegistry.LEATHER), null, item(ItemRegistry.LEATHER)),

        Recipe.shaped(new ItemStack(ItemRegistry.LEATHER_BOOTS),
            null, null, null,
            item(ItemRegistry.LEATHER), null, item(ItemRegistry.LEATHER),
            item(ItemRegistry.LEATHER), null, item(ItemRegistry.LEATHER)),

        // --- Iron Armor ---
        Recipe.shaped(new ItemStack(ItemRegistry.IRON_HELMET),
            item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT),
            item(ItemRegistry.IRON_INGOT), null, item(ItemRegistry.IRON_INGOT),
            null, null, null),

        Recipe.shaped(new ItemStack(ItemRegistry.IRON_CHESTPLATE),
            item(ItemRegistry.IRON_INGOT), null, item(ItemRegistry.IRON_INGOT),
            item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT),
            item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT)),

        Recipe.shaped(new ItemStack(ItemRegistry.IRON_LEGGINGS),
            item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT),
            item(ItemRegistry.IRON_INGOT), null, item(ItemRegistry.IRON_INGOT),
            item(ItemRegistry.IRON_INGOT), null, item(ItemRegistry.IRON_INGOT)),

        Recipe.shaped(new ItemStack(ItemRegistry.IRON_BOOTS),
            null, null, null,
            item(ItemRegistry.IRON_INGOT), null, item(ItemRegistry.IRON_INGOT),
            item(ItemRegistry.IRON_INGOT), null, item(ItemRegistry.IRON_INGOT)),

        // --- Diamond Armor ---
        Recipe.shaped(new ItemStack(ItemRegistry.DIAMOND_HELMET),
            item(ItemRegistry.DIAMOND), item(ItemRegistry.DIAMOND), item(ItemRegistry.DIAMOND),
            item(ItemRegistry.DIAMOND), null, item(ItemRegistry.DIAMOND),
            null, null, null),

        Recipe.shaped(new ItemStack(ItemRegistry.DIAMOND_CHESTPLATE),
            item(ItemRegistry.DIAMOND), null, item(ItemRegistry.DIAMOND),
            item(ItemRegistry.DIAMOND), item(ItemRegistry.DIAMOND), item(ItemRegistry.DIAMOND),
            item(ItemRegistry.DIAMOND), item(ItemRegistry.DIAMOND), item(ItemRegistry.DIAMOND)),

        Recipe.shaped(new ItemStack(ItemRegistry.DIAMOND_LEGGINGS),
            item(ItemRegistry.DIAMOND), item(ItemRegistry.DIAMOND), item(ItemRegistry.DIAMOND),
            item(ItemRegistry.DIAMOND), null, item(ItemRegistry.DIAMOND),
            item(ItemRegistry.DIAMOND), null, item(ItemRegistry.DIAMOND)),

        Recipe.shaped(new ItemStack(ItemRegistry.DIAMOND_BOOTS),
            null, null, null,
            item(ItemRegistry.DIAMOND), null, item(ItemRegistry.DIAMOND),
            item(ItemRegistry.DIAMOND), null, item(ItemRegistry.DIAMOND)),

        // --- Bread (3 wheat) ---
        Recipe.shaped(new ItemStack(ItemRegistry.BREAD),
            null, null, null,
            item(ItemRegistry.WHEAT), item(ItemRegistry.WHEAT), item(ItemRegistry.WHEAT),
            null, null, null),

        // --- Bow ---
        Recipe.shaped(new ItemStack(ItemRegistry.BOW),
            null, item(ItemRegistry.STICK), item(ItemRegistry.STRING),
            null, null, item(ItemRegistry.STRING),
            null, item(ItemRegistry.STICK), item(ItemRegistry.STRING)),

        // --- [FISH] Fishing rod (3 sticks + 2 string, vanilla layout) ---
        Recipe.shaped(new ItemStack(ItemRegistry.FISHING_ROD),
            null, null, item(ItemRegistry.STICK),
            null, item(ItemRegistry.STICK), item(ItemRegistry.STRING),
            item(ItemRegistry.STICK), null, item(ItemRegistry.STRING)),

        // --- Arrow ---
        Recipe.shaped(new ItemStack(ItemRegistry.ARROW, 4),
            null, item(ItemRegistry.FLINT), null,
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.FEATHER), null),

        // --- Flint and Steel ---
        Recipe.shaped(new ItemStack(ItemRegistry.FLINT_AND_STEEL),
            null, item(ItemRegistry.FLINT), null,
            null, item(ItemRegistry.IRON_INGOT), null,
            null, null, null),

        // --- [ENCH] Book (leather + planks) ---
        Recipe.shaped2x2(new ItemStack(ItemRegistry.BOOK),
            item(ItemRegistry.LEATHER), item(ItemRegistry.LEATHER),
            block(BlockType.OAK_PLANKS), block(BlockType.OAK_PLANKS)),

        // --- [ENCH] Enchanting Table (book + 2 diamonds + 4 obsidian) ---
        Recipe.shaped(new ItemStack(ItemRegistry.ENCHANTING_TABLE_ITEM),
            null, item(ItemRegistry.BOOK), null,
            block(BlockType.OBSIDIAN), item(ItemRegistry.DIAMOND), block(BlockType.OBSIDIAN),
            block(BlockType.OBSIDIAN), item(ItemRegistry.DIAMOND), block(BlockType.OBSIDIAN)),

        // --- [TRAN] Glass panes: 2x3 of glass -> 16 panes ---
        Recipe.shaped(new ItemStack(BlockType.GLASS_PANE, 16),
            block(BlockType.GLASS), block(BlockType.GLASS), block(BlockType.GLASS),
            block(BlockType.GLASS), block(BlockType.GLASS), block(BlockType.GLASS),
            null, null, null),

        // --- [ANM] Shears: two iron ingots ---
        Recipe.shaped2x2(new ItemStack(ItemRegistry.SHEARS),
            item(ItemRegistry.IRON_INGOT), null,
            null, item(ItemRegistry.IRON_INGOT)),

        // --- [ANM] Bucket: three iron ingots in a V ---
        Recipe.shaped(new ItemStack(ItemRegistry.BUCKET),
            null, item(ItemRegistry.IRON_INGOT), null,
            null, item(ItemRegistry.IRON_INGOT), null,
            null, item(ItemRegistry.IRON_INGOT), null),

        // --- [GP-009] Slime block: four slime balls in a square ---
        Recipe.shaped2x2(new ItemStack(BlockType.SLIME_BLOCK, 1),
            item(ItemRegistry.SLIME_BALL), item(ItemRegistry.SLIME_BALL),
            item(ItemRegistry.SLIME_BALL), item(ItemRegistry.SLIME_BALL)),

        // --- [RKT] Rocket launch pad: iron ingots in a ring ---
        Recipe.shaped(new ItemStack(BlockType.ROCKET_LAUNCH_PAD, 1),
            item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT),
            item(ItemRegistry.IRON_INGOT), null, item(ItemRegistry.IRON_INGOT),
            item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT)),

        // --- [RKT] Rocket engine: iron ingots in an X ---
        Recipe.shaped(new ItemStack(BlockType.ROCKET_ENGINE, 1),
            null, item(ItemRegistry.IRON_INGOT), null,
            item(ItemRegistry.IRON_INGOT), null, item(ItemRegistry.IRON_INGOT),
            null, item(ItemRegistry.IRON_INGOT), null),

        // --- [RKT] Rocket fuel tank: coal core in an iron cross ---
        Recipe.shaped(new ItemStack(BlockType.ROCKET_FUEL, 1),
            null, item(ItemRegistry.COAL), null,
            item(ItemRegistry.COAL), item(ItemRegistry.IRON_INGOT), item(ItemRegistry.COAL),
            null, item(ItemRegistry.COAL), null),

        // --- [RKT] Rocket body: three iron ingot plates ---
        Recipe.shaped(new ItemStack(BlockType.ROCKET_BODY, 1),
            null, item(ItemRegistry.IRON_INGOT), null,
            null, item(ItemRegistry.IRON_INGOT), null,
            null, item(ItemRegistry.IRON_INGOT), null),

        // --- [RKT] Rocket window: glass porthole in an iron frame ---
        Recipe.shaped2x2(new ItemStack(BlockType.ROCKET_WINDOW, 1),
            item(ItemRegistry.IRON_INGOT), block(BlockType.GLASS),
            block(BlockType.GLASS), item(ItemRegistry.IRON_INGOT)),

        // --- [RKT] Rocket cone: iron ingots in a cap ---
        Recipe.shaped(new ItemStack(BlockType.ROCKET_CONE, 1),
            null, item(ItemRegistry.IRON_INGOT), null,
            item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT), item(ItemRegistry.IRON_INGOT),
            null, null, null),

        // --- [POT] Brewing: water bottle + ingredient -> potion ---
        Recipe.shapeless(new ItemStack(ItemRegistry.POTION_SPEED),
            item(ItemRegistry.WATER_BOTTLE), item(ItemRegistry.SUGAR)),
        Recipe.shapeless(new ItemStack(ItemRegistry.POTION_HEALING),
            item(ItemRegistry.WATER_BOTTLE), item(ItemRegistry.APPLE_ITEM)),
        Recipe.shapeless(new ItemStack(ItemRegistry.POTION_STRENGTH),
            item(ItemRegistry.WATER_BOTTLE), item(ItemRegistry.IRON_INGOT)),
        Recipe.shapeless(new ItemStack(ItemRegistry.POTION_FIRE_RESISTANCE),
            item(ItemRegistry.WATER_BOTTLE), item(ItemRegistry.RESIN)),

        // --- [ECO] Emerald block: 9 emeralds both ways ---
        Recipe.shaped(new ItemStack(BlockType.EMERALD_BLOCK, 1),
            item(ItemRegistry.EMERALD), item(ItemRegistry.EMERALD), item(ItemRegistry.EMERALD),
            item(ItemRegistry.EMERALD), item(ItemRegistry.EMERALD), item(ItemRegistry.EMERALD),
            item(ItemRegistry.EMERALD), item(ItemRegistry.EMERALD), item(ItemRegistry.EMERALD)),
        Recipe.shapeless(new ItemStack(ItemRegistry.EMERALD, 9),
            new ItemStack(BlockType.EMERALD_BLOCK, 1)),

        // --- [ECO] Meteorite tools ---
        Recipe.shaped(new ItemStack(ItemRegistry.METEORITE_PICKAXE),
            item(ItemRegistry.METEORITE_INGOT), item(ItemRegistry.METEORITE_INGOT), item(ItemRegistry.METEORITE_INGOT),
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),
        Recipe.shaped(new ItemStack(ItemRegistry.METEORITE_AXE),
            item(ItemRegistry.METEORITE_INGOT), item(ItemRegistry.METEORITE_INGOT), null,
            item(ItemRegistry.METEORITE_INGOT), item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),
        Recipe.shaped(new ItemStack(ItemRegistry.METEORITE_SHOVEL),
            null, item(ItemRegistry.METEORITE_INGOT), null,
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),
        Recipe.shaped(new ItemStack(ItemRegistry.METEORITE_SWORD),
            null, item(ItemRegistry.METEORITE_INGOT), null,
            null, item(ItemRegistry.METEORITE_INGOT), null,
            null, item(ItemRegistry.STICK), null),
        Recipe.shaped(new ItemStack(ItemRegistry.METEORITE_HOE),
            item(ItemRegistry.METEORITE_INGOT), item(ItemRegistry.METEORITE_INGOT), null,
            null, item(ItemRegistry.STICK), null,
            null, item(ItemRegistry.STICK), null),

        // --- [ECO] Meteorite armour ---
        Recipe.shaped(new ItemStack(ItemRegistry.METEORITE_HELMET),
            item(ItemRegistry.METEORITE_INGOT), item(ItemRegistry.METEORITE_INGOT), item(ItemRegistry.METEORITE_INGOT),
            item(ItemRegistry.METEORITE_INGOT), null, item(ItemRegistry.METEORITE_INGOT),
            null, null, null),
        Recipe.shaped(new ItemStack(ItemRegistry.METEORITE_CHESTPLATE),
            item(ItemRegistry.METEORITE_INGOT), null, item(ItemRegistry.METEORITE_INGOT),
            item(ItemRegistry.METEORITE_INGOT), item(ItemRegistry.METEORITE_INGOT), item(ItemRegistry.METEORITE_INGOT),
            item(ItemRegistry.METEORITE_INGOT), item(ItemRegistry.METEORITE_INGOT), item(ItemRegistry.METEORITE_INGOT)),
        Recipe.shaped(new ItemStack(ItemRegistry.METEORITE_LEGGINGS),
            item(ItemRegistry.METEORITE_INGOT), item(ItemRegistry.METEORITE_INGOT), item(ItemRegistry.METEORITE_INGOT),
            item(ItemRegistry.METEORITE_INGOT), null, item(ItemRegistry.METEORITE_INGOT),
            item(ItemRegistry.METEORITE_INGOT), null, item(ItemRegistry.METEORITE_INGOT)),
        Recipe.shaped(new ItemStack(ItemRegistry.METEORITE_BOOTS),
            null, null, null,
            item(ItemRegistry.METEORITE_INGOT), null, item(ItemRegistry.METEORITE_INGOT),
            item(ItemRegistry.METEORITE_INGOT), null, item(ItemRegistry.METEORITE_INGOT)),

        // --- [ECO] Emerald vault: a permanent trading post ---
        Recipe.shaped(new ItemStack(BlockType.EMERALD_VAULT, 1),
            item(ItemRegistry.EMERALD), block(BlockType.GOLD_BLOCK), item(ItemRegistry.EMERALD),
            block(BlockType.OBSIDIAN), item(ItemRegistry.EMERALD), block(BlockType.OBSIDIAN),
            block(BlockType.OBSIDIAN), block(BlockType.OBSIDIAN), block(BlockType.OBSIDIAN)),
        });

    private static Recipe[] concat(Recipe[] a, Recipe[] b) {
        Recipe[] out = new Recipe[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static ItemStack block(BlockType t) { return new ItemStack(t, 1); }
    private static ItemStack item(Item i) { return new ItemStack(i, 1); }

    private RecipeRegistry() {}

    // --- Smelting Recipes ---

    /** Smelting recipe: input item -> output item. */
    public record SmeltingRecipe(int inputId, int outputId, int outputCount) {}

    /** Fuel burn times (in ticks). */
    public record FuelItem(int itemId, int burnTicks) {}

    public static final SmeltingRecipe[] SMELTING_RECIPES = {
        // Ores -> Ingots
        new SmeltingRecipe(BlockType.IRON_ORE.id, ItemRegistry.IRON_INGOT.id, 1),
        new SmeltingRecipe(BlockType.GOLD_ORE.id, ItemRegistry.GOLD_INGOT.id, 1),
        // Food
        new SmeltingRecipe(ItemRegistry.RAW_PORK.id, ItemRegistry.COOKED_PORK.id, 1),
        new SmeltingRecipe(ItemRegistry.RAW_BEEF.id, ItemRegistry.COOKED_BEEF.id, 1),
        new SmeltingRecipe(ItemRegistry.RAW_CHICKEN.id, ItemRegistry.COOKED_CHICKEN.id, 1),
        new SmeltingRecipe(ItemRegistry.RAW_MUTTON.id, ItemRegistry.COOKED_MUTTON.id, 1),
        // [FISH] Fresh catch dries into a proper meal
        new SmeltingRecipe(ItemRegistry.RAW_FISH.id, ItemRegistry.COOKED_FISH.id, 1),
        // Other
        new SmeltingRecipe(BlockType.SAND.id, BlockType.GLASS.id, 1),
        new SmeltingRecipe(BlockType.COBBLESTONE.id, BlockType.STONE.id, 1),
        new SmeltingRecipe(BlockType.CLAY.id, BlockType.BRICK.id, 1),
        // [GP-PLANKS] Drying raw planks in the furnace
        new SmeltingRecipe(BlockType.OAK_RAW_PLANKS.id, BlockType.OAK_DRIED_PLANKS.id, 1),
        new SmeltingRecipe(BlockType.SPRUCE_RAW_PLANKS.id, BlockType.SPRUCE_DRIED_PLANKS.id, 1),
        new SmeltingRecipe(BlockType.BIRCH_RAW_PLANKS.id, BlockType.BIRCH_DRIED_PLANKS.id, 1),
        new SmeltingRecipe(BlockType.JUNGLE_RAW_PLANKS.id, BlockType.JUNGLE_DRIED_PLANKS.id, 1),
        // [ECO] Meteorite ore blasts down into ingots in the furnace
        new SmeltingRecipe(BlockType.METEORITE_ORE.id, ItemRegistry.METEORITE_INGOT.id, 1),
    };

    /** Get smelting result for an item. */
    public static ItemStack getSmeltingResult(int inputId) {
        for (SmeltingRecipe recipe : SMELTING_RECIPES) {
            if (recipe.inputId() == inputId) {
                Item item = ItemRegistry.getById(recipe.outputId());
                if (item != null) {
                    return new ItemStack(item, recipe.outputCount());
                }
                BlockType block = BlockType.fromId(recipe.outputId());
                if (block != null && block != BlockType.AIR) {
                    return new ItemStack(block, recipe.outputCount());
                }
            }
        }
        return null;
    }
}
