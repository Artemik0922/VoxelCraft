package com.voxelgame.ui.screen;

import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.widget.Button;
import com.voxelgame.ui.widget.TextField;
import com.voxelgame.ui.widget.Toggle;
import com.voxelgame.world.save.WorldMeta;
import com.voxelgame.world.save.WorldSave;

import static com.voxelgame.core.Language.tr;

/**
 * New world setup: name, game mode, seed, and structure toggle.
 *
 * The whole form sits inside a card panel with section separators, a
 * highlighted preview of the selected game mode, and themed inputs.
 */
public class CreateWorldScreen extends Screen {

    public interface Callbacks {
        void onCreate(WorldMeta meta);
        void onCancel();
    }

    private final Callbacks callbacks;

    private TextField nameField;
    private TextField seedField;
    private Button modeButton;
    private Toggle structuresToggle;
    private Button advancedButton;

    private WorldMeta.GameMode mode = WorldMeta.GameMode.SURVIVAL;
    private boolean showAdvanced = false;

    /** Prefilled when re-creating an existing world. */
    private final WorldMeta template;

    public CreateWorldScreen(Callbacks callbacks) {
        this(callbacks, null);
    }

    public CreateWorldScreen(Callbacks callbacks, WorldMeta template) {
        this.callbacks = callbacks;
        this.template = template;
        if (template != null) {
            this.mode = template.gameMode;
        }
    }

    @Override
    public boolean rendersWorld() { return false; }

    @Override
    protected void layout() {
        int cardW = Math.min(360, width - 40);
        int cardX = (width - cardW) / 2;
        int cardTop = 36;
        int pad = 16;
        int innerW = cardW - pad * 2;
        int innerX = cardX + pad;

        int y = cardTop + pad + 32; // title + separator

        // Name field
        nameField = add(new TextField(innerX, y, innerW, 20, 48));
        nameField.setPlaceholder(tr("createWorld.defaultName"));
        nameField.setText(template != null
            ? template.displayName + " (2)"
            : tr("createWorld.defaultName"));
        y += 26;

        // Game mode cycle button with current mode info
        modeButton = add(new Button(innerX, y, innerW, 22, modeLabel(),
            b -> cycleMode()));
        y += 28;

        // Advanced toggle
        advancedButton = add(new Button(innerX, y, innerW, 22,
            tr("createWorld.more"), b -> {
                showAdvanced = !showAdvanced;
                resize(width, height);
            }));

        if (showAdvanced) {
            y += 28;
            seedField = add(new TextField(innerX, y, innerW, 20, 32));
            seedField.setPlaceholder(tr("createWorld.seedInfo"));
            if (template != null) seedField.setText(Long.toString(template.seed));

            y += 26;
            structuresToggle = add(new Toggle(innerX, y, innerW, 22,
                tr("createWorld.structures"),
                template == null || template.generateStructures, (t, v) -> {}));
            y += 28;
        } else {
            y += 28;
        }

        // Bottom buttons
        int by = height - 32;
        int bw = (cardW - 12) / 2;
        add(new Button(cardX + pad, by, bw, 22, tr("createWorld.create"), b -> create()));
        add(new Button(cardX + pad + bw + 12, by, bw, 22, tr("gui.cancel"),
            b -> callbacks.onCancel()));
    }

    private String modeLabel() {
        return tr("options.difficulty").isEmpty()
            ? tr(mode.key)
            : tr(mode.key);
    }

    private void cycleMode() {
        WorldMeta.GameMode[] all = WorldMeta.GameMode.values();
        mode = all[(mode.ordinal() + 1) % all.length];
        modeButton.setLabel(modeLabel());
    }

    private void create() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = tr("createWorld.defaultName");

        long seed = resolveSeed();

        WorldMeta meta = new WorldMeta(name, seed, mode,
            structuresToggle == null || structuresToggle.getValue());
        if (mode == WorldMeta.GameMode.HARDCORE) {
            meta.difficulty = 2;
        }
        meta.folderName = WorldSave.uniqueFolderName(name);

        callbacks.onCreate(meta);
    }

    private long resolveSeed() {
        if (seedField == null) return System.nanoTime();

        String s = seedField.getText().trim();
        if (s.isEmpty()) return System.nanoTime();

        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return s.hashCode();
        }
    }

    @Override
    public void update(double deltaTime) {
        if (nameField != null) nameField.update(deltaTime);
        if (seedField != null) seedField.update(deltaTime);
    }

    @Override
    protected void renderBackground(UIRenderer ui, FontRenderer font, GuiAssets tex) {
        MenuTheme.drawBackdrop(ui, width, height, 0);
        // Draw card BEFORE widgets so it doesn't darken them with its
        // semi-transparent fill (was previously drawn in renderForeground).
        int cardW = Math.min(360, width - 40);
        int cardX = (width - cardW) / 2;
        int cardTop = 36;
        // Calculate card height based on what's visible
        int cardH = showAdvanced ? 290 : 220;
        MenuTheme.drawCard(ui, 0xFF000000, cardX, cardTop, cardW, cardH);
    }

    @Override
    protected void renderForeground(UIRenderer ui, FontRenderer font, GuiAssets tex,
                                    float mx, float my) {
        int cardW = Math.min(360, width - 40);
        int cardX = (width - cardW) / 2;
        int cardTop = 36;
        int pad = 16;
        int innerW = cardW - pad * 2;
        int innerX = cardX + pad;

        // Title
        String title = tr("createWorld.title");
        int tw = font.scaledWidth(title, 2);
        font.drawScaledWithShadow(ui, title, (width - tw) / 2f, cardTop + 8, 2, 0xFFFFFFFF);
        MenuTheme.drawSeparator(ui, 0xFF000000, width / 2f, cardTop + 30, tw / 2f + 10);
        int y = cardTop + pad + 32;

        // Name field label
        font.drawWithShadow(ui, tr("createWorld.name"),
            innerX, y - 10, 0xFF000000 | MenuTheme.TEXT_SECONDARY);
        y += 26; // past name field

        // Mode button is here (y to y+22). Draw badge on its left edge.
        drawModeBadge(ui, font, innerX, y);
        y += 28; // past mode button

        // Mode info text below the button
        String infoKey = mode.key + ".info";
        String info = tr(infoKey);
        int infoCol = mode == WorldMeta.GameMode.HARDCORE
            ? MenuTheme.TEXT_WARNING : MenuTheme.TEXT_SECONDARY;
        font.drawWithShadow(ui, font.trimToWidth(info, innerW),
            innerX, y - 4, 0xFF000000 | (infoCol & 0xFFFFFF));

        if (showAdvanced) {
            font.drawWithShadow(ui, tr("createWorld.seed"),
                innerX, y + 20, 0xFF000000 | MenuTheme.TEXT_SECONDARY);
        }
    }

    /** Draw a small colored square indicating the game mode. */
    private void drawModeBadge(UIRenderer ui, FontRenderer font, int x, int y) {
        int color;
        switch (mode) {
            case CREATIVE -> color = 0xFF4CAF50;
            case HARDCORE -> color = 0xFFE53935;
            default -> color = MenuTheme.ACCENT;
        }
        ui.fillRect(x - 14, y + 4, 10, 10, 0xFF000000 | color);
        ui.drawRectOutline(x - 14, y + 4, 10, 10,
            0xFF000000 | MenuTheme.PANEL_BORDER);
    }

    /** Typed characters are routed here by Game. */
    public boolean charTyped(char c) {
        if (nameField != null && nameField.charTyped(c)) return true;
        if (seedField != null && seedField.charTyped(c)) return true;
        return false;
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
            || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            create();
            return true;
        }
        return super.keyPressed(key, mods);
    }
}
