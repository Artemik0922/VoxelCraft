package com.voxelgame.rendering;

import com.voxelgame.world.BlockType;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.EXTTextureFilterAnisotropic;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Texture Atlas - 16x16 tiles packed into a 256x256 atlas.
 *
 * Tiles are loaded from textures/blocks/*.png (CC0 pack) when available and
 * fall back to procedural pixel art when a PNG is missing.
 */
public class TextureAtlas {
    public static final int ATLAS_SIZE = 256;
    public static final int TEXTURE_SIZE = 16;
    public static final int TEXTURES_PER_ROW = ATLAS_SIZE / TEXTURE_SIZE;

    /** 16 -> 8 -> 4 -> 2 -> 1. Stop before tiles turn into a single averaged texel. */
    private static final int MIP_LEVELS = 5;
    private static final float ANISOTROPY = 8.0f;

    /** Variants generated for high-coverage tiles like grass and stone. */
    private static final int VARIANTS = 4;

    // Face indices used by ChunkMeshBuilder: 0=+X 1=-X 2=+Y 3=-Y 4=+Z 5=-Z
    public static final int FACE_TOP = 2;
    public static final int FACE_BOTTOM = 3;

    private Texture atlasTexture;
    private int arrayTextureId = 0;
    private float anisotropyUsed = 1.0f;
    private final int[] atlasPixels = new int[ATLAS_SIZE * ATLAS_SIZE];
    /** One 16x16 ARGB tile per layer, in slot order. Feeds the texture array. */
    private final List<int[]> layerPixels = new ArrayList<>();
    private final Map<String, Integer> slotByName = new HashMap<>();
    /** tile name -> how many variants exist */
    private final Map<String, Integer> variantCount = new HashMap<>();
    /** tile name -> slot of variant 0 (variants are contiguous) */
    private final Map<String, Integer> variantFirstSlot = new HashMap<>();
    /** face slot -> tile name, for resolving variants at mesh time */
    private final Map<Integer, String> nameBySlot = new HashMap<>();
    private ResourcePack resourcePack;
    private final Map<Integer, int[]> faceSlots = new HashMap<>(); // blockId -> {top, side, bottom}
    private int nextSlot = 0;
    private int loadedFromPng = 0;
    private int generated = 0;

    /**
     * UV rectangle of one tile inside the atlas.
     * v1 = bottom edge of the tile, v2 = top edge (atlas is uploaded flipped).
     */
    public static class TextureCoords {
        public final float u1, v1, u2, v2;

        public TextureCoords(int slot) {
            int x = slot % TEXTURES_PER_ROW;
            int y = slot / TEXTURES_PER_ROW;
            // Half-texel inset stops neighbouring tiles bleeding in at mip/edge
            float pad = 0.5f / ATLAS_SIZE;

            this.u1 = (x * TEXTURE_SIZE) / (float) ATLAS_SIZE + pad;
            this.u2 = ((x + 1) * TEXTURE_SIZE) / (float) ATLAS_SIZE - pad;
            this.v1 = 1.0f - ((y + 1) * TEXTURE_SIZE) / (float) ATLAS_SIZE + pad;
            this.v2 = 1.0f - (y * TEXTURE_SIZE) / (float) ATLAS_SIZE - pad;
        }
    }

    public TextureAtlas() {
        this("");
    }

    public TextureAtlas(String resourcePackName) {
        this.resourcePack = new ResourcePack(resourcePackName);

        registerTiles();
        mapBlockFaces();

        // Reverse index so getSlot() can find a tile's variant group
        for (Map.Entry<String, Integer> e : slotByName.entrySet()) {
            nameBySlot.put(e.getValue(), baseName(e.getKey()));
        }

        build();
    }

    private static String baseName(String key) {
        int hash = key.indexOf('#');
        return hash < 0 ? key : key.substring(0, hash);
    }

    // ------------------------------------------------------------------
    // Tile registration
    // ------------------------------------------------------------------

    private void registerTiles() {
        // Tiles with several variants: the mesher picks one per block
        // position, which breaks up the repetition on large flat surfaces.
        //
        // Foliage is generated rather than taken from a pack: the bundled
        // grass_top.png measured 2.05x green dominance, and since the biome
        // tint is a multiplier that produced green-on-green. The generated
        // palette is near-grey so the tint alone sets the hue.
        // Single PNG tile, no procedural variants
        tile("grass_top", "grass_top.png", BlockType.GRASS_BLOCK);
        tile("dirt", "dirt.png", BlockType.DIRT);
        variants("stone", BlockType.STONE, VARIANTS);
        variants("sand", BlockType.SAND, VARIANTS);

        // Grass side: loads grass_side.png from pack, procedural fallback
        tile("grass_side", "grass_side.png", BlockType.GRASS_BLOCK);
        tile("cobblestone",       "cobblestone.png",       BlockType.COBBLESTONE);
        tile("mossy_cobblestone", "mossy_cobblestone.png", BlockType.MOSSY_COBBLESTONE);
        tile("oak_planks",        "oak_planks.png",        BlockType.OAK_PLANKS);
        tile("oak_log",           "oak_log.png",           BlockType.OAK_LOG);
        tile("oak_log_top",       "oak_log_top.png",       BlockType.OAK_LOG);
        // Birch logs
        tile("birch_log_side",    "birch_log_side.png",    BlockType.BIRCH_LOG);
        tile("birch_log_top",     "birch_log_top.png",     BlockType.BIRCH_LOG);
        // Birch leaves
        tile("birch_leaves",      "birch_leaves.png",      BlockType.BIRCH_LEAVES);
        // Generated for the same reason as grass, and because a pack tile
        // would not carry the ~30% cut-outs the canopy needs.
        // Both variants are uploaded so the graphics setting can switch
        // between them without rebuilding the atlas.
        variantsGenerated("oak_leaves", BlockType.OAK_LEAVES, VARIANTS);
        variantsGenerated("oak_leaves_opaque", BlockType.OAK_LEAVES, VARIANTS);
        tile("sandstone",         "sandstone.png",         BlockType.TERRACOTTA);
        tile("gravel",            "gravel.png",            BlockType.GRAVEL);
        // Overlays: mineral specks meant to sit on top of a stone tile
        tileGenerated("ore_coal",    BlockType.COAL_ORE);
        tileGenerated("ore_iron",    BlockType.IRON_ORE);
        tileGenerated("ore_gold",    BlockType.GOLD_ORE);
        tileGenerated("ore_diamond", BlockType.DIAMOND_ORE);
        tile("bedrock",           "bedrock.png",           BlockType.BEDROCK);
        tile("water",             "water.png",             BlockType.WATER);
        tile("lava",              "lava.png",              BlockType.LAVA);
        tile("glass",             "glass.png",             BlockType.GLASS);
        tile("brick",             "brick.png",             BlockType.BRICK);
        tile("stone_bricks",      "stone_bricks.png",      BlockType.STONE_BRICKS);
        tile("granite",           "granite.png",           BlockType.GRANITE);
        tile("tnt",               "tnt.png",               BlockType.TNT);
        tile("gold_block",        "gold_block.png",        BlockType.GOLD_BLOCK);
        tile("iron_block",        "iron_block.png",        BlockType.IRON_BLOCK);
        tile("diamond_block",     "diamond_block.png",     BlockType.DIAMOND_BLOCK);
        tile("coal_block",        "coal_block.png",        BlockType.COAL_BLOCK);
        tile("crafting_table",    "crafting_table.png",    BlockType.CRAFTING_TABLE);
        tile("snow",              "snow.png",              BlockType.SNOW);
        tile("ice",               "ice.png",               BlockType.ICE);
        tile("clay",              "clay.png",              BlockType.CLAY);
        tile("podzol",            "podzol.png",            BlockType.PODZOL);
        tile("obsidian",          "obsidian.png",          BlockType.OBSIDIAN);
        tile("netherrack",        "netherrack.png",        BlockType.NETHERRACK);
        tile("glowstone",         "glowstone.png",         BlockType.GLOWSTONE);

        // Cross-quad plants: cut-outs on a transparent background
        tile("grass_plant", "grass_plant.png", BlockType.GRASS_PLANT);
        tileGenerated("dandelion", BlockType.DANDELION);
        tileGenerated("poppy", BlockType.POPPY);
        tileGenerated("dead_bush", BlockType.DEAD_BUSH);
        tile("cactus_side",       "cactus_side.png",       BlockType.CACTUS);
        tile("cactus_top",        "cactus_top.png",        BlockType.CACTUS);

        // --- Biome blocks ---
        tileGenerated("autumn_leaves", BlockType.AUTUMN_LEAVES);
        tileGenerated("cherry_leaves", BlockType.CHERRY_LEAVES);
        tileGenerated("lavender", BlockType.LAVENDER);
        tileGenerated("crystal", BlockType.CRYSTAL);
        tileGenerated("basalt", BlockType.BASALT);
        tileGenerated("salt", BlockType.SALT);
        tileGenerated("ash", BlockType.ASH);
        tileGenerated("charred_log", BlockType.CHARRED_LOG);
        tileGenerated("charred_log_top", BlockType.CHARRED_LOG);
        tileGenerated("petrified_log", BlockType.PETRIFIED_LOG);
        tileGenerated("petrified_log_top", BlockType.PETRIFIED_LOG);
        tileGenerated("blue_ice", BlockType.BLUE_ICE);
        tileGenerated("mycelium_top", BlockType.MYCELIUM);
        tileGenerated("terracotta", BlockType.TERRACOTTA);
        tileGenerated("red_sand", BlockType.RED_SAND);
        tileGenerated("lily_pad", BlockType.LILY_PAD);
        tileGenerated("sunflower", BlockType.SUNFLOWER);
        tileGenerated("vine", BlockType.VINE);
        tileGenerated("deepslate", BlockType.DEEPSLATE);

        // --- Missing blocks for creative inventory ---
        tileGenerated("stone", BlockType.STONE);
        tileGenerated("dirt", BlockType.DIRT);
        tileGenerated("sand", BlockType.SAND);
        tileGenerated("granite", BlockType.GRANITE);
        tileGenerated("diorite", BlockType.DIORITE);
        tileGenerated("andesite", BlockType.ANDESITE);
        tileGenerated("polished_granite", BlockType.POLISHED_GRANITE);
        tileGenerated("polished_diorite", BlockType.POLISHED_DIORITE);
        tileGenerated("polished_andesite", BlockType.POLISHED_ANDESITE);
        tileGenerated("coarse_dirt", BlockType.COARSE_DIRT);

        // Planks
        tileGenerated("spruce_planks", BlockType.SPRUCE_PLANKS);
        tileGenerated("birch_planks", BlockType.BIRCH_PLANKS);
        tileGenerated("jungle_planks", BlockType.JUNGLE_PLANKS);
        tileGenerated("acacia_planks", BlockType.ACACIA_PLANKS);
        tileGenerated("dark_oak_planks", BlockType.DARK_OAK_PLANKS);

        // Logs
        tileGenerated("spruce_log", BlockType.SPRUCE_LOG);
        tileGenerated("spruce_log_top", BlockType.SPRUCE_LOG);
        tileGenerated("jungle_log", BlockType.JUNGLE_LOG);
        tileGenerated("jungle_log_top", BlockType.JUNGLE_LOG);

        // Leaves
        variantsGenerated("spruce_leaves", BlockType.SPRUCE_LEAVES, VARIANTS);
        variantsGenerated("jungle_leaves", BlockType.JUNGLE_LEAVES, VARIANTS);

        // Ores
        tileGenerated("emerald_ore", BlockType.EMERALD_ORE);
        tileGenerated("redstone_ore", BlockType.REDSTONE_ORE);
        tileGenerated("lapis_ore", BlockType.LAPIS_ORE);
        tileGenerated("copper_ore", BlockType.COPPER_ORE);

        // Blocks
        tileGenerated("emerald_block", BlockType.EMERALD_BLOCK);
        tileGenerated("white_wool", BlockType.WHITE_WOOL);
        tileGenerated("furnace", BlockType.FURNACE);
        tileGenerated("furnace_side", BlockType.FURNACE);
        tileGenerated("chest", BlockType.CHEST);

        // Crops
        tile("wheat_stage0", "wheat_stage0.png", BlockType.WHEAT);
        tile("wheat_stage1", "wheat_stage1.png", BlockType.WHEAT);
        tile("wheat_stage2", "wheat_stage2.png", BlockType.WHEAT);
        tile("wheat_stage3", "wheat_stage3.png", BlockType.WHEAT);
        tileGenerated("carrot", BlockType.CARROT);
        tileGenerated("potato", BlockType.POTATO);

        // Others
        tileGenerated("bookshelf", BlockType.BOOKSHELF);
        tileGenerated("soul_sand", BlockType.SOUL_SAND);
        tileGenerated("end_stone", BlockType.END_STONE);
        tileGenerated("purpur_block", BlockType.PURPUR_BLOCK);
        tileGenerated("white_concrete", BlockType.WHITE_CONCRETE);
        tileGenerated("red_concrete", BlockType.RED_CONCRETE);
        tileGenerated("green_concrete", BlockType.GREEN_CONCRETE);
        tileGenerated("blue_concrete", BlockType.BLUE_CONCRETE);
        tileGenerated("glowing_obsidian", BlockType.GLOWING_OBSIDIAN);
        tileGenerated("nether_reactor", BlockType.NETHER_REACTOR);
        tileGenerated("nether_bricks", BlockType.NETHER_BRICKS);

        // --- Items (flat sprites) ---
        tileGenerated("iron_sword", BlockType.IRON_SWORD);
        tileGenerated("stick", BlockType.ITEM_STICK);
        tileGenerated("coal", BlockType.ITEM_COAL);
        tileGenerated("iron_ingot", BlockType.ITEM_IRON_INGOT);
        tileGenerated("gold_ingot", BlockType.ITEM_GOLD_INGOT);
        tileGenerated("diamond", BlockType.ITEM_DIAMOND);
        tileGenerated("leather", BlockType.ITEM_LEATHER);
        tileGenerated("string", BlockType.ITEM_STRING);
        tileGenerated("feather", BlockType.ITEM_FEATHER);
        tileGenerated("flint", BlockType.ITEM_FLINT);
        tileGenerated("gunpowder", BlockType.ITEM_GUNPOWDER);
        tileGenerated("wheat", BlockType.ITEM_WHEAT);
        tileGenerated("egg", BlockType.ITEM_EGG);

        // Tools
        tileGenerated("wooden_pickaxe", BlockType.ITEM_WOODEN_PICKAXE);
        tileGenerated("wooden_axe", BlockType.ITEM_WOODEN_AXE);
        tileGenerated("wooden_shovel", BlockType.ITEM_WOODEN_SHOVEL);
        tileGenerated("wooden_sword", BlockType.ITEM_WOODEN_SWORD);
        tileGenerated("wooden_hoe", BlockType.ITEM_WOODEN_HOE);
        tileGenerated("stone_pickaxe", BlockType.ITEM_STONE_PICKAXE);
        tileGenerated("stone_axe", BlockType.ITEM_STONE_AXE);
        tileGenerated("stone_shovel", BlockType.ITEM_STONE_SHOVEL);
        tileGenerated("stone_sword", BlockType.ITEM_STONE_SWORD);
        tileGenerated("stone_hoe", BlockType.ITEM_STONE_HOE);
        tileGenerated("iron_pickaxe", BlockType.ITEM_IRON_PICKAXE);
        tileGenerated("iron_axe", BlockType.ITEM_IRON_AXE);
        tileGenerated("iron_shovel", BlockType.ITEM_IRON_SHOVEL);
        tileGenerated("iron_sword_item", BlockType.ITEM_IRON_SWORD);
        tileGenerated("iron_hoe", BlockType.ITEM_IRON_HOE);
        tileGenerated("diamond_pickaxe", BlockType.ITEM_DIAMOND_PICKAXE);
        tileGenerated("diamond_axe", BlockType.ITEM_DIAMOND_AXE);
        tileGenerated("diamond_shovel", BlockType.ITEM_DIAMOND_SHOVEL);
        tileGenerated("diamond_sword", BlockType.ITEM_DIAMOND_SWORD);
        tileGenerated("diamond_hoe", BlockType.ITEM_DIAMOND_HOE);
        tileGenerated("golden_pickaxe", BlockType.ITEM_GOLDEN_PICKAXE);
        tileGenerated("golden_axe", BlockType.ITEM_GOLDEN_AXE);
        tileGenerated("golden_shovel", BlockType.ITEM_GOLDEN_SHOVEL);
        tileGenerated("golden_sword", BlockType.ITEM_GOLDEN_SWORD);
        tileGenerated("golden_hoe", BlockType.ITEM_GOLDEN_HOE);

        // Armor
        tileGenerated("leather_helmet", BlockType.ITEM_LEATHER_HELMET);
        tileGenerated("leather_chestplate", BlockType.ITEM_LEATHER_CHESTPLATE);
        tileGenerated("leather_leggings", BlockType.ITEM_LEATHER_LEGGINGS);
        tileGenerated("leather_boots", BlockType.ITEM_LEATHER_BOOTS);
        tileGenerated("iron_helmet", BlockType.ITEM_IRON_HELMET);
        tileGenerated("iron_chestplate", BlockType.ITEM_IRON_CHESTPLATE);
        tileGenerated("iron_leggings", BlockType.ITEM_IRON_LEGGINGS);
        tileGenerated("iron_boots", BlockType.ITEM_IRON_BOOTS);
        tileGenerated("diamond_helmet", BlockType.ITEM_DIAMOND_HELMET);
        tileGenerated("diamond_chestplate", BlockType.ITEM_DIAMOND_CHESTPLATE);
        tileGenerated("diamond_leggings", BlockType.ITEM_DIAMOND_LEGGINGS);
        tileGenerated("diamond_boots", BlockType.ITEM_DIAMOND_BOOTS);

        // Food
        tileGenerated("bread", BlockType.ITEM_BREAD);
        tileGenerated("raw_pork", BlockType.ITEM_RAW_PORK);
        tileGenerated("cooked_pork", BlockType.ITEM_COOKED_PORK);
        tileGenerated("raw_beef", BlockType.ITEM_RAW_BEEF);
        tileGenerated("cooked_beef", BlockType.ITEM_COOKED_BEEF);
        tileGenerated("raw_chicken", BlockType.ITEM_RAW_CHICKEN);
        tileGenerated("cooked_chicken", BlockType.ITEM_COOKED_CHICKEN);
        tileGenerated("raw_mutton", BlockType.ITEM_RAW_MUTTON);
        tileGenerated("cooked_mutton", BlockType.ITEM_COOKED_MUTTON);
        tileGenerated("apple", BlockType.ITEM_APPLE);

        // Misc
        tileGenerated("bow", BlockType.ITEM_BOW);
        tileGenerated("arrow", BlockType.ITEM_ARROW);
        tileGenerated("torch", BlockType.ITEM_TORCH);
        tileGenerated("rotten_flesh", BlockType.ITEM_ROTTEN_FLESH);
        tileGenerated("bone", BlockType.ITEM_BONE);
        tileGenerated("spider_eye", BlockType.ITEM_SPIDER_EYE);
    }

    /**
     * Register several variants of one tile under "name", "name#1", ...
     *
     * A resource pack overrides variant 0 only; the remaining variants are
     * generated, so a pack that supplies a single grass texture still works
     * (all variants collapse to it visually).
     */
    private void variants(String name, BlockType type, int count) {
        int first = nextSlot;
        for (int i = 0; i < count; i++) {
            String key = (i == 0) ? name : name + "#" + i;

            int[] pixels = (i == 0) ? loadTilePng(name + ".png") : null;
            if (pixels != null) {
                pixels = keepsAlpha(type) ? pixels : forceOpaque(pixels, type);
                loadedFromPng++;
            } else {
                pixels = TileGenerator.generate(name, type, i);
                generated++;
            }

            int slot = nextSlot++;
            blit(slot, pixels);
            layerPixels.add(pixels);
            slotByName.put(key, slot);
        }
        variantCount.put(name, count);
        variantFirstSlot.put(name, first);
    }

    /** Variant set that never consults a resource pack. */
    private void variantsGenerated(String name, BlockType type, int count) {
        int first = nextSlot;
        for (int i = 0; i < count; i++) {
            String key = (i == 0) ? name : name + "#" + i;
            int[] pixels = TileGenerator.generate(name, type, i);
            generated++;

            int slot = nextSlot++;
            blit(slot, pixels);
            layerPixels.add(pixels);
            slotByName.put(key, slot);
        }
        variantCount.put(name, count);
        variantFirstSlot.put(name, first);
    }

    /** Always procedural, bypassing resource packs and the opacity fixup. */
    private int tileGenerated(String name, BlockType type) {
        Integer existing = slotByName.get(name);
        if (existing != null) return existing;

        int[] pixels = TileGenerator.generate(name, type, 0);
        generated++;

        int slot = nextSlot++;
        blit(slot, pixels);
        layerPixels.add(pixels);
        slotByName.put(name, slot);
        return slot;
    }

    private int tile(String name, String pngFile, BlockType fallback) {
        return tile(name, pngFile, fallback, null);
    }

    /**
     * Register one atlas tile.
     *
     * @param baseUnder when non-null, the PNG is treated as an OVERLAY and is
     *                  composited over a procedural tile of this type. Several
     *                  textures in the CC0 pack (grass_side, the ore tiles) are
     *                  overlays with a mostly empty alpha channel - used raw
     *                  they would be discarded by the shader's alpha test and
     *                  punch see-through holes in solid blocks.
     */
    private int tile(String name, String pngFile, BlockType fallback, BlockType baseUnder) {
        Integer existing = slotByName.get(name);
        if (existing != null) return existing;

        int slot = nextSlot++;
        int[] pixels = loadTilePng(pngFile);

        if (pixels != null) {
            if (baseUnder != null) {
                pixels = compositeOver(generateTile(baseUnder), pixels);
            } else if (!keepsAlpha(fallback)) {
                pixels = forceOpaque(pixels, fallback);
            }
            loadedFromPng++;
        } else {
            pixels = generateTile(fallback);
            generated++;
        }

        blit(slot, pixels);
        layerPixels.add(pixels);
        slotByName.put(name, slot);
        return slot;
    }

    /** Blocks whose texture is legitimately see-through. */
    private boolean keepsAlpha(BlockType type) {
        return type == BlockType.GLASS
            || type == BlockType.WATER
            || type == BlockType.ICE
            || type == BlockType.OAK_LEAVES
            || type == BlockType.SPRUCE_LEAVES
            || type == BlockType.BIRCH_LEAVES
            || type == BlockType.JUNGLE_LEAVES
            // Cross-quad plants are mostly empty space by design
            || type == BlockType.GRASS_PLANT
            || type == BlockType.DANDELION
            || type == BlockType.POPPY
            || type == BlockType.DEAD_BUSH
            // All item sprites have transparent backgrounds
            || type.name.startsWith("item_") || type.name.equals("iron_sword");
    }

    /**
     * Alpha-blend an overlay tile onto an opaque base tile.
     */
    private int[] compositeOver(int[] base, int[] overlay) {
        int[] out = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        for (int i = 0; i < out.length; i++) {
            int ov = overlay[i];
            int a = (ov >>> 24) & 0xFF;

            if (a == 255) { out[i] = ov; continue; }
            if (a == 0) { out[i] = base[i]; continue; }

            int bs = base[i];
            int r = (((ov >> 16) & 0xFF) * a + ((bs >> 16) & 0xFF) * (255 - a)) / 255;
            int g = (((ov >> 8) & 0xFF) * a + ((bs >> 8) & 0xFF) * (255 - a)) / 255;
            int b = ((ov & 0xFF) * a + (bs & 0xFF) * (255 - a)) / 255;
            out[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return out;
    }

    /**
     * Safety net for blocks that must be fully solid: if a supposedly opaque
     * PNG turns out to have holes, fill them from the procedural tile rather
     * than letting the shader discard those texels.
     */
    private int[] forceOpaque(int[] pixels, BlockType fallback) {
        int holes = 0;
        for (int p : pixels) {
            if (((p >>> 24) & 0xFF) < 13) holes++;
        }
        if (holes == 0) return pixels;

        System.out.println("  note: opaque tile had " + holes
            + " transparent texels, filling from procedural base");
        return compositeOver(generateTile(fallback), pixels);
    }

    private void blit(int slot, int[] pixels) {
        int startX = (slot % TEXTURES_PER_ROW) * TEXTURE_SIZE;
        int startY = (slot / TEXTURES_PER_ROW) * TEXTURE_SIZE;
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                atlasPixels[(startY + y) * ATLAS_SIZE + (startX + x)] = pixels[y * TEXTURE_SIZE + x];
            }
        }
    }

    /**
     * Decode a PNG from the classpath into a 16x16 ARGB tile.
     * Returns null when the file is not present.
     */
    /**
     * Read one tile, delegating the search order to the resource pack:
     * selected pack first, bundled CC0 textures second, null if neither has
     * it (the caller then generates the tile procedurally).
     */
    private int[] loadTilePng(String fileName) {
        String name = fileName.endsWith(".png")
            ? fileName.substring(0, fileName.length() - 4)
            : fileName;
        return resourcePack.loadTile(name, TEXTURE_SIZE);
    }

    // ------------------------------------------------------------------
    // Block -> face slot mapping
    // ------------------------------------------------------------------

    private void mapBlockFaces() {
        faces(BlockType.GRASS_BLOCK, "grass_top", "grass_side", "dirt");
        faces(BlockType.DIRT, "dirt");
        faces(BlockType.COARSE_DIRT, "dirt");
        faces(BlockType.PODZOL, "podzol", "dirt", "dirt");
        faces(BlockType.STONE, "stone");
        faces(BlockType.GRANITE, "granite");
        faces(BlockType.POLISHED_GRANITE, "granite");
        faces(BlockType.ANDESITE, "stone");
        faces(BlockType.DIORITE, "stone");
        faces(BlockType.COBBLESTONE, "cobblestone");
        faces(BlockType.MOSSY_COBBLESTONE, "mossy_cobblestone");
        faces(BlockType.STONE_BRICKS, "stone_bricks");
        faces(BlockType.BRICK, "brick");

        faces(BlockType.OAK_PLANKS, "oak_planks");
        faces(BlockType.SPRUCE_PLANKS, "oak_planks");
        faces(BlockType.BIRCH_PLANKS, "oak_planks");
        faces(BlockType.JUNGLE_PLANKS, "oak_planks");
        faces(BlockType.OAK_LOG, "oak_log_top", "oak_log", "oak_log_top");
        faces(BlockType.SPRUCE_LOG, "oak_log_top", "oak_log", "oak_log_top");
        faces(BlockType.BIRCH_LOG, "birch_log_top", "birch_log_side", "birch_log_top");
        faces(BlockType.JUNGLE_LOG, "oak_log_top", "oak_log", "oak_log_top");

        faces(BlockType.OAK_LEAVES, "oak_leaves");
        faces(BlockType.SPRUCE_LEAVES, "oak_leaves");
        faces(BlockType.BIRCH_LEAVES, "birch_leaves");
        faces(BlockType.JUNGLE_LEAVES, "oak_leaves");

        faces(BlockType.SAND, "sand");
        faces(BlockType.GRAVEL, "gravel");
        faces(BlockType.CLAY, "clay");
        faces(BlockType.TERRACOTTA, "sandstone");
        faces(BlockType.SNOW, "snow");
        faces(BlockType.ICE, "ice");

        faces(BlockType.COAL_ORE, "ore_coal");
        faces(BlockType.IRON_ORE, "ore_iron");
        faces(BlockType.GOLD_ORE, "ore_gold");
        faces(BlockType.DIAMOND_ORE, "ore_diamond");
        faces(BlockType.EMERALD_ORE, "ore_diamond");
        faces(BlockType.REDSTONE_ORE, "ore_coal");
        faces(BlockType.LAPIS_ORE, "ore_coal");
        faces(BlockType.COAL_BLOCK, "coal_block");
        faces(BlockType.IRON_BLOCK, "iron_block");
        faces(BlockType.GOLD_BLOCK, "gold_block");
        faces(BlockType.DIAMOND_BLOCK, "diamond_block");
        faces(BlockType.EMERALD_BLOCK, "diamond_block");

        faces(BlockType.WATER, "water");
        faces(BlockType.LAVA, "lava");

        // Wheat crops with growth stages
        faces(BlockType.WHEAT, "wheat_stage0");
        faces(BlockType.GLASS, "glass");
        faces(BlockType.BEDROCK, "bedrock");
        faces(BlockType.OBSIDIAN, "obsidian");
        faces(BlockType.TNT, "tnt");
        faces(BlockType.CRAFTING_TABLE, "crafting_table", "crafting_table", "oak_planks");
        faces(BlockType.FURNACE, "stone");
        faces(BlockType.NETHERRACK, "netherrack");

        faces(BlockType.GRASS_PLANT, "grass_plant");
        faces(BlockType.DANDELION, "dandelion");
        faces(BlockType.POPPY, "poppy");
        faces(BlockType.DEAD_BUSH, "dead_bush");
        faces(BlockType.CACTUS, "cactus_top", "cactus_side", "cactus_top");
        faces(BlockType.GLOWSTONE, "glowstone");
        faces(BlockType.NETHER_BRICKS, "brick");

        // Items
        faces(BlockType.IRON_SWORD, "iron_sword");
        faces(BlockType.ITEM_STICK, "stick");
        faces(BlockType.ITEM_COAL, "coal");
        faces(BlockType.ITEM_IRON_INGOT, "iron_ingot");
        faces(BlockType.ITEM_GOLD_INGOT, "gold_ingot");
        faces(BlockType.ITEM_DIAMOND, "diamond");
        faces(BlockType.ITEM_LEATHER, "leather");
        faces(BlockType.ITEM_STRING, "string");
        faces(BlockType.ITEM_FEATHER, "feather");
        faces(BlockType.ITEM_FLINT, "flint");
        faces(BlockType.ITEM_GUNPOWDER, "gunpowder");
        faces(BlockType.ITEM_WHEAT, "wheat");
        faces(BlockType.ITEM_EGG, "egg");
        faces(BlockType.ITEM_WOODEN_PICKAXE, "wooden_pickaxe");
        faces(BlockType.ITEM_WOODEN_AXE, "wooden_axe");
        faces(BlockType.ITEM_WOODEN_SHOVEL, "wooden_shovel");
        faces(BlockType.ITEM_WOODEN_SWORD, "wooden_sword");
        faces(BlockType.ITEM_WOODEN_HOE, "wooden_hoe");
        faces(BlockType.ITEM_STONE_PICKAXE, "stone_pickaxe");
        faces(BlockType.ITEM_STONE_AXE, "stone_axe");
        faces(BlockType.ITEM_STONE_SHOVEL, "stone_shovel");
        faces(BlockType.ITEM_STONE_SWORD, "stone_sword");
        faces(BlockType.ITEM_STONE_HOE, "stone_hoe");
        faces(BlockType.ITEM_IRON_PICKAXE, "iron_pickaxe");
        faces(BlockType.ITEM_IRON_AXE, "iron_axe");
        faces(BlockType.ITEM_IRON_SHOVEL, "iron_shovel");
        faces(BlockType.ITEM_IRON_SWORD, "iron_sword_item");
        faces(BlockType.ITEM_IRON_HOE, "iron_hoe");
        faces(BlockType.ITEM_DIAMOND_PICKAXE, "diamond_pickaxe");
        faces(BlockType.ITEM_DIAMOND_AXE, "diamond_axe");
        faces(BlockType.ITEM_DIAMOND_SHOVEL, "diamond_shovel");
        faces(BlockType.ITEM_DIAMOND_SWORD, "diamond_sword");
        faces(BlockType.ITEM_DIAMOND_HOE, "diamond_hoe");
        faces(BlockType.ITEM_GOLDEN_PICKAXE, "golden_pickaxe");
        faces(BlockType.ITEM_GOLDEN_AXE, "golden_axe");
        faces(BlockType.ITEM_GOLDEN_SHOVEL, "golden_shovel");
        faces(BlockType.ITEM_GOLDEN_SWORD, "golden_sword");
        faces(BlockType.ITEM_GOLDEN_HOE, "golden_hoe");
        faces(BlockType.ITEM_LEATHER_HELMET, "leather_helmet");
        faces(BlockType.ITEM_LEATHER_CHESTPLATE, "leather_chestplate");
        faces(BlockType.ITEM_LEATHER_LEGGINGS, "leather_leggings");
        faces(BlockType.ITEM_LEATHER_BOOTS, "leather_boots");
        faces(BlockType.ITEM_IRON_HELMET, "iron_helmet");
        faces(BlockType.ITEM_IRON_CHESTPLATE, "iron_chestplate");
        faces(BlockType.ITEM_IRON_LEGGINGS, "iron_leggings");
        faces(BlockType.ITEM_IRON_BOOTS, "iron_boots");
        faces(BlockType.ITEM_DIAMOND_HELMET, "diamond_helmet");
        faces(BlockType.ITEM_DIAMOND_CHESTPLATE, "diamond_chestplate");
        faces(BlockType.ITEM_DIAMOND_LEGGINGS, "diamond_leggings");
        faces(BlockType.ITEM_DIAMOND_BOOTS, "diamond_boots");
        faces(BlockType.ITEM_BREAD, "bread");
        faces(BlockType.ITEM_RAW_PORK, "raw_pork");
        faces(BlockType.ITEM_COOKED_PORK, "cooked_pork");
        faces(BlockType.ITEM_RAW_BEEF, "raw_beef");
        faces(BlockType.ITEM_COOKED_BEEF, "cooked_beef");
        faces(BlockType.ITEM_RAW_CHICKEN, "raw_chicken");
        faces(BlockType.ITEM_COOKED_CHICKEN, "cooked_chicken");
        faces(BlockType.ITEM_RAW_MUTTON, "raw_mutton");
        faces(BlockType.ITEM_COOKED_MUTTON, "cooked_mutton");
        faces(BlockType.ITEM_APPLE, "apple");
        faces(BlockType.ITEM_BOW, "bow");
        faces(BlockType.ITEM_ARROW, "arrow");
        faces(BlockType.ITEM_TORCH, "torch");
        faces(BlockType.ITEM_ROTTEN_FLESH, "rotten_flesh");
        faces(BlockType.ITEM_BONE, "bone");
        faces(BlockType.ITEM_SPIDER_EYE, "spider_eye");
    }

    private void faces(BlockType block, String all) {
        faces(block, all, all, all);
    }

    private void faces(BlockType block, String top, String side, String bottom) {
        int id = block.id & 0xFF;
        faceSlots.put(id, new int[]{
            slotByName.getOrDefault(top, 0),
            slotByName.getOrDefault(side, 0),
            slotByName.getOrDefault(bottom, 0)
        });
    }

    // ------------------------------------------------------------------
    // Public lookup
    // ------------------------------------------------------------------

    public TextureCoords getCoords(byte blockId) {
        return getCoords(blockId, -1);
    }

    /** UV for a specific face. face: 2 = top, 3 = bottom, anything else = side. */
    public TextureCoords getCoords(byte blockId, int face) {
        return new TextureCoords(getSlot(blockId, face));
    }

    public int getSlot(byte blockId) {
        return getSlot(blockId, -1);
    }

    public int getSlot(byte blockId, int face) {
        int id = blockId & 0xFF;
        int[] fs = faceSlots.get(id);
        if (fs == null) {
            return slotByName.getOrDefault("stone", 0);
        }
        if (face == FACE_TOP) return fs[0];
        if (face == FACE_BOTTOM) return fs[2];
        return fs[1];
    }

    /**
     * Slot for a face, picking a texture variant from the block position.
     *
     * Large expanses of one material are the most obvious tiling artefact,
     * so grass/dirt/stone/sand each ship several variants and every block
     * deterministically takes one. Neighbours differ, the pattern does not
     * repeat, and the choice is stable across chunk rebuilds.
     */
    public int getSlot(byte blockId, int face, int wx, int wy, int wz) {
        int slot = getSlot(blockId, face);

        String name = nameBySlot.get(slot);
        if (name == null) return slot;

        // Fast graphics swaps in the hole-free leaf tile
        if ("oak_leaves".equals(name) && !ChunkMeshBuilder.isFancyGraphics()) {
            name = "oak_leaves_opaque";
        }

        Integer count = variantCount.get(name);
        if (count == null || count <= 1) return slot;

        int pick = positionHash(wx, wy, wz) % count;
        return variantFirstSlot.get(name) + pick;
    }

    /**
     * True when this face's texture may be mirrored without looking wrong.
     * Directional art (grass fringe, planks, bricks) must not be flipped.
     */
    public boolean canFlip(byte blockId, int face) {
        String name = nameBySlot.get(getSlot(blockId, face));
        if (name == null) return false;
        return switch (name) {
            // Non-directional tiles: mirroring them breaks up the repetition
            // without producing anything that reads as upside-down
            case "grass_top", "dirt", "stone", "sand", "gravel",
                 "cobblestone", "snow", "netherrack", "clay",
                 "oak_leaves" -> true;
            default -> false;
        };
    }

    /** 0..3: bit 0 flips U, bit 1 flips V. */
    public int flipFor(int wx, int wy, int wz) {
        return (positionHash(wx, wy, wz) >> 3) & 3;
    }

    private static int positionHash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= (h >> 16);
        return h & 0x7FFFFFFF;
    }

    // ------------------------------------------------------------------
    // Upload
    // ------------------------------------------------------------------

    public void build() {
        // OpenGL treats the first uploaded row as t=0 (bottom). Our atlas array
        // has row 0 at the TOP, so flip vertically here; the UV formula in
        // TextureCoords assumes this orientation.
        int[] flipped = new int[atlasPixels.length];
        for (int y = 0; y < ATLAS_SIZE; y++) {
            int src = y * ATLAS_SIZE;
            int dst = (ATLAS_SIZE - 1 - y) * ATLAS_SIZE;
            System.arraycopy(atlasPixels, src, flipped, dst, ATLAS_SIZE);
        }

        atlasTexture = new Texture(ATLAS_SIZE, ATLAS_SIZE, flipped);
        buildArrayTexture();

        // Nothing is cached between runs: the atlas is rebuilt from source
        // every startup, so a texture change always reaches the screen.
        System.out.println("Atlas regenerated (no cache)");
        logFoliageTiles();

        System.out.println("Texture atlas " + ATLAS_SIZE + "x" + ATLAS_SIZE
            + ": " + nextSlot + " tiles (" + loadedFromPng + " from PNG, "
            + generated + " procedural), array layers=" + layerPixels.size()
            + ", mips=" + MIP_LEVELS + ", aniso=" + anisotropyUsed + "x");
    }

    /**
     * Upload every tile as one layer of a GL_TEXTURE_2D_ARRAY.
     *
     * Greedy meshing emits quads spanning many blocks, so UVs run 0..width.
     * A texture array with GL_REPEAT tiles correctly across a merged quad,
     * which a packed atlas cannot do without bleeding into neighbouring tiles.
     */
    private void buildArrayTexture() {
        int layers = layerPixels.size();
        if (layers == 0) return;

        arrayTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, arrayTextureId);

        ByteBuffer buffer = MemoryUtil.memAlloc(TEXTURE_SIZE * TEXTURE_SIZE * 4 * layers);
        for (int[] tile : layerPixels) {
            // Flip each tile so it matches OpenGL's bottom-up row order
            for (int y = TEXTURE_SIZE - 1; y >= 0; y--) {
                for (int x = 0; x < TEXTURE_SIZE; x++) {
                    int p = tile[y * TEXTURE_SIZE + x];
                    buffer.put((byte) ((p >> 16) & 0xFF));
                    buffer.put((byte) ((p >> 8) & 0xFF));
                    buffer.put((byte) (p & 0xFF));
                    buffer.put((byte) ((p >> 24) & 0xFF));
                }
            }
        }
        buffer.flip();

        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA,
            TEXTURE_SIZE, TEXTURE_SIZE, layers, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);

        // Each array layer mips independently, so neighbouring tiles can never
        // bleed into one another - no guard border needed, unlike a 2D atlas.
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_BASE_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAX_LEVEL, MIP_LEVELS - 1);
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);

        // Crisp texels up close, mip-blended in the distance
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        applyAnisotropy();

        MemoryUtil.memFree(buffer);
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    /**
     * Request 8x anisotropic filtering. Ships as an extension on GL 3.3
     * (GL_EXT_texture_filter_anisotropic), so probe before using it.
     */
    private void applyAnisotropy() {
        var caps = org.lwjgl.opengl.GL.getCapabilities();
        if (!caps.GL_EXT_texture_filter_anisotropic) {
            System.out.println("  anisotropic filtering unavailable, skipping");
            return;
        }

        float max = glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
        float level = Math.min(ANISOTROPY, max);
        glTexParameterf(GL_TEXTURE_2D_ARRAY,
            EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, level);
        anisotropyUsed = level;
    }

    /**
     * Print what actually landed in the atlas for the cut-out tiles.
     *
     * This is the check that catches a resource pack silently overriding a
     * generated tile - the failure mode that kept the reworked foliage from
     * reaching the screen.
     */
    private void logFoliageTiles() {
        for (String name : new String[]{"oak_leaves", "tall_grass", "grass_top", "grass_side"}) {
            Integer slot = slotByName.get(name);
            if (slot == null) continue;

            int[] px = layerPixels.get(slot);
            int holes = 0;
            long r = 0, g = 0, b = 0, n = 0;
            for (int p : px) {
                if (((p >>> 24) & 0xFF) < 128) { holes++; continue; }
                r += (p >> 16) & 0xFF;
                g += (p >> 8) & 0xFF;
                b += p & 0xFF;
                n++;
            }
            n = Math.max(1, n);
            double bias = (g / (double) n) / (((r / (double) n) + (b / (double) n)) / 2.0);

            System.out.printf("  %-12s layer=%2d cutout=%4.1f%% avg=(%3d,%3d,%3d) greenBias=%.2f%n",
                name, slot, 100.0 * holes / px.length,
                r / n, g / n, b / n, bias);
        }
    }

    /** Layer of a named tile, for the debug atlas viewer. */
    public int getLayerOf(String tileName) {
        return slotByName.getOrDefault(tileName, -1);
    }

    public void bindArray() {
        glBindTexture(GL_TEXTURE_2D_ARRAY, arrayTextureId);
    }

    public void unbindArray() {
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    public int getLayerCount() { return layerPixels.size(); }

    /** Array layer of the water tile, so the shader can animate just that one. */
    public int getWaterLayer() {
        return slotByName.getOrDefault("water", -1);
    }

    public void bind() {
        if (atlasTexture != null) atlasTexture.bind();
    }

    public void unbind() {
        if (atlasTexture != null) atlasTexture.unbind();
    }

    public void cleanup() {
        if (atlasTexture != null) atlasTexture.cleanup();
        if (arrayTextureId != 0) {
            glDeleteTextures(arrayTextureId);
            arrayTextureId = 0;
        }
    }

    public Texture getTexture() { return atlasTexture; }

    // ------------------------------------------------------------------
    // Procedural fallback tiles
    // ------------------------------------------------------------------

    private int[] generateTile(BlockType block) {
        int[] out = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        int r = (block.color >> 16) & 0xFF;
        int g = (block.color >> 8) & 0xFF;
        int b = block.color & 0xFF;

        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                int seed = x * 17 + y * 31 + block.id * 127;
                int noise = ((seed * 13 * 17) % 41) - 20;
                out[y * TEXTURE_SIZE + x] = generatePixel(block, x, y, r, g, b, noise);
            }
        }
        return out;
    }

    private int generatePixel(BlockType block, int x, int y, int r, int g, int b, int noise) {
        switch (block) {
            case GRASS_BLOCK: return grassPixel(x, y, noise);
            case STONE:       return stonePixel(x, y, noise);
            case COBBLESTONE: return cobblePixel(x, y, noise);
            case DIRT:        return dirtPixel(x, y, noise);
            case OAK_LOG:     return logPixel(x, y, noise);
            case OAK_LEAVES:  return leavesPixel(x, y, noise);
            case SAND:        return sandPixel(x, y, noise);
            case GLASS:       return glassPixel(x, y);
            case WATER:       return waterPixel(x, y, noise);
            case LAVA:        return lavaPixel(x, y, noise);
            case BRICK:       return brickPixel(x, y);
            case OAK_PLANKS:  return planksPixel(x, y, noise);
            case BEDROCK:     return bedrockPixel(x, y, noise);
            case COAL_ORE:
            case IRON_ORE:
            case GOLD_ORE:
            case DIAMOND_ORE: return orePixel(x, y, noise, block.color);
            case TNT:         return tntPixel(x, y);
            case CRAFTING_TABLE: return craftingPixel(x, y, noise);
            default:          return defaultPixel(x, y, r, g, b, noise);
        }
    }

    private int grassPixel(int x, int y, int noise) {
        int r = 93 + noise, g = 130 + noise, b = 66 + noise;
        if ((x + y) % 7 == 0) { r -= 15; g -= 10; }
        if ((x * y) % 11 == 0) { r += 10; g += 15; }
        return clamp(r, g, b);
    }

    private int stonePixel(int x, int y, int noise) {
        int v = 128 + noise;
        if ((x + y * 3) % 23 == 0) v -= 30;
        if ((x * 7 + y * 5) % 31 == 0) v += 20;
        return clamp(v, v, v);
    }

    private int cobblePixel(int x, int y, int noise) {
        int base = (((x / 8) + (y / 8)) % 2 == 0) ? 110 : 90;
        int v = base + noise + ((x * 3 + y * 7) % 20) - 10;
        return clamp(v, v, v);
    }

    private int dirtPixel(int x, int y, int noise) {
        int r = 139 + noise, g = 90 + noise, b = 43 + noise;
        if ((x * 5 + y * 3) % 13 == 0) { r -= 20; g -= 15; b -= 10; }
        return clamp(r, g, b);
    }

    private int logPixel(int x, int y, int noise) {
        int r = 107 + noise, g = 68 + noise, b = 35 + noise;
        if (x % 4 == 0) { r -= 25; g -= 20; b -= 15; }
        if ((x + y) % 8 == 0) { r += 15; g += 10; }
        return clamp(r, g, b);
    }

    private int leavesPixel(int x, int y, int noise) {
        int r = 45 + noise, g = 100 + noise, b = 30 + noise;
        if ((x + y) % 5 == 0) { r -= 20; g -= 15; }
        if ((x * y) % 7 == 0) { g += 25; b += 10; }
        if ((x * 13 + y * 11) % 17 == 0) { r -= 30; g -= 20; b -= 15; }
        return clamp(r, g, b);
    }

    private int sandPixel(int x, int y, int noise) {
        int r = 220 + noise, g = 205 + noise, b = 150 + noise;
        if ((x + y) % 9 == 0) { r -= 10; g -= 10; b -= 5; }
        return clamp(r, g, b);
    }

    private int glassPixel(int x, int y) {
        if (x == 0 || x == 15 || y == 0 || y == 15) return 0xFFC0E8FF;
        return 0x18FFFFFF;
    }

    private int waterPixel(int x, int y, int noise) {
        int r = 48 + noise / 2, g = 100 + noise / 2, b = 200 + noise;
        if ((x + y) % 6 == 0) { b += 20; g += 10; }
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return (0xA0 << 24) | (r << 16) | (g << 8) | b;
    }

    private int lavaPixel(int x, int y, int noise) {
        int r = 220 + noise, g = 80 + noise / 2, b = 20;
        if ((x * 7 + y * 11) % 13 == 0) { r = 255; g = 180; b = 50; }
        if ((x + y * 3) % 17 == 0) { r = 180; g = 40; b = 10; }
        return clamp(r, g, b);
    }

    private int brickPixel(int x, int y) {
        boolean mortar = (y % 8 == 0) || ((x + (y / 8) * 4) % 8 == 0);
        if (mortar) return 0xFFC8C8C8;
        int noise = ((x * 17 + y * 31) % 30) - 15;
        return clamp(170 + noise, 70 + noise, 50 + noise);
    }

    private int planksPixel(int x, int y, int noise) {
        int r = 180 + noise, g = 140 + noise, b = 90 + noise;
        if (y % 4 == 0) { r -= 20; g -= 15; b -= 10; }
        if ((x + y) % 6 == 0) { r += 10; g += 8; b += 5; }
        return clamp(r, g, b);
    }

    private int bedrockPixel(int x, int y, int noise) {
        int v = 40 + noise;
        if ((x * 3 + y * 7) % 11 == 0) v += 30;
        if ((x + y) % 5 == 0) v -= 20;
        return clamp(v, v, v);
    }

    private int orePixel(int x, int y, int noise, int oreColor) {
        int base = 128 + noise;
        if ((x * 13 + y * 17) % 19 < 4) {
            return clamp(((oreColor >> 16) & 0xFF) + noise,
                         ((oreColor >> 8) & 0xFF) + noise,
                         (oreColor & 0xFF) + noise);
        }
        return clamp(base, base, base);
    }

    private int tntPixel(int x, int y) {
        if (y < 4 || y > 11) return 0xFFFFFFFF;
        if (x < 2 || x > 13) return 0xFF808080;
        return 0xFFC02020;
    }

    private int craftingPixel(int x, int y, int noise) {
        int r = 180 + noise, g = 140 + noise, b = 90 + noise;
        if (x % 8 == 0 || y % 8 == 0) { r -= 40; g -= 30; b -= 20; }
        return clamp(r, g, b);
    }

    private int defaultPixel(int x, int y, int r, int g, int b, int noise) {
        int dr = r + noise, dg = g + noise, db = b + noise;
        if (x == 0 || x == 15) { dr -= 25; dg -= 25; db -= 25; }
        if (y == 0 || y == 15) { dr -= 25; dg -= 25; db -= 25; }
        return clamp(dr, dg, db);
    }

    private int clamp(int r, int g, int b) {
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
