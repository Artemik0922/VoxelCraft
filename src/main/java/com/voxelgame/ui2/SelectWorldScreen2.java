package com.voxelgame.ui2;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.world.save.WorldMeta;
import com.voxelgame.world.save.WorldSave;

import java.util.ArrayList;
import java.util.List;

import static com.voxelgame.core.Language.tr;

/**
 * World list, Workshop edition — asymmetric bench layout, nothing like the
 * vanilla centred card.
 *
 * LEFT: a tall walnut board with the world labels (paper tags on nails)
 * and a brass scrollbar; its title lives on a small paper stamp that
 * overlaps the board's top edge.
 * RIGHT: a slim column of actions — big Play plank, Create/Recreate/
 * Delete, a kraft note showing the selected world's details, and Cancel
 * anchored to the bottom.
 */
public class SelectWorldScreen2 extends Scene {

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
    private float scroll;
    private long lastRowClick;

    // Geometry, recomputed in build()
    private int boardX, boardY, boardW, boardH;
    private int listX, listY, listW, listH;
    private int colX, colW;          // action column
    private int infoY, infoH;        // details note in the column

    private Button2 playButton;
    private Button2 deleteButton;
    private Button2 recreateButton;
    private Modal deleteModal;

    public SelectWorldScreen2(Callbacks callbacks) {
        this.callbacks = callbacks;
        refresh();
    }

    public void refresh() {
        worlds = WorldSave.listWorlds();
        if (selected >= worlds.size()) selected = worlds.size() - 1;
        updateButtonStates();
    }

    @Override public boolean rendersWorld() { return false; }

    private void updateButtonStates() {
        boolean has = selected >= 0 && selected < worlds.size();
        boolean dead = has && worlds.get(selected).dead;
        if (playButton != null) playButton.enabled = has && !dead;
        if (deleteButton != null) deleteButton.enabled = has;
        if (recreateButton != null) recreateButton.enabled = has && !dead;
    }

    @Override
    protected void build() {
        // Two-column bench: wide list board + slim action column
        colW = 190;
        int gap = 16;
        boardX = 20;
        boardY = 48;
        boardW = Math.max(240, width - 20 - colW - gap - 12);
        boardH = height - 48 - 20;

        colX = boardX + boardW + gap;
        listX = boardX + 10;
        listY = boardY + 10;
        listW = boardW - 20;
        listH = boardH - 20;

        // Action column: Play on top, Cancel pinned to the bottom
        int bigH = 34;
        int btnH = 24;
        int btnGap = 8;

        playButton = add(new Button2(tr("selectWorld.select"), colW, bigH,
            this::playSelected));
        playButton.x = colX;
        playButton.y = boardY;
        playButton.appearDelay = 0.1f;

        int y = boardY + bigH + btnGap;
        Button2 create = add(new Button2(tr("selectWorld.create"), colW, btnH,
            callbacks::onCreate));
        create.x = colX;
        create.y = y;
        create.appearDelay = 0.15f;
        y += btnH + btnGap;

        recreateButton = add(new Button2(tr("selectWorld.recreate"), colW, btnH,
            () -> {
                if (selected >= 0 && selected < worlds.size()) {
                    callbacks.onRecreate(worlds.get(selected));
                }
            }));
        recreateButton.x = colX;
        recreateButton.y = y;
        recreateButton.appearDelay = 0.2f;
        y += btnH + btnGap;

        deleteButton = add(new Button2(tr("selectWorld.delete"), colW, btnH,
            () -> {
                if (selected >= 0 && selected < worlds.size()) {
                    deleteModal.warning(
                        tr("selectWorld.deleteWarning", worlds.get(selected).displayName));
                    deleteModal.open();
                }
            }));
        deleteButton.x = colX;
        deleteButton.y = y;
        deleteButton.danger = true;
        deleteButton.appearDelay = 0.25f;
        y += btnH + btnGap;

        // Details note between the actions and Cancel
        infoY = y + 4;
        infoH = Math.max(64, boardY + boardH - 26 - btnH - btnGap - infoY);

        Button2 cancel = add(new Button2(tr("gui.cancel"), colW, btnH,
            callbacks::onCancel));
        cancel.x = colX;
        cancel.y = boardY + boardH - btnH;
        cancel.appearDelay = 0.3f;

        deleteModal = add(Modal.confirm(
            tr("selectWorld.deleteQuestion"), "",
            tr("selectWorld.deleteButton"), tr("gui.cancel"),
            () -> {
                if (selected >= 0 && selected < worlds.size()) {
                    WorldSave.delete(worlds.get(selected).folderName);
                    selected = -1;
                    refresh();
                }
            }));

        updateButtonStates();
    }

    private void playSelected() {
        if (selected >= 0 && selected < worlds.size()) {
            callbacks.onPlay(worlds.get(selected));
        }
    }

    private int visibleRows() {
        return listH / ROW_H;
    }

    private int totalScroll() {
        return Math.max(0, worlds.size() * ROW_H - listH);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(float mx, float my, double delta) {
        scroll = Math.max(0, Math.min(totalScroll(), scroll - (float) delta * ROW_H));
        return true;
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (deleteModal.isOpen()) {
            return super.mouseClicked(mx, my, button);
        }
        // Row hit-test in list coordinates
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH
            && !worlds.isEmpty()) {
            int row = (int) ((my - listY) / ROW_H) + (int) (scroll / ROW_H);
            if (row >= 0 && row < worlds.size()) {
                long now = System.currentTimeMillis();
                if (row == selected && now - lastRowClick < 400) {
                    playSelected();
                } else {
                    selected = row;
                    updateButtonStates();
                }
                lastRowClick = now;
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    protected void renderBackground(UiDraw d) {
        // Walnut plank wall with a warm veil
        d.wall(0, 0, width, height, 0xFF6B4A2B);
        d.fill(0, 0, width, height, 0x73261A10);

        // Board, rows and chrome must all sit UNDER the element tree so the
        // delete modal (a tree child) renders on top of them
        drawBoard(d);

        // Title stamp: a paper tag pinned OVER the board's top edge
        String title = tr("selectWorld.title");
        int stampW = d.font.width(title) + 20;
        d.paper(boardX + 8, boardY - 9, stampW, 18);
        d.fill(boardX + 14, boardY - 4, 3, 1, UiTheme.BRASS);
        d.fill(boardX + 15, boardY - 5, 1, 3, UiTheme.BRASS);
        d.fill(boardX + 15, boardY - 4, 1, 1, UiTheme.BRASS_LIGHT);
        d.text(title, boardX + 22, boardY - 4, UiTheme.INK);
        if (!worlds.isEmpty()) {
            drawRows(d);
            drawScrollbar(d);
            drawWorldCount(d);
        } else {
            d.textCentered(tr("selectWorld.empty"), boardX + boardW / 2f,
                boardY + boardH / 2 - 12, UiTheme.TEXT_ON_WOOD_DIM);
            d.textCentered(tr("selectWorld.hint"), boardX + boardW / 2f,
                boardY + boardH / 2 + 4, UiTheme.fade(UiTheme.TEXT_ON_WOOD_DIM, 0.7f));
        }

        drawDetailsNote(d);
    }

    @Override
    protected void renderForeground(UiDraw d) {

    }

    private void drawBoard(UiDraw d) {
        d.card(boardX, boardY, boardW, boardH);
        d.ui.drawRectOutline(boardX + d.offX + 2, boardY + d.offY + 2,
            boardW - 4, boardH - 4, d.aAlpha(UiTheme.fade(UiTheme.BRASS_DIM, 0.8f)));
        d.cornerRivets(boardX, boardY, boardW, boardH);
    }

    private void drawRows(UiDraw d) {
        int visible = visibleRows();
        int rowW = listW - 12;

        for (int i = 0; i < visible; i++) {
            int index = i + (int) (scroll / ROW_H);
            if (index < 0 || index >= worlds.size()) break;

            WorldMeta w = worlds.get(index);
            int y = listY + i * ROW_H;
            boolean isSelected = index == selected;
            boolean hovered = d.mx >= listX && d.mx < listX + listW
                && d.my >= y && d.my < y + ROW_H;

            // Paper label with a nail at the top
            int paperA = isSelected ? 0xFFFFFFFF
                : hovered ? UiTheme.fade(0xFFF2E6C8, 0.92f)
                : UiTheme.fade(0xFFE8D9B8, 0.85f);
            d.ui.drawNineSlice(d.mat.paper, listX, y, rowW, ROW_H - 4,
                UiTheme.PAPER_BORDER, UiTheme.PAPER_TEX, d.aAlpha(paperA));
            if (isSelected) {
                // Brass selection: full outline plus a bracket on the left edge
                d.ui.drawRectOutline(listX, y, rowW, ROW_H - 4, d.aAlpha(UiTheme.BRASS));
                d.fill(listX + 2, y + 4, 3, ROW_H - 12, UiTheme.BRASS);
            }
            // The nail brightens to brass when the row is hovered
            d.fill(listX + rowW / 2 - 1, y + 1, 2, 2,
                hovered ? UiTheme.BRASS_LIGHT : UiTheme.fade(UiTheme.BRASS_DARK, 0.9f));

            // Preview swatch: framed dirt, dimmed for dead worlds
            int iconS = ROW_H - 14;
            int iconX = listX + 8;
            int iconY = y + 5;
            d.ui.drawTiled(com.voxelgame.ui.GuiAssets.INSTANCE.dirt,
                iconX, iconY, iconS, iconS, 16,
                d.aAlpha(w.dead ? 0xFF8A8078 : 0xFFC8BCA8));
            d.ui.drawRectOutline(iconX, iconY, iconS, iconS,
                d.aAlpha(w.dead ? 0xFF5A4A3A : UiTheme.BRASS_DIM));

            int textX = iconX + iconS + 8;
            int textW = rowW - (textX - listX) - 8;

            String name = (w.dead ? "\u2620 " : "") + w.displayName;
            name = d.font.trimToWidth(name, textW);
            int nameColor = w.dead ? UiTheme.fade(UiTheme.INK, 0.55f) : UiTheme.INK;
            d.text(name, textX, y + 6, nameColor);

            String sub = tr(w.gameMode.key) + " \u00B7 " + w.formattedPlayTime()
                + " \u00B7 " + w.formattedLastPlayed();
            sub = d.font.trimToWidth(sub, textW);
            d.text(sub, textX, y + 19, UiTheme.fade(UiTheme.INK_SOFT, w.dead ? 0.5f : 0.95f));
        }
    }

    /** Kraft note in the action column showing the selected world. */
    private void drawDetailsNote(UiDraw d) {
        d.paperCard(colX, infoY, colW, infoH);

        boolean has = selected >= 0 && selected < worlds.size();
        if (!has) {
            List<String> hintLines = wrap(d.font, tr("selectWorld.hint"), colW - 16);
            int hy = infoY + 8;
            for (String line : hintLines) {
                d.text(line, colX + 8, hy, 0xFF5A4A34);
                hy += FontRenderer.LINE_HEIGHT;
            }
            return;
        }
        WorldMeta w = worlds.get(selected);
        String name = (w.dead ? "\u2620 " : "") + w.displayName;
        int x = colX + 8;
        int y = infoY + 8;
        int maxW = colW - 16;
        for (String line : wrap(d.font, name, maxW)) {
            d.text(line, x, y, w.dead ? UiTheme.fade(UiTheme.INK, 0.55f) : UiTheme.INK);
            y += FontRenderer.LINE_HEIGHT;
        }
        y += 2;
        d.brassLine(x, y, maxW);
        y += 4;
        d.text(tr(w.gameMode.key), x, y, UiTheme.INK_SOFT);
        y += FontRenderer.LINE_HEIGHT;
        d.text(w.formattedPlayTime(), x, y, UiTheme.INK_SOFT);
        y += FontRenderer.LINE_HEIGHT;
        d.text(w.formattedLastPlayed(), x, y, UiTheme.INK_SOFT);
    }

    private static List<String> wrap(FontRenderer font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String word : text.split(" ")) {
            if (lines.isEmpty()) {
                lines.add(word);
            } else {
                String candidate = lines.get(lines.size() - 1) + " " + word;
                if (font.width(candidate) <= maxWidth) {
                    lines.set(lines.size() - 1, candidate);
                } else {
                    lines.add(word);
                }
            }
        }
        return lines;
    }

    /** Small stamped counter on the board's bottom edge. */
    private void drawWorldCount(UiDraw d) {
        String stamp = String.valueOf(worlds.size());
        int stampW = d.font.width(stamp);
        int sx = boardX + boardW - stampW - 14;
        int sy = boardY + boardH - 11;
        // Tiny brass diamond, then the count
        d.fill(sx - 5, sy + 2, 3, 1, UiTheme.BRASS);
        d.fill(sx - 4, sy + 1, 1, 3, UiTheme.BRASS);
        d.fill(sx - 4, sy + 2, 1, 1, UiTheme.BRASS_LIGHT);
        d.textShadow(stamp, sx, sy, UiTheme.TEXT_ON_WOOD_DIM);
    }

    private void drawScrollbar(UiDraw d) {
        int total = worlds.size() * ROW_H;
        if (total <= listH) return;

        int trackX = listX + listW - 6;
        d.fill(trackX, listY + 1, 4, listH - 2, UiTheme.fade(0xFF26180E, 0.75f));

        int thumbH = Math.max(16, listH * listH / total);
        float t = totalScroll() > 0 ? scroll / totalScroll() : 0;
        int thumbY = listY + 1 + (int) ((listH - 2 - thumbH) * t);
        d.fill(trackX, thumbY, 4, thumbH, UiTheme.BRASS);
        d.fill(trackX, thumbY, 1, thumbH, UiTheme.BRASS_LIGHT);
    }
}
