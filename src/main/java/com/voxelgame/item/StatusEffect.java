package com.voxelgame.item;

/**
 * [POT] Status effects applied by drinking potions.
 *
 * Each effect knows its display name, the sprite drawn in the HUD while it is
 * active, the liquid colour of its potion bottle, and how long a basic potion
 * of that effect lasts. Powers scale around these base durations.
 */
public enum StatusEffect {

    /** Slowly restores health over time. */
    REGENERATION("Регенерация", "effect_regeneration", 0xE858A0, 15 * 20),
    /** Boosts movement speed. */
    SPEED("Скорость", "effect_speed", 0x50E0C8, 30 * 20),
    /** Boosts melee attack damage. */
    STRENGTH("Сила", "effect_strength", 0xE05040, 30 * 20),
    /** Immune to fire and lava damage; burning stops. */
    FIRE_RESISTANCE("Огнестойкость", "effect_fire_resistance", 0xE89830, 60 * 20);

    public final String displayName;
    public final String spriteName;
    public final int liquidColor;
    public final int baseDurationTicks;

    StatusEffect(String displayName, String spriteName, int liquidColor, int baseDurationTicks) {
        this.displayName = displayName;
        this.spriteName = spriteName;
        this.liquidColor = liquidColor;
        this.baseDurationTicks = baseDurationTicks;
    }
}