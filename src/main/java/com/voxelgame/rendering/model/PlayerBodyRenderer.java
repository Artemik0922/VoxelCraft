package com.voxelgame.rendering.model;

import com.voxelgame.item.ItemStack;
import com.voxelgame.player.Player;
import com.voxelgame.rendering.Camera;
import com.voxelgame.rendering.Shader;
import com.voxelgame.rendering.TextureAtlas;
import com.voxelgame.world.BlockType;
import com.voxelgame.world.entity.PlayerModel;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * Renders the local player's full humanoid body in third-person view (F5) —
 * same quality as the villager model: head pitch and bob, body tilt, walking
 * limbs, sneak pose, attack swing and the selected block held in the right
 * hand. The body is drawn with the player's own skin, so it always matches
 * the first-person arm.
 */
public class PlayerBodyRenderer {

    private final Shader bodyShader;
    private final Shader blockShader;
    private final PlayerModel model;
    private final int textureId;
    private final BlockCube cube = new BlockCube();

    private final Matrix4f baseMatrix = new Matrix4f();
    private final Matrix4f viewProj = new Matrix4f();
    private final Matrix4f handMatrix = new Matrix4f();
    private final Matrix4f heldModel = new Matrix4f();

    public PlayerBodyRenderer(SkinTexture skin) {
        bodyShader = new Shader("shaders/mob.vert", "shaders/mob.frag");
        blockShader = new Shader("shaders/heldblock.vert", "shaders/heldblock.frag");
        model = new PlayerModel();
        // Keep the same GL texture alive as long as the skin is in use
        textureId = skin.getTextureId();
    }

    /**
     * @param heldStack stack in the selected hotbar slot; blocks render as a
     *                  cube, items as a 3D voxel model in the hand
     */
    public void render(Camera camera, Player player, float yaw, float daylight,
                       Vector3f sunColor, TextureAtlas atlas, ItemStack heldStack,
                       float limbSwing, float limbSwingAmount,
                       float attackPhase, float headPitch, float hurtFlash) {

        BlockType held = null;
        int voxelLayer = -1;
        if (heldStack != null && !heldStack.isEmpty()) {
            if (heldStack.isBlock()) {
                held = heldStack.getBlockType();
                if (held.isItemSprite()) {
                    voxelLayer = atlas.getSlot(held.id, 2);
                    held = null;
                }
            } else if (heldStack.getItem() != null && heldStack.getItem().spriteName != null) {
                voxelLayer = atlas.getLayerOf(heldStack.getItem().spriteName);
            }
        }

        renderBody(camera, player, yaw, daylight, sunColor, atlas, held,
            limbSwing, limbSwingAmount, attackPhase, headPitch, hurtFlash,
            voxelLayer);
    }

    /** Body plus the held block/item, shared by the public entry point. */
    private void renderBody(Camera camera, Player player, float yaw, float daylight,
                            Vector3f sunColor, TextureAtlas atlas, BlockType held,
                            float limbSwing, float limbSwingAmount,
                            float attackPhase, float headPitch, float hurtFlash,
                            int voxelLayer) {

        boolean sneaking = player.isSneaking();
        float bobY = (float) Math.abs(Math.sin(limbSwing * 0.6662f)) * 0.05f * limbSwingAmount;

        glEnable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE); // Vanilla humanoid: no culling
        glDisable(GL_BLEND);

        // ----- Body -----
        bodyShader.bind();
        bodyShader.setUniform1i("skin", 0);
        bodyShader.setUniform1f("lightLevel", 0.15f + 0.85f * daylight);
        bodyShader.setUniform3f("sunColor", sunColor);
        bodyShader.setUniform1f("hurtFlash", hurtFlash);
        bodyShader.setUniform2f("tileScale", new Vector2f(1.0f, 1.0f));
        bodyShader.setUniform2f("uvOffset", new Vector2f(0.0f, 0.0f));

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);

        viewProj.set(camera.getProjectionMatrix()).mul(camera.getViewMatrix());

        Vector3f pos = player.getPosition();
        baseMatrix.identity();
        baseMatrix.translate(pos.x, pos.y + 1.35f, pos.z);
        baseMatrix.rotateY((float) Math.toRadians(yaw));
        baseMatrix.scale(1f, -1f, 1f); // Y flip: model Y-down → world Y-up
        baseMatrix.scale(0.9f);

        model.render(baseMatrix, viewProj, bodyShader,
            limbSwing, limbSwingAmount, attackPhase, headPitch, sneaking, bobY);

        // ----- Held block in the right hand -----
        if (held != null && held != BlockType.AIR) {
            model.computeHandMatrix(baseMatrix, attackPhase, sneaking,
                limbSwing, limbSwingAmount, handMatrix);

            blockShader.bind();
            blockShader.setUniformMat4("projection", camera.getProjectionMatrix());
            blockShader.setUniformMat4("view", camera.getViewMatrix());
            blockShader.setUniform1i("blockTextures", 0);
            blockShader.setUniform1f("lightLevel", 0.15f + 0.85f * daylight);
            blockShader.setUniform3f("sunColor", sunColor);

            glActiveTexture(GL_TEXTURE0);
            atlas.bindArray();

            heldModel.set(handMatrix);
            heldModel.translate(0, -0.02f, 0);
            // Vanilla block thirdperson_righthand display: rot [0,45,0], scale 0.5
            heldModel.rotateY((float) Math.toRadians(45));
            heldModel.scale(0.45f);
            blockShader.setUniformMat4("model", heldModel);
            cube.render(atlas, held, blockShader);

            blockShader.unbind();
            atlas.unbindArray();
        }

        // ----- Held item (3D extruded sprite) in the right hand -----
        if (voxelLayer >= 0) {
            ItemModel3D itemModel = ItemModel3D.get(atlas, voxelLayer);
            if (itemModel != null && itemModel.getVertexCount() > 0) {
                model.computeHandMatrix(baseMatrix, attackPhase, sneaking,
                    limbSwing, limbSwingAmount, handMatrix);

                blockShader.bind();
                blockShader.setUniformMat4("projection", camera.getProjectionMatrix());
                blockShader.setUniformMat4("view", camera.getViewMatrix());
                blockShader.setUniform1i("blockTextures", 0);
                blockShader.setUniform1f("lightLevel", 0.15f + 0.85f * daylight);
                blockShader.setUniform3f("sunColor", sunColor);

                glActiveTexture(GL_TEXTURE0);
                atlas.bindArray();

                heldModel.set(handMatrix);
                heldModel.translate(0, 0.04f, 0);
                // Vanilla item/handheld thirdperson_righthand display:
                // rotation [0, -90, 55], translation [0, 4, 0.5]/16, scale 0.85
                heldModel.rotateX((float) Math.toRadians(-90));
                heldModel.rotateY((float) Math.toRadians(-90));
                heldModel.rotateZ((float) Math.toRadians(55));
                heldModel.scale(0.85f);

                blockShader.setUniformMat4("model", heldModel);

                glEnable(GL_CULL_FACE);
                glCullFace(GL_BACK);
                glFrontFace(GL_CCW);

                itemModel.render(blockShader);

                blockShader.unbind();
                atlas.unbindArray();
            }
        }

        bodyShader.unbind();
    }

    public void cleanup() {
        model.cleanup();
        cube.cleanup();
        bodyShader.cleanup();
        blockShader.cleanup();
    }
}