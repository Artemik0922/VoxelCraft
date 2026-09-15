package com.voxelgame.ui.screen;

import com.voxelgame.economy.Reputation;
import com.voxelgame.economy.TradeManager;
import com.voxelgame.economy.TradeOffer;
import com.voxelgame.item.Inventory;
import com.voxelgame.item.ItemStack;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.StackIcons;
import com.voxelgame.ui.UIRenderer;

import static com.voxelgame.core.Language.tr;

/**
 * [ECO] Trading screen.
 *
 * A compact offer list: each row shows what the player hands over, what the
 * villager returns and how many uses remain. Clicking a row selects it, the
 * Trade button runs the deal through {@link TradeManager}.
 */
public class TradingScreen extends Screen {

    public interface Callbacks {
        Inventory inventory();
        int reputation();
        /** How many uses of offer {@code idx} remain for this shop. */
        int stockOf(int idx);
        /** Execute offer {@code idx}; the host owns stock + reputation. */
        TradeManager.Result trade(int idx);
        void onClose();
    }

    private static final int SLOT = GuiAssets.SLOT_SIZE;
    private static final int ROW_H = 44;
    private static final int ROW_PAD = 10;

    private final Callbacks callbacks;
    private final TextureAtlas atlas;
    private final String titleKey;

    private TradeOffer[] offers;
    private int selected = -1;
    private TradeManager.Result lastResult = null;
    private int visibleRows = 0;

    private int panelX, panelY, panelW, panelH;
    private int listY;
    private int buttonX, buttonY, buttonW, buttonH;

    public TradingScreen(TextureAtlas atlas, String titleKey, Callbacks callbacks) {
        this.atlas = atlas;
        this.titleKey = titleKey;
        this.callbacks = callbacks;
    }

    @Override
    public boolean rendersWorld() { return false; }

    @Override
    public boolean usesBlurredBackdrop() { return true; }

    public void setOffers(TradeOffer[] offers) {
        this.offers = offers == null ? new TradeOffer[0] : offers;
        this.selected = Math.min(selected, offers.length - 1);
    }

    @Override
    protected void layout() {
        int maxVisible = Math.max(1, (height - 160) / ROW_H);
        visibleRows = Math.min(offers == null ? 0 : offers.length, maxVisible);
        panelW = 340;
        panelH = ROW_PAD + 18 + 6 + ROW_PAD + visibleRows * ROW_H + ROW_PAD + 24 + ROW_PAD;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        listY = panelY + ROW_PAD + 20;
        buttonH = 22;
        buttonW = 110;
        buttonX = panelX + (panelW - buttonW) / 2;
        buttonY = panelY + panelH - ROW_PAD - buttonH;
    }

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets gui) {
        MenuTheme.drawWorldOverlay(ui, width, height);
        ui.drawNineSlice(gui.glassPanel, panelX, panelY, panelW, panelH,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF10141E);
        font.draw(ui, tr(titleKey), panelX + ROW_PAD, panelY + 8, 0xFFE8EEFF);

        if (offers == null || offers.length == 0) {
            font.draw(ui, tr("trade.empty"), panelX + ROW_PAD, listY + 12, 0xFF8EA2C2);
            return;
        }

        // Reputation hint under the title
        int rep = callbacks.reputation();
        int discount = Reputation.discountPercent(rep);
        String sub = tr("trade.reputation") + ": " + tr(Reputation.tierKey(rep));
        if (discount > 0) sub += " · −" + discount + "%";
        font.draw(ui, sub, panelX + ROW_PAD, panelY + 14, 0xFF8EA2C2);
        font.draw(ui, tr("trade.emeralds") + ": " + emeraldCount(),
            panelX + panelW - 106 - ROW_PAD, panelY + 8, 0xFFE8B23A);

        // Offer rows
        for (int i = 0; i < visibleRows; i++) {
            int ry = listY + i * ROW_H;
            boolean hover = hoverX >= panelX + ROW_PAD && hoverX < panelX + panelW - ROW_PAD
                && hoverY >= ry && hoverY < ry + ROW_H;

            if (selected == i) {
                ui.useSolidColor();
                ui.fillRect(panelX + 6, ry, panelW - 12, ROW_H - 4, 0x2AFFFFFF | MenuTheme.ACCENT);
            } else if (hover) {
                ui.useSolidColor();
                ui.fillRect(panelX + 6, ry, panelW - 12, ROW_H - 4, 0x18FFFFFF);
            }

            TradeOffer o = offers[i];
            int stock = callbacks.stockOf(i);
            int y = ry + 8;

            // Cost icons (the counted payment; emeralds get the discount)
            int pay = emeraldCost(o, rep);
            ui.drawNineSlice(gui.glassSlot, panelX + ROW_PAD, y, SLOT, SLOT,
                3, GuiAssets.SLOT_SIZE, 0xFFFFFFFF);
            StackIcons.drawIcon(ui, atlas, o.costA, panelX + ROW_PAD + 1, y + 1, SLOT - 2);
            drawPaymentCount(ui, font, pay, panelX + ROW_PAD, y + SLOT,
                isEmeraldPay(o) ? 0xFFE8B23A : 0xFFB8C2DC);

            int cx = panelX + ROW_PAD + 28;
            if (o.costB != null && !o.costB.isEmpty()) {
                ui.drawNineSlice(gui.glassSlot, cx, y, SLOT, SLOT,
                    3, GuiAssets.SLOT_SIZE, 0xFFFFFFFF);
                StackIcons.drawIcon(ui, atlas, o.costB, cx + 1, y + 1, SLOT - 2);
                cx += 28;
            }

            font.draw(ui, "→", cx, y + 6, 0xFF8EA2C2);
            cx += 14;

            // Output
            ui.drawNineSlice(gui.glassSlot, cx, y, SLOT, SLOT,
                3, GuiAssets.SLOT_SIZE, 0xFFFFFFFF);
            StackIcons.drawIcon(ui, atlas, o.give, cx + 1, y + 1, SLOT - 2);
            int gx = cx + SLOT + 6;

            String name = StackIcons.displayName(o.give);
            if (o.give.getCount() > 1) name += "×" + o.give.getCount();
            font.draw(ui, name, gx, ry + 10, 0xFFE8EEFF);
            String hint = isEmeraldPay(o)
                ? tr("trade.sells")
                : tr("trade.buys");
            font.draw(ui, hint, gx, ry + 26, 0xFF8EA2C2);

            // Stock badge
            String stockTxt = stock <= 0 ? tr("trade.sold_out")
                : "×" + stock;
            font.draw(ui, stockTxt, panelX + panelW - ROW_PAD - 54, ry + 10,
                stock <= 0 ? 0xFFD05050 : 0xFF8EA2C2);
        }
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets gui,
                                    float mx, float my) {
        hoverX = mx;
        hoverY = my;

        if (offers == null || offers.length == 0) return;

        boolean canTrade = selected >= 0 && selected < offers.length
            && callbacks.stockOf(selected) > 0;
        ui.drawNineSlice(gui.glassPanel, buttonX, buttonY, buttonW, buttonH,
            GuiAssets.GLASS_BORDER, canTrade ? 0xFF2A3A5E : GuiAssets.GLASS_WIDGET,
            canTrade ? 0xFF2A3A5E : 0xFF141A28);
        String label = tr("trade.make");
        font.draw(ui, label, buttonX + (buttonW - font.width(label)) / 2, buttonY + 6,
            canTrade ? 0xFFE8EEFF : 0xFF6B7488);

        if (lastResult != null) {
            int color = lastResult == TradeManager.Result.SUCCESS ? 0xFF7AD07A : 0xFFD08950;
            font.draw(ui, message(lastResult), buttonX, buttonY - 14, color);
        }
    }

    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button != 0 && button != 1) return false;
        if (offers == null) return true;

        // Rows select an offer; double-left-click trades right away.
        for (int i = 0; i < visibleRows; i++) {
            int ry = listY + i * ROW_H;
            if (mx >= panelX + ROW_PAD && mx < panelX + panelW - ROW_PAD
                && my >= ry && my < ry + ROW_H - 4) {
                if (selected == i && button == 0) {
                    doTrade(i);
                } else {
                    selected = i;
                }
                return true;
            }
        }

        if (mx >= buttonX && mx < buttonX + buttonW
            && my >= buttonY && my < buttonY + buttonH) {
            doTrade(selected);
            return true;
        }
        return true;
    }

    private void doTrade(int idx) {
        if (idx < 0 || idx >= offers.length) return;
        lastResult = callbacks.trade(idx);
        if (lastResult != TradeManager.Result.SUCCESS) selected = idx;
    }

    private String message(TradeManager.Result r) {
        return switch (r) {
            case SUCCESS -> tr("trade.done");
            case OUT_OF_STOCK -> tr("trade.out_of_stock");
            case CANNOT_PAY -> tr("trade.cannot_pay");
            case NO_SPACE -> tr("trade.no_space");
        };
    }

    private int emeraldCount() {
        return callbacks.inventory().countByName("emerald");
    }

    private boolean isEmeraldPay(TradeOffer o) {
        return o.costA != null && "emerald".equals(Inventory.canonicalName(o.costA));
    }

    private int emeraldCost(TradeOffer o, int reputation) {
        if (!isEmeraldPay(o)) return o.costA.getCount();
        return Reputation.discountedPrice(reputation, o.costA.getCount());
    }

    private void drawPaymentCount(UIRenderer ui, FontRenderer font, int n, int x, int y, int color) {
        String text = "×" + n;
        font.draw(ui, text, x + SLOT - font.width(text), y - 1, color);
    }

    private float hoverX, hoverY;

    @Override
    public void onClosed() {
        callbacks.onClose();
    }
}