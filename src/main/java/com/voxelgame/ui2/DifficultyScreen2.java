package com.voxelgame.ui2;

import com.voxelgame.core.Settings;

import static com.voxelgame.core.Language.tr;

/** Difficulty picker: choices apply live and close the scene. */
public class DifficultyScreen2 extends SettingsScene {

    public interface Listener {
        void onDifficultyChanged(int difficulty);

        void onClosed();
    }

    private static final int[] LEVELS = {0, 1, 2, 3};
    private static final String[] KEYS = {
        "options.difficulty.peaceful",
        "options.difficulty.easy",
        "options.difficulty.normal",
        "options.difficulty.hard"
    };
    private static final boolean[] DANGER = {false, false, false, true};

    private final Settings settings;
    private final Listener listener;
    private final boolean locked;

    public DifficultyScreen2(Settings settings, Listener listener,
                             boolean overlayWorld, boolean locked) {
        super(overlayWorld);
        this.settings = settings;
        this.listener = listener;
        this.locked = locked;
    }

    @Override
    protected void buildContent() {
        int rowH = 24;
        int gap = 6;
        int bw = Math.min(240, boardW - 28);
        int top = boardY + 16;

        for (int i = 0; i < LEVELS.length; i++) {
            int level = LEVELS[i];
            boolean active = settings.difficulty == level;
            boolean disabled = locked && level != 2;
            String label = (active ? "\u25B6 " : "") + (disabled ? "\u2717 " : "")
                + tr(KEYS[i]);

            final int chosen = level;
            Button2 b = add(new Button2(label, bw, rowH,
                disabled ? null : () -> {
                    listener.onDifficultyChanged(chosen);
                    listener.onClosed();
                }));
            b.x = boardX + (boardW - bw) / 2;
            b.y = top + i * (rowH + gap);
            b.danger = DANGER[i] && active;
            b.appearDelay = 0.08f * i;
        }

        addDoneButton(tr("options.done"), listener::onClosed);
    }

    @Override
    protected String title() { return tr("options.difficulty.title"); }

    @Override
    public void onClosed() {
        settings.save();
    }
}
