package com.voxelgame.rendering;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Directional shadow map for the sun.
 *
 * Renders scene depth from the sun's point of view into a depth-only FBO, then
 * the block shader compares each fragment against it with PCF filtering.
 *
 * The light frustum is an orthographic box that follows the camera, snapped to
 * texel increments so shadows don't shimmer while walking.
 */
public class ShadowMap {
    public static final int SHADOW_RESOLUTION = 2048;

    /** Half-width of the area around the camera that receives shadows. */
    private static final float EXTENT = 72.0f;
    private static final float DEPTH_RANGE = 220.0f;

    private final int fbo;
    private final int depthTexture;
    private final Shader depthShader;

    private final Matrix4f lightProjection = new Matrix4f();
    private final Matrix4f lightView = new Matrix4f();
    private final Matrix4f lightSpace = new Matrix4f();

    public ShadowMap() {
        depthShader = new Shader("shaders/depth.vert", "shaders/depth.frag");

        depthTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24,
            SHADOW_RESOLUTION, SHADOW_RESOLUTION, 0,
            GL_DEPTH_COMPONENT, GL_FLOAT, (java.nio.ByteBuffer) null);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        // Anything outside the light frustum must be treated as fully lit
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR,
            new float[]{1.0f, 1.0f, 1.0f, 1.0f});

        // Hardware depth comparison gives us free 2x2 PCF on top of our own
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);

        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("Shadow FBO incomplete: 0x" + Integer.toHexString(status));
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);

        System.out.println("Shadow map ready: " + SHADOW_RESOLUTION + "x" + SHADOW_RESOLUTION);
    }

    /**
     * Recompute the light matrices for the current camera position.
     */
    public void update(Vector3f cameraPos, Vector3f sunDirection) {
        Vector3f dir = new Vector3f(sunDirection).normalize();

        // Snap the shadow centre to whole texels to stop edge crawling
        float texelSize = (2.0f * EXTENT) / SHADOW_RESOLUTION;
        float cx = Math.round(cameraPos.x / texelSize) * texelSize;
        float cz = Math.round(cameraPos.z / texelSize) * texelSize;
        float cy = Math.round(cameraPos.y / texelSize) * texelSize;
        Vector3f center = new Vector3f(cx, cy, cz);

        Vector3f eye = new Vector3f(dir).mul(-DEPTH_RANGE * 0.5f).add(center);

        // Sun is near-vertical, so use Z as the up hint to keep lookAt stable
        Vector3f up = (Math.abs(dir.y) > 0.99f)
            ? new Vector3f(0, 0, 1)
            : new Vector3f(0, 1, 0);

        lightView.identity().lookAt(eye, center, up);
        lightProjection.identity().ortho(-EXTENT, EXTENT, -EXTENT, EXTENT, 1.0f, DEPTH_RANGE);
        lightProjection.mul(lightView, lightSpace);
    }

    /**
     * Bind the FBO and prepare state for the depth-only pass.
     */
    public void beginDepthPass() {
        glViewport(0, 0, SHADOW_RESOLUTION, SHADOW_RESOLUTION);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glClear(GL_DEPTH_BUFFER_BIT);

        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDisable(GL_BLEND);
        // The shadow FBO is single-sampled; MSAA state must not leak into it
        glDisable(GL_MULTISAMPLE);

        // Render back faces into the shadow map to push acne behind surfaces
        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT);

        depthShader.bind();
        depthShader.setUniformMat4("lightSpace", lightSpace);
    }

    public void endDepthPass(int screenWidth, int screenHeight) {
        depthShader.unbind();
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, screenWidth, screenHeight);
        glCullFace(GL_BACK);
        // Back to the multisampled default framebuffer
        glEnable(GL_MULTISAMPLE);
    }

    /** Bind the depth texture to a texture unit for the lighting pass. */
    public void bindDepthTexture(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        glActiveTexture(GL_TEXTURE0);
    }

    public Shader getDepthShader() { return depthShader; }
    public Matrix4f getLightSpaceMatrix() { return lightSpace; }

    /**
     * Test if a world-space AABB is within the shadow frustum.
     * Used to skip chunks that don't cast shadows into the view.
     */
    public boolean isAABBInShadowFrustum(float minX, float minY, float minZ,
                                           float maxX, float maxY, float maxZ) {
        // Transform the 8 corners to light clip space and check if any are inside
        Vector8f corners = new Vector8f(minX, minY, minZ, maxX, maxY, maxZ);

        // Test all 8 corners — if any is inside the frustum, the box is visible
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) != 0 ? maxX : minX;
            float y = (i & 2) != 0 ? maxY : minY;
            float z = (i & 4) != 0 ? maxZ : minZ;

            // Transform to clip space
            float mx = lightSpace.m00() * x + lightSpace.m10() * y + lightSpace.m20() * z + lightSpace.m30();
            float my = lightSpace.m01() * x + lightSpace.m11() * y + lightSpace.m21() * z + lightSpace.m31();
            float mz = lightSpace.m02() * x + lightSpace.m12() * y + lightSpace.m22() * z + lightSpace.m32();
            float mw = lightSpace.m03() * x + lightSpace.m13() * y + lightSpace.m23() * z + lightSpace.m33();

            // Check if inside the clip space cube [-w, w]
            if (mw > 0 && mx >= -mw && mx <= mw && my >= -mw && my <= mw && mz >= -mw && mz <= mw) {
                return true;
            }
        }
        return false;
    }

    public void cleanup() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(depthTexture);
        depthShader.cleanup();
    }

    /** Helper to store box corners (just for readability). */
    private static class Vector8f {
        float minX, minY, minZ, maxX, maxY, maxZ;
        Vector8f(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
    }
}
