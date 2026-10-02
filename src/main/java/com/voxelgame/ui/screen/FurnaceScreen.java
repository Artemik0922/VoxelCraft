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
 * Furnace UI: input slot (top-left), fuel slot (bottom-left),
 * fire icon and progress arrow in the middle, output slot (right),
 * and player inventory below.
 *
 * Vanilla slot rules enforced through {@link SlotEngine}: only smeltable
 * items enter the input, only fuel enters the fuel slot, and the output slot
 * can only be taken from. Shift-click from the inventory routes the stack to
 * the input or the fuel slot automatically.
 */
public class FurnaceScreen extends Screen {

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

    private static final int PANEL_PAD = 7;
    private static final int TITLE_H = 12;
    private static final int SECTION_GAP = 4;

    // Furnace layout
    private static final int FURNACE_W = SLOT * 5;
    private static final int FURNACE_H = SLOT * 2 + 8;

    private static final int INPUT_DX = 0;
    private static final int INPUT_DY = 0;
    private static final int FUEL_DX = 0;
    private static final int FUEL_DY = SLOT + 8;
    private static final int OUTPUT_DX = SLOT * 3 + 8;
    private static final int OUTPUT_DY = SLOT / 2;

    private final Callbacks callbacks;
    private final TextureAtlas atlas;

    private int panelX, panelY, panelW, panelH;
    private int furnaceX, furnaceY;
    private int storageX, storageY;
    private int hotbarX, hotbarY;

    private int inputSlotX, inputSlotY;
    private int fuelSlotX, fuelSlotY;
    private int outputSlotX, outputSlotY;

    private float lastMx, lastMy;

    public FurnaceScreen(Callbacks callbacks, TextureAtlas atlas) {
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
        int furnaceAreaW = Math.max(FURNACE_W, contentW);
        int storageRows = Inventory.MAIN_INVENTORY_SIZE / COLS;
        int storageH = storageRows * SLOT;

        panelW = PANEL_PAD * 2 + Math.max(contentW, furnaceAreaW);
        panelH = PANEL_PAD * 2 + TITLE_H + SECTION_GAP + FURNACE_H
                + SECTION_GAP + storageH + SECTION_GAP + SLOT;

        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        furnaceX = panelX + (panelW - FURNACE_W) / 2;
        furnaceY = panelY + PANEL_PAD + TITLE_H + SECTION_GAP;

        inputSlotX = furnaceX + INPUT_DX;
        inputSlotY = furnaceY + INPUT_DY;
        fuelSlotX = furnaceX + FUEL_DX;
        fuelSlotY = furnaceY + FUEL_DY;
        outputSlotX = furnaceX + OUTPUT_DX;
        outputSlotY = furnaceY + OUTPUT_DY;

        storageX = panelX + (panelW - contentW) / 2;
        storageY = furnaceY + FURNACE_H + SECTION_GAP * 2;

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

    // ------------------------------------------------------------------
    // Slot views
    // ------------------------------------------------------------------

    private SlotEngine.Slot inputSlot() {
        ContainerData container = callbacks.container();
        return new SlotEngine.Slot() {
            @Override public ItemStack get() { return container.getSlot(ContainerData.FURNACE_INPUT); }
            @Override public void set(ItemStack s) { container.setSlot(ContainerData.FURNACE_INPUT, s); }

            @Override public boolean mayPlace(ItemStack stack) {
                return ContainerData.smeltResultFor(stack) != null;
            }
        };
    }

    private SlotEngine.Slot fuelSlot() {
        ContainerData container = callbacks.container();
        return new SlotEngine.Slot() {
            @Override public ItemStack get() { return container.getSlot(ContainerData.FURNACE_FUEL); }
            @Override public void set(ItemStack s) { container.setSlot(ContainerData.FURNACE_FUEL, s); }

            @Override public boolean mayPlace(ItemStack stack) {
                return ContainerData.isFuel(stack);
            }
        };
    }

    /** Output can only be taken from, never filled. */
    private SlotEngine.Slot outputSlot() {
        ContainerData container = callbacks.container();
        return new SlotEngine.Slot() {
            @Override public ItemStack get() { return container.getSlot(ContainerData.FURNACE_OUTPUT); }
            @Override public void set(ItemStack s) { container.setSlot(ContainerData.FURNACE_OUTPUT, s); }

            @Override public boolean mayPlace(ItemStack stack) { return false; }
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

    /** Shift-click destination for an inventory stack: smeltables go to the
     *  input, fuels to the fuel slot, everything else stays put. */
    private SlotEngine.Slot[] furnaceDestination(ItemStack stack) {
        if (ContainerData.smeltResultFor(stack) != null) {
            return new SlotEngine.Slot[]{ inputSlot() };
        }
        if (ContainerData.isFuel(stack)) {
            return new SlotEngine.Slot[]{ fuelSlot() };
        }
        return new SlotEngine.Slot[0];
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

        font.draw(ui, tr("container.furnace"), panelX + PANEL_PAD, panelY + PANEL_PAD,
            0xFFF2E6C8);

        ContainerData container = callbacks.container();
        Inventory inv = callbacks.inventory();
        ItemStack mouse = callbacks.mouseItem();
        ItemStack hovered = null;

        // Input slot
        drawGlassSlot(ui, gui, inputSlotX, inputSlotY, mx, my);
        ItemStack inputStack = container.getSlot(ContainerData.FURNACE_INPUT);
        if (!inputStack.isEmpty()) {
            StackIcons.drawStack(ui, font, atlas, inputStack, inputSlotX + 1, inputSlotY + 1);
            if (inside(mx, my, inputSlotX, inputSlotY)) hovered = inputStack;
        }

        // Fuel slot
        drawGlassSlot(ui, gui, fuelSlotX, fuelSlotY, mx, my);
        ItemStack fuelStack = container.getSlot(ContainerData.FURNACE_FUEL);
        if (!fuelStack.isEmpty()) {
            StackIcons.drawStack(ui, font, atlas, fuelStack, fuelSlotX + 1, fuelSlotY + 1);
            if (inside(mx, my, fuelSlotX, fuelSlotY)) hovered = fuelStack;
        }

        // Output slot
        drawGlassSlot(ui, gui, outputSlotX, outputSlotY, mx, my);
        ItemStack outputStack = container.getSlot(ContainerData.FURNACE_OUTPUT);
        if (!outputStack.isEmpty()) {
            StackIcons.drawStack(ui, font, atlas, outputStack, outputSlotX + 1, outputSlotY + 1);
            if (inside(mx, my, outputSlotX, outputSlotY)) hovered = outputStack;
        }

        // Fire icon (fuel burn indicator)
        int fireX = fuelSlotX + SLOT / 2 - 6;
        int fireY = fuelSlotY - 12;
        ui.useSolidColor();
        if (container.getFuelTime() > 0 && container.getFuelTimeTotal() > 0) {
            int fireH = (int) ((float) container.getFuelTime() / container.getFuelTimeTotal() * 14);
            ui.fillRect(fireX - 3, fireY - 3, 18, 18, 0x22FF8000);
            ui.fillRect(fireX, fireY + (14 - fireH), 12, fireH, 0xFFFF8000);
            ui.fillRect(fireX - 1, fireY - 1, 14, 1, 0xFF56617A);
            ui.fillRect(fireX - 1, fireY + 14, 14, 1, 0xFF56617A);
            ui.fillRect(fireX - 1, fireY, 1, 14, 0xFF56617A);
            ui.fillRect(fireX + 12, fireY, 1, 14, 0xFF56617A);
        } else {
            ui.fillRect(fireX - 1, fireY - 1, 14, 1, 0xFF56617A);
            ui.fillRect(fireX - 1, fireY + 14, 14, 1, 0xFF56617A);
            ui.fillRect(fireX - 1, fireY, 1, 14, 0xFF56617A);
            ui.fillRect(fireX + 12, fireY, 1, 14, 0xFF56617A);
        }

        // Progress arrow (smelting indicator)
        int arrowX = inputSlotX + SLOT + 4;
        int arrowY = inputSlotY + SLOT / 2 - 4;
        ui.fillRect(arrowX, arrowY, 24, 8, 0xFF2A3140);
        ui.fillRect(arrowX + 24, arrowY + 1, 4, 6, 0xFF2A3140);
        ui.fillRect(arrowX, arrowY, 24, 1, 0xFF3A4258);
        if (container.canSmelt() && container.getFuelTime() > 0) {
            int progressW = (int) ((float) container.getSmeltTime()
                / ContainerData.getSmeltDuration() * 24);
            ui.fillRect(arrowX, arrowY, progressW, 8, 0xFF40C040);
            ui.fillRect(arrowX, arrowY, progressW, 1, 0xFF90E890);
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

        // Input slot
        if (inside(mx, my, inputSlotX, inputSlotY)) {
            handleFurnaceSlotClick(inputSlot(), mouse, button, shift);
            return true;
        }

        // Fuel slot
        if (inside(mx, my, fuelSlotX, fuelSlotY)) {
            handleFurnaceSlotClick(fuelSlot(), mouse, button, shift);
            return true;
        }

        // Output slot: take only
        if (inside(mx, my, outputSlotX, outputSlotY)) {
            SlotEngine.Slot s = outputSlot();
            if (shift) {
                SlotEngine.quickMove(s, playerSlots());
            } else {
                callbacks.onMouseItemChanged(SlotEngine.click(s, mouse, button == 0));
            }
            return true;
        }

        // Hotbar strip
        if (inside(mx, my, hotbarX, hotbarY, Inventory.HOTBAR_SIZE * SLOT, SLOT)) {
            int slot = (int) ((mx - hotbarX) / SLOT);
            if (slot >= 0 && slot < Inventory.HOTBAR_SIZE) {
                SlotEngine.Slot s = hotbarSlot(slot);
                if (shift) {
                    SlotEngine.quickMove(s, furnaceDestination(s.get()));
                } else {
                    callbacks.onMouseItemChanged(SlotEngine.click(s, mouse, button == 0));
                }
            }
            return true;
        }

        // Storage area
        if (inside(mx, my, storageX, storageY,
                COLS * SLOT, Inventory.MAIN_INVENTORY_SIZE / COLS * SLOT)) {
            int col = (int) ((mx - storageX) / SLOT);
            int row = (int) ((my - storageY) / SLOT);
            int slot = row * COLS + col;
            if (slot >= 0 && slot < Inventory.MAIN_INVENTORY_SIZE) {
                SlotEngine.Slot s = storageSlot(slot);
                if (shift) {
                    SlotEngine.quickMove(s, furnaceDestination(s.get()));
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

    private void handleFurnaceSlotClick(SlotEngine.Slot slot, ItemStack mouse,
                                        int button, boolean shift) {
        if (shift) {
            SlotEngine.quickMove(slot, playerSlots());
        } else {
            callbacks.onMouseItemChanged(SlotEngine.click(slot, mouse, button == 0));
        }
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
        if (inside(mx, my, inputSlotX, inputSlotY)) return inputSlot();
        if (inside(mx, my, fuelSlotX, fuelSlotY)) return fuelSlot();
        if (inside(mx, my, outputSlotX, outputSlotY)) return outputSlot();
        if (inside(mx, my, storageX, storageY,
                COLS * SLOT, Inventory.MAIN_INVENTORY_SIZE / COLS * SLOT)) {
            int col = (int) ((mx - storageX) / SLOT);
            int row = (int) ((my - storageY) / SLOT);
            int slot = row * COLS + col;
            if (slot >= 0 && slot < Inventory.MAIN_INVENTORY_SIZE) return storageSlot(slot);
        }
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
