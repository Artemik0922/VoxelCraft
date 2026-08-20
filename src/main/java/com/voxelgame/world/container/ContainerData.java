package com.voxelgame.world.container;

import com.voxelgame.item.Item;
import com.voxelgame.item.ItemStack;
import com.voxelgame.item.ItemRegistry;
import com.voxelgame.world.BlockType;

/**
 * Stores item slots for a block container (chest, furnace, etc.).
 * Data is serialized with the chunk.
 */
public class ContainerData {

    public enum Type {
        CHEST(27),
        FURNACE(3);

        public final int slotCount;

        Type(int slotCount) {
            this.slotCount = slotCount;
        }
    }

    public final Type type;
    private final ItemStack[] slots;

    public ContainerData(Type type) {
        this.type = type;
        this.slots = new ItemStack[type.slotCount];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = emptyStack();
        }
    }

    private static ItemStack emptyStack() {
        return new ItemStack(BlockType.AIR, 0);
    }

    public ItemStack getSlot(int index) {
        if (index < 0 || index >= slots.length) return emptyStack();
        return slots[index];
    }

    public void setSlot(int index, ItemStack stack) {
        if (index < 0 || index >= slots.length) return;
        slots[index] = (stack == null || stack.isEmpty()) ? emptyStack() : stack;
    }

    public int size() {
        return slots.length;
    }

    /**
     * Try to insert an item stack into the container.
     * Returns the remainder that didn't fit.
     */
    public ItemStack insertItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return emptyStack();

        // First try to merge with existing stacks
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].isEmpty()) continue;
            if (slots[i].canMerge(stack)) {
                int space = slots[i].getMaxCount() - slots[i].getCount();
                if (space <= 0) continue;
                int toAdd = Math.min(space, stack.getCount());
                slots[i] = slots[i].copyWithCount(slots[i].getCount() + toAdd);
                stack = stack.copyWithCount(stack.getCount() - toAdd);
                if (stack.isEmpty()) return emptyStack();
            }
        }

        // Then try empty slots
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].isEmpty()) {
                slots[i] = stack.copy();
                return emptyStack();
            }
        }

        return stack;
    }

    // --- Furnace logic ---

    /** Furnace slot indices. */
    public static final int FURNACE_INPUT = 0;
    public static final int FURNACE_FUEL = 1;
    public static final int FURNACE_OUTPUT = 2;

    /** Current fuel burn time remaining. */
    private int fuelTime = 0;
    /** Total fuel time (for UI progress bar). */
    private int fuelTimeTotal = 0;
    /** Smelting progress (0 to SMELT_TIME). */
    private int smeltTime = 0;
    /** Ticks needed to smelt one item. */
    private static final int SMELT_TIME = 200;

    /** Get fuel burn time for an item. */
    private static int getFuelTime(ItemStack fuel) {
        if (fuel == null || fuel.isEmpty()) return 0;
        if (fuel.isBlock()) {
            int id = fuel.getBlockType().id;
            if (id == BlockType.COAL_ORE.id || id == BlockType.COAL_BLOCK.id) return id == BlockType.COAL_BLOCK.id ? 16000 : 1600;
            if (id == BlockType.OAK_LOG.id || id == BlockType.SPRUCE_LOG.id ||
                id == BlockType.BIRCH_LOG.id || id == BlockType.JUNGLE_LOG.id) return 300;
            if (id == BlockType.OAK_PLANKS.id || id == BlockType.SPRUCE_PLANKS.id ||
                id == BlockType.BIRCH_PLANKS.id || id == BlockType.JUNGLE_PLANKS.id ||
                id == BlockType.ACACIA_PLANKS.id || id == BlockType.DARK_OAK_PLANKS.id) return 300;
            // [GP-PLANKS] Improved planks burn like the originals
            if (BlockType.isPlank(id)) return 300;
        } else if (fuel.getItem() != null) {
            int itemId = fuel.getItem().id & 0xFF;
            if (itemId == ItemRegistry.COAL.id) return 1600;
            if (itemId == ItemRegistry.STICK.id) return 100;
        }
        return 0;
    }

    /** Get the smelting input ID (block ID for blocks, item ID for items). */
    private int getInputId() {
        ItemStack input = getSlot(FURNACE_INPUT);
        if (input.isEmpty()) return 0;
        if (input.isBlock()) return input.getBlockType().id;
        if (input.getItem() != null) return input.getItem().id;
        return 0;
    }

    /** Check if the furnace can smelt the current input. */
    public boolean canSmelt() {
        ItemStack input = getSlot(FURNACE_INPUT);
        if (input.isEmpty()) return false;

        ItemStack result = com.voxelgame.item.RecipeRegistry.getSmeltingResult(getInputId());
        if (result == null) return false;

        ItemStack output = getSlot(FURNACE_OUTPUT);
        if (output.isEmpty()) return true;
        if (!output.canMerge(result)) return false;
        return output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    /** Update furnace logic. Call every tick. */
    public void updateFurnace() {
        if (type != Type.FURNACE) return;

        if (canSmelt() && fuelTime > 0) {
            smeltTime++;
            if (smeltTime >= SMELT_TIME) {
                // Smelting complete
                ItemStack input = getSlot(FURNACE_INPUT);
                ItemStack result = com.voxelgame.item.RecipeRegistry.getSmeltingResult(getInputId());
                if (result != null) {
                    ItemStack output = getSlot(FURNACE_OUTPUT);
                    if (output.isEmpty()) {
                        setSlot(FURNACE_OUTPUT, result.copy());
                    } else {
                        setSlot(FURNACE_OUTPUT, output.copyWithCount(output.getCount() + result.getCount()));
                    }
                    // Consume input
                    setSlot(FURNACE_INPUT, input.copyWithCount(input.getCount() - 1));
                }
                smeltTime = 0;
            }
        } else {
            smeltTime = 0;
        }

        // Consume fuel
        if (fuelTime > 0) {
            fuelTime--;
        } else if (canSmelt()) {
            // Try to consume fuel from fuel slot only if we can actually smelt
            ItemStack fuel = getSlot(FURNACE_FUEL);
            if (!fuel.isEmpty()) {
                int burnTime = getFuelTime(fuel);
                if (burnTime > 0) {
                    fuelTime = burnTime;
                    fuelTimeTotal = burnTime;
                    setSlot(FURNACE_FUEL, fuel.copyWithCount(fuel.getCount() - 1));
                }
            }
        }
    }

    public int getFuelTime() { return fuelTime; }
    public int getFuelTimeTotal() { return fuelTimeTotal; }
    public int getSmeltTime() { return smeltTime; }
    public static int getSmeltDuration() { return SMELT_TIME; }

    /**
     * Serialize to string for saving.
     * Slot format: "index:b:blockId:count" for blocks, "index:i:itemId:count"
     * for items, ":durability" appended to damaged tools.
     * Furnaces append their progress: "...|fuelTime,fuelTimeTotal,smeltTime"
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slots.length; i++) {
            ItemStack s = slots[i];
            if (s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(i).append(':');
            if (s.isBlock()) {
                sb.append('b').append(':').append(s.getBlockType().id);
            } else {
                sb.append('i').append(':').append(s.getItem().id);
            }
            sb.append(':').append(s.getCount());
            if (s.isItem() && s.isDamaged()) {
                sb.append(':').append(s.getDurability());
            }
        }
        if (type == Type.FURNACE) {
            sb.append('|').append(fuelTime).append(',')
              .append(fuelTimeTotal).append(',').append(smeltTime);
        }
        return sb.toString();
    }

    /**
     * Deserialize from string.
     */
    public static ContainerData deserialize(String data, Type type) {
        ContainerData container = new ContainerData(type);
        if (data == null || data.isEmpty()) return container;

        String slotsPart = data;
        String statePart = null;
        int bar = data.indexOf('|');
        if (bar >= 0) {
            slotsPart = data.substring(0, bar);
            statePart = data.substring(bar + 1);
        }

        String[] entries = slotsPart.split(";");
        for (String entry : entries) {
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(":");
            try {
                int index = Integer.parseInt(parts[0]);
                if (parts.length >= 4) {
                    int itemId = Integer.parseInt(parts[2]);
                    int count = Integer.parseInt(parts[3]);
                    if ("b".equals(parts[1])) {
                        BlockType block = BlockType.fromId(itemId);
                        if (block != null) {
                            container.setSlot(index, new ItemStack(block, count));
                        }
                    } else {
                        Item item = ItemRegistry.getById(itemId);
                        if (item != null) {
                            ItemStack stack = new ItemStack(item, count);
                            if (parts.length >= 5) {
                                try {
                                    stack.setDurability(Integer.parseInt(parts[4]));
                                } catch (NumberFormatException ignored) {}
                            }
                            container.setSlot(index, stack);
                        }
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        if (type == Type.FURNACE && statePart != null && !statePart.isEmpty()) {
            String[] parts = statePart.split(",");
            if (parts.length >= 3) {
                try {
                    container.fuelTime = Integer.parseInt(parts[0].trim());
                    container.fuelTimeTotal = Integer.parseInt(parts[1].trim());
                    container.smeltTime = Integer.parseInt(parts[2].trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return container;
    }
}
