package com.voxelgame.ui.widget;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.GuiAssets;

/**
 * A themed button that matches the sunset/twilight visual language of the
 * main menu. Supports the classic nine-slice look (when {@link #usePanelStyle}
 * is false, the old neon style) or the new bevelled panel look.
 */
public class Button extends Widget {

    public interface Action {
        void onPress(Button source);
    }

    private String label;
    private final Action action;

    /** Whether to use the bevelled-panel style (default) instead of the
     *  old neon glow. The neon style is preserved for screens that have not
     *  been migrated yet. */
    private boolean usePanelStyle = true;

    public Button(int x, int y, int width, int height, String label, Action action) {
        super(x, y, width, height);
        this.label = label;
        this.action = action;
    }

    public void setLabel(String label) { this.label = label; }
    public String getLabel() { return label; }

    public void setPanelStyle(boolean on) { this.usePanelStyle = on; }

    /** Animated hover amount 0..1, eased in {@link #update}. */
    public float hoverProgress() { return hoverProgress; }

    private float hoverProgress = 0;

    @Override
    public void update(double deltaTime) {
        float target = hovered ? 1.0f : 0.0f;
        hoverProgress += (target - hoverProgress) * (float) Math.min(1.0, deltaTime * 12);
    }

    @Override
    public void render(UIRenderer ui, FontRenderer font, GuiAssets tex, float mx, float my) {
        if (!visible) return;

        int fadeAlpha = (alpha >>> 24) & 0xFF;
        int textY = y + (height - FontRenderer.GLYPH_H) / 2;

        if (usePanelStyle) {
            renderPanelStyle(ui, font, fadeAlpha, textY);
        } else {
            renderNeonStyle(ui, font, tex, fadeAlpha, textY);
        }
    }

    private void renderPanelStyle(UIRenderer ui, FontRenderer font, int fadeAlpha, int textY) {
        if (!enabled) {
            // Dim glass panel
            ui.drawNineSlice(GuiAssets.INSTANCE.glassShadow, x + 2, y + 3, width, height,
                GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET,
                MenuTheme.col(fadeAlpha << 24, 255, MenuTheme.GLASS_SHADOW));
            ui.drawNineSlice(GuiAssets.INSTANCE.glassPanel, x, y, width, height,
                GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET,
                MenuTheme.col(fadeAlpha << 24, 90, MenuTheme.GLASS_ACTIVE));
            font.drawCenteredWithShadow(ui, label,
                x + width / 2.0f, textY,
                MenuTheme.col(fadeAlpha << 24, 160, MenuTheme.TEXT_DIM));
            return;
        }

        MenuTheme.drawPanel(ui, fadeAlpha << 24, x, y, width, height, hoverProgress);

        int textCol = MenuTheme.lerp(MenuTheme.TEXT_LABEL, MenuTheme.TEXT_BRIGHT, hoverProgress);
        textCol = (fadeAlpha << 24) | (textCol & 0xFFFFFF);
        font.drawCenteredWithShadow(ui, label, x + width / 2.0f, textY, textCol);

        if (hoverProgress > 0.05f) {
            int a = (int) (hoverProgress * 255);
            font.drawWithShadow(ui, ">", x + width - 12, textY,
                (a << 24) | (MenuTheme.ACCENT & 0xFFFFFF));
        }
    }

    private void renderNeonStyle(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                  int fadeAlpha, int textY) {
        int TEXT_NORMAL = 0xFFE0E0E0;
        int TEXT_HOVER = 0xFFFFFFFF;
        int TEXT_DISABLED = 0xFF666666;
        int BUTTON_FILL_NORMAL = 0x22FFFFFF;
        int BUTTON_FILL_HOVER = 0x4400FFFF;
        int BUTTON_BORDER_NORMAL = 0x66FFFFFF;
        int BUTTON_BORDER_HOVER = 0xFF00FFFF;
        int GLOW_COLOR = 0xFF00FFFF;

        if (!enabled) {
            int a = (int) (fadeAlpha / 255.0f * 0x11);
            ui.fillRoundedRect(x, y, width, height, 5, (a << 24) | 0xFFFFFF);
            ui.drawNeonBorder(x, y, width, height, (int) (fadeAlpha / 255.0f * 0x33) << 24 | 0x333333);
            font.drawCenteredWithShadow(ui, label,
                    x + width / 2.0f, textY,
                    (int) (fadeAlpha / 255.0f * 0x66) << 24 | 0x666666);
            return;
        }

        float centerX = x + width / 2.0f;
        float centerY = y + height / 2.0f;
        float scaledW = width * (1.0f + hoverProgress * 0.03f);
        float scaledH = height * (1.0f + hoverProgress * 0.03f);
        float drawX = centerX - scaledW / 2;
        float drawY = centerY - scaledH / 2;

        if (hoverProgress > 0.01f) {
            float glowIntensity = hoverProgress * (0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() * 0.003));
            ui.drawGlow(drawX, drawY, scaledW, scaledH, GLOW_COLOR, glowIntensity * fadeAlpha / 255.0f);
        }

        int bgFill = MenuTheme.lerp(BUTTON_FILL_NORMAL, BUTTON_FILL_HOVER, hoverProgress);
        int borderCol = MenuTheme.lerp(BUTTON_BORDER_NORMAL, BUTTON_BORDER_HOVER, hoverProgress);

        bgFill = (int) (((bgFill >>> 24) / 255.0f) * fadeAlpha) << 24 | (bgFill & 0xFFFFFF);
        borderCol = (int) (((borderCol >>> 24) / 255.0f) * fadeAlpha) << 24 | (borderCol & 0xFFFFFF);

        ui.fillRoundedRect(drawX, drawY, scaledW, scaledH, 5, bgFill);
        ui.drawNeonBorder(drawX, drawY, scaledW, scaledH, borderCol);

        int textColor = MenuTheme.lerp(TEXT_NORMAL, TEXT_HOVER, hoverProgress);
        textColor = (int) (((textColor >>> 24) / 255.0f) * fadeAlpha) << 24 | (textColor & 0xFFFFFF);

        font.drawCenteredWithShadow(ui, label, drawX + scaledW / 2.0f, textY, textColor);
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (!visible || !enabled || button != 0 || !contains(mx, my)) return false;
        if (action != null) action.onPress(this);
        return true;
    }
}
