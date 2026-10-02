package com.voxelgame.ui2;

import com.voxelgame.player.Player;
import com.voxelgame.ui.FontRenderer;

import static com.voxelgame.core.Language.tr;

/**
 * Death screen over the frozen, red-tinted world: ember-stamped title with
 * a pulsing halo, the cause of death on a brass line, plank actions.
 */
public class DeathScreen2 extends Scene {

    public interface Callbacks {
        void onRespawn();

        void onTitleScreen();
    }

    private final Callbacks callbacks;
    private final Player.DeathCause cause;
    private final boolean hardcore;

    public DeathScreen2(Player.DeathCause cause, boolean hardcore, Callbacks callbacks) {
        this.cause = cause;
        this.hardcore = hardcore;
        this.callbacks = callbacks;
    }

    @Override public boolean rendersWorld() { return true; }

    @Override public boolean closableWithEscape() { return false; }

    @Override
    protected void build() {
        int bw = Math.min(240, width - 40);
        int bx = (width - bw) / 2;
        int y = height / 2 + 26;
        float delay = 0.15f;

        if (!hardcore) {
            Button2 respawn = add(new Button2(tr("deathScreen.respawn"), bw, 26,
                callbacks::onRespawn));
            respawn.x = bx;
            respawn.y = y;
            respawn.appearDelay = delay;
            y += 32;
            delay += 0.08f;
        }

        Button2 title = add(new Button2(tr("deathScreen.titleScreen"), bw, 26,
            callbacks::onTitleScreen));
        title.x = bx;
        title.y = y;
        title.appearDelay = delay;
    }

    @Override
    protected void renderBackground(UiDraw d) {
        if (com.voxelgame.ui.MenuTheme.blurredBackdrop != 0) {
            d.ui.drawTexture(com.voxelgame.ui.MenuTheme.blurredBackdrop, 0, 0, width, height);
        }
        d.fill(0, 0, width, height, 0x6C880000);
    }

    @Override
    protected void renderForeground(UiDraw d) {
        String titleText = tr("deathScreen.title");
        int scale = 2;
        int tw = d.font.scaledWidth(titleText, scale);
        float tx = (width - tw) / 2f;
        float ty = height / 2.0f - 50;

        // Pulsing ember halo behind the title
        float pulse = 0.5f + 0.5f * (float) Math.sin(d.time * 2.0);
        for (int i = 3; i > 0; i--) {
            int a = (int) ((16 + 8 * pulse) / i) << 24;
            d.ui.fillRoundedRect(tx - 24 - i * 3, ty - 8 - i * 3,
                tw + 48 + i * 6, scale * FontRenderer.GLYPH_H + 16 + i * 6, 10,
                d.aAlpha(a | 0x88331A));
        }

        d.textScaledCentered(titleText, width / 2f, ty, scale, 0xFFF2E6C8);

        int lineY = Math.round(ty) + scale * FontRenderer.GLYPH_H + 8;
        d.brassLine(width / 2f - tw / 2f - 10, lineY, tw + 20);

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
            d.textCentered(tr(causeKey), width / 2f, lineY + 10, 0xFFD8C49A);
        }
    }
}
