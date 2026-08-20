package com.voxelgame.ui.screen;

import com.voxelgame.item.Inventory;
import com.voxelgame.item.ItemStack;
import com.voxelgame.item.Recipe;
import com.voxelgame.item.RecipeRegistry;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.InventoryItemRenderer;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;
import com.voxelgame.world.BlockType;

import static com.voxelgame.core.Language.tr;

/**
 * Survival inventory: 27 storage slots above a hotbar strip, with the held
 * mouse item following the cursor. Left click moves a whole stack; right
 * click moves one item.
 */
public class SurvivalInventoryScreen extends Screen {

    public interface Callbacks {
        Inventory inventory();
        ItemStack mouseItem();
        void onMouseItemChanged(ItemStack stack);
        void onSelectHotbarSlot(int index);
        /** [UI-016] The persistent 2x2 crafting grid (row-major, 4 cells). */
        ItemStack[] craftingGrid();
        /** Fired when the player takes a crafted result out of the grid. */
        void onCraft(ItemStack result);
    }

    private static final int SLOT = GuiAssets.SLOT_SIZE;
    private static final int ICON = GuiAssets.ICON_SIZE;
    private static final int COLS = 9;

    private static final int PANEL_PAD = 7;
    private static final int TITLE_H = 12;
    private static final int SECTION_GAP = 4;
    private static final int CRAFT_GAP = 16;

    private final Callbacks callbacks;
    private final TextureAtlas atlas;

    private int panelX, panelY, panelW, panelH;
    private int storageX, storageY;
    private int hotbarX, hotbarY;
    private int craftX, craftY, resultX, resultY;

    /** [UI-016] Result of the 2x2 grid, recomputed after every grid change. */
    private ItemStack resultSlot = new ItemStack(BlockType.AIR, 0);

    public SurvivalInventoryScreen(Callbacks callbacks, TextureAtlas atlas) {
        this.callbacks = callbacks;
        this.atlas = atlas;
    }

    @Override
    public boolean rendersWorld() { return false; }

    @Override
    protected void layout() {
        int contentW = COLS * SLOT;
        int storageRows = Inventory.MAIN_INVENTORY_SIZE / COLS;
        int storageH = storageRows * SLOT;
        int hotbarH = SLOT;

        // [UI-016] Crafting area sits to the right of the storage grid:
        // 2x2 grid + arrow + result slot.
        int craftW = 2 * SLOT + CRAFT_GAP + SLOT + CRAFT_GAP + SLOT;

        int fixedH = PANEL_PAD * 2 + TITLE_H + SECTION_GAP * 2 + storageH + hotbarH;
        panelW = PANEL_PAD * 2 + contentW + CRAFT_GAP + craftW;
        panelH = fixedH;

        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        storageX = panelX + PANEL_PAD;
        storageY = panelY + PANEL_PAD + TITLE_H;

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
        if (onClosedCallback != null) onClosedCallback.run();
    }

    private Runnable onClosedCallback;
    public void onClose(Runnable cb) { this.onClosedCallback = cb; }

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets gui) {
        ui.fillRect(0, 0, width, height, 0xB0101010);
        ui.drawNineSlice(gui.panel, panelX, panelY, panelW, panelH,
            GuiAssets.BORDER, GuiAssets.WIDGET, 0xFFFFFFFF);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets gui,
                                    float mx, float my) {
        font.draw(ui, tr("container.survival"), storageX, panelY + PANEL_PAD,
            GuiAssets.TEXT_TITLE);

        Inventory inv = callbacks.inventory();
        ItemStack mouse = callbacks.mouseItem();
        ItemStack hoveredStack = null;

        // 27 storage slots
        for (int i = 0; i < Inventory.MAIN_INVENTORY_SIZE; i++) {
            int row = i / COLS;
            int col = i % COLS;
            int sx = storageX + col * SLOT;
            int sy = storageY + row * SLOT;

            ui.drawSprite(gui.slot, sx, sy, SLOT, SLOT);
            ItemStack stack = inv.getInventoryItem(i);
            if (!stack.isEmpty()) {
                drawIcon(ui, stack.getBlockType(), sx + 1, sy + 1);
                drawCount(ui, font, stack.getCount(), sx, sy);
                // [UI-021] Durability bar for tools
                drawDurabilityBar(ui, stack, sx, sy);
                // Track hovered item for tooltip
                if (inside(mx, my, sx, sy, SLOT, SLOT)) {
                    hoveredStack = stack;
                }
            }
        }

        // Hotbar strip
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int sx = hotbarX + i * SLOT;
            ui.drawSprite(gui.slot, sx, hotbarY, SLOT, SLOT);
            ItemStack stack = inv.getHotbarItem(i);
            if (!stack.isEmpty()) {
                drawIcon(ui, stack.getBlockType(), sx + 1, hotbarY + 1);
                drawCount(ui, font, stack.getCount(), sx, hotbarY);
                // [UI-021] Durability bar for tools
                drawDurabilityBar(ui, stack, sx, hotbarY);
                // Track hovered item for tooltip
                if (inside(mx, my, sx, hotbarY, SLOT, SLOT)) {
                    hoveredStack = stack;
                }
            }
            if (i == inv.getSelectedSlot()) {
                ui.useSolidColor();
                for (int k = 0; k < 2; k++) {
                    ui.drawRectOutline(sx - 1 - k, hotbarY - 1 - k,
                        SLOT + 2 + k * 2, SLOT + 2 + k * 2, 0xFFFFFFFF);
                }
            }
        }

        // [UI-016] Crafting area: 2x2 grid + arrow + result preview
        ItemStack[] grid = callbacks.craftingGrid();
        for (int i = 0; i < 4; i++) {
            int sx = craftX + (i % 2) * SLOT;
            int sy = craftY + (i / 2) * SLOT;
            ui.drawSprite(gui.slot, sx, sy, SLOT, SLOT);
            ItemStack stack = grid[i];
            if (stack != null && !stack.isEmpty()) {
                BlockType icon = iconBlock(stack);
                if (icon != null) drawIcon(ui, icon, sx + 1, sy + 1);
                if (inside(mx, my, sx, sy, SLOT, SLOT)) {
                    hoveredStack = stack;
                }
            }
        }

        // Arrow
        font.draw(ui, "→", craftX + 2 * SLOT + CRAFT_GAP / 2 - 3, resultY + 6,
            GuiAssets.TEXT_HINT);

        // Result slot: preview only, crafted on click
        ui.drawSprite(gui.slot, resultX, resultY, SLOT, SLOT);
        if (!resultSlot.isEmpty()) {
            BlockType icon = iconBlock(resultSlot);
            if (icon != null) drawIcon(ui, icon, resultX + 1, resultY + 1);
            drawCount(ui, font, resultSlot.getCount(), resultX, resultY);
            if (inside(mx, my, resultX, resultY, SLOT, SLOT)) {
                hoveredStack = resultSlot;
            }
        }

        // Mouse item follows cursor
        if (!mouse.isEmpty()) {
            drawIcon(ui, mouse.getBlockType(), (int) mx - ICON / 2, (int) my - ICON / 2);
            if (mouse.getCount() > 1) {
                drawCount(ui, font, mouse.getCount(), (int) mx - ICON / 2, (int) my - ICON / 2);
            }
        }
        
        // [UI-015] Draw tooltip for hovered item
        if (hoveredStack != null && mouse.isEmpty()) {
            drawTooltip(ui, font, hoveredStack, (int) mx, (int) my);
        }
    }
    
    /**
     * [UI-021] Draw a durability bar under tool items. Green when high, yellow medium,
     * red when low. Bar is 14px wide, 2px tall, positioned at the bottom of the slot.
     */
    private void drawDurabilityBar(UIRenderer ui, ItemStack stack, int sx, int sy) {
        int maxDur = stack.getMaxDurability();
        if (maxDur <= 0) return;
        
        int dur = stack.getDurability();
        float fraction = 1.0f - (float) dur / maxDur;
        if (fraction >= 1.0f) return; // Full durability, no bar needed
        
        int barW = 14;
        int filled = (int) (barW * fraction);
        int barX = sx + 2;
        int barY = sy + SLOT - 3;
        
        // Background
        ui.useSolidColor();
        ui.drawRectOutline(barX, barY, barW, 2, 0xFF000000);
        // Fill color based on remaining durability
        int color = fraction > 0.6f ? 0xFF55FF55 : (fraction > 0.3f ? 0xFFFFFF55 : 0xFFFF5555);
        if (filled > 0) {
            ui.drawRectOutline(barX, barY, filled, 2, color);
        }
    }
    
    /**
     * [UI-015] Draw a tooltip with item name, durability info, and description.
     * Positioned near the cursor, clamped to screen bounds.
     */
    private void drawTooltip(UIRenderer ui, FontRenderer font, ItemStack stack, int mx, int my) {
        if (stack.isEmpty()) return;
        
        // Build tooltip lines
        java.util.List<String> lines = new java.util.ArrayList<>();
        
        // Item name (localized or formatted)
        String name = getLocalizedName(stack);
        lines.add(name);
        
        // Durability info for tools
        int maxDur = stack.getMaxDurability();
        if (maxDur > 0) {
            int dur = stack.getDurability(); // dur = remaining durability
            int damageTaken = maxDur - dur;
            if (damageTaken > 0) {
                lines.add("Durability: " + dur + "/" + maxDur);
            }
        }
        
        // Count for stackable items
        if (stack.getCount() > 1) {
            // Already shown on the icon, skip
        }
        
        if (lines.isEmpty()) return;
        
        // Calculate tooltip dimensions
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }
        int lineHeight = FontRenderer.LINE_HEIGHT;
        int tooltipW = maxWidth + 8;
        int tooltipH = lines.size() * lineHeight + 6;
        
        // Position tooltip near cursor, clamped to screen
        int tx = mx + 12;
        int ty = my - tooltipH - 4;
        if (tx + tooltipW > ui.getWidth()) tx = mx - tooltipW - 4;
        if (ty < 0) ty = my + 16;
        if (tx < 0) tx = 4;
        
        // Draw background
        ui.useSolidColor();
        ui.fillRect(tx, ty, tooltipW, tooltipH, 0xE0100010); // Dark background
        ui.drawRectOutline(tx, ty, tooltipW, tooltipH, 0xFF500070); // Purple border
        
        // Draw text lines
        int textY = ty + 3;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFFFFF : 0xAAAAAA; // First line white, rest grey
            if (i == 0) color = 0xFFFFFFFF;
            else if (lines.get(i).startsWith("Durability")) {
                // Color durability based on remaining
                int maxDur2 = stack.getMaxDurability();
                if (maxDur2 > 0) {
                    float fraction = 1.0f - (float) stack.getDurability() / maxDur2;
                    color = fraction > 0.6f ? 0xFF55FF55 : (fraction > 0.3f ? 0xFFFFFF55 : 0xFFFF5555);
                }
            } else {
                color = 0xFFAAAAAA;
            }
            font.drawWithShadow(ui, lines.get(i), tx + 4, textY, color);
            textY += lineHeight;
        }
    }
    
    /** Get localized or formatted name for an item stack. */
    private String getLocalizedName(ItemStack stack) {
        if (stack.isBlock()) {
            String key = "block." + stack.getBlockType().name;
            String localized = com.voxelgame.core.Language.tr(key);
            if (localized != null && !localized.equals(key)) return localized;
            // Format block name
            String[] parts = stack.getBlockType().name.split("_");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (p.isEmpty()) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
            return sb.toString();
        }
        if (stack.isItem() && stack.getItem() != null) {
            String key = "item." + stack.getItem().name;
            String localized = com.voxelgame.core.Language.tr(key);
            if (localized != null && !localized.equals(key)) return localized;
            return stack.getItem().name;
        }
        return "Unknown";
    }

    private void drawIcon(UIRenderer ui, BlockType block, int x, int y) {
        TextureAtlas.TextureCoords uv = atlas.getCoords(block.id, 2);
        ui.drawTexture(atlas.getTexture().getId(), x, y, ICON, ICON,
            uv.u1, uv.v2, uv.u2, uv.v1, 0xFFFFFFFF);
    }

    /** Block used to draw a stack's icon, or null when it maps to none. */
    private BlockType iconBlock(ItemStack stack) {
        if (stack == null) return null;
        if (stack.isBlock()) return stack.getBlockType();
        if (stack.isItem() && stack.getItem().blockType != null) {
            return stack.getItem().blockType;
        }
        return null;
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

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        Inventory inv = callbacks.inventory();
        ItemStack mouse = callbacks.mouseItem();

        // [UI-016] Crafting result: take the preview, consume the grid
        if (inside(mx, my, resultX, resultY, SLOT, SLOT)) {
            if (button == 0 && !resultSlot.isEmpty() && mouse.isEmpty()) {
                ItemStack crafted = resultSlot.copy();
                callbacks.onMouseItemChanged(crafted);
                ItemStack[] grid = callbacks.craftingGrid();
                for (ItemStack g : grid) g.remove(1);
                updateResult();
                callbacks.onCraft(crafted);
            }
            return true;
        }

        // [UI-016] Crafting grid: place one from the cursor, or pick up
        for (int i = 0; i < 4; i++) {
            int sx = craftX + (i % 2) * SLOT;
            int sy = craftY + (i / 2) * SLOT;
            if (inside(mx, my, sx, sy, SLOT, SLOT)) {
                handleGridClick(i, button);
                return true;
            }
        }

        ItemStack result;

        // Hotbar strip
        if (inside(mx, my, hotbarX, hotbarY, Inventory.HOTBAR_SIZE * SLOT, SLOT)) {
            int slot = (int) ((mx - hotbarX) / SLOT);
            if (slot >= 0 && slot < Inventory.HOTBAR_SIZE) {
                if (button == 0) {
                    // Left click: swap mouse with slot
                    result = inv.getHotbarItem(slot);
                    inv.setHotbarItem(slot, mouse.getBlockType(), mouse.getCount());
                    callbacks.onMouseItemChanged(result);
                } else {
                    // Right click: place one, or pick up half
                    if (mouse.isEmpty()) {
                        ItemStack slotStack = inv.getHotbarItem(slot);
                        if (!slotStack.isEmpty()) {
                            int half = (slotStack.getCount() + 1) / 2;
                            int remain = slotStack.getCount() - half;
                            inv.setHotbarItem(slot, slotStack.getBlockType(), remain);
                            callbacks.onMouseItemChanged(new ItemStack(slotStack.getBlockType(), half));
                        }
                    } else {
                        // Try to add one to slot
                        ItemStack slotStack = inv.getHotbarItem(slot);
                        if (slotStack.isEmpty()) {
                            inv.setHotbarItem(slot, mouse.getBlockType(), 1);
                            callbacks.onMouseItemChanged(decrement(mouse));
                        } else if (slotStack.getBlockType() == mouse.getBlockType()
                                && slotStack.getCount() < 64) {
                            inv.setHotbarItem(slot, mouse.getBlockType(), slotStack.getCount() + 1);
                            callbacks.onMouseItemChanged(decrement(mouse));
                        }
                    }
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
                if (button == 0) {
                    result = inv.getInventoryItem(slot);
                    inv.setInventoryItem(slot, mouse.getBlockType(), mouse.getCount());
                    callbacks.onMouseItemChanged(result);
                } else {
                    if (mouse.isEmpty()) {
                        ItemStack slotStack = inv.getInventoryItem(slot);
                        if (!slotStack.isEmpty()) {
                            int half = (slotStack.getCount() + 1) / 2;
                            int remain = slotStack.getCount() - half;
                            inv.setInventoryItem(slot, slotStack.getBlockType(), remain);
                            callbacks.onMouseItemChanged(new ItemStack(slotStack.getBlockType(), half));
                        }
                    } else {
                        ItemStack slotStack = inv.getInventoryItem(slot);
                        if (slotStack.isEmpty()) {
                            inv.setInventoryItem(slot, mouse.getBlockType(), 1);
                            callbacks.onMouseItemChanged(decrement(mouse));
                        } else if (slotStack.getBlockType() == mouse.getBlockType()
                                && slotStack.getCount() < 64) {
                            inv.setInventoryItem(slot, mouse.getBlockType(), slotStack.getCount() + 1);
                            callbacks.onMouseItemChanged(decrement(mouse));
                        }
                    }
                }
            }
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    /** [UI-016] Place one item from the cursor into a grid cell, or take it back. */
    private void handleGridClick(int index, int button) {
        ItemStack[] grid = callbacks.craftingGrid();
        ItemStack mouse = callbacks.mouseItem();
        if (button != 0) return;

        if (grid[index].isEmpty() && !mouse.isEmpty()) {
            grid[index] = mouse.copy();
            grid[index].setCount(1);
            callbacks.onMouseItemChanged(decrement(mouse));
        } else if (!grid[index].isEmpty() && mouse.isEmpty()) {
            callbacks.onMouseItemChanged(grid[index].copy());
            grid[index] = new ItemStack(BlockType.AIR, 0);
        }
        updateResult();
    }

    /** [UI-016] Recompute the result preview from the 2x2 grid. */
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

    private ItemStack decrement(ItemStack stack) {
        if (stack.getCount() <= 1) return new ItemStack(BlockType.AIR, 0);
        return new ItemStack(stack.getBlockType(), stack.getCount() - 1);
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
            callbacks.onSelectHotbarSlot(key - org.lwjgl.glfw.GLFW.GLFW_KEY_1);
            return true;
        }
        return false;
    }

    private boolean inside(float mx, float my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
