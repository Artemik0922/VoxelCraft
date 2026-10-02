package com.voxelgame.ui2;

import com.voxelgame.world.save.WorldMeta;
import com.voxelgame.world.save.WorldSave;

import static com.voxelgame.core.Language.tr;

/**
 * New world form, Workshop edition: a kraft-paper sheet with ink fields.
 * Name, game mode plank (with coloured badge and info line), an advanced
 * section with seed + structures behind a "more" plank. Enter creates.
 */
public class CreateWorldScreen2 extends Scene {

    public interface Callbacks {
        void onCreate(WorldMeta meta);

        void onCancel();
    }

    private final Callbacks callbacks;
    /** Prefilled when re-creating an existing world. */
    private final WorldMeta template;

    private TextField2 nameField;
    private TextField2 seedField;
    private Button2 modeButton;
    private Toggle2 structuresToggle;

    private WorldMeta.GameMode mode = WorldMeta.GameMode.SURVIVAL;
    private boolean showAdvanced = false;
    private int modeY;

    private int cardX, cardY, cardW, cardH;

    public CreateWorldScreen2(Callbacks callbacks) {
        this(callbacks, null);
    }

    public CreateWorldScreen2(Callbacks callbacks, WorldMeta template) {
        this.callbacks = callbacks;
        this.template = template;
        if (template != null) {
            this.mode = template.gameMode;
        }
    }

    @Override public boolean rendersWorld() { return false; }

    @Override
    protected void build() {
        cardW = Math.min(380, width - 40);
        cardX = (width - cardW) / 2;
        cardY = 36;
        cardH = showAdvanced ? 320 : 240;

        buildContent();
        setFocused(nameField);
    }

    protected void buildContent() {
        int pad = 16;
        int innerW = cardW - pad * 2;
        int innerX = cardX + pad;
        int y = cardY + pad + 34;

        nameField = add(new TextField2(innerW));
        nameField.x = innerX;
        nameField.y = y;
        nameField.placeholder(tr("createWorld.defaultName"));
        nameField.setText(template != null
            ? template.displayName + " (2)"
            : tr("createWorld.defaultName"));
        y += 30;

        modeButton = add(new Button2(modeLabel(), innerW, 24, this::cycleMode));
        modeButton.x = innerX;
        modeButton.y = y;
        modeY = y;
        y += 46;

        Button2 advanced = add(new Button2(tr("createWorld.more"), innerW, 22,
            () -> {
                showAdvanced = !showAdvanced;
                init(width, height);
            }));
        advanced.x = innerX;
        advanced.y = y;
        y += 28;

        if (showAdvanced) {
            seedField = add(new TextField2(innerW));
            seedField.x = innerX;
            seedField.y = y + 12;
            seedField.placeholder(tr("createWorld.seedInfo"));
            if (template != null) seedField.setText(Long.toString(template.seed));
            y += 42;

            structuresToggle = add(new Toggle2(tr("createWorld.structures"),
                template == null || template.generateStructures));
            structuresToggle.x = innerX;
            structuresToggle.y = y;
            structuresToggle.width = innerW;
            y += 30;
        }

        int by = cardY + cardH - 34;
        int bw = (cardW - pad * 2 - 12) / 2;
        Button2 create = add(new Button2(tr("createWorld.create"), bw, 24, this::create));
        create.x = innerX;
        create.y = by;

        Button2 cancel = add(new Button2(tr("gui.cancel"), bw, 24, callbacks::onCancel));
        cancel.x = innerX + bw + 12;
        cancel.y = by;
    }

    private String modeLabel() {
        return tr(mode.key);
    }

    private void cycleMode() {
        WorldMeta.GameMode[] all = WorldMeta.GameMode.values();
        mode = all[(mode.ordinal() + 1) % all.length];
        modeButton.label(modeLabel());
    }

    private void create() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = tr("createWorld.defaultName");

        long seed = resolveSeed();

        WorldMeta meta = new WorldMeta(name, seed, mode,
            structuresToggle == null || structuresToggle.getState());
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
    public boolean keyPressed(int key, int mods) {
        if ((key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
            || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)
            && (focused == null || focused instanceof TextField2)) {
            create();
            return true;
        }
        return super.keyPressed(key, mods);
    }

    @Override
    protected void renderBackground(UiDraw d) {
        d.wall(0, 0, width, height, 0xFF6B4A2B);
        d.fill(0, 0, width, height, 0x73261A10);

        // The form itself is a big kraft sheet, drawn under the fields
        d.paper(cardX, cardY, cardW, cardH);
        d.ui.drawRectOutline(cardX + d.offX + 2, cardY + d.offY + 2,
            cardW - 4, cardH - 4, d.aAlpha(0x502A1D12));

        String title = tr("createWorld.title");
        int stampW = d.font.width(title) + 20;
        d.paper(cardX + 8, cardY - 9, stampW, 18);
        d.fill(cardX + 14, cardY - 4, 3, 1, UiTheme.BRASS);
        d.fill(cardX + 15, cardY - 5, 1, 3, UiTheme.BRASS);
        d.fill(cardX + 15, cardY - 4, 1, 1, UiTheme.BRASS_LIGHT);
        d.text(title, cardX + 22, cardY - 4, UiTheme.INK);
    }

    @Override
    protected void renderForeground(UiDraw d) {
        int pad = 16;
        int innerX = cardX + pad;
        int y = cardY + pad + 34;

        d.text(tr("createWorld.name"), innerX, y - 10, UiTheme.INK_SOFT);
        y += 30;

        // Mode badge left of the mode plank
        int badgeColor = switch (mode) {
            case CREATIVE -> 0xFF4A7A3A;
            case HARDCORE -> 0xFFA03A24;
            default -> UiTheme.BRASS;
        };
        d.fill(innerX - 13, y + 7, 8, 8, badgeColor);
        d.ui.drawRectOutline(innerX - 13 + d.offX, y + 7 + d.offY, 8, 8,
            d.aAlpha(0xFF2A1D12));

        y += 30;
        String info = tr(mode.key + ".info");
        int infoCol = mode == WorldMeta.GameMode.HARDCORE ? UiTheme.EMBER : UiTheme.INK_SOFT;
        d.text(d.font.trimToWidth(info, cardW - pad * 2), innerX, modeY + 30, infoCol);

        if (showAdvanced) {
            d.text(tr("createWorld.seed"), innerX, modeY + 78, UiTheme.INK_SOFT);
        }
    }
}
