package com.voxelgame.world.fishing;

import com.voxelgame.achievement.AchievementRegistry;
import com.voxelgame.item.ItemRegistry;
import com.voxelgame.item.ItemStack;
import com.voxelgame.stats.WorldStats;
import com.voxelgame.world.World;
import com.voxelgame.world.entity.FishingBobberEntity;
import org.joml.Vector3f;

/**
 * Manages the fishing lifecycle: casting, waiting, biting, reeling in.
 * The bobber entity handles physics; this controller handles game logic.
 */
public class FishingController {

    private final World world;
    private final WorldStats stats;
    private final com.voxelgame.player.Player player;
    private FishingBobberEntity bobber;
    private ItemStack rod;

    /** Forwarded from the bobber when a bite starts (Game wires splash FX). */
    public Runnable onBite;

    private static final int ROD_DURABILITY = 64;
    private static final double JUNK_CHANCE = 0.10;

    public FishingController(World world, WorldStats stats, com.voxelgame.player.Player player) {
        this.world = world;
        this.stats = stats;
        this.player = player;
    }

    /**
     * Cast the fishing rod. Returns true if the cast was successful.
     */
    public boolean cast(Vector3f pos, Vector3f dir, ItemStack rodStack) {
        if (bobber != null && !bobber.isDead()) return false;
        if (rodStack.getItem() != ItemRegistry.FISHING_ROD) return false;

        rod = rodStack;
        Vector3f castPos = new Vector3f(pos);
        castPos.y += 1.5f; // eye height
        bobber = new FishingBobberEntity(world, castPos, dir);
        bobber.onBite = () -> { if (onBite != null) onBite.run(); };
        world.setFishingBobber(bobber);
        return true;
    }

    /**
     * Reel in. Returns the result: "fish", "junk", "nothing", or null if no bobber.
     */
    public String reelIn() {
        if (bobber == null || bobber.isDead()) return null;

        FishingBobberEntity.State state = bobber.getState();
        bobber.markDead();
        world.setFishingBobber(null);

        if (state == FishingBobberEntity.State.BITE) {
            return catchFish();
        } else if (state == FishingBobberEntity.State.WAITING) {
            return "nothing";
        }
        return null;
    }

    private String catchFish() {
        if (rod != null && !isCreative()) {
            damageRod();
        }

        stats.add(WorldStats.FISH_CAUGHT, 1);
        AchievementRegistry.trigger("first_fish");

        long fishCount = stats.get(WorldStats.FISH_CAUGHT);
        if (fishCount >= 10) {
            AchievementRegistry.trigger("fish_10");
        }

        if (Math.random() < JUNK_CHANCE) {
            return "junk";
        }
        return "fish";
    }

    private void damageRod() {
        if (rod == null) return;
        int unbreakingLevel = rod.getEnchantLevel(com.voxelgame.item.Enchantment.UNBREAKING);
        if (unbreakingLevel > 0 && Math.random() < 1.0 / (unbreakingLevel + 1)) {
            return; // Unbreaking saved the rod
        }
        rod.damage(1);
    }

    private boolean isCreative() {
        return player != null && player.isCreative();
    }

    public void update(float dt) {
        if (bobber != null && !bobber.isDead()) {
            bobber.update(dt);
        }
    }

    public FishingBobberEntity getBobber() { return bobber; }
    public boolean hasBobber() { return bobber != null && !bobber.isDead(); }

    public void clear() {
        if (bobber != null) bobber.markDead();
        bobber = null;
        rod = null;
    }
}
