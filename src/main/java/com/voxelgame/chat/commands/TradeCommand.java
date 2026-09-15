package com.voxelgame.chat.commands;

/**
 * [ECO] /trade — open the trading screen with the nearest villager, or
 * the vault/wandering-trader catalogue when no one is in range.
 */
public class TradeCommand implements Command {

    public interface TradeOpener {
        /** Open with the nearest villager if one is in range, else the vault. */
        void openNearestTrade();
    }

    public static TradeOpener opener;

    @Override
    public String getName() { return "trade"; }

    @Override
    public String getUsage() { return "/trade"; }

    @Override
    public String getDescription() { return "торговать с ближайшим жителем"; }

    @Override
    public String execute(String[] args) {
        if (opener == null) return "Trade system not ready.";
        opener.openNearestTrade();
        return null;
    }
}