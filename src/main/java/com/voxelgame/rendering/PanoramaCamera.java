package com.voxelgame.rendering;

import org.joml.Vector3f;

/**
 * Slowly orbiting camera used as the main menu backdrop.
 *
 * The real world is rendered behind the menu from a viewpoint that drifts
 * around a fixed anchor, which is cheaper and far more alive than a static
 * image, and it costs no extra assets.
 *
 * It borrows the game camera rather than owning one, so projection settings
 * and window resizes are handled in exactly one place.
 */
public class PanoramaCamera {

    /** Full revolution time, in seconds. */
    private static final double ORBIT_PERIOD = 90.0;
    /** How far the eye sits from the anchor. */
    private static final float RADIUS = 14.0f;
    /** Height above the anchor. */
    private static final float EYE_HEIGHT = 6.0f;

    private final Vector3f anchor = new Vector3f();
    private final Vector3f eye = new Vector3f();

    private double angle = 0;
    private boolean anchored = false;

    /** Place the orbit centre once, usually just above terrain at spawn. */
    public void setAnchor(float x, float y, float z) {
        anchor.set(x, y, z);
        anchored = true;
    }

    public boolean isAnchored() { return anchored; }

    public void update(double deltaTime) {
        angle += (Math.PI * 2.0 / ORBIT_PERIOD) * deltaTime;
        if (angle > Math.PI * 2.0) angle -= Math.PI * 2.0;
    }

    /**
     * Point the given camera at the anchor from the current orbit position.
     */
    public void apply(Camera camera) {
        if (!anchored) return;

        eye.set(
            anchor.x + (float) Math.cos(angle) * RADIUS,
            anchor.y + EYE_HEIGHT,
            anchor.z + (float) Math.sin(angle) * RADIUS
        );

        camera.setPosition(eye);

        // Look back at the anchor, angled slightly downward
        float dx = anchor.x - eye.x;
        float dz = anchor.z - eye.z;

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        camera.setOrientation(yaw, -12.0f);
    }

    public Vector3f getEye() { return eye; }
}
