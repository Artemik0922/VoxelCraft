package com.voxelgame.ui;

import com.voxelgame.core.Language;
import com.voxelgame.item.Inventory;

import static com.voxelgame.core.Language.tr;
import com.voxelgame.item.ItemStack;
import com.voxelgame.player.Player;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.world.BlockType;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.World;
import com.voxelgame.world.save.WorldMeta;

/**
 * In-game overlay: crosshair, hotbar, hearts and the F3 debug panel.
 *
 * Everything is laid out on whole GUI pixels and drawn from the procedural
 * sprites in {@link GuiAssets} at their native size.
 */
public class GameHud {

    private static final int SLOT = GuiAssets.SLOT_SIZE;   // 18
    private static final int ICON = GuiAssets.ICON_SIZE;   // 16
    private static final int HEART = GuiAssets.HEART_SIZE; // 9

    /** Padding between the hotbar panel edge and the slots inside it. */
    private static final int HOTBAR_PAD = 2;
    /** Gap between the panel and the bottom of the screen. */
    private static final int HOTBAR_MARGIN = 4;
    
    /** Total width of the hotbar panel, used to anchor the hunger row. */
    private int hotbarPanelWidth() {
        return Inventory.HOTBAR_SIZE * SLOT + HOTBAR_PAD * 2;
    }

    private final TextureAtlas atlas;

    private boolean debugVisible = false;
    private double selectionPulse = 0;
    private int lastSelectedSlot = -1;

    // [MINIMAP] Live corner map, rewritten as its own renderer (see Minimap)
    private boolean minimapVisible = true;
    private final Minimap minimap = new Minimap();

    // Damage flash
    private int lastHealth = Integer.MIN_VALUE;
    private double damageFlash = 0;

    public GameHud(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    public void toggleDebug() { debugVisible = !debugVisible; }
    public boolean isDebugVisible() { return debugVisible; }

    /** Toggle the corner map (M). */
    public void toggleMinimap() { minimapVisible = !minimapVisible; }
    public boolean isMinimapVisible() { return minimapVisible; }

    /** Fullscreen map mode (TAB). Implies the map is on while it lasts. */
    public void toggleMinimapExpanded() {
        minimap.toggleExpanded();
        if (minimap.isExpanded()) minimapVisible = true;
    }

    public boolean isMinimapExpanded() { return minimap.isExpanded(); }

    /** True when the gui cursor is over the rendered map (wheel zoom). */
    public boolean minimapHover(float mx, float my) {
        return minimapVisible && minimap.hover(mx, my);
    }

    /** Zoom the map one step; positive = farther out. */
    public void minimapZoom(int step) { minimap.zoomBy(step); }

    /** Extra top inset so status icons clear the map when it is shown. */
    public int minimapTopInset() {
        return minimapVisible ? minimap.totalHeight() + 2 : 6;
    }

    /** Releases the minimap GL textures. */
    public void cleanup() { minimap.cleanup(); }

    /** Values panel shown while adjusting the first-person view model. */
    private void drawTuningPanel(UIRenderer ui, FontRenderer font, String[] lines) {
        int pad = 4;
        int maxW = 0;
        for (String s : lines) maxW = Math.max(maxW, font.width(s));

        int h = lines.length * FontRenderer.LINE_HEIGHT + pad * 2;
        int y = 40;

        ui.useSolidColor();
        ui.fillRect(2, y, maxW + pad * 2, h, 0xC0101020);
        ui.drawRectOutline(2, y, maxW + pad * 2, h, 0xFFFFCC44);

        int ty = y + pad;
        for (String s : lines) {
            font.drawWithShadow(ui, s, 2 + pad, ty, 0xFFFFDD66);
            ty += FontRenderer.LINE_HEIGHT;
        }
    }

    /** F6: overlay the raw block atlas, to confirm what is really uploaded. */
    private boolean atlasVisible = false;
    public void toggleAtlasView() { atlasVisible = !atlasVisible; }
    public boolean isAtlasVisible() { return atlasVisible; }

    /**
     * Draw the 2D atlas over the HUD with a checkerboard behind it, so
     * transparent texels are obvious rather than blending into the sky.
     */
    private void drawAtlasOverlay(UIRenderer ui, FontRenderer font) {
        int size = 192;
        int x = 4;
        int y = 24;

        ui.useSolidColor();
        // Checkerboard: any hole shows the pattern through
        int cell = 8;
        for (int cy = 0; cy < size; cy += cell) {
            for (int cx = 0; cx < size; cx += cell) {
                boolean odd = ((cx / cell) + (cy / cell)) % 2 == 0;
                ui.fillRect(x + cx, y + cy, cell, cell, odd ? 0xFF303030 : 0xFF606060);
            }
        }

        ui.drawTexture(atlas.getTexture().getId(), x, y, size, size,
            0, 1, 1, 0, 0xFFFFFFFF);

        ui.useSolidColor();
        ui.drawRectOutline(x - 1, y - 1, size + 2, size + 2, 0xFFFFFFFF);
        font.drawWithShadow(ui, "Block atlas (F6) - grey squares = alpha holes",
            x, y + size + 3, 0xFFFFFFFF);
    }

    public void update(double deltaTime, int selectedSlot, int health) {
        if (selectedSlot != lastSelectedSlot) {
            lastSelectedSlot = selectedSlot;
            selectionPulse = 1.0;
        }
        selectionPulse = Math.max(0, selectionPulse - deltaTime * 3.0);
        minimap.update(deltaTime);

        // Flash the hearts when health drops
        if (lastHealth != Integer.MIN_VALUE && health < lastHealth) {
            damageFlash = 0.6;
        }
        lastHealth = health;
        damageFlash = Math.max(0, damageFlash - deltaTime);
    }

    // ------------------------------------------------------------------

    public void render(UIRenderer ui, FontRenderer font, GuiAssets gui,
                       Inventory inventory, DebugInfo debug, World world) {
        int w = ui.getWidth();
        int h = ui.getHeight();

        int hotbarW = Inventory.HOTBAR_SIZE * SLOT + HOTBAR_PAD * 2;
        int hotbarH = SLOT + HOTBAR_PAD * 2;
        int hotbarX = (w - hotbarW) / 2;
        int hotbarY = h - hotbarH - HOTBAR_MARGIN;

        drawCrosshair(ui, gui, w, h);
        drawHotbar(ui, font, gui, inventory, hotbarX, hotbarY, hotbarW, hotbarH);

        // Status stack above the hotbar, vanilla-style: XP bar sits directly
        // on the hotbar, hearts and hunger share the row above it, air
        // bubbles float above that. Nothing overlaps.
        boolean survival = debug.gameMode != WorldMeta.GameMode.CREATIVE;
        int statusY = hotbarY - HEART - 2;
        if (survival) statusY -= 5 + 4; // XP bar height + breathing gap

        if (survival) {
            drawHearts(ui, gui, hotbarX, hotbarW, statusY, debug.health, debug.maxHealth);
            drawHunger(ui, gui, hotbarX, hotbarW, statusY, debug.hunger, debug.maxHunger);
            if (debug.air < debug.maxAir) {
                drawAirBubbles(ui, gui, hotbarX, hotbarW, statusY, debug.air, debug.maxAir);
            }
            if (debug.eatProgress > 0) {
                drawEatProgress(ui, gui, hotbarX, statusY, debug.eatProgress);
            }
            drawXpBar(ui, font, hotbarX, hotbarY, hotbarW,
                debug.xpLevel, debug.xpProgress, debug.xpToNext);
        }
        // [POT] Active status-effect icons in the top-right corner
        drawEffectIcons(ui, debug.activeEffects, w);
        drawHeldItemName(ui, font, inventory, w, statusY);

        // [UI-009] World still filling in: brief corner note so the empty
        // distance is not mistaken for a bug
        if (debug.loadingChunks) {
            String msg = tr("hud.loadingChunks");
            int msgW = font.scaledWidth(msg, 1);
            font.drawScaledWithShadow(ui, msg, w - msgW - 12, h - hotbarH - HOTBAR_MARGIN - 22,
                1, 0xFFE0C860);
        }

        if (debugVisible) {
            drawDebug(ui, font, debug);
        }

        if (debug.tuningLines != null) {
            drawTuningPanel(ui, font, debug.tuningLines);
        }

        if (atlasVisible) {
            drawAtlasOverlay(ui, font);
        }

        // [MINIMAP] Corner block map (top-right, below effect icons when both on)
        if (minimapVisible && world != null) {
            minimap.render(ui, font, world, debug.x, debug.y, debug.z, debug.yaw,
                debug.daylight, debug.biomeName, debug.clock);
        }
    }

private void drawCrosshair(UIRenderer ui, GuiAssets gui, int w, int h) {
        int size = GuiAssets.CROSSHAIR_SIZE;
        ui.drawSprite(gui.crosshair, (w - size) / 2, (h - size) / 2, size, size);
    }

    /**
     * [ENCH] Green experience bar above the hotbar with the level number.
     * Fills from the left; starts small like vanilla.
     */
    private void drawXpBar(UIRenderer ui, FontRenderer font,
                           int hotbarX, int hotbarY, int hotbarW,
                           int xpLevel, int xpProgress, int xpToNext) {
        int barW = 182;
        int barH = 4;
        int barX = hotbarX + (hotbarW - barW) / 2;
        int barY = hotbarY - barH - 2;

        ui.useSolidColor();
        // Recessed glass track
        ui.fillRect(barX - 1, barY - 1, barW + 2, barH + 2, 0x40000000);
        ui.fillRect(barX, barY, barW, barH, 0xFF1B2332);
        ui.fillRect(barX, barY - 1, barW, 1, 0xFF2A3450);

        float fraction = xpToNext <= 0 ? 0 : (float) xpProgress / xpToNext;
        if (fraction > 0) {
            ui.fillRect(barX, barY, (int) (barW * fraction), barH, 0xFF8CF080);
            ui.fillRect(barX, barY, (int) (barW * fraction), 1, 0xFFC9FFB0);
        }

        if (xpLevel > 0) {
            String label = String.valueOf(xpLevel);
            font.drawWithShadow(ui, label, barX + barW + 3, barY - 2, 0xFFB0FF90);
        }
    }

    /**
     * One continuous panel with nine sunken slots inside it.
     */
    private void drawHotbar(UIRenderer ui, FontRenderer font, GuiAssets gui,
                            Inventory inventory, int px, int py, int pw, int ph) {
        ui.drawNineSlice(gui.glassPanel, px, py, pw, ph,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF332314);
        // Brass trim along the dock's top edge
        ui.useSolidColor();
        ui.fillRect(px + 2, py, pw - 4, 1, 0xFF8A6420);
        ui.fillRect(px + 2, py + 1, pw - 4, 1, 0x50EDD9A0);

        int selected = inventory.getSelectedSlot();

        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int sx = px + HOTBAR_PAD + i * SLOT;
            int sy = py + HOTBAR_PAD;

            ui.drawNineSlice(gui.glassSlot, sx, sy, SLOT, SLOT,
                3, GuiAssets.SLOT_SIZE, 0xFF3A2A1A);

            ItemStack stack = inventory.getHotbarItem(i);
            if (!stack.isEmpty()) {
                drawItemStackIcon(ui, stack, sx + 1, sy + 1, ICON);
                drawCount(ui, font, stack.getCount(), sx, sy);
                // Draw durability bar for tools
                drawDurabilityBar(ui, stack, sx, sy);
            }
        }

        drawSelectionFrame(ui, px + HOTBAR_PAD + selected * SLOT, py + HOTBAR_PAD);
    }

    /**
     * Draw a durability bar under tool items. Green when high, yellow medium,
     * red when low.
     */
    private void drawDurabilityBar(UIRenderer ui, ItemStack stack, int sx, int sy) {
        int maxDur = stack.getMaxDurability();
        if (maxDur <= 0) return;

        int dur = stack.getDurability();
        float fraction = 1.0f - (float) dur / maxDur;
        if (fraction >= 1.0f) return; // Don't show if full

        int barW = 14;
        int filled = (int) (barW * fraction);
        int barX = sx + 2;
        int barY = sy + SLOT - 3;

        // Background
        ui.useSolidColor();
        ui.drawRectOutline(barX, barY, barW, 2, 0xFF000000);
        // Fill
        int color = fraction > 0.6f ? 0xFF55FF55 : (fraction > 0.3f ? 0xFFFFFF55 : 0xFFFF5555);
        if (filled > 0) {
            ui.drawRectOutline(barX, barY, filled, 2, color);
        }
    }

    /**
     * Accent frame around the active slot with a soft glow, briefly expanded
     * after the selection moves.
     */
    private void drawSelectionFrame(UIRenderer ui, int sx, int sy) {
        int grow = selectionPulse > 0.5 ? 1 : 0;

        int x = sx - 1 - grow;
        int y = sy - 1 - grow;
        int size = SLOT + 2 + grow * 2;

        ui.useSolidColor();
        // Soft halo so the frame reads against the glass slots
        ui.fillRect(x - 1, y - 1, size + 2, size + 2, 0x2A000000 | 0xFFC9973B);
        ui.fillRect(x, y, size, 1, 0xFFEDD9A0);
        ui.fillRect(x, y + size - 1, size, 1, 0xFFC9973B);
        ui.fillRect(x, y, 1, size, 0xFFEDD9A0);
        ui.fillRect(x + size - 1, y, 1, size, 0xFFC9973B);
    }

    /**
     * Stack size in the bottom-right of the slot. Hidden for single items,
     * drawn at font scale 1 so it never covers the icon.
     */
    private void drawCount(UIRenderer ui, FontRenderer font, int count, int sx, int sy) {
        if (count < 2) return;

        String text = String.valueOf(count);
        int tw = font.width(text);

        font.drawWithShadow(ui, text,
            sx + SLOT - tw - 1,
            sy + SLOT - FontRenderer.GLYPH_H - 1,
            0xFFFFFFFF);
    }

    private void drawBlockIcon(UIRenderer ui, BlockType block, int x, int y, int size, float daylight) {
        TextureAtlas.TextureCoords uv = atlas.getIconCoords(block.id);
        ui.drawTexture(atlas.getTexture().getId(), x, y, size, size,
            uv.u1, uv.v2, uv.u2, uv.v1, 0xFFFFFFFF);
    }

    /** Flat sprite icon for a block or an item tile (tools, potions...). */
    private void drawItemStackIcon(UIRenderer ui, ItemStack stack, int x, int y, int size) {
        // Shared icon path: isometric 3D icons for cubes, flat sprites for
        // items — identical to every container screen
        StackIcons.drawIcon(ui, atlas, stack, x, y, size);
    }

    /** Vanilla strips the status rows to a 182px bar centred on the hotbar,
     *  so hearts and hunger keep a clear gap in the middle. */
    private static final int STATUS_BAR_W = 182;

    private int statusBarX(int hotbarX, int hotbarW) {
        return hotbarX + (hotbarW - STATUS_BAR_W) / 2;
    }

    /**
     * Ten hearts sitting on the status row, left aligned with the 182px bar.
     */
    private void drawHearts(UIRenderer ui, GuiAssets gui, int hotbarX, int hotbarW,
                            int statusY, int health, int maxHealth) {
        final int hearts = 10;
        int y = statusY;

        // Each heart covers a tenth of max health; halves show the odd point
        float perHeart = Math.max(1, maxHealth) / (float) hearts;
        int barX = statusBarX(hotbarX, hotbarW);

        // Jitter and brighten briefly when damaged
        boolean flash = damageFlash > 0 && ((int) (damageFlash * 20) % 2 == 0);
        int tint = flash ? 0xFFFFFFFF : 0xFFFFFFFF;
        int bump = (damageFlash > 0) ? -1 : 0;

        for (int i = 0; i < hearts; i++) {
            int x = barX + i * (HEART - 1);
            float filled = health - i * perHeart;

            var mat = com.voxelgame.ui2.UiMaterials.INSTANCE;
            int sprite;
            if (filled >= perHeart - 0.001f) {
                sprite = mat.heartFull;
            } else if (filled >= perHeart * 0.5f) {
                sprite = mat.heartHalf;
            } else {
                sprite = mat.heartEmpty;
            }

            // Always draw the container so the bar keeps its length
            if (sprite != mat.heartEmpty) {
                ui.drawSprite(mat.heartEmpty, x, y, HEART, HEART);
            }
            ui.drawSprite(sprite, x, y + (flash ? bump : 0), HEART, HEART, tint);
        }
    }

    /**
     * Ten hunger drumsticks mirrored to the right of the hotbar, vanilla
     * style: the bar depletes from the left, halves fill the icon's right.
     */
    private void drawHunger(UIRenderer ui, GuiAssets gui, int hotbarX, int hotbarW,
                            int statusY, int hunger, int maxHunger) {
        final int bars = 10;
        int y = statusY;
        float perBar = Math.max(1, maxHunger) / (float) bars;
        int barX = statusBarX(hotbarX, hotbarW);

        for (int i = 0; i < bars; i++) {
            // Right-aligned, growing leftward from the status bar's right edge
            int x = barX + STATUS_BAR_W - HOTBAR_PAD - (bars - i) * (HEART - 1);
            float filled = hunger - i * perBar;

            var mat = com.voxelgame.ui2.UiMaterials.INSTANCE;
            int sprite;
            if (filled >= perBar - 0.001f) {
                sprite = mat.drumstickFull;
            } else if (filled >= perBar * 0.5f) {
                sprite = mat.drumstickHalf;
            } else {
                sprite = mat.drumstickEmpty;
            }
            ui.drawSprite(sprite, x, y, HEART, HEART);
        }
    }

    /**
     * Air bubbles shown above the hearts when the head is underwater. One
     * bubble pops per 1/10th of the supply, like vanilla.
     */
    private void drawAirBubbles(UIRenderer ui, GuiAssets gui, int hotbarX, int hotbarW,
                                int statusY, int air, int maxAir) {
        final int bubbles = 10;
        int y = statusY - HEART - 2;
        float perBubble = Math.max(1, maxAir) / (float) bubbles;
        int barX = statusBarX(hotbarX, hotbarW);

        for (int i = 0; i < bubbles; i++) {
            float filled = air - i * perBubble;
            if (filled <= 0) break;
            // Partial last bubble still reads as one bubble, like vanilla pops
            int x = barX + HOTBAR_PAD + i * (HEART - 1);
            ui.drawSprite(com.voxelgame.ui2.UiMaterials.INSTANCE.bubble, x, y, HEART, HEART);
        }
    }

    /**
     * Eating progress bar above the hotbar. Fills from left to right while the
     * player holds right-click with food.
     */
    private void drawEatProgress(UIRenderer ui, GuiAssets gui, int hotbarX, int statusY,
                                  float progress) {
        int barW = Inventory.HOTBAR_SIZE * SLOT;
        int barH = 3;
        int x = hotbarX + HOTBAR_PAD;
        int y = statusY - barH - 4;

        ui.useSolidColor();
        ui.fillRect(x, y, barW, barH, 0xFF26180E);
        ui.fillRect(x, y - 1, barW, 1, 0xFF5A4326);
        ui.fillRect(x, y, (int) (barW * progress), barH, 0xFF88FF44);
        if (progress > 0) {
            ui.fillRect(x, y, (int) (barW * progress), 1, 0xFFC9FFB0);
        }
    }

    /**
     * [POT] Status-effect icons stacked down the top-right corner. A dark
     * slice over each icon shrinks as the remaining time runs out.
     */
    private void drawEffectIcons(UIRenderer ui,
                                 java.util.Map<com.voxelgame.item.StatusEffect,
                                     com.voxelgame.player.Player.ActiveEffect> effects,
                                 int w) {
        if (effects == null || effects.isEmpty()) return;
        int size = 22;
        int gap = 2;
        int x = w - size - 4;
        // Shift icons below the minimap when it is visible
        int y = minimapTopInset();
        for (com.voxelgame.player.Player.ActiveEffect e : effects.values()) {
            // Frosted chip behind the icon
            ui.useSolidColor();
            ui.fillRect(x - 1, y - 1, size + 2, size + 2, 0x55000000);
            ui.fillRect(x, y - 1, size, 1, 0x30FFFFFF);

            int slot = atlas.getLayerOf(e.type.spriteName);
            if (slot >= 0) {
                TextureAtlas.TextureCoords uv = new TextureAtlas.TextureCoords(slot);
                ui.drawTexture(atlas.getTexture().getId(), x, y, size, size,
                    uv.u1, uv.v2, uv.u2, uv.v1, 0xFFFFFFFF);
            }
            float maxTicks = Math.max(1, e.type.baseDurationTicks);
            float left = java.lang.Math.min(1.0f, (float) e.remainingTicks / maxTicks);
            int fadeH = (int) (size * (1.0f - left));
            if (fadeH > 0) {
                ui.useSolidColor();
                ui.fillRect(x, y + size - fadeH, size, fadeH, 0x90000000);
            }
            y += size + gap;
        }
    }

    /** Name of the held block or item, fading out with the selection pulse. */
    private void drawHeldItemName(UIRenderer ui, FontRenderer font,
                                  Inventory inventory, int w, int statusY) {
        ItemStack held = inventory.getSelectedItem();
        if (held.isEmpty() || selectionPulse <= 0) return;

        int alpha = (int) (255 * Math.min(1.0, selectionPulse * 1.6));
        String name = StackIcons.displayName(held);

        font.drawCenteredWithShadow(ui, name, w / 2, statusY - GuiAssets.HEART_SIZE - 10,
            (alpha << 24) | 0xFFFFFF);
    }

    // ------------------------------------------------------------------

    /** Values the debug panel needs, gathered by the caller. */
    public static class DebugInfo {
        public float fps;
        public double frameMs;
        public float x, y, z;
        public float yaw, pitch;
        public float dirX, dirY, dirZ;
        public int loadedChunks;
        public int renderedChunks;
        public long triangles;
        public int renderDistance;
        public int health;
        public int maxHealth;
        public int hunger;
        public int maxHunger;
        public int air;
        public int maxAir;
        public float eatProgress;
        public WorldMeta.GameMode gameMode;
        public String biomeName = "";
        public String gpu = "";
        public int guiScale;
        public String clock = "";
        public float daylight;
        public int particles;
        public String biome = "";
        public int culled;
        public int pendingChunks;
        public int lightBacklog;
        public int meshBacklog;
        public int workers;
        /** [OPT] Per-pass frame time in microseconds (0 when idle/skipped). */
        public long rebuildUs, shadowUs, worldUs, sceneUs, postUs, uiUs;
        /** [UI-009] True while chunks are still streaming in. */
        public boolean loadingChunks;
        public long leafTriangles;
        public int leafChunks;
        /** Non-null while the view model is being tuned. */
        public String[] tuningLines;
        /** [ENCH] Player experience for the HUD bar. */
        public int xpLevel;
        public int xpProgress;
        public int xpToNext;
        /** [POT] Active status effects and their remaining ticks. */
        public java.util.Map<com.voxelgame.item.StatusEffect, com.voxelgame.player.Player.ActiveEffect> activeEffects
            = java.util.Collections.emptyMap();
    }

    private void drawDebug(UIRenderer ui, FontRenderer font, DebugInfo d) {
        String[] lines = {
            String.format(java.util.Locale.ROOT, "%.0f fps  (%.2f ms)", d.fps, d.frameMs),
            String.format(java.util.Locale.ROOT, "XYZ: %.2f / %.2f / %.2f", d.x, d.y, d.z),
            String.format(java.util.Locale.ROOT, "Block: %d %d %d",
                (int) Math.floor(d.x), (int) Math.floor(d.y), (int) Math.floor(d.z)),
            String.format(java.util.Locale.ROOT, "Chunk: %d, %d",
                Math.floorDiv((int) Math.floor(d.x), 16), Math.floorDiv((int) Math.floor(d.z), 16)),
            String.format(java.util.Locale.ROOT, "Facing: %s", facingName(d.yaw)),
            String.format(java.util.Locale.ROOT, "Look: %.2f %.2f %.2f", d.dirX, d.dirY, d.dirZ),
            String.format(java.util.Locale.ROOT, "Chunks: %d drawn / %d loaded, %d culled",
                d.renderedChunks, d.loadedChunks, d.culled),
            String.format(java.util.Locale.ROOT, "Queue: %d gen (%dw), %d light, %d mesh",
                d.pendingChunks, d.workers, d.lightBacklog, d.meshBacklog),
            String.format(java.util.Locale.ROOT, "Tris: %,d  (cutout pass: %,d in %d)", d.triangles, d.leafTriangles, d.leafChunks),
            String.format(java.util.Locale.ROOT, "Distance: %d   GUI: %dx", d.renderDistance, d.guiScale),
            String.format(java.util.Locale.ROOT, "Biome: %s", d.biome),
            String.format(java.util.Locale.ROOT, "Time: %s  (daylight %.2f)", d.clock, d.daylight),
            String.format(java.util.Locale.ROOT, "Particles: %d", d.particles),
            String.format(java.util.Locale.ROOT, "Biome: %s", d.biomeName),
            String.format(java.util.Locale.ROOT, "Pass ms: rebuild %.2f shadow %.2f world %.2f",
                d.rebuildUs / 1000.0, d.shadowUs / 1000.0, d.worldUs / 1000.0),
            String.format(java.util.Locale.ROOT, "Pass ms: scene %.2f post %.2f ui %.2f",
                d.sceneUs / 1000.0, d.postUs / 1000.0, d.uiUs / 1000.0),
        };

        int pad = 3;
        int maxW = 0;
        for (String s : lines) maxW = Math.max(maxW, font.width(s));

        ui.useSolidColor();
        ui.fillRect(2, 2, maxW + pad * 2, lines.length * FontRenderer.LINE_HEIGHT + pad * 2,
            0xA0000000);

        int y = 2 + pad;
        for (String s : lines) {
            font.drawWithShadow(ui, s, 2 + pad, y, GuiAssets.TEXT_NORMAL);
            y += FontRenderer.LINE_HEIGHT;
        }
    }

    // ------------------------------------------------------------------
    // [MINIMAP] rendered by the dedicated Minimap renderer
    // ------------------------------------------------------------------

    /** Cardinal direction from yaw, matching Minecraft's convention. */
    private static String facingName(float yaw) {
        float a = ((yaw % 360) + 360) % 360;
        if (a >= 315 || a < 45) return "east (+X)";
        if (a < 135) return "south (+Z)";
        if (a < 225) return "west (-X)";
        return "north (-Z)";
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
