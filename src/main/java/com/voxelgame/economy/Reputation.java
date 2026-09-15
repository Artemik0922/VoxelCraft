package com.voxelgame.economy;

/**
 * [ECO] Village reputation.
 *
 * Reputation grows by +1 on every completed trade. It drives the price
 * discount villagers give the player: higher tiers mean friendlier prices.
 *
 *   Нейтрал      (0-4)     0% discount
 *   Дружелюбный  (5-14)    5%  discount
 *   Доверенный   (15-29)   15% discount
 *   Герой        (30+)     30% discount   (also unlocks hero perks)
 */
public final class Reputation {

    public static final int FRIENDLY_TIER  = 5;
    public static final int TRUSTED_TIER   = 15;
    public static final int HERO_TIER      = 30;

    /** 0..3 tier index, used to look up "reputation.neutral/friendly/...". */
    public static int tier(int reputation) {
        if (reputation >= HERO_TIER) return 3;
        if (reputation >= TRUSTED_TIER) return 2;
        if (reputation >= FRIENDLY_TIER) return 1;
        return 0;
    }

    /** Language key for the current tier ("reputation.neutral", ...). */
    public static String tierKey(int reputation) {
        return switch (tier(reputation)) {
            case 1 -> "reputation.friendly";
            case 2 -> "reputation.trusted";
            case 3 -> "reputation.hero";
            default -> "reputation.neutral";
        };
    }

    public static boolean isHero(int reputation) {
        return reputation >= HERO_TIER;
    }

    /** Percentage discount (5 / 15 / 30) for the given reputation. */
    public static int discountPercent(int reputation) {
        return switch (tier(reputation)) {
            case 1 -> 5;
            case 2 -> 15;
            case 3 -> 30;
            default -> 0;
        };
    }

    /** Emerald payment after the reputation discount, at least 1. */
    public static int discountedPrice(int reputation, int baseEmeralds) {
        if (baseEmeralds <= 1) return baseEmeralds;
        int cut = Math.floorDiv(baseEmeralds * discountPercent(reputation), 100);
        return Math.max(1, baseEmeralds - cut);
    }

    private Reputation() {}
}