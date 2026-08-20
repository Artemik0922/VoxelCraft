package com.voxelgame.ui.screen;

import com.voxelgame.item.Inventory;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.ui.FontRenderer;

import static com.voxelgame.core.Language.tr;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.InventoryItemRenderer;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.world.BlockType;

import java.util.ArrayList;
import java.util.List;

/**
 * Creative block picker.
 *
 * The layout is computed once per resize into explicit rectangles so the
 * title, the scrolling grid and the hotbar strip all sit inside the panel
 * and cannot overlap - see {@link #layout()} for the vertical budget.
 *
 * Slots are drawn directly rather than through Slot widgets so the grid can
 * be clipped to the viewport and scrolled by whole pixels.
 */
public class CreativeInventoryScreen extends Screen {

    public interface Callbacks {
        Inventory inventory();
        void onPickBlock(BlockType block);
        void onSelectHotbarSlot(int index);
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
    private final List<BlockType> blocks = new ArrayList<>();

    // Layout rectangles, all in whole GUI pixels
    private int panelX, panelY, panelW, panelH;
    private int gridX, gridY, gridW, gridH;
    private int hotbarX, hotbarY;
    private int scrollX;

    private int visibleRows;
    private int totalRows;
    private int scrollRow = 0;

    private boolean draggingScrollbar = false;
    private BlockType hovered = null;

    public CreativeInventoryScreen(Callbacks callbacks, TextureAtlas atlas) {
        this.callbacks = callbacks;
        this.atlas = atlas;

        for (BlockType t : BlockType.values()) {
            if (t != BlockType.AIR) blocks.add(t);
        }
    }

    @Override
    protected void layout() {
        totalRows = (blocks.size() + COLS - 1) / COLS;

        int contentW = COLS * SLOT;
        int hotbarStripH = SLOT;

        // Vertical budget inside the panel:
        //   pad | title | grid | gap | hotbar | pad
        int fixedH = PANEL_PAD * 2 + TITLE_H + SECTION_GAP + hotbarStripH;
        int availableForGrid = height - 20 - fixedH;   // 20px breathing room

        visibleRows = Math.max(3, availableForGrid / SLOT);
        visibleRows = Math.min(visibleRows, totalRows);

        gridH = visibleRows * SLOT;
        gridW = contentW;

        panelW = PANEL_PAD * 2 + contentW + SCROLLBAR_GAP + SCROLLBAR_W;
        panelH = fixedH + gridH;

        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        gridX = panelX + PANEL_PAD;
        gridY = panelY + PANEL_PAD + TITLE_H;

        scrollX = gridX + gridW + SCROLLBAR_GAP;

        hotbarX = gridX;
        hotbarY = gridY + gridH + SECTION_GAP;

        clampScroll();
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
        ui.fillRect(0, 0, width, height, 0xB0101010);
        ui.drawNineSlice(gui.panel, panelX, panelY, panelW, panelH,
            GuiAssets.BORDER, GuiAssets.WIDGET, 0xFFFFFFFF);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets gui,
                                    float mx, float my) {
        font.draw(ui, tr("container.creative"), gridX, panelY + PANEL_PAD, GuiAssets.TEXT_TITLE);

        hovered = null;
        drawGrid(ui, font, gui, mx, my);
        drawScrollbar(ui, gui, mx, my);
        drawHotbarStrip(ui, font, gui, mx, my);

        if (hovered != null) {
            drawTooltip(ui, font, hovered, mx, my);
        }
    }

    private void drawGrid(UIRenderer ui, FontRenderer font, GuiAssets gui, float mx, float my) {
        for (int row = 0; row < visibleRows; row++) {
            int dataRow = row + scrollRow;

            for (int col = 0; col < COLS; col++) {
                int index = dataRow * COLS + col;
                if (index >= blocks.size()) return;

                int sx = gridX + col * SLOT;
                int sy = gridY + row * SLOT;

                boolean over = inside(mx, my, sx, sy, SLOT, SLOT);
                BlockType block = blocks.get(index);
                if (over) hovered = block;

                ui.drawSprite(over ? gui.slotHover : gui.slot, sx, sy, SLOT, SLOT);
                drawIcon(ui, block, sx + 1, sy + 1);
            }
        }
    }

    /** Bevelled track with a mini-button thumb. */
    private void drawScrollbar(UIRenderer ui, GuiAssets gui, float mx, float my) {
        ui.drawNineSlice(gui.scrollTrack, scrollX, gridY, SCROLLBAR_W, gridH,
            GuiAssets.BORDER, GuiAssets.WIDGET, 0xFFFFFFFF);

        if (maxScrollRow() <= 0) return;

        int thumbH = Math.max(12, gridH * visibleRows / Math.max(1, totalRows));
        int travel = gridH - thumbH;
        int thumbY = gridY + travel * scrollRow / maxScrollRow();

        boolean over = inside(mx, my, scrollX, thumbY, SCROLLBAR_W, thumbH);
        int tex = (over || draggingScrollbar) ? gui.buttonHover : gui.scrollThumb;

        ui.drawNineSlice(tex, scrollX, thumbY, SCROLLBAR_W, thumbH,
            GuiAssets.BORDER, GuiAssets.WIDGET, 0xFFFFFFFF);
    }

    /** Live mirror of the hotbar along the bottom of the panel. */
    private void drawHotbarStrip(UIRenderer ui, FontRenderer font, GuiAssets gui,
                                 float mx, float my) {
        Inventory inv = callbacks.inventory();

        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int sx = hotbarX + i * SLOT;
            boolean over = inside(mx, my, sx, hotbarY, SLOT, SLOT);
            boolean selected = i == inv.getSelectedSlot();

            ui.drawSprite(over ? gui.slotHover : gui.slot, sx, hotbarY, SLOT, SLOT);

            BlockType block = inv.getHotbarItem(i).getBlockType();
            if (block != BlockType.AIR) {
                drawIcon(ui, block, sx + 1, hotbarY + 1);
                if (over) hovered = block;
            }

            if (selected) {
                ui.useSolidColor();
                for (int k = 0; k < 2; k++) {
                    ui.drawRectOutline(sx - 1 - k, hotbarY - 1 - k,
                        SLOT + 2 + k * 2, SLOT + 2 + k * 2, 0xFFFFFFFF);
                }
            }
        }
    }

    private void drawIcon(UIRenderer ui, BlockType block, int x, int y) {
        TextureAtlas.TextureCoords uv = atlas.getIconCoords(block.id);
        ui.drawTexture(atlas.getTexture().getId(), x, y, ICON, ICON,
            uv.u1, uv.v2, uv.u2, uv.v1, 0xFFFFFFFF);
    }

    /**
     * Tooltip offset from the cursor and clamped so it never leaves the
     * screen or covers the slot it describes.
     */
    private void drawTooltip(UIRenderer ui, FontRenderer font, BlockType block,
                             float mx, float my) {
        String name = prettyName(block);
        int tw = font.width(name);
        int boxW = tw + 6;
        int boxH = FontRenderer.GLYPH_H + 6;

        int tx = (int) mx + 12;
        int ty = (int) my - 12;

        // Flip to the other side of the cursor rather than overlapping it
        if (tx + boxW > width - 2) tx = (int) mx - 12 - boxW;
        if (tx < 2) tx = 2;
        if (ty < 2) ty = 2;
        if (ty + boxH > height - 2) ty = height - 2 - boxH;

        ui.useSolidColor();
        ui.fillRect(tx, ty, boxW, boxH, GuiAssets.TOOLTIP_BG);
        ui.drawRectOutline(tx, ty, boxW, boxH, GuiAssets.TOOLTIP_EDGE);

        font.drawWithShadow(ui, name, tx + 3, ty + 3, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        // Scrollbar thumb
        if (maxScrollRow() > 0 && inside(mx, my, scrollX, gridY, SCROLLBAR_W, gridH)) {
            draggingScrollbar = true;
            scrollToMouse(my);
            return true;
        }

        // Grid
        if (inside(mx, my, gridX, gridY, gridW, gridH)) {
            int col = (int) ((mx - gridX) / SLOT);
            int row = (int) ((my - gridY) / SLOT);
            int index = (row + scrollRow) * COLS + col;

            if (col >= 0 && col < COLS && index >= 0 && index < blocks.size()) {
                callbacks.onPickBlock(blocks.get(index));
            }
            return true;
        }

        // Hotbar strip
        if (inside(mx, my, hotbarX, hotbarY, Inventory.HOTBAR_SIZE * SLOT, SLOT)) {
            int i = (int) ((mx - hotbarX) / SLOT);
            if (i >= 0 && i < Inventory.HOTBAR_SIZE) {
                callbacks.onSelectHotbarSlot(i);
            }
            return true;
        }

        return false;
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
        scrollRow -= (int) Math.signum(delta);
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
            callbacks.onSelectHotbarSlot(key - org.lwjgl.glfw.GLFW.GLFW_KEY_1);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------

    private static boolean inside(float mx, float my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String prettyName(BlockType type) {
        // Try localized name first
        String localized = com.voxelgame.core.Language.tr("block." + type.name);
        if (localized != null && !localized.equals("block." + type.name)) {
            return localized;
        }
        // Fallback to formatted English name
        String[] parts = type.name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
