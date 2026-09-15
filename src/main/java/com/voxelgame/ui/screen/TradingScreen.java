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
import com.voxelgame.ui.widget.Button;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.voxelgame.core.Language.tr;

/**
 * [ECO] Trading screen, Minecraft-trader style.
 *
 * The top half lists the villager's offers on the left (compact entries,
 * scrollable) and shows the selected trade big on the right: payment slots,
 * an arrow and the received stack. The bottom half copies the survival
 * inventory layout - 27 storage slots plus a hotbar strip - so the player
 * can see what they are paying with. Double-click, the Trade button or Enter
 * run the deal through {@link TradeManager}.
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
    private static final int COLS = 9;
    private static final int PANEL_PAD = 7;
    private static final int SECTION_GAP = 4;

    private static final int ENTRY_H = 26;
    private static final int LIST_ROWS = 5;
    private static final int LIST_W = 100;
    private static final int SEL_GAP = 12;
    private static final int SEL_W = 74;
    private static final int SCROLLBAR_W = 4;

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
    private Button tradeButton;

    private int panelX, panelY, panelW, panelH;
    private int listX, listY, listW, listH;
    private int selX, selW;
    private int scrollbarX;
    private int storageX, storageY, hotbarX, hotbarY;
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
        int contentW = COLS * SLOT;
        panelW = PANEL_PAD * 2 + LIST_W + SEL_GAP + SEL_W;
        panelX = (width - panelW) / 2;

        listX = panelX + PANEL_PAD;
        listW = LIST_W;
        listY = panelY + 28;
        selX = listX + listW + SEL_GAP;
        selW = SEL_W;
        listH = Math.max(1, Math.min(offers == null ? 0 : offers.length, LIST_ROWS)) * ENTRY_H;

        int invY = listY + listH + 6;
        storageY = invY;
        hotbarY = storageY + 3 * SLOT + SECTION_GAP;
        panelH = 28 + listH + 6 + (3 * SLOT + SECTION_GAP + SLOT) + 8;
        panelY = (height - panelH) / 2;

        storageX = panelX + (panelW - contentW) / 2;
        hotbarX = storageX;
        scrollbarX = listX + listW - SCROLLBAR_W;

        // Trade button under the selected trade details
        buttonW = 64;
        buttonH = 18;
        buttonX = selX + (selW - buttonW) / 2;
        buttonY = listY + 46;
        if (offers != null && offers.length > 0) {
            tradeButton = add(new Button(buttonX, buttonY, buttonW, buttonH,
                tr("trade.make"), src -> doTrade(selected)));
        }

        clampScroll();
    }

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        if (tradeButton != null) {
            tradeButton.enabled = canTradeNow();
        }
    }

    private int maxScrollRow() {
        return Math.max(0, offers == null ? 0 : offers.length - LIST_ROWS);
    }

    private void clampScroll() {
        scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow));
    }

    private void ensureSelectedVisible() {
        if (selected < scrollRow) scrollRow = selected;
        else if (selected >= scrollRow + LIST_ROWS) scrollRow = selected - LIST_ROWS + 1;
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

        font.draw(ui, tr(titleKey), listX, panelY + 8, 0xFFE8EEFF);
        font.drawRight(ui, tr("trade.emeralds") + ": " + emeraldCount(),
            panelX + panelW - PANEL_PAD, panelY + 8, 0xFFE8B23A);

        if (offers == null || offers.length == 0) {
            font.draw(ui, tr("trade.empty"), listX, listY + 12, 0xFF8EA2C2);
            return;
        }

        int rep = callbacks.reputation();
        int discount = Reputation.discountPercent(rep);
        String sub = tr("trade.reputation") + ": " + tr(Reputation.tierKey(rep));
        if (discount > 0) sub += " · −" + discount + "%";
        font.draw(ui, sub, listX, panelY + 18, MenuTheme.TEXT_SECONDARY);

        // Offer list (left column)
        for (int r = 0; r < LIST_ROWS; r++) {
            int row = scrollRow + r;
            if (row >= offers.length) break;
            drawEntry(ui, font, gui, row, listY + r * ENTRY_H);
        }
        drawScrollbar(ui, gui);

        // Selected trade (right column)
        if (selected >= 0 && selected < offers.length) {
            drawSelectedTrade(ui, font, gui, selected);
        }
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
        if (lastResult != null) {
            int color = lastResult == TradeManager.Result.SUCCESS ? 0xFF7AD07A : 0xFFD08950;
            font.draw(ui, message(lastResult), buttonX, buttonY + buttonH + 2, color);
        }

        // Player inventory copy (bottom half), tooltips run live here
        ItemStack hoveredItem = drawInventory(ui, font, gui, mx, my);

        int offer = rowAt(mx, my);
        if (offer < 0 && selected >= 0 && inSelectedTrade(mx, my)) {
            offer = selected;
        }
        if (offer >= 0) {
            drawTradeTooltip(ui, font, offer, mx, my);
        } else if (hoveredItem != null && !hoveredItem.isEmpty()) {
            StackIcons.drawTooltip(ui, font, atlas, hoveredItem, mx, my, width, height);
        }
    }

    /** Raw copy of the survival-inventory storage grid + hotbar (read-only). */
    private ItemStack drawInventory(UIRenderer ui, FontRenderer font, GuiAssets gui,
                                    float mx, float my) {
        Inventory inv = callbacks.inventory();
        ItemStack hovered = null;

        for (int i = 0; i < Inventory.MAIN_INVENTORY_SIZE; i++) {
            int sx = storageX + (i % COLS) * SLOT;
            int sy = storageY + (i / COLS) * SLOT;
            boolean over = inside(mx, my, sx, sy);
            ui.drawNineSlice(over ? gui.glassSlotHover : gui.glassSlot,
                sx, sy, SLOT, SLOT, 3, GuiAssets.SLOT_SIZE, 0xFFFFFFFF);
            ItemStack stack = inv.getInventoryItem(i);
            if (!stack.isEmpty()) {
                StackIcons.drawStack(ui, font, atlas, stack, sx + 1, sy + 1);
                if (over) hovered = stack;
            }
        }

        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int sx = hotbarX + i * SLOT;
            boolean over = inside(mx, my, sx, hotbarY);
            ui.drawNineSlice(over ? gui.glassSlotHover : gui.glassSlot,
                sx, hotbarY, SLOT, SLOT, 3, GuiAssets.SLOT_SIZE, 0xFFFFFFFF);
            ItemStack stack = inv.getHotbarItem(i);
            if (!stack.isEmpty()) {
                StackIcons.drawStack(ui, font, atlas, stack, sx + 1, hotbarY + 1);
                if (over) hovered = stack;
            }
            if (i == inv.getSelectedSlot()) {
                ui.useSolidColor();
                int acc = 0xFF000000 | MenuTheme.ACCENT;
                ui.fillRect(sx - 1, hotbarY - 1, SLOT + 2, 1, acc);
                ui.fillRect(sx - 1, hotbarY + SLOT, SLOT + 2, 1, acc);
                ui.fillRect(sx - 1, hotbarY, 1, SLOT, acc);
                ui.fillRect(sx + SLOT, hotbarY, 1, SLOT, acc);
            }
        }

        return hovered;
    }

    /** Compact clickable entry in the offer list. */
    private void drawEntry(UIRenderer ui, FontRenderer font, GuiAssets gui, int row, int ry) {
        TradeOffer o = offers[row];
        int stock = callbacks.stockOf(row);
        boolean soldOut = stock <= 0;

        boolean hover = hoverX >= listX && hoverX < listX + listW
            && hoverY >= ry && hoverY < ry + ENTRY_H;

        if (selected == row) {
            ui.useSolidColor();
            ui.fillRect(listX, ry, listW, ENTRY_H - 2, 0x2A000000 | MenuTheme.ACCENT);
            ui.fillRect(listX, ry + 1, 2, ENTRY_H - 4, 0xFF000000 | MenuTheme.ACCENT);
        } else if (hover) {
            ui.useSolidColor();
            ui.fillRect(listX, ry, listW, ENTRY_H - 2, 0x18FFFFFF);
        }
        if (soldOut) {
            ui.useSolidColor();
            ui.fillRect(listX, ry, listW, ENTRY_H - 2, 0x30000000);
        }

        int x0 = listX + 2;
        int yc = ry + (ENTRY_H - 12) / 2;

        StackIcons.drawIcon(ui, atlas, o.costA, x0, yc, 12);
        int x = x0 + 14;
        if (o.costB != null && !o.costB.isEmpty()) {
            StackIcons.drawIcon(ui, atlas, o.costB, x, yc, 12);
            x += 14;
        }
        font.draw(ui, "→", x, ry + 9, 0xFF8EA2C2);
        x += 8;

        boolean overGive = hoverX >= x && hoverX < x + SLOT && hoverY >= ry + 4 && hoverY < ry + 4 + SLOT;
        ui.drawNineSlice(overGive ? gui.glassSlotHover : gui.glassSlot,
            x, ry + 4, SLOT, SLOT, 3, GuiAssets.SLOT_SIZE, 0xFFFFFFFF);
        StackIcons.drawStack(ui, font, atlas, o.give, x + 1, ry + 5);

        String stockTxt = soldOut ? "×0" : "×" + stock;
        int sc = soldOut ? 0xFFD05050 : 0xFF8EA2C2;
        font.draw(ui, stockTxt, listX + listW - 12 - font.width(stockTxt), ry + 10, sc);
    }

    /** The big read-only display of the currently selected offer. */
    private void drawSelectedTrade(UIRenderer ui, FontRenderer font, GuiAssets gui, int row) {
        TradeOffer o = offers[row];
        int rep = callbacks.reputation();
        int stock = callbacks.stockOf(row);
        int pay = emeraldCost(o, rep);
        boolean payEmeralds = isEmeraldPay(o);
        boolean affordable = canAfford(o, pay);
        boolean soldOut = stock <= 0;

        int y0 = listY;
        int px = selX;

        // Payment slots, one next to the other, then the arrow and the result
        drawGlassSlotAt(ui, gui, px, y0);
        StackIcons.drawIcon(ui, atlas, o.costA, px + 1, y0 + 1, SLOT - 2);
        drawCount(ui, font, pay, px, y0, payEmeralds, !affordable);
        px += SLOT + 2;

        if (o.costB != null && !o.costB.isEmpty()) {
            drawGlassSlotAt(ui, gui, px, y0);
            StackIcons.drawIcon(ui, atlas, o.costB, px + 1, y0 + 1, SLOT - 2);
            boolean shortB = callbacks.inventory().countByName(
                Inventory.canonicalName(o.costB)) < o.costB.getCount();
            drawCount(ui, font, o.costB.getCount(), px, y0, false, shortB);
            px += SLOT + 2;
        }

        font.draw(ui, "→", px + 2, y0 + 6, 0xFF8EA2C2);
        px += 12;

        drawGlassSlotAt(ui, gui, px, y0);
        StackIcons.drawStack(ui, font, atlas, o.give, px + 1, y0 + 1);

        if (soldOut) {
            ui.useSolidColor();
            ui.fillRect(selX, y0, selW, SLOT, 0x36000000);
        }

        // Give name (clipped so it never hits the stock badge) and stock
        String badge = soldOut ? tr("trade.sold_out") : tr("trade.stock") + ": ×" + stock;
        int nameMax = Math.max(20, selW - font.width(badge) - 4);
        String name = font.trimToWidth(StackIcons.displayName(o.give), nameMax);
        font.draw(ui, name, selX, y0 + 26, soldOut ? 0xFF4A5055 : 0xFFE8EEFF);
        font.draw(ui, badge, selX + selW - font.width(badge), y0 + 26,
            soldOut ? 0xFFD05050 : 0xFF8EA2C2);
    }

    private void drawGlassSlotAt(UIRenderer ui, GuiAssets gui, int x, int y) {
        boolean over = hoverX >= x && hoverX < x + SLOT && hoverY >= y && hoverY < y + SLOT;
        ui.drawNineSlice(over ? gui.glassSlotHover : gui.glassSlot,
            x, y, SLOT, SLOT, 3, GuiAssets.SLOT_SIZE, 0xFFFFFFFF);
    }

    private void drawCount(UIRenderer ui, FontRenderer font, int n, int x, int y,
                           boolean gold, boolean shortfall) {
        String text = "×" + n;
        int color = shortfall ? 0xFFD05050 : (gold ? 0xFFE8B23A : 0xFFB8C2DC);
        font.drawWithShadow(ui, text, x + SLOT - font.width(text), y + SLOT - 1, color);
    }

    private void drawScrollbar(UIRenderer ui, GuiAssets gui) {
        if (maxScrollRow() <= 0) return;
        ui.drawNineSlice(gui.glassTrack, scrollbarX, listY, SCROLLBAR_W, listH,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF151A2A);

        int thumbH = Math.max(12, listH * LIST_ROWS / Math.max(1, offers.length));
        int travel = listH - thumbH;
        int thumbY = listY + travel * scrollRow / maxScrollRow();

        boolean over = draggingThumb
            || (hoverX >= scrollbarX && hoverX < scrollbarX + SCROLLBAR_W
                && hoverY >= thumbY && hoverY < thumbY + thumbH);

        ui.drawNineSlice(gui.glassPanel, scrollbarX, thumbY, SCROLLBAR_W, thumbH,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET,
            over ? (0xFF000000 | MenuTheme.ACCENT) : 0xFF8EA2C2);
    }

    private void drawTradeTooltip(UIRenderer ui, FontRenderer font, int row,
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

        // Offer list: select a trade; double-left-click trades right away
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

        // Scrollbar: grab the thumb
        if (mx >= scrollbarX && mx < scrollbarX + SCROLLBAR_W
            && my >= listY && my < listY + listH) {
            if (maxScrollRow() > 0) {
                draggingThumb = true;
                scrollThumbTo(my);
            }
            return true;
        }

        // The themed Button widget owns the trade button
        super.mouseClicked(mx, my, button);
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
        int thumbH = Math.max(12, listH * LIST_ROWS / Math.max(1, offers.length));
        int travel = Math.max(1, listH - thumbH);
        int ratio = (int) Math.max(0, Math.min(travel, my - listY - thumbH / 2.0f));
        scrollRow = maxScrollRow() * ratio / travel;
    }

    private int rowAt(float mx, float my) {
        if (mx < listX || mx >= listX + listW || my < listY || my >= listY + listH) return -1;
        int row = scrollRow + (int) ((my - listY) / ENTRY_H);
        return row >= 0 && row < offers.length ? row : -1;
    }

    private boolean inSelectedTrade(float mx, float my) {
        return mx >= selX && mx < selX + selW && my >= listY && my < listY + 58;
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

    private boolean canTradeNow() {
        return selected >= 0 && selected < offers.length
            && callbacks.stockOf(selected) > 0
            && canAfford(offers[selected], emeraldCost(offers[selected], callbacks.reputation()));
    }

    private String stackLabel(ItemStack s) {
        String name = StackIcons.displayName(s);
        if (s.getCount() > 1) name += " ×" + s.getCount();
        return name;
    }

    private boolean inside(float mx, float my, int x, int y) {
        return mx >= x && mx < x + SLOT && my >= y && my < y + SLOT;
    }

    private float hoverX, hoverY;

    @Override
    public void onClosed() {
        callbacks.onClose();
    }
}