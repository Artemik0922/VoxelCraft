package com.voxelgame.ui.screen;

import com.voxelgame.item.Enchantment;
import com.voxelgame.item.Inventory;
import com.voxelgame.item.Item;
import com.voxelgame.item.ItemRegistry;
import com.voxelgame.item.ItemStack;
import com.voxelgame.item.ToolType;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;
import com.voxelgame.world.BlockType;

import static com.voxelgame.core.Language.tr;

/**
 * [ENCH] Enchanting table UI.
 *
 * Left slot holds an enchantable tool, the right slot holds lapis
 * lazuli. The three glowing buttons in the middle each offer one
 * enchantment for a price of 1, 2 or 3 levels.
 *
 * Layout (MC-style):
 *     ┌──────────────────────────────────┐
 *     │      Стол зачарования             │
 *     │  [Tool]  [Кнопка 1 (1 ур.)] [Лап.]│
 *     │           [Кнопка 2 (2 ур.)]      │
 *     │           [Кнопка 3 (3 ур.)]      │
 *     │  ─── Player inventory ───         │
 *     │  [ 9 hotbar slots ]               │
 *     └──────────────────────────────────┘
 */
public class EnchantingTableScreen extends Screen {

    public interface Callbacks {
        Inventory inventory();
        ItemStack mouseItem();
        void onMouseItemChanged(ItemStack stack);
        int xpLevel();
        int xpProgress();
        boolean spendXp(int levels);
    }

    private static final int SLOT = GuiAssets.SLOT_SIZE;
    private static final int ICON = GuiAssets.ICON_SIZE;
    private static final int COLS = 9;

    private static final int PANEL_PAD = 7;
    private static final int TITLE_H = 12;
    private static final int SECTION_GAP = 4;

    private static final int OPTION_W = 92;
    private static final int OPTION_H = 20;
    private static final int OPTION_GAP = 4;

    private static final int AREA_W = SLOT + OPTION_W + SLOT + 24;
    private static final int AREA_H = OPTION_H * 3 + OPTION_GAP * 2;

    private final Callbacks callbacks;
    private final TextureAtlas atlas;

    private int panelX, panelY, panelW, panelH;
    private int areaX, areaY;
    private int toolSlotX, toolSlotY;
    private int lapisSlotX, lapisSlotY;
    private int optionX, optionY;
    private int storageX, storageY;
    private int hotbarX, hotbarY;

    // Rolled offers: enchantment + level per option (index 0..2)
    private final Enchantment[] offers = new Enchantment[3];
    private final int[] offerLevels = new int[3];
    private boolean dirty = true; // re-roll when the tool changes

    public EnchantingTableScreen(Callbacks callbacks, TextureAtlas atlas) {
        this.callbacks = callbacks;
        this.atlas = atlas;
    }

    @Override
    public boolean rendersWorld() { return false; }

    @Override
    protected void layout() {
        int contentW = COLS * SLOT;
        int areaW = Math.max(AREA_W, contentW);
        int storageRows = Inventory.MAIN_INVENTORY_SIZE / COLS;
        int storageH = storageRows * SLOT;

        panelW = PANEL_PAD * 2 + areaW;
        panelH = PANEL_PAD * 2 + TITLE_H + SECTION_GAP + AREA_H
                + SECTION_GAP + storageH + SECTION_GAP + SLOT + SECTION_GAP;

        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        areaX = panelX + (panelW - AREA_W) / 2;
        areaY = panelY + PANEL_PAD + TITLE_H + SECTION_GAP;

        toolSlotX = areaX;
        toolSlotY = areaY + AREA_H / 2 - SLOT / 2;
        lapisSlotX = areaX + AREA_W - SLOT;
        lapisSlotY = toolSlotY;
        optionX = areaX + SLOT + 12;
        optionY = areaY;

        storageX = panelX + (panelW - contentW) / 2;
        storageY = areaY + AREA_H + SECTION_GAP * 2;
        hotbarX = storageX;
        hotbarY = storageY + storageH + SECTION_GAP;

        add(new Button((width - 200) / 2, height - 28, 200, 20,
            tr("gui.done"), b -> fireClosed()));
    }

    private void fireClosed() {
        if (onClosedCallback != null) onClosedCallback.run();
    }

    private Runnable onClosedCallback;
    public void onClose(Runnable cb) { this.onClosedCallback = cb; }

    /** Enchantable tool currently sitting in the slot, or null. */
    private ItemStack toolInSlot() {
        return callbacks.inventory().getInventoryItem(
            Inventory.MAIN_INVENTORY_SIZE - 1);
    }

    private void setToolInSlot(ItemStack stack) {
        callbacks.inventory().setInventoryItem(
            Inventory.MAIN_INVENTORY_SIZE - 1, stack);
        dirty = true;
    }

    private ItemStack lapisInSlot() {
        return callbacks.inventory().getInventoryItem(
            Inventory.MAIN_INVENTORY_SIZE - 2);
    }

    private void setLapisInSlot(ItemStack stack) {
        callbacks.inventory().setInventoryItem(
            Inventory.MAIN_INVENTORY_SIZE - 2, stack);
    }

    /** Re-roll the three offers when the tool in the slot changes. */
    private void refreshOffers() {
        if (!dirty) return;
        dirty = false;
        for (int i = 0; i < 3; i++) {
            offers[i] = null;
            offerLevels[i] = 0;
        }
        ItemStack tool = toolInSlot();
        if (tool == null || tool.isEmpty()) return;
        Item item = tool.getItem();
        if (item == null || item.toolType == ToolType.NONE) return;
        java.util.Random rng = new java.util.Random();
        for (int i = 0; i < 3; i++) {
            Enchantment pick = null;
            for (int tries = 0; tries < 8; tries++) {
                Enchantment candidate = Enchantment.rollFor(item.toolType, rng);
                if (candidate != null && (pick == null || candidate != pick)) {
                    pick = candidate;
                    break;
                }
                if (candidate != null) pick = candidate;
            }
            if (pick != null) {
                offers[i] = pick;
                offerLevels[i] = 1 + rng.nextInt(Math.min(pick.maxLevel, i + 2));
            }
        }
    }

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets gui) {
        ui.fillRect(0, 0, width, height, 0xB0101010);
        ui.drawNineSlice(gui.panel, panelX, panelY, panelW, panelH,
            GuiAssets.BORDER, GuiAssets.WIDGET, 0xFFFFFFFF);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets gui,
                                    float mx, float my) {
        font.draw(ui, tr("container.enchanting"), panelX + PANEL_PAD,
            panelY + PANEL_PAD, GuiAssets.TEXT_TITLE);

        ItemStack tool = toolInSlot();
        ItemStack lapis = lapisInSlot();
        ItemStack mouse = callbacks.mouseItem();

        // Tool slot
        ui.drawSprite(gui.slot, toolSlotX, toolSlotY, SLOT, SLOT);
        if (!tool.isEmpty()) drawItem(ui, tool, toolSlotX + 1, toolSlotY + 1);
        if (tool.isEmpty()) {
            font.drawWithShadow(ui, "?", toolSlotX + SLOT / 2 - 3, toolSlotY + SLOT / 2 - 4,
                0xFF888888);
        }

        // Lapis slot
        ui.drawSprite(gui.slot, lapisSlotX, lapisSlotY, SLOT, SLOT);
        if (!lapis.isEmpty()) {
            drawItem(ui, lapis, lapisSlotX + 1, lapisSlotY + 1);
            drawCount(ui, font, lapis.getCount(), lapisSlotX, lapisSlotY);
        }

        // Level indicator
        font.drawWithShadow(ui, tr("enchant.level") + ": " + callbacks.xpLevel(),
            panelX + PANEL_PAD, areaY + AREA_H + 2, 0xFF9D5CB0);

        // Offers
        refreshOffers();
        boolean enchanted = tool.getItem() != null && tool.getItem().toolType != ToolType.NONE;
        int lapisCount = lapis.getItem() == ItemRegistry.LAPIS_LAZULI ? lapis.getCount() : 0;
        for (int i = 0; i < 3; i++) {
            int ox = optionX;
            int oy = optionY + i * (OPTION_H + OPTION_GAP);
            int cost = i + 1;
            boolean enabled = enchanted && offers[i] != null
                && lapisCount >= cost && callbacks.xpLevel() >= cost;
            int bg = enabled ? 0xFF6A3FA0 : 0xFF3A2A55;
            ui.fillRect(ox, oy, OPTION_W, OPTION_H, bg);
            ui.fillRect(ox, oy, OPTION_W, 1, 0xFF9D5CB0);
            ui.fillRect(ox, oy + OPTION_H - 1, OPTION_W, 1, 0xFF9D5CB0);

            String name;
            if (offers[i] != null) {
                name = offers[i].displayName + (offerLevels[i] > 1 ? " " + roman(offerLevels[i]) : "");
            } else {
                name = "???";
            }
            font.drawWithShadow(ui, name, ox + 3, oy + 1, 0xFFD6A0FF);
            font.drawWithShadow(ui, cost + " " + tr("enchant.levelShort"), ox + 3,
                oy + OPTION_H - 9, enabled ? 0xFF9D5CB0 : 0xFF6A5580);
        }

        // --- Player inventory ---
        for (int i = 0; i < Inventory.MAIN_INVENTORY_SIZE; i++) {
            if (i == Inventory.MAIN_INVENTORY_SIZE - 1
                    || i == Inventory.MAIN_INVENTORY_SIZE - 2) continue;
            int row = i / COLS;
            int col = i % COLS;
            int sx = storageX + col * SLOT;
            int sy = storageY + row * SLOT;
            ui.drawSprite(gui.slot, sx, sy, SLOT, SLOT);
            ItemStack stack = callbacks.inventory().getInventoryItem(i);
            if (!stack.isEmpty()) {
                drawItem(ui, stack, sx + 1, sy + 1);
                drawCount(ui, font, stack.getCount(), sx, sy);
            }
        }

        // Hotbar strip
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int sx = hotbarX + i * SLOT;
            ui.drawSprite(gui.slot, sx, hotbarY, SLOT, SLOT);
            ItemStack stack = callbacks.inventory().getHotbarItem(i);
            if (!stack.isEmpty()) {
                drawItem(ui, stack, sx + 1, hotbarY + 1);
                drawCount(ui, font, stack.getCount(), sx, hotbarY);
            }
        }

        // Mouse item follows cursor
        if (!mouse.isEmpty()) {
            drawItem(ui, mouse, (int) mx - ICON / 2, (int) my - ICON / 2);
            if (mouse.getCount() > 1) {
                drawCount(ui, font, mouse.getCount(),
                    (int) mx - ICON / 2, (int) my - ICON / 2);
            }
        }
    }

    private void drawItem(UIRenderer ui, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        if (stack.isBlock()) {
            drawIcon(ui, stack.getBlockType(), x, y);
            return;
        }
        Item item = stack.getItem();
        if (item != null) {
            if (item.blockType != null) {
                drawIcon(ui, item.blockType, x, y);
                return;
            }
            int slot = atlas.getLayerOf(item.spriteName);
            if (slot >= 0) {
                TextureAtlas.TextureCoords uv = new TextureAtlas.TextureCoords(slot);
                ui.drawTexture(atlas.getTexture().getId(), x, y, ICON, ICON,
                    uv.u1, uv.v2, uv.u2, uv.v1, 0xFFFFFFFF);
                return;
            }
        }
        ui.useSolidColor();
        ui.fillRect(x + 2, y + 2, ICON - 4, ICON - 4, 0xFFC0C0C0);
    }

    private void drawIcon(UIRenderer ui, BlockType block, int x, int y) {
        TextureAtlas.TextureCoords uv = atlas.getCoords(block.id, 2);
        ui.drawTexture(atlas.getTexture().getId(), x, y, ICON, ICON,
            uv.u1, uv.v2, uv.u2, uv.v1, 0xFFFFFFFF);
    }

    private void drawCount(UIRenderer ui, FontRenderer font, int count, int sx, int sy) {
        if (count < 2) return;
        String text = String.valueOf(count);
        int tw = font.width(text);
        font.drawWithShadow(ui, text,
            sx + SLOT - tw - 1,
            sy + SLOT - FontRenderer.GLYPH_H - 1,
            0xFFFFFFFF);
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private boolean slotSwap(ItemStack slot, java.util.function.Consumer<ItemStack> setter,
                             boolean mustBeEmpty) {
        ItemStack mouse = callbacks.mouseItem();
        if (mustBeEmpty && !mouse.isEmpty()) return false;
        if (slot.isEmpty() && mouse.isEmpty()) return false;
        setter.accept(mouse);
        callbacks.onMouseItemChanged(slot);
        return true;
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button != 0 && button != 1) return false;
        ItemStack mouse = callbacks.mouseItem();

        // Tool slot: swap anything
        if (inside(mx, my, toolSlotX, toolSlotY, SLOT, SLOT)) {
            if (mouse.isEmpty() && toolInSlot().isEmpty()) return true;
            ItemStack was = toolInSlot();
            setToolInSlot(mouse);
            callbacks.onMouseItemChanged(was);
            return true;
        }

        // Lapis slot: only lapis may enter
        if (inside(mx, my, lapisSlotX, lapisSlotY, SLOT, SLOT)) {
            ItemStack was = lapisInSlot();
            boolean lapisMouse = !mouse.isEmpty()
                && mouse.getItem() == ItemRegistry.LAPIS_LAZULI;
            if (was.isEmpty() && !lapisMouse) return true;
            setLapisInSlot(mouse);
            callbacks.onMouseItemChanged(was);
            return true;
        }

        // Offer buttons
        refreshOffers();
        ItemStack tool = toolInSlot();
        ItemStack lapis = lapisInSlot();
        if (tool.getItem() != null && tool.getItem().toolType != ToolType.NONE) {
            int lapisCount = lapis.getItem() == ItemRegistry.LAPIS_LAZULI ? lapis.getCount() : 0;
            for (int i = 0; i < 3; i++) {
                int ox = optionX;
                int oy = optionY + i * (OPTION_H + OPTION_GAP);
                int cost = i + 1;
                if (inside(mx, my, ox, oy, OPTION_W, OPTION_H)
                        && offers[i] != null
                        && lapisCount >= cost
                        && callbacks.xpLevel() >= cost
                        && callbacks.spendXp(cost)) {
                    tool.addEnchantment(offers[i], offerLevels[i]);
                    lapis.decrement(cost);
                    setToolInSlot(tool);
                    dirty = true;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static boolean inside(float mx, float my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}