package com.voxelgame.ui.screen;

import com.voxelgame.core.Game;
import com.voxelgame.item.Inventory;
import com.voxelgame.item.ItemStack;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.StackIcons;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;
import com.voxelgame.world.BlockType;
import com.voxelgame.world.container.ContainerData;

import static com.voxelgame.core.Language.tr;

/**
 * Chest container UI: chest slots on top, player inventory below.
 *
 * Clicks run through {@link SlotEngine}: whole-stack take/place/merge/swap on
 * left click, half/one on right click, shift-click quick-moves between the
 * chest and the inventory, 1-9 swaps with the hotbar, Q drops, and clicking
 * outside the panel drops the cursor stack into the world.
 */
public class ChestScreen extends Screen {

    public interface Callbacks {
        Inventory inventory();
        ItemStack mouseItem();
        void onMouseItemChanged(ItemStack stack);
        ContainerData container();
        /** Drop a stack into the world at the player's feet. */
        void dropStack(ItemStack stack);
    }

    private static final int SLOT = GuiAssets.SLOT_SIZE;
    private static final int COLS = 9;
    private static final int CHEST_ROWS_SINGLE = 3;
    private static final int CHEST_ROWS_DOUBLE = 6;

    private static final int PANEL_PAD = 7;
    private static final int TITLE_H = 12;
    private static final int SECTION_GAP = 4;

    private final Callbacks callbacks;
    private final TextureAtlas atlas;

    private int panelX, panelY, panelW, panelH;
    private int chestX, chestY;
    private int storageX, storageY;
    private int hotbarX, hotbarY;

    private float lastMx, lastMy;

    public ChestScreen(Callbacks callbacks, TextureAtlas atlas) {
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
        int chestRows = chestRows();
        int chestH = chestRows * SLOT;
        int storageRows = Inventory.MAIN_INVENTORY_SIZE / COLS;
        int storageH = storageRows * SLOT;

        panelW = PANEL_PAD * 2 + contentW;
        panelH = PANEL_PAD * 2 + TITLE_H + SECTION_GAP + chestH
                + SECTION_GAP + storageH + SLOT;

        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        chestX = panelX + PANEL_PAD;
        chestY = panelY + PANEL_PAD + TITLE_H + SECTION_GAP;

        storageX = chestX;
        storageY = chestY + chestH + SECTION_GAP;

        hotbarX = storageX;
        hotbarY = storageY + storageH + SECTION_GAP;

        add(new Button((width - 200) / 2, height - 28, 200, 20,
            tr("gui.done"), b -> fireClosed()));
    }

    private int chestRows() {
        return callbacks.container().size() > 27 ? CHEST_ROWS_DOUBLE : CHEST_ROWS_SINGLE;
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

    // ------------------------------------------------------------------
    // Slot views
    // ------------------------------------------------------------------

    private SlotEngine.Slot chestSlot(int i) {
        ContainerData container = callbacks.container();
        return new SlotEngine.Slot() {
            @Override public ItemStack get() { return container.getSlot(i); }
            @Override public void set(ItemStack s) { container.setSlot(i, s); }
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

    /** Main inventory then hotbar, the vanilla chest-to-player order. */
    private SlotEngine.Slot[] playerSlots() {
        SlotEngine.Slot[] slots = new SlotEngine.Slot[
            Inventory.MAIN_INVENTORY_SIZE + Inventory.HOTBAR_SIZE];
        for (int i = 0; i < Inventory.MAIN_INVENTORY_SIZE; i++) slots[i] = storageSlot(i);
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            slots[Inventory.MAIN_INVENTORY_SIZE + i] = hotbarSlot(i);
        }
        return slots;
    }

    private SlotEngine.Slot[] chestSlots() {
        SlotEngine.Slot[] slots = new SlotEngine.Slot[callbacks.container().size()];
        for (int i = 0; i < slots.length; i++) slots[i] = chestSlot(i);
        return slots;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets gui) {
        MenuTheme.drawWorldOverlay(ui, width, height);
        ui.drawNineSlice(gui.glassPanel, panelX, panelY, panelW, panelH,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF332314);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets gui,
                                    float mx, float my) {
        lastMx = mx;
        lastMy = my;

        font.draw(ui, tr("container.chest"), chestX, panelY + PANEL_PAD, 0xFFF2E6C8);

        ContainerData container = callbacks.container();
        Inventory inv = callbacks.inventory();
        ItemStack mouse = callbacks.mouseItem();
        ItemStack hovered = null;

        // Chest slots
        int chestRows = chestRows();
        for (int i = 0; i < container.size(); i++) {
            int sx = chestX + (i % COLS) * SLOT;
            int sy = chestY + (i / COLS) * SLOT;

            drawGlassSlot(ui, gui, sx, sy, mx, my);
            ItemStack stack = container.getSlot(i);
            if (!stack.isEmpty()) {
                StackIcons.drawStack(ui, font, atlas, stack, sx + 1, sy + 1);
                if (inside(mx, my, sx, sy)) hovered = stack;
            }
        }

        // Player inventory
        for (int i = 0; i < Inventory.MAIN_INVENTORY_SIZE; i++) {
            int sx = storageX + (i % COLS) * SLOT;
            int sy = storageY + (i / COLS) * SLOT;

            drawGlassSlot(ui, gui, sx, sy, mx, my);
            ItemStack stack = inv.getInventoryItem(i);
            if (!stack.isEmpty()) {
                StackIcons.drawStack(ui, font, atlas, stack, sx + 1, sy + 1);
                if (inside(mx, my, sx, sy)) hovered = stack;
            }
        }

        // Hotbar strip
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int sx = hotbarX + i * SLOT;
            drawGlassSlot(ui, gui, sx, hotbarY, mx, my);
            ItemStack stack = inv.getHotbarItem(i);
            if (!stack.isEmpty()) {
                StackIcons.drawStack(ui, font, atlas, stack, sx + 1, hotbarY + 1);
                if (inside(mx, my, sx, hotbarY)) hovered = stack;
            }
        }

        // Cursor stack follows the mouse
        if (!mouse.isEmpty()) {
            StackIcons.drawStack(ui, font, atlas, mouse,
                (int) mx - GuiAssets.ICON_SIZE / 2, (int) my - GuiAssets.ICON_SIZE / 2);
        }

        if (hovered != null && mouse.isEmpty()) {
            StackIcons.drawTooltip(ui, font, atlas, hovered, mx, my, width, height);
        }
    }

    private void drawGlassSlot(UIRenderer ui, GuiAssets gui, int sx, int sy, float mx, float my) {
        boolean hover = inside(mx, my, sx, sy);
        ui.drawNineSlice(gui.glassSlot,
            sx, sy, SLOT, SLOT, 3, GuiAssets.SLOT_SIZE, hover ? 0xFF9A7D4C : 0xFF3A2A1A);
    }

    // ------------------------------------------------------------------
    // Mouse
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        ItemStack mouse = callbacks.mouseItem();
        boolean shift = Game.isShiftDown();

        // Chest slots
        if (inside(mx, my, chestX, chestY, COLS * SLOT, chestRows() * SLOT)) {
            int slot = chestSlotAt(mx, my);
            if (slot >= 0) {
                SlotEngine.Slot s = chestSlot(slot);
                if (shift) {
                    SlotEngine.quickMove(s, playerSlots());
                } else {
                    callbacks.onMouseItemChanged(SlotEngine.click(s, mouse, button == 0));
                }
            }
            return true;
        }

        // Hotbar strip
        if (inside(mx, my, hotbarX, hotbarY, Inventory.HOTBAR_SIZE * SLOT, SLOT)) {
            int slot = (int) ((mx - hotbarX) / SLOT);
            if (slot >= 0 && slot < Inventory.HOTBAR_SIZE) {
                SlotEngine.Slot s = hotbarSlot(slot);
                if (shift) {
                    SlotEngine.quickMove(s, chestSlots());
                } else {
                    callbacks.onMouseItemChanged(SlotEngine.click(s, mouse, button == 0));
                }
            }
            return true;
        }

        // Storage area
        if (inside(mx, my, storageX, storageY,
                COLS * SLOT, Inventory.MAIN_INVENTORY_SIZE / COLS * SLOT)) {
            int slot = storageSlotAt(mx, my);
            if (slot >= 0) {
                SlotEngine.Slot s = storageSlot(slot);
                if (shift) {
                    SlotEngine.quickMove(s, chestSlots());
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

    private int chestSlotAt(float mx, float my) {
        int col = (int) ((mx - chestX) / SLOT);
        int row = (int) ((my - chestY) / SLOT);
        int slot = row * COLS + col;
        return slot >= 0 && slot < callbacks.container().size() ? slot : -1;
    }

    private int storageSlotAt(float mx, float my) {
        int col = (int) ((mx - storageX) / SLOT);
        int row = (int) ((my - storageY) / SLOT);
        int slot = row * COLS + col;
        return slot >= 0 && slot < Inventory.MAIN_INVENTORY_SIZE ? slot : -1;
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
            } else {
                return false;
            }
            return true;
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
        int chestSlot = chestSlotAt(mx, my);
        if (chestSlot >= 0) return chestSlot(chestSlot);
        int storageSlot = storageSlotAt(mx, my);
        if (storageSlot >= 0) return storageSlot(storageSlot);
        if (inside(mx, my, hotbarX, hotbarY, Inventory.HOTBAR_SIZE * SLOT, SLOT)) {
            int slot = (int) ((mx - hotbarX) / SLOT);
            if (slot >= 0 && slot < Inventory.HOTBAR_SIZE) return hotbarSlot(slot);
        }
        return null;
    }

    private boolean inside(float mx, float my, int x, int y) {
        return mx >= x && mx < x + SLOT && my >= y && my < y + SLOT;
    }

    private boolean inside(float mx, float my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
