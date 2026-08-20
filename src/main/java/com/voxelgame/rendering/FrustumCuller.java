package com.voxelgame.rendering;

import org.joml.*;

/**
 * View frustum test for chunk culling.
 *
 * A single instance is reused every frame and refreshed through
 * {@link #update}, so the render loop allocates nothing. Planes are stored
 * as flat floats rather than Vector4f objects for the same reason - this is
 * called once per loaded chunk, every frame.
 */
public class FrustumCuller {

    private final Matrix4f vpMatrix = new Matrix4f();

    /** 6 planes x 4 components (a, b, c, d). */
    private final float[] planes = new float[24];

    public FrustumCuller() {}

    public FrustumCuller(Camera camera) {
        update(camera);
    }

    /** Re-extract the planes from the camera's current view-projection. */
    public void update(Camera camera) {
        vpMatrix.set(camera.getProjectionMatrix()).mul(camera.getViewMatrix());

        float m00 = vpMatrix.m00(), m01 = vpMatrix.m01(), m02 = vpMatrix.m02(), m03 = vpMatrix.m03();
        float m10 = vpMatrix.m10(), m11 = vpMatrix.m11(), m12 = vpMatrix.m12(), m13 = vpMatrix.m13();
        float m20 = vpMatrix.m20(), m21 = vpMatrix.m21(), m22 = vpMatrix.m22(), m23 = vpMatrix.m23();
        float m30 = vpMatrix.m30(), m31 = vpMatrix.m31(), m32 = vpMatrix.m32(), m33 = vpMatrix.m33();

        set(0, m03 + m00, m13 + m10, m23 + m20, m33 + m30); // left
        set(1, m03 - m00, m13 - m10, m23 - m20, m33 - m30); // right
        set(2, m03 + m01, m13 + m11, m23 + m21, m33 + m31); // bottom
        set(3, m03 - m01, m13 - m11, m23 - m21, m33 - m31); // top
        set(4, m03 + m02, m13 + m12, m23 + m22, m33 + m32); // near
        set(5, m03 - m02, m13 - m12, m23 - m22, m33 - m32); // far
    }

    private void set(int i, float a, float b, float c, float d) {
        float len = (float) java.lang.Math.sqrt(a * a + b * b + c * c);
        if (len > 0) { a /= len; b /= len; c /= len; d /= len; }

        int o = i * 4;
        planes[o] = a;
        planes[o + 1] = b;
        planes[o + 2] = c;
        planes[o + 3] = d;
    }

    /**
     * Axis-aligned box test using the positive vertex, which is the cheapest
     * conservative check: only the corner furthest along each plane normal
     * has to be evaluated.
     */
    public boolean isBoxVisible(float minX, float minY, float minZ,
                                float maxX, float maxY, float maxZ) {
        for (int i = 0; i < 6; i++) {
            int o = i * 4;
            float a = planes[o], b = planes[o + 1], c = planes[o + 2], d = planes[o + 3];

            float x = (a > 0) ? maxX : minX;
            float y = (b > 0) ? maxY : minY;
            float z = (c > 0) ? maxZ : minZ;

            if (a * x + b * y + c * z + d < 0) return false;
        }
        return true;
    }

    public boolean isBoxVisible(Vector3f min, Vector3f max) {
        return isBoxVisible(min.x, min.y, min.z, max.x, max.y, max.z);
    }
}
