package com.voxelgame.rendering;

import org.joml.Vector3f;

/**
 * Day/night cycle.
 *
 * Time runs 0..1 over one full day, with 0 at sunrise. A 20 minute day
 * matches the real game's pacing.
 *
 * Everything the renderer needs - sun direction, sky palette, light level -
 * is derived here so the skybox, fog and world shading cannot drift apart.
 */
public class DayNightCycle {

    /** Real seconds per in-game day. */
    private static final double DEFAULT_DAY_LENGTH = 20 * 60;

    private double dayLength = DEFAULT_DAY_LENGTH;

    // Sky palette keyframes, sampled by time of day
    private static final Vector3f NIGHT_ZENITH  = new Vector3f(0.02f, 0.03f, 0.10f);
    private static final Vector3f NIGHT_HORIZON = new Vector3f(0.05f, 0.07f, 0.16f);
    private static final Vector3f DAWN_ZENITH   = new Vector3f(0.22f, 0.34f, 0.62f);
    private static final Vector3f DAWN_HORIZON  = new Vector3f(0.92f, 0.52f, 0.30f);
    private static final Vector3f DAY_ZENITH    = new Vector3f(0.35f, 0.60f, 0.90f);
    private static final Vector3f DAY_HORIZON   = new Vector3f(0.53f, 0.81f, 0.92f);

    private static final Vector3f SUN_DAY   = new Vector3f(1.00f, 0.97f, 0.90f);
    private static final Vector3f SUN_DAWN  = new Vector3f(1.00f, 0.72f, 0.45f);
    private static final Vector3f MOON_TINT = new Vector3f(0.55f, 0.62f, 0.80f);

    private double time;
    private boolean paused = false;

    // Reused so the render loop allocates nothing
    private final Vector3f sunDirection = new Vector3f();
    private final Vector3f moonDirection = new Vector3f();
    private final Vector3f zenithColor = new Vector3f();
    private final Vector3f horizonColor = new Vector3f();
    private final Vector3f sunColor = new Vector3f();

    public DayNightCycle() {
        // Start mid-morning so a fresh world opens in daylight
        this.time = 0.25;
        recompute();
    }

    public void update(double deltaSeconds) {
        if (!paused) {
            time = (time + deltaSeconds / dayLength) % 1.0;
        }
        recompute();
    }

    /** [options.dayLength] Override the day/night cycle length in seconds. */
    public void setDayLengthSeconds(double seconds) {
        dayLength = Math.max(1.0, seconds);
    }

    public double getDayLengthSeconds() { return dayLength; }

    // ------------------------------------------------------------------

    public double getTime() { return time; }

    public void setTime(double t) {
        this.time = ((t % 1.0) + 1.0) % 1.0;
        recompute();
    }

    public void setPaused(boolean paused) { this.paused = paused; }
    public boolean isPaused() { return paused; }

    public Vector3f getSunDirection() { return sunDirection; }
    public Vector3f getMoonDirection() { return moonDirection; }
    public Vector3f getZenithColor() { return zenithColor; }
    public Vector3f getHorizonColor() { return horizonColor; }
    public Vector3f getSunColor() { return sunColor; }

    /** 0 at midnight, 1 in full day - drives how much sunlight reaches blocks. */
    public float getDaylight() {
        // -sunDirection.y is the sun's height above the horizon
        float elevation = -sunDirection.y;
        return clamp01((elevation + 0.15f) / 0.45f);
    }

    public boolean isNight() { return getDaylight() < 0.25f; }

    /** Formatted as a 24 hour clock, for the debug overlay. */
    public String getClock() {
        // time 0 is sunrise, which is 06:00
        double hours = (time * 24.0 + 6.0) % 24.0;
        int h = (int) hours;
        int m = (int) ((hours - h) * 60);
        return String.format("%02d:%02d", h, m);
    }

    // ------------------------------------------------------------------

    private void recompute() {
        // The sun tracks a circle; angle 0 is sunrise on the +X horizon
        double angle = time * Math.PI * 2.0;

        // Pointing FROM the sun TOWARDS the world, which is what shading wants
        sunDirection.set(
            (float) -Math.cos(angle),
            (float) -Math.sin(angle),
            -0.35f
        ).normalize();

        moonDirection.set(sunDirection).negate();

        float daylight = getDaylight();

        // Dawn/dusk weight peaks when the sun sits near the horizon
        float elevation = -sunDirection.y;
        float twilight = clamp01(1.0f - Math.abs(elevation) / 0.28f);

        if (daylight <= 0.001f) {
            zenithColor.set(NIGHT_ZENITH);
            horizonColor.set(NIGHT_HORIZON);
        } else {
            zenithColor.set(NIGHT_ZENITH).lerp(DAY_ZENITH, daylight);
            horizonColor.set(NIGHT_HORIZON).lerp(DAY_HORIZON, daylight);
        }

        // Warm the horizon while the sun is low
        if (twilight > 0.0f && elevation > -0.25f) {
            horizonColor.lerp(DAWN_HORIZON, twilight * 0.75f);
            zenithColor.lerp(DAWN_ZENITH, twilight * 0.35f);
        }

        if (daylight > 0.0f) {
            sunColor.set(SUN_DAWN).lerp(SUN_DAY, clamp01(elevation / 0.35f));
        } else {
            sunColor.set(MOON_TINT);
        }
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
