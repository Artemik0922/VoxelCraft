package com.voxelgame.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * User settings, persisted next to the game as a plain properties file.
 *
 * Values are clamped on load so a hand-edited or truncated file can never
 * put the renderer into an invalid state.
 */
public class Settings {

    private static final Path FILE = Paths.get("options.properties");

// Video
    public int renderDistance = 4;      // chunks (reduced for performance)
    public float fov = 70.0f;           // degrees
    public int guiScale = 0;            // 0 = auto
    public boolean vsync = true;
    public boolean clouds = true;
    public boolean ambientOcclusion = true;
    public boolean shadows = true;
    /** [PP] Post-processing: HDR bloom glow. */
    public boolean bloomEnabled = true;
    /** [PP] Post-processing: FXAA edge smoothing. */
    public boolean fxaaEnabled = false;

    /** [GR-065] 0 = Fast, 1 = Medium, 2 = Fancy, 3 = Ultra. */
    public int graphicsPreset = 2;

    /** Fancy draws leaves as cut-outs; Fast makes them solid. */
    public boolean fancyGraphics = true;
    public boolean viewBobbing = true;
    public boolean fullscreen = false;
    public float gamma = 0.5f;
    /** 0 = all, 1 = decreased, 2 = minimal. */
    public int particleLevel = 0;
    public float musicVolume = 1.0f;
    public float soundVolume = 1.0f;
/** 0 peaceful, 1 easy, 2 normal, 3 hard. */
    public int difficulty = 2;

    /** [menu theme] 0 = Sunset, 1 = AMOLED, 2 = Day, 3 = Night. */
    public int menuTheme = 0;

    /** Invert the mouse Y axis (push up = look down). */
    public boolean invertY = false;

    /** Length of a full day/night cycle in minutes. */
    public float dayLengthMinutes = 20.0f;

    /** How often the world is written to disk, in seconds. */
    public double autoSaveSeconds = 30.0;

// Controls
    public float mouseSensitivity = 0.10f;

    /** [UI-010] False until the first-launch tutorial toast has been shown. */
    public boolean tutorialSeen = false;

    /**
     * World seed. Blank means "pick a fresh one at startup"; the value that
     * was actually used is written back so a world can be revisited.
     */
    public String seed = "";

    /**
     * Folder name under resourcepacks/. Blank uses the bundled textures.
     * Packs are loaded as loose folders, in either the modern
     * assets/minecraft/textures/block or the legacy textures/blocks layout.
     */
    public String resourcePack = "";

    /** Interface language; Russian by default. */
    public String language = Language.DEFAULT;

    // First-person view model placement, tuned in game with F6
    public float handItemX = 0.58f, handItemY = -0.48f, handItemZ = -1.45f;
    public float handItemRotY = -25.0f, handItemRotX = 30.0f, handItemScale = 0.40f;
    public float handArmX = 0.68f, handArmY = -0.52f, handArmZ = -1.05f;
    public float handArmRotZ = -35.0f, handArmRotX = 15.0f;

    public static final int MIN_RENDER_DISTANCE = 2;
    public static final int MAX_RENDER_DISTANCE = 16;
    public static final float MIN_FOV = 30.0f;
    public static final float MAX_FOV = 110.0f;
    public static final float MIN_SENSITIVITY = 0.01f;
    public static final float MAX_SENSITIVITY = 0.50f;
    public static final int MAX_GUI_SCALE = 4;
    public static final float MIN_DAY_LENGTH = 4.0f;
    public static final float MAX_DAY_LENGTH = 120.0f;
    public static final double MIN_AUTOSAVE = 15.0;
    public static final double MAX_AUTOSAVE = 300.0;
    public static final int MENU_THEME_COUNT = 8;

    // ------------------------------------------------------------------

    /** Key bindings live here so they persist with everything else. */
    public final KeyBindings keyBindings = new KeyBindings();

    public static Settings load() {
        Settings s = new Settings();

        if (!Files.exists(FILE)) {
            System.out.println("No options file, using defaults");
            return s;
        }

        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
        } catch (IOException e) {
            System.err.println("Could not read options: " + e.getMessage());
            return s;
        }

        s.renderDistance = readInt(p, "renderDistance", s.renderDistance,
            MIN_RENDER_DISTANCE, MAX_RENDER_DISTANCE);
        s.fov = readFloat(p, "fov", s.fov, MIN_FOV, MAX_FOV);
        s.guiScale = readInt(p, "guiScale", s.guiScale, 0, MAX_GUI_SCALE);
s.mouseSensitivity = readFloat(p, "mouseSensitivity", s.mouseSensitivity,
            MIN_SENSITIVITY, MAX_SENSITIVITY);
        s.tutorialSeen = readBool(p, "tutorialSeen", s.tutorialSeen);

        s.seed = p.getProperty("seed", "").trim();
        s.resourcePack = p.getProperty("resourcePack", "").trim();
        s.language = p.getProperty("language", Language.DEFAULT).trim();
        s.keyBindings.load(p);

        s.handItemX = readFloat(p, "handItemX", s.handItemX, -4, 4);
        s.handItemY = readFloat(p, "handItemY", s.handItemY, -4, 4);
        s.handItemZ = readFloat(p, "handItemZ", s.handItemZ, -8, 0);
        s.handItemRotY = readFloat(p, "handItemRotY", s.handItemRotY, -360, 360);
        s.handItemRotX = readFloat(p, "handItemRotX", s.handItemRotX, -360, 360);
        s.handItemScale = readFloat(p, "handItemScale", s.handItemScale, 0.05f, 2.0f);
        s.handArmX = readFloat(p, "handArmX", s.handArmX, -4, 4);
        s.handArmY = readFloat(p, "handArmY", s.handArmY, -4, 4);
        s.handArmZ = readFloat(p, "handArmZ", s.handArmZ, -8, 0);
        s.handArmRotZ = readFloat(p, "handArmRotZ", s.handArmRotZ, -360, 360);
        s.handArmRotX = readFloat(p, "handArmRotX", s.handArmRotX, -360, 360);

s.vsync = readBool(p, "vsync", s.vsync);
        s.clouds = readBool(p, "clouds", s.clouds);
        s.ambientOcclusion = readBool(p, "ambientOcclusion", s.ambientOcclusion);
        s.shadows = readBool(p, "shadows", s.shadows);
        s.bloomEnabled = readBool(p, "bloomEnabled", s.bloomEnabled);
        s.fxaaEnabled = readBool(p, "fxaaEnabled", s.fxaaEnabled);
        s.graphicsPreset = readInt(p, "graphicsPreset", s.graphicsPreset, 0, 3);
        s.fancyGraphics = readBool(p, "fancyGraphics", s.fancyGraphics);
        s.viewBobbing = readBool(p, "viewBobbing", s.viewBobbing);
        s.fullscreen = readBool(p, "fullscreen", s.fullscreen);
        s.gamma = readFloat(p, "gamma", s.gamma, 0, 1);
        s.particleLevel = readInt(p, "particleLevel", s.particleLevel, 0, 2);
        s.musicVolume = readFloat(p, "musicVolume", s.musicVolume, 0, 1);
        s.soundVolume = readFloat(p, "soundVolume", s.soundVolume, 0, 1);
        s.difficulty = readInt(p, "difficulty", s.difficulty, 0, 3);
        s.menuTheme = readInt(p, "menuTheme", s.menuTheme, 0, MENU_THEME_COUNT - 1);
        s.invertY = readBool(p, "invertY", s.invertY);
        s.dayLengthMinutes = readFloat(p, "dayLengthMinutes",
            s.dayLengthMinutes, MIN_DAY_LENGTH, MAX_DAY_LENGTH);
        s.autoSaveSeconds = readFloat(p, "autoSaveSeconds",
            (float) s.autoSaveSeconds, (float) MIN_AUTOSAVE, (float) MAX_AUTOSAVE);

        System.out.println("Loaded options from " + FILE.toAbsolutePath());
        return s;
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("renderDistance", Integer.toString(renderDistance));
        p.setProperty("fov", Float.toString(fov));
        p.setProperty("guiScale", Integer.toString(guiScale));
        p.setProperty("mouseSensitivity", Float.toString(mouseSensitivity));
        p.setProperty("tutorialSeen", Boolean.toString(tutorialSeen));
        p.setProperty("seed", seed == null ? "" : seed);
        p.setProperty("resourcePack", resourcePack == null ? "" : resourcePack);
        p.setProperty("language", language == null ? Language.DEFAULT : language);
        keyBindings.save(p);

        p.setProperty("handItemX", Float.toString(handItemX));
        p.setProperty("handItemY", Float.toString(handItemY));
        p.setProperty("handItemZ", Float.toString(handItemZ));
        p.setProperty("handItemRotY", Float.toString(handItemRotY));
        p.setProperty("handItemRotX", Float.toString(handItemRotX));
        p.setProperty("handItemScale", Float.toString(handItemScale));
        p.setProperty("handArmX", Float.toString(handArmX));
        p.setProperty("handArmY", Float.toString(handArmY));
        p.setProperty("handArmZ", Float.toString(handArmZ));
        p.setProperty("handArmRotZ", Float.toString(handArmRotZ));
        p.setProperty("handArmRotX", Float.toString(handArmRotX));
p.setProperty("vsync", Boolean.toString(vsync));
        p.setProperty("clouds", Boolean.toString(clouds));
        p.setProperty("ambientOcclusion", Boolean.toString(ambientOcclusion));
        p.setProperty("shadows", Boolean.toString(shadows));
        p.setProperty("bloomEnabled", Boolean.toString(bloomEnabled));
        p.setProperty("fxaaEnabled", Boolean.toString(fxaaEnabled));
        p.setProperty("graphicsPreset", Integer.toString(graphicsPreset));
        p.setProperty("fancyGraphics", Boolean.toString(fancyGraphics));
        p.setProperty("viewBobbing", Boolean.toString(viewBobbing));
        p.setProperty("fullscreen", Boolean.toString(fullscreen));
        p.setProperty("gamma", Float.toString(gamma));
        p.setProperty("particleLevel", Integer.toString(particleLevel));
        p.setProperty("musicVolume", Float.toString(musicVolume));
        p.setProperty("soundVolume", Float.toString(soundVolume));
        p.setProperty("difficulty", Integer.toString(difficulty));
        p.setProperty("menuTheme", Integer.toString(menuTheme));
        p.setProperty("invertY", Boolean.toString(invertY));
        p.setProperty("dayLengthMinutes", Float.toString(dayLengthMinutes));
        p.setProperty("autoSaveSeconds", Float.toString((float) autoSaveSeconds));

        try (OutputStream out = Files.newOutputStream(FILE)) {
            p.store(out, "VoxelCraft options");
        } catch (IOException e) {
            System.err.println("Could not save options: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------

    private static int readInt(Properties p, String key, int fallback, int min, int max) {
        try {
            return clamp(Integer.parseInt(p.getProperty(key, String.valueOf(fallback)).trim()), min, max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float readFloat(Properties p, String key, float fallback, float min, float max) {
        try {
            return clamp(Float.parseFloat(p.getProperty(key, String.valueOf(fallback)).trim()), min, max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean readBool(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Boolean.parseBoolean(v.trim());
    }

    /**
     * Resolve the configured seed. Numeric input is used directly, anything
     * else is hashed like the real game does, and a blank value produces a
     * random world.
     */
    public long resolveSeed() {
        if (seed == null || seed.isBlank()) {
            return System.nanoTime() ^ (System.currentTimeMillis() << 16);
        }
        try {
            return Long.parseLong(seed.trim());
        } catch (NumberFormatException e) {
            return seed.trim().hashCode();
        }
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
