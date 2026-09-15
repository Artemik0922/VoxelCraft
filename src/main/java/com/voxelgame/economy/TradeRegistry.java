package com.voxelgame.economy;

import com.voxelgame.item.ItemRegistry;
import com.voxelgame.item.ItemStack;
import com.voxelgame.world.BlockType;
import com.voxelgame.world.entity.Villager;

import java.util.HashMap;
import java.util.Map;

/**
 * [ECO] Static trade tables keyed by villager profession.
 *
 * Every profession offers 2–4 trades. The wandering trader (accessed
 * through the emerald vault block) sells meteorite ore, rocket parts and
 * potions that no village profession carries.
 */
public final class TradeRegistry {

    /** Max reusable offers. */
    private static final int HIGH = 12;
    private static final int MED  = 8;
    private static final int LOW  = 4;

    private static final Map<Villager.Profession, TradeOffer[]> TABLES = new HashMap<>();

    static {
        // ------------------------------------------------------------------
        // Village professions
        // ------------------------------------------------------------------

        TABLES.put(Villager.Profession.FARMER, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(BlockType.WHEAT, 16), 1, HIGH),
            TradeOffer.buy(new ItemStack(BlockType.CARROT, 16), 1, HIGH),
            TradeOffer.buy(new ItemStack(BlockType.POTATO, 16), 1, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 1),
                new ItemStack(ItemRegistry.BREAD, 5), HIGH),
        });

        TABLES.put(Villager.Profession.LIBRARIAN, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(ItemRegistry.BOOK, 1), 2, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 2),
                new ItemStack(ItemRegistry.BOOK, 1), HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 5),
                new ItemStack(BlockType.BOOKSHELF, 1), LOW),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 3),
                new ItemStack(ItemRegistry.LAPIS_LAZULI, 1), MED),
        });

        TABLES.put(Villager.Profession.BLACKSMITH, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(ItemRegistry.COAL, 16), 1, HIGH),
            TradeOffer.buy(new ItemStack(ItemRegistry.IRON_INGOT, 6), 1, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 5),
                new ItemStack(ItemRegistry.IRON_PICKAXE, 1), LOW),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 4),
                new ItemStack(ItemRegistry.BUCKET, 1), MED),
        });

        TABLES.put(Villager.Profession.BUTCHER, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(ItemRegistry.RAW_BEEF, 14), 1, HIGH),
            TradeOffer.buy(new ItemStack(ItemRegistry.RAW_PORK, 14), 1, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 1),
                new ItemStack(ItemRegistry.COOKED_BEEF, 1), HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 1),
                new ItemStack(ItemRegistry.COOKED_PORK, 1), HIGH),
        });

        TABLES.put(Villager.Profession.PRIEST, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(ItemRegistry.SPIDER_EYE, 1), 1, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 2),
                new ItemStack(BlockType.GLOWSTONE, 1), MED),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 3),
                new ItemStack(ItemRegistry.POTION_HEALING, 1), LOW),
        });

        TABLES.put(Villager.Profession.FISHERMAN, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(ItemRegistry.STRING, 6), 1, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 1),
                new ItemStack(BlockType.ICE, 1), MED),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 1),
                new ItemStack(BlockType.LILY_PAD, 1), MED),
        });

        TABLES.put(Villager.Profession.FLETCHER, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(ItemRegistry.FLINT, 7), 1, HIGH),
            TradeOffer.buy(new ItemStack(ItemRegistry.STICK, 12), 1, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 1),
                new ItemStack(ItemRegistry.ARROW, 8), HIGH),
        });

        TABLES.put(Villager.Profession.LEATHERWORKER, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(ItemRegistry.LEATHER, 6), 1, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 2),
                new ItemStack(ItemRegistry.LEATHER_CHESTPLATE, 1), MED),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 1),
                new ItemStack(ItemRegistry.LEATHER_BOOTS, 1), HIGH),
        });

        TABLES.put(Villager.Profession.SHEPHERD, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(BlockType.WHITE_WOOL, 12), 1, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 2),
                new ItemStack(BlockType.RED_CONCRETE, 1), MED),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 2),
                new ItemStack(BlockType.BLUE_CONCRETE, 1), MED),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 2),
                new ItemStack(BlockType.GREEN_CONCRETE, 1), MED),
        });

        TABLES.put(Villager.Profession.TOOLSMITH, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(ItemRegistry.IRON_INGOT, 4), 1, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 2),
                new ItemStack(ItemRegistry.IRON_SHOVEL, 1), HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 7),
                new ItemStack(ItemRegistry.DIAMOND_SHOVEL, 1), LOW),
        });

        TABLES.put(Villager.Profession.ARMORER, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(ItemRegistry.IRON_INGOT, 4), 1, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 3),
                new ItemStack(ItemRegistry.IRON_HELMET, 1), MED),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 5),
                new ItemStack(ItemRegistry.IRON_CHESTPLATE, 1), MED),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 6),
                new ItemStack(ItemRegistry.DIAMOND_BOOTS, 1), LOW),
        });

        TABLES.put(Villager.Profession.WEAPONSMITH, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(ItemRegistry.GUNPOWDER, 5), 1, HIGH),
            TradeOffer.buy(new ItemStack(ItemRegistry.IRON_INGOT, 5), 1, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 4),
                new ItemStack(ItemRegistry.IRON_SWORD, 1), MED),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 8),
                new ItemStack(ItemRegistry.DIAMOND_SWORD, 1), LOW),
        });

        TABLES.put(Villager.Profession.CARTOGRAPHER, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(BlockType.CLAY, 4), 1, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 2),
                new ItemStack(BlockType.BAMBOO, 1), MED),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 2),
                new ItemStack(BlockType.CACTUS, 1), MED),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 3),
                new ItemStack(BlockType.BLUE_ICE, 1), LOW),
        });

        TABLES.put(Villager.Profession.CLERIC, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(ItemRegistry.ROTTEN_FLESH, 1), 1, HIGH),
            TradeOffer.buy(new ItemStack(ItemRegistry.GOLD_INGOT, 1), 1, MED),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 1),
                new ItemStack(ItemRegistry.WATER_BOTTLE, 1), MED),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 3),
                new ItemStack(ItemRegistry.POTION_HEALING, 1), LOW),
        });

        TABLES.put(Villager.Profession.MASON, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(BlockType.COBBLESTONE, 8), 1, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 1),
                new ItemStack(BlockType.STONE_BRICKS, 1), HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 2),
                new ItemStack(BlockType.CHISELED_SANDSTONE, 1), MED),
        });

        TABLES.put(Villager.Profession.WARRIOR, new TradeOffer[]{
            TradeOffer.buy(new ItemStack(ItemRegistry.ARROW, 6), 1, HIGH),
            TradeOffer.buy(new ItemStack(ItemRegistry.IRON_INGOT, 4), 1, HIGH),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 4),
                new ItemStack(ItemRegistry.IRON_SWORD, 1), MED),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 5),
                new ItemStack(BlockType.TNT, 1), LOW),
        });

        // NITWIT: no trades
        TABLES.put(Villager.Profession.NITWIT, new TradeOffer[]{});

        // ------------------------------------------------------------------
        // Wandering trader / Emerald Vault (exclusives + meteors)
        // ------------------------------------------------------------------
        TABLES.put(null, new TradeOffer[]{
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 5),
                new ItemStack(BlockType.METEORITE_ORE, 1), LOW),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 6),
                new ItemStack(BlockType.ROCKET_FUEL, 1), LOW),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 4),
                new ItemStack(ItemRegistry.POTION_FIRE_RESISTANCE, 1), LOW),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 3),
                new ItemStack(ItemRegistry.SLIME_BALL, 4), MED),
            TradeOffer.sell(new ItemStack(ItemRegistry.EMERALD, 6),
                new ItemStack(BlockType.ROCKET_BODY, 1), LOW),
        });
    }

    /** Offers for a village profession, or an empty array for NITWIT. */
    public static TradeOffer[] offersFor(Villager.Profession p) {
        TradeOffer[] offers = TABLES.get(p);
        return offers != null ? offers : new TradeOffer[0];
    }

    /** Wandering-trader / vault catalogue (keyed by null profession). */
    public static TradeOffer[] wanderingOffers() {
        return offersFor(null);
    }

    private TradeRegistry() {}
}