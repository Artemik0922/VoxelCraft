package com.voxelgame.ui.screen;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;
import com.voxelgame.ui.widget.TextField;

/**
 * Co-op join screen: player name, server host, and port. Connect opens the
 * world once the server handshake completes; Back returns to the main menu.
 */
public class MultiplayerScreen extends Screen {

    public interface Callbacks {
        void onConnect(String name, String host, int port);
        void onBack();
    }

    private final Callbacks callbacks;

    private TextField nameField;
    private TextField hostField;
    private TextField portField;
    private String status = "";

    public MultiplayerScreen(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    public boolean rendersWorld() { return false; }

    /** Show connection feedback (progress / errors) at the bottom of the card. */
    public void setStatus(String s) { this.status = s; }

    @Override
    protected void layout() {
        int cardW = Math.min(340, width - 40);
        int cardX = (width - cardW) / 2;
        int cardTop = 36;
        int pad = 16;
        int innerW = cardW - pad * 2;
        int innerX = cardX + pad;
        int y = cardTop + pad + 32;

        nameField = add(new TextField(innerX, y, innerW, 20, 24));
        nameField.setPlaceholder("Player");
        nameField.setText("Player");
        y += 26;

        hostField = add(new TextField(innerX, y, innerW, 20, 64));
        hostField.setPlaceholder("localhost");
        hostField.setText("localhost");
        y += 26;

        portField = add(new TextField(innerX, y, innerW, 20, 10));
        portField.setPlaceholder("25565");
        portField.setText("25565");
        y += 28;

        add(new Button(innerX, y, innerW, 22, "Подключиться", b -> connect()));
        y += 28;

        int by = height - 32;
        int bw = (cardW - 12) / 2;
        add(new Button(cardX + pad, by, bw, 22, "Назад", b -> callbacks.onBack()));
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
        setStatus("Подключение к " + host + ":" + port + "…");
        callbacks.onConnect(name, host, port);
    }

    @Override
    public void update(double deltaTime) {
        if (nameField != null) nameField.update(deltaTime);
        if (hostField != null) hostField.update(deltaTime);
        if (portField != null) portField.update(deltaTime);
    }

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets tex) {
        MenuTheme.drawBackdrop(ui, width, height, 0);
        int cardW = Math.min(340, width - 40);
        MenuTheme.drawCard(ui, 0xFF000000, (width - cardW) / 2, 36, cardW, 230);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {
        int cardW = Math.min(340, width - 40);
        int cardX = (width - cardW) / 2;
        String title = "Сетевая игра";
        int tw = font.scaledWidth(title, 2);
        font.drawScaledWithShadow(ui, title, (width - tw) / 2f, 44, 2, 0xFFFFFFFF);
        MenuTheme.drawSeparator(ui, 0xFF000000, width / 2f, 66, tw / 2f + 10);

        if (!status.isEmpty()) {
            font.drawWithShadow(ui, font.trimToWidth(status, cardW - 32),
                cardX + 16, height - 44, 0xFF000000 | MenuTheme.TEXT_WARNING);
        }
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

    /** Typed characters are routed here by Game. */
    public boolean charTyped(char c) {
        if (nameField != null && nameField.charTyped(c)) return true;
        if (hostField != null && hostField.charTyped(c)) return true;
        if (portField != null && portField.charTyped(c)) return true;
        return false;
    }
}
