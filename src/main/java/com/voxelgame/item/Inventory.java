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

        giveStartingItems();
    }

    private void giveStartingItems() {
        hotbar[0] = new ItemStack(BlockType.GRASS_BLOCK, 64);
        hotbar[1] = new ItemStack(BlockType.DIRT, 64);
        hotbar[2] = new ItemStack(BlockType.STONE, 64);
        hotbar[3] = new ItemStack(BlockType.COBBLESTONE, 64);
        hotbar[4] = new ItemStack(BlockType.OAK_PLANKS, 64);
        hotbar[5] = new ItemStack(BlockType.GLASS, 64);
        hotbar[6] = new ItemStack(BlockType.OAK_LOG, 64);
        hotbar[7] = new ItemStack(BlockType.OAK_LEAVES, 64);
        hotbar[8] = new ItemStack(BlockType.SAND, 64);
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
    public boolean addItem(BlockType type, int count) {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (hotbar[i].isBlock() && hotbar[i].getBlockType() == type && hotbar[i].canAdd(count)) {
                hotbar[i].add(count); return true;
            }
        }
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            if (mainInventory[i].isBlock() && mainInventory[i].getBlockType() == type && mainInventory[i].canAdd(count)) {
                mainInventory[i].add(count); return true;
            }
        }
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (hotbar[i].isEmpty()) { hotbar[i] = new ItemStack(type, count); return true; }
        }
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            if (mainInventory[i].isEmpty()) { mainInventory[i] = new ItemStack(type, count); return true; }
        }
        return false;
    }

    public boolean addItem(Item item, int count) {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (hotbar[i].isItem() && hotbar[i].getItem() == item && hotbar[i].canAdd(count)) {
                hotbar[i].add(count); return true;
            }
        }
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            if (mainInventory[i].isItem() && mainInventory[i].getItem() == item && mainInventory[i].canAdd(count)) {
                mainInventory[i].add(count); return true;
            }
        }
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (hotbar[i].isEmpty()) { hotbar[i] = new ItemStack(item, count); return true; }
        }
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            if (mainInventory[i].isEmpty()) { mainInventory[i] = new ItemStack(item, count); return true; }
        }
        return false;
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
}
