package com.voxelgame.chat.commands;

import com.voxelgame.item.Inventory;
import com.voxelgame.item.Item;
import com.voxelgame.item.ItemRegistry;
import com.voxelgame.world.BlockType;

import java.util.HashMap;
import java.util.Map;

/**
 * /give <item> [count] — дать предмет в инвентарь.
 *
 * Item names match the ItemRegistry field names (lowercase), e.g.:
 * /give diamond 64
 * /give iron_ingot 16
 * /give wooden_pickaxe
 */
public class GiveCommand implements Command {

    /** Access to the player inventory — set by Game. */
    public static Inventory currentInventory;

    /** Name → Item lookup built from ItemRegistry. */
    private static final Map<String, Item> ITEM_NAMES = new HashMap<>();
    private static final Map<String, BlockType> BLOCK_NAMES = new HashMap<>();

    static {
        // Register all items by their registry name
        for (Item item : new Item[]{
            ItemRegistry.STICK, ItemRegistry.COAL, ItemRegistry.IRON_INGOT,
            ItemRegistry.GOLD_INGOT, ItemRegistry.DIAMOND, ItemRegistry.LEATHER,
            ItemRegistry.STRING, ItemRegistry.FEATHER, ItemRegistry.FLINT,
            ItemRegistry.GUNPOWDER, ItemRegistry.WHEAT, ItemRegistry.EGG,
            ItemRegistry.WOODEN_PICKAXE, ItemRegistry.WOODEN_AXE,
            ItemRegistry.WOODEN_SHOVEL, ItemRegistry.WOODEN_SWORD,
            ItemRegistry.WOODEN_HOE,
            ItemRegistry.STONE_PICKAXE, ItemRegistry.STONE_AXE,
            ItemRegistry.STONE_SHOVEL, ItemRegistry.STONE_SWORD,
            ItemRegistry.STONE_HOE,
            ItemRegistry.IRON_PICKAXE, ItemRegistry.IRON_AXE,
            ItemRegistry.IRON_SHOVEL, ItemRegistry.IRON_SWORD,
            ItemRegistry.IRON_HOE,
            ItemRegistry.DIAMOND_PICKAXE, ItemRegistry.DIAMOND_AXE,
            ItemRegistry.DIAMOND_SHOVEL, ItemRegistry.DIAMOND_SWORD,
            ItemRegistry.DIAMOND_HOE,
            ItemRegistry.GOLDEN_PICKAXE, ItemRegistry.GOLDEN_AXE,
            ItemRegistry.GOLDEN_SHOVEL, ItemRegistry.GOLDEN_SWORD,
            ItemRegistry.GOLDEN_HOE,
            ItemRegistry.BREAD, ItemRegistry.RAW_PORK, ItemRegistry.COOKED_PORK,
            ItemRegistry.RAW_BEEF, ItemRegistry.COOKED_BEEF,
            ItemRegistry.RAW_CHICKEN, ItemRegistry.COOKED_CHICKEN,
            ItemRegistry.RAW_MUTTON, ItemRegistry.COOKED_MUTTON,
            ItemRegistry.APPLE_ITEM,
            ItemRegistry.BOW, ItemRegistry.ARROW,
            ItemRegistry.TORCH_ITEM, ItemRegistry.CRAFTING_TABLE_ITEM,
            ItemRegistry.FURNACE_ITEM, ItemRegistry.CHEST_ITEM,
            ItemRegistry.ROTTEN_FLESH, ItemRegistry.BONE, ItemRegistry.SPIDER_EYE,
            ItemRegistry.LEATHER_HELMET, ItemRegistry.LEATHER_CHESTPLATE,
            ItemRegistry.LEATHER_LEGGINGS, ItemRegistry.LEATHER_BOOTS,
            ItemRegistry.IRON_HELMET, ItemRegistry.IRON_CHESTPLATE,
            ItemRegistry.IRON_LEGGINGS, ItemRegistry.IRON_BOOTS,
            ItemRegistry.DIAMOND_HELMET, ItemRegistry.DIAMOND_CHESTPLATE,
            ItemRegistry.DIAMOND_LEGGINGS, ItemRegistry.DIAMOND_BOOTS,
        }) {
            if (item != null) {
                ITEM_NAMES.put(item.name.toLowerCase(), item);
            }
        }
        // Register blocks by name
        for (BlockType bt : BlockType.values()) {
            BLOCK_NAMES.put(bt.name.toLowerCase(), bt);
        }
    }

    @Override
    public String getName() { return "give"; }

    @Override
    public String getUsage() { return "/give <item> [count]"; }

    @Override
    public String getDescription() { return "дать предмет"; }

    @Override
    public String execute(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("укажи предмет");
        }

        if (currentInventory == null) {
            return "Нет активного инвентаря.";
        }

        String itemName = args[0].toLowerCase();
        int count = 1;
        if (args.length >= 2) {
            try {
                count = Integer.parseInt(args[1]);
                if (count < 1) count = 1;
                if (count > 64) count = 64;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("количество должно быть числом");
            }
        }

        // Try item first
        Item item = ITEM_NAMES.get(itemName);
        if (item != null) {
            currentInventory.addItem(item, count);
            return String.format("Выдано: %s x%d", item.displayName, count);
        }

        // Try block
        BlockType block = BLOCK_NAMES.get(itemName);
        if (block != null && block != BlockType.AIR) {
            currentInventory.addItem(block, count);
            return String.format("Выдано: %s x%d", block.name, count);
        }

        throw new IllegalArgumentException("неизвестный предмет '" + itemName + "'. Попробуй: diamond, iron_ingot, wooden_pickaxe, stone, ...");
    }
}
