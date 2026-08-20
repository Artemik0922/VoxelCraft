package com.voxelgame.item;

/**
 * [ENCH] The tool enchantments the enchanting table can roll.
 *
 * Each enchantment knows its maximum level, which tool kinds it applies
 * to, and a weight that controls how likely it is to be offered.
 */
public enum Enchantment {

    EFFICIENCY("Эффективность", 5, 10,
        ToolType.PICKAXE, ToolType.AXE, ToolType.SHOVEL),
    FORTUNE("Удача", 3, 2,
        ToolType.PICKAXE),
    SILK_TOUCH("Шёлковое касание", 1, 1,
        ToolType.PICKAXE),
    SHARPNESS("Острота", 5, 10,
        ToolType.SWORD),
    UNBREAKING("Прочность", 3, 5,
        ToolType.PICKAXE, ToolType.AXE, ToolType.SHOVEL, ToolType.SWORD, ToolType.HOE);

    public final String displayName;
    public final int maxLevel;
    public final int weight;
    public final ToolType[] tools;

    Enchantment(String displayName, int maxLevel, int weight, ToolType... tools) {
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.weight = weight;
        this.tools = tools;
    }

    /** True when this enchantment can be applied to the given tool kind. */
    public boolean appliesTo(ToolType tool) {
        for (ToolType t : tools) {
            if (t == tool) return true;
        }
        return false;
    }

    /** Weighted random pick among enchantments usable with the tool. */
    public static Enchantment rollFor(ToolType tool, java.util.Random rng) {
        int total = 0;
        for (Enchantment e : values()) {
            if (e.appliesTo(tool)) total += e.weight;
        }
        if (total <= 0) return null;
        int roll = rng.nextInt(total);
        for (Enchantment e : values()) {
            if (!e.appliesTo(tool)) continue;
            roll -= e.weight;
            if (roll < 0) return e;
        }
        return null;
    }
}