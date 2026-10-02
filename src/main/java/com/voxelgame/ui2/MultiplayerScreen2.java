package com.voxelgame.ui2;

import static com.voxelgame.core.Language.tr;

/**
 * Co-op join screen: name, host and port on a kraft sheet; connect opens
 * the world once the handshake completes. Status line shows progress and
 * errors.
 */
public class MultiplayerScreen2 extends Scene {

    public interface Callbacks {
        void onConnect(String name, String host, int port);

        void onBack();
    }

    private final Callbacks callbacks;

    private TextField2 nameField;
    private TextField2 hostField;
    private TextField2 portField;
    private String status = "";

    private int cardX, cardY, cardW, cardH;

    public MultiplayerScreen2(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    @Override public boolean rendersWorld() { return false; }

    /** Connection feedback (progress / errors) shown at the bottom. */
    public void setStatus(String s) { this.status = s; }

    @Override
    protected void build() {
        cardW = Math.min(380, width - 40);
        cardX = (width - cardW) / 2;
        cardY = 36;
        cardH = 236;

        int pad = 16;
        int innerW = cardW - pad * 2;
        int innerX = cardX + pad;
        int y = cardY + pad + 34;

        nameField = add(new TextField2(innerW));
        nameField.x = innerX;
        nameField.y = y;
        nameField.placeholder("Player");
        nameField.setText("Player");
        y += 30;

        hostField = add(new TextField2(innerW));
        hostField.x = innerX;
        hostField.y = y;
        hostField.placeholder("localhost");
        hostField.setText("localhost");
        y += 30;

        portField = add(new TextField2(innerW));
        portField.x = innerX;
        portField.y = y;
        portField.placeholder("25565");
        portField.setText("25565");
        y += 32;

        Button2 connect = add(new Button2(tr("menu.multiplayer.connect"), innerW, 24,
            this::connect));
        connect.x = innerX;
        connect.y = y;

        Button2 back = add(new Button2(tr("menu.multiplayer.back"), innerW, 24, callbacks::onBack));
        back.x = innerX;
        back.y = cardY + cardH - 34;

        setFocused(nameField);
    }

    private void connect() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = "Player";
        String host = hostField.getText().trim();
        if (host.isEmpty()) host = "localhost";
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            port = com.voxelgame.net.Net.DEFAULT_PORT;
        }
        setStatus(tr("menu.multiplayer.connecting", host, port));
        callbacks.onConnect(name, host, port);
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
            || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            connect();
            return true;
        }
        return super.keyPressed(key, mods);
    }

    @Override
    protected void renderBackground(UiDraw d) {
        d.wall(0, 0, width, height, 0xFF6B4A2B);
        d.fill(0, 0, width, height, 0x73261A10);

        d.paper(cardX, cardY, cardW, cardH);
        d.ui.drawRectOutline(cardX + d.offX + 2, cardY + d.offY + 2,
            cardW - 4, cardH - 4, d.aAlpha(0x502A1D12));
        d.cornerRivets(cardX, cardY, cardW, cardH);

        String title = tr("menu.multiplayer");
        int stampW = d.font.width(title) + 20;
        d.paper(cardX + 8, cardY - 9, stampW, 18);
        d.fill(cardX + 14, cardY - 4, 3, 1, UiTheme.BRASS);
        d.fill(cardX + 15, cardY - 5, 1, 3, UiTheme.BRASS);
        d.fill(cardX + 15, cardY - 4, 1, 1, UiTheme.BRASS_LIGHT);
        d.text(title, cardX + 22, cardY - 4, UiTheme.INK);
    }

    @Override
    protected void renderForeground(UiDraw d) {
        if (!status.isEmpty()) {
            d.textShadow(d.font.trimToWidth(status, cardW - 32),
                cardX + 16, cardY + cardH + 8, UiTheme.BRASS_LIGHT);
        }
    }
}
