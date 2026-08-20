package com.voxelgame.rendering.texture;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry of all procedural textures. At startup, AtlasBuilder reads
 * these generators, renders them into the atlas, and builds the UV map.
 */
public final class TextureRegistry {

    public record BlockFaces(String blockId, String top, String side, String bottom) {
        public static BlockFaces single(String blockId, String face) {
            return new BlockFaces(blockId, face, face, face);
        }
        public static BlockFaces topSide(String blockId, String top, String side) {
            return new BlockFaces(blockId, top, side, side);
        }
    }

    private static final Map<String, TextureGenerator> TILES = new LinkedHashMap<>();
    private static final List<BlockFaces> BLOCKS = new ArrayList<>();

    private TextureRegistry() {}

    /** Register a named 16x16 tile generator. */
    public static void registerTile(String name, TextureGenerator generator) {
        TILES.put(name, generator);
    }

    /** Map a block's faces to atlas tile names. */
    public static void registerBlock(BlockFaces faces) {
        BLOCKS.add(faces);
    }

    public static Map<String, TextureGenerator> tiles() {
        return TILES;
    }

    public static List<BlockFaces> blocks() {
        return BLOCKS;
    }

    /** Resolve the tile name for a block face. */
    public static String tileFor(String blockId, String face) {
        for (BlockFaces b : BLOCKS) {
            if (b.blockId().equals(blockId)) {
                return switch (face) {
                    case "top" -> b.top();
                    case "bottom" -> b.bottom();
                    default -> b.side();
                };
            }
        }
        return null;
    }
}
