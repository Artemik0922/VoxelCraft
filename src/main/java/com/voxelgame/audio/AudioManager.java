package com.voxelgame.audio;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;

/**
 * OpenAL playback manager.
 *
 * A small pool of sources plays one-shot effects (currently footstep
 * variants). The listener sits at the player's head so future positional
 * effects get their volume/panning for free.
 */
public class AudioManager {

    private static long device;
    private static long context;
    private static boolean available = false;

    private static final int SOURCE_COUNT = 8;
    private static int[] sources;
    private static int nextSource = 0;

    /** Combined volume applied to every effect, set from the options screen. */
    private static float soundVolume = 1.0f;

    public static boolean isAvailable() { return available; }

    /** Open the default device and create the source pool. */
    public static void init() {
        try {
            device = alcOpenDevice((ByteBuffer) null);
            if (device == 0L) {
                System.err.println("AudioManager: no OpenAL device available, sound disabled");
                return;
            }

            ALCCapabilities alcCaps = ALC.createCapabilities(device);
            context = alcCreateContext(device, (IntBuffer) null);
            if (context == 0L) {
                System.err.println("AudioManager: could not create OpenAL context, sound disabled");
                alcCloseDevice(device);
                return;
            }
            if (!alcMakeContextCurrent(context)) {
                System.err.println("AudioManager: could not make context current, sound disabled");
                alcDestroyContext(context);
                alcCloseDevice(device);
                return;
            }

            AL.createCapabilities(alcCaps);

            sources = new int[SOURCE_COUNT];
            alGenSources(sources);
            for (int src : sources) {
                // Effects play at the head: no distance attenuation, no doppler
                alSourcei(src, AL_SOURCE_RELATIVE, AL_TRUE);
                alSource3f(src, AL_POSITION, 0, 0, 0);
                alSourcef(src, AL_ROLLOFF_FACTOR, 0.0f);
            }

            available = true;
            System.out.println("AudioManager: OpenAL ready, "
                + alGetString(AL_VERSION) + " / " + alcGetString(device, ALC_DEVICE_SPECIFIER));
        } catch (Throwable t) {
            System.err.println("AudioManager: init failed (" + t + "), sound disabled");
            available = false;
        }
    }

    /** Put the ears at the camera: position plus forward/up orientation. */
    public static void setListener(float px, float py, float pz,
                                   float fx, float fy, float fz,
                                   float ux, float uy, float uz) {
        if (!available) return;
        alListener3f(AL_POSITION, px, py, pz);
        alListenerfv(AL_ORIENTATION, new float[] { fx, fy, fz, ux, uy, uz });
    }

    public static void setSoundVolume(float volume) {
        soundVolume = volume;
    }

    /**
     * Play a random clip from the given resource folder.
     *
     * @param folder resource path like "sounds/steps/grass"
     * @param pitch  playback rate multiplier (1.0 = as recorded)
     * @param gain   relative loudness (already includes the master volume)
     */
    public static void play(String folder, float pitch, float gain) {
        if (!available || soundVolume <= 0.001f) return;

        int buffer = SoundLoader.randomBuffer(folder);
        if (buffer == 0) return;

        int src = sources[nextSource];
        nextSource = (nextSource + 1) % sources.length;

        // Restart the source even if it was still playing, so a fast walk
        // never loses a footstep to an occupied channel
        alSourceStop(src);
        alSourcei(src, AL_BUFFER, buffer);
        alSourcef(src, AL_PITCH, pitch);
        alSourcef(src, AL_GAIN, gain * soundVolume);
        alSourcePlay(src);
    }

    public static void destroy() {
        if (!available) return;
        alDeleteSources(sources);
        SoundLoader.cleanup();
        alcMakeContextCurrent(0L);
        alcDestroyContext(context);
        alcCloseDevice(device);
        available = false;
        System.out.println("AudioManager: closed");
    }
}
