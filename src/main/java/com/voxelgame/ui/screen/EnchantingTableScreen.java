package com.voxelgame.ui.screen;

import com.voxelgame.core.Game;
import com.voxelgame.item.Enchantment;
import com.voxelgame.item.Inventory;
import com.voxelgame.item.Item;
import com.voxelgame.item.ItemRegistry;
import com.voxelgame.item.ItemStack;
import com.voxelgame.item.ToolType;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.StackIcons;
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
 * The player inventory below uses the shared {@link SlotEngine} click
 * semantics: whole-stack take/place/merge/swap, right-click half/one,
 * shift-click quick move, 1-9 hotbar swap, Q drop.
 */
public class EnchantingTableScreen extends Screen {

    public interface Callbacks {
        Inventory inventory();
        ItemStack mouseItem();
        void onMouseItemChanged(ItemStack stack);
        int xpLevel();
        int xpProgress();
        boolean spendXp(int levels);
        /** Drop a stack into the world at the player's feet. */
        void dropStack(ItemStack stack);
    }

    private static final int SLOT = GuiAssets.SLOT_SIZE;
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

    private float lastMx, lastMy;

    public EnchantingTableScreen(Callbacks callbacks, TextureAtlas atlas) {
        this.callbacks = callbacks;
        this.atlas = atlas;
    }

    @Override
    public boolean rendersWorld() { return false; }

    @Override
    public boolean usesBlurredBackdrop() { return true; }

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
        if (onClosedCallback != null) {
            Runnable cb = onClosedCallback;
            onClosedCallback = null;
            cb.run();
        }
    }

    private Runnable onClosedCallback;
    public void onClose(Runnable cb) { this.onClosedCallback = cb; }

    /** Also fires when the screen is popped with Escape. */
    @Override
    public void onClosed() { fireClosed(); }

    // The tool and lapis "slots" live at the tail of the player inventory.

    private static final int TOOL_INV_SLOT = Inventory.MAIN_INVENTORY_SIZE - 1;
    private static final int LAPIS_INV_SLOT = Inventory.MAIN_INVENTORY_SIZE - 2;

    private ItemStack toolInSlot() {
        return callbacks.inventory().getInventoryItem(TOOL_INV_SLOT);
    }

    private void setToolInSlot(ItemStack stack) {
        callbacks.inventory().setInventoryItem(TOOL_INV_SLOT, stack);
        dirty = true;
    }

    private ItemStack lapisInSlot() {
        return callbacks.inventory().getInventoryItem(LAPIS_INV_SLOT);
    }

    private void setLapisInSlot(ItemStack stack) {
        callbacks.inventory().setInventoryItem(LAPIS_INV_SLOT, stack);
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

    // ------------------------------------------------------------------
    // Slot views
    // ------------------------------------------------------------------

    private SlotEngine.Slot toolSlot() {
        return new SlotEngine.Slot() {
            @Override public ItemStack get() { return toolInSlot(); }
            @Override public void set(ItemStack s) { setToolInSlot(s); }
        };
    }

    private SlotEngine.Slot lapisSlot() {
        return new SlotEngine.Slot() {
            @Override public ItemStack get() { return lapisInSlot(); }
            @Override public void set(ItemStack s) { setLapisInSlot(s); }

            @Override public boolean mayPlace(ItemStack stack) {
                return stack.isItem() && stack.getItem() == ItemRegistry.LAPIS_LAZULI;
            }
        };
    }

    private SlotEngine.Slot storageSlot(int i) {
        Inventory inv = callbacks.inventory();
        return new SlotEngine.Slot() {
            @Override public ItemStack get() { return inv.getInventoryItem(i); }
            @Override public void set(ItemStack s) { inv.setInventoryItem(i, s); }
        };
    }

    private SlotEngine.Slot hotbarSlot(int i) {
        Inventory inv = callbacks.inventory();
        return new SlotEngine.Slot() {
            @Override public ItemStack get() { return inv.getHotbarItem(i); }
            @Override public void set(ItemStack s) { inv.setHotbarItem(i, s); }
        };
    }

    private SlotEngine.Slot[] playerSlots() {
        SlotEngine.Slot[] slots = new SlotEngine.Slot[
            Inventory.MAIN_INVENTORY_SIZE + Inventory.HOTBAR_SIZE];
        for (int i = 0; i < Inventory.MAIN_INVENTORY_SIZE; i++) slots[i] = storageSlot(i);
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            slots[Inventory.MAIN_INVENTORY_SIZE + i] = hotbarSlot(i);
        }
        return slots;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets gui) {
        MenuTheme.drawWorldOverlay(ui, width, height);
        ui.drawNineSlice(gui.glassPanel, panelX, panelY, panelW, panelH,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF10141E);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets gui,
                                    float mx, float my) {
        lastMx = mx;
        lastMy = my;

        font.draw(ui, tr("container.enchanting"), panelX + PANEL_PAD,
            panelY + PANEL_PAD, 0xFFE8EEFF);

        ItemStack tool = toolInSlot();
        ItemStack lapis = lapisInSlot();
        ItemStack mouse = callbacks.mouseItem();
        ItemStack hovered = null;

        // Tool slot
        drawGlassSlot(ui, gui, toolSlotX, toolSlotY, mx, my);
        if (!tool.isEmpty()) {
            StackIcons.drawStack(ui, font, atlas, tool, toolSlotX + 1, toolSlotY + 1);
            if (inside(mx, my, toolSlotX, toolSlotY, SLOT, SLOT)) hovered = tool;
        } else {
            font.drawWithShadow(ui, "?", toolSlotX + SLOT / 2 - 3, toolSlotY + SLOT / 2 - 4,
                0xFFB8A0D6);
        }

        // Lapis slot
        drawGlassSlot(ui, gui, lapisSlotX, lapisSlotY, mx, my);
        if (!lapis.isEmpty()) {
            StackIcons.drawStack(ui, font, atlas, lapis, lapisSlotX + 1, lapisSlotY + 1);
            if (inside(mx, my, lapisSlotX, lapisSlotY, SLOT, SLOT)) hovered = lapis;
        }

        // Level indicator
        font.drawWithShadow(ui, tr("enchant.level") + ": " + callbacks.xpLevel(),
            panelX + PANEL_PAD, areaY + AREA_H + 2, 0xFFC78BE8);

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
            boolean hover = inside(mx, my, ox, oy, OPTION_W, OPTION_H);
            int tint = enabled ? 0xFF5A3190 : (hover ? 0xFF3A2A55 : 0xFF271C3E);
            ui.drawNineSlice(gui.glassPanel, ox, oy, OPTION_W, OPTION_H,
                GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, tint);
            if (enabled) {
                ui.useSolidColor();
                ui.fillRect(ox + 4, oy + OPTION_H - 2, OPTION_W - 8, 1, 0xFF9D5CB0);
            }

            String name;
            if (offers[i] != null) {
                name = offers[i].displayName + (offerLevels[i] > 1 ? " " + roman(offerLevels[i]) : "");
            } else {
                name = "???";
            }
            font.drawWithShadow(ui, name, ox + 3, oy + 1, 0xFFE2C0FF);
            font.drawWithShadow(ui, cost + " " + tr("enchant.levelShort"), ox + 3,
                oy + OPTION_H - 9, enabled ? 0xFFC78BE8 : 0xFF6A5580);
        }

        // --- Player inventory (tool/lapis cells are shown up top) ---
        for (int i = 0; i < Inventory.MAIN_INVENTORY_SIZE; i++) {
            if (i == TOOL_INV_SLOT || i == LAPIS_INV_SLOT) continue;
            int row = i / COLS;
            int col = i % COLS;
            int sx = storageX + col * SLOT;
            int sy = storageY + row * SLOT;
            drawGlassSlot(ui, gui, sx, sy, mx, my);
            ItemStack stack = callbacks.inventory().getInventoryItem(i);
            if (!stack.isEmpty()) {
                StackIcons.drawStack(ui, font, atlas, stack, sx + 1, sy + 1);
                if (inside(mx, my, sx, sy, SLOT, SLOT)) hovered = stack;
            }
        }

        // Hotbar strip
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int sx = hotbarX + i * SLOT;
            drawGlassSlot(ui, gui, sx, hotbarY, mx, my);
            ItemStack stack = callbacks.inventory().getHotbarItem(i);
            if (!stack.isEmpty()) {
                StackIcons.drawStack(ui, font, atlas, stack, sx + 1, hotbarY + 1);
                if (inside(mx, my, sx, hotbarY, SLOT, SLOT)) hovered = stack;
            }
        }

        // Mouse item follows the cursor
        if (!mouse.isEmpty()) {
            StackIcons.drawStack(ui, font, atlas, mouse,
                (int) mx - GuiAssets.ICON_SIZE / 2, (int) my - GuiAssets.ICON_SIZE / 2);
        }

        if (hovered != null && mouse.isEmpty()) {
            StackIcons.drawTooltip(ui, font, atlas, hovered, mx, my, width, height);
        }
    }

    private void drawGlassSlot(UIRenderer ui, GuiAssets gui, int sx, int sy, float mx, float my) {
        boolean hover = inside(mx, my, sx, sy, SLOT, SLOT);
        ui.drawNineSlice(hover ? gui.glassSlotHover : gui.glassSlot,
            sx, sy, SLOT, SLOT, 3, GuiAssets.SLOT_SIZE, 0xFFFFFFFF);
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

    // ------------------------------------------------------------------
    // Mouse
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button != 0 && button != 1) return false;
        ItemStack mouse = callbacks.mouseItem();
        boolean shift = Game.isShiftDown();

        // Tool slot: swap anything
        if (inside(mx, my, toolSlotX, toolSlotY, SLOT, SLOT)) {
            SlotEngine.Slot slot = toolSlot();
            if (shift) {
                SlotEngine.quickMove(slot, playerSlots());
            } else {
                callbacks.onMouseItemChanged(SlotEngine.click(slot, mouse, button == 0));
            }
            return true;
        }

        // Lapis slot: only lapis may enter
        if (inside(mx, my, lapisSlotX, lapisSlotY, SLOT, SLOT)) {
            SlotEngine.Slot slot = lapisSlot();
            if (shift) {
                SlotEngine.quickMove(slot, playerSlots());
            } else {
                callbacks.onMouseItemChanged(SlotEngine.click(slot, mouse, button == 0));
            }
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

        // Hotbar strip
        if (inside(mx, my, hotbarX, hotbarY, Inventory.HOTBAR_SIZE * SLOT, SLOT)) {
            int slot = (int) ((mx - hotbarX) / SLOT);
            if (slot >= 0 && slot < Inventory.HOTBAR_SIZE) {
                SlotEngine.Slot s = hotbarSlot(slot);
                if (shift) {
                    SlotEngine.Slot[] storage = new SlotEngine.Slot[Inventory.MAIN_INVENTORY_SIZE];
                    for (int i = 0; i < storage.length; i++) storage[i] = storageSlot(i);
                    SlotEngine.quickMove(s, storage);
                } else {
                    callbacks.onMouseItemChanged(SlotEngine.click(s, mouse, button == 0));
                }
            }
            return true;
        }

        // Storage area (tool/lapis cells live up top, skip them here)
        if (inside(mx, my, storageX, storageY,
                COLS * SLOT, Inventory.MAIN_INVENTORY_SIZE / COLS * SLOT)) {
            int col = (int) ((mx - storageX) / SLOT);
            int row = (int) ((my - storageY) / SLOT);
            int slot = row * COLS + col;
            if (slot >= 0 && slot < Inventory.MAIN_INVENTORY_SIZE
                    && slot != TOOL_INV_SLOT && slot != LAPIS_INV_SLOT) {
                SlotEngine.Slot s = storageSlot(slot);
                if (shift) {
                    SlotEngine.Slot[] hotbar = new SlotEngine.Slot[Inventory.HOTBAR_SIZE];
                    for (int i = 0; i < hotbar.length; i++) hotbar[i] = hotbarSlot(i);
                    SlotEngine.quickMove(s, hotbar);
                } else {
                    callbacks.onMouseItemChanged(SlotEngine.click(s, mouse, button == 0));
                }
            }
            return true;
        }

        // Click outside the panel drops the cursor stack into the world
        if (!mouse.isEmpty() && !insidePanel(mx, my) && !overDoneButton(mx, my)) {
            callbacks.dropStack(mouse.copy());
            callbacks.onMouseItemChanged(SlotEngine.empty());
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    private boolean overDoneButton(float mx, float my) {
        return my >= height - 32 && my < height - 8
            && mx >= (width - 200) / 2 && mx < (width - 200) / 2 + 200;
    }

    private boolean insidePanel(float mx, float my) {
        return mx >= panelX && mx < panelX + panelW && my >= panelY && my < panelY + panelH;
    }

    // ------------------------------------------------------------------
    // Keyboard: Q drop, 1-9 hotbar swap
    // ------------------------------------------------------------------

    @Override
    public boolean keyPressed(int key, int mods) {
        if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
            int index = key - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
            SlotEngine.Slot hovered = hoveredSlot();
            if (hovered != null) {
                SlotEngine.hotbarSwap(hovered, hotbarSlot(index));
                return true;
            }
            return false;
        }

        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_Q) {
            SlotEngine.Slot hovered = hoveredSlot();
            if (hovered != null) {
                boolean entire = (mods & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0;
                ItemStack dropped = SlotEngine.pullForDrop(hovered, entire);
                if (!dropped.isEmpty()) callbacks.dropStack(dropped);
                return true;
            }
        }
        return false;
    }

    private SlotEngine.Slot hoveredSlot() {
        float mx = lastMx, my = lastMy;
        if (inside(mx, my, toolSlotX, toolSlotY, SLOT, SLOT)) return toolSlot();
        if (inside(mx, my, lapisSlotX, lapisSlotY, SLOT, SLOT)) return lapisSlot();
        if (inside(mx, my, storageX, storageY,
                COLS * SLOT, Inventory.MAIN_INVENTORY_SIZE / COLS * SLOT)) {
            int col = (int) ((mx - storageX) / SLOT);
            int row = (int) ((my - storageY) / SLOT);
            int slot = row * COLS + col;
            if (slot >= 0 && slot < Inventory.MAIN_INVENTORY_SIZE
                    && slot != TOOL_INV_SLOT && slot != LAPIS_INV_SLOT) {
                return storageSlot(slot);
            }
        }
        if (inside(mx, my, hotbarX, hotbarY, Inventory.HOTBAR_SIZE * SLOT, SLOT)) {
            int slot = (int) ((mx - hotbarX) / SLOT);
            if (slot >= 0 && slot < Inventory.HOTBAR_SIZE) return hotbarSlot(slot);
        }
        return null;
    }

    private static boolean inside(float mx, float my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
