package com.voxelgame.rendering.model;

import com.voxelgame.item.Item;
import com.voxelgame.item.ItemStack;
import com.voxelgame.rendering.Camera;
import com.voxelgame.rendering.Shader;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.world.BlockType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * First-person view model, vanilla Minecraft pose pipeline.
 *
 * Three mutually exclusive states, exactly like the original game:
 *  - empty hand: the bare right arm enters from the lower right corner
 *    (skin-textured 4x12x4 limb, the classic arm chain);
 *  - a block: the full cube, rotated so the top and two sides show;
 *  - an item (tool, food, material): the 16x16 sprite extruded 1 texel
 *    deep and swung up diagonally, like vanilla's item/handheld model.
 *
 * The transforms replicate vanilla's ItemInHandRenderer: hand base
 * (0.56, -0.52, -0.72), the 45-degree yaw roll, the swing arc driven by
 * sin(sqrt(progress)*PI) and sin(progress^2*PI), and the -0.6 equip drop.
 *
 * Drawn in its own pass after the world with the depth buffer cleared, so
 * the hand can never intersect nearby geometry, and with fog disabled so
 * it does not fade out at the edge of the render distance.
 *
 * Everything sits in view space: the camera is treated as the origin, so
 * the hand follows the head automatically without tracking its transform.
 */
public class HeldItemRenderer {

    /** Seconds for one full swing. */
    private static final float SWING_TIME = 0.25f;
    /** Seconds for the shorter place-block/use jab. */
    private static final float PLACE_TIME = 0.18f;

    /** Vanilla hand anchor in view space (ItemInHandRenderer). */
    private static final float HAND_X = 0.56f, HAND_Y = -0.52f, HAND_Z = -0.72f;
    /** Vanilla arm anchor is slightly further out than the item anchor. */
    private static final float ARM_X = 0.64f, ARM_Y = -0.60f, ARM_Z = -0.72f;
    /** Vanilla equip drop: the item sinks 0.6 units while equipping. */
    private static final float EQUIP_DROP = 0.6f;

    /** 1/16: one skin/sprite pixel in world units. */
    private static final float P = 1.0f / 16.0f;

    private final Shader modelShader;
    private final Shader blockShader;
    private final SkinTexture skin;

    private final ModelBox arm;
    private final BlockCube cube;

    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f identityView = new Matrix4f();
    private final Matrix4f model = new Matrix4f();

    private float swingProgress = 0;   // 0 = idle, counts up to 1
    private boolean swinging = false;
    private float placeProgress = 0;
    private boolean placing = false;

    private float bobPhase = 0;
    private float bobAmount = 0;

    /** Live-adjustable offsets on top of the vanilla pose; see HandTuning. */
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
        // and leg pixels instead. Model space is Y-down like vanilla's.
        arm = new ModelBox(40, 16, 4, 12, 4, -2, -12, -2);

        cube = new BlockCube();
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
    }

    // ------------------------------------------------------------------

    /**
     * @param held stack currently selected in the hotbar, or null/empty for none
     */
    public void render(Camera camera, TextureAtlas atlas, ItemStack held,
                       float lightLevel, Vector3f sunColor) {

        // A fresh depth range for the view model, so it is never clipped by
        // the world it is standing in
        glClear(GL_DEPTH_BUFFER_BIT);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        glDisable(GL_BLEND);

        // Narrower FOV than the world keeps the hand from looking distorted
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

        // What to draw in the hand: a block cube, an extruded item sprite,
        // or the bare arm. Vanilla shows the arm only on an empty hand.
        BlockType renderBlock = null;
        int voxelLayer = -1;

        if (held != null && !held.isEmpty()) {
            if (held.isBlock()) {
                BlockType type = held.getBlockType();
                if (isItemSprite(type)) {
                    voxelLayer = atlas.getSlot(type.id, 2);
                } else {
                    renderBlock = type;
                }
            } else if (held.getItem() != null) {
                Item item = held.getItem();
                if (item.spriteName != null) {
                    voxelLayer = atlas.getLayerOf(item.spriteName);
                }
                if (voxelLayer < 0 && item.blockType != null
                        && !isItemSprite(item.blockType)) {
                    renderBlock = item.blockType;
                }
            }
        }

        boolean hasBlock = renderBlock != null && renderBlock != BlockType.AIR;

        if (voxelLayer >= 0) {
            renderHeldItemModel(atlas, voxelLayer, lightLevel, sunColor);
        } else if (hasBlock) {
            renderHeldBlock(atlas, renderBlock, lightLevel, sunColor);
        } else {
            renderArm(lightLevel, sunColor);
        }

        glDepthMask(true);
    }

    /** Sprite-only block types (tools, food, materials) — never a cube. */
    static boolean isItemSprite(BlockType type) {
        return type != null && type.isItemSprite();
    }

    // ------------------------------------------------------------------
    // Vanilla pose chains
    // ------------------------------------------------------------------

    /**
     * Shared vanilla anchor: swing pre-offset, hand position with the equip
     * drop, and the 45-degree attack roll with the swing terms. Vanilla
     * ItemInHandRenderer#applyItemArmAttackTransform.
     *
     * @param s 0..1 swing progress (0 = resting pose)
     */
    private void applyVanillaHand(Matrix4f m, float s, float armAnchor) {
        float sq = (float) Math.sqrt(s);
        float sinSq = (float) Math.sin(sq * Math.PI);
        float sinQuad = (float) Math.sin(s * s * Math.PI);

        if (s > 0.001f) {
            // Vanilla swing pre-offset: forward scoop, up then down
            if (armAnchor > 0) {
                m.translate(-0.3f * sinSq, 0.4f * (float) Math.sin(sq * Math.PI * 2),
                    -0.4f * sinQuad);
            } else {
                m.translate(-0.4f * sinSq, 0.2f * (float) Math.sin(sq * Math.PI * 2),
                    -0.2f * sinQuad);
            }
        }

        if (armAnchor > 0) {
            m.translate(ARM_X, ARM_Y - EQUIP_DROP * (1 - equipProgress), ARM_Z);
        } else {
            m.translate(HAND_X, HAND_Y - EQUIP_DROP * (1 - equipProgress), HAND_Z);
        }

        // Attack roll: 45 degrees at rest, swept away by the swing
        m.rotateY((float) Math.toRadians(45 - 20 * sinQuad));
        m.rotateZ((float) Math.toRadians(-20 * sinSq));
        m.rotateX((float) Math.toRadians(-80 * sinSq));
        m.rotateY((float) Math.toRadians(-20 * sinQuad));
    }

    /** Walking bob: the hand rolls gently with the step rhythm. */
    private void applyBob(Matrix4f m) {
        if (bobAmount > 0.001f) {
            m.rotateZ((float) Math.sin(bobPhase) * 0.045f * bobAmount);
        }
    }

    // ------------------------------------------------------------------
    // Passes
    // ------------------------------------------------------------------

    /** The bare right arm, vanilla renderPlayerArm chain. */
    private void renderArm(float lightLevel, Vector3f sunColor) {
        modelShader.bind();
        modelShader.setUniformMat4("projection", projection);
        modelShader.setUniformMat4("view", identityView);
        modelShader.setUniform1i("skin", 0);
        modelShader.setUniform1f("lightLevel", lightLevel);
        modelShader.setUniform3f("sunColor", sunColor);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, skin.getTextureId());

        model.identity();

        // Vanilla arm swing pre-offset + equip drop; the arm pose itself is
        // authored directly in view space (the item anchor chain does not
        // apply to the bare arm)
        float s = activeSwing();
        if (s > 0.001f) {
            float sq = (float) Math.sqrt(s);
            model.translate(-0.3f * (float) Math.sin(sq * Math.PI),
                0.4f * (float) Math.sin(sq * Math.PI * 2),
                -0.4f * (float) Math.sin(s * Math.PI));
        }
        model.translate(0, -EQUIP_DROP * (1 - equipProgress), 0);
        applyBob(model);

        // Fist just off the bottom-right corner; the limb runs mostly away
        // from the camera (strong vanilla foreshortening), shoulder hidden
        // behind the screen edge (ModelBox is in world units, hand end at
        // its y=0 origin, shoulder 12px along -Y)
        model.translate(0.48f, -0.36f, -0.62f);
        model.rotateY((float) Math.toRadians(-66));
        model.rotateZ((float) Math.toRadians(60));

        modelShader.setUniformMat4("model", model);
        arm.render();

        modelShader.unbind();
    }

    private float activeSwing() {
        if (swinging) return swingProgress;
        if (placing) return placeProgress;
        return 0;
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

        model.identity();
        applyVanillaHand(model, activeSwing(), 0);
        applyBob(model);

        model.translate(tuning.offsetX, tuning.offsetY, tuning.offsetZ);

        // Vanilla 1.8 doBlockTransformations shape, retuned so the cube sits
        // fully on screen in the lower right, top and two sides showing
        model.scale(0.35f);
        model.translate(0.05f, 0.35f, 0.05f);
        model.rotateY((float) Math.toRadians(30));
        model.rotateX((float) Math.toRadians(-80));
        model.rotateY((float) Math.toRadians(60));
        // Our cube is centred, vanilla's hangs off the origin corner
        model.translate(0.5f, 0.5f, 0.5f);

        blockShader.setUniformMat4("model", model);

        cube.render(atlas, held, blockShader);

        blockShader.unbind();
        atlas.unbindArray();
    }

    /**
     * Render a held item as the vanilla item/handheld model: the sprite
     * extruded 1 texel deep, firstperson_righthand display
     * (rotation [0, -90, 25], translation [1.13, 3.2, 1.13]/16, scale 0.68).
     */
    private void renderHeldItemModel(TextureAtlas atlas, int layer,
                                     float lightLevel, Vector3f sunColor) {
        ItemModel3D itemModel = ItemModel3D.get(atlas, layer);
        if (itemModel == null || itemModel.getVertexCount() == 0) return;

        blockShader.bind();
        blockShader.setUniformMat4("projection", projection);
        blockShader.setUniformMat4("view", identityView);
        blockShader.setUniform1i("blockTextures", 0);
        blockShader.setUniform1f("lightLevel", lightLevel);
        blockShader.setUniform3f("sunColor", sunColor);

        glActiveTexture(GL_TEXTURE0);
        atlas.bindArray();

        model.identity();
        applyVanillaHand(model, activeSwing(), 0);
        applyBob(model);

        model.translate(tuning.offsetX, tuning.offsetY, tuning.offsetZ);

        // firstperson_righthand display block, translation in 1/16 units.
        // The +90/+20 pair stands the tool up nearly vertical, blade tilted
        // slightly left — the vanilla rest pose
        model.translate(-0.06f, 0.10f, 0);
        model.translate(1.13f * P, 3.2f * P, 1.13f * P);
        model.rotateY((float) Math.toRadians(90));
        model.rotateZ((float) Math.toRadians(20));
        model.scale(0.68f);

        blockShader.setUniformMat4("model", model);

        // Closed voxel boxes: backface culling halves the fill cost
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        itemModel.render(blockShader);

        blockShader.unbind();
        atlas.unbindArray();
    }

    public void cleanup() {
        arm.cleanup();
        cube.cleanup();
        ItemModel3D.cleanupAll();
        modelShader.cleanup();
        blockShader.cleanup();
    }
}
