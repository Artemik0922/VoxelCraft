package com.voxelgame.rendering;

import com.voxelgame.item.Item;
import com.voxelgame.item.ItemStack;
import com.voxelgame.rendering.model.BlockCube;
import com.voxelgame.rendering.model.ItemModel3D;
import com.voxelgame.world.*;
import com.voxelgame.world.entity.AsteroidEntity;
import com.voxelgame.world.entity.FallingBlockEntity;
import com.voxelgame.world.entity.FishingBobberEntity;
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
    /** Chunks beyond the fog are invisible, so Game drives this from fogEnd
     *  (renderDistance * 16) plus a one-chunk safety margin. */
    private float cullDistance = 144.0f;

    /** The shadow pass keeps a larger radius so fogged hills still cast
     *  shadows onto the terrain near the player. */
    private static final float SHADOW_CULL_DISTANCE = 144.0f;

    private final Map<Long, RenderChunk> renderChunks = new HashMap<>();
    private final TextureAtlas textureAtlas;
    private BlockCube heldItemCube = new BlockCube();

    /** Unit-cube faces in BlockCube order: 0 +X, 1 -X, 2 +Y, 3 -Y, 4 +Z, 5 -Z. */
    private static final float[][][] ITEM_CUBE_FACES = {
        {{ 0.5f,-0.5f, 0.5f},{ 0.5f,-0.5f,-0.5f},{ 0.5f, 0.5f,-0.5f},{ 0.5f, 0.5f, 0.5f}},
        {{-0.5f,-0.5f,-0.5f},{-0.5f,-0.5f, 0.5f},{-0.5f, 0.5f, 0.5f},{-0.5f, 0.5f,-0.5f}},
        {{-0.5f, 0.5f, 0.5f},{ 0.5f, 0.5f, 0.5f},{ 0.5f, 0.5f,-0.5f},{-0.5f, 0.5f,-0.5f}},
        {{-0.5f,-0.5f,-0.5f},{ 0.5f,-0.5f,-0.5f},{ 0.5f,-0.5f, 0.5f},{-0.5f,-0.5f, 0.5f}},
        {{-0.5f,-0.5f, 0.5f},{ 0.5f,-0.5f, 0.5f},{ 0.5f, 0.5f, 0.5f},{-0.5f, 0.5f, 0.5f}},
        {{ 0.5f,-0.5f,-0.5f},{-0.5f,-0.5f,-0.5f},{-0.5f, 0.5f,-0.5f},{ 0.5f, 0.5f,-0.5f}},
    };
    private static final float[] ITEM_CUBE_SHADES = {0.80f, 0.80f, 1.00f, 0.55f, 0.68f, 0.68f};
    private static final float[][] ITEM_CUBE_NORMALS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
    };

    /** Last frame's batched item-entity mesh, rebuilt whenever items exist. */
    private Mesh itemBatchMesh = null;

    /** Held-block pipeline for rendering dropped items as 3D voxel models. */
    private Shader itemShader = null;

    // Reused each frame to avoid per-frame allocation
    private final List<RenderChunk> transparentQueue = new ArrayList<>();
    private final List<RenderChunk> leafQueue = new ArrayList<>();

    public Renderer() {
        this("");
    }

    public Renderer(String resourcePackName) {
        textureAtlas = new TextureAtlas(resourcePackName);
    }

    /** [OPT] Fade-out distance for chunk/entity culling, set from fogEnd. */
    public void setCullDistance(float blocks) {
        cullDistance = blocks;
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
        shader.setUniformMat4("model", IDENTITY_MATRIX);

        glActiveTexture(GL_TEXTURE0);
        atlas.bindArray();

        float camX = camera.getPosition().x;
        float camZ = camera.getPosition().z;
        float cullSq = cullDistance * cullDistance;

        // Batch every visible item cube into one mesh (one draw call instead
        // of one per entity). Baking the true per-face atlas layer also fixes
        // the old path, which always sampled atlas layer 0. The mesh is
        // rebuilt every frame because items bob and spin.
        List<Float> positions = new ArrayList<>(items.size() * 72);
        List<Float> texCoords = new ArrayList<>(items.size() * 48);
        List<Float> normals = new ArrayList<>(items.size() * 72);
        List<Float> colors = new ArrayList<>(items.size() * 72);
        List<Float> layers = new ArrayList<>(items.size() * 24);
        List<Float> ao = new ArrayList<>(items.size() * 24);
        List<Float> wave = new ArrayList<>(items.size() * 24);
        List<Float> emissive = new ArrayList<>(items.size() * 24);
        List<Integer> indices = new ArrayList<>(items.size() * 36);

        for (var item : items) {
            Vector3f pos = item.getPosition();
            float dx = pos.x - camX;
            float dz = pos.z - camZ;
            if (dx * dx + dz * dz > cullSq) continue;

            // Items with a sprite tile render as 3D voxel models in pass II
            if (isSpriteItem(item.getStack())) continue;

            BlockType renderBlock = item.getStack().isItem()
                ? getItemRenderBlock(item.getStack().getItem())
                : item.getStack().getBlockType();

            int[] faceLayers = new int[6];
            for (int face = 0; face < 6; face++) {
                faceLayers[face] = atlas.getSlot(renderBlock.id, face);
            }

            Matrix4f model = new Matrix4f();
            model.translate(pos.x, pos.y + item.getBobOffset(), pos.z);
            model.rotateY((float) java.lang.Math.toRadians(item.getRotation()));
            model.rotateX((float) java.lang.Math.toRadians(20));
            model.scale(0.3f);

            Matrix3f rot = new Matrix3f(model);
            for (int face = 0; face < 6; face++) {
                float[][] corners = ITEM_CUBE_FACES[face];
                float shade = ITEM_CUBE_SHADES[face];
                Vector3f n = rot.transform(new Vector3f(ITEM_CUBE_NORMALS[face]))
                             .normalize();
                int base = positions.size() / 3;
                int[] order = {0, 1, 2, 0, 2, 3};
                float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                for (int i = 0; i < 6; i++) {
                    float[] c = corners[order[i]];
                    Vector3f v = model.transformPosition(new Vector3f(c[0], c[1], c[2]));
                    positions.add(v.x);
                    positions.add(v.y);
                    positions.add(v.z);
                    texCoords.add(uvs[order[i]][0]);
                    texCoords.add(uvs[order[i]][1]);
                    normals.add(n.x);
                    normals.add(n.y);
                    normals.add(n.z);
                    colors.add(shade);
                    colors.add(shade);
                    colors.add(shade);
                    layers.add((float) faceLayers[face]);
                    ao.add(1.0f);
                    wave.add(0.0f);
                    emissive.add(0.0f);
                    indices.add(base + i);
                }
            }
        }

        if (indices.isEmpty()) {
            atlas.unbindArray();
            shader.unbind();
            return;
        }

        if (itemBatchMesh != null) itemBatchMesh.cleanup();
        itemBatchMesh = new Mesh(positions, texCoords, normals, colors, layers, ao, wave, emissive, indices);
        itemBatchMesh.render();

        renderSpriteItemEntities(items, world, camera, lightLevel);

        atlas.unbindArray();
        shader.unbind();
    }

    /** True when a dropped stack is rendered as a voxel item, not a cube. */
    private boolean isSpriteItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.isBlock()) return false;
        Item it = stack.getItem();
        return it != null && it.spriteName != null;
    }

    /**
     * Draw loose item entities that have a sprite tile as chunky 3D voxel
     * models (the same ItemModel3D used in the players' hands), so a thrown
     * sword no longer looks like a stone cube.
     */
    private void renderSpriteItemEntities(List<ItemEntity> items, World world,
                                          Camera camera, float lightLevel) {
        boolean any = false;
        for (var item : items) {
            ItemStack stack = item.getStack();
            if (!isSpriteItem(stack)) continue;

            Vector3f pos = item.getPosition();
            Item it = stack.getItem();
            int layer = textureAtlas.getLayerOf(it.spriteName);
            ItemModel3D model3d = ItemModel3D.get(textureAtlas, layer);
            if (model3d == null || model3d.getVertexCount() == 0) continue;

            if (!any) {
                if (itemShader == null) {
                    itemShader = new Shader("shaders/heldblock.vert",
                        "shaders/heldblock.frag");
                }
                itemShader.bind();
                itemShader.setUniformMat4("projection", camera.getProjectionMatrix());
                itemShader.setUniformMat4("view", camera.getViewMatrix());
                itemShader.setUniform1i("blockTextures", 0);
                itemShader.setUniform1f("lightLevel", lightLevel);
                itemShader.setUniform3f("sunColor", new Vector3f(1.0f, 1.0f, 1.0f));
                any = true;
            }

            Matrix4f model = new Matrix4f();
            model.translate(pos.x, pos.y + item.getBobOffset(), pos.z);
            model.rotateY((float) java.lang.Math.toRadians(item.getRotation()));
            model.rotateX((float) java.lang.Math.toRadians(35));
            model.scale(0.45f);
            itemShader.setUniformMat4("model", model);

            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
            glFrontFace(GL_CCW);

            model3d.render(itemShader);
        }
        if (any) itemShader.unbind();
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
     * [FISH] Draw the fishing bobber: a small red-and-white cube that bobs
     * on the water surface and darts when a fish bites.
     */
    public void renderFishingBobber(World world, Shader shader, Camera camera,
                                     com.voxelgame.rendering.TextureAtlas atlas,
                                     float lightLevel) {
        if (world.getFishingBobber() == null) return;

        FishingBobberEntity bobber = world.getFishingBobber();
        if (bobber.isDead()) return;

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

        Vector3f pos = bobber.getPosition();
        org.joml.Matrix4f model = new org.joml.Matrix4f();
        model.identity();
        model.translate(pos.x, pos.y + 0.1f, pos.z);

        // Bobbing animation
        float bob = (float) java.lang.Math.sin(System.nanoTime() / 500_000_000.0) * 0.05f;
        model.translate(0, bob, 0);

        // Flash red when fish is biting
        if (bobber.getState() == FishingBobberEntity.State.BITE) {
            float flash = (float) java.lang.Math.sin(System.nanoTime() / 100_000_000.0);
            if (flash > 0) {
                shader.setUniform3f("sunColor", new Vector3f(2.0f, 0.5f, 0.5f));
            }
        }

        model.scale(0.15f);
        shader.setUniformMat4("model", model);

        heldItemCube.render(atlas, BlockType.REDSTONE_ORE, shader);

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

            // [PERF-ASYNC] Геометрия уже строится в воркере — дождёмся заливки
            if (inFlightBuilds.containsKey(entry.getKey())) { meshBacklog++; continue; }

            // Очередь воркеров заполнена — остаток переносится на следующие кадры
            if (inFlightBuilds.size() >= MAX_IN_FLIGHT_BUILDS) { meshBacklog++; continue; }

            submitGeometryBuild(entry.getKey(), chunk, world);
            meshBacklog++;
        }

        processFinishedBuilds(world);
    }

    // ------------------------------------------------------------------
    // [PERF-ASYNC] Асинхронная сборка геометрии чанков.
    //
    // Раньше ChunkMeshBuilder.build (чистый CPU, 20-70 мс на сложный чанк)
    // выполнялся прямо в кадре: бюджет 6 мс проверялся только перед началом
    // работы над чанком, поэтому один тяжёлый чанк раздувал кадр до 50-80 мс
    // и FPS при полёте падал до 12-20. Теперь геометрию строят фоновые
    // потоки, а главный поток только заливает готовые меши в GL
    // (не больше UPLOAD_BUDGET_PER_FRAME за кадр).
    //
    // Корректность: геометрия зависит от данных чанка и 8 соседей. Перед
    // стартом запоминаются их meshVersion; результат принимается, только
    // если ни один счётчик не изменился, чанк по-прежнему в мире, грязен
    // и его свет уже пересчитан. Иначе результат выбрасывается, и чанк
    // перестраивается в следующем кадре. Все GL-вызовы, как и раньше,
    // только на главном потоке.
    // ------------------------------------------------------------------

    /** Готовые меши, заливаемые в GPU за один кадр. */
    private static final int UPLOAD_BUDGET_PER_FRAME = 3;
    /** Одновременно строящихся геометрий не больше этого числа. */
    private static final int MAX_IN_FLIGHT_BUILDS = 8;

    private final java.util.concurrent.ExecutorService meshWorkers =
        java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "mesh-worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

    private final Map<Long, PendingGeom> inFlightBuilds = new HashMap<>();

    private static final class PendingGeom {
        long key;
        Chunk chunk;
        World world;
        Chunk[] neighbors;
        long[] versions;
        java.util.concurrent.Future<ChunkMeshBuilder.ChunkGeom> future;
    }

    private void submitGeometryBuild(long key, Chunk chunk, World world) {
        PendingGeom p = new PendingGeom();
        p.key = key;
        p.chunk = chunk;
        p.world = world;
        p.neighbors = new Chunk[9];
        p.versions = new long[9];
        int cx = chunk.getChunkX();
        int cz = chunk.getChunkZ();
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk n = world.getChunk(cx + dx, cz + dz);
                p.neighbors[i] = n;
                p.versions[i] = n != null ? n.getMeshVersion() : 0L;
                i++;
            }
        }
        try {
            p.future = meshWorkers.submit(() -> ChunkMeshBuilder.build(chunk, world, textureAtlas));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            return; // пул уже выключен; чанк остаётся грязным
        }
        inFlightBuilds.put(key, p);
    }

    private void processFinishedBuilds(World world) {
        if (inFlightBuilds.isEmpty()) return;
        int uploads = 0;
        java.util.Iterator<Map.Entry<Long, PendingGeom>> it =
            inFlightBuilds.entrySet().iterator();
        while (it.hasNext() && uploads < UPLOAD_BUDGET_PER_FRAME) {
            Map.Entry<Long, PendingGeom> e = it.next();
            PendingGeom p = e.getValue();
            if (!p.future.isDone()) continue;
            it.remove();

            boolean valid = p.world == world
                && world.getChunks().get(p.key) == p.chunk
                && p.chunk.isDirty()
                && !p.chunk.isLightDirty()
                && versionsUnchanged(p);
            if (!valid) continue; // данные изменились — чанк перестроится позже

            ChunkMeshBuilder.ChunkGeom geom;
            try {
                geom = p.future.get();
            } catch (Exception ex) {
                // Синхронный путь как до оптимизации — гарантия результата
                System.err.println("Async mesh build failed, rebuilding sync: " + ex);
                RenderChunk rc = renderChunks.computeIfAbsent(p.key, k -> new RenderChunk());
                rc.rebuild(p.chunk, p.world, textureAtlas);
                p.chunk.setDirty(false);
                remeshGeneration++;
                uploads++;
                continue;
            }

            RenderChunk rc = renderChunks.computeIfAbsent(p.key, k -> new RenderChunk());
            rc.applyGeometry(p.chunk, geom);
            p.chunk.setDirty(false);
            remeshGeneration++;
            uploads++;
        }
    }

    private boolean versionsUnchanged(PendingGeom p) {
        for (int i = 0; i < p.neighbors.length; i++) {
            Chunk n = p.neighbors[i];
            if (n != null && n.getMeshVersion() != p.versions[i]) return false;
        }
        return true;
    }

    /** How many chunk meshes have been rebuilt; drives shadow-map invalidation. */
    private long remeshGeneration = 0;

    /** Monotonic counter bumped on every chunk remesh. */
    public long getRemeshGeneration() { return remeshGeneration; }

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
    /** [PERF] Сколько геометрий прямо сейчас строится в воркерах. */
    public int getInFlightBuilds() { return inFlightBuilds.size(); }
    /** [PERF-ASYNC] Диагностика последней заливки геометрии. */
    private static volatile String lastUploadKey = "-";
    private static volatile int lastUploadQuads = -1;
    public String getLastUploadKey() { return lastUploadKey; }
    public int getLastUploadQuads() { return lastUploadQuads; }

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
                if (ddx * ddx + ddz * ddz >= cullDistance * cullDistance) continue;
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
                // Use the packed chunk key so the map lookup actually finds the chunk
                int cx = (int) (rc.modelMatrix.m30() / Chunk.SIZE);
                int cz = (int) (rc.modelMatrix.m32() / Chunk.SIZE);
                Chunk chunk = world.getChunks().get(Chunk.key(cx, cz));
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
        // Keep depth testing; the pre-pass below locks the layer depth.
        glDisable(GL_CULL_FACE);

        // [GR-017] Depth pre-pass: write depth only (no color) so the real
        // transparent draw uses GL_EQUAL and never overdraws itself within
        // the same layer, eliminating the worst sorting artifacts.
        glColorMask(false, false, false, false);
        glDepthMask(true);
        glDepthFunc(GL_LESS);
        for (RenderChunk rc : transparentQueue) {
            shader.setUniformMat4("model", rc.modelMatrix);
            shader.setUniform1f("fadeAlpha", 1.0f);
            rc.transparent.render();
        }

        // Now draw colour with depth locked to the pre-pass values
        glColorMask(true, true, true, true);
        glDepthMask(false);
        glDepthFunc(GL_EQUAL);
        for (RenderChunk rc : transparentQueue) {
            shader.setUniformMat4("model", rc.modelMatrix);
            shader.setUniform1f("fadeAlpha", rc.updateFadeAlpha());
            rc.transparent.render();
            drawnTriangles += rc.transparent.getIndexCount() / 3;
        }

        glDepthFunc(GL_LESS);
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
        float cullSq = cullDistance * cullDistance;

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
            if (dx * dx + dz * dz >= SHADOW_CULL_DISTANCE * SHADOW_CULL_DISTANCE) continue;

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
        // [PERF-ASYNC] Остановить воркеры геометрии и забыть незавершённые сборки
        meshWorkers.shutdownNow();
        inFlightBuilds.clear();
        for (RenderChunk rc : renderChunks.values()) rc.cleanup();
        renderChunks.clear();
        if (slidingDoorMesh != null) slidingDoorMesh.cleanup();
        slidingDoorMesh = null;
        if (itemBatchMesh != null) itemBatchMesh.cleanup();
        itemBatchMesh = null;
        if (itemShader != null) itemShader.cleanup();
        itemShader = null;
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
            applyGeometry(chunk, ChunkMeshBuilder.build(chunk, world, atlas));
        }

        /**
         * [PERF-ASYNC] Заливка готовой геометрии в GL (только главный поток).
         * CPU-часть вынесена в воркеры, здесь остался строго GL + состояние.
         */
        void applyGeometry(Chunk chunk, ChunkMeshBuilder.ChunkGeom geom) {
            // Only the very first build fades in; an edit-triggered rebuild
            // would otherwise flash every time a block is placed nearby.
            boolean firstBuild = opaque == null && transparent == null && leaves == null;

            modelMatrix = new Matrix4f().translate(chunk.getWorldX(), 0, chunk.getWorldZ());

            opaque = Mesh.rebind(opaque, geom.opaque);
            transparent = Mesh.rebind(transparent, geom.transparent);
            leaves = Mesh.rebind(leaves, geom.leaves);

            // [PERF-ASYNC] Диагностика: что именно залили
            lastUploadKey = chunk.getChunkX() + "," + chunk.getChunkZ();
            lastUploadQuads = geom.opaque == null ? 0 : geom.opaque.indices.size() / 6;

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
