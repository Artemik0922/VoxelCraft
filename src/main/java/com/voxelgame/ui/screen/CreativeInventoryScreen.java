package com.voxelgame.ui.screen;

import com.voxelgame.core.Game;
import com.voxelgame.item.Inventory;
import com.voxelgame.item.ItemRegistry;
import com.voxelgame.item.ItemStack;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GlassTooltip;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.StackIcons;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.TextField;
import com.voxelgame.world.BlockType;

import static com.voxelgame.core.Language.tr;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Creative inventory: every block and item in one searchable, scrollable
 * grid above a live hotbar mirror.
 *
 * Vanilla cursor semantics: clicking a palette entry puts a full stack on
 * the cursor (one with right click), shift-click sends it straight into the
 * selected hotbar slot, clicking a hotbar slot with the cursor overwrites it
 * (empty cursor selects), Q clears a hotbar slot and clicking outside the
 * panel destroys the cursor stack - creative deletes instead of dropping.
 */
public class CreativeInventoryScreen extends Screen {

    public interface Callbacks {
        Inventory inventory();
        void onPickBlock(BlockType block);
        void onSelectHotbarSlot(int index);

        /** Cursor stack carried between clicks (creative destroys on close). */
        default ItemStack mouseItem() { return new ItemStack(BlockType.AIR, 0); }
        default void onMouseItemChanged(ItemStack stack) {}
    }

    private static final int SLOT = GuiAssets.SLOT_SIZE;   // 18
    private static final int ICON = GuiAssets.ICON_SIZE;   // 16
    private static final int COLS = 9;

    private static final int PANEL_PAD = 7;    // panel edge -> content
    private static final int TITLE_H = 12;     // title line height
    private static final int SECTION_GAP = 6;  // grid -> hotbar
    private static final int SCROLLBAR_W = 8;
    private static final int SCROLLBAR_GAP = 3;

    private final Callbacks callbacks;
    private final TextureAtlas atlas;
    private final List<ItemStack> palette = new ArrayList<>();
    private final List<ItemStack> filtered = new ArrayList<>();

    // Layout rectangles, all in whole GUI pixels
    private int panelX, panelY, panelW, panelH;
    private int gridX, gridY, gridW, gridH;
    private int hotbarX, hotbarY;
    private int scrollX;
    private int searchX, searchY, searchW, searchH;

    private int visibleRows;
    private int totalRows;
    private int scrollRow = 0;

    private boolean draggingScrollbar = false;
    private ItemStack hovered = null;
    private final TextField searchField = new TextField(0, 0, 10, 12, 32);

    public CreativeInventoryScreen(Callbacks callbacks, TextureAtlas atlas) {
        this.callbacks = callbacks;
        this.atlas = atlas;

        for (BlockType t : BlockType.values()) {
            // Sprite-only item pseudo-blocks duplicate the real palette
            // entries and come back as placeless block stacks
            if (t == BlockType.AIR || t.isItemSprite()) continue;
            palette.add(new ItemStack(t, 1));
        }
        for (com.voxelgame.item.Item item : ItemRegistry.all()) {
            palette.add(new ItemStack(item, 1));
        }
    }

    @Override
    public boolean usesBlurredBackdrop() { return true; }

    @Override
    protected void layout() {
        rebuildFiltered();

        int contentW = COLS * SLOT;
        int hotbarStripH = SLOT;

        // Vertical budget inside the panel:
        //   pad | title | search | grid | gap | hotbar | pad
        int fixedH = PANEL_PAD * 2 + TITLE_H + searchH + 4 + SECTION_GAP + hotbarStripH;
        int availableForGrid = height - 20 - fixedH;

        visibleRows = Math.max(3, availableForGrid / SLOT);
        visibleRows = Math.min(visibleRows, totalRows);

        gridH = visibleRows * SLOT;
        gridW = contentW;

        panelW = PANEL_PAD * 2 + contentW + SCROLLBAR_GAP + SCROLLBAR_W;
        panelH = fixedH + gridH;

        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        searchW = contentW;
        searchH = 14;
        searchX = panelX + PANEL_PAD;
        searchY = panelY + PANEL_PAD + TITLE_H;

        gridX = searchX;
        gridY = searchY + searchH + 4;

        scrollX = gridX + gridW + SCROLLBAR_GAP;

        hotbarX = gridX;
        hotbarY = gridY + gridH + SECTION_GAP;

        searchField.x = searchX;
        searchField.y = searchY;
        searchField.width = searchW;
        searchField.height = searchH;

        clampScroll();
    }

    private void rebuildFiltered() {
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (ItemStack stack : palette) {
            if (query.isEmpty()
                    || StackIcons.displayName(stack).toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(stack);
            }
        }
        totalRows = (filtered.size() + COLS - 1) / COLS;
    }

    private int maxScrollRow() {
        return Math.max(0, totalRows - visibleRows);
    }

    private void clampScroll() {
        scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow));
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
    public void update(double deltaTime) {
        super.update(deltaTime);
        searchField.update(deltaTime);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets gui,
                                    float mx, float my) {
        lastMx = mx;
        lastMy = my;

        font.draw(ui, tr("container.creative"), gridX, panelY + PANEL_PAD, 0xFFF2E6C8);

        hovered = null;
        drawSearchField(ui, font, gui, mx, my);
        drawGrid(ui, font, gui, mx, my);
        drawScrollbar(ui, gui, mx, my);
        drawHotbarStrip(ui, font, gui, mx, my);

        // Cursor stack follows the mouse
        ItemStack mouse = callbacks.mouseItem();
        if (!mouse.isEmpty()) {
            StackIcons.drawStack(ui, font, atlas, mouse,
                (int) mx - ICON / 2, (int) my - ICON / 2);
        }

        if (hovered != null && mouse.isEmpty()) {
            StackIcons.drawTooltip(ui, font, atlas, hovered, mx, my, width, height);
        }
    }

    private void drawSearchField(UIRenderer ui, FontRenderer font, GuiAssets gui,
                                 float mx, float my) {
        searchField.render(ui, font, gui, mx, my);
        if (searchField.getText().isEmpty() && !searchField.isFocused()) {
            font.draw(ui, tr("creative.search"), searchX + 6,
                searchY + (searchH - FontRenderer.GLYPH_H) / 2, 0xFF6B7488);
        }
    }

    private void drawGrid(UIRenderer ui, FontRenderer font, GuiAssets gui, float mx, float my) {
        for (int row = 0; row < visibleRows; row++) {
            int dataRow = row + scrollRow;

            for (int col = 0; col < COLS; col++) {
                int index = dataRow * COLS + col;
                if (index >= filtered.size()) return;

                int sx = gridX + col * SLOT;
                int sy = gridY + row * SLOT;

                boolean over = inside(mx, my, sx, sy, SLOT, SLOT);
                ItemStack stack = filtered.get(index);
                if (over) hovered = stack;

                ui.drawNineSlice(gui.glassSlot,
                    sx, sy, SLOT, SLOT, 3, GuiAssets.SLOT_SIZE, over ? 0xFF9A7D4C : 0xFF3A2A1A);
                StackIcons.drawIcon(ui, atlas, stack, sx + 1, sy + 1, ICON);
            }
        }
    }

    /** Recessed glass track with a rounded glass thumb. */
    private void drawScrollbar(UIRenderer ui, GuiAssets gui, float mx, float my) {
        ui.drawNineSlice(gui.glassTrack, scrollX, gridY, SCROLLBAR_W, gridH,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF26180E);

        if (maxScrollRow() <= 0) return;

        int thumbH = Math.max(12, gridH * visibleRows / Math.max(1, totalRows));
        int travel = gridH - thumbH;
        int thumbY = gridY + travel * scrollRow / maxScrollRow();

        boolean over = inside(mx, my, scrollX, thumbY, SCROLLBAR_W, thumbH);
        int tint = (over || draggingScrollbar) ? MenuTheme.ACCENT : 0xFF8EA2C2;

        ui.drawNineSlice(gui.glassPanel, scrollX, thumbY, SCROLLBAR_W, thumbH,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, tint);
    }

    /** Live mirror of the hotbar along the bottom of the panel. */
    private void drawHotbarStrip(UIRenderer ui, FontRenderer font, GuiAssets gui,
                                 float mx, float my) {
        Inventory inv = callbacks.inventory();

        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int sx = hotbarX + i * SLOT;
            boolean over = inside(mx, my, sx, hotbarY, SLOT, SLOT);
            boolean selected = i == inv.getSelectedSlot();

            ui.drawNineSlice(gui.glassSlot,
                sx, hotbarY, SLOT, SLOT, 3, GuiAssets.SLOT_SIZE, over ? 0xFF9A7D4C : 0xFF3A2A1A);

            ItemStack stack = inv.getHotbarItem(i);
            if (!stack.isEmpty()) {
                StackIcons.drawStack(ui, font, atlas, stack, sx + 1, hotbarY + 1);
                if (over) hovered = stack;
            }

            if (selected) {
                ui.useSolidColor();
                ui.fillRect(sx - 1, hotbarY - 1, SLOT + 2, 1, MenuTheme.ACCENT);
                ui.fillRect(sx - 1, hotbarY + SLOT, SLOT + 2, 1, MenuTheme.ACCENT);
                ui.fillRect(sx - 1, hotbarY, 1, SLOT, MenuTheme.ACCENT);
                ui.fillRect(sx + SLOT, hotbarY, 1, SLOT, MenuTheme.ACCENT);
            }
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        // Search field grabs clicks first
        if (searchField.contains(mx, my)) {
            searchField.setFocused(true);
            return true;
        }
        searchField.setFocused(false);

        // Scrollbar thumb
        if (maxScrollRow() > 0 && inside(mx, my, scrollX, gridY, SCROLLBAR_W, gridH)) {
            draggingScrollbar = true;
            scrollToMouse(my);
            return true;
        }

        ItemStack mouse = callbacks.mouseItem();

        // Palette grid
        if (inside(mx, my, gridX, gridY, gridW, gridH)) {
            int col = (int) ((mx - gridX) / SLOT);
            int row = (int) ((my - gridY) / SLOT);
            int index = (row + scrollRow) * COLS + col;

            if (col >= 0 && col < COLS && index >= 0 && index < filtered.size()) {
                ItemStack proto = filtered.get(index);
                if (Game.isShiftDown()) {
                    // Straight into the selected hotbar slot
                    callbacks.inventory().setHotbarItem(
                        callbacks.inventory().getSelectedSlot(),
                        proto.copyWithCount(proto.getMaxStackSize()));
                } else if (button == 0) {
                    callbacks.onMouseItemChanged(proto.copyWithCount(proto.getMaxStackSize()));
                } else {
                    callbacks.onMouseItemChanged(proto.copyWithCount(1));
                }
            }
            return true;
        }

        // Hotbar strip
        if (inside(mx, my, hotbarX, hotbarY, Inventory.HOTBAR_SIZE * SLOT, SLOT)) {
            int i = (int) ((mx - hotbarX) / SLOT);
            if (i >= 0 && i < Inventory.HOTBAR_SIZE) {
                if (!mouse.isEmpty()) {
                    // Creative overwrite with whatever is on the cursor
                    callbacks.inventory().setHotbarItem(i, mouse.copy());
                } else if (button == 2) {
                    // Middle click: clear the slot
                    callbacks.inventory().setHotbarItem(i, new ItemStack(BlockType.AIR, 0));
                } else {
                    callbacks.onSelectHotbarSlot(i);
                }
            }
            return true;
        }

        // Clicking outside the panel destroys the cursor stack (creative)
        if (!mouse.isEmpty() && !insidePanel(mx, my)) {
            callbacks.onMouseItemChanged(new ItemStack(BlockType.AIR, 0));
            return true;
        }

        return false;
    }

    private boolean insidePanel(float mx, float my) {
        return mx >= panelX && mx < panelX + panelW && my >= panelY && my < panelY + panelH;
    }

    @Override
    public boolean mouseDragged(float mx, float my, int button) {
        if (!draggingScrollbar) return false;
        scrollToMouse(my);
        return true;
    }

    @Override
    public void mouseReleased(float mx, float my, int button) {
        draggingScrollbar = false;
    }

    private void scrollToMouse(float my) {
        if (maxScrollRow() <= 0) return;
        float t = (my - gridY) / (float) gridH;
        scrollRow = Math.round(t * maxScrollRow());
        clampScroll();
    }

    @Override
    public boolean mouseScrolled(float mx, float my, double delta) {
        if (searchField.isFocused()) return false;
        scrollRow -= (int) Math.signum(delta);
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (searchField.isFocused() && searchField.keyPressed(key, mods)) {
            layout();
            return true;
        }

        if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
            callbacks.onSelectHotbarSlot(key - org.lwjgl.glfw.GLFW.GLFW_KEY_1);
            return true;
        }

        // Q clears the hotbar slot under the cursor (creative delete)
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_Q) {
            Inventory inv = callbacks.inventory();
            int hotbarSlot = hotbarSlotAt();
            if (hotbarSlot >= 0) {
                inv.setHotbarItem(hotbarSlot, new ItemStack(BlockType.AIR, 0));
                return true;
            }
        }
        return false;
    }

    /** Typed characters land in the search field (routed by Game). */
    public boolean charTyped(char c) {
        if (searchField.charTyped(c)) {
            layout();
            return true;
        }
        return false;
    }

    private int hotbarSlotAt() {
        float mx = lastMx, my = lastMy;
        if (inside(mx, my, hotbarX, hotbarY, Inventory.HOTBAR_SIZE * SLOT, SLOT)) {
            int i = (int) ((mx - hotbarX) / SLOT);
            if (i >= 0 && i < Inventory.HOTBAR_SIZE) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------

    private Runnable onClosedCallback;

    /** Registers a callback fired when the screen closes for any reason. */
    public void onClose(Runnable cb) { this.onClosedCallback = cb; }

    @Override
    public void onClosed() {
        if (onClosedCallback != null) {
            Runnable cb = onClosedCallback;
            onClosedCallback = null;
            cb.run();
        }
    }

    // ------------------------------------------------------------------

    private float lastMx, lastMy;

    private static boolean inside(float mx, float my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
