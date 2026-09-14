package com.voxelgame.ui.screen;

import com.voxelgame.core.Game;
import com.voxelgame.item.Inventory;
import com.voxelgame.item.ItemStack;
import com.voxelgame.item.Recipe;
import com.voxelgame.item.RecipeRegistry;
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
 * Survival inventory, Minecraft-style: armor column, 27 storage slots, hotbar
 * strip and the persistent 2x2 crafting grid with a live result slot.
 *
 * Every click goes through {@link SlotEngine}: left click takes/places/merges
 * whole stacks, right click splits and places single items, shift-click quick
 * moves between regions, 1-9 swaps the hovered slot with the hotbar, Q drops
 * and clicking outside the panel drops the cursor stack into the world.
 */
public class SurvivalInventoryScreen extends Screen {

    public interface Callbacks {
        Inventory inventory();
        ItemStack mouseItem();
        void onMouseItemChanged(ItemStack stack);
        void onSelectHotbarSlot(int index);
        /** The persistent 2x2 crafting grid (row-major, 4 cells). */
        ItemStack[] craftingGrid();
        /** Fired when the player takes a crafted result out of the grid. */
        void onCraft(ItemStack result);
        /** Drop a stack into the world at the player's feet. */
        void dropStack(ItemStack stack);
    }

    private static final int SLOT = GuiAssets.SLOT_SIZE;
    private static final int COLS = 9;

    private static final int PANEL_PAD = 7;
    private static final int TITLE_H = 12;
    private static final int SECTION_GAP = 4;
    private static final int CRAFT_GAP = 16;
    private static final int ARMOR_GAP = 10;

    private final Callbacks callbacks;
    private final TextureAtlas atlas;

    private int panelX, panelY, panelW, panelH;
    private int armorX, armorY;
    private int storageX, storageY;
    private int hotbarX, hotbarY;
    private int craftX, craftY, resultX, resultY;

    /** Result of the 2x2 grid, recomputed after every grid change. */
    private ItemStack resultSlot = new ItemStack(BlockType.AIR, 0);

    // Hover tracking for keyboard interactions (Q, 1-9)
    private float lastMx, lastMy;

    public SurvivalInventoryScreen(Callbacks callbacks, TextureAtlas atlas) {
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
        int storageRows = Inventory.MAIN_INVENTORY_SIZE / COLS;
        int storageH = storageRows * SLOT;
        int craftW = 2 * SLOT + CRAFT_GAP + SLOT + CRAFT_GAP + SLOT;
        int armorW = SLOT;

        panelW = PANEL_PAD * 2 + armorW + ARMOR_GAP + contentW + CRAFT_GAP + craftW;
        panelH = PANEL_PAD * 2 + TITLE_H + SECTION_GAP * 2 + storageH + SLOT;

        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        armorX = panelX + PANEL_PAD;
        armorY = panelY + PANEL_PAD + TITLE_H;

        storageX = armorX + armorW + ARMOR_GAP;
        storageY = armorY;

        hotbarX = storageX;
        hotbarY = storageY + storageH + SECTION_GAP;

        craftX = storageX + contentW + CRAFT_GAP;
        craftY = storageY;
        resultX = craftX + 2 * SLOT + CRAFT_GAP + SLOT;
        resultY = craftY + SLOT;

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
    // Slot views over the player's storage
    // ------------------------------------------------------------------

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

    private SlotEngine.Slot armorSlot(int i) {
        Inventory inv = callbacks.inventory();
        return new SlotEngine.Slot() {
            @Override public ItemStack get() { return inv.getArmor(i); }
            @Override public void set(ItemStack s) { inv.setArmor(i, s); }

            @Override public boolean mayPlace(ItemStack stack) {
                return stack.isItem() && stack.getItem() != null
                    && stack.getItem().isArmor()
                    && stack.getItem().armorSlot.index == i;
            }
        };
    }

    private SlotEngine.Slot gridSlot(int i) {
        return new SlotEngine.Slot() {
            @Override public ItemStack get() { return callbacks.craftingGrid()[i]; }
            @Override public void set(ItemStack s) {
                callbacks.craftingGrid()[i] = s == null || s.isEmpty()
                    ? new ItemStack(BlockType.AIR, 0) : s;
                updateResult();
            }
        };
    }

    /** Main inventory then hotbar - the vanilla chest-to-player order. */
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

        font.draw(ui, tr("container.survival"), storageX, panelY + PANEL_PAD, 0xFFE8EEFF);
        font.draw(ui, tr("container.armor"), armorX, panelY + PANEL_PAD, 0xFFE8EEFF);

        Inventory inv = callbacks.inventory();
        ItemStack mouse = callbacks.mouseItem();
        ItemStack hoveredStack = null;
        String hoveredArmorHint = null;

        // Armor column
        for (int i = 0; i < Inventory.ARMOR_SIZE; i++) {
            int sx = armorX;
            int sy = armorY + i * SLOT;
            drawGlassSlot(ui, gui, sx, sy, mx, my);
            ItemStack stack = inv.getArmor(i);
            if (!stack.isEmpty()) {
                StackIcons.drawStack(ui, font, atlas, stack, sx + 1, sy + 1);
                if (inside(mx, my, sx, sy)) hoveredStack = stack;
            } else if (inside(mx, my, sx, sy)) {
                hoveredArmorHint = tr("ui.armor.slot." + i);
            }
        }

        // 27 storage slots
        for (int i = 0; i < Inventory.MAIN_INVENTORY_SIZE; i++) {
            int row = i / COLS;
            int col = i % COLS;
            int sx = storageX + col * SLOT;
            int sy = storageY + row * SLOT;

            drawGlassSlot(ui, gui, sx, sy, mx, my);
            ItemStack stack = inv.getInventoryItem(i);
            if (!stack.isEmpty()) {
                StackIcons.drawStack(ui, font, atlas, stack, sx + 1, sy + 1);
                if (inside(mx, my, sx, sy)) hoveredStack = stack;
            }
        }

        // Hotbar strip
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int sx = hotbarX + i * SLOT;
            drawGlassSlot(ui, gui, sx, hotbarY, mx, my);
            ItemStack stack = inv.getHotbarItem(i);
            if (!stack.isEmpty()) {
                StackIcons.drawStack(ui, font, atlas, stack, sx + 1, hotbarY + 1);
                if (inside(mx, my, sx, hotbarY)) hoveredStack = stack;
            }
            if (i == inv.getSelectedSlot()) {
                ui.useSolidColor();
                ui.fillRect(sx - 1, hotbarY - 1, SLOT + 2, 1, MenuTheme.ACCENT);
                ui.fillRect(sx - 1, hotbarY + SLOT, SLOT + 2, 1, MenuTheme.ACCENT);
                ui.fillRect(sx - 1, hotbarY, 1, SLOT, MenuTheme.ACCENT);
                ui.fillRect(sx + SLOT, hotbarY, 1, SLOT, MenuTheme.ACCENT);
            }
        }

        // Crafting area: 2x2 grid + arrow + result preview
        ItemStack[] grid = callbacks.craftingGrid();
        for (int i = 0; i < 4; i++) {
            int sx = craftX + (i % 2) * SLOT;
            int sy = craftY + (i / 2) * SLOT;
            drawGlassSlot(ui, gui, sx, sy, mx, my);
            ItemStack stack = grid[i];
            if (stack != null && !stack.isEmpty()) {
                StackIcons.drawStack(ui, font, atlas, stack, sx + 1, sy + 1);
                if (inside(mx, my, sx, sy)) hoveredStack = stack;
            }
        }

        font.draw(ui, "→", craftX + 2 * SLOT + CRAFT_GAP / 2 - 3, resultY + 6,
            GuiAssets.TEXT_HINT);

        drawGlassSlot(ui, gui, resultX, resultY, mx, my);
        if (!resultSlot.isEmpty()) {
            StackIcons.drawStack(ui, font, atlas, resultSlot, resultX + 1, resultY + 1);
            if (inside(mx, my, resultX, resultY)) hoveredStack = resultSlot;
        }

        // Cursor stack follows the mouse
        if (!mouse.isEmpty()) {
            StackIcons.drawStack(ui, font, atlas, mouse,
                (int) mx - GuiAssets.ICON_SIZE / 2, (int) my - GuiAssets.ICON_SIZE / 2);
        }

        // Tooltip: the hovered stack, or the piece an empty armor slot wants
        if (hoveredStack != null && mouse.isEmpty()) {
            StackIcons.drawTooltip(ui, font, atlas, hoveredStack, mx, my, width, height);
        } else if (hoveredArmorHint != null && mouse.isEmpty()) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            lines.add(hoveredArmorHint);
            com.voxelgame.ui.GlassTooltip.draw(ui, font, lines, (int) mx, (int) my,
                width, height);
        }
    }

    private void drawGlassSlot(UIRenderer ui, GuiAssets gui, int sx, int sy, float mx, float my) {
        boolean hover = inside(mx, my, sx, sy);
        ui.drawNineSlice(hover ? gui.glassSlotHover : gui.glassSlot,
            sx, sy, SLOT, SLOT, 3, GuiAssets.SLOT_SIZE, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------
    // Mouse
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        Inventory inv = callbacks.inventory();
        ItemStack mouse = callbacks.mouseItem();
        boolean shift = Game.isShiftDown();

        // Crafting result: take onto the cursor, or craft everything with shift
        if (inside(mx, my, resultX, resultY)) {
            takeResult(shift);
            return true;
        }

        // Crafting grid
        for (int i = 0; i < 4; i++) {
            int sx = craftX + (i % 2) * SLOT;
            int sy = craftY + (i / 2) * SLOT;
            if (inside(mx, my, sx, sy)) {
                if (shift) {
                    SlotEngine.quickMove(gridSlot(i), playerSlots());
                } else {
                    callbacks.onMouseItemChanged(
                        SlotEngine.click(gridSlot(i), mouse, button == 0));
                }
                return true;
            }
        }

        // Armor slots
        for (int i = 0; i < Inventory.ARMOR_SIZE; i++) {
            int sx = armorX;
            int sy = armorY + i * SLOT;
            if (inside(mx, my, sx, sy)) {
                SlotEngine.Slot slot = armorSlot(i);
                if (shift) {
                    SlotEngine.quickMove(slot, playerSlots());
                } else {
                    callbacks.onMouseItemChanged(SlotEngine.click(slot, mouse, button == 0));
                }
                return true;
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

        // Storage area
        if (inside(mx, my, storageX, storageY,
                COLS * SLOT, Inventory.MAIN_INVENTORY_SIZE / COLS * SLOT)) {
            int col = (int) ((mx - storageX) / SLOT);
            int row = (int) ((my - storageY) / SLOT);
            int slot = row * COLS + col;
            if (slot >= 0 && slot < Inventory.MAIN_INVENTORY_SIZE) {
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

        // Click outside the panel drops the cursor stack into the world,
        // matching vanilla. The Done button keeps its normal behaviour.
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
    // Crafting
    // ------------------------------------------------------------------

    private void takeResult(boolean shift) {
        if (resultSlot.isEmpty()) return;
        ItemStack[] grid = callbacks.craftingGrid();

        if (shift) {
            // Craft as many as the grid ingredients and inventory space allow
            int guard = 0;
            while (!resultSlot.isEmpty() && guard++ < 64) {
                ItemStack leftover = callbacks.inventory().addStack(resultSlot.copy());
                if (!leftover.isEmpty()) break;
                consumeGrid(grid);
                callbacks.onCraft(resultSlot);
                updateResult();
            }
            return;
        }

        ItemStack mouse = callbacks.mouseItem();
        if (mouse.isEmpty()) {
            callbacks.onMouseItemChanged(resultSlot.copy());
            consumeGrid(grid);
            callbacks.onCraft(resultSlot);
        } else if (mouse.canMerge(resultSlot)
                && mouse.getCount() + resultSlot.getCount() <= mouse.getMaxStackSize()) {
            callbacks.onMouseItemChanged(
                mouse.copyWithCount(mouse.getCount() + resultSlot.getCount()));
            consumeGrid(grid);
            callbacks.onCraft(resultSlot);
        }
        updateResult();
    }

    private void consumeGrid(ItemStack[] grid) {
        for (int i = 0; i < grid.length; i++) {
            ItemStack g = grid[i];
            if (g == null || g.isEmpty()) continue;
            grid[i] = g.getCount() <= 1
                ? new ItemStack(BlockType.AIR, 0) : g.copyWithCount(g.getCount() - 1);
        }
    }

    /** Recompute the result preview from the 2x2 grid. */
    private void updateResult() {
        resultSlot = new ItemStack(BlockType.AIR, 0);
        ItemStack[] grid = callbacks.craftingGrid();
        for (Recipe recipe : RecipeRegistry.RECIPES) {
            if (recipe.matches(grid, 2)) {
                resultSlot = recipe.getResult().copy();
                return;
            }
        }
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
                callbacks.onSelectHotbarSlot(index);
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

    /** The slot under the last mouse position, or null. */
    private SlotEngine.Slot hoveredSlot() {
        float mx = lastMx, my = lastMy;

        for (int i = 0; i < 4; i++) {
            int sx = craftX + (i % 2) * SLOT;
            int sy = craftY + (i / 2) * SLOT;
            if (inside(mx, my, sx, sy)) return gridSlot(i);
        }
        for (int i = 0; i < Inventory.ARMOR_SIZE; i++) {
            if (inside(mx, my, armorX, armorY + i * SLOT)) return armorSlot(i);
        }
        if (inside(mx, my, hotbarX, hotbarY, Inventory.HOTBAR_SIZE * SLOT, SLOT)) {
            int slot = (int) ((mx - hotbarX) / SLOT);
            if (slot >= 0 && slot < Inventory.HOTBAR_SIZE) return hotbarSlot(slot);
        }
        if (inside(mx, my, storageX, storageY,
                COLS * SLOT, Inventory.MAIN_INVENTORY_SIZE / COLS * SLOT)) {
            int col = (int) ((mx - storageX) / SLOT);
            int row = (int) ((my - storageY) / SLOT);
            int slot = row * COLS + col;
            if (slot >= 0 && slot < Inventory.MAIN_INVENTORY_SIZE) return storageSlot(slot);
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
