package com.voxelgame.ui.screen;

import com.voxelgame.item.*;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.world.BlockType;

import static com.voxelgame.core.Language.tr;

/**
 * Crafting table screen - 3x3 grid + result slot.
 *
 * [UI-017][UI-018][UI-019] A recipe book: click the button at the top right
 * to open a scrollable list of every recipe, each shown as a miniature
 * 3x3 pattern with its output. Clicking an entry fills the grid for you.
 *
 * Mouse cursor semantics match the survival inventory: left click places one
 * item from the cursor into a grid cell (or picks the cell up), the result
 * slot hands the crafted stack onto the cursor and consumes the grid.
 */
public class CraftingScreen extends Screen {

    public interface Callbacks {
        Inventory inventory();
        ItemStack mouseItem();
        void onMouseItemChanged(ItemStack stack);
        void onClose();
        /** Fired when the player takes a crafted result out of the grid. */
        void onCraft(ItemStack result);
    }

    private static final int SLOT = GuiAssets.SLOT_SIZE;
    private static final int ICON = GuiAssets.ICON_SIZE;
    private static final int SECTION_GAP = 20;

    // [UI-017] Recipe book geometry
    private static final int BOOK_ENTRY_H = 34;
    private static final int BOOK_VISIBLE = 9;
    private static final int BOOK_W = 200;

    private final Callbacks callbacks;
    private final TextureAtlas atlas;
    private final ItemStack[] grid = new ItemStack[9];
    private ItemStack resultSlot = new ItemStack(BlockType.AIR, 0);

    private int panelX, panelY, panelW, panelH;
    private int gridStartX, gridStartY;
    private int outputX, outputY;

    // [UI-017] Book state
    private boolean bookOpen = false;
    private int bookScroll = 0;
    private int bookButtonX, bookButtonY, bookButtonW, bookButtonH;
    private int bookX, bookY, bookW, bookH;

    public CraftingScreen(Callbacks callbacks, TextureAtlas atlas) {
        this.callbacks = callbacks;
        this.atlas = atlas;
        for (int i = 0; i < 9; i++) grid[i] = new ItemStack(BlockType.AIR, 0);
    }

    @Override
    public boolean rendersWorld() { return false; }

    @Override
    protected void layout() {
        int gridSize = 3 * SLOT;
        panelW = gridSize + SECTION_GAP + SLOT + SECTION_GAP + SLOT + 20;
        panelH = gridSize + 40;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        gridStartX = panelX + 10;
        gridStartY = panelY + 25;
        outputX = gridStartX + gridSize + SECTION_GAP + 10;
        outputY = gridStartY + SLOT;

        // [UI-017] Book button hangs off the top right of the panel
        bookButtonW = 78;
        bookButtonH = 16;
        bookButtonX = panelX + panelW - bookButtonW - 6;
        bookButtonY = panelY + 6;

        bookW = BOOK_W;
        bookH = 4 + BOOK_VISIBLE * BOOK_ENTRY_H;
        bookX = panelX + panelW + 12;
        bookY = panelY;
    }

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets gui) {
        ui.fillRect(0, 0, width, height, 0xC8000000);
        ui.drawNineSlice(gui.panel, panelX, panelY, panelW, panelH,
            GuiAssets.BORDER, GuiAssets.WIDGET, 0xFFFFFFFF);
        font.draw(ui, tr("container.crafting"), gridStartX, panelY + 8, GuiAssets.TEXT_TITLE);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets gui, float mx, float my) {
        // Grid
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int sx = gridStartX + x * SLOT;
                int sy = gridStartY + y * SLOT;
                ui.drawSprite(gui.slot, sx, sy, SLOT, SLOT);
                ItemStack item = grid[y * 3 + x];
                if (!item.isEmpty()) drawItemIcon(ui, item, sx + 1, sy + 1);
            }
        }

        // Arrow
        font.draw(ui, "→", outputX - SECTION_GAP / 2, outputY + 6, GuiAssets.TEXT_HINT);

        // Output
        ui.drawSprite(gui.slot, outputX, outputY, SLOT, SLOT);
        if (!resultSlot.isEmpty()) drawItemIcon(ui, resultSlot, outputX + 1, outputY + 1);

        // [UI-017] Book button
        ui.drawNineSlice(gui.panel, bookButtonX, bookButtonY, bookButtonW, bookButtonH,
            GuiAssets.BORDER, GuiAssets.WIDGET, 0xFF303040);
        ui.drawRectOutline(bookButtonX, bookButtonY, bookButtonW, bookButtonH, 0xFF666666);
        font.draw(ui, tr("recipeBook.toggle"), bookButtonX + 5, bookButtonY + 4, GuiAssets.TEXT_NORMAL);

        if (bookOpen) {
            renderBook(ui, font, gui, mx, my);
        }

        // Mouse item follows the cursor
        ItemStack mouse = callbacks.mouseItem();
        if (mouse != null && !mouse.isEmpty()) {
            drawItemIcon(ui, mouse, (int) mx - ICON / 2, (int) my - ICON / 2);
            if (mouse.getCount() > 1) {
                String text = String.valueOf(mouse.getCount());
                font.drawWithShadow(ui, text,
                    (int) mx + ICON / 2 - font.width(text),
                    (int) my + ICON / 2 - FontRenderer.GLYPH_H,
                    0xFFFFFFFF);
            }
        }
    }

    /** [UI-017] The scrollable list of recipes. */
    private void renderBook(UIRenderer ui, FontRenderer font, GuiAssets gui, float mx, float my) {
        ui.drawNineSlice(gui.panel, bookX, bookY, bookW, bookH,
            GuiAssets.BORDER, GuiAssets.WIDGET, 0xFF202030);
        ui.drawRectOutline(bookX, bookY, bookW, bookH, 0xFF555555);
        font.draw(ui, tr("recipeBook.title"), bookX + 8, bookY + 6, GuiAssets.TEXT_TITLE);

        Recipe[] recipes = RecipeRegistry.RECIPES;
        int maxScroll = Math.max(0, recipes.length - BOOK_VISIBLE);
        if (bookScroll > maxScroll) bookScroll = maxScroll;

        for (int i = 0; i < BOOK_VISIBLE; i++) {
            int idx = bookScroll + i;
            if (idx >= recipes.length) break;

            Recipe r = recipes[idx];
            int ey = bookY + 20 + i * BOOK_ENTRY_H;

            // Mini 3x3 pattern: 7px cells with 1px gaps
            int px = bookX + 8;
            int py = ey + 4;
            ItemStack[] inputs = r.getInputs();
            int w = r.getWidth();
            int h = r.getHeight();
            for (int gy = 0; gy < 3; gy++) {
                for (int gx = 0; gx < 3; gx++) {
                    ui.useSolidColor();
                    ui.fillRect(px + gx * 8, py + gy * 8, 7, 7, 0xFF0E0E18);
                    int si = gy * 3 + gx;
                    ItemStack ing = (gy < h && gx < w) ? inputs[gy * w + gx] : null;
                    if (ing != null && !ing.isEmpty()) {
                        drawIngredientIcon(ui, ing, px + gx * 8 + 1, py + gy * 8 + 1, 5);
                    }
                }
            }

            // Arrow and output icon
            int ox = px + 3 * 8 + 10;
            font.draw(ui, "→", ox, py + 3, GuiAssets.TEXT_HINT);
            int outX = ox + 14;
            ui.useSolidColor();
            ui.fillRect(outX, py, 18, 18, 0xFF0E0E18);
            ui.drawRectOutline(outX, py, 18, 18, 0xFF444444);
            drawIngredientIcon(ui, r.getResult(), outX + 1, py + 1, 16);
            if (r.getResult().getCount() > 1) {
                font.draw(ui, "x" + r.getResult().getCount(), outX + 10, py + 11, GuiAssets.TEXT_HINT);
            }

            // Recipe name
            String name = recipeName(r);
            font.draw(ui, name, outX + 22, py + 6, GuiAssets.TEXT_NORMAL);

            // Hover highlight
            if (mx >= px && mx < bookX + bookW - 6 && my >= ey && my < ey + BOOK_ENTRY_H) {
                ui.useSolidColor();
                ui.fillRect(bookX + 4, ey, bookW - 8, BOOK_ENTRY_H, 0x2AFFFFFF);
            }
        }
    }

    private String recipeName(Recipe r) {
        ItemStack out = r.getResult();
        if (out.isBlock()) {
            String t = tr("block." + out.getBlockType().name);
            if (!t.equals("block." + out.getBlockType().name)) return t;
        } else if (out.getItem() != null) {
            String t = tr("item." + out.getItem().name);
            if (!t.equals("item." + out.getItem().name)) return t;
            return out.getItem().displayName;
        }
        return "";
    }

    private void drawItemIcon(UIRenderer ui, ItemStack item, int x, int y) {
        drawIngredientIcon(ui, item, x, y, ICON);
    }

    /** Flat sprite icon for a block or an item tile. */
    private void drawIngredientIcon(UIRenderer ui, ItemStack stack, int x, int y, int size) {
        if (stack.isEmpty()) return;
        if (stack.isBlock()) {
            TextureAtlas.TextureCoords uv = atlas.getCoords(stack.getBlockType().id, 2);
            ui.drawTexture(atlas.getTexture().getId(), x, y, size, size,
                uv.u1, uv.v2, uv.u2, uv.v1, 0xFFFFFFFF);
            return;
        }
        Item item = stack.getItem();
        if (item != null) {
            int slot = atlas.getLayerOf(item.spriteName);
            if (slot >= 0) {
                TextureAtlas.TextureCoords uv = new TextureAtlas.TextureCoords(slot);
                ui.drawTexture(atlas.getTexture().getId(), x, y, size, size,
                    uv.u1, uv.v2, uv.u2, uv.v1, 0xFFFFFFFF);
                return;
            }
        }
        ui.useSolidColor();
        ui.fillRect(x, y, size, size, 0xFF9A9A9A);
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        // [UI-017] Book button toggles the recipe list
        if (mx >= bookButtonX && mx < bookButtonX + bookButtonW
            && my >= bookButtonY && my < bookButtonY + bookButtonH) {
            bookOpen = !bookOpen;
            return true;
        }

        // [UI-018] Clicking a recipe fills the grid and shows its output
        if (bookOpen) {
            for (int i = 0; i < BOOK_VISIBLE; i++) {
                int idx = bookScroll + i;
                if (idx >= RecipeRegistry.RECIPES.length) break;
                int ey = bookY + 20 + i * BOOK_ENTRY_H;
                if (mx >= bookX + 4 && mx < bookX + bookW - 4
                    && my >= ey && my < ey + BOOK_ENTRY_H) {
                    applyRecipe(RecipeRegistry.RECIPES[idx]);
                    return true;
                }
            }
        }

        // Grid
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int sx = gridStartX + x * SLOT;
                int sy = gridStartY + y * SLOT;
                if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) {
                    handleGridClick(x, y, button);
                    return true;
                }
            }
        }

        // Output
        if (mx >= outputX && mx < outputX + SLOT && my >= outputY && my < outputY + SLOT) {
            if (button == 0 && !resultSlot.isEmpty() && callbacks.mouseItem().isEmpty()) {
                ItemStack crafted = resultSlot.copy();
                callbacks.onMouseItemChanged(crafted);
                for (ItemStack g : grid) g.remove(1);
                updateResult();
                callbacks.onCraft(crafted);
            }
            return true;
        }
        return false;
    }

    /** [UI-018] Copy a recipe's pattern into the crafting grid. */
    private void applyRecipe(Recipe recipe) {
        for (int i = 0; i < 9; i++) grid[i] = new ItemStack(BlockType.AIR, 0);
        ItemStack[] inputs = recipe.getInputs();
        int w = recipe.getWidth();
        for (int j = 0; j < inputs.length; j++) {
            ItemStack ing = inputs[j];
            if (ing == null || ing.isEmpty()) continue;
            int row = j / w;
            int col = j % w;
            grid[row * 3 + col] = ing.copy();
        }
        updateResult();
    }

    /** [UI-019] Scroll the recipe list with the mouse wheel. */
    @Override
    public boolean mouseScrolled(float mx, float my, double delta) {
        if (!bookOpen) return false;
        bookScroll -= (int) delta;
        int max = Math.max(0, RecipeRegistry.RECIPES.length - BOOK_VISIBLE);
        bookScroll = Math.max(0, Math.min(bookScroll, max));
        return true;
    }

    /** Place one item from the cursor into a cell, or take the cell onto the cursor. */
    private void handleGridClick(int x, int y, int button) {
        ItemStack mouse = callbacks.mouseItem();
        if (button != 0) return;

        if (grid[y * 3 + x].isEmpty() && !mouse.isEmpty()) {
            grid[y * 3 + x] = mouse.copyWithCount(1);
            callbacks.onMouseItemChanged(decrement(mouse));
        } else if (!grid[y * 3 + x].isEmpty() && mouse.isEmpty()) {
            callbacks.onMouseItemChanged(grid[y * 3 + x].copy());
            grid[y * 3 + x] = new ItemStack(BlockType.AIR, 0);
        }
        updateResult();
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

    private ItemStack decrement(ItemStack stack) {
        if (stack.getCount() <= 1) return new ItemStack(BlockType.AIR, 0);
        return stack.copyWithCount(stack.getCount() - 1);
    }

    private Runnable onClosedCallback;
    public void onClose(Runnable cb) { this.onClosedCallback = cb; }

    private void fireClosed() {
        // Return grid contents to the inventory so nothing is lost
        Inventory inv = callbacks.inventory();
        for (ItemStack g : grid) {
            if (g == null || g.isEmpty()) continue;
            if (g.isItem()) {
                inv.addItem(g.getItem(), g.getCount());
            } else {
                inv.addItem(g.getBlockType(), g.getCount());
            }
        }
        if (onClosedCallback != null) onClosedCallback.run();
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            fireClosed();
            return true;
        }
        return false;
    }
}