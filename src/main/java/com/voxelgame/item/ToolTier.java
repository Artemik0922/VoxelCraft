package com.voxelgame.item;

/**
 * Tool tiers define mining speed, attack damage bonus, and durability.
 */
public enum ToolTier {
    WOOD(1, 59, 2.0f, 4.0f, 15),
    STONE(2, 131, 4.0f, 5.0f, 5),
    IRON(3, 250, 6.0f, 6.0f, 14),
    DIAMOND(4, 1561, 8.0f, 7.0f, 10),
    GOLD(0, 32, 12.0f, 4.0f, 22),
    /** Space metal forged into tools: diamond's harvest level, but tougher,
     *  faster and sharper than anything on the base world. */
    METEORITE(4, 2400, 9.0f, 8.0f, 15);

    /** Harvest level: 0=wood, 1=stone, 2=iron, 3=diamond */
    public final int harvestLevel;
    /** Max durability for tools of this tier */
    public final int maxDurability;
    /** Mining speed multiplier */
    public final float miningSpeed;
    /** Attack damage bonus added to base tool damage */
    public final float attackDamage;
    /** Enchantability higher = better enchants */
    public final int enchantability;

    ToolTier(int harvestLevel, int maxDurability, float miningSpeed,
             float attackDamage, int enchantability) {
        this.harvestLevel = harvestLevel;
        this.maxDurability = maxDurability;
        this.miningSpeed = miningSpeed;
        this.attackDamage = attackDamage;
        this.enchantability = enchantability;
    }
}
