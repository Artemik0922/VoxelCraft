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
 * Furnace UI: input slot (top-left), fuel slot (bottom-left),
 * fire icon and progress arrow in the middle, output slot (right),
 * and player inventory below.
 *
 * Layout (MC-style):
 *     ┌──────────────────────────────┐
 *     │       Furnace                 │
 *     │                               │
 *     │  [Input]  →→→  [Output]       │
 *     │  [Fuel]  ▲▲                   │
 *     │                               │
 *     │  ─── Player inventory ───     │
 *     │  [ 9 hotbar slots ]           │
 *     └──────────────────────────────┘
 */
public class FurnaceScreen extends Screen {

    public interface Callbacks {
        Inventory inventory();
        ItemStack mouseItem();
        void onMouseItemChanged(ItemStack stack);
        ContainerData container();
    }

    private static final int SLOT = GuiAssets.SLOT_SIZE;
    private static final int ICON = GuiAssets.ICON_SIZE;
    private static final int COLS = 9;

    private static final int PANEL_PAD = 7;
    private static final int TITLE_H = 12;
    private static final int SECTION_GAP = 4;

    // Furnace layout
    private static final int FURNACE_W = SLOT * 5;  // space for slots + arrows
    private static final int FURNACE_H = SLOT * 2 + 8; // two rows of slots + gap

    // Slot positions within furnace area (relative to furnaceX, furnaceY)
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

    // Slot screen positions (computed in layout)
    private int inputSlotX, inputSlotY;
    private int fuelSlotX, fuelSlotY;
    private int outputSlotX, outputSlotY;

    public FurnaceScreen(Callbacks callbacks, TextureAtlas atlas) {
        this.callbacks = callbacks;
        this.atlas = atlas;
    }

    @Override
    public boolean rendersWorld() { return false; }

    @Override
    protected void layout() {
        int contentW = COLS * SLOT;
        int furnaceAreaW = Math.max(FURNACE_W, contentW);
        int storageRows = Inventory.MAIN_INVENTORY_SIZE / COLS;
        int storageH = storageRows * SLOT;
        int hotbarH = SLOT;

        panelW = PANEL_PAD * 2 + Math.max(contentW, furnaceAreaW);
        panelH = PANEL_PAD * 2 + TITLE_H + SECTION_GAP + FURNACE_H
                + SECTION_GAP + storageH + SECTION_GAP + hotbarH + SECTION_GAP;

        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        furnaceX = panelX + (panelW - FURNACE_W) / 2;
        furnaceY = panelY + PANEL_PAD + TITLE_H + SECTION_GAP;

        // Compute absolute slot positions
        inputSlotX = furnaceX + INPUT_DX;
        inputSlotY = furnaceY + INPUT_DY;
        fuelSlotX = furnaceX + FUEL_DX;
        fuelSlotY = furnaceY + FUEL_DY;
        outputSlotX = furnaceX + OUTPUT_DX;
        outputSlotY = furnaceY + OUTPUT_DY;

        // Player inventory centered below
        storageX = panelX + (panelW - contentW) / 2;
        storageY = furnaceY + FURNACE_H + SECTION_GAP * 2;

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
        font.draw(ui, tr("container.furnace"), panelX + PANEL_PAD, panelY + PANEL_PAD,
            GuiAssets.TEXT_TITLE);

        ContainerData container = callbacks.container();
        Inventory inv = callbacks.inventory();
        ItemStack mouse = callbacks.mouseItem();

        // --- Furnace slots ---

        // Input slot (top-left)
        ui.drawSprite(gui.slot, inputSlotX, inputSlotY, SLOT, SLOT);
        ItemStack inputStack = container.getSlot(ContainerData.FURNACE_INPUT);
        if (!inputStack.isEmpty()) {
            drawItem(ui, inputStack, inputSlotX + 1, inputSlotY + 1);
            drawCount(ui, font, inputStack.getCount(), inputSlotX, inputSlotY);
        }

        // Fuel slot (bottom-left)
        ui.drawSprite(gui.slot, fuelSlotX, fuelSlotY, SLOT, SLOT);
        ItemStack fuelStack = container.getSlot(ContainerData.FURNACE_FUEL);
        if (!fuelStack.isEmpty()) {
            drawItem(ui, fuelStack, fuelSlotX + 1, fuelSlotY + 1);
            drawCount(ui, font, fuelStack.getCount(), fuelSlotX, fuelSlotY);
        }

        // Output slot (right)
        ui.drawSprite(gui.slot, outputSlotX, outputSlotY, SLOT, SLOT);
        ItemStack outputStack = container.getSlot(ContainerData.FURNACE_OUTPUT);
        if (!outputStack.isEmpty()) {
            drawItem(ui, outputStack, outputSlotX + 1, outputSlotY + 1);
            drawCount(ui, font, outputStack.getCount(), outputSlotX, outputSlotY);
        }

        // --- Fire icon (fuel burn indicator) ---
        // Positioned between fuel slot and input slot
        int fireX = fuelSlotX + SLOT / 2 - 6;
        int fireY = fuelSlotY - 12;
        if (container.getFuelTime() > 0 && container.getFuelTimeTotal() > 0) {
            int fireH = (int) ((float) container.getFuelTime() / container.getFuelTimeTotal() * 14);
            // Draw fire as orange/red gradient fill
            ui.fillRect(fireX, fireY + (14 - fireH), 12, fireH, 0xFFFF8000);
            // Fire border
            ui.fillRect(fireX - 1, fireY - 1, 14, 1, 0xFF404040);
            ui.fillRect(fireX - 1, fireY + 14, 14, 1, 0xFF404040);
            ui.fillRect(fireX - 1, fireY, 1, 14, 0xFF404040);
            ui.fillRect(fireX + 12, fireY, 1, 14, 0xFF404040);
        } else {
            // Empty fire outline
            ui.fillRect(fireX - 1, fireY - 1, 14, 1, 0xFF404040);
            ui.fillRect(fireX - 1, fireY + 14, 14, 1, 0xFF404040);
            ui.fillRect(fireX - 1, fireY, 1, 14, 0xFF404040);
            ui.fillRect(fireX + 12, fireY, 1, 14, 0xFF404040);
        }

        // --- Progress arrow (smelting indicator) ---
        // Between input and output
        int arrowX = inputSlotX + SLOT + 4;
        int arrowY = inputSlotY + SLOT / 2 - 4;
        // Arrow background (gray)
        ui.fillRect(arrowX, arrowY, 24, 8, 0xFF404040);
        ui.fillRect(arrowX + 24, arrowY + 1, 4, 6, 0xFF404040);
        // Arrow fill (green)
        if (container.canSmelt() && container.getFuelTime() > 0) {
            int progressW = (int) ((float) container.getSmeltTime() / ContainerData.getSmeltDuration() * 24);
            ui.fillRect(arrowX, arrowY, progressW, 8, 0xFF40C040);
        }

        // --- Player inventory ---
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
        } else if (stack.getItem() != null) {
            drawItemIcon(ui, stack, x, y);
        }
    }

    private void drawIcon(UIRenderer ui, BlockType block, int x, int y) {
        TextureAtlas.TextureCoords uv = atlas.getCoords(block.id, 2);
        ui.drawTexture(atlas.getTexture().getId(), x, y, ICON, ICON,
            uv.u1, uv.v2, uv.u2, uv.v1, 0xFFFFFFFF);
    }

    private void drawItemIcon(UIRenderer ui, ItemStack stack, int x, int y) {
        // Use the item's block type if available, otherwise fallback
        if (stack.getItem().blockType != null) {
            drawIcon(ui, stack.getItem().blockType, x, y);
        } else {
            // Generic item icon fallback
            ui.fillRect(x + 2, y + 2, ICON - 4, ICON - 4, 0xFFC0C0C0);
        }
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

        // Input slot
        if (inside(mx, my, inputSlotX, inputSlotY, SLOT, SLOT)) {
            result = callbacks.container().getSlot(ContainerData.FURNACE_INPUT);
            callbacks.container().setSlot(ContainerData.FURNACE_INPUT, mouse);
            callbacks.onMouseItemChanged(result);
            return true;
        }

        // Fuel slot
        if (inside(mx, my, fuelSlotX, fuelSlotY, SLOT, SLOT)) {
            result = callbacks.container().getSlot(ContainerData.FURNACE_FUEL);
            callbacks.container().setSlot(ContainerData.FURNACE_FUEL, mouse);
            callbacks.onMouseItemChanged(result);
            return true;
        }

        // Output slot
        if (inside(mx, my, outputSlotX, outputSlotY, SLOT, SLOT)) {
            result = callbacks.container().getSlot(ContainerData.FURNACE_OUTPUT);
            callbacks.container().setSlot(ContainerData.FURNACE_OUTPUT, mouse);
            callbacks.onMouseItemChanged(result);
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
