package com.voxelgame.rendering.model;

import com.voxelgame.rendering.Camera;
import com.voxelgame.rendering.Shader;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.world.BlockType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * First-person view model: the player's arm and whatever block it holds.
 *
 * Drawn in its own pass after the world with the depth buffer cleared, so
 * the arm can never intersect nearby geometry, and with fog disabled so it
 * does not fade out at the edge of the render distance.
 *
 * Everything sits in view space: the camera is treated as the origin, so
 * the arm follows the head automatically without tracking its transform.
 */
public class HeldItemRenderer {

    /** Seconds for one full swing. */
    private static final float SWING_TIME = 0.25f;
    /** Seconds for the shorter place-block jab. */
    private static final float PLACE_TIME = 0.18f;

    private final Shader modelShader;
    private final Shader blockShader;
    private final SkinTexture skin;

    private final ModelBox arm;
    private final BlockCube cube;
    private final ItemSprite sprite;

    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f identityView = new Matrix4f();
    private final Matrix4f model = new Matrix4f();

    private float swingProgress = 0;   // 0 = idle, counts up to 1
    private boolean swinging = false;
    private float placeProgress = 0;
    private boolean placing = false;

    private float bobPhase = 0;
    private float bobAmount = 0;
    /** Idle sway phase: slow periodic movement when standing still. */
    private float idlePhase = 0;

    /** Live-adjustable placement; see HandTuning. */
    private final HandTuning tuning = new HandTuning();
    public HandTuning getTuning() { return tuning; }

    /** 1 means settled; anything less is mid equip animation. */
    private float equipProgress = 1.0f;
    private static final float EQUIP_TIME = 0.22f;

    public HeldItemRenderer(SkinTexture skin) {
        this.skin = skin;

        modelShader = new Shader("shaders/model.vert", "shaders/model.frag");
        blockShader = new Shader("shaders/heldblock.vert", "shaders/heldblock.frag");

        // The skin sheet draws the arm as a 4x12x4 limb, so the box must be
        // built with those dimensions or its UVs read a 32x12 strip of body
        // and leg pixels instead. It is laid along -Z by the model matrix,
        // not by swapping height and depth here.
        arm = new ModelBox(40, 16, 4, 12, 4, -2, -12, -2);

        cube = new BlockCube();
        sprite = new ItemSprite();
    }

    // ------------------------------------------------------------------
    // Animation triggers
    // ------------------------------------------------------------------

    /** Left click / mining: full swing arc. */
    public void startSwing() {
        swinging = true;
        swingProgress = 0;
    }

    /** Right click / placing: short forward jab. */
    public void startPlace() {
        placing = true;
        placeProgress = 0;
    }

    /** Selected hotbar slot changed: replay the equip animation. */
    public void startEquip() {
        equipProgress = 0;
    }

    public boolean isSwinging() { return swinging; }

    /** 0..1 while an attack swing is in progress, 0 otherwise (third-person arm). */
    public float getAttackPhase() { return swinging ? swingProgress : 0; }

    /**
     * @param walkSpeed horizontal speed, drives the walking sway
     */
    public void update(double deltaTime, float walkSpeed, boolean onGround) {
        float dt = (float) deltaTime;

        if (swinging) {
            swingProgress += dt / SWING_TIME;
            if (swingProgress >= 1.0f) {
                swingProgress = 0;
                swinging = false;
            }
        }

        if (placing) {
            placeProgress += dt / PLACE_TIME;
            if (placeProgress >= 1.0f) {
                placeProgress = 0;
                placing = false;
            }
        }

        if (equipProgress < 1.0f) {
            equipProgress = Math.min(1.0f, equipProgress + dt / EQUIP_TIME);
        }

        // Bob phase advances with distance walked, matching the camera bob
        if (onGround && walkSpeed > 0.1f) {
            bobPhase += walkSpeed * dt * 1.9f;
            bobAmount += (Math.min(walkSpeed / 5.0f, 1.0f) - bobAmount) * Math.min(1, dt * 8);
        } else {
            bobAmount += (0 - bobAmount) * Math.min(1, dt * 8);
        }

        // Idle sway: slow periodic movement when standing still.
        // Period ~3 seconds, very subtle. Makes the held item feel alive.
        idlePhase += dt * 2.094f; // 2π / 3 ≈ 2.094 rad/s
    }

    // ------------------------------------------------------------------

    /**
     * @param held block currently selected in the hotbar, or AIR for none
     */
    public void render(Camera camera, TextureAtlas atlas, BlockType held,
                       float lightLevel, Vector3f sunColor) {

        // A fresh depth range for the view model, so it is never clipped by
        // the world it is standing in
        glClear(GL_DEPTH_BUFFER_BIT);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        glDisable(GL_BLEND);

        // Narrower FOV than the world keeps the arm from looking distorted
        projection.identity().setPerspective(
            (float) Math.toRadians(70.0f),
            camera.getAspectRatio(), 0.05f, 8.0f);

        // View matrix is identity plus the walking bob. The camera's own
        // world view matrix is deliberately never used here: the view model
        // lives in view space, where the eye is the origin.
        identityView.identity();
        if (bobAmount > 0.001f) {
            identityView.translate(
                (float) Math.cos(bobPhase) * 0.022f * bobAmount,
                (float) -Math.abs(Math.sin(bobPhase)) * 0.030f * bobAmount,
                0);
        }

        boolean hasBlock = held != null && held != BlockType.AIR;

        if (hasBlock) {
            if (isFlatItem(held)) {
                renderHeldSprite(atlas, held, lightLevel, sunColor);
            } else {
                renderHeldBlock(atlas, held, lightLevel, sunColor);
            }
        }
        renderArm(hasBlock, lightLevel, sunColor);

        glDepthMask(true);
    }

    private void renderArm(boolean holdingBlock, float lightLevel, Vector3f sunColor) {
        modelShader.bind();
        modelShader.setUniformMat4("projection", projection);
        modelShader.setUniformMat4("view", identityView);
        modelShader.setUniform1i("skin", 0);
        modelShader.setUniform1f("lightLevel", lightLevel);
        modelShader.setUniform3f("sunColor", sunColor);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, skin.getTextureId());

        // Arm enters from the lower right corner, angled about 45 degrees
        // towards the item so the hand meets the underside of the block
        model.identity();

        model.translate(tuning.armX, tuning.armY, tuning.armZ);
        model.rotateZ((float) Math.toRadians(tuning.armRotZ));
        model.rotateX((float) Math.toRadians(tuning.armRotX));

        // Lay the limb along -Z so it reaches away from the camera. The box
        // itself stays 4x12x4 to match how the skin is drawn.
        model.rotateX((float) Math.toRadians(90));

        applySwing(model);
        applyBob(model);
        applyEquip(model);

        modelShader.setUniformMat4("model", model);
        arm.render();

        modelShader.unbind();
    }

    private void renderHeldBlock(TextureAtlas atlas, BlockType held,
                                 float lightLevel, Vector3f sunColor) {
        blockShader.bind();
        blockShader.setUniformMat4("projection", projection);
        blockShader.setUniformMat4("view", identityView);
        blockShader.setUniform1i("blockTextures", 0);
        blockShader.setUniform1f("lightLevel", lightLevel);
        blockShader.setUniform3f("sunColor", sunColor);

        glActiveTexture(GL_TEXTURE0);
        atlas.bindArray();

        // View space: camera at the origin looking down -Z, +Y up, +X right,
        // so the lower right of the screen is (+X, -Y, -Z).
        model.identity();

        model.translate(tuning.itemX, tuning.itemY, tuning.itemZ);
        model.rotateY((float) Math.toRadians(tuning.itemRotY));
        model.rotateX((float) Math.toRadians(tuning.itemRotX));

        applySwing(model);
        applyBob(model);
        applyEquip(model);

        // Applied last, so it scales the item and not the offsets above
        model.scale(tuning.itemScale);

        blockShader.setUniformMat4("model", model);

        cube.render(atlas, held, blockShader);

        blockShader.unbind();
        atlas.unbindArray();
    }

    /** Items rendered as flat sprites instead of 3D cubes. */
    private boolean isFlatItem(BlockType type) {
        return type == BlockType.IRON_SWORD;
    }

    /** Render a flat item sprite (swords, tools) in first person. */
    private void renderHeldSprite(TextureAtlas atlas, BlockType held,
                                   float lightLevel, Vector3f sunColor) {
        blockShader.bind();
        blockShader.setUniformMat4("projection", projection);
        blockShader.setUniformMat4("view", identityView);
        blockShader.setUniform1i("blockTextures", 0);
        blockShader.setUniform1f("lightLevel", lightLevel);
        blockShader.setUniform3f("sunColor", sunColor);

        glActiveTexture(GL_TEXTURE0);
        atlas.bindArray();

        model.identity();

        // Flat items are held blade-up, angled diagonally across the view
        model.translate(0.65f, -0.55f, -1.2f);
        model.rotateY((float) Math.toRadians(-35.0f));
        model.rotateX((float) Math.toRadians(55.0f));
        model.rotateZ((float) Math.toRadians(-15.0f));

        applySwing(model);
        applyBob(model);
        applyEquip(model);

        model.scale(1.2f);

        blockShader.setUniformMat4("model", model);

        sprite.render(atlas, held, blockShader);

        blockShader.unbind();
        atlas.unbindArray();
    }

    /** Swing arc on left click, plus the shorter jab when placing. */
    private void applySwing(Matrix4f m) {
        if (swinging) {
            // arc runs 0 -> 1 -> 0 across the swing
            float arc = (float) Math.sin(swingProgress * Math.PI);

            m.translate(0, -0.20f * arc, 0.10f * arc);
            m.rotateX((float) Math.toRadians(-65.0f * arc));
            m.rotateZ((float) Math.toRadians(-25.0f * arc));
        }

        if (placing) {
            float jab = (float) Math.sin(placeProgress * Math.PI);
            m.translate(0, 0, -0.20f * jab);
            m.rotateX((float) Math.toRadians(-16.0f * jab));
        }
    }

    /**
     * Slight roll while walking. The translation part of the bob lives in
     * the view matrix so it moves arm and item together.
     * When standing still (bobAmount≈0), applies subtle idle sway instead.
     */
    private void applyBob(Matrix4f m) {
        if (bobAmount > 0.001f) {
            // Walking: gentle roll matching camera bob
            m.rotateZ((float) Math.sin(bobPhase) * 0.045f * bobAmount);
        } else if (equipProgress >= 1.0f && !swinging && !placing) {
            // Idle: very subtle periodic sway so the item never looks frozen
            // Amplitude ±0.035 rad (~2 degrees) vertical and slight rotation
            float idle = (float) Math.sin(idlePhase);
            m.translate(0, idle * 0.005f, 0);
            m.rotateX(idle * 0.035f);
            m.rotateZ((float) Math.cos(idlePhase * 0.7f) * 0.018f);
        }
    }

    /**
     * Equip animation: the item drops out of frame and springs back with a
     * slight overshoot whenever the selected slot changes.
     */
    private void applyEquip(Matrix4f m) {
        if (equipProgress >= 1.0f) return;

        float t = equipProgress;

        // Dip down over the first half, rise back with overshoot
        float drop;
        if (t < 0.5f) {
            float k = t / 0.5f;
            drop = -0.55f * k;
        } else {
            float k = (t - 0.5f) / 0.5f;
            // Overshoot slightly past the rest pose, then settle
            drop = -0.55f * (1 - k) + 0.06f * (float) Math.sin(k * Math.PI);
        }

        m.translate(0, drop, 0);
        m.rotateX((float) Math.toRadians(38.0f * -drop));
    }

    public void cleanup() {
        arm.cleanup();
        cube.cleanup();
        sprite.cleanup();
        modelShader.cleanup();
        blockShader.cleanup();
    }
}
