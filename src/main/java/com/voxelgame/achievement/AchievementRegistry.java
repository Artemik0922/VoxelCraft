package com.voxelgame.achievement;

import com.voxelgame.world.BlockType;
import com.voxelgame.ui.toast.ToastManager;
import com.voxelgame.ui.toast.AchievementToast;
import com.voxelgame.core.Language;

import java.util.*;

/**
 * Registry and tracker for all achievements in the game.
 * Achievements are unlocked by calling trigger() with specific event IDs.
 */
public class AchievementRegistry {

    private static final Map<String, Achievement> achievements = new HashMap<>();
    private static final Set<String> unlocked = new HashSet<>();
    private static ToastManager toastManager;

    // Register all achievements
    static {
        // Exploration
        register(new Achievement("first_biome", "achievement.first_biome", "achievement.first_biome.desc", BlockType.GRASS_BLOCK.id));
        register(new Achievement("five_biomes", "achievement.five_biomes", "achievement.five_biomes.desc", BlockType.OAK_LOG.id));

        // Mining
        register(new Achievement("mine_stone", "achievement.mine_stone", "achievement.mine_stone.desc", BlockType.STONE.id));
        register(new Achievement("mine_iron", "achievement.mine_iron", "achievement.mine_iron.desc", BlockType.IRON_ORE.id));
        register(new Achievement("mine_diamond", "achievement.mine_diamond", "achievement.mine_diamond.desc", BlockType.DIAMOND_ORE.id, 0xFF55FFFF));

        // Crafting
        register(new Achievement("craft_pickaxe", "achievement.craft_pickaxe", "achievement.craft_pickaxe.desc", BlockType.OAK_PLANKS.id));
        register(new Achievement("craft_furnace", "achievement.craft_furnace", "achievement.craft_furnace.desc", BlockType.FURNACE.id));
        register(new Achievement("craft_sword", "achievement.craft_sword", "achievement.craft_sword.desc", BlockType.STONE.id));

        // Smelting
        register(new Achievement("smelt_iron", "achievement.smelt_iron", "achievement.smelt_iron.desc", BlockType.IRON_BLOCK.id));

        // Combat
        register(new Achievement("kill_mob", "achievement.kill_mob", "achievement.kill_mob.desc", -1));

        // Building
        register(new Achievement("place_block", "achievement.place_block", "achievement.place_block.desc", BlockType.OAK_PLANKS.id));

        // [ECO] Trading
        register(new Achievement("first_trade", "achievement.first_trade", "achievement.first_trade.desc", BlockType.EMERALD_BLOCK.id));
        register(new Achievement("village_hero", "achievement.village_hero", "achievement.village_hero.desc", BlockType.EMERALD_BLOCK.id, 0xFFAAFFAA));
        register(new Achievement("meteorite", "achievement.meteorite", "achievement.meteorite.desc", BlockType.METEORITE_ORE.id));
    }

    public static void register(Achievement ach) {
        achievements.put(ach.id, ach);
    }

    public static void setToastManager(ToastManager tm) {
        toastManager = tm;
    }

    /**
     * Trigger an achievement by ID. If it hasn't been unlocked yet,
     * unlocks it and shows a toast notification.
     */
    public static void trigger(String id) {
        if (unlocked.contains(id)) return;
        Achievement ach = achievements.get(id);
        if (ach == null) return;

        unlocked.add(id);

        if (toastManager != null) {
            String title = Language.tr(ach.titleKey);
            String desc = Language.tr(ach.descriptionKey);
            toastManager.enqueue(new AchievementToast(title, desc, ach.iconBlockId, ach.color));
        }

        System.out.println("[Achievement] Unlocked: " + ach.id + " - " + ach.titleKey);
    }

    public static boolean isUnlocked(String id) {
        return unlocked.contains(id);
    }

    public static int getUnlockedCount() {
        return unlocked.size();
    }

    public static int getTotalCount() {
        return achievements.size();
    }

    public static Collection<Achievement> getAll() {
        return achievements.values();
    }

    public static void clear() {
        unlocked.clear();
    }
}
