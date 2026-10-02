package com.voxelgame.ui2;

import com.voxelgame.core.Settings;

import static com.voxelgame.core.Language.tr;

/** Sound settings: two live sliders on the bench, persisted on close. */
public class SoundSettingsScreen2 extends SettingsScene {

    public interface Listener {
        void onMusicVolume(float volume);

        void onSoundVolume(float volume);

        void onClosed();
    }

    private final Settings settings;
    private final Listener listener;

    public SoundSettingsScreen2(Settings settings, Listener listener, boolean overlayWorld) {
        super(overlayWorld);
        this.settings = settings;
        this.listener = listener;
    }

    @Override
    protected void buildContent() {
        int rowH = 22;
        int top = boardY + 16;
        int lw = colWidth();

        Slider2 music = new Slider2(tr("options.sound.music"), 0.0, 1.0,
            settings.musicVolume);
        music.x = colLeft();
        music.y = top;
        music.width = lw;
        music.listener((s, v) -> listener.onMusicVolume((float) v));
        add(music);

        Slider2 sound = new Slider2(tr("options.sound.sound"), 0.0, 1.0,
            settings.soundVolume);
        sound.x = colRight();
        sound.y = top;
        sound.width = lw;
        sound.listener((s, v) -> listener.onSoundVolume((float) v));
        add(sound);

        addDoneButton(tr("options.done"), listener::onClosed);
    }

    @Override
    protected String title() { return tr("options.sound.title"); }

    @Override
    public void onClosed() {
        settings.save();
    }
}
