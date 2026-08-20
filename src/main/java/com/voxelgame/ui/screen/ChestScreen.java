package com.voxelgame.ui.screen;

import com.voxelgame.item.Inventory;
import com.voxelgame.item.ItemStack;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;
import com.voxelgame.world.BlockType;
import com.voxelgame.world.container.ContainerData;

import static com.voxelgame.core.Language.tr;

/**
 * Chest container UI: 27 chest slots + player inventory.
 * Left click swaps, right click splits/places one item.
 */
public class ChestScreen extends Screen {

    public interface Callbacks {
        Inventory inventory();
        ItemStack mouseItem();
        void onMouseItemChanged(ItemStack stack);
        ContainerData container();
    }

    private static final int SLOT = GuiAssets.SLOT_SIZE;
    private static final int ICON = GuiAssets.ICON_SIZE;
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

    public ChestScreen(Callbacks callbacks, TextureAtlas atlas) {
        this.callbacks = callbacks;
        this.atlas = atlas;
    }

    @Override
    public boolean rendersWorld() { return false; }

    @Override
    protected void layout() {
        int contentW = COLS * SLOT;
        // Determine if double chest
        int chestRows = (callbacks.container().size() > 27) ? CHEST_ROWS_DOUBLE : CHEST_ROWS_SINGLE;
        int chestH = chestRows * SLOT;
        int storageRows = Inventory.MAIN_INVENTORY_SIZE / COLS;
        int storageH = storageRows * SLOT;
        int hotbarH = SLOT;

        panelW = PANEL_PAD * 2 + contentW;
        panelH = PANEL_PAD * 2 + TITLE_H + SECTION_GAP + chestH
                + SECTION_GAP + storageH + hotbarH;

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
        font.draw(ui, tr("container.chest"), chestX, panelY + PANEL_PAD,
            GuiAssets.TEXT_TITLE);

        ContainerData container = callbacks.container();
        Inventory inv = callbacks.inventory();
        ItemStack mouse = callbacks.mouseItem();

        // Chest slots (27 or 54 for double chest)
        int chestRows = (container.size() > 27) ? CHEST_ROWS_DOUBLE : CHEST_ROWS_SINGLE;
        for (int i = 0; i < container.size(); i++) {
            int row = i / COLS;
            int col = i % COLS;
            int sx = chestX + col * SLOT;
            int sy = chestY + row * SLOT;

            ui.drawSprite(gui.slot, sx, sy, SLOT, SLOT);
            ItemStack stack = container.getSlot(i);
            if (!stack.isEmpty()) {
                drawItem(ui, stack, sx + 1, sy + 1);
                drawCount(ui, font, stack.getCount(), sx, sy);
            }
        }

        // Player inventory
        for (int i = 0; i < Inventory.MAIN_INVENTORY_SIZE; i++) {
            int row = i / COLS;
            int col = i % COLS;
            int sx = storageX + col * SLOT;
            int sy = storageY + row * SLOT;

            ui.drawSprite(gui.slot, sx, sy, SLOT, SLOT);
            ItemStack stack = inv.getInventoryItem(i);
            if (!stack.isEmpty()) {
                drawItem(ui, stack, sx + 1, sy + 1);
                drawCount(ui, font, stack.getCount(), sx, sy);
            }
        }

        // Hotbar strip
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int sx = hotbarX + i * SLOT;
            ui.drawSprite(gui.slot, sx, hotbarY, SLOT, SLOT);
            ItemStack stack = inv.getHotbarItem(i);
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
        if (stack.isBlock()) {
            drawIcon(ui, stack.getBlockType(), x, y);
        } else {
            // Draw item sprite
            drawIcon(ui, BlockType.STONE, x, y); // Fallback for items
        }
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

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        ItemStack mouse = callbacks.mouseItem();
        ItemStack result;

        // Chest slots
        int chestRows = (callbacks.container().size() > 27) ? CHEST_ROWS_DOUBLE : CHEST_ROWS_SINGLE;
        if (inside(mx, my, chestX, chestY, COLS * SLOT, chestRows * SLOT)) {
            int col = (int) ((mx - chestX) / SLOT);
            int row = (int) ((my - chestY) / SLOT);
            int slot = row * COLS + col;
            if (slot >= 0 && slot < callbacks.container().size()) {
                result = callbacks.container().getSlot(slot);
                callbacks.container().setSlot(slot, mouse);
                callbacks.onMouseItemChanged(result);
            }
            return true;
        }

        // Hotbar strip
        if (inside(mx, my, hotbarX, hotbarY, Inventory.HOTBAR_SIZE * SLOT, SLOT)) {
            int slot = (int) ((mx - hotbarX) / SLOT);
            if (slot >= 0 && slot < Inventory.HOTBAR_SIZE) {
                if (button == 0) {
                    result = callbacks.inventory().getHotbarItem(slot);
                    callbacks.inventory().setHotbarItem(slot, mouse.getBlockType(), mouse.getCount());
                    callbacks.onMouseItemChanged(result);
                } else {
                    // Right click: place one or pick up half
                    if (mouse.isEmpty()) {
                        ItemStack slotStack = callbacks.inventory().getHotbarItem(slot);
                        if (!slotStack.isEmpty()) {
                            int half = (slotStack.getCount() + 1) / 2;
                            int remain = slotStack.getCount() - half;
                            callbacks.inventory().setHotbarItem(slot, slotStack.getBlockType(), remain);
                            callbacks.onMouseItemChanged(new ItemStack(slotStack.getBlockType(), half));
                        }
                    } else {
                        ItemStack slotStack = callbacks.inventory().getHotbarItem(slot);
                        if (slotStack.isEmpty()) {
                            callbacks.inventory().setHotbarItem(slot, mouse.getBlockType(), 1);
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
                    result = callbacks.inventory().getInventoryItem(slot);
                    callbacks.inventory().setInventoryItem(slot, mouse.getBlockType(), mouse.getCount());
                    callbacks.onMouseItemChanged(result);
                } else {
                    if (mouse.isEmpty()) {
                        ItemStack slotStack = callbacks.inventory().getInventoryItem(slot);
                        if (!slotStack.isEmpty()) {
                            int half = (slotStack.getCount() + 1) / 2;
                            int remain = slotStack.getCount() - half;
                            callbacks.inventory().setInventoryItem(slot, slotStack.getBlockType(), remain);
                            callbacks.onMouseItemChanged(new ItemStack(slotStack.getBlockType(), half));
                        }
                    } else {
                        ItemStack slotStack = callbacks.inventory().getInventoryItem(slot);
                        if (slotStack.isEmpty()) {
                            callbacks.inventory().setInventoryItem(slot, mouse.getBlockType(), 1);
                            callbacks.onMouseItemChanged(decrement(mouse));
                        }
                    }
                }
            }
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    private ItemStack decrement(ItemStack stack) {
        if (stack.getCount() <= 1) return new ItemStack(BlockType.AIR, 0);
        return new ItemStack(stack.getBlockType(), stack.getCount() - 1);
    }

    private boolean inside(float mx, float my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
