package com.voxelgame.item;

import com.voxelgame.core.Language;
import com.voxelgame.world.BlockType;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a stack of items in the inventory.
 * Can hold either a BlockType (for blocks) or an Item (for tools, materials, etc).
 */
public class ItemStack {
    private BlockType blockType;
    private Item item;
    private int count;
    private int durability; // for tools
    private Map<Enchantment, Integer> enchantments; // lazily created

    public ItemStack(BlockType blockType, int count) {
        this.blockType = blockType;
        this.item = null;
        this.count = blockType != null ? Math.min(count, 64) : 0;
        this.durability = 0;
    }

    public ItemStack(Item item, int count) {
        this.blockType = null;
        this.item = item;
        this.count = item != null ? Math.min(count, item.maxStackSize) : 0;
        this.durability = item != null ? item.getMaxDurability() : 0;
    }

    public ItemStack(Item item) {
        this(item, 1);
    }

    public BlockType getBlockType() { return blockType; }
    public Item getItem() { return item; }
    public boolean isBlock() { return blockType != null; }
    public boolean isItem() { return item != null; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = Math.min(count, getMaxStackSize()); }

    public int getMaxStackSize() {
        if (item != null) return item.maxStackSize;
        return 64;
    }

    public boolean canAdd(int amount) {
        return count + amount <= getMaxStackSize();
    }

    public int add(int amount) {
        int canAdd = Math.min(amount, getMaxStackSize() - count);
        count += canAdd;
        return amount - canAdd;
    }

    public int remove(int amount) {
        int canRemove = Math.min(amount, count);
        count -= canRemove;
        return amount - canRemove;
    }

    public boolean isEmpty() { return count <= 0; }
    public void clear() { count = 0; }

    // --- Tool durability ---
    public int getDurability() { return durability; }
    public void setDurability(int d) { this.durability = d; }
    public int getMaxDurability() {
        return item != null ? item.getMaxDurability() : 0;
    }
    public boolean isDamaged() { return durability < getMaxDurability(); }

    public void damage(int amount) {
        durability -= amount;
        if (durability <= 0) {
            durability = 0;
            count = 0; // Tool breaks
        }
    }

    /** [ENCH] Remove items from the stack (fuel costs etc). */
    public void decrement(int amount) {
        count = Math.max(0, count - amount);
    }

    // --- Display ---
    public String getDisplayName() {
        if (item != null) {
            String localized = Language.tr("item." + item.name);
            return localized != null ? localized : item.displayName;
        }
        if (blockType != null) {
            String localized = Language.tr("block." + blockType.name);
            return localized != null ? localized : blockType.name;
        }
        return "Air";
    }

    // --- Attack damage ---
    public float getAttackDamage() {
        if (item != null) return item.getAttackDamage();
        return 1.0f; // Fist
    }

    // --- Enchantments [ENCH] ---
    /** Level of an enchantment on this stack, 0 when absent. */
    public int getEnchantLevel(Enchantment e) {
        if (enchantments == null) return 0;
        Integer lvl = enchantments.get(e);
        return lvl == null ? 0 : lvl;
    }

    public void addEnchantment(Enchantment e, int level) {
        if (enchantments == null) enchantments = new HashMap<>();
        enchantments.put(e, Math.min(level, e.maxLevel));
    }

    public boolean hasEnchantments() {
        return enchantments != null && !enchantments.isEmpty();
    }

    public Map<Enchantment, Integer> getEnchantments() {
        return enchantments;
    }

    /** Short purple names for the enchanting screen and tooltips. */
    public String getEnchantmentSummary() {
        if (!hasEnchantments()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(entry.getKey().displayName);
            if (entry.getValue() > 1) sb.append(' ').append(entry.getValue());
        }
        return sb.toString();
    }

    // --- Mining ---
    public ToolTier getToolTier() {
        if (item != null) return item.tier;
        return null;
    }

    public ToolType getToolType() {
        if (item != null) return item.toolType;
        return ToolType.NONE;
    }

    public boolean isEmptyStack() { return count <= 0; }

    public ItemStack copy() {
        if (item != null) {
            ItemStack copy = new ItemStack(item, count);
            copy.durability = durability;
            if (enchantments != null) {
                copy.enchantments = new HashMap<>(enchantments);
            }
            return copy;
        }
        ItemStack copy = new ItemStack(blockType, count);
        if (enchantments != null) {
            copy.enchantments = new HashMap<>(enchantments);
        }
        return copy;
    }

    /** Create a copy with a specific count. */
    public ItemStack copyWithCount(int newCount) {
        ItemStack copy = copy();
        copy.count = Math.min(newCount, copy.getMaxStackSize());
        return copy;
    }

    /** Check if this stack can merge with another. */
    public boolean canMerge(ItemStack other) {
        if (other == null || isEmpty() || other.isEmpty()) return false;
        if (hasEnchantments() || other.hasEnchantments()) return false;
        if (item != null && item == other.item && durability == other.durability) return true;
        return blockType != null && blockType == other.blockType;
    }

    /** Get the maximum stack size (alias for getMaxStackSize). */
    public int getMaxCount() {
        return getMaxStackSize();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemStack)) return false;
        ItemStack other = (ItemStack) o;
        if (item != null && item == other.item) return true;
        if (blockType != null && blockType == other.blockType) return true;
        return false;
    }
}
