package com.voxelgame.ui.screen;

import com.voxelgame.item.ItemStack;
import com.voxelgame.world.BlockType;

/**
 * Minecraft-parity slot interaction shared by every container screen.
 *
 * Screens describe their slots through the tiny {@link Slot} view and hand
 * every click through {@link #click}, every shift-click through
 * {@link #quickMove}. The engine owns the exact vanilla semantics:
 *
 * - Left click: pick up the whole stack, place the whole stack, merge two
 *   compatible stacks up to the stack limit, or swap incompatible stacks.
 * - Right click: pick up half (rounded up), or place a single item.
 * - Everything runs on real {@link ItemStack} copies, so tools keep their
 *   durability and enchanted items never merge into each other.
 */
public final class SlotEngine {

    private SlotEngine() {}

    /** A clickable slot view over some storage (inventory row, chest cell, ...). */
    public interface Slot {
        ItemStack get();
        void set(ItemStack stack);

        /** May the given cursor stack be placed into this slot? */
        default boolean mayPlace(ItemStack stack) { return true; }

        /** May stacks be taken out of this slot? */
        default boolean mayTake() { return true; }
    }

    public static ItemStack empty() { return new ItemStack(BlockType.AIR, 0); }

    // ------------------------------------------------------------------
    // Core clicks
    // ------------------------------------------------------------------

    /**
     * One click on a slot. {@code left} selects the left-click behaviour
     * (take all / place all / merge / swap); anything else is the
     * right-click behaviour (take half / place one).
     *
     * @return the new cursor stack
     */
    public static ItemStack click(Slot slot, ItemStack mouse, boolean left) {
        ItemStack inSlot = slot.get();

        if (!left) {
            return rightClick(slot, inSlot, mouse);
        }

        if (mouse.isEmpty()) {
            // Pick the whole stack up
            if (inSlot.isEmpty() || !slot.mayTake()) return mouse;
            slot.set(empty());
            return inSlot;
        }

        if (inSlot.isEmpty()) {
            // Place the whole stack
            if (!slot.mayPlace(mouse)) return mouse;
            slot.set(mouse);
            return empty();
        }

        if (inSlot.canMerge(mouse)) {
            // Merge into the slot up to its stack limit
            int space = inSlot.getMaxStackSize() - inSlot.getCount();
            if (space <= 0 || !slot.mayPlace(mouse)) return mouse;
            int move = Math.min(space, mouse.getCount());
            slot.set(inSlot.copyWithCount(inSlot.getCount() + move));
            return move >= mouse.getCount()
                ? empty() : mouse.copyWithCount(mouse.getCount() - move);
        }

        // Incompatible stacks swap places
        if (!slot.mayPlace(mouse) || !slot.mayTake()) return mouse;
        slot.set(mouse);
        return inSlot;
    }

    private static ItemStack rightClick(Slot slot, ItemStack inSlot, ItemStack mouse) {
        if (mouse.isEmpty()) {
            // Take the larger half
            if (inSlot.isEmpty() || !slot.mayTake()) return mouse;
            int take = (inSlot.getCount() + 1) / 2;
            int keep = inSlot.getCount() - take;
            slot.set(keep <= 0 ? empty() : inSlot.copyWithCount(keep));
            return inSlot.copyWithCount(take);
        }

        // Place a single item
        if (!slot.mayPlace(mouse)) return mouse;
        if (inSlot.isEmpty()) {
            slot.set(mouse.copyWithCount(1));
            return decrement(mouse);
        }
        if (inSlot.canMerge(mouse) && inSlot.getCount() < inSlot.getMaxStackSize()) {
            slot.set(inSlot.copyWithCount(inSlot.getCount() + 1));
            return decrement(mouse);
        }
        return mouse;
    }

    // ------------------------------------------------------------------
    // Shift-click quick move
    // ------------------------------------------------------------------

    /**
     * Move the whole source stack through the destination slots in order,
     * merging into compatible stacks first, then into empty slots. Whatever
     * fits leaves the source; the rest (if any) stays in it.
     */
    public static ItemStack quickMove(Slot source, Slot[] destinations) {
        ItemStack stack = source.get();
        if (stack.isEmpty() || !source.mayTake()) return stack;

        // First pass: merge into existing stacks
        for (Slot dst : destinations) {
            if (stack.isEmpty()) break;
            ItemStack inDst = dst.get();
            if (inDst.isEmpty() || !inDst.canMerge(stack)) continue;
            int space = inDst.getMaxStackSize() - inDst.getCount();
            if (space <= 0) continue;
            int move = Math.min(space, stack.getCount());
            dst.set(inDst.copyWithCount(inDst.getCount() + move));
            stack = move >= stack.getCount()
                ? empty() : stack.copyWithCount(stack.getCount() - move);
        }

        // Second pass: empty slots
        for (Slot dst : destinations) {
            if (stack.isEmpty()) break;
            if (!dst.get().isEmpty() || !dst.mayPlace(stack)) continue;
            dst.set(stack);
            stack = empty();
        }

        source.set(stack);
        return stack;
    }

    // ------------------------------------------------------------------
    // Hotbar number keys over a hovered slot
    // ------------------------------------------------------------------

    /**
     * Pressing 1-9 with a slot hovered: merge-or-swap it with the matching
     * hotbar slot, exactly like vanilla.
     */
    public static void hotbarSwap(Slot hovered, Slot hotbar) {
        ItemStack a = hovered.get();
        ItemStack b = hotbar.get();

        if (!a.isEmpty() && !b.isEmpty() && a.canMerge(b)) {
            int space = a.getMaxStackSize() - a.getCount();
            int move = Math.min(space, b.getCount());
            if (move > 0) {
                hovered.set(a.copyWithCount(a.getCount() + move));
                hotbar.set(move >= b.getCount()
                    ? empty() : b.copyWithCount(b.getCount() - move));
            }
            return;
        }
        if (!hotbar.mayPlace(a) || !hotbar.mayTake()
            || !hovered.mayPlace(b) || !hovered.mayTake()) return;
        hovered.set(b);
        hotbar.set(a);
    }

    // ------------------------------------------------------------------
    // Q to drop from a slot
    // ------------------------------------------------------------------

    /**
     * Pull one item (or the whole stack with Ctrl) out of a slot so the
     * caller can drop it into the world.
     */
    public static ItemStack pullForDrop(Slot slot, boolean entireStack) {
        ItemStack inSlot = slot.get();
        if (inSlot.isEmpty() || !slot.mayTake()) return empty();
        int n = entireStack ? inSlot.getCount() : 1;
        ItemStack dropped = inSlot.copyWithCount(n);
        slot.set(n >= inSlot.getCount()
            ? empty() : inSlot.copyWithCount(inSlot.getCount() - n));
        return dropped;
    }

    /** Convenience: the cursor stack after removing one item. */
    public static ItemStack decrement(ItemStack stack) {
        return stack.getCount() <= 1
            ? empty() : stack.copyWithCount(stack.getCount() - 1);
    }
}
