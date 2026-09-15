package com.voxelgame.economy;

import com.voxelgame.item.ItemStack;

/**
 * [ECO] One villager trade offer.
 *
 * The player pays {@code costA} (and the optional {@code costB}) and
 * receives {@code give}. A villager stocks every offer; each offer can be
 * used {@code maxUses} times before it has to refresh.
 *
 * Costs and payments are matched by canonical name, so an offer asking for
 * "wheat" accepts both registry-item wheat and the ITEM_WHEAT pseudo-block
 * stack, and an offer paying emeralds hands out ItemRegistry.EMERALD either
 * way.
 */
public final class TradeOffer {

    /** What the player must hand over (required). */
    public final ItemStack costA;
    /** Optional second payment; may be null. */
    public final ItemStack costB;
    /** What the player receives. */
    public final ItemStack give;
    /** How many times one villager can fulfil this offer before refreshing. */
    public final int maxUses;

    public TradeOffer(ItemStack costA, ItemStack costB, ItemStack give, int maxUses) {
        this.costA = costA;
        this.costB = costB;
        this.give = give;
        this.maxUses = Math.max(1, maxUses);
    }

    /** Villager sells {@code give} in return for {@code cost}. */
    public static TradeOffer sell(ItemStack cost, ItemStack give, int maxUses) {
        return new TradeOffer(cost, null, give, maxUses);
    }

    /** Villager buys {@code surplus} from the player for {@code emeralds}. */
    public static TradeOffer buy(ItemStack surplus, int emeralds, int maxUses) {
        return new TradeOffer(surplus, null, new ItemStack(
            com.voxelgame.item.ItemRegistry.EMERALD, emeralds), maxUses);
    }

    /** Canonical name of the payment item (for the trade screen hint). */
    public String costAName() {
        return com.voxelgame.item.Inventory.canonicalName(costA);
    }

    public String costBName() {
        return costB == null ? "" : com.voxelgame.item.Inventory.canonicalName(costB);
    }

    public String giveName() {
        return com.voxelgame.item.Inventory.canonicalName(give);
    }
}