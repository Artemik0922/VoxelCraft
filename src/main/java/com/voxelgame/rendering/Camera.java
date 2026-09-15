package com.voxelgame.rendering;

import org.joml.*;

public class Camera {
    private Vector3f position;
    private Vector3f front;
    private Vector3f up;
    private Vector3f right;
    private Vector3f worldUp;
    
    private float yaw = -90.0f;
    private float pitch = 0.0f;
    
    private float fov = 70.0f;
    private float aspectRatio;
    private float near = 0.1f;
    private float far = 256.0f; // ~renderDistance(8 chunks = 128 blocks) * 2
    
    private Matrix4f projectionMatrix;
    private Matrix4f viewMatrix;

    // Earthquake screen shake, applied in camera space to every view matrix
    private final Vector3f shakeOffset = new Vector3f();
    private float shakeRoll = 0;
    private final Vector3f shakeEye = new Vector3f();
    
    public Camera(Vector3f position, int width, int height) {
        this(position, width, height, 70.0f);
    }
    
    public Camera(Vector3f position, int width, int height, float fov) {
        this.position = new Vector3f(position);
        this.front = new Vector3f(0, 0, -1);
        this.up = new Vector3f(0, 1, 0);
        this.right = new Vector3f();
        this.worldUp = new Vector3f(0, 1, 0);
        this.fov = fov;
        this.aspectRatio = (float) width / height;
        this.projectionMatrix = new Matrix4f();
        this.viewMatrix = new Matrix4f();
        
        updateProjection();
        updateVectors();
    }
    
    public void setPosition(Vector3f pos) {
        this.position.set(pos);
    }
    
    /** Keep the projection matched to the framebuffer after a window resize. */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.aspectRatio = (float) width / height;
        updateProjection();
    }

    /** [OPT] Pull the far plane in to just past the fog so depth precision and
     *  the shadow frustum tighten; the skybox sits on the far plane but far
     *  still exceeds fogEnd, so the horizon stays seamless. */
    public void setFar(float far) {
        if (far <= near + 1.0f || java.lang.Math.abs(this.far - far) < 0.01f) return;
        this.far = far;
        updateProjection();
    }
    
    public void setFov(float fov) {
        this.fov = fov;
        updateProjection();
    }
    
    public float getFov() { return fov; }
    public float getAspectRatio() { return aspectRatio; }
    
    /** Set look direction absolutely, used by the menu panorama. */
    public void setOrientation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = java.lang.Math.max(-89.0f, java.lang.Math.min(89.0f, pitch));
        updateVectors();
    }
    
    public void rotate(float yawOffset, float pitchOffset) {
        yaw += yawOffset;
        pitch += pitchOffset;
        
        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;
        
        updateVectors();
    }
    
    private void updateVectors() {
        float yawRad = (float) java.lang.Math.toRadians(yaw);
        float pitchRad = (float) java.lang.Math.toRadians(pitch);
        
        front.x = (float) (java.lang.Math.cos(yawRad) * java.lang.Math.cos(pitchRad));
        front.y = (float) java.lang.Math.sin(pitchRad);
        front.z = (float) (java.lang.Math.sin(yawRad) * java.lang.Math.cos(pitchRad));
        front.normalize();
        
        right.set(front).cross(worldUp).normalize();
        up.set(right).cross(front).normalize();
    }
    
    private void updateProjection() {
        projectionMatrix.setPerspective((float) java.lang.Math.toRadians(fov), aspectRatio, near, far);
    }
    
    /** Screen-shake offset (camera-space) and roll, applied to every view matrix. */
    public void setShake(float x, float y, float z, float roll) {
        shakeOffset.set(x, y, z);
        shakeRoll = roll;
    }

    public Matrix4f getViewMatrix() {
        viewMatrix.identity().lookAt(position, new Vector3f(position).add(front), up);
        if (shakeRoll != 0
                || shakeOffset.x != 0 || shakeOffset.y != 0 || shakeOffset.z != 0) {
            // Shift the eye along the camera axes, then roll about the view
            // axis (post-multiplied rotation = camera-space for view matrices)
            shakeEye.set(position);
            shakeEye.x += right.x * shakeOffset.x + up.x * shakeOffset.y
                + front.x * shakeOffset.z;
            shakeEye.y += right.y * shakeOffset.x + up.y * shakeOffset.y
                + front.y * shakeOffset.z;
            shakeEye.z += right.z * shakeOffset.x + up.z * shakeOffset.y
                + front.z * shakeOffset.z;
            viewMatrix.identity().lookAt(shakeEye, new Vector3f(shakeEye).add(front), up);
            viewMatrix.rotateZ(shakeRoll);
        }
        return viewMatrix;
    }
    
    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }
    
    public Vector3f getPosition() { return position; }
    public Vector3f getFront() { return front; }
    public Vector3f getRight() { return right; }
    public Vector3f getUp() { return up; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}
