package com.voxelgame.item;

import com.voxelgame.world.BlockType;

/**
 * Represents an item definition - template for item types.
 * This is the prototype; ItemStack is the instance.
 */
public class Item {

    public final int id;
    public final String name;
    public final String displayName;
    public final int maxStackSize;

    // Tool properties
    public final ToolTier tier;
    public final ToolType toolType;

    // Combat
    public final float baseAttackDamage;
    public final float attackSpeed;

    // Armor
    public final ArmorSlot armorSlot;
    public final int armorPoints;

    // Food
    public final int foodValue;
    public final float saturation;

    // Block association (for block items)
    public final BlockType blockType;

    // Potion effect (for potion items)
    public final StatusEffect potionEffect;

    // Sprite index for rendering
    public final String spriteName;

    private Item(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.displayName = b.displayName;
        this.maxStackSize = b.maxStackSize;
        this.tier = b.tier;
        this.toolType = b.toolType;
        this.baseAttackDamage = b.baseAttackDamage;
        this.attackSpeed = b.attackSpeed;
        this.armorSlot = b.armorSlot;
        this.armorPoints = b.armorPoints;
        this.foodValue = b.foodValue;
        this.saturation = b.saturation;
        this.blockType = b.blockType;
        this.potionEffect = b.potionEffect;
        this.spriteName = b.spriteName;
    }

    public boolean isTool() { return toolType != ToolType.NONE && tier != null; }
    public boolean isArmor() { return armorSlot != null; }
    public boolean isFood() { return foodValue > 0; }
    public boolean isBlock() { return blockType != null; }
    public boolean isPotion() { return potionEffect != null; }

    public int getMaxDurability() {
        return isTool() ? tier.maxDurability : 0;
    }

    public float getAttackDamage() {
        return baseAttackDamage + (tier != null ? tier.attackDamage : 0);
    }

    public static Builder builder(int id, String name) {
        return new Builder(id, name);
    }

    public static class Builder {
        private final int id;
        private final String name;
        private String displayName;
        private int maxStackSize = 64;
        private ToolTier tier;
        private ToolType toolType = ToolType.NONE;
        private float baseAttackDamage = 1.0f;
        private float attackSpeed = 4.0f;
        private ArmorSlot armorSlot;
        private int armorPoints;
        private int foodValue;
        private float saturation;
        private BlockType blockType;
        private StatusEffect potionEffect;
        private String spriteName;

        Builder(int id, String name) {
            this.id = id;
            this.name = name;
            this.displayName = name;
        }

        public Builder displayName(String s) { this.displayName = s; return this; }
        public Builder stackSize(int n) { this.maxStackSize = n; return this; }
        public Builder tier(ToolTier t) { this.tier = t; return this; }
        public Builder toolType(ToolType t) { this.toolType = t; return this; }
        public Builder attackDamage(float d) { this.baseAttackDamage = d; return this; }
        public Builder attackSpeed(float s) { this.attackSpeed = s; return this; }
        public Builder armorSlot(ArmorSlot s) { this.armorSlot = s; return this; }
        public Builder armorPoints(int p) { this.armorPoints = p; return this; }
        public Builder food(int value, float sat) { this.foodValue = value; this.saturation = sat; return this; }
        public Builder block(BlockType b) { this.blockType = b; return this; }
        public Builder potion(StatusEffect e) { this.potionEffect = e; return this; }
        public Builder sprite(String s) { this.spriteName = s; return this; }

        public Item build() { return new Item(this); }
    }
}
