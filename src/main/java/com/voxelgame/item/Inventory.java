package com.voxelgame.item;

import com.voxelgame.world.BlockType;

/**
 * Player inventory with hotbar and main inventory.
 */
public class Inventory {
    private ItemStack[] hotbar; // 9 slots
    private ItemStack[] mainInventory; // 27 slots
    private int selectedSlot = 0;

    // Armor slots
    private ItemStack[] armor; // 4 slots: helmet, chestplate, leggings, boots

    public static final int HOTBAR_SIZE = 9;
    public static final int MAIN_INVENTORY_SIZE = 27;
    public static final int ARMOR_SIZE = 4;

    public Inventory() {
        hotbar = new ItemStack[HOTBAR_SIZE];
        mainInventory = new ItemStack[MAIN_INVENTORY_SIZE];
        armor = new ItemStack[ARMOR_SIZE];

        for (int i = 0; i < HOTBAR_SIZE; i++) hotbar[i] = new ItemStack(BlockType.AIR, 0);
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) mainInventory[i] = new ItemStack(BlockType.AIR, 0);
        for (int i = 0; i < ARMOR_SIZE; i++) armor[i] = new ItemStack(BlockType.AIR, 0);

        giveStarterKit();
    }

    /**
     * Survival starter kit: basic tools, light and food so the first night
     * is survivable, plus some materials to build with.
     */
    public void giveStarterKit() {
        setHotbarItem(0, ItemRegistry.WOODEN_PICKAXE, 1);
        setHotbarItem(1, ItemRegistry.WOODEN_AXE, 1);
        setHotbarItem(2, ItemRegistry.WOODEN_SWORD, 1);
        setHotbarItem(3, BlockType.ITEM_TORCH, 16);
        setHotbarItem(4, ItemRegistry.BREAD, 6);
        setHotbarItem(5, BlockType.COBBLESTONE, 32);
        setHotbarItem(6, BlockType.OAK_PLANKS, 16);
        setHotbarItem(7, BlockType.DIRT, 16);
    }

    /** True when hotbar, storage and armor hold nothing at all. */
    public boolean isCompletelyEmpty() {
        for (ItemStack s : hotbar) if (!s.isEmpty()) return false;
        for (ItemStack s : mainInventory) if (!s.isEmpty()) return false;
        for (ItemStack s : armor) if (!s.isEmpty()) return false;
        return true;
    }

    public ItemStack getSelectedItem() {
        return hotbar[selectedSlot];
    }

    public int getSelectedBlockId() {
        ItemStack item = getSelectedItem();
        if (item.isEmpty()) return 0;
        return item.isBlock() ? item.getBlockType().id : 0;
    }

    public boolean removeSelectedItem() {
        ItemStack item = getSelectedItem();
        if (item.isEmpty()) return false;
        item.remove(1);
        return true;
    }

    public void setSelectedSlot(int slot) {
        if (slot >= 0 && slot < HOTBAR_SIZE) selectedSlot = slot;
    }

    public int getSelectedSlot() { return selectedSlot; }

    public void scrollSlot(int direction) {
        selectedSlot += direction;
        if (selectedSlot < 0) selectedSlot = HOTBAR_SIZE - 1;
        if (selectedSlot >= HOTBAR_SIZE) selectedSlot = 0;
    }

    public void setHotbarItem(int slot, BlockType type, int count) {
        if (slot < 0 || slot >= HOTBAR_SIZE) return;
        hotbar[slot] = new ItemStack(type, count);
    }

    public void setHotbarItem(int slot, Item item, int count) {
        if (slot < 0 || slot >= HOTBAR_SIZE) return;
        hotbar[slot] = new ItemStack(item, count);
    }

    /** Replace a hotbar slot with an arbitrary stack (keeps tools intact). */
    public void setHotbarItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= HOTBAR_SIZE) return;
        hotbar[slot] = stack == null || stack.isEmpty()
            ? new ItemStack(BlockType.AIR, 0) : stack;
    }

    public ItemStack getHotbarItem(int slot) {
        if (slot >= 0 && slot < HOTBAR_SIZE) return hotbar[slot];
        return new ItemStack(BlockType.AIR, 0);
    }

    public ItemStack getInventoryItem(int slot) {
        if (slot >= 0 && slot < MAIN_INVENTORY_SIZE) return mainInventory[slot];
        return new ItemStack(BlockType.AIR, 0);
    }

    public void setInventoryItem(int slot, BlockType type, int count) {
        if (slot < 0 || slot >= MAIN_INVENTORY_SIZE) return;
        mainInventory[slot] = new ItemStack(type, count);
    }

    public void setInventoryItem(int slot, Item item, int count) {
        if (slot < 0 || slot >= MAIN_INVENTORY_SIZE) return;
        mainInventory[slot] = new ItemStack(item, count);
    }

    /** [ENCH] Replace a main-inventory slot with an arbitrary stack. */
    public void setInventoryItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= MAIN_INVENTORY_SIZE) return;
        mainInventory[slot] = stack == null ? new ItemStack(BlockType.AIR, 0) : stack;
    }

    // --- Armor ---
    public ItemStack getArmor(int slot) {
        if (slot >= 0 && slot < ARMOR_SIZE) return armor[slot];
        return new ItemStack(BlockType.AIR, 0);
    }

    public void setArmor(int slot, ItemStack item) {
        if (slot >= 0 && slot < ARMOR_SIZE) armor[slot] = item;
    }

    public int getTotalArmorPoints() {
        int total = 0;
        for (ItemStack a : armor) {
            if (!a.isEmpty() && a.getItem() != null && a.getItem().isArmor()) {
                total += a.getItem().armorPoints;
            }
        }
        return total;
    }

    public ItemStack[] getArmorSlots() { return armor; }

    // --- Add items ---
    /** Add a count of a block, splitting across slots when it exceeds the
     *  stack limit. Returns true when all of it found a home. */
    public boolean addItem(BlockType type, int count) {
        int remaining = count;
        for (int i = 0; i < HOTBAR_SIZE && remaining > 0; i++) {
            if (hotbar[i].isBlock() && hotbar[i].getBlockType() == type) {
                remaining = hotbar[i].add(remaining);
            }
        }
        for (int i = 0; i < MAIN_INVENTORY_SIZE && remaining > 0; i++) {
            if (mainInventory[i].isBlock() && mainInventory[i].getBlockType() == type) {
                remaining = mainInventory[i].add(remaining);
            }
        }
        // Fill empty slots with as many full stacks as fit
        while (remaining > 0) {
            boolean placed = false;
            for (int i = 0; i < HOTBAR_SIZE && remaining > 0 && !placed; i++) {
                if (hotbar[i].isEmpty()) {
                    int put = Math.min(remaining, 64);
                    hotbar[i] = new ItemStack(type, put);
                    remaining -= put;
                    placed = true;
                }
            }
            if (remaining > 0 && !placed) {
                for (int i = 0; i < MAIN_INVENTORY_SIZE && remaining > 0; i++) {
                    if (mainInventory[i].isEmpty()) {
                        int put = Math.min(remaining, 64);
                        mainInventory[i] = new ItemStack(type, put);
                        remaining -= put;
                        placed = true;
                        break;
                    }
                }
            }
            if (remaining > 0 && !placed) break; // nowhere left
        }
        return remaining <= 0;
    }

    public boolean addItem(Item item, int count) {
        int remaining = count;
        for (int i = 0; i < HOTBAR_SIZE && remaining > 0; i++) {
            if (hotbar[i].isItem() && hotbar[i].getItem() == item) {
                remaining = hotbar[i].add(remaining);
            }
        }
        for (int i = 0; i < MAIN_INVENTORY_SIZE && remaining > 0; i++) {
            if (mainInventory[i].isItem() && mainInventory[i].getItem() == item) {
                remaining = mainInventory[i].add(remaining);
            }
        }
        while (remaining > 0) {
            boolean placed = false;
            for (int i = 0; i < HOTBAR_SIZE && remaining > 0 && !placed; i++) {
                if (hotbar[i].isEmpty()) {
                    int put = Math.min(remaining, item.maxStackSize);
                    hotbar[i] = new ItemStack(item, put);
                    remaining -= put;
                    placed = true;
                }
            }
            if (remaining > 0 && !placed) {
                for (int i = 0; i < MAIN_INVENTORY_SIZE && remaining > 0; i++) {
                    if (mainInventory[i].isEmpty()) {
                        int put = Math.min(remaining, item.maxStackSize);
                        mainInventory[i] = new ItemStack(item, put);
                        remaining -= put;
                        placed = true;
                        break;
                    }
                }
            }
            if (remaining > 0 && !placed) break;
        }
        return remaining <= 0;
    }

    /**
     * Insert a whole stack anywhere it fits: merge into compatible stacks
     * first, then empty slots, hotbar before main inventory. Tools and
     * enchanted items keep their identity.
     *
     * @return the leftover that did not fit (an empty stack when all fit)
     */
    public ItemStack addStack(ItemStack stack) {
        ItemStack rest = stack == null || stack.isEmpty()
            ? new ItemStack(BlockType.AIR, 0) : stack.copy();

        for (int i = 0; i < HOTBAR_SIZE && !rest.isEmpty(); i++) {
            rest = mergeInto(hotbar, i, rest);
        }
        for (int i = 0; i < MAIN_INVENTORY_SIZE && !rest.isEmpty(); i++) {
            rest = mergeInto(mainInventory, i, rest);
        }
        for (int i = 0; i < HOTBAR_SIZE && !rest.isEmpty(); i++) {
            if (hotbar[i].isEmpty()) {
                hotbar[i] = rest;
                rest = new ItemStack(BlockType.AIR, 0);
            }
        }
        for (int i = 0; i < MAIN_INVENTORY_SIZE && !rest.isEmpty(); i++) {
            if (mainInventory[i].isEmpty()) {
                mainInventory[i] = rest;
                rest = new ItemStack(BlockType.AIR, 0);
            }
        }
        return rest;
    }

    private ItemStack mergeInto(ItemStack[] arr, int idx, ItemStack source) {
        ItemStack target = arr[idx];
        if (target.isEmpty() || !target.canMerge(source)) return source;
        int space = target.getMaxStackSize() - target.getCount();
        if (space <= 0) return source;
        int move = Math.min(space, source.getCount());
        arr[idx] = target.copyWithCount(target.getCount() + move);
        return move >= source.getCount()
            ? new ItemStack(BlockType.AIR, 0) : source.copyWithCount(source.getCount() - move);
    }

    public boolean removeItem(BlockType type, int count) {
        int remaining = count;
        for (int i = 0; i < HOTBAR_SIZE && remaining > 0; i++) {
            if (hotbar[i].isBlock() && hotbar[i].getBlockType() == type) remaining = hotbar[i].remove(remaining);
        }
        for (int i = 0; i < MAIN_INVENTORY_SIZE && remaining > 0; i++) {
            if (mainInventory[i].isBlock() && mainInventory[i].getBlockType() == type) remaining = mainInventory[i].remove(remaining);
        }
        return remaining <= 0;
    }

    public boolean removeItem(Item item, int count) {
        int remaining = count;
        for (int i = 0; i < HOTBAR_SIZE && remaining > 0; i++) {
            if (hotbar[i].isItem() && hotbar[i].getItem() == item) remaining = hotbar[i].remove(remaining);
        }
        for (int i = 0; i < MAIN_INVENTORY_SIZE && remaining > 0; i++) {
            if (mainInventory[i].isItem() && mainInventory[i].getItem() == item) remaining = mainInventory[i].remove(remaining);
        }
        return remaining <= 0;
    }

    public int countItem(BlockType type) {
        int count = 0;
        for (ItemStack item : hotbar) if (item.isBlock() && item.getBlockType() == type) count += item.getCount();
        for (ItemStack item : mainInventory) if (item.isBlock() && item.getBlockType() == type) count += item.getCount();
        return count;
    }

    public int countItem(Item item) {
        int count = 0;
        for (ItemStack s : hotbar) if (s.isItem() && s.getItem() == item) count += s.getCount();
        for (ItemStack s : mainInventory) if (s.isItem() && s.getItem() == item) count += s.getCount();
        return count;
    }

    // --- [ECO] Canonical-name matching for trades ---
    /** Canonical name of an ItemStack: the item name (for registry items) or
     *  the block name (for BlockType stacks like ITEM_EMERALD). */
    public static String canonicalName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        if (stack.isItem() && stack.getItem() != null) return stack.getItem().name;
        if (stack.isBlock() && stack.getBlockType() != null) return stack.getBlockType().name;
        return "";
    }

    /** Total count of a stack identified by its canonical name. */
    public int countByName(String name) {
        if (name == null || name.isEmpty()) return 0;
        int count = 0;
        for (ItemStack s : hotbar) {
            if (canonicalName(s).equals(name)) count += s.getCount();
        }
        for (ItemStack s : mainInventory) {
            if (canonicalName(s).equals(name)) count += s.getCount();
        }
        return count;
    }

    /** Remove up to {@code count} of a stack identified by its canonical name.
     *  Returns true when the full amount was removed. */
    public boolean removeByName(String name, int count) {
        if (name == null || name.isEmpty()) return false;
        int remaining = count;
        for (int i = 0; i < HOTBAR_SIZE && remaining > 0; i++) {
            if (canonicalName(hotbar[i]).equals(name)) remaining = hotbar[i].remove(remaining);
        }
        for (int i = 0; i < MAIN_INVENTORY_SIZE && remaining > 0; i++) {
            if (canonicalName(mainInventory[i]).equals(name)) remaining = mainInventory[i].remove(remaining);
        }
        return remaining <= 0;
    }
}
