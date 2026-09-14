package com.voxelgame.ui.screen;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;
import com.voxelgame.world.save.WorldMeta;
import com.voxelgame.world.save.WorldSave;

import java.util.ArrayList;
import java.util.List;

import static com.voxelgame.core.Language.tr;

/**
 * World list: pick an existing save, or create, delete and re-create one.
 *
 * The list is drawn directly rather than through widgets so rows can be
 * clipped to the viewport and scrolled by whole pixels. Rows now use the
 * shared panel styling and the whole screen has a card-style layout
 * consistent with the main menu.
 */
public class SelectWorldScreen extends Screen {

    public interface Callbacks {
        void onPlay(WorldMeta world);
        void onCreate();
        void onRecreate(WorldMeta world);
        void onCancel();
    }

    private static final int ROW_H = 38;

    private final Callbacks callbacks;
    private List<WorldMeta> worlds = new ArrayList<>();

    private int selected = -1;
    private float scroll = 0;

    private int listX, listY, listW, listH;

    private Button playButton;
    private Button deleteButton;
    private Button recreateButton;

    /** Set while the delete confirmation is showing. */
    private WorldMeta pendingDelete = null;

    public SelectWorldScreen(Callbacks callbacks) {
        this.callbacks = callbacks;
        refresh();
    }

    public void refresh() {
        worlds = WorldSave.listWorlds();
        if (selected >= worlds.size()) selected = worlds.size() - 1;
    }

    @Override
    public boolean rendersWorld() { return false; }

    @Override
    protected void layout() {
        int cardPad = 16;
        int cardW = Math.min(380, width - 40);
        int cardX = (width - cardW) / 2;
        int cardTop = 36;
        int cardH = Math.max(ROW_H * 3, height - 130);

        listX = cardX + cardPad;
        listY = cardTop + cardPad + 32; // title + separator
        listW = cardW - cardPad * 2;
        listH = cardH - cardPad * 2 - 32;

        int bw = (listW - 8) / 2;
        int by = cardTop + cardH + 8;

        playButton = add(new Button(listX, by, bw, 22, tr("selectWorld.select"),
            b -> playSelected()));
        add(new Button(listX + bw + 8, by, bw, 22, tr("selectWorld.create"),
            b -> callbacks.onCreate()));

        int bw3 = (listW - 16) / 3;
        int by2 = by + 28;
        deleteButton = add(new Button(listX, by2, bw3, 22, tr("selectWorld.delete"),
            b -> askDelete()));
        recreateButton = add(new Button(listX + bw3 + 8, by2, bw3, 22,
            tr("selectWorld.recreate"), b -> recreateSelected()));
        add(new Button(listX + (bw3 + 8) * 2, by2, bw3, 22, tr("gui.cancel"),
            b -> callbacks.onCancel()));

        updateButtonState();
    }

    private void updateButtonState() {
        boolean has = selected >= 0 && selected < worlds.size();
        boolean dead = has && worlds.get(selected).dead;
        if (playButton != null) playButton.enabled = has && !dead;
        if (deleteButton != null) deleteButton.enabled = has;
        if (recreateButton != null) recreateButton.enabled = has && !dead;
    }

    private void playSelected() {
        if (selected >= 0 && selected < worlds.size()) {
            callbacks.onPlay(worlds.get(selected));
        }
    }

    private void recreateSelected() {
        if (selected >= 0 && selected < worlds.size()) {
            callbacks.onRecreate(worlds.get(selected));
        }
    }

    private void askDelete() {
        if (selected >= 0 && selected < worlds.size()) {
            pendingDelete = worlds.get(selected);
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets tex) {
        MenuTheme.drawBackdrop(ui, width, height, 0);
        // Draw card BEFORE widgets so it doesn't darken them with its
        // semi-transparent fill (was previously drawn in renderForeground).
        int cardW = Math.min(380, width - 40);
        int cardX = (width - cardW) / 2;
        int cardTop = 36;
        int cardH = Math.max(ROW_H * 3, height - 130);
        MenuTheme.drawCard(ui, 0xFF000000, cardX, cardTop, cardW, cardH);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {
        // Card panel dimensions
        int cardW = Math.min(380, width - 40);
        int cardX = (width - cardW) / 2;
        int cardTop = 36;
        int cardH = Math.max(ROW_H * 3, height - 130);

        // Title
        String title = tr("selectWorld.title");
        int tw = font.scaledWidth(title, 2);
        font.drawScaledWithShadow(ui, title, (width - tw) / 2f, cardTop + 8, 2, 0xFFFFFFFF);
        MenuTheme.drawSeparator(ui, 0xFF000000, width / 2f, cardTop + 30, tw / 2f + 10);

        // List background - rounded glass inset
        ui.drawNineSlice(tex.glassTrack, listX, listY, listW, listH, 4,
            GuiAssets.GLASS_WIDGET, MenuTheme.col(0xFF000000, 255, 0xFF101623));

        if (worlds.isEmpty()) {
            font.drawCenteredWithShadow(ui, tr("selectWorld.empty"),
                width / 2.0f, listY + listH / 2 - 10,
                0xFF000000 | MenuTheme.TEXT_SECONDARY);
            font.drawCenteredWithShadow(ui, tr("selectWorld.hint"),
                width / 2.0f, listY + listH / 2 + 4,
                0xFF000000 | MenuTheme.TEXT_DIM);
        } else {
            drawRows(ui, font, tex, mx, my);
        }

        if (pendingDelete != null) {
            drawDeleteConfirm(ui, font, tex, mx, my);
        }
    }

    private void drawRows(UIRenderer ui, FontRenderer font, GuiAssets tex,
                          float mx, float my) {
        int visible = listH / ROW_H;

        for (int i = 0; i < visible; i++) {
            int index = i + (int) (scroll / ROW_H);
            if (index < 0 || index >= worlds.size()) break;

            WorldMeta w = worlds.get(index);
            int y = listY + i * ROW_H;

            boolean hovered = pendingDelete == null
                && mx >= listX && mx < listX + listW && my >= y && my < y + ROW_H;
            boolean isSelected = index == selected;

            if (isSelected || hovered) {
                MenuTheme.drawPanel(ui, 0xFF000000, listX + 2, y + 1,
                    listW - 4, ROW_H - 2, hovered && !isSelected);
                if (isSelected) {
                    // Amber left-edge indicator
                    ui.fillRect(listX + 3, y + 4, 3, ROW_H - 8,
                        0xFF000000 | MenuTheme.ACCENT);
                }
            }

            // Icon placeholder: a small tinted dirt swatch stands in for a preview
            int iconX = listX + 8;
            int iconY = y + 5;
            int iconS = ROW_H - 10;
            int tint = w.dead ? 0xFF555555 : 0xFFFFFFFF;
            ui.drawTiled(tex.dirt, iconX, iconY, iconS, iconS, 16, tint);
            ui.drawRectOutline(iconX, iconY, iconS, iconS,
                w.dead ? 0x66304058 : 0x66A0B6D6);

            int textX = listX + 8 + iconS + 6;
            int textW = listW - 8 - iconS - 16;

            int textTint = w.dead ? 0xFF777777 : MenuTheme.TEXT_LABEL;
            String name = font.trimToWidth(w.displayName, textW);
            font.drawWithShadow(ui, name, textX, y + 6,
                0xFF000000 | (textTint & 0xFFFFFF));

            String sub = (w.dead ? "\u2620 " : "") + tr(w.gameMode.key) + " \u00B7 "
                + w.formattedLastPlayed();
            font.drawWithShadow(ui, font.trimToWidth(sub, textW),
                textX, y + 18,
                0xFF000000 | MenuTheme.TEXT_SECONDARY);
        }

        // Scrollbar when the list overflows
        int total = worlds.size() * ROW_H;
        if (total > listH) {
            float t = scroll / (float) (total - listH);
            int thumbH = Math.max(16, listH * listH / total);
            int thumbY = listY + (int) ((listH - thumbH) * t);

            ui.fillRect(listX + listW - 6, listY + 1, 4, listH - 2,
                MenuTheme.col(0xFF000000, 90, 0x0A0E18));
            ui.drawNineSlice(tex.glassPanel, listX + listW - 8, thumbY, 7, thumbH,
                GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET,
                0xFF000000 | MenuTheme.ACCENT_LIGHT);
        }
    }

    /** Modal confirmation, so a world is never lost to a stray click. */
    private void drawDeleteConfirm(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                   float mx, float my) {
        ui.fillRect(0, 0, width, height, 0xC0000000);

        int bw = Math.min(320, width - 20);
        int bh = 110;
        int bx = (width - bw) / 2;
        int by = (height - bh) / 2;

        MenuTheme.drawCard(ui, 0xFF000000, bx, by, bw, bh);

        font.drawCenteredWithShadow(ui, tr("selectWorld.deleteQuestion"),
            width / 2.0f, by + 12, 0xFF000000 | MenuTheme.TEXT_BRIGHT);
        font.drawCenteredWithShadow(ui,
            font.trimToWidth(tr("selectWorld.deleteWarning", pendingDelete.displayName), bw - 16),
            width / 2.0f, by + 30, 0xFF000000 | MenuTheme.TEXT_WARNING);

        int cbw = (bw - 24) / 2;
        drawModalButton(ui, font, bx + 8, by + 60, cbw, 22,
            tr("selectWorld.deleteButton"), mx, my, true);
        drawModalButton(ui, font, bx + bw - 8 - cbw, by + 60, cbw, 22,
            tr("gui.cancel"), mx, my, false);
    }

    private void drawModalButton(UIRenderer ui, FontRenderer font,
                                 int x, int y, int w, int h, String label,
                                 float mx, float my, boolean danger) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        MenuTheme.drawPanel(ui, 0xFF000000, x, y, w, h, hover);
        int col = hover ? MenuTheme.TEXT_BRIGHT : MenuTheme.TEXT_LABEL;
        if (danger && hover) col = MenuTheme.TEXT_WARNING;
        font.drawCenteredWithShadow(ui, label, x + w / 2.0f,
            y + (h - FontRenderer.GLYPH_H) / 2.0f,
            0xFF000000 | (col & 0xFFFFFF));
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (pendingDelete != null) {
            handleDeleteModal(mx, my);
            return true;
        }

        // Row selection, with double click to play
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int row = (int) ((my - listY) / ROW_H) + (int) (scroll / ROW_H);
            if (row >= 0 && row < worlds.size()) {
                long now = System.currentTimeMillis();
                if (row == selected && now - lastClickTime < 400) {
                    playSelected();
                } else {
                    selected = row;
                    updateButtonState();
                }
                lastClickTime = now;
            }
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    private long lastClickTime = 0;

    private void handleDeleteModal(float mx, float my) {
        int bw = Math.min(320, width - 20);
        int bh = 110;
        int bx = (width - bw) / 2;
        int by = (height - bh) / 2;
        int cbw = (bw - 24) / 2;

        int y = by + 60;
        if (my < y || my >= y + 22) {
            if (my < by || my >= by + bh) pendingDelete = null;
            return;
        }

        if (mx >= bx + 8 && mx < bx + 8 + cbw) {
            WorldSave.delete(pendingDelete.folderName);
            pendingDelete = null;
            selected = -1;
            refresh();
            updateButtonState();
        } else if (mx >= bx + bw - 8 - cbw && mx < bx + bw - 8) {
            pendingDelete = null;
        }
    }

    @Override
    public boolean mouseScrolled(float mx, float my, double delta) {
        int total = worlds.size() * ROW_H;
        if (total <= listH) return true;

        scroll = Math.max(0, Math.min(total - listH,
            scroll - (float) delta * ROW_H));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (pendingDelete != null && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            pendingDelete = null;
            return true;
        }
        return false;
    }
}
