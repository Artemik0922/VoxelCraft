package com.voxelgame.ui.screen;

import com.voxelgame.core.Game;
import com.voxelgame.item.*;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.StackIcons;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.TextField;
import com.voxelgame.world.BlockType;

import java.util.Locale;

import static com.voxelgame.core.Language.tr;

/**
 * Crafting table screen - 3x3 grid + result slot above the player inventory,
 * with a recipe book on the right.
 *
 * All slot clicks run through {@link SlotEngine} with vanilla semantics:
 * left click take/place/merge/swap, right click half/one, shift-click quick
 * move (and craft-all on the result slot), 1-9 hotbar swap, Q drop.
 *
 * The recipe book only fills the grid with materials the player actually
 * owns: clicking an entry pulls one ingredient set out of the inventory,
 * shift-clicking crafts as many copies of the result as the materials allow.
 */
public class CraftingScreen extends Screen {

    public interface Callbacks {
        Inventory inventory();
        ItemStack mouseItem();
        void onMouseItemChanged(ItemStack stack);
        void onClose();
        /** Fired when the player takes a crafted result out of the grid. */
        void onCraft(ItemStack result);
        /** Drop a stack into the world at the player's feet. */
        void dropStack(ItemStack stack);
    }

    private static final int SLOT = GuiAssets.SLOT_SIZE;
    private static final int SECTION_GAP = 20;

    // Recipe book geometry
    private static final int BOOK_ENTRY_H = 34;
    private static final int BOOK_VISIBLE = 9;
    private static final int BOOK_W = 200;
    private static final int BOOK_SEARCH_H = 14;

    private static final int PANEL_PAD = 7;
    private static final int TITLE_H = 12;

    private final Callbacks callbacks;
    private final TextureAtlas atlas;
    private final ItemStack[] grid = new ItemStack[9];
    private ItemStack resultSlot = new ItemStack(BlockType.AIR, 0);

    private int panelX, panelY, panelW, panelH;
    private int gridStartX, gridStartY;
    private int outputX, outputY;
    private int storageX, storageY;
    private int hotbarX, hotbarY;

    // Book state
    private boolean bookOpen = false;
    private int bookScroll = 0;
    private int bookButtonX, bookButtonY, bookButtonW, bookButtonH;
    private int bookX, bookY, bookW, bookH;

    private float lastMx, lastMy;

    /** [UI-042] Recipe book search: filters the visible recipe list. */
    private final TextField searchField = new TextField(0, 0, 10, BOOK_SEARCH_H, 24);
    private Recipe[] visibleList = RecipeRegistry.RECIPES;

    public CraftingScreen(Callbacks callbacks, TextureAtlas atlas) {
        this.callbacks = callbacks;
        this.atlas = atlas;
        for (int i = 0; i < 9; i++) grid[i] = new ItemStack(BlockType.AIR, 0);
    }

    @Override
    public boolean rendersWorld() { return false; }

    @Override
    public boolean usesBlurredBackdrop() { return true; }

    @Override
    protected void layout() {
        int gridSize = 3 * SLOT;
        int contentW = 9 * SLOT;
        panelW = PANEL_PAD * 2 + contentW;
        panelH = PANEL_PAD * 2 + TITLE_H + 6 + gridSize + SECTION_GAP
                + 3 * SLOT + SECTION_GAP + SLOT;

        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        // Crafting section centered above the inventory
        int craftSectionW = gridSize + SECTION_GAP + SLOT + SECTION_GAP + SLOT;
        gridStartX = panelX + (panelW - craftSectionW) / 2;
        gridStartY = panelY + PANEL_PAD + TITLE_H + 6;
        outputX = gridStartX + gridSize + SECTION_GAP;
        outputY = gridStartY + SLOT;

        storageX = panelX + PANEL_PAD;
        storageY = gridStartY + gridSize + SECTION_GAP;

        hotbarX = storageX;
        hotbarY = storageY + 3 * SLOT + SECTION_GAP;

        // Book button hangs off the top right of the panel
        bookButtonW = 78;
        bookButtonH = 16;
        bookButtonX = panelX + panelW - bookButtonW - 6;
        bookButtonY = panelY + 6;

        bookW = BOOK_W;
        bookH = 4 + 20 + BOOK_SEARCH_H + 4 + BOOK_VISIBLE * BOOK_ENTRY_H;
        bookX = panelX + panelW + 12;
        bookY = panelY;

        // Search row sits between the title and the first recipe entry
        searchField.x = bookX + 8;
        searchField.y = bookY + 22;
        searchField.width = bookW - 16;
        searchField.height = BOOK_SEARCH_H;
        searchField.setPlaceholder(tr("recipeBook.search"));

        rebuildRecipes();
    }

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets gui) {
        MenuTheme.drawWorldOverlay(ui, width, height);
        ui.drawNineSlice(gui.glassPanel, panelX, panelY, panelW, panelH,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF332314);
        font.draw(ui, tr("container.crafting"), gridStartX, panelY + 8, 0xFFF2E6C8);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets gui, float mx, float my) {
        lastMx = mx;
        lastMy = my;

        Inventory inv = callbacks.inventory();
        ItemStack mouse = callbacks.mouseItem();
        ItemStack hovered = null;

        // Grid
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int sx = gridStartX + x * SLOT;
                int sy = gridStartY + y * SLOT;
                boolean over = mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT;
                ui.drawNineSlice(gui.glassSlot, sx, sy, SLOT, SLOT,
                    3, GuiAssets.SLOT_SIZE, over ? 0xFF9A7D4C : 0xFF3A2A1A);
                ItemStack item = grid[y * 3 + x];
                if (!item.isEmpty()) {
                    StackIcons.drawStack(ui, font, atlas, item, sx + 1, sy + 1);
                    if (inside(mx, my, sx, sy)) hovered = item;
                }
            }
        }

        // Arrow
        font.draw(ui, "→", outputX - SECTION_GAP / 2, outputY + 6, 0xFF8EA2C2);

        // Output
        ui.drawNineSlice(gui.glassSlot, outputX, outputY, SLOT, SLOT,
            3, GuiAssets.SLOT_SIZE, 0xFF3A2A1A);
        if (!resultSlot.isEmpty()) {
            StackIcons.drawStack(ui, font, atlas, resultSlot, outputX + 1, outputY + 1);
            if (inside(mx, my, outputX, outputY)) hovered = resultSlot;
        }

        // Book button
        ui.drawNineSlice(gui.glassPanel, bookButtonX, bookButtonY, bookButtonW, bookButtonH,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF26304A);
        ui.useSolidColor();
        ui.fillRect(bookButtonX + 4, bookButtonY + bookButtonH - 2,
            bookButtonW - 8, 1, MenuTheme.ACCENT);
        font.draw(ui, tr("recipeBook.toggle"), bookButtonX + 5, bookButtonY + 4, 0xFFF2E6C8);

        if (bookOpen) {
            renderBook(ui, font, gui, mx, my);
        }

        // Player inventory
        for (int i = 0; i < Inventory.MAIN_INVENTORY_SIZE; i++) {
            int sx = storageX + (i % 9) * SLOT;
            int sy = storageY + (i / 9) * SLOT;
            boolean over = mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT;
            ui.drawNineSlice(gui.glassSlot, sx, sy, SLOT, SLOT,
                3, GuiAssets.SLOT_SIZE, over ? 0xFF9A7D4C : 0xFF3A2A1A);
            ItemStack stack = inv.getInventoryItem(i);
            if (!stack.isEmpty()) {
                StackIcons.drawStack(ui, font, atlas, stack, sx + 1, sy + 1);
                if (inside(mx, my, sx, sy)) hovered = stack;
            }
        }

        // Hotbar strip
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int sx = hotbarX + i * SLOT;
            boolean over = mx >= sx && mx < sx + SLOT && my >= hotbarY && my < hotbarY + SLOT;
            ui.drawNineSlice(gui.glassSlot, sx, hotbarY, SLOT, SLOT,
                3, GuiAssets.SLOT_SIZE, over ? 0xFF9A7D4C : 0xFF3A2A1A);
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

    /** The scrollable list of recipes. */
    private void renderBook(UIRenderer ui, FontRenderer font, GuiAssets gui, float mx, float my) {
        ui.drawNineSlice(gui.glassPanel, bookX, bookY, bookW, bookH,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF151A2A);
        ui.useSolidColor();
        ui.fillRect(bookX + 8, bookY + 1, bookW - 16, 1, 0x60FFFFFF);
        font.draw(ui, tr("recipeBook.title"), bookX + 8, bookY + 6, 0xFFF2E6C8);

        // Search bar filters the list below it
        searchField.render(ui, font, gui, mx, my);

        Recipe[] recipes = visibleList;
        int maxScroll = Math.max(0, recipes.length - BOOK_VISIBLE);
        if (bookScroll > maxScroll) bookScroll = maxScroll;

        for (int i = 0; i < BOOK_VISIBLE; i++) {
            int idx = bookScroll + i;
            if (idx >= recipes.length) break;

            Recipe r = recipes[idx];
            int ey = bookY + 22 + BOOK_SEARCH_H + 4 + i * BOOK_ENTRY_H;

            // Mini 3x3 pattern: 7px cells with 1px gaps
            int px = bookX + 8;
            int py = ey + 4;
            ItemStack[] inputs = r.getInputs();
            int w = r.getWidth();
            int h = r.getHeight();
            for (int gy = 0; gy < 3; gy++) {
                for (int gx = 0; gx < 3; gx++) {
                    ui.useSolidColor();
                    ui.fillRect(px + gx * 8, py + gy * 8, 7, 7, 0xFF151A28);
                    int si = gy * 3 + gx;
                    ItemStack ing = (gy < h && gx < w) ? inputs[gy * w + gx] : null;
                    if (ing != null && !ing.isEmpty()) {
                        drawIngredientIcon(ui, ing, px + gx * 8 + 1, py + gy * 8 + 1, 5);
                    }
                }
            }

            // Arrow and output icon
            int ox = px + 3 * 8 + 10;
            font.draw(ui, "→", ox, py + 3, 0xFF8EA2C2);
            int outX = ox + 14;
            ui.drawNineSlice(gui.glassSlot, outX, py, 18, 18, 3, GuiAssets.SLOT_SIZE, 0xFF3A2A1A);
            drawIngredientIcon(ui, r.getResult(), outX + 1, py + 1, 16);
            if (r.getResult().getCount() > 1) {
                font.draw(ui, "x" + r.getResult().getCount(), outX + 10, py + 11, 0xFFB8C2DC);
            }

            // Recipe name; dim when the inventory cannot cover one set
            String name = recipeName(r);
            boolean affordable = hasIngredients(r, 1);
            font.draw(ui, name, outX + 22, py + 6, affordable ? 0xFFF2E6C8 : 0xFF6B7488);

            // Hover highlight
            if (mx >= px && mx < bookX + bookW - 6 && my >= ey && my < ey + BOOK_ENTRY_H) {
                ui.useSolidColor();
                ui.fillRect(bookX + 4, ey, bookW - 8, BOOK_ENTRY_H, 0x2A000000 | MenuTheme.ACCENT);
            }
        }
    }

    private String recipeName(Recipe r) {
        return StackIcons.displayName(r.getResult());
    }

    /** [UI-042] Recompute the recipe list from the search query. */
    private void rebuildRecipes() {
        String q = searchField.getText().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            visibleList = RecipeRegistry.RECIPES;
            return;
        }
        java.util.ArrayList<Recipe> out = new java.util.ArrayList<>();
        for (Recipe r : RecipeRegistry.RECIPES) {
            if (StackIcons.displayName(r.getResult()).toLowerCase(Locale.ROOT).contains(q)) {
                out.add(r);
                continue;
            }
            for (ItemStack ing : r.getInputs()) {
                if (ing != null && !ing.isEmpty()
                        && StackIcons.displayName(ing).toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(r);
                    break;
                }
            }
        }
        visibleList = out.toArray(new Recipe[0]);
    }

    private void drawIngredientIcon(UIRenderer ui, ItemStack stack, int x, int y, int size) {
        if (stack == null || stack.isEmpty()) return;
        if (stack.isBlock()) {
            TextureAtlas.TextureCoords uv = atlas.getCoords(stack.getBlockType().id, 2);
            ui.drawTexture(atlas.getTexture().getId(), x, y, size, size,
                uv.u1, uv.v2, uv.u2, uv.v1, 0xFFFFFFFF);
            return;
        }
        Item item = stack.getItem();
        if (item != null && item.spriteName != null) {
            int slot = atlas.getLayerOf(item.spriteName);
            if (slot >= 0) {
                TextureAtlas.TextureCoords uv = new TextureAtlas.TextureCoords(slot);
                ui.drawTexture(atlas.getTexture().getId(), x, y, size, size,
                    uv.u1, uv.v2, uv.u2, uv.v1, 0xFFFFFFFF);
                return;
            }
        }
        if (item != null && item.blockType != null) {
            TextureAtlas.TextureCoords uv = atlas.getCoords(item.blockType.id, 2);
            ui.drawTexture(atlas.getTexture().getId(), x, y, size, size,
                uv.u1, uv.v2, uv.u2, uv.v1, 0xFFFFFFFF);
            return;
        }
        ui.useSolidColor();
        ui.fillRect(x, y, size, size, 0xFF9A9A9A);
    }

    // ------------------------------------------------------------------
    // Slot views
    // ------------------------------------------------------------------

    private SlotEngine.Slot gridSlot(int index) {
        return new SlotEngine.Slot() {
            @Override public ItemStack get() { return grid[index]; }
            @Override public void set(ItemStack s) {
                grid[index] = s == null || s.isEmpty()
                    ? new ItemStack(BlockType.AIR, 0) : s;
                updateResult();
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
    // Mouse
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        ItemStack mouse = callbacks.mouseItem();
        boolean shift = Game.isShiftDown();

        // Book button toggles the recipe list
        if (mx >= bookButtonX && mx < bookButtonX + bookButtonW
            && my >= bookButtonY && my < bookButtonY + bookButtonH) {
            bookOpen = !bookOpen;
            return true;
        }

        // Clicking a recipe fills the grid from the inventory (or shift:
        // crafts straight into the inventory), clicking the search field focuses it
        if (bookOpen) {
            if (searchField.mouseClicked(mx, my, button)) return true;
            for (int i = 0; i < BOOK_VISIBLE; i++) {
                int idx = bookScroll + i;
                if (idx >= visibleList.length) break;
                int ey = bookY + 22 + BOOK_SEARCH_H + 4 + i * BOOK_ENTRY_H;
                if (mx >= bookX + 4 && mx < bookX + bookW - 4
                    && my >= ey && my < ey + BOOK_ENTRY_H) {
                    useRecipe(visibleList[idx], shift);
                    return true;
                }
            }
        }

        // Grid
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int sx = gridStartX + x * SLOT;
                int sy = gridStartY + y * SLOT;
                if (inside(mx, my, sx, sy)) {
                    int index = y * 3 + x;
                    if (shift) {
                        SlotEngine.quickMove(gridSlot(index), playerSlots());
                    } else {
                        callbacks.onMouseItemChanged(
                            SlotEngine.click(gridSlot(index), mouse, button == 0));
                    }
                    return true;
                }
            }
        }

        // Output: take onto the cursor, shift crafts everything
        if (inside(mx, my, outputX, outputY)) {
            takeResult(shift);
            return true;
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
                9 * SLOT, Inventory.MAIN_INVENTORY_SIZE / 9 * SLOT)) {
            int col = (int) ((mx - storageX) / SLOT);
            int row = (int) ((my - storageY) / SLOT);
            int slot = row * 9 + col;
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

        // Click outside the panel drops the cursor stack into the world
        if (!mouse.isEmpty() && !insidePanel(mx, my) && !insideBookArea(mx, my)
                && !overDoneButton(mx, my)) {
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

    private boolean insideBookArea(float mx, float my) {
        return bookOpen && mx >= bookX && mx < bookX + bookW && my >= bookY && my < bookY + bookH;
    }

    // ------------------------------------------------------------------
    // Recipe book
    // ------------------------------------------------------------------

    /** Does the inventory cover {@code times} full ingredient sets? */
    private boolean hasIngredients(Recipe recipe, int times) {
        Inventory inv = callbacks.inventory();
        for (ItemStack ing : recipe.getInputs()) {
            if (ing == null || ing.isEmpty()) continue;
            int have = ing.isBlock()
                ? inv.countItem(ing.getBlockType()) : inv.countItem(ing.getItem());
            if (have < times) return false;
        }
        return true;
    }

    /** Click on a book entry: fill the grid with one owned ingredient set,
     *  or with shift craft the result into the inventory repeatedly. */
    private void useRecipe(Recipe recipe, boolean shift) {
        if (shift) {
            Inventory inv = callbacks.inventory();
            int guard = 0;
            while (hasIngredients(recipe, 1) && guard++ < 64) {
                consumeIngredients(recipe, 1);
                ItemStack leftover = inv.addStack(recipe.getResult().copy());
                if (!leftover.isEmpty()) {
                    // Refund what could not be crafted and stop
                    refundIngredients(recipe, 1);
                    break;
                }
                callbacks.onCraft(recipe.getResult());
            }
            return;
        }

        if (!hasIngredients(recipe, 1)) return;

        // Return whatever the grid held back to the inventory first
        returnGridToInventory();

        ItemStack[] inputs = recipe.getInputs();
        int w = recipe.getWidth();
        for (int j = 0; j < inputs.length; j++) {
            ItemStack ing = inputs[j];
            if (ing == null || ing.isEmpty()) continue;
            int row = j / w;
            int col = j % w;
            grid[row * 3 + col] = takeOneFromInventory(ing);
        }
        updateResult();
    }

    private ItemStack takeOneFromInventory(ItemStack ing) {
        Inventory inv = callbacks.inventory();
        if (ing.isBlock()) {
            for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
                ItemStack s = inv.getHotbarItem(i);
                if (s.isBlock() && s.getBlockType() == ing.getBlockType()) {
                    ItemStack one = s.copyWithCount(1);
                    inv.setHotbarItem(i, s.getCount() > 1
                        ? s.copyWithCount(s.getCount() - 1) : new ItemStack(BlockType.AIR, 0));
                    return one;
                }
            }
            for (int i = 0; i < Inventory.MAIN_INVENTORY_SIZE; i++) {
                ItemStack s = inv.getInventoryItem(i);
                if (s.isBlock() && s.getBlockType() == ing.getBlockType()) {
                    ItemStack one = s.copyWithCount(1);
                    inv.setInventoryItem(i, s.getCount() > 1
                        ? s.copyWithCount(s.getCount() - 1) : new ItemStack(BlockType.AIR, 0));
                    return one;
                }
            }
        } else if (ing.isItem()) {
            for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
                ItemStack s = inv.getHotbarItem(i);
                if (s.isItem() && s.getItem() == ing.getItem()) {
                    ItemStack one = s.copyWithCount(1);
                    inv.setHotbarItem(i, s.getCount() > 1
                        ? s.copyWithCount(s.getCount() - 1) : new ItemStack(BlockType.AIR, 0));
                    return one;
                }
            }
            for (int i = 0; i < Inventory.MAIN_INVENTORY_SIZE; i++) {
                ItemStack s = inv.getInventoryItem(i);
                if (s.isItem() && s.getItem() == ing.getItem()) {
                    ItemStack one = s.copyWithCount(1);
                    inv.setInventoryItem(i, s.getCount() > 1
                        ? s.copyWithCount(s.getCount() - 1) : new ItemStack(BlockType.AIR, 0));
                    return one;
                }
            }
        }
        return new ItemStack(BlockType.AIR, 0);
    }

    private void consumeIngredients(Recipe recipe, int times) {
        Inventory inv = callbacks.inventory();
        for (ItemStack ing : recipe.getInputs()) {
            if (ing == null || ing.isEmpty()) continue;
            if (ing.isBlock()) inv.removeItem(ing.getBlockType(), times);
            else inv.removeItem(ing.getItem(), times);
        }
    }

    /** Refund a consumed ingredient set (used when the output no longer fits). */
    private void refundIngredients(Recipe recipe, int times) {
        Inventory inv = callbacks.inventory();
        for (ItemStack ing : recipe.getInputs()) {
            if (ing == null || ing.isEmpty()) continue;
            for (int t = 0; t < times; t++) {
                if (ing.isBlock()) inv.addItem(ing.getBlockType(), 1);
                else if (ing.isItem()) inv.addItem(ing.getItem(), 1);
            }
        }
    }

    // ------------------------------------------------------------------
    // Result
    // ------------------------------------------------------------------

    private void takeResult(boolean shift) {
        if (resultSlot.isEmpty()) return;

        if (shift) {
            int guard = 0;
            while (!resultSlot.isEmpty() && guard++ < 64) {
                ItemStack leftover = callbacks.inventory().addStack(resultSlot.copy());
                if (!leftover.isEmpty()) break;
                consumeGrid();
                callbacks.onCraft(resultSlot);
                updateResult();
            }
            return;
        }

        ItemStack mouse = callbacks.mouseItem();
        if (mouse.isEmpty()) {
            callbacks.onMouseItemChanged(resultSlot.copy());
            consumeGrid();
            callbacks.onCraft(resultSlot);
        } else if (mouse.canMerge(resultSlot)
                && mouse.getCount() + resultSlot.getCount() <= mouse.getMaxStackSize()) {
            callbacks.onMouseItemChanged(
                mouse.copyWithCount(mouse.getCount() + resultSlot.getCount()));
            consumeGrid();
            callbacks.onCraft(resultSlot);
        }
        updateResult();
    }

    private void consumeGrid() {
        for (int i = 0; i < 9; i++) {
            ItemStack g = grid[i];
            if (g == null || g.isEmpty()) continue;
            grid[i] = g.getCount() <= 1
                ? new ItemStack(BlockType.AIR, 0) : g.copyWithCount(g.getCount() - 1);
        }
    }

    private void updateResult() {
        resultSlot = new ItemStack(BlockType.AIR, 0);
        for (Recipe recipe : RecipeRegistry.RECIPES) {
            if (recipe.matches(grid, 3)) {
                resultSlot = recipe.getResult().copy();
                return;
            }
        }
    }

    /** Scroll the recipe list with the mouse wheel. */
    @Override
    public boolean mouseScrolled(float mx, float my, double delta) {
        if (!bookOpen) return false;
        bookScroll -= (int) delta;
        int max = Math.max(0, visibleList.length - BOOK_VISIBLE);
        bookScroll = Math.max(0, Math.min(bookScroll, max));
        return true;
    }

    // ------------------------------------------------------------------
    // Keyboard: Q drop, 1-9 hotbar swap
    // ------------------------------------------------------------------

    @Override
    public boolean keyPressed(int key, int mods) {
        // While the search field is focused, all keys feed the field
        // (backspace, arrows) instead of acting on slots. TextField returns
        // false for plain letters — handled via charTyped instead.
        if (bookOpen && searchField.isFocused()) {
            if (searchField.keyPressed(key, mods)) {
                rebuildRecipes();
                bookScroll = 0;
            }
            return true;
        }

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

    /** Keyboard: recipe-book search text entry. */
    public boolean charTyped(char c) {
        if (!bookOpen || !searchField.isFocused()) return false;
        if (!searchField.charTyped(c)) return false;
        rebuildRecipes();
        bookScroll = 0;
        return true;
    }

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        searchField.update(deltaTime);
    }

    private SlotEngine.Slot hoveredSlot() {
        float mx = lastMx, my = lastMy;
        for (int i = 0; i < 9; i++) {
            int sx = gridStartX + (i % 3) * SLOT;
            int sy = gridStartY + (i / 3) * SLOT;
            if (inside(mx, my, sx, sy)) return gridSlot(i);
        }
        if (inside(mx, my, storageX, storageY,
                9 * SLOT, Inventory.MAIN_INVENTORY_SIZE / 9 * SLOT)) {
            int col = (int) ((mx - storageX) / SLOT);
            int row = (int) ((my - storageY) / SLOT);
            int slot = row * 9 + col;
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

    // ------------------------------------------------------------------
    // Close: return grid contents so nothing is lost
    // ------------------------------------------------------------------

    private Runnable onClosedCallback;
    public void onClose(Runnable cb) { this.onClosedCallback = cb; }

    private void fireClosed() {
        returnGridToInventory();
        if (onClosedCallback != null) {
            Runnable cb = onClosedCallback;
            onClosedCallback = null;
            cb.run();
        }
    }

    /** Also fires when the screen is popped with Escape. */
    @Override
    public void onClosed() { fireClosed(); }

    private void returnGridToInventory() {
        Inventory inv = callbacks.inventory();
        for (int i = 0; i < 9; i++) {
            ItemStack g = grid[i];
            if (g == null || g.isEmpty()) continue;
            ItemStack leftover = inv.addStack(g);
            grid[i] = leftover.isEmpty() ? new ItemStack(BlockType.AIR, 0) : leftover;
        }
        updateResult();
    }
}
