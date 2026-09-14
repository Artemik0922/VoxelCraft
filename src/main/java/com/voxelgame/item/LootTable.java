package com.voxelgame.item;

import com.voxelgame.world.BlockType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Weighted loot tables for container fills.
 *
 * An entry names an item (or block), a count range and a relative weight.
 * A roll picks {@code rolls} independent winners, so rarer goods appear only
 * occasionally while the bread-and-butter stack shows up in most chests.
 *
 * Everything is deterministic given the Random the caller passes, which lets
 * the world seed rolls by chest position: the same chest always generates the
 * same contents, different chests differ.
 */
public final class LootTable {

    /** One weighted entry of the table. */
    public static final class Entry {
        final Item item;        // null when the entry is a block
        final BlockType block;
        final int min, max, weight;

        Entry(Item item, BlockType block, int min, int max, int weight) {
            this.item = item;
            this.block = block;
            this.min = min;
            this.max = max;
            this.weight = weight;
        }

        ItemStack stack(int count) {
            return item != null ? new ItemStack(item, count) : new ItemStack(block, count);
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private int totalWeight = 0;

    public static LootTable table() {
        return new LootTable();
    }

    public LootTable add(Item item, int min, int max, int weight) {
        entries.add(new Entry(item, null, min, max, weight));
        totalWeight += weight;
        return this;
    }

    public LootTable add(BlockType block, int min, int max, int weight) {
        entries.add(new Entry(null, block, min, max, weight));
        totalWeight += weight;
        return this;
    }

    /**
     * Roll the table. Every roll is an independent weighted draw, so one
     * item can win twice; an empty result is possible and fine.
     */
    public List<ItemStack> roll(int rolls, Random rng) {
        List<ItemStack> out = new ArrayList<>();
        if (entries.isEmpty() || totalWeight <= 0) return out;

        for (int i = 0; i < rolls; i++) {
            int pick = rng.nextInt(totalWeight);
            for (Entry e : entries) {
                pick -= e.weight;
                if (pick < 0) {
                    int count = e.min + rng.nextInt(e.max - e.min + 1);
                    out.add(e.stack(count));
                    break;
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // The tables
    // ------------------------------------------------------------------

    /**
     * Bonus rolls for generated structure chests, on top of the template's
     * guaranteed items: mostly useful supplies, tools rarely, valuables
     * very rarely.
     */
    public static final LootTable STRUCTURE_CHEST = table()
        .add(ItemRegistry.COAL,          2,  6, 20)
        .add(ItemRegistry.IRON_INGOT,    1,  4, 14)
        .add(ItemRegistry.GOLD_INGOT,    1,  3,  8)
        .add(ItemRegistry.DIAMOND,       1,  2,  2)
        .add(ItemRegistry.LAPIS_LAZULI,  1,  4,  6)
        .add(ItemRegistry.BREAD,         1,  3, 12)
        .add(ItemRegistry.APPLE_ITEM,    1,  3, 10)
        .add(ItemRegistry.STRING,        1,  3,  8)
        .add(ItemRegistry.ARROW,         2,  6, 10)
        .add(ItemRegistry.FLINT,         1,  3,  8)
        .add(ItemRegistry.STICK,         2,  8, 10)
        .add(ItemRegistry.WOODEN_PICKAXE, 1, 1,  4)
        .add(ItemRegistry.STONE_PICKAXE,  1, 1,  3)
        .add(ItemRegistry.IRON_PICKAXE,   1, 1,  1)
        .add(ItemRegistry.IRON_SWORD,     1, 1,  2)
        .add(BlockType.ITEM_TORCH,      2,  8, 10)
        .add(ItemRegistry.GUNPOWDER,     1,  3,  5)
        .add(ItemRegistry.BONE,          1,  3,  7)
        .add(ItemRegistry.LEATHER,       1,  3,  6)
        .add(ItemRegistry.SLIME_BALL,    1,  2,  3);

    private LootTable() {}
}
