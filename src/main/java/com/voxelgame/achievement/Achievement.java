package com.voxelgame.achievement;

/**
 * Represents a single achievement that can be unlocked.
 */
public class Achievement {
    public final String id;
    public final String titleKey;
    public final String descriptionKey;
    public final int iconBlockId; // BlockType id for icon, or -1 for no icon
    public final int color; // ARGB color for toast

    public Achievement(String id, String titleKey, String descriptionKey, int iconBlockId, int color) {
        this.id = id;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
        this.iconBlockId = iconBlockId;
        this.color = color;
    }

    public Achievement(String id, String titleKey, String descriptionKey, int iconBlockId) {
        this(id, titleKey, descriptionKey, iconBlockId, 0xFFFFAA00); // default gold
    }
}
