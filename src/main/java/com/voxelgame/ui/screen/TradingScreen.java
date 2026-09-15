package com.voxelgame.ui.screen;

import com.voxelgame.economy.Reputation;
import com.voxelgame.economy.TradeManager;
import com.voxelgame.economy.TradeOffer;
import com.voxelgame.item.Inventory;
import com.voxelgame.item.ItemStack;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GlassTooltip;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.StackIcons;
import com.voxelgame.ui.UIRenderer;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.voxelgame.core.Language.tr;

/**
 * [ECO] Trading screen.
 *
 * A compact offer list: each row shows what the player hands over, what the
 * villager returns and how many uses remain. Clicking a row selects it, the
 * Trade button (or double-click, or Enter) runs the deal through
 * {@link TradeManager}. Long lists scroll with the wheel or a draggable
 * scrollbar; rows the player cannot afford or that are sold out are faded,
 * and hovering a row shows a tooltip with the full cost breakdown.
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
    private static final int SCROLLBAR_W = 6;

    private final Callbacks callbacks;
    private final TextureAtlas atlas;
    private final String titleKey;

    private TradeOffer[] offers;
    private int selected = -1;
    private TradeManager.Result lastResult = null;
    private long lastResultUntil = 0;
    private int visibleRows = 0;
    private int scrollRow = 0;
    private boolean draggingThumb = false;

    private int panelX, panelY, panelW, panelH;
    private int listX, listY, listW, listH;
    private int scrollbarX;
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
        int maxVisible = Math.max(1, (height - 190) / ROW_H);
        visibleRows = Math.min(offers == null ? 0 : offers.length, maxVisible);
        panelW = 360;
        int headerH = 30;
        listH = visibleRows * ROW_H;
        panelH = headerH + listH + 16 + 22 + 8;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        listX = panelX + ROW_PAD;
        listY = panelY + headerH;
        listW = panelW - ROW_PAD * 2 - SCROLLBAR_W - 4;
        scrollbarX = panelX + panelW - ROW_PAD - SCROLLBAR_W;

        buttonH = 22;
        buttonW = 120;
        buttonX = panelX + (panelW - buttonW) / 2;
        buttonY = panelY + panelH - 10 - buttonH;

        clampScroll();
    }

    private int maxScrollRow() {
        return Math.max(0, offers == null ? 0 : offers.length - visibleRows);
    }

    private void clampScroll() {
        scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow));
    }

    private void ensureSelectedVisible() {
        if (selected < scrollRow) scrollRow = selected;
        else if (selected >= scrollRow + visibleRows) scrollRow = selected - visibleRows + 1;
        clampScroll();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets gui) {
        MenuTheme.drawWorldOverlay(ui, width, height);
        ui.drawNineSlice(gui.glassPanel, panelX, panelY, panelW, panelH,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF10141E);
        font.draw(ui, tr(titleKey), panelX + ROW_PAD, panelY + 7, 0xFFE8EEFF);
        font.drawRight(ui, tr("trade.emeralds") + ": " + emeraldCount(),
            panelX + panelW - ROW_PAD, panelY + 7, 0xFFE8B23A);

        if (offers == null || offers.length == 0) {
            font.draw(ui, tr("trade.empty"), panelX + ROW_PAD, listY + 12, 0xFF8EA2C2);
            return;
        }

        int rep = callbacks.reputation();
        int discount = Reputation.discountPercent(rep);
        String sub = tr("trade.reputation") + ": " + tr(Reputation.tierKey(rep));
        if (discount > 0) sub += " · −" + discount + "%";
        font.draw(ui, sub, panelX + ROW_PAD, panelY + 18, 0xFF8EA2C2);

        for (int r = 0; r < visibleRows; r++) {
            int row = scrollRow + r;
            if (row >= offers.length) break;
            drawRow(ui, font, gui, row, listY + r * ROW_H);
        }

        drawScrollbar(ui, gui);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets gui,
                                    float mx, float my) {
        hoverX = mx;
        hoverY = my;

        if (offers == null || offers.length == 0) return;

        if (lastResult != null && System.currentTimeMillis() > lastResultUntil) {
            lastResult = null;
        }
        drawButton(ui, font, gui, mx, my);

        // Row tooltip (uses the live cursor position)
        int hovered = rowAt(mx, my);
        if (hovered >= 0) {
            drawRowTooltip(ui, font, hovered, mx, my);
        }
    }

    private void drawRow(UIRenderer ui, FontRenderer font, GuiAssets gui, int row, int ry) {
        TradeOffer o = offers[row];
        int rep = callbacks.reputation();
        int stock = callbacks.stockOf(row);
        int pay = emeraldCost(o, rep);
        boolean payEmeralds = isEmeraldPay(o);
        boolean affordable = canAfford(o, pay);
        boolean soldOut = stock <= 0;

        boolean hover = hoverX >= listX && hoverX < scrollbarX
            && hoverY >= ry && hoverY < ry + ROW_H;

        if (selected == row) {
            ui.useSolidColor();
            ui.fillRect(listX - 2, ry, scrollbarX - (listX - 2), ROW_H - 4, 0x2A000000 | MenuTheme.ACCENT);
            ui.fillRect(listX - 3, ry + 2, 2, ROW_H - 8, 0xFF000000 | MenuTheme.ACCENT);
        } else if (hover) {
            ui.useSolidColor();
            ui.fillRect(listX - 2, ry, scrollbarX - (listX - 2), ROW_H - 4, 0x18FFFFFF);
        }
        if (soldOut) {
            ui.useSolidColor();
            ui.fillRect(listX - 2, ry, scrollbarX - (listX - 2), ROW_H - 4, 0x26000000);
        }

        int y = ry + 8;
        int dx = listX;

        // Cost icons (the counted payment; emeralds get the discount)
        ui.drawNineSlice(gui.glassSlot, dx, y, SLOT, SLOT, 3, GuiAssets.SLOT_SIZE, 0xFFFFFFFF);
        StackIcons.drawIcon(ui, atlas, o.costA, dx + 1, y + 1, SLOT - 2);
        drawCount(ui, font, pay, dx, y, payEmeralds, !affordable);
        dx += SLOT + 3;

        if (o.costB != null && !o.costB.isEmpty()) {
            ui.drawNineSlice(gui.glassSlot, dx, y, SLOT, SLOT, 3, GuiAssets.SLOT_SIZE, 0xFFFFFFFF);
            StackIcons.drawIcon(ui, atlas, o.costB, dx + 1, y + 1, SLOT - 2);
            boolean shortB = callbacks.inventory().countByName(
                Inventory.canonicalName(o.costB)) < o.costB.getCount();
            drawCount(ui, font, o.costB.getCount(), dx, y, false, shortB);
            dx += SLOT + 3;
        }

        font.draw(ui, "→", dx, y + 5, 0xFF8EA2C2);
        dx += 14;

        ui.drawNineSlice(gui.glassSlot, dx, y, SLOT, SLOT, 3, GuiAssets.SLOT_SIZE, 0xFFFFFFFF);
        StackIcons.drawIcon(ui, atlas, o.give, dx + 1, y + 1, SLOT - 2);
        dx += SLOT + 6;

        // Give name, clipped so it never collides with the stock badge
        String badge = soldOut ? tr("trade.sold_out") : "×" + stock;
        int textRight = scrollbarX - 4;
        int nameMax = Math.max(20, textRight - font.width(badge) - 8 - dx);
        String name = font.trimToWidth(stackLabel(o.give), nameMax);

        int nameColor = soldOut ? 0xFF4A5055 : (affordable ? 0xFFE8EEFF : 0xFF8EA2C2);
        font.draw(ui, name, dx, ry + 10, nameColor);
        font.draw(ui, tr(isEmeraldPay(o) ? "trade.sells" : "trade.buys"),
            dx, ry + 26, soldOut ? 0xFF4A5055 : 0xFF8EA2C2);

        font.draw(ui, badge, textRight - font.width(badge), ry + 10,
            soldOut ? 0xFFD05050 : 0xFF8EA2C2);
    }

    private void drawCount(UIRenderer ui, FontRenderer font, int n, int x, int y,
                           boolean gold, boolean shortfall) {
        String text = "×" + n;
        int color = shortfall ? 0xFFD05050 : (gold ? 0xFFE8B23A : 0xFFB8C2DC);
        font.drawWithShadow(ui, text, x + SLOT - font.width(text), y + SLOT - 1, color);
    }

    private void drawScrollbar(UIRenderer ui, GuiAssets gui) {
        ui.drawNineSlice(gui.glassTrack, scrollbarX, listY, SCROLLBAR_W, listH,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF151A2A);
        if (maxScrollRow() <= 0) return;

        int thumbH = Math.max(12, listH * visibleRows / Math.max(1, offers.length));
        int travel = listH - thumbH;
        int thumbY = listY + travel * scrollRow / maxScrollRow();

        boolean over = draggingThumb
            || (hoverX >= scrollbarX && hoverX < scrollbarX + SCROLLBAR_W
                && hoverY >= thumbY && hoverY < thumbY + thumbH);

        ui.drawNineSlice(gui.glassPanel, scrollbarX, thumbY, SCROLLBAR_W, thumbH,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET,
            over ? (0xFF000000 | MenuTheme.ACCENT) : 0xFF8EA2C2);
    }

    private void drawButton(UIRenderer ui, FontRenderer font, GuiAssets gui,
                            float mx, float my) {
        boolean canTrade = selected >= 0 && selected < offers.length
            && callbacks.stockOf(selected) > 0
            && canAfford(offers[selected], emeraldCost(offers[selected], callbacks.reputation()));
        boolean over = mx >= buttonX && mx < buttonX + buttonW
            && my >= buttonY && my < buttonY + buttonH;

        int tint = !canTrade ? 0xFF141A28 : (over ? (0xFF000000 | MenuTheme.ACCENT) : 0xFF2A3A5E);
        ui.drawNineSlice(gui.glassPanel, buttonX, buttonY, buttonW, buttonH,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, tint);

        String label = tr("trade.make");
        font.draw(ui, label, buttonX + (buttonW - font.width(label)) / 2, buttonY + 6,
            canTrade ? 0xFFE8EEFF : 0xFF6B7488);

        if (lastResult != null) {
            int color = lastResult == TradeManager.Result.SUCCESS ? 0xFF7AD07A : 0xFFD08950;
            font.draw(ui, message(lastResult), buttonX, buttonY - 13, color);
        }
    }

    private void drawRowTooltip(UIRenderer ui, FontRenderer font, int row,
                                float mx, float my) {
        TradeOffer o = offers[row];
        int rep = callbacks.reputation();
        boolean payEmeralds = isEmeraldPay(o);
        int pay = emeraldCost(o, rep);
        int stock = callbacks.stockOf(row);

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        lines.add("→ " + stackLabel(o.give));
        colors.add(0xFFFFFFFF);

        lines.add(stackLabel(o.costA) + " ×" + (payEmeralds ? pay : o.costA.getCount()));
        colors.add(payEmeralds ? 0xFFE8B23A : 0xFFB8C2DC);

        if (o.costB != null && !o.costB.isEmpty()) {
            lines.add(stackLabel(o.costB) + " ×" + o.costB.getCount());
            colors.add(0xFFB8C2DC);
        }

        lines.add(stock > 0 ? tr("trade.stock") + ": ×" + stock : tr("trade.sold_out"));
        colors.add(stock > 0 ? 0xFFB8C2DC : 0xFFD05050);

        int[] carr = new int[colors.size()];
        for (int i = 0; i < carr.length; i++) carr[i] = colors.get(i);
        GlassTooltip.draw(ui, font, lines, carr, (int) mx, (int) my, width, height);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button != 0 && button != 1) return false;
        if (offers == null) return true;

        // Scrollbar: grab the thumb
        if (mx >= scrollbarX && mx < scrollbarX + SCROLLBAR_W
            && my >= listY && my < listY + listH) {
            if (maxScrollRow() > 0) {
                draggingThumb = true;
                scrollThumbTo(my);
            }
            return true;
        }

        // Rows select an offer; double-left-click trades right away.
        int row = rowAt(mx, my);
        if (row >= 0) {
            if (selected == row && button == 0) {
                doTrade(row);
            } else {
                selected = row;
                ensureSelectedVisible();
            }
            return true;
        }

        if (mx >= buttonX && mx < buttonX + buttonW
            && my >= buttonY && my < buttonY + buttonH) {
            doTrade(selected);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(float mx, float my, int button) {
        if (!draggingThumb) return false;
        scrollThumbTo(my);
        return true;
    }

    @Override
    public void mouseReleased(float mx, float my, int button) {
        draggingThumb = false;
        super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(float mx, float my, double delta) {
        if (offers == null) return true;
        scrollRow -= (int) delta;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (offers == null || offers.length == 0) return false;

        if (key == GLFW.GLFW_KEY_UP) {
            if (selected > 0) {
                selected--;
                ensureSelectedVisible();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            if (selected < offers.length - 1) {
                selected++;
                ensureSelectedVisible();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            doTrade(selected);
            return true;
        }
        return false;
    }

    private void scrollThumbTo(float my) {
        int thumbH = Math.max(12, listH * visibleRows / Math.max(1, offers.length));
        int travel = Math.max(1, listH - thumbH);
        int ratio = (int) Math.max(0, Math.min(travel, my - listY - thumbH / 2.0f));
        scrollRow = maxScrollRow() * ratio / travel;
    }

    private int rowAt(float mx, float my) {
        if (mx < listX || mx >= scrollbarX || my < listY || my >= listY + listH) return -1;
        int row = scrollRow + (int) ((my - listY) / ROW_H);
        return row >= 0 && row < offers.length ? row : -1;
    }

    private void doTrade(int idx) {
        if (idx < 0 || idx >= offers.length) return;
        lastResult = callbacks.trade(idx);
        lastResultUntil = System.currentTimeMillis() + 2500;
        if (lastResult != TradeManager.Result.SUCCESS) {
            selected = idx;
            ensureSelectedVisible();
        }
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

    private boolean canAfford(TradeOffer o, int pay) {
        Inventory inv = callbacks.inventory();
        if (inv.countByName(Inventory.canonicalName(o.costA)) < pay) return false;
        if (o.costB != null && !o.costB.isEmpty()
            && inv.countByName(Inventory.canonicalName(o.costB)) < o.costB.getCount()) return false;
        return true;
    }

    private String stackLabel(ItemStack s) {
        String name = StackIcons.displayName(s);
        if (s.getCount() > 1) name += " ×" + s.getCount();
        return name;
    }

    private float hoverX, hoverY;

    @Override
    public void onClosed() {
        callbacks.onClose();
    }
}