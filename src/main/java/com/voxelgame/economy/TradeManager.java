package com.voxelgame.economy;

import com.voxelgame.item.Inventory;
import com.voxelgame.item.ItemStack;

/**
 * [ECO] Trade execution engine.
 *
 * Reads the player's inventory, applies the reputation discount to any
 * emerald payment, consumes the required goods, hands out the reward and
 * reports back the outcome. All item matching is by canonical name so that
 * both registry items and BlockType pseudo-block stacks are accepted.
 */
public final class TradeManager {

    public enum Result {
        SUCCESS,          // trade went through (+1 reputation)
        OUT_OF_STOCK,     // this villager has already sold all of them
        CANNOT_PAY,       // the player does not own the required goods
        NO_SPACE          // the reward did not fit in the inventory
    }

    /**
     * Execute one offer against the given inventory using the player's
     * current reputation.
     *
     * @param stockRemaining how many of this offer the villager still has
     * @return the outcome; stockRemaining is NOT touched here (the caller
     *         owns the villager's counters)
     */
    public static Result execute(Inventory inv, TradeOffer offer, int reputation, int stockRemaining) {
        if (offer == null) return Result.CANNOT_PAY;
        if (stockRemaining <= 0) return Result.OUT_OF_STOCK;

        int payA = offer.costA.getCount();
        if (isEmerald(offer.costA)) {
            payA = Reputation.discountedPrice(reputation, payA);
        }
        if (inv.countByName(Inventory.canonicalName(offer.costA)) < payA) return Result.CANNOT_PAY;
        if (offer.costB != null && !offer.costB.isEmpty()) {
            if (inv.countByName(Inventory.canonicalName(offer.costB)) < offer.costB.getCount()) {
                return Result.CANNOT_PAY;
            }
        }

        // Consume the payment, then try to place the reward.
        inv.removeByName(Inventory.canonicalName(offer.costA), payA);
        if (offer.costB != null && !offer.costB.isEmpty()) {
            inv.removeByName(Inventory.canonicalName(offer.costB), offer.costB.getCount());
        }

        ItemStack leftover = inv.addStack(offer.give.copy());
        if (!leftover.isEmpty()) {
            // Reward did not fit — refund the payment.
            inv.addStack(offer.costA.copyWithCount(payA));
            if (offer.costB != null && !offer.costB.isEmpty()) {
                inv.addStack(offer.costB.copy());
            }
            return Result.NO_SPACE;
        }

        return Result.SUCCESS;
    }

    private static boolean isEmerald(ItemStack s) {
        return s != null && !s.isEmpty()
            && "emerald".equals(Inventory.canonicalName(s));
    }

    private TradeManager() {}
}