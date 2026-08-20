package com.voxelgame.ui;

import com.voxelgame.core.Language;
import com.voxelgame.item.Inventory;

import static com.voxelgame.core.Language.tr;
import com.voxelgame.item.ItemStack;
import com.voxelgame.player.Player;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.world.BlockType;
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

    // Damage flash
    private int lastHealth = Integer.MIN_VALUE;
    private double damageFlash = 0;

    public GameHud(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    public void toggleDebug() { debugVisible = !debugVisible; }
    public boolean isDebugVisible() { return debugVisible; }

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

        // Flash the hearts when health drops
        if (lastHealth != Integer.MIN_VALUE && health < lastHealth) {
            damageFlash = 0.6;
        }
        lastHealth = health;
        damageFlash = Math.max(0, damageFlash - deltaTime);
    }

    // ------------------------------------------------------------------

    public void render(UIRenderer ui, FontRenderer font, GuiAssets gui,
                       Inventory inventory, DebugInfo debug) {
        int w = ui.getWidth();
        int h = ui.getHeight();

        int hotbarW = Inventory.HOTBAR_SIZE * SLOT + HOTBAR_PAD * 2;
        int hotbarH = SLOT + HOTBAR_PAD * 2;
        int hotbarX = (w - hotbarW) / 2;
        int hotbarY = h - hotbarH - HOTBAR_MARGIN;

        drawCrosshair(ui, gui, w, h);
        drawHotbar(ui, font, gui, inventory, hotbarX, hotbarY, hotbarW, hotbarH);
        // Creative hides health, hunger and stack counts
        if (debug.gameMode != WorldMeta.GameMode.CREATIVE) {
            drawHearts(ui, gui, hotbarX, hotbarY, debug.health, debug.maxHealth);
            drawHunger(ui, gui, hotbarX, hotbarY, debug.hunger, debug.maxHunger);
            if (debug.air < debug.maxAir) {
                drawAirBubbles(ui, gui, hotbarX, hotbarY, debug.air, debug.maxAir);
            }
            if (debug.eatProgress > 0) {
                drawEatProgress(ui, gui, hotbarX, hotbarY, debug.eatProgress);
            }
        }
        // [ENCH] Experience bar above the hotbar (survival only)
        if (debug.gameMode != WorldMeta.GameMode.CREATIVE) {
            drawXpBar(ui, font, hotbarX, hotbarY, hotbarW,
                debug.xpLevel, debug.xpProgress, debug.xpToNext);
        }
drawHeldItemName(ui, font, inventory, w, hotbarY);

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
        ui.fillRect(barX - 1, barY - 1, barW + 2, barH + 2, 0x80000000);

        float fraction = xpToNext <= 0 ? 0 : (float) xpProgress / xpToNext;
        if (fraction > 0) {
            ui.fillRect(barX, barY, (int) (barW * fraction), barH, 0xFF8CF080);
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
        ui.drawNineSlice(gui.hotbarPanel, px, py, pw, ph,
            GuiAssets.BORDER, GuiAssets.WIDGET, 0xFFFFFFFF);

        int selected = inventory.getSelectedSlot();

        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int sx = px + HOTBAR_PAD + i * SLOT;
            int sy = py + HOTBAR_PAD;

            ui.drawSprite(gui.slot, sx, sy, SLOT, SLOT);

            ItemStack stack = inventory.getHotbarItem(i);
            if (!stack.isEmpty()) {
                drawBlockIcon(ui, stack.getBlockType(), sx + 1, sy + 1, ICON, 1.0f);
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
     * 2px white outline around the active slot, briefly expanded after the
     * selection moves.
     */
    private void drawSelectionFrame(UIRenderer ui, int sx, int sy) {
        int grow = selectionPulse > 0.5 ? 1 : 0;

        int x = sx - 1 - grow;
        int y = sy - 1 - grow;
        int size = SLOT + 2 + grow * 2;

        ui.useSolidColor();
        for (int i = 0; i < 2; i++) {
            ui.drawRectOutline(x - i, y - i, size + i * 2, size + i * 2, 0xFFFFFFFF);
        }
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

    /**
     * Ten hearts sitting directly above the hotbar, left aligned with it.
     */
    private void drawHearts(UIRenderer ui, GuiAssets gui, int hotbarX, int hotbarY,
                            int health, int maxHealth) {
        final int hearts = 10;
        int y = hotbarY - HEART - 2;

        // Each heart covers a tenth of max health; halves show the odd point
        float perHeart = Math.max(1, maxHealth) / (float) hearts;

        // Jitter and brighten briefly when damaged
        boolean flash = damageFlash > 0 && ((int) (damageFlash * 20) % 2 == 0);
        int tint = flash ? 0xFFFFFFFF : 0xFFFFFFFF;
        int bump = (damageFlash > 0) ? -1 : 0;

        for (int i = 0; i < hearts; i++) {
            int x = hotbarX + HOTBAR_PAD + i * (HEART - 1);
            float filled = health - i * perHeart;

            int sprite;
            if (filled >= perHeart - 0.001f) {
                sprite = gui.heartFull;
            } else if (filled >= perHeart * 0.5f) {
                sprite = gui.heartHalf;
            } else {
                sprite = gui.heartEmpty;
            }

            // Always draw the container so the bar keeps its length
            if (sprite != gui.heartEmpty) {
                ui.drawSprite(gui.heartEmpty, x, y, HEART, HEART);
            }
            ui.drawSprite(sprite, x, y + (flash ? bump : 0), HEART, HEART, tint);
        }
    }

    /**
     * Ten hunger bars mirrored to the right of the hotbar. Each covers two
     * points; halves show the odd point.
     */
    private void drawHunger(UIRenderer ui, GuiAssets gui, int hotbarX, int hotbarY,
                            int hunger, int maxHunger) {
        final int bars = 10;
        int y = hotbarY - HEART - 2;
        float perBar = Math.max(1, maxHunger) / (float) bars;
        int panelW = hotbarPanelWidth();

        for (int i = 0; i < bars; i++) {
            // Right-aligned, growing leftward from the panel's right edge
            int x = hotbarX + panelW - HOTBAR_PAD - (bars - i) * (HEART - 1);
            float filled = hunger - i * perBar;

            int sprite;
            if (filled >= perBar - 0.001f) {
                sprite = gui.heartFull;
            } else if (filled >= perBar * 0.5f) {
                sprite = gui.heartHalf;
            } else {
                sprite = gui.heartEmpty;
            }
            if (sprite != gui.heartEmpty) {
                ui.drawSprite(gui.heartEmpty, x, y, HEART, HEART);
            }
            // Hunger uses a warm tint to distinguish from health
            ui.drawSprite(sprite, x, y, HEART, HEART, 0xFFFFCC66);
        }
    }

    /**
     * Air bubbles shown above the hearts when the player is underwater.
     */
    private void drawAirBubbles(UIRenderer ui, GuiAssets gui, int hotbarX, int hotbarY,
                                int air, int maxAir) {
        final int bubbles = 10;
        int y = hotbarY - HEART * 2 - 6;
        float perBubble = Math.max(1, maxAir) / (float) bubbles;

        for (int i = 0; i < bubbles; i++) {
            int x = hotbarX + HOTBAR_PAD + i * (HEART - 1);
            float filled = air - i * perBubble;
            int sprite = filled >= perBubble - 0.001f ? gui.heartFull : gui.heartEmpty;
            ui.drawSprite(sprite, x, y, HEART, HEART, 0xFF88CCFF);
        }
    }

    /**
     * Eating progress bar above the hotbar. Fills from left to right while the
     * player holds right-click with food.
     */
    private void drawEatProgress(UIRenderer ui, GuiAssets gui, int hotbarX, int hotbarY,
                                  float progress) {
        int barW = Inventory.HOTBAR_SIZE * SLOT;
        int barH = 3;
        int x = hotbarX + HOTBAR_PAD;
        int y = hotbarY - barH - 4;

        ui.useSolidColor();
        ui.fillRect(x, y, barW, barH, 0xFF444444);
        ui.fillRect(x, y, (int) (barW * progress), barH, 0xFF88FF44);
    }

    /** Name of the held block, fading out with the selection pulse. */
    private void drawHeldItemName(UIRenderer ui, FontRenderer font,
                                  Inventory inventory, int w, int hotbarY) {
        ItemStack held = inventory.getSelectedItem();
        if (held.isEmpty() || selectionPulse <= 0) return;

        int alpha = (int) (255 * Math.min(1.0, selectionPulse * 1.6));
        String name = prettyName(held.getBlockType());

        font.drawCenteredWithShadow(ui, name, w / 2, hotbarY - GuiAssets.HEART_SIZE - 12,
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
