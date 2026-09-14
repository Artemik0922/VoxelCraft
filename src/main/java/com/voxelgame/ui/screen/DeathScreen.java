package com.voxelgame.ui.screen;

import com.voxelgame.player.Player;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;

import static com.voxelgame.core.Language.tr;

/**
 * Shown when the player dies, over a red-tinted freeze frame of the world.
 */
public class DeathScreen extends Screen {

    public interface Callbacks {
        void onRespawn();
        void onTitleScreen();
    }

    private final Callbacks callbacks;
    private final Player.DeathCause cause;
    private final boolean hardcore;

    public DeathScreen(Player.DeathCause cause, boolean hardcore, Callbacks callbacks) {
        this.cause = cause;
        this.hardcore = hardcore;
        this.callbacks = callbacks;
    }

    @Override
    public boolean rendersWorld() { return true; }

    @Override
    public boolean closableWithEscape() { return false; }

    @Override
    protected void layout() {
        int bw = 220;
        int cx = (width - bw) / 2;
        int y = height / 2 + 20;

        if (!hardcore) {
            add(new Button(cx, y, bw, 22, tr("deathScreen.respawn"),
                b -> callbacks.onRespawn()));
        }
        add(new Button(cx, y + (hardcore ? 0 : 28), bw, 22,
            tr("deathScreen.titleScreen"), b -> callbacks.onTitleScreen()));
    }

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets tex) {
        MenuTheme.drawWorldOverlay(ui, width, height);
        // Red tint over the frozen world
        ui.fillRect(0, 0, width, height, 0x40880000);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {
        String title = tr("deathScreen.title");
        int scale = 2;
        int tw = font.scaledWidth(title, scale);

        // Soft red halo behind the title
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() * 0.002);
        float tx = (width - tw) / 2f;
        float ty = height / 2.0f - 50;
        for (int i = 3; i > 0; i--) {
            int a = (int) ((16 + 8 * pulse) / i) << 24;
            ui.fillRoundedRect(tx - 24 - i * 3, ty - 8 - i * 3,
                tw + 48 + i * 6, scale * FontRenderer.GLYPH_H + 16 + i * 6, 10,
                a | 0x880000);
        }

        font.drawScaledWithShadow(ui, title, tx, ty, scale, 0xFFFFFFFF);

        // Separator
        MenuTheme.drawSeparator(ui, 0xFF000000, width / 2f, ty + scale * FontRenderer.GLYPH_H + 8,
            tw / 2f + 20);

        // Cause of death
        String causeKey = switch (cause) {
            case FELL -> "death.fell";
            case DROWN -> "death.drown";
            case STARVE -> "death.starve";
            case LAVA -> "death.lava";
            case FIRE -> "death.fire";
            case CACTUS -> "death.cactus";
            case EXPLOSION -> "death.explosion";
            case MOB -> "death.mob";
            case VOID -> "death.void";
            case GENERIC -> null;
        };
        if (causeKey != null) {
            String causeText = tr(causeKey);
            font.drawCenteredWithShadow(ui, causeText,
                width / 2.0f, ty + scale * FontRenderer.GLYPH_H + 18,
                0xFF000000 | MenuTheme.TEXT_SECONDARY);
        }
    }
}
