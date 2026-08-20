package com.voxelgame.world.container;

import com.voxelgame.item.ItemStack;

/**
 * A double chest combines two adjacent chests into a 54-slot container.
 * Slots 0-26 are from the first chest, slots 27-53 from the second.
 */
public class DoubleChestData extends ContainerData {

    private final ContainerData left;
    private final ContainerData right;

    public DoubleChestData(ContainerData left, ContainerData right) {
        super(Type.CHEST);
        this.left = left;
        this.right = right;
    }

    @Override
    public int size() {
        return 54; // Two chests combined
    }

    @Override
    public ItemStack getSlot(int index) {
        if (index < 27) {
            return left.getSlot(index);
        }
        return right.getSlot(index - 27);
    }

    @Override
    public void setSlot(int index, ItemStack stack) {
        if (index < 27) {
            left.setSlot(index, stack);
        } else {
            right.setSlot(index - 27, stack);
        }
    }
}
