package com.voxelgame.rendering;

import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * [PP] Post-processing stack: multisampled scene FBO, Gaussian bloom and
 * FXAA.
 *
 * The whole 3D frame is rendered into an MSAA colour/depth FBO, then
 * resolved to a single-sample texture. A bright-pass bloom is blurred along
 * X then Y in two half-resolution ping-pong buffers, and a final pass
 * composites the result onto the default framebuffer (with optional FXAA
 * edge smoothing). The 2D interface is drawn afterwards, untouched.
 */
public class PostProcess {

    private static final int SAMPLES = 4;

    private final Shader compositeShader;
    private final Shader blurShader;
    private final int quadVao;
    private final int quadVbo;

    // Main scene: multisampled render target + resolved colour texture
    private int msaaFbo;
    private int msaaColorRb;
    private int msaaDepthRb;
    private int resolveFbo;
    private int resolveTexture;
    private int sceneW;
    private int sceneH;

    // Bloom: half-resolution ping-pong buffers
    private int blurFboA;
    private int blurTextureA;
    private int blurFboB;
    private int blurTextureB;
    private int bloomW;
    private int bloomH;

    // Backdrop: quarter-resolution blurred copy of the scene, served to the
    // 2D interface as the frosted-glass layer behind in-game menus
    private int backdropFboA;
    private int backdropTexA;
    private int backdropFboB;
    private int backdropTexB;
    private int backdropW;
    private int backdropH;

    public PostProcess(int width, int height) {
        compositeShader = new Shader("shaders/postprocess.vert", "shaders/postprocess.frag");
        blurShader = new Shader("shaders/postprocess.vert", "shaders/bloom_blur.frag");
        quadVbo = glGenBuffers();
        quadVao = createQuad();

        createSceneTargets(width, height);
        createBloomTargets(width, height);
        createBackdropTargets(width, height);
    }

    /** Rebuild every render target for a new window size. */
    public void resize(int width, int height) {
        deleteSceneTargets();
        deleteBloomTargets();
        deleteBackdropTargets();
        createSceneTargets(width, height);
        createBloomTargets(width, height);
        createBackdropTargets(width, height);
    }

    /** Bind the multisampled scene FBO and clear it ready for the 3D pass. */
    public void beginScene(int width, int height, Vector3f clearColor) {
        glBindFramebuffer(GL_FRAMEBUFFER, msaaFbo);
        glViewport(0, 0, width, height);
        glClearColor(clearColor.x, clearColor.y, clearColor.z, 1.0f);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glEnable(GL_MULTISAMPLE);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Resolve the finished scene to a texture, run bloom and FXAA, then
     * composite the result onto the default framebuffer. Call this after
     * the 3D pass and before the 2D interface.
     */
    public void renderScene(int width, int height, boolean bloom, boolean fxaa) {
        // 1. Resolve the multisampled scene into a single-sample texture
        glBindFramebuffer(GL_READ_FRAMEBUFFER, msaaFbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFbo);
        glBlitFramebuffer(0, 0, width, height,
            0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, width, height);

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_BLEND);

        int bloomTexture = 0;
        if (bloom) {
            // Horizontal bright pass into A
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, resolveTexture);
            blurShader.bind();
            blurShader.setUniform1i("uTex", 0);
            blurShader.setUniform2f("uTexel", new Vector2f(1.0f / bloomW, 1.0f / bloomH));
            blurShader.setUniform2f("uDirection", new Vector2f(1.0f, 0.0f));
            blurShader.setUniform1i("uFirstPass", 1);
            glBindFramebuffer(GL_FRAMEBUFFER, blurFboA);
            glViewport(0, 0, bloomW, bloomH);
            drawQuad();

            // Vertical blur into B, sampling the pre-filtered A
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, blurTextureA);
            blurShader.setUniform2f("uDirection", new Vector2f(0.0f, 1.0f));
            blurShader.setUniform1i("uFirstPass", 0);
            glBindFramebuffer(GL_FRAMEBUFFER, blurFboB);
            glViewport(0, 0, bloomW, bloomH);
            drawQuad();
            blurShader.unbind();
            bloomTexture = blurTextureB;
        }

        // 2. Composite onto the window
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, width, height);

        compositeShader.bind();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, resolveTexture);
        compositeShader.setUniform1i("uScene", 0);
        if (bloom && bloomTexture != 0) {
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, bloomTexture);
            compositeShader.setUniform1i("uBloom", 1);
            compositeShader.setUniform1f("uBloomStrength", 1.0f);
        } else {
            compositeShader.setUniform1f("uBloomStrength", 0.0f);
        }
        glActiveTexture(GL_TEXTURE0);
        compositeShader.setUniform1f("uFxaaEnabled", fxaa ? 1.0f : 0.0f);
        compositeShader.setUniform2f("uResolution", new Vector2f(width, height));
        drawQuad();
        compositeShader.unbind();

        glBindTexture(GL_TEXTURE_2D, 0);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
    }

    /**
     * Downsample and blur the resolved scene into the quarter-resolution
     * backdrop textures, ready to be drawn as the frosted-glass layer behind
     * in-game menus. Two sweeping 5-tap passes (horizontal, then vertical)
     * plus the 4x minification read give a strong blur cheaply.
     */
    public void blurSceneForBackdrop() {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_BLEND);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, resolveTexture);
        blurShader.bind();
        blurShader.setUniform1i("uTex", 0);
        blurShader.setUniform2f("uTexel", new Vector2f(1.0f / backdropW, 1.0f / backdropH));
        blurShader.setUniform1i("uFirstPass", 0);

        // Horizontal pass into A
        blurShader.setUniform2f("uDirection", new Vector2f(1.0f, 0.0f));
        glBindFramebuffer(GL_FRAMEBUFFER, backdropFboA);
        glViewport(0, 0, backdropW, backdropH);
        drawQuad();

        // Vertical pass into B, sampling A
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, backdropTexA);
        blurShader.setUniform2f("uDirection", new Vector2f(0.0f, 1.0f));
        glBindFramebuffer(GL_FRAMEBUFFER, backdropFboB);
        glViewport(0, 0, backdropW, backdropH);
        drawQuad();
        blurShader.unbind();

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, sceneW, sceneH);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
    }

    /** Texture id of the blurred scene backdrop (0 if never created). */
    public int getBackdropTexture() {
        return backdropTexB;
    }

    public void cleanup() {
        compositeShader.cleanup();
        blurShader.cleanup();
        glDeleteBuffers(quadVbo);
        glDeleteVertexArrays(quadVao);
        deleteSceneTargets();
        deleteBloomTargets();
        deleteBackdropTargets();
    }

    // ------------------------------------------------------------------

    private void drawQuad() {
        glBindVertexArray(quadVao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
    }

    private int createQuad() {
        float[] verts = {
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 1.0f,
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 1.0f,
            -1.0f,  1.0f, 0.0f, 1.0f,
        };
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var buf = stack.mallocFloat(verts.length);
            buf.put(verts).flip();
            glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);
        }
        int stride = 4 * Float.BYTES;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2 * Float.BYTES);
        glBindVertexArray(0);
        return vao;
    }

    private void createSceneTargets(int width, int height) {
        sceneW = width;
        sceneH = height;

        msaaColorRb = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, msaaColorRb);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, SAMPLES, GL_RGBA8, width, height);

        msaaDepthRb = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, msaaDepthRb);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, SAMPLES, GL_DEPTH_COMPONENT24, width, height);

        msaaFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, msaaFbo);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, msaaColorRb);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, msaaDepthRb);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("PostProcess MSAA FBO incomplete");
        }

        resolveTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, resolveTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        resolveFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, resolveFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, resolveTexture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("PostProcess resolve FBO incomplete");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void createBloomTargets(int width, int height) {
        bloomW = Math.max(1, width / 2);
        bloomH = Math.max(1, height / 2);

        blurTextureA = glGenTextures();
        blurTextureB = glGenTextures();
        for (int tex : new int[]{blurTextureA, blurTextureB}) {
            glBindTexture(GL_TEXTURE_2D, tex);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, bloomW, bloomH, 0,
                GL_RGBA, GL_FLOAT, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }
        glBindTexture(GL_TEXTURE_2D, 0);

        blurFboA = attachmentFbo(blurTextureA);
        blurFboB = attachmentFbo(blurTextureB);
    }

    private int attachmentFbo(int texture) {
        int fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("PostProcess blur FBO incomplete");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        return fbo;
    }

    private void createBackdropTargets(int width, int height) {
        backdropW = Math.max(1, width / 4);
        backdropH = Math.max(1, height / 4);

        backdropTexA = glGenTextures();
        backdropTexB = glGenTextures();
        for (int tex : new int[]{backdropTexA, backdropTexB}) {
            glBindTexture(GL_TEXTURE_2D, tex);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, backdropW, backdropH, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }
        glBindTexture(GL_TEXTURE_2D, 0);

        backdropFboA = attachmentFbo(backdropTexA);
        backdropFboB = attachmentFbo(backdropTexB);
    }

    private void deleteBackdropTargets() {
        glDeleteFramebuffers(backdropFboA);
        glDeleteFramebuffers(backdropFboB);
        glDeleteTextures(backdropTexA);
        glDeleteTextures(backdropTexB);
    }

    private void deleteSceneTargets() {
        glDeleteRenderbuffers(msaaColorRb);
        glDeleteRenderbuffers(msaaDepthRb);
        glDeleteFramebuffers(msaaFbo);
        glDeleteFramebuffers(resolveFbo);
        glDeleteTextures(resolveTexture);
    }

    private void deleteBloomTargets() {
        glDeleteFramebuffers(blurFboA);
        glDeleteFramebuffers(blurFboB);
        glDeleteTextures(blurTextureA);
        glDeleteTextures(blurTextureB);
    }
}