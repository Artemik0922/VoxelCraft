package com.voxelgame.rendering;

import com.voxelgame.rendering.model.BlockCube;
import com.voxelgame.world.*;
import com.voxelgame.world.entity.AsteroidEntity;
import com.voxelgame.world.entity.FallingBlockEntity;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.entity.TntEntity;
import org.joml.*;

import java.util.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * Renders the voxel world in two passes:
 *   1. opaque geometry, depth write on, blending off
 *   2. transparent geometry (water/glass/leaves), back-to-front, depth write off
 */
public class Renderer {
    private static final float CULL_DISTANCE = 144.0f;

    private final Map<Long, RenderChunk> renderChunks = new HashMap<>();
    private final TextureAtlas textureAtlas;
    private final BlockCube heldItemCube = new BlockCube();

    // Reused each frame to avoid per-frame allocation
    private final List<RenderChunk> transparentQueue = new ArrayList<>();
    private final List<RenderChunk> leafQueue = new ArrayList<>();

    public Renderer() {
        this("");
    }

    public Renderer(String resourcePackName) {
        textureAtlas = new TextureAtlas(resourcePackName);
    }

    /** Draw spinning mini-blocks for loose item entities. */
    public void renderItemEntities(World world, Shader shader, Camera camera,
                                    com.voxelgame.rendering.TextureAtlas atlas,
                                    float lightLevel) {
        var items = world.getItemEntities();
        if (items.isEmpty()) return;

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glDisable(GL_BLEND);

        shader.bind();
        shader.setUniformMat4("projection", camera.getProjectionMatrix());
        shader.setUniformMat4("view", camera.getViewMatrix());
        shader.setUniform1i("blockTextures", 0);
        shader.setUniform1f("lightLevel", lightLevel);
        shader.setUniform3f("sunColor", new Vector3f(1.0f, 1.0f, 1.0f));
        shader.setUniform1f("fadeAlpha", 1.0f);

        glActiveTexture(GL_TEXTURE0);
        atlas.bindArray();

        org.joml.Matrix4f model = new org.joml.Matrix4f();
        for (var item : items) {
            Vector3f pos = item.getPosition();
            model.identity();
            model.translate(pos.x, pos.y + item.getBobOffset(), pos.z);
            model.rotateY((float) java.lang.Math.toRadians(item.getRotation()));
            model.rotateX((float) java.lang.Math.toRadians(20));
            model.scale(0.3f);

            shader.setUniformMat4("model", model);

            // Handle both block items and non-block items
            if (item.getStack().isItem()) {
                // Use the item's associated block type for rendering
                com.voxelgame.world.BlockType renderBlock = getItemRenderBlock(item.getStack().getItem());
                heldItemCube.render(atlas, renderBlock, shader);
            } else {
                heldItemCube.render(atlas, item.getStack().getBlockType(), shader);
            }
        }

        atlas.unbindArray();
        shader.unbind();
    }

    /**
     * [ENCH] Draw experience orbs: small glowing cubes with the glowstone
     * texture, bobbing and slowly rotating, scaled by their value.
     */
    public void renderXpOrbs(World world, Shader shader, Camera camera,
                             com.voxelgame.rendering.TextureAtlas atlas,
                             float lightLevel) {
        var orbs = world.getXpOrbs();
        if (orbs.isEmpty()) return;

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glDisable(GL_BLEND);

        shader.bind();
        shader.setUniformMat4("projection", camera.getProjectionMatrix());
        shader.setUniformMat4("view", camera.getViewMatrix());
        shader.setUniform1i("blockTextures", 0);
        shader.setUniform1f("lightLevel", 1.0f);
        shader.setUniform3f("sunColor", new Vector3f(1.0f, 1.0f, 1.0f));
        shader.setUniform1f("fadeAlpha", 1.0f);

        glActiveTexture(GL_TEXTURE0);
        atlas.bindArray();

        org.joml.Matrix4f model = new org.joml.Matrix4f();
        float time = (System.nanoTime() / 1_000_000_000.0f);
        for (var orb : orbs) {
            Vector3f pos = orb.getPosition();
            float scale = 0.16f + orb.getValue() * 0.02f;
            model.identity();
            model.translate(pos.x, pos.y + orb.getBobOffset(), pos.z);
            model.rotateY(time * 120.0f + orb.getPosition().x * 37.0f);
            model.scale(scale);

            shader.setUniformMat4("model", model);
            heldItemCube.render(atlas, com.voxelgame.world.BlockType.GLOWSTONE, shader);
        }

        atlas.unbindArray();
        shader.unbind();
    }

    /**
     * Draw primed TNT blocks. Each one is a full-sized TNT cube at the
     * entity's position; flashing white when the fuse timer says so.
     */
    public void renderTntEntities(World world, Shader shader, Camera camera,
                                   com.voxelgame.rendering.TextureAtlas atlas,
                                   float lightLevel) {
        var tnts = world.getTntEntities();
        if (tnts.isEmpty()) return;

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glDisable(GL_BLEND);

        shader.bind();
        shader.setUniformMat4("projection", camera.getProjectionMatrix());
        shader.setUniformMat4("view", camera.getViewMatrix());
        shader.setUniform1i("blockTextures", 0);
        shader.setUniform1f("lightLevel", lightLevel);
        shader.setUniform3f("sunColor", new Vector3f(1.0f, 1.0f, 1.0f));
        shader.setUniform1f("fadeAlpha", 1.0f);

        glActiveTexture(GL_TEXTURE0);
        atlas.bindArray();

        org.joml.Matrix4f model = new org.joml.Matrix4f();
        for (TntEntity tnt : tnts) {
            Vector3f pos = tnt.getPosition();
            model.identity();
            // Centre the cube on its block position
            model.translate(pos.x, pos.y + 0.5f, pos.z);

            // Slight wobble that speeds up as the fuse burns down
            float fuse = tnt.getFuseProgress();
            float wobble = (float) java.lang.Math.sin(fuse * 30.0f) * 0.02f * fuse;
            model.rotateZ(wobble);

            shader.setUniformMat4("model", model);

            // Flash white on alternating ticks — the shader's emissive
            // attribute is not set by BlockCube, so we use a uniform tint
            // instead: when flashing, override sunColor to white
            if (tnt.isFlashing()) {
                shader.setUniform3f("sunColor", new Vector3f(2.0f, 2.0f, 2.0f));
            } else {
                shader.setUniform3f("sunColor", new Vector3f(1.0f, 1.0f, 1.0f));
            }

            heldItemCube.render(atlas, BlockType.TNT, shader);
        }

        // Restore sunColor for subsequent passes
        shader.setUniform3f("sunColor", new Vector3f(1.0f, 1.0f, 1.0f));

        atlas.unbindArray();
        shader.unbind();
    }

    /**
     * [GP-022] Draw tumbling sand/gravel. Same cube pass as TNT, but the
     * block keeps its own texture and stays axis-aligned while falling.
     */
    public void renderFallingBlocks(World world, Shader shader, Camera camera,
                                    com.voxelgame.rendering.TextureAtlas atlas,
                                    float lightLevel) {
        var fallers = world.getFallingBlocks();
        if (fallers.isEmpty()) return;

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glDisable(GL_BLEND);

        shader.bind();
        shader.setUniformMat4("projection", camera.getProjectionMatrix());
        shader.setUniformMat4("view", camera.getViewMatrix());
        shader.setUniform1i("blockTextures", 0);
        shader.setUniform1f("lightLevel", lightLevel);
        shader.setUniform3f("sunColor", new Vector3f(1.0f, 1.0f, 1.0f));
        shader.setUniform1f("fadeAlpha", 1.0f);

        glActiveTexture(GL_TEXTURE0);
        atlas.bindArray();

        org.joml.Matrix4f model = new org.joml.Matrix4f();
        for (FallingBlockEntity fb : fallers) {
            Vector3f pos = fb.getPosition();
            model.identity();
            // The cube spans [pos, pos+1]: centre it on its block position
            model.translate(pos.x + 0.5f, pos.y + 0.5f, pos.z + 0.5f);
            shader.setUniformMat4("model", model);
            heldItemCube.render(atlas, BlockType.fromId(fb.getBlockId()), shader);
        }

        atlas.unbindArray();
        shader.unbind();
    }

    /**
     * [AST] Draw falling asteroids: a tumbling boulder made of block cubes
     * with a hot tint, so it reads as burning against the sky.
     */
    public void renderAsteroids(World world, Shader shader, Camera camera,
                                com.voxelgame.rendering.TextureAtlas atlas) {
        var rocks = world.getAsteroids();
        if (rocks.isEmpty()) return;

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glDisable(GL_BLEND);

        shader.bind();
        shader.setUniformMat4("projection", camera.getProjectionMatrix());
        shader.setUniformMat4("view", camera.getViewMatrix());
        shader.setUniform1i("blockTextures", 0);
        // Burning rock stays bright against the night sky
        shader.setUniform1f("lightLevel", 1.0f);
        shader.setUniform3f("sunColor", new Vector3f(1.25f, 1.12f, 0.85f));
        shader.setUniform1f("fadeAlpha", 1.0f);

        glActiveTexture(GL_TEXTURE0);
        atlas.bindArray();

        org.joml.Matrix4f base = new org.joml.Matrix4f();
        org.joml.Matrix4f model = new org.joml.Matrix4f();
        for (AsteroidEntity rock : rocks) {
            Vector3f pos = rock.getPosition();
            base.identity();
            base.translate(pos.x, pos.y, pos.z);
            base.rotateY(rock.getYaw());
            base.rotateX(rock.getPitch());
            base.rotateZ(rock.getRoll());
            for (AsteroidEntity.Part part : rock.getParts()) {
                model.set(base);
                model.translate(part.ox, part.oy, part.oz);
                model.scale(part.scale);
                shader.setUniformMat4("model", model);
                heldItemCube.render(atlas, part.block, shader);
            }
        }

        // Restore the plain tint for subsequent passes
        shader.setUniform3f("sunColor", new Vector3f(1.0f, 1.0f, 1.0f));

        atlas.unbindArray();
        shader.unbind();
    }

    public void render(World world, Shader shader, Camera camera) {
        glActiveTexture(GL_TEXTURE0);
        textureAtlas.bindArray();

        // Meshes were already refreshed by prepare() before the shadow pass
        renderChunks.entrySet().removeIf(e -> {
            if (world.getChunks().containsKey(e.getKey())) return false;
            e.getValue().cleanup();
            return true;
        });

        renderOpaquePass(world, camera, shader);
        renderSlidingDoors(world, shader, camera);
        renderLeafPass(shader);
        renderTransparentPass(world, camera, shader);

        textureAtlas.unbindArray();
    }

    /** Map items to their render block types. */
    private com.voxelgame.world.BlockType getItemRenderBlock(com.voxelgame.item.Item item) {
        if (item == null) return com.voxelgame.world.BlockType.STONE;
        // Map items to representative blocks for rendering
        String name = item.name;
        if (name.contains("pork")) return com.voxelgame.world.BlockType.SAND;
        if (name.contains("beef") || name.contains("mutton") || name.contains("chicken")) return com.voxelgame.world.BlockType.DIRT;
        if (name.contains("iron_ingot")) return com.voxelgame.world.BlockType.IRON_BLOCK;
        if (name.contains("gold_ingot")) return com.voxelgame.world.BlockType.GOLD_BLOCK;
        if (name.contains("diamond")) return com.voxelgame.world.BlockType.DIAMOND_BLOCK;
        if (name.contains("coal")) return com.voxelgame.world.BlockType.COAL_BLOCK;
        if (name.contains("leather")) return com.voxelgame.world.BlockType.BRICK;
        if (name.contains("bread")) return com.voxelgame.world.BlockType.SAND;
        if (name.contains("apple")) return com.voxelgame.world.BlockType.APPLE;
        return com.voxelgame.world.BlockType.STONE;
    }

    /**
     * Recompute lighting and rebuild meshes for chunks that changed.
     */
    /**
     * Time budget for relighting and remeshing, in milliseconds per frame.
     *
     * Both still run on the main thread because they touch GL buffers and
     * neighbouring chunk state. Spending a bounded slice keeps a burst of
     * dirty chunks from stalling a frame; the rest is picked up next frame.
     */
    private static final long REBUILD_BUDGET_NS = 6_000_000L;

    private void updateDirtyChunks(World world) {
        long deadline = System.nanoTime() + REBUILD_BUDGET_NS;

        lightBacklog = 0;
        meshBacklog = 0;

        // Light first: the mesher reads light values, so a chunk relit this
        // frame must not be meshed with stale data
        for (Map.Entry<Long, Chunk> entry : world.getChunks().entrySet()) {
            Chunk chunk = entry.getValue();
            if (!chunk.isLightDirty()) continue;

            if (System.nanoTime() > deadline) { lightBacklog++; continue; }
            LightEngine.computeChunkLight(chunk, world);
        }

        for (Map.Entry<Long, Chunk> entry : world.getChunks().entrySet()) {
            Chunk chunk = entry.getValue();
            if (!chunk.isDirty()) continue;

            // Never mesh a chunk whose light is still pending
            if (chunk.isLightDirty()) { meshBacklog++; continue; }

            if (System.nanoTime() > deadline) { meshBacklog++; continue; }

            RenderChunk rc = renderChunks.computeIfAbsent(entry.getKey(), k -> new RenderChunk());
            rc.rebuild(chunk, world, textureAtlas);
            chunk.setDirty(false);
        }
    }

    /** Reused every frame; the render loop must not allocate. */
    private final FrustumCuller frustum = new FrustumCuller();
    private int culledByFrustum = 0;

    /** Cached visible chunks (updated only when camera moves significantly) */
    private final List<RenderChunk> visibleChunks = new ArrayList<>();
    private final List<RenderChunk> visibleLeaves = new ArrayList<>();
    private final List<RenderChunk> visibleTransparent = new ArrayList<>();
    private float lastCamX = Float.NaN, lastCamZ = Float.NaN;
    private int frameCounter = 0;
    private static final int CACHE_REFRESH_INTERVAL = 5; // Refresh every 5 frames

    public int getCulledByFrustum() { return culledByFrustum; }

    /** [UI-009] Chunks still waiting for relighting this frame. */
    public int getLightBacklog() { return lightBacklog; }

    /** [UI-009] Chunks still waiting for a mesh rebuild this frame. */
    public int getMeshBacklog() { return meshBacklog; }

    /**
     * Frustum test against the chunk's full column. Using the real vertical
     * extent would need a per-chunk bounding box; the column is conservative
     * and still rejects everything behind and beside the camera.
     */
    private boolean isChunkVisible(Chunk chunk) {
        float x0 = chunk.getWorldX();
        float z0 = chunk.getWorldZ();
        return frustum.isBoxVisible(
            x0, 0, z0,
            x0 + Chunk.SIZE, Chunk.HEIGHT, z0 + Chunk.SIZE);
    }

    private int lightBacklog = 0;
    private int meshBacklog = 0;

    /** Mirrors Game's F5 debug toggle so the pass switches don't re-enable it. */
    private boolean cullingEnabled = true;

    /**
     * Ambient occlusion is baked into the meshes, so toggling it has to
     * invalidate every chunk and rebuild.
     */
    public void setAmbientOcclusionEnabled(boolean enabled, World world) {
        if (ChunkMeshBuilder.isAmbientOcclusionEnabled() == enabled) return;

        ChunkMeshBuilder.setAmbientOcclusionEnabled(enabled);
        for (Chunk chunk : world.getChunks().values()) {
            chunk.setDirty(true);
        }
    }

    public void setCullingEnabled(boolean enabled) {
        this.cullingEnabled = enabled;
    }

    private void applyCulling() {
        if (cullingEnabled) {
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
        } else {
            glDisable(GL_CULL_FACE);
        }
    }

    private void renderOpaquePass(World world, Camera camera, Shader shader) {
        glDisable(GL_BLEND);
        glDepthMask(true);
        applyCulling();

        transparentQueue.clear();
        leafQueue.clear();
        drawnChunks = 0;
        drawnTriangles = 0;
        culledByFrustum = 0;

        frustum.update(camera);

        // Cache visible chunks (refresh every N frames or on camera move)
        float camX = camera.getPosition().x;
        float camZ = camera.getPosition().z;
        frameCounter++;
        if (frameCounter >= CACHE_REFRESH_INTERVAL || Float.isNaN(lastCamX) ||
            java.lang.Math.abs(camX - lastCamX) > 16 || java.lang.Math.abs(camZ - lastCamZ) > 16) {
            frameCounter = 0;
            lastCamX = camX;
            lastCamZ = camZ;
            visibleChunks.clear();
            for (Map.Entry<Long, RenderChunk> e : renderChunks.entrySet()) {
                RenderChunk rc = e.getValue();
                Chunk c = world.getChunks().get(e.getKey());
                if (c == null || !isChunkVisible(c)) continue;
                float ddx = c.getWorldX() + 8 - camX;
                float ddz = c.getWorldZ() + 8 - camZ;
                if (ddx * ddx + ddz * ddz >= CULL_DISTANCE * CULL_DISTANCE) continue;
                visibleChunks.add(rc);
            }
        }

        for (RenderChunk rc : visibleChunks) {
            if (rc.opaque != null) {
                // [GR-002] Fading chunks blend against what is behind them;
                // depth writes stay on, so they still occlude each other.
                float fade = rc.updateFadeAlpha();
                boolean fading = fade < 1.0f;
                if (fading) {
                    glEnable(GL_BLEND);
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                }
                shader.setUniformMat4("model", rc.modelMatrix);
                shader.setUniform1f("fadeAlpha", fade);
                rc.opaque.render();
                if (fading) glDisable(GL_BLEND);
                drawnChunks++;
                drawnTriangles += rc.opaque.getIndexCount() / 3;
            }

            if (rc.leaves != null) {
                leafQueue.add(rc);
            }

            if (rc.transparent != null) {
                Chunk chunk = world.getChunks().get(rc.modelMatrix.m30() / Chunk.SIZE); // approximate key lookup
                float dx = chunk != null ? chunk.getWorldX() + 8 - camX : 0;
                float dz = chunk != null ? chunk.getWorldZ() + 8 - camZ : 0;
                rc.sortKey = dx * dx + dz * dz;
                transparentQueue.add(rc);
            }
        }
    }

    /**
     * Leaves and cross plants: alpha-tested like the opaque pass, but with
     * culling off so the cut-outs reveal a back face instead of the inside
     * of the canopy. Depth writes stay on - these are not blended.
     */
    private void renderLeafPass(Shader shader) {
        leafTriangles = 0;
        if (leafQueue.isEmpty()) return;

        glDisable(GL_BLEND);
        glDepthMask(true);
        glDisable(GL_CULL_FACE);

        for (RenderChunk rc : leafQueue) {
            float fade = rc.updateFadeAlpha();
            boolean fading = fade < 1.0f;
            if (fading) {
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            }
            shader.setUniformMat4("model", rc.modelMatrix);
            shader.setUniform1f("fadeAlpha", fade);
            rc.leaves.render();
            if (fading) glDisable(GL_BLEND);
            int tris = rc.leaves.getIndexCount() / 3;
            drawnTriangles += tris;
            leafTriangles += tris;
        }

        applyCulling();
    }

    /** Proof the cut-out pass is actually running, surfaced on F3. */
    private long leafTriangles = 0;
    public long getLeafTriangles() { return leafTriangles; }
    public int getLeafChunks() { return leafQueue.size(); }

    private void renderTransparentPass(World world, Camera camera, Shader shader) {
        if (transparentQueue.isEmpty()) return;

        // Far chunks first so nearer translucent surfaces blend over them
        transparentQueue.sort((a, b) -> Float.compare(b.sortKey, a.sortKey));

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        // Keep depth testing, but don't occlude other translucent surfaces
        glDepthMask(false);
        // Water and leaves should be visible from both sides
        glDisable(GL_CULL_FACE);

        for (RenderChunk rc : transparentQueue) {
            shader.setUniformMat4("model", rc.modelMatrix);
            shader.setUniform1f("fadeAlpha", rc.updateFadeAlpha());
            rc.transparent.render();
            drawnTriangles += rc.transparent.getIndexCount() / 3;
        }

        glDepthMask(true);
        glDisable(GL_BLEND);
        applyCulling();
    }

    // ------------------------------------------------------------------
    // [SD] Sliding doors
    //
    // Doors are never baked into the chunk mesh: the panel animates per
    // frame, so a small dynamic mesh is rebuilt whenever any door moved.
    // Each door is two double-sided quads (bottom + top half), each half
    // sampling its own 16x16 tile from the atlas - the 16x32 sheet is cut,
    // never stretched. The panel sits WALL_OFFSET in front of the wall
    // face and slides sideways into the neighbouring wall cell.
    // ------------------------------------------------------------------

    private Mesh slidingDoorMesh;
    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f().identity();

    private void renderSlidingDoors(World world, Shader shader, Camera camera) {
        java.util.Collection<SlidingDoor> doors = world.getSlidingDoors();
        if (doors.isEmpty()) return;

        java.util.List<Float> positions = new java.util.ArrayList<>(doors.size() * 96);
        java.util.List<Float> texCoords = new java.util.ArrayList<>(doors.size() * 64);
        java.util.List<Float> normals = new java.util.ArrayList<>(doors.size() * 96);
        java.util.List<Float> colors = new java.util.ArrayList<>(doors.size() * 96);
        java.util.List<Float> layers = new java.util.ArrayList<>(doors.size() * 32);
        java.util.List<Float> ao = new java.util.ArrayList<>(doors.size() * 32);
        java.util.List<Float> wave = new java.util.ArrayList<>(doors.size() * 32);
        java.util.List<Float> emissive = new java.util.ArrayList<>(doors.size() * 32);
        java.util.List<Integer> indices = new java.util.ArrayList<>(doors.size() * 36);

        int topLayer = textureAtlas.getSlotByName("sliding_door_top");
        int bottomLayer = textureAtlas.getSlotByName("sliding_door_bottom");
        float camX = camera.getPosition().x;
        float camZ = camera.getPosition().z;
        float cullSq = CULL_DISTANCE * CULL_DISTANCE;

        for (SlidingDoor d : doors) {
            float dcx = d.x + 0.5f - camX;
            float dcz = d.z + 0.5f - camZ;
            if (dcx * dcx + dcz * dcz > cullSq) continue;
            if (world.getBlock(d.x, d.y, d.z) != BlockType.SLIDING_DOOR.id
                || world.getBlock(d.x, d.y + 1, d.z) != BlockType.SLIDING_DOOR.id) {
                continue;
            }

            float slide = d.slideOffset();
            boolean facingZ = d.axis == 0;
            float plane = 0.0f; // flush with the wall's min face, like MC doors
            float off = -SlidingDoor.WALL_OFFSET;

            float light = world.getLight(d.x, d.y, d.z) / (float) Chunk.MAX_LIGHT;
            float shade = 0.18f + 0.82f * light;
            float[] tint = BiomeColors.tintFor(world, BlockType.SLIDING_DOOR.id, 2, d.x, d.y, d.z);

            for (int half = 0; half < 2; half++) {
                float y0 = d.y + half;
                float x0, x1, z0, z1;
                if (facingZ) {
                    x0 = d.x + slide;     x1 = d.x + 1 + slide;
                    z0 = d.z + plane + off; z1 = d.z + plane + off;
                } else {
                    x0 = d.x + plane + off; x1 = d.x + plane + off;
                    z0 = d.z + slide;       z1 = d.z + 1 + slide;
                }
                int layer = half == 0 ? bottomLayer : topLayer;

                for (int side = 0; side < 2; side++) {
                    int base = positions.size() / 3;
                    float sign = (side == 0) ? 1 : -1;
                    float nx = facingZ ? 0.0f : sign;
                    float nz = facingZ ? sign : 0.0f;

                    float[][] corners = {
                        {x0, y0,     z0},
                        {x1, y0,     z1},
                        {x1, y0 + 1, z1},
                        {x0, y0 + 1, z0}
                    };
                    float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                    int[] order = (side == 0) ? new int[]{0, 1, 2, 3} : new int[]{1, 0, 3, 2};

                    for (int i = 0; i < 4; i++) {
                        int c = order[i];
                        positions.add(corners[c][0]);
                        positions.add(corners[c][1]);
                        positions.add(corners[c][2]);
                        texCoords.add(uvs[c][0]);
                        texCoords.add(uvs[c][1]);
                        normals.add(nx);
                        normals.add(0.0f);
                        normals.add(nz);
                        colors.add(tint[0] * shade);
                        colors.add(tint[1] * shade);
                        colors.add(tint[2] * shade);
                        layers.add((float) layer);
                        ao.add(1.0f);
                        wave.add(0.0f);
                        emissive.add(0.0f);
                    }
                    indices.add(base);     indices.add(base + 1); indices.add(base + 2);
                    indices.add(base);     indices.add(base + 2); indices.add(base + 3);
                }
            }
        }

        if (indices.isEmpty()) return;

        if (slidingDoorMesh != null) slidingDoorMesh.cleanup();
        slidingDoorMesh = new Mesh(positions, texCoords, normals, colors, layers, ao, wave, emissive, null, indices);

        glDisable(GL_BLEND);
        glDepthMask(true);
        glDisable(GL_CULL_FACE); // panels are double-sided
        shader.setUniformMat4("model", IDENTITY_MATRIX);
        shader.setUniform1f("fadeAlpha", 1.0f);
        slidingDoorMesh.render();
        applyCulling();
    }

    /**
     * Depth-only pass from the sun's point of view. Only opaque geometry
     * casts shadows; water and glass would otherwise darken everything below.
     */
    public void renderShadowPass(World world, Camera camera, Shader depthShader, ShadowMap shadowMap) {
        // Camera frustum: chunks that are not visible cannot cast visible
        // shadows, so skip them - this is the biggest saving in the pass.
        frustum.update(camera);

        for (Map.Entry<Long, RenderChunk> entry : renderChunks.entrySet()) {
            RenderChunk rc = entry.getValue();
            if (rc.opaque == null) continue;

            Chunk chunk = world.getChunks().get(entry.getKey());
            if (chunk == null) continue;

            float dx = chunk.getWorldX() + 8 - camera.getPosition().x;
            float dz = chunk.getWorldZ() + 8 - camera.getPosition().z;
            if (dx * dx + dz * dz >= CULL_DISTANCE * CULL_DISTANCE) continue;

            // Shadow frustum culling: skip chunks outside the light's view
            if (!shadowMap.isAABBInShadowFrustum(
                    chunk.getWorldX(), 0, chunk.getWorldZ(),
                    chunk.getWorldX() + Chunk.SIZE, Chunk.HEIGHT, chunk.getWorldZ() + Chunk.SIZE)) {
                continue;
            }

            // Skip chunks outside the camera's view
            if (!isChunkVisible(chunk)) continue;

            depthShader.setUniformMat4("model", rc.modelMatrix);
            rc.opaque.render();
        }
    }

    /** Meshes must exist before the shadow pass runs. */
    public void prepare(World world) {
        updateDirtyChunks(world);
    }

    // Per-frame statistics for the debug overlay
    private int drawnChunks = 0;
    private long drawnTriangles = 0;

    public int getDrawnChunks() { return drawnChunks; }
    public long getDrawnTriangles() { return drawnTriangles; }

    public TextureAtlas getTextureAtlas() { return textureAtlas; }

    public void cleanup() {
        for (RenderChunk rc : renderChunks.values()) rc.cleanup();
        renderChunks.clear();
        if (slidingDoorMesh != null) slidingDoorMesh.cleanup();
        slidingDoorMesh = null;
        textureAtlas.cleanup();
    }

    private static class RenderChunk {
        Mesh opaque;
        Mesh transparent;
        Mesh leaves;
        Matrix4f modelMatrix;
        float sortKey;
        /** [GR-002] Fade-in state: bornNs + fadeAlpha for freshly built chunks. */
        private long bornNs = 0;
        private float fadeAlpha = 1.0f;
        private static final long FADE_DURATION_NS = 400_000_000L;

        /** Progress of the fade-in, 0..1; 1 once settled (and forever after). */
        float updateFadeAlpha() {
            if (fadeAlpha >= 1.0f) return 1.0f;
            fadeAlpha = java.lang.Math.min(1.0f,
                (System.nanoTime() - bornNs) / (float) FADE_DURATION_NS);
            return fadeAlpha;
        }

        void rebuild(Chunk chunk, World world, TextureAtlas atlas) {
            // Only the very first build fades in; an edit-triggered rebuild
            // would otherwise flash every time a block is placed nearby.
            boolean firstBuild = opaque == null && transparent == null && leaves == null;
            cleanup();

            modelMatrix = new Matrix4f().translate(chunk.getWorldX(), 0, chunk.getWorldZ());

            ChunkMeshBuilder.MeshData data = ChunkMeshBuilder.build(chunk, world, atlas);
            opaque = data.opaque;
            transparent = data.transparent;
            leaves = data.leaves;

            if (firstBuild) {
                bornNs = System.nanoTime();
                fadeAlpha = 0.0f;
            } else {
                fadeAlpha = 1.0f;
            }
        }

        void cleanup() {
            if (opaque != null) { opaque.cleanup(); opaque = null; }
            if (transparent != null) { transparent.cleanup(); transparent = null; }
            if (leaves != null) { leaves.cleanup(); leaves = null; }
        }
    }
}
