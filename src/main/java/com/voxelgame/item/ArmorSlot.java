package com.voxelgame.item;

/**
 * Armor slots.
 */
public enum ArmorSlot {
    HELMET(0),
    CHESTPLATE(1),
    LEGGINGS(2),
    BOOTS(3);

    public final int index;
    ArmorSlot(int index) { this.index = index; }
}
