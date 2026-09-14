package com.voxelgame.ui;

import com.voxelgame.core.Language;
import com.voxelgame.item.ItemStack;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.world.BlockType;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared item-stack drawing for every inventory surface: flat sprite icon,
 * stack count, tool durability bar, enchantment glint and the standard
 * tooltip (name, durability, enchant lines) — one implementation instead of
 * the four drifting per-screen copies.
 */
public final class StackIcons {

    /** Vanilla-style purple for enchantment text and glint. */
    public static final int ENCHANT = 0xFFB24BF3;
    private static final int GLINT_FILL = 0x28A050FF;
    private static final int GLINT_EDGE = 0x70A050FF;

    private StackIcons() {}

    // ------------------------------------------------------------------
    // Icons
    // ------------------------------------------------------------------

    /** Full slot contents: icon, glint, durability bar and count. */
    public static void drawStack(UIRenderer ui, FontRenderer font, TextureAtlas atlas,
                                 ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        drawIcon(ui, atlas, stack, x, y, GuiAssets.ICON_SIZE);
        if (stack.hasEnchantments()) drawGlint(ui, x, y, GuiAssets.ICON_SIZE);
        drawDurability(ui, stack, x, y);
        drawCount(ui, font, stack.getCount(), x, y);
    }

    /** Flat sprite icon: block tile, item sprite, or the item's block fallback. */
    public static void drawIcon(UIRenderer ui, TextureAtlas atlas, ItemStack stack,
                                int x, int y, int size) {
        if (stack == null || stack.isEmpty()) return;

        if (stack.isBlock()) {
            drawBlockTile(ui, atlas, stack.getBlockType(), x, y, size);
            return;
        }

        com.voxelgame.item.Item item = stack.getItem();
        if (item != null && item.spriteName != null) {
            int layer = atlas.getLayerOf(item.spriteName);
            if (layer >= 0) {
                TextureAtlas.TextureCoords uv = new TextureAtlas.TextureCoords(layer);
                ui.drawTexture(atlas.getTexture().getId(), x, y, size, size,
                    uv.u1, uv.v2, uv.u2, uv.v1, 0xFFFFFFFF);
                return;
            }
        }
        if (item != null && item.blockType != null) {
            drawBlockTile(ui, atlas, item.blockType, x, y, size);
            return;
        }
        // Last resort: grey chip so the slot is never empty-looking
        ui.useSolidColor();
        ui.fillRect(x + 2, y + 2, size - 4, size - 4, 0xFF8A93A6);
    }

    private static void drawBlockTile(UIRenderer ui, TextureAtlas atlas, BlockType block,
                                      int x, int y, int size) {
        TextureAtlas.TextureCoords uv = atlas.getCoords(block.id, 2);
        ui.drawTexture(atlas.getTexture().getId(), x, y, size, size,
            uv.u1, uv.v2, uv.u2, uv.v1, 0xFFFFFFFF);
    }

    /** Count in the bottom-right corner; hidden for single items. */
    public static void drawCount(UIRenderer ui, FontRenderer font, int count, int x, int y) {
        if (count < 2) return;
        String text = String.valueOf(count);
        font.drawWithShadow(ui, text,
            x + GuiAssets.SLOT_SIZE - font.width(text) - 1,
            y + GuiAssets.SLOT_SIZE - FontRenderer.GLYPH_H - 1,
            0xFFFFFFFF);
    }

    /** Green→yellow→red bar under damaged tools. */
    public static void drawDurability(UIRenderer ui, ItemStack stack, int x, int y) {
        int maxDur = stack.getMaxDurability();
        if (maxDur <= 0) return;
        float damage = 1.0f - (float) stack.getDurability() / maxDur;
        if (damage <= 0f) return;

        int barW = 14;
        int filled = (int) (barW * damage);
        int barX = x + 2;
        int barY = y + GuiAssets.SLOT_SIZE - 3;

        ui.useSolidColor();
        ui.drawRectOutline(barX, barY, barW, 2, 0xFF000000);
        int color = damage < 0.4f ? 0xFF55FF55 : (damage < 0.7f ? 0xFFFFFF55 : 0xFFFF5555);
        if (filled > 0) ui.drawRectOutline(barX, barY, filled, 2, color);
    }

    /** Soft purple shimmer border marking an enchanted item. */
    public static void drawGlint(UIRenderer ui, int x, int y, int size) {
        ui.useSolidColor();
        ui.fillRect(x, y, size, size, GLINT_FILL);
        ui.fillRect(x, y, size, 1, GLINT_EDGE);
        ui.fillRect(x, y + size - 1, size, 1, GLINT_EDGE);
        ui.fillRect(x, y, 1, size, GLINT_EDGE);
        ui.fillRect(x + size - 1, y, 1, size, GLINT_EDGE);
    }

    // ------------------------------------------------------------------
    // Tooltip
    // ------------------------------------------------------------------

    /** Standard tooltip: name, remaining durability, enchant lines. */
    public static void drawTooltip(UIRenderer ui, FontRenderer font, TextureAtlas atlas,
                                   ItemStack stack, float mx, float my,
                                   int screenW, int screenH) {
        if (stack == null || stack.isEmpty()) return;

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        lines.add(displayName(stack));
        colors.add(0xFFFFFFFF);

        int maxDur = stack.getMaxDurability();
        if (maxDur > 0 && stack.isDamaged()) {
            float damage = 1.0f - (float) stack.getDurability() / maxDur;
            lines.add(localized("ui.durability", "Durability: ")
                + stack.getDurability() + " / " + maxDur);
            colors.add(damage < 0.4f ? 0xFF55FF55 : (damage < 0.7f ? 0xFFFFFF55 : 0xFFFF5555));
        }

        if (stack.hasEnchantments() && stack.getEnchantments() != null) {
            for (var e : stack.getEnchantments().entrySet()) {
                String name = e.getKey().displayName;
                lines.add(e.getValue() > 1 ? name + " " + e.getValue() : name);
                colors.add(ENCHANT);
            }
        }

        GlassTooltip.draw(ui, font, lines, toArray(colors),
            (int) mx, (int) my, screenW, screenH);
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    /** Localized name with a readable Title-Case fallback. */
    public static String displayName(ItemStack stack) {
        if (stack == null) return "?";
        if (stack.isBlock()) return localized("block." + stack.getBlockType().name,
            pretty(stack.getBlockType().name));
        if (stack.isItem() && stack.getItem() != null) {
            return localized("item." + stack.getItem().name, stack.getItem().displayName);
        }
        return "?";
    }

    private static String localized(String key, String fallback) {
        String t = Language.tr(key);
        if (t != null && !t.isEmpty() && !t.equals(key)) return t;
        return fallback;
    }

    private static String pretty(String snake) {
        String[] parts = snake.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
